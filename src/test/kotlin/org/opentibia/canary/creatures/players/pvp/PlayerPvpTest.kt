package org.opentibia.canary.creatures.players.pvp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.WorldType

/**
 * Transcript tests for the Canary PvP components.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/players/components/pvp/player_pvp.hpp:14-27, player_pvp.cpp:14-38
 *  - src/creatures/players/player.cpp:4442-4483 (addPzLockTicks)
 *  - src/creatures/players/player.cpp:6226-6232 (setPvpMode/getPvpMode)
 *  - src/creatures/players/player.cpp:6981-6984 (hasKilled)
 *  - src/creatures/players/player.cpp:7127-7151 (getSkullClient)
 *  - src/creatures/players/player.cpp:7153-7205 (skull ticks)
 *  - src/creatures/players/player.cpp:7207-7261 (attacked/attacker ledgers)
 *  - src/creatures/players/player.cpp:7263-7301 (addUnjustifiedDead)
 *  - src/creatures/players/player.cpp:7318-7329 (checkSkullTicks)
 *  - src/creatures/players/player.cpp:7873-7916 (sendUnjustifiedPoints/sendOpenPvpSituations)
 */
class PlayerPvpTest {

    private class UnjustifiedFrame(
        val dayProgress: Int,
        val dayLeft: Int,
        val weekProgress: Int,
        val weekLeft: Int,
        val monthProgress: Int,
        val monthLeft: Int,
        val skullDuration: Int,
    )

    private class RecordingPvpClient : PvpClient {
        val unjustifiedFrames = mutableListOf<UnjustifiedFrame>()
        val openPvpSituations = mutableListOf<Int>()
        val creatureSkulls = mutableListOf<Pair<Creature, Skulls_t>>()
        val creatureMarks = mutableListOf<Pair<Creature, CreatureMark_t>>()

        override fun sendUnjustifiedPoints(
            dayProgress: Int,
            dayLeft: Int,
            weekProgress: Int,
            weekLeft: Int,
            monthProgress: Int,
            monthLeft: Int,
            skullDuration: Int,
        ) {
            unjustifiedFrames.add(
                UnjustifiedFrame(dayProgress, dayLeft, weekProgress, weekLeft, monthProgress, monthLeft, skullDuration),
            )
        }

        override fun sendOpenPvpSituations(openPvpSituations: Int) {
            this.openPvpSituations.add(openPvpSituations)
        }

        override fun sendCreatureSkull(creature: Creature, skull: Skulls_t) {
            creatureSkulls.add(creature to skull)
        }

        override fun sendCreatureMark(creature: Creature, mark: CreatureMark_t) {
            creatureMarks.add(creature to mark)
        }
    }

    private class World(
        val client: RecordingPvpClient? = RecordingPvpClient(),
        val worldType: WorldType = WorldType.PVP,
        val configuredWorldType: String = ExpertPvp.RETRO_PVP_WORLD_TYPE,
        val config: PvpConfig = PvpConfig(),
        var now: Long = 1_700_000_000L,
        val messages: MutableList<String> = mutableListOf(),
        val icons: MutableList<String> = mutableListOf(),
        val scheduleCalls: MutableList<Long> = mutableListOf(),
        val stopCalls: MutableList<Long> = mutableListOf(),
    ) {
        private val components = mutableMapOf<Long, PlayerPvp>()
        private val scheduledTasks = mutableMapOf<Long, () -> Unit>()
        private var nextEventId = 1L

        var visiblePlayers: (PlayerPvp) -> List<PlayerPvp> = { emptyList() }
        var allPlayers: () -> Collection<PlayerPvp> = { components.values.toList() }

        fun player(id: Long, name: String = "Player$id"): Player =
            Player(id = id, name = name, maxHealth = 150, health = 150, position = Position(100, 100, 7))

        fun component(guid: Long): PlayerPvp? = components[guid]

        fun pvp(player: Player): PlayerPvp {
            val component = PlayerPvp(
                player = player,
                playerByGuid = { guid -> components[guid] },
                configuredWorldType = { configuredWorldType },
                worldType = { this.worldType },
                config = config,
                client = client,
                timeNow = { now },
                otsysTime = { now * 1000 },
                sendTextMessage = { messages.add(it) },
                sendIcons = { icons.add("icons") },
                visiblePlayers = { centre -> visiblePlayers(centre) },
                allPlayers = { allPlayers() },
                scheduleEvent = { duration, task ->
                    scheduleCalls.add(duration)
                    val id = nextEventId++
                    scheduledTasks[id] = task
                    id
                },
                stopEvent = { id -> stopCalls.add(id) },
            )
            components[player.id] = component
            return component
        }

        fun runScheduledTask(eventId: Long) {
            scheduledTasks.remove(eventId)?.invoke()
        }
    }

