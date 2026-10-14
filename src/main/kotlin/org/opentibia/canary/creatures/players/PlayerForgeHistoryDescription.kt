package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Items
import org.opentibia.canary.systems.forge.ForgeAction_t
import org.opentibia.canary.systems.forge.ForgeHistory
import org.opentibia.canary.utils.Tools

/**
 * `Player::registerForgeHistoryDescription`
 * (src/creatures/players/player.cpp:11868-12023).
 *
 * Renders the HTML description the client shows for a forge-history entry and
 * appends the mutated copy through `forgeHistory().add(history)`. The C++
 * `Item::items.getItemIdByName` / `Item::items[id]` lookups are the injected
 * [items] registry; `formatPrice` (tools.cpp:1884) is ported in [Tools]. An
 * unresolved item name reads the registry's default entry (id 0), whose empty
 * article/name reproduce `Item::items[0]`.
 *
 * `SLIVERSTOCORES` and `INCREASELIMIT` re-label the stored entry as
 * `DUSTTOSLIVERS` exactly like the C++ assignments before `add`.
 */
fun Player.registerForgeHistoryDescription(history: ForgeHistory, items: Items = Items()) {
    val successfulString = if (history.success) "Successful" else "Unsuccessful"
    val historyTierString = if (history.tier > 0) "tier - 1" else "consumed"
    val price = if (history.bonus != 3) Tools.formatPrice(history.cost.toString(), true) else "0"

    val itemId = items.getItemIdByName(history.firstItemName)
    val itemType = items.getItemType(itemId)
    val convergence = if (history.convergence) " (convergence)" else ""

    val detailsResponse = StringBuilder()
    when (history.actionType) {
        ForgeAction_t.FUSION -> {
            val secondItem = if (history.success) {
                if (history.bonus == 8) "unchanged" else "consumed"
            } else {
                if (history.bonus == 8) "unchanged" else historyTierString
            }
            val firstResult = if (history.success) "tier + 1" else "unchanged"
            val dustCost = if (history.success) history.dustCost.toString() else "100"

            detailsResponse.append(
                "$successfulString$convergence <br><br>" +
                    "Fusion partners:" +
                    "<ul> " +
                    "<li>" +
                    "First item: ${itemType.article} ${itemType.name}, tier ${history.tier}" +
                    "</li>" +
                    "<li>" +
                    "Second item: ${itemType.article} ${itemType.name}, tier ${history.tier}" +
                    "</li>" +
                    "</ul>" +
                    "<br>" +
                    "Result:" +
                    "<ul> " +
                    "<li>" +
                    "First item: $firstResult" +
                    "</li>" +
                    "<li>" +
                    "Second item: $secondItem" +
                    "</li>" +
                    "</ul>" +
                    "<br>" +
                    "Invested:" +
                    "<ul>" +
                    "<li>" +
                    "${history.coresCost} cores" +
                    "</li>" +
                    "<li>" +
                    "$dustCost dust" +
                    "</li>" +
                    "<li>" +
                    "$price gold" +
                    "</li>" +
                    "</ul>",
            )
        }

        ForgeAction_t.TRANSFER -> detailsResponse.append(
            "$successfulString$convergence <br><br>" +
                "Transfer partners:" +
                "<ul> " +
                "<li>" +
                "First item: ${itemType.article} ${itemType.name}, tier ${history.tier}" +
                "</li>" +
                "<li>" +
                "Second item: ${itemType.article} ${itemType.name}, tier ${history.tier}" +
                "</li>" +
                "</ul>" +
                "<br>" +
                "Result:" +
                "<ul> " +
                "<li>" +
                "First item: ${itemType.article} ${itemType.name}, tier ${history.tier}" +
                "</li>" +
                "<li>" +
                "Second item: ${itemType.article} ${itemType.name}, $price" +
                "</li>" +
                "</ul>" +
                "<br>" +
                "Invested:" +
                "<ul>" +
                "<li>" +
                "1 cores" +
                "</li>" +
                "<li>" +
                "100 dust" +
                "</li>" +
                "<li>" +
                "$price gold" +
                "</li>" +
                "</ul>",
        )

        ForgeAction_t.DUSTTOSLIVERS -> detailsResponse.append(
            "Converted ${history.cost} dust to ${history.gained} slivers.",
        )

        ForgeAction_t.SLIVERSTOCORES -> {
            history.actionType = ForgeAction_t.DUSTTOSLIVERS
            detailsResponse.append(
                "Converted ${history.cost} slivers to ${history.gained} exalted core.",
            )
        }

        ForgeAction_t.INCREASELIMIT -> {
            history.actionType = ForgeAction_t.DUSTTOSLIVERS
            detailsResponse.append(
                "Spent ${history.cost} dust to increase the dust limit to ${history.gained + 1}.",
            )
        }
    }

    history.description = detailsResponse.toString()

    forgeHistory().add(history)
}
