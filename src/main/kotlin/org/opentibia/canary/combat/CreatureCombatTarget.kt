package org.opentibia.canary.combat

import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionPlayer
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.MonkData
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.creatures.players.getMantra
import org.opentibia.canary.creatures.skills_t

/**
 * Injected view of the game-owned creature combat state the production
 * [CreatureCombatTarget] adapter reads.
 *
 * The C++ `Combat::doCombatHealth`/`doCombatMana` chain calls
 * `g_game().combatBlockHit`/`combatChangeHealth`, which read `Creature`/`Game`
 * state. [CreatureCombatTarget] transcribes the `Creature` half directly; this
 * seam carries the `Game`/`Monster` half (`combatIsImmune`, `getDefense`,
 * `getArmor`, `getMitigation`, absorb/increase seams, the `Monster` faction
 * lookup and the `Monster::onAttackedByPlayer` route). Every member cites the
 * C++ symbol it replaces. The defaults are the inert values so an embedded host
 * without the full seam set still dispatches damage.
 */
interface CombatTargetState {
    /** `Game::combatChangeHealth`'s `Creature::isAttackable()` seam (`game.cpp:8387`). */
    fun isAttackable(creature: Creature): Boolean = true

    /** `Creature::isImmune(combatType)` (`creature.cpp:955`). */
    fun isImmune(creature: Creature, combatType: CombatType): Boolean = false

    /**
     * `Creature::isImmune(conditionType)` (`creature.hpp:522`); the monster
     * type's condition-immunity bitset (`Monster::isImmune`, monster.cpp:3535)
     * lives on the host-owned `MonsterTypeState`, so the production resolution
     * is `MonsterPipelinesHost.combatTargetState()`; hosts without it keep the
     * inert default (an unregistered creature is never immune).
     */
    fun isImmuneCondition(creature: Creature, conditionType: ConditionType): Boolean = false

    /** `Creature::getDefense()` (`creature.cpp:970`). */
    fun getDefense(creature: Creature): Int = 0

    /** `Creature::getArmor()` (`creature.cpp:978`). */
    fun getArmor(creature: Creature): Int = 0

    /** `Creature::blockHit`'s `blockCount > 0` local (`creature.cpp:963-968`). */
    fun hasDefense(creature: Creature): Boolean = false

    /** `Creature::canUseDefense` (`creature.cpp:969`). */
    fun canUseDefense(creature: Creature): Boolean = false

    /** `Creature::getMitigation()` (`creature.cpp:910-922`). */
    fun getMitigation(creature: Creature): Double = 0.0

    /** `Creature::getAbsorbPercent(combat)` (`creature.cpp:1979`). */
    fun getAbsorbPercent(creature: Creature, combatType: CombatType): Int = 0

    /** `Creature::getAbsorbFlat(combat)` (`creature.cpp:1962`). */
    fun getAbsorbFlat(creature: Creature, combatType: CombatType): Int = 0

    /** `Creature::getIncreasePercent(combat)` (`creature.cpp:1996`). */
    fun getIncreasePercent(creature: Creature, combatType: CombatType): Int = 0

    /**
     * `Player::getMantra()` (`combat.cpp:748`) transcribed over the live
     * [Player] (`Player::getMantra`, player.cpp:671-694, sums the seven armor
     * slots through `PlayerEquipmentState.getMantra`); non-players contribute
     * zero.
     */
    fun getMantra(creature: Creature): Int = (creature as? Player)?.getMantra() ?: 0

    /**
     * `Monster::isEnemyFaction(faction)` (`monster.cpp:267-273`); the monster
     * type's `enemyFactions` live on the host-owned `MonsterTypeState`, so the
     * production resolution is `MonsterPipelinesHost.combatTargetState()`; hosts
     * without it keep the inert default (no known enemies).
     */
    fun isEnemyFaction(monster: Monster, faction: Faction): Boolean = false

    /**
     * `Monster::onAttackedByPlayer(attackerPlayer)` (`monster.cpp:600-625`)
     * fired by `Combat::CombatHealthFunc` (combat.cpp:895-899). The production
     * host routes this to `MonsterPipelinesHost::onAttackedByPlayer`.
     */
    fun onAttackedByPlayer(monster: Monster, attackerPlayer: Player) {}

