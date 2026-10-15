package org.opentibia.canary.spells

import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountType
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.ZoneType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.players.PlayerAttackState
import org.opentibia.canary.creatures.players.canExiva
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.ZoneType as GameZoneType
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Production [SpellCaster] adapter over a live game [Player].
 *
 * `Spell::playerSpellCheck`/`Player::playerCastInstant` and the rune checks
 * (src/creatures/combat/spells.cpp) read the raw `Player`; the port's
 * [SpellCaster] seam replaces it, so the spell host constructs this adapter.
 * Every member transcribes the exact `Player`/`Creature` symbol it replaces,
 * including the composed state: [getAimAtTargetSpells] returns
 * `Player::getAimAtTargetSpells` (`player.cpp:13369-13371`) populated by the
 * client's aim-at-target packet through
 * `Player::updateAimAtTargetSpells` (`player.cpp:13361-13367`), so
 * `applyInstantSpellDirection` turns a marked direction spell toward the
 * attacked creature (spells.cpp:47-59).
 *
 * The `Spell::postCastSpell` side effects (spells.cpp:913-939) are live by
 * default: [addManaSpent] transcribes `Player::addManaSpent`
 * (player.cpp:3554-3606), [sendCancelMessage] routes the `ReturnValue` through
 * `Player::sendCancelMessage` (player.cpp:2117-2119) and
 * [updateLastAggressiveAction] writes the composed [PlayerAttackState] clock
 * (`Player::lastAggressiveAction`, player.hpp:1880). `Player::changeMana`'s
 * `g_game().addPlayerMana`/`Player::sendStats` side effects are not dispatched
 * (player.cpp:6765-6771; PARITY GAP).
 */
