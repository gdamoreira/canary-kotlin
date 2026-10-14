package org.opentibia.canary.creatures.monsters

import org.opentibia.canary.creatures.CreatureIcon
import org.opentibia.canary.creatures.CreatureIconModifications_t
import org.opentibia.canary.creatures.Monster
import kotlin.math.ceil

/**
 * Port of the `Monster` forge/hazard/soul-pit surface:
 * - `Monster::applyStacks` (src/creatures/monsters/monster.cpp:3857-3864)
 * - `Monster::configureForgeSystem` (monster.cpp:3866-3889)
 * - `Monster::canBeForgeMonster` (monster.cpp:3891-3893)
 * - `Monster::isForgeCreature`/`Monster::setForgeMonster` (monster.cpp:3895-3901)
 * - `Monster::getForgeStack`/`Monster::setForgeStack` (monster.cpp:3903-3909)
 * - `Monster::getMonsterForgeClassification`/`setMonsterForgeClassification`
 *   (monster.cpp:3911-3918)
 * - `Monster::setTimeToChangeFiendish`/`getTimeToChangeFiendish`
 *   (monster.cpp:3920-3926)
 * - `Monster::clearFiendishStatus` (monster.cpp:3932-3943)
 * - `Monster::canDropLoot`/`Monster::getRaceId` (monster.cpp:3945-3947, 3144-3146)
 * - `Monster::setSoulPitStack` (monster.cpp:3201-3208) and the hazard accessors
 *   (monster.cpp:3148-3198)
 *
 * The world side effects stay injected: `setIcon` runs on the owning [Monster]
 * (`Creature::setIcon`, creature.hpp:347-350), while `updateCreatureIcon`
 * (`g_game().updateCreatureIcon`), `sendUpdateCreature`
 * (`g_game().sendUpdateCreature`), `registerCreatureEvent`
 * (`Creature::registerCreatureEvent`, the Lua event registry) and
 * `setDropLoot`/`setSkillLoss` stay with the caller. `getHealthMultiplier`
 * reads the `RATE_*_HEALTH` config in Canary (monsters.cpp:332-334), so
 * [clearFiendishStatus] takes the already-resolved multiplier.
 */
object MonsterForge {

    /** `configureForgeSystem` dust event name (monster.cpp:3884). */
    const val FORGE_SYSTEM_MONSTER_EVENT: String = "ForgeSystemMonster"

    /** The fiendish stack literal (monster.cpp:3872). */
    const val FIENDISH_STACK: Int = 15

    /** `normal_random(1, 5)` influenced draw bounds (monster.cpp:3877). */
    const val INFLUENCED_MIN_STACK: Int = 1
    const val INFLUENCED_MAX_STACK: Int = 5

    /** `setSoulPitStack` boss-stack sentinel (monster.cpp:3202). */
    const val SOUL_PIT_BOSS_STACK: Int = 40

    /** `Creature::creatureIcons` keys used by the forge/soul-pit status. */
    const val ICON_KEY_FORGE: String = "forge"
    const val ICON_KEY_SOUL_PIT: String = "soulpit"

    /** `Monster::getForgeStack` (monster.cpp:3903-3905). */
    fun getForgeStack(state: MonsterTypeState): Int = state.forgeStack

    /** `Monster::getMonsterForgeClassification` (monster.cpp:3911-3914). */
    fun getMonsterForgeClassification(state: MonsterTypeState): ForgeClassification =
        state.monsterForgeClassification

    /** `Monster::setMonsterForgeClassification` (monster.cpp:3916-3918). */
    fun setMonsterForgeClassification(state: MonsterTypeState, classification: ForgeClassification) {
        state.monsterForgeClassification = classification
    }

    /** `Monster::setTimeToChangeFiendish` (monster.cpp:3920-3922). */
    fun setTimeToChangeFiendish(state: MonsterTypeState, time: Long) {
        state.timeToChangeFiendish = time
    }

    /** `Monster::getTimeToChangeFiendish` (monster.cpp:3924-3926). */
    fun getTimeToChangeFiendish(state: MonsterTypeState): Long = state.timeToChangeFiendish

    /** `Monster::isForgeCreature` (monster.cpp:3895-3897). */
    fun isForgeCreature(state: MonsterTypeState): Boolean = state.monsterType?.info?.isForgeCreature == true

