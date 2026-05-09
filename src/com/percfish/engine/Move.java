package com.percfish.engine;

public record Move(int from, int to) {
    @Override
    public String toString() {
        return toAlgebraic(from) + toAlgebraic(to);
    }

    public String toAlgebraic(int index) {
        int file = index % 9;
        int rank = index / 9;
        return "" + (char) ('a' + file) + (rank + 1);
    }
}