    private fun link(world: World, firstId: Long, secondId: Long): Pair<PlayerPvp, PlayerPvp> {
        val first = world.pvp(world.player(firstId))
        val second = world.pvp(world.player(secondId))
        return first to second
    }

    // --- mode component (player_pvp.cpp) --------------------------------------------

    @Test
    fun `the pvp mode defaults to dove`() {
        val world = World()
        val component = world.pvp(world.player(1))

        assertThat(component.getMode()).isEqualTo(PvpMode_t.PVP_MODE_DOVE)
    }

    @Test
    fun `setMode stores the normalized mode and returns the result`() {
        val world = World()
        val component = world.pvp(world.player(1))

        val result = component.setMode(PvpMode_t.PVP_MODE_RED_FIST, ExpertPvpModeSource.ClientByte)

        assertThat(result.mode).isEqualTo(PvpMode_t.PVP_MODE_RED_FIST)
        assertThat(result.accepted).isTrue()
        assertThat(component.getMode()).isEqualTo(PvpMode_t.PVP_MODE_RED_FIST)
    }

    @Test
    fun `setMode normalizes an invalid mode to dove and stores it`() {
        val world = World()
        val component = world.pvp(world.player(1))
        component.setMode(PvpMode_t.PVP_MODE_YELLOW_HAND, ExpertPvpModeSource.StoredPlayerState)

        val result = component.setMode(PvpMode_t(99), ExpertPvpModeSource.ClientByte)

        assertThat(result.accepted).isFalse()
        assertThat(result.normalized).isTrue()
        assertThat(result.reason).isEqualTo(ExpertPvpDecisionReason.InvalidMode)
        assertThat(component.getMode()).isEqualTo(PvpMode_t.PVP_MODE_DOVE)
    }

    @Test
    fun `normalizeMode is const and does not change the stored mode`() {
        val world = World()
        val component = world.pvp(world.player(1))
        component.setMode(PvpMode_t.PVP_MODE_WHITE_HAND, ExpertPvpModeSource.StoredPlayerState)

        val result = component.normalizeMode(PvpMode_t.PVP_MODE_RED_FIST, ExpertPvpModeSource.ClientByte)

        assertThat(result.mode).isEqualTo(PvpMode_t.PVP_MODE_RED_FIST)
        assertThat(component.getMode()).isEqualTo(PvpMode_t.PVP_MODE_WHITE_HAND)
    }

    @Test
    fun `isExpertPvpEnabled follows the configured world type`() {
        val expert = World(configuredWorldType = "expert-pvp")
        val retro = World(configuredWorldType = "retro-pvp")

        assertThat(expert.pvp(expert.player(1)).isExpertPvpEnabled()).isTrue()
        assertThat(retro.pvp(retro.player(1)).isExpertPvpEnabled()).isFalse()
    }

    @Test
    fun `defaultModeForClient and modeFromClientByte delegate to ExpertPvp`() {
        val world = World()
        val component = world.pvp(world.player(1))

        assertThat(component.defaultModeForClient().mode).isEqualTo(PvpMode_t.PVP_MODE_DOVE)
        assertThat(component.modeFromClientByte(3).mode).isEqualTo(PvpMode_t.PVP_MODE_RED_FIST)
        assertThat(component.modeFromClientByte(99).accepted).isFalse()
    }

    // --- pvp situations (attacked/attacker ledgers) ---------------------------------

