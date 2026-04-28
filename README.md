# Percfish

An UCI-compatible chess engine for the **Perc** variant, written in Java.

## The Perc Variant
Perc is played on a **9x9 board** with unique pieces and environmental mechanics.

### Key Features:
* **The Void:** Three stationary blocks at `d5`, `e5`, and `f5` that block movement and vision (except for Knights).
* **Echo Piece:** Mimics the movement rules of the last piece moved by the opponent.
* **New Pieces:** * **Cannon:** Slides like a Rook but requires a "screen" to jump over for captures.
    * **Falcon/Hunter:** Asymmetrical, directional sliders.
    * **Promotions:** Shogi-style promotions (Dragon King and Dragon Horse).
* **Stalemate:** In Perc, stalemate is a **loss** for the player with no legal moves.

## Development Status
Current Version: **0.1.0** (Core UCI Protocol & Board Representation)

**Custom Commands:**
* `show` - Displays the current board state in ASCII format.

## Author
* **sembii**
