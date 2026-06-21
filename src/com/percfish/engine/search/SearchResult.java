package com.percfish.engine.search;

import com.percfish.engine.state.Move;
import java.util.List;

/**
 * Holds the result of a search.
 * For single-PV mode, {@code bestMove} and {@code score} are the primary result.
 * For MultiPV mode, {@code multiPV} contains the top N (move, score) pairs.
 */
public record SearchResult(Move bestMove, int score, int depth, long nodes, long ttHits,
                           List<PVEntry> multiPV, List<Move> pv) {

    public SearchResult(Move bestMove, int score, int depth, long nodes, long ttHits) {
        this(bestMove, score, depth, nodes, ttHits, List.of(), List.of());
    }

    public SearchResult(Move bestMove, int score, int depth, long nodes, long ttHits, List<PVEntry> multiPV) {
        this(bestMove, score, depth, nodes, ttHits, multiPV, List.of());
    }

    /**
     * A single principal variation entry: a move and its score from white's perspective.
     */
    public record PVEntry(Move move, int score, List<Move> pv) {
        public PVEntry(Move move, int score) {
            this(move, score, List.of());
        }
    }
}
