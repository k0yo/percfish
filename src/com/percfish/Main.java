package com.percfish;

import com.percfish.engine.Board;
import java.util.Scanner;

public class Main {
    private static Board board = new Board();

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
            case "show" -> System.out.println(board.getAsciiBoard());
            case "position" -> handlePosition(parts);
            case "quit" -> System.exit(0);
        }
        // Add "go" logic next
    }

    private static void handlePosition(String[] args) {
        // Command: position [startpos | fen ...] [moves ...]
        if (args.length < 2) return;

        if (args[1].equals("startpos")) {
            board.loadPfen(Board.START_PFEN);
        } else if (args[1].equals("fen")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                sb.append(args[i]).append(i == args.length - 1 ? "" : " ");
            }
            board.loadPfen(sb.toString());
            }
    }
}