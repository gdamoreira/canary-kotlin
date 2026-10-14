package org.opentibia.canary.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionInvisible
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.items.Item
import org.opentibia.canary.network.protocol.InMemoryProtocolSink

/**
 * Transcript of the 16x16 spectator sector wiring:
 *  - `MapSector::addCreature`/`removeCreature` (src/map/utils/mapsector.cpp:49-102)
 *  - `MapCache::createMapSector`/`getBestMapSector`/`getMapSector`
 *    (src/map/mapcache.cpp:414-452, mapcache.hpp:174-182)
 *  - `Map::placeCreature` sector add (src/map/map.cpp:674)
 *  - `Map::moveCreature` sector switch + `sendCreatureMove` fanout (src/map/map.cpp:679-814)
 *  - `Tile::removeCreature` sector removal (src/items/tile.cpp:1434-1441)
 *  - `Game::placeCreature` appear fanout (src/game/game.cpp:1532-1541)
 *  - `Game::removeCreature` remove fanout (src/game/game.cpp:1561-1593)
 */
class MapSpectatorSectorsTest {

    private val center = Position(100, 100, 7)

    @BeforeEach
    fun resetSpectatorsCache() {
        Spectators.clearCache()
    }

    private fun GameMap.withGroundTile(position: Position): Tile {
        val tile = getOrCreateTile(position)
        tile.ground = Item(id = 100)
        return tile
    }

    private fun player(id: Long, position: Position): Player =
        Player(id, "P$id", 100, 100, position = position)

    private fun monster(id: Long, position: Position): Monster =
        Monster(id, "M$id", 100, 100, position = position)

    private fun npc(id: Long, position: Position): Npc =
        Npc(id, "N$id", 100, position)

