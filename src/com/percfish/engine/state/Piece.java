package com.percfish.engine.state;

public final class Piece {
    // Types
    public static final int EMPTY = 0;
    public static final int KING = 1;
    public static final int PAWN = 2;
    public static final int KNIGHT = 3;
    public static final int BISHOP = 4;
    public static final int CANNON = 5;
    public static final int FALCON = 6;
    public static final int HUNTER = 7;
    public static final int ROOK = 8;
    public static final int D_HORSE = 9;
    public static final int D_KING = 10;
    public static final int ECHO = 11;
    public static final int VOID = 15;

    // Colors
    public static final int WHITE = 16;
    public static final int BLACK = 32;

    // Masks for bitwise logic
    public static final int TYPE_MASK = 15;
    public static final int COLOR_MASK = 48;

    public static int getType(int piece) { return piece & TYPE_MASK; }
    public static int getColor(int piece) { return piece & COLOR_MASK; }

    public static boolean isSlider(int piece) {
        int type = getType(piece);
        return switch (type) {
            case Piece.BISHOP, Piece.CANNON, Piece.FALCON, Piece.HUNTER, Piece.ROOK,
                 Piece.D_HORSE, Piece.D_KING -> true;
            default -> false;
        };
    }
}
