package org.opentibia.canary.creatures.players.components

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.kv.KVMemory
import org.opentibia.canary.kv.ValueWrapper

private val CASTLEMANIA = Achievement(
    id = 1,
    name = "Castlemania",
    description = "You have an eye for suspicious places and love to read other people's diaries.",
    secret = true,
    grade = 2,
    points = 5,
)

private val CHORISTER = Achievement(
    id = 2,
    name = "Chorister",
    description = "Lalalala... you now know the cult's hymn sung in Liberty Bay by heart.",
    grade = 1,
    points = 1,
)

private val THE_MILKMAN = Achievement(
    id = 3,
    name = "The Milkman",
    description = "Who's the milkman? You are!",
    grade = 1,
    points = 2,
)

private val ALL_ACHIEVEMENTS = listOf(CASTLEMANIA, CHORISTER, THE_MILKMAN)

/**
 * Transcript tests for the Canary `PlayerAchievement` component.
 *
 * Source of truth: ../.projects/canary
 *  - src/creatures/players/components/player_achievement.hpp:15-48
 *  - src/creatures/players/components/player_achievement.cpp:16-140
 *  - src/game/game.cpp:12481-12503 (`Game::registerAchievement`,
 *    `getAchievementById`, `getAchievementByName`)
 *  - data/scripts/lib/register_achievements.lua:1-30 (achievement records,
 *    test data only; the registry itself is Lua-driven in Canary)
 */
class PlayerAchievementsTest {

    private class FakeAchievementPlayer : AchievementPlayer {
        var playerName: String = "Knight"
        val textMessages = mutableListOf<String>()
        val cyclopediaSends = mutableListOf<Pair<Int, List<Pair<Achievement, Long>>>>()

        override fun getName(): String = playerName

        override fun sendTextMessage(message: String) {
            textMessages.add(message)
        }

        override fun sendCyclopediaCharacterAchievements(
            secretsUnlocked: Int,
            achievementsUnlocked: List<Pair<Achievement, Long>>,
        ) {
            cyclopediaSends.add(secretsUnlocked to achievementsUnlocked)
        }
    }

    private class Fixture(
        achievements: List<Achievement> = ALL_ACHIEVEMENTS,
    ) {
        val player = FakeAchievementPlayer()
        val kv = KVMemory()
        val registry = InMemoryAchievementRegistry(achievements)
        val debugLogs = mutableListOf<String>()
        val errorLogs = mutableListOf<String>()
        var nowSeconds = 1_700_000_000L

        val achievement = PlayerAchievement(
            player = player,
            kv = kv,
            registry = registry,
            now = { nowSeconds },
            logDebug = { debugLogs.add(it) },
            logError = { errorLogs.add(it) },
        )
    }

    @Test
    fun `isUnlocked returns false for id zero`() {
        assertThat(Fixture().achievement.isUnlocked(0)).isFalse()
    }

    @Test
    fun `add refuses an unknown achievement id`() {
        val f = Fixture()

        assertThat(f.achievement.add(99)).isFalse()
        assertThat(f.achievement.getPoints()).isEqualTo(0)
    }

    @Test
    fun `add unlocks the achievement, adds its points, persists it and messages`() {
        val f = Fixture()

        assertThat(f.achievement.add(1)).isTrue()

        assertThat(f.achievement.isUnlocked(1)).isTrue()
        assertThat(f.achievement.getUnlockedAchievements()).containsExactly(1 to 1_700_000_000L)
        assertThat(f.achievement.getUnlockedKV().get("Castlemania")!!.getInt().toLong()).isEqualTo(1_700_000_000L)
        assertThat(f.achievement.getPoints()).isEqualTo(5)
        assertThat(f.player.textMessages)
            .containsExactly("Congratulations! You earned the achievement Castlemania.")
    }

    @Test
    fun `add respects the message flag`() {
        val f = Fixture()

        assertThat(f.achievement.add(2, message = false)).isTrue()

        assertThat(f.player.textMessages).isEmpty()
        assertThat(f.achievement.getPoints()).isEqualTo(1)
    }

    @Test
    fun `add uses the caller timestamp when nonzero`() {
        val f = Fixture()

        assertThat(f.achievement.add(1, timestamp = 77L)).isTrue()

        assertThat(f.achievement.getUnlockedKV().get("Castlemania")!!.getInt().toLong()).isEqualTo(77L)
    }

    @Test
    fun `add refuses an already unlocked achievement`() {
        val f = Fixture()
        f.achievement.add(1)
        f.achievement.getUnlockedKV().set("Castlemania", ValueWrapper(999))

        assertThat(f.achievement.add(1)).isFalse()

        assertThat(f.achievement.getPoints()).isEqualTo(5)
        assertThat(f.achievement.getUnlockedKV().get("Castlemania")!!.getInt()).isEqualTo(999)
        assertThat(f.player.textMessages).hasSize(1)
    }

