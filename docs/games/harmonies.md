# Harmonies

- **Players**: 1–4 (equipment: board box). Solo games (1 player) do not count toward win/loss statistics — same convention as Yahtzee.
- Harmonies is a spatial token-placement game (personal board + stacked tokens). Like Akropolis and Wingspan, ScoreHub does **not** simulate the board itself — the player computes each category's subtotal from their physical board and enters it directly.
- One-shot grid (no rounds), built the same way as `WingspanGameActivity`: a label column + one column per player, each with 6 category rows + a total row. All rows are colored per row once every player has entered a score for that category (highest = green, lowest = red).

## Scoring categories

| Category | Rule |
|---|---|
| 🌲 Trees | Sum of all Tree scores. A Tree is 1 green token on top of 0/1/2 brown tokens (height 1/2/3), scoring 1/3/7 pts per Tree. |
| ⛰️ Mountains | Sum of all Mountain scores. A Mountain is a stack of 1-3 grey tokens, scoring 1/3/7 pts by height — but **0 pts if not adjacent to another Mountain**. |
| 🌾 Fields | 5 pts per separate group of 2+ contiguous yellow tokens (a bigger group is still just one Field, worth 5 pts). |
| 🏠 Buildings | 5 pts per Building (1 red token on brown/grey/red) that is surrounded by at least 3 differently colored tokens — otherwise 0 pts. |
| 🌊 River | Points for the longest river of blue tokens (Side A of the personal board), or the islands created by water (Side B) — either way, a single subtotal entered by the player. |
| 🦌 Animals | Sum of points scored across all placed Animal cards (and the optional Nature's Spirit card, scored the same way). |

Total = sum of all 6 categories. Highest total wins.

## UI notes

- `HarmoniesCategory.getPossibleValues()`: Trees/Mountains use `0..50`; Fields/Buildings are restricted to multiples of 5 (`0, 5, 10, … 50`) since each Field/Building is worth exactly 5 pts; River uses `0..40`; Animals uses `0..99` (up to 4 cards).
- Uses `LABEL_COL_DP = 65` for the category label column, consistent with every other game.
- Not round-based, so it does **not** use the fixed header / scrollable content pattern — it follows the Wingspan/Akropolis horizontal-scroll, one-shot-grid layout instead (`scoreTableContainer` in `activity_harmonies_game.xml`).
