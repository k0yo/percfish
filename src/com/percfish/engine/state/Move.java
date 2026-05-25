package com.percfish.engine.state;

public record Move(int from, int to, int promotionType) {
    public Move(int from, int to) {
        this(from, to, Piece.EMPTY);
    }

    public boolean isPromotion() {
        return promotionType != Piece.EMPTY;
    }

    public static Move fromString(String move) {
        if (move.length() != 4 && move.length() != 5) {
            throw new IllegalArgumentException("Invalid move: " + move);
        }

        int from = fromAlgebraic(move.substring(0, 2));
        int to = fromAlgebraic(move.substring(2, 4));
        int promotionType = move.length() == 5 ? promotionTypeFromChar(move.charAt(4)) : Piece.EMPTY;

        return new Move(from, to, promotionType);
    }

    @Override
    public String toString() {
        String move = toAlgebraic(from) + toAlgebraic(to);
        return isPromotion() ? move + promotionChar(promotionType) : move;
    }

    private static String toAlgebraic(int index) {
        int file = index % 9;
        int rank = index / 9;
        return "" + (char) ('a' + file) + (rank + 1);
    }

    private static int fromAlgebraic(String square) {
        int file = square.charAt(0) - 'a';
        int rank = square.charAt(1) - '1';

        if (file < 0 || file >= 9 || rank < 0 || rank >= 9) {
            throw new IllegalArgumentException("Invalid square: " + square);
        }

        return rank * 9 + file;
    }

    private static char promotionChar(int promotionType) {
        return switch (promotionType) {
            case Piece.KNIGHT -> 'n';
            case Piece.BISHOP -> 'b';
            case Piece.ROOK -> 'r';
            case Piece.CANNON -> 'c';
            case Piece.FALCON -> 'f';
            case Piece.HUNTER -> 'h';
            case Piece.D_HORSE -> 'd';
            case Piece.D_KING -> 'x';
            default -> '?';
        };
    }

    private static int promotionTypeFromChar(char c) {
        return switch (Character.toLowerCase(c)) {
            case 'n' -> Piece.KNIGHT;
            case 'b' -> Piece.BISHOP;
            case 'r' -> Piece.ROOK;
            case 'c' -> Piece.CANNON;
            case 'f' -> Piece.FALCON;
            case 'h' -> Piece.HUNTER;
            case 'd' -> Piece.D_HORSE;
            case 'x' -> Piece.D_KING;
            default -> throw new IllegalArgumentException("Invalid promotion piece: " + c);
        };
    }
}
