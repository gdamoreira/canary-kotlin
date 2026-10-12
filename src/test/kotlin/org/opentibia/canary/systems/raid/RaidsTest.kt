package org.opentibia.canary.systems.raid

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.map.Position
import java.io.File
import java.nio.file.Path

class RaidsTest {

    private fun realDataDir(): String = "data-canary"

    @Test
    fun `loads real raids xml into raid definitions`() {
        val raids = Raids(environment(realDataDir()))

        assertThat(raids.loadFromXml()).isTrue()
        assertThat(raids.isLoaded()).isTrue()

        val raid = raids.getRaidByName("Example")
        assertThat(raid).isNotNull()
        assertThat(raid!!.getName()).isEqualTo("Example")
        assertThat(raid.getInterval()).isEqualTo(30_000) // interval2="500" * 60
        assertThat(raid.getMargin()).isEqualTo(6_000_000) // margin="100" * 60 * 1000
        assertThat(raid.canBeRepeated()).isFalse()
        assertThat(raid.isLoaded()).isTrue()
    }

    @Test
    fun `parses announce and singlespawn events from real example xml`() {
        val raids = Raids(environment(realDataDir()))
        raids.loadFromXml()

        val raid = raids.getRaidByName("Example")!!
        val events = raid.raidEvents
        assertThat(events).hasSize(2)

        val announce = events[0] as AnnounceEvent
        assertThat(announce.getDelay()).isEqualTo(1000)
        assertThat(announce.message).isEqualTo("Beware! The Toad is coming!!!")
        assertThat(announce.messageType).isEqualTo(MessageType.MESSAGE_EVENT_ADVANCE)

        val spawn = events[1] as SingleSpawnEvent
        assertThat(spawn.getDelay()).isEqualTo(1000)
        assertThat(spawn.monsterName).isEqualTo("Toad")
        assertThat(spawn.position).isEqualTo(Position(5000, 5008, 7))
    }

    @Test
    fun `maps announce type attribute to canary message types`(@TempDir dir: Path) {
        writeRaid(
            dir,
            "Types",
            """
            <raid>
              <announce delay="1000" type="warning" message="w"/>
              <announce delay="1000" type="event" message="e"/>
              <announce delay="1000" type="default" message="d"/>
              <announce delay="1000" type="description" message="desc"/>
              <announce delay="1000" type="smallstatus" message="s"/>
              <announce delay="1000" type="redconsole" message="r"/>
              <announce delay="1000" type="bogus" message="b"/>
            </raid>
            """.trimIndent(),
        )

        val raids = Raids(environment(dir.toFile().path))
        raids.loadFromXml()

        val types = raids.getRaidByName("Types")!!.raidEvents.map { (it as AnnounceEvent).messageType }
        assertThat(types).containsExactly(
            MessageType.MESSAGE_GAME_HIGHLIGHT,
            MessageType.MESSAGE_EVENT_ADVANCE,
            MessageType.MESSAGE_EVENT_ADVANCE,
            MessageType.MESSAGE_LOOK,
            MessageType.MESSAGE_FAILURE,
            MessageType.MESSAGE_GAMEMASTER_CONSOLE,
            MessageType.MESSAGE_EVENT_ADVANCE, // unknown type keeps the default
        )
    }

    @Test
    fun `clamps event delay to the raid minimum ticks`(@TempDir dir: Path) {
        writeRaid(
            dir,
            "Clamp",
            """
            <raid>
              <announce delay="10" message="too fast"/>
            </raid>
            """.trimIndent(),
        )

        val raids = Raids(environment(dir.toFile().path))
        raids.loadFromXml()

        assertThat(raids.getRaidByName("Clamp")!!.raidEvents.single().getDelay()).isEqualTo(1000)
    }

