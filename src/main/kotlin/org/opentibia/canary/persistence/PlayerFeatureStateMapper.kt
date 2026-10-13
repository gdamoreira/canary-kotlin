package org.opentibia.canary.persistence

import org.opentibia.canary.creatures.PlayerFeatures
import org.opentibia.canary.database.CharmAssignmentState
import org.opentibia.canary.database.PlayerFeatureState
import org.opentibia.canary.database.WeaponProficiencyPerkState
import org.opentibia.canary.database.WeaponProficiencyState
import org.opentibia.canary.database.WheelGemGradeState
import org.opentibia.canary.database.WheelGemState as WheelGemRecord
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.bestiary.PlayerBestiaryState
import org.opentibia.canary.systems.offlinetraining.Skill
import org.opentibia.canary.systems.proficiency.ProficiencyPerk
import org.opentibia.canary.systems.proficiency.WeaponProficiencyBonus
import org.opentibia.canary.systems.proficiency.WeaponProficiencyData
import org.opentibia.canary.systems.wheel.PlayerWheelGem
import org.opentibia.canary.systems.wheel.WheelFragmentType
import org.opentibia.canary.systems.wheel.WheelGemAffinity
import org.opentibia.canary.systems.wheel.WheelGemBasicModifier
import org.opentibia.canary.systems.wheel.WheelGemQuality
import org.opentibia.canary.systems.wheel.WheelGemState
import org.opentibia.canary.systems.wheel.WheelGemSupremeModifier
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
 *  - charm assignments   -> `Player::charmsArray` raceId/tier per charm rune
 *    (`iologindata_save_player.cpp:487-497`, `iologindata_load_player.cpp:495-505`)
 *  - mounts              -> reserved `PlayerStorage` mount range + current mount
 *  - familiars           -> `PlayerFamiliars` reserved range + looktype
 *  - weapon proficiency  -> `WeaponProficiency proficiency` (weapon_proficiency.hpp:135),
 *    including the serialized `ProficiencyPerk` list (`weapon_proficiency.cpp:347-361`)
 *  - wheel gems          -> `PlayerWheel::m_revealedGems` / `m_activeGems` and the
 *    `m_basicGrades` / `m_supremeGrades` modifier grades (player_wheel.cpp:1727-1797)
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
        val wheelGems = features.wheel.gems

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
            charmAssignments = toCharmAssignments(bestiary),
            mounts = mounts.getOwnedMounts(),
            currentMount = mounts.getCurrentMount(),
            randomizeMount = if (mounts.isRandomMounted()) 1 else 0,
            familiars = familiars.familiars.map { it.lookType }.toSet(),
            familiarLookType = familiars.familiarLooktype,
            weaponProficiencies = features.weaponProficiencies.entries
                .associate { (weaponId, data) ->
                    weaponId to WeaponProficiencyState(
                        experience = data.experience,
                        mastered = data.mastered,
                        perks = toPerkStates(data.perks),
                    )
                },
            wheelGems = wheelGems.revealedGems.map { gem -> toWheelGemRecord(gem) },
            activeWheelGems = toActiveWheelGems(wheelGems),
            wheelGemGrades = toWheelGemGrades(wheelGems),
        )
    }

    private fun toCharmAssignments(bestiary: PlayerBestiaryState): Map<Int, CharmAssignmentState> {
        val result = linkedMapOf<Int, CharmAssignmentState>()
        for (charm in CharmRune.entries) {
            if (charm == CharmRune.CHARM_NONE) {
                continue
            }
            val raceId = bestiary.parseRacebyCharm(charm)
            val tier = bestiary.getCharmTier(charm)
            if (raceId != 0 || tier != 0) {
                result[charm.value] = CharmAssignmentState(raceId = raceId, tier = tier)
            }
        }
        return result
    }

    private fun toPerkStates(perks: List<ProficiencyPerk>): List<WeaponProficiencyPerkState> =
        perks.map { perk ->
            WeaponProficiencyPerkState(
                level = perk.level,
                index = perk.index,
                value = perk.value,
                spellId = perk.spellId,
                range = perk.range,
                bestiaryId = perk.bestiaryId,
                bestiaryName = perk.bestiaryName,
                augmentType = perk.augmentType,
                skillId = perk.skillId,
                element = perk.element,
                type = perk.type.value,
            )
        }

    private fun toWheelGemRecord(gem: PlayerWheelGem): WheelGemRecord = WheelGemRecord(
        locked = gem.locked,
        affinity = gem.affinity.value,
        quality = gem.quality.value,
        basicModifier1 = gem.basicModifier1.value,
        basicModifier2 = gem.basicModifier2?.value,
        supremeModifier = gem.supremeModifier?.value,
    )

    private fun toActiveWheelGems(gems: WheelGemState): Map<Int, Int> {
        val result = linkedMapOf<Int, Int>()
        gems.activeGems.forEachIndexed { affinity, gem ->
            if (gem == null) {
                return@forEachIndexed
            }
            val order = gems.revealedGems.indexOfFirst { it === gem }
            if (order >= 0) {
                result[affinity] = order
            }
        }
        return result
    }

    private fun toWheelGemGrades(gems: WheelGemState): List<WheelGemGradeState> {
        val result = mutableListOf<WheelGemGradeState>()
        gems.basicGrades.forEachIndexed { position, grade ->
            if (grade > 0) {
                result.add(WheelGemGradeState(WheelFragmentType.Lesser.value, position, grade))
            }
        }
        gems.supremeGrades.forEachIndexed { position, grade ->
            if (grade > 0) {
                result.add(WheelGemGradeState(WheelFragmentType.Greater.value, position, grade))
            }
        }
        return result
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
        applyWheelGems(state, features.wheel.gems)
    }

    private fun applyWheelGems(state: PlayerFeatureState, gems: WheelGemState) {
        gems.revealedGems.clear()
        for (record in state.wheelGems) {
            gems.revealedGems.add(
                PlayerWheelGem(
                    locked = record.locked,
                    affinity = WheelGemAffinity.entries.firstOrNull { it.value == record.affinity }
                        ?: WheelGemAffinity.Green,
                    quality = WheelGemQuality.entries.firstOrNull { it.value == record.quality }
                        ?: WheelGemQuality.Lesser,
                    basicModifier1 = WheelGemBasicModifier.entries.firstOrNull { it.value == record.basicModifier1 }
                        ?: WheelGemBasicModifier.General_PhysicalResistance,
                    basicModifier2 = record.basicModifier2?.let { value ->
                        WheelGemBasicModifier.entries.firstOrNull { it.value == value }
                    },
                    supremeModifier = record.supremeModifier?.let { value ->
                        WheelGemSupremeModifier.entries.firstOrNull { it.value == value }
                    },
                ),
            )
        }

        gems.activeGems.fill(null)
        for ((affinity, order) in state.activeWheelGems) {
            if (affinity !in gems.activeGems.indices) {
                continue
            }
            gems.activeGems[affinity] = gems.revealedGems.getOrNull(order) ?: continue
        }

        gems.basicGrades.fill(0)
        gems.supremeGrades.fill(0)
        for (entry in state.wheelGemGrades) {
            when (entry.fragmentType) {
                WheelFragmentType.Lesser.value ->
                    if (entry.position in gems.basicGrades.indices) {
                        gems.basicGrades[entry.position] = entry.grade
                    }
                WheelFragmentType.Greater.value ->
                    if (entry.position in gems.supremeGrades.indices) {
                        gems.supremeGrades[entry.position] = entry.grade
                    }
            }
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
        applyCharmAssignments(state, bestiary)
    }

    private fun applyCharmAssignments(state: PlayerFeatureState, bestiary: PlayerBestiaryState) {
        for (charm in CharmRune.entries) {
            if (charm == CharmRune.CHARM_NONE) {
                continue
            }
            bestiary.parseRacebyCharm(charm, true, 0)
            bestiary.setCharmTier(charm, 0)
        }
        for ((charmValue, assignment) in state.charmAssignments) {
            val charm = CharmRune.fromValue(charmValue) ?: continue
            if (charm == CharmRune.CHARM_NONE) {
                continue
            }
            bestiary.parseRacebyCharm(charm, true, assignment.raceId)
            bestiary.setCharmTier(charm, assignment.tier)
        }
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
                perks = proficiency.perks.mapTo(mutableListOf()) { perk -> toProficiencyPerk(perk) },
                mastered = proficiency.mastered,
            )
        }
    }

    private fun toProficiencyPerk(perk: WeaponProficiencyPerkState): ProficiencyPerk = ProficiencyPerk(
        level = perk.level,
        index = perk.index,
        value = perk.value,
        spellId = perk.spellId,
        range = perk.range,
        bestiaryId = perk.bestiaryId,
        bestiaryName = perk.bestiaryName,
        augmentType = perk.augmentType,
        skillId = perk.skillId,
        element = perk.element,
        type = WeaponProficiencyBonus.entries.firstOrNull { it.value == perk.type }
            ?: WeaponProficiencyBonus.ATTACK_DAMAGE,
    )

    private fun skillFromValue(value: Int): Skill =
        Skill.entries.firstOrNull { it.value == value } ?: Skill.SKILL_NONE

    /** `iologindata_load_player.cpp:179` / `iologindata_save_player.cpp:323`. */
    private const val MILLIS_PER_SECOND = 1000
}
