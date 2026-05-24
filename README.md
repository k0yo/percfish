# Percfish

An UCI-compatible chess engine for the **Perc** variant, written in Java.

## Development Status
Current Version: **0.4.0** _(Full legal move generation)_

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
* `go perft <depth>` - Counts legal move-tree nodes to the given depth.
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
