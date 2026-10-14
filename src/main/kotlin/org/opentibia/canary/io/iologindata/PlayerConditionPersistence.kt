package org.opentibia.canary.io.iologindata

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionAttributes
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionLight
import org.opentibia.canary.combat.condition.ConditionManaShield
import org.opentibia.canary.combat.condition.ConditionOutfit
import org.opentibia.canary.combat.condition.ConditionRegeneration
import org.opentibia.canary.combat.condition.ConditionSoul
import org.opentibia.canary.combat.condition.ConditionSpeed
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.io.PropWriteStream

private val logger = KotlinLogging.logger {}

/**
 * Save half of the condition codec: the `Condition::serialize` family
 * (`src/creatures/combat/condition.cpp:277-301` plus the subclass overrides at
 * :786-825, :1474-1488, :1652-1656, :1712-1720, :1832-1848, :2510-2535,
 * :2682-2686, :2862-2881) driven by the `IOLoginDataSave::savePlayerFirst`
 * loop (src/io/functions/iologindata_save_player.cpp:279-291). The decode half
 * lives in [IOLoginDataSubloads] and reads the exact same layout.
 *
 * The C++ loop iterates `player->conditions` (the active list
 * `Creature` holds) and writes only the `Condition::isPersistent()`
 * conditions, each terminated by `CONDITIONATTR_END`. The port keeps the same
 * active-list behavior and additionally serializes
 * `Player::storedConditionList` (player.hpp:1698), the list
 * [IOLoginDataSubloads.loadPlayerConditions] fills before the
 * `Player::onCreatureAppear` replay moves it into the active list
 * (player.cpp:12231-12234), so an offline load -> save round trip preserves
 * the persisted stream instead of clearing it. The two lists are disjoint:
 * the replay moves each stored condition through `addCondition` and clears
 * the stored list.
 *
 * PARITY GAPS:
 *  - The per-class attributes plus `CONDITIONATTR_END` are written; the
 *    `ConditionAttributes` payload (`SKILLS`/`STATS`/`BUFFS`/`ABSORBS`/
 *    `INCREASES`/`CHARM_CHANCE_MODIFIER`, condition.cpp:786-825) is not,
 *    because the Kotlin `ConditionAttributes` keeps its flat arrays private
 *    with no read accessor (`combat/condition` is outside this wave's file
 *    scope). A persistent `ConditionAttributes` therefore reloads with a
 *    zeroed payload; the gap is logged on save.
 *  - `Condition::serialize` always writes `CONDITIONATTR_TICKSOUND` and
 *    `CONDITIONATTR_ADDSOUND` (condition.cpp:289-295); the Kotlin
 *    `Condition.setParam` accepts but does not retain them (Condition.kt:151-157),
 *    so both are written as `SILENCE` (0) here. The decode half does not
 *    consume the stored value either.
 */
class PlayerConditionPersistence {

    /**
     * `IOLoginDataSave::savePlayerFirst` condition block
     * (iologindata_save_player.cpp:279-291): every persistent condition of the
     * player is serialized and closed with `CONDITIONATTR_END`; non-persistent
     * conditions are skipped by `Condition::isPersistent`
     * (condition.cpp:475-489). The C++ `PropWriteStream` out-parameter becomes
     * the returned blob.
     */
    fun savePlayerConditions(player: Player): ByteArray =
        encodeConditions(player.storedConditionList + player.conditions)

    /**
     * The serialization loop over a standalone condition list, so callers and
     * tests can drive the codec without a live [Player]. The order is the list
     * order, exactly like the C++ range-for over `player->conditions`.
     */
    fun encodeConditions(conditions: List<Condition>): ByteArray {
        val stream = PropWriteStream()
        for (condition in conditions) {
            if (condition.isPersistent()) {
                serializeCondition(condition, stream)
                stream.writeU8(CONDITION_ATTR_END)
            }
        }
        return stream.getStream()
    }

