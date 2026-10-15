package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.Town

/**
 * Wave 47 A2 transcript tests for the creature move/appear/remove host hooks:
 *
 * - `Map::moveCreature` spectator fanout to `Creature::onCreatureMove`
 *   (src/map/map.cpp:788-790) and the virtual body (creature.cpp:403-577).
 * - `Player::onCreatureAppear` (player.cpp:12220-12316),
 *   `Player::onRemoveCreature` (player.cpp:12318-12360) and
 *   `Player::onCreatureMove` (player.cpp:12362-12425).
 * - `Monster::onCreatureAppear`/`onRemoveCreature`/`onCreatureMove` tails
 *   (monster.cpp:348-477) through the pipeline host coordinator seams.
 * - `PlayerActionTimers` (player.cpp:3412-3477, 8540) and `PlayerTownState`
 *   (player.cpp:8307-8326) host composition; `Player::isInGhostMode`
 *   (player.hpp:566-569) and `Player::switchGhostMode` (player.hpp:571-573).
 * - `Creature::drainHealth`/`gainHealth` sink consistency
 *   (creature.cpp:886-899, condition.cpp:1507-1510).
 */
class CreatureHostHooksTest {

    private var nowValue = 1_000_000L

    @BeforeEach
    fun resetSpectatorsCache() {
        Spectators.clearCache()
    }

    private fun GameMap.withGroundTile(position: Position): Tile {
        val tile = getOrCreateTile(position)
        tile.ground = Item(id = 100)
        return tile
    }

    private fun monster(id: Long, position: Position): Monster =
        Monster(id, "M$id", 100, 100, position = position)

    private fun player(id: Long = 1, position: Position = Position(100, 100, 7)): Player =
        Player(id, "P$id", 100, 100, position = position).apply {
            now = { nowValue }
        }

