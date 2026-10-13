package org.opentibia.canary.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player

/**
 * Transcript of `Spectators` (src/map/spectators.cpp:36-326,
 * src/map/spectators.hpp:21-129) over a fake `Map::getMapSector` seam.
 */
class SpectatorsTest {

    private lateinit var map: FakeSpectatorMap

    @BeforeEach
    fun resetSpectatorsCache() {
        Spectators.clearCache()
        map = FakeSpectatorMap()
    }

    private fun player(id: Long, position: Position): Player =
        Player(id, "Player$id", 100, 100, position = position)

    private fun monster(id: Long, position: Position): Monster =
        Monster(id, "Monster$id", 100, 100, position = position)

    private fun npc(id: Long, position: Position): Npc =
        Npc(id, "Npc$id", 100, position)

    // --- find / getSpectators ------------------------------------------------

    @Test
    fun `find includes creatures inside the default viewport bounds`() {
        val center = Position(100, 100, 7)
        val maxCorner = monster(1, Position(111, 111, 7))
        val minCorner = monster(2, Position(89, 89, 7))
        val farX = monster(3, Position(112, 100, 7))
        val farY = monster(4, Position(100, 112, 7))
        val nearX = monster(5, Position(88, 100, 7))
        val nearY = monster(6, Position(100, 88, 7))
        listOf(maxCorner, minCorner, farX, farY, nearX, nearY).forEach(map::place)

        val spectators = Spectators(map).find<Creature>(center)

        assertThat(spectators.data()).containsExactlyInAnyOrder(maxCorner, minCorner)
    }

    @Test
    fun `find applies explicit positive range arguments symmetrically`() {
        val center = Position(100, 100, 7)
        val inside = monster(1, Position(108, 106, 7))
        val outX = monster(2, Position(109, 100, 7))
        val outY = monster(3, Position(100, 107, 7))
        listOf(inside, outX, outY).forEach(map::place)

        val spectators = Spectators(map).find<Creature>(center, false, 8, 8, 6, 6)

        assertThat(spectators.data()).containsExactly(inside)
    }

    @Test
    fun `find excludes creatures on other floors when multifloor is false`() {
        val center = Position(100, 100, 7)
        val sameFloor = player(1, Position(100, 100, 7))
        val otherFloor = player(2, Position(100, 100, 8))
        listOf(sameFloor, otherFloor).forEach(map::place)

        val spectators = Spectators(map).find<Creature>(center)

        assertThat(spectators.data()).containsExactly(sameFloor)
    }

    @Test
    fun `find multifloor expands the z-range and offsets lower floors underground`() {
        val center = Position(100, 100, 8)
        val twoFloorsBelow = monster(1, Position(98, 98, 10))
        val outOfDepth = monster(2, Position(98, 98, 11))
        val oneFloorAbove = monster(3, Position(100, 100, 7))
        val shiftedOut = monster(4, Position(100, 100, 6))
        val outOfDepthAbove = monster(5, Position(98, 98, 5))
        listOf(twoFloorsBelow, outOfDepth, oneFloorAbove, shiftedOut, outOfDepthAbove).forEach(map::place)

        val spectators = Spectators(map).find<Creature>(center, true, 1, 1, 1, 1)

        assertThat(spectators.data()).containsExactlyInAnyOrder(twoFloorsBelow, oneFloorAbove)
    }

    @Test
    fun `find multifloor clamps the z-range on the surface layer and above`() {
        val ground = Position(100, 100, 7)
        val shallow = Position(100, 100, 6)
        val high = Position(100, 100, 5)
        val z7 = monster(1, Position(98, 98, 7))
        val z8 = monster(2, Position(98, 98, 8))
        val z9 = monster(3, Position(98, 98, 9))
        val z10 = monster(4, Position(98, 98, 10))
        listOf(z7, z8, z9, z10).forEach(map::place)

        val onSurface = Spectators(map).find<Creature>(ground, true, 1, 1, 1, 1)
        val oneAbove = Spectators(map).find<Creature>(shallow, true, 1, 1, 1, 1)
        val deep = Spectators(map).find<Creature>(high, true, 1, 1, 1, 1)

        assertThat(onSurface.data()).containsExactlyInAnyOrder(z8, z9)
        assertThat(oneAbove.data()).containsExactlyInAnyOrder(z7, z8)
        assertThat(deep.data()).containsExactly(z7)
    }

