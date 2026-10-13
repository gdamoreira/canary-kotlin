package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.network.message.writeTibiaString

/**
 * `VipStatus_t` (`src/creatures/creatures_definitions.hpp:783-788`).
 */
enum class VipStatus(val value: Int) {
    Offline(0),
    Online(1),
    Pending(2),
    Training(3),
}

/**
 * Outbound social/window frames for the Canary `ProtocolProfileId::Current`
 * profile (`protocol_new` / 15.x: `oldProtocol == false`), ported from
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendBosstiaryData`          (0x61, protocolgame.cpp:11171-11204)
 *  - `ProtocolGame::sendCloseContainer`         (0x6F, protocolgame.cpp:7981-7986)
 *  - `ProtocolGame::sendTradeItemRequest`       (0x7D/0x7E, protocolgame.cpp:7936-7973)
 *  - `ProtocolGame::sendCloseTrade`             (0x7F, protocolgame.cpp:7975-7979)
 *  - `ProtocolGame::sendChannelsDialog`         (0xAB, protocolgame.cpp:6209-6221)
 *  - `ProtocolGame::sendChannel`                (0xAC, protocolgame.cpp:6223-6235)
 *  - `ProtocolGame::sendCreatePrivateChannel`   (0xB2, protocolgame.cpp:6198-6207)
 *  - `ProtocolGame::sendClosePrivate`           (0xB3, protocolgame.cpp:6191-6196)
 *  - `ProtocolGame::sendHousesInfo`             (0xC6, protocolgame.cpp:12112-12145)
 *  - `ProtocolGame::sendVIP`                    (0xD2, protocolgame.cpp:9374-9405)
 *  - `ProtocolGame::sendVIPGroups`              (0xD4, protocolgame.cpp:9407-9425)
 *  - `ProtocolGame::sendModalWindow`            (0xFA, protocolgame.cpp:9630-9659)
 *
 * Every multi-byte field is Little-Endian and strings are `uint16` length plus
 * UTF-8. Values Canary resolves from live player/engine state (channel lists,
 * house ids, VIP groups, modal contents) are passed in by the caller.
 */
object SocialWindowWriter {

    const val PACKET_BOSSTIARY_DATA: Int = 0x61
    const val PACKET_CLOSE_CONTAINER: Int = 0x6F
    const val PACKET_TRADE_REQUEST_ACK: Int = 0x7D
    const val PACKET_TRADE_REQUEST: Int = 0x7E
    const val PACKET_CLOSE_TRADE: Int = 0x7F
    const val PACKET_CHANNELS_DIALOG: Int = 0xAB
    const val PACKET_CHANNEL: Int = 0xAC
    const val PACKET_CREATE_PRIVATE_CHANNEL: Int = 0xB2
    const val PACKET_CLOSE_PRIVATE: Int = 0xB3
    const val PACKET_HOUSES_INFO: Int = 0xC6
    const val PACKET_VIP: Int = 0xD2
    const val PACKET_VIP_GROUPS: Int = 0xD4
    const val PACKET_MODAL_WINDOW: Int = 0xFA

    /** `sendVIP` caps the icon at 10 (`std::min<uint32_t>(10, icon)`, protocolgame.cpp:9391). */
    private const val MAX_VIP_ICON = 10L

    /** One `sendChannelsDialog` entry (`Channel::getId`/`getName`, protocolgame.cpp:6216-6217). */
    data class ChannelSummary(
        val id: Int,
        val name: String,
    )

    /**
     * One `sendVIP` record (protocolgame.cpp:9374-9405). [status] is
     * `PlayerVIP::getStatus`, [notify] the stored notification flag.
     */
    data class VipEntry(
        val guid: Long,
        val name: String,
        val description: String = "",
        val icon: Long = 0,
        val notify: Boolean = false,
        val status: VipStatus = VipStatus.Offline,
    )

    /** One `PlayerVIP::getGroups` entry (`VIPGroup::id/name/customizable`). */
    data class VipGroup(
        val id: Int,
        val name: String,
        val customizable: Boolean = false,
    )

    /** One `ModalWindow::buttons`/`choices` pair (`std::list<std::pair<std::string, uint8_t>>`). */
    data class ModalWindowButton(
        val id: Int,
        val label: String,
    )