    companion object {
        /** The inert state used when a host injects no game seams. */
        val Inert: CombatTargetState = object : CombatTargetState {}
    }
}

/**
 * Production [CombatTarget] adapter over a live game [Creature].
 *
 * In C++ the combat core receives the raw `std::shared_ptr<Creature>`; the port
 * replaces it with the injected [CombatTarget] seam, so a host that wants the
 * ported damage dispatch to run against the world constructs this adapter
 * (e.g. `Game.combatTargetOf`). Every member transcribes the exact
 * `Creature`/`Player`/`Monster` symbol it replaces; the game-owned reads come
 * from the injected [state].
 *
 * The adapter is deliberately per-call and stateless: it holds no deferred
 * references, so a creature removed or replaced between calls cannot leak
 * state (AGENTS.md deferred-callback lifetime safety).
 */
class CreatureCombatTarget(
    /** The adapted creature. */
    val creature: Creature,
    private val state: CombatTargetState = CombatTargetState.Inert,
) : CombatTarget {

    /** `Creature::getHealth()`. */
    override fun getHealth(): Int = creature.health

    /** `Creature::getMaxHealth()`. */
    override fun getMaxHealth(): Int = creature.maxHealth

    /**
     * `Creature::gainHealth(nullptr, amount)` (`creature.cpp:886-891`) reduced
     * to its `changeHealth(amount)` core; the spectator broadcast is owned by
     * the caller in this port.
     */
    override fun addHealth(amount: Int): Int {
        creature.changeHealth(amount)
        return creature.health
    }

    /**
     * `Creature::drainHealth(amount)` (`creature.cpp:893-899`) reduced to its
     * `changeHealth(-amount, false)` core.
     */
    override fun drainHealth(amount: Int): Int {
        creature.changeHealth(-amount, false)
        return creature.health
    }

    /** `Creature::getMana()`. */
    override fun getMana(): Int = creature.mana

    /** `Creature::getMaxMana()`. */
    override fun getMaxMana(): Int = creature.maxMana

    /**
     * `Creature::changeMana(manaChange)` (`creature.cpp:869-875`): positive
     * values restore up to `getMaxMana()`, negative values drain down to 0.
     */
    override fun changeMana(amount: Int): Int {
        if (amount > 0) {
            creature.mana += minOf(amount, creature.maxMana - creature.mana)
        } else {
            creature.mana = maxOf(0, creature.mana + amount)
        }
        return creature.mana
    }

    override fun isAttackable(): Boolean = state.isAttackable(creature)

    override fun isImmune(combatType: CombatType): Boolean = state.isImmune(creature, combatType)

    override fun getDefense(): Int = state.getDefense(creature)

    override fun getArmor(): Int = state.getArmor(creature)

    override fun hasDefense(): Boolean = state.hasDefense(creature)

    override fun canUseDefense(): Boolean = state.canUseDefense(creature)

    override fun getMitigation(): Double = state.getMitigation(creature)

    override fun getAbsorbPercent(combatType: CombatType): Int = state.getAbsorbPercent(creature, combatType)

    override fun getAbsorbFlat(combatType: CombatType): Int = state.getAbsorbFlat(creature, combatType)

    override fun getIncreasePercent(combatType: CombatType): Int = state.getIncreasePercent(creature, combatType)

    override fun getMantra(): Int = state.getMantra(creature)

    /**
     * `Creature::getBuff(index)` (`creature.hpp:309-315`): the `varBuffs` array
     * seeded at 100.
     */
    override fun getBuff(buff: Buff): Int = creature.getBuff(buff.value)

    /** `Creature::getID()` (`creature.hpp:156`) narrowed like the C++ `uint32_t` id. */
    override fun getID(): Int = creature.id.toInt()

    /** `Creature::getPlayer()`; null for monsters and NPCs. */
    override fun getPlayer(): ConditionPlayer? = (creature as? Player)?.let { PlayerConditionTarget(it) }

    /**
     * `Creature::getMonster()` (`combat.cpp:834`) as the [CombatMonster] view;
     * `onAttackedByPlayer` routes the real [Player] through
     * [CombatTargetState.onAttackedByPlayer].
     */
    override fun getMonster(): CombatMonster? {
        val monster = creature as? Monster ?: return null
        return object : CombatMonster {
            override fun isEnemyFaction(faction: Faction): Boolean = state.isEnemyFaction(monster, faction)

            override fun onAttackedByPlayer(attackerPlayer: ConditionPlayer) {
                val attacker = (attackerPlayer as? PlayerConditionTarget)?.player ?: return
                state.onAttackedByPlayer(monster, attacker)
            }
        }
    }

    /** `Creature::getSkull()` (`combat.cpp:989`). */
    override fun getSkull(): Skulls_t = creature.getSkull()

    override fun isImmune(conditionType: ConditionType): Boolean = state.isImmuneCondition(creature, conditionType)

    /** `Creature::addCombatCondition(condition, attackerPlayer)` (`creature.cpp:1393-1404`). */
    override fun addCombatCondition(condition: Condition, attackerPlayer: Boolean): Boolean =
        creature.addCombatCondition(condition, attackerPlayer)
}

