# Belote

- **Players**: exactly 4, in **2 fixed teams of 2** (equipment: cards). `gameType = "belote"`.
- Unlike every other game in ScoreHub, Belote is **team-based**: player selection requires
  assigning the 4 selected players into two teams of 2 before starting.
- ScoreHub does not simulate trick-taking or trump suit — only the round's outcome is entered:
  which team took the contract, how many raw card points they won (0–162), whether either
  team made a capot, and which team (if any) announced belote-rebelote.
- **Scoring** (`BeloteRound.computeScores`):
  - Total points per hand: 162 (152 for the 8 tricks + 10 "dix de der"), or 252 on a capot
    (100 for the der instead of 10).
  - Contract succeeds if attacking team's raw points > 81 → they score their points, defense
    scores `162 - points`.
  - Exactly 81-81 → **litige**: defense scores 81, attacking team's 81 points are carried
    over (`litigeCarry`) to whichever team next successfully makes a contract as attacker.
  - < 81 → chute: attacking team scores 0, defense scores 162.
  - Capot: winning team scores 252, losing team scores 0. If the attacking team achieves it,
    any pending litige carry is resolved into their score.
  - Belote-Rebelote: +20 to the announcing team's score, added on top regardless of the
    contract's outcome (inviolable).
- **Score limit**: **501 points**. First team to reach it (checked after each round) wins;
  highest total wins in case both cross it the same round.
- Team totals are computed via `BeloteScoring.computeRoundScores(rounds)`, which threads the
  litige carry through the round list — always recompute from round 1, never store the carry
  standalone.
- **Player selection**: `BelotePlayerSelectionActivity` requires exactly 4 players, then opens
  a team-assignment dialog (`BeloteTeamAssignmentDialog`) where the user checks which 2 players
  form "Team 2" (the remaining 2 form "Team 1"). Both must have exactly 2 members.
- **Statistics quirk**: `GameResult` is still stored per player (schema-wide constraint), so
  teammates always share an identical score/win flag for a given game — this is intended and
  mirrors the team result correctly at the individual-stats level, same convention as other
  games that share info without a dedicated team table.
- Uses the fixed header / scrollable content pattern with **2 team columns** instead of one
  column per player (`LABEL_COL_DP = 65`).
