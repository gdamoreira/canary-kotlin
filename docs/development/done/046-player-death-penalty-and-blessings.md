# Feature: Player Death Penalty, Blessings & Corpse Drop Mechanics

## 1. Explanation
Implements RPG death consequences (`src/game/game.cpp`, `src/enums/player_blessings.hpp`). When a player dies (health reaches 0), the engine calculates experience loss, skill point loss, inventory drop chances based on active blessings, spawns a dead player corpse with dropped items, and teleports the player back to their town temple.

## 2. Impact
Core RPG high-stakes death loop. Governs blessing economics (Twist of Fate, 5 standard blessings), corpse looting, and death logging.

## 3. Functionality Affected
- **Experience & Skill Reduction**: Standard Tibia loss: $10\%$ base reduction, reduced by $8\%$ per blessing (down to $2\%$ with all 5 blessings).
- **Equipment Drop**: $100\%$ backpack drop, $10\%$ chance per equipment slot without blessings (reduced to $0\%$ with full blessings).
- **Corpse Generation**: Spawns player corpse item on death tile, holds dropped items, and labels description with killer name (e.g. *"You see the dead human (Vol: 10). He was killed by a Dragon."*).
- **Temple Respawn**: Resets health/mana and moves player to `town.templePosition`.

## 4. Agent Implementation Details
- Implement `DeathPenaltyEngine.kt` evaluating active blessings on `Player`.
- Implement `PlayerDeathEvent` creating corpse containers on `GameMap`.
- Teleport player to temple position and reset conditions.

## 5. Test Reference
- `org.opentibia.canary.game.DeathPenaltyTest`: Tests unblessed player dying drops backpack and loses 10% exp, and full-blessed player loses reduced exp and keeps all items.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
