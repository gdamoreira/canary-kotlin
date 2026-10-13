package org.opentibia.canary.lib.messaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class MessagingTest {

    @Test
    fun `dispatches messages to listeners appended for the type in order`() {
        val dispatcher = MessageDispatcher<CommandType>()
        val received = mutableListOf<String>()

        dispatcher.appendListener(CommandType.start) { received += "first" }
        dispatcher.appendListener(CommandType.start) { received += "second" }
        dispatcher.appendListener(CommandType.run) { received += "run" }

        dispatcher.dispatch(command(CommandType.start))

        assertThat(received).containsExactly("first", "second")
    }

    @Test
    fun `listeners receive the dispatched message`() {
        val dispatcher = CommandDispatcher()
        var seen: ICommand? = null

        dispatcher.appendListener(CommandType.load) { seen = it }

        dispatcher.dispatch(command(CommandType.load))

        assertThat(seen?.type).isEqualTo(CommandType.load)
    }

    @Test
    fun `prepend listener inserts before the existing listeners`() {
        val dispatcher = MessageDispatcher<CommandType>()
        val order = mutableListOf<String>()

        dispatcher.appendListener(CommandType.run) { order += "appended" }
        dispatcher.prependListener(CommandType.run) { order += "prepended" }

        dispatcher.dispatch(command(CommandType.run))

        assertThat(order).containsExactly("prepended", "appended")
    }

    @Test
    fun `insert listener inserts before the matching listener`() {
        val dispatcher = MessageDispatcher<CommandType>()
        val order = mutableListOf<String>()
        val second = { _: ICommand -> order += "second" }

        dispatcher.appendListener(CommandType.run) { order += "first" }
        dispatcher.appendListener(CommandType.run, second)
        dispatcher.insertListener(CommandType.run, { order += "inserted" }, second)

        dispatcher.dispatch(command(CommandType.run))

        assertThat(order).containsExactly("first", "inserted", "second")
    }

    @Test
    fun `insert listener falls back to the end when the anchor is missing`() {
        val dispatcher = MessageDispatcher<CommandType>()
        val order = mutableListOf<String>()
        val missing = { _: ICommand -> order += "missing" }

        dispatcher.appendListener(CommandType.run) { order += "first" }
        dispatcher.insertListener(CommandType.run, { order += "tail" }, missing)

        dispatcher.dispatch(command(CommandType.run))

        assertThat(order).containsExactly("first", "tail")
    }

    @Test
    fun `remove listener stops delivery`() {
        val dispatcher = MessageDispatcher<CommandType>()
        val counter = AtomicInteger()
        val listener: CommandHandler = { counter.incrementAndGet() }

        dispatcher.appendListener(CommandType.run, listener)
        dispatcher.dispatch(command(CommandType.run))
        assertThat(counter.get()).isEqualTo(1)

        dispatcher.removeListener(CommandType.run, listener)
        dispatcher.dispatch(command(CommandType.run))
        assertThat(counter.get()).isEqualTo(1)
    }

    @Test
    fun `has listener reflects the exact registration`() {
        val dispatcher = MessageDispatcher<CommandType>()
        val listener = { _: ICommand -> }

        assertThat(dispatcher.hasAnyListener(CommandType.start)).isFalse()
        assertThat(dispatcher.hasListener(CommandType.start, listener)).isFalse()

        dispatcher.appendListener(CommandType.start, listener)

        assertThat(dispatcher.hasAnyListener(CommandType.start)).isTrue()
        assertThat(dispatcher.hasListener(CommandType.start, listener)).isTrue()
        assertThat(dispatcher.hasListener(CommandType.start, { _: ICommand -> })).isFalse()
    }

    @Test
    fun `scoped remover removes tracked listeners on close and reset`() {
        val dispatcher = MessageDispatcher<CommandType>()
        val counter = AtomicInteger()
        val remover = CommandRemover(dispatcher)

        remover.appendListener(CommandType.run) { counter.incrementAndGet() }
        dispatcher.dispatch(command(CommandType.run))
        assertThat(counter.get()).isEqualTo(1)

        remover.close()
        dispatcher.dispatch(command(CommandType.run))
        assertThat(counter.get()).isEqualTo(1)

        remover.appendListener(CommandType.run) { counter.incrementAndGet() }
        dispatcher.dispatch(command(CommandType.run))
        assertThat(counter.get()).isEqualTo(2)

        remover.reset()
        dispatcher.dispatch(command(CommandType.run))
        assertThat(counter.get()).isEqualTo(2)
    }

    @Test
    fun `message policy extracts the event from the message`() {
        assertThat(MessagePolicy.getEvent(command(CommandType.run))).isEqualTo(CommandType.run)
        assertThat(MessagePolicy.getEvent(command(CommandType.load))).isEqualTo(CommandType.load)
    }

    @Test
    fun `scoped remover remove listener untracks it`() {
        val dispatcher = MessageDispatcher<CommandType>()
        val counter = AtomicInteger()
        val listener: CommandHandler = { counter.incrementAndGet() }
        val remover = CommandRemover(dispatcher)

        remover.appendListener(CommandType.run, listener)
        remover.removeListener(CommandType.run, listener)
        dispatcher.dispatch(command(CommandType.run))

        assertThat(counter.get()).isZero()
        remover.close()
        assertThat(dispatcher.hasAnyListener(CommandType.run)).isFalse()
    }

    @Test
    fun `scoped remover moves tracked listeners to the new dispatcher`() {
        val first = MessageDispatcher<CommandType>()
        val second = MessageDispatcher<CommandType>()
        val counter = AtomicInteger()
        val remover = CommandRemover(first)

        remover.appendListener(CommandType.run) { counter.incrementAndGet() }
        remover.setDispatcher(second)

        first.dispatch(command(CommandType.run))
        second.dispatch(command(CommandType.run))
        assertThat(counter.get()).isZero()

        remover.appendListener(CommandType.run) { counter.incrementAndGet() }
        second.dispatch(command(CommandType.run))
        assertThat(counter.get()).isEqualTo(1)

        remover.close()
    }

    @Test
    fun `listener interface sets up dispatcher callbacks`() {
        val dispatcher = CommandDispatcher()
        val listener = object : CommandListener {
            val received = mutableListOf<CommandType>()

            override fun setupListeners(dispatcher: CommandDispatcher) {
                dispatcher.appendListener(CommandType.shutdown) { received += it.type }
            }
        }

        listener.setupListeners(dispatcher)
        val listeners: CommandListeners = listOf(listener)
        dispatcher.dispatch(command(CommandType.shutdown))

        assertThat(listeners).hasSize(1)
        assertThat(listener.received).containsExactly(CommandType.shutdown)
    }
}
