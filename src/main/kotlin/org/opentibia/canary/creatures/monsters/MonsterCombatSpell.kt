package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountType
import org.opentibia.canary.combat.AreaCombat
import org.opentibia.canary.combat.CombatParams
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.FormulaType
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.ZoneType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.spells.CombatSpell
import org.opentibia.canary.spells.CombatSpellCombat
import org.opentibia.canary.spells.CombatSpellScriptRunner
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.spells.SpellBook
import org.opentibia.canary.spells.SpellCaster
import org.opentibia.canary.spells.SpellTarget
import org.opentibia.canary.spells.SpellTile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * `Monster` view of the `SpellCaster` seam (src/creatures/combat/spells.cpp).
 *
 * The C++ `CombatSpell::castSpell(const std::shared_ptr<Creature>&, ...)`
 * overloads take the raw `Creature`; the Kotlin spell pipeline splits that into
 * [SpellCaster]/[SpellTarget], so the monster actor needs this adapter. Only the
 * members the non-scripted combat branch reaches are meaningful for a monster:
 * [getPosition] and [getDirection]. The player-only reads stay inert
 * (`0`/`false`/`null`) because a monster spell block never sets a script id, so
 * the scripted `SpellEngine` paths cannot be entered through
 * [MonsterCombatSpell].
 */
internal class MonsterSpellCaster(
    internal val monster: Monster,
) : SpellCaster {

    override fun getID(): Long = monster.id

    override fun getName(): String = monster.name

    override fun getPosition(): Position = monster.position

    override fun getDirection(): Direction = monster.direction

    override fun getLevel(): Int = 0

    override fun getMagicLevel(): Int = 0

    override fun getMana(): Int = 0

    override fun getMaxMana(): Int = 0

    override fun getSoul(): Int = 0

    override fun getVocationId(): Int = 0

    override fun getWeaponType(): WeaponType_t = WeaponType_t.WEAPON_NONE

    /** Inert: the player-zone read is unreachable from a monster combat cast. */
    override fun getZoneType(): ZoneType = ZoneType.ZONE_NORMAL

    override fun getSkull(): Skulls_t = monster.getSkull()

    override fun isPremium(): Boolean = false

    override fun getGroupId(): Int = 0

    override fun isAccessPlayer(): Boolean = false

    override fun isFirstOnStack(): Boolean = false

    override fun getAccountType(): AccountType = AccountType.ACCOUNT_TYPE_NORMAL

    override fun getAccount(): Account? = null

    override fun isOffline(): Boolean = false

    override fun getTile(): SpellTile? = null

    override fun canExiva(spellParam: String): Boolean = false

    override fun sendTextMessage(type: MessageClass, text: String) = Unit

    override fun hasFlag(flag: PlayerFlags_t): Boolean = false

    override fun hasCondition(type: ConditionType, subId: Int): Boolean = monster.hasCondition(type, subId)

    override fun getSpellBook(): SpellBook? = null

    override fun getAttackedCreature(): SpellTarget? =
        monster.getAttackedCreature()?.let { MonsterSpellTarget(it) }

    override fun addCondition(condition: Condition): Boolean = monster.addCondition(condition)

    override fun sendCancelMessage(ret: ReturnValue) = Unit

    override fun addManaSpent(manaCost: Int) = Unit

    override fun changeMana(delta: Int) = Unit

    override fun changeSoul(delta: Int) = Unit

    override fun addInFightTicks() = Unit

    override fun updateLastAggressiveAction() = Unit
}

/**
 * `Creature` view of the `SpellTarget` seam for the monster combat cast
 * (`CombatSpell::castSpell(creature, target)`, spells.cpp:420-466).
 */
internal class MonsterSpellTarget(
    internal val creature: Creature,
) : SpellTarget {

    override fun getID(): Long = creature.id

    override fun getName(): String = creature.name

    override fun getPosition(): Position = creature.position

    override fun getHealth(): Int = creature.health

    override fun isRemoved(): Boolean = creature.isRemoved()

    override fun isAccessPlayer(): Boolean = (creature as? Player)?.isAccessPlayer() ?: false
}

