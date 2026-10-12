# Feature: Status Protocol & Server Monitoring (ProtocolStatus)

## 1. Explanation
Provides the OpenTibia XML/JSON server status protocol (`ProtocolStatus` - Opcode `0xFF`). Allows status pingers, OTServ monitoring websites, and launcher clients to query server stats (online players, uptime, server name, version, map details).

## 2. Impact
Low risk, vital for community discovery and external monitoring tools. Keeps server statistics accessible without disrupting game or login flows.

## 3. Functionality Affected
- **Packet Ingestion**: Routes packets with protocol identifier `0xFF` on the login/status port (7171/7172).
- **XML / JSON Status Info**: Serializes uptime, server name, IP, port, current player count, maximum players, and server software metadata.

## 4. Agent Implementation Details
- Add status protocol handler (`StatusHandler.kt`) or route `0xFF` within `TibiaProtocolRouter`.
- Implement `ServerStatusService` collecting metrics: JVM memory, connected players, server startup time.
- Generate standard XML response `<tsqp version="1.0"><serverinfo .../><players .../><motd .../></tsqp>`.
- Optionally support JSON info format requested by modern web launchers.

## 5. Test Reference
- `org.opentibia.canary.network.protocol.StatusProtocolTest`: Unit/integration tests simulating client ping with opcode `0xFF` (with and without Adler32) and validating the XML/JSON response body.
