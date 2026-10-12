# Feature: Game World Protocol & Connection Handshake (ProtocolGame)

## 1. Explanation
Manages player connection to the main game world server (port 7172). Handles the initial game handshake, RSA verification of character name and session key, player instantiation, and establishes the continuous bidirectional game packet stream.

## 2. Impact
Foundational system for gameplay. Bridges player network sessions with the internal game simulation and entity management.

## 3. Functionality Affected
- **Game Server Acceptor**: Listens on port 7172.
- **Game Handshake**: Receives OS, Protocol Version, Client Version, RSA block with character name and XTEA key.
- **Session Transition**: Links the TCP channel to an active `Player` entity in `GameWorld`.
- **Packet Streaming**: Establishes bidirectional message flow between player input events and outgoing world updates.

## 4. Agent Implementation Details
- Implement `GameConnectionHandler.kt` to manage channel lifecycle for game clients.
- Implement game login packet decoder verifying character existence, ban checks, and account status.
- Implement server-first or client-first prelude handling depending on target client version profile.
- Dispatch player connection event to the game world actor/dispatcher.

## 5. Test Reference
- `org.opentibia.canary.network.protocol.GameHandshakeTest`: Tests game world login with valid character, invalid credentials, and duplicate logins.