    /**
     * The `ModalWindow` payload (`src/game/modal_window/modal_window.hpp:19-24`).
     * Clicking [defaultEscapeButton]/[defaultEnterButton] sends its id or, when
     * set to `0xFF`, behaves as the client's default.
     */
    data class ModalWindowFrame(
        val id: Long,
        val title: String,
        val message: String,
        val buttons: List<ModalWindowButton> = emptyList(),
        val choices: List<ModalWindowButton> = emptyList(),
        val defaultEscapeButton: Int = 0xFF,
        val defaultEnterButton: Int = 0xFF,
        val priority: Boolean = false,
    )

    /**
     * `sendBosstiaryData` (protocolgame.cpp:11171-11204): the fixed Bane/Archfoe/
     * Nemesis kill thresholds and the points granted at each stage.
     */
    fun writeBosstiaryData(out: ByteBuf) {
        out.writeByte(PACKET_BOSSTIARY_DATA)
        out.writeShortLE(25)
        out.writeShortLE(100)
        out.writeShortLE(300)
        out.writeShortLE(5)
        out.writeShortLE(20)
        out.writeShortLE(60)
        out.writeShortLE(1)
        out.writeShortLE(3)
        out.writeShortLE(5)
        out.writeShortLE(5)
        out.writeShortLE(15)
        out.writeShortLE(30)
        out.writeShortLE(10)
        out.writeShortLE(30)
        out.writeShortLE(60)
        out.writeShortLE(10)
        out.writeShortLE(30)
        out.writeShortLE(60)
    }

    /** `sendCloseContainer` (protocolgame.cpp:7981-7986). */
    fun writeCloseContainer(out: ByteBuf, cid: Int) {
        out.writeByte(PACKET_CLOSE_CONTAINER)
        out.writeByte(cid)
    }

    /**
     * `sendTradeItemRequest` (protocolgame.cpp:7936-7973). [ack] selects the
     * `0x7D` acknowledgement opcode; a container is flattened breadth-first with
     * the container itself first, exactly like Canary's `itemList`.
     */
    fun writeTradeItemRequest(out: ByteBuf, traderName: String, item: Item, ack: Boolean) {
        out.writeByte(if (ack) PACKET_TRADE_REQUEST_ACK else PACKET_TRADE_REQUEST)
        out.writeTibiaString(traderName)

        if (item is Container) {
            val items = ArrayList<Item>()
            val pending = ArrayDeque<Container>()
            items += item
            pending += item
            while (pending.isNotEmpty()) {
                val container = pending.removeFirst()
                for (child in container.items) {
                    if (child is Container) {
                        pending += child
                    }
                    items += child
                }
            }

            out.writeByte(items.size)
            for (entry in items) {
                GameWindowWriter.writeItem(out, entry)
            }
        } else {
            out.writeByte(0x01)
            GameWindowWriter.writeItem(out, item)
        }
    }

    /** `sendCloseTrade` (protocolgame.cpp:7975-7979): a frame carrying only the opcode. */
    fun writeCloseTrade(out: ByteBuf) {
        out.writeByte(PACKET_CLOSE_TRADE)
    }

    /** `sendChannelsDialog` (protocolgame.cpp:6209-6221). */
    fun writeChannelsDialog(out: ByteBuf, channels: List<ChannelSummary>) {
        out.writeByte(PACKET_CHANNELS_DIALOG)
        out.writeByte(channels.size)
        for (channel in channels) {
            out.writeShortLE(channel.id)
            out.writeTibiaString(channel.name)
        }
    }

    /**
     * `sendChannel` (protocolgame.cpp:6223-6235). A `null` user list serializes
     * as the zero count Canary writes for absent maps.
     */
    fun writeChannel(
        out: ByteBuf,
        channelId: Int,
        channelName: String,
        users: List<String>?,
        invitedUsers: List<String>?,
    ) {
        out.writeByte(PACKET_CHANNEL)
        out.writeShortLE(channelId)
        out.writeTibiaString(channelName)
        out.writeChannelUserNames(users)
        out.writeChannelUserNames(invitedUsers)
    }

