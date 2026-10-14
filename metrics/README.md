# Canary Kotlin Metrics (Prometheus)

> Adapted from `canary/metrics/README.md` for the Kotlin/JVM engine. Upstream
> Canary exports through the OpenTelemetry SDK; this port renders the same
> scrape payload from the in-process `MetricsRegistry`.

By default, no metrics endpoint is exposed. To enable it, turn on the
Prometheus exporter and pick an address:

`config.lua`

```lua
metricsEnablePrometheus = true
metricsPrometheusAddress = "0.0.0.0:9464"
```

The same values can be provided through the environment
(`CANARY_METRICS_ENABLE_PROMETHEUS=true`,
`CANARY_METRICS_PROMETHEUS_ADDRESS=0.0.0.0:9464`) or the `--config` file.

This exposes a Prometheus endpoint at `http://localhost:9464/metrics`.

## Quickstart with Prometheus + Grafana

The `docker-compose.yml` in this folder starts a Prometheus instance that
scrapes the endpoint and a Grafana instance:

```bash
cd metrics
docker compose up -d
```

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:4444` (default credentials `admin` / `admin`; you
  are prompted to change the password on first login). Add the Prometheus data
  source at `http://prometheus:9090` inside the compose network.

Prometheus reaches the server through `host.docker.internal:9464`
(`extra_hosts: host-gateway`), so the server may run directly on the host.

## Exported metrics

The registry renders the Canary metric families:

- `canary_players_online_total` (gauge) and `canary_game_tick_duration_ms`
  (gauge) — the two base gauges.
- Counters (`_total`, `counter` type), up/down counters (`gauge`) and the five
  latency histograms (`method_latency`, `lua_latency`, `query_latency`,
  `task_latency`, `lock_latency`) recorded through `ScopedLatency` wrappers.

Upstream dashboards and the analytics screenshots live in the upstream project
(`opentibiabr/canary`); the exported metric names match so the same queries
apply.

## Status in this port

- The Prometheus **HTTP endpoint is implemented** (`PrometheusEndpoint`, bound
  to `metricsEnablePrometheus`/`metricsPrometheusAddress`).
- The OpenTelemetry SDK and the periodic OStream reader are **not ported**; the
  registry exposes scrape output only, and latency instrumentation call sites
  are wired incrementally.
