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

    public List<Move> generateMoves(Board board) {
        List<Move> moves = new ArrayList<>();

        for (int i = 0; i < 81; i++) {
            int piece = board.getSquare(i);

            if (piece != Piece.EMPTY && Piece.getColor(piece) == (board.isWhiteToMove ? Piece.WHITE : Piece.BLACK)) {
                addMovesForPiece(i, piece, board, moves);
            }

        }
        return moves;
    }

    private void addMovesForPiece(int startSquare, int piece, Board board, List<Move> moves) {
        switch (Piece.getType(piece)) {
            case Piece.CANNON -> {
                addSlidingMoves(startSquare, piece, board, moves);
                addCannonCaptureMoves(startSquare, piece, board, moves);
            }
            case Piece.KING -> addKingMoves(startSquare, piece, board, moves);
            case Piece.KNIGHT -> addKnightMoves(startSquare, piece, board, moves);
            case Piece.PAWN -> addPawnMoves(startSquare, piece, board, moves);
            case Piece.ECHO -> addEchoMoves(startSquare, piece, board, moves);
            case Piece.D_KING -> {
                addSlidingMoves(startSquare, piece, board, moves);
                addStepMoves(startSquare, piece, board, moves, BISHOP_DIRS);
            }
            case Piece.D_HORSE -> {
                addSlidingMoves(startSquare, piece, board, moves);
                addStepMoves(startSquare, piece, board, moves, ROOK_DIRS);
            }
            default -> {
                if (Piece.isSlider(piece)) {
                    addSlidingMoves(startSquare, piece, board, moves);
                }
            }
        }
    }

    private void addSlidingMoves(int startSquare, int piece, Board board, List<Move> moves) {
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
                    moves.add(new Move(startSquare, targetSquare));
                }

                if (isCapture) {
                    break;
                }
            }
        }
    }

    private void addStepMoves(int startSquare, int piece, Board board, List<Move> moves, int[] directions) {
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

            moves.add(new Move(startSquare, targetSquare));
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
        addMovesForPiece(startSquare, echoPiece, board, moves);
    }

    private void addKingMoves(int startSquare, int piece, Board board, List<Move> moves) {
        addStepMoves(startSquare, piece, board, moves, ALL_DIRS);
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

            moves.add(new Move(startSquare, targetSquare));
        }
    }

    private void addPawnMoves(int startSquare, int piece, Board board, List<Move> moves) {
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

            moves.add(new Move(startSquare, targetSquare));
        }
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
