# Feature: Familiars Vocation Summon System

## 1. Explanation
Implements CipSoft's Level 200+ Vocation Familiars (`data/libs/systems/familiar.lua`, `data/XML/familiars.xml`). Characters level $\ge 200$ can summon an invincible 15-minute combat companion on a 30-minute cooldown timer:
- **Knight**: *Skullfrost* (Knight Familiar - Tank / Melee physical + Ice wave)
- **Paladin**: *Snowbash* (Paladin Familiar - Distance holy bolts + area divine aura)
- **Sorcerer**: *Sorcerer Familiar* (Death strike + Great fire wave)
- **Druid**: *Druid Familiar* (Area healing + Ice wave)

## 2. Impact
Mid-to-endgame solo hunting combat amplifier and boss burst DPS support.

## 3. Functionality Affected
- **Summon Cooldown Engine**: Enforces 30-minute cooldown tracking per player.
- **Companion AI**: Follows master, attacks master's locked target, matches master movement speed.
- **Despawn Timer**: Companion automatically despawns after 15 minutes or when master logs out.

## 4. Agent Implementation Details
- Implement `FamiliarSystem.kt` managing familiar summon spells and timers.
- Extend `Monster` / `Creature` with `master` reference and companion behavior.
- Hook familiar despawn timer onto `GameDispatcher.schedule(...)`.

## 5. Test Reference
- `org.opentibia.canary.systems.FamiliarSystemTest`: Tests level 200 player summoning familiar, familiar attacking master's target, cooldown timer preventing immediate recast, and automatic 15-minute expiration.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
