# Ligretto

- **Players**: 2–12 (equipment: cards). Also known as Racing Demon / Nerts / Dutch Blitz in other editions; ScoreHub tracks the standard Ligretto scoring only (it does not simulate the real-time card play).
- **`gameType`**: `"ligretto"`.
- **Round structure**: at the end of each real-time round, every player reports two numbers:
  - **Played** — how many cards they placed onto the shared central piles that round (any value, no cap enforced by the app beyond a generous upper bound).
  - **Left** — how many cards remained in their own Ligretto stack when the round ended (0–10, since a Ligretto stack starts with 10 cards).
  - Round score = `Played − 2 × Left`.
- **Finisher**: the round label can optionally be tinted with the color of the player who called "Ligretto!" first (i.e. emptied their stack, `Left = 0`). Picking a finisher is optional — it's a visual aid, not a rule enforced by the app, since the physical game is scored the same regardless of who called it first.
- **Score limit**: **99 pts**. Highest total wins; if multiple players are tied at ≥ 99, the highest total wins (same tie-break as the physical rules).
- Uses the fixed header / scrollable content pattern (total row inside `tableContainer`), `LABEL_COL_DP = 65`, `ROW_HEIGHT_DP = 48`. The header is a single block: one empty label cell spanning both lines, and per player a name cell above a symbol row (`SYMBOL_PLAYED` = 🃏, `SYMBOL_LEFT` = 📚) in the player's color. Two equal-width sub-columns per player, separated by real 1dp views (`makeVerticalDivider`), also between players.
- Cells follow the Cactus look: the points given by the entry are centered (**Played** `+n`, **Left** `−2n`), and for **Left** the number of cards selected is tiny in the bottom-right corner. Both are entered through list pickers (Played 0–40, Left 0–10), with pencil prefix and preselected value when re-editing.
- Score coloring: per round and per column, the points follow the standard best/worst/neutral rule across all players in that row (highest = green, lowest = red).
- The round label cell is tinted with the color of the player who called "Ligretto!" (optional, purely visual).
