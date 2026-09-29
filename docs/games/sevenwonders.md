# 7 Wonders

- **Players**: 3–7 (equipment: board box). `gameType = "sevenwonders"`.
- One-shot tally per player (no rounds) — same convention as Akropolis / Wingspan / Harmonies.
  ScoreHub does not simulate card drafting; the player computes each category from their
  physical city/Wonder board and enters the subtotal directly.
- **Categories** (official scoring-sheet order):
  1. 🗡️ **Military** — net sum of Victory/Defeat conflict tokens (+1/+3/+5 per age won, −1 per
     loss). Entered as a single **signed** value (can be negative). Picker range: −6 to +18.
  2. 🪙 **Coins** — the player enters their raw coin count; the app computes `coins / 3`
     (rounded down) as victory points. Leftover coins (0–2) score nothing.
  3. 🏛️ **Wonder** — fixed points printed on the built Wonder stages. Single subtotal.
  4. 🏠 **Civilian** (blue cards) — sum of points printed on built blue cards. Single subtotal.
  5. 💰 **Commerce** (yellow cards, Age III) — points from qualifying yellow cards. Single subtotal.
  6. ⚗️ **Guilds** (purple cards, Age III) — value depends on city/neighbor configuration.
     Single subtotal.
  7. 🔬 **Science** (green cards) — **the only multi-input category**: the player enters three
     separate counts — Compass, Gear, Tablet — one per symbol type. The app computes:
     `compass² + gear² + tablet² + 7 × min(compass, gear, tablet)`
     (squares reward specializing in one symbol; the `+7` bonus per complete set rewards
     spreading across all three). Mirrors Wingspan's 3-habitat sub-row grouping in the UI.
- **Total** = sum of all 7 categories. Highest total wins.
- Uses the **one-shot grid** layout (icon/label column + one column per player), same family as
  `AkropolisGameActivity` / `WingspanGameActivity`. The Science row is built as a 4-sub-row group
  (Compass / Gear / Tablet / computed subtotal) exactly like Wingspan's bird-habitat group —
  only the Compass/Gear/Tablet sub-cells are tappable pickers; the subtotal sub-cell is
  calculated and color-coded like any other subtotal.
- **Score coloring**: standard best/worst rule across all players, applied to the row subtotal
  cells and the grand total (not to the raw Compass/Gear/Tablet sub-cells, which have no
  intrinsic "better/worse" direction on their own).
