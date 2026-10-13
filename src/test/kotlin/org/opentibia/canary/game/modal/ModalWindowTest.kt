package org.opentibia.canary.game.modal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.offlinetraining.Skill
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Transcript of Canary's modal window model and routing:
 *  - `struct ModalWindow` (src/game/modal_window/modal_window.hpp:19-33)
 *  - `Player::sendModalWindow` / `hasModalWindowOpen` / `onModalWindowHandled` /
 *    `clearModalWindows` (src/creatures/players/player.cpp:8314-8333, 9021-9028)
 *  - `Game::playerAnswerModalWindow` (src/game/game.cpp:11144-11176)
 */
class ModalWindowTest {

    @Test
    fun `exposes the ModalWindow struct defaults`() {
        val window = ModalWindow(id = 7L, title = "Choose a Skill", message = "Please choose a skill:")

        assertThat(window.id).isEqualTo(7L)
        assertThat(window.title).isEqualTo("Choose a Skill")
        assertThat(window.message).isEqualTo("Please choose a skill:")
        assertThat(window.buttons).isEmpty()
        assertThat(window.choices).isEmpty()
        // ModalWindow ctor: defaultEnterButton = 0xFF, defaultEscapeButton = 0xFF, priority = false.
        assertThat(window.defaultEnterButton).isEqualTo(0xFF)
        assertThat(window.defaultEscapeButton).isEqualTo(0xFF)
        assertThat(window.priority).isFalse()
    }

    @Test
    fun `records buttons and choices as text id pairs`() {
        val window = ModalWindow(id = 1L, title = "Quest", message = "Accept?")

        // Mirrors ModalWindowFunctions::luaModalWindowAddButton/AddChoice:
        // `emplace_back(text, id)` on the pair<text, uint8_t> lists.
        window.buttons.add("Okay" to 1)
        window.buttons.add("Cancel" to 0)
        window.choices.add("Reward" to 3)

        assertThat(window.buttons).containsExactly("Okay" to 1, "Cancel" to 0)
        assertThat(window.choices).containsExactly("Reward" to 3)
    }

    @Test
    fun `tracks a sent window per player and forwards it to the client`() {
        val client = RecordingClient()
        val manager = ModalWindowManager(client = client)
        val player = playerAt(1)
        val window = ModalWindow(42L, "Quest", "Accept?")

        manager.sendModalWindow(player, window)

        assertThat(client.sent).containsExactly(window)
        assertThat(manager.hasModalWindowOpen(player, 42L)).isTrue()
        assertThat(manager.hasModalWindowOpen(player, 43L)).isFalse()
        assertThat(manager.hasModalWindowOpen(playerAt(2), 42L)).isFalse()
    }

    @Test
    fun `drops a window without a client like Player sendModalWindow`() {
        val manager = ModalWindowManager()
        val player = playerAt(1)
        val window = ModalWindow(42L, "Quest", "Accept?")

        manager.sendModalWindow(player, window)

        assertThat(manager.hasModalWindowOpen(player, 42L)).isFalse()
    }

    @Test
    fun `onModalWindowHandled erases the window entries`() {
        val manager = ModalWindowManager(client = RecordingClient())
        val player = playerAt(1)
        manager.sendModalWindow(player, ModalWindow(42L, "Quest", "Accept?"))
        manager.sendModalWindow(player, ModalWindow(42L, "Quest", "Accept?"))

        manager.onModalWindowHandled(player, 42L)

        assertThat(manager.hasModalWindowOpen(player, 42L)).isFalse()
    }

    @Test
    fun `clearModalWindows removes every tracked window`() {
        val manager = ModalWindowManager(client = RecordingClient())
        val player = playerAt(1)
        manager.sendModalWindow(player, ModalWindow(1L, "A", "a"))
        manager.sendModalWindow(player, ModalWindow(2L, "B", "b"))

        manager.clearModalWindows(player)

        assertThat(manager.hasModalWindowOpen(player, 1L)).isFalse()
        assertThat(manager.hasModalWindowOpen(player, 2L)).isFalse()
    }

    @Test
    fun `playerAnswerModalWindow ignores an answer for a window that is not open`() {
        val manager = ModalWindowManager(client = RecordingClient())
        val player = playerAt(1)

        assertThat(manager.playerAnswerModalWindow(player, 42L, button = 1, choice = 0)).isFalse()
    }

