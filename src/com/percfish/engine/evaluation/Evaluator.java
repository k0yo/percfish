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
        PIECE_VALUES[Piece.BISHOP] = 400;
        PIECE_VALUES[Piece.CANNON] = 550;
        PIECE_VALUES[Piece.FALCON] = 450;
        PIECE_VALUES[Piece.HUNTER] = 450;
        PIECE_VALUES[Piece.ROOK] = 750;
        PIECE_VALUES[Piece.D_HORSE] = 700;
        PIECE_VALUES[Piece.D_KING] = 780;
        PIECE_VALUES[Piece.ECHO] = 400;
    }

    public int evaluate(Board board) {
        int score = evaluateWhitePerspective(board);
        return board.isWhiteToMove ? score : -score;
    }

    public int evaluateWhitePerspective(Board board) {
        int score = 0;

        // White pieces
        int[] whiteSquares = board.getPieceSquares(Piece.WHITE);
        int whiteCount = board.getPieceCount(Piece.WHITE);
        for (int i = 0; i < whiteCount; i++) {
            int piece = board.getSquare(whiteSquares[i]);
            int type = Piece.getType(piece);
            if (type != Piece.KING) {
                score += PIECE_VALUES[type];
            }
        }

        // Black pieces
        int[] blackSquares = board.getPieceSquares(Piece.BLACK);
        int blackCount = board.getPieceCount(Piece.BLACK);
        for (int i = 0; i < blackCount; i++) {
            int piece = board.getSquare(blackSquares[i]);
            int type = Piece.getType(piece);
            if (type != Piece.KING) {
                score -= PIECE_VALUES[type];
            }
        }

        // Mobility bonus
        boolean oldWhiteToMove = board.isWhiteToMove;

        board.isWhiteToMove = true;
        int whiteMobility = moveGenerator.generatePseudoLegalMoves(board).size();

        board.isWhiteToMove = false;
        int blackMobility = moveGenerator.generatePseudoLegalMoves(board).size();

        board.isWhiteToMove = oldWhiteToMove;

        score += (whiteMobility - blackMobility) * MOBILITY_BONUS_PER_MOVE;

        // Tempo bonus
        score += board.isWhiteToMove ? TEMPO_BONUS : -TEMPO_BONUS;

        return score;
    }
}