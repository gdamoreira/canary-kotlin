package org.opentibia.canary.items.containers

import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.CylinderLink_t
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.QueryDestinationResult
import org.opentibia.canary.items.QueryMaxCountResult
import org.opentibia.canary.items.Thing
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.systems.rewardchest.ReturnValue

/** Contract for the `Player` members `Mailbox::sendItem` uses (src/creatures/players/player.hpp). */
interface MailboxRecipient {
    /** `Player::getInbox()` (player.cpp:905-907). */
    fun getInbox(): Container

    /** `Player::isOnline()` (player.hpp:1841). */
    fun isOnline(): Boolean

    /** `Player::onReceiveMail()` (player.hpp). */
    fun onReceiveMail()
}

/**
 * Seam for the `g_game()`/`g_saveManager()` calls `Mailbox::sendItem` performs
 * (src/items/containers/mailbox/mailbox.cpp:74-119). The port injects them per
 * AGENTS.md 1.1 instead of reaching the globals.
 */
interface MailboxRouter {
    /** `Game::getPlayerByName(name, allowOffline)` (src/game/game.cpp). */
    fun getPlayerByName(name: String, allowOffline: Boolean): MailboxRecipient?

    /** `Game::internalMoveItem(from, to, index, item, count, actor, flags)` (src/game/game.cpp:2436). */
    fun internalMoveItem(
        from: Cylinder?,
        to: Cylinder,
        index: Int,
        item: Item,
        count: Int,
        actor: Creature?,
        flags: Int,
    ): ReturnValue

    /** `Game::transformItem(item, newId)` (src/game/game.cpp). */
    fun transformItem(item: Item, newId: Int): Item?

    /** `SaveManager::savePlayer(player)` (src/game/scheduling/save_manager.cpp). */
    fun savePlayer(player: MailboxRecipient)

    /** `Player::autoCloseContainers(container)` (src/creatures/players/player.cpp:1271). */
    fun autoCloseContainers(container: Container)
}

/**
 * Port of `Mailbox` (src/items/containers/mailbox/mailbox.hpp:15-50,
 * mailbox.cpp:18-144).
 *
 * `sendItem`'s game/spectator/save-manager lookups become the injected
 * [router] seam.
 */
