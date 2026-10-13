package org.opentibia.canary.network.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

data class WebhookTask(
    val payload: String,
    val url: String,
)

/**
 * Result of a webhook HTTP POST. [statusCode] mirrors the integer returned by
 * Canary's `Webhook::sendRequest` (a negative value signals a transport
 * failure) and [responseBody] mirrors its `response_body` out-parameter.
 */
data class WebhookResponse(
    val statusCode: Int,
    val responseBody: String,
)

/**
 * Injected HTTP sender seam replacing Canary's curl call in
 * `Webhook::sendRequest`.
 */
interface WebhookSender {
    suspend fun sendRequest(url: String, payload: String): WebhookResponse
}

/**
 * `java.net.http` implementation of [WebhookSender] with the same request
 * contract as Canary: JSON content headers, Canary user agent and a response
 * body captured for logging. The blocking JDK call runs on [Dispatchers.IO].
 */
class HttpWebhookSender(
    private val client: HttpClient = HttpClient.newBuilder().build(),
) : WebhookSender {

    override suspend fun sendRequest(url: String, payload: String): WebhookResponse = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder(URI.create(url))
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .header("user-agent", "canary (https://github.com/opentibiabr/canary)")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            WebhookResponse(response.statusCode(), response.body())
        } catch (exception: IOException) {
            logger.error(exception) { "Failed to send webhook message; HTTP request failed" }
            WebhookResponse(-1, "")
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error(exception) { "Failed to send webhook message; HTTP request interrupted" }
            WebhookResponse(-1, "")
        } catch (exception: IllegalArgumentException) {
            logger.error(exception) { "Failed to send webhook message; invalid webhook URL" }
            WebhookResponse(-1, "")
        }
    }
}

/**
 * Kotlin port of Canary's `Webhook` (`src/server/network/webhook/webhook.hpp`
 * and `webhook.cpp`): builds Discord payloads, queues them and drains the queue
 * one task per [sendWebhook] call.
 *
 * The C++ singleton/thread-pool bootstrap is replaced by constructor injection:
 * [sender] is the HTTP seam, the config values (`SERVER_NAME`,
 * `DISCORD_WEBHOOK_URL`, `DISCORD_SEND_FOOTER`, `DISCORD_WEBHOOK_DELAY_MS`) are
 * constructor parameters and [dispatcher] drives the periodic [run] loop.
 */
class Webhook(
    private val sender: WebhookSender,
    private val serverName: String,
    private val webhookUrl: String = "",
    private val sendFooter: Boolean = false,
    private val delayMs: Long = DEFAULT_DELAY_MS,
    private val dispatcher: GameDispatcher? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val mapper = ObjectMapper()
    private val lock = Any()
    private val tasks = ArrayDeque<WebhookTask>()
    private val isSending = AtomicBoolean(false)

    fun sendPayload(payload: String, url: String) {
        synchronized(lock) {
            tasks.addLast(WebhookTask(payload, url))
        }
    }

    fun sendMessage(title: String, message: String, color: Int, url: String = "", embed: Boolean = true) {
        val targetUrl = url.ifEmpty { webhookUrl }
        if (targetUrl.isEmpty() || title.isEmpty() || message.isEmpty()) {
            return
        }
        sendPayload(getPayload(title, message, color, embed), targetUrl)
    }

    fun sendMessage(message: String, url: String = "") {
        val targetUrl = url.ifEmpty { webhookUrl }
        if (targetUrl.isEmpty() || message.isEmpty()) {
            return
        }
        sendPayload(getPayload("", message, -1, false), targetUrl)
    }

    /**
     * Drains the oldest queued task. Success pops the task; transport failures
     * and HTTP errors (including 429/504) keep it queued, exactly like Canary.
     */
    suspend fun sendWebhook() {
        if (!isSending.compareAndSet(false, true)) {
            return
        }
        try {
            val task = synchronized(lock) { tasks.firstOrNull() } ?: return

            val response = sender.sendRequest(task.url, task.payload)
            val responseCode = response.statusCode
            if (responseCode == -1) {
                return
            }
            if (responseCode == 429 || responseCode == 504) {
                logger.warn { "Webhook encountered error code $responseCode, re-queueing task." }
                return
            }
            if (responseCode >= 300) {
                logger.error {
                    "Failed to send webhook message, error code: $responseCode " +
                        "response body: ${response.responseBody} request body: ${task.payload}"
                }
                return
            }

            logger.debug { "Webhook successfully sent to ${task.url}" }
            synchronized(lock) {
                if (tasks.firstOrNull() === task) {
                    tasks.removeFirst()
                }
            }
        } finally {
            isSending.set(false)
        }
    }

    /** Schedules [sendWebhook] on the Maintenance lane and re-arms itself. */
    fun run() {
        val scheduler = dispatcher ?: return
        scheduler.schedule(delayMs, DispatcherLane.Maintenance) {
            sendWebhook()
            run()
        }
    }

    private fun getPayload(title: String, message: String, color: Int, embed: Boolean = true): String {
        val payload = mapper.createObjectNode()
        if (embed) {
            val embedNode = mapper.createObjectNode()
            embedNode.put("title", title)
            if (message.isNotEmpty()) {
                embedNode.put("description", message)
            }
            if (sendFooter) {
                embedNode.putObject("footer").put("text", "$serverName | ${formatDate(clock.millis())}")
            }
            if (color >= 0) {
                embedNode.put("color", color)
            }
            payload.putArray("embeds").add(embedNode)
        } else {
            payload.put("content", message.ifEmpty { title })
        }
        return mapper.writeValueAsString(payload)
    }

    private fun formatDate(timeMillis: Long): String =
        DATE_FORMATTER.format(Instant.ofEpochMilli(timeMillis).atZone(clock.zone))

    companion object {
        const val DEFAULT_DELAY_MS: Long = 1000

        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    }
}