class PlayerSpellCaster(
    /** The wrapped player. */
    val player: Player,
    /**
     * `Player::lastAggressiveAction` state (`Player::updateLastAggressiveAction`,
     * player.cpp:1186-1188) through the composed [PlayerAttackState]. The host
     * player must inject the same instance it exposes to the transcendence gate
     * (`Player::checkLastAggressiveActionWithin`, player.cpp:11201); without one
     * the write stays inert (PARITY GAP, the port's `Player` does not own the
     * attack state yet).
     */
    private val attackState: PlayerAttackState? = null,
    /** `Player::addManaSpent(amount)` (`player.cpp:3554-3606`) progression seam. */
    private val manaSpentProgression: (Player, Int) -> Unit = ::addManaSpentProgression,
    /** `Player::sendCancelMessage(ret)` (`player.cpp:2117-2119`) protocol seam. */
    private val cancelMessageSink: (Player, ReturnValue) -> Unit = { target, ret ->
        target.sendCancelMessage.invoke(ret)
    },
): SpellCaster {
    /** `Creature::getID()`. */
    override fun getID(): Long = player.id

    /** `Creature::getName()`. */
    override fun getName(): String = player.name

    /** `Creature::getPosition()`. */
    override fun getPosition(): Position = player.position

    /** `Creature::getDirection()`. */
    override fun getDirection(): Direction = player.direction

    /** `Player::getLevel()`. */
    override fun getLevel(): Int = player.level

    /** `Player::getMagicLevel()`. */
    override fun getMagicLevel(): Int = player.getMagicLevel()

    /** `Creature::getMana()`. */
    override fun getMana(): Int = player.mana

    /** `Creature::getMaxMana()`. */
    override fun getMaxMana(): Int = player.maxMana

    /** `Player::getSoul()` (`player.cpp:671` via `PlayerVitalsState`). */
    override fun getSoul(): Int = player.features.vitals.getSoul()

    /** `Player::getVocationId()`. */
    override fun getVocationId(): Int = player.getVocationId()

    /**
     * `Player::getWeaponType()` (`player.cpp:466-472`): the equipped weapon's
     * type through `Player::getWeapon()`.
     */
    override fun getWeaponType(): WeaponType_t = player.getWeapon()?.getWeaponType() ?: WeaponType_t.WEAPON_NONE

    /** `Player::getZoneType()` (`spells.cpp:523`). */
    override fun getZoneType(): ZoneType = when (player.getZoneType()) {
        GameZoneType.ZONE_PROTECTION -> ZoneType.ZONE_PROTECTION
        GameZoneType.ZONE_NOPVP -> ZoneType.ZONE_NO_PVP
        GameZoneType.ZONE_PVP -> ZoneType.ZONE_PVP
        // `ZONE_NOLOGOUT` has no counterpart in the condition seam's ZoneType.
        GameZoneType.ZONE_NOLOGOUT,
        GameZoneType.ZONE_NORMAL,
        -> ZoneType.ZONE_NORMAL
    }

    /** `Creature::getSkull()`. */
    override fun getSkull(): Skulls_t = player.getSkull()

    /** `Player::isPremium()`. */
    override fun isPremium(): Boolean = player.isPremium()

    /** `Player::getGroup()->id` (`spells.cpp:577`). */
    override fun getGroupId(): Int = player.group?.id ?: 0

    /** `Player::isAccessPlayer()` (`player.cpp:7524`). */
    override fun isAccessPlayer(): Boolean = player.isAccessPlayer()

    /** `Player::isFirstOnStack()` (`player.cpp:13157-13172`). */
    override fun isFirstOnStack(): Boolean {
        val tile = player.getTile() ?: return false
        val bottomCreature = tile.getBottomCreature() ?: return false
        val bottomPlayer = bottomCreature as? Player ?: return false

        if (bottomPlayer.isInGhostMode()) {
            return true
        }

        if (player.hasCondition(ConditionType.CONDITION_SPELLCOOLDOWN)) {
            return false
        }

        return player === bottomPlayer
    }

    /** `Player::getAccountType()`. */
    override fun getAccountType(): AccountType = player.getAccountType()

    /** `Player::getAccount()`. */
    override fun getAccount(): Account? = player.account

    /** `Player::isOffline()` (`player.hpp:481-483`): `getID() == 0`. */
    override fun isOffline(): Boolean = player.id == 0L

    /** `Creature::getTile()` as the [SpellTile] view (`spells.cpp:84`, `93`). */
    override fun getTile(): SpellTile? = player.getTile()?.let { PlayerSpellTile(it) }

    /**
     * `Player::canExiva(spellParam)` (`player.cpp:7000-7048`). The `NO_PVP`
     * restriction needs `g_game().getWorldType()`/`g_game().getPlayerByName`
     * and the target's VIP component, which the extension's defaults treat as
     * an unrestricted PvP world.
     */
    override fun canExiva(spellParam: String): Boolean = player.canExiva(spellParam)

    /** `Player::sendTextMessage(type, text)` through the protocol sink seam. */
    override fun sendTextMessage(type: MessageClass, text: String) {
        player.sendTextMessageTyped(type, text)
    }

    /** `Player::hasFlag(flag)`. */
    override fun hasFlag(flag: PlayerFlags_t): Boolean = player.hasFlag(flag)

    /** `Creature::hasCondition(type, subId)`. */
    override fun hasCondition(type: ConditionType, subId: Int): Boolean = player.hasCondition(type, subId)

    /** `Player::learnedInstantSpellList` storage (`player.hpp:1696`). */
    override fun getSpellBook(): SpellBook? = player.spellBook

    /** `Player::getAttackedCreature()` as the [SpellTarget] view (`spells.cpp:1188`, `1216`). */
    override fun getAttackedCreature(): SpellTarget? = player.getAttackedCreature()?.let { CreatureSpellTarget(it) }

    /**
     * `Player::getAimAtTargetSpells()` (`player.cpp:13369-13371`): the
     * composed aim-at-target set written by
     * `Player::updateAimAtTargetSpells` (protocolgame.cpp:12196-12209).
     */
    override fun getAimAtTargetSpells(): Set<Int> = player.getAimAtTargetSpells()

    /** `Creature::addCondition(condition)`. */
    override fun addCondition(condition: Condition): Boolean = player.addCondition(condition)

    /** `Player::sendCancelMessage(ret)` (`player.cpp:2117-2119`) through the protocol seam. */
    override fun sendCancelMessage(ret: ReturnValue) {
        cancelMessageSink(player, ret)
    }

    /**
     * `Player::addManaSpent(amount)` (player.cpp:3554-3606): the vocation
     * magic-level progression. The default [manaSpentProgression] seam binds
     * the ported transcription below.
     */
    override fun addManaSpent(manaCost: Int) {
        manaSpentProgression(player, manaCost)
    }

    /**
     * `Player::changeMana(manaChange)` (`player.cpp:6765-6771`) reduced to the
     * `Creature::changeMana` clamp (creature.cpp:878-884) behind the
     * `HasInfiniteMana` gate.
     */
    override fun changeMana(delta: Int) {
        if (player.hasFlag(PlayerFlags_t.HasInfiniteMana)) {
            return
        }

        if (delta > 0) {
            player.mana += minOf(delta, player.maxMana - player.mana)
        } else {
            player.mana = maxOf(0, player.mana + delta)
        }
    }

    /** `Player::changeSoul(soulChange)` (`PlayerVitalsState`). */
    override fun changeSoul(delta: Int) {
        player.features.vitals.changeSoul(delta)
    }

    /** `Player::addInFightTicks()` (`player.cpp`). */
    override fun addInFightTicks() {
        player.addInFightTicks()
    }

    /** `Player::updateLastAggressiveAction()` (`player.cpp:1186-1188`). */
    override fun updateLastAggressiveAction() {
        attackState?.updateLastAggressiveAction()
    }
}

