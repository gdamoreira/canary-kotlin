package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster

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