class Mailbox(
    id: Int,
    type: ItemType? = null,
) : Item(id, count = 1, type = type), Cylinder {

    /** `g_game()`/`g_saveManager()` seam used by [sendItem] (PARITY: injected dependency). */
    var router: MailboxRouter? = null

    /** `Mailbox::getMailbox` (mailbox.hpp:20-22). */
    fun getMailbox(): Mailbox = this

    override fun getItem(): Item = this

    override fun getContainer(): Container? = super<Item>.getContainer()

    /** `Mailbox::getCylinder` (mailbox.hpp:24-26). */
    override fun getCylinder(): Cylinder = this

    override fun getParent(): Cylinder? = super<Item>.getParent()

    override fun isRemoved(): Boolean = super<Item>.isRemoved()

    /** `Mailbox::queryAdd` (mailbox.cpp:18-24). */
    override fun queryAdd(index: Int, thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue {
        val item = thing?.getItem() ?: return ReturnValue.NOTPOSSIBLE
        return if (canSend(item)) ReturnValue.NOERROR else ReturnValue.NOTPOSSIBLE
    }

    /** `Mailbox::queryMaxCount` (mailbox.cpp:26-29). */
    override fun queryMaxCount(
        index: Int,
        thing: Thing?,
        count: Int,
        maxQueryCount: Int,
        flags: Int,
    ): QueryMaxCountResult = QueryMaxCountResult(ReturnValue.NOERROR, maxOf(1, count))

    /** `Mailbox::queryRemove` (mailbox.cpp:31-33). */
    override fun queryRemove(thing: Thing?, count: Int, flags: Int, actor: Creature?): ReturnValue =
        ReturnValue.NOTPOSSIBLE

    /** `Mailbox::queryDestination` (mailbox.cpp:35-37). */
    override fun queryDestination(index: Int, thing: Thing?, destItem: Item?, flags: Int): QueryDestinationResult =
        QueryDestinationResult(getMailbox(), index, destItem, flags)

    /** `Mailbox::addThing` (mailbox.cpp:39-41). */
    override fun addThing(thing: Thing?) = addThing(0, thing)

    /** `Mailbox::addThing` (mailbox.cpp:43-52). */
    override fun addThing(index: Int, thing: Thing?) {
        val item = thing?.getItem() ?: return
        if (canSend(item)) {
            sendItem(item)
        }
    }

    /** `Mailbox::updateThing` (mailbox.cpp:54-56). */
    override fun updateThing(thing: Thing?, itemId: Int, count: Int) {
        // Empty in Canary.
    }

    /** `Mailbox::replaceThing` (mailbox.cpp:58-60). */
    override fun replaceThing(index: Int, thing: Thing?) {
        // Empty in Canary.
    }

    /** `Mailbox::removeThing` (mailbox.cpp:62-64). */
    override fun removeThing(thing: Thing?, count: Int) {
        // Empty in Canary.
    }

    /** `Mailbox::postAddNotification` (mailbox.cpp:66-68). */
    override fun postAddNotification(thing: Thing?, oldParent: Cylinder?, index: Int, link: CylinderLink_t) {
        getParent()?.postAddNotification(thing, oldParent, index, CylinderLink_t.LINK_PARENT)
    }

    /** `Mailbox::postRemoveNotification` (mailbox.cpp:70-72). */
    override fun postRemoveNotification(thing: Thing?, newParent: Cylinder?, index: Int, link: CylinderLink_t) {
        getParent()?.postRemoveNotification(thing, newParent, index, CylinderLink_t.LINK_PARENT)
    }

    /** `Mailbox::sendItem` (mailbox.cpp:74-119). */
    private fun sendItem(item: Item): Boolean {
        val receiver = getReceiver(item) ?: return false

        /** No need to continue if its still empty */
        if (receiver.isEmpty()) {
            return false
        }

        val itemContainer = item.getContainer()
        if (itemContainer != null && item.getTile() != null) {
            router?.autoCloseContainers(itemContainer)
        }

        val player = router?.getPlayerByName(receiver, true) ?: return false
        var writer = item.getAttribute(ItemAttribute.WRITER) as? String ?: ""
        var date = getTimeNow()
        var text = ""
        if (item.id == ITEM_LETTER && writer.isNotEmpty()) {
            date = (item.getAttribute(ItemAttribute.DATE) as? Number)?.toLong() ?: 0L
            text = item.getAttribute(ItemAttribute.TEXT) as? String ?: ""
        }
        val moveItem = router ?: return false
        val playerInbox = player.getInbox()
        val itemParent = item.getParent()
        val moved = moveItem.internalMoveItem(
            itemParent,
            playerInbox,
            INDEX_WHEREEVER,
            item,
            item.getItemCount(),
            null,
            CylinderFlags.FLAG_NOLIMIT,
        )
        if (moved == ReturnValue.NOERROR) {
            val newItem = moveItem.transformItem(item, item.id + 1)
            if (newItem != null && newItem.id == ITEM_LETTER_STAMPED && writer.isNotEmpty()) {
                newItem.setAttribute(ItemAttribute.WRITER, writer)
                newItem.setAttribute(ItemAttribute.DATE, date)
                newItem.setAttribute(ItemAttribute.TEXT, text)
            }
            if (player.isOnline()) {
                player.onReceiveMail()
            } else {
                moveItem.savePlayer(player)
            }
            return true
        }
        return false
    }

    /** `Mailbox::getReceiver` (mailbox.cpp:121-140). `null` is the C++ `false`. */
    private fun getReceiver(item: Item): String? {
        val container = item.getContainer()
        if (container != null) {
            for (containerItem in container.getItemList()) {
                if (containerItem.id == ITEM_LABEL) {
                    val receiver = getReceiver(containerItem)
                    if (receiver != null) {
                        return receiver
                    }
                }
            }
            return null
        }

        val text = item.getAttribute(ItemAttribute.TEXT) as? String ?: ""
        if (text.isEmpty()) {
            return null
        }

        return text.substringBefore('\n').trim(' ')
    }

    companion object {
        /** `ITEM_PARCEL` (src/utils/utils_definitions.hpp:632). */
        const val ITEM_PARCEL = 3503

        /** `ITEM_LETTER` (src/utils/utils_definitions.hpp:634). */
        const val ITEM_LETTER = 3505

        /** `ITEM_LETTER_STAMPED` (src/utils/utils_definitions.hpp:635). */
        const val ITEM_LETTER_STAMPED = 3506

        /** `ITEM_LABEL` (src/utils/utils_definitions.hpp:636). */
        const val ITEM_LABEL = 3507

        /** `Mailbox::canSend` (mailbox.cpp:142-144). */
        internal fun canSend(item: Item): Boolean =
            !item.hasOwner() && (item.id == ITEM_PARCEL || item.id == ITEM_LETTER)
    }
}
