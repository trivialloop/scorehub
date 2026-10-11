# Backup (export / import) — beta

- Export: Settings > Export data. Produces a JSON file (`format = "scorehub-backup"`, `formatVersion = 1`)
  through the Storage Access Framework (no permission). Players are referenced by `uuid`, never by row id.
  Results of deleted players are ignored. Games are grouped by `(gameType, playedAt)`.
- Identity: a player is `uuid` + `extraUuids` (identities of the same person on other devices).
  Two players are "the same" when their identity sets intersect.
- Import, empty database: imported directly (overwrite), uuids kept.
- Import, non-empty database: the user chooses **Overwrite** (confirmation, then everything replaced,
  `last_player_order` prefs reset) or **Merge**.
- Merge (`BackupImportActivity`, logic in `BackupMergePlanner`):
  1. 1 source <-> 1 local sharing an identity: linked automatically (shown as "same UUID" or "linked").
  2. Other sources (no match, or 1->many / many->1): the user links to a local player or creates a new one
     (editable name, no collision; editable color). Nothing is preselected; Import stays disabled until valid.
  3. A link makes the local player absorb the source identities in `extraUuids` (it keeps its own `uuid`).
     A new player inherits the source uuid (fresh uuid if the source overlapped local players).
  4. A game whose `(gameType, playedAt)` already exists locally is ignored as a whole.
  5. Everything is written in one transaction: new players first, then results with the LOCAL player ids.
- `playedAt` is never regenerated (stats "Counted" group by it).
- Files with an unknown `gameType` or a newer `formatVersion` are refused ("update the app").
- Not handled: the same physical game typed separately on two devices (different `playedAt`).
