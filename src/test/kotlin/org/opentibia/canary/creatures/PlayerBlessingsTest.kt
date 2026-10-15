package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.kv.ValueWrapper
import org.opentibia.canary.map.Position

/**
 * Transcribes the blessing state of Canary's `Player`:
 *
 * - `Player::blessings` (player.hpp:1818) and the accessors
 *   `addBlessing`/`removeBlessing`/`hasBlessing`/`getBlessingCount`/
 *   `getBlessingsName` (player.cpp:2422-2440, 10544-10579)
 * - `Player::checkAndShowBlessingMessage` (player.cpp:12993-13018)
 * - `Player::getLostPercent` (player.cpp:7350-7393) and
 *   `Player::getLostExperience` (player.hpp:1921-1923) over `Creature::skillLoss`
 *   (creature.hpp:932) and `Player::isPromoted` (player.cpp:7341-7344)
 * - `Player::getPercentLevel` (player.cpp:3831-3840), `Player::addExperience`
 *   level-percent bookkeeping (player.cpp:3737-3741)
 */
class PlayerBlessingsTest {

    private fun newPlayer(
        level: Int = 1,
        experience: Long = 0,
        vocationId: Int = 0,
    ) = Player(
        id = 77,
        name = "Blessing Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
        level = level,
        experience = experience,
    ).apply {
        this.vocationId = vocationId
    }

    private fun promotedVocations() = Vocations().apply {
        loadFromXml(
            """
            <vocations>
                <vocation id="4" name="Knight" clientid="1"/>
                <vocation id="8" name="Elite Knight" clientid="1" fromvoc="4"/>
            </vocations>
            """.trimIndent(),
        )
    }

    @Test
    fun `blessing enum matches Canary values and order`() {
        // enum class Blessings (src/enums/player_blessings.hpp:16-25).
        assertThat(Blessings.entries.map { it.value })
            .containsExactly(1, 2, 3, 4, 5, 6, 7, 8)
        assertThat(Blessings.TwistOfFate.value).isEqualTo(1)
        assertThat(Blessings.TheWisdomOfSolitude.value).isEqualTo(2)
        assertThat(Blessings.TheSparkOfThePhoenix.value).isEqualTo(3)
        assertThat(Blessings.TheFireOfTheSuns.value).isEqualTo(4)
        assertThat(Blessings.TheSpiritualShielding.value).isEqualTo(5)
        assertThat(Blessings.TheEmbraceOfTibia.value).isEqualTo(6)
        assertThat(Blessings.HearthOfTheMountain.value).isEqualTo(7)
        assertThat(Blessings.BloodOfTheMountain.value).isEqualTo(8)
    }

    @Test
    fun `new player holds no blessings`() {
        // Player::blessings defaults to ten zeroes (player.hpp:1818).
        val player = newPlayer()

        for (index in 1..10) {
            assertThat(player.hasBlessing(index)).isFalse()
            assertThat(player.getBlessingCount(index)).isEqualTo(0)
        }
        assertThat(player.getBlessingsName()).isEmpty()
    }

    @Test
    fun `addBlessing accumulates the count and hasBlessing reports it`() {
        // Player::addBlessing (player.cpp:2422-2428), hasBlessing (2438-2440).
        val player = newPlayer()

        player.addBlessing(3, 1)
        assertThat(player.hasBlessing(3)).isTrue()
        assertThat(player.getBlessingCount(3)).isEqualTo(1)

        player.addBlessing(3, 2)
        assertThat(player.getBlessingCount(3)).isEqualTo(3)
        assertThat(player.hasBlessing(2)).isFalse()
    }

    @Test
    fun `addBlessing stops at the uint8 sentinel`() {
        // player.cpp:2423-2425: a 255 count refuses further additions.
        val player = newPlayer()

        player.addBlessing(1, 255)
        player.addBlessing(1, 1)

        assertThat(player.getBlessingCount(1)).isEqualTo(255)
    }

    @Test
    fun `addBlessing wraps like the uint8 storage`() {
        // player.cpp:2427 stores into `std::vector<uint8_t>`.
        val player = newPlayer()

        player.addBlessing(2, 250)
        player.addBlessing(2, 10)

        assertThat(player.getBlessingCount(2)).isEqualTo(4)
    }

    @Test
    fun `removeBlessing subtracts and ignores an empty slot`() {
        // Player::removeBlessing (player.cpp:2430-2436).
        val player = newPlayer()
        player.addBlessing(1, 3)

        player.removeBlessing(1, 2)
        assertThat(player.getBlessingCount(1)).isEqualTo(1)

        player.removeBlessing(1, 1)
        assertThat(player.hasBlessing(1)).isFalse()

        player.removeBlessing(1, 1)
        assertThat(player.getBlessingCount(1)).isEqualTo(0)
    }

