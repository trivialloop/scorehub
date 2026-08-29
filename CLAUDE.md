# ScoreHub — Instructions for Claude

## Project overview

**ScoreHub** is an Android board game score tracking application, built in Kotlin using a View-based architecture (not Compose).

- **Package** : `com.github.trivialloop.scorehub`
- **Min SDK** : 24 (Android 7.0)
- **Target SDK** : 34
- **Language** : Kotlin
- **UI** : XML Views + ViewBinding (no Jetpack Compose)
- **DB** : Room (SQLite)
- **License** : GPL v3

This file covers rules that apply to the **whole project**. Game-specific notes
(scoring rules, layout quirks, player counts, help text, etc.) live in
`docs/games/<game>.md` — one file per game. Read the relevant per-game doc
before touching that game's code, in addition to this file.

---

## Architecture

```
app/src/main/java/com/github/trivialloop/scorehub/
├── MainActivity.kt                  # Home screen, game selection
├── GeneralStatsActivity.kt          # Cross-game general statistics
├── SettingsActivity.kt              # Language + theme
├── ScoreHubApplication.kt           # Application class (theme init)
├── GameRegistry.kt                  # Central registry of all games
├── data/
│   ├── AppDatabase.kt               # Room database singleton
│   ├── Player.kt                    # Player entity (id, name, color ARGB, createdAt)
│   ├── GameResult.kt                # Result entity (gameType, playerId, score, isWinner, isDraw)
│   ├── PlayerDao.kt                 # Player CRUD
│   ├── GameResultDao.kt             # Stats + top20 queries
│   └── PlayersColors.kt             # Available color palette
├── games/
│   ├── akropolis/  cactus/  cribbage/  escoba/  farkle/  flip7/
│   ├── freegame/    ligretto/  oh_hell/  qwixx/  skyjo/  tarot/
│   └── wingspan/  yahtzee/
└── utils/
    ├── LocaleHelper.kt              # Runtime language switching
    ├── ScoreColorHelper.kt          # Shared utility: ScoreColorRole
    └── ThemeHelper.kt               # Light / Dark / System

docs/games/                          # One markdown file per game (rules, UI notes)
```

---

## Important rules

### General
- All variable names, function names, and comments must be in **English**.

### Database
- **Never modify** `AppDatabase` without creating a **Room migration** (`addMigrations(...)` in the builder).
- Current version is **`version = 1`** — any column or table addition must increment this number and provide the SQL migration script.
- `GameResult.gameType` is a plain string (`"yahtzee"`, `"skyjo"`, `"ligretto"`, etc., not an enum).

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

For games where lower is better (e.g. Skyjo): minimum → red, maximum → green (use `ScoreColorRole(value, allValues, higherIsBetter = false)`).

Use `ScoreColorRole(value, allValues)` from `utils/ScoreColorHelper.kt` for all score color decisions.

### Editable cell visual convention

- **Pencil prefix**: when a cell is already filled but still editable, prefix the dialog title with a ✏ emoji.
- **Background color**: use `cell_editable_bg` for empty editable cells, `cell_editable_filled_bg` for filled+editable.
- **Keyboard auto-open**: all score input dialogs must call `dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)` and `editText.requestFocus()` so the soft keyboard opens immediately on dialog show.

### Edge-to-edge window insets (mandatory for every Activity)

Every Activity must apply system bar insets the same way. **Never use `Type.statusBars()` alone** — always use `Type.systemBars()` and apply padding to both `appBarLayout` (top only) and `binding.root` (left/right/bottom), so gesture navigation and horizontal insets (landscape, notches) are handled correctly.

