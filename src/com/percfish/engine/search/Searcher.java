package com.percfish.engine.search;

import com.percfish.engine.evaluation.Evaluator;
import com.percfish.engine.state.*;
import java.util.List;
import java.util.function.Consumer;

public class Searcher {
    private static final int MATE_SCORE = 1_000_000;
    private static final int INFINITY = MATE_SCORE + 1_000;
    private static final long NO_DEADLINE = Long.MAX_VALUE;
    private static final int MAX_QUIESCENCE_DEPTH = 4;
    private static final int DELTA_PRUNING_MARGIN = 200;
    private static final int MAX_KILLER_PLY = 100;
    private static final int KILLER_SLOTS = 2;

    private static final int NMP_DEPTH_THRESHOLD = 3;
    private static final int NMP_REDUCTION = 2;

    private static final int LMR_MAX_DEPTH = 64;
    private static final int LMR_MAX_MOVES = 218;
    private static final int LMR_MOVE_INDEX_THRESHOLD = 3;
    private static final int LMR_MIN_DEPTH = 3;

    private final int[][] lmrTable = new int[LMR_MAX_DEPTH][LMR_MAX_MOVES];

    {
        for (int d = 1; d < LMR_MAX_DEPTH; d++) {
            for (int m = 1; m < LMR_MAX_MOVES; m++) {
                double r = Math.log(d) * Math.log(m) / 2.2;
                lmrTable[d][m] = Math.max(0, Math.min((int) r, d - 1));
            }
        }
    }

    private final Evaluator evaluator = new Evaluator();
    private final MoveGenerator moveGenerator = new MoveGenerator();
    private final PositionHistory pathHistory = new PositionHistory();
    private final TranspositionTable tt = new TranspositionTable(64); // 64MB default

    // killerMoves[ply][slot] stores quiet move that caused a beta cutoff at this ply level.
    private final Move[][] killerMoves = new Move[MAX_KILLER_PLY][KILLER_SLOTS];

    // History heuristic: [side (0/1)][from (0-80)][to (0-80)] as a flat short array.
    private final short[] historyTable = new short[2 * 81 * 81];

    // Pre-allocated per-ply buffers for quiet moves (avoids allocation and cross-ply corruption in search).
    private final Move[][] quietBuffers = new Move[MAX_KILLER_PLY][128];

    private volatile boolean stop = false;
    private long nodes;
    private long ttHits;

    public void stop() {
        stop = true;
    }

    public void resetNewGame() {
        stop = false;
        nodes = 0;
        ttHits = 0;
        tt.clear();
        clearKillerMoves();
        ageHistoryTable();
    }

    public SearchResult search(Board board, int depth) {
        return search(board, depth, null);
    }

    public SearchResult search(Board board, int depth, PositionHistory history) {
        stop = false;
        return search(board, depth, NO_DEADLINE, null, history);
    }

    public SearchResult searchIterative(Board board, int maxDepth, long movetimeMs, Consumer<SearchResult> onDepthCompleted) {
        return searchIterative(board, maxDepth, movetimeMs, onDepthCompleted, null);
    }

    public SearchResult searchIterative(Board board, int maxDepth, long movetimeMs,
                                        Consumer<SearchResult> onDepthCompleted, PositionHistory history) {
        stop = false;
        nodes = 0;
        ttHits = 0;
        tt.clear();
        clearKillerMoves();
        ageHistoryTable();
        long deadlineNanos = movetimeMs == NO_DEADLINE ? NO_DEADLINE : System.nanoTime() + Math.max(1L, movetimeMs) * 1_000_000L;
        List<Move> legalMoves = moveGenerator.generateLegalMoves(board);

        if (legalMoves.isEmpty()) {
            return new SearchResult(null, -MATE_SCORE, 0, 0, 0);
        }

        Move pvMove = null;
        orderMoves(board, legalMoves, pvMove, null);
        SearchResult bestCompletedResult = new SearchResult(legalMoves.getFirst(), evaluator.evaluate(board), 0, 0, 0);

        for (int depth = 1; depth <= maxDepth; depth++) {
            try {
                SearchResult result = search(board, depth, deadlineNanos, pvMove, history);
                bestCompletedResult = result;
                pvMove = result.bestMove();
                onDepthCompleted.accept(result);
                
                if (Math.abs(result.score()) >= MATE_SCORE - 1000) {
                    break;
                }
            } catch (SearchStoppedException e) {
                break;
            }
        }

        return bestCompletedResult;
    }

