package org.opentibia.canary.game.death

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterBehavior
import org.opentibia.canary.creatures.players.ITEM_FEMALE_CORPSE
import org.opentibia.canary.creatures.players.ITEM_MALE_CORPSE
import org.opentibia.canary.creatures.players.PlayerExperienceState
import org.opentibia.canary.creatures.players.checkAutoLoot
import org.opentibia.canary.creatures.players.onGainExperience
import org.opentibia.canary.creatures.players.sendLootMessage
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.network.protocol.GameWindowWriter
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.protocol.MessageValue
import org.opentibia.canary.network.protocol.TextMessageFrame
import org.opentibia.canary.utils.Tools

/**
 * World composition of `Creature::onDeath` (`src/creatures/creature.cpp:579-720`)
 * and the `Creature::dropCorpse` corpse/loot pipeline it owns
 * (creature.cpp:732-828).
 *
 * The C++ body resolves the damage-map killers, awards the experience map,
 * runs the kill hooks, drops the corpse and calls the `death` virtual. Every
 * `g_game()`/`g_configManager()`/`Item` global it touches (creature re-lookup,
 * `PZ_LOCKED`, `removeCreature`, magic effects, tile insertion, decay, the kill
 * hooks, the reward-corpse/loot-message/auto-loot tail, the
 * `MESSAGE_EXPERIENCE_OTHERS` spectator fanout, the metric counters and the
 * experience sinks) is a constructor seam per AGENTS.md 1.1; the entity-owned
 * virtuals (`getRace`/`getCorpse`/`dropCorpse`/`dropLoot`/`getGainedExperience`/
 * `getNameDescription`/`death`) stay on the creature, exactly like the C++
 * virtual dispatch.
 *
 * The composition root binds one instance per world (`Game` places it on every
 * creature it accepts); a creature outside a world keeps the port's inert tail.
 */
