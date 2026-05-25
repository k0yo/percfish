package com.percfish.engine.state;

import java.util.Random;

public class Zobrist {
    private static final long[][] PIECE_KEYS = new long[81][64]; // 81 squares, max 64 piece bit-combos
    private static final long SIDE_KEY;
    private static final long[] ECHO_POWER_KEYS = new long[16]; // 16 possible piece types for echo power

    static {
        Random random = new Random(67); // Seed for reproducibility

        for (int i = 0; i < 81; i++) {
            for (int j = 0; j < 64; j++) {
                PIECE_KEYS[i][j] = random.nextLong();
            }
        }

        SIDE_KEY = random.nextLong();

        for (int i = 0; i < 16; i++) {
            ECHO_POWER_KEYS[i] = random.nextLong();
        }
    }

    public static long getPieceKey(int square, int piece) {
        return PIECE_KEYS[square][piece];
    }

    public static long getSideKey() {
        return SIDE_KEY;
    }

    public static long getEchoPowerKey(int type) {
        return ECHO_POWER_KEYS[type];
    }

    public static long calculateKey(Board board) {
        long key = 0;

        for (int i = 0; i < 81; i++) {
            int piece = board.getSquare(i);
            if (piece != Piece.EMPTY) {
                key ^= getPieceKey(i, piece);
            }
        }

        if (board.isWhiteToMove) {
            key ^= getSideKey();
        }

        key ^= getEchoPowerKey(board.getEchoPower());

        return key;
    }
}
