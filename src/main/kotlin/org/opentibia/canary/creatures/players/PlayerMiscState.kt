package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.FindPathParams
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.Ammo_t
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.house.AccessHouseLevel
import org.opentibia.canary.map.house.House
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.spells.SoundEffect
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The remaining Player misc accessors
 * (src/creatures/players/player.cpp:1379-1381, 1981-1996, 6056-6064,
 * 6175-6178, 7331-7339, 8335-8358, 10581-10585, 11819-11823, 12100-12189,
 * 12897-12921, 13020-13028).
 *
 * The host already owns `Player`'s guild/party/flags/inventory, so the
 * transcriptions are extension functions that keep the exact `Player::` names
 * of their C++ symbols. The map tile lookup, the guild online list, the house
 * access level, the `ProtocolGame::send*`/update callbacks and the C++
 * out-parameter base of `Creature::getPathSearchParams` are call-site seams per
 * AGENTS.md 1.1.
 */

/** `SPEECHBUBBLE_HIRELING` (`src/creatures/creatures_definitions.hpp:341`). */
private const val SPEECHBUBBLE_HIRELING = 7

/** `CONST_ANI_BURSTARROW` (`src/utils/utils_definitions.hpp:283`). */
private const val CONST_ANI_BURSTARROW = 7

/** `CONST_ANI_DIAMONDARROW` (`src/utils/utils_definitions.hpp:332`). */
private const val CONST_ANI_DIAMONDARROW = 57

/**
 * `Player::canOpenCorpse(ownerId)` (player.cpp:1379-1381): the owner or a
 * member of the joined party (the `Party::canOpenCorpse` owner lookup is a
 * seam; party.hpp).
 */
fun Player.canOpenCorpse(
    /** `ownerId` (player.cpp:1379). */
    ownerId: Long,
    /** `Party::canOpenCorpse(ownerId)` (party.hpp). */
    partyCanOpenCorpse: (Long) -> Boolean = { false },
): Boolean = id == ownerId || (getParty() != null && partyCanOpenCorpse(ownerId))

/**
 * `Player::isNearDepotBox()` (player.cpp:1981-1996): a depot-flagged tile in
 * the 3x3 block around the player.
 */
fun Player.isNearDepotBox(
    /** `g_game().map.getTile(x, y, z)` (player.cpp:1985). */
    getTile: (Position) -> Tile? = { null },
): Boolean {
    for (cx in -1..1) {
        for (cy in -1..1) {
            val posTile = getTile(Position(position.x + cx, position.y + cy, position.z)) ?: continue
            if (posTile.hasFlag(TileFlags.TILESTATE_DEPOT.value)) {
                return true
            }
        }
    }
    return false
}

/**
 * `Player::hasPermittedConditionInPZ()` (player.cpp:12897-12921): the fixed
 * set of damage-over-time conditions allowed to persist in a protection zone.
 */
fun Player.hasPermittedConditionInPZ(): Boolean =
    ALLOWED_PZ_CONDITIONS.any { getCondition(it) != null }

/** The `allowedConditions` set of player.cpp:12898-12906. */
private val ALLOWED_PZ_CONDITIONS = listOf(
    ConditionType.CONDITION_ENERGY,
    ConditionType.CONDITION_FIRE,
    ConditionType.CONDITION_POISON,
    ConditionType.CONDITION_BLEEDING,
    ConditionType.CONDITION_AGONY,
    ConditionType.CONDITION_CURSED,
    ConditionType.CONDITION_DAZZLED,
)

/**
 * `Player::canSpeakWithHireling(speechbubble)` (player.cpp:13020-13028): only
 * the hireling bubble is gated, and only on house access.
 */
fun Player.canSpeakWithHireling(
    /** `speechbubble` (player.cpp:13020). */
    speechbubble: Int,
    /** `Creature::getTile()` (player.cpp:13021). */
    getTile: () -> Tile? = { null },
    /** `House::getHouseAccessLevel(player)` (player.cpp:13022). */
    houseAccessLevel: (House) -> AccessHouseLevel = { AccessHouseLevel.HOUSE_NOT_INVITED },
): Boolean {
    val house = getTile()?.getHouse()
    if (speechbubble == SPEECHBUBBLE_HIRELING &&
        (house == null || houseAccessLevel(house) == AccessHouseLevel.HOUSE_NOT_INVITED)
    ) {
        return false
    }

    return true
}

/**
 * `Player::getHelpers()` (player.cpp:8335-8358): the distinct guild/party
 * helpers, the guild online count alone, or the party size plus its leader.
 * The C++ `vector_set` deduplicates by pointer identity, and its
 * `emplace(m_party->getLeader())` counts a null leader as one entry; both are
 * transcribed.
 */
