# Harmonies

- **Players**: 1–4 (equipment: board box). Solo games (1 player) do not count toward win/loss statistics — same convention as Yahtzee.
- Harmonies is a spatial token-placement game (personal board + stacked tokens). Like Akropolis, Wingspan and Ticket to Ride, ScoreHub does **not** simulate the board itself — the player computes each category's subtotal from their physical board and enters it directly.
- Not round-based: a single one-shot tally per player, entered progressively and saved manually via the **Finish game** button — same convention as Ticket to Ride, since the number of completed Animal cards varies per player and there's no reliable "all fields filled" completion trigger.
- Uses the fixed header / scrollable content pattern (`headerContainer` + `scrollView`/`tableContainer`), built the same way as `TicketToRideGameActivity`: smaller fixed-height rows (`ROW_HEIGHT_DP = 44`, `ANIMAL_ROW_HEIGHT_DP = 38` for animal card slot rows) and a wider label column (`LABEL_COL_DP = 80`) to fit the category names comfortably.

## Scoring categories

| Category | Rule |
|---|---|
| 🌲 Trees | Sum of all Tree scores. A Tree is 1 green token on top of 0/1/2 brown tokens (height 1/2/3), scoring 1/3/7 pts per Tree. Entered as a single subtotal. |
| ⛰️ Mountains | Sum of all Mountain scores. A Mountain is a stack of 1-3 grey tokens, scoring 1/3/7 pts by height — but **0 pts if not adjacent to another Mountain**. Entered as a single subtotal. |
| 🌾 Fields | 5 pts per separate group of 2+ contiguous yellow tokens (a bigger group is still just one Field, worth 5 pts). Entered as a single subtotal, restricted to multiples of 5. |
| 🏠 Buildings | 5 pts per Building (1 red token on brown/grey/red) that is surrounded by at least 3 differently colored tokens — otherwise 0 pts. Entered as a single subtotal, restricted to multiples of 5. |
| 🌊 River | Points for the longest river of blue tokens (Side A of the personal board), or the islands created by water (Side B) — either way, a single subtotal entered by the player. |
| 🦌 Animals | **Entered one card at a time**, like Ticket to Ride's destination tickets: tap "+" to add a completed Animal card's score, tap an existing entry to edit or delete it. Rows are aligned across players as "slots" even though players may have different numbers of cards. |

Total = the 5 fixed-category subtotals + the sum of all Animal card entries. Highest total wins.

## UI notes

- `HarmoniesPlayerScore` splits the model in two: `scores: MutableMap<HarmoniesCategory, Int?>` for the 5 fixed categories (`getCategoryTotal()`), and `animalEntries: MutableList<Int>` for the variable-length Animal card list (`getAnimalsTotal()`). `getTotal()` combines both.
- `HarmoniesCategory.getPossibleValues()`: Trees/Mountains use `0..50`; Fields/Buildings are restricted to multiples of 5 (`0, 5, 10, … 50`) since each Field/Building is worth exactly 5 pts; River uses `0..40`.
- `HARMONIES_ANIMAL_CARD_VALUES` (`0..30`) is the picker range for a single Animal card's score, added via "+".
- The Animals section follows the exact same 3-part layout as Ticket to Ride's ticket lists: a "+" header row, one slot row per entry (aligned across players), and a "Subtotal" row.
