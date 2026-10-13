package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Decode tests for the wave-35 batch B4 inbound opcodes, transcribed from
 * Canary `src/server/network/protocol/protocolgame.cpp`:
 *  - `0xC8` `ProtocolGame::parseAimAtTarget` (protocolgame.cpp:12196)
 *  - `0xC9` `ProtocolGame::parseGetTransactionDetails` (protocolgame.cpp:4336)
 *  - `0xCA` `ProtocolGame::resendLivestreamViewerContainer` (protocolgame.cpp:12448)
 *  - `0xCB` `ProtocolGame::parseBrowseField` (protocolgame.cpp:4506)
 *  - `0xCC` `ProtocolGame::parseSeekInContainer` (protocolgame.cpp:4511)
 *  - `0xCD` `ProtocolGame::parseInspectionObject` (protocolgame.cpp:2913)
 *  - `0xCE` `ProtocolGame::parseInspectPlayer` (protocolgame.cpp:3325)
 *  - `0xCF` `ProtocolGame::sendBlessingWindow` (protocolgame.cpp:5899)
 *  - `0xD2` `Game::playerRequestOutfit` (game.cpp:7206)
 *  - `0xDB` `ProtocolGame::parseCyclopediaMapAction` (protocolgame.cpp:3332)
 *  - `0xDC`-`0xDF` `parseAddVip`/`parseRemoveVip`/`parseEditVip`/`parseVipGroupActions`
 *    (protocolgame.cpp:2844-2888)
 *  - `0xE5` `ProtocolGame::parseCyclopediaCharacterInfo` (protocolgame.cpp:3026)
 *  - `0xE6` `ProtocolGame::parseBugReport` (protocolgame.cpp:4241)
 *  - `0xE8` `ProtocolGame::parseOfferDescription` (protocolgame.cpp:4258)
 *  - `0xEB` `ProtocolGame::parsePreyAction` (protocolgame.cpp:4263)
 *  - `0xEC` `ProtocolGame::parseSetHirelingName` (protocolgame.cpp:3350)
 *  - `0xED` `ProtocolGame::parseSendResourceBalance` (protocolgame.cpp:4305)
 *  - `0xEE` `ProtocolGame::parseGreet` (protocolgame.cpp:4253)
 *  - `0xF3` `ProtocolGame::parseGetObjectInfo` (protocolgame.cpp:4342)
 */
class Wave35BatchB4PacketHandlerTest {

    // ----- 0xC8 parseAimAtTarget -----

    @Test
    fun `decodes 0xC8 aim at target spell updates`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeShortLE(0x1234)
        payload.writeByte(1)
        payload.writeShortLE(0x5678)
        payload.writeByte(0)

        val command = AimAtTargetPacketHandler.parse(AimAtTargetPacketHandler.OPCODE_AIM_AT_TARGET, payload)

