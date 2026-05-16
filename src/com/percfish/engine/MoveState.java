package com.percfish.engine;

public record MoveState(
        Move move,
        int movedPiece,
        int capturedPiece,
        int oldEchoPower,
        boolean oldWhiteToMove
) {
}
