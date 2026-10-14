package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.spells.Spell

/**
 * `Player::triggerMomentum` and `Player::clearCooldowns`
 * (src/creatures/players/player.cpp:11095-11176).
 *
 * Both methods operate on the `Player` condition list already owned by the
 * host (`Creature.conditions`, creature.hpp:843) and keep the exact `Player::`
 * names of the C++ symbols they transcribe. Every external dependency is a
 * call-site seam per AGENTS.md 1.1:
 *
 *  - `g_spells().getInstantSpellById` becomes [instantSpell];
 *  - `Spell::isSpender`/`Spell::isBuilder` (spells.cpp:1637-1645, over
 *    `monkSpellType`) become [isSpender]/[isBuilder] because the port's
 *    [Spell] does not carry `monkSpellType`;
 *  - `Player::sendSpellCooldown`/`Player::sendSpellGroupCooldown`
 *    (player.cpp:8997-9007, already on the host) become the send callbacks;
 *  - `g_logger().error` becomes [logError];
 *  - for `triggerMomentum`: `OTSYS_TIME()`, `Creature::getZoneType`,
 *    `PlayerWheel::getBonusData().momentum`, global `uniform_random`,
 *    `g_game().addMagicEffect` and `Player::sendTextMessage` become [now],
 *    [zoneType], [momentumBonus], [uniformRandom], [addMagicEffect] and
 *    [sendTextMessage].
 *
 * PARITY GAP: `singleSpellCaster`-style augment/proficiency cooldown
 * reductions on the cast path are not modelled; this file only carries the
 * clearing and Momentum behaviors.
 */

/** `std::numeric_limits<uint16_t>::max()` used by both transcriptions. */
private const val UINT16_MAX = 0xFFFF

/**
 * `Player::clearCooldowns(bool spenders, bool builders, int32_t ticks)`
 * (player.cpp:11139-11176).
 *
 * Without flags every spell and group cooldown condition is zeroed; with
 * `spenders`/`builders` only the matching monk spell kinds clear, and a group
 * cooldown whose sub-id does not resolve to a spell still clears. An
 * unresolvable single-spell cooldown logs and is left untouched.
 */
fun Player.clearCooldowns(
    /** `spenders` (player.cpp:11139). */
    spenders: Boolean = false,
    /** `builders` (player.cpp:11139). */
    builders: Boolean = false,
    /** `ticks` (player.cpp:11139): when > 0 the remaining ticks shrink instead of zeroing. */
    ticks: Int = 0,
    /** `g_spells().getInstantSpellById(spellId)` (player.cpp:11151). */
    instantSpell: (Int) -> Spell? = { null },
    /** `Spell::isSpender()` (spells.cpp:1642-1644; `monkSpellType == Spender`). */
    isSpender: (Spell) -> Boolean = { false },
    /** `Spell::isBuilder()` (spells.cpp:1637-1639; `monkSpellType == Builder`). */
    isBuilder: (Spell) -> Boolean = { false },
    /** `Player::sendSpellCooldown(spellId, time)` (player.cpp:8997-9001). */
    sendSpellCooldown: (Int, Int) -> Unit = { _, _ -> },
    /** `Player::sendSpellGroupCooldown(group, time)` (player.cpp:9003-9007). */
    sendSpellGroupCooldown: (SpellGroup, Int) -> Unit = { _, _ -> },
    /** `g_logger().error` (player.cpp:11156). */
    logError: (String) -> Unit = {},
) {
    for (condition in conditions) {
        val type = condition.type
        val checkSpellId = condition.subId
        val spellId = if (checkSpellId > UINT16_MAX) 0 else checkSpellId
        val spell = instantSpell(spellId)

        if (spell == null) {
            if (type == ConditionType.CONDITION_SPELLCOOLDOWN) {
                logError(
                    "Player::clearCooldowns - Player $name has a cooldown for an invalid spellId $spellId."
                )
                continue
            } else if (type == ConditionType.CONDITION_SPELLGROUPCOOLDOWN) {
                // Allow clearing the group even without a matching spell.
                condition.setTicks(if (ticks > 0) maxOf(0, condition.ticks - ticks) else 0)
                sendSpellGroupCooldown(SpellGroup.fromValue(spellId), maxOf(0, condition.ticks))
                continue
            }
        }

        if (type == ConditionType.CONDITION_SPELLCOOLDOWN ||
            type == ConditionType.CONDITION_SPELLGROUPCOOLDOWN
        ) {
            if ((spenders && spell != null && isSpender(spell)) ||
                (builders && spell != null && isBuilder(spell)) ||
                (!spenders && !builders)
            ) {
                condition.setTicks(if (ticks > 0) maxOf(0, condition.ticks - ticks) else 0)
                val updatedTicks = maxOf(0, condition.ticks)
                if (type == ConditionType.CONDITION_SPELLGROUPCOOLDOWN) {
                    sendSpellGroupCooldown(SpellGroup.fromValue(spellId), updatedTicks)
                } else {
                    sendSpellCooldown(spellId, updatedTicks)
                }
            }
        }
    }
}

