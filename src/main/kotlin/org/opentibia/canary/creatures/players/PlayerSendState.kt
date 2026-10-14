package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.prey.PreySlot
import org.opentibia.canary.systems.taskhunting.TaskHuntingSlot

/**
 * The final `Player::send*` members of player.cpp that carry real logic
 * instead of a single protocol forward
 * (src/creatures/players/player.cpp:2456-2461, 7058-7065, 7082-7086,
 * 7797-7800, 10392-10400, 10630-10640, 12847-12859).
 *
 * The remaining `Player::send*` symbols are one-line `if (client)
 * client->send*(...)` forwarders whose writers already live in
 * `network/protocol`; they are not duplicated here. `Player::client` and every
 * `ProtocolGame::send*` call are call-site seams per AGENTS.md 1.1, injected as
 * sink lambdas that default to the C++ `client == nullptr` behavior.
 */

/**
 * `Player::sendLootMessage(message)` (player.cpp:12847-12859): without a party
 * the message goes to the player itself; otherwise to the party leader (when
 * set) and every party member. `Player::sendTextMessage(MessageClasses,
 * message)` is the [Player.sendTextMessageTyped] protocol seam.
 */
fun Player.sendLootMessage(message: String) {
    val party = getParty()
    if (party == null) {
        sendTextMessageTyped(MessageClass.MESSAGE_LOOT, message)
        return
    }

    party.getLeader()?.sendTextMessageTyped(MessageClass.MESSAGE_LOOT, message)
    for (partyMember in party.getMembers()) {
        partyMember.sendTextMessageTyped(MessageClass.MESSAGE_LOOT, message)
    }
}

/**
 * `Player::sendMarketLeave()` (player.cpp:2456-2461): `inMarket` is cleared
 * unconditionally and the leave frame is sent only to an attached client.
 */
fun Player.sendMarketLeave(
    /** `Player::client != nullptr` (player.cpp:2458). */
    hasClient: () -> Boolean = { false },
    /** `client->sendMarketLeave()` (player.cpp:2459). */
    clientSendMarketLeave: () -> Unit = {},
) {
    setInMarket(false)
    if (hasClient()) {
        clientSendMarketLeave()
    }
}

/**
 * `Player::sendPlayerPartyIcons(player)` (player.cpp:7797-7800): the party
 * shield then the skull, in that order, for the target player.
 */
fun Player.sendPlayerPartyIcons(
    /** The `player` argument (player.cpp:7797). */
    player: Player,
    /** `Player::sendPartyCreatureShield(player)` (player.cpp:7798). */
    clientSendPartyCreatureShield: (Player) -> Unit = { target -> sendPartyCreatureShield(target) },
    /** `Player::sendPartyCreatureSkull(player)` (player.cpp:7799). */
    clientSendPartyCreatureSkull: (Player) -> Unit = { target -> sendPartyCreatureSkull(target) },
) {
    clientSendPartyCreatureShield(player)
    clientSendPartyCreatureSkull(player)
}

/**
 * `Player::sendPreyData()` (player.cpp:10392-10400): every prey slot, then the
 * money/bank/prey-card resource balance. `Player::getMoney()` and
 * `Player::getPreyCards()` are already Kotlin `Player` extensions; the bank
 * balance seam defaults to zero because `Player::getBankBalance` is only
 * ported on the uncomposed `PlayerVitalsState` (PARITY GAP).
 */
fun Player.sendPreyData(
    /** `Player::client != nullptr` (player.cpp:10393). */
    hasClient: () -> Boolean = { false },
    /** `client->sendPreyData(slot)` (player.cpp:10395). */
    clientSendPreyData: (PreySlot) -> Unit = {},
    /** `Player::getBankBalance()` (player.cpp:10398). */
    getBankBalance: () -> Long = { 0 },
    /** `client->sendResourcesBalance(getMoney(), getBankBalance(), getPreyCards())` (player.cpp:10398). */
    clientSendResourcesBalance: (Long, Long, Long) -> Unit = { _, _, _ -> },
) {
    if (!hasClient()) {
        return
    }

    for (slot in features.prey.preys) {
        clientSendPreyData(slot)
    }

    clientSendResourcesBalance(getMoney(), getBankBalance(), getPreyCards())
}

/**
 * `Player::sendTaskHuntingData()` (player.cpp:10630-10640): the
 * money/bank/prey-card/task-point resource balance first, then every task
 * hunting slot. The C++ slots are `unique_ptr` and null-checked; the Kotlin
 * state list holds non-null slots, so the loop is unconditional.
 */
fun Player.sendTaskHuntingData(
    /** `Player::client != nullptr` (player.cpp:10631). */
    hasClient: () -> Boolean = { false },
    /** `Player::getBankBalance()` (player.cpp:10631). */
    getBankBalance: () -> Long = { 0 },
    /** `client->sendResourcesBalance(getMoney(), getBankBalance(), getPreyCards(), getTaskHuntingPoints())` (player.cpp:10631). */
    clientSendResourcesBalance: (Long, Long, Long, Long) -> Unit = { _, _, _, _ -> },
    /** `client->sendTaskHuntingData(slot)` (player.cpp:10635). */
    clientSendTaskHuntingData: (TaskHuntingSlot) -> Unit = {},
) {
    if (!hasClient()) {
        return
    }

    clientSendResourcesBalance(getMoney(), getBankBalance(), getPreyCards(), getTaskHuntingPoints())
    for (slot in features.taskHunting.taskHunting) {
        clientSendTaskHuntingData(slot)
    }
}

/**
 * `Player::sendAddTileItem(itemTile, pos, item)` (player.cpp:7058-7065): the
 * item's stack position on the tile is resolved first; `-1` suppresses the
 * frame exactly like C++.
 */
fun Player.sendAddTileItem(
    /** `itemTile` (player.cpp:7058). */
    itemTile: Tile,
    /** `pos` (player.cpp:7058). */
    pos: Position,
    /** `item` (player.cpp:7058). */
    item: Item,
    /** `Player::client != nullptr` (player.cpp:7059). */
    hasClient: () -> Boolean = { false },
    /** `client->sendAddTileItem(pos, stackpos, item)` (player.cpp:7063). */
    clientSendAddTileItem: (Position, Int, Item) -> Unit = { _, _, _ -> },
) {
    if (!hasClient()) {
        return
    }

    val stackpos = itemTile.getStackposOfItem(this, item)
    if (stackpos != -1) {
        clientSendAddTileItem(pos, stackpos, item)
    }
}

/**
 * `Player::sendUpdateTileCreature(creature)` (player.cpp:7082-7086): the
 * creature's client index on its tile, then the frame. C++ dereferences
 * `creature->getTile()` unconditionally; the port returns early when the tile
 * or client is absent (hardening, same frame as `client == nullptr`).
 */
fun Player.sendUpdateTileCreature(
    /** `creature` (player.cpp:7082). */
    creature: Creature,
    /** `Player::client != nullptr` (player.cpp:7083). */
    hasClient: () -> Boolean = { false },
    /** `client->sendUpdateTileCreature(pos, stackpos, creature)` (player.cpp:7084). */
    clientSendUpdateTileCreature: (Position, Int, Creature) -> Unit = { _, _, _ -> },
) {
    if (!hasClient()) {
        return
    }

    val tile = creature.getTile() ?: return
    clientSendUpdateTileCreature(
        creature.position,
        tile.getClientIndexOfCreature(this, creature),
        creature,
    )
}