    private SearchResult search(Board board, int depth, long deadlineNanos, Move pvMoveHint, PositionHistory history) {
        checkTime(deadlineNanos);
        pathHistory.clear();
        if (history != null) {
            pathHistory.copyFrom(history);
        } else {
            pathHistory.record(board.getZobristKey());
        }

        int searchDepth = Math.max(1, depth);
        int movingColor = board.isWhiteToMove ? Piece.WHITE : Piece.BLACK;
        List<Move> pseudoLegalMoves = moveGenerator.generatePseudoLegalMoves(board);

        if (pseudoLegalMoves.isEmpty()) {
            return new SearchResult(null, -MATE_SCORE, searchDepth, nodes, ttHits);
        }

        // Try to get PV move from TT for root move ordering
        Move ttMove = null;
        TTEntry entry = tt.probe(board.getZobristKey());
        if (entry != null) {
            ttMove = entry.bestMove();
        }

        orderMoves(board, pseudoLegalMoves, pvMoveHint, ttMove);
        Move bestMove = null;
        int bestScore = -INFINITY;
        int alpha = -INFINITY;

        for (Move move : pseudoLegalMoves) {
            if (isKingCapture(board, move)) {
                continue;
            }

            MoveState state = board.makeMove(move);
            pathHistory.record(board.getZobristKey());
            int score;

            try {
                if (moveGenerator.isInCheck(board, movingColor)) {
                    continue;
                }
                score = -negamax(board, searchDepth - 1, 1, -INFINITY, -alpha, deadlineNanos);
            } finally {
                pathHistory.unrecord(board.getZobristKey());
                board.unmakeMove(state);
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
                alpha = bestScore;
            }
        }

        if (bestMove == null) {
            return new SearchResult(null, -MATE_SCORE, searchDepth, nodes, ttHits);
        }

        // Store root result in TT
        int storeScore = bestScore;
        tt.store(board.getZobristKey(), storeScore, searchDepth, TranspositionTable.EXACT, bestMove);

        return new SearchResult(bestMove, bestScore, searchDepth, nodes, ttHits);
    }

