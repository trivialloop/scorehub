# Oh Hell

- **Players**: 3–8 (equipment: cards). `gameType = "oh_hell"`.
- Total rounds = `(51 / numPlayers) * 2`; max cards per round rises then falls symmetrically, peaking at the middle round(s).
- **Round order**: the starting player rotates every round (`startPlayerIndex = (roundNumber - 1) % numPlayers`). Bidding and result entry both follow this order.
- **Contract phase**: players bid in order; the last bidder cannot pick the value that would make the total bid equal the round's max cards (forbidden value shown as `✗ n` in the picker).
- **Result phase**: starts as soon as all contracts are entered (contracts are then locked). Same player order as the bidding.
  - ✅ = made the contract exactly (score `5 + 2 × contract`).
  - ❌ repeated once per trick missed, over or under (score `-2` per cross).
- Highest total after all rounds wins.

## Grid layout

- Uses the fixed header / scrollable content pattern (`headerContainer` + `scrollView`/`tableContainer`, no `totalContainer`). The total row is the last child of `tableContainer`.
- **One row per round** (the old contract row + result row have been merged). Each player cell is a `FrameLayout` built by `makeRoundCell`:
  - **Center**: signed round score (`+11`, `-4`), colored with `ScoreColorRole` (best = green, worst = red, computed across ALL players of the row, not only the visible columns).
  - **Bottom-right**: result emoji (✅ / ❌❌…) followed by the contract in small light grey (same look as the raw score in Cactus). The emoji only appears once the result phase has reached that player.
- **Round label cell** (`LABEL_COL_DP = 65`): shows `N` and `(maxCards)`, tinted with the color of the **first player of the round** (`startPlayerIndex`), not the last bidder.
- **Visible columns**: up to 5 players are all shown. With more than 5, only a window of 5 centered on the active player is displayed (active column wider: `columnWeight`).

## Editing rules

- Empty cell of the active player: `cell_editable_bg`.
- Previous player's cell in the current phase stays editable until the next player validates: `cell_editable_filled_bg`, dialog title prefixed with `✏️`.
- Once the contract phase is complete, contracts can no longer be changed.
- Rows of previous rounds are read-only.
