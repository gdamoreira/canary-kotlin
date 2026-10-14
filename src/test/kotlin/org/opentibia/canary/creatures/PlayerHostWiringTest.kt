package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.players.CountBlock
import org.opentibia.canary.creatures.players.PlayerTask
import org.opentibia.canary.creatures.players.WalkStartPolicy
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.map.Position
import java.nio.file.Files
import java.nio.file.Path

/**
 * Host wiring tests for the wave-44 state classes:
 *
 * - `Player::death` (src/creatures/players/player.cpp:4035-4284) through the
 *   host [Player] override, over the exposed `manaSpent`, `magLevelPercent`,
 *   `levelPercent`, `lastLogout`, `damageMap` and `skillLoss` state.
 * - `Player::onWalk` (player.cpp:2942-2961), `Player::goToFollowCreature`
 *   (player.cpp:6161-6171) and `Player::canWalkthrough` (player.cpp:1424-1489)
 *   through [PlayerWalkState].
 * - `Creature::startAutoWalk` + `Creature::getNextStep`
 *   (creature.cpp:315-340) over the `listWalkDir` queue.
 * - `Player::setNextWalkTask`/`setNextWalkActionTask`/`setNextActionTask`/
 *   `setNextActionPushTask`/`setNextPotionActionTask` (player.cpp:2983-3070)
 *   over the injected dispatcher seams.
 */
class PlayerHostWiringTest {

    private var nowValue = 1_000_000L

    private val vocations: Vocations = Vocations().apply {
        assertThat(loadFromXml(realXml())).isTrue()
    }

    private data class Scheduled(
        val eventId: Long,
        val lane: DispatcherLane,
        val delayMs: Long,
        val context: String,
        val action: () -> Unit,
    )

    private fun newPlayer(
        id: Long = 1,
        level: Int = 30,
        experience: Long = 400_000,
        magLevel: Int = 3,
        health: Int = 120,
    ): Player = Player(
        id = id,
        name = "Hero$id",
        maxHealth = 500,
        health = health,
        maxMana = 300,
        mana = 10,
        // Hundredths, like `JdbcPlayerRepository` persists `capacity / 100`.
        capacity = 40_000.0,
        position = Position(100, 100, 7),
    ).apply {
        this.level = level
        this.experience = experience
        this.magLevel = magLevel
        this.vocationId = 4
        vocations = this@PlayerHostWiringTest.vocations
        now = { nowValue }
    }

    // --- Player::death (player.cpp:4035-4284) --------------------------------------

    @Test
    fun `host death applies the level, magic level and skill penalties`() {
        val player = newPlayer()
        player.manaSpent = 1_000
        player.addSkillTries(skills_t.SKILL_SWORD, 30)

        player.death(null)

        assertThat(player.experience).isEqualTo(369_679)
        assertThat(player.level).isEqualTo(30)
        assertThat(player.magLevel).isEqualTo(2)
        assertThat(player.manaSpent).isEqualTo(13_748)
        assertThat(player.getSpentMana()).isEqualTo(13_748)
        assertThat(player.getMagicLevelPercent()).isEqualTo(95.47)
        assertThat(player.getLevelPercent()).isEqualTo(3)
        assertThat(player.getSkillLevel(skills_t.SKILL_SWORD)).isEqualTo(10)
        assertThat(player.getSkillTries(skills_t.SKILL_SWORD)).isEqualTo(28)
        assertThat(player.getLastLogout()).isGreaterThan(0L)
        assertThat(player.health).isEqualTo(player.maxHealth)
        assertThat(player.mana).isEqualTo(player.maxMana)
    }

    @Test
    fun `host changeHealth routes the killing blow through the death override`() {
        val player = newPlayer(health = 10)

        player.changeHealth(-50)

        assertThat(player.experience).isEqualTo(369_679)
        assertThat(player.health).isEqualTo(player.maxHealth)
    }

    @Test
    fun `host death reads the damage map and the player id provider`() {
        val player = newPlayer()
        player.manaSpent = 0
        val dealerA = newPlayer(id = 2, level = 100, experience = 0)
        val dealerB = newPlayer(id = 3, level = 100, experience = 0)
        player.playerByIdProvider = { id -> mapOf(2L to dealerA, 3L to dealerB)[id] }
        player.addDamagePoints(dealerA, 100)
        player.addDamagePoints(dealerB, 100)
        var reduction = 0
        player.sendReLoginWindow = { reduction = it }

        player.death(dealerA)

        assertThat(reduction).isEqualTo(20)
        assertThat(player.experience).isEqualTo(393_935)
    }

    @Test
    fun `host death without skill loss teleports to the temple`() {
        val player = newPlayer(health = 10)
        player.setSkillLoss(false)
        player.templePositionProvider = { Position(50, 60, 7) }
        val teleports = mutableListOf<Triple<Player, Position, Boolean>>()
        player.internalTeleport = { p, pos, effect -> teleports.add(Triple(p, pos, effect)) }

        player.death(null)

        assertThat(player.experience).isEqualTo(400_000)
        assertThat(player.health).isEqualTo(player.maxHealth)
        assertThat(teleports).containsExactly(Triple(player, Position(50, 60, 7), true))
    }

    @Test
    fun `addDamagePoints accumulates, stamps and idles out`() {
        val player = newPlayer()
        val attacker = newPlayer(id = 9)
        player.now = { 1_234 }

        player.addDamagePoints(attacker, 10)
        player.addDamagePoints(attacker, 5)

        assertThat(player.getDamageMap()[9L]).isEqualTo(CountBlock(total = 15, ticks = 1_234))
        assertThat(player.lastHitCreatureId).isEqualTo(9L)

        player.onIdleStatus()

        assertThat(player.getDamageMap()).isEmpty()
    }