class CreatureDeathPipeline(
    /** `g_game().getCreatureByID` (game.cpp:1296) for the last-hit/damage-map re-resolution. */
    private val resolveCreature: (Long) -> Creature? = { null },
    /** `g_configManager().getNumber(PZ_LOCKED)` (configmanager.cpp:340), the most-damage in-fight window. */
    private val pzLocked: () -> Int = { PZ_LOCKED_DEFAULT },
    /** `g_game().removeCreature(creature, false)` (game.cpp:1551) for the dropped-corpse removal. */
    private val removeCreature: (Creature) -> Unit = {},
    /** `g_game().addMagicEffect(pos, effect)` (game.cpp:9343). */
    private val addMagicEffect: (Position, Int) -> Unit = { _, _ -> },
    /** `g_game().internalAddItem(tile, item, INDEX_WHEREEVER, FLAG_NOLIMIT)` (game.cpp:2693). */
    private val addItemToTile: (Tile, Item) -> Unit = { tile, item -> tile.addItem(item) },
    /** `Item::startDecaying()` (item.cpp:3501). */
    private val startDecaying: (Item) -> Unit = {},
    /** `Player::onKilledMonster` (player.cpp:6680-6698). */
    private val onKilledMonster: (Player, Monster) -> Unit = { _, _ -> },
    /** `Player::onKilledPlayer` (player.cpp:6595-6604) — returns the unjustified flag. */
    private val onKilledPlayer: (Creature, Player, Boolean) -> Boolean = { _, _, _ -> false },
    /** `Creature::deprecatedOnKilledCreature` (creature.cpp:1290-1303) — returns the unjustified flag. */
    private val deprecatedOnKilledCreature: (Creature, Creature, Boolean) -> Boolean = { _, _, _ -> false },
    /** `Player::removeAttacked` (player.cpp:7235-7245). */
    private val removeAttacked: (Player, Creature) -> Unit = { _, _ -> },
    /** `Player::getProtocolVersion` (player.cpp:1022-1030) behind the loot-message colour gate (creature.cpp:789). */
    private val getProtocolVersion: (Player) -> Int = { 0 },
    /** `Player::sendLootMessage` (player.cpp:12847-12862). */
    private val sendLootMessage: (Player, String) -> Unit = { player, message -> player.sendLootMessage(message) },
    /** `Item::isRewardCorpse` (item.hpp:140) behind the `disallowedCorpses` gate (creature.cpp:787). */
    private val isRewardCorpse: (Item) -> Boolean = { false },
    /**
     * `g_game().map.getPathMatching(player->getPosition(), dirList,
     * FrozenPathingConditionCall(corpse->getPosition()), fpp)` with
     * `minTargetDist = 0`, `maxTargetDist = 1`, `fullPathSearch`, `clearSight`
     * (creature.cpp:798-806).
     */
    private val isCorpseReachable: (Player, Position) -> Boolean = { _, _ -> false },
    /** `Player::checkAutoLoot(isBoss)` (player.cpp:5692-5708). */
    private val checkAutoLoot: (Player, Boolean) -> Boolean = { player, isBoss -> player.checkAutoLoot(isBoss) },
    /** `g_dispatcher().addEvent(..., "Game::playerQuickLootCorpse")` (creature.cpp:808-812). */
    private val scheduleQuickLoot: (Player, Container, Position) -> Unit = { _, _, _ -> },
    /** `corpse->sendUpdateToClient(player)` (creature.cpp:814). */
    private val sendCorpseUpdate: (Player, Item) -> Unit = { _, _ -> },
    /** `Spectators().find<Player>(position)` (creature.cpp:1323). */
    private val findSpectatorPlayers: (Position) -> List<Player> = { emptyList() },
    /** `Player::sendTextMessage(TextMessage)` (creature.cpp:1326, player.cpp:6350-6407). */
    private val sendTextMessageFrame: (Player, TextMessageFrame) -> Unit = { player, frame ->
        player.sendTextMessageFrame(frame)
    },
    /** `g_metrics().addCounter(name, value, labels)` (creature.cpp:690-717). */
    private val addCounter: (String, Double, Map<String, String>) -> Unit = { _, _, _ -> },
) {
    /**
     * `Creature::onDeath` (creature.cpp:579-720) over [creature]: the `onDeath`
     * listener tail is the caller's (the port's creature hook), then the C++
     * damage-map resolution, the `dropCorpse`/`death`/removal/`removeMaster`
     * sequence. Every attacker re-resolves by id, so a removed or replaced
     * creature is skipped (AGENTS.md deferred-callback lifetime safety).
     */
    fun onDeath(creature: Creature) {
        var lastHitUnjustified = false
        var mostDamageUnjustified = false

        val lastHitCreature = resolveCreature(creature.lastHitCreatureId)
        val thisPlayer = creature as? Player
        val thisMonster = creature as? Monster
        val thisMaster = creature.getMaster()
        var lastHitCreatureMaster: Creature? = null

        if (lastHitCreature != null && thisPlayer != null) {
            deprecatedOnKilledCreature(lastHitCreature, creature, true)
            lastHitUnjustified = onKilledPlayer(lastHitCreature, thisPlayer, true)
            lastHitCreatureMaster = lastHitCreature.getMaster()
            if (creature.getZoneType() == ZoneType.ZONE_PVP) {
                creature.setDropLoot(false)
                creature.setSkillLoss(false)
            }
        }

        var mostDamageCreature: Creature? = null
        val timeNow = creature.now()
        val inFightTicks = pzLocked()
        var mostDamage = 0
        val experienceMap = LinkedHashMap<Creature, Long>()
        val killers = LinkedHashSet<Player>()

        for ((creatureId, damageInfo) in creature.getDamageMap()) {
            if (creatureId == 0L) {
                continue
            }

            val attacker = resolveCreature(creatureId) ?: continue
            val total = damageInfo.total
            val ticks = damageInfo.ticks
            if (total == 0 || ticks == 0L) {
                continue
            }

            if (total > mostDamage && timeNow - ticks <= inFightTicks) {
                mostDamage = total
                mostDamageCreature = attacker
            }

            if (attacker === creature) {
                continue
            }

            val gainExp = creature.getGainedExperience(attacker)
            var effectiveAttacker = attacker
            val attackerMaster = attacker.getMaster() ?: attacker
            val attackerPlayer = attackerMaster as? Player
            if (attackerPlayer != null) {
                val party = attackerPlayer.getParty()
                killers.add(attackerPlayer)
                val leader = party?.getLeader()
                if (leader != null && party.isSharedExperienceActive() && party.isSharedExperienceEnabled()) {
                    effectiveAttacker = leader
                    killers.add(leader)
                    mostDamageCreature = leader

                    for (partyMember in party.getMembers()) {
                        killers.add(partyMember)
                    }
                }
            }

            experienceMap[effectiveAttacker] = (experienceMap[effectiveAttacker] ?: 0L) + gainExp
        }

        for ((attacker, experience) in experienceMap) {
            awardExperience(attacker, experience, creature)
        }

        val mostDamageCreatureMaster = mostDamageCreature?.getMaster()
        mostDamageCreature = mostDamageCreatureMaster ?: mostDamageCreature

        for (killer in killers) {
            if (thisMonster != null) {
                onKilledMonster(killer, thisMonster)
            } else if (thisPlayer != null) {
                val isResponsible = mostDamageCreature === killer || mostDamageCreatureMaster === killer
                if (isResponsible && !lastHitUnjustified) {
                    onKilledPlayer(killer, thisPlayer, false)
                }

                removeAttacked(killer, thisPlayer)
            }
        }

        if (mostDamageCreature != null &&
            (mostDamageCreature !== lastHitCreature || thisMonster != null) &&
            mostDamageCreature !== lastHitCreatureMaster
        ) {
            if (lastHitCreature !== mostDamageCreatureMaster &&
                (lastHitCreatureMaster == null || mostDamageCreatureMaster !== lastHitCreatureMaster)
            ) {
                mostDamageUnjustified = deprecatedOnKilledCreature(mostDamageCreature, creature, false)
            }
        }

        recordDeathMetrics(
            creature = creature,
            thisPlayer = thisPlayer,
            thisMaster = thisMaster,
            lastHitCreature = lastHitCreature,
            mostDamageCreature = mostDamageCreature,
            mostDamageCreatureMaster = mostDamageCreatureMaster,
            mostDamage = mostDamage,
            lastHitUnjustified = lastHitUnjustified,
            mostDamageUnjustified = mostDamageUnjustified,
        )

        // `dropCorpse(...)` is the virtual call of `Creature::onDeath`
        // (creature.cpp:720); `Player::dropCorpse` overrides the PvP
        // player-kill short-circuit.
        val droppedCorpse = creature.dropCorpse(
            lastHitCreature,
            mostDamageCreature,
            lastHitUnjustified,
            mostDamageUnjustified,
        )
        creature.death(lastHitCreature)

        if (droppedCorpse && thisPlayer == null) {
            removeCreature(creature)
        }

        if (thisMaster != null) {
            creature.removeMaster()
        }
    }

    /**
     * `Creature::onDeath` metric tail (creature.cpp:688-717): a player death
     * records `player_death` with the level/attribution/justified label set, a
     * monster death records `monster_death` with the resolved killer (the
     * most-damage master wins the name) and the summon flag. The C++
     * `std::to_string` conversions render booleans as `"1"`/`"0"` and names as
     * themselves or `(none)`.
     */
    private fun recordDeathMetrics(
        creature: Creature,
        thisPlayer: Player?,
        thisMaster: Creature?,
        lastHitCreature: Creature?,
        mostDamageCreature: Creature?,
        mostDamageCreatureMaster: Creature?,
        mostDamage: Int,
        lastHitUnjustified: Boolean,
        mostDamageUnjustified: Boolean,
    ) {
        val mostDamagePlayer = mostDamageCreature as? Player
        val mostMasterPlayer = mostDamageCreatureMaster as? Player
        val killedByPlayer = mostDamagePlayer != null || mostMasterPlayer != null

        if (thisPlayer != null) {
            addCounter(
                "player_death",
                1.0,
                mapOf(
                    "name" to creature.getNameDescription(),
                    "level" to thisPlayer.level.toString(),
                    "most_damage_creature" to (mostDamageCreature?.name ?: "(none)"),
                    "last_hit_creature" to (lastHitCreature?.name ?: "(none)"),
                    "most_damage_dealt" to mostDamage.toString(),
                    "most_damage_creature_master" to (mostDamageCreatureMaster?.name ?: "(none)"),
                    "most_damage_unjustified" to toMetricBoolean(mostDamageUnjustified),
                    "last_hit_unjustified" to toMetricBoolean(lastHitUnjustified),
                    "by_player" to toMetricBoolean(killedByPlayer),
                ),
            )
            return
        }

        var killerName = mostDamageCreature?.name ?: "(none)"
        if (mostDamageCreatureMaster != null) {
            killerName = mostDamageCreatureMaster.name
        }
        addCounter(
            "monster_death",
            1.0,
            mapOf(
                "name" to creature.name,
                "killer" to killerName,
                "is_summon" to toMetricBoolean(thisMaster != null),
                "by_player" to toMetricBoolean(killedByPlayer),
            ),
        )
    }

    /**
     * `Creature::dropCorpse` (creature.cpp:732-828) over the entity virtuals:
     * a no-loot monster only puffs (with the death events when it has a
     * master), otherwise the race splash and the corpse are placed, the loot
     * rolls into the corpse, the reward-corpse/loot-message/auto-loot tail runs
     * and the death events execute with the full `CreatureEvent::executeOnDeath`
     * argument set (creature.cpp:819-824).
     */
    fun dropCorpse(
        creature: Creature,
        lastHitCreature: Creature?,
        mostDamageCreature: Creature?,
        lastHitUnjustified: Boolean,
        mostDamageUnjustified: Boolean,
    ): Boolean {
        if (!creature.lootDrop && creature is Monster) {
            if (creature.getMaster() != null) {
                creature.onDeathEventsHost(
                    creature,
                    null,
                    lastHitCreature,
                    mostDamageCreature,
                    lastHitUnjustified,
                    mostDamageUnjustified,
                )
            }

            addMagicEffect(creature.position, MonsterBehavior.CONST_ME_POFF)
            return true
        }

        val tile = creature.getTile()
        val splashFluid = MonsterBehavior.splashFluidForRace(creature.getRace())
        if (tile != null && splashFluid != null) {
            val splash = Item.create(MonsterBehavior.ITEM_FULLSPLASH, splashFluid)
            addItemToTile(tile, splash)
            startDecaying(splash)
        }

        val corpse = creature.getCorpse(lastHitCreature, mostDamageCreature)
        if (tile != null && corpse != null) {
            addItemToTile(tile, corpse)
            creature.dropLoot(corpse.getContainer(), lastHitCreature)
            startDecaying(corpse)

            // Reward-corpse/loot-message/auto-loot tail (creature.cpp:786-816).
            val disallowedCorpses = isRewardCorpse(corpse) ||
                corpse.id == ITEM_MALE_CORPSE || corpse.id == ITEM_FEMALE_CORPSE
            val player = mostDamageCreature as? Player
            val corpseContainer = corpse.getContainer()
            if (corpseContainer != null && player != null && !disallowedCorpses) {
                val monster = creature as? Monster
                if (monster != null && !monster.isRewardBoss()) {
                    val colourMessage = getProtocolVersion(player) > 1200
                    val suffix = corpseContainer.getAttribute(ItemAttribute.LOOTMESSAGE_SUFFIX) as? String ?: ""
                    var lootMessage =
                        "Loot of ${creature.getNameDescription()}: ${getContentDescription(corpseContainer, colourMessage)}"
                    if (suffix.isNotEmpty()) {
                        lootMessage = "$lootMessage ($suffix)"
                    }
                    sendLootMessage(player, "$lootMessage.")
                }

                if (monster != null && checkAutoLoot(player, monster.isRewardBoss()) &&
                    isCorpseReachable(player, corpse.position)
                ) {
                    scheduleQuickLoot(player, corpseContainer, corpse.position)
                }

                sendCorpseUpdate(player, corpse)
            }
        }

        creature.onDeathEventsHost(
            creature,
            corpse,
            lastHitCreature,
            mostDamageCreature,
            lastHitUnjustified,
            mostDamageUnjustified,
        )
        return true
    }

    /**
     * `Container::getContentDescription(sendColoredMessage)`
     * (src/items/containers/container.cpp:295-318): the container iterator
     * skips every non-empty nested container (its children are described
     * instead) and renders each leaf through `Item::getNameDescription`, in the
     * `{id|name}` form for a coloured message and the plain name otherwise;
     * an empty corpse reads `nothing`. The `items` package is outside this
     * workstream's file ownership, so the C++ body is ported here, at its only
     * non-Lua call site (`Creature::dropCorpse`, creature.cpp:791).
     */
    private fun getContentDescription(container: Container, sendColoredMessage: Boolean): String {
        val descriptions = mutableListOf<String>()
        val iterator = container.iterator()
        while (iterator.hasNext()) {
            val item = iterator.current() ?: break
            iterator.advance()

            val nested = item.getContainer()
            if (nested != null && nested.items.isNotEmpty()) {
                continue
            }

            descriptions += if (sendColoredMessage) {
                "{${item.id}|${item.getNameDescription()}}"
            } else {
                item.getNameDescription()
            }
        }

        return if (descriptions.isEmpty()) "nothing" else descriptions.joinToString(", ")
    }


    /**
     * `creature->onGainExperience(experience, thisCreature)` dispatch
     * (creature.cpp:655-657): a `Player` attacker runs `Player::onGainExperience`
     * (player.cpp:6709-6722) over the production gain sinks; any other attacker
     * runs the `Creature::onGainExperience` master chain (creature.cpp:1308-1328).
     *
     * The shared-experience target gate passes the dying creature only when it is
     * not a player, mirroring the C++ `target->getPlayer() == nullptr` check
     * (the port's `Thing::getPlayer` base returns `null` for every creature).
     */
    private fun awardExperience(attacker: Creature, gainExp: Long, target: Creature?) {
        if (attacker is Player) {
            val shareTarget = target.takeIf { it !is Player }
            attacker.onGainExperience(
                gainExp = gainExp,
                target = shareTarget,
                creatureOnGainExperience = { exp, tgt -> creatureOnGainExperience(attacker, exp, tgt) },
                gainExperience = { exp, tgt -> playerGainExperience(attacker, exp, tgt) },
            )
        } else {
            creatureOnGainExperience(attacker, gainExp, target)
        }
    }

    /**
     * `Creature::onGainExperience` (creature.cpp:1308-1328): a zero gain or a
     * masterless creature stops; every other summon forwards half (all, for a
     * familiar) to its master. A non-familiar gain additionally notifies every
     * player in the creature's position with the
     * `MESSAGE_EXPERIENCE_OTHERS` text frame (creature.cpp:1322-1327), showing
     * the halved gain the C++ local carries after `gainExp /= 2`.
     */
    private fun creatureOnGainExperience(creature: Creature, gainExp: Long, target: Creature?) {
        if (gainExp == 0L) {
            return
        }

        val master = creature.getMaster() ?: return
        val monster = creature as? Monster
        val isFamiliar = monster?.monsterDeathHost?.isFamiliar ?: false
        val granted = if (isFamiliar) gainExp else gainExp / 2

        awardExperience(master, granted, target)

        if (isFamiliar) {
            return
        }

        val spectators = findSpectatorPlayers(creature.position)
        if (spectators.isEmpty()) {
            return
        }

        val suffix = if (granted != 1L) "s" else ""
        val frame = TextMessageFrame(
            type = MessageClass.MESSAGE_EXPERIENCE_OTHERS,
            text = "${Tools.ucfirst(creature.getNameDescription())} gained $granted experience point$suffix.",
            position = creature.position,
            primary = MessageValue(granted, GameWindowWriter.TEXTCOLOR_WHITE_EXP),
        )
        for (spectator in spectators) {
            sendTextMessageFrame(spectator, frame)
        }
    }

    /**
     * `Player::gainExperience` (player.cpp:6701-6707) over the ported
     * [PlayerExperienceState] (`Player::addExperience`, player.cpp:3613).
     */
    private fun playerGainExperience(player: Player, gainExp: Long, target: Creature?) {
        PlayerExperienceState(
            player = player,
            getDamageMap = { player.getDamageMap() },
            addExperience = { _, exp, _ -> player.addExperience(exp) },
        ).gainExperience(gainExp, target)
    }

    companion object {
        /** `loadIntConfig(L, PZ_LOCKED, "pzLocked", 60000)` (configmanager.cpp:340). */
        const val PZ_LOCKED_DEFAULT = 60_000

        /** `std::to_string(bool)` behind the C++ metric labels. */
        private fun toMetricBoolean(value: Boolean): String = if (value) "1" else "0"
    }
}
