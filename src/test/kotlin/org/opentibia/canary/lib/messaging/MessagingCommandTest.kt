package org.opentibia.canary.lib.messaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MessagingCommandTest {

    @Test
    fun `command types expose the canary order`() {
        assertThat(CommandType.entries.map { it.name }).containsExactly("start", "load", "run", "shutdown")
    }

    @Test
    fun `command factory wraps the type in a message`() {
        val message: ICommand = command(CommandType.shutdown)

        assertThat(message.type).isEqualTo(CommandType.shutdown)
    }

    @Test
    fun `event type has no entries yet`() {
        assertThat(EventType.entries).isEmpty()
    }

    @Test
    fun `event aliases accept the dispatcher and remover types`() {
        val dispatcher = EventDispatcher()
        val remover: EventRemover = EventRemover(dispatcher)
        val listeners: EventListeners = emptyList()

        assertThat(dispatcher).isNotNull()
        assertThat(remover).isNotNull()
        assertThat(listeners).isEmpty()
    }
}
