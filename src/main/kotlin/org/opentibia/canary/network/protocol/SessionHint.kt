package org.opentibia.canary.network.protocol

import org.opentibia.canary.utils.Tools
import java.time.Duration

/**
 * Kotlin port of Canary's protocol session hint store.
 *
 * C++ sources: `src/server/network/protocol/protocol_session_hint.hpp` and
 * `src/server/network/protocol/protocol_session_hint.cpp`. The C++ singleton is
 * replaced by constructor injection ([nanoTime] is the steady-clock seam used
 * for expiry); behavior (TTLs, reuse, eviction) matches Canary.
 */
data class ProtocolSessionHintLease(
    val leaseId: Long,
    val remoteIp: UInt,
    val behavior: InitialConnectionBehavior,
    val candidateIds: List<Long>,
    val expiresAt: Long,
) {
    /** Kotlin equivalent of Canary's `explicit operator bool()`. */
    val hasValue: Boolean get() = leaseId != 0L && candidateIds.isNotEmpty()
}

class ProtocolSessionHintStore(
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private data class Hint(
        val id: Long,
        val remoteIp: UInt,
        val profileId: ProtocolProfileId,
        val behavior: InitialConnectionBehavior,
        val accountSessionHash: String,
        val allowedCharacterNames: MutableSet<String>,
        var expiresAt: Long,
        val reusable: Boolean,
    )

    private val lock = Any()
    private val hints = mutableListOf<Hint>()
    private var nextHintId = 1L
    private var nextLeaseId = 1L

    fun registerHint(
        remoteIp: UInt,
        profileId: ProtocolProfileId,
        accountSession: String,
        characterNames: List<String>,
    ) {
        val profile = ProtocolProfileRegistry.getProfile(profileId)
        if (profile == null || !ProtocolProfileRegistry.isProfileAllowed(profileId)) {
            return
        }

        val now = nanoTime()
        synchronized(lock) {
            cleanupExpired(now)

            if (hints.size >= MAX_HINTS) {
                hints.removeAt(0)
            }

            val hint = Hint(
                id = nextHintId++,
                remoteIp = remoteIp,
                profileId = profileId,
                behavior = profile.initialBehavior,
                accountSessionHash = hashSession(accountSession),
                allowedCharacterNames = characterNames.mapTo(mutableSetOf()) { it.lowercase() },
                expiresAt = getHintExpiration(profile, now),
                reusable = shouldReuseHintAfterMatch(profile),
            )

            hints.removeAll { existingHint ->
                existingHint.remoteIp == hint.remoteIp &&
                    existingHint.allowedCharacterNames.any { it in hint.allowedCharacterNames }
            }
            hints.add(hint)
        }
    }

    fun claimByIp(
        remoteIp: UInt,
        requiredBehavior: InitialConnectionBehavior? = null,
    ): ProtocolSessionHintLease? {
        val now = nanoTime()
        synchronized(lock) {
            cleanupExpired(now)

            var behavior: InitialConnectionBehavior? = null
            val candidates = mutableListOf<Long>()
            for (hint in hints) {
                if (hint.remoteIp != remoteIp) {
                    continue
                }
                if (requiredBehavior != null && !requiredBehavior.hasSameWireBehavior(hint.behavior)) {
                    continue
                }

                if (behavior == null) {
                    behavior = hint.behavior
                } else if (!behavior.hasSameWireBehavior(hint.behavior)) {
                    return null
                }

                candidates.add(hint.id)
            }

            val resolvedBehavior = behavior ?: return null
            if (candidates.isEmpty()) {
                return null
            }

            return ProtocolSessionHintLease(
                leaseId = nextLeaseId++,
                remoteIp = remoteIp,
                behavior = resolvedBehavior,
                candidateIds = candidates,
                expiresAt = now + HINT_TTL_NANOS,
            )
        }
    }

    fun consumeIfMatches(
        lease: ProtocolSessionHintLease,
        accountSession: String,
        characterName: String,
        clientVersion: Int,
    ): Boolean = consumeAndResolveProfile(lease, accountSession, characterName, clientVersion) != null

    fun consumeAndResolveProfile(
        lease: ProtocolSessionHintLease,
        accountSession: String,
        characterName: String,
        clientVersion: Int,
    ): ProtocolProfileId? {
        if (!lease.hasValue) {
            return null
        }

        val now = nanoTime()
        synchronized(lock) {
            cleanupExpired(now)

            val accountSessionHash = hashSession(accountSession)
            val normalizedCharacterName = characterName.lowercase()
            for (hintId in lease.candidateIds) {
                val hint = hints.firstOrNull { it.id == hintId } ?: continue

                val profile = ProtocolProfileRegistry.getProfile(hint.profileId) ?: continue
                if (profile.clientVersion != clientVersion) {
                    continue
                }
                if (hint.accountSessionHash != accountSessionHash) {
                    continue
                }
                if (normalizedCharacterName !in hint.allowedCharacterNames) {
                    continue
                }

                if (hint.reusable) {
                    hint.expiresAt = getHintExpiration(profile, now)
                } else {
                    hints.remove(hint)
                }
                return hint.profileId
            }

            return null
        }
    }

    fun clearReusableHintsByIp(
        remoteIp: UInt,
        requiredBehavior: InitialConnectionBehavior? = null,
    ) {
        val now = nanoTime()
        synchronized(lock) {
            cleanupExpired(now)

            hints.removeAll { hint ->
                hint.remoteIp == remoteIp &&
                    hint.reusable &&
                    (requiredBehavior == null || requiredBehavior.hasSameWireBehavior(hint.behavior))
            }
        }
    }

    private fun cleanupExpired(now: Long) {
        hints.removeAll { hint -> hint.expiresAt <= now }
    }

    private fun shouldReuseHintAfterMatch(profile: ProtocolProfile): Boolean =
        !profile.initialBehavior.hasSameWireBehavior(ProtocolProfileRegistry.defaultModernInitialBehavior())

    private fun getHintExpiration(profile: ProtocolProfile, now: Long): Long =
        now + if (shouldReuseHintAfterMatch(profile)) REUSABLE_HINT_TTL_NANOS else HINT_TTL_NANOS

    private fun hashSession(accountSession: String): String = Tools.transformToSHA256(accountSession)

    private companion object {
        val HINT_TTL_NANOS: Long = Duration.ofSeconds(30).toNanos()
        val REUSABLE_HINT_TTL_NANOS: Long = Duration.ofHours(24).toNanos()
        const val MAX_HINTS = 512
    }
}
