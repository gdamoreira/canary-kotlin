package org.opentibia.canary.game.death

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterBehavior
import org.opentibia.canary.creatures.players.PlayerExperienceState
import org.opentibia.canary.creatures.players.onGainExperience
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile

/**
 * World composition of `Creature::onDeath` (`src/creatures/creature.cpp:579-720`)
 * and the `Creature::dropCorpse` corpse/loot pipeline it owns
 * (creature.cpp:732-828).
 *
 * The C++ body resolves the damage-map killers, awards the experience map,
 * runs the kill hooks, drops the corpse and calls the `death` virtual. Every
 * `g_game()`/`g_configManager()`/`Item` global it touches (creature re-lookup,
 * `PZ_LOCKED`, `removeCreature`, magic effects, tile insertion, decay, the kill
 * hooks and the experience sinks) is a constructor seam per AGENTS.md 1.1; the
 * entity-owned virtuals (`getRace`/`getCorpse`/`dropLoot`/`getGainedExperience`/
 * `death`) stay on the creature, exactly like the C++ virtual dispatch.
 *
 * The composition root binds one instance per world (`Game` places it on every
 * creature it accepts); a creature outside a world keeps the port's inert tail.
 *
 * PARITY GAPS:
 * - the `player_death`/`monster_death` metric counters (creature.cpp:675-717);
 * - `Creature::deprecatedOnKilledCreature`'s Lua `onKill` event loop
 *   (creature.cpp:1290-1303), which has no production host in this port;
 * - the reward-corpse/loot-message/auto-loot tail of `dropCorpse`
 *   (creature.cpp:786-816) and the `MESSAGE_EXPERIENCE_OTHERS` notice of
 *   `Creature::onGainExperience` (creature.cpp:1322-1327), which belong to the
 *   protocol and loot subsystems.
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

        val droppedCorpse = dropCorpse(
            creature = creature,
            lastHitCreature = lastHitCreature,
            mostDamageCreature = mostDamageCreature,
            lastHitUnjustified = lastHitUnjustified,
            mostDamageUnjustified = mostDamageUnjustified,
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
     * `Creature::dropCorpse` (creature.cpp:732-828) over the entity virtuals:
     * a no-loot monster only puffs (with the death events when it has a
     * master), otherwise the race splash and the corpse are placed, the loot
     * rolls into the corpse and the death events run with the full
     * `CreatureEvent::executeOnDeath` argument set (creature.cpp:819-824).
     */
    private fun dropCorpse(
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
            // PARITY GAP: reward-corpse flag, loot message, auto-loot path and
            // the corpse container update (creature.cpp:786-816).
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
     * familiar) to its master. The `MESSAGE_EXPERIENCE_OTHERS` spectator notice
     * is a protocol side effect (PARITY GAP).
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
    }
}
