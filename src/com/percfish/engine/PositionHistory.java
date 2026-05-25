package com.percfish.engine;

import java.util.Arrays;

public class PositionHistory {
    private static final long EMPTY = 0L;

    private long[] keys = new long[1024];
    private int[] counts = new int[1024];
    private int size = 0;

    public void clear() {
        Arrays.fill(keys, EMPTY);
        Arrays.fill(counts, 0);
        size = 0;
    }

    public void record(Board board) {
        record(board.repetitionKey());
    }

    public void record(long key) {
        ensureCapacity();
        int index = findSlot(key);
        if (counts[index] == 0) {
            keys[index] = normalizeKey(key);
            size++;
        }
        counts[index]++;
    }

    public void unrecord(Board board) {
        unrecord(board.repetitionKey());
    }

    public void unrecord(long key) {
        int index = findSlot(key);
        int count = counts[index];
        if (count <= 1) {
            removeAt(index);
        } else {
            counts[index] = count - 1;
        }
    }

    public int getCount(Board board) {
        return getCount(board.repetitionKey());
    }

    public int getCount(long key) {
        int index = findSlot(key);
        return counts[index];
    }

    public boolean isThreefoldRepetition(Board board) {
        return getCount(board) >= 3;
    }

    private void ensureCapacity() {
        if (size * 2 < keys.length) {
            return;
        }
        long[] oldKeys = keys;
        int[] oldCounts = counts;
        keys = new long[oldKeys.length << 1];
        counts = new int[oldCounts.length << 1];
        size = 0;

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldCounts[i] > 0) {
                long originalKey = denormalizeKey(oldKeys[i]);
                int index = findSlot(originalKey);
                keys[index] = normalizeKey(originalKey);
                counts[index] = oldCounts[i];
                size++;
            }
        }
    }

    private int findSlot(long key) {
        long normalized = normalizeKey(key);
        int mask = keys.length - 1;
        int index = mix(normalized) & mask;
        while (counts[index] > 0 && keys[index] != normalized) {
            index = (index + 1) & mask;
        }
        return index;
    }

    private void removeAt(int removeIndex) {
        if (counts[removeIndex] == 0) {
            return;
        }
        counts[removeIndex] = 0;
        keys[removeIndex] = EMPTY;
        size--;

        int mask = keys.length - 1;
        int index = (removeIndex + 1) & mask;
        while (counts[index] > 0) {
            long keyToRehash = denormalizeKey(keys[index]);
            int countToRehash = counts[index];
            counts[index] = 0;
            keys[index] = EMPTY;
            size--;

            int target = findSlot(keyToRehash);
            keys[target] = normalizeKey(keyToRehash);
            counts[target] = countToRehash;
            size++;
            index = (index + 1) & mask;
        }
    }

    private static long normalizeKey(long key) {
        return key == EMPTY ? Long.MIN_VALUE : key;
    }

    private static long denormalizeKey(long stored) {
        return stored == Long.MIN_VALUE ? EMPTY : stored;
    }

    private static int mix(long x) {
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return (int) x;
    }
}
