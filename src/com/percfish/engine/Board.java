package com.percfish.engine;

public class Board {
    public static final String START_PFEN = "r1n1k1n1r/1h1cec1f1/ppp1b1ppp/4b4/3vvv3/4B4/PPP1B1PPP/1F1CEC1H1/R1N1K1N1R w -";

    private final int[] squares;
    public boolean isWhiteToMove;
    private int echoPower;

    public Board() {
        this.squares = new int[81];
        this.isWhiteToMove = true;
        this.echoPower = Piece.EMPTY;
    }

    public int getSquare(int index) {
        return squares[index];
    }

    public int getEchoPower() {
        return echoPower;
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
                    squares[index] = charToPiece(c);
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

    public void makeMove(String move) {
        // Move execution and echoPower update
    }
}
