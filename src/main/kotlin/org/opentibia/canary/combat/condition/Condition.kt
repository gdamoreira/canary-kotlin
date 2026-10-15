package org.opentibia.canary.combat.condition

import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.SoundEffect

/**
 * Base `Condition` (`src/creatures/combat/condition.cpp:144-557`,
 * `condition.hpp:24-80`).
 *
 * The lifecycle mirrors the C++ exactly:
 * - [startCondition] stamps `endTime` for finite durations
 *   (`condition.cpp:468-473`).
 * - [executeCondition] decrements `ticks` without re-stamping `endTime`
 *   (`condition.cpp:308-324`).
 * - [updateCondition] is the stacking gate: same type plus an actually longer
 *   duration replaces; an immortal (`ticks == -1`) target refuses a finite
 *   refresh (`condition.cpp:543-557`).
 * - `endCondition`/`addCondition`/`clone` are the abstract hooks the concrete
 *   conditions implement (`condition.hpp:32-38`).
 *
 * `g_game()` sound/effect side effects are intentionally not reproduced
 * (AGENTS.md 1.1); the engine is pure over [ConditionTarget].
 */
abstract class Condition(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    isBuff: Boolean = false,
    subId: Int = 0,
    isPersistent: Boolean = false,
    protected val now: () -> Long = { System.currentTimeMillis() },
) {
    /** `Condition::id` (`condition.hpp:67`). */
    var id: ConditionId = id
        protected set

    /** `Condition::conditionType` (`condition.hpp:66`); mutable in `ConditionSpeed::setParam`. */
    var type: ConditionType = type
        protected set

    /** `Condition::isBuff` (`condition.hpp:68`). */
    var isBuff: Boolean = isBuff
        protected set

    /** `Condition::subId` (`condition.hpp:64`). */
    var subId: Int = subId
        protected set

    /** `Condition::m_isPersistent` (`condition.hpp:69`). */
    var isPersistentFlag: Boolean = isPersistent
        protected set

    /** `Condition::owner` (`condition.hpp:61`). */
    var owner: Int = 0
        protected set

    /** `Condition::drainBodyStage` (`condition.hpp:62`). */
    var drainBodyStage: Int = 0
        protected set

    /** `Condition::tickSound` (`condition.hpp:74`), default `SILENCE`. */
    var tickSound: SoundEffect = SoundEffect.SILENCE
        protected set

    /** `Condition::addSound` (`condition.hpp:75`), default `SILENCE`. */
    var addSound: SoundEffect = SoundEffect.SILENCE
        protected set

    /** `Condition::ticks` (`condition.hpp:65`); mutated through [setTicks]. */
    protected var ticksValue: Int = ticks

    /** Read accessor for [ticksValue]. */
    val ticks: Int get() = ticksValue

    /**
     * `Condition::endTime` (`condition.hpp:63`); ctor
     * (`condition.cpp:464-466`): immortal conditions start at `LONG_MAX`.
     */
    var endTime: Long = if (ticks == -1) Long.MAX_VALUE else 0L
        protected set

    /** `Condition::startCondition` (`condition.cpp:468-473`). */
    open fun startCondition(target: ConditionTarget?): Boolean {
        if (ticksValue > 0) {
            endTime = ticksValue + now()
        }
        return true
    }

    /** `Condition::executeCondition` (`condition.cpp:308-324`). */
    open fun executeCondition(target: ConditionTarget?, interval: Int): Boolean {
        if (ticksValue == -1) {
            return true
        }

        ticksValue = maxOf(0, ticksValue - interval)
        if (endTime < now()) {
            return false
        }

        // `g_game().sendSingleSoundEffect` (`condition.cpp:319-321`) is not
        // reproduced; the configured `tickSound` id is retained and serialized.
        return true
    }

    /**
     * `Condition::updateCondition` (`condition.cpp:543-557`).
     *
     * Note the strict `>` in the endTime comparison: an equal remaining
     * duration does not refresh.
     */
    protected open fun updateCondition(addCondition: Condition): Boolean {
        if (type != addCondition.type) {
            return false
        }

        if (ticksValue == -1 && addCondition.ticks > 0) {
            return false
        }

        if (addCondition.ticks >= 0 && endTime > now() + addCondition.ticks) {
            return false
        }

        return true
    }

    /** `Condition::setTicks` (`condition.cpp:303-306`) — re-stamps `endTime`. */
    open fun setTicks(newTicks: Int) {
        ticksValue = newTicks
        endTime = newTicks + now()
    }

    /** `Condition::setParam` (`condition.cpp:144-183`). */
    open fun setParam(param: ConditionParam, value: Int): Boolean = when (param) {
        ConditionParam.CONDITION_PARAM_TICKS -> {
            ticksValue = value
            true
        }

        ConditionParam.CONDITION_PARAM_OWNER -> {
            owner = value
            true
        }

        ConditionParam.CONDITION_PARAM_DRAIN_BODY -> {
            drainBodyStage = value and 0xFF
            true
        }

        ConditionParam.CONDITION_PARAM_BUFF_SPELL -> {
            isBuff = value != 0
            true
        }

        ConditionParam.CONDITION_PARAM_SUBID -> {
            subId = value
            true
        }

        ConditionParam.CONDITION_PARAM_SOUND_TICK -> {
            tickSound = soundEffectOf(value)
            true
        }

        ConditionParam.CONDITION_PARAM_SOUND_ADD -> {
            addSound = soundEffectOf(value)
            true
        }

        else -> false
    }

    /** `Condition::setPositionParam` (`condition.cpp:185-187`) — default rejects. */
    open fun setPositionParam(param: ConditionParam, pos: Position): Boolean = false

    /**
     * `Condition::serialize` (`condition.cpp:277-301`): the base header, the
     * retained tick/add sound slots and the persistence flag. Subclasses append
     * their payload through the same override chain the C++ virtual defines.
     */
    open fun serialize(stream: PropWriteStream) {
        stream.writeU8(ConditionAttr.CONDITIONATTR_TYPE.value)
        stream.writeU8(type.value)

        stream.writeU8(ConditionAttr.CONDITIONATTR_ID.value)
        stream.writeU32(id.value.toLong() and 0xFFFFFFFFL)

        stream.writeU8(ConditionAttr.CONDITIONATTR_TICKS.value)
        stream.writeU32(ticksValue.toLong() and 0xFFFFFFFFL)

        stream.writeU8(ConditionAttr.CONDITIONATTR_ISBUFF.value)
        stream.writeU8(if (isBuff) 1 else 0)

        stream.writeU8(ConditionAttr.CONDITIONATTR_SUBID.value)
        stream.writeU32(subId.toLong() and 0xFFFFFFFFL)

        stream.writeU8(ConditionAttr.CONDITIONATTR_TICKSOUND.value)
        stream.writeU16(tickSound.value)

        stream.writeU8(ConditionAttr.CONDITIONATTR_ADDSOUND.value)
        stream.writeU16(addSound.value)

        stream.writeU8(ConditionAttr.CONDITIONATTR_PERSISTENT.value)
        stream.writeU8(if (isPersistentFlag) 1 else 0)
    }

    /**
     * `Condition::unserialize` (`condition.cpp:189-197`): reads attribute
     * records until `CONDITIONATTR_END`; a failed read exits the loop and keeps
     * the condition, a rejected attribute drops it.
     */
    fun unserialize(stream: PropStream): Boolean {
        while (true) {
            val attrValue = stream.readU8() ?: break
            if (attrValue == ConditionAttr.CONDITIONATTR_END.value) {
                break
            }

            val attr = ConditionAttr.entries.firstOrNull { it.value == attrValue } ?: return false
            if (!unserializeProp(attr, stream)) {
                return false
            }
        }
        return true
    }

    /**
     * `Condition::unserializeProp` (`condition.cpp:199-275`): the base
     * attributes. An unknown attribute returns `false` and drops the condition
     * exactly like the C++ `default`.
     */
    open fun unserializeProp(attr: ConditionAttr, stream: PropStream): Boolean {
        when (attr) {
            ConditionAttr.CONDITIONATTR_TYPE -> {
                val value = stream.readI32() ?: return false
                type = ConditionType.entries.firstOrNull { it.value == value } ?: return false
            }

            ConditionAttr.CONDITIONATTR_ID -> {
                val value = stream.readI32() ?: return false
                id = ConditionId.entries
                    .firstOrNull { (it.value.toLong() and 0xFFFFFFFFL) == (value.toLong() and 0xFFFFFFFFL) }
                    ?: return false
            }

            ConditionAttr.CONDITIONATTR_TICKS -> ticksValue = stream.readI32() ?: return false
            ConditionAttr.CONDITIONATTR_ISBUFF -> isBuff = (stream.readU8() ?: return false) != 0
            ConditionAttr.CONDITIONATTR_SUBID -> subId = stream.readU32()?.toInt() ?: return false
            ConditionAttr.CONDITIONATTR_TICKSOUND -> tickSound = soundEffectOf(stream.readU16() ?: return false)
            ConditionAttr.CONDITIONATTR_ADDSOUND -> addSound = soundEffectOf(stream.readU16() ?: return false)
            ConditionAttr.CONDITIONATTR_PERSISTENT -> isPersistentFlag = (stream.readU8() ?: return false) != 0

            ConditionAttr.CONDITIONATTR_END -> Unit

            else -> return false
        }
        return true
    }

    /** `Condition::getIcons` (`condition.cpp:514-521`). */
    open fun getIcons(): Set<PlayerIcon> {
        val icons = mutableSetOf<PlayerIcon>()
        if (isBuff) {
            icons.add(PlayerIcon.PartyBuff)
        }
        return icons
    }

    /** `Condition::isPersistent` (`condition.cpp:475-489`). */
    fun isPersistent(): Boolean {
        if (isPersistentFlag) {
            return true
        }
        if (ticks == -1) {
            return false
        }
        if (id != ConditionId.CONDITIONID_DEFAULT && id != ConditionId.CONDITIONID_COMBAT) {
            return false
        }
        return true
    }

    /** `Condition::isRemovableOnDeath` (`condition.cpp:491-512`). */
    fun isRemovableOnDeath(): Boolean {
        if (isPersistentFlag) {
            return false
        }
        if (ticks == -1) {
            return false
        }
        if (type in NON_REMOVABLE_ON_DEATH) {
            return false
        }
        return true
    }

    /** `Condition::endCondition` (`condition.hpp:32`) — pure virtual in Canary. */
    abstract fun endCondition(target: ConditionTarget?)

    /** `Condition::addCondition` (`condition.hpp:33`) — pure virtual in Canary. */
    abstract fun addCondition(target: ConditionTarget?, condition: Condition)

    /** `Condition::clone` (`condition.hpp:38`) — pure virtual in Canary. */
    abstract fun clone(): Condition

    companion object {
        /** `Condition::isRemovableOnDeath` (`condition.cpp:500-505`). */
        private val NON_REMOVABLE_ON_DEATH = setOf(
            ConditionType.CONDITION_SPELLCOOLDOWN,
            ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
            ConditionType.CONDITION_MUTED,
            ConditionType.CONDITION_GOSHNARTAINT,
        )

        /**
         * `Condition::createDamageCondition` (`condition.cpp:326-341`).
         * Returns null for non-damage types, exactly like the C++ `default`.
         */
        fun createDamageCondition(
            id: ConditionId,
            type: ConditionType,
            buff: Boolean = false,
            subId: Int = 0,
        ): ConditionDamage? = when (type) {
            ConditionType.CONDITION_POISON,
            ConditionType.CONDITION_FIRE,
            ConditionType.CONDITION_ENERGY,
            ConditionType.CONDITION_DROWN,
            ConditionType.CONDITION_FREEZING,
            ConditionType.CONDITION_DAZZLED,
            ConditionType.CONDITION_CURSED,
            ConditionType.CONDITION_BLEEDING,
            -> ConditionDamage(id, type, buff, subId)

            else -> null
        }

        /**
         * `Condition::createCondition(PropStream &)` (`condition.cpp:414-462`):
         * the loader header reader. Every record must appear in the exact
         * `TYPE`, `ID`, `TICKS`, `ISBUFF`, `SUBID` order the serializer writes;
         * any mismatch returns `null` like the C++ guard chain. `isPersistent`
         * is not part of the header (the `PERSISTENT` attribute restores it).
         */
        fun createCondition(stream: PropStream): Condition? {
            if (stream.readU8() != ConditionAttr.CONDITIONATTR_TYPE.value) {
                return null
            }
            val typeValue = stream.readU8() ?: return null

            if (stream.readU8() != ConditionAttr.CONDITIONATTR_ID.value) {
                return null
            }
            val idValue = stream.readU32() ?: return null

            if (stream.readU8() != ConditionAttr.CONDITIONATTR_TICKS.value) {
                return null
            }
            val ticks = stream.readU32()?.toInt() ?: return null

            if (stream.readU8() != ConditionAttr.CONDITIONATTR_ISBUFF.value) {
                return null
            }
            val buff = stream.readU8() ?: return null

            if (stream.readU8() != ConditionAttr.CONDITIONATTR_SUBID.value) {
                return null
            }
            val subId = stream.readU32() ?: return null

            val type = ConditionType.entries.firstOrNull { it.value == typeValue } ?: return null
            val id = ConditionId.entries
                .firstOrNull { (it.value.toLong() and 0xFFFFFFFFL) == idValue }
                ?: return null

            return createCondition(
                id = id,
                type = type,
                ticks = ticks,
                param = 0,
                buff = buff != 0,
                subId = subId.toInt(),
            )
        }

        /**
         * `static_cast<SoundEffect_t>(value)` (condition.cpp:170, 175). The
         * complete C++ value mirror backs every valid id; an unknown id
         * normalizes to `SILENCE` like the Lua bridge does.
         */
        private fun soundEffectOf(value: Int): SoundEffect =
            SoundEffect.entries.firstOrNull { it.value == value } ?: SoundEffect.SILENCE

        /**
         * `Condition::createCondition` (`condition.cpp:343-412`).
         *
         * Factory parity for the ported subset; `CONDITION_AGONY` has no Canary
         * class and keeps the C++ `default: return nullptr` behaviour.
         */
        fun createCondition(
            id: ConditionId,
            type: ConditionType,
            ticks: Int,
            param: Int = 0,
            buff: Boolean = false,
            subId: Int = 0,
            isPersistent: Boolean = false,
            now: () -> Long = { System.currentTimeMillis() },
            random: kotlin.random.Random = kotlin.random.Random.Default,
            walkable: (Position) -> Boolean = { false },
            rateSpellCooldown: Double = 1.0,
            warnUnsafeScripts: Boolean = false,
            isLookTypeRegistered: (Int) -> Boolean = { false },
            monsterOutfit: (String) -> Outfit? = { null },
        ): Condition? {
            createDamageCondition(id, type, buff, subId)?.let { return it }

            return when (type) {
                ConditionType.CONDITION_HASTE,
                ConditionType.CONDITION_PARALYZE,
                -> ConditionSpeed(id, type, ticks, buff, subId, param, now, random)

                ConditionType.CONDITION_INVISIBLE ->
                    ConditionInvisible(id, type, ticks, buff, subId, now)

                ConditionType.CONDITION_OUTFIT ->
                    ConditionOutfit(
                        id,
                        type,
                        ticks,
                        buff,
                        subId,
                        now,
                        warnUnsafeScripts,
                        isLookTypeRegistered,
                        monsterOutfit,
                    )

                ConditionType.CONDITION_LIGHT ->
                    ConditionLight(
                        id,
                        type,
                        ticks,
                        buff,
                        subId,
                        param and 0xFF,
                        (param and 0xFF00) shr 8,
                        now,
                    )

                ConditionType.CONDITION_REGENERATION ->
                    ConditionRegeneration(id, type, ticks, buff, subId, now, rateSpellCooldown)

                ConditionType.CONDITION_SOUL ->
                    ConditionSoul(id, type, ticks, buff, subId, now)

                ConditionType.CONDITION_LESSERHEX,
                ConditionType.CONDITION_INTENSEHEX,
                ConditionType.CONDITION_GREATERHEX,
                ConditionType.CONDITION_MENTOROTHER,
                ConditionType.CONDITION_ATTRIBUTES,
                -> ConditionAttributes(id, type, ticks, buff, subId, now)

                ConditionType.CONDITION_MANASHIELD ->
                    ConditionManaShield(id, type, ticks, buff, subId, now)

                ConditionType.CONDITION_SPELLCOOLDOWN ->
                    ConditionSpellCooldown(id, type, ticks, buff, subId, now)

                ConditionType.CONDITION_SPELLGROUPCOOLDOWN ->
                    ConditionSpellGroupCooldown(id, type, ticks, buff, subId, now)

                ConditionType.CONDITION_SERENE ->
                    ConditionSerene(id, type, ticks, buff, subId, now)

                ConditionType.CONDITION_FEARED ->
                    ConditionFeared(id, type, ticks, buff, subId, now, walkable, random)

                ConditionType.CONDITION_ROOTED,
                ConditionType.CONDITION_INFIGHT,
                ConditionType.CONDITION_DRUNK,
                ConditionType.CONDITION_EXHAUST,
                ConditionType.CONDITION_EXHAUST_COMBAT,
                ConditionType.CONDITION_EXHAUST_HEAL,
                ConditionType.CONDITION_MUTED,
                ConditionType.CONDITION_CHANNELMUTEDTICKS,
                ConditionType.CONDITION_YELLTICKS,
                ConditionType.CONDITION_POWERLESS,
                ConditionType.CONDITION_PACIFIED,
                // condition.cpp:390-401 does not forward isPersistent here.
                -> ConditionGeneric(id, type, ticks, buff, subId, false, now)

                // condition.cpp:403-404 forwards isPersistent for the
                // BAKRAGORE world-change condition only.
                ConditionType.CONDITION_BAKRAGORE ->
                    ConditionGeneric(id, type, ticks, buff, subId, isPersistent, now)

                // condition.cpp:406-407 leaves GOSHNARTAINT non-persistent.
                ConditionType.CONDITION_GOSHNARTAINT ->
                    ConditionGeneric(id, type, ticks, buff, subId, false, now)

                else -> null
            }
        }
    }
}

