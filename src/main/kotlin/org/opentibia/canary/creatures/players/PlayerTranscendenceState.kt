package org.opentibia.canary.creatures.players

import java.util.Random
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionOutfit
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.uniformRandom
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.scheduling.SCHEDULER_MINTICKS
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.wheel.WheelOnThink

/** `CONST_ME_AVATAR_APPEAR` (`src/utils/utils_definitions.hpp:215`). */
private const val CONST_ME_AVATAR_APPEAR = 244

/** `TRANSCENDENCE_AVATAR_DURATION` default (configmanager.cpp:369). */
private const val TRANSCENDENCE_AVATAR_DURATION_DEFAULT = 7000

/** `CHECK_AGGRESSIVE_ACTION_INTERVAL` used by the avatar gate (player.cpp:11201). */
private const val TRANSCENDENCE_AGGRESSIVE_ACTION_INTERVAL = 2000

/**
 * `Player::updateAimAtTargetSpells`/`getAimAtTargetSpells`
 * (player.cpp:13361-13371) over the C++
 * `std::unordered_set<uint16_t> aimAtTargetSpellIds` (player.hpp:1705).
 *
 * The methods keep the exact `Player::` names; the spell ids are the 16-bit
 * values the C++ stores.
 */
class PlayerAimAtTargetState {

    /** `std::unordered_set<uint16_t> aimAtTargetSpellIds` (player.hpp:1705). */
    private val aimAtTargetSpellIds = linkedSetOf<Int>()

    /** `Player::updateAimAtTargetSpells` (player.cpp:13361-13367). */
    fun updateAimAtTargetSpells(spellId: Int, state: Int) {
        if (state != 0) {
            aimAtTargetSpellIds.add(spellId)
        } else {
            aimAtTargetSpellIds.remove(spellId)
        }
    }

    /** `Player::getAimAtTargetSpells` (player.cpp:13369-13371). */
    fun getAimAtTargetSpells(): Set<Int> = aimAtTargetSpellIds
}

/**
 * `Player::triggerTranscendence` (player.cpp:11178-11243).
 *
 * The wheel avatar-forge on-think timer, the tiered legs/boots equipment and
 * the vocation avatar look type are real port state. The
 * `TRANSCENDENCE_AVATAR_DURATION` config, the C++ `uniform_random`,
 * `g_game().addMagicEffect`/`reloadCreature`,
 * `PlayerWheel::sendGiftOfLifeCooldown`, the protocol sends and the
 * post-transcendence refresh task are seams per AGENTS.md 1.1.
 *
 * The refresh callback captures only the immutable send seams; the host that
 * schedules it must re-resolve the player by id and drop the callback when the
 * player is gone or replaced (AGENTS.md deferred-callback rule). The scheduled
 * delay is `std::max<uint32_t>(SCHEDULER_MINTICKS, duration)` (player.cpp:11219).
 */
fun Player.triggerTranscendence(
    /** `OTSYS_TIME()` (player.cpp:11179, 11201). */
    now: Long = System.currentTimeMillis(),
    /** `uniform_random(0, 10000) / 100.` (player.cpp:11199). */
    randomChance: Double = uniformRandom(0, 10000, Random()) / 100.0,
    /** `g_configManager().getNumber(TRANSCENDENCE_AVATAR_DURATION)` (player.cpp:11203). */
    duration: Int = TRANSCENDENCE_AVATAR_DURATION_DEFAULT,
    /** `Creature::getZoneType()` (player.cpp:11201). */
    zoneType: () -> ZoneType = { getZoneType() },
    /** `Player::checkLastAggressiveActionWithin(2000)` (player.cpp:11201). */
    checkLastAggressiveActionWithin: (Int) -> Boolean = { false },
    /** `Player::sendSkills()` (player.cpp:11215). */
    sendSkills: () -> Unit = {},
    /** `Player::sendStats()` (player.cpp:11216). */
    sendStats: () -> Unit = {},
    /** `Player::sendBasicData()` (player.cpp:11217). */
    sendBasicData: () -> Unit = {},
    /** `Player::sendTextMessage(MESSAGE_ATTENTION, ...)` (player.cpp:11219). */
    sendTextMessage: (MessageClass, String) -> Unit = { _, _ -> },
    /** `g_game().addMagicEffect(getPosition(), CONST_ME_AVATAR_APPEAR)` (player.cpp:11214). */
    addMagicEffect: (Position, Int) -> Unit = { _, _ -> },
    /** `g_dispatcher().scheduleEvent(task)` (player.cpp:11228). */
    scheduleRefresh: (Long, () -> Unit) -> Unit = { _, _ -> },
    /** `PlayerWheel::sendGiftOfLifeCooldown()` (player.cpp:11239). */
    sendGiftOfLifeCooldown: () -> Unit = {},
    /** `g_game().reloadCreature(getPlayer())` (player.cpp:11240). */
    reloadCreature: () -> Unit = {},
) {
    if (wheel().getOnThinkTimer(WheelOnThink.AVATAR_FORGE) > now) {
        return
    }

    val item = getInventoryItem(Slots_t.CONST_SLOT_LEGS) ?: return
    if (item.getTier() == 0) {
        return
    }

    var chance = item.getTranscendenceChance()
    val playerBoots = getInventoryItem(Slots_t.CONST_SLOT_FEET)
    if (playerBoots != null && playerBoots.getTier() > 0) {
        val amplificationChange = playerBoots.getAmplificationChance() / 100
        chance *= 1 + amplificationChange
    }

    if (zoneType() != ZoneType.ZONE_PROTECTION &&
        checkLastAggressiveActionWithin(TRANSCENDENCE_AGGRESSIVE_ACTION_INTERVAL) &&
        (now / 1000) % 2 == 0L &&
        chance > 0 &&
        randomChance < chance
    ) {
        val outfitCondition = Condition.createCondition(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_OUTFIT,
            duration,
            0,
        ) as? ConditionOutfit ?: return

        val outfit = Outfit().apply {
            lookType = vocations?.getVocation(vocationId)?.getAvatarLookType() ?: 0
        }
        outfitCondition.setOutfit(outfit)
        addCondition(outfitCondition)

        wheel().setOnThinkTimer(WheelOnThink.AVATAR_FORGE, now + duration)
        addMagicEffect(position, CONST_ME_AVATAR_APPEAR)

        sendSkills()
        sendStats()
        sendBasicData()

        sendTextMessage(MessageClass.MESSAGE_ATTENTION, "Transcendence was triggered.")

        scheduleRefresh(maxOf(SCHEDULER_MINTICKS, duration.toLong())) {
            sendSkills()
            sendStats()
            sendBasicData()
        }

        sendGiftOfLifeCooldown()
        reloadCreature()
    }
}
