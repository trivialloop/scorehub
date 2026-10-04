# Cribbage

- **Players**: exactly 2 (equipment: cards).
- Each round takes **2 lines**: the top line has two equal-width columns per player (**In play** with −/score/+ buttons, and **End of round**); the bottom line holds the **Crib**, spanning the full width of the player block. The round label cell spans both lines and is tinted with the color of the player who plays first.
- Only the dealer has a crib; the other player's crib cell uses `cell_never_bg`. An empty crib cell shows `SYMBOL_CRIB` (🎴) as a hint.
- The fixed header is a single block: one empty label cell spanning both lines, and per player a name cell above a symbol row (`SYMBOL_IN_PLAY` = 🏃, `SYMBOL_HAND` = 🏁) in the player's color. Vertical separators are real 1dp views (`makeVerticalDivider`), between the two sub-columns and between players, in the header, the round blocks and the total row.
- Win condition: **121 pts**. Highest score wins, even mid-round.
- Uses the fixed header / scrollable content pattern (total row inside `tableContainer`) with `ROW_HEIGHT_DP = 48`.
