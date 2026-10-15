package org.opentibia.canary.network

import java.io.File
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap

/**
 * Production-composition tests for the chat boot: [CanaryServer] composes the
 * `g_chat()` registry ([CanaryServer.chatManager]) and the `GAME_STATE_INIT`
 * arm loads `{CORE_DIRECTORY}/chatchannels/chatchannels.xml` through
 * `g_chat().load()` (src/game/game.cpp:967,
 * src/creatures/interactions/chat.cpp:340-410).
 */
class CanaryServerChatBootTest {

    @TempDir
    lateinit var coreDir: Path

    private lateinit var dispatcher: GameDispatcher

    private val channelsXml = """
        <chatchannels>
            <channel id="9" name="World Chat" public="1"/>
            <channel id="10" name="Staff" public="0"/>
        </chatchannels>
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        dispatcher = GameDispatcher().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        dispatcher.shutdown()
    }

    private fun server(core: String?): CanaryServer = CanaryServer(
        loginPort = 0,
        gamePort = 0,
        gameMap = GameMap(),
        dispatcher = dispatcher,
        coreDirectory = core,
    )

    private fun writeChannelsXml() {
        val file = File(coreDir.toFile(), "chatchannels/chatchannels.xml")
        file.parentFile.mkdirs()
        file.writeText(channelsXml)
    }

    @Test
    fun `the boot loads the chat channels from the core directory`() {
        writeChannelsXml()
        val server = server(coreDir.toString())
        try {
            server.start()

            val publicChannel = server.chatManager.getChannelById(9)
            assertThat(publicChannel).isNotNull
            assertThat(publicChannel!!.getName()).isEqualTo("World Chat")
            assertThat(publicChannel.isPublicChannel()).isTrue

            val staffChannel = server.chatManager.getChannelById(10)
            assertThat(staffChannel).isNotNull
            assertThat(staffChannel!!.getName()).isEqualTo("Staff")
            assertThat(staffChannel.isPublicChannel()).isFalse
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a boot without the chat XML keeps the channel registry empty`() {
        val server = server(coreDir.toString())
        try {
            server.start()

            assertThat(server.chatManager.getChannelById(9)).isNull()
            assertThat(server.chatManager.getChannelById(10)).isNull()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a boot without a core directory keeps the chat load inert`() {
        val server = server(core = null)
        try {
            server.start()

            assertThat(server.chatManager.getChannelById(9)).isNull()
        } finally {
            server.stop()
        }
    }
}
