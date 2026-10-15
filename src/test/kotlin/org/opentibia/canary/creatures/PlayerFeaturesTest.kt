package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.players.animus_mastery.AnimusMastery
import org.opentibia.canary.creatures.players.components.Achievement
import org.opentibia.canary.creatures.players.components.InMemoryAchievementRegistry
import org.opentibia.canary.items.Abilities
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.concoction.ConcoctionType
import org.opentibia.canary.systems.dailyreward.DailyRewardStatus
import org.opentibia.canary.systems.forge.ForgeHistory
import org.opentibia.canary.systems.offlinetraining.Skill

private val CASTLEMANIA = Achievement(
    id = 1,
    name = "Castlemania",
    description = "You have an eye for suspicious places and love to read other people's diaries.",
    secret = true,
    grade = 2,
    points = 5,
)

class PlayerFeaturesTest {

    private fun newPlayer(level: Int = 1) = Player(
        id = 42,
        name = "Feature Hero",
        maxHealth = 200,
        health = 200,
        position = Position(100, 100, 7),
        level = level,
    )

    @Test
    fun `player exposes a features holder tied to the player identity`() {
        val player = newPlayer()

        assertThat(player.features).isNotNull
        assertThat(player.features.playerId).isEqualTo(player.id)
    }

    @Test
    fun `each per-player feature sub-state is present and usable`() {
        val features = newPlayer().features

        assertThat(features.wheel.getWheelPoints()).isEqualTo(0)

        features.offlineTraining.setOfflineTrainingSkill(Skill.SKILL_SWORD)
        assertThat(features.offlineTraining.getOfflineTrainingSkill()).isEqualTo(Skill.SKILL_SWORD)
        features.offlineTraining.addOfflineTrainingTime(1_000)
        assertThat(features.offlineTraining.getOfflineTrainingTime()).isEqualTo(1_000)

        assertThat(features.mounts.isMounted()).isFalse()
        assertThat(features.mounts.getCurrentMount()).isEqualTo(0)

        features.concoctions.updateConcoction(ConcoctionType.FireResilience.itemId, 3_600)
        assertThat(features.concoctions.isConcoctionActive(ConcoctionType.FireResilience)).isTrue()

        assertThat(features.dailyReward.isDailyReward)
            .isEqualTo(DailyRewardStatus.DAILY_REWARD_NOTCOLLECTED.value)
        features.dailyReward.setDailyReward(DailyRewardStatus.DAILY_REWARD_COLLECTED.value)
        assertThat(features.dailyReward.isDailyReward)
            .isEqualTo(DailyRewardStatus.DAILY_REWARD_COLLECTED.value)

        features.familiars.addFamiliar(1_000)
        assertThat(features.familiars.familiars.map { it.lookType }).containsExactly(1_000)

        features.bestiary.addBestiaryKillCount(5, 3)
        assertThat(features.bestiary.getBestiaryKillCount(5)).isEqualTo(3)

        features.forge.setForgeDusts(123)
        assertThat(features.forge.getForgeDusts()).isEqualTo(123)

        features.forgeHistory.add(ForgeHistory(id = 9))
        assertThat(features.forgeHistory.get().map { it.id }).containsExactly(9)

        assertThat(features.hazard.getHazardSystemPoints()).isEqualTo(0)
    }

    @Test
    fun `wheel reads the player level on demand`() {
        val player = newPlayer(level = 250)
        assertThat(player.features.wheel.getWheelPoints()).isEqualTo(200)
    }

    @Test
    fun `level dependent features are created lazily and only once`() {
        var levelCalls = 0
        val features = PlayerFeatures(playerId = 7, levelProvider = {
            levelCalls++
            250
        })

        assertThat(levelCalls).isEqualTo(0)
        assertThat(features.wheel.getWheelPoints()).isEqualTo(200)
        assertThat(levelCalls).isEqualTo(1)
        assertThat(features.wheel.getWheelPoints()).isEqualTo(200)
        assertThat(levelCalls).isEqualTo(1)
    }

    @Test
    fun `existing positional player construction still compiles and exposes features`() {
        val player = Player(1, "Adventurer", 100, 100, position = Position(100, 100, 7))
        assertThat(player.features).isNotNull
    }

    // --- composed player state holders (combat bonuses / foods / message buffer) -

    @Test
    fun `player features compose the combat bonus food and message buffer holders`() {
        val player = newPlayer()
        val type = ItemType(id = 1, name = "Cleave Sword")
        type.abilities = Abilities().apply { cleavePercent = 12 }
        player.addItem(Item(id = 1, count = 1, type = type), Slots_t.CONST_SLOT_RIGHT)

        assertThat(player.features.combatBonuses.getCleavePercent()).isEqualTo(12)

        player.features.activeFoods.updateFood(1234, 60_000)
        assertThat(player.features.activeFoods.isFoodActive(1234)).isTrue()

        assertThat(player.features.messageBuffer.messageBufferCount).isZero()
    }

