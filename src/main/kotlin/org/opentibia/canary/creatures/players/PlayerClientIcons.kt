package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.PlayerIcon as ConditionPlayerIcon
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.livestream.IconBakragore
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.network.protocol.PlayerIcon

/**
 * `Player::getClientIcons` (src/creatures/players/player.cpp:909-937): the
 * per-condition icon set `ProtocolGame::sendIcons` serializes through
 * `CreatureWindowWriter.writePlayerIcons` (protocolgame.cpp:6284-6291).
 *
 * The condition list (`Creature::conditions`), `Player::isSuppress`
 * (player.cpp:356-364), `Player::pzLocked` (player.hpp:1869) and
 * `Creature::getTile()` already live on the port's `Player`. The client
 * `sendRestingStatus` call (player.cpp:930, 935) is the seam.
 *
 * The port keeps the condition-side `PlayerIcon` (`condition.hpp`) and the
 * wire-side `PlayerIcon` (`enums/player_icons.hpp`) as two enums; the C++ set
 * is mapped by declared value onto the wire enum.
 */
fun Player.getClientIcons(
    /** `client->sendRestingStatus(1|0)` (player.cpp:930, 935). */
    sendRestingStatus: (Int) -> Unit = {},
): Set<PlayerIcon> {
    val icons = linkedSetOf<PlayerIcon>()

    for (condition in conditions) {
        if (!isSuppress(condition.type, false)) {
            for (conditionIcon in condition.getIcons()) {
                icons.add(conditionIcon.toWireIcon())
            }
            if (icons.size == 9) {
                return icons
            }
        }
    }

    if (pzLocked && icons.size < 9) {
        icons.add(PlayerIcon.RedSwords)
    }

    val tile = getTile()
    if (tile != null && tile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)) {
        if (icons.size < 9) {
            icons.add(PlayerIcon.Pigeon)
        }
        sendRestingStatus(1)

        icons.remove(PlayerIcon.Swords)
    } else {
        sendRestingStatus(0)
    }

    return icons
}

/** `enums/player_icons.hpp` values are shared by both port enums. */
private fun ConditionPlayerIcon.toWireIcon(): PlayerIcon =
    PlayerIcon.entries.first { it.value == value }

/**
 * `Player::removeBakragoreIcons()` (player.cpp:2266-2272): every
 * `magic_enum::enum_values<IconBakragore>()` subtitle (`None`..`Taint9`) whose
 * `CONDITION_BAKRAGORE` condition exists is force-removed with the default
 * condition id.
 */
fun Player.removeBakragoreIcons() {
    for (icon in IconBakragore.entries) {
        if (hasCondition(ConditionType.CONDITION_BAKRAGORE, icon.value)) {
            removeCondition(ConditionType.CONDITION_BAKRAGORE, ConditionId.CONDITIONID_DEFAULT, true)
        }
    }
}

/**
 * `Player::removeBakragoreIcon(icon)` (player.cpp:2274-2278): the same force
 * removal for one `IconBakragore` subtitle.
 */
fun Player.removeBakragoreIcon(icon: IconBakragore) {
    if (hasCondition(ConditionType.CONDITION_BAKRAGORE, icon.value)) {
        removeCondition(ConditionType.CONDITION_BAKRAGORE, ConditionId.CONDITIONID_DEFAULT, true)
    }
}
