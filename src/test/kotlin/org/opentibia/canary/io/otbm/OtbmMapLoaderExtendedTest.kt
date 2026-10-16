package org.opentibia.canary.io.otbm

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the remaining `IOMap::parseMapDataAttributes` /
 * `IOMap::parseTileArea` nodes (src/io/iomap.cpp:93-261): the external
 * spawn/npc/house/zone file attributes, `OTBM_ATTR_TILE_FLAGS`,
 * `OTBM_HOUSETILE`, `OTBM_ITEM` child nodes, and `OTBM_TILE_ZONE`.
 */
class OtbmMapLoaderExtendedTest {

    private fun ByteBuf.node(type: Int, props: ByteBuf.() -> Unit) {
        writeByte(OtbmConstants.NODE_START.toInt())
        writeByte(type)
        props()
        writeByte(OtbmConstants.NODE_END.toInt())
    }

    private fun ByteBuf.writeString(value: String) {
        writeShortLE(value.length)
        writeBytes(value.toByteArray())
    }

    private fun buildMap(mapData: ByteBuf.() -> Unit): ByteBuf {
        val buf = Unpooled.buffer()
        buf.writeInt(0)
        buf.writeByte(OtbmConstants.NODE_START.toInt())
        buf.writeByte(OtbmConstants.OTBM_ROOT.toInt())
        buf.writeIntLE(2)
        buf.node(OtbmConstants.OTBM_MAP_DATA.toInt(), mapData)
        buf.writeByte(OtbmConstants.NODE_END.toInt())
        return buf
    }

    private val extMonsterFile = 11
    private val extHouseFile = 13
    private val extNpcFile = 23
    private val extZoneFile = 24
    private val tileFlagsAttr = 3

    @Test
    fun `map data attributes resolve the external file names against the map path`() {
        val buffer = buildMap {
            writeByte(OtbmConstants.OTBM_ATTR_DESCRIPTION.toInt())
            writeString("Test")
            writeByte(extMonsterFile)
            writeString("world-monster.xml")
            writeByte(extNpcFile)
            writeString("world-npc.xml")
            writeByte(extHouseFile)
            writeString("world-house.xml")
            writeByte(extZoneFile)
            writeString("world-zones.xml")
        }

        val map = OtbmMapLoader.load(buffer, path = "/maps/world.otbm")

        assertThat(map.path).isEqualTo("/maps/world.otbm")
        assertThat(map.monsterfile).isEqualTo("/maps/world-monster.xml")
        assertThat(map.npcfile).isEqualTo("/maps/world-npc.xml")
        assertThat(map.housefile).isEqualTo("/maps/world-house.xml")
        assertThat(map.zonesfile).isEqualTo("/maps/world-zones.xml")
    }

    @Test
    fun `house tiles register the house and map the tile flags`() {
        val buffer = buildMap {
            node(OtbmConstants.OTBM_TILE_AREA.toInt()) {
                writeShortLE(1000)
                writeShortLE(1000)
                writeByte(7)
                node(14) {
                    writeByte(1)
                    writeByte(2)
                    writeIntLE(5)
                    writeByte(tileFlagsAttr)
                    writeIntLE((1 shl 0) or (1 shl 3))
                    writeByte(OtbmConstants.OTBM_ATTR_ITEM.toInt())
                    writeShortLE(106)
                }
            }
        }

        val map = OtbmMapLoader.load(buffer)
        val position = Position(1001, 1002, 7)

        assertThat(map.houses.getHouse(5)).isNotNull
        assertThat(map.houses.getHouse(5)?.tiles).containsExactly(
            org.opentibia.canary.map.house.HouseTile(position, 5),
        )
        val tile = map.getTile(position)
        assertThat(tile?.houseId).isEqualTo(5)
        assertThat(tile?.flags?.and(TileFlags.TILESTATE_PROTECTIONZONE.value)).isEqualTo(TileFlags.TILESTATE_PROTECTIONZONE.value)
        assertThat(tile?.flags?.and(TileFlags.TILESTATE_NOLOGOUT.value)).isEqualTo(TileFlags.TILESTATE_NOLOGOUT.value)
        assertThat(tile?.ground?.id).isEqualTo(106)
    }

    @Test
    fun `item child nodes load their id and attributes`() {
        val itemTypes = ItemTypeRegistry().apply {
            register(ItemType(2148, "coin", isStackable = true, isMovable = true))
        }
        val buffer = buildMap {
            node(OtbmConstants.OTBM_TILE_AREA.toInt()) {
                writeShortLE(50)
                writeShortLE(60)
                writeByte(7)
                node(OtbmConstants.OTBM_TILE.toInt()) {
                    writeByte(0)
                    writeByte(0)
                    node(OtbmConstants.OTBM_ITEM.toInt()) {
                        writeShortLE(2148)
                        writeByte(15)
                        writeByte(5)
                    }
                }
            }
        }

        val map = OtbmMapLoader.load(buffer, itemTypes = itemTypes)
        val tile = map.getTile(Position(50, 60, 7))

        assertThat(tile?.items).hasSize(1)
        assertThat(tile?.items?.first()?.id).isEqualTo(2148)
        assertThat(tile?.items?.first()?.count).isEqualTo(5)
    }

    @Test
    fun `zone child nodes attach the position to every listed zone`() {
        val buffer = buildMap {
            node(OtbmConstants.OTBM_TILE_AREA.toInt()) {
                writeShortLE(10)
                writeShortLE(20)
                writeByte(7)
                node(OtbmConstants.OTBM_TILE.toInt()) {
                    writeByte(0)
                    writeByte(0)
                    node(19) {
                        writeShortLE(2)
                        writeShortLE(10)
                        writeShortLE(11)
                    }
                }
            }
        }

        val map = OtbmMapLoader.load(buffer)
        val position = Position(10, 20, 7)

        assertThat(map.zones.getZone(10)?.contains(position)).isTrue()
        assertThat(map.zones.getZone(11)?.contains(position)).isTrue()
    }

    @Test
    fun `towns node resolves id, name and temple position`() {
        val buffer = Unpooled.buffer()
        buffer.writeInt(0)
        buffer.writeByte(OtbmConstants.NODE_START.toInt())
        buffer.writeByte(OtbmConstants.OTBM_ROOT.toInt())
        buffer.writeIntLE(2)
        buffer.node(OtbmConstants.OTBM_MAP_DATA.toInt()) {}
        buffer.node(OtbmConstants.OTBM_TOWNS.toInt()) {
            node(OtbmConstants.OTBM_TOWN.toInt()) {
                writeIntLE(1)
                writeString("Thais")
                writeShortLE(32369)
                writeShortLE(32241)
                writeByte(7)
            }
            node(OtbmConstants.OTBM_TOWN.toInt()) {
                writeIntLE(8)
                writeString("Dawnport")
                writeShortLE(32097)
                writeShortLE(32215)
                writeByte(7)
            }
        }
        buffer.writeByte(OtbmConstants.NODE_END.toInt())

        val map = OtbmMapLoader.load(buffer, "world/canary.otbm")

        val thais = map.towns.getTown(1)
        assertThat(thais?.getName()).isEqualTo("Thais")
        assertThat(thais?.getTemplePosition()).isEqualTo(Position(32369, 32241, 7))
        assertThat(map.towns.getTown(8)?.getName()).isEqualTo("Dawnport")
        assertThat(map.towns.getTown("thais")).isSameAs(thais)
    }
}
