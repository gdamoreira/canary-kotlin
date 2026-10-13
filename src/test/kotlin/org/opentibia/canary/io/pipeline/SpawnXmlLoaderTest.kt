package org.opentibia.canary.io.pipeline

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.creatures.spawns.FakeMonsterFactory
import org.opentibia.canary.creatures.spawns.FakeNpcFactory
import org.opentibia.canary.creatures.spawns.FakeSpawnScheduler
import org.opentibia.canary.creatures.spawns.MonsterType
import org.opentibia.canary.creatures.spawns.NpcType
import org.opentibia.canary.creatures.spawns.SpawnMonster
import org.opentibia.canary.creatures.spawns.SpawnNpc
import org.opentibia.canary.creatures.spawns.monsterTypeLookup
import org.opentibia.canary.creatures.spawns.npcTypeLookup
import org.opentibia.canary.map.Position
import java.io.File
import java.nio.file.Path

class SpawnXmlLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    private val now = { 0L }
    private val scheduler = FakeSpawnScheduler(now)
    private val monsterFactory = FakeMonsterFactory()
    private val npcFactory = FakeNpcFactory()

    private fun xmlFile(name: String, content: String): File =
        tempDir.resolve(name).toFile().apply { writeText(content) }

    @Test
    fun `loads monster spawn blocks with offsets direction and schedule intervals`() {
        val dragon = MonsterType("Dragon", isBlockable = true)
        val rat = MonsterType("Rat", isBlockable = true)
        val file = xmlFile(
            "monster.xml",
            """
            <monsters>
                <monster centerx="1000" centery="1000" centerz="7" radius="5">
                    <monster name="Dragon" x="1" y="-2" direction="2" spawntime="90"/>
                    <monster name="Rat" x="0" y="0"/>
                </monster>
            </monsters>
            """.trimIndent(),
        )

        val spawns = SpawnXmlLoader.loadMonsters(file) { pos, radius ->
            SpawnMonster(pos, radius, monsterTypeLookup(dragon, rat), monsterFactory, scheduler, now = now)
        }

        assertThat(spawns).isNotNull
        assertThat(spawns!!.isLoaded()).isTrue
        assertThat(spawns.getspawnMonsterList()).hasSize(1)

        val spawn = spawns.getspawnMonsterList().first()
        assertThat(spawn.radius).isEqualTo(5)
        assertThat(spawn.monsterSpawnMap).hasSize(2)

        val dragonBlock = spawn.monsterSpawnMap[1]!!
        assertThat(dragonBlock.pos).isEqualTo(Position(1001, 998, 7))
        assertThat(dragonBlock.direction.name).isEqualTo("South")
        assertThat(dragonBlock.interval).isEqualTo(90000L)
        assertThat(dragonBlock.monsterTypes.keys).containsExactly(dragon)

        val ratBlock = spawn.monsterSpawnMap[2]!!
        assertThat(ratBlock.pos).isEqualTo(Position(1000, 1000, 7))
        assertThat(ratBlock.direction.name).isEqualTo("North")
        assertThat(ratBlock.interval).isEqualTo(60000L)
        assertThat(ratBlock.monsterTypes.keys).containsExactly(rat)
    }

    @Test
    fun `monster xml defaults missing radius to -1 and missing or zero spawntime to the default respawn time`() {
        val rat = MonsterType("Rat", isBlockable = true)
        val caveRat = MonsterType("Cave Rat", isBlockable = true)
        val file = xmlFile(
            "monster_defaults.xml",
            """
            <monsters>
                <monster centerx="10" centery="20" centerz="7">
                    <monster name="Rat" spawntime="0"/>
                    <monster name="Cave Rat"/>
                </monster>
            </monsters>
            """.trimIndent(),
        )

        val spawns = SpawnXmlLoader.loadMonsters(file) { pos, radius ->
            SpawnMonster(pos, radius, monsterTypeLookup(rat, caveRat), monsterFactory, scheduler, now = now)
        }

        val spawn = spawns!!.getspawnMonsterList().first()
        assertThat(spawn.radius).isEqualTo(-1)
        assertThat(spawn.monsterSpawnMap).hasSize(1)
        val block = spawn.monsterSpawnMap[1]!!
        assertThat(block.pos).isEqualTo(Position(10, 20, 7))
        assertThat(block.interval).isEqualTo(60000L)
        assertThat(block.monsterTypes.keys).containsExactly(rat, caveRat)
    }

    @Test
    fun `npc xml only registers npcs whose spawntime is between one second and one day`() {
        val alice = NpcType("Alice")
        val dave = NpcType("Dave")
        val file = xmlFile(
            "npc.xml",
            """
            <npcs>
                <npc centerx="500" centery="600" centerz="6" radius="4">
                    <npc name="Alice" x="2" y="-1" direction="1" spawntime="60"/>
                    <npc name="Bob" spawntime="0"/>
                    <npc name="Carol" spawntime="86401"/>
                    <npc name="Dave" spawntime="86400"/>
                    <npc name="Eve"/>
                </npc>
            </npcs>
            """.trimIndent(),
        )

        val spawns = SpawnXmlLoader.loadNpcs(file) { pos, radius ->
            SpawnNpc(pos, radius, npcTypeLookup(alice, dave), npcFactory, scheduler, now = now)
        }

        assertThat(spawns).isNotNull
        assertThat(spawns!!.isLoaded()).isTrue
        assertThat(spawns.getSpawnNpcList()).hasSize(1)

        val spawn = spawns.getSpawnNpcList().first()
        assertThat(spawn.radius).isEqualTo(4)
        assertThat(spawn.spawnNpcMap).hasSize(2)

        val aliceBlock = spawn.spawnNpcMap[1]!!
        assertThat(aliceBlock.pos).isEqualTo(Position(502, 599, 6))
        assertThat(aliceBlock.direction.name).isEqualTo("East")
        assertThat(aliceBlock.interval).isEqualTo(60000L)
        assertThat(aliceBlock.npcType).isSameAs(alice)

        val daveBlock = spawn.spawnNpcMap[2]!!
        assertThat(daveBlock.pos).isEqualTo(Position(500, 600, 6))
        assertThat(daveBlock.direction.name).isEqualTo("North")
        assertThat(daveBlock.interval).isEqualTo(86400000L)
        assertThat(daveBlock.npcType).isSameAs(dave)
    }

    @Test
    fun `empty spawn blocks and unknown monster names are skipped`() {
        val rat = MonsterType("Rat", isBlockable = true)
        val file = xmlFile(
            "monster_skips.xml",
            """
            <monsters>
                <monster centerx="1" centery="1" centerz="7"/>
                <monster centerx="2" centery="2" centerz="7" radius="2">
                    <monster name="Unknown"/>
                </monster>
            </monsters>
            """.trimIndent(),
        )

        val spawns = SpawnXmlLoader.loadMonsters(file) { pos, radius ->
            SpawnMonster(pos, radius, monsterTypeLookup(rat), monsterFactory, scheduler, now = now)
        }

        assertThat(spawns!!.getspawnMonsterList()).hasSize(1)
        assertThat(spawns.getspawnMonsterList().first().monsterSpawnMap).isEmpty()
    }
}
