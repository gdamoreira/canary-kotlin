package org.opentibia.canary.creatures.players.pvp

import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.spells.WorldType
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * `Kill` (`src/creatures/creatures_definitions.hpp:1615-1624`).
 */
data class Kill(
    val target: Long,
    val time: Long,
    var unavenged: Boolean,
)

/**
 * `Player::SkullTimeInfo` (`src/creatures/players/player.hpp:451-454`).
 */
data class SkullTimeInfo(
    val remainingSeconds: Long = 0,
    val remainingDays: Int = 0,
)

/**
 * Injected `g_configManager().getNumber(...)` values used by the PvP component.
 *
 * Defaults are the exact `loadIntConfig` defaults (`src/config/configmanager.cpp`):
 * [orangeSkullDuration]:327, [redSkullDuration]:347, [blackSkullDuration]:229,
 * [dayKillsToRed]:244, [weekKillsToRed]:375, [monthKillsToRed]:326,
 * [pzLocked]:340, [whiteSkullTime]:386.
 */
data class PvpConfig(
    val orangeSkullDuration: Int = 7,
    val redSkullDuration: Int = 30,
    val blackSkullDuration: Int = 45,
    val dayKillsToRed: Int = 3,
    val weekKillsToRed: Int = 5,
    val monthKillsToRed: Int = 10,
    val pzLocked: Int = 60_000,
    val whiteSkullTime: Int = 15 * 60 * 1000,
)

/**
 * `ProtocolGame` PvP senders `Player` forwards to
 * (`src/server/network/protocol/protocolgame.hpp:380-381,420,467`). A `null`
 * client models `Player::client == nullptr`.
 */
interface PvpClient {
    /** `ProtocolGame::sendUnjustifiedPoints` (protocolgame.hpp:380). */
    fun sendUnjustifiedPoints(
        dayProgress: Int,
        dayLeft: Int,
        weekProgress: Int,
        weekLeft: Int,
        monthProgress: Int,
        monthLeft: Int,
        skullDuration: Int,
    )

    /** `ProtocolGame::sendOpenPvpSituations` (protocolgame.hpp:381). */
    fun sendOpenPvpSituations(openPvpSituations: Int)

    /**
     * `ProtocolGame::sendCreatureSkull` (protocolgame.hpp:420); C++ resolves
     * `player->getSkullClient(creature)` inside the sender
     * (protocolgame.cpp:4693) and gates the packet on `WORLD_TYPE_PVP`
     * (protocolgame.cpp:4688); the port resolves the skull in
     * [PlayerPvp.sendCreatureSkull] and leaves the world gate to the
     * implementation, like `CreatureWindowWriter.writeCreatureSkull`.
     */
    fun sendCreatureSkull(creature: Creature, skull: Skulls_t)

    /** `ProtocolGame::sendCreatureMark` (protocolgame.hpp:467). */
    fun sendCreatureMark(creature: Creature, mark: CreatureMark_t)
}

/**
 * Per-player PvP component.
 *
 * Mirror of Canary `PlayerPvp`
 * (`src/creatures/players/components/pvp/player_pvp.hpp:14-27`,
 * player_pvp.cpp:14-38) for the mode state, plus the player-side PvP methods
 * that Canary keeps on `Player`: the attacked/attacker situation ledgers
 * (player.cpp:7207-7261), the unjustified-kill ledger (player.cpp:7263-7301),
 * the skull tick transitions (player.cpp:7153-7205, 7318-7329) and the PZ lock
 * window (player.cpp:4442-4483). The PZ lock fields themselves
 * (`pzLocked`/`pzLockOnlyUntil`/`pzLockEventId`) live on [Player] exactly as in
 * Canary (player.hpp:1869-1871).
 * The class is intentionally *not* `Player.kt` (task constraint); global and
 * player dependencies are constructor-injected seams (AGENTS.md 1.1):
 * [playerByGuid] replaces `g_game().getPlayerByGUID`, [configuredWorldType]
 * replaces `g_configManager().getString(WORLD_TYPE)`, [worldType] replaces
 * `g_game().getWorldType()`, [config] replaces `g_configManager().getNumber`,
 * [client] replaces `Player::client`, [timeNow]/[otsysTime] replace
 * `time(nullptr)`/`OTSYS_TIME()` and the dispatcher/text/icon sinks replace
 * `g_dispatcher()` and the protocol sends.
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe, so every behavior is auditable against the C++ line references.
 */
