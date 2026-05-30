package com.percfish.engine.state;

public class Board {
    public static final String START_PFEN = "r1n1k1n1r/1h1cec1f1/ppp1b1ppp/4b4/3vvv3/4B4/PPP1B1PPP/1F1CEC1H1/R1N1K1N1R w -";

    private static final int MAX_PIECES_PER_SIDE = 18;

    private final int[] squares;
    public boolean isWhiteToMove;
    private int echoPower;
    private long zobristKey;

    private final int[] whitePieceSquares;
    private int whitePieceCount;
    private final int[] blackPieceSquares;
    private int blackPieceCount;

    // King square cache: kingSquare[Piece.WHITE] and kingSquare[Piece.BLACK]
    private final int[] kingSquare;

    public Board() {
        this.squares = new int[81];
        this.whitePieceSquares = new int[MAX_PIECES_PER_SIDE];
        this.blackPieceSquares = new int[MAX_PIECES_PER_SIDE];
        this.kingSquare = new int[2];
        this.isWhiteToMove = true;
        this.echoPower = Piece.EMPTY;
        this.zobristKey = Zobrist.calculateKey(this);
    }

    public int getSquare(int index) {
        return squares[index];
    }

    public int getEchoPower() {
        return echoPower;
    }

    public long getZobristKey() {
        return zobristKey;
    }

    public int findKing(int color) {
        int idx = color == Piece.WHITE ? 0 : 1;
        return kingSquare[idx];
    }

    public int[] getPieceSquares(int color) {
        return color == Piece.WHITE ? whitePieceSquares : blackPieceSquares;
    }

    public int getPieceCount(int color) {
        return color == Piece.WHITE ? whitePieceCount : blackPieceCount;
    }

    private int charToPiece(char c) {
        int color = Character.isUpperCase(c) ? Piece.WHITE : Piece.BLACK;
        char lower = Character.toLowerCase(c);
        int type = switch (lower) {
            case 'p' -> Piece.PAWN;
            case 'n' -> Piece.KNIGHT;
            case 'b' -> Piece.BISHOP;
            case 'r' -> Piece.ROOK;
            case 'c' -> Piece.CANNON;
            case 'f' -> Piece.FALCON;
            case 'h' -> Piece.HUNTER;
            case 'k' -> Piece.KING;
            case 'e' -> Piece.ECHO;
            case 'd' -> Piece.D_HORSE;
            case 'x' -> Piece.D_KING;
            case 'v' -> Piece.VOID;
            default -> Piece.EMPTY;
        };

        return color | type;
    }

    private char pieceToChar(int piece) {
        if (piece == Piece.EMPTY) return '.';

        int type = Piece.getType(piece);
        char c = switch (type) {
            case Piece.PAWN -> 'p';
            case Piece.KNIGHT -> 'n';
            case Piece.BISHOP -> 'b';
            case Piece.ROOK -> 'r';
            case Piece.CANNON -> 'c';
            case Piece.FALCON -> 'f';
            case Piece.HUNTER -> 'h';
            case Piece.KING -> 'k';
            case Piece.ECHO -> 'e';
            case Piece.D_HORSE -> 'd';
            case Piece.D_KING -> 'x';
            case Piece.VOID -> 'v';
            default -> '?';
        };

        return (piece & Piece.WHITE) != 0 ? Character.toUpperCase(c) : c;
    }

    public String getAsciiBoard() {
        StringBuilder builder = new StringBuilder();
        builder.append("  +---------------------------+\n");

        for (int rank = 8; rank >= 0; rank--) {
            builder.append(rank + 1).append(" |");

            for (int file = 0; file < 9; file++) {
                int index = rank * 9 + file;
                int piece = squares[index];

                if ((Piece.getType(piece)) == Piece.VOID) {
                    builder.append(" # ");
                } else {
                    builder.append(" ").append(pieceToChar(piece)).append(" ");
                }
            }
            builder.append("|\n");
        }

        builder.append("  +---------------------------+\n");
        builder.append("    a  b  c  d  e  f  g  h  i\n");
        builder.append("Turn: ").append(isWhiteToMove ? "White" : "Black").append("\n");
        builder.append("Echo Power: ").append(pieceToChar(echoPower)).append("\n");
        builder.append("PFEN: ").append(toPfen()).append("\n");
        builder.append("Key: ").append(String.format("%016X", zobristKey)).append("\n");

        return builder.toString();
    }