    /**
     * `Condition::serialize` (condition.cpp:277-301): header, buff flag, sub id,
     * the two sound slots and the persistence flag, then the subclass payload
     * through the same class dispatch the C++ virtual `serialize` overrides
     * define.
     */
    private fun serializeCondition(condition: Condition, stream: PropWriteStream) {
        stream.writeU8(CONDITION_ATTR_TYPE)
        stream.writeU8(condition.type.value)

        stream.writeU8(CONDITION_ATTR_ID)
        stream.writeU32(condition.id.value.toLong() and 0xFFFFFFFFL)

        stream.writeU8(CONDITION_ATTR_TICKS)
        stream.writeU32(condition.ticks.toLong() and 0xFFFFFFFFL)

        stream.writeU8(CONDITION_ATTR_ISBUFF)
        stream.writeU8(if (condition.isBuff) 1 else 0)

        stream.writeU8(CONDITION_ATTR_SUBID)
        stream.writeU32(condition.subId.toLong() and 0xFFFFFFFFL)

        // `tickSound`/`addSound` are not retained by `Condition.setParam`
        // (Condition.kt:151-157); the C++ default is SILENCE.
        stream.writeU8(CONDITION_ATTR_TICKSOUND)
        stream.writeU16(0)

        stream.writeU8(CONDITION_ATTR_ADDSOUND)
        stream.writeU16(0)

        stream.writeU8(CONDITION_ATTR_PERSISTENT)
        stream.writeU8(if (condition.isPersistentFlag) 1 else 0)

        when (condition) {
            is ConditionAttributes -> logger.warn {
                "[savePlayerConditions] - ConditionAttributes payload has no read accessor; " +
                    "flat skills/stats/buffs/absorbs/increases are not persisted (PARITY GAP)"
            }

            is ConditionRegeneration -> serializeRegeneration(condition, stream)
            is ConditionSoul -> serializeSoul(condition, stream)
            is ConditionManaShield -> serializeManaShield(condition, stream)
            is ConditionDamage -> serializeDamage(condition, stream)
            is ConditionSpeed -> serializeSpeed(condition, stream)
            is ConditionOutfit -> serializeOutfit(condition, stream)
            is ConditionLight -> serializeLight(condition, stream)
            else -> Unit
        }
    }

    /** `ConditionRegeneration::serialize` (condition.cpp:1474-1488). */
    private fun serializeRegeneration(condition: ConditionRegeneration, stream: PropWriteStream) {
        stream.writeU8(CONDITION_ATTR_HEALTHTICKS)
        stream.writeU32(condition.healthTicks.toLong() and 0xFFFFFFFFL)

        stream.writeU8(CONDITION_ATTR_HEALTHGAIN)
        stream.writeU32(condition.healthGain.toLong() and 0xFFFFFFFFL)

        stream.writeU8(CONDITION_ATTR_MANATICKS)
        stream.writeU32(condition.manaTicks.toLong() and 0xFFFFFFFFL)

        stream.writeU8(CONDITION_ATTR_MANAGAIN)
        stream.writeU32(condition.manaGain.toLong() and 0xFFFFFFFFL)
    }

    /** `ConditionSoul::serialize` (condition.cpp:1712-1720). */
    private fun serializeSoul(condition: ConditionSoul, stream: PropWriteStream) {
        stream.writeU8(CONDITION_ATTR_SOULGAIN)
        stream.writeU32(condition.soulGain.toLong() and 0xFFFFFFFFL)

        stream.writeU8(CONDITION_ATTR_SOULTICKS)
        stream.writeU32(condition.soulTicks.toLong() and 0xFFFFFFFFL)
    }

    /** `ConditionManaShield::serialize` (condition.cpp:1652-1656). */
    private fun serializeManaShield(condition: ConditionManaShield, stream: PropWriteStream) {
        stream.writeU8(CONDITION_ATTR_MANASHIELD)
        stream.writeU32(condition.manaShield.toLong() and 0xFFFFFFFFL)
    }

    /** `ConditionDamage::serialize` (condition.cpp:1832-1848). */
    private fun serializeDamage(condition: ConditionDamage, stream: PropWriteStream) {
        stream.writeU8(CONDITION_ATTR_DELAYED)
        stream.writeU8(if (condition.delayed) 1 else 0)

        stream.writeU8(CONDITION_ATTR_PERIODDAMAGE)
        stream.writeI32(condition.periodDamage)

        if (condition.owner != 0) {
            stream.writeU8(CONDITION_ATTR_OWNER)
            stream.writeU32(condition.owner.toLong() and 0xFFFFFFFFL)
        }

        for (intervalInfo in condition.scheduledDamage) {
            stream.writeU8(CONDITION_ATTR_INTERVALDATA)
            stream.writeI32(intervalInfo.timeLeft)
            stream.writeI32(intervalInfo.value)
            stream.writeI32(intervalInfo.interval)
        }
    }

    /** `ConditionSpeed::serialize` (condition.cpp:2510-2535). */
    private fun serializeSpeed(condition: ConditionSpeed, stream: PropWriteStream) {
        stream.writeU8(CONDITION_ATTR_SPEEDDELTA)
        stream.writeI32(condition.speedDelta)

        stream.writeU8(CONDITION_ATTR_FORMULA_MINA)
        stream.writeU32(condition.mina.toRawBits().toLong() and 0xFFFFFFFFL)

        stream.writeU8(CONDITION_ATTR_FORMULA_MINB)
        stream.writeU32(condition.minb.toRawBits().toLong() and 0xFFFFFFFFL)

        stream.writeU8(CONDITION_ATTR_FORMULA_MAXA)
        stream.writeU32(condition.maxa.toRawBits().toLong() and 0xFFFFFFFFL)

        stream.writeU8(CONDITION_ATTR_FORMULA_MAXB)
        stream.writeU32(condition.maxb.toRawBits().toLong() and 0xFFFFFFFFL)

        if (condition.owner != 0) {
            stream.writeU8(CONDITION_ATTR_OWNER)
            stream.writeU32(condition.owner.toLong() and 0xFFFFFFFFL)
        }
    }

