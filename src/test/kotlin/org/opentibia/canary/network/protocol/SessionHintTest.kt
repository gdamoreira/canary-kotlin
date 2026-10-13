package org.opentibia.canary.network.protocol

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Transcript of Canary `tests/unit/server/network/protocol/multiprotocol_test.cpp`
 * (`SessionHintTest`) plus expiry, eviction, and behavior-filter coverage.
 *
 * C++ sources: src/server/network/protocol/protocol_session_hint.hpp/.cpp
 */
class SessionHintTest {

    private val store = ProtocolSessionHintStore()

    @Test
    fun `claim does not consume hint`() {
        val testIp = 0x0A000101u
        val session = "account-one\npassword-one"
        val character = "Hint Knight"

        store.registerHint(testIp, ProtocolProfileId.Tibia1100, session, listOf(character))

        val lease = store.claimByIp(testIp)
        assertThat(lease).isNotNull
        assertThat(store.consumeIfMatches(lease!!, session, character, 1100)).isTrue()
    }

    @Test
    fun `modern hint is consumed after validated login`() {
        val testIp = 0x0A000107u
        val session = "modern-relog\npassword"
        val character = "Modern Relog"

        store.registerHint(testIp, ProtocolProfileId.Current, session, listOf(character))

        val lease = store.claimByIp(testIp)
        assertThat(lease).isNotNull
        assertThat(store.consumeIfMatches(lease!!, session, character, CLIENT_VERSION)).isTrue()
        assertThat(store.claimByIp(testIp)).isNull()
    }

    @Test
    fun `legacy hint remains claimable after validated relog`() {
        val testIp = 0x0A000108u
        val session = "legacy-relog\npassword"
        val character = "Legacy Relog"

        store.registerHint(testIp, ProtocolProfileId.Cipsoft860CanaryExtended, session, listOf(character))

        val firstLease = store.claimByIp(testIp)
        assertThat(firstLease).isNotNull
        assertThat(firstLease!!.behavior.transport).isEqualTo(TransportProfileId.LegacyClassic)
        assertThat(store.consumeIfMatches(firstLease, session, character, 860)).isTrue()

        val relogLease = store.claimByIp(testIp)
        assertThat(relogLease).isNotNull
        assertThat(relogLease!!.behavior.transport).isEqualTo(TransportProfileId.LegacyClassic)
        assertThat(store.consumeIfMatches(relogLease, session, character, 860)).isTrue()
    }

    @Test
    fun `same initial behavior different profiles can select transport`() {
        val testIp = 0x0A000102u
        val vanillaSession = "vanilla-account\nvanilla-password"
        val extendedSession = "extended-account\nextended-password"

        store.registerHint(testIp, ProtocolProfileId.Cipsoft860Vanilla, vanillaSession, listOf("Vanilla Character"))
        store.registerHint(
            testIp,
            ProtocolProfileId.Cipsoft860CanaryExtended,
            extendedSession,
            listOf("Extended Character"),
        )

        val lease = store.claimByIp(testIp)
        assertThat(lease).isNotNull
        assertThat(lease!!.candidateIds).hasSize(2)
        assertThat(store.consumeIfMatches(lease, extendedSession, "Extended Character", 860)).isTrue()
    }

    @Test
    fun `conflicting current and 1100 hints do not select transport`() {
        val testIp = 0x0A000103u

        store.registerHint(testIp, ProtocolProfileId.Current, "modern-account\nmodern-password", listOf("Modern Character"))
        store.registerHint(testIp, ProtocolProfileId.Tibia1100, "old-account\nold-password", listOf("Old Character"))

        assertThat(store.claimByIp(testIp)).isNull()
    }

    @Test
    fun `conflicting 860 and modern hints do not select legacy`() {
        val testIp = 0x0A000104u

        store.registerHint(testIp, ProtocolProfileId.Current, "modern-account\nmodern-password", listOf("Modern Character"))
        store.registerHint(
            testIp,
            ProtocolProfileId.Cipsoft860Vanilla,
            "legacy-account\nlegacy-password",
            listOf("Legacy Character"),
        )

        assertThat(store.claimByIp(testIp)).isNull()
    }

    @Test
    fun `new hint replaces older same ip character hint`() {
        val testIp = 0x0A000105u
        val character = "Shared Character"
        val modernSession = "modern-account\nmodern-password"
        val legacySession = "legacy-account\nlegacy-password"

        store.registerHint(testIp, ProtocolProfileId.Current, modernSession, listOf(character))
        store.registerHint(testIp, ProtocolProfileId.Cipsoft860Vanilla, legacySession, listOf(character))

        val lease = store.claimByIp(testIp)
        assertThat(lease).isNotNull
        assertThat(lease!!.behavior.transport).isEqualTo(TransportProfileId.LegacyClassic)
        assertThat(store.consumeIfMatches(lease, legacySession, character, 860)).isTrue()
    }