fun Player.getHelpers(
    /** `Guild::getMembersOnline()` (player.cpp:8337). */
    guildMembersOnline: (Guild) -> Collection<Player> = { emptyList() },
    /** `Guild::getMemberCountOnline()` (player.cpp:8349). */
    guildMemberCountOnline: (Guild) -> Int = { 0 },
): Int {
    val guild = getGuild()
    val party = getParty()

    if (guild != null && party != null) {
        val helperSet: MutableSet<Player?> = Collections.newSetFromMap(IdentityHashMap())
        helperSet.addAll(guildMembersOnline(guild))
        helperSet.addAll(party.getMembers())
        helperSet.addAll(party.getInvitees())
        helperSet.add(party.getLeader())
        return helperSet.size
    }

    if (guild != null) {
        return guildMemberCountOnline(guild)
    }

    if (party != null) {
        return party.getMemberCount() + party.getInvitationCount() + 1
    }

    return 0
}

/**
 * `Player::getPathSearchParams(creature, fpp)` (player.cpp:6175-6178): the
 * `Creature::` base fills the struct (an out-parameter base seam) and the
 * player forces a full path search.
 */
fun Player.getPathSearchParams(
    /** `fpp` (player.cpp:6175). */
    fpp: FindPathParams,
    /** `Creature::getPathSearchParams(creature, fpp)` (creature.hpp). */
    base: () -> Unit = {},
) {
    base()
    fpp.fullPathSearch = true
}

/**
 * `Player::updateBaseSpeed()` (player.cpp:7331-7339): `vocation->getBaseSpeed()
 * + level - 1` capped at the max speed, or the staff cap when
 * `PlayerFlags_t::SetMaxSpeed` is set.
 */
fun Player.updateBaseSpeed(
    /** `Vocation::getBaseSpeed()` (player.cpp:7335). */
    vocationBaseSpeed: () -> Int = { 0 },
) {
    val maxSpeed =
        if (hasFlag(PlayerFlags_t.SetMaxSpeed)) Player.PLAYER_MAX_STAFF_SPEED else Creature.PLAYER_MAX_SPEED

    if (!hasFlag(PlayerFlags_t.SetMaxSpeed)) {
        val computedSpeed = vocationBaseSpeed() + (level - 1)
        setBaseSpeed(minOf(computedSpeed, maxSpeed))
    } else {
        setBaseSpeed(maxSpeed)
    }
}

/**
 * `Player::updateState()` (player.cpp:6056-6064): weight, light, inventory ids
 * and stats, followed by the deferred sale-list refresh when a shop is open.
 */
fun Player.updateState(
    /** `Player::updateInventoryWeight()` (player.cpp:1072-1084). */
    updateInventoryWeight: () -> Unit = {},
    /** `Player::updateItemsLight()` (player.cpp:6284-6305). */
    updateItemsLight: () -> Unit = {},
    /** `Player::sendInventoryIds()` (player.cpp:9287-9291). */
    sendInventoryIds: () -> Unit = {},
    /** `Player::sendStats()` (player.cpp:2347-2352). */
    sendStats: () -> Unit = {},
    /** `Player::updateSaleShopList()` (player.cpp:6051-6054). */
    updateSaleShopList: () -> Unit = {},
    /** `Player::shopOwner != nullptr` (player.cpp:6061). */
    hasShopOwner: () -> Boolean = { false },
) {
    updateInventoryWeight()
    updateItemsLight()
    sendInventoryIds()
    sendStats()

    if (hasShopOwner()) {
        updateSaleShopList()
    }
}

/**
 * `Player::getAttackSoundEffect()` (player.cpp:12100-12142): the weapon family
 * picks the swing sound; a missing or non-weapon tool falls back to fists.
 */
fun Player.getAttackSoundEffect(): SoundEffect {
    val tool = getWeapon() ?: return SoundEffect.HUMAN_CLOSE_ATK_FIST

    val weaponType = weaponTypeProvider(tool.id)
    if (weaponType == WeaponType_t.WEAPON_NONE || weaponType == WeaponType_t.WEAPON_SHIELD) {
        return SoundEffect.HUMAN_CLOSE_ATK_FIST
    }

    return when (weaponType) {
        WeaponType_t.WEAPON_AXE -> SoundEffect.MELEE_ATK_AXE
        WeaponType_t.WEAPON_SWORD -> SoundEffect.MELEE_ATK_SWORD
        WeaponType_t.WEAPON_CLUB -> SoundEffect.MELEE_ATK_CLUB

        WeaponType_t.WEAPON_AMMO,
        WeaponType_t.WEAPON_DISTANCE,
        -> when (tool.getAmmoType()) {
            Ammo_t.AMMO_BOLT -> SoundEffect.DIST_ATK_CROSSBOW
            Ammo_t.AMMO_ARROW -> SoundEffect.DIST_ATK_BOW
            else -> SoundEffect.DIST_ATK_THROW
        }

        WeaponType_t.WEAPON_WAND -> SoundEffect.MAGICAL_RANGE_ATK
        else -> SoundEffect.SILENCE
    }
}

/**
 * `Player::getHitSoundEffect()` (player.cpp:12144-12189): the projectile
 * impact sound. The C++ `WEAPON_AMMO` arrow branch has no fallback return for
 * other shoot types, so it falls through to `SILENCE`; that behavior is
 * transcribed.
 */
/**
 * `Player::updatePartyTrackerAnalyzer(force)` (player.cpp:1743-1747): with a
 * client and a party, the party tracker frame is refreshed through the client.
 * The `Player::client` sink and the `m_party` read are seams per AGENTS.md 1.1.
 */
