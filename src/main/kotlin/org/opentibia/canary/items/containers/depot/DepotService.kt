package org.opentibia.canary.items.containers.depot

import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item

class DepotService {
    // Key: (playerId, townId) -> Locker Container
    private val lockers = mutableMapOf<Pair<Long, Int>, Container>()
    // Key: playerId -> Inbox Container
    private val inboxes = mutableMapOf<Long, Container>()

    fun getLocker(playerId: Long, townId: Int): Container {
        return lockers.getOrPut(playerId to townId) {
            Container(id = 2594, capacity = 30) // 2594 = Depot Locker
        }
    }

    fun getInbox(playerId: Long): Container {
        return inboxes.getOrPut(playerId) {
            Container(id = 14404, capacity = 30) // 14404 = Mailbox Inbox
        }
    }

    fun depositItem(playerId: Long, townId: Int, item: Item): Boolean {
        val locker = getLocker(playerId, townId)
        return locker.addItem(item)
    }

    fun sendMail(fromPlayerId: Long, recipientPlayerId: Long, recipientTownId: Int, parcel: Item): Boolean {
        val inbox = getInbox(recipientPlayerId)
        return inbox.addItem(parcel)
    }
}
