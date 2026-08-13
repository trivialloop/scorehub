# Yahtzee

- **Players**: 1–8 (equipment: dice). Solo games (1 player) do not count toward win/loss statistics.
- **Best score**: highest grand total wins. Column coloring: highest grand total = green, lowest = red.
- **Chance category**: `getPossibleValues()` returns `30 downTo 5` so the dialog opens showing large values first.
- **Last-filled category**: re-editable with `"✏ "` dialog-title prefix and `cell_editable_filled_bg` background.
- Not round-based in the header/scroll sense — uses its own player-column layout (`YahtzeeGameActivity`), with a category label column on the left and one scrollable column per player, centering the active player when there are more than 5 players.
