package org.opentibia.canary.items.containers.depot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item

class DepotMailTest {

    @Test
    fun `stores items in player depot locker across towns`() {
        val depotService = DepotService()
        val playerId = 100L

        val sword = Item(id = 2376) // Sword
        val success = depotService.depositItem(playerId, townId = 1, item = sword)
        assertThat(success).isTrue()

        val locker = depotService.getLocker(playerId, townId = 1)
        assertThat(locker.items).hasSize(1)
        assertThat(locker.items.first().id).isEqualTo(2376)
    }

    @Test
    fun `delivers parcel through mailbox into recipient inbox`() {
        val depotService = DepotService()
        val senderId = 1L
        val recipientId = 2L

        val parcel = Item(id = 2595) // Parcel
        val delivered = depotService.sendMail(
            fromPlayerId = senderId,
            recipientPlayerId = recipientId,
            recipientTownId = 1,
            parcel = parcel
        )

        assertThat(delivered).isTrue()

        val inbox = depotService.getInbox(recipientId)
        assertThat(inbox.items).hasSize(1)
        assertThat(inbox.items.first().id).isEqualTo(2595)
    }
}
