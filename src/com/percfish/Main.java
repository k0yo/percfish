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
        if (command.equals("uci")) {
            System.out.println("id name Percfish 0.1.0");
            System.out.println("id author sembii");
            System.out.println("uciok");
        } else if (command.equals("isready")) {
            System.out.println("readyok");
        } else if (command.equals("quit")) {
            System.exit(0);
        }
        // Add "position" and "go" logic next
    }
}