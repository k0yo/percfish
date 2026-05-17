package com.percfish.engine;

import java.util.HashMap;
import java.util.Map;

public class PositionHistory {
    private final Map<String, Integer> repetitionCounts = new HashMap<>();

    public void clear() {
        repetitionCounts.clear();
    }

    public void record(Board board) {
        String key = board.repetitionKey();
        repetitionCounts.put(key, getCount(board) + 1);
    }

    public void unrecord(Board board) {
        String key = board.repetitionKey();
        int count = getCount(board);

        if (count <= 1) {
            repetitionCounts.remove(key);
        } else {
            repetitionCounts.put(key, count - 1);
        }
    }

    public int getCount(Board board) {
        return repetitionCounts.getOrDefault(board.repetitionKey(), 0);
    }

    public boolean isThreefoldRepetition(Board board) {
        return getCount(board) >= 3;
    }
}