    @Test
    fun `playerAnswerModalWindow erases the window and routes the answer to the onModalWindow creature events`() {
        val environment = env()
        environment.loadChunk(
            """
            local ev = CreatureEvent("ModalAnswer")
            function ev.onModalWindow(player, modalWindowId, button, choice)
                seen_modal_player = player:getName()
                seen_modal_id = modalWindowId
                seen_modal_button = button
                seen_modal_choice = choice
            end
            ev:register()
            """.trimIndent()
        )

        val dispatcher = GameDispatcher().apply { start() }
        try {
            val manager = ModalWindowManager(
                scripting = ScriptingDispatch(environment, dispatcher),
                client = RecordingClient(),
            )
            val player = playerAt(1)
            manager.sendModalWindow(player, ModalWindow(42L, "Quest", "Accept?"))

            assertThat(manager.playerAnswerModalWindow(player, 42L, button = 1, choice = 2)).isTrue()
            assertThat(manager.hasModalWindowOpen(player, 42L)).isFalse()

            awaitWorldCommit(dispatcher)

            assertThat(environment.getGlobal("seen_modal_player").tojstring()).isEqualTo("Hero")
            assertThat(environment.getGlobal("seen_modal_id").toint()).isEqualTo(42)
            assertThat(environment.getGlobal("seen_modal_button").toint()).isEqualTo(1)
            assertThat(environment.getGlobal("seen_modal_choice").toint()).isEqualTo(2)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `playerAnswerModalWindow applies the offline training skill on the confirm button`() {
        assertThat(ModalWindowManager.OFFLINE_TRAINING_WINDOW_ID).isEqualTo(0xFFFFFFFFL)

        val environment = env()
        environment.loadChunk(
            """
            local ev = CreatureEvent("ModalAnswer")
            function ev.onModalWindow(player, modalWindowId, button, choice)
                seen_modal_id = modalWindowId
            end
            ev:register()
            """.trimIndent()
        )

        val dispatcher = GameDispatcher().apply { start() }
        try {
            val manager = ModalWindowManager(
                scripting = ScriptingDispatch(environment, dispatcher),
                client = RecordingClient(),
            )
            val player = playerAt(1)
            manager.sendModalWindow(player, ModalWindow(ModalWindowManager.OFFLINE_TRAINING_WINDOW_ID, "Choose a Skill", "Please choose a skill:"))

            assertThat(
                manager.playerAnswerModalWindow(
                    player,
                    ModalWindowManager.OFFLINE_TRAINING_WINDOW_ID,
                    button = 1,
                    choice = Skill.SKILL_SWORD.value,
                ),
            ).isTrue()
            assertThat(player.features.offlineTraining.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_SWORD)
            assertThat(manager.hasModalWindowOpen(player, ModalWindowManager.OFFLINE_TRAINING_WINDOW_ID)).isFalse()

            awaitWorldCommit(dispatcher)

            // The offline training branch never reaches the creature events (game.cpp:11156-11175).
            assertThat(environment.getGlobal("seen_modal_id").isnil()).isTrue()
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `playerAnswerModalWindow aborts offline training on a non confirm button`() {
        val manager = ModalWindowManager(client = RecordingClient())
        val player = playerAt(1)
        val messages = mutableListOf<String>()
        player.sendTextMessage = { messages.add(it) }
        manager.sendModalWindow(player, ModalWindow(ModalWindowManager.OFFLINE_TRAINING_WINDOW_ID, "Choose a Skill", "Please choose a skill:"))

        assertThat(
            manager.playerAnswerModalWindow(
                player,
                ModalWindowManager.OFFLINE_TRAINING_WINDOW_ID,
                button = 0,
                choice = Skill.SKILL_SWORD.value,
            ),
        ).isTrue()

        assertThat(messages).containsExactly("Offline training aborted.")
        assertThat(player.features.offlineTraining.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_NONE)
        assertThat(manager.hasModalWindowOpen(player, ModalWindowManager.OFFLINE_TRAINING_WINDOW_ID)).isFalse()
    }

    @Test
    fun `playerAnswerModalWindow ignores a non trainable offline training choice`() {
        val manager = ModalWindowManager(client = RecordingClient())
        val player = playerAt(1)
        val messages = mutableListOf<String>()
        player.sendTextMessage = { messages.add(it) }
        manager.sendModalWindow(player, ModalWindow(ModalWindowManager.OFFLINE_TRAINING_WINDOW_ID, "Choose a Skill", "Please choose a skill:"))

        assertThat(
            manager.playerAnswerModalWindow(
                player,
                ModalWindowManager.OFFLINE_TRAINING_WINDOW_ID,
                button = 1,
                choice = 99,
            ),
        ).isTrue()

        assertThat(messages).isEmpty()
        assertThat(player.features.offlineTraining.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_NONE)
    }

    private fun playerAt(id: Long): Player =
        Player(id, "Hero", 100, 100, position = Position(100, 100, 7))

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun awaitWorldCommit(dispatcher: GameDispatcher) {
        val ran = CountDownLatch(1)
        dispatcher.dispatch { ran.countDown() }
        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    }

    private class RecordingClient : ModalWindowClient {
        val sent = mutableListOf<ModalWindow>()

        override fun sendModalWindow(modalWindow: ModalWindow) {
            sent.add(modalWindow)
        }
    }
}
