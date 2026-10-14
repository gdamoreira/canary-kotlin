package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

/**
 * Per-`Monster` runtime state derived from its [MonsterType]:
 * - the constructor copies `mType->info.attackSpells`/`defenseSpells` into the
 *   instance vectors (`Monster::Monster`, src/creatures/monsters/monster.cpp:104-105);
 * - the forge latches `forgeStack`/`monsterForgeClassification`/
 *   `timeToChangeFiendish` (src/creatures/monsters/monster.hpp:403-407);
 * - the hazard flags and the soul-pit latch (src/creatures/monsters/monster.hpp:460-466).
 *
 * The Kotlin [Monster] entity is intentionally thin (see [MonsterBehaviorState]),
 * so the C++ per-`Monster` members live here, owned by the world host entry that
 * created it (keyed by the owning monster id, see [MonsterPipelinesHost]).
 */
class MonsterTypeState(
    /** `Monster::m_monsterType` (src/creatures/monsters/monster.hpp:363). */
    val monsterType: MonsterType? = null,
) {
    /** `Monster::forgeStack` (src/creatures/monsters/monster.hpp:406). */
    var forgeStack: Int = 0

    /** `Monster::monsterForgeClassification` (src/creatures/monsters/monster.hpp:407). */
    var monsterForgeClassification: ForgeClassification = ForgeClassification.Normal

    /** `Monster::timeToChangeFiendish` (src/creatures/monsters/monster.hpp:403). */
    var timeToChangeFiendish: Long = 0L

    /** `Monster::hazard` (src/creatures/monsters/monster.hpp:460). */
    var hazard: Boolean = false

    /** `Monster::hazardCrit` (src/creatures/monsters/monster.hpp:461). */
    var hazardCrit: Boolean = false

    /** `Monster::hazardDodge` (src/creatures/monsters/monster.hpp:462). */
    var hazardDodge: Boolean = false

    /** `Monster::hazardDamageBoost` (src/creatures/monsters/monster.hpp:463). */
    var hazardDamageBoost: Boolean = false

    /** `Monster::hazardDefenseBoost` (src/creatures/monsters/monster.hpp:464). */
    var hazardDefenseBoost: Boolean = false

    /** `Monster::soulPit` (src/creatures/monsters/monster.hpp:466). */
    var soulPit: Boolean = false

    /** `Monster::attackSpells` instance copy (monster.cpp:104, monster.hpp:448). */
    val attackSpells: MutableList<MonsterSpellBlock> =
        monsterType?.info?.attackSpells?.map { it.toMonsterSpellBlock() }?.toMutableList() ?: mutableListOf()

    /** `Monster::defenseSpells` instance copy (monster.cpp:105, monster.hpp:449). */
    val defenseSpells: MutableList<MonsterSpellBlock> =
        monsterType?.info?.defenseSpells?.map { it.toMonsterSpellBlock() }?.toMutableList() ?: mutableListOf()

    /**
     * `Creature::eventsList` (`src/creatures/creature.hpp:646-660`) as the
     * attached named creature events. `Monster::Monster` registers every
     * `mType->info.scripts` entry (monster.cpp:107-114) and
     * `MonsterTypeFunctions::luaMonsterTypeRegisterEvent` pushes late
     * registrations onto already-spawned monsters
     * (monster_type_functions.cpp:1235-1242).
     *
     * PARITY GAP: the set records the attachment only; the named
     * `CreatureEvent` handlers are not dispatched per creature yet, because the
     * port keeps the `g_creatureEvents()` registry in `LuaEnvironment` and
     * `ScriptingDispatch` still fires every registered event (see
     * ScriptingDispatch.kt).
     */
    val creatureEvents: MutableSet<String> = linkedSetOf<String>().apply {
        addAll(monsterType?.info?.scripts ?: emptySet())
    }

    /**
     * `Creature::registerCreatureEvent` (`src/creatures/creature.cpp:1674-1693`):
     * attaches [name] to the live monster; false when it was already attached
     * (the C++ duplicate branch) or, in the C++, unknown to `g_creatureEvents()`.
     */
    fun registerCreatureEvent(name: String): Boolean = creatureEvents.add(name)

    /** Whether [name] is attached to this live monster (`hasEventRegistered`). */
    fun hasCreatureEvent(name: String): Boolean = creatureEvents.contains(name)

    /**
     * `Monster::onCreatureAppear` Lua dispatch (monster.cpp:351-376):
     * `onAppear(self, creature)`. Returns true when the callback handled the
     * event (`callFunction` true), which stops the default appear flow.
     */
    fun runCreatureAppearEvent(monster: Monster, creature: Creature): Boolean =
        runCallback(monsterType?.info?.creatureAppearEvent) { listOf(monster, creature) }

    /**
     * `Monster::onRemoveCreature` Lua dispatch (monster.cpp:394-419):
     * `onDisappear(self, creature)`. Returns true when the callback handled the
     * event, which stops the default disappear flow.
     */
    fun runCreatureDisappearEvent(monster: Monster, creature: Creature): Boolean =
        runCallback(monsterType?.info?.creatureDisappearEvent) { listOf(monster, creature) }

    /**
     * `Monster::onCreatureMove` Lua dispatch (monster.cpp:437-465):
     * `onMove(self, creature, oldPosition, newPosition)`. Returns true when the
     * callback handled the event, which stops the default movement flow.
     */
    fun runCreatureMoveEvent(
        monster: Monster,
        creature: Creature,
        oldPosition: Position,
        newPosition: Position,
    ): Boolean = runCallback(monsterType?.info?.creatureMoveEvent) {
        listOf(monster, creature, oldPosition, newPosition)
    }

    /**
     * `Monster::onCreatureSay` Lua dispatch (monster.cpp:571-597):
     * `onSay(self, creature, type, message)`. The C++ uses `callVoidFunction`,
     * so the callback result is ignored.
     */
    fun runCreatureSayEvent(monster: Monster, creature: Creature, type: Int, text: String) {
        val info = monsterType?.info ?: return
        if (info.creatureSayEvent == -1) {
            return
        }
        info.scriptInterface?.callEvent(info.creatureSayEvent, listOf(monster, creature, type, text))
    }

    /**
     * `Monster::onAttackedByPlayer` Lua dispatch (monster.cpp:600-625):
     * `onPlayerAttack(self, attackerPlayer)`. The C++ uses `callVoidFunction`,
     * so the callback result is ignored.
     */
    fun runAttackedByPlayerEvent(monster: Monster, attackerPlayer: Player) {
        val info = monsterType?.info ?: return
        if (info.monsterAttackedByPlayerEvent == -1) {
            return
        }
        info.scriptInterface?.callEvent(
            info.monsterAttackedByPlayerEvent,
            listOf(monster, attackerPlayer),
        )
    }

    /**
     * `Monster::onSpawn` Lua event (monster.cpp:627-648): when the type captured
     * an `onSpawn` event, calls it with the monster and the spawn position.
     */
    fun runSpawnEvent(monster: Monster, position: Position) {
        val info = monsterType?.info ?: return
        if (info.spawnEvent == -1) {
            return
        }
        info.scriptInterface?.callEvent(info.spawnEvent, listOf(monster, position))
    }

    /** Shared `-1`-gated dispatch for the `callFunction` creature events. */
    private fun runCallback(eventId: Int?, arguments: () -> List<Any?>): Boolean {
        if (eventId == null || eventId == -1) {
            return false
        }
        val info = monsterType?.info ?: return false
        return info.scriptInterface?.callEvent(eventId, arguments()) ?: false
    }

    /**
     * `Monster::onThink` Lua event prefix (monster.cpp:1581-1600): returns true
     * when the captured callback handled the tick (`callFunction` true), which
     * stops the default think flow.
     */
    fun runThinkEvent(monster: Monster, interval: Int): Boolean {
        val info = monsterType?.info ?: return false
        if (info.thinkEvent == -1) {
            return false
        }
        return info.scriptInterface?.callEvent(info.thinkEvent, listOf(monster, interval)) ?: false
    }

    /**
     * Replaces the inert [MonsterAsyncHost] type seams with this state so the
     * deferred pipelines read the real `Monster` instance data:
     * `host.attackSpells` (`Monster::attackSpells`), `host.canUseAttack`
     * (`Monster::canUseAttack`, monster.cpp:2095-2107), `host.castSpell`
     * (`spellBlock.spell->castSpell(monster, target)`, monster.cpp:1979),
     * `host.changeTargetChance` (`info.changeTargetChance`) and the
     * `host.strategiesTarget*` reads (`Monster::searchTargetImmediate` /
     * `Monster::prepareTargetSearchCompute`, monster.cpp:906-925, 1115-1125).
     *
     * [resolveCreature] is `g_game().getCreatureByID`; the cast is a no-op when
     * the block has no spell (the C++ commit loop skips those) or the target id
     * no longer resolves (the deferred-callback lifetime rule).
     */
    fun bindHost(
        host: MonsterAsyncHost,
        monster: Monster,
        resolveCreature: (Long) -> Creature? = { null },
    ) {
        host.attackSpells = { attackSpells }
        host.changeTargetChance = { monsterType?.info?.changeTargetChance ?: 0 }
        host.strategiesTargetNearest = { monsterType?.info?.strategiesTargetNearest ?: 0 }
        host.strategiesTargetHealth = { monsterType?.info?.strategiesTargetHealth ?: 0 }
        host.strategiesTargetDamage = { monsterType?.info?.strategiesTargetDamage ?: 0 }
        host.canUseAttack = { pos, target ->
            MonsterBehavior.canUseAttack(
                attackSpells = attackSpells,
                isHostile = monsterType?.info?.isHostile ?: true,
                pos = pos,
                targetPos = target.position,
                isSightClear = host.isSightClear,
            )
        }
        host.castSpell = { spellBlock, target ->
            val spell = spellBlock.spell
            if (spell != null) {
                val creature = resolveCreature(target.id)
                if (creature != null) {
                    spell.castSpell(monster, creature)
                }
            }
        }
    }
}

/**
 * Instance copy of `struct spellBlock_t` from the monster type
 * (`attackSpells = mType->info.attackSpells`, monster.cpp:104-105). The C++
 * vector copy shares the `std::shared_ptr<BaseSpell>` slots; [SpellBlock.spell]
 * carries the same [BaseSpell] reference.
 */
internal fun SpellBlock.toMonsterSpellBlock(): MonsterSpellBlock = MonsterSpellBlock(
    spell = spell,
    chance = chance,
    speed = speed,
    range = range,
    minCombatValue = minCombatValue,
    maxCombatValue = maxCombatValue,
    combatSpell = combatSpell,
    isMelee = isMelee,
)
