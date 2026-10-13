package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.modal.ModalWindow
import org.opentibia.canary.game.modal.ModalWindowClient
import org.opentibia.canary.game.modal.ModalWindowManager
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `ModalWindow` userdata class, mirroring
 * `ModalWindowFunctions` (src/lua/functions/core/game/modal_window_functions.cpp).
 */
class ModalWindowFunctionsLibraryTest {

    private class RecordingModalWindowOperations : ModalWindowOperations {
        val sent = mutableListOf<Pair<Player, ModalWindow>>()
        val errors = mutableListOf<String>()

        override fun sendToPlayer(player: Player, window: ModalWindow): Boolean {
            sent += player to window
            return true
        }

        override fun reportError(message: String) {
            errors += message
        }
    }

    private fun environment(operations: ModalWindowOperations): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { ModalWindowFunctions.install(it, operations) }

    @Test
    fun `the constructor stores id title and message`() {
        val environment = environment(RecordingModalWindowOperations())

        environment.loadChunk(
            """
            window = ModalWindow(7, 'Title', 'Message')
            id = window:getId()
            title = window:getTitle()
            message = window:getMessage()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("window").isuserdata()).isTrue()
        assertThat(environment.getGlobal("id").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("title").tojstring()).isEqualTo("Title")
        assertThat(environment.getGlobal("message").tojstring()).isEqualTo("Message")
    }

    @Test
    fun `setters update the window state`() {
        val environment = environment(RecordingModalWindowOperations())

        environment.loadChunk(
            """
            window = ModalWindow(1, 'Title', 'Message')
            window:setTitle('New')
            window:setMessage('Body')
            title = window:getTitle()
            message = window:getMessage()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("title").tojstring()).isEqualTo("New")
        assertThat(environment.getGlobal("message").tojstring()).isEqualTo("Body")
    }

    @Test
    fun `buttons and choices keep the Canary pair shape`() {
        val environment = environment(RecordingModalWindowOperations())

        environment.loadChunk(
            """
            window = ModalWindow(1, 'Title', 'Message')
            window:addButton(1, 'Ok')
            window:addChoice(2, 'Yes')
            buttons = window:getButtonCount()
            choices = window:getChoiceCount()
            enter = window:getDefaultEnterButton()
            escape = window:getDefaultEscapeButton()
            window:setDefaultEnterButton(1)
            window:setDefaultEscapeButton(2)
            newEnter = window:getDefaultEnterButton()
            newEscape = window:getDefaultEscapeButton()
            priority = window:hasPriority()
            window:setPriority(true)
            newPriority = window:hasPriority()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("buttons").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("choices").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("enter").toint()).isEqualTo(255)
        assertThat(environment.getGlobal("escape").toint()).isEqualTo(255)
        assertThat(environment.getGlobal("newEnter").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("newEscape").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("priority").toboolean()).isFalse()
        assertThat(environment.getGlobal("newPriority").toboolean()).isTrue()
    }

    @Test
    fun `sendToPlayer hands the window to the operations seam`() {
        val operations = RecordingModalWindowOperations()
        val environment = environment(operations)
        environment.globals.set(
            "hero",
            environment.userdata.wrap(
                "Player",
                Player(1, "Hero", 100, 100, position = Position(100, 100, 7)),
            ),
        )

        environment.loadChunk(
            """
            window = ModalWindow(3, 'Title', 'Message')
            sent = window:sendToPlayer(hero)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("sent").toboolean()).isTrue()
        assertThat(operations.sent).hasSize(1)
        assertThat(operations.sent.single().second.id).isEqualTo(3)
    }

    @Test
    fun `sendToPlayer reports a missing player`() {
        val operations = RecordingModalWindowOperations()
        val environment = environment(operations)

        environment.loadChunk(
            """
            window = ModalWindow(3, 'Title', 'Message')
            window:sendToPlayer('not a player')
            """.trimIndent(),
        )

        assertThat(operations.errors).containsExactly("Player not found")
    }

    @Test
    fun `the manager-backed operations skip open windows and track new sends`() {
        val delivered = mutableListOf<ModalWindow>()
        val manager = ModalWindowManager(client = ModalWindowClient { delivered += it })
        val environment = environment(WorldModalWindowOperations(manager))
        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        environment.globals.set("hero", environment.userdata.wrap("Player", player))

        environment.loadChunk(
            """
            window = ModalWindow(9, 'Title', 'Message')
            first = window:sendToPlayer(hero)
            second = window:sendToPlayer(hero)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("first").toboolean()).isTrue()
        assertThat(environment.getGlobal("second").toboolean()).isTrue()
        assertThat(delivered).hasSize(1)
    }
}
