package org.opentibia.canary.scripting

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position

class ScriptingEngineTest {

    @Test
    fun `registers and executes item action script`() {
        val registry = ScriptRegistry()
        val ropeId = 2120

        var ropeUsed = false
        registry.registerAction(itemId = ropeId) { player, item, position ->
            ropeUsed = true
            player.drainHealth(10)
            true
        }

        val player = Player(1, "Adventurer", 100, 100, position = Position(100, 100, 7))
        val ropeItem = Item(id = ropeId)

        val handled = registry.executeAction(player, ropeItem, Position(100, 100, 7))
        assertThat(handled).isTrue()
        assertThat(ropeUsed).isTrue()
        assertThat(player.health).isEqualTo(90)
    }

    @Test
    fun `registers and executes talkaction command`() {
        val registry = ScriptRegistry()

        var commandOutput = ""
        registry.registerTalkAction(words = "!online") { player, words, param ->
            commandOutput = "1 player currently online."
            true
        }

        val player = Player(1, "Adventurer", 100, 100, position = Position(100, 100, 7))
        val handled = registry.executeTalkAction(player, "!online", "")

        assertThat(handled).isTrue()
        assertThat(commandOutput).isEqualTo("1 player currently online.")
    }
}
