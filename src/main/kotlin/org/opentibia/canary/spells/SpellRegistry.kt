package org.opentibia.canary.spells

import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.wheel.WheelSpellBoost
import org.opentibia.canary.systems.wheel.WheelSpellGrade
import java.util.TreeMap

/**
 * `Spell`/`InstantSpell`/`RuneSpell` and the `Spells` registry
 * (`src/creatures/combat/spells.hpp:130-385`, `spells.cpp:79-335`).
 *
 * Plain state and lookups only; the cast pipeline lives in [SpellEngine]. The
 * Wheel of Destiny, weapon-proficiency and augment terms of Canary are separate
 * subsystems and are not modelled here (PARITY GAPS recorded on each method).
 */
abstract class Spell {

    /** `Spell::name` (`spells.hpp:309`). */
    var name: String = ""

    /** `Spell::m_words` (`spells.hpp:310`). */
    var words: String = ""

    /** `Spell::m_separator` (`spells.hpp:311`). */
    var separator: String = ""

    /** `Spell::m_spellId` (`spells.hpp:283`). */
    var spellId: Int = 0

    /** `BaseSpell::m_spellScriptId` (`spells.hpp:103`). */
    var scriptId: Int = 0

    /** `Spell::spellType` (`spells.hpp:274`). */
    var spellType: SpellType = SpellType.SPELL_UNDEFINED

    /** `Spell::group` (`spells.hpp:272`). */
    var group: SpellGroup = SpellGroup.SPELLGROUP_NONE

    /** `Spell::secondaryGroup` (`spells.hpp:273`). */
    var secondaryGroup: SpellGroup = SpellGroup.SPELLGROUP_NONE

    /** `Spell::cooldown` (`spells.hpp:276`). */
    var cooldown: Int = 1000

    /** `Spell::groupCooldown` (`spells.hpp:277`). */
    var groupCooldown: Int = 1000

    /** `Spell::secondaryGroupCooldown` (`spells.hpp:278`). */
    var secondaryGroupCooldown: Int = 0

    /** `Spell::level` (`spells.hpp:279`). */
    var level: Int = 0

    /** `Spell::magLevel` (`spells.hpp:280`). */
    var magicLevel: Int = 0

    /** `Spell::range` (`spells.hpp:281`). */
    var range: Int = -1

    /** `Spell::mana` (`spells.hpp:295`). */
    var mana: Int = 0

    /** `Spell::manaPercent` (`spells.hpp:296`). */
    var manaPercent: Int = 0

    /** `Spell::soul` (`spells.hpp:297`). */
    var soul: Int = 0

    /** `Spell::needWeapon` (`spells.hpp:299`). */
    var needWeapon: Boolean = false

    /** `Spell::blockingSolid` (`spells.hpp:300`). */
    var blockingSolid: Boolean = false

    /** `Spell::blockingCreature` (`spells.hpp:301`). */
    var blockingCreature: Boolean = false

    /** `Spell::aggressive` (`spells.hpp:302`). */
    var aggressive: Boolean = true

    /** `Spell::learnable` (`spells.hpp:303`). */
    var learnable: Boolean = false

    /** `Spell::enabled` (`spells.hpp:304`). */
    var enabled: Boolean = true

    /** `Spell::premium` (`spells.hpp:305`). */
    var premium: Boolean = false

    /** `Spell::selfTarget` (`spells.hpp:285`). */
    var selfTarget: Boolean = false

    /** `Spell::needTarget` (`spells.hpp:286`). */
    var needTarget: Boolean = false

    /** `Spell::allowOnSelf` (`spells.hpp:287`). */
    var allowOnSelf: Boolean = true

    /** `Spell::pzLocked` (`spells.hpp:288`). */
    var pzLocked: Boolean = false

    /** `BaseSpell::soundImpactEffect` (`spells.hpp:99`). */
    var soundImpactEffect: SoundEffect = SoundEffect.SILENCE

    /** `BaseSpell::soundCastEffect` (`spells.hpp:100`). */
    var soundCastEffect: SoundEffect = SoundEffect.SPELL_OR_RUNE

    /** `Spell::vocSpellMap` (`spells.hpp:270`). */
    val vocSpellMap: MutableMap<Int, Boolean> = mutableMapOf()

    /**
     * `Spell::whellOfDestinyUpgraded` (`spells.hpp:290`; the C++ field carries
     * that typo). Set by the wheel spell-table registration
     * (`IOWheel::initializeGlobalData`, io_wheel.cpp:98).
     */
    private var wheelOfDestinyUpgraded: Boolean = false

    /** `Spell::wheelOfDestinyRegularBoost` (`spells.hpp:291`). */
    private val wheelOfDestinyRegularBoost = IntArray(WheelSpellBoost.entries.size + 1)

    /** `Spell::wheelOfDestinyUpgradedBoost` (`spells.hpp:292`). */
    private val wheelOfDestinyUpgradedBoost = IntArray(WheelSpellBoost.entries.size + 1)