    @Test
    fun `sorts raid events by delay on load`(@TempDir dir: Path) {
        writeRaid(
            dir,
            "Sorted",
            """
            <raid>
              <announce delay="3000" type="event" message="third"/>
              <singlespawn delay="1000" name="Rat" x="1" y="2" z="3"/>
              <singlespawn delay="2000" name="Wolf" x="4" y="5" z="6"/>
            </raid>
            """.trimIndent(),
        )

        val raids = Raids(environment(dir.toFile().path))
        raids.loadFromXml()

        val raid = raids.getRaidByName("Sorted")!!
        assertThat(raid.raidEvents.map { it.getDelay() }).containsExactly(1000, 2000, 3000)
        assertThat((raid.raidEvents[0] as SingleSpawnEvent).monsterName).isEqualTo("Rat")
        assertThat((raid.raidEvents[2] as AnnounceEvent).message).isEqualTo("third")
    }

    @Test
    fun `executes multi stage events in order with delta scheduling`(@TempDir dir: Path) {
        writeRaid(
            dir,
            "Stages",
            """
            <raid>
              <announce delay="6000" type="event" message="wave"/>
              <singlespawn delay="1000" name="Rat" x="10" y="20" z="7"/>
              <singlespawn delay="5000" name="Wolf" x="11" y="21" z="7"/>
            </raid>
            """.trimIndent(),
        )

        val dispatcher = ManualRaidDispatcher()
        val log = mutableListOf<String>()
        val raids = Raids(environment(dir.toFile().path, dispatcher = dispatcher, log = log))
        raids.loadFromXml()

        val raid = raids.getRaidByName("Stages")!!
        raid.startRaid()

        // Nothing executes until the scheduled task fires.
        assertThat(log).isEmpty()

        dispatcher.runAll()

        assertThat(log.filter { it.startsWith("spawn:") || it.startsWith("announce:") })
            .containsExactly(
                "spawn:Rat@10,20,7",
                "spawn:Wolf@11,21,7",
                "announce:wave",
            )
        // First event at its own delay; later events at the delta from the previous
        // delay, floored at RAID_MINTICKS (1000): 1000, 5000-1000=4000, 6000-5000=1000.
        assertThat(dispatcher.delays()).containsExactly(1000L, 4000L, 1000L)
        assertThat(raid.state).isEqualTo(RaidState.RAIDSTATE_IDLE)
    }

    @Test
    fun `startup schedules checks and checkRaids starts an eligible raid`(@TempDir dir: Path) {
        writeRaid(
            dir,
            "Timer",
            """
            <raid>
              <announce delay="1000" type="event" message="go"/>
            </raid>
            """.trimIndent(),
        )

        val dispatcher = ManualRaidDispatcher()
        val log = mutableListOf<String>()
        var now = 0L
        val raids = Raids(
            environment(
                dir.toFile().path,
                dispatcher = dispatcher,
                log = log,
                clock = RaidClock { now },
                random = RaidRandom { _, _ -> 0 },
            ),
        )
        raids.loadFromXml()
        val raid = raids.getRaidByName("Timer")!!

        assertThat(raid.getMargin()).isEqualTo(60_000) // margin="1" * 60 * 1000

        assertThat(raids.startup()).isTrue()
        assertThat(raids.isStarted()).isTrue()

        // Before the margin elapses the raid must not start.
        raids.checkRaids()
        assertThat(raids.getRunning()).isNull()

        // Once lastRaidEnd + margin has elapsed the raid is selected.
        now = 60_000
        raids.checkRaids()
        assertThat(raids.getRunning()).isSameAs(raid)
        assertThat(raid.state).isEqualTo(RaidState.RAIDSTATE_EXECUTING)
    }

    @Test
    fun `repeat attribute uses canary boolean string semantics`(@TempDir dir: Path) {
        assertThat(parseRepeat(dir, "Repeater", "yes")).isTrue()
        assertThat(parseRepeat(dir, "Repeater", "false")).isFalse()
        assertThat(parseRepeat(dir, "Repeater", "no")).isFalse()
        assertThat(parseRepeat(dir, "Repeater", "0")).isFalse()
    }

