package com.percfish.engine;

import java.util.List;

/**
 * Holds all configurable UCI engine options.
 * Thread-safe via volatile fields; setoption is called from the main thread
 * before any search begins, so no synchronisation is needed at runtime.
 */
public class EngineOptions {
    // ── Option definitions (metadata for the uci command) ──
    public record OptionDef(String name, String type, String defaultValue, int min, int max) {
        @Override
        public String toString() {
            return "option name " + name + " type " + type
                    + " default " + defaultValue
                    + " min " + min + " max " + max;
        }
    }

    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    private static final List<OptionDef> OPTIONS = List.of(
            new OptionDef("Hash",    "spin", "64",   1, 16384),
            new OptionDef("Threads", "spin", "1",    1, MAX_THREADS),
            new OptionDef("MultiPV", "spin", "1",    1, 256)
    );

    public static List<OptionDef> getOptionDefs() {
        return OPTIONS;
    }

    // ── Mutable values ──
    private volatile int hash = 64;
    private volatile int threads = 1;
    private volatile int multiPV = 1;

    public int hash()      { return hash; }
    public int threads()   { return threads; }
    public int multiPV()   { return multiPV; }

    /**
     * Parses a UCI setoption command and updates the corresponding field.
     * Silently ignores unknown options.
     */
    public void set(String name, String value) {
        switch (name) {
            case "Hash"    -> hash    = clamp(Integer.parseInt(value), 1, 16384);
            case "Threads" -> threads = clamp(Integer.parseInt(value), 1, MAX_THREADS);
            case "MultiPV" -> multiPV = clamp(Integer.parseInt(value), 1, 256);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