/**
 * `Player::triggerMomentum` (player.cpp:11095-11137): with a tiered head item
 * equipped, a positive Momentum chance (boots amplification and wheel bonus
 * included) and the even-second PvP fight gate, every stored cooldown loses
 * 2000 ticks and the player is notified.
 *
 * The C++ loop sets `triggered` for *every* stored condition (player.cpp:11124),
 * not only the cooldown entries, so the effect/message pair fires whenever the
 * player holds any condition at all; that behavior is transcribed verbatim.
 */
fun Player.triggerMomentum(
    /** `OTSYS_TIME()` (utils/time.hpp); milliseconds. */
    now: () -> Long = { System.currentTimeMillis() },
    /** `Creature::getZoneType()` (player.cpp:11119). */
    zoneType: () -> ZoneType = { ZoneType.ZONE_NOPVP },
    /** `m_wheelPlayer.getBonusData().momentum` (player.cpp:11108). */
    momentumBonus: () -> Double = { 0.0 },
    /** Inclusive global `uniform_random(min, max)` (player.cpp:11109). */
    uniformRandom: (Int, Int) -> Double = { min, _ -> min.toDouble() },
    /** `g_game().addMagicEffect(getPosition(), CONST_ME_HOURGLASS)` (player.cpp:11134). */
    addMagicEffect: () -> Unit = {},
    /** `Player::sendTextMessage(MESSAGE_ATTENTION, ...)` (player.cpp:11135). */
    sendTextMessage: (String) -> Unit = {},
    /** `Player::sendSpellCooldown(spellId, time)` (player.cpp:11167). */
    sendSpellCooldown: (Int, Int) -> Unit = { _, _ -> },
    /** `Player::sendSpellGroupCooldown(group, time)` (player.cpp:11167). */
    sendSpellGroupCooldown: (SpellGroup, Int) -> Unit = { _, _ -> },
) {
    val item = getInventoryItem(Slots_t.CONST_SLOT_HEAD) ?: return
    if (item.getTier() == 0) {
        return
    }

    var chance = item.getMomentumChance()
    val playerBoots = getInventoryItem(Slots_t.CONST_SLOT_FEET)
    if (playerBoots != null && playerBoots.getTier() != 0) {
        val amplificationChange = playerBoots.getAmplificationChance() / 100
        chance *= 1 + amplificationChange
    }

    chance += momentumBonus()
    val randomChance = uniformRandom(0, 10000) / 100.0
    if (zoneType() != ZoneType.ZONE_PROTECTION &&
        hasCondition(ConditionType.CONDITION_INFIGHT) &&
        (now() / 1000) % 2 == 0L && chance > 0 && randomChance < chance
    ) {
        var triggered = false
        for (condition in conditions) {
            val type = condition.type
            val checkSpellId = condition.subId
            val spellId = if (checkSpellId > UINT16_MAX) 0 else checkSpellId
            val ticks = condition.ticks
            val newTicks = if (ticks <= 2000) 0 else ticks - 2000
            // player.cpp:11124 sets `triggered` outside the cooldown check.
            triggered = true

            if (type == ConditionType.CONDITION_SPELLCOOLDOWN ||
                (type == ConditionType.CONDITION_SPELLGROUPCOOLDOWN &&
                    spellId > SpellGroup.SPELLGROUP_SUPPORT.value)
            ) {
                condition.setTicks(newTicks)
                if (type == ConditionType.CONDITION_SPELLGROUPCOOLDOWN) {
                    sendSpellGroupCooldown(SpellGroup.fromValue(spellId), newTicks)
                } else {
                    sendSpellCooldown(spellId, newTicks)
                }
            }
        }

        if (triggered) {
            addMagicEffect()
            sendTextMessage("Momentum was triggered.")
        }
    }
}
