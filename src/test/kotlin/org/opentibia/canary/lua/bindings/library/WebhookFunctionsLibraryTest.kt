package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript tests for the `Webhook` table, mirroring `WebhookFunctions`
 * (src/lua/functions/core/network/webhook_functions.cpp) including the
 * `url = Lua::getString(L, -1)` overload dispatch.
 */
class WebhookFunctionsLibraryTest {

    private class RecordingWebhookOperations : WebhookOperations {
        val contentMessages = mutableListOf<Pair<String, String>>()
        val embedMessages = mutableListOf<List<Any>>()

        override fun sendMessage(message: String, url: String) {
            contentMessages += message to url
        }

        override fun sendMessage(title: String, message: String, color: Int, url: String) {
            embedMessages += listOf(title, message, color, url)
        }
    }

    private fun environment(operations: WebhookOperations): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { WebhookFunctions.install(it, operations) }

    @Test
    fun `a single argument posts the title as a content message`() {
        val operations = RecordingWebhookOperations()
        val environment = environment(operations)

        environment.loadChunk("Webhook.sendMessage('Server restarted')")

        assertThat(operations.contentMessages).containsExactly("Server restarted" to "")
        assertThat(operations.embedMessages).isEmpty()
    }

    @Test
    fun `title plus message posts the second overload with the message as url`() {
        val operations = RecordingWebhookOperations()
        val environment = environment(operations)

        environment.loadChunk("Webhook.sendMessage('Title', 'Message')")

        assertThat(operations.contentMessages).containsExactly("Title" to "Message")
    }

    @Test
    fun `a color argument dispatches the embed overload`() {
        val operations = RecordingWebhookOperations()
        val environment = environment(operations)

        environment.loadChunk("Webhook.sendMessage('Title', 'Message', 65280)")

        assertThat(operations.embedMessages).hasSize(1)
        val embed = operations.embedMessages.single()
        assertThat(embed[0]).isEqualTo("Title")
        assertThat(embed[1]).isEqualTo("Message")
        assertThat(embed[2]).isEqualTo(65280)
        assertThat(embed[3]).isEqualTo("65280")
    }

    @Test
    fun `an explicit url dispatches the embed overload with that url`() {
        val operations = RecordingWebhookOperations()
        val environment = environment(operations)

        environment.loadChunk("Webhook.sendMessage('Title', 'Message', 255, 'https://discord.test/hook')")

        assertThat(operations.embedMessages).containsExactly(
            listOf("Title", "Message", 255, "https://discord.test/hook"),
        )
    }
}