    private int negamax(Board board, int depth, int ply, int alpha, int beta, long deadlineNanos) {
        checkTime(deadlineNanos);
        nodes++;

        long key = board.getZobristKey();
        if (pathHistory.getCount(key) >= 3) {
            return 0;
        }

        TTEntry entry = tt.probe(key);
        Move ttMove = null;

        if (entry != null && entry.depth() >= depth) {
            ttHits++;
            int ttScore = entry.score();

            if (entry.flag() == TranspositionTable.EXACT) {
                return ttScore;
            } else if (entry.flag() == TranspositionTable.LOWER_BOUND) {
                alpha = Math.max(alpha, ttScore);
            } else if (entry.flag() == TranspositionTable.UPPER_BOUND) {
                beta = Math.min(beta, ttScore);
            }

            if (alpha >= beta) {
                return ttScore;
            }
        }
        if (entry != null) {
            ttMove = entry.bestMove();
        }

        int movingColor = board.isWhiteToMove ? Piece.WHITE : Piece.BLACK;
        boolean inCheck = moveGenerator.isInCheck(board, movingColor);

        // ── Null-Move Pruning (NMP) ──
        if (depth >= NMP_DEPTH_THRESHOLD
                && !inCheck
                && hasNonPawnMaterial(board, movingColor)) {

            MoveState nullState = board.makeNullMove();
            pathHistory.record(board.getZobristKey());
            int nullScore;
            try {
                nullScore = -negamax(board, depth - 1 - NMP_REDUCTION, ply + 1,
                        -beta, -beta + 1, deadlineNanos);
            } finally {
                pathHistory.unrecord(board.getZobristKey());
                board.unmakeNullMove(nullState);
            }

            if (nullScore >= beta) {
                return beta;   // cutoff – opponent doesn't need to move to refute
            }
        }
        // ── End NMP ───

        if (depth == 0) {
            return quiescence(board, ply, 0, alpha, beta, deadlineNanos);
        }

        List<Move> pseudoLegalMoves = moveGenerator.generatePseudoLegalMoves(board);
        if (pseudoLegalMoves.isEmpty()) {
            return -MATE_SCORE + ply;
        }

        orderMoves(board, pseudoLegalMoves, null, ttMove, ply);
        int bestScore = -INFINITY;
        Move bestMove = null;
        int originalAlpha = alpha;

        int quietCount = 0;
        int sideIdx = board.isWhiteToMove ? 0 : 1;
        int moveIndex = 0;

        for (Move move : pseudoLegalMoves) {
            if (isKingCapture(board, move)) {
                moveIndex++;
                continue;
            }

            boolean isQuiet = !isCapture(board, move) && !move.isPromotion();

            MoveState state = board.makeMove(move);
            pathHistory.record(board.getZobristKey());
            int score;

            try {
                if (moveGenerator.isInCheck(board, movingColor)) {
                    moveIndex++;
                    continue;
                }

                // ── Late Move Reduction (LMR) ──
                int historyScore = isQuiet ? getHistoryScore(sideIdx, move.from(), move.to()) : 0;
                int newDepth = depth - 1;

                if (lmrEligible(moveIndex, depth, isQuiet, inCheck, historyScore)) {
                    int clampedDepth = Math.min(depth, LMR_MAX_DEPTH - 1);
                    int clampedMoveIdx = Math.min(moveIndex, LMR_MAX_MOVES - 1);
                    int reduction = lmrTable[clampedDepth][clampedMoveIdx];
                    int reducedDepth = Math.max(0, newDepth - reduction);

                    score = -negamax(board, reducedDepth, ply + 1, -alpha - 1, -alpha, deadlineNanos);

                    if (score <= alpha) {
                        moveIndex++;
                        continue;
                    }
                }
                // ── End LMR ──

                if (isQuiet && quietCount < quietBuffers[ply].length) {
                    quietBuffers[ply][quietCount++] = move;
                }

                score = -negamax(board, newDepth, ply + 1, -beta, -alpha, deadlineNanos);
            } finally {
                pathHistory.unrecord(board.getZobristKey());
                board.unmakeMove(state);
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

            alpha = Math.max(alpha, score);

            if (alpha >= beta) {
                if (!isCapture(board, move) && !move.isPromotion()) {
                    storeKillerMove(ply, move);
                    updateHistory(sideIdx, move, quietBuffers[ply], quietCount, depth);
                }
                break;
            }

            moveIndex++;
        }

        if (bestMove == null) {
            return -MATE_SCORE + ply;
        }

        int storeScore = bestScore;

        int flag = TranspositionTable.EXACT;
        if (bestScore <= originalAlpha) flag = TranspositionTable.UPPER_BOUND;
        else if (bestScore >= beta) flag = TranspositionTable.LOWER_BOUND;

        tt.store(key, storeScore, depth, flag, bestMove);

        return bestScore;
    }

    private int quiescence(Board board, int ply, int qDepth, int alpha, int beta, long deadlineNanos) {
        checkTime(deadlineNanos);
        nodes++;

        long key = board.getZobristKey();
        // Threefold repetition draw in quiescence as well
        if (pathHistory.getCount(key) >= 3) {
            return 0;
        }

        int movingColor = board.isWhiteToMove ? Piece.WHITE : Piece.BLACK;
        boolean inCheck = moveGenerator.isInCheck(board, movingColor);
        int bestScore = -INFINITY;
        int standPat = -INFINITY;

        if (!inCheck) {
            standPat = evaluator.evaluate(board);
            if (standPat >= beta) {
                return standPat;
            }

            alpha = Math.max(alpha, standPat);
            bestScore = standPat;
        }

        if (qDepth >= MAX_QUIESCENCE_DEPTH) {
            return inCheck ? evaluator.evaluate(board) : bestScore;
        }

        List<Move> pseudoLegalMoves = moveGenerator.generatePseudoLegalMoves(board);
        orderMoves(board, pseudoLegalMoves, null, null, ply);
        boolean foundLegalMove = false;

        for (Move move : pseudoLegalMoves) {
            if (isKingCapture(board, move)) {
                continue;
            }

            boolean isCapture = isCapture(board, move);
            if (!inCheck && isCapture && standPat + captureGain(board, move) + DELTA_PRUNING_MARGIN <= alpha) {
                continue;
            }

            MoveState state = board.makeMove(move);
            pathHistory.record(board.getZobristKey());
            int score;

            try {
                if (moveGenerator.isInCheck(board, movingColor)) {
                    continue;
                }

                foundLegalMove = true;
                if (!inCheck && !isCapture) {
                    continue;
                }

                score = -quiescence(board, ply + 1, qDepth + 1, -beta, -alpha, deadlineNanos);
            } finally {
                pathHistory.unrecord(board.getZobristKey());
                board.unmakeMove(state);
            }

            if (score > bestScore) {
                bestScore = score;
            }

            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                break;
            }
        }

        if (inCheck && !foundLegalMove) {
            return -MATE_SCORE + ply;
        }

        return bestScore;
    }

