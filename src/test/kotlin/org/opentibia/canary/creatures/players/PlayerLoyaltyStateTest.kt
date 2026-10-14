package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player loyalty family:
 *
 * - `Player::getLoyaltyPoints` (player.cpp:10683-10685)
 * - `Player::setLoyaltyBonus` / `getLoyaltyBonus` (player.cpp:10687-10702)
 * - `Player::setLoyaltyTitle` / `getLoyaltyTitle` (player.cpp:10692-10698)
 * - `Player::getLoyaltyMagicLevel` (player.cpp:7429-7456)
 * - `Player::getLoyaltySkill` (player.cpp:1100-1127)
 *
 * The loyalty bonus is `loyaltyBonusPercent` (player.hpp:1850) and the runtime
 * stat accessors add the bonus on top of the accumulated tries/mana. The
 * vocation request tables (`Vocation::getReqSkillTries` / `getTotalSkillTries`
 * / `getReqMana` / `getTotalMana`) come from a real [Vocation], and
 * `Player::manaSpent` is an injected seam because the host `Player` does not own
 * the field yet (see [PlayerDeathState]).
 */
class PlayerLoyaltyStateTest {

    private fun newPlayer(
        magLevel: Int = 0,
        swordTries: Long = 0,
    ) = Player(
        id = 31,
        name = "Loyal Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    ).apply {
        this.magLevel = magLevel
        addSkillTries(skills_t.SKILL_SWORD, swordTries)
    }

    private fun newVocation(manaMultiplier: Float = 2.0f): Vocation {
        val vocation = Vocation(1)
        vocation.manaMultiplier = manaMultiplier
        return vocation
    }

    private fun newState(
        player: Player = newPlayer(),
        vocation: Vocation? = newVocation(),
        manaSpent: Long = 0,
        bonus: Int = 0,
        title: String = "",
        points: Long = 0,
        sendSkills: () -> Unit = {},
    ) = PlayerLoyaltyState(
        player = player,
        getVocation = { vocation },
        getManaSpent = { manaSpent },
        sendSkills = sendSkills,
        loyaltyBonusPercent = bonus,
        loyaltyTitle = title,
        loyaltyPoints = points,
    )

    @Test
    fun `defaults mirror the Canary member initializers`() {
        // player.hpp:1709 (loyaltyTitle), 1796 (loyaltyPoints),
        // 1850 (loyaltyBonusPercent).
        val state = newState()

        assertThat(state.getLoyaltyBonus()).isZero()
        assertThat(state.getLoyaltyTitle()).isEmpty()
        assertThat(state.getLoyaltyPoints()).isZero()
    }

    @Test
    fun `setLoyaltyBonus stores the uint16 percent and sends skills`() {
        // player.cpp:10687-10690: `loyaltyBonusPercent = bonus; sendSkills();`.
        var skillsSent = 0
        val state = newState(bonus = 10, sendSkills = { skillsSent++ })

        assertThat(state.getLoyaltyBonus()).isEqualTo(10)

        state.setLoyaltyBonus(25)
        assertThat(state.getLoyaltyBonus()).isEqualTo(25)
        assertThat(skillsSent).isEqualTo(1)

        state.setLoyaltyBonus(70_000)
        assertThat(state.getLoyaltyBonus()).isEqualTo(70_000 and 0xFFFF)
    }

    @Test
    fun `loyalty title and points round trip`() {
        // player.cpp:10692-10698.
        val state = newState(title = "Sage", points = 3_600)

        assertThat(state.getLoyaltyTitle()).isEqualTo("Sage")
        assertThat(state.getLoyaltyPoints()).isEqualTo(3_600)

        state.setLoyaltyTitle("Guardian")
        assertThat(state.getLoyaltyTitle()).isEqualTo("Guardian")
    }

