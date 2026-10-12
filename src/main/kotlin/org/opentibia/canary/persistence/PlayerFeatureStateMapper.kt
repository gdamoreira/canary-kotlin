package org.opentibia.canary.persistence

import org.opentibia.canary.creatures.PlayerFeatures
import org.opentibia.canary.database.PlayerFeatureState
import org.opentibia.canary.database.WeaponProficiencyState
import org.opentibia.canary.systems.offlinetraining.Skill
import org.opentibia.canary.systems.proficiency.WeaponProficiencyData
import org.opentibia.canary.systems.wheel.WheelSlots

/**
 * Pure mapping between the persisted [PlayerFeatureState] DTO and the in-memory
 * [PlayerFeatures] holder.
 *
 * Field correspondence (Kotlin holder -> Canary source):
 *  - wheel points        -> `PlayerWheel::getPointsBySlotType`
 *    (src/creatures/players/components/wheel/player_wheel.cpp:4016)
 *  - offline training    -> `Player::offlineTrainingTime` (milliseconds in the
 *    engine, seconds in the DB; `iologindata_load_player.cpp:179` multiplies by
 *    1000, `iologindata_save_player.cpp:323` divides by 1000)
 *  - daily reward        -> `Player::isDailyReward` (iologindata_load_player.cpp:166)
 *  - forge dust/level    -> `Player::getForgeDusts` / `getForgeDustLevel`
 *    (iologindata_save_player.cpp:265-266)
 *  - bestiary kills      -> `Player::getBestiaryKillCount` (player.cpp:12975)
 *  - charms              -> `Player::charmPoints` and friends (player.cpp:1830-1895)
 *  - mounts              -> reserved `PlayerStorage` mount range + current mount
 *  - familiars           -> `PlayerFamiliars` reserved range + looktype
 *  - weapon proficiency  -> `WeaponProficiency proficiency` (weapon_proficiency.hpp:135)
 *
 * The mapper performs no I/O and no gameplay policy; it only reads and replaces
 * the state that the repository persists.
 */
object PlayerFeatureStateMapper {

    fun toFeatureState(playerId: Long, features: PlayerFeatures): PlayerFeatureState {
        val bestiary = features.bestiary
        val mounts = features.mounts
        val familiars = features.familiars
        val offlineTraining = features.offlineTraining

        return PlayerFeatureState(
            playerId = playerId,
            wheelPoints = features.wheel.pointsBySlot.entries
                .associate { (slot, points) -> slot.value to points },
            offlineTrainingTime = offlineTraining.getOfflineTrainingTime() / MILLIS_PER_SECOND,
            offlineTrainingSkill = offlineTraining.getOfflineTrainingSkill().value,
            dailyReward = features.dailyReward.isDailyReward,
            forgeDusts = features.forge.getForgeDusts(),
            forgeDustLevel = features.forge.getForgeDustLevel(),
            bestiaryKillCounts = bestiary.getBestiaryKillCounts(),
            charmPoints = bestiary.getCharmPoints(),
            minorCharmEchoes = bestiary.getMinorCharmEchoes(),
            maxCharmPoints = bestiary.getMaxCharmPoints(),
            maxMinorCharmEchoes = bestiary.getMaxMinorCharmEchoes(),
            charmExpansion = bestiary.hasCharmExpansion,
            usedRunesBit = bestiary.getUsedRunesBit(),
            unlockedRunesBit = bestiary.getUnlockedRunesBit(),
            mounts = mounts.getOwnedMounts(),
            currentMount = mounts.getCurrentMount(),
            randomizeMount = if (mounts.isRandomMounted()) 1 else 0,
            familiars = familiars.familiars.map { it.lookType }.toSet(),
            familiarLookType = familiars.familiarLooktype,
            weaponProficiencies = features.weaponProficiencies.entries
                .associate { (weaponId, data) ->
                    weaponId to WeaponProficiencyState(experience = data.experience, mastered = data.mastered)
                },
        )
    }

    fun applyTo(state: PlayerFeatureState, features: PlayerFeatures) {
        applyWheel(state, features)
        applyOfflineTraining(state, features)
        applyDailyRewardAndForge(state, features)
        applyBestiary(state, features)
        applyMounts(state, features)
        applyFamiliars(state, features)
        applyWeaponProficiencies(state, features)
    }

    private fun applyWheel(state: PlayerFeatureState, features: PlayerFeatures) {
        features.wheel.pointsBySlot.clear()
        for ((slotValue, points) in state.wheelPoints) {
            WheelSlots.fromValue(slotValue)?.let { features.wheel.setPointsBySlotType(it, points) }
        }
    }

    private fun applyOfflineTraining(state: PlayerFeatureState, features: PlayerFeatures) {
        val offlineTraining = features.offlineTraining
        offlineTraining.setOfflineTrainingSkill(skillFromValue(state.offlineTrainingSkill))

        val currentTime = offlineTraining.getOfflineTrainingTime()
        if (currentTime > 0) {
            offlineTraining.removeOfflineTrainingTime(currentTime)
        }
        offlineTraining.addOfflineTrainingTime(state.offlineTrainingTime * MILLIS_PER_SECOND)
    }

    private fun applyDailyRewardAndForge(state: PlayerFeatureState, features: PlayerFeatures) {
        features.dailyReward.setDailyReward(state.dailyReward)

        features.forge.setForgeDusts(state.forgeDusts)
        val currentLevel = features.forge.getForgeDustLevel()
        if (currentLevel > 0) {
            features.forge.removeForgeDustLevel(currentLevel)
        }
        features.forge.addForgeDustLevel(state.forgeDustLevel)
    }

    private fun applyBestiary(state: PlayerFeatureState, features: PlayerFeatures) {
        val bestiary = features.bestiary
        bestiary.setBestiaryKillCounts(state.bestiaryKillCounts)
        bestiary.setCharmPoints(state.charmPoints)
        bestiary.setMinorCharmEchoes(state.minorCharmEchoes)
        bestiary.setMaxCharmPoints(state.maxCharmPoints)
        bestiary.setMaxMinorCharmEchoes(state.maxMinorCharmEchoes)
        bestiary.hasCharmExpansion = state.charmExpansion
        bestiary.setUsedRunesBit(state.usedRunesBit)
        bestiary.setUnlockedRunesBit(state.unlockedRunesBit)
    }

    private fun applyMounts(state: PlayerFeatureState, features: PlayerFeatures) {
        features.mounts.setOwnedMounts(state.mounts)
        features.mounts.setCurrentMount(state.currentMount)
        features.mounts.setRandomMount(state.randomizeMount)
    }

    private fun applyFamiliars(state: PlayerFeatureState, features: PlayerFeatures) {
        features.familiars.familiars.clear()
        for (lookType in state.familiars) {
            features.familiars.addFamiliar(lookType)
        }
        features.familiars.setFamiliarLooktype(state.familiarLookType)
    }

    private fun applyWeaponProficiencies(state: PlayerFeatureState, features: PlayerFeatures) {
        features.weaponProficiencies.clear()
        for ((weaponId, proficiency) in state.weaponProficiencies) {
            features.weaponProficiencies[weaponId] = WeaponProficiencyData(
                experience = proficiency.experience,
                mastered = proficiency.mastered,
            )
        }
    }

    private fun skillFromValue(value: Int): Skill =
        Skill.entries.firstOrNull { it.value == value } ?: Skill.SKILL_NONE

    /** `iologindata_load_player.cpp:179` / `iologindata_save_player.cpp:323`. */
    private const val MILLIS_PER_SECOND = 1000
}