    private fun bind(player: Player, map: GameMap): InMemoryProtocolSink {
        val sink = InMemoryProtocolSink(player, map)
        player.protocolSink = sink
        return sink
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    // --- sector membership ----------------------------------------------------

    @Test
    fun `placeCreature registers players monsters and npcs in the 16x16 sector species lists`() {
        val map = GameMap()
        val player = player(1, Position(100, 100, 7))
        val monster = monster(2, Position(101, 100, 7))
        val npc = npc(3, Position(102, 100, 7))
        listOf(player, monster, npc).forEach { map.withGroundTile(it.position) }

        assertThat(map.placeCreature(player.position, player)).isTrue()
        assertThat(map.placeCreature(monster.position, monster)).isTrue()
        assertThat(map.placeCreature(npc.position, npc)).isTrue()

        val sector = map.getMapSector(100, 100)
        assertThat(sector).isNotNull()
        assertThat(sector!!.creatureList).containsExactly(player, monster, npc)
        assertThat(sector.playerList).containsExactly(player)
        assertThat(sector.monsterList).containsExactly(monster)
        assertThat(sector.npcList).containsExactly(npc)
    }

    @Test
    fun `getMapSector resolves one spectator sector per 16x16 chunk`() {
        val map = GameMap()
        map.withGroundTile(Position(100, 100, 7))
        map.withGroundTile(Position(111, 111, 7))
        map.withGroundTile(Position(112, 112, 7))

        assertThat(map.getMapSector(100, 100)).isSameAs(map.getMapSector(111, 111))
        assertThat(map.getMapSector(112, 112)).isNotSameAs(map.getMapSector(100, 100))
        assertThat(map.getMapSector(1000, 1000)).isNull()
    }

    @Test
    fun `getOrCreateTile links adjacent 16x16 spectator sectors`() {
        val map = GameMap()
        map.withGroundTile(Position(0, 0, 7))
        map.withGroundTile(Position(16, 0, 7))
        map.withGroundTile(Position(0, 16, 7))

        val origin = map.getMapSector(0, 0)
        assertThat(origin).isNotNull()
        assertThat(origin!!.sectorE).isSameAs(map.getMapSector(16, 0))
        assertThat(origin.sectorS).isSameAs(map.getMapSector(0, 16))
    }

    @Test
    fun `Spectators resolves creatures through the map sector grid`() {
        val map = GameMap()
        val near = monster(1, Position(105, 100, 7))
        val far = monster(2, Position(130, 100, 7))
        listOf(near, far).forEach { map.withGroundTile(it.position) }

        assertThat(map.placeCreature(near.position, near)).isTrue()
        assertThat(map.placeCreature(far.position, far)).isTrue()

        assertThat(Spectators(map).find<Creature>(center, useCache = false).data()).containsExactly(near)
    }

    @Test
    fun `moveCreature switches the sector entry when crossing a 16x16 boundary`() {
        val map = GameMap()
        val from = map.withGroundTile(Position(110, 100, 7))
        val to = map.withGroundTile(Position(114, 100, 7))
        val rat = monster(1, from.position)
        assertThat(map.placeCreature(from.position, rat)).isTrue()

        val oldSector = map.getMapSector(110, 100)
        val newSector = map.getMapSector(114, 100)
        assertThat(oldSector).isNotSameAs(newSector)
        assertThat(oldSector!!.monsterList).containsExactly(rat)

        map.moveCreature(rat, to)

        assertThat(oldSector.monsterList).isEmpty()
        assertThat(oldSector.creatureList).isEmpty()
        assertThat(newSector!!.monsterList).containsExactly(rat)
        assertThat(newSector.creatureList).containsExactly(rat)
    }

    @Test
    fun `moveCreature keeps the sector entry for steps inside one chunk`() {
        val map = GameMap()
        val from = map.withGroundTile(Position(100, 100, 7))
        val to = map.withGroundTile(Position(101, 100, 7))
        val rat = monster(1, from.position)
        assertThat(map.placeCreature(from.position, rat)).isTrue()
        val sector = map.getMapSector(100, 100)!!

        map.moveCreature(rat, to)

        assertThat(sector.creatureList).containsExactly(rat)
        assertThat(sector.monsterList).containsExactly(rat)
    }

    @Test
    fun `removeCreature drops the sector entry`() {
        val map = GameMap()
        val position = Position(100, 100, 7)
        map.withGroundTile(position)
        val rat = monster(1, position)
        assertThat(map.placeCreature(position, rat)).isTrue()
        val sector = map.getMapSector(position.x, position.y)!!

        assertThat(map.removeCreature(rat)).isTrue()

        assertThat(sector.creatureList).isEmpty()
        assertThat(sector.monsterList).isEmpty()
    }

    // --- place fanout ---------------------------------------------------------

    @Test
    fun `placeCreature emits the appear frame to in-range player spectators`() {
        val map = GameMap()
        map.withGroundTile(center)
        map.withGroundTile(Position(101, 100, 7))
        val observer = player(1, center)
        assertThat(map.placeCreature(center, observer)).isTrue()
        val sink = bind(observer, map)

        val rat = monster(2, Position(101, 100, 7))
        assertThat(map.placeCreature(rat.position, rat)).isTrue()

        assertThat(sink.frames).hasSize(1)
        assertThat(sink.frames[0]).startsWith(*bytes(0x6A, 0x65, 0x00, 0x64, 0x00, 0x07, 0x01))
    }

    // --- move fanout ----------------------------------------------------------

    @Test
    fun `moveCreature emits sendCreatureMove to in-range player spectators`() {
        val map = GameMap()
        map.withGroundTile(center)
        val from = map.withGroundTile(Position(101, 100, 7))
        val to = map.withGroundTile(Position(102, 100, 7))
        val observer = player(1, center)
        val rat = monster(2, from.position)
        assertThat(map.placeCreature(center, observer)).isTrue()
        assertThat(map.placeCreature(from.position, rat)).isTrue()
        val sink = bind(observer, map)

        map.moveCreature(rat, to)

        assertThat(sink.frames).containsExactly(
            bytes(0x6D, 0x65, 0x00, 0x64, 0x00, 0x07, 0x01, 0x66, 0x00, 0x64, 0x00, 0x07),
        )
    }

    @Test
    fun `moveCreature does not emit outside the spectator viewport`() {
        val map = GameMap()
        val observerPos = Position(120, 100, 7)
        val nearObserverPos = Position(99, 100, 7)
        map.withGroundTile(observerPos)
        map.withGroundTile(nearObserverPos)
        val from = map.withGroundTile(Position(101, 100, 7))
        val to = map.withGroundTile(Position(102, 100, 7))
        val observer = player(1, observerPos)
        val nearObserver = player(2, nearObserverPos)
        val rat = monster(3, from.position)
        assertThat(map.placeCreature(observerPos, observer)).isTrue()
        assertThat(map.placeCreature(nearObserverPos, nearObserver)).isTrue()
        assertThat(map.placeCreature(from.position, rat)).isTrue()
        val sink = bind(observer, map)
        val nearSink = bind(nearObserver, map)

        map.moveCreature(rat, to)

        assertThat(sink.frames).isEmpty()
        assertThat(nearSink.frames).isNotEmpty()
    }

    @Test
    fun `moveCreature refreshes players at both ends of a teleport`() {
        val map = GameMap()
        val oldPos = Position(100, 100, 7)
        val newPos = Position(120, 100, 7)
        val firstObserverPos = Position(95, 100, 7)
        val secondObserverPos = Position(125, 100, 7)
        listOf(oldPos, newPos, firstObserverPos, secondObserverPos).forEach { map.withGroundTile(it) }
        val newTile = map.getTile(newPos)!!
        val first = player(1, firstObserverPos)
        val second = player(2, secondObserverPos)
        val rat = monster(3, oldPos)
        assertThat(map.placeCreature(firstObserverPos, first)).isTrue()
        assertThat(map.placeCreature(secondObserverPos, second)).isTrue()
        assertThat(map.placeCreature(oldPos, rat)).isTrue()
        val firstSink = bind(first, map)
        val secondSink = bind(second, map)

        map.moveCreature(rat, newTile)

        assertThat(firstSink.frames).containsExactly(
            bytes(0x6C, 0x64, 0x00, 0x64, 0x00, 0x07, 0x01),
        )
        assertThat(secondSink.frames).hasSize(1)
        assertThat(secondSink.frames[0]).startsWith(*bytes(0x6A, 0x78, 0x00, 0x64, 0x00, 0x07, 0x01))
    }

    @Test
    fun `moveCreature skips the frame for spectators that cannot see the creature`() {
        val map = GameMap()
        map.withGroundTile(center)
        map.withGroundTile(Position(99, 100, 7))
        val from = map.withGroundTile(Position(101, 100, 7))
        val to = map.withGroundTile(Position(102, 100, 7))
        val observer = player(1, center)
        val accessObserver = player(2, Position(99, 100, 7)).apply {
            group = Group(name = "GM", access = true)
        }
        val rat = monster(3, from.position)
        assertThat(map.placeCreature(center, observer)).isTrue()
        assertThat(map.placeCreature(accessObserver.position, accessObserver)).isTrue()
        assertThat(map.placeCreature(from.position, rat)).isTrue()
        rat.addCondition(
            ConditionInvisible(
                ConditionId.CONDITIONID_COMBAT,
                ConditionType.CONDITION_INVISIBLE,
                ticks = 10000,
                now = { 0 },
            ),
        )
        val sink = bind(observer, map)
        val accessSink = bind(accessObserver, map)

        map.moveCreature(rat, to)

        assertThat(sink.frames).isEmpty()
        assertThat(accessSink.frames).isNotEmpty()
    }

    // --- remove fanout --------------------------------------------------------

    @Test
    fun `removeCreature emits sendRemoveTileThing with the client stack position`() {
        val map = GameMap()
        map.withGroundTile(center)
        val adjacent = map.withGroundTile(Position(101, 100, 7))
        val observer = player(1, center)
        val rat = monster(2, adjacent.position)
        assertThat(map.placeCreature(center, observer)).isTrue()
        assertThat(map.placeCreature(adjacent.position, rat)).isTrue()
        val sink = bind(observer, map)

        assertThat(map.removeCreature(rat)).isTrue()

        assertThat(sink.frames).containsExactly(bytes(0x6C, 0x65, 0x00, 0x64, 0x00, 0x07, 0x01))
    }
}
