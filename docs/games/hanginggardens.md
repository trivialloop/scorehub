# Hanging Gardens (Les Jardins Suspendus)

- **Players**: 1–5 (equipment: board box). `gameType = "hanginggardens"`. Solo games do not count toward win/loss statistics.
- One-shot tally per player, saved manually via **Finish game** (same convention as Ticket to Ride / Harmonies).
- Uses the fixed header / scrollable content pattern (`headerContainer` + `scrollView`/`tableContainer`) with `LABEL_COL_DP = 65`, `ROW_HEIGHT_DP = 44` and `ENTRY_ROW_HEIGHT_DP = 38` for entry slot rows.
- Embellishment tiles add flowers/animals/trees, so they are already counted in those rows.
- Highest total wins. Official tie-break (most tools + gold) is not tracked.

## Icon-only labels

Row labels are **emoji on a colored background** (no text, so no translation needed), in the same spirit as Akropolis. The only strings kept are dialogs, buttons and the game name.

| Row | Emoji | Background |
|---|---|---|
| Irrigation | 💧 | light blue |
| Blue / Red / Yellow flowers | 🌸 | blue / red / yellow |
| Dragon tree | 🌳 | dark green (`COLOR_TREES`, `0xFF1B5E20`) |
| Cedar | 🌲 | dark green (`COLOR_TREES`) |
| Palm | 🌴 | dark green (`COLOR_TREES`) |
| Trees subtotal | Σ | dark green (`COLOR_TREES`) |
| Animals (+ subtotal Σ) | 🦚 | orange |
| Characters (+ subtotal Σ) | 🧑 | brown |
| Royal objectives | 👑 | purple |
| Total | Σ | grey (`cell_calculated_bg`) |

- The three tree species and their subtotal share one green; species are told apart by their emoji only.
- Flower dialogs prefix the title with the matching colored square (🟦🌸 / 🟥🌸 / 🟨🌸) via `RowStyle.dialogEmoji`, since the flower emoji alone does not carry the color.
- Entry slot rows and the subtotal row share the color of their section, so each block reads as one colored zone.
- Subtotal rows show only `Σ` in the label cell; the value cells use the calculated style.

## Rows and pickers

- **Irrigation**: picker with `0` + the 6 point values (`HANGING_GARDENS_IRRIGATION_VALUES`).
- **Flowers** (Blue/Red/Yellow): `0..24` picker, only the largest connected group of each color scores (1 pt per flower).
- **Trees**: one "+" section per species, each with its own picker (`HangingGardensTree.getPossibleValues()`):
  - Dragon: 2, 7
  - Cedar: 2, 5, 9
  - Palm: 3, 7, 12, 18
- **Animals**: "+" entries, `0..30` picker.
- **Characters**: "+" entries, `0..30` picker.
- **Royal objectives**: `0..14` picker.

Tap an entry to edit or delete it. Entry rows are aligned across players as "slots".

## Model

- `HangingGardensPlayerScore` keeps fixed blocks as `Int?` (`irrigation`, `objectives`, `flowers` map) and variable blocks as `MutableList<Int>` (`animalEntries`, `humanEntries`; `treeEntries` is a map species → list).
- `getTotal()` = irrigation + flowers + trees + animals + humans + objectives.
