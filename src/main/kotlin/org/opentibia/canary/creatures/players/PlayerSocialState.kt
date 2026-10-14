package org.opentibia.canary.creatures.players

import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountErrors
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.creatures.players.vip.VipStatus

/**
 * Self-contained mirror of the Player guild/faction state
 * (src/creatures/players/player.cpp:4511-4525, 6234-6240, 7395-7401,
 * 12617-12625) plus the social/premium/account accessors around it.
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps `guildNick` and `faction` directly on `Player` (player.hpp:1708, 1858);
 * the state is exposed as an injectable seam so the host player can delegate
 * without a global (AGENTS.md 1.1). `Player::addList`/`removeList`,
 * `Player::setAccount` and the premium helpers are extension functions whose
 * `g_game()`/`g_logger()` dependencies are call-site seams.
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

/**
 * `Player::setAccount(accountId)` (player.cpp:12617-12625): a bound account
 * short-circuits with a warning; otherwise a fresh [Account] is created,
 * assigned and loaded, and the load result reported. `g_logger().warn` is the
 * [logWarn] seam (AGENTS.md 1.1).
 */
fun Player.setAccount(
    /** The `accountId` argument (player.cpp:12617). */
    accountId: Long,
    /** `std::make_shared<Account>(accountId)` (player.cpp:12622). */
    accountFactory: (Long) -> Account = { Account(id = it) },
    /** `g_logger().warn("Account was already set!")` (player.cpp:12619). */
    logWarn: (String) -> Unit = {},
): Boolean {
    if (account != null) {
        logWarn("Account was already set!")
        return true
    }

    val loadedAccount = accountFactory(accountId)
    account = loadedAccount
    return AccountErrors.Ok == loadedAccount.load()
}

/**
 * `Player::removeList()` (player.cpp:4511-4517): drops the player from the
 * game registry and marks the player offline on every other player's VIP list.
 * The `g_game()` registry and `PlayerVIP::notifyStatusChange` are seams per
 * AGENTS.md 1.1.
 */
fun Player.removeList(
    /** `g_game().removePlayer(player)` (player.cpp:4512). */
    removePlayerFromGame: (Player) -> Unit = {},
    /** `g_game().getPlayers()` (player.cpp:4514). */
    gamePlayers: () -> Collection<Player> = { emptyList() },
    /** `player->vip().notifyStatusChange(this, VipStatus_t::Offline)` (player.cpp:4515). */
    notifyVipStatusChange: (Player, Player, VipStatus) -> Unit = { _, _, _ -> },
) {
    removePlayerFromGame(this)

    for (player in gamePlayers()) {
        notifyVipStatusChange(player, this, VipStatus.Offline)
    }
}

/**
 * `Player::addList()` (player.cpp:4519-4525): announces the player's current
 * VIP status to every logged-in player's VIP list, then adds the player to the
 * game registry.
 */
fun Player.addList(
    /** `g_game().getPlayers()` (player.cpp:4520). */
    gamePlayers: () -> Collection<Player> = { emptyList() },
    /** `vip().getStatus()` (player.cpp:4521); `PlayerVIP::status` defaults Online. */
    vipStatus: () -> VipStatus = { VipStatus.Online },
    /** `player->vip().notifyStatusChange(this, vip().getStatus())` (player.cpp:4521). */
    notifyVipStatusChange: (Player, Player, VipStatus) -> Unit = { _, _, _ -> },
    /** `g_game().addPlayer(player)` (player.cpp:4523). */
    addPlayerToGame: (Player) -> Unit = {},
) {
    for (player in gamePlayers()) {
        notifyVipStatusChange(player, this, vipStatus())
    }

    addPlayerToGame(this)
}
