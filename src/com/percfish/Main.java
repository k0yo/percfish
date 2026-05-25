package com.percfish;

import com.percfish.engine.Board;
import com.percfish.engine.Evaluator;
import com.percfish.engine.GameResult;
import com.percfish.engine.Move;
import com.percfish.engine.MoveGenerator;
import com.percfish.engine.PositionHistory;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class Main {
    private static final Board board = new Board();
    private static final PositionHistory positionHistory = new PositionHistory();
    private static final Random random = new Random();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNextLine()) {
            String command = scanner.nextLine().trim();
            handleCommand(command);
        }
    }

    private static void handleCommand(String command) {
        String[] parts =  command.split(" ");
        String commandType = parts[0];

        switch (commandType) {
            case "uci" -> {
                System.out.println("id name Percfish 0.1.0");
                System.out.println("id author Sembii");
                System.out.println("uciok");
            }
            case "isready" -> System.out.println("readyok");
            case "d" -> System.out.println(board.getAsciiBoard());
            case "position" -> handlePosition(parts);
            case "eval" -> {
                Evaluator evaluator = new Evaluator();
                double eval = evaluator.evaluateWhitePerspective(board) / 100.0;
                System.out.printf("Evaluation: %.1f%n", eval);
            }
            case "result" -> {
                MoveGenerator moveGenerator = new MoveGenerator();
                GameResult gameResult = moveGenerator.getGameResult(board, positionHistory);
                System.out.println("Game result: " + gameResult);
            }
            case "go" -> handleGo(parts);
            case "genmoves" -> {
                System.out.println("Generating moves...");
                MoveGenerator moveGenerator = new MoveGenerator();
                List<Move> moves = moveGenerator.generateLegalMoves(board);
                for (Move move : moves) {
                    System.out.println(move.toString());
                }
                System.out.println("Total moves: " + moves.size());
            }
            case "quit" -> System.exit(0);
        }

    }

    private static void handleGo(String[] args) {
        if (args.length >= 2 && args[1].equals("perft")) {
            handlePerft(args);
            return;
        }

        playRandomMove();
    }

    private static void handlePerft(String[] args) {
        if (args.length != 3) {
            return;
        }

        int depth = Integer.parseInt(args[2]);
        MoveGenerator moveGenerator = new MoveGenerator();

        for (int currentDepth = 1; currentDepth <= depth; currentDepth++) {
            long startTime = System.nanoTime();
            long nodes = moveGenerator.perft(board, currentDepth);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            System.out.println("perft(" + currentDepth + ") = " + nodes + " (" + elapsedMs + " ms)");
        }
    }

    private static void playRandomMove() {
        MoveGenerator moveGenerator = new MoveGenerator();
        List<Move> legalMoves = moveGenerator.generateLegalMoves(board);

        if (legalMoves.isEmpty()) {
            System.out.println("bestmove 0000");
            return;
        }

        Move bestMove = legalMoves.get(random.nextInt(legalMoves.size()));
        System.out.println("bestmove " + bestMove);
    }

    private static void handlePosition(String[] args) {
        // Command: position [startpos | fen ...] [moves ...]
        if (args.length < 2) return;

        int moveStartIndex = -1;

        if (args[1].equals("startpos")) {
            board.loadPfen(Board.START_PFEN);
            positionHistory.clear();
            positionHistory.record(board);
            moveStartIndex = args.length > 2 && args[2].equals("moves") ? 3 : -1;
        } else if (args[1].equals("fen")) {
            int fenEndIndex = args.length;
            for (int i = 2; i < args.length; i++) {
                if (args[i].equals("moves")) {
                    fenEndIndex = i;
                    moveStartIndex = i + 1;
                    break;
                }
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < fenEndIndex; i++) {
                sb.append(args[i]).append(i == fenEndIndex - 1 ? "" : " ");
            }
            board.loadPfen(sb.toString());
            positionHistory.clear();
            positionHistory.record(board);
        }

        if (moveStartIndex != -1) {
            for (int i = moveStartIndex; i < args.length; i++) {
                board.makeMove(args[i]);
                positionHistory.record(board);
            }
        }
    }
}