    /** `Monster::setForgeMonster` (monster.cpp:3899-3901), writes the type flag. */
    fun setForgeMonster(state: MonsterTypeState, forge: Boolean) {
        state.monsterType?.info?.isForgeCreature = forge
    }

    /** `Monster::canDropLoot` (monster.cpp:3945-3947). */
    fun canDropLoot(state: MonsterTypeState): Boolean =
        state.monsterType?.info?.lootItems?.isNotEmpty() == true

    /** `Monster::getRaceId` (monster.cpp:3144-3146). */
    fun getRaceId(state: MonsterTypeState): Int = state.monsterType?.info?.raceid ?: 0

    /** `Monster::isRewardBoss` (monster.cpp:291-293). */
    fun isRewardBoss(state: MonsterTypeState): Boolean = state.monsterType?.info?.isRewardBoss == true

    /**
     * `Monster::canBeForgeMonster` (monster.cpp:3891-3893): a monster can be
     * forged while its stack is still zero, it is not a summon or reward boss,
     * it drops loot, the type is forge-enabled and it has a bestiary race id.
     */
    fun canBeForgeMonster(state: MonsterTypeState, isSummon: Boolean): Boolean =
        state.forgeStack == 0 &&
            !isSummon &&
            !isRewardBoss(state) &&
            canDropLoot(state) &&
            isForgeCreature(state) &&
            getRaceId(state) > 0

    /**
     * `Monster::applyStacks` (monster.cpp:3857-3864): scales the current max
     * health by `1 + (15 * forgeStack + 35) / 100.f` (float arithmetic, rounded
     * up) and applies it to both health and max health.
     */
    fun applyStacks(state: MonsterTypeState, monster: Monster) {
        val percentToIncrement = 1 + (15 * state.forgeStack + 35) / 100f
        val newHealth = ceil(monster.maxHealth.toFloat() * percentToIncrement).toInt()
        monster.maxHealth = newHealth
        monster.health = newHealth
    }

    /**
     * `Monster::setForgeStack` (monster.cpp:3907-3909): stores the stack and
     * immediately applies the scaled health.
     */
    fun setForgeStack(state: MonsterTypeState, monster: Monster, stack: Int) {
        state.forgeStack = stack
        applyStacks(state, monster)
    }

    /**
     * `Monster::configureForgeSystem` (monster.cpp:3866-3889): a forgeable
     * monster gets the fiendish (stack 15, no stack count on the icon) or the
     * influenced (drawn 1..5 when no stack was given) forge icon, is registered
     * for the `ForgeSystemMonster` dust event and has its creature update sent.
     * A non-forgeable monster is a complete no-op.
     */
    fun configureForgeSystem(
        state: MonsterTypeState,
        monster: Monster,
        stack: Int = 0,
        isSummon: Boolean,
        normalRandom: (Int, Int) -> Int = { min, _ -> min },
        updateCreatureIcon: () -> Unit = {},
        registerCreatureEvent: (String) -> Unit = {},
        sendUpdateCreature: () -> Unit = {},
    ) {
        if (!canBeForgeMonster(state, isSummon)) {
            return
        }

        if (state.monsterForgeClassification == ForgeClassification.Fiendish) {
            setForgeStack(state, monster, FIENDISH_STACK)
            monster.setIcon(ICON_KEY_FORGE, CreatureIcon(CreatureIconModifications_t.Fiendish, 0))
            updateCreatureIcon()
        } else if (state.monsterForgeClassification == ForgeClassification.Influenced) {
            var influencedStack = stack
            if (influencedStack == 0) {
                influencedStack = normalRandom(INFLUENCED_MIN_STACK, INFLUENCED_MAX_STACK)
            }
            setForgeStack(state, monster, influencedStack)
            monster.setIcon(ICON_KEY_FORGE, CreatureIcon(CreatureIconModifications_t.Influenced, influencedStack))
            updateCreatureIcon()
        }

        registerCreatureEvent(FORGE_SYSTEM_MONSTER_EVENT)
        sendUpdateCreature()
    }

