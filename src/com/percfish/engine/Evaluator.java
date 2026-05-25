package com.percfish.engine;

public class Evaluator {
    private static final int TEMPO_BONUS = 10;
    private static final int MOBILITY_BONUS_PER_MOVE = 2;
    private static final int[] PIECE_VALUES = new int[16];

    static {
        PIECE_VALUES[Piece.PAWN] = 100;
        PIECE_VALUES[Piece.KNIGHT] = 400;
        PIECE_VALUES[Piece.BISHOP] = 325;
        PIECE_VALUES[Piece.CANNON] = 350;
        PIECE_VALUES[Piece.FALCON] = 375;
        PIECE_VALUES[Piece.HUNTER] = 375;
        PIECE_VALUES[Piece.ROOK] = 650;
        PIECE_VALUES[Piece.D_HORSE] = 690;
        PIECE_VALUES[Piece.D_KING] = 750;
        PIECE_VALUES[Piece.ECHO] = 350;
    }

    public int evaluate(Board board) {
        int score = evaluateWhitePerspective(board);
        score += board.isWhiteToMove ? TEMPO_BONUS : -TEMPO_BONUS;
        return board.isWhiteToMove ? score : -score;
    }

    public int evaluateWhitePerspective(Board board) {
        int score = 0;

        for (int i = 0; i < 81; i++) {
            int piece = board.getSquare(i);
            int type = Piece.getType(piece);

            if (piece == Piece.EMPTY || type == Piece.VOID || type == Piece.KING) {
                continue;
            }

            int value = PIECE_VALUES[type];

            if (Piece.getColor(piece) == Piece.WHITE) {
                score += value;
            } else {
                score -= value;
            }
        }

        return score + evaluateMobilityWhitePerspective(board);
    }

    private int evaluateMobilityWhitePerspective(Board board) {
        MoveGenerator moveGenerator = new MoveGenerator();
        boolean oldWhiteToMove = board.isWhiteToMove;

        board.isWhiteToMove = true;
        int whiteMobility = moveGenerator.generateLegalMoves(board).size();

        board.isWhiteToMove = false;
        int blackMobility = moveGenerator.generateLegalMoves(board).size();

        board.isWhiteToMove = oldWhiteToMove;

        return (whiteMobility - blackMobility) * MOBILITY_BONUS_PER_MOVE;
    }
}