    @Test
    fun `find multifloor shifts the viewport by the per-floor offset`() {
        val center = Position(100, 100, 8)
        val sameFloor = monster(1, Position(100, 100, 8))
        val shiftedBelow = monster(2, Position(98, 98, 10))
        val unshiftedBelow = monster(3, Position(100, 100, 10))
        val shiftedAboveOut = monster(4, Position(100, 100, 6))
        listOf(sameFloor, shiftedBelow, unshiftedBelow, shiftedAboveOut).forEach(map::place)

        val spectators = Spectators(map).find<Creature>(center, true, 1, 1, 1, 1)

        assertThat(spectators.data()).containsExactlyInAnyOrder(sameFloor, shiftedBelow)
    }

    @Test
    fun `find selects only the requested species`() {
        val center = Position(100, 100, 7)
        val player = player(1, center)
        val monster = monster(2, center)
        val npc = npc(3, center)
        listOf(player, monster, npc).forEach(map::place)

        assertThat(Spectators(map).find<Player>(center).data()).containsExactly(player)
        assertThat(Spectators(map).find<Monster>(center).data()).containsExactly(monster)
        assertThat(Spectators(map).find<Npc>(center).data()).containsExactly(npc)
    }

    @Test
    fun `find walks every sector overlapping the viewport rectangle`() {
        val center = Position(100, 100, 7)
        val nearSectorCorner = monster(1, Position(92, 94, 7))
        val farSectorCorner = monster(2, Position(108, 106, 7))
        val sameSectorOutOfRange = monster(3, Position(85, 85, 7))
        val otherSectorOutOfRange = monster(4, Position(120, 120, 7))
        listOf(nearSectorCorner, farSectorCorner, sameSectorOutOfRange, otherSectorOutOfRange).forEach(map::place)

        val spectators = Spectators(map).find<Creature>(center, false, 8, 8, 6, 6)

        assertThat(spectators.data()).containsExactlyInAnyOrder(nearSectorCorner, farSectorCorner)
    }

    @Test
    fun `find chained calls merge both snapshots`() {
        val first = monster(1, Position(100, 100, 7))
        val second = monster(2, Position(300, 300, 7))
        map.place(first)
        map.place(second)

        val spectators = Spectators(map)
            .find<Creature>(Position(100, 100, 7))
            .find<Creature>(Position(300, 300, 7))

        assertThat(spectators.data()).containsExactlyInAnyOrder(first, second)
    }

    @Test
    fun `find with useCache false always queries the map`() {
        val center = Position(100, 100, 7)
        val creature = monster(1, center)
        map.place(creature)

        assertThat(Spectators(map).find<Creature>(center, useCache = false).data()).containsExactly(creature)

        map.remove(creature)
        assertThat(Spectators(map).find<Creature>(center, useCache = false).empty()).isTrue()
    }

    // --- SpectatorsCache ------------------------------------------------------

    @Test
    fun `find serves repeated queries from the cache after world changes`() {
        val center = Position(100, 100, 7)
        val cached = player(1, center)
        map.place(cached)

        val first = Spectators(map).find<Player>(center)
        assertThat(first.data()).containsExactly(cached)

        map.remove(cached)
        val second = Spectators(map).find<Player>(center)
        assertThat(second.data()).containsExactly(cached)
    }

    @Test
    fun `find caches empty snapshots until clearCache is called`() {
        val center = Position(200, 200, 7)
        assertThat(Spectators(map).find<Creature>(center).empty()).isTrue()

        val later = monster(1, center)
        map.place(later)
        assertThat(Spectators(map).find<Creature>(center).empty()).isTrue()

        Spectators.clearCache()
        assertThat(Spectators(map).find<Creature>(center).data()).containsExactly(later)
    }

