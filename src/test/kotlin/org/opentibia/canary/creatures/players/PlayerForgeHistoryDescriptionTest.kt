package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.forge.ForgeAction_t
import org.opentibia.canary.systems.forge.ForgeHistory

/**
 * Transcribes `Player::registerForgeHistoryDescription`
 * (src/creatures/players/player.cpp:11868-12023).
 *
 * The C++ renders the history entry as the HTML description the client shows
 * and appends the mutated copy through `forgeHistory().add(history)`. The
 * `Item::items.getItemIdByName` / `Item::items[id]` lookups are the injected
 * [Items] registry; `formatPrice` (tools.cpp:1884) is the ported helper.
 *
 * `SLIVERSTOCORES` and `INCREASELIMIT` re-label the stored entry as
 * `DUSTTOSLIVERS` (player.cpp:12002-12009), which is pinned here.
 */
class PlayerForgeHistoryDescriptionTest {

    private fun newPlayer(): Player =
        Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun swordItems(): Items = Items().apply {
        register(ItemType(id = 1000, name = "sword", article = "a"))
    }

    private fun history(actionType: ForgeAction_t, success: Boolean = true): ForgeHistory = ForgeHistory(
        actionType = actionType,
        tier = 3,
        bonus = 0,
        cost = 1000,
        dustCost = 100,
        coresCost = 50,
        success = success,
        firstItemName = "sword",
        secondItemName = "sword",
    )

    @Test
    fun `fusion success renders both partners and the tier plus one result`() {
        // player.cpp:11877-11937.
        val player = newPlayer()
        val entry = history(ForgeAction_t.FUSION)

        player.registerForgeHistoryDescription(entry, swordItems())

        assertThat(entry.description).isEqualTo(
            "Successful <br><br>Fusion partners:<ul> <li>First item: a sword, tier 3</li>" +
                "<li>Second item: a sword, tier 3</li></ul><br>Result:<ul> " +
                "<li>First item: tier + 1</li><li>Second item: consumed</li></ul><br>Invested:" +
                "<ul><li>50 cores</li><li>100 dust</li><li>1 k gold</li></ul>",
        )
        assertThat(player.forgeHistory().get().map { it.description })
            .containsExactly(entry.description)
    }

    @Test
    fun `fusion success shows the convergence marker and the unchanged second item`() {
        // The `history.bonus == 8` second-item label (player.cpp:11933).
        val player = newPlayer()
        val entry = history(ForgeAction_t.FUSION).apply {
            convergence = true
            bonus = 8
        }

        player.registerForgeHistoryDescription(entry, swordItems())

        assertThat(entry.description).startsWith("Successful (convergence) <br>")
        assertThat(entry.description).contains("Second item: unchanged")
    }

    @Test
    fun `fusion failure uses the tier-loss label and the fixed 100 dust`() {
        // player.cpp:11939-11974.
        val player = newPlayer()
        val entry = history(ForgeAction_t.FUSION, success = false)

        player.registerForgeHistoryDescription(entry, swordItems())

        assertThat(entry.description).isEqualTo(
            "Unsuccessful <br><br>Fusion partners:<ul> <li>First item: a sword, tier 3</li>" +
                "<li>Second item: a sword, tier 3</li></ul><br>Result:<ul> " +
                "<li>First item: unchanged</li><li>Second item: tier - 1</li></ul><br>Invested:" +
                "<ul><li>50 cores</li><li>100 dust</li><li>1 k gold</li></ul>",
        )
    }

    @Test
    fun `fusion failure with tier zero reads consumed instead of tier minus one`() {
        // `historyTierString` (player.cpp:11871): tier 0 -> "consumed".
        val player = newPlayer()
        val entry = history(ForgeAction_t.FUSION, success = false).apply { tier = 0 }

        player.registerForgeHistoryDescription(entry, swordItems())

        assertThat(entry.description).contains("<li>Second item: consumed</li>")
        assertThat(entry.description).contains("First item: a sword, tier 0")
    }

    @Test
    fun `transfer renders the donor tier and the price`() {
        // player.cpp:11975-12001.
        val player = newPlayer()
        val entry = history(ForgeAction_t.TRANSFER).apply { tier = 2 }

        player.registerForgeHistoryDescription(entry, swordItems())

        assertThat(entry.description).isEqualTo(
            "Successful <br><br>Transfer partners:<ul> <li>First item: a sword, tier 2</li>" +
                "<li>Second item: a sword, tier 2</li></ul><br>Result:<ul> " +
                "<li>First item: a sword, tier 2</li><li>Second item: a sword, 1 k</li></ul>" +
                "<br>Invested:<ul><li>1 cores</li><li>100 dust</li><li>1 k gold</li></ul>",
        )
    }

    @Test
    fun `a bonus of three prints a zero price`() {
        // `price` (player.cpp:11873): `history.bonus != 3` gates formatPrice.
        val player = newPlayer()
        val entry = history(ForgeAction_t.TRANSFER).apply { bonus = 3 }

        player.registerForgeHistoryDescription(entry, swordItems())

        assertThat(entry.description).contains("<li>Second item: a sword, 0</li>")
        assertThat(entry.description).contains("<li>0 gold</li>")
    }

    @Test
    fun `dust-to-slivers renders the conversion`() {
        // player.cpp:12003-12004.
        val player = newPlayer()
        val entry = history(ForgeAction_t.DUSTTOSLIVERS).apply {
            cost = 100
            gained = 25
        }

        player.registerForgeHistoryDescription(entry, swordItems())

        assertThat(entry.description).isEqualTo("Converted 100 dust to 25 slivers.")
    }

    @Test
    fun `slivers-to-cores relabels the entry as dust-to-slivers`() {
        // player.cpp:12005-12007: `history.actionType = DUSTTOSLIVERS`.
        val player = newPlayer()
        val entry = history(ForgeAction_t.SLIVERSTOCORES).apply {
            cost = 30
            gained = 5
        }

        player.registerForgeHistoryDescription(entry, swordItems())

        assertThat(entry.description).isEqualTo("Converted 30 slivers to 5 exalted core.")
        assertThat(entry.actionType).isEqualTo(ForgeAction_t.DUSTTOSLIVERS)
        assertThat(player.forgeHistory().get().single().actionType).isEqualTo(ForgeAction_t.DUSTTOSLIVERS)
    }

    @Test
    fun `increase-limit reports the next dust level and relabels the entry`() {
        // player.cpp:12008-12009: `history.gained + 1`.
        val player = newPlayer()
        val entry = history(ForgeAction_t.INCREASELIMIT).apply {
            cost = 10
            gained = 75
        }

        player.registerForgeHistoryDescription(entry, swordItems())

        assertThat(entry.description).isEqualTo("Spent 10 dust to increase the dust limit to 76.")
        assertThat(entry.actionType).isEqualTo(ForgeAction_t.DUSTTOSLIVERS)
    }

    @Test
    fun `an unknown first item name resolves to the empty item type`() {
        // `Item::items[0]` is the default entry; the name never resolved.
        val player = newPlayer()
        val entry = history(ForgeAction_t.FUSION).apply { firstItemName = "missing item" }

        player.registerForgeHistoryDescription(entry, swordItems())

        assertThat(entry.description).contains("First item:  , tier 3")
    }
}
