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

    private final Evaluator evaluator = new Evaluator();
    private final MoveGenerator moveGenerator = new MoveGenerator();
    private final PositionHistory pathHistory = new PositionHistory();
    private final TranspositionTable tt = new TranspositionTable(64); // 64MB default

    // killerMoves[ply][slot] stores quiet move that caused a beta cutoff at this ply level.
    // Slot 0 = most recent killer, Slot 1 = second most recent.
    private final Move[][] killerMoves = new Move[MAX_KILLER_PLY][KILLER_SLOTS];

    private volatile boolean stop = false;
    private long nodes;
    private long ttHits;

    public void stop() {
        stop = true;
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
        tt.clear();
        clearKillerMoves();
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
        nodes = 0;
        ttHits = 0;
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
        tt.store(board.getZobristKey(), bestScore, searchDepth, TranspositionTable.EXACT, bestMove);

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
            // Adjust mate scores for distance from root
            if (ttScore > MATE_SCORE - 1000) ttScore -= ply;
            else if (ttScore < -MATE_SCORE + 1000) ttScore += ply;

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

        if (depth == 0) {
            return quiescence(board, ply, 0, alpha, beta, deadlineNanos);
        }

        int movingColor = board.isWhiteToMove ? Piece.WHITE : Piece.BLACK;
        List<Move> pseudoLegalMoves = moveGenerator.generatePseudoLegalMoves(board);
        if (pseudoLegalMoves.isEmpty()) {
            return -MATE_SCORE + ply;
        }

        orderMoves(board, pseudoLegalMoves, null, ttMove, ply);
        int bestScore = -INFINITY;
        Move bestMove = null;
        int originalAlpha = alpha;

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
                score = -negamax(board, depth - 1, ply + 1, -beta, -alpha, deadlineNanos);
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
                }
                break;
            }
        }

        if (bestMove == null) {
            return -MATE_SCORE + ply;
        }

        int flag = TranspositionTable.EXACT;
        if (bestScore <= originalAlpha) flag = TranspositionTable.UPPER_BOUND;
        else if (bestScore >= beta) flag = TranspositionTable.LOWER_BOUND;

        tt.store(key, bestScore, depth, flag, bestMove);

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
            if (!inCheck && isCapture && standPat + captureGain(board, move) + DELTA_PRUNING_MARGIN < alpha) {
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

    private void orderMoves(Board board, List<Move> moves, Move pvMove, Move ttMove) {
        orderMoves(board, moves, pvMove, ttMove, 0);
    }

    private void orderMoves(Board board, List<Move> moves, Move pvMove, Move ttMove, int ply) {
        int opponentPawnColor = board.isWhiteToMove ? Piece.BLACK : Piece.WHITE;

        for (int i = 0; i < moves.size() - 1; i++) {
            int bestIndex = i;
            int bestScore = scoreMove(board, moves.get(i), pvMove, ttMove, opponentPawnColor, ply);
            for (int j = i + 1; j < moves.size(); j++) {
                int score = scoreMove(board, moves.get(j), pvMove, ttMove, opponentPawnColor, ply);
                if (score > bestScore) {
                    bestIndex = j;
                    bestScore = score;
                }
            }
            if (bestIndex != i) {
                Move tempMove = moves.get(i);
                moves.set(i, moves.get(bestIndex));
                moves.set(bestIndex, tempMove);
            }
        }
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
}
