# 7 Wonders

- **Players**: 3–7 (equipment: board box). `gameType = "sevenwonders"`.
- One-shot tally per player, entered progressively and saved manually via the **Finish game**
  button — same convention as Ticket to Ride / Harmonies, since the Guilds category is a
  variable-length list with no reliable "all fields filled" completion trigger.
- Row-based grid (one row per category, one column per player), same family as
  `HarmoniesGameActivity` / `TicketToRideGameActivity`: fixed `headerContainer` (player names),
  scrollable `tableContainer`, fixed `btnFinishGame` below the scroll.
- **Category labels are icon-only** (no text) — color + emoji identify the row, keeping the
  grid compact: ⚔️ Military (red), 🪙 Coins (gold), 🏛️ Wonder (brown), 🏠 Civilian (blue),
  ⚖️ Commerce (yellow), ⚗️ Guilds (purple), 🔬 Science (green).

## Categories

| Category | Rule |
|---|---|
| ⚔️ Military | Net sum of Victory/Defeat conflict tokens (+1/+3/+5 per age won, −1 per loss). Entered as a single **signed** value (picker: −6 to +18). |
| 🪙 Coins | **Two rows**: the player enters their raw coin count on the input row; the row directly below is **calculated** and shows `coins ÷ 3` (rounded down) automatically — mirrors how Science shows its computed subtotal below its input row. |
| 🏛️ Wonder | Fixed points printed on the built Wonder stages. Single subtotal (picker: 0–40). |
| 🏠 Civilian | Sum of points printed on built blue cards. Single subtotal (picker: 0–60). |
| ⚖️ Commerce | Points from qualifying commercial structures. Single subtotal (picker: 0–30). |
| ⚗️ Guilds | **Entered one card at a time**, like Harmonies' Animal cards or Ticket to Ride's destination tickets: tap "+" to add a completed guild card's point value, tap an existing entry to edit or delete it. Rows are aligned across players as slots even though players may have different guild counts. A subtotal row below sums all entries. |
| 🔬 Science | **The only multi-input category**: a single row whose cell holds **3 tappable sub-buttons side by side** — 🧭 Compass, ⚙️ Gear, 📜 Tablet — one per symbol type, each opening its own 0–12 picker. The row directly below is calculated and shows the computed science score automatically: `compass² + gear² + tablet² + 7 × min(compass, gear, tablet)`. |

Total = Military + Coins÷3 + Wonder + Civilian + Commerce + Guilds + Science. Highest total wins.

## UI notes

- `SevenWondersPlayerScore` keeps the 4 flat categories plus Coins and the 3 Science symbols as
  nullable `Int`, and `guildEntries: MutableList<Int>` for the variable-length Guild list —
  same split as `HarmoniesPlayerScore` (fixed categories map vs. a list for Animal cards).
- `getCoinPoints()` = `coins / 3` (integer division). `getScienceScore()` and `getGuildsTotal()`
  are likewise computed, never stored.
- `isComplete()` only checks the fixed categories (Military, Coins, Wonder, Civilian, Commerce,
  the 3 Science symbols) — an empty Guild list is a valid final state, so it is excluded from
  the completion check, same convention as Harmonies' Animal cards. It is not used to
  auto-trigger game end; the player explicitly taps **Finish game**.
- Score coloring: standard best/worst rule, applied to the Coins and Science *subtotal* rows
  and to the flat category rows once all players have entered a value — not to the raw Science
  symbol sub-buttons or individual Guild entries, which have no intrinsic better/worse direction
  on their own.
