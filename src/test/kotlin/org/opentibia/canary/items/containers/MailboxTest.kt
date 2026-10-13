package org.opentibia.canary.items.containers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for `Mailbox` (src/items/containers/mailbox/mailbox.cpp:18-144,
 * mailbox.hpp:15-50). The `g_game()`/`SaveManager`/spectator calls are observed
 * through the [MailboxRouter] seam.
 */
class MailboxTest {

    private class FakeRecipient(
        val inboxContainer: Container = Container(Inbox.ITEM_INBOX, capacity = 30),
        var online: Boolean = true,
    ) : MailboxRecipient {
        var receivedMail = 0
        override fun getInbox(): Container = inboxContainer
        override fun isOnline(): Boolean = online
        override fun onReceiveMail() {
            receivedMail++
        }
    }

    private class FakeRouter : MailboxRouter {
        var player: MailboxRecipient? = null
        var capturedName: String? = null
        var capturedAllowOffline: Boolean? = null
        var moveResult: ReturnValue = ReturnValue.NOERROR
        var transformResult: ((Item, Int) -> Item?)? = null
        var transformedItem: Item? = null
        var transformedNewId: Int? = null
        var movedFrom: Cylinder? = null
        var movedTo: Cylinder? = null
        var movedItem: Item? = null
        var movedIndex: Int? = null
        var movedCount: Int? = null
        var movedFlags: Int? = null
        var savedPlayer: MailboxRecipient? = null
        var autoClosedContainer: Container? = null

        override fun getPlayerByName(name: String, allowOffline: Boolean): MailboxRecipient? {
            capturedName = name
            capturedAllowOffline = allowOffline
            return player
        }

        override fun internalMoveItem(
            from: Cylinder?,
            to: Cylinder,
            index: Int,
            item: Item,
            count: Int,
            actor: Creature?,
            flags: Int,
        ): ReturnValue {
            movedFrom = from
            movedTo = to
            movedItem = item
            movedIndex = index
            movedCount = count
            movedFlags = flags
            if (moveResult == ReturnValue.NOERROR) {
                to.internalAddThing(item)
            }
            return moveResult
        }

        override fun transformItem(item: Item, newId: Int): Item? {
            transformedNewId = newId
            val result = transformResult?.invoke(item, newId)
            transformedItem = result
            return result
        }

        override fun savePlayer(player: MailboxRecipient) {
            savedPlayer = player
        }

        override fun autoCloseContainers(container: Container) {
            autoClosedContainer = container
        }
    }

    private fun parcel(): Container = Container(
        Mailbox.ITEM_PARCEL,
        capacity = 8,
        type = ItemType(Mailbox.ITEM_PARCEL, "Parcel", isContainer = true, capacity = 8),
    )

    private fun label(text: String): Item = Item(Mailbox.ITEM_LABEL).apply {
        setAttribute(ItemAttribute.TEXT, text)
    }

