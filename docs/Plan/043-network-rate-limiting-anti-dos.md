# Feature: Network Rate Limiting, Flood Protection & Anti-DoS

## 1. Explanation
Protects the server against malicious socket exhaustion, packet flooding, slowloris attacks, and connection spam (`src/server/network/connection/connection.cpp` lines 305-316). Enforces maximum packets per second limits (`MAX_PACKETS_PER_SECOND`) and max connections per IP subnet.

## 2. Impact
Production stability. Without rate limiting, a single bot client can send 10,000 TCP packets per second, saturating CPU cycles, exhausting Netty memory pools, and causing latency spikes for legitimate players.

## 3. Functionality Affected
- **Token Bucket Limiter**: Tracks packet counts per connection over a moving 1-second window.
- **Auto-Disconnect & Ban**: Automatically severs connections exceeding 50–100 packets/sec and logs the offending IP.
- **Idle Timeout**: Automatically closes sockets that remain silent or incomplete for $>30$ seconds (`CONNECTION_READ_TIMEOUT`).

## 4. Agent Implementation Details
- Implement `RateLimiterHandler.kt` as an early ChannelInboundHandler in Netty's pipeline.
- Implement token-bucket algorithm per socket channel.
- If threshold is violated, log warning and issue immediate `ctx.close()`.

## 5. Test Reference
- `org.opentibia.canary.network.codec.RateLimiterHandlerTest`: Floods a channel with 200 packets in 100ms and verifies the handler closes the connection and rejects further packets.
