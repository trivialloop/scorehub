# Hanging Gardens (Les Jardins Suspendus)

- **Players**: 1–5 (equipment: board box). `gameType = "hanging_gardens"`. Solo games do not count toward win/loss statistics.
- One-shot tally per player, saved manually via **Finish game** (same convention as Ticket to Ride / Harmonies).
- Rows: Irrigation (6-value picker), Blue/Red/Yellow flowers (0–24 picker), Trees (one "+" section per species: dragon, cedar, palm), Animals ("+" entries), Characters ("+" entries), Royal objectives (0–14 picker), Total.
- Embellishment tiles add flowers/animals/trees, so they are already counted in those rows.
- Highest total wins. Official tie-break (most tools + gold) is not tracked.
- `HangingGardensPlayerScore` keeps fixed blocks as `Int?` and variable blocks as `MutableList<Int>` (`treeEntries` is a map species → list).
