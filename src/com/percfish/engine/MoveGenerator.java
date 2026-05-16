package com.percfish.engine;

import java.util.ArrayList;
import java.util.List;

public class MoveGenerator {
    private static final int[] DIRECTION_OFFSETS = {9, -9, -1, 1, 8, -8, 10, -10};
    private static final int[][] NUM_SQUARES_TO_EDGE = new int[81][8];

    private static final int[] BISHOP_DIRS = {4, 5, 6, 7};
    private static final int[] ROOK_DIRS = {0, 1, 2, 3};
    private static final int[] ALL_DIRS = {0, 1, 2, 3, 4, 5, 6, 7};
    private static final int[] WHITE_FALCON = {1, 4, 6};
    private static final int[] WHITE_HUNTER = {0, 5, 7};
    private static final int[] PAWN_PROMOTIONS = {
            Piece.KNIGHT, Piece.BISHOP, Piece.CANNON, Piece.FALCON, Piece.HUNTER, Piece.ROOK
    };

    static {
        precompute();
    }

    private static void precompute() {
        for (int i = 0; i < 81; i++) {
            int r = i / 9;
            int f = i % 9;

            NUM_SQUARES_TO_EDGE[i][0] = 8 - r;    // North
            NUM_SQUARES_TO_EDGE[i][1] = r;        // South
            NUM_SQUARES_TO_EDGE[i][2] = f;        // West
            NUM_SQUARES_TO_EDGE[i][3] = 8 - f;    // East
            NUM_SQUARES_TO_EDGE[i][4] = Math.min(8 - r, f);       // NW
            NUM_SQUARES_TO_EDGE[i][5] = Math.min(r, 8 - f);       // SE
            NUM_SQUARES_TO_EDGE[i][6] = Math.min(8 - r, 8 - f);   // NE
            NUM_SQUARES_TO_EDGE[i][7] = Math.min(r, f);           // SW
        }
    }

    public List<Move> generatePseudoLegalMoves(Board board) {
        List<Move> moves = new ArrayList<>();

        for (int i = 0; i < 81; i++) {
            int piece = board.getSquare(i);

            if (piece != Piece.EMPTY && Piece.getColor(piece) == (board.isWhiteToMove ? Piece.WHITE : Piece.BLACK)) {
                addMovesForPiece(i, piece, board, moves, true);
            }

        }
        return moves;
    }

    public List<Move> generateLegalMoves(Board board) {
        int movingColor = board.isWhiteToMove ? Piece.WHITE : Piece.BLACK;
        List<Move> legalMoves = new ArrayList<>();

        for (Move move : generatePseudoLegalMoves(board)) {
            MoveState state = board.makeMove(move);

            if (!isInCheck(board, movingColor)) {
                legalMoves.add(move);
            }

            board.unmakeMove(state);
        }

        return legalMoves;
    }

    public boolean isSideToMoveLost(Board board) {
        return generateLegalMoves(board).isEmpty();
    }

    public boolean isSquareAttacked(Board board, int square, int byColor) {
        for (int i = 0; i < 81; i++) {
            int piece = board.getSquare(i);

            if (piece == Piece.EMPTY || Piece.getColor(piece) != byColor) {
                continue;
            }

            if (pieceAttacksSquare(i, piece, square, board)) {
                return true;
            }
        }

        return false;
    }

    public boolean isInCheck(Board board, int color) {
        int kingSquare = board.findKing(color);

        if (kingSquare == -1) {
            throw new IllegalStateException("No king found for color: " + color);
        }

        int opponentColor = color == Piece.WHITE ? Piece.BLACK : Piece.WHITE;
        return isSquareAttacked(board, kingSquare, opponentColor);
    }

