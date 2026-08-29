# Ticket to Ride

- **Players**: 2–5 (equipment: board box).
- **`gameType`**: `"ticket_to_ride"`.
- Not round-based: a single one-shot tally per player, entered progressively during/after
  the physical game and saved manually via the **Finish game** button (there is no natural
  "all fields filled" completion trigger like Wingspan/Akropolis, since ticket counts vary).
- **Route (tronçon) points** — for each of the 6 route lengths (1–6 train cars), the app
  tracks how many routes of that length the player claimed. Official points table:

  | Length | Points |
  |---|---|
  | 1 | 1 |
  | 2 | 2 |
  | 3 | 4 |
  | 4 | 7 |
  | 5 | 10 |
  | 6 | 15 |

  Tap a length cell to open a picker (0–12) and set the count for that player.
- **Destination tickets** — two independent lists per player, entered the same way as
  Farkle's roll entries (tap **+** to add, pick the ticket's printed point value from a
  list, tap an existing entry to edit or delete it):
  - **Completed tickets**: each value is **added** to the score.
  - **Failed tickets**: each value is **subtracted** from the score.
  - Rows are laid out as aligned "slots" across all players (like Farkle's completed-round
    cells), so the grid stays tabular even though players may have different numbers of
    tickets.
- **Longest path bonus**: a single toggle row. Tapping a player's cell flips
  `hasLongestPath`; multiple players can hold it simultaneously in case of a tie.
  Worth a flat **+10** points.
- **Total** = route points + completed tickets − failed tickets + longest path bonus.
  Highest total wins. Colored (green/red) only after the game is finished.
- Uses the fixed header / scrollable content pattern (`LABEL_COL_DP = 65`), with a
  `btnFinishGame` action bar below the scroll area that locks all inputs, saves the
  `GameResult`s, and shows the shared `GameResultsDialog`.