    @Test
    fun `loyalty magic level stops at the base when the table is flat`() {
        // player.cpp:7431-7437: currReqMana >= nextReqMana returns the base.
        val player = newPlayer(magLevel = 1)
        val state = newState(player = player, vocation = newVocation(manaMultiplier = 1.0f))

        assertThat(state.getLoyaltyMagicLevel()).isEqualTo(1)
    }

    @Test
    fun `loyalty magic level adds no level without a mana surplus`() {
        // player.cpp:7429-7456 with manaSpent below the next request.
        val state = newState(player = newPlayer(magLevel = 0), manaSpent = 800, bonus = 10)

        assertThat(state.getLoyaltyMagicLevel()).isZero()
    }

    @Test
    fun `loyalty magic level advances once when spent mana plus loyalty reaches the request`() {
        // player.cpp:7440-7453: spent 1500 + 10% of 1500 = 1650 >= 1600.
        val state = newState(player = newPlayer(magLevel = 0), manaSpent = 1500, bonus = 10)

        assertThat(state.getLoyaltyMagicLevel()).isEqualTo(1)
    }

    @Test
    fun `loyalty magic level advances multiple levels like Canary`() {
        // player.cpp:7440-7453: 1000 spent toward reqMana(1) = 1600 with a
        // 1000% bonus walks past reqMana(2) = 3200 and stops below 6400.
        val state = newState(player = newPlayer(magLevel = 0), manaSpent = 1_000, bonus = 1_000)

        assertThat(state.getLoyaltyMagicLevel()).isEqualTo(2)
    }

    @Test
    fun `loyalty magic level falls back to the base without a vocation`() {
        val state = newState(player = newPlayer(magLevel = 7), vocation = null, manaSpent = 99_999)

        assertThat(state.getLoyaltyMagicLevel()).isEqualTo(7)
    }

    @Test
    fun `loyalty skill stops when the request table is flat`() {
        // player.cpp:1101-1105: currReqTries >= nextReqTries returns the base.
        // SKILL_CRITICAL_HIT_CHANCE has a zero skill base, so every request is 0.
        val state = newState()

        assertThat(state.getLoyaltySkill(skills_t.SKILL_CRITICAL_HIT_CHANCE)).isEqualTo(10)
    }

    @Test
    fun `loyalty skill keeps the base without enough loyalty tries`() {
        // player.cpp:1107-1112: 20 tries at level 10 + 100% loyalty = 40 < req(11) = 50.
        val player = newPlayer(swordTries = 20)
        val state = newState(player = player, bonus = 100)

        assertThat(state.getLoyaltySkill(skills_t.SKILL_SWORD)).isEqualTo(10)
    }

    @Test
    fun `loyalty skill advances one level when loyalty tries cover the request`() {
        // player.cpp:1107-1121 with the default sword table:
        // req(11) = 50, req(12) = 100; totalTries 40 + 100% = 80 >= 50.
        val player = newPlayer(swordTries = 40)
        val state = newState(player = player, bonus = 100)

        assertThat(state.getLoyaltySkill(skills_t.SKILL_SWORD)).isEqualTo(11)
    }

    @Test
    fun `loyalty skill stops at the new request table after advancing`() {
        // player.cpp:1113-1120: after level++ the loop re-reads req(level + 1)
        // and stops when the remaining 30 loyalty tries are below it.
        val player = newPlayer(swordTries = 40)
        val state = newState(player = player, bonus = 100)

        assertThat(state.getLoyaltySkill(skills_t.SKILL_SWORD)).isEqualTo(11)
        // The formula does not mutate player state.
        assertThat(player.getSkillLevel(skills_t.SKILL_SWORD)).isEqualTo(10)
        assertThat(player.getSkillTries(skills_t.SKILL_SWORD)).isEqualTo(40)
    }

    @Test
    fun `loyalty skill falls back to the base without a vocation`() {
        val player = newPlayer(swordTries = 500)
        val state = newState(player = player, vocation = null, bonus = 100)

        assertThat(state.getLoyaltySkill(skills_t.SKILL_SWORD)).isEqualTo(10)
    }
}