    /** `sendCreatePrivateChannel` (protocolgame.cpp:6198-6207): the owner is the only initial user. */
    fun writeCreatePrivateChannel(out: ByteBuf, channelId: Int, channelName: String, playerName: String) {
        out.writeByte(PACKET_CREATE_PRIVATE_CHANNEL)
        out.writeShortLE(channelId)
        out.writeTibiaString(channelName)
        out.writeShortLE(0x01)
        out.writeTibiaString(playerName)
        out.writeShortLE(0x00)
    }

    /** `sendClosePrivate` (protocolgame.cpp:6191-6196). */
    fun writeClosePrivate(out: ByteBuf, channelId: Int) {
        out.writeByte(PACKET_CLOSE_PRIVATE)
        out.writeShortLE(channelId)
    }

    /**
     * `sendHousesInfo` (protocolgame.cpp:12112-12145). [houseClientId] is the
     * client id of the house owned by the player (0 when none), [accountHouseCount]
     * `Player::getAccountId`'s house count and [housesClientIds] every house in
     * the map in `Houses::getHouses` iteration order.
     */
    fun writeHousesInfo(
        out: ByteBuf,
        accountHouseCount: Int,
        houseClientId: Long,
        housesClientIds: List<Long>,
    ) {
        out.writeByte(PACKET_HOUSES_INFO)
        out.writeIntLE(houseClientId.toInt())
        out.writeByte(0x00)
        out.writeByte(accountHouseCount)
        out.writeByte(0x00)
        out.writeByte(3)
        out.writeByte(3)
        out.writeByte(0x01)
        out.writeByte(0x01)
        out.writeIntLE(houseClientId.toInt())
        out.writeShortLE(housesClientIds.size)
        for (clientId in housesClientIds) {
            out.writeIntLE(clientId.toInt())
        }
    }

    /**
     * `sendVIP` current-profile branch (protocolgame.cpp:9374-9405): description,
     * capped icon, notification flag, status and the group ids the VIP belongs to.
     */
    fun writeVip(out: ByteBuf, entry: VipEntry, groups: List<Int> = emptyList()) {
        out.writeByte(PACKET_VIP)
        out.writeIntLE(entry.guid.toInt())
        out.writeTibiaString(entry.name)
        out.writeTibiaString(entry.description)
        out.writeIntLE(minOf(MAX_VIP_ICON, entry.icon).toInt())
        out.writeByte(if (entry.notify) 0x01 else 0x00)
        out.writeByte(entry.status.value)
        out.writeByte(groups.size)
        for (groupId in groups) {
            out.writeByte(groupId)
        }
    }

    /** `sendVIPGroups` (protocolgame.cpp:9407-9425): the trailing byte is the remaining group slots. */
    fun writeVipGroups(out: ByteBuf, groups: List<VipGroup>, maxGroupEntries: Int) {
        out.writeByte(PACKET_VIP_GROUPS)
        out.writeByte(groups.size)
        for (group in groups) {
            out.writeByte(group.id)
            out.writeTibiaString(group.name)
            out.writeByte(if (group.customizable) 0x01 else 0x00)
        }
        out.writeByte(maxGroupEntries - groups.size)
    }

    /** `sendModalWindow` (protocolgame.cpp:9630-9659). */
    fun writeModalWindow(out: ByteBuf, modal: ModalWindowFrame) {
        out.writeByte(PACKET_MODAL_WINDOW)
        out.writeIntLE(modal.id.toInt())
        out.writeTibiaString(modal.title)
        out.writeTibiaString(modal.message)

        out.writeByte(modal.buttons.size)
        for (button in modal.buttons) {
            out.writeTibiaString(button.label)
            out.writeByte(button.id)
        }

        out.writeByte(modal.choices.size)
        for (choice in modal.choices) {
            out.writeTibiaString(choice.label)
            out.writeByte(choice.id)
        }

        out.writeByte(modal.defaultEscapeButton)
        out.writeByte(modal.defaultEnterButton)
        out.writeByte(if (modal.priority) 0x01 else 0x00)
    }

    private fun ByteBuf.writeChannelUserNames(names: List<String>?) {
        if (names == null) {
            writeShortLE(0)
            return
        }

        writeShortLE(names.size)
        for (name in names) {
            writeTibiaString(name)
        }
    }
}