    private void checkTime(long deadlineNanos) {
        if (stop || System.nanoTime() >= deadlineNanos) {
            throw new SearchStoppedException();
        }
    }

    private void storeKillerMove(int ply, Move move) {
        if (ply >= MAX_KILLER_PLY) return;
        // Don't store captures or promotions as killers
        if (move.isPromotion()) return;
        if (killerMoves[ply][0] != null && killerMoves[ply][0].equals(move)) {
            return;
        }

        killerMoves[ply][1] = killerMoves[ply][0];
        killerMoves[ply][0] = move;
    }

    private void clearKillerMoves() {
        for (int ply = 0; ply < MAX_KILLER_PLY; ply++) {
            killerMoves[ply][0] = null;
            killerMoves[ply][1] = null;
        }
    }

    // ── History heuristic helpers ──

    private static int historyIdx(int side, int from, int to) {
        return side * 81 * 81 + from * 81 + to;
    }

    private void ageHistoryTable() {
        for (int i = 0; i < historyTable.length; i++) {
            historyTable[i] /= 2;
        }
    }

    private int getHistoryScore(int side, int from, int to) {
        return historyTable[historyIdx(side, from, to)];
    }

    private void updateHistory(int side, Move bestMove, Move[] quiets, int count, int depth) {
        int bonus = Math.min(depth * depth, 256);

        for (int i = 0; i < count; i++) {
            Move q = quiets[i];
            if (q.equals(bestMove)) continue;
            int idx = historyIdx(side, q.from(), q.to());
            int v = historyTable[idx] - bonus;
            historyTable[idx] = (short) Math.max(v, Short.MIN_VALUE);
        }

        int bestIdx = historyIdx(side, bestMove.from(), bestMove.to());
        int v = historyTable[bestIdx] + bonus;
        historyTable[bestIdx] = (short) Math.min(v, Short.MAX_VALUE);
    }

    // ── Move ordering ──

    private void orderMoves(Board board, List<Move> moves, Move pvMove, Move ttMove) {
        orderMoves(board, moves, pvMove, ttMove, 0);
    }

