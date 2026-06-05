package com.percfish.engine.evaluation;

import com.percfish.engine.state.Board;
import com.percfish.engine.state.Piece;

public class Evaluator {
    private static final int TEMPO_BONUS = 10;
    public static final int[] PIECE_VALUES = new int[16];

    private static final int[] PHASE_WEIGHTS = new int[16];
    private static final int MAX_PHASE = 24; // 24 = pure middle-game

    // Mop-up parameters
    private static final int MOPUP_MATERIAL_THRESHOLD = 300;
    private static final int MOPUP_EDGE_MULTIPLIER = 12;
    private static final int MOPUP_PROXIMITY_MULTIPLIER = 8;

    private static final int CENTER_SQUARE = 40;

    static {
        PIECE_VALUES[Piece.PAWN] = 100;
        PIECE_VALUES[Piece.KNIGHT] = 314;
        PIECE_VALUES[Piece.BISHOP] = 408;
        PIECE_VALUES[Piece.CANNON] = 203;
        PIECE_VALUES[Piece.FALCON] = 279;
        PIECE_VALUES[Piece.HUNTER] = 296;
        PIECE_VALUES[Piece.ROOK] = 720;
        PIECE_VALUES[Piece.D_HORSE] = 609;
        PIECE_VALUES[Piece.D_KING] = 816;
        PIECE_VALUES[Piece.ECHO] = 530;

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

        // White pieces (PSTs are from Black's perspective, so we mirror for White)
        int[] whiteSquares = board.getPieceSquares(Piece.WHITE);
        int whiteCount = board.getPieceCount(Piece.WHITE);
        for (int i = 0; i < whiteCount; i++) {
            int sq = whiteSquares[i];
            int piece = board.getSquare(sq);
            int type = Piece.getType(piece);
            int mirrorSq = PST.mirrorSquare(sq);
            int val = PIECE_VALUES[type];
            score_mg += val + PST.mg(type, mirrorSq);
            score_eg += val + PST.eg(type, mirrorSq);
            materialWhite += val;
            if (type != Piece.KING) {
                currentPhase += PHASE_WEIGHTS[type];
            }
        }

        // Black pieces (PSTs are from Black's perspective — no mirroring needed)
        int[] blackSquares = board.getPieceSquares(Piece.BLACK);
        int blackCount = board.getPieceCount(Piece.BLACK);
        for (int i = 0; i < blackCount; i++) {
            int sq = blackSquares[i];
            int piece = board.getSquare(sq);
            int type = Piece.getType(piece);
            int val = PIECE_VALUES[type];
            score_mg -= val + PST.mg(type, sq);
            score_eg -= val + PST.eg(type, sq);
            materialBlack += val;
            if (type != Piece.KING) {
                currentPhase += PHASE_WEIGHTS[type];
            }
        }

        // Tempo bonus (MG)
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