    @Test
    fun `queryAdd only accepts parcels and letters without an owner`() {
        // Mailbox::queryAdd (mailbox.cpp:18-24) over Mailbox::canSend (:142-144).
        val mailbox = Mailbox(3803)
        val ownedParcel = parcel().apply { setAttribute(ItemAttribute.OWNER, 9L) }

        assertThat(mailbox.queryAdd(0, parcel(), 1, 0)).isEqualTo(ReturnValue.NOERROR)
        assertThat(mailbox.queryAdd(0, Item(Mailbox.ITEM_LETTER), 1, 0)).isEqualTo(ReturnValue.NOERROR)
        assertThat(mailbox.queryAdd(0, Item(2376), 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(mailbox.queryAdd(0, ownedParcel, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(Mailbox.canSend(ownedParcel)).isFalse()
    }

    @Test
    fun `queryMaxCount returns at least one and queryRemove is never possible`() {
        // Mailbox::queryMaxCount/queryRemove (mailbox.cpp:26-33).
        val mailbox = Mailbox(3803)

        assertThat(mailbox.queryMaxCount(0, parcel(), 5, 0)).isEqualTo(5)
        assertThat(mailbox.queryMaxCount(0, parcel(), 0, 0)).isEqualTo(1)
        assertThat(mailbox.queryRemove(parcel(), 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(mailbox.queryDestination(0, parcel(), null, 0)).isSameAs(mailbox)
        assertThat(mailbox.getMailbox()).isSameAs(mailbox)
    }

    @Test
    fun `addThing routes a label receiver into the recipient inbox`() {
        // Mailbox::addThing (mailbox.cpp:43-52), getReceiver (:121-140) and
        // sendItem (:74-119).
        val mailbox = Mailbox(3803)
        val router = FakeRouter()
        val recipient = FakeRecipient()
        router.player = recipient
        router.transformResult = { _, newId -> Item(newId) }
        mailbox.router = router

        val parcel = parcel()
        parcel.addItem(label("  Bob \nignored second line"))
        val parentBefore = parcel.getParent()

        mailbox.addThing(parcel)

        assertThat(router.capturedName).isEqualTo("Bob")
        assertThat(router.capturedAllowOffline).isTrue()
        assertThat(router.movedTo).isSameAs(recipient.inboxContainer)
        assertThat(router.movedFrom).isSameAs(parentBefore)
        assertThat(router.movedIndex).isEqualTo(INDEX_WHEREEVER)
        assertThat(router.movedCount).isEqualTo(1)
        assertThat(router.movedFlags).isEqualTo(CylinderFlags.FLAG_NOLIMIT)
        assertThat(recipient.inboxContainer.items).containsExactly(parcel)
        assertThat(recipient.receivedMail).isEqualTo(1)
        assertThat(router.savedPlayer).isNull()
    }

    @Test
    fun `addThing stamps a letter and preserves writer date and text`() {
        // Mailbox::sendItem (mailbox.cpp:95-109): `transformItem(item, id + 1)`
        // and the writer/date/text copy onto `ITEM_LETTER_STAMPED`.
        val mailbox = Mailbox(3803)
        val router = FakeRouter()
        val recipient = FakeRecipient(online = false)
        router.player = recipient
        router.transformResult = { _, newId -> Item(newId) }
        mailbox.router = router

        val letter = Item(Mailbox.ITEM_LETTER).apply {
            setAttribute(ItemAttribute.WRITER, "Alice")
            setAttribute(ItemAttribute.DATE, 123L)
            setAttribute(ItemAttribute.TEXT, "hello there")
        }

        mailbox.addThing(letter)

        val stamped = router.transformedItem ?: error("transformItem was not called")
        assertThat(router.transformedNewId).isEqualTo(Mailbox.ITEM_LETTER + 1)
        assertThat(stamped.id).isEqualTo(Mailbox.ITEM_LETTER_STAMPED)
        assertThat(stamped.getAttribute(ItemAttribute.WRITER)).isEqualTo("Alice")
        assertThat(stamped.getAttribute(ItemAttribute.DATE)).isEqualTo(123L)
        assertThat(stamped.getAttribute(ItemAttribute.TEXT)).isEqualTo("hello there")
        assertThat(recipient.receivedMail).isEqualTo(0)
        assertThat(router.savedPlayer).isSameAs(recipient)
    }

    @Test
    fun `getReceiver only inspects direct label children`() {
        // getReceiver (mailbox.cpp:121-130) recurses only into `ITEM_LABEL`
        // children, so a label hidden in a sub-container is not a receiver.
        val mailbox = Mailbox(3803)
        val router = FakeRouter()
        val recipient = FakeRecipient()
        router.player = recipient
        router.transformResult = { _, newId -> Item(newId) }
        mailbox.router = router

        val inner = Container(1987, capacity = 8, type = ItemType(1987, "Bag", isContainer = true))
        inner.addItem(label("Carol"))
        val parcel = parcel()
        parcel.addItem(inner)

        mailbox.addThing(parcel)

        assertThat(router.capturedName).isNull()
        assertThat(router.movedItem).isNull()
    }

    @Test
    fun `addThing ignores non sendable items`() {
        val mailbox = Mailbox(3803)
        val router = FakeRouter()
        router.player = FakeRecipient()
        mailbox.router = router

        mailbox.addThing(Item(2376))

        assertThat(router.capturedName).isNull()
        assertThat(router.movedItem).isNull()
    }

    @Test
    fun `sendItem fails without a receiver text`() {
        // mailbox.cpp:76-83.
        val mailbox = Mailbox(3803)
        val router = FakeRouter()
        router.player = FakeRecipient()
        mailbox.router = router

        mailbox.addThing(parcel())

        assertThat(router.capturedName).isNull()
        assertThat(router.movedItem).isNull()
    }

    @Test
    fun `sendItem fails when the recipient player is unknown`() {
        val mailbox = Mailbox(3803)
        val router = FakeRouter()
        router.player = null
        mailbox.router = router
        val parcel = parcel()
        parcel.addItem(label("Ghost"))

        mailbox.addThing(parcel)

        assertThat(router.capturedName).isEqualTo("Ghost")
        assertThat(router.movedItem).isNull()
    }

    @Test
    fun `sendItem stops when the move fails`() {
        val mailbox = Mailbox(3803)
        val router = FakeRouter()
        val recipient = FakeRecipient()
        router.player = recipient
        router.moveResult = ReturnValue.NOTPOSSIBLE
        router.transformResult = { _, newId -> Item(newId) }
        mailbox.router = router
        val parcel = parcel()
        parcel.addItem(label("Dana"))

        mailbox.addThing(parcel)

        assertThat(recipient.inboxContainer.items).isEmpty()
        assertThat(recipient.receivedMail).isEqualTo(0)
        assertThat(router.savedPlayer).isNull()
    }

    @Test
    fun `sendItem reads the receiver from a letter text`() {
        // getReceiver (mailbox.cpp:132-139): a non-container item's TEXT first line.
        val mailbox = Mailbox(3803)
        val router = FakeRouter()
        router.player = FakeRecipient()
        router.transformResult = { _, newId -> Item(newId) }
        mailbox.router = router

        mailbox.addThing(Item(Mailbox.ITEM_LETTER).apply { setAttribute(ItemAttribute.TEXT, "Erin") })

        assertThat(router.capturedName).isEqualTo("Erin")
    }

    @Test
    fun `sendItem closes open spectators containers when the item has a tile`() {
        // mailbox.cpp:85-89: `item->getContainer() && item->getTile()`.
        val mailbox = Mailbox(3803)
        val router = FakeRouter()
        router.player = FakeRecipient()
        router.transformResult = { _, newId -> Item(newId) }
        mailbox.router = router

        val tile = Tile(Position(100, 100, 7))
        val parcel = parcel()
        parcel.addItem(label("Fay"))
        parcel.setParent(tile)

        mailbox.addThing(parcel)

        assertThat(router.autoClosedContainer).isSameAs(parcel)
    }
}
