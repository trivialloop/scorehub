# Skyjo

- **Players**: 2–8 (equipment: board box).
- **Best score**: lowest total wins. Score limit: **100 pts**.
- **Finisher penalty**: if the round finisher is not strictly the lowest scorer alone, their raw score is **doubled**.
- **Coloring**: finisher cell uses background color (green/red), other players use text color.
- Uses the fixed header / scrollable content pattern — **reference implementation** for all other round-based games.
- **Statistics exception**: `getBestScoreByPlayer` is inverted in `SkyjoStatsActivity` (MIN instead of MAX) because lower is better.
