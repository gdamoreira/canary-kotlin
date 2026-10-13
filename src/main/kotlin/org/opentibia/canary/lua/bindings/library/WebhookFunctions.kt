package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.network.webhook.Webhook

/**
 * Engine seam behind the global `Webhook` table. Mirrors the three `g_webhook()`
 * calls of `WebhookFunctions::luaWebhookSendMessage`
 * (src/lua/functions/core/libs/../network/webhook_functions.cpp:20-37).
 *
 * The two overloads follow Canary's `Webhook::sendMessage(message, url = "")` and
 * `Webhook::sendMessage(title, message, color, url = "")` declarations.
 */
interface WebhookOperations {
    /** `Webhook::sendMessage(message, url)` (content-only payload). */
    fun sendMessage(message: String, url: String = "")

    /** `Webhook::sendMessage(title, message, color, url)` (embed payload). */
    fun sendMessage(title: String, message: String, color: Int, url: String)
}

/** Documented no-op used when no webhook sender is wired. */
object UnwiredWebhookOperations : WebhookOperations {
    override fun sendMessage(message: String, url: String) {}

    override fun sendMessage(title: String, message: String, color: Int, url: String) {}
}

/** [WebhookOperations] over the ported `Webhook` queue. */
class WorldWebhookOperations(private val webhook: Webhook) : WebhookOperations {
    override fun sendMessage(message: String, url: String) = webhook.sendMessage(message, url)

    override fun sendMessage(title: String, message: String, color: Int, url: String) =
        webhook.sendMessage(title, message, color, url)
}

/**
 * Registers the global `Webhook` table with the single method of
 * `WebhookFunctions::init` (network/webhook_functions.cpp:15-18):
 *
 *   Webhook.sendMessage(title, message, color, url = "WEBHOOK_DISCORD_URL")
 *   Webhook.sendMessage(message, url = "WEBHOOK_DISCORD_URL")
 *
 * The dispatch reproduces `url = Lua::getString(L, -1)` and the `url == title` /
 * `url == message` comparisons that select which `g_webhook()` overload runs.
 */
object WebhookFunctions {
    const val WEBHOOK_GLOBAL = "Webhook"

    fun install(env: LuaEnvironment, operations: WebhookOperations = UnwiredWebhookOperations) {
        val webhook = (env.globals.get(WEBHOOK_GLOBAL) as? LuaTable)
            ?: LuaTable().also { env.globals.set(WEBHOOK_GLOBAL, it) }

        webhook.set("sendMessage", libraryFunction { args ->
            val title = luaArgString(args, 1)
            val message = luaArgString(args, 2)
            val color = luaArgInt(args, 3, 0)
            val url = luaArgString(args, args.narg())
            when {
                url == title -> operations.sendMessage(title)
                url == message -> operations.sendMessage(title, url)
                else -> operations.sendMessage(title, message, color, url)
            }
            LuaValue.NIL
        })
    }
}
