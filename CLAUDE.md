# ScoreHub — Instructions for Claude

## Project overview

**ScoreHub** is an Android board game score tracking application, built in Kotlin using a View-based architecture (not Compose). It currently supports Cactus, Cribbage, Escoba, Skyjo, Tarot, Wingspan, and Yahtzee.

- **Package** : `com.github.trivialloop.scorehub`
- **Min SDK** : 24 (Android 7.0)
- **Target SDK** : 34
- **Language** : Kotlin
- **UI** : XML Views + ViewBinding (no Jetpack Compose)
- **DB** : Room (SQLite)
- **License** : GPL v3

---

## Architecture

```
app/src/main/java/com/github/trivialloop/scorehub/
├── MainActivity.kt                  # Home screen, game selection
├── GeneralStatsActivity.kt          # Cross-game general statistics
├── SettingsActivity.kt              # Language + theme
├── ScoreHubApplication.kt           # Application class (theme init)
├── data/
│   ├── AppDatabase.kt               # Room database singleton
│   ├── Player.kt                    # Player entity (id, name, color ARGB, createdAt)
│   ├── GameResult.kt                # Result entity (gameType, playerId, score, isWinner, isDraw)
│   ├── PlayerDao.kt                 # Player CRUD
│   ├── GameResultDao.kt             # Stats + top20 queries
│   └── PlayersColors.kt             # Available color palette
├── games/
│   ├── cactus/
│   ├── cribbage/
│   ├── escoba/
│   ├── skyjo/
│   ├── tarot/
│   ├── wingspan/
│   └── yahtzee/
└── utils/
    ├── LocaleHelper.kt              # Runtime language switching
    ├── ScoreColorHelper.kt          # Shared utility: scoreColorRole()
    └── ThemeHelper.kt               # Light / Dark / System
```

---

## Important rules

### General
- All variable names, function names, and comments must be in **English**.

### Database
- **Never modify** `AppDatabase` without creating a **Room migration** (`addMigrations(...)` in the builder).
- Current version is **`version = 1`** — any column or table addition must increment this number and provide the SQL migration script.
- `GameResult.gameType` is a plain string (`"yahtzee"`, `"skyjo"`, etc., not an enum).

---

## UI / Theme

- Theme is `Theme.MaterialComponents.DayNight.NoActionBar`.
- Always use **semantic colors** (`?attr/colorPrimary`, `?android:attr/colorBackground`, etc.) rather than hardcoded values, so dark mode works automatically.
- Adaptive colors (game cells) are defined in `res/values/colors.xml` **and** `res/values-night/colors.xml`.

### Unified cell color system (all games)

All game grids share a single set of semantic color names. **Never add per-game color names.**

| Color name | Light | Dark | Usage |
|---|---|---|---|
| `cell_editable_bg` | `#FFF8E1` | `#454545` | Empty cell that can be filled right now |
| `cell_editable_filled_bg` | `#FFFFFF` | `#2C2C2C` | Cell already filled but still editable |
| `cell_locked_bg` | `#F0F0F0` | `#1A1A1A` | Cell filled and locked (no longer editable) |
| `cell_never_bg` | `#BDBDBD` | `#000000` | Cell that can never be filled (e.g. first-player crib column) |
| `score_cell_background` | `#FFFFFF` | `#2C2C2C` | Neutral/read-only score cell background |
| `score_cell_text` | `#000000` | `#FFFFFF` | Neutral score text |
| `score_text_best` | `#4CAF50` | `#4CAF50` | Best score text (green) |
| `score_text_worst` | `#F44336` | `#F44336` | Worst score text (red) |
| `cell_border` | `#CCCCCC` | `#555555` | Cell stroke/border |
| `header_cell_background` | `#D3D3D3` | `#3A3A3A` | Header/label cell background |
| `header_cell_text` | `#000000` | `#FFFFFF` | Header/label cell text |

Legacy aliases (e.g. `skyjo_score_green`, `cactus_cell_border`) remain in `colors.xml` as `@color/` references to the canonical names above. Do not use legacy names in new code.

