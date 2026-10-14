package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.spells.WorldType

/**
 * `Player::ExivaRestrictions` (`src/creatures/players/player.hpp:182-193`): the
 * per-target restrictions the Find Person (exiva) spell honors on no-PvP
 * worlds. `playerWhitelist` and `guildWhitelist` are the C++
 * `std::vector<uint32_t>` guid/id lists.
 */
data class ExivaRestrictions(
    var allowAll: Boolean = false,
    var allowOwnGuild: Boolean = true,
    var allowOwnParty: Boolean = true,
    var allowVipList: Boolean = true,
    var allowPlayerWhitelist: Boolean = true,
    var allowGuildWhitelist: Boolean = true,
    val playerWhitelist: MutableList<Long> = mutableListOf(),
    val guildWhitelist: MutableList<Long> = mutableListOf(),
)

/**
 * `Player::canExiva` (`src/creatures/players/player.cpp:7000-7048`).
 *
 * Every restriction is evaluated against the *target's* stored rules. The
 * `g_game().getWorldType()` / `g_game().getPlayerByName()` globals and the
 * target's `Player::vip()` component are injected seams per AGENTS.md 1.1.
 */
fun Player.canExiva(
    /** `spellParam` (player.cpp:7000). */
    spellParam: String,
    /** `g_game().getWorldType()` (game.cpp:926-928). */
    worldType: () -> WorldType = { WorldType.PVP },
    /** `g_game().getPlayerByName(spellParam)` (player.cpp:7005). */
    targetPlayerByName: (String) -> Player? = { null },
    /** `targetPlayer->vip().exists(getGUID())` (player.cpp:7032). */
    targetVipExists: (target: Player, guid: Long) -> Boolean = { _, _ -> false },
): Boolean {
    if (worldType() != WorldType.NO_PVP) {
        return true
    }

    val targetPlayer = targetPlayerByName(spellParam) ?: return false
    val targetRestrictions = targetPlayer.getExivaRestrictions()

    if (targetRestrictions.allowAll) {
        return true
    }

    if (targetRestrictions.allowOwnGuild) {
        val sourceGuild = getGuild()
        val targetGuild = targetPlayer.getGuild()
        if (sourceGuild != null && targetGuild != null && sourceGuild.id == targetGuild.id) {
            return true
        }
    }

    if (targetRestrictions.allowOwnParty) {
        val sourceParty = getParty()
        val targetParty = targetPlayer.getParty()
        if (sourceParty != null && targetParty != null && sourceParty === targetParty) {
            return true
        }
    }

    if (targetRestrictions.allowVipList && targetVipExists(targetPlayer, getGUID())) {
        return true
    }

    if (targetRestrictions.allowGuildWhitelist) {
        val playerGuild = getGuild()
        if (playerGuild != null && playerGuild.id in targetRestrictions.guildWhitelist) {
            return true
        }
    }

    if (targetRestrictions.allowPlayerWhitelist && getGUID() in targetRestrictions.playerWhitelist) {
        return true
    }

    return false
}
