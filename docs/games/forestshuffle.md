# Forest Shuffle (Forêt Mixte)

- **Players**: 2–5 (equipment: board box). Displayed as "Forêt Mixte" in French, "Forest Shuffle" in English; DB key `forestshuffle`.
- Forest Shuffle (Lookout Games) is a card game where each player grows a forest of trees and attaches cards to their top, bottom, left and right sides. Card-by-card scoring depends on the whole forest, so ScoreHub does **not** simulate it — each player computes four subtotals at the end of the game and enters them directly (same philosophy as Akropolis / Wingspan / Harmonies).
- Not round-based: one-shot tally per player, saved manually with the **Finish game** button (same convention as Harmonies / Ticket to Ride).
- Uses the fixed header / scrollable content pattern (`headerContainer` + `scrollView`/`tableContainer`) with `LABEL_COL_DP = GameSymbols.LABEL_COL_DP_ONE_SHOT` (56) and `ROW_HEIGHT_DP = 44`. Template: `HarmoniesGameActivity`, minus the variable-length Animal card list.
- The game ends when the third Winter card is revealed.

## Scoring categories (official final-scoring order)

| Row | Rule |
|---|---|
| 🌳 Trees | Points printed on the Tree cards. |
| 🌸 Top / Bottom | Points of the cards attached to the top and bottom of the trees. |
| 🦌 Left / Right | Points of the cards attached to the left and right of the trees. |
| 🪨 Cave | 1 point per card placed under the Cave (plus the Cave's own scoring). |

Total (`Σ`) = sum of the four subtotals. Highest total wins; ties are draws. Solo results (1 player) never count as wins.

## UI notes

- `ForestShufflePlayerScore.scores: MutableMap<ForestShuffleCategory, Int?>`; `getTotal()` treats missing values as 0.
- Every picker uses `0..FOREST_SHUFFLE_MAX_SCORE` (200), a generous ceiling since a large forest can score well above 100.
- Row emojis come from `GameSymbols` (`DECIDUOUS_TREE`, `BLOSSOM`, `DEER`, `ROCK`) — no new symbols were added.
- Colors on category rows are applied immediately (best green / worst red); the total row is only colored once the game is over.
- The game icon is an original vector (`ic_forestshuffle_game.xml`), not the publisher's box art.
