package org.opentibia.canary.network.protocol.misc

import io.netty.buffer.ByteBuf
import org.opentibia.canary.network.connection.SessionEndInformations
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.IconBakragore

/**
 * Outbound session/state miscellaneous frames for the Canary
 * `ProtocolProfileId::Current` profile (`protocol_new` / 15.x:
 * `oldProtocol == false`), ported from
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendReLoginWindow`       (0x28, protocolgame.cpp:5797-5811)
 *  - `ProtocolGame::sendFYIBox`              (0x15, protocolgame.cpp:8469-8474)
 *  - `ProtocolGame::sendAllowBugReport`      (0x1A, protocolgame.cpp:8595-8603)
 *  - `ProtocolGame::sendUnjustifiedPoints`   (0xB7, protocolgame.cpp:6308-6323)
 *  - `ProtocolGame::sendOpenPvpSituations`   (0xB8, protocolgame.cpp:6325-6336)
 *  - `ProtocolGame::sendPendingStateEntered` (0x0A, protocolgame.cpp:8561-8569)
 *  - `ProtocolGame::sendEnterWorld`          (0x0F, protocolgame.cpp:8571-8575)
 *  - `ProtocolGame::sendSessionEndInformation` (0x18, protocolgame.cpp:2933-2941)
 *  - `ProtocolGame::sendDisableLoginMusic`   (0x85, protocolgame.cpp:11716-11726)
 *  - `ProtocolGame::sendPlayerVocation`      (0x8B, protocolgame.cpp:8456-8465)
 *  - `ProtocolGame::sendPremiumTrigger`      (0x9E, protocolgame.cpp:6000-6019)
 *  - `ProtocolGame::sendIconBakragore`       (0xA2, protocolgame.cpp:6296-6306)
 *  - `ProtocolGame::sendClientCheck`         (0x63, protocolgame.cpp:6586-6596)
 *  - `ProtocolGame::sendGameNews`            (0x98, protocolgame.cpp:6598-6608)
 *  - `ProtocolGame::sendPing`                (0x1D, protocolgame.cpp:8171-8177)
 *  - `ProtocolGame::sendPingBack`            (0x1E, protocolgame.cpp:8179-8183)
 *  - `ProtocolGame::sendRestingStatus`       (0xA9, protocolgame.cpp:8219-8258)
 *
 * The `!player`, `oldProtocol` and `version` gates stay with the caller. For
 * the current profile `version >= 1121`, so `sendReLoginWindow` always writes
 * all three payload bytes, and `sendPing` uses the `0x1D` wire byte (the
 * `0x1E` fallback only applies to `oldProtocol && version < 953`). The
 * `sendSessionEndInformation` `disconnect()` call is caller-side.
 * Every multi-byte field is Little-Endian.
 */
object MiscSessionWriter {

    const val PACKET_RE_LOGIN_WINDOW: Int = 0x28
    const val PACKET_FYI_BOX: Int = 0x15
    const val PACKET_ALLOW_BUG_REPORT: Int = 0x1A
    const val PACKET_UNJUSTIFIED_POINTS: Int = 0xB7
    const val PACKET_OPEN_PVP_SITUATIONS: Int = 0xB8
    const val PACKET_PENDING_STATE_ENTERED: Int = 0x0A
    const val PACKET_ENTER_WORLD: Int = 0x0F
    const val PACKET_SESSION_END_INFORMATION: Int = 0x18
    const val PACKET_DISABLE_LOGIN_MUSIC: Int = 0x85
    const val PACKET_PLAYER_VOCATION: Int = 0x8B
    const val PACKET_PREMIUM_TRIGGER: Int = 0x9E
    const val PACKET_PLAYER_ICONS: Int = 0xA2
    const val PACKET_CLIENT_CHECK: Int = 0x63
    const val PACKET_GAME_NEWS: Int = 0x98
    const val PACKET_PING: Int = 0x1D
    const val PACKET_PING_BACK: Int = 0x1E
    const val PACKET_RESTING_STATUS: Int = 0xA9

    /** `sendReLoginWindow` `version >= 1055` flag byte (protocolgame.cpp:5801). */
    private const val RE_LOGIN_WINDOW_UNKNOWN: Int = 0x00

    /** `sendReLoginWindow` `version >= 1121` death-redemption byte (protocolgame.cpp:5807). */
    private const val RE_LOGIN_WINDOW_DEATH_REDEMPTION: Int = 0x00

    /** `sendAllowBugReport`'s "bug report enabled" byte (protocolgame.cpp:8601). */
    private const val BUG_REPORT_ENABLED: Int = 0x00

    /** `sendPremiumTrigger` sub-feature count (protocolgame.cpp:6009). */
    private const val PREMIUM_TRIGGER_COUNT: Int = 16