    private fun parseRepeat(dir: Path, name: String, value: String): Boolean {
        val raidsDir = File(dir.toFile(), "raids").apply { mkdirs() }
        File(raidsDir, "$name.xml").writeText("<raid/>")
        File(raidsDir, "raids.xml").writeText(
            """<raids><raid name="$name" file="$name.xml" interval2="1" repeat="$value"/></raids>""",
        )
        val raids = Raids(environment(dir.toFile().path))
        raids.loadFromXml()
        return raids.getRaidByName(name)!!.canBeRepeated()
    }

    private fun writeRaid(dir: Path, name: String, raidXml: String) {
        val raidsDir = File(dir.toFile(), "raids").apply { mkdirs() }
        File(raidsDir, "$name.xml").writeText(raidXml)
        File(raidsDir, "raids.xml").writeText(
            """<raids><raid name="$name" file="$name.xml" interval2="1" margin="1"/></raids>""",
        )
    }

    private fun environment(
        dir: String,
        dispatcher: ManualRaidDispatcher = ManualRaidDispatcher(),
        log: MutableList<String> = mutableListOf(),
        announcer: RecordingAnnouncer = RecordingAnnouncer(log),
        spawner: RecordingSpawner = RecordingSpawner(log),
        random: RaidRandom = RaidRandom { _, _ -> 1 },
        clock: RaidClock = RaidClock { 0L },
        scriptRunner: RaidScriptRunner? = null,
    ): TestEnvironment = TestEnvironment(dir, dispatcher, announcer, spawner, random, clock, scriptRunner)

    private class TestEnvironment(
        override val dataDirectory: String,
        override val dispatcher: ManualRaidDispatcher,
        override val announcer: RecordingAnnouncer,
        override val spawner: RecordingSpawner,
        override val random: RaidRandom,
        override val clock: RaidClock,
        override val scriptRunner: RaidScriptRunner?,
    ) : RaidEnvironment

    private class ManualRaidDispatcher : RaidDispatcher {
        class Scheduled(val id: Long, val delayMs: Long, val taskName: String, val callback: () -> Unit)

        val scheduled = mutableListOf<Scheduled>()
        private val cancelled = mutableSetOf<Long>()
        private var nextId = 1L

        override fun scheduleEvent(delayMs: Long, taskName: String, callback: () -> Unit): Long {
            val id = nextId++
            scheduled.add(Scheduled(id, delayMs, taskName, callback))
            return id
        }

        override fun stopEvent(eventId: Long) {
            cancelled.add(eventId)
        }

        fun runAll() {
            var index = 0
            while (index < scheduled.size) {
                val task = scheduled[index]
                if (task.id !in cancelled) {
                    task.callback()
                }
                index++
            }
        }

        fun delays(): List<Long> = scheduled.filter { it.id !in cancelled }.map { it.delayMs }
    }

    private class RecordingAnnouncer(private val log: MutableList<String>) : RaidAnnouncer {
        val broadcasts = mutableListOf<Pair<String, MessageType>>()

        override fun broadcastMessage(message: String, messageType: MessageType) {
            broadcasts.add(message to messageType)
            log.add("announce:$message")
        }

        override fun sendWebhook(message: String) {
            log.add("webhook:$message")
        }
    }

    private class RecordingSpawner(private val log: MutableList<String>) : RaidSpawner {
        val created = mutableListOf<String>()
        val placed = mutableListOf<Pair<String, Position>>()

        override fun createMonster(name: String): RaidMonster {
            created.add(name)
            return RaidMonster(name)
        }

        override fun placeCreature(
            monster: RaidMonster,
            position: Position,
            extendedPos: Boolean,
            force: Boolean,
        ): Boolean {
            placed.add(monster.name to position)
            log.add("spawn:${monster.name}@${position.x},${position.y},${position.z}")
            return true
        }

        override fun getTile(x: Int, y: Int, z: Int): RaidTile? = null
    }
}
