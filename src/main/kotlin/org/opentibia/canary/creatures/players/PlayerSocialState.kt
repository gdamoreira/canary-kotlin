package org.opentibia.canary.creatures.players

import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.Faction

/**
 * Self-contained mirror of the Player guild/faction state
 * (src/creatures/players/player.cpp:6234-6240, 7395-7401).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps `guildNick` and `faction` directly on `Player` (player.hpp:1708, 1858);
 * the state is exposed as an injectable seam so the host player can delegate
 * without a global (AGENTS.md 1.1).
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe, so every behavior is auditable against the C++ line references.
 */
class PlayerSocialState(
    /** `Player::guildNick` (player.hpp:1708); empty by default. */
    guildNick: String = "",
    /** `Player::faction` (player.hpp:1858); `FACTION_PLAYER` by default. */
    faction: Faction = Faction.Player,
) {

    private var guildNickValue: String = guildNick
    private var factionValue: Faction = faction

    /** `Player::getGuildNick` (player.cpp:7395-7397). */
    fun getGuildNick(): String = guildNickValue

    /** `Player::setGuildNick` (player.cpp:7399-7401). */
    fun setGuildNick(nick: String) {
        guildNickValue = nick
    }

    /** `Player::getFaction` (player.cpp:6234-6236). */
    fun getFaction(): Faction = factionValue

    /** `Player::setFaction` (player.cpp:6238-6240). */
    fun setFaction(factionId: Faction) {
        factionValue = factionId
    }
}

/**
 * `Player::getPremiumDays` (player.cpp:7544-7546): the account's remaining
 * premium days, 0 without an account (the C++ dereferences `account`).
 */
fun Player.getPremiumDays(now: Long = getTimeNow()): Int = account?.getPremiumRemainingDays(now) ?: 0

/**
 * `Player::getAccountId` (player.cpp:12631-12633): the account id, 0 without an
 * account.
 */
fun Player.getAccountId(): Long = account?.id ?: 0

/**
 * `Player::getPremiumLastDay` (player.cpp:7548-7550): the account's premium
 * expiry timestamp, 0 without an account.
 */
fun Player.getPremiumLastDay(): Long = account?.getPremiumLastDay() ?: 0

/**
 * `Player::isVip` (player.cpp:7552-7554): `VIP_SYSTEM_ENABLED` and a running
 * premium window. The config flag (configmanager.cpp:168, default false) and
 * the clock are injected per AGENTS.md 1.1.
 */
fun Player.isVip(vipSystemEnabled: Boolean = false, now: Long = getTimeNow()): Boolean =
    vipSystemEnabled && (getPremiumDays(now) > 0 || getPremiumLastDay() > now)

/**
 * `Player::isPartner(player)` (player.cpp:7790-7795): null, self and partyless
 * comparisons are false; otherwise both players must share the same party.
 */
fun Player.isPartner(player: Player?): Boolean {
    if (player == null || getParty() == null || player === this) {
        return false
    }
    return getParty() === player.getParty()
}
