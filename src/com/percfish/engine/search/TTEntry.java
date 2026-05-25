package com.percfish.engine.search;

import com.percfish.engine.state.Move;

public record TTEntry(long key, int score, int depth, int flag, Move bestMove) {}