    /**
     * `Monster::clearFiendishStatus` (monster.cpp:3932-3943): resets the forge
     * latches, restores the health to `info.health`/`info.healthMax` scaled by
     * `getHealthMultiplier()` and drops the forge icon.
     */
    fun clearFiendishStatus(
        state: MonsterTypeState,
        monster: Monster,
        healthMultiplier: Double = 1.0,
        updateCreatureIcon: () -> Unit = {},
        sendUpdateCreature: () -> Unit = {},
    ) {
        state.timeToChangeFiendish = 0L
        state.forgeStack = 0
        state.monsterForgeClassification = ForgeClassification.Normal

        val info = state.monsterType?.info
        monster.health = ((info?.health ?: 0) * healthMultiplier).toInt()
        monster.maxHealth = ((info?.healthMax ?: 0) * healthMultiplier).toInt()

        monster.removeIcon(ICON_KEY_FORGE)
        updateCreatureIcon()
        sendUpdateCreature()
    }

    /**
     * `Monster::setSoulPitStack` (monster.cpp:3201-3208): a stack of 40 is the
     * boss soul pit (exclamation icon, no stack count, skill loss unless the
     * monster is a summon); any other stack shows the opened soul pit with its
     * count. Both scale the health through [setForgeStack], latch the soul-pit
     * status and disable loot.
     */
    fun setSoulPitStack(
        state: MonsterTypeState,
        monster: Monster,
        stack: Int,
        isSummon: Boolean = false,
        setDropLoot: (Boolean) -> Unit = {},
        setSkillLoss: (Boolean) -> Unit = { monster.setSkillLoss(it) },
    ) {
        val isBoss = stack == SOUL_PIT_BOSS_STACK
        val icon = if (isBoss) {
            CreatureIconModifications_t.ReducedHealthExclamation
        } else {
            CreatureIconModifications_t.ReducedHealth
        }
        setForgeStack(state, monster, stack)
        monster.setIcon(ICON_KEY_SOUL_PIT, CreatureIcon(icon, if (isBoss) 0 else stack))
        state.soulPit = true
        setDropLoot(false)
        setSkillLoss(isBoss && !isSummon)
    }

    /** `Monster::getHazard` (monster.cpp:3148-3150). */
    fun getHazard(state: MonsterTypeState): Boolean = state.hazard

    /** `Monster::setHazard` (monster.cpp:3152-3154). */
    fun setHazard(state: MonsterTypeState, value: Boolean) {
        state.hazard = value
    }

    /** `Monster::getHazardSystemCrit` (monster.cpp:3156-3158). */
    fun getHazardSystemCrit(state: MonsterTypeState): Boolean = state.hazardCrit

    /** `Monster::setHazardSystemCrit` (monster.cpp:3160-3162). */
    fun setHazardSystemCrit(state: MonsterTypeState, value: Boolean) {
        state.hazardCrit = value
    }

    /** `Monster::getHazardSystemDodge` (monster.cpp:3164-3166). */
    fun getHazardSystemDodge(state: MonsterTypeState): Boolean = state.hazardDodge

    /** `Monster::setHazardSystemDodge` (monster.cpp:3168-3170). */
    fun setHazardSystemDodge(state: MonsterTypeState, value: Boolean) {
        state.hazardDodge = value
    }

    /** `Monster::getHazardSystemDamageBoost` (monster.cpp:3172-3174). */
    fun getHazardSystemDamageBoost(state: MonsterTypeState): Boolean = state.hazardDamageBoost

    /** `Monster::setHazardSystemDamageBoost` (monster.cpp:3176-3178). */
    fun setHazardSystemDamageBoost(state: MonsterTypeState, value: Boolean) {
        state.hazardDamageBoost = value
    }

    /** `Monster::getHazardSystemDefenseBoost` (monster.cpp:3180-3182). */
    fun getHazardSystemDefenseBoost(state: MonsterTypeState): Boolean = state.hazardDefenseBoost

    /** `Monster::setHazardSystemDefenseBoost` (monster.cpp:3184-3186). */
    fun setHazardSystemDefenseBoost(state: MonsterTypeState, value: Boolean) {
        state.hazardDefenseBoost = value
    }

    /** `Monster::getSoulPit` (monster.cpp:3188-3190). */
    fun getSoulPit(state: MonsterTypeState): Boolean = state.soulPit

    /** `Monster::setSoulPit` (monster.cpp:3192-3194). */
    fun setSoulPit(state: MonsterTypeState, value: Boolean) {
        state.soulPit = value
    }
}
