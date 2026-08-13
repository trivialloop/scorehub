# Qwixx

- **Players**: 2–5 (equipment: board box).
- Not round-based in the header/scroll sense: uses a full custom grid built from cell width/height computed from screen size (`computeCellSize()`), 4 color rows (Red/Yellow ascending, Green/Blue descending) + penalty row + total, per player.
- Row score = triangular sum of checked boxes (locking the last number of a row counts as one extra box).
- Turn structure: `ALL` phase (every player may check one number or pass) → `ACTIVE_SECOND` phase (active player's mandatory/optional second action).
- Game ends when any player reaches 4 penalties, or 2 color rows are globally locked.
