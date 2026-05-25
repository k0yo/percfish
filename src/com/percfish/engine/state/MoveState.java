package com.percfish.engine.state;

public record MoveState(
        Move move,
        int movedPiece,
        int capturedPiece,
        int oldEchoPower,
        boolean oldWhiteToMove
) {
}
