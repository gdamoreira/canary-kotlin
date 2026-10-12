package org.opentibia.canary.systems.soulwar

import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import kotlin.random.Random

/**
 * Canary engine core for the Soul War / Goshnar's Taint subsystem.
 *
 * This file ports the C++ pieces the Lua soul-war scripts drive through engine
 * bindings; the encounter/quest decision logic itself stays Lua (AGENTS.md 1.4):
 *
 * - `ConditionType_t` values (`src/creatures/creatures_definitions.hpp:144-151`)
 * - `ConditionId_t` values (`src/creatures/creatures_definitions.hpp:433-446`)
 * - `ConditionParam_t` values (`src/creatures/creatures_definitions.hpp:178-237`)
 * - `PlayerIcon` values (`src/enums/player_icons.hpp:16-50`)
 * - `Condition` / `ConditionGeneric` / `ConditionFeared`
 *   (`src/creatures/combat/condition.cpp:144-696, 2163-2455`)
 * - `Combat::checkFearConditionAffected`
 *   (`src/creatures/combat/combat.cpp:1003-1029`)
 *
 * Constructor seams replace the Canary globals (`g_game()`, `g_dispatcher()`)
 * per AGENTS.md 1.1: the wall clock is injected as [Condition.now] and the map
 * tile query behind `ConditionFeared::canWalkTo` is injected as `walkable`.
 */

/** `ConditionType_t` (creatures_definitions.hpp:117-157) — soul-war relevant subset. */
enum class ConditionType(val value: Int) {
    CONDITION_FEARED(30),
    CONDITION_GOSHNARTAINT(35),
    CONDITION_AGONY(37),
}

/** `ConditionId_t` (creatures_definitions.hpp:433-446). */
enum class ConditionId(val value: Int) {
    CONDITIONID_DEFAULT(-1),
    CONDITIONID_COMBAT(0),
}

/** `ConditionParam_t` (creatures_definitions.hpp:177-237) — soul-war relevant subset. */
enum class ConditionParam(val value: Int) {
    CONDITION_PARAM_OWNER(1),
    CONDITION_PARAM_TICKS(2),
    CONDITION_PARAM_BUFF_SPELL(44),
    CONDITION_PARAM_SUBID(45),
    CONDITION_PARAM_CASTER_POSITION(60),
}

/** `PlayerIcon` (enums/player_icons.hpp:16-50) — soul-war relevant subset. */
enum class PlayerIcon(val value: Int) {
    PartyBuff(12),
    Feared(20),
    GoshnarTaint1(21),
    GoshnarTaint2(22),
    GoshnarTaint3(23),
    GoshnarTaint4(24),
    GoshnarTaint5(25),
    Agony(27),
}

/**
 * Base `Condition` (`condition.cpp:144-557`, `condition.hpp:24-80`).
 *
 * `endCondition`/`addCondition` are the abstract hooks implemented by the
 * concrete conditions; `isRemovableOnDeath` keeps the exact Canary ordering so
 * taint survives death (`condition.cpp:491-512`).
 */
