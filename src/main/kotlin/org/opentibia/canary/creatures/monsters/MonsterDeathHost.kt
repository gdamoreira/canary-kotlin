package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Container

/**
 * Live death-side facts and world sinks of a placed [Monster]
 * (src/creatures/monsters/monster.cpp:873-876, 1386-1388, 3227-3436).
 *
 * The C++ `Monster` reads these straight off its members (`m_monsterType->info`,
 * `forgeStack`, `lookcorpse`, `race`, the loot table) and calls the
 * `g_game()`/`Item` globals for the corpse and loot. In the port the type/forge
 * facts live on the world host's `MonsterTypeState` and the world sinks are
 * injected, so the owning composition (`Game.internalPlaceCreature`) binds one
 * instance per placed monster and the [Monster] overrides read it. The values
 * that change after placement (`forgeStack`, the forge classification, the
 * live `lootDrop`) are read through providers at death time; a reused monster
 * id gets a fresh host when the replacement is placed.
 */
class MonsterDeathHost(
    /** `MonsterType::info.lookcorpse` (`src/creatures/monsters/monsters.hpp:72`), `Monster::getLookCorpse` (monster.cpp:878-881). */
    val lookCorpse: Int = 0,
    /** `MonsterType::info.race` (`src/creatures/monsters/monsters.hpp:68`), `Monster::getRace` (monster.cpp:1386-1388). */
    val race: RaceType = RaceType.RACE_NONE,
    /** `MonsterType::info.experience`, the base of `Monster::getLostExperience` (monster.cpp:873-876). */
    val experience: Long = 0,
    /**
     * `Monster::forgeStack` (`src/creatures/monsters/monster.hpp:406`) read live
     * at death time (forge classification changes it after placement), the mult
     * tier of `Monster::getLostExperience`.
     */
    val forgeStack: () -> Int = { 0 },
    /**
     * `MonsterType::info.isFamiliar`, read by `Creature::onGainExperience`
     * (creature.cpp:1318-1320) for the half-experience summon share.
     */
    val isFamiliar: Boolean = false,
    /** `Item::CreateItem(getLookCorpse())` (creature.cpp:838-843). */
    val createCorpse: (Int) -> Container? = { null },
    /** `Monster::dropLoot` inputs (monster.cpp:3415-3436) over the live loot table/flags. */
    val lootRequest: () -> MonsterLootDropRequest = { MonsterLootDropRequest() },
    /** `getLootRandom()` (`data/libs/functions/functions.lua:68-75`) die roll. */
    val nextRoll: () -> Int = { 0 },
    /** `uniform_random(FORGE_MIN_SLIVERS, FORGE_MAX_SLIVERS)` sliver draw (monster.cpp:3420). */
    val nextSliverCount: (Int, Int) -> Int = { min, _ -> min },
    /** `Monster::death` body (monster.cpp:3227-3303) run after `Creature::dropCorpse`. */
    val die: (Monster, Creature?) -> Unit = { _, _ -> },
)

/**
 * `Creature::getPlayer`/`getMaster`/`getMaster->getPlayer` projection behind
 * `Monster::getCorpse`'s owner stamp (monster.cpp:3305-3320): the most-damage
 * creature when it is a player, otherwise its master when that is a player.
 */
fun targetCreatureFactsOf(creature: Creature): TargetCreatureFacts {
    val player = creature as? Player
    val master = creature.getMaster()
    return TargetCreatureFacts(
        id = creature.id,
        position = creature.position,
        playerId = player?.id,
        masterId = master?.id,
        masterPlayerId = (master as? Player)?.id,
    )
}