    /** `sendPremiumTrigger` individual trigger byte (protocolgame.cpp:6012). */
    private const val PREMIUM_TRIGGER_SET: Int = 0x01

    /** `sendClientCheck` payload size (protocolgame.cpp:6592). */
    private const val CLIENT_CHECK_PAYLOAD_SIZE: Int = 1

    /** `sendClientCheck` raw probe byte (protocolgame.cpp:6593). */
    private const val CLIENT_CHECK_PROBE: Int = 0x01

    /** `sendGameNews` category/group id (protocolgame.cpp:6604). */
    private const val GAME_NEWS_CATEGORY: Int = 1

    /** `sendGameNews` highlight flag (protocolgame.cpp:6605). */
    private const val GAME_NEWS_HIGHLIGHT: Int = 0x01

    /** `sendDisableLoginMusic` sub-opcode for login music (protocolgame.cpp:11723). */
    private const val LOGIN_MUSIC_TYPE: Int = 0x01

    /** `sendDisableLoginMusic` music id `0x00` (protocolgame.cpp:11724). */
    private const val LOGIN_MUSIC_ID: Int = 0x00

    /** `sendPlayerVocation` fixed vocation byte (protocolgame.cpp:8462). */
    private const val PLAYER_VOCATION_FIXED: Int = 13

    /** `sendRestingStatus` `DAILY_REWARD_*` streak thresholds (creatures_definitions.hpp:578-587). */
    private const val DAILY_REWARD_FIRST: Int = 2
    private const val DAILY_REWARD_MP_REGENERATION: Int = 3
    private const val DAILY_REWARD_STAMINA_REGENERATION: Int = 4
    private const val DAILY_REWARD_DOUBLE_HP_REGENERATION: Int = 5
    private const val DAILY_REWARD_DOUBLE_MP_REGENERATION: Int = 6
    private const val DAILY_REWARD_SOUL_REGENERATION: Int = 7

    /** `sendRestingStatus` streak `>= 2` active-bonus flag (protocolgame.cpp:8231). */
    private const val RESTING_STATUS_ACTIVE: Int = 0x01

    /** `sendReLoginWindow` (protocolgame.cpp:5797-5811). */
    fun writeReLoginWindow(out: ByteBuf, unfairFightReduction: Int) {
        out.writeByte(PACKET_RE_LOGIN_WINDOW)
        out.writeByte(RE_LOGIN_WINDOW_UNKNOWN)
        out.writeByte(unfairFightReduction)
        out.writeByte(RE_LOGIN_WINDOW_DEATH_REDEMPTION)
    }

    /** `sendFYIBox` (protocolgame.cpp:8469-8474). */
    fun writeFYIBox(out: ByteBuf, message: String) {
        out.writeByte(PACKET_FYI_BOX)
        out.writeTibiaString(message)
    }

    /** `sendAllowBugReport` (protocolgame.cpp:8595-8603). */
    fun writeAllowBugReport(out: ByteBuf) {
        out.writeByte(PACKET_ALLOW_BUG_REPORT)
        out.writeByte(BUG_REPORT_ENABLED)
    }

    /** `sendUnjustifiedPoints` (protocolgame.cpp:6308-6323). */
    fun writeUnjustifiedPoints(
        out: ByteBuf,
        dayProgress: Int,
        dayLeft: Int,
        weekProgress: Int,
        weekLeft: Int,
        monthProgress: Int,
        monthLeft: Int,
        skullDuration: Int,
    ) {
        out.writeByte(PACKET_UNJUSTIFIED_POINTS)
        out.writeByte(dayProgress)
        out.writeByte(dayLeft)
        out.writeByte(weekProgress)
        out.writeByte(weekLeft)
        out.writeByte(monthProgress)
        out.writeByte(monthLeft)
        out.writeByte(skullDuration)
    }

    /** `sendOpenPvpSituations` (protocolgame.cpp:6325-6336). */
    fun writeOpenPvpSituations(out: ByteBuf, openPvpSituations: Int) {
        out.writeByte(PACKET_OPEN_PVP_SITUATIONS)
        out.writeByte(openPvpSituations)
    }

    /** `sendPendingStateEntered` (protocolgame.cpp:8561-8569). */
    fun writePendingStateEntered(out: ByteBuf) {
        out.writeByte(PACKET_PENDING_STATE_ENTERED)
    }

    /** `sendEnterWorld` (protocolgame.cpp:8571-8575). */
    fun writeEnterWorld(out: ByteBuf) {
        out.writeByte(PACKET_ENTER_WORLD)
    }

    /**
     * `sendSessionEndInformation` (protocolgame.cpp:2933-2941). [information] is
     * sent as its raw enum byte; `disconnect()` stays with the caller.
     */
    fun writeSessionEndInformation(out: ByteBuf, information: SessionEndInformations) {
        out.writeByte(PACKET_SESSION_END_INFORMATION)
        out.writeByte(information.ordinal)
    }

