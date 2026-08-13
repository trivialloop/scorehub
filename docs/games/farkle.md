# Farkle

- **Players**: 2–8 (equipment: dice).
- Turn-based: each player takes turns rolling dice. In-progress turn shows Add / Bank / Farkle buttons.
- Score limit: **10,000 pts**. After a player reaches the limit, all other players get one last turn.
- 3 consecutive Farkles in a row lose the player all accumulated points (`tripleFarklePenalty`).
- Uses the fixed header / scrollable content pattern; `ROW_HEIGHT_DP = 48` for completed round rows.
