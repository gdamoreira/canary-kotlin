package org.opentibia.canary.config

data class FeatureFlags(
    // Wheel system
    val toggleWheelSystem: Boolean = true,
    val wheelPointsPerLevel: Int = 1,
    val wheelMonkQuestBonus: Int = 10,
    val wheelAtelierRevealGreaterCost: Int = 6000000,
    val wheelAtelierRevealLesserCost: Int = 125000,
    val wheelAtelierRevealRegularCost: Int = 1000000,
    val wheelAtelierRotateGreaterCost: Int = 500000,
    val wheelAtelierRotateLesserCost: Int = 125000,
    val wheelAtelierRotateRegularCost: Int = 250000,

    // Reward chest
    val rewardChestCollectEnabled: Boolean = true,
    val rewardChestMaxCollectItems: Int = 200,

    // Offline training
    val rateOfflineTrainingSpeed: Float = 1.0f,

    // Weapon proficiency
    val weaponProficiencyGainMultiplier: Float = 0.33f,
    val weaponProficiencyMaxLevels: Int = 10,
    val weaponProficiencyMaxPerksPerLevel: Int = 6,

    // Hazard system
    val toggleHazardSystem: Boolean = true,
    val hazardCriticalChance: Int = 750,
    val hazardCriticalInterval: Int = 2000,
    val hazardCriticalMultiplier: Int = 25,
    val hazardDamageMultiplier: Int = 200,
    val hazardDefenseMultiplier: Int = 0,
    val hazardDodgeMultiplier: Int = 85,
    val hazardExpBonusMultiplier: Float = 2.0f,
    val hazardLootBonusMultiplier: Int = 2,
    val hazardPodsDamage: Int = 5,
    val hazardPodsDropMultiplier: Int = 87,
    val hazardPodsTimeToDamage: Int = 2000,
    val hazardPodsTimeToSpawn: Int = 4000,
    val hazardSpawnPlunderMultiplier: Int = 25,

    // Forge
    val forgeMaxDust: Int = 225,
    val forgeAmountMultiplier: Float = 3.0f,
    val forgeBaseSuccessRate: Int = 50,
    val forgeBonusSuccessRate: Int = 15,
    val forgeConvergenceFusionDustCost: Int = 130,
    val forgeConvergenceTransferDustCost: Int = 160,
    val forgeCoreCost: Int = 50,
    val forgeCostOneSliver: Int = 20,
    val forgeFiendishCreaturesLimit: Int = 3,
    val forgeInfluencedCreaturesLimit: Int = 300,
    val forgeFusionDustCost: Int = 100,
    val forgeMaxItemTier: Int = 10,
    val forgeMaxSlivers: Int = 7,
    val forgeMinSlivers: Int = 3,
    val forgeSliverAmount: Int = 3,
    val forgeTierLossReduction: Int = 50,
    val forgeTransferDustCost: Int = 100,
    val forgeFiendishIntervalTime: String = "1",
    val forgeFiendishIntervalType: String = "hour",

    // Tibiadrome concoction
    val tibiadromeConcoctionCooldown: Int = 24 * 60 * 60,
    val tibiadromeConcoctionDuration: Int = 60 * 60,
    val tibiadromeConcoctionTickType: String = "online"
)

object FeatureFlagsLoader {

