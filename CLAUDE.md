# ScoreHub — Instructions for Claude

## Project overview

**ScoreHub** is an Android board game score tracking application, built in Kotlin using a View-based architecture (not Compose). It currently supports Yahtzee and Skyjo.

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
│   ├── yahtzee/
│   │   ├── YahtzeeScoreManager.kt   # Model (YahtzeePlayerScore, YahtzeeCategory)
│   │   ├── YahtzeePlayerSelectionActivity.kt
│   │   ├── YahtzeeGameActivity.kt   # Score grid built programmatically (column-based)
│   │   ├── YahtzeeStatsActivity.kt
│   │   └── YahtzeeTop20Activity.kt
│   ├── skyjo/
│   │   ├── SkyjoScoreManager.kt     # Model (SkyjoRound, SkyjoPlayerState, SkyjoCellColor)
│   │   ├── SkyjoPlayerSelectionActivity.kt
│   │   ├── SkyjoGameActivity.kt     # Grid built programmatically (row-based)
│   │   ├── SkyjoStatsActivity.kt
│   │   └── SkyjoTop20Activity.kt
│   └── wingspan/
│       ├── WingspanScoreManager.kt  # Model (WingspanPlayerScore, WingspanCategory)
│       ├── WingspanPlayerSelectionActivity.kt
│       ├── WingspanGameActivity.kt  # Score grid built programmatically (column-based)
│       ├── WingspanStatsActivity.kt
│       └── WingspanTop20Activity.kt
└── utils/
    ├── LocaleHelper.kt              # Runtime language switching
    └── ThemeHelper.kt               # Light / Dark / System
```

---

## Important rules

### General
- All variable names, function names, and comments must be in **English**.

### Database
- **Never modify** `AppDatabase` without creating a **Room migration** (`addMigrations(...)` in the builder).
- Current version is **`version = 1`** — any column or table addition must increment this number and provide the SQL migration script.
- `GameResult.gameType` is `"yahtzee"` or `"skyjo"` (plain string, not an enum).

### UI / Theme
- Theme is `Theme.MaterialComponents.DayNight.NoActionBar`.
- Always use **semantic colors** (`?attr/colorPrimary`, `?android:attr/colorBackground`, etc.) rather than hardcoded values, so dark mode works automatically.
- Adaptive colors (game cells) are defined in `res/values/colors.xml` **and** `res/values-night/colors.xml`.
- Skyjo-specific colors: `skyjo_score_green`, `skyjo_score_red`, `skyjo_cell_border`.
- Yahtzee-specific colors: `score_cell_background`, `score_cell_text`, `yahtzee_calculated_cell_background`, `header_cell_background`, `yahtzee_bonus_progress_text`.
- Wingspan-specific colors: `wingspan_score_green`, `wingspan_score_red`, `wingspan_cell_border`.

### Localisation
- The app supports **English** (`res/values/strings.xml`) and **French** (`res/values-fr/strings.xml`).
- Every new string must be added to **both files**.
- Never hardcode text in Kotlin code or XML layouts.
- Language switching is handled at runtime via `LocaleHelper` — every Activity must override `attachBaseContext`.

### Code sharing between games
- `item_player_selection.xml`, `dialog_add_player.xml`, `item_player_stats.xml`, `item_top20.xml` are **shared** between Yahtzee and Skyjo.
- `ColorSelectionAdapter` and `PlayerSelectionAdapter` are defined in `YahtzeePlayerSelectionActivity.kt` and reused by Skyjo via import.
- Any change to these shared components affects **both games**.

### Game grids
- Grids (Yahtzee and Skyjo) are built **entirely in code** inside each `GameActivity` (no RecyclerView or Adapter).
- Text size and cell padding adapt to the number of players:
  - 2–3 players: 14sp, 14dp padding
  - 4–5 players: 13sp, 10dp padding
  - 6–8 players: 11sp, 7dp padding
- Player names are always truncated with `maxLines = 1` + `ellipsize = TruncateAt.END`.
- The grid must fill the full screen: rows use `height=0` + `weight=1` on the vertical axis so they share available space evenly. The `ScrollView` with `fillViewport=true` handles overflow when there are many rounds.

### Statistics
- **Yahtzee**: highest score = best. `getBestScoreByPlayer` = MAX.
- **Skyjo**: lowest score = best. `getBestScoreByPlayer` returns MIN (inverted in `SkyjoStatsActivity`).
- **Solo games** (1 player, Yahtzee only) do not count toward win statistics (`isSoloGame = playerScores.size == 1`).
- `getCountedGamesPlayedByPlayer` excludes solo games (sessions where `playedAt` appears for only one player).

### Skyjo — business rules
- Score limit: **100 points** → triggers the end-of-game popup.
- **Finisher penalty**: if the player who ends the round does not have the **strictly lowest** score alone, their score is **doubled**.
- Valid score range per round: **-12 to 140**.
- The finisher's cell gets a **green background** (strictly lowest) or **red background** (otherwise).
- Other players: **green text** if score = global minimum, **red text** if score = global maximum.
- A score from the previous round can be edited (`isPrev = index == rounds.lastIndex - 1`).

### Tests
- Unit tests in `app/src/test/` (JVM, no Android context).
- Instrumented tests in `app/src/androidTest/` (Room in-memory).
- Existing test files:
  - `YahtzeePlayerScoreTest.kt` — Yahtzee scoring logic
  - `SkyjoScoreManagerTest.kt` — Skyjo logic (rounds, penalties, colors)
  - `DatabaseTest.kt` — Room DAOs

### CI/CD
- GitHub Actions: `.github/workflows/android-ci-cd.yml`
- Tests: `./gradlew testFossDebugUnitTest`
- Lint: `./gradlew lintFossDebug`
- Automatic release if: tag does not exist + fastlane changelog present + entry in `CHANGELOG.md`
- Flavors `foss` (F-Droid) and `gplay` (Google Play) — same `applicationId` for now.

---

## Versioning

Format: `MAJOR.MINOR.PATCH` (SemVer)

| Change type | Version |
|---|---|
| Bug fix | PATCH (`1.x.y+1`) |
| New backward-compatible feature (new game, new stat…) | MINOR (`1.x+1.0`) |
| Major UX overhaul or breaking change | MAJOR (`x+1.0.0`) |

Current version: **1.1.0** (`versionCode = 3`)

For each new version:
1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`
2. Create `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
3. Create `fastlane/metadata/android/fr-FR/changelogs/<versionCode>.txt`
4. Add a `## [x.y.z]` entry in `CHANGELOG.md`

---

## Adding a new game

1. Create a `games/<gamename>/` package with:
   - `<Game>ScoreManager.kt` — data model and business rules
   - `<Game>PlayerSelectionActivity.kt` — reuse `ActivityPlayerSelectionBinding`
   - `<Game>GameActivity.kt` — grid built in code
   - `<Game>StatsActivity.kt` — reuse `item_player_stats.xml`
   - `<Game>Top20Activity.kt` — reuse `item_top20.xml`
2. Add layouts `activity_<game>_game/stats/top20.xml`
3. Add the card in `activity_main.xml` and the listener in `MainActivity.kt`
4. Declare the 4 activities in `AndroidManifest.xml`
5. Add a `menu_<game>_player_selection.xml`
6. Add strings in `strings.xml` and `strings-fr.xml`
7. Add a section in `activity_general_stats.xml` and `GeneralStatsActivity.kt`
8. Add unit tests in `<Game>ScoreManagerTest.kt`