### Score coloring rules (uniform across all games)

Per row (manche / category row / etc.):
- **All values identical** → all neutral.
- **Minimum value** → `score_text_best` (green), **bold**.
- **Maximum value** → `score_text_worst` (red), **bold**.
- **Everything else** → `score_cell_text` (neutral).

For Skyjo (lower = better): minimum → red, maximum → green (use `ScoreColorHelper.scoreColorRole(lowerIsBetter = true)`).

Use `ScoreColorHelper.scoreColorRole(value, allValues)` from `utils/ScoreColorHelper.kt` for all score color decisions.

### Editable cell visual convention

- **Pencil prefix**: when a cell is already filled but still editable, when clicking and the cell, prefix the title with a ✏ emoji.
- **Background color**: use `cell_editable_bg` for empty editable cells, `cell_editable_filled_bg` for filled+editable.
- **Keyboard auto-open**: all score input dialogs must call `dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)` and `editText.requestFocus()` so the soft keyboard opens immediately on dialog show.

---

## Localisation

- The app supports **English** (`res/values/strings.xml`) and **French** (`res/values-fr/strings.xml`).
- Every new string must be added to **both files**.
- Never hardcode text in Kotlin code or XML layouts.
- Language switching is handled at runtime via `LocaleHelper` — every Activity must override `attachBaseContext`.

---

## Player selection — uniform behaviour

All `PlayerSelectionActivity` classes must follow this behaviour (same as Cribbage):

- **Maximum exceeded**: if the user tries to check a player when the maximum is already reached, uncheck it immediately and show a Toast with the max-players message.
- **Minimum not met**: if the user taps "Start game" with fewer than the minimum, show a Toast with the min-players message; do not start the game.
- The current set of checked players is always preserved across both error cases.

---

## Game grids

- Grids are built **entirely in code** inside each `GameActivity` (no RecyclerView or Adapter).
- Player names are always truncated with `maxLines = 1` + `ellipsize = TruncateAt.END`.
- The `ScrollView` with `fillViewport=true` handles overflow when there are many rounds.

### Fixed row height (Cribbage / Escoba pattern)

For multi-column grids (Cribbage, Escoba) all rows use a **fixed height** (`ROW_HEIGHT_DP = 48dp`) so cells align perfectly across the header, round rows, and total row.

### In-play (pegging) cells

Games with a pegging phase (Cribbage, Escoba) use a `[−] score [+]` cell with weight 1.5f (wider than the hand cell at 1f) to give larger tap targets. The score text color follows the same best/worst/neutral rules compared across all players in that row.

---

## Game-specific notes

### Yahtzee
- Highest score = best. Column coloring: highest grand total = green, lowest = red.
- **Chance category**: `getPossibleValues()` returns `30 downTo 5` so the dialog opens showing large values first.
- Last-filled category: re-editable with `"✏ "` prefix and `cell_editable_filled_bg` background.
- Solo games (1 player) do not count toward win/loss statistics.

### Skyjo
- Lower score = best. Score limit: **100 pts**.
- Finisher penalty: if not strictly lowest alone, score is **doubled**.
- Coloring: finisher cell uses background color (green/red), others use text color.

### Escoba
- 2-column layout per player per round: **In play** (in-play, weight 1.5) + **End of round** (hand, weight 1).
- Pegging via +/− buttons; locked once any player enters their end-of-round score.
- End-of-round score: dialog with auto-focus keyboard; pencil prefix when re-editing.
- Score limit: **21 pts**. Highest score wins.
- Previous round editable until the new round gets any pegging activity.

### Cribbage
- 3-column layout per player: **In play** (in-play) + **End of round** + **Crib**.
- Only the dealer has a crib; the other player's crib column uses `cell_never_bg`.
- Win condition: **121 pts**. Highest score wins.

### Cactus / Skyjo / Tarot
- Round label cell tinted with the **first player's** (or declarer's) color.
- Score coloring: min = green text, max = red text, equal = neutral.