    /**
     * Loads PFEN (Perc FEN) string into the board state.
     * Example PFEN: "r1n1k1n1r/.../9/R1N1K1N1R w c"
     */
    public void loadPfen(String pfen) {
        String[] parts = pfen.split(" ");
        String placement = parts[0];

        for (int i = 0; i < 81; i++) squares[i] = Piece.EMPTY;
        whitePieceCount = 0;
        blackPieceCount = 0;
        kingSquare[0] = -1;
        kingSquare[1] = -1;

        String[] ranks = placement.split("/");
        int currentRank = 8;

        for (String rankContent: ranks) {
            int currentFile = 0;
            for (int i = 0; i < rankContent.length(); i++) {
                char c = rankContent.charAt(i);

                if (Character.isDigit(c)) {
                    currentFile += Character.getNumericValue(c);
                } else {
                    int index = currentRank * 9 + currentFile;
                    int piece = charToPiece(c);
                    squares[index] = piece;
                    addPieceToLists(index, piece);
                    currentFile++;
                }
            }
            currentRank--;
        }

        this.isWhiteToMove = parts[1].equals("w");

        if (!parts[2].equals("-")) {
            this.echoPower = Piece.getType(charToPiece(parts[2].charAt(0)));
        } else {
            this.echoPower = Piece.EMPTY;
        }

        this.zobristKey = Zobrist.calculateKey(this);
    }

    public String toPfen() {
        StringBuilder pfen = new StringBuilder();

        for (int rank = 8; rank >= 0; rank--) {
            int emptyCount = 0;
            for (int file = 0; file < 9; file++) {
                int index = rank * 9 + file;
                int piece = squares[index];

                if (piece == Piece.EMPTY) {
                    emptyCount++;
                } else if (Piece.getType(piece) == Piece.VOID) {
                    if (emptyCount > 0) pfen.append(emptyCount);
                    pfen.append("v");
                    emptyCount = 0;
                } else {
                    if (emptyCount > 0) pfen.append(emptyCount);
                    pfen.append(pieceToChar(piece));
                    emptyCount = 0;
                }
            }
            if (emptyCount > 0) pfen.append(emptyCount);
            if (rank > 0) pfen.append("/");
        }

        String turn = isWhiteToMove ? "w" : "b";
        String echo = (echoPower == Piece.EMPTY) ? "-" : String.valueOf(pieceToChar(echoPower)).toLowerCase();

        return pfen + " " + turn + " " + echo;
    }

    public long repetitionKey() {
        return zobristKey;
    }

    public MoveState makeMove(String move) {
        return makeMove(Move.fromString(move));
    }

    public MoveState makeMove(Move move) {
        int movedPiece = squares[move.from()];
        int capturedPiece = squares[move.to()];
        int oldEchoPower = echoPower;
        boolean oldWhiteToMove = isWhiteToMove;

        int movedType = Piece.getType(movedPiece);
        int movedColor = Piece.getColor(movedPiece);

        int newEchoPower = movedType == Piece.ECHO ? echoPower : movedType;
        int finalType = move.isPromotion() ? move.promotionType() : movedType;
        int finalPiece = movedColor | finalType;

        // Update Zobrist Key
        zobristKey ^= Zobrist.getPieceKey(move.from(), movedPiece);
        if (capturedPiece != Piece.EMPTY) {
            zobristKey ^= Zobrist.getPieceKey(move.to(), capturedPiece);
        }
        zobristKey ^= Zobrist.getPieceKey(move.to(), finalPiece);
        zobristKey ^= Zobrist.getSideKey();
        zobristKey ^= Zobrist.getEchoPowerKey(oldEchoPower);
        zobristKey ^= Zobrist.getEchoPowerKey(newEchoPower);

        // Update piece lists
        removePieceFromLists(move.from(), movedPiece);
        if (capturedPiece != Piece.EMPTY) {
            removePieceFromLists(move.to(), capturedPiece);
        }
        addPieceToLists(move.to(), finalPiece);

        squares[move.to()] = finalPiece;
        squares[move.from()] = Piece.EMPTY;
        echoPower = newEchoPower;
        isWhiteToMove = !isWhiteToMove;

        return new MoveState(move, movedPiece, capturedPiece, oldEchoPower, oldWhiteToMove);
    }