/**
 * Port of the `Combat` instance `Monsters::deserializeSpell` builds for a
 * monster spell block (src/creatures/monsters/monsters.cpp:83-267).
 *
 * The payload keeps the `CombatParams` the scalar-only deserializer dropped:
 * `COMBAT_PARAM_TYPE` (combat type), `COMBAT_PARAM_EFFECT` (impact effect) and
 * `COMBAT_PARAM_DISTANCEEFFECT` (shoot) are set from the `MonsterSpell` fields
 * exactly like the C++ `combatPtr->setParam` calls. `doCombat` dispatches
 * through [executeCombat]; the Kotlin `Combat` execution is not wired yet
 * (PARITY GAP shared with `WorldCombatOperations.execute`), so the default
 * seam is a no-op and tests inject a recorder.
 */
class MonsterCombatSpellCombat(
    /** `Combat::params` (`src/creatures/combat/combat.hpp:345`). */
    val params: CombatParams = CombatParams(),

    /** `Combat::area` (`combat.hpp:347`). */
    var area: AreaCombat? = null,
) : CombatSpellCombat {

    /** `Combat::formulaType`/`mina`/`minb`/`maxa`/`maxb` (`combat.hpp:349-353`). */
    var formulaType: FormulaType = FormulaType.UNDEFINED
    var mina: Double = 0.0
    var minb: Double = 0.0
    var maxa: Double = 0.0
    var maxb: Double = 0.0

    /**
     * `Combat::doCombat` body (`src/creatures/combat/combat.cpp`). The world
     * execution belongs to the combat subsystem; the seam receives the
     * re-resolved monster caster, the target (null for the position overload)
     * and the strike position.
     */
    var executeCombat: (Monster?, Creature?, Position) -> Unit = { _, _, _ -> }

    /** `Combat::hasArea` (`combat.cpp:674-676`). */
    override fun hasArea(): Boolean = area != null

    /** `Combat::setParam` (`combat.cpp:590-669`), keyed by `CombatParam_t`. */
    override fun setParam(param: Int, value: Int): Boolean = when (param) {
        COMBAT_PARAM_TYPE -> {
            params.combatType = CombatType.entries.firstOrNull { it.value == value } ?: CombatType.NONE
            true
        }

        COMBAT_PARAM_EFFECT -> {
            params.impactEffect = value
            true
        }

        COMBAT_PARAM_DISTANCEEFFECT -> {
            params.distanceEffect = value
            true
        }

        COMBAT_PARAM_BLOCKSHIELD -> {
            params.blockedByShield = value != 0
            true
        }

        COMBAT_PARAM_BLOCKARMOR -> {
            params.blockedByArmor = value != 0
            true
        }

        COMBAT_PARAM_TARGETCASTERORTOPMOST -> {
            params.targetCasterOrTopMost = value != 0
            true
        }

        COMBAT_PARAM_CREATEITEM -> {
            params.itemId = value
            true
        }

        COMBAT_PARAM_AGGRESSIVE -> {
            params.aggressive = value != 0
            true
        }

        COMBAT_PARAM_DISPEL -> {
            // The C++ static_cast keeps unknown values; the Kotlin enum clamps to NONE.
            params.dispelType = ConditionType.entries.firstOrNull { it.value == value }
                ?: ConditionType.CONDITION_NONE
            true
        }

        COMBAT_PARAM_USECHARGES -> {
            params.useCharges = value != 0
            true
        }

        COMBAT_PARAM_IMPACTSOUND -> {
            params.soundImpactEffect = value
            true
        }

        COMBAT_PARAM_CASTSOUND -> {
            params.soundCastEffect = value
            true
        }

        COMBAT_PARAM_CHAIN_EFFECT -> {
            params.chainEffect = value
            true
        }

        else -> false
    }

    /** `Combat::doCombat(creature, position)` (`combat.cpp`, position overload). */
    override fun doCombat(creature: SpellCaster?, position: Position) {
        executeCombat((creature as? MonsterSpellCaster)?.monster, null, position)
    }

    /** `Combat::doCombat(creature, target)` (`combat.cpp`, target overload). */
    override fun doCombat(creature: SpellCaster?, target: SpellTarget) {
        executeCombat(
            (creature as? MonsterSpellCaster)?.monster,
            (target as? MonsterSpellTarget)?.creature,
            target.getPosition(),
        )
    }

    companion object {
        /** `COMBAT_PARAM_TYPE` (`src/creatures/creatures_definitions.hpp:297`). */
        const val COMBAT_PARAM_TYPE = 0

        /** `COMBAT_PARAM_EFFECT` (`creatures_definitions.hpp:298`). */
        const val COMBAT_PARAM_EFFECT = 1

        /** `COMBAT_PARAM_DISTANCEEFFECT` (`creatures_definitions.hpp:299`). */
        const val COMBAT_PARAM_DISTANCEEFFECT = 2

        /** `COMBAT_PARAM_BLOCKSHIELD` (`creatures_definitions.hpp:300`). */
        const val COMBAT_PARAM_BLOCKSHIELD = 3

        /** `COMBAT_PARAM_BLOCKARMOR` (`creatures_definitions.hpp:301`). */
        const val COMBAT_PARAM_BLOCKARMOR = 4

        /** `COMBAT_PARAM_TARGETCASTERORTOPMOST` (`creatures_definitions.hpp:302`). */
        const val COMBAT_PARAM_TARGETCASTERORTOPMOST = 5

        /** `COMBAT_PARAM_CREATEITEM` (`creatures_definitions.hpp:303`). */
        const val COMBAT_PARAM_CREATEITEM = 6

        /** `COMBAT_PARAM_AGGRESSIVE` (`creatures_definitions.hpp:304`). */
        const val COMBAT_PARAM_AGGRESSIVE = 7

        /** `COMBAT_PARAM_DISPEL` (`creatures_definitions.hpp:305`). */
        const val COMBAT_PARAM_DISPEL = 8

        /** `COMBAT_PARAM_USECHARGES` (`creatures_definitions.hpp:306`). */
        const val COMBAT_PARAM_USECHARGES = 9

        /** `COMBAT_PARAM_CASTSOUND` (`creatures_definitions.hpp:308`). */
        const val COMBAT_PARAM_CASTSOUND = 10

        /** `COMBAT_PARAM_IMPACTSOUND` (`creatures_definitions.hpp:309`). */
        const val COMBAT_PARAM_IMPACTSOUND = 11

        /** `COMBAT_PARAM_CHAIN_EFFECT` (`creatures_definitions.hpp:310`). */
        const val COMBAT_PARAM_CHAIN_EFFECT = 12
    }
}