    @Test
    fun `removeBlessing wraps like the uint8 storage`() {
        // player.cpp:2435 stores into `std::vector<uint8_t>`.
        val player = newPlayer()
        player.addBlessing(1, 5)

        player.removeBlessing(1, 10)

        assertThat(player.getBlessingCount(1)).isEqualTo(251)
    }

    @Test
    fun `getBlessingCount bounds out of range indices`() {
        // player.cpp:10545-10551 guards `index > 0 && index <= blessings.size()`.
        val player = newPlayer()

        assertThat(player.getBlessingCount(0)).isEqualTo(0)
        assertThat(player.getBlessingCount(11)).isEqualTo(0)
    }

    @Test
    fun `getBlessingCount reads the store summary when requested`() {
        // player.cpp:10552-10554: kv().scoped("summary")->scoped("blessings")->scoped(index)->get("amount").
        val player = newPlayer()
        player.kv.scoped("summary").scoped("blessings").scoped("2").set("amount", ValueWrapper(3))

        assertThat(player.getBlessingCount(2, storeCount = true)).isEqualTo(3)
        assertThat(player.getBlessingCount(2)).isEqualTo(0)
    }

    @Test
    fun `getBlessingsName joins the held blessings with start case spacing`() {
        // Player::getBlessingsName (player.cpp:10557-10579) over toStartCaseWithSpace (tools.cpp:412-426).
        val player = newPlayer()
        player.addBlessing(1, 1)
        player.addBlessing(2, 1)
        player.addBlessing(5, 1)

        // player.cpp:10569-10575 appends ", " for every name but the last, then "and ".
        assertThat(player.getBlessingsName())
            .isEqualTo("Twist Of Fate, The Wisdom Of Solitude, and The Spiritual Shielding.")
    }

    @Test
    fun `getBlessingsName uses no conjunction for a single blessing`() {
        // player.cpp:10572-10575 only emits "and " when more than one name is present.
        val player = newPlayer()
        player.addBlessing(4, 1)

        assertThat(player.getBlessingsName()).isEqualTo("The Fire Of The Suns.")
    }

    @Test
    fun `checkAndShowBlessingMessage grants the adventurer blessings below the level gate`() {
        // player.cpp:12993-13017: level < ADVENTURERSBLESSING_LEVEL (21) and vocation > none.
        val player = newPlayer(vocationId = 1)
        val messages = mutableListOf<String>()
        var blessStatusSent = 0
        player.sendTextMessage = { messages.add(it) }
        player.sendBlessStatus = { blessStatusSent++ }

        player.checkAndShowBlessingMessage()

        for (index in 2..6) {
            assertThat(player.hasBlessing(index)).isTrue()
        }
        assertThat(player.hasBlessing(1)).isFalse()
        assertThat(blessStatusSent).isEqualTo(1)
        assertThat(messages).containsExactly(
            "You have received adventurer's blessings for being level lower than 21!\n" +
                "You are still blessed with ",
        )
    }

    @Test
    fun `checkAndShowBlessingMessage reports the retained blessings`() {
        // player.cpp:13012: bless non-empty -> "You are still blessed with <names>".
        val player = newPlayer(level = 25)
        player.addBlessing(1, 1)
        val messages = mutableListOf<String>()
        player.sendTextMessage = { messages.add(it) }

        player.checkAndShowBlessingMessage()

        assertThat(messages).containsExactly("You are still blessed with Twist Of Fate.")
    }

    @Test
    fun `checkAndShowBlessingMessage reports the loss of every blessing`() {
        // player.cpp:13012: empty names -> "You lost all your blessings.".
        val player = newPlayer(level = 25)
        val messages = mutableListOf<String>()
        player.sendTextMessage = { messages.add(it) }

        player.checkAndShowBlessingMessage()

        assertThat(messages).containsExactly("You lost all your blessings.")
    }

    @Test
    fun `checkAndShowBlessingMessage skips the level gate for rookies`() {
        // player.cpp:12995: getVocationId() > VOCATION_NONE is required.
        val player = newPlayer(level = 5, vocationId = 0)
        val messages = mutableListOf<String>()
        player.sendTextMessage = { messages.add(it) }

        player.checkAndShowBlessingMessage()

        for (index in 2..6) {
            assertThat(player.hasBlessing(index)).isFalse()
        }
        assertThat(messages).containsExactly("You lost all your blessings.")
    }

    @Test
    fun `getLostPercent uses the fixed ten percent below level 24`() {
        // player.cpp:7377-7380: percentReduction 0 and lossPercent 10.
        val player = newPlayer(level = 10, experience = 1000)

        assertThat(player.getLostPercent()).isEqualTo(0.1)
    }

