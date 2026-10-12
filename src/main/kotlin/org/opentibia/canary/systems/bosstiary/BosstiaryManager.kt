package org.opentibia.canary.systems.bosstiary

class BosstiaryManager {
    // Key: (playerId, bossName.lowercase()) -> kill count
    private val kills = mutableMapOf<Pair<Long, String>, Int>()
    // Key: (playerId, bossName.lowercase()) -> expiration timestamp in epoch seconds
    private val cooldowns = mutableMapOf<Pair<Long, String>, Long>()

    fun recordBossKill(playerId: Long, bossName: String, count: Int = 1) {
        val key = playerId to bossName.lowercase()
        val current = kills.getOrDefault(key, 0)
        kills[key] = current + count
    }

    fun getKillCount(playerId: Long, bossName: String): Int {
        return kills.getOrDefault(playerId to bossName.lowercase(), 0)
    }

    /**
     * Standard Bane boss tier thresholds:
     * Tier 1: 25 kills
     * Tier 2: 100 kills
     * Tier 3: 300 kills
     */
    fun getMasteryLevel(playerId: Long, bossName: String): Int {
        val count = getKillCount(playerId, bossName)
        return when {
            count >= 300 -> 3
            count >= 100 -> 2
            count >= 25 -> 1
            else -> 0
        }
    }

    fun setBossCooldown(playerId: Long, bossName: String, cooldownSeconds: Long) {
        val expireEpoch = (System.currentTimeMillis() / 1000) + cooldownSeconds
        cooldowns[playerId to bossName.lowercase()] = expireEpoch
    }

    fun canFightBoss(playerId: Long, bossName: String): Boolean {
        val expiresAt = cooldowns[playerId to bossName.lowercase()] ?: return true
        val now = System.currentTimeMillis() / 1000
        return now >= expiresAt
    }
}
