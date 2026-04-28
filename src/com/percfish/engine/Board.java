package com.percfish.engine;

public class Board {
    public static final String START_PFEN = "r1n1k1n1r/1h1cec1f1/ppp1b1ppp/4b4/3vvv3/4B4/PPP1B1PPP/1F1CEC1H1/R1N1K1N1R w - - 0 1 -";

    private final int[] squares;
    private boolean whiteToMove;
    private int echoPower;
    private final boolean[] isVoid;

    public Board() {
        this.squares = new int[81];
        this.whiteToMove = true;
        this.echoPower = Piece.EMPTY;
        this.isVoid = new boolean[81];

        // Void squares (d5, e5, f5)
        isVoid[40] = true; // d5
        isVoid[41] = true; // e5
        isVoid[42] = true; // f5
    }

    public boolean isVoid(int index) {
        return isVoid[index];
    }

    public void loadPfen(String pfen) {
        String[] parts = pfen.split(" ");
        String placement = parts[0];

        for (int i = 1; i < 81; i++) squares[i] = Piece.EMPTY;

        String[] ranks = placement.split("/");
        int currentRank = 8;
    }

    public void makeMove(String move) {
        // Move execution and echoPower update
    }
}