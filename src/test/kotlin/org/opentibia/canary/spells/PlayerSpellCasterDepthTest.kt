package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.players.PlayerAttackState
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the [PlayerSpellCaster] side-effect defaults:
 *
 *  - `Player::addManaSpent` (src/creatures/players/player.cpp:3554-3606) driven
 *    from `Spell::postCastSpell` (spells.cpp:936-939);
 *  - `Player::updateLastAggressiveAction` (player.cpp:1186-1188) through the
 *    composed `PlayerAttackState` over `Player::lastAggressiveAction`
 *    (player.hpp:1880);
 *  - `Player::sendCancelMessage(ReturnValue)` (player.cpp:2117-2119) routing
 *    the returned `ReturnValue` into the player's cancel-message sink.
 */
class PlayerSpellCasterDepthTest {

    private fun player(): Player =
        Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 500,
            mana = 500,
            position = Position(100, 100, 7),
        )

    private fun playerWithVocation(): Player = player().apply {
        vocationId = 1
        vocations = Vocations().apply {
            loadFromXml(
                """
                <vocations>
                    <vocation id="1" name="None" clientid="1" description="-" manamultiplier="2.0"/>
                </vocations>
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `the default mana spent progression advances the magic level`() {
        // player.cpp:3554-3595: `currReqMana = vocation->getReqMana(magLevel)`,
        // `nextReqMana = vocation->getReqMana(magLevel + 1)`; `getReqMana(1)` is
        // `floor(1600 * 2^0) = 1600` for the 2.0 multiplier.
        val player = playerWithVocation()
        val caster = PlayerSpellCaster(player)

        caster.addManaSpent(1600)

        assertThat(player.magLevel).isEqualTo(1)
        assertThat(player.manaSpent).isZero()

        caster.addManaSpent(100)

        assertThat(player.magLevel).isEqualTo(1)
        assertThat(player.manaSpent).isEqualTo(100)
    }

    @Test
    fun `the mana spent progression sends the advance message and refreshes stats`() {
        // player.cpp:3580-3582, 3607-3610.
        val player = playerWithVocation()
        val texts = mutableListOf<Pair<MessageClass, String>>()
        var stats = 0
        var skills = 0
        player.sendTextMessageTyped = { type, text -> texts.add(type to text) }
        player.sendStats = { stats++ }
        player.sendSkills = { skills++ }

        PlayerSpellCaster(player).addManaSpent(1700)

        assertThat(texts).containsExactly(MessageClass.MESSAGE_EVENT_ADVANCE to "You advanced to magic level 1.")
        assertThat(stats).isEqualTo(1)
        assertThat(skills).isEqualTo(1)
        assertThat(player.manaSpent).isEqualTo(100)
    }

    @Test
    fun `the mana spent progression stops for not gain mana players`() {
        // player.cpp:3555-3557: the `NotGainMana` early return.
        val player = playerWithVocation()
        player.setFlag(PlayerFlags_t.NotGainMana)

        PlayerSpellCaster(player).addManaSpent(5000)

        assertThat(player.magLevel).isZero()
        assertThat(player.manaSpent).isZero()
    }

    @Test
    fun `the composed attack state clock advances on an aggressive action`() {
        // player.cpp:1186-1188: `lastAggressiveAction = OTSYS_TIME()`.
        var now = 1_000L
        val attackState = PlayerAttackState(now = { now })
        val caster = PlayerSpellCaster(player(), attackState = attackState)

        caster.updateLastAggressiveAction()

        assertThat(attackState.getLastAggressiveAction()).isEqualTo(1_000L)
        now = 2_000L
        assertThat(attackState.checkLastAggressiveActionWithin(2_000)).isTrue()
        now = 4_000L
        assertThat(attackState.checkLastAggressiveActionWithin(2_000)).isFalse()
    }

    @Test
    fun `the default cancel sink routes the return value to the player`() {
        // player.cpp:2117-2119: `sendCancelMessage(getReturnMessage(message))`.
        val player = player()
        val seen = mutableListOf<ReturnValue>()
        player.sendCancelMessage = { seen.add(it) }

        PlayerSpellCaster(player).sendCancelMessage(ReturnValue.NOTENOUGHMANA)

        assertThat(seen).containsExactly(ReturnValue.NOTENOUGHMANA)
    }
}