    /**
     * `ConditionOutfit::serialize` (condition.cpp:2682-2686) over
     * `PropWriteStream::write<Outfit_t>` (creatures_definitions.hpp:1832-1850,
     * 3 x `uint16_t`, 8 x `uint8_t`, 5 x `uint16_t` in declaration order).
     */
    private fun serializeOutfit(condition: ConditionOutfit, stream: PropWriteStream) {
        stream.writeU8(CONDITION_ATTR_OUTFIT)

        val outfit = condition.outfit
        stream.writeU16(outfit.lookType)
        stream.writeU16(outfit.lookTypeEx)
        stream.writeU16(outfit.lookMount)
        stream.writeU8(outfit.lookHead)
        stream.writeU8(outfit.lookBody)
        stream.writeU8(outfit.lookLegs)
        stream.writeU8(outfit.lookFeet)
        stream.writeU8(outfit.lookAddons)
        stream.writeU8(outfit.lookMountHead)
        stream.writeU8(outfit.lookMountBody)
        stream.writeU8(outfit.lookMountLegs)
        stream.writeU8(outfit.lookMountFeet)
        stream.writeU16(outfit.lookFamiliarsType)
        stream.writeU16(outfit.lookWing)
        stream.writeU16(outfit.lookAura)
        stream.writeU16(outfit.lookEffect)
        stream.writeU16(outfit.lookShader)
    }

    /** `ConditionLight::serialize` (condition.cpp:2862-2881). */
    private fun serializeLight(condition: ConditionLight, stream: PropWriteStream) {
        stream.writeU8(CONDITION_ATTR_LIGHTCOLOR)
        stream.writeU32(condition.lightInfo.color.toLong() and 0xFFFFFFFFL)

        stream.writeU8(CONDITION_ATTR_LIGHTLEVEL)
        stream.writeU32(condition.lightInfo.level.toLong() and 0xFFFFFFFFL)

        stream.writeU8(CONDITION_ATTR_LIGHTTICKS)
        stream.writeU32(condition.internalLightTicks.toLong() and 0xFFFFFFFFL)

        stream.writeU8(CONDITION_ATTR_LIGHTINTERVAL)
        stream.writeU32(condition.lightChangeInterval.toLong() and 0xFFFFFFFFL)
    }

    private companion object {
        /** `ConditionAttr_t` (creatures_definitions.hpp:71-107). */
        const val CONDITION_ATTR_TYPE = 1
        const val CONDITION_ATTR_ID = 2
        const val CONDITION_ATTR_TICKS = 3
        const val CONDITION_ATTR_HEALTHTICKS = 4
        const val CONDITION_ATTR_HEALTHGAIN = 5
        const val CONDITION_ATTR_MANATICKS = 6
        const val CONDITION_ATTR_MANAGAIN = 7
        const val CONDITION_ATTR_DELAYED = 8
        const val CONDITION_ATTR_OWNER = 9
        const val CONDITION_ATTR_INTERVALDATA = 10
        const val CONDITION_ATTR_SPEEDDELTA = 11
        const val CONDITION_ATTR_FORMULA_MINA = 12
        const val CONDITION_ATTR_FORMULA_MINB = 13
        const val CONDITION_ATTR_FORMULA_MAXA = 14
        const val CONDITION_ATTR_FORMULA_MAXB = 15
        const val CONDITION_ATTR_LIGHTCOLOR = 16
        const val CONDITION_ATTR_LIGHTLEVEL = 17
        const val CONDITION_ATTR_LIGHTTICKS = 18
        const val CONDITION_ATTR_LIGHTINTERVAL = 19
        const val CONDITION_ATTR_SOULTICKS = 20
        const val CONDITION_ATTR_SOULGAIN = 21
        const val CONDITION_ATTR_OUTFIT = 25
        const val CONDITION_ATTR_PERIODDAMAGE = 26
        const val CONDITION_ATTR_ISBUFF = 27
        const val CONDITION_ATTR_SUBID = 28
        const val CONDITION_ATTR_MANASHIELD = 29
        const val CONDITION_ATTR_ADDSOUND = 30
        const val CONDITION_ATTR_TICKSOUND = 31
        const val CONDITION_ATTR_PERSISTENT = 35
        const val CONDITION_ATTR_END = 254
    }
}