    @Test
    fun `getLostPercent reduces below level 24 by eight percent per blessing`() {
        // player.cpp:7370-7371 (factor 8), 7378-7379.
        val player = newPlayer(level = 10, experience = 1000)
        for (index in 2..5) {
            player.addBlessing(index, 1)
        }

        assertThat(player.getLostPercent()).isCloseTo(0.068, within(1e-12))
    }

    @Test
    fun `getLostPercent caps the below level 24 reduction at fifty percent`() {
        // player.cpp:7378: `percentReduction >= 0.40 ? 0.50 : percentReduction`.
        val player = newPlayer(level = 10, experience = 1000)
        for (index in 2..6) {
            player.addBlessing(index, 1)
        }

        assertThat(player.getLostPercent()).isEqualTo(0.05)
    }

    @Test
    fun `getLostPercent uses the retro factor when the world is retro`() {
        // player.cpp:7369-7371: retro factor is 6.31 instead of 8.
        val player = newPlayer(level = 10, experience = 1000)
        player.isRetroPvpWorldType = true
        for (index in 2..6) {
            player.addBlessing(index, 1)
        }

        assertThat(player.getLostPercent()).isCloseTo(0.06845, within(1e-12))
    }

    @Test
    fun `getLostPercent at level 24 uses the level formula`() {
        // player.cpp:7374-7376: ((tmpLevel + 50) * 50 * (tmpLevel^2 - 5*tmpLevel + 8)) / experience.
        val player = newPlayer(level = 24, experience = 1_716_800)

        assertThat(player.getLostPercent()).isCloseTo(0.01, within(1e-12))
    }

    @Test
    fun `getLostPercent applies the promotion reduction of thirty percent`() {
        // player.cpp:7385-7387: `percentReduction += 30 / 100.`.
        val player = newPlayer(level = 24, experience = 1_716_800, vocationId = 8)
        player.vocations = promotedVocations()

        with(player) {
            assertThat(isPromoted()).isTrue()
            assertThat(getLostPercent()).isCloseTo(0.007, within(1e-12))
        }
    }

    @Test
    fun `getLostPercent counts only blessings two through eight`() {
        // player.cpp:7352-7356 starts the blessing count at index 2 (Twist of Fate excluded).
        val player = newPlayer(level = 10, experience = 1000)
        player.addBlessing(1, 1)

        assertThat(player.getLostPercent()).isEqualTo(0.1)
    }

    @Test
    fun `getLostPercent honors the configured death lose percent`() {
        // player.cpp:7359-7367: DEATH_LOSE_PERCENT replaces the level formula.
        val player = newPlayer(level = 10, experience = 1000)
        player.deathLosePercent = 10

        assertThat(player.getLostPercent()).isEqualTo(0.1)
    }

    @Test
    fun `getLostPercent subtracts promotion and each blessing when configured`() {
        // player.cpp:7361-7365: promoted subtracts 3, every counted blessing subtracts 1.
        val player = newPlayer(level = 10, experience = 1000, vocationId = 8)
        player.vocations = promotedVocations()
        player.deathLosePercent = 10
        for (index in 2..5) {
            player.addBlessing(index, 1)
        }

        assertThat(player.getLostPercent()).isCloseTo(0.03, within(1e-12))
    }

    @Test
    fun `getLostPercent clamps the configured penalty at zero`() {
        // player.cpp:7366: `std::max<int32_t>(0, deathLosePercent) / 100.`.
        val player = newPlayer(level = 10, experience = 1000, vocationId = 8)
        player.vocations = promotedVocations()
        player.deathLosePercent = 1
        player.addBlessing(2, 1)
        player.addBlessing(3, 1)

        assertThat(player.getLostPercent()).isEqualTo(0.0)
    }

    @Test
    fun `getLostExperience multiplies the experience by the loss percent`() {
        // player.hpp:1921-1923: skillLoss decides between the loss and zero.
        val player = newPlayer(level = 10, experience = 1000)
        player.deathLosePercent = 10

        assertThat(player.getLostExperience()).isEqualTo(100L)

        player.setSkillLoss(false)
        assertThat(player.getLostExperience()).isEqualTo(0L)
    }

    @Test
    fun `addExperience tracks the level percent`() {
        // player.cpp:3737-3741: levelPercent = getPercentLevel(exp - curr, next - curr).
        val player = newPlayer(level = 1, experience = 0)

        player.addExperience(50)

        assertThat(player.getLevelPercent()).isEqualTo(50)
    }

    @Test
    fun `getPercentLevel matches the Canary rounding`() {
        // player.cpp:3831-3840.
        assertThat(Player.getPercentLevel(0, 0)).isEqualTo(0.0)
        assertThat(Player.getPercentLevel(50, 100)).isEqualTo(50.0)
        assertThat(Player.getPercentLevel(1, 3)).isEqualTo(33.33)
        assertThat(Player.getPercentLevel(101, 100)).isEqualTo(0.0)
    }
}