/**
 * Production [ConditionPlayer] view of a live [Player].
 *
 * `Combat::CombatHealthFunc` reads `caster->getPlayer()` as the attacking-player
 * identity and hands it to `Monster::onAttackedByPlayer` (combat.cpp:895-899);
 * this adapter carries the real [Player] so the production monster route can be
 * reached. The condition-engine members delegate to the ported `Player` state;
 * the members whose owning subsystem is not ported yet are inert with a PARITY
 * GAP note (`Player::getCharmChanceModifier`, the stat/skill packets,
 * `Player::sendMonkData` and `Player::setImmuneFear`).
 */
class PlayerConditionTarget(
    /** The wrapped player. */
    val player: Player,
) : ConditionPlayer {

    /** `Player::getMaxHealth()`. */
    override fun getMaxHealth(): Int = player.maxHealth

    /** `Player::getMaxMana()`. */
    override fun getMaxMana(): Int = player.maxMana

    /** `Player::getBaseMagicLevel()` (`player.cpp:1120`). */
    override fun getBaseMagicLevel(): Int = player.getBaseMagicLevel()

    /** `Player::getCapacity()`. */
    override fun getCapacity(): Double = player.capacity

    /** `Player::getBaseSkill(skill)` (`player.cpp:1127`). */
    override fun getBaseSkill(skill: Skill): Int {
        val mapped = skills_t.entries.firstOrNull { it.value == skill.value } ?: return 0
        return player.getBaseSkill(mapped)
    }

    /** `Player::setVarSkill(skill, value)` (`player.cpp:1118`). */
    override fun setVarSkill(skill: Skill, value: Int) {
        player.setVarSkill(skill, value)
    }

    /** `Player::setVarStats(stat, value)` (`player.cpp:1100`). */
    override fun setVarStats(stat: Stat, value: Int) {
        player.setVarStats(stat, value)
    }

    /** PARITY GAP: `Player::getCharmChanceModifier` is not modelled on the port's `Player`. */
    override fun getCharmChanceModifier(): Int = 0

    /** PARITY GAP: `Player::setCharmChanceModifier` is not modelled on the port's `Player`. */
    override fun setCharmChanceModifier(value: Int) = Unit

    /** PARITY GAP: the `Player::sendStats` packet is not wired through this view. */
    override fun sendStats() = Unit

    /** PARITY GAP: the `Player::sendSkills` packet is not wired through this view. */
    override fun sendSkills() = Unit

    /** PARITY GAP: the `Player::sendMonkData` packet is not wired through this view. */
    override fun sendMonkData(type: MonkData, value: Int) = Unit

    /** `Player::sendSpellCooldown(spellId, time)` (`player.cpp:8997-9001`). */
    override fun sendSpellCooldown(spellId: Int, time: Int) {
        player.sendSpellCooldown(spellId, time)
    }

    /** `Player::sendSpellGroupCooldown(groupId, time)` (`player.cpp:9003-9007`). */
    override fun sendSpellGroupCooldown(groupId: SpellGroup, time: Int) {
        player.sendSpellGroupCooldown(groupId, time)
    }

    /** PARITY GAP: `Player::setImmuneFear` state lives in the unported soul-war engine. */
    override fun setImmuneFear(immuneTime: Long) = Unit
}
