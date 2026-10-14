> Adapted from canary/docs/systems/livestream/README.md.
> Canary-Kotlin status: the livestream engine core is ported
> (`creatures/players/livestream/Livestream.kt` and
> `network/protocol/livestream/LivestreamViewerWriter.kt`); the system contract
> remains the reference for session and protocol behavior.

# Livestream

Livestream documentation is collected here because this feature spans protocol
login, game packet forwarding, Lua commands, database state, and external login
services.

- [System contract](system-contract.md) describes the expected behavior,
  invariants, unsafe simplifications, and validation checklist for maintaining
  the livestream viewer system.