    @Test
    fun `find widens a cached range and requeries the map`() {
        val center = Position(100, 100, 7)
        val near = monster(1, Position(100, 100, 7))
        map.place(near)

        val narrow = Spectators(map).find<Creature>(center, false, 1, 1, 1, 1)
        assertThat(narrow.data()).containsExactly(near)

        val far = monster(2, Position(105, 100, 7))
        map.place(far)
        val widened = Spectators(map).find<Creature>(center)

        assertThat(widened.data()).containsExactlyInAnyOrder(near, far)
    }

    @Test
    fun `find narrows a cached species range with a distance check`() {
        val center = Position(100, 100, 7)
        val near = player(1, Position(100, 100, 7))
        val far = player(2, Position(105, 100, 7))
        listOf(near, far).forEach(map::place)

        assertThat(Spectators(map).find<Player>(center).size()).isEqualTo(2)

        val narrowed = Spectators(map).find<Player>(center, false, 1, 1, 1, 1)

        assertThat(narrowed.data()).containsExactly(near)
    }

    @Test
    fun `cached all-creature queries keep every entry regardless of range or floor`() {
        val center = Position(100, 100, 7)
        val near = monster(1, Position(100, 100, 7))
        val farOtherFloor = monster(2, Position(105, 100, 8))
        listOf(near, farOtherFloor).forEach(map::place)

        val multifloor = Spectators(map).find<Creature>(center, true)
        assertThat(multifloor.data()).containsExactlyInAnyOrder(near, farOtherFloor)

        // src/map/spectators.cpp:109: the distance/floor/species clause is
        // OR-ed with the unfiltered case, so an all-creature cache hit keeps
        // every cached entry even for a narrowed single-floor query.
        val narrowed = Spectators(map).find<Creature>(center, false, 1, 1, 1, 1)
        assertThat(narrowed.data()).containsExactlyInAnyOrder(near, farOtherFloor)
    }

    @Test
    fun `find serves same-floor species queries from a multifloor cache filtered by z`() {
        val center = Position(100, 100, 7)
        val surface = player(1, Position(100, 100, 7))
        val oneFloorDown = player(2, Position(101, 101, 8))
        listOf(surface, oneFloorDown).forEach(map::place)

        val multifloor = Spectators(map).find<Player>(center, true)
        assertThat(multifloor.data()).containsExactlyInAnyOrder(surface, oneFloorDown)

        val sameFloor = Spectators(map).find<Player>(center, false)
        assertThat(sameFloor.data()).containsExactly(surface)
    }

    @Test
    fun `find with a species filter falls back to an unfiltered cache`() {
        val center = Position(100, 100, 7)
        val player = player(1, center)
        val monster = monster(2, Position(101, 100, 7))
        listOf(player, monster).forEach(map::place)

        assertThat(Spectators(map).find<Creature>(center).size()).isEqualTo(2)
        assertThat(Spectators(map).find<Player>(center).data()).containsExactly(player)
    }

    // --- filter / excludeMaster ----------------------------------------------

    @Test
    fun `filter returns only the requested species`() {
        val player = player(1, Position(100, 100, 7))
        val monster = monster(2, Position(100, 100, 7))
        val npc = npc(3, Position(100, 100, 7))
        val snapshot = Spectators(map).insert(player).insert(monster).insert(npc)

        assertThat(snapshot.filter<Player>().data()).containsExactly(player)
        assertThat(snapshot.filter<Monster>().data()).containsExactly(monster)
        assertThat(snapshot.filter<Npc>().data()).containsExactly(npc)
    }

    @Test
    fun `filter of Creature returns an empty snapshot`() {
        val snapshot = Spectators(map).insert(player(1, Position(100, 100, 7)))

        assertThat(snapshot.filter<Creature>().empty()).isTrue()
    }

    @Test
    fun `excludeMaster removes monsters that have a master`() {
        val player = player(1, Position(100, 100, 7))
        val wildMonster = monster(2, Position(100, 100, 7))
        val master = monster(3, Position(100, 100, 7))
        val summon = monster(4, Position(100, 100, 7))
        summon.setMaster(master)
        val snapshot = Spectators(map).insert(player).insert(wildMonster).insert(master).insert(summon)

        assertThat(snapshot.excludeMaster().data()).containsExactly(wildMonster, master)
    }