/**
 * `Player::addManaSpent(amount)` (src/creatures/players/player.cpp:3554-3606)
 * transcribed over the public `Player` state: the `NotGainMana` early return,
 * the vocation requirement table (`Vocation::getReqMana`, vocation.cpp:295),
 * the magic-level loop with its advance message, and the trailing
 * `sendStats()`/`sendSkills()` refresh.
 *
 * PARITY GAPS: the `g_events().eventPlayerOnGainSkillTries` /
 * `g_callbacks()` notifications, the level-up screenshot and the
 * `g_creatureEvents().playerAdvance` dispatch (player.cpp:3566-3567, 3582, 3584)
 * stay Lua/host side, exactly like `Player::addSkillAdvance`
 * (`Creature.addSkillTries`, player.cpp:1190-1249); the `magLevelPercent` write
 * (player.cpp:3597-3601) is not reachable because the port's `Player` keeps the
 * field private, so the percent refresh awaits a host accessor.
 */
internal fun addManaSpentProgression(player: Player, amount: Int) {
    if (player.hasFlag(PlayerFlags_t.NotGainMana)) {
        return
    }

    val vocation = player.vocations?.getVocation(player.vocationId) ?: return
    var currReqMana = vocation.getReqMana(player.magLevel)
    var nextReqMana = vocation.getReqMana(player.magLevel + 1)
    if (currReqMana >= nextReqMana) {
        // player has reached max magic level
        return
    }

    if (amount == 0) {
        return
    }

    var remaining = amount
    var sendUpdateStats = false
    while (player.manaSpent + remaining >= nextReqMana) {
        remaining -= (nextReqMana - player.manaSpent).toInt()

        player.magLevel++
        player.manaSpent = 0

        player.sendTextMessageTyped(
            MessageClass.MESSAGE_EVENT_ADVANCE,
            "You advanced to magic level ${player.magLevel}.",
        )

        sendUpdateStats = true
        currReqMana = nextReqMana
        nextReqMana = vocation.getReqMana(player.magLevel + 1)
        if (currReqMana >= nextReqMana) {
            return
        }
    }

    player.manaSpent += remaining

    if (sendUpdateStats) {
        player.sendStats()
        player.sendSkills()
    }
}

/**
 * `Creature` view of the [SpellTarget] seam (the `Spell::playerInstantSpellCheck`
 * and direction reads of spells.cpp).
 */
class CreatureSpellTarget(
    private val creature: Creature,
) : SpellTarget {

    override fun getID(): Long = creature.id

    override fun getName(): String = creature.name

    override fun getPosition(): Position = creature.position

    override fun getHealth(): Int = creature.health

    override fun isRemoved(): Boolean = creature.isRemoved()

    override fun isAccessPlayer(): Boolean = (creature as? Player)?.isAccessPlayer() ?: false
}

/**
 * `Tile` view of the [SpellTile] seam: `Tile::hasFlag(flag)` over the
 * `TILESTATE_*` bitmask (`spells.cpp:85`, `93`).
 */
class PlayerSpellTile(
    private val tile: Tile,
) : SpellTile {
    override fun hasFlag(flag: TileFlags): Boolean = tile.hasFlag(flag.value)
}
