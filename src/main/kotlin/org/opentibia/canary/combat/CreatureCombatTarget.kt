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
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.creatures.players.getMantra
import org.opentibia.canary.creatures.players.hasPermittedConditionInPZ
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.items.ItemProperty
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile

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

    /**
     * `Creature::drainHealth(attacker, damage)` (`creature.cpp:893-899`): the
     * `changeHealth(-amount, false)` core plus the attacker credit over the
     * live creature. The C++ attacker is the raw `std::shared_ptr<Creature>`;
     * the [CombatTarget] view is unwrapped through this adapter (the game and
     * Lua factories always produce it), so a seam-only attacker without a live
     * creature records nothing.
     */
    override fun drainHealth(attacker: CombatTarget?, amount: Int): Int {
        creature.drainHealth((attacker as? CreatureCombatTarget)?.creature, amount)
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

/**
 * Production [CombatActor] view of a live game [Creature].
 *
 * The C++ target-validation and combat-entry functions receive the raw
 * `std::shared_ptr<Creature>`; the port replaces it with the injected
 * [CombatActor] seam. This adapter transcribes the `Creature`/`Monster` half,
 * with the game-owned reads (`isAttackable`, the monster faction/enemy lookups,
 * `onAttackedByPlayer`) coming from the injected [state]. It backs the world
 * gates that need a `CombatActor` without a Lua environment, e.g. the
 * `Combat::isProtected` check of `MagicField::onStepInField`
 * (src/creatures/combat/combat.cpp:2628-2633).
 *
 * PARITY GAP: `Creature::getFaction()` uses the same projection as the Lua
 * [org.opentibia.canary.lua.bindings.LuaCombatWorld] default
 * (`Faction.Player` for players, `Faction.Default` otherwise); the
 * monster-type faction table stays with the host state.
 */
class CreatureCombatActor(
    /** The adapted creature. */
    val creature: Creature,
    private val state: CombatTargetState = CombatTargetState.Inert,
) : CombatActor {

    /** `Creature::getPlayer()`; null for monsters and NPCs. */
    override fun getPlayer(): CombatPlayer? = (creature as? Player)?.let { PlayerCombatActor(it, state) }

    /** `Creature::getMonster()`; null for players and NPCs. */
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

    /** `Creature::getNpc()` non-null check (combat.cpp:547). */
    override fun getNpc(): Boolean = creature is Npc

    /** `Creature::getMaster()`. */
    override fun getMaster(): CombatActor? = creature.getMaster()?.let { CreatureCombatActor(it, state) }

    /** `Creature::isSummon()`. */
    override fun isSummon(): Boolean = creature.isSummon()

    /** `Creature::isAttackable()` (combat.cpp:348). */
    override fun isAttackable(): Boolean = state.isAttackable(creature)

    /** `Creature::getZoneType()`. */
    override fun getZoneType(): ZoneType = creature.getZoneType()

    /** `Creature::getFaction()` (combat.cpp:498-541); see the class PARITY GAP. */
    override fun getFaction(): Faction = if (creature is Player) Faction.Player else Faction.Default

    /** `Creature::getPosition()`. */
    override fun getPosition(): Position = creature.position

    /** `Creature::getTile()`. */
    override fun getTile(): CombatTile? = creature.getTile()?.let { CreatureCombatTile(it, state) }
}

/**
 * Production [CombatPlayer] view of a live game [Player].
 *
 * `Combat::isProtected` (`src/creatures/combat/combat.cpp:432-447`) reads the
 * level, vocation, skull and PZ-permission members; every accessor transcribes
 * the `Player` symbol it replaces, mirroring the Lua
 * [org.opentibia.canary.lua.bindings.LuaCombatPlayer] adapter without the Lua
 * environment.
 */
class PlayerCombatActor(
    /** The adapted player. */
    val player: Player,
    private val state: CombatTargetState = CombatTargetState.Inert,
) : CombatPlayer {

    /** `Player::getPlayer()` — a player is its own player view. */
    override fun getPlayer(): CombatPlayer = this

    /** `Player::getMonster()` — null for a player. */
    override fun getMonster(): CombatMonster? = null

    /** `Player::getNpc()` — false for a player. */
    override fun getNpc(): Boolean = false

    /** `Player::getMaster()`. */
    override fun getMaster(): CombatActor? = player.getMaster()?.let { CreatureCombatActor(it, state) }

    /** `Player::isSummon()`. */
    override fun isSummon(): Boolean = player.isSummon()

    /** `Player::isAttackable()` (`combat.cpp:348`). */
    override fun isAttackable(): Boolean = state.isAttackable(player)

    /** `Player::getZoneType()`. */
    override fun getZoneType(): ZoneType = player.getZoneType()

    /** `Player::getFaction()`; see [CreatureCombatActor]'s PARITY GAP. */
    override fun getFaction(): Faction = Faction.Player

    /** `Player::getPosition()`. */
    override fun getPosition(): Position = player.position

    /** `Player::getTile()`. */
    override fun getTile(): CombatTile? = player.getTile()?.let { CreatureCombatTile(it, state) }

    /** `Player::hasFlag(flag)` (`combat.cpp:326`, `348`). */
    override fun hasFlag(flag: PlayerFlags_t): Boolean = player.hasFlag(flag)

    /** `Player::getLevel()` (`combat.cpp:434`). */
    override fun getLevel(): Int = player.level

    /** `Player::getSkull()` (`combat.cpp:442`). */
    override fun getSkull(): Skulls_t = player.getSkull()

    /** `Player::getSkullClient(creature)` (`combat.cpp:442`, player.cpp:7127-7151). */
    override fun getSkullClient(creature: CombatActor): Skulls_t {
        val target = when (creature) {
            is CreatureCombatActor -> creature.creature
            is PlayerCombatActor -> creature.player
            else -> return Skulls_t.SKULL_NONE
        }
        return player.pvpPlayer.getSkullClient(target)
    }

    /** `Player::hasSecureMode()` (`combat.cpp:368`). */
    override fun hasSecureMode(): Boolean = player.secureMode

    /** `Player::hasPermittedConditionInPZ()` (`combat.cpp:461`, player.cpp:12897-12921). */
    override fun hasPermittedConditionInPZ(): Boolean = player.hasPermittedConditionInPZ()

    /** `Player::getVocation()->canCombat()` (`combat.cpp:438`). */
    override fun getVocationCanCombat(): Boolean =
        player.vocations?.getVocation(player.vocationId)?.canCombat() ?: false

    /** `Player::getVocationId()` (`combat.cpp:438`). */
    override fun getVocationId(): Int = player.getVocationId()
}

/**
 * Production [CombatTile] view of a live game [Tile]
 * (`Game::map.getTile(pos)`), carrying the `Tile` accessors of
 * `Combat::canDoCombat`/`Combat::CombatFunc` (`combat.cpp:376-426`,
 * `1507-1643`).
 */
class CreatureCombatTile(
    /** The adapted tile. */
    val tile: Tile,
    private val state: CombatTargetState = CombatTargetState.Inert,
) : CombatTile {

    /** `Tile::hasFlag(flag)` (`combat.cpp:398-460`). */
    override fun hasFlag(flag: TileFlags): Boolean = tile.hasFlag(flag.value)

    /** `Tile::hasProperty(prop)` (`combat.cpp:381`, `457`). */
    override fun hasProperty(property: Int): Boolean {
        val itemProperty = ItemProperty.entries.firstOrNull { it.value == property } ?: return false
        return tile.hasProperty(itemProperty)
    }

    /** `Tile::getPosition()` (`combat.cpp:408`). */
    override fun getPosition(): Position = tile.position

    /** `Tile::getTeleportItem()` (`combat.cpp:402`). */
    override fun getTeleportItem(): Boolean = tile.getTeleportItem() != null

    /** The `Tile::getItemList()` item ids (`combat.cpp:384-391`, magic wall check). */
    override fun getItemIds(): List<Int> = tile.items.map { it.id }

    /** `Tile::getCreatures()` (`combat.cpp:1536`, `1610`). */
    override fun getCreatures(): List<CombatActor> =
        tile.getCreatures().map { CreatureCombatActor(it, state) }

    /** `Tile::getTopCreature()` (`combat.cpp:1537`, `1612`). */
    override fun getTopCreature(): CombatActor? =
        tile.getTopCreature()?.let { CreatureCombatActor(it, state) }
}
