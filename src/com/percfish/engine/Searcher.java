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

    private volatile boolean stop = false;

    public void stop() {
        stop = true;
    }

    public SearchResult search(Board board, int depth) {
        stop = false;
        return search(board, depth, NO_DEADLINE);
    }

    public SearchResult searchIterative(Board board, int maxDepth, long movetimeMs, Consumer<SearchResult> onDepthCompleted) {
        stop = false;
        long deadlineNanos = movetimeMs == NO_DEADLINE ? NO_DEADLINE : System.nanoTime() + Math.max(1L, movetimeMs) * 1_000_000L;
        List<Move> legalMoves = moveGenerator.generateLegalMoves(board);

        if (legalMoves.isEmpty()) {
            return new SearchResult(null, -MATE_SCORE, 0);
        }

        orderMoves(board, legalMoves);
        SearchResult bestCompletedResult = new SearchResult(legalMoves.getFirst(), evaluator.evaluate(board), 0);

        for (int depth = 1; depth <= maxDepth; depth++) {
            try {
                SearchResult result = search(board, depth, deadlineNanos);
                bestCompletedResult = result;
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

    private SearchResult search(Board board, int depth, long deadlineNanos) {
        checkTime(deadlineNanos);
        pathHistory.clear();
        pathHistory.record(board);

        int searchDepth = Math.max(1, depth);
        List<Move> legalMoves = moveGenerator.generateLegalMoves(board);

        if (legalMoves.isEmpty()) {
            return new SearchResult(null, -MATE_SCORE, searchDepth);
        }

        orderMoves(board, legalMoves);
        Move bestMove = null;
        int bestScore = Integer.MIN_VALUE;
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

        return new SearchResult(bestMove, bestScore, searchDepth);
    }

    private int negamax(Board board, int depth, int ply, int alpha, int beta, long deadlineNanos) {
        checkTime(deadlineNanos);

        if (pathHistory.getCount(board) >= 2) {
            return 0;
        }

        if (depth == 0) {
            return evaluator.evaluate(board);
        }

        List<Move> legalMoves = moveGenerator.generateLegalMoves(board);

        if (legalMoves.isEmpty()) {
            return -MATE_SCORE + ply;
        }

        orderMoves(board, legalMoves);
        int bestScore = Integer.MIN_VALUE;

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
            }

            alpha = Math.max(alpha, score);

            if (alpha >= beta) {
                break;
            }
        }

        return bestScore;
    }

    private void checkTime(long deadlineNanos) {
        if (stop || System.nanoTime() >= deadlineNanos) {
            throw new SearchStoppedException();
        }
    }

    private void orderMoves(Board board, List<Move> moves) {
        int[] scores = new int[moves.size()];
        int opponentPawnColor = board.isWhiteToMove ? Piece.BLACK : Piece.WHITE;

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            int score = 0;
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
