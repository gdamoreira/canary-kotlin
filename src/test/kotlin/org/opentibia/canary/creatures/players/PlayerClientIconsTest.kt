package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.livestream.IconBakragore
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.network.protocol.PlayerIcon

/**
 * Transcribes `Player::getClientIcons` (src/creatures/players/player.cpp:909-937):
 * the icon bitset `ProtocolGame::sendIcons` serializes through
 * `CreatureWindowWriter.writePlayerIcons` (protocolgame.cpp:6284-6291).
 *
 * The condition list (`Creature::conditions`), `Player::isSuppress`
 * (player.cpp:356-364), `Player::pzLocked` (player.hpp:1869) and
 * `Creature::getTile()` already live on the port's `Player`; the client
 * `sendRestingStatus` call (player.cpp:930, 935) is the only seam.
 *
 * The Bakragore icon removals are transcribed too:
 * `Player::removeBakragoreIcons`/`removeBakragoreIcon` (player.cpp:2266-2278).
 */
class PlayerClientIconsTest {

    private fun newPlayer() =
        Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun addCondition(
        player: Player,
        type: ConditionType,
        subId: Int = 0,
        buff: Boolean = false,
    ): Condition {
        val condition = Condition.createCondition(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = type,
            ticks = 60_000,
            buff = buff,
            subId = subId,
        )
        checkNotNull(condition) { "condition $type must be creatable" }
        assertThat(player.addCondition(condition)).isTrue()
        return condition
    }

    @Test
    fun `no conditions yields no icons and a resting status of zero`() {
        // player.cpp:909-937, no condition and no protection zone.
        val player = newPlayer()
        val resting = mutableListOf<Int>()

        val icons = player.getClientIcons { resting.add(it) }

        assertThat(icons).isEmpty()
        assertThat(resting).containsExactly(0)
    }

    @Test
    fun `condition icons are collected`() {
        // player.cpp:912-920: `condition->getIcons()`.
        val player = newPlayer()
        addCondition(player, ConditionType.CONDITION_INFIGHT)

        val icons = player.getClientIcons()

        assertThat(icons).containsExactly(PlayerIcon.Swords)
    }

    @Test
    fun `suppressed condition types are skipped`() {
        // player.cpp:913: `!isSuppress(condition->getType(), false)`.
        val player = newPlayer()
        addCondition(player, ConditionType.CONDITION_INFIGHT)
        player.addConditionSuppressions(arrayOf(ConditionType.CONDITION_INFIGHT))

        val icons = player.getClientIcons()

        assertThat(icons).isEmpty()
    }

    @Test
    fun `pz lock adds the red swords icon`() {
        // player.cpp:922-924.
        val player = newPlayer()
        player.pzLocked = true

        val icons = player.getClientIcons()

        assertThat(icons).containsExactly(PlayerIcon.RedSwords)
    }

    @Test
    fun `protection zone adds the pigeon and removes the swords icon`() {
        // player.cpp:926-936.
        val player = newPlayer()
        addCondition(player, ConditionType.CONDITION_INFIGHT)
        val tile = Tile(Position(100, 100, 7))
        tile.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        player.setParent(tile)
        val resting = mutableListOf<Int>()

        val icons = player.getClientIcons { resting.add(it) }

        assertThat(icons).containsExactly(PlayerIcon.Pigeon)
        assertThat(icons).doesNotContain(PlayerIcon.Swords)
        assertThat(resting).containsExactly(1)
    }

    @Test
    fun `nine collected icons short-circuit the remaining conditions`() {
        // player.cpp:918-920: `if (icons.size() == 9) return icons;`. Haste and
        // paralyze are mutually exclusive (Creature::onAddCondition), so the
        // nine sources below are pairwise independent.
        val player = newPlayer()
        val types = listOf(
            ConditionType.CONDITION_INFIGHT,
            ConditionType.CONDITION_DRUNK,
            ConditionType.CONDITION_ROOTED,
            ConditionType.CONDITION_LESSERHEX,
            ConditionType.CONDITION_INTENSEHEX,
            ConditionType.CONDITION_GREATERHEX,
            ConditionType.CONDITION_MANASHIELD,
            ConditionType.CONDITION_MENTOROTHER,
        )
        for (type in types) {
            addCondition(player, type)
        }
        addCondition(player, ConditionType.CONDITION_GOSHNARTAINT, subId = 1)
        addCondition(player, ConditionType.CONDITION_POWERLESS, buff = true)

        val icons = player.getClientIcons()

        assertThat(icons).hasSize(9)
        assertThat(icons).doesNotContain(PlayerIcon.PartyBuff, PlayerIcon.Powerless)
    }

    // --- Player::removeBakragoreIcons / removeBakragoreIcon ----------------------

    @Test
    fun `removeBakragoreIcon force-removes every bakragore subtitle`() {
        // player.cpp:2274-2278: the guard is hasCondition(type, enumToValue(icon)),
        // but `Creature::removeCondition(type, CONDITIONID_DEFAULT, force)`
        // (creature.cpp:1435-1467) erases every condition of that type+id pair,
        // so all subtitles fall together.
        val player = newPlayer()
        addCondition(player, ConditionType.CONDITION_BAKRAGORE, subId = IconBakragore.Taint3.value)
        addCondition(player, ConditionType.CONDITION_BAKRAGORE, subId = IconBakragore.Taint5.value)

        player.removeBakragoreIcon(IconBakragore.Taint3)

        assertThat(player.hasCondition(ConditionType.CONDITION_BAKRAGORE, IconBakragore.Taint3.value)).isFalse()
        assertThat(player.hasCondition(ConditionType.CONDITION_BAKRAGORE, IconBakragore.Taint5.value)).isFalse()
    }

    @Test
    fun `removeBakragoreIcon is inert without the matching condition`() {
        // The `if (hasCondition(...))` guard (2275).
        val player = newPlayer()
        addCondition(player, ConditionType.CONDITION_BAKRAGORE, subId = IconBakragore.Taint1.value)

        player.removeBakragoreIcon(IconBakragore.Taint9)

        assertThat(player.hasCondition(ConditionType.CONDITION_BAKRAGORE, IconBakragore.Taint1.value)).isTrue()
    }

    @Test
    fun `removeBakragoreIcons clears every taint subtitle`() {
        // player.cpp:2266-2272: magic_enum::enum_values<IconBakragore>() = None..Taint9.
        val player = newPlayer()
        for (icon in IconBakragore.entries) {
            addCondition(player, ConditionType.CONDITION_BAKRAGORE, subId = icon.value)
        }

        player.removeBakragoreIcons()

        for (icon in IconBakragore.entries) {
            assertThat(player.hasCondition(ConditionType.CONDITION_BAKRAGORE, icon.value)).isFalse()
        }
    }
}
