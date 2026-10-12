# Feature: Real Monster Definition & Loot Engine Pipeline

## 1. Explanation
Specifies the monster template loading and loot generation pipeline (`data/monster/`, `data/scripts/lib/register_monster_type.lua`, `src/creatures/monsters/`):
1. **Monster Type Definitions**:
   - Vitals: Experience, Health, Speed, Defense, Armor.
   - LookType: Monster sprite looktype, body, head, legs, feet colors, corpse item ID.
   - Immunities: Paralyze, Fire, Earth, Energy, Ice, Holy, Death, Drown, Lifedrain.
   - Voices: Randomized yelling sentences (e.g. *"FCHHHHH"*, *"GROOOWL"*).
   - Combat Spells: Melee, Fire wave, Energy beam, Great fireball, Self-healing, Summoning minions.
2. **Loot Table Engine**:
   - Items dropped on death with drop chance in basis points ($1..100,000$ where $100,000 = 100\%$).
   - Max count for stackable items (gold coins, creature products).
   - Container loot nesting (dropping items inside a bag inside the monster corpse).

## 2. Impact
Drives all PvE encounters, hunting economics, experience gain, and equipment drop rates across the entire server.

## 3. Functionality Affected
- **Monster Spawner**: Instantiates monsters dynamically using the registered `MonsterType` template.
- **Corpse Generation**: When monster dies, drops the configured corpse item on the tile and rolls the loot table.
- **Loot Multipliers**: Applies server loot rate and active Prey/Bosstiary/Hazard multipliers to drop rolls.

## 4. Agent Implementation Details
- Implement `MonsterPipelineService.kt` loading monster definitions from XML or Lua registration tables.
- Implement `LootEngine.kt` simulating drop rolls using cryptographically secure or pseudo-random number generators (`ThreadLocalRandom`).
- Automatically nest generated loot into the spawned corpse container.

## 5. Test Reference
- `org.opentibia.canary.creatures.MonsterPipelineServiceTest`: Loads a sample monster definition (e.g. Dragon), kills monster, and verifies corpse generation with rolled loot items.
