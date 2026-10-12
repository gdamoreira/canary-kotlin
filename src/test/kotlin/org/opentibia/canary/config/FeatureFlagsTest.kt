package org.opentibia.canary.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FeatureFlagsTest {

    @Test
    fun `defaults match Canary advanced feature configuration`() {
        val flags = FeatureFlagsLoader.load(environment = emptyMap())

        // Wheel system
        assertThat(flags.toggleWheelSystem).isTrue()
        assertThat(flags.wheelPointsPerLevel).isEqualTo(1)
        assertThat(flags.wheelMonkQuestBonus).isEqualTo(10)
        assertThat(flags.wheelAtelierRevealGreaterCost).isEqualTo(6000000)
        assertThat(flags.wheelAtelierRevealLesserCost).isEqualTo(125000)
        assertThat(flags.wheelAtelierRevealRegularCost).isEqualTo(1000000)
        assertThat(flags.wheelAtelierRotateGreaterCost).isEqualTo(500000)
        assertThat(flags.wheelAtelierRotateLesserCost).isEqualTo(125000)
        assertThat(flags.wheelAtelierRotateRegularCost).isEqualTo(250000)

        // Reward chest
        assertThat(flags.rewardChestCollectEnabled).isTrue()
        assertThat(flags.rewardChestMaxCollectItems).isEqualTo(200)

        // Offline training
        assertThat(flags.rateOfflineTrainingSpeed).isEqualTo(1.0f)

        // Weapon proficiency
        assertThat(flags.weaponProficiencyGainMultiplier).isEqualTo(0.33f)
        assertThat(flags.weaponProficiencyMaxLevels).isEqualTo(10)
        assertThat(flags.weaponProficiencyMaxPerksPerLevel).isEqualTo(6)

        // Hazard system
        assertThat(flags.toggleHazardSystem).isTrue()
        assertThat(flags.hazardCriticalChance).isEqualTo(750)
        assertThat(flags.hazardCriticalInterval).isEqualTo(2000)
        assertThat(flags.hazardCriticalMultiplier).isEqualTo(25)
        assertThat(flags.hazardDamageMultiplier).isEqualTo(200)
        assertThat(flags.hazardDefenseMultiplier).isEqualTo(0)
        assertThat(flags.hazardDodgeMultiplier).isEqualTo(85)
        assertThat(flags.hazardExpBonusMultiplier).isEqualTo(2.0f)
        assertThat(flags.hazardLootBonusMultiplier).isEqualTo(2)
        assertThat(flags.hazardPodsDamage).isEqualTo(5)
        assertThat(flags.hazardPodsDropMultiplier).isEqualTo(87)
        assertThat(flags.hazardPodsTimeToDamage).isEqualTo(2000)
        assertThat(flags.hazardPodsTimeToSpawn).isEqualTo(4000)
        assertThat(flags.hazardSpawnPlunderMultiplier).isEqualTo(25)

        // Forge
        assertThat(flags.forgeMaxDust).isEqualTo(225)
        assertThat(flags.forgeAmountMultiplier).isEqualTo(3.0f)
        assertThat(flags.forgeBaseSuccessRate).isEqualTo(50)
        assertThat(flags.forgeBonusSuccessRate).isEqualTo(15)
        assertThat(flags.forgeConvergenceFusionDustCost).isEqualTo(130)
        assertThat(flags.forgeConvergenceTransferDustCost).isEqualTo(160)
        assertThat(flags.forgeCoreCost).isEqualTo(50)
        assertThat(flags.forgeCostOneSliver).isEqualTo(20)
        assertThat(flags.forgeFiendishCreaturesLimit).isEqualTo(3)
        assertThat(flags.forgeInfluencedCreaturesLimit).isEqualTo(300)
        assertThat(flags.forgeFusionDustCost).isEqualTo(100)
        assertThat(flags.forgeMaxItemTier).isEqualTo(10)
        assertThat(flags.forgeMaxSlivers).isEqualTo(7)
        assertThat(flags.forgeMinSlivers).isEqualTo(3)
        assertThat(flags.forgeSliverAmount).isEqualTo(3)
        assertThat(flags.forgeTierLossReduction).isEqualTo(50)
        assertThat(flags.forgeTransferDustCost).isEqualTo(100)
        assertThat(flags.forgeFiendishIntervalTime).isEqualTo("1")
        assertThat(flags.forgeFiendishIntervalType).isEqualTo("hour")

        // Tibiadrome concoction
        assertThat(flags.tibiadromeConcoctionCooldown).isEqualTo(24 * 60 * 60)
        assertThat(flags.tibiadromeConcoctionDuration).isEqualTo(60 * 60)
        assertThat(flags.tibiadromeConcoctionTickType).isEqualTo("online")
    }

    @Test
    fun `overrides wheel system flags from CANARY environment variables`() {
        val env = mapOf(
            "CANARY_TOGGLE_WHEELSYSTEM" to "false",
            "CANARY_WHEEL_POINTS_PER_LEVEL" to "2",
            "CANARY_WHEEL_MONK_QUEST_BONUS" to "20",
            "CANARY_WHEEL_ATELIER_REVEAL_GREATER_COST" to "42"
        )

        val flags = FeatureFlagsLoader.load(environment = env)

        assertThat(flags.toggleWheelSystem).isFalse()
        assertThat(flags.wheelPointsPerLevel).isEqualTo(2)
        assertThat(flags.wheelMonkQuestBonus).isEqualTo(20)
        assertThat(flags.wheelAtelierRevealGreaterCost).isEqualTo(42)
    }

    @Test
    fun `overrides reward chest and offline training flags from CANARY environment variables`() {
        val env = mapOf(
            "CANARY_REWARD_CHEST_COLLECT_ENABLED" to "false",
            "CANARY_REWARD_CHEST_MAX_COLLECT_ITEMS" to "500",
            "CANARY_RATE_OFFLINE_TRAINING_SPEED" to "2.5"
        )

        val flags = FeatureFlagsLoader.load(environment = env)

        assertThat(flags.rewardChestCollectEnabled).isFalse()
        assertThat(flags.rewardChestMaxCollectItems).isEqualTo(500)
        assertThat(flags.rateOfflineTrainingSpeed).isEqualTo(2.5f)
    }

    @Test
    fun `overrides weapon proficiency flags from CANARY environment variables`() {
        val env = mapOf(
            "CANARY_WEAPON_PROFICIENCY_GAIN_MULTIPLIER" to "0.5",
            "CANARY_WEAPON_PROFICIENCY_MAX_LEVELS" to "20",
            "CANARY_WEAPON_PROFICIENCY_MAX_PERKS_PER_LEVEL" to "12"
        )

        val flags = FeatureFlagsLoader.load(environment = env)

        assertThat(flags.weaponProficiencyGainMultiplier).isEqualTo(0.5f)
        assertThat(flags.weaponProficiencyMaxLevels).isEqualTo(20)
        assertThat(flags.weaponProficiencyMaxPerksPerLevel).isEqualTo(12)
    }

    @Test
    fun `overrides hazard system flags from CANARY environment variables`() {
        val env = mapOf(
            "CANARY_TOGGLE_HAZARDSYSTEM" to "false",
            "CANARY_HAZARD_DAMAGE_MULTIPLIER" to "300",
            "CANARY_HAZARD_EXP_BONUS_MULTIPLIER" to "3.5",
            "CANARY_HAZARD_PODS_TIME_TO_SPAWN" to "1000"
        )

        val flags = FeatureFlagsLoader.load(environment = env)

        assertThat(flags.toggleHazardSystem).isFalse()
        assertThat(flags.hazardDamageMultiplier).isEqualTo(300)
        assertThat(flags.hazardExpBonusMultiplier).isEqualTo(3.5f)
        assertThat(flags.hazardPodsTimeToSpawn).isEqualTo(1000)
    }

    @Test
    fun `overrides forge flags from CANARY environment variables`() {
        val env = mapOf(
            "CANARY_FORGE_MAX_DUST" to "500",
            "CANARY_FORGE_AMOUNT_MULTIPLIER" to "4.5",
            "CANARY_FORGE_FUSION_DUST_COST" to "90",
            "CANARY_FORGE_FIENDISH_INTERVAL_TYPE" to "minute"
        )

        val flags = FeatureFlagsLoader.load(environment = env)

        assertThat(flags.forgeMaxDust).isEqualTo(500)
        assertThat(flags.forgeAmountMultiplier).isEqualTo(4.5f)
        assertThat(flags.forgeFusionDustCost).isEqualTo(90)
        assertThat(flags.forgeFiendishIntervalType).isEqualTo("minute")
    }

    @Test
    fun `overrides tibiadrome concoction flags from CANARY environment variables`() {
        val env = mapOf(
            "CANARY_TIBIADROME_CONCOCTION_COOLDOWN" to "60",
            "CANARY_TIBIADROME_CONCOCTION_DURATION" to "30",
            "CANARY_TIBIADROME_CONCOCTION_TICK_TYPE" to "offline"
        )

        val flags = FeatureFlagsLoader.load(environment = env)

        assertThat(flags.tibiadromeConcoctionCooldown).isEqualTo(60)
        assertThat(flags.tibiadromeConcoctionDuration).isEqualTo(30)
        assertThat(flags.tibiadromeConcoctionTickType).isEqualTo("offline")
    }

    @Test
    fun `falls back to defaults for invalid numeric and boolean overrides`() {
        val env = mapOf(
            "CANARY_WHEEL_POINTS_PER_LEVEL" to "not-a-number",
            "CANARY_RATE_OFFLINE_TRAINING_SPEED" to "not-a-float",
            "CANARY_TOGGLE_HAZARDSYSTEM" to "not-a-boolean",
            "CANARY_WEAPON_PROFICIENCY_MAX_LEVELS" to ""
        )

        val flags = FeatureFlagsLoader.load(environment = env)

        assertThat(flags.wheelPointsPerLevel).isEqualTo(1)
        assertThat(flags.rateOfflineTrainingSpeed).isEqualTo(1.0f)
        assertThat(flags.toggleHazardSystem).isTrue()
        assertThat(flags.weaponProficiencyMaxLevels).isEqualTo(10)
    }
}