    private boolean pieceAttacksSquare(int startSquare, int piece, int targetSquare, Board board) {
        int type = Piece.getType(piece);

        if (type == Piece.ECHO) {
            type = board.getEchoPower();

            if (type == Piece.EMPTY || type == Piece.ECHO) {
                return false;
            }
        }

        int attackPiece = Piece.getColor(piece) | type;

        return switch (type) {
            case Piece.PAWN -> pawnAttacksSquare(startSquare, attackPiece, targetSquare);
            case Piece.KNIGHT -> knightAttacksSquare(startSquare, targetSquare);
            case Piece.KING -> stepAttacksSquare(startSquare, targetSquare, ALL_DIRS);
            case Piece.CANNON -> cannonAttacksSquare(startSquare, targetSquare, board);
            case Piece.D_KING -> slidingAttacksSquare(startSquare, attackPiece, targetSquare, board)
                    || stepAttacksSquare(startSquare, targetSquare, BISHOP_DIRS);
            case Piece.D_HORSE -> slidingAttacksSquare(startSquare, attackPiece, targetSquare, board)
                    || stepAttacksSquare(startSquare, targetSquare, ROOK_DIRS);
            default -> Piece.isSlider(attackPiece)
                    && slidingAttacksSquare(startSquare, attackPiece, targetSquare, board);
        };
    }

    private boolean pawnAttacksSquare(int startSquare, int piece, int targetSquare) {
        int forwardOffset = Piece.getColor(piece) == Piece.WHITE ? 9 : -9;
        return startSquare + forwardOffset == targetSquare;
    }

    private boolean knightAttacksSquare(int startSquare, int targetSquare) {
        int startRank = startSquare / 9;
        int startFile = startSquare % 9;
        int targetRank = targetSquare / 9;
        int targetFile = targetSquare % 9;

        int rankDiff = Math.abs(startRank - targetRank);
        int fileDiff = Math.abs(startFile - targetFile);

        return (rankDiff == 2 && fileDiff == 1) || (rankDiff == 1 && fileDiff == 2);
    }

    private boolean stepAttacksSquare(int startSquare, int targetSquare, int[] directions) {
        for (int dirIndex : directions) {
            int stepSquare = startSquare + DIRECTION_OFFSETS[dirIndex];

            if (stepSquare == targetSquare && isOneStepAway(startSquare, targetSquare)) {
                return true;
            }
        }

        return false;
    }

    private boolean isOneStepAway(int startSquare, int targetSquare) {
        int startRank = startSquare / 9;
        int startFile = startSquare % 9;
        int targetRank = targetSquare / 9;
        int targetFile = targetSquare % 9;

        return Math.abs(startRank - targetRank) <= 1 && Math.abs(startFile - targetFile) <= 1;
    }

    private boolean slidingAttacksSquare(int startSquare, int piece, int targetSquare, Board board) {
        for (int dirIndex : getDirectionsForPiece(piece)) {
            for (int n = 0; n < NUM_SQUARES_TO_EDGE[startSquare][dirIndex]; n++) {
                int currentSquare = startSquare + DIRECTION_OFFSETS[dirIndex] * (n + 1);
                int pieceOnCurrent = board.getSquare(currentSquare);

                if (Piece.getType(pieceOnCurrent) == Piece.VOID) {
                    break;
                }

                if (currentSquare == targetSquare) {
                    return true;
                }

                if (pieceOnCurrent != Piece.EMPTY) {
                    break;
                }
            }
        }

        return false;
    }

    private boolean cannonAttacksSquare(int startSquare, int targetSquare, Board board) {
        for (int dirIndex : ROOK_DIRS) {
            boolean foundScreen = false;

            for (int n = 0; n < NUM_SQUARES_TO_EDGE[startSquare][dirIndex]; n++) {
                int currentSquare = startSquare + DIRECTION_OFFSETS[dirIndex] * (n + 1);
                int pieceOnCurrent = board.getSquare(currentSquare);

                if (Piece.getType(pieceOnCurrent) == Piece.VOID) {
                    break;
                }

                if (currentSquare == targetSquare) {
                    return foundScreen;
                }

                if (pieceOnCurrent != Piece.EMPTY) {
                    if (foundScreen) {
                        break;
                    }

                    foundScreen = true;
                }
            }
        }

        return false;
    }

    private void addMovesForPiece(int startSquare, int piece, Board board, List<Move> moves, boolean allowPromotions) {
        switch (Piece.getType(piece)) {
            case Piece.CANNON -> {
                addSlidingMoves(startSquare, piece, board, moves, allowPromotions);
                addCannonCaptureMoves(startSquare, piece, board, moves);
            }
            case Piece.KING -> addKingMoves(startSquare, piece, board, moves);
            case Piece.KNIGHT -> addKnightMoves(startSquare, piece, board, moves);
            case Piece.PAWN -> addPawnMoves(startSquare, piece, board, moves, allowPromotions);
            case Piece.ECHO -> addEchoMoves(startSquare, piece, board, moves);
            case Piece.D_KING -> {
                addSlidingMoves(startSquare, piece, board, moves, allowPromotions);
                addStepMoves(startSquare, piece, board, moves, BISHOP_DIRS, allowPromotions);
            }
            case Piece.D_HORSE -> {
                addSlidingMoves(startSquare, piece, board, moves, allowPromotions);
                addStepMoves(startSquare, piece, board, moves, ROOK_DIRS, allowPromotions);
            }
            default -> {
                if (Piece.isSlider(piece)) {
                    addSlidingMoves(startSquare, piece, board, moves, allowPromotions);
                }
            }
        }
    }