    /**
     * Performs a null move: flips the side to move, clears echo power (since no piece moved),
     * and updates the Zobrist key accordingly. Returns a {@link MoveState} holding the prior
     * echo power and turn for later restoration via {@link #unmakeNullMove(MoveState)}.
     */
    public MoveState makeNullMove() {
        int oldEchoPower = this.echoPower;
        boolean oldWhiteToMove = this.isWhiteToMove;

        zobristKey ^= Zobrist.getSideKey();
        zobristKey ^= Zobrist.getEchoPowerKey(echoPower);
        echoPower = Piece.EMPTY;
        zobristKey ^= Zobrist.getEchoPowerKey(echoPower);
        isWhiteToMove = !isWhiteToMove;

        return new MoveState(null, Piece.EMPTY, Piece.EMPTY, oldEchoPower, oldWhiteToMove);
    }

    /**
     * Reverses a null move previously applied via {@link #makeNullMove()}.
     */
    public void unmakeNullMove(MoveState state) {
        isWhiteToMove = state.oldWhiteToMove();
        zobristKey ^= Zobrist.getSideKey();
        zobristKey ^= Zobrist.getEchoPowerKey(echoPower);
        echoPower = state.oldEchoPower();
        zobristKey ^= Zobrist.getEchoPowerKey(echoPower);
    }

    public void unmakeMove(MoveState state) {
        Move move = state.move();

        int movedPiece = state.movedPiece();
        int capturedPiece = state.capturedPiece();
        int oldEchoPower = state.oldEchoPower();
        boolean oldWhiteToMove = state.oldWhiteToMove();

        int movedColor = Piece.getColor(movedPiece);
        int movedType = Piece.getType(movedPiece);
        int newEchoPower = movedType == Piece.ECHO ? oldEchoPower : movedType;
        int finalType = move.isPromotion() ? move.promotionType() : movedType;
        int finalPiece = movedColor | finalType;

        // Revert Zobrist Key
        zobristKey ^= Zobrist.getPieceKey(move.from(), movedPiece);
        if (capturedPiece != Piece.EMPTY) {
            zobristKey ^= Zobrist.getPieceKey(move.to(), capturedPiece);
        }
        zobristKey ^= Zobrist.getPieceKey(move.to(), finalPiece);
        zobristKey ^= Zobrist.getSideKey();
        zobristKey ^= Zobrist.getEchoPowerKey(oldEchoPower);
        zobristKey ^= Zobrist.getEchoPowerKey(newEchoPower);

        // Revert piece lists
        removePieceFromLists(move.to(), finalPiece);
        addPieceToLists(move.from(), movedPiece);
        if (capturedPiece != Piece.EMPTY) {
            addPieceToLists(move.to(), capturedPiece);
        }

        squares[move.from()] = movedPiece;
        squares[move.to()] = capturedPiece;
        echoPower = oldEchoPower;
        isWhiteToMove = oldWhiteToMove;
    }

    private void addPieceToLists(int square, int piece) {
        int type = Piece.getType(piece);
        if (type == Piece.VOID) return; // Void squares are not pieces
        int color = Piece.getColor(piece);
        if (color == Piece.WHITE) {
            whitePieceSquares[whitePieceCount++] = square;
        } else {
            blackPieceSquares[blackPieceCount++] = square;
        }
        if (type == Piece.KING) {
            int idx = color == Piece.WHITE ? 0 : 1;
            kingSquare[idx] = square;
        }
    }

    private void removePieceFromLists(int square, int piece) {
        int type = Piece.getType(piece);
        if (type == Piece.VOID) return; // Void squares are not in piece lists
        int color = Piece.getColor(piece);
        int[] list = color == Piece.WHITE ? whitePieceSquares : blackPieceSquares;
        int count = color == Piece.WHITE ? whitePieceCount : blackPieceCount;

        for (int i = 0; i < count; i++) {
            if (list[i] == square) {
                // Swap with last element and decrement count
                list[i] = list[count - 1];
                if (color == Piece.WHITE) {
                    whitePieceCount--;
                } else {
                    blackPieceCount--;
                }
                return;
            }
        }
    }
}