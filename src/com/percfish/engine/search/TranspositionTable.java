package com.percfish.engine.search;

import com.percfish.engine.state.Move;

public class TranspositionTable {
    public static final int EXACT = 0;
    public static final int LOWER_BOUND = 1;
    public static final int UPPER_BOUND = 2;

    private final TTEntry[] table;
    private final int size;

    public TranspositionTable(int sizeMb) {
        // Roughly each entry is: 8 (long) + 4 (int) + 4 (int) + 4 (int) + reference (8) = 28 bytes
        // Let's use 32 bytes as a safe estimate per entry.
        int entries = (sizeMb * 1024 * 1024) / 32;
        // Find largest power of 2 for entries to use bitmask
        this.size = Integer.highestOneBit(entries);
        this.table = new TTEntry[size];
    }

    private int getIndex(long key) {
        return (int) (key & (size - 1));
    }

    public void store(long key, int score, int depth, int flag, Move bestMove) {
        int index = getIndex(key);
        // Replacement strategy: always replace for now, or replace if deeper depth
        if (table[index] == null || depth >= table[index].depth()) {
            table[index] = new TTEntry(key, score, depth, flag, bestMove);
        }
    }

    public TTEntry probe(long key) {
        int index = getIndex(key);
        TTEntry entry = table[index];
        if (entry != null && entry.key() == key) {
            return entry;
        }
        return null;
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            table[i] = null;
        }
    }
}