fun Player.updatePartyTrackerAnalyzer(
    /** The `force` argument (player.cpp:1743). */
    force: Boolean = false,
    /** `Player::client != nullptr` (player.cpp:1744). */
    hasClient: () -> Boolean = { false },
    /** `Player::m_party` (player.hpp). */
    party: () -> Party? = { getParty() },
    /** `client->updatePartyTrackerAnalyzer(m_party, force)` (player.cpp:1745). */
    clientUpdatePartyTrackerAnalyzer: (Party, Boolean) -> Unit = { _, _ -> },
) {
    if (hasClient()) {
        party()?.let { clientUpdatePartyTrackerAnalyzer(it, force) }
    }
}

/**
 * `Player::updateImpactTracker(type, amount)` (player.cpp:1790-1794): the
 * client impact-tracker frame. `Player::client` is the [hasClient] seam.
 */
fun Player.updateImpactTracker(
    /** The `type` argument (player.cpp:1790). */
    type: CombatType,
    /** The `amount` argument (player.cpp:1790). */
    amount: Int,
    /** `Player::client != nullptr` (player.cpp:1791). */
    hasClient: () -> Boolean = { false },
    /** `client->sendUpdateImpactTracker(type, amount)` (player.cpp:1792). */
    clientSendUpdateImpactTracker: (CombatType, Int) -> Unit = { _, _ -> },
) {
    if (hasClient()) {
        clientSendUpdateImpactTracker(type, amount)
    }
}

/**
 * `Player::updateInputAnalyzer(type, amount, target)` (player.cpp:1796-1800):
 * the client hunt-analyser input frame. `Player::client` is the [hasClient]
 * seam.
 */
fun Player.updateInputAnalyzer(
    /** The `type` argument (player.cpp:1796). */
    type: CombatType,
    /** The `amount` argument (player.cpp:1796). */
    amount: Int,
    /** The `target` argument (player.cpp:1796). */
    target: String,
    /** `Player::client != nullptr` (player.cpp:1797). */
    hasClient: () -> Boolean = { false },
    /** `client->sendUpdateInputAnalyzer(type, amount, target)` (player.cpp:1798). */
    clientSendUpdateInputAnalyzer: (CombatType, Int, String) -> Unit = { _, _, _ -> },
) {
    if (hasClient()) {
        clientSendUpdateInputAnalyzer(type, amount, target)
    }
}

/**
 * `Player::disconnect()` (player.cpp:10581-10585): forwards the disconnect to
 * the attached client. `Player::client` is the [hasClient] seam per AGENTS.md
 * 1.1.
 */
fun Player.disconnect(
    /** `Player::client != nullptr` (player.cpp:10582). */
    hasClient: () -> Boolean = { false },
    /** `client->disconnect()` (player.cpp:10583). */
    clientDisconnect: () -> Unit = {},
) {
    if (hasClient()) {
        clientDisconnect()
    }
}

/**
 * `Player::closeForgeWindow()` (player.cpp:11819-11823): closes the attached
 * client's forge window. `Player::client` is the [hasClient] seam per AGENTS.md
 * 1.1.
 */
fun Player.closeForgeWindow(
    /** `Player::client != nullptr` (player.cpp:11820). */
    hasClient: () -> Boolean = { false },
    /** `client->closeForgeWindow()` (player.cpp:11821). */
    clientCloseForgeWindow: () -> Unit = {},
) {
    if (hasClient()) {
        clientCloseForgeWindow()
    }
}

fun Player.getHitSoundEffect(): SoundEffect {
    val tool = getWeapon() ?: return SoundEffect.SILENCE

    return when (weaponTypeProvider(tool.id)) {
        WeaponType_t.WEAPON_AMMO -> {
            val itemType = tool.type
            when (itemType?.ammoType ?: 0) {
                Ammo_t.AMMO_BOLT.value -> SoundEffect.DIST_ATK_CROSSBOW_SHOT
                Ammo_t.AMMO_ARROW.value -> when (itemType?.shootType ?: 0) {
                    CONST_ANI_BURSTARROW -> SoundEffect.BURST_ARROW_EFFECT
                    CONST_ANI_DIAMONDARROW -> SoundEffect.DIAMOND_ARROW_EFFECT
                    // The C++ arrow branch has no return for other shoot
                    // types and falls through to SILENCE (player.cpp:12163).
                    else -> SoundEffect.SILENCE
                }

                else -> SoundEffect.DIST_ATK_THROW_SHOT
            }
        }

        WeaponType_t.WEAPON_DISTANCE -> when (tool.getAmmoType()) {
            Ammo_t.AMMO_BOLT -> SoundEffect.DIST_ATK_CROSSBOW_SHOT
            Ammo_t.AMMO_ARROW -> SoundEffect.DIST_ATK_BOW_SHOT
            else -> SoundEffect.DIST_ATK_THROW_SHOT
        }

        WeaponType_t.WEAPON_WAND -> SoundEffect.DIST_ATK_WAND_SHOT
        else -> SoundEffect.SILENCE
    }
}
