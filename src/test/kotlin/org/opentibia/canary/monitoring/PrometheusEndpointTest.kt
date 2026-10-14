package org.opentibia.canary.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ConfigSource
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * `metricsEnablePrometheus` / `metricsPrometheusAddress` HTTP exporter
 * (`metrics/README.md`, `config.lua.dist:670-671`): the scrape endpoint serves
 * `http://<address>/metrics`.
 */
class PrometheusEndpointTest {

    private val client: HttpClient = HttpClient.newHttpClient()

    private fun get(port: Int, path: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun manager(environment: Map<String, String>): ConfigManager =
        ConfigManager(
            keyMap = ConfigKeys.all.associateWith { it.luaIdentifier },
            environment = environment,
            properties = ConfigSource.EMPTY,
        )

    @Test
    fun `serves the prometheus scrape on the metrics path`() {
        val registry = MetricsRegistry()
        registry.setOnlinePlayers(7)
        PrometheusEndpoint(registry, "127.0.0.1:0").use { endpoint ->
            endpoint.start()

            val response = get(endpoint.boundPort, "/metrics")

            assertThat(response.statusCode()).isEqualTo(200)
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/plain")
            assertThat(response.body()).contains("canary_players_online_total 7")
        }
    }

    @Test
    fun `unknown paths answer not found`() {
        PrometheusEndpoint(MetricsRegistry(), "127.0.0.1:0").use { endpoint ->
            endpoint.start()

            assertThat(get(endpoint.boundPort, "/other").statusCode()).isEqualTo(404)
        }
    }

    @Test
    fun `address parsing accepts host port pairs`() {
        assertThat(PrometheusEndpoint.parseAddress("0.0.0.0:9464"))
            .isEqualTo(InetSocketAddress("0.0.0.0", 9464))
        assertThat(PrometheusEndpoint.parseAddress("localhost:0"))
            .isEqualTo(InetSocketAddress("localhost", 0))
        assertThat(PrometheusEndpoint.parseAddress("not-an-address")).isNull()
        assertThat(PrometheusEndpoint.parseAddress("host:not-a-port")).isNull()
        assertThat(PrometheusEndpoint.parseAddress("host:70000")).isNull()
    }

    @Test
    fun `start if enabled honours the config switch`() {
        val disabled = PrometheusEndpoint.startIfEnabled(
            manager(mapOf("CANARY_METRICS_ENABLE_PROMETHEUS" to "false")),
            MetricsRegistry(),
        )
        assertThat(disabled).isNull()

        val enabled = PrometheusEndpoint.startIfEnabled(
            manager(
                mapOf(
                    "CANARY_METRICS_ENABLE_PROMETHEUS" to "true",
                    "CANARY_METRICS_PROMETHEUS_ADDRESS" to "127.0.0.1:0",
                ),
            ),
            MetricsRegistry(),
        )
        assertThat(enabled).isNotNull
        enabled!!.close()
    }

    @Test
    fun `invalid address keeps the endpoint disabled`() {
        val endpoint = PrometheusEndpoint.startIfEnabled(
            manager(
                mapOf(
                    "CANARY_METRICS_ENABLE_PROMETHEUS" to "true",
                    "CANARY_METRICS_PROMETHEUS_ADDRESS" to "not-an-address",
                ),
            ),
            MetricsRegistry(),
        )
        assertThat(endpoint).isNull()
    }
}