Required in every Activity's `onCreate()`, right after `setContentView`:

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)

ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->

    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

    binding.appBarLayout.setPadding(
        0,
        systemBars.top,
        0,
        0
    )

    binding.root.setPadding(
        systemBars.left,
        0,
        systemBars.right,
        systemBars.bottom
    )

    insets
}
```

**Do NOT** use the older pattern below — it only pads the top of `appBarLayout` via `statusBars()` and never pads `binding.root`, which causes layout/display bugs (e.g. content hidden or misaligned behind system bars) on devices with gesture navigation or side insets:

```kotlin
// ❌ DEPRECATED — do not use
ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
    val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
    binding.appBarLayout.setPadding(0, statusBarInsets.top, 0, 0)
    insets
}
```

This applies to **every screen with a toolbar/appBarLayout**, including one-off screens like Free Game that don't follow the round-based grid pattern.

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

### Label column width — uniform across all round-based games

**All round-based games must use `LABEL_COL_DP = 65` for the first (label/round-number) column.**

This constant is also used by Akropolis (`ICON_COL_DP = 65`) and is the canonical width for all left-side label columns in score grids. Never use a different hardcoded value.

```kotlin
companion object {
    // ...
    private const val LABEL_COL_DP = 65   // uniform with all other round-based games
}
```

The label cell layout params must reference this constant:
```kotlin
layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT)
```

### Row baseline alignment (mandatory for every hand-built grid row)

Every `LinearLayout` row built in code (via a `makeRow()`-style helper) **must** set
`isBaselineAligned = false`. By default, Android's horizontal `LinearLayout` aligns
child views on their text baseline rather than centering them within the row's fixed
height. This is invisible when every cell in a row has the same kind of content, but as
soon as a row mixes empty cells with filled/bold ones — subtotal rows, "add" buttons
next to filled entries, toggle cells (checked vs. unchecked) — the cells render at
slightly different vertical offsets, producing a visible row-to-row misalignment even
though every cell shares the same declared height.

```kotlin
private fun makeRow(heightDp: Int): LinearLayout = LinearLayout(this).apply {
    orientation  = LinearLayout.HORIZONTAL
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
    isBaselineAligned = false   // ← always set this
}
```

`FreeGameActivity.kt`, `QwixxGameActivity.kt`, and `TicketToRideGameActivity.kt` already
follow this rule — use them as reference when adding a new game's grid.

### Fixed header + scrollable content (mandatory for all round-based games)

**All games with rounds MUST use the following layout pattern unconditionally.**

The behaviour is:
- The **header row** (player names / column labels) is always **fixed** at the top of the screen.
- The **round rows AND the total row** are inside the `ScrollView` together — so the total row sits just below the last round row when few rounds exist, and naturally scrolls out of view when many rounds are present (the user scrolls down to see it, and `fullScroll(FOCUS_DOWN)` ensures it is always visible after each update).
- There is **no separate `totalContainer`** outside the scroll.

**XML layout structure** (required in `activity_<game>_game.xml` for every round-based game):
```xml
<!-- Fixed header — always visible -->
<LinearLayout
    android:id="@+id/headerContainer"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical" />

<!-- Scrollable content: round rows + total row -->
<ScrollView
    android:id="@+id/scrollView"
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="1"
    android:fillViewport="true">
    <LinearLayout
        android:id="@+id/tableContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical" />