    @Test
    fun `addAttacked links both players ledgers`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)

        alice.addAttacked(bob)

        assertThat(alice.hasAttacked(bob)).isTrue()
        assertThat(alice.getAttackedPlayerGuids()).containsExactly(2L)
        assertThat(bob.getAttackerPlayerGuids()).containsExactly(1L)
        assertThat(bob.hasAttacked(alice)).isFalse()
    }

    @Test
    fun `addAttacked is idempotent`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)

        alice.addAttacked(bob)
        alice.addAttacked(bob)

        assertThat(alice.getAttackedPlayerGuids()).containsExactly(2L)
        assertThat(bob.getAttackerPlayerGuids()).containsExactly(1L)
    }

    @Test
    fun `addAttacked ignores self and NotGainInFight players`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        alice.player.setFlag(PlayerFlags_t.NotGainInFight)

        alice.addAttacked(bob)
        alice.addAttacked(alice)

        assertThat(alice.getAttackedPlayerGuids()).isEmpty()
        assertThat(bob.getAttackerPlayerGuids()).isEmpty()
    }

    @Test
    fun `hasAttacked reports false under NotGainInFight`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        alice.addAttacked(bob)

        alice.player.setFlag(PlayerFlags_t.NotGainInFight)

        assertThat(alice.hasAttacked(bob)).isFalse()
    }

    @Test
    fun `removeAttacked unlinks both ledgers`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        alice.addAttacked(bob)

        alice.removeAttacked(bob)

        assertThat(alice.hasAttacked(bob)).isFalse()
        assertThat(alice.getAttackedPlayerGuids()).isEmpty()
        assertThat(bob.getAttackerPlayerGuids()).isEmpty()
    }

    @Test
    fun `clearAttacked removes every attacker backlink`() {
        val world = World()
        val alice = world.pvp(world.player(1))
        val bob = world.pvp(world.player(2))
        val carol = world.pvp(world.player(3))
        alice.addAttacked(bob)
        alice.addAttacked(carol)

        alice.clearAttacked()

        assertThat(alice.getAttackedPlayerGuids()).isEmpty()
        assertThat(bob.getAttackerPlayerGuids()).isEmpty()
        assertThat(carol.getAttackerPlayerGuids()).isEmpty()
    }

    @Test
    fun `sendOpenPvpSituations reports the attacked set size`() {
        val world = World()
        val alice = world.pvp(world.player(1))
        val bob = world.pvp(world.player(2))
        val carol = world.pvp(world.player(3))
        alice.addAttacked(bob)
        alice.addAttacked(carol)

        alice.sendOpenPvpSituations()

        assertThat(world.client!!.openPvpSituations).containsExactly(2)
    }

    @Test
    fun `sendOpenPvpSituations is a no-op without a protocol client`() {
        val world = World(client = null)
        val alice = world.pvp(world.player(1))
        val bob = world.pvp(world.player(2))
        alice.addAttacked(bob)

        alice.sendOpenPvpSituations()

        assertThat(world.client).isNull()
    }

    // --- unjustified points ---------------------------------------------------------

    @Test
    fun `addUnjustifiedDead records the kill and warns the killer`() {
        val world = World(config = PvpConfig(dayKillsToRed = 3, weekKillsToRed = 5, monthKillsToRed = 10))
        world.now = 1_700_000_000L
        val (alice, bob) = link(world, 1, 2)

        alice.addUnjustifiedDead(bob)

        assertThat(world.messages).containsExactly("Warning! The murder of Player2 was not justified.")
        assertThat(alice.getUnjustifiedKills()).containsExactly(Kill(target = 2L, time = 1_700_000_000L, unavenged = true))
        assertThat(alice.hasKilled(bob)).isTrue()
        assertThat(alice.player.getSkull()).isEqualTo(Skulls_t.SKULL_NONE)
    }

    @Test
    fun `addUnjustifiedDead is skipped for NotGainInFight players and in pvp-enforced worlds`() {
        val noGain = World()
        val (alice, bob) = link(noGain, 1, 2)
        alice.player.setFlag(PlayerFlags_t.NotGainInFight)
        alice.addUnjustifiedDead(bob)

        val enforced = World(worldType = WorldType.PVP_ENFORCED)
        val (carol, dave) = link(enforced, 3, 4)
        carol.addUnjustifiedDead(dave)

        assertThat(alice.getUnjustifiedKills()).isEmpty()
        assertThat(noGain.messages).isEmpty()
        assertThat(carol.getUnjustifiedKills()).isEmpty()
        assertThat(enforced.messages).isEmpty()
    }

    @Test
    fun `hasKilled matches only recent unavenged kills against the target`() {
        val world = World(config = PvpConfig(orangeSkullDuration = 7))
        val (alice, bob) = link(world, 1, 2)
        val carol = world.pvp(world.player(3))
        alice.addUnjustifiedDead(bob)

        assertThat(alice.hasKilled(bob)).isTrue()
        assertThat(alice.hasKilled(carol)).isFalse()

        world.now += 7L * 24 * 60 * 60 - 1
        assertThat(alice.hasKilled(bob)).isTrue()

        world.now += 1
        assertThat(alice.hasKilled(bob)).isFalse()
    }

    @Test
    fun `an avenged kill no longer counts`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        alice.addUnjustifiedDead(bob)

        alice.getUnjustifiedKills().first().unavenged = false

        assertThat(alice.hasKilled(bob)).isFalse()
    }

    @Test
    fun `hitting the day threshold promotes the killer to a red skull`() {
        val world = World(config = PvpConfig(dayKillsToRed = 1, redSkullDuration = 30))
        world.now = 1_700_000_000L
        val (alice, bob) = link(world, 1, 2)

        alice.addUnjustifiedDead(bob)

        assertThat(alice.player.getSkull()).isEqualTo(Skulls_t.SKULL_RED)
        assertThat(alice.getSkullTicks()).isEqualTo(30L * 24 * 60 * 60)
    }

    @Test
    fun `hitting twice the day threshold promotes the killer to a black skull`() {
        val world = World(config = PvpConfig(dayKillsToRed = 1, redSkullDuration = 30, blackSkullDuration = 45))
        world.now = 1_700_000_000L
        val (alice, bob) = link(world, 1, 2)
        val carol = world.pvp(world.player(3))

        alice.addUnjustifiedDead(bob)
        world.now += 1
        alice.addUnjustifiedDead(carol)

        assertThat(alice.player.getSkull()).isEqualTo(Skulls_t.SKULL_BLACK)
        assertThat(alice.getSkullTicks()).isEqualTo(45L * 24 * 60 * 60)
    }

    @Test
    fun `an existing black skull is not downgraded by a later kill`() {
        val world = World(config = PvpConfig(dayKillsToRed = 1, blackSkullDuration = 45))
        world.now = 1_700_000_000L
        val (alice, bob) = link(world, 1, 2)
        val carol = world.pvp(world.player(3))
        val dave = world.pvp(world.player(4))
        alice.addUnjustifiedDead(bob)
        world.now += 1
        alice.addUnjustifiedDead(carol)
        val blackTicks = alice.getSkullTicks()

        world.now += 1
        alice.addUnjustifiedDead(dave)

        assertThat(alice.player.getSkull()).isEqualTo(Skulls_t.SKULL_BLACK)
        assertThat(alice.getSkullTicks()).isEqualTo(blackTicks)
    }

    @Test
    fun `sendUnjustifiedPoints reports the capped progress and remaining counts`() {
        val world = World(config = PvpConfig(dayKillsToRed = 3, weekKillsToRed = 5, monthKillsToRed = 10))
        world.now = 1_700_000_000L
        val (alice, bob) = link(world, 1, 2)
        alice.addUnjustifiedDead(bob)
        val client = requireNotNull(world.client)
        client.unjustifiedFrames.clear()

        alice.sendUnjustifiedPoints()

        val frame = client.unjustifiedFrames.single()
        assertThat(frame.dayProgress).isEqualTo(33)
        assertThat(frame.dayLeft).isEqualTo(2)
        assertThat(frame.weekProgress).isEqualTo(20)
        assertThat(frame.weekLeft).isEqualTo(4)
        assertThat(frame.monthProgress).isEqualTo(10)
        assertThat(frame.monthLeft).isEqualTo(9)
        assertThat(frame.skullDuration).isEqualTo(0)
    }

    @Test
    fun `sendUnjustifiedPoints doubles the caps and reports the skull days for red skulls`() {
        val world = World(config = PvpConfig(dayKillsToRed = 3, weekKillsToRed = 5, monthKillsToRed = 10, redSkullDuration = 30))
        world.now = 1_700_000_000L
        val (alice, bob) = link(world, 1, 2)
        alice.addUnjustifiedDead(bob)
        alice.player.setSkull(Skulls_t.SKULL_RED)
        alice.setSkullTicks(30L * 24 * 60 * 60)
        val client = requireNotNull(world.client)
        client.unjustifiedFrames.clear()

        alice.sendUnjustifiedPoints()

        val frame = client.unjustifiedFrames.single()
        assertThat(frame.dayProgress).isEqualTo(17)
        assertThat(frame.dayLeft).isEqualTo(5)
        assertThat(frame.weekProgress).isEqualTo(10)
        assertThat(frame.weekLeft).isEqualTo(9)
        assertThat(frame.monthProgress).isEqualTo(5)
        assertThat(frame.monthLeft).isEqualTo(19)
        assertThat(frame.skullDuration).isEqualTo(30)
    }

    @Test
    fun `sendUnjustifiedPoints is a no-op without a protocol client`() {
        val world = World(client = null)
        val (alice, bob) = link(world, 1, 2)
        alice.addUnjustifiedDead(bob)

        alice.sendUnjustifiedPoints()

        assertThat(world.client).isNull()
    }

    // --- skull ticks -----------------------------------------------------------------

    @Test
    fun `computeSkullTimeFromLastKill derives red skull time from the last kill`() {
        val world = World(config = PvpConfig(redSkullDuration = 30))
        world.now = 1_700_000_000L
        val (alice, bob) = link(world, 1, 2)
        alice.addUnjustifiedDead(bob)
        alice.player.setSkull(Skulls_t.SKULL_RED)

        val info = alice.computeSkullTimeFromLastKill()

        assertThat(info.remainingSeconds).isEqualTo(30L * 24 * 60 * 60)
        assertThat(info.remainingDays).isEqualTo(30)
    }

    @Test
    fun `computeSkullTimeFromLastKill uses the cached last kill time`() {
        val world = World(config = PvpConfig(redSkullDuration = 30))
        world.now = 1_700_000_000L
        val (alice, bob) = link(world, 1, 2)
        alice.addUnjustifiedDead(bob)
        alice.player.setSkull(Skulls_t.SKULL_RED)
        alice.setSkullTicks(5L * 24 * 60 * 60)

        val info = alice.computeSkullTimeFromLastKill()

        assertThat(info.remainingSeconds).isEqualTo(5L * 24 * 60 * 60)
        assertThat(info.remainingDays).isEqualTo(5)
    }

    @Test
    fun `computeSkullTimeFromLastKill returns the stored ticks when not red or black`() {
        val world = World()
        val component = world.pvp(world.player(1))
        component.setSkullTicks(500)

        val info = component.computeSkullTimeFromLastKill()

        assertThat(info.remainingSeconds).isEqualTo(500)
        assertThat(info.remainingDays).isEqualTo(1)
    }

    @Test
    fun `computeSkullTimeFromLastKill clamps an expired red skull to zero`() {
        val world = World(config = PvpConfig(redSkullDuration = 30))
        world.now = 1_700_000_000L
        val (alice, bob) = link(world, 1, 2)
        alice.addUnjustifiedDead(bob)
        alice.player.setSkull(Skulls_t.SKULL_RED)

        world.now += 31L * 24 * 60 * 60
        val info = alice.computeSkullTimeFromLastKill()

        assertThat(info.remainingSeconds).isZero()
        assertThat(info.remainingDays).isZero()
    }

    @Test
    fun `refreshSkullTicksFromLastKill seeds the ticks`() {
        val world = World(config = PvpConfig(redSkullDuration = 30))
        world.now = 1_700_000_000L
        val (alice, bob) = link(world, 1, 2)
        alice.addUnjustifiedDead(bob)
        alice.player.setSkull(Skulls_t.SKULL_RED)

        alice.refreshSkullTicksFromLastKill()

        assertThat(alice.getSkullTicks()).isEqualTo(30L * 24 * 60 * 60)
    }

    @Test
    fun `checkSkullTicks decrements and clears the red skull at zero`() {
        val world = World()
        val component = world.pvp(world.player(1))
        component.player.setSkull(Skulls_t.SKULL_RED)
        component.setSkullTicks(100)

        component.checkSkullTicks(60)
        assertThat(component.getSkullTicks()).isEqualTo(40)
        assertThat(component.player.getSkull()).isEqualTo(Skulls_t.SKULL_RED)

        component.checkSkullTicks(100)
        assertThat(component.getSkullTicks()).isZero()
        assertThat(component.player.getSkull()).isEqualTo(Skulls_t.SKULL_NONE)
    }

    @Test
    fun `checkSkullTicks keeps the skull while in fight`() {
        val world = World()
        val component = world.pvp(world.player(1))
        component.player.setSkull(Skulls_t.SKULL_BLACK)
        component.setSkullTicks(10)
        component.player.addCondition(
            Condition.createCondition(ConditionId.CONDITIONID_DEFAULT, ConditionType.CONDITION_INFIGHT, 60_000, 0),
        )

        component.checkSkullTicks(10)

        assertThat(component.getSkullTicks()).isZero()
        assertThat(component.player.getSkull()).isEqualTo(Skulls_t.SKULL_BLACK)
    }

    // --- pz lock ---------------------------------------------------------------------

    @Test
    fun `addPzLockTicks opens the lock and schedules its expiry`() {
        val world = World(config = PvpConfig(pzLocked = 60_000))
        val component = world.pvp(world.player(1))

        component.addPzLockTicks()

        assertThat(component.isPzLocked()).isTrue()
        assertThat(world.scheduleCalls).containsExactly(60_000L)
        assertThat(world.icons).containsExactly("icons")
    }

    @Test
    fun `addPzLockTicks does not reschedule inside the same window`() {
        val world = World(config = PvpConfig(pzLocked = 60_000))
        val component = world.pvp(world.player(1))

        component.addPzLockTicks()
        component.addPzLockTicks()

        assertThat(world.scheduleCalls).hasSize(1)
    }

    @Test
    fun `addPzLockTicks is skipped under NotGainInFight`() {
        val world = World()
        val component = world.pvp(world.player(1))
        component.player.setFlag(PlayerFlags_t.NotGainInFight)

        component.addPzLockTicks()

        assertThat(component.isPzLocked()).isFalse()
        assertThat(world.scheduleCalls).isEmpty()
    }

    @Test
    fun `the scheduled pz lock expiry clears the lock and a non red skull`() {
        val world = World(config = PvpConfig(pzLocked = 60_000))
        val component = world.pvp(world.player(1))
        component.player.setSkull(Skulls_t.SKULL_WHITE)
        component.addPzLockTicks()

        world.now += 60
        world.runScheduledTask(eventId = 1)

        assertThat(component.isPzLocked()).isFalse()
        assertThat(component.player.getSkull()).isEqualTo(Skulls_t.SKULL_NONE)
        assertThat(world.icons).hasSize(2)
    }

    @Test
    fun `the scheduled pz lock expiry keeps a red skull`() {
        val world = World(config = PvpConfig(pzLocked = 60_000))
        val component = world.pvp(world.player(1))
        component.player.setSkull(Skulls_t.SKULL_RED)
        component.addPzLockTicks()

        world.now += 60
        world.runScheduledTask(eventId = 1)

        assertThat(component.player.getSkull()).isEqualTo(Skulls_t.SKULL_RED)
    }

    // --- client skull transitions ---------------------------------------------------

    @Test
    fun `getSkullClient returns orange for the viewer's own recent unavenged kills`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        alice.addUnjustifiedDead(bob)

        assertThat(alice.getSkullClient(alice.player)).isEqualTo(Skulls_t.SKULL_ORANGE)
    }

    @Test
    fun `getSkullClient returns orange when the creature player killed the viewer`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        bob.addUnjustifiedDead(alice)

        assertThat(alice.getSkullClient(bob.player)).isEqualTo(Skulls_t.SKULL_ORANGE)
    }

    @Test
    fun `getSkullClient returns yellow when the creature player attacked the viewer`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        bob.addAttacked(alice)

        assertThat(alice.getSkullClient(bob.player)).isEqualTo(Skulls_t.SKULL_YELLOW)
    }

    @Test
    fun `getSkullClient returns green for a party member`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        val party = Party(leader = alice.player)
        bob.player.setParty(party)

        assertThat(alice.getSkullClient(bob.player)).isEqualTo(Skulls_t.SKULL_GREEN)
    }

    @Test
    fun `getSkullClient falls back to the creature skull`() {
        val world = World()
        val alice = world.pvp(world.player(1))
        val bob = world.pvp(world.player(2))
        bob.player.setSkull(Skulls_t.SKULL_RED)

        assertThat(alice.getSkullClient(bob.player)).isEqualTo(Skulls_t.SKULL_RED)

        val monster = Monster(id = 9, name = "Rat", maxHealth = 20, health = 20, position = Position(100, 100, 7))
        monster.setSkull(Skulls_t.SKULL_ORANGE)
        assertThat(alice.getSkullClient(monster)).isEqualTo(Skulls_t.SKULL_ORANGE)
    }

    @Test
    fun `getSkullClient returns none outside a pvp world`() {
        val world = World(worldType = WorldType.NO_PVP)
        val (alice, bob) = link(world, 1, 2)
        bob.addAttacked(alice)
        bob.player.setSkull(Skulls_t.SKULL_RED)

        assertThat(alice.getSkullClient(bob.player)).isEqualTo(Skulls_t.SKULL_NONE)
    }

    // --- situation marks (ExpertPvp) ------------------------------------------------

    @Test
    fun `the situation mark is yellow when the two players have a direct situation`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        bob.addAttacked(alice)

        assertThat(ExpertPvp.getSituationMark(bob, alice, enabled = true)).isEqualTo(ExpertPvpSituationMark.Yellow)
        assertThat(ExpertPvp.getSituationMark(alice, bob, enabled = true)).isEqualTo(ExpertPvpSituationMark.Yellow)
    }

    @Test
    fun `the situation mark is orange when an ally of the viewer is in a situation with the subject`() {
        val world = World()
        val alice = world.pvp(world.player(1))
        val bob = world.pvp(world.player(2))
        val carol = world.pvp(world.player(3))
        val party = Party(leader = alice.player)
        carol.player.setParty(party)
        bob.addAttacked(carol)

        assertThat(ExpertPvp.getSituationMark(bob, alice, enabled = true)).isEqualTo(ExpertPvpSituationMark.Orange)
    }

    @Test
    fun `the situation mark is brown when the subject has any pvp situation`() {
        val world = World()
        val dave = world.pvp(world.player(4))
        val bob = world.pvp(world.player(2))
        val carol = world.pvp(world.player(3))
        bob.addAttacked(carol)

        assertThat(ExpertPvp.getSituationMark(bob, dave, enabled = true)).isEqualTo(ExpertPvpSituationMark.Brown)
    }

    @Test
    fun `the situation mark is none for the same player or a disabled feature`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        bob.addAttacked(alice)

        assertThat(ExpertPvp.getSituationMark(bob, bob, enabled = true)).isEqualTo(ExpertPvpSituationMark.None)
        assertThat(ExpertPvp.getSituationMark(bob, alice, enabled = false)).isEqualTo(ExpertPvpSituationMark.None)
    }

    @Test
    fun `getSituationCreatureMark maps marks to the client colors`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        val carol = world.pvp(world.player(3))
        bob.addAttacked(alice)
        val dave = world.pvp(world.player(4))
        bob.addAttacked(dave)

        assertThat(ExpertPvp.getSituationCreatureMark(bob, alice, enabled = true)).isEqualTo(CreatureMark_t.CREATURE_MARK_YELLOW)
        assertThat(ExpertPvp.getSituationCreatureMark(bob, carol, enabled = true)).isEqualTo(CreatureMark_t.CREATURE_MARK_BROWN)
        assertThat(ExpertPvp.getSituationCreatureMark(bob, bob, enabled = true)).isEqualTo(CreatureMark_t.CREATURE_MARK_UNMARKED)
    }

    @Test
    fun `refreshCreatureMarkForViewer sends the resolved mark`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        bob.addAttacked(alice)

        ExpertPvp.refreshCreatureMarkForViewer(alice, bob, enabled = true)

        assertThat(requireNotNull(world.client).creatureMarks).containsExactly(bob.player to CreatureMark_t.CREATURE_MARK_YELLOW)
    }

    @Test
    fun `refreshVisibleSituationMarks sends marks to the visible spectators`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        bob.addAttacked(alice)
        world.visiblePlayers = { centre -> if (centre === alice) listOf(alice) else emptyList() }

        ExpertPvp.refreshVisibleSituationMarks(alice, bob, enabled = true, visiblePlayers = world.visiblePlayers)

        assertThat(requireNotNull(world.client).creatureMarks).contains(bob.player to CreatureMark_t.CREATURE_MARK_YELLOW)
    }

    @Test
    fun `removeAttacked refreshes the visible expert situation marks`() {
        val world = World(configuredWorldType = "expert-pvp")
        val (alice, bob) = link(world, 1, 2)
        alice.addAttacked(bob)
        world.visiblePlayers = { centre -> if (centre === alice) listOf(alice) else emptyList() }
        requireNotNull(world.client).creatureMarks.clear()

        alice.removeAttacked(bob)

        assertThat(requireNotNull(world.client).creatureMarks).contains(bob.player to CreatureMark_t.CREATURE_MARK_UNMARKED)
    }

    @Test
    fun `clearAttacked refreshes the visible expert situation marks`() {
        val world = World(configuredWorldType = "expert-pvp")
        val (alice, bob) = link(world, 1, 2)
        alice.addAttacked(bob)
        world.visiblePlayers = { viewer -> if (viewer === alice) listOf(bob) else emptyList() }
        requireNotNull(world.client).creatureMarks.clear()

        alice.clearAttacked()

        assertThat(requireNotNull(world.client).creatureMarks).contains(bob.player to CreatureMark_t.CREATURE_MARK_UNMARKED)
    }

    // --- expert side effects (ExpertPvp) --------------------------------------------

    @Test
    fun `applyCombatSideEffects links the fight applies the white skull and pz lock`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        val context = ExpertPvpRelationContext(actorGuid = 1, subjectGuid = 2, subjectIsPlayer = true)
        val decision = ExpertPvp.evaluateCombatAction(
            PvpMode_t.PVP_MODE_RED_FIST,
            ExpertPvpActionKind.DirectAttack,
            context,
        )

        ExpertPvp.applyCombatSideEffects(
            decision,
            context,
            enabled = true,
            playerByGuid = { guid -> world.component(guid) },
        )

        assertThat(decision.allowed).isTrue()
        assertThat(alice.hasAttacked(bob)).isTrue()
        assertThat(alice.player.getSkull()).isEqualTo(Skulls_t.SKULL_WHITE)
        assertThat(alice.isPzLocked()).isTrue()
        assertThat(world.client!!.creatureSkulls).contains(alice.player to Skulls_t.SKULL_WHITE)
    }

    @Test
    fun `applyCombatSideEffects is a no-op when the feature is disabled`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        val context = ExpertPvpRelationContext(actorGuid = 1, subjectGuid = 2, subjectIsPlayer = true)
        val decision = ExpertPvp.evaluateCombatAction(
            PvpMode_t.PVP_MODE_RED_FIST,
            ExpertPvpActionKind.DirectAttack,
            context,
        )

        ExpertPvp.applyCombatSideEffects(
            decision,
            context,
            enabled = false,
            playerByGuid = { guid -> world.component(guid) },
        )

        assertThat(alice.hasAttacked(bob)).isFalse()
        assertThat(alice.player.getSkull()).isEqualTo(Skulls_t.SKULL_NONE)
    }

    @Test
    fun `applyFieldStepSideEffects applies the blocked-step white skull and pz lock`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        val field = ExpertPvp.makeFieldContext(1, PvpMode_t.PVP_MODE_RED_FIST, ExpertPvp.ITEM_MAGICWALL, true)
        val context = ExpertPvpRelationContext(actorGuid = 1, subjectGuid = 2, subjectIsPlayer = true)
        val decision = ExpertPvp.evaluateFieldStep(field, context)

        ExpertPvp.applyFieldStepSideEffects(
            decision,
            context,
            enabled = true,
            playerByGuid = { guid -> world.component(guid) },
        )

        assertThat(decision.canStep).isFalse()
        assertThat(alice.hasAttacked(bob)).isTrue()
        assertThat(alice.player.getSkull()).isEqualTo(Skulls_t.SKULL_WHITE)
        assertThat(alice.isPzLocked()).isTrue()
    }

    @Test
    fun `applyFieldStepSideEffects ignores a step that is allowed`() {
        val world = World()
        val (alice, bob) = link(world, 1, 2)
        val field = ExpertPvp.makeFieldContext(1, PvpMode_t.PVP_MODE_DOVE, ExpertPvp.ITEM_MAGICWALL, true)
        val context = ExpertPvpRelationContext(actorGuid = 1, subjectGuid = 2, subjectIsPlayer = true)
        val decision = ExpertPvp.evaluateFieldStep(field, context)

        ExpertPvp.applyFieldStepSideEffects(
            decision,
            context,
            enabled = true,
            playerByGuid = { guid -> world.component(guid) },
        )

        assertThat(alice.hasAttacked(bob)).isFalse()
        assertThat(alice.player.getSkull()).isEqualTo(Skulls_t.SKULL_NONE)
    }
}
