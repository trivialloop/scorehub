# Score sheets

A score sheet is a picture of the final grid of a finished game, kept like the paper sheet of a
board game night. **Disabled by default** (limit = 0).

## Principle

- Taken in `saveResultsAndShowSummary()` with `ScoreSheetManager.captureAndSave(...)`, using the
  **same `playedAt`** as the `GameResult` rows. The key of a sheet is `(gameType, playedAt)`, the same
  key as a session in the stats and in the backup.
- The views are drawn onto a bitmap, scaled to at most `MAX_WIDTH_PX = 800` wide (and 8192 high), and
  saved as **lossy WebP, quality 80** (`QUALITY`): about 15–40 KB per sheet. Both constants
  trade sharpness (zoom) against disk space.
- The capture waits for the layout pass (`runAfterLayout`). `restore` is posted afterwards so the
  grid is never rebuilt from inside a layout pass.
- The sheet uses the theme active when the game was played (light or dark).

## Storage

- Files: `noBackupFilesDir/score_sheets/<gameType>_<playedAt>.webp`. `noBackupFilesDir` on purpose:
  `allowBackup="true"` is on, and Google Auto Backup is capped at 25 MB; going over would fail the
  whole backup, Room database included.
- Table `score_sheets` (`id`, `gameType`, `playedAt`, `fileName`, `createdAt`, index on `gameType`),
  added by `MIGRATION_2_3` (DB version 3).
- No label column: the players of a sheet are read from `game_results` with the same
  `(gameType, playedAt)`. Names stay up to date after a rename, and the link survives a backup
  "Overwrite" since `playedAt` is never regenerated.
- A row whose file is missing (e.g. after a device restore) is deleted when the list opens or when
  the viewer fails to decode it.

## Limit

- Setting "Score sheets kept per game", steps `0 (disabled), 1, 2, 3, 5, 10, 20, 30, 50, 100,
  unlimited` (`ScoreSheetPrefs.LIMITS`), stored in the `settings` prefs.
- The limit is **per game**. After each save, the oldest sheets of that game above the limit are
  deleted (`prune`). Lowering the limit therefore takes effect when the next game of that game is saved.
- Settings also show the space used and a "Delete all score sheets" button.

## Screens

- `ScoreSheetsActivity`: list of sheets, newest first, with a game filter and a player filter.
  - Opened from the main menu: both filters are free.
  - Opened from the "History" item of a game (`EXTRA_GAME_TYPE`): the game filter is fixed on that game
    **even when it has no sheet yet**, and the player filter only lists players of that game.
  - Empty message: "disabled" when the feature is off, "none for this game" when opened from a game,
    "none" otherwise.
- `ScoreSheetViewerActivity`: browses the filtered list (`EXTRA_SHEET_IDS`). Previous = newer sheet,
  next = older sheet. Delete moves on to the next sheet, or closes when it was the last one.
  `ui/ZoomImageView`: pinch to zoom, drag, double-tap to zoom in / back to the fitted width.
- Entry points: main menu, and each game's player-selection menu.

## Menu convention (every `menu_<game>_player_selection.xml`)

Game statistics, Top 20 and score sheets (`action_score_sheets`) are `showAsAction="never"` (3-dot
overflow menu). Only the help item stays `always`. `menu_main.xml` and the in-game menus are unchanged.

## Hooking a game

```kotlin
// grid in headerContainer + tableContainer
ScoreSheetManager.captureAndSave(this, GAME_TYPE, playedAt, binding.headerContainer, binding.tableContainer)
// one-shot grid in scoreTableContainer (Akropolis, Qwixx, Wingspan, Yahtzee)
ScoreSheetManager.captureAndSave(this, GAME_TYPE, playedAt, binding.scoreTableContainer)
```

- Call it right after `val playedAt = System.currentTimeMillis()`, **after** the final grid has been
  built (`gameOver = true; buildTable()`).
- **Games that only display a window of 5 players** (Farkle, Flip 7, Oh Hell, Yahtzee, and Ligretto if it
  has `getVisiblePlayers`) keep a `captureAll` flag: `getVisiblePlayers()` returns everybody and
  `columnWeight()` returns `1f` while it is true. They pass
  `views = listOf(...)`, `prepare = { captureAll = true; buildTable() }` and
  `restore = { captureAll = false; buildTable() }`. This only matters above 5 players.
- **Free Game** has no sheet: it does not save results either.

## Not in the backup

Sheets are not exported and not touched by an import ("Overwrite" leaves them in place): they are
local souvenirs, the scores themselves are in `game_results`. See `backup.md`.

## Tests

`ScoreSheetPrefsTest` covers the steps, the default (disabled) and the number of sheets to prune.