class PlayerPvp(
    val player: Player,
    /** `g_game().getPlayerByGUID` (`player.cpp:7251`, 7138-7142). */
    internal val playerByGuid: (Long) -> PlayerPvp? = { null },
    /** `g_configManager().getString(WORLD_TYPE)` (configmanager.cpp:413). */
    private val configuredWorldType: () -> String = { ExpertPvp.RETRO_PVP_WORLD_TYPE },
    /** `g_game().getWorldType()` (game.cpp:926-928). */
    private val worldType: () -> WorldType = { WorldType.PVP },
    /** `g_configManager().getNumber(...)` values. */
    private val config: PvpConfig = PvpConfig(),
    /** `Player::client`. */
    private val client: PvpClient? = null,
    /** `time(nullptr)` / `getTimeNow()` (tools.cpp:533). */
    private val timeNow: () -> Long = { getTimeNow() },
    /** `OTSYS_TIME()`. */
    private val otsysTime: () -> Long = { System.currentTimeMillis() },
    /** `Player::sendTextMessage(MESSAGE_EVENT_ADVANCE, ...)` (`player.cpp:7268`). */
    private val sendTextMessage: (String) -> Unit = {},
    /** `Player::sendIcons()` (`player.cpp:4456`, 4478). */
    private val sendIcons: () -> Unit = {},
    /** `g_dispatcher().scheduleEvent(task)` (`player.cpp:4481`). */
    private val scheduleEvent: (duration: Long, task: () -> Unit) -> Long = { _, _ -> 0L },
    /** `g_dispatcher().stopEvent(eventId)` (`player.cpp:4452`). */
    private val stopEvent: (eventId: Long) -> Unit = {},
    /** `Spectators().find<Player>(center->getPosition(), true)` (expert_pvp.cpp:192-203). */
    private val visiblePlayers: (PlayerPvp) -> List<PlayerPvp> = { emptyList() },
    /** `g_game().getPlayers()` (expert_pvp.cpp:902). */
    private val allPlayers: () -> Collection<PlayerPvp> = { emptyList() },
) {

    /** `PlayerPvp::m_mode` (player_pvp.hpp:26), default `PVP_MODE_DOVE`. */
    private var mode: PvpMode_t = PvpMode_t.PVP_MODE_DOVE

    /** `Player::attackedSet` (player.hpp:1668). */
    private val attackedSet = linkedSetOf<Long>()

    /** `Player::attackerSet`; C++ mutates it from the attacker's `addAttacked`. */
    private val attackerSet = linkedSetOf<Long>()

    /** `Player::unjustifiedKills` (player.hpp:1754). */
    private val unjustifiedKills = mutableListOf<Kill>()

    /** `Player::skullTicks` (player.hpp:1737). */
    private var skullTicks: Long = 0

    /** `Player::m_lastKillTimeCache`/`m_lastKillTimeCached` (player.hpp:1738-1739). */
    private var lastKillTimeCache: Long = 0
    private var lastKillTimeCached: Boolean = false

    // `Player::pzLockOnlyUntil`/`pzLocked`/`pzLockEventId` (player.hpp:1869-1871)
    // live on [Player]; Canary keeps them on the player, not on the component.

    /** `Player::guildWarVector` (player.hpp); populated by the guild-war subsystem. */
    internal val guildWarVector = mutableListOf<Long>()

    // --- PlayerPvp component (player_pvp.cpp) ---------------------------------------

    /** `PlayerPvp::isExpertPvpEnabled` (player_pvp.cpp:14-16). */
    fun isExpertPvpEnabled(): Boolean = ExpertPvp.isEnabled(configuredWorldType())

    /** `PlayerPvp::getMode` (player_pvp.cpp:18-20). */
    fun getMode(): PvpMode_t = mode

    /** `PlayerPvp::defaultModeForClient` (player_pvp.cpp:22-24). */
    fun defaultModeForClient(): ExpertPvpModeResult = ExpertPvp.defaultModeForClient()

    /** `PlayerPvp::modeFromClientByte` (player_pvp.cpp:26-28). */
    fun modeFromClientByte(rawMode: Int): ExpertPvpModeResult = ExpertPvp.modeFromClientByte(rawMode)

    /** `PlayerPvp::normalizeMode` (player_pvp.cpp:30-32). */
    fun normalizeMode(
        requestedMode: PvpMode_t,
        source: ExpertPvpModeSource = ExpertPvpModeSource.StoredPlayerState,
    ): ExpertPvpModeResult = ExpertPvp.normalizeMode(requestedMode, source)

    /** `PlayerPvp::setMode` (player_pvp.cpp:34-38). */
    fun setMode(
        requestedMode: PvpMode_t,
        source: ExpertPvpModeSource = ExpertPvpModeSource.StoredPlayerState,
    ): ExpertPvpModeResult {
        val result = normalizeMode(requestedMode, source)
        mode = result.mode
        return result
    }

    // --- situations (Player::hasAttacked/addAttacked/removeAttacked/clearAttacked) ---

    /** `Player::hasAttacked` (player.cpp:7207-7214). */
    fun hasAttacked(attacked: PlayerPvp): Boolean {
        if (player.hasFlag(PlayerFlags_t.NotGainInFight)) {
            return false
        }

        return attacked.player.id in attackedSet
    }

    /** `Player::getAttackedPlayerGuids` (player.cpp:7216-7218). */
    fun getAttackedPlayerGuids(): Set<Long> = attackedSet

    /** `Player::getAttackerPlayerGuids` (player.cpp:7220-7222). */
    fun getAttackerPlayerGuids(): Set<Long> = attackerSet

    /** `Player::addAttacked` (player.cpp:7223-7233). */
    fun addAttacked(attacked: PlayerPvp) {
        if (player.hasFlag(PlayerFlags_t.NotGainInFight) || attacked.player === player) {
            return
        }

        if (attackedSet.add(attacked.player.id)) {
            attacked.attackerSet.add(player.id)
        }
    }

    /** `Player::removeAttacked` (player.cpp:7235-7245). */
    fun removeAttacked(attacked: PlayerPvp) {
        if (attacked.player === player) {
            return
        }

        if (!attackedSet.remove(attacked.player.id)) {
            return
        }

        attacked.attackerSet.remove(player.id)
        if (isExpertPvpEnabled()) {
            ExpertPvp.refreshVisibleSituationMarks(this, attacked, enabled = true, visiblePlayers = visiblePlayers)
        }
    }

    /** `Player::clearAttacked` (player.cpp:7247-7261). */
    fun clearAttacked() {
        val shouldRefreshExpertMarks = isExpertPvpEnabled() && attackedSet.isNotEmpty()
        for (attackedGuid in attackedSet) {
            playerByGuid(attackedGuid)?.attackerSet?.remove(player.id)
        }
        attackedSet.clear()
        if (shouldRefreshExpertMarks) {
            ExpertPvp.refreshAllVisibleSituationMarks(
                enabled = true,
                allPlayers = allPlayers,
                visiblePlayers = visiblePlayers,
            )
        }
    }

    /** `Player::sendOpenPvpSituations` (player.cpp:7912-7916). */
    fun sendOpenPvpSituations() {
        val pvpClient = client ?: return
        pvpClient.sendOpenPvpSituations(min(attackedSet.size, 255))
    }

    /** `Player::sendCreatureSkull` (player.cpp:7311-7315). */
    fun sendCreatureSkull(creature: Creature) {
        val pvpClient = client ?: return
        pvpClient.sendCreatureSkull(creature, getSkullClient(creature))
    }

    /** `Player::sendCreatureMark` (player.hpp:978). */
    fun sendCreatureMark(creature: Creature, mark: CreatureMark_t) {
        client?.sendCreatureMark(creature, mark)
    }

    // --- unjustified points ---------------------------------------------------------

    /** `Player::getUnjustifiedKills`; read view of `unjustifiedKills`. */
    fun getUnjustifiedKills(): List<Kill> = unjustifiedKills

    /**
     * `Player::unjustifiedKills` direct assignment
     * (`player.hpp:1754`; `PlayerFunctions::luaPlayerSetKills`,
     * player_functions.cpp:1219). The C++ member is public and the Lua binding
     * replaces the whole vector; the component keeps the list private, so this
     * seam transcribes the assignment.
     */
    fun setUnjustifiedKills(kills: List<Kill>) {
        unjustifiedKills.clear()
        unjustifiedKills.addAll(kills)
    }

    /** `Player::hasKilled` (player.cpp:6981-6984). */
    fun hasKilled(player: PlayerPvp): Boolean = unjustifiedKills.any { kill ->
        kill.target == player.player.id &&
            (timeNow() - kill.time) < config.orangeSkullDuration.toLong() * 24 * 60 * 60 &&
            kill.unavenged
    }

    /** `Player::addUnjustifiedDead` (player.cpp:7263-7301). */
    fun addUnjustifiedDead(attacked: PlayerPvp) {
        if (player.hasFlag(PlayerFlags_t.NotGainInFight) ||
            attacked.player === player ||
            worldType() == WorldType.PVP_ENFORCED
        ) {
            return
        }

        sendTextMessage("Warning! The murder of ${attacked.player.name} was not justified.")

        val killTime = timeNow()
        unjustifiedKills.add(Kill(target = attacked.player.id, time = killTime, unavenged = true))
        updateLastKillTimeCache(killTime)

        var dayKills = 0
        var weekKills = 0
        var monthKills = 0
        for (kill in unjustifiedKills) {
            val diff = timeNow() - kill.time
            if (diff <= 4L * 60 * 60) {
                dayKills += 1
            }
            if (diff <= 7L * 24 * 60 * 60) {
                weekKills += 1
            }
            if (diff <= 30L * 24 * 60 * 60) {
                monthKills += 1
            }
        }

        if (player.getSkull() != Skulls_t.SKULL_BLACK) {
            if (dayKills >= 2 * config.dayKillsToRed ||
                weekKills >= 2 * config.weekKillsToRed ||
                monthKills >= 2 * config.monthKillsToRed
            ) {
                player.setSkull(Skulls_t.SKULL_BLACK)
                skullTicks = config.blackSkullDuration.toLong() * 24 * 60 * 60
            } else if (dayKills >= config.dayKillsToRed ||
                weekKills >= config.weekKillsToRed ||
                monthKills >= config.monthKillsToRed
            ) {
                player.setSkull(Skulls_t.SKULL_RED)
                skullTicks = config.redSkullDuration.toLong() * 24 * 60 * 60
            }
        }

        sendUnjustifiedPoints()
    }

    /** `Player::sendUnjustifiedPoints` (player.cpp:7873-7910). */
    fun sendUnjustifiedPoints() {
        val pvpClient = client ?: return

        var dayKills = 0.0
        var weekKills = 0.0
        var monthKills = 0.0
        for (kill in unjustifiedKills) {
            val diff = timeNow() - kill.time
            if (diff <= 24L * 60 * 60) {
                dayKills += 1.0
            }
            if (diff <= 7L * 24 * 60 * 60) {
                weekKills += 1.0
            }
            if (diff <= 30L * 24 * 60 * 60) {
                monthKills += 1.0
            }
        }

        val isRedOrBlack = player.getSkull() == Skulls_t.SKULL_RED || player.getSkull() == Skulls_t.SKULL_BLACK

        val dayMax = ((if (isRedOrBlack) 2 else 1) * config.dayKillsToRed).toDouble()
        val weekMax = ((if (isRedOrBlack) 2 else 1) * config.weekKillsToRed).toDouble()
        val monthMax = ((if (isRedOrBlack) 2 else 1) * config.monthKillsToRed).toDouble()

        val dayProgress = if (dayMax > 0) min((dayKills / dayMax * 100.0).roundToInt(), 100) else 0
        val weekProgress = if (weekMax > 0) min((weekKills / weekMax * 100.0).roundToInt(), 100) else 0
        val monthProgress = if (monthMax > 0) min((monthKills / monthMax * 100.0).roundToInt(), 100) else 0
        val dayLeft = min(if (dayMax > 0) max(dayMax - dayKills, 0.0) else 0.0, 255.0).toInt()
        val weekLeft = min(if (weekMax > 0) max(weekMax - weekKills, 0.0) else 0.0, 255.0).toInt()
        val monthLeft = min(if (monthMax > 0) max(monthMax - monthKills, 0.0) else 0.0, 255.0).toInt()

        val info = computeSkullTimeFromLastKill()
        pvpClient.sendUnjustifiedPoints(
            dayProgress,
            dayLeft,
            weekProgress,
            weekLeft,
            monthProgress,
            monthLeft,
            info.remainingDays,
        )
    }

    // --- skull ticks -----------------------------------------------------------------

    /** `Player::getSkullTicks` (player.cpp:7153-7155). */
    fun getSkullTicks(): Long = skullTicks

    /** `Player::setSkullTicks` (player.cpp:7157-7159). */
    fun setSkullTicks(ticks: Long) {
        skullTicks = ticks
    }

    /** `Player::refreshSkullTicksFromLastKill` (player.cpp:7161-7164). */
    fun refreshSkullTicksFromLastKill() {
        skullTicks = computeSkullTimeFromLastKill().remainingSeconds
    }

    /** `Player::updateLastKillTimeCache` (player.cpp:7166-7172). */
    fun updateLastKillTimeCache(killTime: Long) {
        if (killTime <= 0) {
            return
        }

        lastKillTimeCache = max(lastKillTimeCache, killTime)
        lastKillTimeCached = true
    }

    /** `Player::computeSkullTimeFromLastKill` (player.cpp:7174-7205). */
    fun computeSkullTimeFromLastKill(): SkullTimeInfo {
        var ticks = skullTicks

        if (ticks == 0L && (player.getSkull() == Skulls_t.SKULL_RED || player.getSkull() == Skulls_t.SKULL_BLACK)) {
            var lastKillTime = 0L
            if (lastKillTimeCached) {
                lastKillTime = lastKillTimeCache
            } else {
                for (kill in unjustifiedKills) {
                    lastKillTime = max(lastKillTime, kill.time)
                }

                lastKillTimeCache = lastKillTime
                lastKillTimeCached = true
            }

            if (lastKillTime > 0) {
                val now = timeNow()
                val durationDays = if (player.getSkull() == Skulls_t.SKULL_RED) {
                    config.redSkullDuration
                } else {
                    config.blackSkullDuration
                }
                val duration = durationDays.toLong() * 24 * 60 * 60
                ticks = max(0, duration - (now - lastKillTime))
            }
        }

        var remainingDays = 0
        if (ticks > 0) {
            remainingDays = min(ceil(ticks.toDouble() / (24 * 60 * 60)), 255.0).toInt()
        }

        return SkullTimeInfo(remainingSeconds = ticks, remainingDays = remainingDays)
    }

    /** `Player::checkSkullTicks` (player.cpp:7318-7329). */
    fun checkSkullTicks(ticks: Long) {
        val newTicks = skullTicks - ticks
        skullTicks = if (newTicks < 0) 0 else newTicks

        val skull = player.getSkull()
        if ((skull == Skulls_t.SKULL_RED || skull == Skulls_t.SKULL_BLACK) &&
            skullTicks < 1 &&
            !player.hasCondition(ConditionType.CONDITION_INFIGHT)
        ) {
            player.setSkull(Skulls_t.SKULL_NONE)
        }
    }

    /**
     * `Player::getSkullClient` (player.cpp:7127-7151).
     *
     * C++ resolves `player->hasKilled(getPlayer())` / `player->hasAttacked(getPlayer())`
     * on the other player's state; the port resolves that player's component
     * through [playerByGuid]. An unresolved opponent contributes no
     * kill/attack facts (the component is always attached in Canary).
     */
    fun getSkullClient(creature: Creature): Skulls_t {
        if (worldType() != WorldType.PVP) {
            return Skulls_t.SKULL_NONE
        }

        val creaturePlayer = creature as? Player
        if (creaturePlayer != null && creaturePlayer.getSkull() == Skulls_t.SKULL_NONE) {
            val otherPvp = playerByGuid(creaturePlayer.getGUID())
            if (creaturePlayer === player) {
                if (unjustifiedKills.any { kill ->
                        kill.unavenged &&
                            (timeNow() - kill.time) < config.orangeSkullDuration.toLong() * 24 * 60 * 60
                    }
                ) {
                    return Skulls_t.SKULL_ORANGE
                }
            }

            if (otherPvp != null && otherPvp.hasKilled(this)) {
                return Skulls_t.SKULL_ORANGE
            }

            if (otherPvp != null && otherPvp.hasAttacked(this)) {
                return Skulls_t.SKULL_YELLOW
            }

            val party = player.getParty()
            if (party != null && party === creaturePlayer.getParty()) {
                return Skulls_t.SKULL_GREEN
            }
        }

        return creature.getSkull()
    }

    // --- grouping/war facts (Player methods used by ExpertPvp) ----------------------

    /** `Player::isPartner` (player.cpp:7790-7794). */
    fun isPartner(other: PlayerPvp): Boolean {
        val party = player.getParty() ?: return false
        if (other.player === player) {
            return false
        }
        return party === other.player.getParty()
    }

    /** `Player::isGuildMate` (player.cpp:9357-9362). */
    fun isGuildMate(other: PlayerPvp): Boolean {
        val guild = player.getGuild() ?: return false
        return guild === other.player.getGuild()
    }

    /** `Player::isInWarList` (player.cpp:7416-7418). */
    fun isInWarList(guildId: Long): Boolean = guildId in guildWarVector

    /** `Player::isInWar` (player.cpp:7403-7413). */
    fun isInWar(other: PlayerPvp): Boolean {
        val guild = player.getGuild() ?: return false
        val otherGuild = other.player.getGuild() ?: return false
        return isInWarList(otherGuild.id) && other.isInWarList(guild.id)
    }

    // --- pz lock ---------------------------------------------------------------------

    /** `Player::isPzLocked`; read view of the player's `pzLocked`. */
    fun isPzLocked(): Boolean = player.pzLocked

    /** `Player::pzLockOnlyUntil`; exposed for inspecting systems. */
    internal fun getPzLockOnlyUntil(): Long = player.pzLockOnlyUntil

    /** `Player::addPzLockTicks` (player.cpp:4442-4483). */
    fun addPzLockTicks() {
        if (player.hasFlag(PlayerFlags_t.NotGainInFight)) {
            return
        }

        val duration = config.pzLocked.toLong()
        val expiresAt = otsysTime() + duration
        if (player.pzLockOnlyUntil >= expiresAt) {
            return
        }

        player.pzLockOnlyUntil = expiresAt
        player.pzLocked = true
        sendIcons()
        if (player.pzLockEventId != 0L) {
            stopEvent(player.pzLockEventId)
        }

        val task = {
            player.pzLockEventId = 0
            if (!player.hasCondition(ConditionType.CONDITION_INFIGHT) && player.pzLockOnlyUntil <= otsysTime()) {
                player.pzLockOnlyUntil = 0
                if (player.pzLocked) {
                    player.pzLocked = false
                    sendIcons()
                }
                if (player.getSkull() != Skulls_t.SKULL_RED && player.getSkull() != Skulls_t.SKULL_BLACK) {
                    player.setSkull(Skulls_t.SKULL_NONE)
                }
            }
        }
        player.pzLockEventId = scheduleEvent(duration, task)
    }
}