        assertThat(command).isEqualTo(
            AimAtTargetCommand(
                spells = listOf(
                    AimAtTargetSpell(spellId = 0x1234, state = 1),
                    AimAtTargetSpell(spellId = 0x5678, state = 0),
                ),
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes 0xC8 with zero spell updates`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)

        assertThat(AimAtTargetPacketHandler.parse(AimAtTargetPacketHandler.OPCODE_AIM_AT_TARGET, payload))
            .isEqualTo(AimAtTargetCommand(spells = emptyList()))
    }

    @Test
    fun `rejects a truncated 0xC8 aim at target spell state`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeShortLE(0x1234)

        assertThat(AimAtTargetPacketHandler.parse(AimAtTargetPacketHandler.OPCODE_AIM_AT_TARGET, payload)).isNull()
    }

    // ----- 0xC9 parseGetTransactionDetails -----

    @Test
    fun `decodes 0xC9 get transaction details id`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(0x12345678)

        val command = TransactionDetailsPacketHandler.parse(
            TransactionDetailsPacketHandler.OPCODE_GET_TRANSACTION_DETAILS,
            payload,
        )

        assertThat(command).isEqualTo(TransactionDetailsCommand(transactionId = 0x12345678L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xC9 get transaction details`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(1)

        assertThat(
            TransactionDetailsPacketHandler.parse(
                TransactionDetailsPacketHandler.OPCODE_GET_TRANSACTION_DETAILS,
                payload,
            ),
        ).isNull()
    }

    // ----- 0xCA resendLivestreamViewerContainer -----

    @Test
    fun `decodes 0xCA livestream viewer container id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(7)

        val command = ContainerBrowsePacketHandler.parse(
            ContainerBrowsePacketHandler.OPCODE_LIVESTREAM_VIEWER_CONTAINER,
            payload,
        )

        assertThat(command).isEqualTo(LivestreamViewerContainerCommand(containerId = 7))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects an empty 0xCA livestream viewer container`() {
        assertThat(
            ContainerBrowsePacketHandler.parse(
                ContainerBrowsePacketHandler.OPCODE_LIVESTREAM_VIEWER_CONTAINER,
                Unpooled.buffer(),
            ),
        ).isNull()
    }

    // ----- 0xCB parseBrowseField -----

    @Test
    fun `decodes 0xCB browse field position`() {
        val payload = Unpooled.buffer()
        writePosition(payload, 100, 100, 7)

        val command = ContainerBrowsePacketHandler.parse(ContainerBrowsePacketHandler.OPCODE_BROWSE_FIELD, payload)

        assertThat(command).isEqualTo(BrowseFieldCommand(position = Position(100, 100, 7)))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xCB browse field position`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(100)
        payload.writeShortLE(100)

        assertThat(ContainerBrowsePacketHandler.parse(ContainerBrowsePacketHandler.OPCODE_BROWSE_FIELD, payload))
            .isNull()
    }

    // ----- 0xCC parseSeekInContainer -----

    @Test
    fun `decodes 0xCC seek in container fields`() {
        val payload = Unpooled.buffer()
        payload.writeByte(5)
        payload.writeShortLE(16)
        payload.writeByte(1)

        val command = ContainerBrowsePacketHandler.parse(
            ContainerBrowsePacketHandler.OPCODE_SEEK_IN_CONTAINER,
            payload,
        )

        assertThat(command).isEqualTo(SeekInContainerCommand(containerId = 5, index = 16, primaryType = 1))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xCC seek in container`() {
        val payload = Unpooled.buffer()
        payload.writeByte(5)
        payload.writeByte(16)

        assertThat(ContainerBrowsePacketHandler.parse(ContainerBrowsePacketHandler.OPCODE_SEEK_IN_CONTAINER, payload))
            .isNull()
    }

    // ----- 0xCD parseInspectionObject -----

    @Test
    fun `decodes 0xCD normal object inspection position`() {
        val payload = Unpooled.buffer()
        payload.writeByte(InspectionPacketHandler.INSPECT_NORMALOBJECT)
        writePosition(payload, 100, 100, 7)

        val command = InspectionPacketHandler.parse(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT, payload)

        assertThat(command).isEqualTo(InspectObjectCommand.NormalObject(Position(100, 100, 7)))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes 0xCD npc trade inspection item`() {
        val payload = Unpooled.buffer()
        payload.writeByte(InspectionPacketHandler.INSPECT_NPCTRADE)
        payload.writeShortLE(2160)
        payload.writeByte(3)

        val command = InspectionPacketHandler.parse(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT, payload)

        assertThat(command).isEqualTo(InspectObjectCommand.NpcTrade(itemId = 2160, itemCount = 3))
    }

    @Test
    fun `decodes 0xCD cyclopedia inspection item`() {
        val payload = Unpooled.buffer()
        payload.writeByte(InspectionPacketHandler.INSPECT_CYCLOPEDIA)
        payload.writeShortLE(2160)
        payload.writeByte(3)

        val command = InspectionPacketHandler.parse(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT, payload)

        assertThat(command).isEqualTo(InspectObjectCommand.Cyclopedia(itemId = 2160, itemCount = 3))
    }

    @Test
    fun `decodes 0xCD proficiency inspection item`() {
        val payload = Unpooled.buffer()
        payload.writeByte(InspectionPacketHandler.INSPECT_PROFICIENCY)
        payload.writeShortLE(2160)
        payload.writeByte(0)

        val command = InspectionPacketHandler.parse(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT, payload)

        assertThat(command).isEqualTo(InspectObjectCommand.Proficiency(itemId = 2160, unknown = 0))
    }

    @Test
    fun `decodes 0xCD player trade inspection without payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(InspectionPacketHandler.INSPECT_PLAYERTRADE)

        val command = InspectionPacketHandler.parse(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT, payload)

        assertThat(command).isEqualTo(InspectObjectCommand.NoOp(inspectionType = 2))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xCD normal object inspection position`() {
        val payload = Unpooled.buffer()
        payload.writeByte(InspectionPacketHandler.INSPECT_NORMALOBJECT)
        payload.writeShortLE(100)

        assertThat(InspectionPacketHandler.parse(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT, payload)).isNull()
    }

    // ----- 0xCE parseInspectPlayer -----

    @Test
    fun `decodes 0xCE inspect player action with player id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeIntLE(7)

        val command = InspectionPacketHandler.parse(InspectionPacketHandler.OPCODE_INSPECT_PLAYER, payload)

        assertThat(command).isEqualTo(InspectPlayerCommand(action = 1, playerId = 7L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes 0xCE inspect player action without player id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)

        val command = InspectionPacketHandler.parse(InspectionPacketHandler.OPCODE_INSPECT_PLAYER, payload)

        assertThat(command).isEqualTo(InspectPlayerCommand(action = 0, playerId = null))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xCE inspect player id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(5)
        payload.writeShortLE(7)

        assertThat(InspectionPacketHandler.parse(InspectionPacketHandler.OPCODE_INSPECT_PLAYER, payload)).isNull()
    }

    // ----- 0xCF sendBlessingWindow -----

    @Test
    fun `decodes 0xCF blessing window request without payload`() {
        val payload = Unpooled.buffer()

        val command = BlessingWindowPacketHandler.parse(BlessingWindowPacketHandler.OPCODE_BLESSING_WINDOW, payload)

        assertThat(command).isEqualTo(BlessingWindowCommand)
        assertThat(payload.readableBytes()).isZero()
    }

    // ----- 0xD2 playerRequestOutfit -----

    @Test
    fun `decodes 0xD2 outfit window request without payload`() {
        val payload = Unpooled.buffer()

        val command = OutfitRequestPacketHandler.parse(OutfitRequestPacketHandler.OPCODE_PLAYER_REQUEST_OUTFIT, payload)

        assertThat(command).isEqualTo(OutfitWindowRequestCommand)
        assertThat(payload.readableBytes()).isZero()
    }

    // ----- 0xDB parseCyclopediaMapAction -----

    @Test
    fun `decodes 0xDB cyclopedia map action 0`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeShortLE(0x1234)

        val command = CyclopediaPacketHandler.parseMapAction(payload)

        assertThat(command).isEqualTo(CyclopediaMapActionCommand(action = 0, mapId = 0x1234, extra = null))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes 0xDB cyclopedia map action 1`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeShortLE(0x1234)
        payload.writeIntLE(0x12345678)

        val command = CyclopediaPacketHandler.parseMapAction(payload)

        assertThat(command).isEqualTo(
            CyclopediaMapActionCommand(action = 1, mapId = 0x1234, extra = 0x12345678L),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes 0xDB cyclopedia map action 2`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeShortLE(0x1234)

        val command = CyclopediaPacketHandler.parseMapAction(payload)

        assertThat(command).isEqualTo(CyclopediaMapActionCommand(action = 2, mapId = 0x1234, extra = null))
    }

    @Test
    fun `decodes 0xDB unknown cyclopedia map action without payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(9)

        val command = CyclopediaPacketHandler.parseMapAction(payload)

        assertThat(command).isEqualTo(CyclopediaMapActionCommand(action = 9, mapId = null, extra = null))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xDB cyclopedia map action`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeByte(1)

        assertThat(CyclopediaPacketHandler.parseMapAction(payload)).isNull()
    }

    // ----- 0xDC parseAddVip -----

    @Test
    fun `decodes 0xDC add vip name`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("Alice")

        val command = VipPacketHandler.parse(VipPacketHandler.OPCODE_ADD_VIP, payload)

        assertThat(command).isEqualTo(AddVipCommand(name = "Alice"))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xDC add vip name`() {
        val payload = Unpooled.buffer()
        payload.writeByte(5)
        payload.writeBytes("Ali".toByteArray())

        assertThat(VipPacketHandler.parse(VipPacketHandler.OPCODE_ADD_VIP, payload)).isNull()
    }

    // ----- 0xDD parseRemoveVip -----

    @Test
    fun `decodes 0xDD remove vip guid`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(0x12345678)

        val command = VipPacketHandler.parse(VipPacketHandler.OPCODE_REMOVE_VIP, payload)

        assertThat(command).isEqualTo(RemoveVipCommand(guid = 0x12345678L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xDD remove vip guid`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(1)

        assertThat(VipPacketHandler.parse(VipPacketHandler.OPCODE_REMOVE_VIP, payload)).isNull()
    }

