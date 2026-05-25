package com.percfish.engine.search;

public record SearchResult(com.percfish.engine.state.Move bestMove, int score, int depth, long nodes, long ttHits) {}