    @Test
    fun `remove erases the achievement, its KV key and its points`() {
        val f = Fixture()
        f.achievement.add(1)
        f.achievement.add(3)

        assertThat(f.achievement.remove(1)).isTrue()

        assertThat(f.achievement.isUnlocked(1)).isFalse()
        assertThat(f.achievement.getUnlockedAchievements()).containsExactly(3 to 1_700_000_000L)
        assertThat(f.achievement.getUnlockedKV().get("Castlemania")).isNull()
        assertThat(f.achievement.getPoints()).isEqualTo(2)
    }

    @Test
    fun `remove refuses a locked achievement`() {
        val f = Fixture()

        assertThat(f.achievement.remove(1)).isFalse()

        assertThat(f.achievement.getPoints()).isEqualTo(0)
    }

    @Test
    fun `getPoints defaults to zero and addPoints accumulates`() {
        val f = Fixture()

        assertThat(f.achievement.getPoints()).isEqualTo(0)

        f.achievement.addPoints(5)
        f.achievement.addPoints(2)
        assertThat(f.achievement.getPoints()).isEqualTo(7)
    }

    @Test
    fun `getPoints narrows the stored value to uint16 like Canary`() {
        val f = Fixture()
        f.kv.scoped("achievements").set("points", ValueWrapper(70_000))

        assertThat(f.achievement.getPoints()).isEqualTo(4_464)
    }

    @Test
    fun `removePoints clamps at zero`() {
        val f = Fixture()
        f.achievement.addPoints(5)

        f.achievement.removePoints(3)
        assertThat(f.achievement.getPoints()).isEqualTo(2)

        f.achievement.removePoints(10)
        assertThat(f.achievement.getPoints()).isEqualTo(0)
    }

    @Test
    fun `getUnlockedKV scopes the unlocked entries under achievements unlocked`() {
        val f = Fixture()

        f.achievement.getUnlockedKV().set("Castlemania", ValueWrapper(5))

        assertThat(f.kv.scoped("achievements").scoped("unlocked").get("Castlemania")!!.getInt()).isEqualTo(5)
    }

    @Test
    fun `loadUnlockedAchievements resolves persisted names and logs`() {
        val f = Fixture()
        f.achievement.getUnlockedKV().set("Castlemania", ValueWrapper(111))

        f.achievement.loadUnlockedAchievements()

        assertThat(f.achievement.getUnlockedAchievements()).containsExactly(1 to 111L)
        assertThat(f.debugLogs).containsExactly(
            "[loadUnlockedAchievements] - Loading unlocked achievements: 1",
            "[loadUnlockedAchievements] - Achievement Castlemania found for player Knight.",
        )
    }

    @Test
    fun `loadUnlockedAchievements skips unknown names and logs an error`() {
        val f = Fixture()
        f.achievement.getUnlockedKV().set("Ghost Achievement", ValueWrapper(111))

        f.achievement.loadUnlockedAchievements()

        assertThat(f.achievement.getUnlockedAchievements()).isEmpty()
        assertThat(f.errorLogs)
            .containsExactly("[loadUnlockedAchievements] - Achievement Ghost Achievement not found.")
    }

    @Test
    fun `loadUnlockedAchievements appends on every call, matching Canary's load-without-clear`() {
        val f = Fixture()
        f.achievement.getUnlockedKV().set("Castlemania", ValueWrapper(111))

        f.achievement.loadUnlockedAchievements()
        f.achievement.loadUnlockedAchievements()

        assertThat(f.achievement.getUnlockedAchievements()).containsExactly(1 to 111L, 1 to 111L)
    }

    @Test
    fun `sendUnlockedSecretAchievements counts the secrets and forwards every unlocked entry`() {
        val f = Fixture()
        f.achievement.add(1, message = false)
        f.achievement.add(2, message = false)

        f.achievement.sendUnlockedSecretAchievements()

        assertThat(f.player.cyclopediaSends).containsExactly(
            1 to listOf(CASTLEMANIA to 1_700_000_000L, CHORISTER to 1_700_000_000L),
        )
    }

    @Test
    fun `sendUnlockedSecretAchievements sends zero secrets when only public entries exist`() {
        val f = Fixture()
        f.achievement.add(2, message = false)

        f.achievement.sendUnlockedSecretAchievements()

        assertThat(f.player.cyclopediaSends).containsExactly(0 to listOf(CHORISTER to 1_700_000_000L))
    }
}
