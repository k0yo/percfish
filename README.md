# Percfish

An UCI-compatible chess engine for the **Perc** variant, written in Java.

## Development Status
Current Version: **0.3.0** _(Legal move generation, perft, repetition tracking, eval, iterative search, time management)_

Rough ladder:
* **v0.1.0** - First working search `go` (even 1-ply), returns `bestmove`.
* **v0.2.0** - Iterative deepening + basic time controls.
* **v0.3.0** - Stable repetition/draw handling in play and search + small regression test suite.
* **v0.4.0** - Better move ordering and basic tactics stability.
* **v1.0.0** - Rules and engine behavior considered stable (no illegal moves, no king captures, make/unmake solid, results reliable).

## Usage

Build:
```bash
mkdir -p out/production/percfish
javac -d out/production/percfish src/com/percfish/Main.java src/com/percfish/engine/*.java
```

Run:
```bash
java -cp out/production/percfish com.percfish.Main
```

### Commands:
* `uci` - Prints engine identification and `uciok`.
* `isready` - Prints `readyok`.
* `position startpos [moves ...]` - Loads the starting position and optionally applies moves.
* `position fen <pfen> [moves ...]` - Loads a PFEN position and optionally applies moves.
* `d` - Displays the current board state in ASCII format.
* `eval` - Prints the current material evaluation from White's perspective.
* `genmoves` - Prints all legal moves for the current board state.
* `go` - Searches to the default depth and prints the best move.
* `go depth <depth>` - Searches to the given depth and prints the best move.
* `go movetime <ms>` - Searches until the given number of milliseconds has elapsed.
* `go wtime <ms> btime <ms> winc <ms> binc <ms>` - Searches using UCI-style time controls.
* `go perft <depth>` - Counts legal move-tree nodes to the given depth.
* `go perft divide <depth>` - Counts legal move-tree nodes per move to the given depth.
* `stop` - Stops an ongoing search immediately.
* `result` - Prints the current game result (`ONGOING`, `WHITE_WINS`, `BLACK_WINS`, or `DRAW`).
* `quit` - Exits the engine.

## The Perc Variant
Perc is played on a **9x9 board** with unique pieces and environmental mechanics.

### Key Features:
* **The Void:** Three stationary blocks at `d5`, `e5`, and `f5` that block movement and vision (except for Knights).
* **Echo Piece:** Mimics the movement rules of the last piece moved by the opponent.
* **New Pieces:**
    * **Cannon:** Slides like a Rook but requires a "screen" to jump over for captures.
    * **Falcon/Hunter:** Asymmetrical, directional sliders.
    * **Promotions:** Shogi-style promotions (Dragon King and Dragon Horse).
* **Stalemate:** In Perc, stalemate is a **loss** for the player with no legal moves.

## Author
* **sembii**