    fun load(environment: Map<String, String> = System.getenv()): FeatureFlags {
        return FeatureFlags(
            toggleWheelSystem = environment.bool("CANARY_TOGGLE_WHEELSYSTEM", true),
            wheelPointsPerLevel = environment.int("CANARY_WHEEL_POINTS_PER_LEVEL", 1),
            wheelMonkQuestBonus = environment.int("CANARY_WHEEL_MONK_QUEST_BONUS", 10),
            wheelAtelierRevealGreaterCost = environment.int("CANARY_WHEEL_ATELIER_REVEAL_GREATER_COST", 6000000),
            wheelAtelierRevealLesserCost = environment.int("CANARY_WHEEL_ATELIER_REVEAL_LESSER_COST", 125000),
            wheelAtelierRevealRegularCost = environment.int("CANARY_WHEEL_ATELIER_REVEAL_REGULAR_COST", 1000000),
            wheelAtelierRotateGreaterCost = environment.int("CANARY_WHEEL_ATELIER_ROTATE_GREATER_COST", 500000),
            wheelAtelierRotateLesserCost = environment.int("CANARY_WHEEL_ATELIER_ROTATE_LESSER_COST", 125000),
            wheelAtelierRotateRegularCost = environment.int("CANARY_WHEEL_ATELIER_ROTATE_REGULAR_COST", 250000),

            rewardChestCollectEnabled = environment.bool("CANARY_REWARD_CHEST_COLLECT_ENABLED", true),
            rewardChestMaxCollectItems = environment.int("CANARY_REWARD_CHEST_MAX_COLLECT_ITEMS", 200),

            rateOfflineTrainingSpeed = environment.float("CANARY_RATE_OFFLINE_TRAINING_SPEED", 1.0f),

            weaponProficiencyGainMultiplier = environment.float("CANARY_WEAPON_PROFICIENCY_GAIN_MULTIPLIER", 0.33f),
            weaponProficiencyMaxLevels = environment.int("CANARY_WEAPON_PROFICIENCY_MAX_LEVELS", 10),
            weaponProficiencyMaxPerksPerLevel = environment.int("CANARY_WEAPON_PROFICIENCY_MAX_PERKS_PER_LEVEL", 6),

            toggleHazardSystem = environment.bool("CANARY_TOGGLE_HAZARDSYSTEM", true),
            hazardCriticalChance = environment.int("CANARY_HAZARD_CRITICAL_CHANCE", 750),
            hazardCriticalInterval = environment.int("CANARY_HAZARD_CRITICAL_INTERVAL", 2000),
            hazardCriticalMultiplier = environment.int("CANARY_HAZARD_CRITICAL_MULTIPLIER", 25),
            hazardDamageMultiplier = environment.int("CANARY_HAZARD_DAMAGE_MULTIPLIER", 200),
            hazardDefenseMultiplier = environment.int("CANARY_HAZARD_DEFENSE_MULTIPLIER", 0),
            hazardDodgeMultiplier = environment.int("CANARY_HAZARD_DODGE_MULTIPLIER", 85),
            hazardExpBonusMultiplier = environment.float("CANARY_HAZARD_EXP_BONUS_MULTIPLIER", 2.0f),
            hazardLootBonusMultiplier = environment.int("CANARY_HAZARD_LOOT_BONUS_MULTIPLIER", 2),
            hazardPodsDamage = environment.int("CANARY_HAZARD_PODS_DAMAGE", 5),
            hazardPodsDropMultiplier = environment.int("CANARY_HAZARD_PODS_DROP_MULTIPLIER", 87),
            hazardPodsTimeToDamage = environment.int("CANARY_HAZARD_PODS_TIME_TO_DAMAGE", 2000),
            hazardPodsTimeToSpawn = environment.int("CANARY_HAZARD_PODS_TIME_TO_SPAWN", 4000),
            hazardSpawnPlunderMultiplier = environment.int("CANARY_HAZARD_SPAWN_PLUNDER_MULTIPLIER", 25),

            forgeMaxDust = environment.int("CANARY_FORGE_MAX_DUST", 225),
            forgeAmountMultiplier = environment.float("CANARY_FORGE_AMOUNT_MULTIPLIER", 3.0f),
            forgeBaseSuccessRate = environment.int("CANARY_FORGE_BASE_SUCCESS_RATE", 50),
            forgeBonusSuccessRate = environment.int("CANARY_FORGE_BONUS_SUCCESS_RATE", 15),
            forgeConvergenceFusionDustCost = environment.int("CANARY_FORGE_CONVERGENCE_FUSION_DUST_COST", 130),
            forgeConvergenceTransferDustCost = environment.int("CANARY_FORGE_CONVERGENCE_TRANSFER_DUST_COST", 160),
            forgeCoreCost = environment.int("CANARY_FORGE_CORE_COST", 50),
            forgeCostOneSliver = environment.int("CANARY_FORGE_COST_ONE_SLIVER", 20),
            forgeFiendishCreaturesLimit = environment.int("CANARY_FORGE_FIENDISH_CREATURES_LIMIT", 3),
            forgeInfluencedCreaturesLimit = environment.int("CANARY_FORGE_INFLUENCED_CREATURES_LIMIT", 300),
            forgeFusionDustCost = environment.int("CANARY_FORGE_FUSION_DUST_COST", 100),
            forgeMaxItemTier = environment.int("CANARY_FORGE_MAX_ITEM_TIER", 10),
            forgeMaxSlivers = environment.int("CANARY_FORGE_MAX_SLIVERS", 7),
            forgeMinSlivers = environment.int("CANARY_FORGE_MIN_SLIVERS", 3),
            forgeSliverAmount = environment.int("CANARY_FORGE_SLIVER_AMOUNT", 3),
            forgeTierLossReduction = environment.int("CANARY_FORGE_TIER_LOSS_REDUCTION", 50),
            forgeTransferDustCost = environment.int("CANARY_FORGE_TRANSFER_DUST_COST", 100),
            forgeFiendishIntervalTime = environment.string("CANARY_FORGE_FIENDISH_INTERVAL_TIME", "1"),
            forgeFiendishIntervalType = environment.string("CANARY_FORGE_FIENDISH_INTERVAL_TYPE", "hour"),

            tibiadromeConcoctionCooldown = environment.int("CANARY_TIBIADROME_CONCOCTION_COOLDOWN", 24 * 60 * 60),
            tibiadromeConcoctionDuration = environment.int("CANARY_TIBIADROME_CONCOCTION_DURATION", 60 * 60),
            tibiadromeConcoctionTickType = environment.string("CANARY_TIBIADROME_CONCOCTION_TICK_TYPE", "online")
        )
    }

    private fun Map<String, String>.bool(key: String, default: Boolean): Boolean =
        this[key]?.lowercase()?.toBooleanStrictOrNull() ?: default

    private fun Map<String, String>.int(key: String, default: Int): Int =
        this[key]?.toIntOrNull() ?: default

    private fun Map<String, String>.float(key: String, default: Float): Float =
        this[key]?.toFloatOrNull() ?: default

    private fun Map<String, String>.string(key: String, default: String): String =
        this[key] ?: default
}
