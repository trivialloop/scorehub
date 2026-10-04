# Escoba

- **Players**: exactly 2 (equipment: cards).
- 2 equal-width columns per player per round (weight 1 each): **In play** (−/score/+) and **End of round**.
- The fixed header is a single block: one empty label cell spanning both lines (no inner border), and per player a name cell (spanning both columns) above a symbol row (`SYMBOL_IN_PLAY` = 🏃, `SYMBOL_HAND` = 🏁), separated by a border. Both lines use the player's color as background.
- The round label cell is tinted with the color of the player who starts the round (player 1 on odd rounds, player 2 on even rounds).
- In-play via −/+ buttons (hidden once locked); locked once any player enters their end-of-round score.
- End-of-round score: list picker 0–20; pencil prefix and preselected value when re-editing.
- Score limit: **21 pts**. Highest score wins.
- Previous round stays editable until the new round gets any in-play activity.
- Uses the fixed header / scrollable content pattern (total row inside `tableContainer`) with `ROW_HEIGHT_DP = 48`.