    @Test
    fun `wrong character does not consume hint`() {
        val testIp = 0x0A000109u
        val session = "account-two\npassword-two"
        val character = "Correct Character"

        store.registerHint(testIp, ProtocolProfileId.Tibia1100, session, listOf(character))

        val lease = store.claimByIp(testIp)
        assertThat(lease).isNotNull
        assertThat(store.consumeIfMatches(lease!!, session, "Wrong Character", 1100)).isFalse()
        assertThat(store.consumeIfMatches(lease, session, character, 1100)).isTrue()
    }

    @Test
    fun `consume returns matched asset profile`() {
        val testIp = 0x0A000106u
        val session = "extended-account\nextended-password"
        val character = "Extended Character"

        store.registerHint(testIp, ProtocolProfileId.Cipsoft860CanaryExtended, session, listOf(character))

        val lease = store.claimByIp(testIp)
        assertThat(lease).isNotNull
        val matchedProfile = store.consumeAndResolveProfile(lease!!, session, character, 860)
        assertThat(matchedProfile).isEqualTo(ProtocolProfileId.Cipsoft860CanaryExtended)
    }

    @Test
    fun `character name matching is case insensitive`() {
        val testIp = 0x0A00010Au
        val session = "case-account\ncase-password"

        store.registerHint(testIp, ProtocolProfileId.Tibia1100, session, listOf("Hint Knight"))

        val lease = store.claimByIp(testIp)
        assertThat(lease).isNotNull
        assertThat(store.consumeIfMatches(lease!!, session, "HINT KNIGHT", 1100)).isTrue()
    }

    @Test
    fun `expired hint is not claimable`() {
        var now = 0L
        val expiringStore = ProtocolSessionHintStore { now }
        val testIp = 0x0A00010Bu

        expiringStore.registerHint(testIp, ProtocolProfileId.Current, "expiring-account", listOf("Expiring Character"))

        now += Duration.ofSeconds(31).toNanos()
        assertThat(expiringStore.claimByIp(testIp)).isNull()
    }

    @Test
    fun `reusable legacy hint survives past transient ttl`() {
        var now = 0L
        val reusableStore = ProtocolSessionHintStore { now }
        val testIp = 0x0A00010Cu

        reusableStore.registerHint(testIp, ProtocolProfileId.Cipsoft860Vanilla, "legacy-account", listOf("Legacy Character"))

        now += Duration.ofSeconds(31).toNanos()
        val lease = reusableStore.claimByIp(testIp)
        assertThat(lease).isNotNull
        assertThat(reusableStore.consumeIfMatches(lease!!, "legacy-account", "Legacy Character", 860)).isTrue()
    }

    @Test
    fun `max hints evicts the oldest entries`() {
        val evictingStore = ProtocolSessionHintStore()

        for (index in 0 until 512) {
            evictingStore.registerHint(
                index.toUInt(),
                ProtocolProfileId.Cipsoft860Vanilla,
                "session-$index",
                listOf("character-$index"),
            )
        }
        assertThat(evictingStore.claimByIp(0u)).isNotNull

        evictingStore.registerHint(999u, ProtocolProfileId.Cipsoft860Vanilla, "session-999", listOf("character-999"))

        assertThat(evictingStore.claimByIp(0u)).isNull()
        assertThat(evictingStore.claimByIp(999u)).isNotNull
    }

    @Test
    fun `clear reusable hints by ip honors required behavior`() {
        val testIp = 0x0A00010Du
        val session = "clear-account\nclear-password"
        val character = "Clear Character"

        store.registerHint(testIp, ProtocolProfileId.Cipsoft860Vanilla, session, listOf(character))
        val lease = store.claimByIp(testIp)
        assertThat(lease).isNotNull
        assertThat(store.consumeIfMatches(lease!!, session, character, 860)).isTrue()

        store.clearReusableHintsByIp(testIp, ProtocolProfileRegistry.defaultModernInitialBehavior())
        assertThat(store.claimByIp(testIp)).isNotNull

        store.clearReusableHintsByIp(testIp)
        assertThat(store.claimByIp(testIp)).isNull()
    }

    @Test
    fun `registration ignores profiles blocked pending fixture`() {
        val testIp = 0x0A00010Eu

        store.registerHint(testIp, ProtocolProfileId.OTCv8Extended860, "blocked-account", listOf("Blocked Character"))

        assertThat(store.claimByIp(testIp)).isNull()
    }

    @Test
    fun `claim by ip honors required behavior filter`() {
        val testIp = 0x0A00010Fu

        store.registerHint(testIp, ProtocolProfileId.Cipsoft860Vanilla, "legacy-account", listOf("Legacy Character"))

        assertThat(store.claimByIp(testIp, ProtocolProfileRegistry.defaultModernInitialBehavior())).isNull()
        assertThat(store.claimByIp(testIp)).isNotNull
    }
}