/**
 * Monster-side `CombatSpell` actor (`src/creatures/combat/spells.hpp:106-128`):
 * the `spellBlock_t.spell` of an `addAttack`/`addDefense` block. It composes the
 * ported [CombatSpell] over the [MonsterCombatSpellCombat] payload built by
 * `Monsters::deserializeSpell`, and adapts the monster cast call
 * (`spellBlock.spell->castSpell(monster, target)`, monster.cpp:1979) onto the
 * `SpellCaster`/`SpellTarget` seams.
 *
 * The monster `CombatSpell` is never scripted: `deserializeSpell` never loads a
 * script id, so [CombatSpellScriptRunner] stays inert and only the combat branch
 * is reachable.
 */
class MonsterCombatSpell(
    /** `CombatSpell::getCombat` (`spells.cpp:374-376`) over `CombatSpell::m_combat` (`spells.hpp:126`). */
    val combat: MonsterCombatSpellCombat,
    needTarget: Boolean,
    needDirection: Boolean,
) : BaseSpell {

    private val actor: CombatSpell =
        CombatSpell(combat, needTarget, needDirection, CombatSpellScriptRunner { _, _ -> false })

    /** `BaseSpell::soundCastEffect` (`spells.hpp:100`). */
    var soundCastEffect: SoundEffect
        get() = actor.soundCastEffect
        set(value) {
            actor.soundCastEffect = value
        }

    /** `BaseSpell::soundImpactEffect` (`spells.hpp:99`). */
    var soundImpactEffect: SoundEffect
        get() = actor.soundImpactEffect
        set(value) {
            actor.soundImpactEffect = value
        }

    /** `spellBlock.spell->castSpell(monster, target)` (monster.cpp:1979). */
    override fun castSpell(caster: Monster, target: Creature) {
        actor.castSpell(MonsterSpellCaster(caster), MonsterSpellTarget(target))
    }
}
