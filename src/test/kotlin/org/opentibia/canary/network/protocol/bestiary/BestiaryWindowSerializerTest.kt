package org.opentibia.canary.network.protocol.bestiary

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.bestiary.BestiaryCreature
import org.opentibia.canary.systems.bestiary.BestiaryCreatures
import org.opentibia.canary.systems.bestiary.BestiaryMonsterData
import org.opentibia.canary.systems.bestiary.BestiaryRace
import org.opentibia.canary.systems.bestiary.BestiaryRaceSummary
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.bestiary.MonsterBestiaryInfo

class BestiaryWindowSerializerTest {

    private val dragon = MonsterBestiaryInfo(
        raceid = 34,
        name = "Dragon",
        bestiaryFirstUnlock = 50,
        bestiarySecondUnlock = 500,
        bestiaryToUnlock = 1000,
        bestiaryCharmsPoints = 25,
        bestiaryStars = 3,
        bestiaryOccurrence = 1,
        bestiaryClass = "Dragon",
        bestiaryRace = BestiaryRace.BESTY_RACE_DRAGON,
    )

    private fun dragonData(killCount: Int, currentLevel: Int) = BestiaryMonsterData(
        raceId = 34,
        bestiaryClass = "Dragon",
        killCount = killCount,
        currentLevel = currentLevel,
        monster = dragon,
    )

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `serializes races 0xD5 with class strings and little endian counters`() {
        val out = Unpooled.buffer()

        BestiaryWindowSerializer.writeBestiaryRaces(
            out,
            listOf(
                BestiaryRaceSummary(
                    race = BestiaryRace.BESTY_RACE_AMPHIBIC,
                    bestiaryClass = "Amphibic",
                    monsterCount = 3,
                    unlockedCount = 1,
                ),
                BestiaryRaceSummary(
                    race = BestiaryRace.BESTY_RACE_AQUATIC,
                    bestiaryClass = "",
                    monsterCount = 0,
                    unlockedCount = 0,
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD5,
                0x02, 0x00, // race count
                0x08, 0x00, 0x41, 0x6D, 0x70, 0x68, 0x69, 0x62, 0x69, 0x63, // "Amphibic"
                0x03, 0x00, // monster count
                0x01, 0x00, // unlocked count
                0x00, 0x00, // empty class
                0x00, 0x00,
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes one race block per supplied summary after the count`() {
        val out = Unpooled.buffer()
        val races = List(21) { BestiaryRaceSummary(BestiaryRace.entries[it + 1], "", 0, 0) }

        BestiaryWindowSerializer.writeBestiaryRaces(out, races)

        assertThat(out.toByteArray()).containsExactly(
            *(bytes(0xD5, 0x15, 0x00) + ByteArray(21 * 6))
        )
    }

    @Test
    fun `writes nothing for an aborted races response`() {
        val out = Unpooled.buffer()

        BestiaryWindowSerializer.writeBestiaryRaces(out, null)

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `serializes entry changed 0xD9 as opcode and little endian race id`() {
        val out = Unpooled.buffer()

        BestiaryWindowSerializer.writeBestiaryEntryChanged(out, 0x22)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD9,
                0x22, 0x00,
            )
        )
    }

    @Test
    fun `serializes creatures 0xD6 ascending with progress occurrence and animus`() {
        val out = Unpooled.buffer()
        val creatures = BestiaryCreatures(
            searchText = "dragon",
            creatures = listOf(
                BestiaryCreature(raceId = 0x0106, name = "Rat", killCount = 0, currentLevel = 0, occurrence = 0),
                BestiaryCreature(raceId = 0x0064, name = "Dragon", killCount = 120, currentLevel = 3, occurrence = 1),
            ),
        )

        BestiaryWindowSerializer.writeBestiaryCreatures(
            out,
            creatures,
            animusBonus = { name -> if (name == "Dragon") 250 else 0 },
            animusPoints = 125,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD6,
                0x06, 0x00, 0x64, 0x72, 0x61, 0x67, 0x6F, 0x6E, // search text "dragon"
                0x02, 0x00, // creature count
                0x64, 0x00, // race id 100 (sorted before 262)
                0x03, // progress
                0x01, // occurrence
                0xFA, 0x00, // animus bonus 250
                0x06, 0x01, // race id 262
                0x00, // no progress byte
                0x00, 0x00, // no animus bonus
                0x7D, 0x00, // animus points 125
            )
        )
    }

    @Test
    fun `serializes creatures 0xD6 with an empty search text and empty list`() {
        val out = Unpooled.buffer()

        BestiaryWindowSerializer.writeBestiaryCreatures(
            out,
            BestiaryCreatures(searchText = "", creatures = emptyList()),
            animusPoints = 7,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD6,
                0x00, 0x00, // empty search text
                0x00, 0x00, // creature count
                0x07, 0x00, // animus points
            )
        )
    }

    @Test
    fun `writes nothing for an aborted creatures response`() {
        val out = Unpooled.buffer()

        BestiaryWindowSerializer.writeBestiaryCreatures(out, null)

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `serializes monster data 0xD7 level one header and unlock thresholds`() {
        val out = Unpooled.buffer()

        BestiaryWindowSerializer.writeBestiaryMonsterData(
            out,
            BestiaryMonsterDataFrame(data = dragonData(killCount = 10, currentLevel = 1)),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD7,
                0x22, 0x00, // race id 34
                0x06, 0x00, 0x44, 0x72, 0x61, 0x67, 0x6F, 0x6E, // class "Dragon"
                0x01, // current level
                0x00, 0x00, // animus bonus
                0x00, 0x00, // animus points
                0x0A, 0x00, 0x00, 0x00, // kill count 10
                0x32, 0x00, // first unlock 50
                0xF4, 0x01, // second unlock 500
                0xE8, 0x03, // to unlock 1000
                0x03, // stars
                0x01, // occurrence
                0x00, // loot count
            )
        )
    }

    @Test
    fun `serializes monster data 0xD7 level two loot gating and combat stats`() {
        val out = Unpooled.buffer()
        val frame = BestiaryMonsterDataFrame(
            data = dragonData(killCount = 10, currentLevel = 2),
            lootItems = listOf(
                BestiaryLootEntry(id = 2148, name = "gold coin", countmax = 100, chance = 50000),
                BestiaryLootEntry(id = 2160, name = "crystal coin", countmax = 1, chance = 2500),
            ),
            healthMax = 1000,
            experience = 700,
            baseSpeed = 220,
            armor = 30,
        )

        BestiaryWindowSerializer.writeBestiaryMonsterData(out, frame)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD7,
                0x22, 0x00,
                0x06, 0x00, 0x44, 0x72, 0x61, 0x67, 0x6F, 0x6E,
                0x02,
                0x00, 0x00,
                0x00, 0x00,
                0x0A, 0x00, 0x00, 0x00,
                0x32, 0x00, 0xF4, 0x01, 0xE8, 0x03,
                0x03, 0x01,
                0x02, // loot count
                0x64, 0x08, // item id 2148
                0x00, // difficulty zero -> shown at level two
                0x00,
                0x09, 0x00, 0x67, 0x6F, 0x6C, 0x64, 0x20, 0x63, 0x6F, 0x69, 0x6E, // "gold coin"
                0x01, // countmax > 0
                0x00, 0x00, // hidden loot writes a zero item id
                0x02, // difficulty two -> hidden at level two
                0x00,
                0x19, 0x00, // charm points 25
                0x00, // attack mode (hostile, melee)
                0x02,
                0xE8, 0x03, 0x00, 0x00, // health 1000
                0xBC, 0x02, 0x00, 0x00, // experience 700
                0xDC, 0x00, // base speed 220
                0x1E, 0x00, // armor 30
                0x04, 0xFF, 0xFF, 0xFF, 0x7F, // mitigation 0.0 as scaled double
            )
        )
    }

    @Test
    fun `shows loot details at level one when the config enables loot display`() {
        val out = Unpooled.buffer()
        val frame = BestiaryMonsterDataFrame(
            data = dragonData(killCount = 10, currentLevel = 1),
            lootItems = listOf(
                BestiaryLootEntry(id = 2148, name = "gold coin", countmax = 100, chance = 50000),
            ),
        )

        BestiaryWindowSerializer.writeBestiaryMonsterData(out, frame, showLootsInBestiary = true)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD7,
                0x22, 0x00,
                0x06, 0x00, 0x44, 0x72, 0x61, 0x67, 0x6F, 0x6E,
                0x01,
                0x00, 0x00,
                0x00, 0x00,
                0x0A, 0x00, 0x00, 0x00,
                0x32, 0x00, 0xF4, 0x01, 0xE8, 0x03,
                0x03, 0x01,
                0x01, // loot count
                0x64, 0x08,
                0x00,
                0x00,
                0x09, 0x00, 0x67, 0x6F, 0x6C, 0x64, 0x20, 0x63, 0x6F, 0x69, 0x6E,
                0x01,
            )
        )
    }

    @Test
    fun `writes attack mode one when the monster keeps distance`() {
        val out = Unpooled.buffer()

        BestiaryWindowSerializer.writeBestiaryMonsterData(
            out,
            BestiaryMonsterDataFrame(
                data = dragonData(killCount = 10, currentLevel = 2),
                targetDistance = true,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD7,
                0x22, 0x00,
                0x06, 0x00, 0x44, 0x72, 0x61, 0x67, 0x6F, 0x6E,
                0x02,
                0x00, 0x00,
                0x00, 0x00,
                0x0A, 0x00, 0x00, 0x00,
                0x32, 0x00, 0xF4, 0x01, 0xE8, 0x03,
                0x03, 0x01,
                0x00,
                0x19, 0x00,
                0x01, // attack mode ranged
                0x02,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x04, 0xFF, 0xFF, 0xFF, 0x7F,
            )
        )
    }

    @Test
    fun `writes attack mode two for a non hostile monster`() {
        val out = Unpooled.buffer()

        BestiaryWindowSerializer.writeBestiaryMonsterData(
            out,
            BestiaryMonsterDataFrame(
                data = dragonData(killCount = 10, currentLevel = 2),
                isHostile = false,
                targetDistance = true,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD7,
                0x22, 0x00,
                0x06, 0x00, 0x44, 0x72, 0x61, 0x67, 0x6F, 0x6E,
                0x02,
                0x00, 0x00,
                0x00, 0x00,
                0x0A, 0x00, 0x00, 0x00,
                0x32, 0x00, 0xF4, 0x01, 0xE8, 0x03,
                0x03, 0x01,
                0x00,
                0x19, 0x00,
                0x02, // attack mode passive
                0x02,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x04, 0xFF, 0xFF, 0xFF, 0x7F,
            )
        )
    }

    @Test
    fun `serializes monster data 0xD7 level three elements and locations`() {
        val out = Unpooled.buffer()
        val frame = BestiaryMonsterDataFrame(
            data = dragonData(killCount = 600, currentLevel = 3).copy(
                monster = dragon.copy(elementMap = mapOf(1 to 20, 9 to 50)),
            ),
            healthMax = 1000,
            experience = 700,
            baseSpeed = 220,
            armor = 30,
            bestiaryLocations = "Dragon Lair",
        )

        BestiaryWindowSerializer.writeBestiaryMonsterData(out, frame)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD7,
                0x22, 0x00,
                0x06, 0x00, 0x44, 0x72, 0x61, 0x67, 0x6F, 0x6E,
                0x03,
                0x00, 0x00,
                0x00, 0x00,
                0x58, 0x02, 0x00, 0x00, // kill count 600
                0x32, 0x00, 0xF4, 0x01, 0xE8, 0x03,
                0x03, 0x01,
                0x00,
                0x19, 0x00,
                0x00, // attack mode
                0x02,
                0xE8, 0x03, 0x00, 0x00,
                0xBC, 0x02, 0x00, 0x00,
                0xDC, 0x00,
                0x1E, 0x00,
                0x04, 0xFF, 0xFF, 0xFF, 0x7F,
                0x08, // eight element slots
                0x00, 0x64, 0x00, // physical 100
                0x01, 0x50, 0x00, // fire 100 - 20
                0x02, 0x64, 0x00,
                0x03, 0x64, 0x00,
                0x04, 0x32, 0x00, // ice 100 - 50
                0x05, 0x64, 0x00,
                0x06, 0x64, 0x00,
                0x07, 0x64, 0x00,
                0x01, 0x00, // tracker flag
                0x0B, 0x00, 0x44, 0x72, 0x61, 0x67, 0x6F, 0x6E, 0x20, 0x4C, 0x61, 0x69, 0x72, // "Dragon Lair"
            )
        )
    }

    @Test
    fun `writes nothing for an aborted monster data response`() {
        val out = Unpooled.buffer()

        BestiaryWindowSerializer.writeBestiaryMonsterData(out, null)

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `serializes charms 0xD8 with reset cost tier assignment and stage two monsters`() {
        val out = Unpooled.buffer()
        val frame = BestiaryCharmsFrame(
            level = 150,
            isPremium = true,
            charms = listOf(
                BestiaryCharmEntry(charm = CharmRune.CHARM_WOUND, unlocked = true, tier = 2, raceId = 34),
                BestiaryCharmEntry(charm = CharmRune.CHARM_ENFLAME),
            ),
            usedCharmRunes = listOf(CharmRune.CHARM_WOUND, CharmRune.CHARM_ENFLAME),
            stageTwoMonsters = listOf(34, 35),
        )

        BestiaryWindowSerializer.writeBestiaryCharms(out, frame)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD8,
                0xF0, 0xB3, 0x1A, 0x00, 0x00, 0x00, 0x00, 0x00, // reset cost 1_750_000
                0x02, // charm count
                0x00, // charm rune wound
                0x02, // tier
                0x01, // creature assigned
                0x22, 0x00, // race id 34
                0x98, 0x3A, 0x00, 0x00, // remove cost 15_000
                0x01, // charm rune enflame
                0x00,
                0x00,
                0x04, // premium slots 6 minus 2 used
                0x02, 0x00, // stage two count
                0x22, 0x00, 0x00, 0x00, // stage two race id 34 as uint32
                0x23, 0x00, 0x00, 0x00, // stage two race id 35 as uint32
            )
        )
    }

    @Test
    fun `removes stage two monsters assigned to two charm runes`() {
        val out = Unpooled.buffer()
        val frame = BestiaryCharmsFrame(
            level = 150,
            isPremium = true,
            charms = listOf(
                BestiaryCharmEntry(charm = CharmRune.CHARM_WOUND, unlocked = true, tier = 0, raceId = 34),
                BestiaryCharmEntry(charm = CharmRune.CHARM_ENFLAME, unlocked = true, tier = 0, raceId = 34),
            ),
            usedCharmRunes = listOf(CharmRune.CHARM_WOUND, CharmRune.CHARM_ENFLAME),
            stageTwoMonsters = listOf(34, 35),
        )

        BestiaryWindowSerializer.writeBestiaryCharms(out, frame)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD8,
                0xF0, 0xB3, 0x1A, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x02,
                0x00, 0x00, 0x01, 0x22, 0x00, 0x98, 0x3A, 0x00, 0x00,
                0x01, 0x00, 0x01, 0x22, 0x00, 0x98, 0x3A, 0x00, 0x00,
                0x04,
                0x01, 0x00, // only race id 35 survives
                0x23, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `applies the charm expansion discount and all slots marker`() {
        val out = Unpooled.buffer()
        val frame = BestiaryCharmsFrame(
            level = 150,
            isPremium = true,
            hasCharmExpansion = true,
            charms = listOf(
                BestiaryCharmEntry(charm = CharmRune.CHARM_WOUND, unlocked = true, tier = 1, raceId = 34),
            ),
            usedCharmRunes = listOf(CharmRune.CHARM_WOUND),
        )

        BestiaryWindowSerializer.writeBestiaryCharms(out, frame)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD8,
                0xF4, 0x06, 0x14, 0x00, 0x00, 0x00, 0x00, 0x00, // discounted reset cost 1_312_500
                0x01,
                0x00, 0x01, 0x01, 0x22, 0x00, 0xF2, 0x2B, 0x00, 0x00, // discounted remove cost 11_250
                0xFF, // all charm slots
                0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes a zero assigned flag when an unlocked charm has no creature`() {
        val out = Unpooled.buffer()
        val frame = BestiaryCharmsFrame(
            level = 50,
            charms = listOf(
                BestiaryCharmEntry(charm = CharmRune.CHARM_WOUND, unlocked = true, tier = 3),
            ),
            stageTwoMonsters = listOf(34),
        )

        BestiaryWindowSerializer.writeBestiaryCharms(out, frame)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xD8,
                0xA0, 0x86, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, // reset cost 100_000
                0x01,
                0x00, 0x03, 0x00, // no creature assigned
                0x02, // free slots 2 minus 0 used
                0x01, 0x00,
                0x22, 0x00, 0x00, 0x00,
            )
        )
    }

    @Test
    fun `writes a charm frame through an embedded channel`() {
        val channel = EmbeddedChannel()
        try {
            val out = Unpooled.buffer()
            BestiaryWindowSerializer.writeBestiaryCharms(out, BestiaryCharmsFrame(level = 50))

            assertThat(channel.writeOutbound(out)).isTrue()
            val frame = channel.readOutbound<ByteBuf>()
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(0xD8)
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
