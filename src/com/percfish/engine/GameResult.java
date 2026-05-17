package com.percfish.engine;

public enum GameResult {
    ONGOING,
    WHITE_WINS,
    BLACK_WINS,
    DRAW;

    public boolean isTerminal() {
        return this != ONGOING;
    }
}