    @Test
    fun `excludePlayerMaster removes only the summons of players`() {
        val playerMaster = player(1, Position(100, 100, 7))
        val playerSummon = monster(2, Position(100, 100, 7))
        playerSummon.setMaster(playerMaster)
        val monsterMaster = monster(3, Position(100, 100, 7))
        val monsterSummon = monster(4, Position(100, 100, 7))
        monsterSummon.setMaster(monsterMaster)
        val wildMonster = monster(5, Position(100, 100, 7))
        val snapshot = Spectators(map)
            .insert(playerMaster)
            .insert(playerSummon)
            .insert(monsterMaster)
            .insert(monsterSummon)
            .insert(wildMonster)

        assertThat(snapshot.excludePlayerMaster().data())
            .containsExactly(playerMaster, monsterMaster, monsterSummon, wildMonster)
    }

    // --- insert / insertAll / erase / join ------------------------------------

    @Test
    fun `insert and insertAll deduplicate merged snapshots by identity`() {
        val inserted = player(1, Position(100, 100, 7))
        val equalInstance = Player(1, "Player1", 100, 100, position = Position(100, 100, 7))
        val snapshot = Spectators(map).insert(inserted).insertAll(listOf(inserted, equalInstance))

        assertThat(snapshot.size()).isEqualTo(2)
        assertThat(snapshot.data()).containsExactly(inserted, equalInstance)
        assertThat(snapshot.insert(null).size()).isEqualTo(2)
        assertThat(snapshot.insertAll(emptyList()).size()).isEqualTo(2)

        val equalCopy = Player(1, "Player1", 100, 100, position = Position(100, 100, 7))
        assertThat(snapshot.contains(inserted)).isTrue()
        assertThat(snapshot.contains(equalCopy)).isFalse()
    }

    @Test
    fun `erase removes by identity and join unions without mutating the sources`() {
        val player = player(1, Position(100, 100, 7))
        val monster = monster(2, Position(100, 100, 7))
        val first = Spectators(map).insert(player)
        val second = Spectators(map).insert(monster)

        val joined = first.join(second)

        assertThat(joined.data()).containsExactly(player, monster)
        assertThat(first.data()).containsExactly(player)
        assertThat(second.data()).containsExactly(monster)
        assertThat(first.erase(player)).isTrue()
        assertThat(first.empty()).isTrue()
        assertThat(first.erase(player)).isFalse()
    }

    // --- SpectatorSector ------------------------------------------------------

    @Test
    fun `SpectatorSector addCreature and removeCreature maintain the species lists`() {
        val position = Position(100, 100, 7)
        val sector = SpectatorSector()
        val player = player(1, position)
        val monster = monster(2, position)
        val npc = npc(3, position)

        sector.addCreature(player)
        sector.addCreature(monster)
        sector.addCreature(npc)

        assertThat(sector.creatureList).containsExactly(player, monster, npc)
        assertThat(sector.playerList).containsExactly(player)
        assertThat(sector.monsterList).containsExactly(monster)
        assertThat(sector.npcList).containsExactly(npc)

        sector.removeCreature(monster)
        assertThat(sector.creatureList).containsExactly(player, npc)
        assertThat(sector.monsterList).isEmpty()

        sector.removeCreature(monster(99, position))
        assertThat(sector.creatureList).containsExactly(player, npc)
    }
}

/** Test map seam mirroring `MapCache::getMapSector` (src/map/mapcache.hpp:174-182). */
private class FakeSpectatorMap : SpectatorMapAccess {
    private val sectors = mutableMapOf<Long, SpectatorSector>()

    override fun getMapSector(x: Int, y: Int): SpectatorSector? = sectors[sectorKey(x, y)]

    fun place(creature: Creature) {
        val position = creature.position
        sectors.getOrPut(sectorKey(position.x, position.y)) { SpectatorSector() }.addCreature(creature)
    }

    fun remove(creature: Creature) {
        val position = creature.position
        sectors[sectorKey(position.x, position.y)]?.removeCreature(creature)
    }

    private fun sectorKey(x: Int, y: Int): Long =
        ((x / SECTOR_SIZE).toLong() shl 32) or (y / SECTOR_SIZE).toLong()

    private companion object {
        const val SECTOR_SIZE = 16
    }
}
