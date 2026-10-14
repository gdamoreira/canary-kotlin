package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.components.PlayerAsyncTask
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.writeTibiaString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Router wiring tests for the wave-35 batch B4 inbound opcodes: aim at target
 * (`0xC8`), transaction details (`0xC9`), livestream viewer container (`0xCA`),
 * browse field / seek in container (`0xCB`/`0xCC`), inspection (`0xCD`/`0xCE`),
 * blessing window (`0xCF`), outfit request (`0xD2`), cyclopedia map/character
 * info (`0xDB`/`0xE5`), VIP (`0xDC`-`0xDF`), bug report (`0xE6`), offer
 * description (`0xE8`), prey (`0xEB`), hireling name (`0xEC`), resource balance
 * (`0xED`), NPC greet (`0xEE`) and object info (`0xF3`). Each decoded command
 * crosses the WorldCommit confinement boundary (AGENTS.md 1.2); the prey and
 * cyclopedia requests additionally apply their ported engine state.
 */
class Wave35BatchB4RouterWiringTest {

    @Test
    fun `routes every batch B4 opcode and counts them handled`() {
        val frame = Unpooled.buffer()
        writeAimAtTarget(frame)
        writeTransactionDetails(frame)
        writeLivestreamViewerContainer(frame)
        writeBrowseField(frame)
        writeSeekInContainer(frame)
        writeNormalObjectInspection(frame)
        writeInspectPlayer(frame)
        writeBlessingWindow(frame)
        writeOutfitRequest(frame)
        writeCyclopediaMapAction(frame)
        writeAddVip(frame)
        writeRemoveVip(frame)
        writeEditVip(frame)
        writeVipGroupAction(frame)
        writeCyclopediaCharacterInfo(frame, characterId = 1)
        writeBugReport(frame)
        writeOfferDescription(frame)
        writePreyAction(frame)
        writeHirelingName(frame)
        writeResourceBalance(frame)
        writeGreet(frame)
        writeObjectInfo(frame)

        assertThat(route(frame)).isEqualTo(22)
    }

