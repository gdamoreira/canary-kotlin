package org.opentibia.canary.network.protocol.podium

import io.netty.buffer.ByteBuf
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Outbound podium window serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendPodiumWindow`         (0xC8, protocolgame.cpp:9260-9353)
 *  - `ProtocolGame::sendMonsterPodiumWindow`  (0xC2, protocolgame.cpp:11432-11489)
 *  - `ProtocolGame::sendPodiumDetails`        (no opcode, protocolgame.cpp:11394-11430)
 *  - `ProtocolGame::addOutfitAndMountBytes`   (protocolgame.cpp:225-248)
 *
 * The `!podium`/`oldProtocol` guards stay with the caller. The podium custom
 * attributes (`PodiumVisible`, `LookType`, `LookMount`, `LookDirection`,
 * `LookHead`, ...) are resolved by the caller into the frames; `null` models an
 * absent custom attribute, whose default is the C++ fallback (`1` for the
 * visible byte, `2` for the direction, a zero `uint16_t` for the look values).
 * `sendPodiumDetails` receives the `toSendMonsters` list resolved by the
 * caller, with `null` modelling a race id without a `MonsterType` (Canary
 * counts it and writes nothing). Every multi-byte field is Little-Endian.
 */
object PodiumWindowWriter {

    const val PACKET_PODIUM_WINDOW: Int = 0xC8
    const val PACKET_MONSTER_PODIUM_WINDOW: Int = 0xC2

    /** `ITEM_PODIUM_OF_VIGOUR` (`src/utils/utils_definitions.hpp:701`). */
    const val ITEM_PODIUM_OF_VIGOUR: Int = 38707

    /** `sendMonsterPodiumWindow` "Tantugly's Head" look type ex (protocolgame.cpp:11413). */
    private const val TENTUGLY_LOOK_TYPE_EX: Int = 35105

    /** `sendMonsterPodiumWindow` replacement look type ex (protocolgame.cpp:11414). */
    private const val TENTUGLY_REPLACEMENT_LOOK_TYPE_EX: Int = 39003

    /** One custom-attribute look value on the podium (`LookType`/`LookMount`). */
    data class PodiumLook(
        val value: Int,
        val head: Int = 0,
        val body: Int = 0,
        val legs: Int = 0,
        val feet: Int = 0,
        val addons: Int = 0,
    )

    /** One owned outfit row of the podium window. */
    data class PodiumOutfitEntry(
        val lookType: Int,
        val name: String,
        val addons: Int,
    )

    /** One owned mount row of the podium window. */
    data class PodiumMountEntry(
        val clientId: Int,
        val name: String,
    )

    /** The 0xC8 podium window payload. */
    data class PodiumWindowFrame(
        val position: Position,
        val itemId: Int,
        val stackpos: Int,
        val visible: Int? = null,
        val direction: Int? = null,
        val lookType: PodiumLook? = null,
        val lookMount: PodiumLook? = null,
        val outfits: List<PodiumOutfitEntry> = emptyList(),
        val mounts: List<PodiumMountEntry> = emptyList(),
    )

    /**
     * One `sendPodiumDetails` row. `null` list elements model a race id whose
     * `MonsterType` lookup failed: the count still includes it and no bytes are
     * written, exactly like the C++ `continue`.
     */
    data class MonsterPodiumEntry(
        val raceId: Int,
        val name: String,
        val lookType: Int,
        val lookTypeEx: Int,
        val head: Int = 0,
        val body: Int = 0,
        val legs: Int = 0,
        val feet: Int = 0,
        val addons: Int = 0,
    )

    /** The 0xC2 monster podium window payload. */
    data class MonsterPodiumWindowFrame(
        val position: Position,
        val itemId: Int,
        val stackPos: Int,
        val isBossPodium: Boolean,
        val lookType: Int? = null,
        val lookHead: Int = 0,
        val lookBody: Int = 0,
        val lookLegs: Int = 0,
        val lookFeet: Int = 0,
        val lookAddons: Int = 0,
        val visible: Int? = null,
        val direction: Int? = null,
        val monsters: List<MonsterPodiumEntry?> = emptyList(),
    )

    /** `sendPodiumWindow` (protocolgame.cpp:9260-9353). */
    fun writePodiumWindow(out: ByteBuf, frame: PodiumWindowFrame) {
        out.writeByte(PACKET_PODIUM_WINDOW)

        val lookType = frame.lookType
        if (lookType != null) {
            writeLook(out, lookType, addons = true)
        } else {
            out.writeShortLE(0)
        }

        val lookMount = frame.lookMount
        if (lookMount != null) {
            writeLook(out, lookMount, addons = false)
        } else {
            out.writeShortLE(0)
            out.writeByte(0)
            out.writeByte(0)
            out.writeByte(0)
            out.writeByte(0)
        }

        out.writeShortLE(0)

        out.writeShortLE(frame.outfits.size)
        for (outfit in frame.outfits) {
            out.writeShortLE(outfit.lookType)
            out.writeTibiaString(outfit.name)
            out.writeByte(outfit.addons)
            out.writeByte(0x00)
        }

        out.writeShortLE(frame.mounts.size)
        for (mount in frame.mounts) {
            out.writeShortLE(mount.clientId)
            out.writeTibiaString(mount.name)
            out.writeByte(0x00)
        }

        out.writeShortLE(0)
        out.writeByte(0x05)
        out.writeByte(if (lookMount != null) 0x01 else 0x00)
        out.writeShortLE(0)

        out.writePosition(frame.position)
        out.writeShortLE(frame.itemId)
        out.writeByte(frame.stackpos)

        out.writeByte(frame.visible ?: 0x01)
        out.writeByte(if (lookType != null) 0x01 else 0x00)
        out.writeByte(frame.direction ?: 2)
    }

    /** `sendMonsterPodiumWindow` (protocolgame.cpp:11432-11489). */
    fun writeMonsterPodiumWindow(out: ByteBuf, frame: MonsterPodiumWindowFrame) {
        out.writeByte(PACKET_MONSTER_PODIUM_WINDOW)

        val lookValue = frame.lookType ?: 0
        val isBossSelected = lookValue > 0

        out.writeShortLE(if (isBossSelected) lookValue else 0)
        if (isBossSelected) {
            out.writeByte(frame.lookHead)
            out.writeByte(frame.lookBody)
            out.writeByte(frame.lookLegs)
            out.writeByte(frame.lookFeet)
            out.writeByte(frame.lookAddons)
        } else {
            out.writeShortLE(0)
        }

        out.writeShortLE(0)
        out.writeByte(if (frame.isBossPodium) 0x01 else 0x00)
        writePodiumDetails(out, frame.monsters, frame.isBossPodium)

        out.writePosition(frame.position)
        out.writeShortLE(frame.itemId)
        out.writeByte(frame.stackPos)

        out.writeByte(frame.visible ?: 0x01)
        out.writeByte(if (frame.lookType != null) 0x01 else 0x00)
        out.writeByte(frame.direction ?: 2)
    }

    /**
     * `sendPodiumDetails` (protocolgame.cpp:11394-11430). Non-boss podiums only
     * need the race id; boss podiums append the monster name and outfit, with
     * the "Tantugly's Head" look type ex replacement.
     */
    fun writePodiumDetails(out: ByteBuf, monsters: List<MonsterPodiumEntry?>, isBoss: Boolean) {
        out.writeShortLE(monsters.size)
        for (monster in monsters) {
            if (monster == null) {
                continue
            }

            if (!isBoss) {
                out.writeShortLE(monster.raceId)
                continue
            }

            out.writeShortLE(monster.raceId)
            var lookTypeEx = monster.lookTypeEx
            if (lookTypeEx == TENTUGLY_LOOK_TYPE_EX) {
                lookTypeEx = TENTUGLY_REPLACEMENT_LOOK_TYPE_EX
                out.writeTibiaString("Tentugly")
            } else {
                out.writeTibiaString(monster.name)
            }
            out.writeShortLE(monster.lookType)
            if (monster.lookType != 0) {
                out.writeByte(monster.head)
                out.writeByte(monster.body)
                out.writeByte(monster.legs)
                out.writeByte(monster.feet)
                out.writeByte(monster.addons)
            } else {
                out.writeShortLE(lookTypeEx)
            }
        }
    }

    /** `addOutfitAndMountBytes` (protocolgame.cpp:225-248) for the podium look values. */
    private fun writeLook(out: ByteBuf, look: PodiumLook, addons: Boolean) {
        out.writeShortLE(look.value)
        if (look.value != 0) {
            out.writeByte(look.head)
            out.writeByte(look.body)
            out.writeByte(look.legs)
            out.writeByte(look.feet)
            if (addons) {
                out.writeByte(look.addons)
            }
        }
    }

    private fun ByteBuf.writePosition(pos: Position) {
        writeShortLE(pos.x)
        writeShortLE(pos.y)
        writeByte(pos.z)
    }
}
