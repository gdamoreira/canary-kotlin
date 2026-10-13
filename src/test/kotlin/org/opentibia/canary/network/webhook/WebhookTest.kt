package org.opentibia.canary.network.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.scheduling.GameDispatcher
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Covers the payload fields emitted by `Webhook::getPayload` and the queue
 * semantics of `Webhook::sendWebhook` (retain on error, pop on success).
 *
 * C++ sources: src/server/network/webhook/webhook.hpp/.cpp
 */
class WebhookTest {

    private val mapper = ObjectMapper()
    private val clock = Clock.fixed(Instant.parse("2025-12-31T23:59:58Z"), ZoneOffset.UTC)

    private class RecordingSender(var responseCode: Int = 204) : WebhookSender {
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun sendRequest(url: String, payload: String): WebhookResponse {
            calls.add(url to payload)
            return WebhookResponse(responseCode, """{"ok":true}""")
        }
    }

    private fun webhook(
        sender: WebhookSender,
        url: String = DEFAULT_URL,
        sendFooter: Boolean = false,
        delayMs: Long = Webhook.DEFAULT_DELAY_MS,
        dispatcher: GameDispatcher? = null,
    ) = Webhook(
        sender = sender,
        serverName = "Canary",
        webhookUrl = url,
        sendFooter = sendFooter,
        delayMs = delayMs,
        dispatcher = dispatcher,
        clock = clock,
    )

    @Test
    fun `embed payload mirrors canary title description footer and color fields`() = runBlocking<Unit> {
        val sender = RecordingSender()
        val webhook = webhook(sender, sendFooter = true)

        webhook.sendMessage("Server started", "World is ready", 0x00FF00, "https://discord.example/custom")
        webhook.sendWebhook()

        assertThat(sender.calls).hasSize(1)
        val (url, payload) = sender.calls.single()
        assertThat(url).isEqualTo("https://discord.example/custom")

        val root = mapper.readTree(payload)
        val embed = root.path("embeds").path(0)
        assertThat(embed.path("title").asText()).isEqualTo("Server started")
        assertThat(embed.path("description").asText()).isEqualTo("World is ready")
        assertThat(embed.path("color").asInt()).isEqualTo(0x00FF00)
        assertThat(embed.path("footer").path("text").asText()).isEqualTo("Canary | 31/12/2025 23:59:58")
        assertThat(root.has("content")).isFalse()
    }

    @Test
    fun `embed payload omits footer and color when disabled`() = runBlocking<Unit> {
        val sender = RecordingSender()
        val webhook = webhook(sender, sendFooter = false)

        webhook.sendMessage("Server started", "World is ready", -1)
        webhook.sendWebhook()

        val embed = mapper.readTree(sender.calls.single().second).path("embeds").path(0)
        assertThat(embed.path("title").asText()).isEqualTo("Server started")
        assertThat(embed.path("description").asText()).isEqualTo("World is ready")
        assertThat(embed.has("footer")).isFalse()
        assertThat(embed.has("color")).isFalse()
    }

    @Test
    fun `non embed message sends content field`() = runBlocking<Unit> {
        val sender = RecordingSender()
        val webhook = webhook(sender)

        webhook.sendMessage("Plain message")
        webhook.sendWebhook()

        val root = mapper.readTree(sender.calls.single().second)
        assertThat(root.path("content").asText()).isEqualTo("Plain message")
        assertThat(root.has("embeds")).isFalse()
    }

    @Test
    fun `send message drops payloads without url or content`() = runBlocking<Unit> {
        val sender = RecordingSender()
        val withoutUrl = webhook(sender, url = "")
        val configured = webhook(sender)

        withoutUrl.sendMessage("title", "message", 0)
        withoutUrl.sendMessage("")
        withoutUrl.sendMessage("title", "message", 0, "https://discord.example/explicit")
        configured.sendMessage("")
        withoutUrl.sendWebhook()

        assertThat(sender.calls).hasSize(1)
        assertThat(sender.calls.single().first).isEqualTo("https://discord.example/explicit")
    }

    @Test
    fun `queued payloads are delivered fifo and popped after success`() = runBlocking<Unit> {
        val sender = RecordingSender()
        val webhook = webhook(sender)

        webhook.sendPayload("payload-a", DEFAULT_URL)
        webhook.sendPayload("payload-b", DEFAULT_URL)

        webhook.sendWebhook()
        webhook.sendWebhook()
        webhook.sendWebhook()

        assertThat(sender.calls).containsExactly(
            DEFAULT_URL to "payload-a",
            DEFAULT_URL to "payload-b",
        )
    }

    @Test
    fun `rate limited response keeps payload queued`() = runBlocking<Unit> {
        val sender = RecordingSender(responseCode = 429)
        val webhook = webhook(sender)

        webhook.sendPayload("payload", DEFAULT_URL)
        webhook.sendWebhook()
        assertThat(sender.calls).hasSize(1)

        sender.responseCode = 204
        webhook.sendWebhook()
        assertThat(sender.calls).hasSize(2)
        assertThat(sender.calls[0].second).isEqualTo(sender.calls[1].second)
    }

    @Test
    fun `transport failure keeps payload queued`() = runBlocking<Unit> {
        val sender = RecordingSender(responseCode = -1)
        val webhook = webhook(sender)

        webhook.sendPayload("payload", DEFAULT_URL)
        webhook.sendWebhook()
        assertThat(sender.calls).hasSize(1)

        sender.responseCode = 200
        webhook.sendWebhook()
        assertThat(sender.calls).hasSize(2)
    }

    @Test
    fun `server error keeps payload queued like canary`() = runBlocking<Unit> {
        val sender = RecordingSender(responseCode = 500)
        val webhook = webhook(sender)

        webhook.sendPayload("payload", DEFAULT_URL)
        webhook.sendWebhook()
        webhook.sendWebhook()

        assertThat(sender.calls).hasSize(2)
    }

    @Test
    fun `default delay constant matches canary`() {
        assertThat(Webhook.DEFAULT_DELAY_MS).isEqualTo(1000L)
    }

    @Test
    fun `non embed titled message falls back to the message body`() = runBlocking<Unit> {
        val sender = RecordingSender()
        val webhook = webhook(sender)

        webhook.sendMessage("Server started", "World is ready", 0, embed = false)
        webhook.sendWebhook()

        val root = mapper.readTree(sender.calls.single().second)
        assertThat(root.path("content").asText()).isEqualTo("World is ready")
        assertThat(root.has("embeds")).isFalse()
    }

    @Test
    fun `run dispatches the queued payload immediately and re-arms the loop`() {
        val sender = RecordingSender()
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val webhook = webhook(sender, delayMs = 60_000, dispatcher = dispatcher)
            webhook.sendPayload("payload", DEFAULT_URL)
            webhook.run()

            val deadline = System.currentTimeMillis() + 2_000
            while (sender.calls.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
            assertThat(sender.calls).hasSize(1)
        } finally {
            dispatcher.shutdown()
        }
    }

    @Test
    fun `run flushes queued payload on maintenance lane`() {
        val sender = RecordingSender()
        val dispatcher = GameDispatcher()
        dispatcher.start()
        try {
            val webhook = webhook(sender, delayMs = 10, dispatcher = dispatcher)
            webhook.sendPayload("payload", DEFAULT_URL)
            webhook.run()

            val deadline = System.currentTimeMillis() + 5_000
            while (sender.calls.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
            assertThat(sender.calls).hasSize(1)
        } finally {
            dispatcher.shutdown()
        }
    }

    private companion object {
        const val DEFAULT_URL = "https://discord.example/webhook"
    }
}