    @Test
    fun `rejects a malformed add vip name without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(VipPacketHandler.OPCODE_ADD_VIP)
        frame.writeByte(5)
        frame.writeBytes("Ali".toByteArray())

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `rejects a malformed edit vip group list without counting it handled`() {
        val frame = Unpooled.buffer()
        frame.writeByte(VipPacketHandler.OPCODE_EDIT_VIP)
        frame.writeIntLE(1)
        frame.writeTibiaString("Buddy")
        frame.writeIntLE(1)
        frame.writeByte(1)
        frame.writeByte(2)
        frame.writeByte(1)

        assertThat(route(frame)).isEqualTo(0)
    }

    @Test
    fun `applies the prey action engine on the world commit lane`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val player = hero()
            player.features.preyEngine.initializePrey(player.features.prey)
            val frame = Unpooled.buffer()
            writePreyAction(frame, slot = 2, action = 0)

            val handled = GamePacketRouter.route(player, frame, context(dispatcher))
            awaitWorldCommit(dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(player.features.prey.dialogMessages)
                .containsExactly("To unlock this prey slot first you must buy it on store.")
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `applies the cyclopedia recent deaths request on the world commit lane`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val player = hero()
            val game = Game(GameMap(), dispatcher)
            val frame = Unpooled.buffer()
            writeCyclopediaCharacterInfo(frame, characterId = 1)

            val handled = GamePacketRouter.route(player, frame, context(dispatcher, game))
            awaitWorldCommit(dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(player.hasAsyncOngoingTask(PlayerAsyncTask.RecentDeaths)).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `resolves a zero cyclopedia character id to the requesting player`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val player = hero()
            val game = Game(GameMap(), dispatcher)
            val frame = Unpooled.buffer()
            writeCyclopediaCharacterInfo(frame, characterId = 0)

            val handled = GamePacketRouter.route(player, frame, context(dispatcher, game))
            awaitWorldCommit(dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(player.hasAsyncOngoingTask(PlayerAsyncTask.RecentDeaths)).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `ignores the cyclopedia character info request of another character`() {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val player = hero()
            val game = Game(GameMap(), dispatcher)
            val frame = Unpooled.buffer()
            writeCyclopediaCharacterInfo(frame, characterId = 2)

            val handled = GamePacketRouter.route(player, frame, context(dispatcher, game))
            awaitWorldCommit(dispatcher)

            assertThat(handled).isEqualTo(1)
            assertThat(player.hasAsyncOngoingTask(PlayerAsyncTask.RecentDeaths)).isFalse()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `routes bundled zero-payload opcodes in one frame`() {
        val frame = Unpooled.buffer()
        writeBlessingWindow(frame)
        writeOutfitRequest(frame)

        assertThat(route(frame)).isEqualTo(2)
    }

    private fun writeAimAtTarget(frame: ByteBuf) {
        frame.writeByte(AimAtTargetPacketHandler.OPCODE_AIM_AT_TARGET)
        frame.writeByte(2)
        frame.writeShortLE(0x1234)
        frame.writeByte(1)
        frame.writeShortLE(0x5678)
        frame.writeByte(0)
    }

    private fun writeTransactionDetails(frame: ByteBuf) {
        frame.writeByte(TransactionDetailsPacketHandler.OPCODE_GET_TRANSACTION_DETAILS)
        frame.writeIntLE(1)
    }

    private fun writeLivestreamViewerContainer(frame: ByteBuf) {
        frame.writeByte(ContainerBrowsePacketHandler.OPCODE_LIVESTREAM_VIEWER_CONTAINER)
        frame.writeByte(7)
    }

    private fun writeBrowseField(frame: ByteBuf) {
        frame.writeByte(ContainerBrowsePacketHandler.OPCODE_BROWSE_FIELD)
        writePosition(frame, 100, 100, 7)
    }

    private fun writeSeekInContainer(frame: ByteBuf) {
        frame.writeByte(ContainerBrowsePacketHandler.OPCODE_SEEK_IN_CONTAINER)
        frame.writeByte(5)
        frame.writeShortLE(16)
        frame.writeByte(1)
    }

    private fun writeNormalObjectInspection(frame: ByteBuf) {
        frame.writeByte(InspectionPacketHandler.OPCODE_INSPECTION_OBJECT)
        frame.writeByte(InspectionPacketHandler.INSPECT_NORMALOBJECT)
        writePosition(frame, 100, 100, 7)
    }

    private fun writeInspectPlayer(frame: ByteBuf) {
        frame.writeByte(InspectionPacketHandler.OPCODE_INSPECT_PLAYER)
        frame.writeByte(1)
        frame.writeIntLE(7)
    }

    private fun writeBlessingWindow(frame: ByteBuf) {
        frame.writeByte(BlessingWindowPacketHandler.OPCODE_BLESSING_WINDOW)
    }

    private fun writeOutfitRequest(frame: ByteBuf) {
        frame.writeByte(OutfitRequestPacketHandler.OPCODE_PLAYER_REQUEST_OUTFIT)
    }

    private fun writeCyclopediaMapAction(frame: ByteBuf) {
        frame.writeByte(CyclopediaPacketHandler.OPCODE_CYCLOPEDIA_MAP_ACTION)
        frame.writeByte(0)
        frame.writeShortLE(1)
    }

    private fun writeAddVip(frame: ByteBuf) {
        frame.writeByte(VipPacketHandler.OPCODE_ADD_VIP)
        frame.writeTibiaString("Alice")
    }

    private fun writeRemoveVip(frame: ByteBuf) {
        frame.writeByte(VipPacketHandler.OPCODE_REMOVE_VIP)
        frame.writeIntLE(2)
    }

    private fun writeEditVip(frame: ByteBuf) {
        frame.writeByte(VipPacketHandler.OPCODE_EDIT_VIP)
        frame.writeIntLE(1)
        frame.writeTibiaString("Buddy")
        frame.writeIntLE(15)
        frame.writeByte(1)
        frame.writeByte(1)
        frame.writeByte(2)
    }

    private fun writeVipGroupAction(frame: ByteBuf) {
        frame.writeByte(VipPacketHandler.OPCODE_VIP_GROUP_ACTIONS)
        frame.writeByte(1)
        frame.writeTibiaString("Group")
    }

    private fun writeCyclopediaCharacterInfo(frame: ByteBuf, characterId: Int) {
        frame.writeByte(CyclopediaPacketHandler.OPCODE_CYCLOPEDIA_CHARACTER_INFO)
        frame.writeIntLE(characterId)
        frame.writeByte(CyclopediaPacketHandler.CYCLOPEDIA_CHARACTERINFO_RECENT_DEATHS)
        frame.writeShortLE(5)
        frame.writeShortLE(1)
    }

    private fun writeBugReport(frame: ByteBuf) {
        frame.writeByte(BugReportPacketHandler.OPCODE_BUG_REPORT)
        frame.writeByte(3)
        frame.writeTibiaString("bug")
    }

    private fun writeOfferDescription(frame: ByteBuf) {
        frame.writeByte(OfferDescriptionPacketHandler.OPCODE_OFFER_DESCRIPTION)
        frame.writeIntLE(31)
    }

    private fun writePreyAction(frame: ByteBuf, slot: Int = 0, action: Int = 2) {
        frame.writeByte(PreyPacketHandler.OPCODE_PREY_ACTION)
        frame.writeByte(slot)
        frame.writeByte(action)
        if (action == PreyPacketHandler.PREY_ACTION_MONSTER_SELECTION) {
            frame.writeByte(3)
        }
    }

    private fun writeHirelingName(frame: ByteBuf) {
        frame.writeByte(HirelingNamePacketHandler.OPCODE_SET_HIRELING_NAME)
        frame.writeTibiaString("Bob")
        frame.writeIntLE(1001)
        frame.writeIntLE(2001)
    }

    private fun writeResourceBalance(frame: ByteBuf) {
        frame.writeByte(ResourceBalancePacketHandler.OPCODE_SEND_RESOURCE_BALANCE)
        frame.writeByte(0x56)
    }

    private fun writeGreet(frame: ByteBuf) {
        frame.writeByte(NpcGreetPacketHandler.OPCODE_GREET)
        frame.writeIntLE(2)
    }

    private fun writeObjectInfo(frame: ByteBuf) {
        frame.writeByte(ObjectInfoPacketHandler.OPCODE_GET_OBJECT_INFO)
        frame.writeByte(1)
        frame.writeShortLE(2160)
        frame.writeByte(0)
    }

    private fun writePosition(frame: ByteBuf, x: Int, y: Int, z: Int) {
        frame.writeShortLE(x)
        frame.writeShortLE(y)
        frame.writeByte(z)
    }

    private fun route(frame: ByteBuf): Int {
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val handled = GamePacketRouter.route(hero(), frame, context(dispatcher))
            awaitWorldCommit(dispatcher)
            return handled
        } finally {
            dispatcher.shutdown()
        }
    }

    private fun context(dispatcher: GameDispatcher, game: Game? = null): GamePacketContext = GamePacketContext(
        map = GameMap(),
        dispatcher = dispatcher,
        luaEnvironment = LuaEnvironment().apply { initState() },
        game = game,
    )

    private fun hero(): Player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

    private fun awaitWorldCommit(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }
}