    private void addSlidingMoves(int startSquare, int piece, Board board, List<Move> moves, boolean allowPromotions) {
        int type = Piece.getType(piece);
        int color = Piece.getColor(piece);
        int[] directions = getDirectionsForPiece(piece);

        for (int dirIndex : directions) {
            for (int n = 0; n < NUM_SQUARES_TO_EDGE[startSquare][dirIndex]; n++) {
                int targetSquare = startSquare + DIRECTION_OFFSETS[dirIndex] * (n + 1);
                int pieceOnTarget = board.getSquare(targetSquare);

                if (Piece.getColor(pieceOnTarget) == color || Piece.getType(pieceOnTarget) == Piece.VOID) {
                    break;
                }

                // Skip if it's a capture and the piece is a Cannon
                boolean isCapture = Piece.getColor(pieceOnTarget) != Piece.EMPTY;
                if (!(isCapture && type == Piece.CANNON)) {
                    addMove(startSquare, targetSquare, piece, moves, allowPromotions);
                }

                if (isCapture) {
                    break;
                }
            }
        }
    }

    private void addStepMoves(int startSquare, int piece, Board board, List<Move> moves, int[] directions, boolean allowPromotions) {
        int color = Piece.getColor(piece);
        int startRank = startSquare / 9;
        int startFile = startSquare % 9;

        for (int dirIndex : directions) {
            int targetSquare = startSquare + DIRECTION_OFFSETS[dirIndex];

            if (targetSquare < 0 || targetSquare >= 81) {
                continue;
            }

            int targetRank = targetSquare / 9;
            int targetFile = targetSquare % 9;

            if (Math.abs(startRank - targetRank) > 1 || Math.abs(startFile - targetFile) > 1) {
                continue;
            }

            int pieceOnTarget = board.getSquare(targetSquare);

            if (Piece.getType(pieceOnTarget) == Piece.VOID) {
                continue;
            }

            if (Piece.getColor(pieceOnTarget) == color) {
                continue;
            }

            addMove(startSquare, targetSquare, piece, moves, allowPromotions);
        }
    }

    private void addCannonCaptureMoves(int startSquare, int piece, Board board, List<Move> moves) {
        int color = Piece.getColor(piece);

        for (int dirIndex : ROOK_DIRS) {
            boolean foundScreen = false;
            for (int n = 0; n < NUM_SQUARES_TO_EDGE[startSquare][dirIndex]; n++) {
                int targetSquare = startSquare + DIRECTION_OFFSETS[dirIndex] * (n + 1);
                int pieceOnTarget = board.getSquare(targetSquare);

                if (!foundScreen) {
                    if (pieceOnTarget != Piece.EMPTY && Piece.getType(pieceOnTarget) != Piece.VOID) {
                        foundScreen = true;
                    } else if (Piece.getType(pieceOnTarget) == Piece.VOID) {
                        break;
                    }
                } else {
                    if (pieceOnTarget != Piece.EMPTY && Piece.getType(pieceOnTarget) != Piece.VOID) {
                        if (Piece.getColor(pieceOnTarget) != color) {
                            moves.add(new Move(startSquare, targetSquare));
                        }
                        break;
                    } else if (Piece.getType(pieceOnTarget) == Piece.VOID) {
                        break;
                    }
                }
            }
        }
    }

    private void addEchoMoves(int startSquare, int piece, Board board, List<Move> moves) {
        int echoPower = board.getEchoPower();

        if (echoPower == Piece.EMPTY || echoPower == Piece.ECHO) {
            return;
        }

        int echoPiece = Piece.getColor(piece) | echoPower;
        addMovesForPiece(startSquare, echoPiece, board, moves, false);
    }