    // --- Creature::startAutoWalk / getNextStep (creature.cpp:315-340) --------------

    @Test
    fun `host startAutoWalk queues steps and getNextStep consumes them through onWalk`() {
        val player = newPlayer()
        val walked = mutableListOf<Direction>()
        val events = mutableListOf<WalkStartPolicy>()
        player.playerOnWalkCallback = { walked.add(it) }
        player.addEventWalk = { events.add(it) }

        player.startAutoWalk(listOf(Direction.East, Direction.North), startPolicy = WalkStartPolicy.ImmediateWhenReady)

        assertThat(player.listWalkDir).containsExactly(Direction.East, Direction.North)
        assertThat(events).containsExactly(WalkStartPolicy.ImmediateWhenReady)

        assertThat(player.getNextStep()).isEqualTo(Direction.North)
        assertThat(player.getNextStep()).isEqualTo(Direction.East)
        assertThat(player.getNextStep()).isNull()
        assertThat(walked).containsExactly(Direction.North, Direction.East)
        assertThat(player.listWalkDir).isEmpty()
    }

    @Test
    fun `host startAutoWalk refuses a rooted player`() {
        val player = newPlayer()
        player.addCondition(
            Condition.createCondition(
                ConditionId.CONDITIONID_DEFAULT,
                ConditionType.CONDITION_ROOTED,
                60_000,
                now = { nowValue },
            ),
        )

        player.startAutoWalk(listOf(Direction.East))

        assertThat(player.listWalkDir).isEmpty()
    }

    // --- Player::onWalk / goToFollowCreature / canWalkthrough ----------------------

    @Test
    fun `host onWalk clears the pending action task`() {
        val player = newPlayer()
        val stopped = mutableListOf<Long>()
        player.scheduleEvent = { _, _, _, _ -> 42L }
        player.stopEvent = { stopped.add(it) }

        player.setNextActionTask(PlayerTask(0, "action", {}))
        player.onWalk(Direction.West)

        assertThat(stopped).containsExactly(42L)
    }

    @Test
    fun `host goToFollowCreature delegates to the walk state`() {
        val player = newPlayer()
        var follows = 0
        player.creatureGoToFollowCreature = { follows++ }

        player.goToFollowCreature()

        assertThat(follows).isEqualTo(1)
    }

    @Test
    fun `host canWalkthrough allows access players`() {
        val walker = newPlayer()
        walker.group = Group(access = true)
        val subject = newPlayer(id = 2)

        assertThat(walker.canWalkthrough(subject)).isTrue()
    }

    // --- scheduler variants (player.cpp:2983-3070) ---------------------------------

    @Test
    fun `host scheduler variants reserve the PlayerWalk and PlayerAction lanes`() {
        val player = newPlayer()
        val scheduled = mutableListOf<Scheduled>()
        val stopped = mutableListOf<Long>()
        player.scheduleEvent = { lane, delay, context, action ->
            val eventId = scheduled.size + 1L
            scheduled.add(Scheduled(eventId, lane, delay, context, action))
            eventId
        }
        player.stopEvent = { stopped.add(it) }

        player.setNextWalkTask(PlayerTask(250, "Player::setNextWalkTask", {}))
        player.setNextActionPushTask(PlayerTask(50, "push", {}))
        player.setNextPotionActionTask(PlayerTask(75, "potion", {}))
        player.setNextActionTask(PlayerTask(100, "action", {}))
        player.setNextWalkActionTask(PlayerTask(150, "walkAction", {}))
        player.onWalkComplete()

        assertThat(scheduled.map { it.lane }).containsExactly(
            DispatcherLane.PlayerWalk,
            DispatcherLane.PlayerAction,
            DispatcherLane.PlayerAction,
            DispatcherLane.PlayerAction,
            DispatcherLane.PlayerAction,
        )
        assertThat(scheduled.map { it.context }).containsExactly(
            "Player::setNextWalkTask",
            "push",
            "potion",
            "action",
            "walkAction",
        )
        assertThat(scheduled.map { it.delayMs }).containsExactly(250L, 50L, 75L, 100L, 150L)
        assertThat(stopped).isEmpty()
    }

    @Test
    fun `host onWalkAborted cancels the walk and drops the stored action`() {
        val player = newPlayer()
        val scheduled = mutableListOf<Scheduled>()
        player.scheduleEvent = { lane, delay, context, action ->
            val eventId = scheduled.size + 1L
            scheduled.add(Scheduled(eventId, lane, delay, context, action))
            eventId
        }
        var cancels = 0
        player.sendCancelWalk = { cancels++ }
        player.setNextWalkActionTask(PlayerTask(0, "walkAction", {}))

        player.onWalkAborted()
        player.onWalkComplete()

        assertThat(cancels).isEqualTo(1)
        assertThat(scheduled).isEmpty()
    }

    @Test
    fun `host setNextWalkTask cancels the walk when scheduling is rejected`() {
        val player = newPlayer()
        var cancels = 0
        player.sendCancelWalk = { cancels++ }

        player.setNextWalkTask(PlayerTask(0, "walk", {}))

        assertThat(cancels).isEqualTo(1)
    }

    private fun realXml(): String {
        val candidates = listOf(
            Path.of("data/XML/vocations.xml"),
            Path.of("../data/XML/vocations.xml"),
            Path.of("../../data/XML/vocations.xml"),
        )
        val path = candidates.firstOrNull { Files.exists(it) }
            ?: error("data/XML/vocations.xml not found from ${System.getProperty("user.dir")}")
        return Files.readString(path)
    }
}
