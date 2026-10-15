package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.network.message.readTibiaString

/**
 * Find-Person (exiva) restriction state sent by a client on a no-PvP world
 * (client opcode `0xCA`), mirroring `ProtocolGame::parseExivaRestrictions`
 * (src/server/network/protocol/protocolgame.cpp:12209-12250) and the
 * `addExivaEntries`/`removeExivaEntries` helpers (protocolgame.cpp:189-224).
 */
data class ExivaRestrictionFlags(
    val allowAll: Boolean = false,
    val allowOwnGuild: Boolean = true,
    val allowOwnParty: Boolean = true,
    val allowVipList: Boolean = true,
    val allowPlayerWhitelist: Boolean = true,
    val allowGuildWhitelist: Boolean = true,
)

/**
 * One decoded `0xCA` payload: the six flag bytes followed by the four
 * add/remove name lists (player names then guild names), each a `uint16_t`
 * count and that many Tibia strings.
 */
data class ExivaRestrictionsCommand(
    val flags: ExivaRestrictionFlags,
    val addedPlayerNames: List<String> = emptyList(),
    val removedPlayerNames: List<String> = emptyList(),
    val addedGuildNames: List<String> = emptyList(),
    val removedGuildNames: List<String> = emptyList(),
)

/**
 * The names `ProtocolGame::sendExivaRestrictions(false, ...)` echoes back after
 * an apply: only entries the lookup resolved and the whitelist accepted
 * (added) or contained (removed).
 */
data class ExivaRestrictionsEcho(
    val addedPlayerNames: List<String> = emptyList(),
    val removedPlayerNames: List<String> = emptyList(),
    val addedGuildNames: List<String> = emptyList(),
    val removedGuildNames: List<String> = emptyList(),
)

/**
 * Decoder and whitelist applier for the exiva-restrictions client packet.
 *
 * The dispatch gates stay with the caller, exactly like
 * `ProtocolGame::parsePacketFromDispatcher`'s
 * `!oldProtocol && g_game().getWorldType() == WORLD_TYPE_NO_PVP` check
 * (protocolgame.cpp:1992-1996) and the `WORLD_TYPE` string check inside
 * `parseExivaRestrictions` (protocolgame.cpp:12210-12212).
 */
object ExivaRestrictionsPacketHandler {

    const val OPCODE_EXIVA_RESTRICTIONS: Int = 0xCA

    /** Reads the six flags and the four name lists; `null` on a truncated payload. */
    fun parse(buffer: ByteBuf): ExivaRestrictionsCommand? {
        if (buffer.readableBytes() < 6) {
            return null
        }

        val flags = ExivaRestrictionFlags(
            allowAll = buffer.readUnsignedByte().toInt() != 0,
            allowOwnGuild = buffer.readUnsignedByte().toInt() != 0,
            allowOwnParty = buffer.readUnsignedByte().toInt() != 0,
            allowVipList = buffer.readUnsignedByte().toInt() != 0,
            allowPlayerWhitelist = buffer.readUnsignedByte().toInt() != 0,
            allowGuildWhitelist = buffer.readUnsignedByte().toInt() != 0,
        )

        val addedPlayerNames = readNames(buffer) ?: return null
        val removedPlayerNames = readNames(buffer) ?: return null
        val addedGuildNames = readNames(buffer) ?: return null
        val removedGuildNames = readNames(buffer) ?: return null

        return ExivaRestrictionsCommand(
            flags = flags,
            addedPlayerNames = addedPlayerNames,
            removedPlayerNames = removedPlayerNames,
            addedGuildNames = addedGuildNames,
            removedGuildNames = removedGuildNames,
        )
    }

    /**
     * Applies [command] to `player->getExivaRestrictions()` exactly like
     * `parseExivaRestrictions` (protocolgame.cpp:12213-12250): the flags are
     * assigned first, then each name list is resolved through the injected
     * lookups and added/removed under the `MAX_EXIVA_WHITELIST` cap. Returns the
     * accepted names for the `sendExivaRestrictions(false, ...)` echo.
     */
    fun apply(
        player: Player,
        command: ExivaRestrictionsCommand,
        maxWhitelist: Int,
        /** `IOLoginData::getGuidByName` (iologindata.cpp:310-320). */
        playerGuidByName: (String) -> Long,
        /** `IOGuild::getGuildIdByName` (ioguild.cpp:47-58). */
        guildIdByName: (String) -> Long,
    ): ExivaRestrictionsEcho {
        val restrictions = player.getExivaRestrictions()

        restrictions.allowAll = command.flags.allowAll
        restrictions.allowOwnGuild = command.flags.allowOwnGuild
        restrictions.allowOwnParty = command.flags.allowOwnParty
        restrictions.allowVipList = command.flags.allowVipList
        restrictions.allowPlayerWhitelist = command.flags.allowPlayerWhitelist
        restrictions.allowGuildWhitelist = command.flags.allowGuildWhitelist

        val addedPlayers = addEntries(
            command.addedPlayerNames,
            restrictions.playerWhitelist,
            maxWhitelist,
            playerGuidByName,
        )
        val removedPlayers = removeEntries(
            command.removedPlayerNames,
            restrictions.playerWhitelist,
            maxWhitelist,
            playerGuidByName,
        )
        val addedGuilds = addEntries(
            command.addedGuildNames,
            restrictions.guildWhitelist,
            maxWhitelist,
            guildIdByName,
        )
        val removedGuilds = removeEntries(
            command.removedGuildNames,
            restrictions.guildWhitelist,
            maxWhitelist,
            guildIdByName,
        )

        return ExivaRestrictionsEcho(
            addedPlayerNames = addedPlayers,
            removedPlayerNames = removedPlayers,
            addedGuildNames = addedGuilds,
            removedGuildNames = removedGuilds,
        )
    }

    /** `addExivaEntries` (protocolgame.cpp:190-206). */
    private fun addEntries(
        names: List<String>,
        whitelist: MutableList<Long>,
        maxLimit: Int,
        lookup: (String) -> Long,
    ): List<String> {
        val addedNames = mutableListOf<String>()
        val addedGuids = mutableSetOf<Long>()
        var lookupAttempts = 0

        for (name in names) {
            if (whitelist.size >= maxLimit || addedGuids.size >= maxLimit || lookupAttempts >= maxLimit) {
                continue
            }

            lookupAttempts++
            val id = lookup(name)
            if (id != 0L && addedGuids.add(id) && id !in whitelist) {
                whitelist.add(id)
                addedNames.add(name)
            }
        }

        return addedNames
    }

    /** `removeExivaEntries` (protocolgame.cpp:208-224). */
    private fun removeEntries(
        names: List<String>,
        whitelist: MutableList<Long>,
        maxLimit: Int,
        lookup: (String) -> Long,
    ): List<String> {
        val removedNames = mutableListOf<String>()
        val removedGuids = mutableSetOf<Long>()
        var lookupAttempts = 0

        for (name in names) {
            if (removedGuids.size >= maxLimit || lookupAttempts >= maxLimit) {
                continue
            }

            lookupAttempts++
            val id = lookup(name)
            if (id != 0L && removedGuids.add(id) && whitelist.remove(id)) {
                removedNames.add(name)
            }
        }

        return removedNames
    }

    private fun readNames(buffer: ByteBuf): List<String>? {
        if (buffer.readableBytes() < 2) {
            return null
        }

        val size = buffer.readUnsignedShortLE()
        val names = ArrayList<String>(size)
        for (i in 0 until size) {
            val name = runCatching { buffer.readTibiaString() }.getOrNull() ?: return null
            names.add(name)
        }
        return names
    }
}