sealed class Condition(
    val id: ConditionId,
    val type: ConditionType,
    ticks: Int,
    isBuff: Boolean = false,
    subId: Int = 0,
    val isPersistentFlag: Boolean = false,
    protected val now: () -> Long = { System.currentTimeMillis() },
) {
    /** `Condition::ticks` (condition.hpp:65). */
    private var ticksValue: Int = ticks

    /** Read accessor for [ticksValue]; mutation goes through [setTicks]/[setParam]. */
    val ticks: Int get() = ticksValue

    /**
     * `Condition::endTime` (condition.hpp:63; ctor `condition.cpp:464-466`):
     * an immortal (`ticks == -1`) condition starts at `LONG_MAX`, otherwise 0
     * until [startCondition] stamps it.
     */
    private var endTimeValue: Long = if (ticks == -1) Long.MAX_VALUE else 0L

    /** Read accessor for [endTimeValue]. */
    val endTime: Long get() = endTimeValue

    /** `Condition::owner` (condition.hpp:61). */
    var owner: Int = 0
        protected set

    var isBuff: Boolean = isBuff
        protected set

    var subId: Int = subId
        protected set

    /** `Condition::startCondition` (condition.cpp:468-473). */
    open fun startCondition(creaturePosition: Position? = null): Boolean {
        if (ticksValue > 0) {
            endTimeValue = ticksValue + now()
        }
        return true
    }

    /** `Condition::executeCondition` (condition.cpp:308-324). */
    open fun executeCondition(interval: Int): Boolean {
        if (ticksValue == -1) {
            return true
        }

        ticksValue = maxOf(0, ticksValue - interval)
        if (endTimeValue < now()) {
            return false
        }

        return true
    }

    /**
     * `Condition::updateCondition` (condition.cpp:543-557) — the stacking gate
     * used when the same `(type, id, subId)` is added again. A longer duration
     * replaces, a shorter one is ignored.
     */
    protected open fun updateCondition(addCondition: Condition): Boolean {
        if (type != addCondition.type) {
            return false
        }

        if (ticksValue == -1 && addCondition.ticksValue > 0) {
            return false
        }

        if (addCondition.ticksValue >= 0 && endTimeValue > now() + addCondition.ticksValue) {
            return false
        }

        return true
    }

    /** `Condition::getIcons` (condition.cpp:514-521). */
    open fun getIcons(): Set<PlayerIcon> {
        val icons = mutableSetOf<PlayerIcon>()
        if (isBuff) {
            icons.add(PlayerIcon.PartyBuff)
        }
        return icons
    }

    /** `Condition::isRemovableOnDeath` (condition.cpp:491-512). */
    fun isRemovableOnDeath(): Boolean {
        if (isPersistentFlag) {
            return false
        }

        if (ticksValue == -1) {
            return false
        }

        if (type in NON_REMOVABLE_ON_DEATH) {
            return false
        }

        return true
    }

    /** `Condition::isPersistent` (condition.cpp:475-489). */
    fun isPersistent(): Boolean {
        if (isPersistentFlag) {
            return true
        }
        if (ticksValue == -1) {
            return false
        }
        if (id != ConditionId.CONDITIONID_DEFAULT && id != ConditionId.CONDITIONID_COMBAT) {
            return false
        }
        return true
    }

    /** `Condition::setTicks` (condition.cpp:303-306) — re-stamps `endTime`. */
    open fun setTicks(newTicks: Int) {
        ticksValue = newTicks
        endTimeValue = newTicks + now()
    }

    /**
     * `Condition::setParam` (condition.cpp:144-183) — soul-war relevant params.
     * Note `CONDITION_PARAM_TICKS` deliberately does not re-stamp `endTime`.
     */
    open fun setParam(param: ConditionParam, value: Int): Boolean {
        return when (param) {
            ConditionParam.CONDITION_PARAM_TICKS -> {
                ticksValue = value
                true
            }

            ConditionParam.CONDITION_PARAM_OWNER -> {
                owner = value
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

            else -> false
        }
    }

    /** `Condition::setPositionParam` (condition.cpp:185-187) — default rejects. */
    open fun setPositionParam(param: ConditionParam, pos: Position): Boolean = false

    /** `Condition::endCondition` (condition.hpp:32) — pure virtual in Canary. */
    abstract fun endCondition(creature: PlayerConditionState?)

    /** `Condition::addCondition` (condition.hpp:33) — pure virtual in Canary. */
    abstract fun addCondition(addCondition: Condition)

    companion object {
        /** `Condition::isRemovableOnDeath` (condition.cpp:500-505). */
        private val NON_REMOVABLE_ON_DEATH = setOf(
            ConditionType.CONDITION_GOSHNARTAINT,
        )

        /**
         * `Condition::createCondition` (condition.cpp:343-412) restricted to the
         * soul-war types. `CONDITION_AGONY` has no engine condition class in
         * Canary either, so it returns null here exactly like the C++ `default`.
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
            walkable: (Position) -> Boolean = { false },
            random: Random = Random.Default,
        ): Condition? {
            return when (type) {
                ConditionType.CONDITION_FEARED ->
                    ConditionFeared(id, type, ticks, buff, subId, isPersistent, now, walkable, random)

                ConditionType.CONDITION_GOSHNARTAINT ->
                    ConditionGeneric(id, type, ticks, buff, subId, isPersistent, now)

                else -> null
            }
        }
    }
}

/**
 * `ConditionGeneric` (`condition.cpp:563-653`) — carries the Goshnar's Taint
 * condition. Stacking simply re-stamps the ticks and the `subId` selects the
 * amplification icon (taint 1..5).
 */
class ConditionGeneric(
    id: ConditionId,
    type: ConditionType,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    isPersistent: Boolean = false,
    now: () -> Long = { System.currentTimeMillis() },
) : Condition(id, type, ticks, buff, subId, isPersistent, now) {

    /**
     * `ConditionGeneric::addCondition` (condition.cpp:578-586). The add-sound
     * side effect (`g_game().sendSingleSoundEffect`) is out of engine scope and
     * therefore not played.
     */
    override fun addCondition(addCondition: Condition) {
        if (updateCondition(addCondition)) {
            setTicks(addCondition.ticks)
        }
    }

    /** `ConditionGeneric::getIcons` (condition.cpp:588-649) — Goshnar's Taint slice. */
    override fun getIcons(): Set<PlayerIcon> {
        val icons = super.getIcons().toMutableSet()

        when (type) {
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

    /** `ConditionGeneric::endCondition` (condition.cpp:574-576) — no-op. */
    override fun endCondition(creature: PlayerConditionState?) {
        // No behaviour.
    }
}

/**
 * `ConditionFeared` (`condition.cpp:2163-2455`, `condition.hpp:344-380`).
 *
 * Ports the region-selection [getFleeDirection] state machine, the stacking
 * [addCondition], the [getIcons] deserved-icon, and the [endCondition] fear
 * immunity. The `executeCondition` walk scheduling (`getFleePath`,
 * `forcePlayerAutoWalk`, `condition.cpp:2299-2427`) depends on the live map and
 * pathfinder and is not reproduced here; [executeCondition] therefore only runs
 * the base duration tick.
 */
class ConditionFeared(
    id: ConditionId,
    type: ConditionType = ConditionType.CONDITION_FEARED,
    ticks: Int,
    buff: Boolean = false,
    subId: Int = 0,
    isPersistent: Boolean = false,
    now: () -> Long = { System.currentTimeMillis() },
    private val walkable: (Position) -> Boolean = { false },
    private val random: Random = Random.Default,
) : Condition(id, type, ticks, buff, subId, isPersistent, now) {

    /** `m_directionsVector` (condition.hpp:367-376). */
    private val directionsVector = listOf(
        Direction.North,
        Direction.NorthEast,
        Direction.East,
        Direction.SouthEast,
        Direction.South,
        Direction.SouthWest,
        Direction.West,
        Direction.NorthWest,
    )

    /** `fleeingFromPos` (condition.hpp:378) — the caster position. */
    var fleeingFromPos: Position = Position(0, 0, 0)
        internal set

    /** `fleeIndx` (condition.hpp:379), exposed for engine tests. */
    var fleeIndx: Int = 99
        internal set

    /**
     * `ConditionFeared::setPositionParam` (condition.cpp:2385-2391).
     */
    override fun setPositionParam(param: ConditionParam, pos: Position): Boolean {
        if (param == ConditionParam.CONDITION_PARAM_CASTER_POSITION) {
            fleeingFromPos = pos
            return true
        }
        return false
    }

    /** `ConditionFeared::startCondition` (condition.cpp:2396-2401). */
    override fun startCondition(creaturePosition: Position?): Boolean {
        if (creaturePosition != null) {
            getFleeDirection(creaturePosition)
        }
        return super.startCondition(creaturePosition)
    }

    /**
     * `ConditionFeared::executeCondition` (condition.cpp:2403-2427) — the map
     * walk block is omitted (see class docs); only the base duration tick runs.
     */
    override fun executeCondition(interval: Int): Boolean {
        return super.executeCondition(interval)
    }

    /** `ConditionFeared::endCondition` (condition.cpp:2429-2438). */
    override fun endCondition(creature: PlayerConditionState?) {
        // `creature->stopEventWalk()` has no walk state to release in this port.
        creature?.setImmuneFear()
    }

    /** `ConditionFeared::addCondition` (condition.cpp:2440-2444). */
    override fun addCondition(addCondition: Condition) {
        if (updateCondition(addCondition)) {
            setTicks(addCondition.ticks)
        }
    }

    /** `ConditionFeared::getIcons` (condition.cpp:2446-2451). */
    override fun getIcons(): Set<PlayerIcon> {
        val icons = super.getIcons().toMutableSet()
        icons.add(PlayerIcon.Feared)
        return icons
    }

    /**
     * `ConditionFeared::getFleeDirection` (condition.cpp:2223-2297).
     *
     * Selects the starting index into [directionsVector] from the caster offset.
     * Returns false only when the creature shares the caster's SQM and no random
     * walkable direction can be found (or the region is unreachable).
     */
    internal fun getFleeDirection(creaturePos: Position): Boolean {
        val offx = creaturePos.x - fleeingFromPos.x
        val offy = creaturePos.y - fleeingFromPos.y

        return when {
            // Same SQM: flee anywhere (condition.cpp:2230-2236).
            offx == 0 && offy == 0 -> getRandomDirection(creaturePos)

            // Monster on SW region: flee N, NE or E (condition.cpp:2237-2250).
            offx >= 1 && offy <= 0 -> {
                fleeIndx = if (offy == 0) 2 else 0
                true
            }

            // Monster on NW region: flee E, SE or S (condition.cpp:2251-2264).
            offx >= 0 && offy >= 1 -> {
                fleeIndx = if (offx == 0) 4 else 2
                true
            }

            // Monster on NE region: flee S, SW or W (condition.cpp:2265-2278).
            offx <= -1 && offy >= 0 -> {
                fleeIndx = if (offy == 0) 6 else 4
                true
            }

            // Monster on SE region: flee W, NW or N (condition.cpp:2279-2293).
            offx <= 0 && offy <= -1 -> {
                fleeIndx = if (offx == 0) 0 else 6
                true
            }

            else -> false
        }
    }

    /**
     * `ConditionFeared::getRandomDirection` (condition.cpp:2172-2197).
     *
     * Note Canary stores the *direction value* (not the shuffled index) into
     * `fleeIndx` (`this->fleeIndx = static_cast<uint8_t>(*it)`), which is then
     * used as an index into [directionsVector] by `getFleePath`; the quirk is
     * reproduced faithfully with [Direction.ordinal].
     */
    internal fun getRandomDirection(pos: Position): Boolean {
        val directions = directionsVector.shuffled(random)
        val found = directions.firstOrNull { canWalkTo(pos, it) }
        if (found != null) {
            fleeIndx = found.ordinal
            return true
        }
        return false
    }

    /**
     * `ConditionFeared::canWalkTo` (condition.cpp:2204-2221). The live tile
     * `queryAdd`/field-damage check is folded into the injected [walkable] seam.
     */
    internal fun canWalkTo(pos: Position, moveDirection: Direction): Boolean {
        val destination = getNextPosition(moveDirection, pos)
        return walkable(destination)
    }

    /**
     * `ConditionFeared::isStuck` (condition.cpp:2165-2170).
     */
    internal fun isStuck(pos: Position): Boolean {
        return directionsVector.all { !canWalkTo(pos, it) }
    }
}

/**
 * `Party` member access used by [Combat.checkFearConditionAffected]
 * (`party.hpp:37-43`, `party.cpp:61-71`): `getMembers` is the member list and
 * `getMemberCount` is its size.
 */
class SoulWarParty(
    private val members: MutableList<PlayerConditionState> = mutableListOf(),
) {
    /** `Party::getMemberCount` (party.cpp:69-71). */
    fun getMemberCount(): Int = members.size

    /** `Party::getMembers` (party.cpp:61-63). */
    fun getMembers(): List<PlayerConditionState> = members
}

/**
 * Self-contained mirror of the Canary player/creature state the soul-war
 * taint/fear engine reads and writes. It is deliberately *not* `Player.kt` or
 * `Creature.kt` (task constraint): it transcribes the exact methods used by the
 * subsystem with the globals replaced by constructor-injected state:
 *
 * - `Creature::addCondition` / `getCondition` / `hasCondition` / `removeCondition`
 *   (`creature.cpp:1372-1605`)
 * - `Player::setImmuneFear` / `isImmuneFear` (`player.cpp:1930-1938`)
 * - `Player::setImmuneCleanse` / `isImmuneCleanse` (`player.cpp:1897-1928`)
 * - `Player::getParty` (`player.cpp` accessor)
 */
class PlayerConditionState(
    val id: Long = 0,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val conditions = mutableListOf<Condition>()

    /** `m_fearCondition` (player.hpp) — `pair<ConditionType_t, time>`. */
    private var fearConditionType: ConditionType? = null
    private var fearConditionEnd: Long = 0L

    /** `cleanseConditions` (player.hpp) — `vector<pair<ConditionType_t, time>>`. */
    private val cleanseConditions = mutableMapOf<ConditionType, Long>()

    /**
     * Active party membership (`Player::getParty`); the Kotlin property getter
     * is emitted as the `getParty()` accessor.
     */
    var party: SoulWarParty? = null

    /**
     * `Player::setImmuneFear` (player.cpp:1930-1933); Canary default 10000 ms.
     */
    fun setImmuneFear(immuneTime: Long = 10000) {
        fearConditionType = ConditionType.CONDITION_FEARED
        fearConditionEnd = now() + immuneTime
    }

    /** `Player::isImmuneFear` (player.cpp:1935-1938). */
    fun isImmuneFear(): Boolean {
        return fearConditionType == ConditionType.CONDITION_FEARED && now() <= fearConditionEnd
    }

    /**
     * `Player::setImmuneCleanse` (player.cpp:1897-1911). Fear is redirected to
     * [setImmuneFear] with 11000 ms; every other type records 11000 ms.
     */
    fun setImmuneCleanse(conditionType: ConditionType) {
        if (conditionType == ConditionType.CONDITION_FEARED) {
            setImmuneFear(11000)
        } else {
            cleanseConditions[conditionType] = now() + 11000
        }
    }

    /** `Player::isImmuneCleanse` (player.cpp:1913-1928). */
    fun isImmuneCleanse(conditionType: ConditionType): Boolean {
        if (conditionType == ConditionType.CONDITION_FEARED) {
            return isImmuneFear()
        }

        val until = cleanseConditions[conditionType] ?: return false
        return now() <= until
    }

    /**
     * `Creature::addCondition` (creature.cpp:1372-1395). A same
     * `(type, id, subId)` match stacks through `Condition::addCondition`.
     */
    fun addCondition(condition: Condition, creaturePosition: Position? = null): Boolean {
        val previous = getCondition(condition.type, condition.id, condition.subId)
        if (previous != null) {
            previous.addCondition(condition)
            return true
        }

        if (condition.startCondition(creaturePosition)) {
            conditions.add(condition)
            return true
        }

        return false
    }

    /** `Creature::getCondition(type, conditionId, subId)` (creature.cpp:1513-1525). */
    fun getCondition(type: ConditionType, conditionId: ConditionId, subId: Int = 0): Condition? {
        return conditions.firstOrNull { it.type == type && it.id == conditionId && it.subId == subId }
    }

    /** `Creature::hasCondition(type, subId)` (creature.cpp:1585-1605). */
    fun hasCondition(type: ConditionType, subId: Int = 0): Boolean {
        val timeNow = now()
        return conditions.any {
            it.type == type && it.subId == subId && (it.endTime >= timeNow || it.ticks == -1)
        }
    }

    /** `Creature::removeCondition(type)` (creature.cpp:1412-1433). */
    fun removeCondition(type: ConditionType) {
        val iterator = conditions.iterator()
        while (iterator.hasNext()) {
            val condition = iterator.next()
            if (condition.type != type) {
                continue
            }
            iterator.remove()
            condition.endCondition(this)
        }
    }

    /** `Creature::removeCondition(type, conditionId)` (creature.cpp:1435-1467). */
    fun removeCondition(type: ConditionType, conditionId: ConditionId) {
        val iterator = conditions.iterator()
        while (iterator.hasNext()) {
            val condition = iterator.next()
            if (condition.type != type || condition.id != conditionId) {
                continue
            }
            iterator.remove()
            condition.endCondition(this)
        }
    }

    /** `Creature::removeCondition(condition)` (creature.cpp:1486-1498). */
    fun removeCondition(condition: Condition) {
        if (!conditions.remove(condition)) {
            return
        }
        condition.endCondition(this)
    }
}

/**
 * `Combat` fear gate (`src/creatures/combat/combat.cpp:1003-1029`).
 */
object Combat {
    /**
     * `Combat::checkFearConditionAffected` (combat.cpp:1003-1029).
     *
     * A player can be feared when they are neither immune nor already feared,
     * and their party still has fear budget: `(memberCount + 5) / 5` members,
     * reduced by every already-feared member.
     */
    fun checkFearConditionAffected(player: PlayerConditionState): Boolean {
        if (player.isImmuneFear()) {
            return false
        }

        if (player.hasCondition(ConditionType.CONDITION_FEARED)) {
            return false
        }

        val party = player.party
        if (party != null) {
            var affectedCount = (party.getMemberCount() + 5) / 5

            for (member in party.getMembers()) {
                if (member.hasCondition(ConditionType.CONDITION_FEARED)) {
                    affectedCount -= 1
                }
            }

            if (affectedCount <= 0) {
                return false
            }
        }

        return true
    }
}

/**
 * `getNextPosition` (src/utils/tools.cpp:581-628) — the eight-step position
 * translation used by the fear pathing.
 */
internal fun getNextPosition(direction: Direction, pos: Position): Position {
    return when (direction) {
        Direction.North -> Position(pos.x, pos.y - 1, pos.z)
        Direction.South -> Position(pos.x, pos.y + 1, pos.z)
        Direction.West -> Position(pos.x - 1, pos.y, pos.z)
        Direction.East -> Position(pos.x + 1, pos.y, pos.z)
        Direction.SouthWest -> Position(pos.x - 1, pos.y + 1, pos.z)
        Direction.NorthWest -> Position(pos.x - 1, pos.y - 1, pos.z)
        Direction.NorthEast -> Position(pos.x + 1, pos.y - 1, pos.z)
        Direction.SouthEast -> Position(pos.x + 1, pos.y + 1, pos.z)
    }
}