    @Test
    fun `the composed message buffer keeps the escalation through the injected seams`() {
        val conditions = mutableListOf<Condition>()
        val messages = mutableListOf<Pair<MessageClass, String>>()
        val sharedMuteCounts = mutableMapOf<Long, Int>()
        val features = PlayerFeatures(
            playerId = 42,
            maxMessageBuffer = { 4 },
            addCondition = { conditions.add(it) },
            sendTextMessage = { type, text -> messages.add(type to text) },
            muteCountMap = sharedMuteCounts,
        )

        repeat(5) { features.messageBuffer.removeMessageBuffer() }

        assertThat(conditions).hasSize(1)
        assertThat(messages).containsExactly(MessageClass.MESSAGE_FAILURE to "You are muted for 5 seconds.")
        assertThat(sharedMuteCounts).containsEntry(42L, 2)
    }

    @Test
    fun `the composed combat bonuses read the injected equipped items seam`() {
        val type = ItemType(id = 1, name = "Cleave Sword")
        type.abilities = Abilities().apply { cleavePercent = 9 }
        val item = Item(id = 1, count = 1, type = type)
        val features = PlayerFeatures(
            playerId = 42,
            getEquippedItems = { listOf(item) },
        )

        assertThat(features.combatBonuses.getCleavePercent()).isEqualTo(9)
    }

    @Test
    fun `the composed message buffer routes the mute through the owning player`() {
        val player = newPlayer()
        val features = PlayerFeatures(
            playerId = player.id,
            wheelPlayer = player,
            maxMessageBuffer = { 4 },
        )

        repeat(5) { features.messageBuffer.removeMessageBuffer() }

        assertThat(features.messageBuffer.messageBufferCount).isEqualTo(5)
        assertThat(player.getCondition(ConditionType.CONDITION_MUTED)).isNotNull()
    }

    @Test
    fun `the composed message buffer honors the player cannot be muted flag`() {
        val player = newPlayer()
        player.setFlag(PlayerFlags_t.CannotBeMuted)
        val features = PlayerFeatures(
            playerId = player.id,
            wheelPlayer = player,
            maxMessageBuffer = { 4 },
        )

        features.messageBuffer.removeMessageBuffer()

        assertThat(features.messageBuffer.messageBufferCount).isZero()
        assertThat(player.getCondition(ConditionType.CONDITION_MUTED)).isNull()
    }

    // --- composed achievement component (player.cpp:12758, player_achievement.cpp) ---

    @Test
    fun `player features compose one achievement component per player`() {
        val features = newPlayer().features

        assertThat(features.achiev()).isSameAs(features.achievement)
    }

    @Test
    fun `the composed achievement resolves its registry and persists through the player kv`() {
        val player = newPlayer()
        val features = PlayerFeatures(
            playerId = player.id,
            wheelPlayer = player,
            achievementRegistry = InMemoryAchievementRegistry(listOf(CASTLEMANIA)),
        )

        assertThat(features.achiev().add(1, timestamp = 77L)).isTrue()

        assertThat(features.achiev().isUnlocked(1)).isTrue()
        assertThat(features.achiev().getPoints()).isEqualTo(5)
        assertThat(player.kv.scoped("achievements").scoped("unlocked").get("Castlemania")!!.getInt()).isEqualTo(77)
    }

    @Test
    fun `the composed achievement sends the unlock message through the owning player typed seam`() {
        val player = newPlayer()
        val messages = mutableListOf<Pair<MessageClass, String>>()
        player.sendTextMessageTyped = { type, text -> messages.add(type to text) }
        val features = PlayerFeatures(
            playerId = player.id,
            wheelPlayer = player,
            achievementRegistry = InMemoryAchievementRegistry(listOf(CASTLEMANIA)),
        )

        assertThat(features.achiev().add(1)).isTrue()

        assertThat(messages).containsExactly(
            MessageClass.MESSAGE_EVENT_ADVANCE to
                "Congratulations! You earned the achievement Castlemania.",
        )
    }

    @Test
    fun `the composed achievement forwards unlocked secrets through the send seam`() {
        val player = newPlayer()
        val sends = mutableListOf<Pair<Int, List<Pair<Achievement, Long>>>>()
        val features = PlayerFeatures(
            playerId = player.id,
            wheelPlayer = player,
            achievementRegistry = InMemoryAchievementRegistry(listOf(CASTLEMANIA)),
            sendCyclopediaCharacterAchievements = { secrets, achievements -> sends.add(secrets to achievements) },
        )

        features.achiev().add(1, message = false, timestamp = 77L)
        features.achiev().sendUnlockedSecretAchievements()

        assertThat(sends).containsExactly(1 to listOf(CASTLEMANIA to 77L))
    }

    // --- composed animus mastery component (player.cpp:12812) -----------------------

    @Test
    fun `player features compose one animus mastery component per player`() {
        val features = newPlayer().features

        assertThat(features.animusMastery()).isSameAs(features.animusMastery)
        assertThat(features.animusMastery().getPoints()).isZero()

        features.animusMastery().add("Dragon")

        assertThat(features.animusMastery().has("DRAGON")).isTrue()
    }

    @Test
    fun `player features expose the injected animus mastery instance`() {
        val injected = AnimusMastery(monsterXpMultiplier = 100f, monstersXpMultiplier = 0.1f)
        val features = PlayerFeatures(playerId = 7, animusMastery = injected)

        assertThat(features.animusMastery()).isSameAs(injected)
    }
}
