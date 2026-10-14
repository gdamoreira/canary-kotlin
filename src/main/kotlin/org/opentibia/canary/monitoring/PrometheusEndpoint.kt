package org.opentibia.canary.monitoring

import com.sun.net.httpserver.HttpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger {}

/**
 * `metricsEnablePrometheus` / `metricsPrometheusAddress` HTTP exporter: serves
 * [MetricsRegistry.scrape] on `http://<host>:<port>/metrics`
 * (`metrics/README.md`, `config.lua.dist:670-671`, default `localhost:9464`).
 *
 * Port infrastructure replacing the C++ OpenTelemetry Prometheus exporter
 * (`src/lib/metrics/metrics.cpp`): the JDK HTTP server exposes the same scrape
 * payload the registry renders.
 */
class PrometheusEndpoint(
    private val registry: MetricsRegistry,
    private val address: String,
) : AutoCloseable {

    private var server: HttpServer? = null

    /** Bound port once [start] ran (useful with port `0`). */
    val boundPort: Int
        get() = server?.address?.port ?: error("Prometheus endpoint has not been started")

    fun start(): PrometheusEndpoint {
        check(server == null) { "Prometheus endpoint is already running" }
        val socket = parseAddress(address)
            ?: throw IllegalArgumentException("Invalid metricsPrometheusAddress: '$address'")
        val httpServer = HttpServer.create(socket, 0)
        httpServer.createContext("/metrics") { exchange ->
            val body = registry.scrape().toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        httpServer.createContext("/") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        httpServer.executor = null
        httpServer.start()
        server = httpServer
        logger.info { "Prometheus metrics endpoint listening on $address/metrics" }
        return this
    }

    override fun close() {
        server?.stop(0)
        server = null
    }

    companion object {

        /** `host:port` address parsing; `null` when the value is malformed or the port is out of range. */
        fun parseAddress(address: String): InetSocketAddress? {
            val separator = address.lastIndexOf(':')
            if (separator <= 0 || separator == address.length - 1) {
                return null
            }
            val host = address.substring(0, separator)
            val port = address.substring(separator + 1).toIntOrNull() ?: return null
            if (port !in 0..65535) {
                return null
            }
            return InetSocketAddress(host, port)
        }

        /**
         * Starts the exporter when `metricsEnablePrometheus` is on
         * (`ConfigManager::getBoolean`, configmanager.cpp:554); a malformed address
         * keeps the endpoint disabled with a warning, mirroring the boot's
         * best-effort metrics behavior.
         */
        fun startIfEnabled(configManager: ConfigManager, registry: MetricsRegistry): PrometheusEndpoint? {
            val key = ConfigKeys.byName("METRICS_ENABLE_PROMETHEUS") ?: return null
            if (!configManager.getBoolean(key, key.defaultValue as? Boolean ?: false)) {
                return null
            }
            val addressKey = ConfigKeys.byName("METRICS_PROMETHEUS_ADDRESS")
            val address = addressKey?.let { configManager.getString(it, it.defaultValue as? String ?: "") }
                ?.takeIf { it.isNotBlank() }
                ?: "localhost:9464"
            val endpoint = PrometheusEndpoint(registry, address)
            return try {
                endpoint.start()
            } catch (exception: Exception) {
                logger.warn { "Prometheus metrics endpoint disabled: ${exception.message}" }
                null
            }
        }
    }
}
