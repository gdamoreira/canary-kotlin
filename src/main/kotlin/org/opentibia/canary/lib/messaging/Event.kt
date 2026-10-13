package org.opentibia.canary.lib.messaging

/** `enum class EventType` (src/lib/messaging/event.hpp:11-12); still empty in Canary. */
enum class EventType

typealias IEvent = Message<EventType>
typealias EventHandler = MessageHandler<EventType>
typealias EventPolicy = MessagePolicy<EventType>
typealias EventListener = IMessageListener<EventType>
typealias EventListeners = MessageListeners<EventType>
typealias EventDispatcher = MessageDispatcher<EventType>
typealias EventRemover = MessageRemover<EventType>

/** `event(EventType type)` (src/lib/messaging/event.hpp:22-24). */
fun event(type: EventType): IEvent = IEvent(type)
