# Oh Hell

- **Players**: 3–8 (equipment: cards). `gameType = "oh_hell"`.
- Total rounds = `(51 / numPlayers) * 2`; max cards per round rises then falls symmetrically, peaking at the middle round(s).
- Contract phase: players bid in rotating order; the last bidder cannot pick the value that would make the total bid equal the round's max cards.
- Result phase: ✅ = made contract exactly (score `5 + 2×contract`), ❌ repeated per trick missed (score `-2` per cross).
- Highest total after all rounds wins.
