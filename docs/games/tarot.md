# Tarot

- **Players**: 3–5 (equipment: board box / dedicated 78-card deck).
- Round label cell tinted with the declarer's color.
- 3-step round entry dialog: declarer/contract/bouts → options (optional) → points made.
- Score limit: **1,000 pts**. Highest total wins. Scores are zero-sum per round (declarer vs. defenders/partner).
- Uses the fixed header / scrollable content pattern (header = player names, scroll = round rows + add-round row + total row).
- **Round cell** (single `FrameLayout`, `makeRoundCell`): signed score centered (green = camp won, red = camp lost); bottom-right shows the camp's card points (declarer/partner = points made, defense = 91 − points made) followed by the round's symbols. Tapping a cell opens a hint dialog explaining only the symbols present in that cell.