    /** `Spell::isInstant` (`spells.hpp:141`) — pure virtual in Canary. */
    abstract fun isInstant(): Boolean

    /** `Spell::getWheelOfDestinyUpgraded` (`spells.cpp:731-733`). */
    fun getWheelOfDestinyUpgraded(): Boolean = wheelOfDestinyUpgraded

    /**
     * `Spell::getWheelOfDestinyBoost` (`spells.cpp:735-748`): the regular boost
     * applies from grade `REGULAR`, the upgraded boost from `UPGRADED`; the
     * C++ `std::array::at` out-of-range guard yields 0 here.
     */
    fun getWheelOfDestinyBoost(boost: WheelSpellBoost, grade: WheelSpellGrade): Int {
        val index = boost.value
        if (index >= wheelOfDestinyRegularBoost.size) {
            return 0
        }

        var value = 0
        if (grade.value >= WheelSpellGrade.REGULAR.value) {
            value += wheelOfDestinyRegularBoost[index]
        }
        if (grade.value >= WheelSpellGrade.UPGRADED.value) {
            value += wheelOfDestinyUpgradedBoost[index]
        }
        return value
    }

    /** `Spell::setWheelOfDestinyUpgraded` (`spells.cpp:751-753`). */
    fun setWheelOfDestinyUpgraded(value: Boolean) {
        wheelOfDestinyUpgraded = value
    }

    /**
     * `Spell::setWheelOfDestinyBoost` (`spells.cpp:755-765`): only the
     * `REGULAR` and `UPGRADED` grades hold a value; their boosts are overwritten
     * per call.
     */
    fun setWheelOfDestinyBoost(boost: WheelSpellBoost, grade: WheelSpellGrade, value: Int) {
        val index = boost.value
        if (index >= wheelOfDestinyRegularBoost.size) {
            return
        }
        if (grade == WheelSpellGrade.REGULAR) {
            wheelOfDestinyRegularBoost[index] = value
        } else if (grade == WheelSpellGrade.UPGRADED) {
            wheelOfDestinyUpgradedBoost[index] = value
        }
    }

    /** `Spell::isLearnable` (`spells.cpp:948-950`). */
    fun isLearnable(): Boolean = learnable

    /**
     * `Spell::getManaCost` (`spells.cpp:952-977`).
     *
     * PARITY GAP: the Wheel of Destiny upgraded-boost and spell-bonus mana
     * reductions (`spells.cpp:953-958`) and the augment/proficiency systems are
     * not modelled, so the base cost is returned unmodified.
     */
    fun getManaCost(caster: SpellCaster): Int {
        if (mana != 0) {
            return mana
        }

        if (manaPercent != 0) {
            return (caster.getMaxMana() * manaPercent) / 100
        }

        return 0
    }

    /** `Spell::getSoulCost` (`spells.cpp:979-981`). */
    fun getSoulCost(): Int = soul

    /**
     * `Spell::addVocMap` (`spells.cpp:1039-1047`). `0xFFFF` is the overflow
     * sentinel Canary rejects.
     */
    fun addVocMap(vocationId: Int, value: Boolean) {
        if (vocationId == 0xFFFF) {
            return
        }
        vocSpellMap[vocationId] = value
    }
}

/** `InstantSpell` (`spells.hpp:316-347`). */
class InstantSpell : Spell() {

    /** `InstantSpell::hasParam` (`spells.hpp:343`). */
    var hasParam: Boolean = false

    /** `InstantSpell::hasPlayerNameParam` (`spells.hpp:344`). */
    var hasPlayerNameParam: Boolean = false

    /** `InstantSpell::needDirection` (`spells.hpp:342`). */
    var needDirection: Boolean = false

    /** `InstantSpell::casterTargetOrDirection` (`spells.hpp:346`). */
    var casterTargetOrDirection: Boolean = false

    /** `InstantSpell::checkLineOfSight` (`spells.hpp:345`). */
    var checkLineOfSight: Boolean = true

    /** `InstantSpell::isInstant` (`spells.cpp:1375-1377`). */
    override fun isInstant(): Boolean = true
}

/** `RuneSpell` (`spells.hpp:349-385`), including the `Action` fields it inherits. */
class RuneSpell : Spell() {

    /** `RuneSpell::runeId` (`spells.hpp:382`). */
    var runeId: Int = 0

    /** `RuneSpell::charges` (`spells.hpp:383`). */
    var charges: Int = 0
        set(value) {
            // `RuneSpell::setCharges` (spells.cpp:1629-1634) latches hasCharges.
            if (value > 0) {
                hasCharges = true
            }
            field = value
        }

    /** `RuneSpell::hasCharges` (`spells.hpp:384`). */
    var hasCharges: Boolean = false
        private set

    /** `Action::allowFarUse` (`src/lua/creature/actions.hpp:129`). */
    var allowFarUse: Boolean = false

    /** `Action::checkFloor` (`src/lua/creature/actions.hpp:130`). */
    var checkFloor: Boolean = true

