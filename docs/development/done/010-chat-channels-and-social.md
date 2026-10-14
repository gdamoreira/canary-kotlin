# Feature: Chat, Channels & Social Systems

## 1. Explanation
Implements creature speech (say, yell, whisper), standard chat channels (Local Chat, Server Log, World Chat, English Chat, Guild, Party), private messages, and social systems (VIP list, friend requests, ignore list, party, guild).

## 2. Impact
Essential for multiplayer communication, moderation, party hunting, and server messaging.

## 3. Functionality Affected
- **Speech**: Public speech broadcasted to spectators within range (say: 1-4 tiles, yell: floor-wide/large radius, whisper: 1 tile).
- **Channels**: Channel join/leave permissions, custom channels, party channels.
- **Party System**: Shared experience calculations, party leader delegation, invitation flow, party buffs/analyzer.
- **Guild System**: Guild ranks, permissions, titles, guild chat.

## 4. Agent Implementation Details
- Implement `ChatChannelManager` managing active channels and subscriber sets.
- Implement speech parser delegating to spell words or standard talkactions.
- Implement `Party` class with shared experience logic and distance checking.
- Implement VIP list storage and online/offline status notifications.

## 5. Test Reference
- `org.opentibia.canary.chat.ChatChannelTest`: Join, leave, send message, mute permissions.
- `org.opentibia.canary.social.PartyTest`: Shared exp distribution tests based on member levels and proximity.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
