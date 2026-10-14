package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.vocations.Vocation

/**
 * Self-contained mirror of the Player loyalty state
 * (src/creatures/players/player.cpp:1100-1127, 7429-7456, 10683-10702).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps `loyaltyPoints`, `loyaltyTitle` and `loyaltyBonusPercent` directly on
 * `Player` (player.hpp:1709, 1796, 1850); the state is exposed as an injectable
 * seam so the host player can delegate without a global (AGENTS.md 1.1). The
 * skill/mana request tables come from the injected [Vocation]
 * (`Vocation::getReqSkillTries`/`getTotalSkillTries`/`getReqMana`/
 * `getTotalMana`), and `Player::manaSpent` (player.hpp:1722) is a seam because
 * the host `Player` does not own the field (see [PlayerDeathState]).
 *
 * `Player::setLoyaltyBonus` calls `Player::sendSkills()` (player.cpp:10689),
 * the only out-of-package side effect.
 *
 * PARITY GAPS: the host `Player::getLoyaltyMagicLevel`/`getMagicLevel` still
 * return the base magic level (Creature.kt:2317); delegating them to
 * [getLoyaltyMagicLevel] is host wiring outside this package.
 */
class PlayerLoyaltyState(
    /** The host `Player` whose skill table and base magic level are read. */
    private val player: Player,
    /** `Player::vocation` (player.hpp:1755); `null` when the registry is unwired. */
    private val getVocation: () -> Vocation? = { player.vocations?.getVocation(player.vocationId) },
    /** `Player::manaSpent` (player.hpp:1722). */
    private val getManaSpent: () -> Long = { 0L },
    /** `Player::sendSkills()` (player.cpp:10689) protocol seam. */
    private val sendSkills: () -> Unit = {},
    /** `Player::loyaltyBonusPercent` (player.hpp:1850); `uint16_t`. */
    loyaltyBonusPercent: Int = 0,
    /** `Player::loyaltyTitle` (player.hpp:1709). */
    loyaltyTitle: String = "",
    /** `Player::loyaltyPoints` (player.hpp:1796); `uint32_t`. */
    loyaltyPoints: Long = 0,
) {

    private var loyaltyBonusPercent: Int = loyaltyBonusPercent and UINT16_MAX
    private var loyaltyTitle: String = loyaltyTitle
    private var loyaltyPoints: Long = loyaltyPoints and UINT32_MAX

    /** `Player::getLoyaltyPoints` (player.cpp:10683-10685). */
    fun getLoyaltyPoints(): Long = loyaltyPoints

    /** `Player::getLoyaltyBonus` (player.cpp:10700-10702). */
    fun getLoyaltyBonus(): Int = loyaltyBonusPercent

    /** `Player::setLoyaltyBonus` (player.cpp:10687-10690); sends skills. */
    fun setLoyaltyBonus(bonus: Int) {
        loyaltyBonusPercent = bonus and UINT16_MAX
        sendSkills()
    }

    /** `Player::getLoyaltyTitle` (player.cpp:10696-10698). */
    fun getLoyaltyTitle(): String = loyaltyTitle

    /** `Player::setLoyaltyTitle` (player.cpp:10692-10694). */
    fun setLoyaltyTitle(title: String) {
        loyaltyTitle = title
    }

    /**
     * `Player::getLoyaltyMagicLevel` (player.cpp:7429-7456): the magic level
     * the accumulated `manaSpent` reaches once the loyalty percentage is added
     * to the total mana spent. Without a wired vocation the base magic level is
     * returned (C++ would dereference the null `vocation`).
     */
    fun getLoyaltyMagicLevel(): Int {
        val vocation = getVocation() ?: return player.getBaseMagicLevel()

        var level = player.getBaseMagicLevel()
        var currReqMana = vocation.getReqMana(level)
        var nextReqMana = vocation.getReqMana(level + 1)
        if (currReqMana >= nextReqMana) {
            // player has reached max magic level
            return level
        }

        var spent = getManaSpent()
        val totalMana = vocation.getTotalMana(level) + spent
        var loyaltyMana = (totalMana * getLoyaltyBonus()) / 100
        while ((spent + loyaltyMana) >= nextReqMana) {
            loyaltyMana -= nextReqMana - spent
            level++
            spent = 0

            currReqMana = nextReqMana
            nextReqMana = vocation.getReqMana(level + 1)
            if (currReqMana >= nextReqMana) {
                loyaltyMana = 0
                break
            }
        }
        return level
    }

    /**
     * `Player::getLoyaltySkill` (player.cpp:1100-1127): the skill level the raw
     * tries reach once the loyalty percentage is added to the total tries spent.
     * Without a wired vocation the base skill level is returned (C++ would
     * dereference the null `vocation`).
     */
    fun getLoyaltySkill(skill: skills_t): Int {
        val vocation = getVocation() ?: return player.getSkillLevel(skill)

        val baseLevel = player.getSkillLevel(skill)
        var level = baseLevel
        var currReqTries = vocation.getReqSkillTries(skill.value, level)
        var nextReqTries = vocation.getReqSkillTries(skill.value, level + 1)
        if (currReqTries >= nextReqTries) {
            // player has reached max skill
            return player.getSkillLevel(skill)
        }

        var tries = player.getSkillTries(skill)
        val totalTries = vocation.getTotalSkillTries(skill.value, baseLevel) + tries
        var loyaltyTries = (totalTries * getLoyaltyBonus()) / 100
        while ((tries + loyaltyTries) >= nextReqTries) {
            loyaltyTries -= nextReqTries - tries
            level++
            tries = 0

            currReqTries = nextReqTries
            nextReqTries = vocation.getReqSkillTries(skill.value, level + 1)
            if (currReqTries >= nextReqTries) {
                loyaltyTries = 0
                break
            }
        }
        return level
    }

    companion object {
        /** `std::numeric_limits<uint16_t>::max()`. */
        private const val UINT16_MAX = 0xFFFF

        /** `std::numeric_limits<uint32_t>::max()`. */
        private const val UINT32_MAX = 0xFFFF_FFFFL
    }
}