    // ----- 0xDE parseEditVip -----

    @Test
    fun `decodes 0xDE edit vip entry with the icon clamp and group list`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(1)
        payload.writeTibiaString("Buddy")
        payload.writeIntLE(15)
        payload.writeByte(1)
        payload.writeByte(2)
        payload.writeByte(1)
        payload.writeByte(2)

        val command = VipPacketHandler.parse(VipPacketHandler.OPCODE_EDIT_VIP, payload)

        assertThat(command).isEqualTo(
            EditVipCommand(
                guid = 1L,
                description = "Buddy",
                icon = 10,
                notify = true,
                groupsId = listOf(1, 2),
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xDE edit vip group list`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(1)
        payload.writeTibiaString("Buddy")
        payload.writeIntLE(1)
        payload.writeByte(1)
        payload.writeByte(2)
        payload.writeByte(1)

        assertThat(VipPacketHandler.parse(VipPacketHandler.OPCODE_EDIT_VIP, payload)).isNull()
    }

    // ----- 0xDF parseVipGroupActions -----

    @Test
    fun `decodes 0xDF add vip group`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeTibiaString("Group")

        val command = VipPacketHandler.parse(VipPacketHandler.OPCODE_VIP_GROUP_ACTIONS, payload)

        assertThat(command).isEqualTo(VipGroupActionCommand.Add(groupName = "Group"))
    }

    @Test
    fun `decodes 0xDF edit vip group`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeByte(3)
        payload.writeTibiaString("New")

        val command = VipPacketHandler.parse(VipPacketHandler.OPCODE_VIP_GROUP_ACTIONS, payload)

        assertThat(command).isEqualTo(VipGroupActionCommand.Edit(groupId = 3, newGroupName = "New"))
    }

    @Test
    fun `decodes 0xDF remove vip group`() {
        val payload = Unpooled.buffer()
        payload.writeByte(3)
        payload.writeByte(3)

        val command = VipPacketHandler.parse(VipPacketHandler.OPCODE_VIP_GROUP_ACTIONS, payload)

        assertThat(command).isEqualTo(VipGroupActionCommand.Remove(groupId = 3))
    }

    @Test
    fun `decodes 0xDF unknown vip group action without payload`() {
        val payload = Unpooled.buffer()
        payload.writeByte(9)

        val command = VipPacketHandler.parse(VipPacketHandler.OPCODE_VIP_GROUP_ACTIONS, payload)

        assertThat(command).isEqualTo(VipGroupActionCommand.Unknown(action = 9))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xDF edit vip group name`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeByte(3)
        payload.writeShortLE(5)

        assertThat(VipPacketHandler.parse(VipPacketHandler.OPCODE_VIP_GROUP_ACTIONS, payload)).isNull()
    }

    // ----- 0xE5 parseCyclopediaCharacterInfo -----

    @Test
    fun `decodes 0xE5 cyclopedia character info without pagination`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(5)
        payload.writeByte(0)

        val command = CyclopediaPacketHandler.parseCharacterInfo(payload)

        assertThat(command).isEqualTo(
            CyclopediaCharacterInfoCommand(characterId = 5L, infoType = 0, entriesPerPage = 0, page = 0),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes 0xE5 recent deaths pagination clamping`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(5)
        payload.writeByte(CyclopediaPacketHandler.CYCLOPEDIA_CHARACTERINFO_RECENT_DEATHS)
        payload.writeShortLE(320)
        payload.writeShortLE(0)

        val command = CyclopediaPacketHandler.parseCharacterInfo(payload)

        assertThat(command).isEqualTo(
            CyclopediaCharacterInfoCommand(
                characterId = 5L,
                infoType = CyclopediaPacketHandler.CYCLOPEDIA_CHARACTERINFO_RECENT_DEATHS,
                entriesPerPage = 30,
                page = 1,
            ),
        )
    }

    @Test
    fun `decodes 0xE5 recent pvp kills pagination floor`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(5)
        payload.writeByte(CyclopediaPacketHandler.CYCLOPEDIA_CHARACTERINFO_RECENTPVPKILLS)
        payload.writeShortLE(1)
        payload.writeShortLE(2)

        val command = CyclopediaPacketHandler.parseCharacterInfo(payload)

        assertThat(command).isEqualTo(
            CyclopediaCharacterInfoCommand(
                characterId = 5L,
                infoType = CyclopediaPacketHandler.CYCLOPEDIA_CHARACTERINFO_RECENTPVPKILLS,
                entriesPerPage = 5,
                page = 2,
            ),
        )
    }

    @Test
    fun `rejects a truncated 0xE5 recent deaths pagination`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(5)
        payload.writeByte(CyclopediaPacketHandler.CYCLOPEDIA_CHARACTERINFO_RECENT_DEATHS)
        payload.writeShortLE(5)

        assertThat(CyclopediaPacketHandler.parseCharacterInfo(payload)).isNull()
    }

    // ----- 0xE6 parseBugReport -----

    @Test
    fun `decodes 0xE6 bug report without position`() {
        val payload = Unpooled.buffer()
        payload.writeByte(3)
        payload.writeTibiaString("typo")

        val command = BugReportPacketHandler.parse(BugReportPacketHandler.OPCODE_BUG_REPORT, payload)

        assertThat(command).isEqualTo(BugReportCommand(category = 3, message = "typo", position = null))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes 0xE6 map bug report with position`() {
        val payload = Unpooled.buffer()
        payload.writeByte(BugReportPacketHandler.BUG_CATEGORY_MAP)
        payload.writeTibiaString("hole")
        writePosition(payload, 100, 100, 7)

        val command = BugReportPacketHandler.parse(BugReportPacketHandler.OPCODE_BUG_REPORT, payload)

        assertThat(command).isEqualTo(
            BugReportCommand(category = 0, message = "hole", position = Position(100, 100, 7)),
        )
    }

    @Test
    fun `rejects a truncated 0xE6 map bug report position`() {
        val payload = Unpooled.buffer()
        payload.writeByte(BugReportPacketHandler.BUG_CATEGORY_MAP)
        payload.writeTibiaString("hole")
        payload.writeShortLE(100)

        assertThat(BugReportPacketHandler.parse(BugReportPacketHandler.OPCODE_BUG_REPORT, payload)).isNull()
    }

    // ----- 0xE8 parseOfferDescription -----

    @Test
    fun `decodes 0xE8 offer description id`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(31)

        val command = OfferDescriptionPacketHandler.parse(
            OfferDescriptionPacketHandler.OPCODE_OFFER_DESCRIPTION,
            payload,
        )

        assertThat(command).isEqualTo(OfferDescriptionCommand(offerId = 31L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xE8 offer description id`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(31)

        assertThat(OfferDescriptionPacketHandler.parse(OfferDescriptionPacketHandler.OPCODE_OFFER_DESCRIPTION, payload))
            .isNull()
    }

    // ----- 0xEB parsePreyAction -----

    @Test
    fun `decodes 0xEB monster selection index`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeByte(2)
        payload.writeByte(3)

        val command = PreyPacketHandler.parse(PreyPacketHandler.OPCODE_PREY_ACTION, payload)

        assertThat(command).isEqualTo(
            PreyActionCommand(slot = 0, action = 2, option = 0, index = 3, raceId = 0),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `decodes 0xEB prey option`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeByte(5)
        payload.writeByte(2)

        val command = PreyPacketHandler.parse(PreyPacketHandler.OPCODE_PREY_ACTION, payload)

        assertThat(command).isEqualTo(
            PreyActionCommand(slot = 1, action = 5, option = 2, index = -1, raceId = 0),
        )
    }

    @Test
    fun `decodes 0xEB list all selection race id`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeByte(4)
        payload.writeShortLE(0x1234)

        val command = PreyPacketHandler.parse(PreyPacketHandler.OPCODE_PREY_ACTION, payload)

        assertThat(command).isEqualTo(
            PreyActionCommand(slot = 2, action = 4, option = 0, index = -1, raceId = 0x1234),
        )
    }

    @Test
    fun `rejects a truncated 0xEB monster selection index`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0)
        payload.writeByte(2)

        assertThat(PreyPacketHandler.parse(PreyPacketHandler.OPCODE_PREY_ACTION, payload)).isNull()
    }

    // ----- 0xEC parseSetHirelingName -----

    @Test
    fun `decodes 0xEC hireling name fields`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("Bob")
        payload.writeIntLE(1001)
        payload.writeIntLE(2001)

        val command = HirelingNamePacketHandler.parse(HirelingNamePacketHandler.OPCODE_SET_HIRELING_NAME, payload)

        assertThat(command).isEqualTo(HirelingNameCommand(name = "Bob", hirelingId = 1001L, unknown = 2001L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xEC hireling name payload`() {
        val payload = Unpooled.buffer()
        payload.writeTibiaString("Bob")
        payload.writeIntLE(1001)

        assertThat(HirelingNamePacketHandler.parse(HirelingNamePacketHandler.OPCODE_SET_HIRELING_NAME, payload))
            .isNull()
    }

    // ----- 0xED parseSendResourceBalance -----

    @Test
    fun `decodes 0xED requested resource`() {
        val payload = Unpooled.buffer()
        payload.writeByte(0x56)

        val command = ResourceBalancePacketHandler.parse(
            ResourceBalancePacketHandler.OPCODE_SEND_RESOURCE_BALANCE,
            payload,
        )

        assertThat(command).isEqualTo(ResourceBalanceCommand(requestedResource = 0x56))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects an empty 0xED requested resource`() {
        assertThat(
            ResourceBalancePacketHandler.parse(
                ResourceBalancePacketHandler.OPCODE_SEND_RESOURCE_BALANCE,
                Unpooled.buffer(),
            ),
        ).isNull()
    }

    // ----- 0xEE parseGreet -----

    @Test
    fun `decodes 0xEE npc id`() {
        val payload = Unpooled.buffer()
        payload.writeIntLE(7)

        val command = NpcGreetPacketHandler.parse(NpcGreetPacketHandler.OPCODE_GREET, payload)

        assertThat(command).isEqualTo(NpcGreetCommand(npcId = 7L))
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `rejects a truncated 0xEE npc id`() {
        val payload = Unpooled.buffer()
        payload.writeShortLE(7)

        assertThat(NpcGreetPacketHandler.parse(NpcGreetPacketHandler.OPCODE_GREET, payload)).isNull()
    }

    // ----- 0xF3 parseGetObjectInfo -----

    @Test
    fun `decodes 0xF3 object info entries`() {
        val payload = Unpooled.buffer()
        payload.writeByte(2)
        payload.writeShortLE(2160)
        payload.writeByte(1)
        payload.writeShortLE(2170)
        payload.writeByte(0)

        val command = ObjectInfoPacketHandler.parse(ObjectInfoPacketHandler.OPCODE_GET_OBJECT_INFO, payload)

        assertThat(command).isEqualTo(
            ObjectInfoCommand(
                objects = listOf(
                    ObjectInfoEntry(id = 2160, data = 1),
                    ObjectInfoEntry(id = 2170, data = 0),
                ),
            ),
        )
        assertThat(payload.readableBytes()).isZero()
    }

    @Test
    fun `stops 0xF3 object info at a partial trailing entry`() {
        val payload = Unpooled.buffer()
        payload.writeByte(3)
        payload.writeShortLE(2160)
        payload.writeByte(1)
        payload.writeShortLE(2170)
        payload.writeByte(0)

        val command = ObjectInfoPacketHandler.parse(ObjectInfoPacketHandler.OPCODE_GET_OBJECT_INFO, payload)

        assertThat(command?.objects).containsExactly(
            ObjectInfoEntry(id = 2160, data = 1),
            ObjectInfoEntry(id = 2170, data = 0),
        )
    }

    @Test
    fun `decodes 0xF3 object info with no complete entry`() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        payload.writeShortLE(2160)

        val command = ObjectInfoPacketHandler.parse(ObjectInfoPacketHandler.OPCODE_GET_OBJECT_INFO, payload)

        assertThat(command?.objects).isEmpty()
    }

    // ----- generic -----

    @Test
    fun `returns null for an unrelated opcode`() {
        assertThat(AimAtTargetPacketHandler.parse(0xFE, Unpooled.buffer())).isNull()
        assertThat(ObjectInfoPacketHandler.parse(0xFE, Unpooled.buffer())).isNull()
    }

    private fun writePosition(payload: ByteBuf, x: Int, y: Int, z: Int) {
        payload.writeShortLE(x)
        payload.writeShortLE(y)
        payload.writeByte(z)
    }
}
