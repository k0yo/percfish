package com.percfish.engine;

import java.util.List;
import java.util.function.Consumer;

public class Searcher {
    private static final int MATE_SCORE = 1_000_000;
    private static final int INFINITY = MATE_SCORE + 1_000;
    private static final int MAX_TIMED_DEPTH = 100;
    private static final long NO_DEADLINE = Long.MAX_VALUE;

    private final Evaluator evaluator = new Evaluator();
    private final MoveGenerator moveGenerator = new MoveGenerator();
    private final PositionHistory pathHistory = new PositionHistory();
    private final TranspositionTable tt = new TranspositionTable(64); // 64MB default

    private volatile boolean stop = false;

    public void stop() {
        stop = true;
    }

    public SearchResult search(Board board, int depth) {
        stop = false;
        return search(board, depth, NO_DEADLINE, null);
    }

    public SearchResult searchIterative(Board board, int maxDepth, long movetimeMs, Consumer<SearchResult> onDepthCompleted) {
        stop = false;
        tt.clear();
        long deadlineNanos = movetimeMs == NO_DEADLINE ? NO_DEADLINE : System.nanoTime() + Math.max(1L, movetimeMs) * 1_000_000L;
        List<Move> legalMoves = moveGenerator.generateLegalMoves(board);

        if (legalMoves.isEmpty()) {
            return new SearchResult(null, -MATE_SCORE, 0);
        }

        Move pvMove = null;
        orderMoves(board, legalMoves, pvMove, null);
        SearchResult bestCompletedResult = new SearchResult(legalMoves.getFirst(), evaluator.evaluate(board), 0);

        for (int depth = 1; depth <= maxDepth; depth++) {
            try {
                SearchResult result = search(board, depth, deadlineNanos, pvMove);
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

    private SearchResult search(Board board, int depth, long deadlineNanos, Move pvMoveHint) {
        checkTime(deadlineNanos);
        pathHistory.clear();
        pathHistory.record(board);

        int searchDepth = Math.max(1, depth);
        List<Move> legalMoves = moveGenerator.generateLegalMoves(board);

        if (legalMoves.isEmpty()) {
            return new SearchResult(null, -MATE_SCORE, searchDepth);
        }

        // Try to get PV move from TT for root move ordering
        Move ttMove = null;
        TranspositionTable.Entry entry = tt.probe(board.getZobristKey());
        if (entry != null) {
            ttMove = entry.bestMove();
        }

        orderMoves(board, legalMoves, pvMoveHint, ttMove);
        Move bestMove = null;
        int bestScore = -INFINITY;
        int alpha = -INFINITY;

        for (Move move : legalMoves) {
            MoveState state = board.makeMove(move);
            int score;

            try {
                score = -negamax(board, searchDepth - 1, 1, -INFINITY, -alpha, deadlineNanos);
            } finally {
                board.unmakeMove(state);
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

            alpha = Math.max(alpha, bestScore);
        }

        // Store root result in TT
        tt.store(board.getZobristKey(), bestScore, searchDepth, TranspositionTable.EXACT, bestMove);

        return new SearchResult(bestMove, bestScore, searchDepth);
    }

    private int negamax(Board board, int depth, int ply, int alpha, int beta, long deadlineNanos) {
        checkTime(deadlineNanos);

        if (pathHistory.getCount(board) >= 2) {
            return 0;
        }

        long key = board.getZobristKey();
        TranspositionTable.Entry entry = tt.probe(key);
        Move ttMove = null;

        if (entry != null && entry.depth() >= depth) {
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
            return evaluator.evaluate(board);
        }

        List<Move> legalMoves = moveGenerator.generateLegalMoves(board);

        if (legalMoves.isEmpty()) {
            return -MATE_SCORE + ply;
        }

        orderMoves(board, legalMoves, null, ttMove);
        int bestScore = -INFINITY;
        Move bestMove = null;
        int originalAlpha = alpha;

        for (Move move : legalMoves) {
            MoveState state = board.makeMove(move);
            pathHistory.record(board);
            int score;

            try {
                score = -negamax(board, depth - 1, ply + 1, -beta, -alpha, deadlineNanos);
            } finally {
                pathHistory.unrecord(board);
                board.unmakeMove(state);
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

            alpha = Math.max(alpha, score);

            if (alpha >= beta) {
                break;
            }
        }

        int flag = TranspositionTable.EXACT;
        if (bestScore <= originalAlpha) flag = TranspositionTable.UPPER_BOUND;
        else if (bestScore >= beta) flag = TranspositionTable.LOWER_BOUND;

        tt.store(key, bestScore, depth, flag, bestMove);

        return bestScore;
    }

    private void checkTime(long deadlineNanos) {
        if (stop || System.nanoTime() >= deadlineNanos) {
            throw new SearchStoppedException();
        }
    }

    private void orderMoves(Board board, List<Move> moves, Move pvMove, Move ttMove) {
        int[] scores = new int[moves.size()];
        int opponentPawnColor = board.isWhiteToMove ? Piece.BLACK : Piece.WHITE;

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            int score = 0;

            if (move.equals(pvMove)) {
                score = 2_000_000;
            } else if (move.equals(ttMove)) {
                score = 1_000_000;
            } else {
                int movedPiece = board.getSquare(move.from());
                int capturedPiece = board.getSquare(move.to());

                // Promotions
                if (move.isPromotion()) {
                    score += 10000 + Evaluator.PIECE_VALUES[move.promotionType()];
                }

                // Captures via MVV-LVA
                if (capturedPiece != Piece.EMPTY && Piece.getType(capturedPiece) != Piece.VOID) {
                    score += 1000 + (Evaluator.PIECE_VALUES[Piece.getType(capturedPiece)] * 10) - Evaluator.PIECE_VALUES[Piece.getType(movedPiece)];
                }

                // Penalize any move whose destination square is attacked by an opponent pawn
                if (isSquareAttackedByPawn(board, move.to(), opponentPawnColor)) {
                    score -= 50;
                }
            }

            scores[i] = score;
        }

        for (int i = 0; i < moves.size() - 1; i++) {
            int bestIndex = i;
            for (int j = i + 1; j < moves.size(); j++) {
                if (scores[j] > scores[bestIndex]) {
                    bestIndex = j;
                }
            }
            if (bestIndex != i) {
                Move tempMove = moves.get(i);
                moves.set(i, moves.get(bestIndex));
                moves.set(bestIndex, tempMove);

                int tempScore = scores[i];
                scores[i] = scores[bestIndex];
                scores[bestIndex] = tempScore;
            }
        }
    }

    private boolean isSquareAttackedByPawn(Board board, int square, int pawnColor) {
        int backwardOffset = (pawnColor == Piece.WHITE) ? -9 : 9;
        int attackerSquare = square + backwardOffset;
        if (attackerSquare >= 0 && attackerSquare < 81) {
            int piece = board.getSquare(attackerSquare);
            if (Piece.getType(piece) == Piece.PAWN && Piece.getColor(piece) == pawnColor) {
                return true;
            }
        }
        return false;
    }

    public record SearchResult(Move bestMove, int score, int depth) {
    }

    private static class SearchStoppedException extends RuntimeException {
    }
}