</ScrollView>
<!-- NO totalContainer outside the scroll -->
```

**Kotlin `buildTable()` pattern** (required — **no conditional logic on screen height**, total always inside `tableContainer`):
```kotlin
private fun buildTable() {
    // Fixed header
    binding.headerContainer.removeAllViews()
    binding.headerContainer.addView(buildHeaderRow())

    // Scrollable: round rows + (optional add-round row) + total
    binding.tableContainer.removeAllViews()
    rounds.forEach { binding.tableContainer.addView(buildRoundRow(it)) }
    // if applicable: if (!gameOver) binding.tableContainer.addView(buildAddRoundRow())
    binding.tableContainer.addView(buildTotalRow())  // ← INSIDE the scroll

    // Always scroll to bottom so the latest round + total are visible
    binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
}
```

**IMPORTANT — do NOT**:
- Put `buildTotalRow()` in a separate `totalContainer` outside the `ScrollView`.
- Use a conditional like `if (totalNaturalHeight > screenHeight)` to switch between layouts. The split header/scroll is always active.
- Declare a `totalContainer` in XML at all.

> Note: a few older games (Cactus, Cribbage, Skyjo, Tarot) still contain a
> conditional-height fallback predating this rule; treat the unconditional
> pattern above as the standard for any **new** game and any refactor.

Games that are **not** round-based (Wingspan, Yahtzee, Akropolis) keep their own layout approach — this rule does not apply to them.

### Fixed row height (Cribbage / Escoba / Farkle / Ligretto pattern)

For multi-column grids with fixed-height rows, all rows use a **fixed height** (`ROW_HEIGHT_DP = 48dp`) so cells align perfectly across the header and round rows.

### In-play cells

Games with an in-play phase (Cribbage, Escoba, Farkle) use a `[−] score [+]` cell with weight 1.5f (wider than the score cell at 1f) to give larger tap targets. The score text color follows the same best/worst/neutral rules compared across all players in that row.

---

## Statistics

- For most games, highest score = best. `getBestScoreByPlayer` = MAX. See each game's doc in `docs/games/` for the exception list (currently only **Skyjo**, where lowest score = best and `getBestScoreByPlayer` is inverted).
- `getCountedGamesPlayedByPlayer` excludes solo games (sessions where `playedAt` appears for only one player).

---

## Tests

- Unit tests in `app/src/test/` (JVM, no Android context).
- Instrumented tests in `app/src/androidTest/` (Room in-memory).
- Test file naming: `<Game>ScoreManagerTest.kt` (e.g. `YahtzeeScoreManagerTest.kt`).
- One test file per game score manager, plus `DatabaseTest.kt` for Room DAOs.

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
   - If the game has **rounds**: use the **fixed header / scrollable content** XML structure described in the "Game grids" section. The layout must declare `headerContainer`, `scrollView`, and `tableContainer`. There is **no `totalContainer`** — the total row is added inside `tableContainer` at the end of `buildTable()`.
   - If the game has **no rounds** (one-shot scoring like Wingspan/Akropolis): use a single scrollable or horizontal container as appropriate.
4. **Declare the activities** in `AndroidManifest.xml`.
5. **Add menus**:
   - `menu_<game>_player_selection.xml` — must include game statistics, top 20, and the help item.
   - `menu_<game>_game.xml` — contains only the help item.
6. **Add strings** in `values/strings.xml` and `values-fr/strings.xml`.
7. **Add a drawable icon** referenced by `GameDefinition.iconResId`.
8. **Add a section** in `activity_general_stats.xml` and `GeneralStatsActivity.kt`.
9. **Add unit tests** in `<Game>ScoreManagerTest.kt`. Mandatory — cover scoring logic, state helpers, and edge cases.
10. **Add help content** in `ui/HelpDialogs.kt` (see below).
11. **Write `docs/games/<game>.md`** — a short doc following the template of the existing per-game files, covering scoring rules, player counts, and any UI quirks specific to that game.

### Help content (`ui/HelpDialogs.kt`)

- In `getGameHelp()`: add a `GameHelp` entry for the game type with:
  - `players` — number of players supported
  - `objective` — one-sentence summary of the game goal
  - `scoring` — how points are counted
  - `endCondition` — what triggers the end of the game
  - `wikipediaUrl` — Wikipedia URL for the game (resolved per-locale via string resources)
- In `getAppHelp()`: add an `AppHelp` entry with 3–4 concise steps explaining how to use the ScoreHub interface for that game (e.g. how to pick the finisher, how to enter scores, when the game ends in the app).
- Add the corresponding strings in both `values/strings.xml` and `values-fr/strings.xml` following the naming convention:
  - Game rules: `help_<game>_players`, `help_<game>_objective`, `help_<game>_scoring`, `help_<game>_end`, `help_<game>_wikipedia_url`
  - App usage: `app_help_<game>_1` through `app_help_<game>_N`
