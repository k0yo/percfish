package com.percfish.engine.evaluation;

import com.percfish.engine.state.Board;
import com.percfish.engine.state.MoveGenerator;
import com.percfish.engine.state.Piece;

public class Evaluator {
    private static final int TEMPO_BONUS = 10;
    private static final int MOBILITY_BONUS_PER_MOVE = 2;
    public static final int[] PIECE_VALUES = new int[16];

    private static final int[] PHASE_WEIGHTS = new int[16];
    private static final int MAX_PHASE = 24;

    // Mop-up parameters
    private static final int MOPUP_MATERIAL_THRESHOLD = 300;
    private static final int MOPUP_EDGE_MULTIPLIER = 12;
    private static final int MOPUP_PROXIMITY_MULTIPLIER = 8;

    private static final int CENTER_SQUARE = 40; // e5

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

        PHASE_WEIGHTS[Piece.KNIGHT] = 1;
        PHASE_WEIGHTS[Piece.BISHOP] = 1;
        PHASE_WEIGHTS[Piece.FALCON] = 2;
        PHASE_WEIGHTS[Piece.HUNTER] = 2;
        PHASE_WEIGHTS[Piece.ECHO] = 2;
        PHASE_WEIGHTS[Piece.CANNON] = 3;
        PHASE_WEIGHTS[Piece.D_HORSE] = 3;
        PHASE_WEIGHTS[Piece.ROOK] = 4;
        PHASE_WEIGHTS[Piece.D_KING] = 4;
    }

    public int evaluate(Board board) {
        int score = evaluateWhitePerspective(board);
        return board.isWhiteToMove ? score : -score;
    }

    public int evaluateWhitePerspective(Board board) {
        int score_mg = 0;
        int score_eg = 0;
        int currentPhase = 0;
        int materialWhite = 0;
        int materialBlack = 0;

        // White pieces
        int[] whiteSquares = board.getPieceSquares(Piece.WHITE);
        int whiteCount = board.getPieceCount(Piece.WHITE);
        for (int i = 0; i < whiteCount; i++) {
            int piece = board.getSquare(whiteSquares[i]);
            int type = Piece.getType(piece);
            if (type != Piece.KING) {
                int val = PIECE_VALUES[type];
                score_mg += val;
                score_eg += val;
                materialWhite += val;
                currentPhase += PHASE_WEIGHTS[type];
            }
        }

        // Black pieces
        int[] blackSquares = board.getPieceSquares(Piece.BLACK);
        int blackCount = board.getPieceCount(Piece.BLACK);
        for (int i = 0; i < blackCount; i++) {
            int piece = board.getSquare(blackSquares[i]);
            int type = Piece.getType(piece);
            if (type != Piece.KING) {
                int val = PIECE_VALUES[type];
                score_mg -= val;
                score_eg -= val;
                materialBlack += val;
                currentPhase += PHASE_WEIGHTS[type];
            }
        }

        // Mobility bonus (MG only)
        boolean oldWhiteToMove = board.isWhiteToMove;

        board.isWhiteToMove = true;
        int whiteMobility = moveGenerator.generatePseudoLegalMoves(board).size();

        board.isWhiteToMove = false;
        int blackMobility = moveGenerator.generatePseudoLegalMoves(board).size();

        board.isWhiteToMove = oldWhiteToMove;

        score_mg += (whiteMobility - blackMobility) * MOBILITY_BONUS_PER_MOVE;

        // Tempo bonus (MG))
        score_mg += board.isWhiteToMove ? TEMPO_BONUS : -TEMPO_BONUS;

        // Mop-Up heuristic (EG)
        int matDiff = materialWhite - materialBlack;
        if (Math.abs(matDiff) >= MOPUP_MATERIAL_THRESHOLD) {
            score_eg += mopUpBonus(board, materialWhite, materialBlack);
        }

        // Phase blending
        double scale = Math.min(currentPhase, MAX_PHASE) / (double) MAX_PHASE;
        return (int) (scale * score_mg + (1.0 - scale) * score_eg);
    }

    /**
     * Computes a mop-up bonus for the dominant side, feeding into the EG accumulator.
     * Returns a positive score from White's perspective if White is winning.
     */
    private int mopUpBonus(Board board, int materialWhite, int materialBlack) {
        boolean whiteWinning = materialWhite > materialBlack;
        int winningColor = whiteWinning ? Piece.WHITE : Piece.BLACK;
        int losingColor = whiteWinning ? Piece.BLACK : Piece.WHITE;

        int losingKing = board.findKing(losingColor);
        int winningKing = board.findKing(winningColor);

        // (a) Push enemy king to edge: bonus proportional to Manhattan distance from e5
        int edgeBonus = manhattanDistance(losingKing, CENTER_SQUARE) * MOPUP_EDGE_MULTIPLIER;

        // (b) March winning king closer: bonus proportional to king-king proximity
        int kingDist = chebyshevDistance(winningKing, losingKing);
        int proximityBonus = Math.max(0, 8 - kingDist) * MOPUP_PROXIMITY_MULTIPLIER;

        int total = edgeBonus + proximityBonus;
        return whiteWinning ? total : -total;
    }

    // --- Coordinate helpers ---

    private static int rank(int square) {
        return square / 9;
    }

    private static int file(int square) {
        return square % 9;
    }

    private static int manhattanDistance(int a, int b) {
        return Math.abs(rank(a) - rank(b)) + Math.abs(file(a) - file(b));
    }

    private static int chebyshevDistance(int a, int b) {
        return Math.max(Math.abs(rank(a) - rank(b)), Math.abs(file(a) - file(b)));
    }
}