    /**
     * `sendDisableLoginMusic` (protocolgame.cpp:11716-11726). The frame shares
     * opcode `0x85` with `sendMusicSoundEffect`.
     */
    fun writeDisableLoginMusic(out: ByteBuf) {
        out.writeByte(PACKET_DISABLE_LOGIN_MUSIC)
        out.writeByte(LOGIN_MUSIC_TYPE)
        out.writeShortLE(LOGIN_MUSIC_ID)
    }

    /** `sendPlayerVocation` (protocolgame.cpp:8456-8465). */
    fun writePlayerVocation(out: ByteBuf, targetId: Long, vocationClientId: Int) {
        out.writeByte(PACKET_PLAYER_VOCATION)
        out.writeIntLE(targetId.toInt())
        out.writeByte(PLAYER_VOCATION_FIXED)
        out.writeByte(vocationClientId)
    }

    /**
     * `sendPremiumTrigger` (protocolgame.cpp:6000-6019). The `version < 1100`,
     * `FREE_PREMIUM` and `VIP_SYSTEM_ENABLED` gates stay with the caller.
     */
    fun writePremiumTrigger(out: ByteBuf) {
        out.writeByte(PACKET_PREMIUM_TRIGGER)
        out.writeByte(PREMIUM_TRIGGER_COUNT)
        repeat(PREMIUM_TRIGGER_COUNT) {
            out.writeByte(PREMIUM_TRIGGER_SET)
        }
    }

    /**
     * `sendIconBakragore` (protocolgame.cpp:6296-6306): an empty 64-bit icon
     * bitset followed by the Bakragore icon byte. The `oldProtocol` /
     * `shouldSuppressPreLoginPacket` gates stay with the caller.
     */
    fun writeIconBakragore(out: ByteBuf, icon: IconBakragore) {
        out.writeByte(PACKET_PLAYER_ICONS)
        out.writeLongLE(0L)
        out.writeByte(icon.value)
    }

    /** `sendClientCheck` (protocolgame.cpp:6586-6596). */
    fun writeClientCheck(out: ByteBuf) {
        out.writeByte(PACKET_CLIENT_CHECK)
        out.writeIntLE(CLIENT_CHECK_PAYLOAD_SIZE)
        out.writeByte(CLIENT_CHECK_PROBE)
    }

    /** `sendGameNews` (protocolgame.cpp:6598-6608). */
    fun writeGameNews(out: ByteBuf) {
        out.writeByte(PACKET_GAME_NEWS)
        out.writeIntLE(GAME_NEWS_CATEGORY)
        out.writeByte(GAME_NEWS_HIGHLIGHT)
    }

    /** `sendPing` (protocolgame.cpp:8171-8177), current-profile wire byte `0x1D`. */
    fun writePing(out: ByteBuf) {
        out.writeByte(PACKET_PING)
    }

    /** `sendPingBack` (protocolgame.cpp:8179-8183). */
    fun writePingBack(out: ByteBuf) {
        out.writeByte(PACKET_PING_BACK)
    }

    /**
     * `sendRestingStatus` (protocolgame.cpp:8219-8258). The caller resolves the
     * `player->kv()->scoped("daily-reward")->get("streak")` value into
     * [dailyStreak]; the bonus text mirrors protocolgame.cpp:8235-8256.
     */
    fun writeRestingStatus(out: ByteBuf, protection: Int, dailyStreak: Int) {
        out.writeByte(PACKET_RESTING_STATUS)
        out.writeByte(protection)
        out.writeByte(if (dailyStreak < DAILY_REWARD_FIRST) 0 else RESTING_STATUS_ACTIVE)

        if (dailyStreak < DAILY_REWARD_FIRST) {
            out.writeTibiaString("Resting Area (no active bonus)")
            return
        }

        val message = buildString {
            append("Active Resting Area Bonuses: ")
            if (dailyStreak < DAILY_REWARD_DOUBLE_HP_REGENERATION) {
                append("\nHit Points Regeneration")
            } else {
                append("\nDouble Hit Points Regeneration")
            }
            if (dailyStreak >= DAILY_REWARD_MP_REGENERATION) {
                if (dailyStreak < DAILY_REWARD_DOUBLE_MP_REGENERATION) {
                    append(",\nMana Points Regeneration")
                } else {
                    append(",\nDouble Mana Points Regeneration")
                }
            }
            if (dailyStreak >= DAILY_REWARD_STAMINA_REGENERATION) {
                append(",\nStamina Points Regeneration")
            }
            if (dailyStreak >= DAILY_REWARD_SOUL_REGENERATION) {
                append(",\nSoul Points Regeneration")
            }
            append(".")
        }
        out.writeTibiaString(message)
    }
}
