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
- **Destination tickets** — a single unified list per player, `ticketEntries: MutableList<Int>`,
  where each entry is a *signed* point value: positive = completed ticket (added to the
  score), negative = failed ticket (subtracted from the score). This replaces the earlier
  two-list (`completedTickets` / `failedTickets`) design.
  - **Header row**: instead of a single "+" cell, each player's header cell shows two tap
    targets side by side — ✅ (add a completed ticket) and ❌ (add a failed ticket). Tapping
    either opens the same value picker (1–30); the app stores the value with the
    corresponding sign.
  - **Entry rows**: each entry is displayed as an icon (✅ or ❌, matching the entry's sign)
    next to its signed score (`+12` or `-9`). The two are independently tappable:
    - Tapping the **icon** flips the entry's sign in place (completed ↔ failed) — the
      displayed score sign updates accordingly without needing to re-pick the value.
    - Tapping the **score** opens an edit/delete dialog (prefixed with the entry's icon),
      letting the player change the magnitude (keeping the current sign) or delete the
      entry entirely.
  - Rows are laid out as aligned "slots" across all players (like Farkle's completed-round
    cells), so the grid stays tabular even though players may have different numbers of
    entries.
  - `getCompletedTicketsPoints()` sums only positive entries; `getFailedTicketsPoints()`
    sums the magnitude of negative entries (returned as a positive number, for display);
    `getTicketsTotal()` is the direct sum of all signed entries and is what feeds `getTotal()`.
- **Longest path bonus**: a single toggle row. Tapping a player's cell flips
  `hasLongestPath`; multiple players can hold it simultaneously in case of a tie.
  Worth a flat **+10** points.
- **Total** = route points + net ticket total (`getTicketsTotal()`) + longest path bonus.
  Highest total wins. Colored (green/red) only after the game is finished.
- Uses the fixed header / scrollable content pattern (`LABEL_COL_DP = 65`), with a
  `btnFinishGame` action bar below the scroll area that locks all inputs, saves the
  `GameResult`s, and shows the shared `GameResultsDialog`.