/**
 * `ConditionGeneric` (`condition.cpp:563-653`) — the pass-through condition
 * used for drunks, in-fight, taint and the cooldown families.
 */
open class ConditionGeneric(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    isPersistent: Boolean = false,
    now: () -> Long = { System.currentTimeMillis() },
) : Condition(id, type, ticks, buff, subId, isPersistent, now) {

    /**
     * `ConditionGeneric::addCondition` (`condition.cpp:578-586`).
     * The add-sound side effect is presentation-only and not reproduced.
     */
    override fun addCondition(target: ConditionTarget?, condition: Condition) {
        if (updateCondition(condition)) {
            setTicks(condition.ticks)
        }
    }

    /** `ConditionGeneric::getIcons` (`condition.cpp:588-649`). */
    override fun getIcons(): Set<PlayerIcon> {
        val icons = super.getIcons().toMutableSet()

        when (type) {
            ConditionType.CONDITION_INFIGHT -> icons.add(PlayerIcon.Swords)
            ConditionType.CONDITION_DRUNK -> icons.add(PlayerIcon.Drunk)
            ConditionType.CONDITION_ROOTED -> icons.add(PlayerIcon.Rooted)
            ConditionType.CONDITION_LESSERHEX -> icons.add(PlayerIcon.LesserHex)
            ConditionType.CONDITION_INTENSEHEX -> icons.add(PlayerIcon.IntenseHex)
            ConditionType.CONDITION_GREATERHEX -> icons.add(PlayerIcon.GreaterHex)
            ConditionType.CONDITION_POWERLESS -> icons.add(PlayerIcon.Powerless)
            ConditionType.CONDITION_MENTOROTHER -> icons.add(PlayerIcon.MentorOther)

            ConditionType.CONDITION_GOSHNARTAINT -> when (subId) {
                1 -> icons.add(PlayerIcon.GoshnarTaint1)
                2 -> icons.add(PlayerIcon.GoshnarTaint2)
                3 -> icons.add(PlayerIcon.GoshnarTaint3)
                4 -> icons.add(PlayerIcon.GoshnarTaint4)
                5 -> icons.add(PlayerIcon.GoshnarTaint5)
                else -> Unit
            }

            else -> Unit
        }

        return icons
    }

    /** `ConditionGeneric::endCondition` (`condition.cpp:574-576`) — no-op. */
    override fun endCondition(target: ConditionTarget?) {
        // No behaviour.
    }

    /** `ConditionGeneric::clone` (`condition.cpp:651-653`). */
    override fun clone(): Condition {
        val copy = ConditionGeneric(id, type, ticks, isBuff, subId, isPersistentFlag, now)
        copy.apply {
            tickSound = this@ConditionGeneric.tickSound
            addSound = this@ConditionGeneric.addSound
        }
        return copy
    }

    /** `ConditionGeneric::startCondition` (`condition.cpp:566-568`). */
    override fun startCondition(target: ConditionTarget?): Boolean =
        super.startCondition(target)

    /** `ConditionGeneric::executeCondition` (`condition.cpp:570-572`). */
    override fun executeCondition(target: ConditionTarget?, interval: Int): Boolean =
        super.executeCondition(target, interval)
}
