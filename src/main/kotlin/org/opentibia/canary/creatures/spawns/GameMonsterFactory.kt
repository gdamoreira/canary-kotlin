package org.opentibia.canary.creatures.spawns

import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.monsters.MonsterType as RegisteredMonsterType
import org.opentibia.canary.game.Game
import org.opentibia.canary.map.Position
import java.util.concurrent.atomic.AtomicLong

/** `Monster::monsterAutoID` seed (src/creatures/monsters/monster.cpp:33). */
const val MONSTER_AUTO_ID_START: Long = 0x50000001L

/**
 * Production [MonsterFactory] over the composed world: the Kotlin counterpart
 * of `std::make_shared<Monster>(monsterType)`
 * (src/creatures/monsters/spawns/spawn_monster.cpp:199) plus the world-entry
 * tail the C++ engine keeps around it
 * (`g_game().internalPlaceCreature` on startup, `g_game().placeCreature` on a
 * respawn, spawn_monster.cpp:200-210) and the `Creature::addList` registration
 * inside `Game::internalPlaceCreature` (src/game/game.cpp:1513).
 *
 * The monster stats come from the registered `creatures.monsters.MonsterType`
 * (`Monster::Monster`, src/creatures/monsters/monster.cpp:90-108); the
 * `RATE_HEALTH`-driven `getHealthMultiplier` and the creature event
 * registration of the C++ constructor are PARITY GAPS. A monster that cannot
 * enter the world resolves to `null`, mirroring the C++ `return false`.
 *
 * `Monster::monsterAutoID` is a C++ static; the port replaces the global with
 * this factory-owned sequence (AGENTS.md 1.1). The constructor-assigned id is a
 * PARITY GAP against the C++ `setID()` after placement
 * (`Game::internalPlaceCreature`, game.cpp:1512): the Kotlin pipeline host is
 * registered before the map placement and reads `monster.id`, so the id is
 * assigned up front and a rejected placement leaves a gap in the sequence.
 */
class GameMonsterFactory(
    private val game: Game,
    private val monsterTypes: Monsters,
    /**
     * `MONSTER_PERF_TEST_FORCE_ACTIVE` (spawn_monster.cpp:201-204): the startup
     * path admits the forced creature check.
     */
    private val monsterPerfTestForceActive: Boolean = false,
) : MonsterFactory {
    private val monsterAutoId = AtomicLong(MONSTER_AUTO_ID_START)

    override fun create(monsterType: MonsterType, position: Position, startup: Boolean): Monster? {
        val registeredType = monsterTypes.getMonsterType(monsterType.name, silent = true) ?: return null
        val monster = construct(registeredType, position)

        val placed = if (startup) {
            game.internalPlaceCreature(
                monster,
                position,
                extendedPos = true,
                forced = false,
                creatureCheck = monsterPerfTestForceActive,
            )
        } else {
            game.placeCreature(monster, position, extendedPos = false, forced = true)
        }
        if (!placed) {
            return null
        }

        game.addMonster(monster)
        return monster
    }

    /**
     * `Monster::createMonster(name)` (src/creatures/monsters/monster.cpp:82-88):
     * resolves the type through the booted `g_monsters()` registry and
     * constructs the monster (`Monster::Monster`, monster.cpp:90-108) without
     * placing it; `null` mirrors the C++ `nullptr`. The caller places it
     * (`g_game().placeCreature`) and dispatches `Monster::onSpawn` when the
     * placement succeeds, exactly like `GameFunctions::luaGameCreateMonster`
     * (game_functions.cpp:589) and the raid events
     * (src/lua/creature/raids.cpp:395).
     *
     * The monster is constructed at the C++ default position; the C++
     * `Monster::setID()` runs during `Game::internalPlaceCreature`
     * (game.cpp:1512), so a caller that never places it leaves a gap in the
     * auto-id sequence (documented PARITY GAP of [create]).
     */
    fun createMonster(name: String): Monster? {
        val registeredType = monsterTypes.getMonsterType(name) ?: return null
        return construct(registeredType, Position(0, 0, 0))
    }

    /** `Monster::Monster(mType)` (monster.cpp:90-108) stat projection. */
    private fun construct(registeredType: RegisteredMonsterType, position: Position): Monster =
        Monster(
            id = monsterAutoId.getAndIncrement(),
            name = registeredType.name,
            maxHealth = registeredType.info.healthMax,
            health = registeredType.info.health,
            speed = registeredType.getBaseSpeed(),
            position = position,
        )
}
