package com.percfish;

import com.percfish.engine.evaluation.Evaluator;
import com.percfish.engine.search.SearchResult;
import com.percfish.engine.search.Searcher;
import com.percfish.engine.state.*;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final String VERSION = "0.3.4";
    private static final int DEFAULT_SEARCH_DEPTH = 100;

    private static final Board board = new Board();
    private static final PositionHistory positionHistory = new PositionHistory();

    static {
        board.loadPfen(Board.START_PFEN);
        positionHistory.record(board);
    }

    private static final Searcher searcher = new Searcher();

    private static Thread searchThread = null;
    private static final Object searchLock = new Object();
    private static volatile long searchStartNanos;

    public static void main(String[] args) {
        System.out.println("Percfish " + VERSION + " by sembii");
        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNextLine()) {
            String command = scanner.nextLine().trim();
            if (command.isEmpty()) continue;
            handleCommand(command);
        }
    }

    private static void handleCommand(String command) {
        String[] parts =  command.split(" ");
        String commandType = parts[0];

        switch (commandType) {
            case "uci" -> {
                System.out.println("id name Percfish " + VERSION);
                System.out.println("id author sembii");
                System.out.println("uciok");
            }
            case "isready" -> System.out.println("readyok");
            case "d" -> System.out.println(board.getAsciiBoard());
            case "position" -> {
                stopSearch();
                handlePosition(parts);
            }
            case "eval" -> {
                Evaluator evaluator = new Evaluator();
                double eval = evaluator.evaluateWhitePerspective(board) / 100.0;
                System.out.printf("Evaluation: %.2f%n", eval);
            }
            case "result" -> {
                MoveGenerator moveGenerator = new MoveGenerator();
                GameResult gameResult = moveGenerator.getGameResult(board, positionHistory);
                System.out.println("Game result: " + gameResult);
            }
            case "go" -> {
                stopSearch();
                handleGo(parts);
            }
            case "stop" -> stopSearch();
            case "genmoves" -> {
                stopSearch();
                System.out.println("Generating moves...");
                MoveGenerator moveGenerator = new MoveGenerator();
                List<Move> moves = moveGenerator.generateLegalMoves(board);
                for (Move move : moves) {
                    System.out.println(move.toString());
                }
                System.out.println("Total moves: " + moves.size());
            }
            case "quit" -> {
                stopSearch();
                System.exit(0);
            }
        }

    }

    private static void stopSearch() {
        synchronized (searchLock) {
            searcher.stop();
            if (searchThread != null) {
                try {
                    searchThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                searchThread = null;
            }
        }
    }

    private static void handleGo(String[] args) {
        if (args.length >= 2 && args[1].equals("perft")) {
            if (args.length >= 3 && args[2].equals("divide")) {
                handlePerftDivide(args);
            } else {
                handlePerft(args);
            }
            return;
        }

        int depth = DEFAULT_SEARCH_DEPTH;
        long movetimeMs = -1;
        long wtime = -1;
        long btime = -1;
        long winc = 0;
        long binc = 0;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "depth" -> {
                    if (i + 1 < args.length) depth = Integer.parseInt(args[++i]);
                }
                case "movetime" -> {
                    if (i + 1 < args.length) movetimeMs = Long.parseLong(args[++i]);
                }
                case "wtime" -> {
                    if (i + 1 < args.length) wtime = Long.parseLong(args[++i]);
                }
                case "btime" -> {
                    if (i + 1 < args.length) btime = Long.parseLong(args[++i]);
                }
                case "winc" -> {
                    if (i + 1 < args.length) winc = Long.parseLong(args[++i]);
                }
                case "binc" -> {
                    if (i + 1 < args.length) binc = Long.parseLong(args[++i]);
                }
            }
        }

        if (movetimeMs == -1) {
            long time = board.isWhiteToMove ? wtime : btime;
            long inc = board.isWhiteToMove ? winc : binc;

            if (time != -1) {
                movetimeMs = time / 20 + inc / 2;
                movetimeMs = Math.min(movetimeMs, time / 2); // Cap at half remaining time
            }
        }

        if (movetimeMs != -1) {
            handleSearchIterative(depth, movetimeMs);
        } else {
            handleSearchIterative(depth, Long.MAX_VALUE);
        }
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

    private static void handlePerftDivide(String[] args) {
        if (args.length != 4) {
            return;
        }

        int depth = Integer.parseInt(args[3]);
        MoveGenerator moveGenerator = new MoveGenerator();
        moveGenerator.perftDivide(board, depth);
    }

    private static void handleSearchIterative(int depth, long movetimeMs) {
        synchronized (searchLock) {
            searchThread = new Thread(() -> {
                searchStartNanos = System.nanoTime();
                SearchResult result = searcher.searchIterative(board, depth, movetimeMs, Main::printSearchInfo, positionHistory);
                System.out.println("bestmove " + (result.bestMove() == null ? "0000" : result.bestMove()));
            });
            searchThread.start();
        }
    }

    private static void printSearchInfo(SearchResult result) {
        long elapsedMs = Math.max(1L, (System.nanoTime() - searchStartNanos) / 1_000_000L);
        long nps = (result.nodes() * 1000L) / elapsedMs;
        System.out.println("info depth " + result.depth() + " score cp " + result.score() +
                " nodes " + result.nodes() + " nps " + nps + " tthits " + result.ttHits() + " time " + elapsedMs);
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
