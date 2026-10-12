# Feature: Prometheus Metrics, Observability & Webhook Alerts

## 1. Explanation
Implements enterprise observability and alerts (`src/server/network/webhook/`, metrics telemetry). Exposes a lightweight Prometheus `/metrics` HTTP endpoint scraping internal server health, tick latency, memory allocations, and emits Discord webhook notifications on server lifecycle events.

## 2. Impact
Production monitoring. Operators can integrate Prometheus & Grafana dashboards to monitor tick lag spikes, memory growth, player count curves, and receive instant Discord alerts when crashes or server restarts occur.

## 3. Functionality Affected
- **Prometheus Scrape Endpoint**:
  - `canary_players_online_total`: Gauge tracking active player count.
  - `canary_game_tick_duration_ms`: Histogram measuring `GameDispatcher` loop cycle latency.
  - `canary_netty_direct_memory_bytes`: Tracking Netty off-heap buffer allocations.
  - `jvm_memory_used_bytes` & `jvm_gc_pause_seconds`.
- **Discord Webhooks**:
  - Emits embed messages to configured Discord Webhook URLs on server startup, shutdown, and error alerts.

## 4. Agent Implementation Details
- Add `io.micrometer:micrometer-registry-prometheus` to `build.gradle.kts`.
- Implement `MetricsServer.kt` serving Prometheus scrapes on port 9090.
- Implement `DiscordWebhookClient.kt` dispatching async JSON payload notifications.

## 5. Test Reference
- `org.opentibia.canary.monitoring.MetricsServerTest`: Queries the `/metrics` endpoint and verifies presence of `canary_players_online_total` and tick metrics.
