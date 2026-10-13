package org.opentibia.canary.lib.messaging

/** `struct Message` (src/lib/messaging/message.hpp:14-17). */
class Message<T>(val type: T)

/** `using MessageHandler = void(const Message<T> &)` (src/lib/messaging/message.hpp:19-20). */
typealias MessageHandler<T> = (Message<T>) -> Unit

/** `struct MessagePolicy` with `getEvent` (src/lib/messaging/message.hpp:22-27). */
class MessagePolicy<T> {
    companion object {
        fun <T> getEvent(message: Message<T>): T = message.type
    }
}

/**
 * `MessageDispatcher` (src/lib/messaging/message.hpp:29-30), a port of the
 * eventpp `EventDispatcher` surface used by Canary: append, prepend, insert,
 * remove, has-listener queries and synchronous dispatch. Dispatch snapshots the
 * listener list, so listeners may mutate the registry during dispatch (eventpp
 * guarantees new listeners are not triggered in the same dispatch).
 */
class MessageDispatcher<T> {

    private val listeners = linkedMapOf<T, MutableList<MessageHandler<T>>>()

    fun appendListener(type: T, listener: MessageHandler<T>) {
        listeners.getOrPut(type) { mutableListOf() } += listener
    }

    fun prependListener(type: T, listener: MessageHandler<T>) {
        listeners.getOrPut(type) { mutableListOf() }.add(0, listener)
    }

    /** Inserts before `before`; falls back to the end when `before` is not found (eventpp). */
    fun insertListener(type: T, listener: MessageHandler<T>, before: MessageHandler<T>) {
        val entries = listeners.getOrPut(type) { mutableListOf() }
        val index = entries.indexOfFirst { it == before }
        if (index == -1) {
            entries += listener
        } else {
            entries.add(index, listener)
        }
    }

    fun removeListener(type: T, listener: MessageHandler<T>) {
        listeners[type]?.remove(listener)
    }

    fun hasAnyListener(type: T): Boolean = listeners[type]?.isNotEmpty() == true

    fun hasListener(type: T, listener: MessageHandler<T>): Boolean = listeners[type]?.any { it == listener } == true

    fun dispatch(message: Message<T>) {
        val entries = listeners[message.type]?.toList() ?: return
        entries.forEach { it(message) }
    }
}

/**
 * `MessageRemover` (src/lib/messaging/message.hpp:32-33), a port of the eventpp
 * `ScopedRemover`. The C++ destructor is mapped to [close] so the remover can be
 * used with `use { }`.
 */
class MessageRemover<T>(dispatcher: MessageDispatcher<T>) : AutoCloseable {

    private var dispatcher: MessageDispatcher<T> = dispatcher
    private val tracked = mutableListOf<Pair<T, MessageHandler<T>>>()

    fun appendListener(type: T, listener: MessageHandler<T>) {
        dispatcher.appendListener(type, listener)
        tracked += type to listener
    }

    fun removeListener(type: T, listener: MessageHandler<T>) {
        dispatcher.removeListener(type, listener)
        tracked.remove(type to listener)
    }

    fun setDispatcher(dispatcher: MessageDispatcher<T>) {
        reset()
        this.dispatcher = dispatcher
    }

    /** `ScopedRemover::reset`: removes every tracked listener. */
    fun reset() {
        tracked.forEach { (type, listener) -> dispatcher.removeListener(type, listener) }
        tracked.clear()
    }

    override fun close() {
        reset()
    }
}

/** `IMessageListener` (src/lib/messaging/message.hpp:35-40). */
interface IMessageListener<T> {
    fun setupListeners(dispatcher: MessageDispatcher<T>)
}

/** `MessageListeners` (src/lib/messaging/message.hpp:42-43). */
typealias MessageListeners<T> = List<IMessageListener<T>>
