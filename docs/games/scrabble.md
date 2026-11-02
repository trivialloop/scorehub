# Scrabble

- **Players**: 2–4 (equipment: board box). `gameType = "scrabble"`.
- ScoreHub does not simulate the board or the dictionary: the player computes the score of each word and enters it.
- Turn-based, one active player at a time, but the grid is laid out like a round-based game: **one row per round** (turn n of every player), one column per player. Uses the fixed header / scrollable content pattern (`headerContainer` + `scrollView`/`tableContainer`, total row inside `tableContainer`, `LABEL_COL_DP = 65`).
- **Active turn cell**: list of entries, a running `= sum` row when there is more than one entry, then a `[+]` button (add a score, any number of times — same idea as Harmonies' animal cards) and a `[✅]` button that closes the turn and passes to the next player (✅ with no entry = pass).
- Entries are **signed integers** (−999..999) typed in a dialog (keyboard opens immediately). Tap an entry to edit it (✏️ prefix) or delete it; this stays possible on past turns until the game is finished.
- Entries can also hold manual corrections (e.g. a challenge penalty, entered as a negative number).
- **No score limit and no automatic end**: the game is closed manually with the fixed **Finish game** button below the scroll area.
- **End-of-game step** (single dialog): pick who went out first (or *Nobody* for a blocked game) and enter the value of the tiles left on each rack. `ScrabbleEndGame.computeAdjustments` subtracts every rack and gives the player who went out the sum of the others' racks. The result is shown in an extra row (`GameSymbols.INPUT_LATIN_LETTERS`) above the Total row; it is only displayed if at least one adjustment is non-zero. Then inputs are locked, `GameResult`s saved (shared `playedAt`) and `GameResultsDialog` shown.
- **Row heights**: each cell height is computed by hand (`cellHeightDp`) and the row takes the tallest one — a WRAP_CONTENT horizontal row ignores MATCH_PARENT children when a sibling has a fixed height, which clipped or split cells.
- Round rows: once every player has closed the round, the round sums are colored best/worst with `ScoreColorRole`. The total row is colored live.
- Highest total wins. Draw if several players share the top score.
