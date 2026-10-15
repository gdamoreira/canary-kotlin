package org.opentibia.canary.combat.condition

import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream

/**
 * `ConditionOutfit` (`src/creatures/combat/condition.cpp:2667-2752`,
 * `condition.hpp:414-435`).
 *
 * Applies a fixed or lazily-resolved monster outfit on start, restores the
 * creature's default outfit on end, and refreshes the outfit when stacked.
 *
 * Constructor seams replace the Canary globals per AGENTS.md 1.1:
 * `g_configManager().getBoolean(WARN_UNSAFE_SCRIPTS)` becomes
 * [warnUnsafeScripts], `g_game().isLookTypeRegistered` becomes
 * [isLookTypeRegistered], and `g_monsters().getMonsterType(name)->info.outfit`
 * becomes [monsterOutfit].
 */
class ConditionOutfit(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    now: () -> Long = { System.currentTimeMillis() },
    private val warnUnsafeScripts: Boolean = false,
    private val isLookTypeRegistered: (Int) -> Boolean = { false },
    private val monsterOutfit: (String) -> Outfit? = { null },
) : Condition(id, type, ticks, buff, subId, false, now) {

    /** `ConditionOutfit::outfit` (`condition.hpp:433`). */
    var outfit: Outfit = Outfit()
        private set

    /** `ConditionOutfit::monsterName` (`condition.hpp:434`). */
    var monsterName: String = ""
        private set

    /** `ConditionOutfit::setOutfit` (`condition.cpp:2667-2669`). */
    fun setOutfit(newOutfit: Outfit) {
        outfit = newOutfit.copy()
    }

    /** `ConditionOutfit::setLazyMonsterOutfit` (`condition.cpp:2671-2673`). */
    fun setLazyMonsterOutfit(monsterName: String) {
        this.monsterName = monsterName
    }

    /** `ConditionOutfit::startCondition` (`condition.cpp:2692-2714`). */
    override fun startCondition(target: ConditionTarget?): Boolean {
        if (warnUnsafeScripts && outfit.lookType != 0 && !isLookTypeRegistered(outfit.lookType)) {
            return false
        }

        if (outfit.lookType == 0 && outfit.lookTypeEx == 0 && monsterName.isNotEmpty()) {
            val resolved = monsterOutfit(monsterName)
            if (resolved != null) {
                outfit = resolved.copy()
            } else {
                return false
            }
        }

        if (!super.startCondition(target)) {
            return false
        }

        target?.internalCreatureChangeOutfit(outfit)
        return true
    }

    /** `ConditionOutfit::executeCondition` (`condition.cpp:2716-2718`). */
    override fun executeCondition(target: ConditionTarget?, interval: Int): Boolean =
        super.executeCondition(target, interval)

    /** `ConditionOutfit::endCondition` (`condition.cpp:2720-2722`). */
    override fun endCondition(target: ConditionTarget?) {
        if (target != null) {
            target.internalCreatureChangeOutfit(target.getDefaultOutfit())
        }
    }

    /** `ConditionOutfit::addCondition` (`condition.cpp:2724-2748`). */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        if (warnUnsafeScripts && outfit.lookType != 0 && !isLookTypeRegistered(outfit.lookType)) {
            return
        }

        if (updateCondition(condition)) {
            setTicks(condition.ticks)

            val incoming = condition as ConditionOutfit
            if (incoming.monsterName.isNotEmpty() && incoming.monsterName != monsterName) {
                val resolved = monsterOutfit(incoming.monsterName)
                if (resolved != null) {
                    setOutfit(resolved)
                } else {
                    return
                }
            } else if (incoming.outfit.lookType != 0 || incoming.outfit.lookTypeEx != 0) {
                setOutfit(incoming.outfit)
            }

            target?.internalCreatureChangeOutfit(outfit)
        }
    }

    /** `ConditionOutfit::serialize` (`condition.cpp:2682-2686`). */
    override fun serialize(stream: PropWriteStream) {
        super.serialize(stream)

        stream.writeU8(ConditionAttr.CONDITIONATTR_OUTFIT.value)
        writeOutfit(stream, outfit)
    }

    /** `ConditionOutfit::unserializeProp` (`condition.cpp:2675-2680`). */
    override fun unserializeProp(attr: ConditionAttr, stream: PropStream): Boolean {
        when (attr) {
            ConditionAttr.CONDITIONATTR_OUTFIT -> outfit = readOutfit(stream) ?: return false
            else -> return super.unserializeProp(attr, stream)
        }
        return true
    }

    /**
     * `PropWriteStream::write<Outfit_t>` (creatures_definitions.hpp:1832-1850):
     * 3 x `uint16_t`, 9 x `uint8_t`, 5 x `uint16_t` in declaration order.
     */
    private fun writeOutfit(stream: PropWriteStream, value: Outfit) {
        stream.writeU16(value.lookType)
        stream.writeU16(value.lookTypeEx)
        stream.writeU16(value.lookMount)
        stream.writeU8(value.lookHead)
        stream.writeU8(value.lookBody)
        stream.writeU8(value.lookLegs)
        stream.writeU8(value.lookFeet)
        stream.writeU8(value.lookAddons)
        stream.writeU8(value.lookMountHead)
        stream.writeU8(value.lookMountBody)
        stream.writeU8(value.lookMountLegs)
        stream.writeU8(value.lookMountFeet)
        stream.writeU16(value.lookFamiliarsType)
        stream.writeU16(value.lookWing)
        stream.writeU16(value.lookAura)
        stream.writeU16(value.lookEffect)
        stream.writeU16(value.lookShader)
    }

    /** `PropStream::read<Outfit_t>` (creatures_definitions.hpp:1832-1850). */
    private fun readOutfit(stream: PropStream): Outfit? = Outfit(
        lookType = stream.readU16() ?: return null,
        lookTypeEx = stream.readU16() ?: return null,
        lookMount = stream.readU16() ?: return null,
        lookHead = stream.readU8() ?: return null,
        lookBody = stream.readU8() ?: return null,
        lookLegs = stream.readU8() ?: return null,
        lookFeet = stream.readU8() ?: return null,
        lookAddons = stream.readU8() ?: return null,
        lookMountHead = stream.readU8() ?: return null,
        lookMountBody = stream.readU8() ?: return null,
        lookMountLegs = stream.readU8() ?: return null,
        lookMountFeet = stream.readU8() ?: return null,
        lookFamiliarsType = stream.readU16() ?: return null,
        lookWing = stream.readU16() ?: return null,
        lookAura = stream.readU16() ?: return null,
        lookEffect = stream.readU16() ?: return null,
        lookShader = stream.readU16() ?: return null,
    )

    /** `ConditionOutfit::clone` (`condition.cpp:2750-2752`). */
    override fun clone(): Condition {
        val copy = ConditionOutfit(
            id,
            type,
            ticks,
            isBuff,
            subId,
            now,
            warnUnsafeScripts,
            isLookTypeRegistered,
            monsterOutfit,
        )
        copy.apply {
            endTime = this@ConditionOutfit.endTime
            owner = this@ConditionOutfit.owner
            tickSound = this@ConditionOutfit.tickSound
            addSound = this@ConditionOutfit.addSound
            outfit = this@ConditionOutfit.outfit.copy()
            monsterName = this@ConditionOutfit.monsterName
        }
        return copy
    }
}
