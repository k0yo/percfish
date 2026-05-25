package com.percfish.engine.evaluation;

import com.percfish.engine.state.Board;
import com.percfish.engine.state.MoveGenerator;
import com.percfish.engine.state.Piece;

public class Evaluator {
    private static final int TEMPO_BONUS = 10;
    private static final int MOBILITY_BONUS_PER_MOVE = 2;
    public static final int[] PIECE_VALUES = new int[16];
    private final MoveGenerator moveGenerator = new MoveGenerator();

    static {
        PIECE_VALUES[Piece.PAWN] = 100;
        PIECE_VALUES[Piece.KNIGHT] = 400;
        PIECE_VALUES[Piece.BISHOP] = 370;
        PIECE_VALUES[Piece.CANNON] = 480;
        PIECE_VALUES[Piece.FALCON] = 450;
        PIECE_VALUES[Piece.HUNTER] = 450;
        PIECE_VALUES[Piece.ROOK] = 670;
        PIECE_VALUES[Piece.D_HORSE] = 700;
        PIECE_VALUES[Piece.D_KING] = 750;
        PIECE_VALUES[Piece.ECHO] = 350;
    }

    public int evaluate(Board board) {
        int score = evaluateWhitePerspective(board);
        score += evaluateMobilityWhitePerspective(board);
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

        return score;
    }

    private int evaluateMobilityWhitePerspective(Board board) {
        boolean oldWhiteToMove = board.isWhiteToMove;

        board.isWhiteToMove = true;
        int whiteMobility = moveGenerator.generatePseudoLegalMoves(board).size();

        board.isWhiteToMove = false;
        int blackMobility = moveGenerator.generatePseudoLegalMoves(board).size();

        board.isWhiteToMove = oldWhiteToMove;

        return (whiteMobility - blackMobility) * MOBILITY_BONUS_PER_MOVE;
    }
}