    private fun rootedCondition(): Condition =
        Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT,
            ConditionType.CONDITION_ROOTED,
            60_000,
            now = { nowValue },
        )!!

    private fun paralyzeCondition(): Condition =
        Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT,
            ConditionType.CONDITION_PARALYZE,
            60_000,
            now = { nowValue },
        )!!

    /** Direct [Creature] subclass so every open hook can be observed. */
    private class ProbeCreature(
        id: Long,
        maxHealth: Int = 100,
        health: Int = maxHealth,
        speed: Int = 200,
        position: Position = Position(100, 100, 7),
    ) : Creature(id, "Probe $id", maxHealth, health, 0, 0, speed, position) {

        data class MoveCall(
            val creature: Creature,
            val newPos: Position,
            val oldPos: Position,
            val teleport: Boolean,
        )

        val moves = mutableListOf<MoveCall>()
        var resets = 0
        var deaths = 0
        var extraSwing = false
        var attacks = 0
        val followDisappears = mutableListOf<Boolean>()
        val attackedDisappears = mutableListOf<Boolean>()
        val zoneChanges = mutableListOf<ZoneType>()
        val attackedZoneChanges = mutableListOf<ZoneType>()

        override fun onCreatureMove(
            creature: Creature,
            newTile: Tile?,
            newPos: Position,
            oldTile: Tile?,
            oldPos: Position,
            teleport: Boolean,
        ) {
            moves.add(MoveCall(creature, newPos, oldPos, teleport))
            super.onCreatureMove(creature, newTile, newPos, oldTile, oldPos, teleport)
        }

        override fun resetMovementState() {
            resets++
            super.resetMovementState()
        }

        override fun death(lastHitCreature: Creature?) {
            deaths++
        }

        override fun onFollowCreatureDisappear(isLogout: Boolean) {
            followDisappears.add(isLogout)
        }

        override fun onAttackedCreatureDisappear(isLogout: Boolean) {
            attackedDisappears.add(isLogout)
        }

        override fun onChangeZone(zone: ZoneType) {
            zoneChanges.add(zone)
            super.onChangeZone(zone)
        }

        override fun onAttackedCreatureChangeZone(zone: ZoneType) {
            attackedZoneChanges.add(zone)
            super.onAttackedCreatureChangeZone(zone)
        }

        override fun hasExtraSwing(): Boolean = extraSwing
    }

    // --- Map::moveCreature -> Creature::onCreatureMove (map.cpp:788-790) -----------

    @Test
    fun `moveCreature notifies the mover and every spectator through onCreatureMove`() {
        val map = GameMap()
        val center = Position(100, 100, 7)
        val from = Position(101, 100, 7)
        val to = Position(102, 100, 7)
        listOf(center, from, to).forEach { map.withGroundTile(it) }
        val observer = ProbeCreature(1, position = center)
        val mover = monster(2, from)
        val moves = mutableListOf<Pair<Position, Position>>()
        mover.onCreatureMoveHost = { _, oldPos, newPos -> moves.add(oldPos to newPos) }
        assertThat(map.placeCreature(center, observer)).isTrue()
        assertThat(map.placeCreature(from, mover)).isTrue()

        map.moveCreature(mover, map.getTile(to)!!)

        // map.cpp:706-722 includes the mover in its own spectator snapshot.
        assertThat(observer.moves).containsExactly(
            ProbeCreature.MoveCall(mover, to, from, false),
        )
        assertThat(moves).containsExactly(from to to)
    }

    @Test
    fun `removeCreature runs the hardcoded disappear flag of the base hook`() {
        // creature.cpp:412-414 ignores the isLogout parameter and always calls
        // onCreatureDisappear(creature, true).
        val map = GameMap()
        val observerPos = Position(100, 100, 7)
        val victimPos = Position(101, 100, 7)
        map.withGroundTile(observerPos)
        map.withGroundTile(victimPos)
        val observer = ProbeCreature(1, position = observerPos)
        val victim = monster(2, victimPos)
        assertThat(map.placeCreature(observerPos, observer)).isTrue()
        assertThat(map.placeCreature(victimPos, victim)).isTrue()
        observer.setAttackedCreature(victim)

        map.removeCreature(victim, isLogout = false)

        assertThat(observer.getAttackedCreature()).isNull()
        assertThat(observer.attackedDisappears).containsExactly(true)
    }

    // --- Creature::onCreatureMove self-move bookkeeping (creature.cpp:501-548) -----

    @Test
    fun `self move stamps lastStep and the step costs`() {
        val probe = ProbeCreature(1)
        probe.now = { nowValue }
        val tile = Tile(Position(100, 100, 7))

        probe.onCreatureMove(probe, tile, Position(101, 100, 7), tile, Position(100, 100, 7), false)
        assertThat(probe.lastStep).isEqualTo(nowValue)
        assertThat(probe.lastStepCost).isEqualTo(1)

        probe.onCreatureMove(probe, tile, Position(101, 101, 7), tile, Position(100, 100, 7), false)
        assertThat(probe.lastStepCost).isEqualTo(Creature.WALK_DIAGONAL_EXTRA_COST)

        probe.onCreatureMove(probe, tile, Position(100, 100, 6), tile, Position(100, 100, 7), false)
        assertThat(probe.lastStepCost).isEqualTo(Creature.WALK_FLOOR_CHANGE_EXTRA_COST)
    }

    @Test
    fun `teleport self move stops the walk event`() {
        val probe = ProbeCreature(1)
        probe.now = { nowValue }
        probe.eventWalk = 7
        val stopped = mutableListOf<Long>()
        probe.stopEvent = { stopped.add(it) }
        val tile = Tile(Position(100, 100, 7))

        probe.onCreatureMove(probe, tile, Position(150, 100, 7), tile, Position(100, 100, 7), true)

        assertThat(stopped).containsExactly(7L)
        assertThat(probe.eventWalk).isZero()
        assertThat(probe.lastStepCost).isEqualTo(1)
    }

    @Test
    fun `rooted self move resets the movement state and returns early`() {
        val probe = ProbeCreature(1)
        probe.now = { nowValue }
        probe.addCondition(rootedCondition())
        val target = ProbeCreature(2, position = Position(101, 100, 7))
        probe.setAttackedCreature(target)
        val tile = Tile(Position(100, 100, 7))

        probe.onCreatureMove(probe, tile, Position(101, 100, 7), tile, Position(100, 100, 7), false)

        assertThat(probe.resets).isEqualTo(1)
        assertThat(probe.lastStep).isZero()
        assertThat(probe.getAttackedCreature()).isSameAs(target)
    }

    // --- Creature::onCreatureMove follow/attack disappear (creature.cpp:550-577) ---

    @Test
    fun `moved follow target leaving sight clears the follow and fires the hook`() {
        val follower = ProbeCreature(1, position = Position(100, 100, 7))
        val follow = ProbeCreature(2, position = Position(105, 100, 7))
        assertThat(follower.setFollowCreature(follow)).isTrue()

        // Map::moveCreature re-parents the creature before the fanout, so the
        // hook reads the post-move position.
        val oldPos = follow.position
        follow.position = Position(150, 100, 7)

        follower.onCreatureMove(follow, null, follow.position, null, oldPos, false)

        assertThat(follower.getFollowCreature()).isNull()
        assertThat(follower.followDisappears).containsExactly(false)
    }

    @Test
    fun `moved attack target leaving sight clears the target and fires the hook`() {
        val attacker = ProbeCreature(1, position = Position(100, 100, 7))
        val target = ProbeCreature(2, position = Position(105, 100, 7))
        assertThat(attacker.setAttackedCreature(target)).isTrue()

        val oldPos = target.position
        target.position = Position(150, 100, 7)

        attacker.onCreatureMove(target, null, target.position, null, oldPos, false)

        assertThat(attacker.getAttackedCreature()).isNull()
        assertThat(attacker.attackedDisappears).containsExactly(false)
    }

    @Test
    fun `moved attack target in sight with extra swing checks the attack`() {
        val attacker = ProbeCreature(1, position = Position(100, 100, 7))
        val target = ProbeCreature(2, position = Position(105, 100, 7))
        assertThat(attacker.setAttackedCreature(target)).isTrue()
        attacker.extraSwing = true
        attacker.checkCreatureAttack = { attacker.attacks++ }

        attacker.onCreatureMove(
            target,
            null,
            Position(106, 100, 7),
            null,
            Position(105, 100, 7),
            false,
        )

        assertThat(attacker.attacks).isEqualTo(1)
    }

    @Test
    fun `self move into a protection zone clears the attack target`() {
        // creature.cpp:545-547 -> onChangeZone: a protection zone drops the
        // attacked creature (creature.cpp:436-442).
        val map = GameMap()
        val normal = Position(100, 100, 7)
        val protectedPos = Position(101, 100, 7)
        map.withGroundTile(normal)
        map.withGroundTile(protectedPos).setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        val attacker = ProbeCreature(1, position = normal)
        val target = ProbeCreature(2, position = Position(105, 100, 7))
        map.withGroundTile(target.position)
        assertThat(map.placeCreature(normal, attacker)).isTrue()
        assertThat(map.placeCreature(target.position, target)).isTrue()
        assertThat(attacker.setAttackedCreature(target)).isTrue()

        map.moveCreature(attacker, map.getTile(protectedPos)!!)

        assertThat(attacker.zoneChanges).containsExactly(ZoneType.ZONE_PROTECTION)
        assertThat(attacker.getAttackedCreature()).isNull()
    }

    @Test
    fun `moved attack target entering a protection zone fires the change-zone hook`() {
        // creature.cpp:572-574 -> onAttackedCreatureChangeZone
        // (creature.cpp:444-452).
        val map = GameMap()
        val attackerPos = Position(100, 100, 7)
        val from = Position(104, 100, 7)
        val to = Position(105, 100, 7)
        map.withGroundTile(attackerPos)
        map.withGroundTile(from)
        map.withGroundTile(to).setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        val attacker = ProbeCreature(1, position = attackerPos)
        val target = ProbeCreature(2, position = from)
        assertThat(map.placeCreature(attackerPos, attacker)).isTrue()
        assertThat(map.placeCreature(from, target)).isTrue()
        assertThat(attacker.setAttackedCreature(target)).isTrue()

        map.moveCreature(target, map.getTile(to)!!)

        assertThat(attacker.attackedZoneChanges).containsExactly(ZoneType.ZONE_PROTECTION)
        assertThat(attacker.getAttackedCreature()).isNull()
    }

    // --- Monster::on* tails (monster.cpp:348-477) ---------------------------------

    @Test
    fun `monster appear remove and move delegate to the pipeline host seams`() {
        val monster = monster(7, Position(100, 100, 7))
        monster.now = { nowValue }
        val other = ProbeCreature(8, position = Position(101, 100, 7))
        val appears = mutableListOf<Pair<Creature, Boolean>>()
        val removes = mutableListOf<Pair<Creature, Boolean>>()
        val moves = mutableListOf<Triple<Creature, Position, Position>>()
        monster.onCreatureAppearHost = { creature, isLogin -> appears.add(creature to isLogin) }
        monster.onRemoveCreatureHost = { creature, isLogout -> removes.add(creature to isLogout) }
        monster.onCreatureMoveHost = { creature, oldPos, newPos -> moves.add(Triple(creature, oldPos, newPos)) }
        val tile = Tile(Position(100, 100, 7))

        monster.onCreatureAppear(other, false)
        monster.onRemoveCreature(other, true)
        monster.onCreatureMove(other, tile, Position(102, 100, 7), tile, Position(101, 100, 7), false)
        monster.onCreatureMove(monster, tile, Position(100, 100, 6), tile, Position(100, 100, 7), true)

        assertThat(appears).containsExactly(other to false)
        assertThat(removes).containsExactly(other to true)
        assertThat(moves).containsExactly(
            Triple(other, Position(101, 100, 7), Position(102, 100, 7)),
            Triple(monster, Position(100, 100, 7), Position(100, 100, 6)),
        )
        assertThat(monster.lastStep).isEqualTo(nowValue)
    }

    @Test
    fun `monster extra swing is projected from the behavior host`() {
        val monster = monster(7, Position(100, 100, 7))
        var projected = false
        monster.hasExtraSwingProvider = { projected }

        assertThat(monster.hasExtraSwing()).isFalse()
        projected = true
        assertThat(monster.hasExtraSwing()).isTrue()
    }

    // --- Player::onCreatureAppear (player.cpp:12220-12316) -------------------------

    @Test
    fun `player login appear refreshes both cyclopedia trackers`() {
        val player = player()
        val client = RecordingTrackerClient()
        player.cyclopediaMonsterTrackerClient = client

        player.onCreatureAppear(player, true)

        assertThat(client.trackerRefreshes).isEqualTo(2)
        assertThat(player.getLastPosition()).isEqualTo(player.position)

        player.onCreatureAppear(player, false)
        assertThat(client.trackerRefreshes).isEqualTo(2)
    }

    private class RecordingTrackerClient : CyclopediaMonsterTrackerClient {
        var trackerRefreshes = 0

        override fun parseSendBosstiary() {
            // No behaviour.
        }

        override fun sendBestiaryEntryChanged(raceId: Int) {
            // No behaviour.
        }

        override fun refreshCyclopediaMonsterTracker(tracker: Set<MonsterType>, isBoss: Boolean) {
            trackerRefreshes++
        }
    }

    // --- Player::onRemoveCreature (player.cpp:12318-12360) -------------------------

    @Test
    fun `player logout stores the login position last logout and clears invitations`() {
        val player = player(position = Position(120, 130, 7))
        player.now = { 5_000_000 }
        val party = Party(leader = player(position = Position(121, 130, 7)))
        player.addPartyInvitation(party)

        player.onRemoveCreature(player, isLogout = true)

        assertThat(player.getLoginPosition()).isEqualTo(Position(120, 130, 7))
        assertThat(player.getLastLogout()).isEqualTo(5_000)
        assertThat(player.invitePartyList).isEmpty()
    }

    @Test
    fun `player logout after death stores the temple position`() {
        val player = player(position = Position(120, 130, 7))
        player.setTown(Town(1).apply { setTemplePos(Position(50, 60, 7)) })
        player.health = 0

        player.onRemoveCreature(player, isLogout = true)

        assertThat(player.getLoginPosition()).isEqualTo(Position(50, 60, 7))
    }

    @Test
    fun `player remove of another creature keeps the logout state`() {
        val player = player(position = Position(120, 130, 7))
        player.now = { 5_000_000 }
        val other = player(id = 2, position = Position(121, 130, 7))

        player.onRemoveCreature(other, isLogout = true)

        assertThat(player.getLoginPosition()).isEqualTo(Position(0, 0, 0))
        assertThat(player.getLastLogout()).isZero()
    }

    // --- Player::onCreatureMove (player.cpp:12362-12425) ---------------------------

    @Test
    fun `player self move onto the same floor updates the paralyze walk exhaust`() {
        val player = player()
        player.addCondition(paralyzeCondition())
        player.walkDelayProvider = { 250 }

        player.onCreatureMove(
            player,
            null,
            Position(101, 100, 7),
            null,
            Position(100, 100, 7),
            false,
        )

        assertThat(player.walkExhausted()).isTrue()
    }

    @Test
    fun `player move update ignores the paralyze exhaust for another creature`() {
        val player = player()
        player.addCondition(paralyzeCondition())
        player.walkDelayProvider = { 250 }
        val other = monster(2, Position(101, 100, 7))

        player.onCreatureMove(other, null, Position(102, 100, 7), null, Position(101, 100, 7), false)

        assertThat(player.walkExhausted()).isFalse()
    }

    @Test
    fun `player teleport move leaves the market and applies the stairhop pacified condition`() {
        val player = player()
        player.setInMarket(true)
        player.stairhopDelay = 5_000

        player.onCreatureMove(
            player,
            null,
            Position(105, 100, 7),
            null,
            Position(100, 100, 7),
            true,
        )

        assertThat(player.isInMarket()).isFalse()
        assertThat(player.getCondition(ConditionType.CONDITION_PACIFIED)).isNotNull()
    }

    @Test
    fun `player moved follow target refreshes the follow path`() {
        val player = player()
        // The follow sight gate runs through `Player::canSee` (player.cpp:1402),
        // which is inert without an attached client.
        player.hasClient = { true }
        val follow = followTarget()
        assertThat(player.setFollowCreature(follow)).isTrue()
        var refreshes = 0
        player.creatureGoToFollowCreature = { refreshes++ }

        val oldPos = follow.position
        follow.position = Position(105, 100, 7)
        player.onCreatureMove(follow, null, follow.position, null, oldPos, false)

        // The base follow branch refreshes once (creature.cpp:551-555) and the
        // Player override refreshes again (player.cpp:12366-12369).
        assertThat(refreshes).isEqualTo(2)
        assertThat(player.isUpdatingPath).isFalse()
    }

    private fun followTarget(): Creature =
        ProbeCreature(2, position = Position(104, 100, 7))

    // --- PlayerActionTimers host composition (player.cpp:3084-3477, 8540) ----------

    @Test
    fun `player UI exhaustion uses the injected clock`() {
        val player = player()
        player.now = { 1_000 }

        assertThat(player.isUIExhausted(250)).isFalse()
        player.updateUIExhausted()
        assertThat(player.isUIExhausted(250)).isTrue()

        player.now = { 2_000 }
        assertThat(player.isUIExhausted(250)).isFalse()
    }

    @Test
    fun `player next action deadline delegates to PlayerActionTimers`() {
        val player = player()
        player.now = { 2_000 }

        player.setNextAction(3_000)
        assertThat(player.canDoAction()).isFalse()
        assertThat(player.getNextActionTime()).isEqualTo(1_000)

        player.now = { 3_500 }
        assertThat(player.canDoAction()).isTrue()
        assertThat(player.getNextActionTime()).isEqualTo(50)
    }

    @Test
    fun `player necklace ring potion and module gates delegate to PlayerActionTimers`() {
        val player = player()
        player.now = { 2_000 }

        player.setNextNecklaceAction(3_000)
        player.setNextRingAction(4_000)
        player.setNextPotionAction(5_000)
        player.setModuleDelay(7, 100)
        assertThat(player.canEquipNecklace()).isFalse()
        assertThat(player.canEquipRing()).isFalse()
        assertThat(player.canDoPotionAction()).isFalse()
        assertThat(player.canRunModule(7)).isFalse()
        assertThat(player.getNextPotionActionTime()).isEqualTo(3_000)

        player.now = { 5_000 }
        assertThat(player.canEquipNecklace()).isTrue()
        assertThat(player.canEquipRing()).isTrue()
        assertThat(player.canDoPotionAction()).isTrue()
        assertThat(player.canRunModule(7)).isTrue()
    }

    @Test
    fun `player ping timeout forces the logout through the composed timers`() {
        val player = player()
        player.now = { 1_000_000 }
        player.canLogout = { true }
        player.playerLogoutEvent = { true }
        var removed = 0
        player.removeCreatureOnTimeout = { removed++ }

        player.sendPing()
        player.now = { 1_060_000 }
        player.sendPing()

        assertThat(removed).isEqualTo(1)
    }

    @Test
    fun `player login protection delegates to PlayerActionTimers`() {
        val player = player()
        player.now = { 1_000 }

        player.setLoginProtection(500)
        assertThat(player.isLoginProtected()).isTrue()

        player.now = { 1_600 }
        assertThat(player.isLoginProtected()).isFalse()

        player.setLoginProtection(500)
        player.resetLoginProtection()
        assertThat(player.isLoginProtected()).isFalse()
    }

    // --- PlayerTownState host composition (player.cpp:8307-8326) -------------------

    @Test
    fun `player town state exposes the temple and login positions`() {
        val player = player()
        val town = Town(1).apply { setTemplePos(Position(50, 60, 7)) }

        player.setTown(town)

        assertThat(player.getTown()).isSameAs(town)
        assertThat(player.getTemplePosition()).isEqualTo(Position(50, 60, 7))
        assertThat(player.getLoginPosition()).isEqualTo(Position(0, 0, 0))
    }

    @Test
    fun `player no-loss death teleports to the composed town temple`() {
        val player = player()
        player.setSkillLoss(false)
        player.setTown(Town(1).apply { setTemplePos(Position(50, 60, 7)) })
        val teleports = mutableListOf<Position>()
        player.internalTeleport = { _, position, _ -> teleports.add(position) }

        player.death(null)

        assertThat(teleports).containsExactly(Position(50, 60, 7))
    }

    // --- Player::isInGhostMode / switchGhostMode (player.hpp:566-573) --------------

    @Test
    fun `player ghost mode follows switchGhostMode`() {
        val player = player()

        assertThat(player.isInGhostMode()).isFalse()
        player.switchGhostMode()
        assertThat(player.isInGhostMode()).isTrue()
        assertThat(player.ghostMode).isTrue()
        player.switchGhostMode()
        assertThat(player.isInGhostMode()).isFalse()
    }

    // --- drain/gain health sink consistency (creature.cpp:886-899) -----------------

    @Test
    fun `drainHealth suppresses the broadcast but still runs the death path`() {
        val probe = ProbeCreature(1, maxHealth = 100, health = 100)
        var broadcasts = 0
        probe.addCreatureHealth = { broadcasts++ }

        probe.drainHealth(100)

        assertThat(probe.health).isZero()
        assertThat(probe.isDead).isTrue()
        assertThat(probe.deaths).isEqualTo(1)
        assertThat(broadcasts).isZero()
    }

    @Test
    fun `addHealth broadcasts the clamped gain`() {
        val probe = ProbeCreature(1, maxHealth = 100, health = 50)
        var broadcasts = 0
        probe.addCreatureHealth = { broadcasts++ }

        probe.addHealth(30)
        assertThat(probe.health).isEqualTo(80)
        assertThat(broadcasts).isEqualTo(1)

        probe.addHealth(100)
        assertThat(probe.health).isEqualTo(100)
        assertThat(broadcasts).isEqualTo(2)

        probe.addHealth(10)
        assertThat(probe.health).isEqualTo(100)
        assertThat(broadcasts).isEqualTo(2)
    }
}