    private void addKingMoves(int startSquare, int piece, Board board, List<Move> moves) {
        addStepMoves(startSquare, piece, board, moves, ALL_DIRS, false);
    }

    private static final int[] KNIGHT_OFFSETS = {-19, -17, -11, -7, 7, 11, 17, 19};

    private void addKnightMoves(int startSquare, int piece, Board board, List<Move> moves) {
        int color = Piece.getColor(piece);
        int startRank = startSquare / 9;
        int startFile = startSquare % 9;

        for (int offset : KNIGHT_OFFSETS) {
            int targetSquare = startSquare + offset;

            if (targetSquare < 0 || targetSquare >= 81) {
                continue;
            }

            int targetRank = targetSquare / 9;
            int targetFile = targetSquare % 9;

            int rankDiff = Math.abs(startRank - targetRank);
            int fileDiff = Math.abs(startFile - targetFile);

            if (!((rankDiff == 2 && fileDiff == 1) || (rankDiff == 1 && fileDiff == 2))) {
                continue;
            }

            int pieceOnTarget = board.getSquare(targetSquare);

            if (Piece.getType(pieceOnTarget) == Piece.VOID) {
                continue;
            }

            if (Piece.getColor(pieceOnTarget) == color) {
                continue;
            }

            addMove(startSquare, targetSquare, piece, moves, false);
        }
    }

    private void addPawnMoves(int startSquare, int piece, Board board, List<Move> moves, boolean allowPromotions) {
        int color = Piece.getColor(piece);
        int forwardOffset = (color == Piece.WHITE) ? 9 : -9;

        int targetSquare = startSquare + forwardOffset;

        if (targetSquare >= 0 && targetSquare < 81) {
            int pieceOnTarget = board.getSquare(targetSquare);

            if (Piece.getType(pieceOnTarget) == Piece.VOID) {
                return;
            }

            if (Piece.getColor(pieceOnTarget) == color) {
                return;
            }

            addMove(startSquare, targetSquare, piece, moves, allowPromotions);
        }
    }

    private void addMove(int startSquare, int targetSquare, int piece, List<Move> moves, boolean allowPromotions) {
        if (!allowPromotions) {
            moves.add(new Move(startSquare, targetSquare));
            return;
        }

        int color = Piece.getColor(piece);

        switch (Piece.getType(piece)) {
            case Piece.PAWN -> {
                if (!isPawnPromotionRank(targetSquare, color)) {
                    moves.add(new Move(startSquare, targetSquare));
                    return;
                }

                for (int promotionType : PAWN_PROMOTIONS) {
                    moves.add(new Move(startSquare, targetSquare, promotionType));
                }
            }
            case Piece.ROOK -> {
                if (!isLastRank(targetSquare, color)) {
                    moves.add(new Move(startSquare, targetSquare));
                    return;
                }

                moves.add(new Move(startSquare, targetSquare));
                moves.add(new Move(startSquare, targetSquare, Piece.D_KING));
            }
            case Piece.BISHOP -> {
                if (!isLastRank(targetSquare, color)) {
                    moves.add(new Move(startSquare, targetSquare));
                    return;
                }

                moves.add(new Move(startSquare, targetSquare));
                moves.add(new Move(startSquare, targetSquare, Piece.D_HORSE));
            }
            default -> moves.add(new Move(startSquare, targetSquare));
        }
    }

    private boolean isPawnPromotionRank(int square, int color) {
        int rank = square / 9;
        return color == Piece.WHITE ? rank >= 6 : rank <= 2;
    }

    private boolean isLastRank(int square, int color) {
        int rank = square / 9;
        return color == Piece.WHITE ? rank == 8 : rank == 0;
    }

    private int[] getDirectionsForPiece(int piece) {
        int type = Piece.getType(piece);
        int color = Piece.getColor(piece);

        return switch (type) {
            case Piece.BISHOP, Piece.D_HORSE -> BISHOP_DIRS;
            case Piece.ROOK, Piece.CANNON, Piece.D_KING -> ROOK_DIRS;
            case Piece.FALCON -> color == Piece.WHITE
                    ? WHITE_FALCON
                    : WHITE_HUNTER;
            case Piece.HUNTER -> color == Piece.WHITE
                    ? WHITE_HUNTER
                    : WHITE_FALCON;
            default -> new int[0];
        };
    }
}
