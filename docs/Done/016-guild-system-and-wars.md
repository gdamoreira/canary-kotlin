# Feature: Guild Management & Guild War System

## 1. Explanation
Implements player guilds, ranks (Leader, Vice-Leader, Member), invites, guild bank/treasury balance, guild chat channels, and Guild Wars (frag limits, war declarations, surrender, and war emblems on player nameplates).

## 2. Impact
Core endgame community and PvP system. Directly impacts player organization, PvP combat rules (green/red skull flags during wars), and broadcast notifications.

## 3. Functionality Affected
- **Guild Model**: Ranks, member lists, guild motd, treasury.
- **War System**: War declarations, acceptance, frag tracking, war scoreboard.
- **Visual Emblems**: Green shield for guildmates, blue shield for allies, red shield for war enemies.

## 4. Agent Implementation Details
- Implement `Guild`, `GuildRank`, and `GuildMember` classes in `org.opentibia.canary.social.guild`.
- Implement `GuildWarManager` handling declarations, scoring, and skull/emblem evaluations.
- Persist guild memberships and balances to storage.

## 5. Test Reference
- `org.opentibia.canary.social.guild.GuildWarTest`: Tests war declaration, kill score accumulation, frag limit victory, and emblem resolutions.