    /** `Action::checkLineOfSight` (`src/lua/creature/actions.hpp:131`). */
    var checkLineOfSight: Boolean = true

    /** `RuneSpell::isInstant` (`spells.cpp:1613-1615`). */
    override fun isInstant(): Boolean = false
}

/**
 * `Spells` registry (`spells.hpp:28-79`).
 *
 * Canary reaches this through the `g_spells()` singleton; per AGENTS.md 1.1 the
 * instance is injected instead. The maps keep Canary's `std::map` ordering so
 * `getInstantSpell`/`getSpellsByVocation` resolve ties identically.
 */
class Spells {

    /** `Spells::instants` (`spells.hpp:76`). */
    private val instants: TreeMap<String, InstantSpell> = TreeMap()

    /** `Spells::runes` (`spells.hpp:75`). */
    private val runes: TreeMap<Int, RuneSpell> = TreeMap()

    /** `Spells::getSpellByName` (`spells.cpp:255-261`). */
    fun getSpellByName(name: String): Spell? =
        getRuneSpellByName(name) ?: getInstantSpellByName(name)

    /** `Spells::getRuneSpell` (`spells.cpp:263-274`). */
    fun getRuneSpell(id: Int): RuneSpell? =
        runes[id] ?: runes.values.firstOrNull { it.runeId == id }

    /** `Spells::getRuneSpellByName` (`spells.cpp:276-283`). */
    fun getRuneSpellByName(name: String): RuneSpell? =
        runes.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * `Spells::getInstantSpell` (`spells.cpp:285-317`): longest case-insensitive
     * words prefix wins; extra text is only accepted for `hasParam` spells with
     * a space separator and at least one character of parameter.
     */
    fun getInstantSpell(words: String): InstantSpell? {
        var result: InstantSpell? = null

        for (instant in instants.values) {
            val instantWords = instant.words
            val spellLen = instantWords.length
            if (words.length < spellLen) {
                continue
            }
            if (!words.regionMatches(0, instantWords, 0, spellLen, ignoreCase = true)) {
                continue
            }

            if (result == null || spellLen > result.words.length) {
                result = instant
                if (words.length == spellLen) {
                    break
                }
            }
        }

        val found = result ?: return null
        val resultWords = found.words
        if (words.length > resultWords.length) {
            if (!found.hasParam) {
                return null
            }

            val spellLen = resultWords.length
            val paramLen = words.length - spellLen
            if (paramLen < 2 || words[spellLen] != ' ') {
                return null
            }
        }
        return found
    }

    /** `Spells::getInstantSpellById` (`spells.cpp:319-326`). */
    fun getInstantSpellById(spellId: Int): InstantSpell? =
        instants.values.firstOrNull { it.spellId == spellId }

    /** `Spells::getInstantSpellByName` (`spells.cpp:328-335`). */
    fun getInstantSpellByName(name: String): InstantSpell? =
        instants.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** `Spells::getSpellsByVocation` (`spells.cpp:229-245`). */
    fun getSpellsByVocation(vocationId: Int): List<Int> =
        instants.values.filter { it.vocSpellMap[vocationId] == true }.map { it.spellId }

    /** `Spells::getInstantSpells` (`spells.cpp:247-249`). */
    fun getInstantSpells(): Map<String, InstantSpell> = instants

    /** `Spells::hasInstantSpell` (`spells.cpp:169-175`) — exact map key. */
    fun hasInstantSpell(word: String): Boolean = instants.containsKey(word)

    /** `Spells::setInstantSpell` (`spells.cpp:177-179`) — `try_emplace`. */
    fun setInstantSpell(word: String, instant: InstantSpell) {
        instants.putIfAbsent(word, instant)
    }

    /** `Spells::clear` (`spells.cpp:164-167`). */
    fun clear() {
        instants.clear()
        runes.clear()
    }

    /** `Spells::registerInstantLuaEvent` (`spells.cpp:181-204`). */
    fun registerInstantLuaEvent(instant: InstantSpell?): Boolean {
        if (instant == null) {
            return false
        }

        if (instant.words.isEmpty()) {
            return false
        }

        if (hasInstantSpell(instant.words)) {
            return false
        }

        setInstantSpell(instant.words, instant)
        return true
    }

    /** `Spells::registerRuneLuaEvent` (`spells.cpp:206-222`). */
    fun registerRuneLuaEvent(rune: RuneSpell?): Boolean {
        if (rune == null) {
            return false
        }

        return runes.putIfAbsent(rune.runeId, rune) == null
    }

    /** `Spells::isMonkShrineSpell` (`spells.cpp:224-227`). */
    fun isMonkShrineSpell(name: String): Boolean =
        name == "Mystic Repulse" || name == "Forceful Uppercut" || name == "Focus Harmony"

    companion object {
        /** `Spells::getCasterPosition` (`spells.cpp:337-339`). */
        fun getCasterPosition(creature: SpellCaster, dir: Direction): Position =
            dir.offset(creature.getPosition())
    }
}