    private void orderMoves(Board board, List<Move> moves, Move pvMove, Move ttMove, int ply) {
        int opponentPawnColor = board.isWhiteToMove ? Piece.BLACK : Piece.WHITE;

        moves.sort((a, b) -> Integer.compare(
                scoreMove(board, b, pvMove, ttMove, opponentPawnColor, ply),
                scoreMove(board, a, pvMove, ttMove, opponentPawnColor, ply)));
    }

    private int scoreMove(Board board, Move move, Move pvMove, Move ttMove, int opponentPawnColor, int ply) {
        if (move.equals(pvMove)) {
            return 2_000_000;
        }
        if (move.equals(ttMove)) {
            return 1_000_000;
        }

        if (ply < MAX_KILLER_PLY) {
            if (move.equals(killerMoves[ply][0])) {
                return 900_000;
            }
            if (move.equals(killerMoves[ply][1])) {
                return 800_000;
            }
        }

        int score = 0;
        int movedPiece = board.getSquare(move.from());
        int capturedPiece = board.getSquare(move.to());

        if (move.isPromotion()) {
            score += 10000 + Evaluator.PIECE_VALUES[move.promotionType()];
        }

        if (capturedPiece != Piece.EMPTY && Piece.getType(capturedPiece) != Piece.VOID) {
            score += 1000 + (Evaluator.PIECE_VALUES[Piece.getType(capturedPiece)] * 10)
                    - Evaluator.PIECE_VALUES[Piece.getType(movedPiece)];
        } else if (!move.isPromotion()) {
            int sideIdx = (Piece.getColor(movedPiece) >> 5) & 1;
            score += getHistoryScore(sideIdx, move.from(), move.to());
        }

        if (isSquareAttackedByPawn(board, move.to(), opponentPawnColor)) {
            score -= 50;
        }

        return score;
    }

    private boolean isSquareAttackedByPawn(Board board, int square, int pawnColor) {
        int backwardOffset = (pawnColor == Piece.WHITE) ? -9 : 9;
        int attackerSquare = square + backwardOffset;
        if (attackerSquare >= 0 && attackerSquare < 81) {
            int piece = board.getSquare(attackerSquare);
            return Piece.getType(piece) == Piece.PAWN && Piece.getColor(piece) == pawnColor;
        }
        return false;
    }

    private boolean isCapture(Board board, Move move) {
        int capturedPiece = board.getSquare(move.to());
        return capturedPiece != Piece.EMPTY && Piece.getType(capturedPiece) != Piece.VOID;
    }

    private boolean isKingCapture(Board board, Move move) {
        return Piece.getType(board.getSquare(move.to())) == Piece.KING;
    }

    private int captureGain(Board board, Move move) {
        int capturedType = Piece.getType(board.getSquare(move.to()));
        if (capturedType <= Piece.EMPTY || capturedType == Piece.VOID) {
            return 0;
        }
        return Evaluator.PIECE_VALUES[capturedType];
    }

    // ── Late Move Reduction helpers ──

    private boolean lmrEligible(int moveIndex, int depth, boolean isQuiet, boolean inCheck, int historyScore) {
        if (moveIndex <= LMR_MOVE_INDEX_THRESHOLD) return false;
        if (depth < LMR_MIN_DEPTH) return false;
        if (!isQuiet) return false;
        if (inCheck) return false;
        return historyScore <= 0;
    }

    // ── Null-Move Pruning helpers ──

    /**
     * Returns {@code true} if the given side has at least one piece on the board
     * that is neither a King nor a Pawn. Used as a zugzwang guard for NMP:
     * positions with only kings and pawns are prone to zugzwang and should
     * not attempt null-move pruning.
     */
    private boolean hasNonPawnMaterial(Board board, int color) {
        int[] squares = board.getPieceSquares(color);
        int count = board.getPieceCount(color);
        for (int i = 0; i < count; i++) {
            int type = Piece.getType(board.getSquare(squares[i]));
            if (type != Piece.KING && type != Piece.PAWN) {
                return true;
            }
        }
        return false;
    }
}
