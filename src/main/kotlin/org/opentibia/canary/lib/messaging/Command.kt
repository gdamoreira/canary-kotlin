package org.opentibia.canary.lib.messaging

/** `enum class CommandType` (src/lib/messaging/command.hpp:11-16). */
enum class CommandType {
    start,
    load,
    run,
    shutdown,
}

typealias ICommand = Message<CommandType>
typealias CommandHandler = MessageHandler<CommandType>
typealias CommandPolicy = MessagePolicy<CommandType>
typealias CommandListener = IMessageListener<CommandType>
typealias CommandListeners = MessageListeners<CommandType>
typealias CommandDispatcher = MessageDispatcher<CommandType>
typealias CommandRemover = MessageRemover<CommandType>

/** `command(CommandType type)` (src/lib/messaging/command.hpp:26-28). */
fun command(type: CommandType): ICommand = ICommand(type)
