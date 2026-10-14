package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.modal.ModalWindow
import org.opentibia.canary.game.modal.ModalWindowManager
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam behind the `ModalWindow` userdata class. Mirrors the
 * `Player::hasModalWindowOpen` + `Player::sendModalWindow` pair of
 * `ModalWindowFunctions::luaModalWindowSendToPlayer`
 * (src/lua/functions/core/game/modal_window_functions.cpp:234-252).
 */
interface ModalWindowOperations {
    /** Sends [window] to [player] unless the window is already open; returns the C++ `true`. */
    fun sendToPlayer(player: Player, window: ModalWindow): Boolean

    fun reportError(message: String) {}
}

/** Documented no-op used when no modal-window manager is wired. */
object UnwiredModalWindowOperations : ModalWindowOperations {
    override fun sendToPlayer(player: Player, window: ModalWindow): Boolean = false
}

/** [ModalWindowOperations] over the ported [ModalWindowManager]. */
class WorldModalWindowOperations(private val manager: ModalWindowManager) : ModalWindowOperations {
    override fun sendToPlayer(player: Player, window: ModalWindow): Boolean {
        if (!manager.hasModalWindowOpen(player, window.id)) {
            manager.sendModalWindow(player, window)
        }
        return true
    }
}

/**
 * Registers the `ModalWindow` userdata class with every method of
 * `ModalWindowFunctions::init` (modal_window_functions.cpp:16-43):
 *
 *   ModalWindow(id, title, message)
 *   modalWindow:getId() / getTitle() / getMessage()
 *   modalWindow:setTitle(text) / setMessage(text)
 *   modalWindow:getButtonCount() / getChoiceCount()
 *   modalWindow:addButton(id, text) / addChoice(id, text)
 *   modalWindow:getDefaultEnterButton() / setDefaultEnterButton(buttonId)
 *   modalWindow:getDefaultEscapeButton() / setDefaultEscapeButton(buttonId)
 *   modalWindow:hasPriority() / setPriority(priority)
 *   modalWindow:sendToPlayer(player)
 *
 * Every missing-userdata branch reproduces the C++ `lua_pushnil`, and the
 * `addButton`/`addChoice` pair ordering is `(text, id)` like the C++
 * `std::list<std::pair<std::string, uint8_t>>`.
 */
object ModalWindowFunctions {
    const val MODAL_WINDOW_TYPE = "ModalWindow"

    fun install(env: LuaEnvironment, operations: ModalWindowOperations = UnwiredModalWindowOperations) {
        env.userdata.registerClass(MODAL_WINDOW_TYPE, "", libraryFunction { args ->
            val window = ModalWindow(
                id = luaArgLong(args, 1),
                title = luaArgString(args, 2),
                message = luaArgString(args, 3),
            )
            env.userdata.wrap(MODAL_WINDOW_TYPE, window)
        })

        registerGetter(env, "getId") { LuaValue.valueOf(it.id.toDouble()) }
        registerGetter(env, "getTitle") { LuaValue.valueOf(it.title) }
        registerGetter(env, "getMessage") { LuaValue.valueOf(it.message) }
        registerGetter(env, "getButtonCount") { LuaValue.valueOf(it.buttons.size) }
        registerGetter(env, "getChoiceCount") { LuaValue.valueOf(it.choices.size) }
        registerGetter(env, "getDefaultEnterButton") { LuaValue.valueOf(it.defaultEnterButton) }
        registerGetter(env, "getDefaultEscapeButton") { LuaValue.valueOf(it.defaultEscapeButton) }
        registerGetter(env, "hasPriority") { LuaValue.valueOf(it.priority) }

        env.userdata.registerMethod(MODAL_WINDOW_TYPE, "setTitle", libraryFunction { args ->
            val window = args.arg(1).touserdata() as? ModalWindow ?: return@libraryFunction LuaValue.NIL
            window.title = luaArgString(args, 2)
            LuaValue.TRUE
        })

        env.userdata.registerMethod(MODAL_WINDOW_TYPE, "setMessage", libraryFunction { args ->
            val window = args.arg(1).touserdata() as? ModalWindow ?: return@libraryFunction LuaValue.NIL
            window.message = luaArgString(args, 2)
            LuaValue.TRUE
        })

        env.userdata.registerMethod(MODAL_WINDOW_TYPE, "addButton", libraryFunction { args ->
            val window = args.arg(1).touserdata() as? ModalWindow ?: return@libraryFunction LuaValue.NIL
            window.buttons += luaArgString(args, 3) to luaArgUint8(args, 2)
            LuaValue.TRUE
        })

        env.userdata.registerMethod(MODAL_WINDOW_TYPE, "addChoice", libraryFunction { args ->
            val window = args.arg(1).touserdata() as? ModalWindow ?: return@libraryFunction LuaValue.NIL
            window.choices += luaArgString(args, 3) to luaArgUint8(args, 2)
            LuaValue.TRUE
        })

        env.userdata.registerMethod(MODAL_WINDOW_TYPE, "setDefaultEnterButton", libraryFunction { args ->
            val window = args.arg(1).touserdata() as? ModalWindow ?: return@libraryFunction LuaValue.NIL
            window.defaultEnterButton = luaArgUint8(args, 2)
            LuaValue.TRUE
        })

        env.userdata.registerMethod(MODAL_WINDOW_TYPE, "setDefaultEscapeButton", libraryFunction { args ->
            val window = args.arg(1).touserdata() as? ModalWindow ?: return@libraryFunction LuaValue.NIL
            window.defaultEscapeButton = luaArgUint8(args, 2)
            LuaValue.TRUE
        })

        env.userdata.registerMethod(MODAL_WINDOW_TYPE, "setPriority", libraryFunction { args ->
            val window = args.arg(1).touserdata() as? ModalWindow ?: return@libraryFunction LuaValue.NIL
            window.priority = luaArgBoolean(args, 2, false)
            LuaValue.TRUE
        })

        env.userdata.registerMethod(MODAL_WINDOW_TYPE, "sendToPlayer", libraryFunction { args ->
            val player = args.arg(2).touserdata() as? Player
            if (player == null) {
                operations.reportError("Player not found")
                return@libraryFunction args.arg(1)
            }
            val window = args.arg(1).touserdata() as? ModalWindow
                ?: return@libraryFunction LuaValue.NIL
            LuaValue.valueOf(operations.sendToPlayer(player, window))
        })
    }

    private fun registerGetter(env: LuaEnvironment, name: String, getter: (ModalWindow) -> LuaValue) {
        env.userdata.registerMethod(MODAL_WINDOW_TYPE, name, libraryFunction { args ->
            val window = args.arg(1).touserdata() as? ModalWindow ?: return@libraryFunction LuaValue.NIL
            getter(window)
        })
    }
}