### Wingspan
- One-shot grid (no rounds): all category rows colored per row once all players have entered scores.
- Highest total = winner.

---

## Statistics

- **Yahtzee, Cactus, Escoba, Cribbage, Wingspan, Tarot**: highest score = best. `getBestScoreByPlayer` = MAX.
- **Skyjo**: lowest score = best. `getBestScoreByPlayer` returns MIN (inverted in `SkyjoStatsActivity`).
- `getCountedGamesPlayedByPlayer` excludes solo games (sessions where `playedAt` appears for only one player).

---

## Tests

- Unit tests in `app/src/test/` (JVM, no Android context).
- Instrumented tests in `app/src/androidTest/` (Room in-memory).
- Test file naming: `<Game>ScoreManagerTest.kt` (e.g. `YahtzeeScoreManagerTest.kt`).
- Existing test files:
  - `YahtzeeScoreManagerTest.kt` — Yahtzee scoring logic
  - `SkyjoScoreManagerTest.kt` — Skyjo rounds, penalties, colors
  - `EscobaScoreManagerTest.kt` — Escoba rounds, pegging, totals
  - `CribbageScoreManagerTest.kt` — Cribbage rounds, pegging, crib
  - `TarotScoreManagerTest.kt` — Tarot zero-sum, scoring
  - `WingspanScoreManagerTest.kt` — Wingspan category totals
  - `CactusScoreManagerTest.kt` — Cactus points, cell colors
  - `DatabaseTest.kt` — Room DAOs

---

## CI/CD

- GitHub Actions: `.github/workflows/android-ci-cd.yml`
- Tests: `./gradlew testFossDebugUnitTest`
- Lint: `./gradlew lintFossDebug`
- Automatic release if: tag does not exist + fastlane changelog present + entry in `CHANGELOG.md`.
- Flavors `foss` (F-Droid) and `gplay` (Google Play) — same `applicationId` for now.

---

## Versioning

Format: `MAJOR.MINOR.PATCH` (SemVer)

| Change type | Version |
|---|---|
| Bug fix | PATCH (`1.x.y+1`) |
| New backward-compatible feature (new game, new stat…) | MINOR (`1.x+1.0`) |
| Major UX overhaul or breaking change | MAJOR (`x+1.0.0`) |

For each new version:
1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`
2. Create `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
3. Create `fastlane/metadata/android/fr-FR/changelogs/<versionCode>.txt`
4. Add a `## [x.y.z]` entry in `CHANGELOG.md`

---

## Adding a new game

The main screen reads its game list from a **central registry** — no change to
`MainActivity` or `activity_main.xml` is needed when adding a game.

### Steps
1. **Register the game** in `GameRegistry.kt` — add one `GameDefinition` entry to `ALL_GAMES`.
2. **Create the game package** `games/<gamename>/` with:
   - `<Game>ScoreManager.kt` — data model and business rules
   - `<Game>PlayerSelectionActivity.kt` — reuse `ActivityPlayerSelectionBinding`; enforce min/max player rules with Toast messages
   - `<Game>GameActivity.kt` — grid built in code; use shared color names; open keyboard immediately on dialogs
   - `<Game>StatsActivity.kt` — reuse `item_player_stats.xml`
   - `<Game>Top20Activity.kt` — reuse `item_top20.xml`
3. **Add layouts** `activity_<game>_game.xml`, `activity_<game>_stats.xml`, `activity_<game>_top20.xml`.
4. **Declare the activities** in `AndroidManifest.xml`.
5. **Add a menu** `menu_<game>_player_selection.xml`.
6. **Add strings** in `values/strings.xml` and `values-fr/strings.xml`.
7. **Add a drawable icon** referenced by `GameDefinition.iconResId`.
8. **Add a section** in `activity_general_stats.xml` and `GeneralStatsActivity.kt`.
9. **Add unit tests** in `<Game>ScoreManagerTest.kt`. Mandatory — cover scoring logic, state helpers, and edge cases.
