# Ligretto

- **Players**: 2–12 (equipment: cards). Also known as Racing Demon / Nerts / Dutch Blitz in other editions; ScoreHub tracks the standard Ligretto scoring only (it does not simulate the real-time card play).
- **`gameType`**: `"ligretto"`.
- **Round structure**: at the end of each real-time round, every player reports two numbers:
  - **Played** — how many cards they placed onto the shared central piles that round (any value, no cap enforced by the app beyond a generous upper bound).
  - **Left** — how many cards remained in their own Ligretto stack when the round ended (0–10, since a Ligretto stack starts with 10 cards).
  - Round score = `Played − 2 × Left`.
- **Finisher**: the round label can optionally be tinted with the color of the player who called "Ligretto!" first (i.e. emptied their stack, `Left = 0`). Picking a finisher is optional — it's a visual aid, not a rule enforced by the app, since the physical game is scored the same regardless of who called it first.
- **Score limit**: **99 pts**. Highest total wins; if multiple players are tied at ≥ 99, the highest total wins (same tie-break as the physical rules).
- Uses the fixed header / scrollable content pattern, `LABEL_COL_DP = 65`, `ROW_HEIGHT_DP = 48`, two sub-columns per player (**Played** weight 1, **Left** weight 1) — same visual family as Escoba/Cribbage's multi-column round rows.
- Score coloring: per round, the *computed* round score (`Played − 2×Left`) follows the standard best/worst/neutral rule across all players in that row (highest = green, lowest = red). The raw `Played`/`Left` sub-cells themselves are not colored, only the round's overall best/worst styling is reflected via bold cell text once both values are entered — kept simple like Cactus's single-score-per-round display.
