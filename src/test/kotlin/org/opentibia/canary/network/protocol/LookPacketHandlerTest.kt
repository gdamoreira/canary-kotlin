package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.items.SLOTP_HEAD
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.readTibiaString
import org.opentibia.canary.spells.RuneSpell
import org.opentibia.canary.spells.Spells
import org.opentibia.canary.systems.imbuement.Imbuements

class LookPacketHandlerTest {

    @Test
    fun `formats item inspection description text`() {
        val swordType = ItemType(id = 2400, name = "magic sword", article = "a", weight = 42.0)
        val sword = Item(id = 2400, count = 1, type = swordType)

        assertThat(DescriptionFormatter.formatItem(sword, 1)).isEqualTo("You see a magic sword.\nIt weighs 42.00 oz.")
    }

    @Test
    fun `formats monster inspection description text`() {
        val monster = Monster(1001, "Dragon", 1000, 1000, position = Position(100, 100, 7))
        val desc = DescriptionFormatter.formatCreature(monster)
        assertThat(desc).contains("You see a Dragon.")
        assertThat(desc).contains("Health: 100%")
    }

    @Test
    fun `parses look packet 0x8C and builds text message packet`() {
        val map = GameMap()
        val pos = Position(100, 100, 7)
        val tile = map.getOrCreateTile(pos)
        val item = Item(id = 2160, count = 10, type = ItemType(id = 2160, name = "Crystal Coin", weight = 0.1, isStackable = true))
        tile.addItem(item)

        val player = Player(1, "Hero", 100, 100, position = pos)

        val payload = Unpooled.buffer()
        payload.writeShortLE(pos.x)
        payload.writeShortLE(pos.y)
        payload.writeByte(pos.z)
        payload.writeShortLE(2160)
        payload.writeByte(0) // stackpos

        val textResponse = LookPacketHandler.handleLook(player, payload, map)
        assertThat(textResponse).isNotNull

        val opcode = textResponse!!.readUnsignedByte().toInt()
        assertThat(opcode).isEqualTo(0xB4) // MESSAGE_TEXT opcode

        val messageClass = textResponse.readUnsignedByte().toInt()
        assertThat(messageClass).isEqualTo(0x13) // MESSAGE_INFO_DESCR

        val messageText = textResponse.readTibiaString()
        assertThat(messageText).isEqualTo("You see 10 Crystal Coins.\nThey weigh 1.00 oz.")
    }

    @Test
    fun `look distance beyond one tile omits the weight line`() {
        val map = GameMap()
        val playerPos = Position(100, 100, 7)
        val itemPos = Position(103, 100, 7)
        val tile = map.getOrCreateTile(itemPos)
        tile.addItem(Item(id = 2160, count = 10, type = ItemType(id = 2160, name = "crystal coin", weight = 0.1, isStackable = true)))

        val player = Player(1, "Hero", 100, 100, position = playerPos)

        val payload = Unpooled.buffer()
        payload.writeShortLE(itemPos.x)
        payload.writeShortLE(itemPos.y)
        payload.writeByte(itemPos.z)
        payload.writeShortLE(2160)
        payload.writeByte(0)

        val textResponse = LookPacketHandler.handleLook(player, payload, map)
        assertThat(textResponse).isNotNull
        textResponse!!.readUnsignedByte()
        textResponse.readUnsignedByte()
        assertThat(textResponse.readTibiaString()).isEqualTo("You see 10 crystal coins.")
    }

    @Test
    fun `a different z adds the fifteen floor look distance`() {
        val map = GameMap()
        val playerPos = Position(100, 100, 7)
        val itemPos = Position(100, 100, 6)
        val tile = map.getOrCreateTile(itemPos)
        tile.addItem(Item(id = 2400, count = 1, type = ItemType(id = 2400, name = "sword", article = "a", weight = 42.0)))

        val player = Player(1, "Hero", 100, 100, position = playerPos)

        val payload = Unpooled.buffer()
        payload.writeShortLE(itemPos.x)
        payload.writeShortLE(itemPos.y)
        payload.writeByte(itemPos.z)
        payload.writeShortLE(2400)
        payload.writeByte(0)

        val textResponse = LookPacketHandler.handleLook(player, payload, map)
        assertThat(textResponse).isNotNull
        textResponse!!.readUnsignedByte()
        textResponse.readUnsignedByte()
        assertThat(textResponse.readTibiaString()).isEqualTo("You see a sword.")
    }

    // --- renderer composition seams (Item::items, g_spells(), g_vocations(), Game::getObjectCategory) ---

    private fun lookPayload(pos: Position, itemId: Int): ByteBuf {
        val payload = Unpooled.buffer()
        payload.writeShortLE(pos.x)
        payload.writeShortLE(pos.y)
        payload.writeByte(pos.z)
        payload.writeShortLE(itemId)
        payload.writeByte(0)
        return payload
    }

    private fun lookedText(response: ByteBuf?): String {
        assertThat(response).isNotNull
        response!!.readUnsignedByte()
        response.readUnsignedByte()
        return response.readTibiaString()
    }

    @Test
    fun `fluid container look names the fluid through the items seam`() {
        val map = GameMap()
        val pos = Position(100, 100, 7)
        map.getOrCreateTile(pos).addItem(
            Item(
                id = 2006,
                count = 7,
                type = ItemType(id = 2006, name = "vial", article = "a", isFluidContainer = true, weight = 1.8),
            ),
        )
        val player = Player(1, "Hero", 100, 100, position = pos)
        val items = Items().apply { register(ItemType(id = 7, name = "blood")) }

        val response = LookPacketHandler.handleLook(player, lookPayload(pos, 2006), map, items = items)

        assertThat(lookedText(response)).isEqualTo("You see a vial of blood.\nIt weighs 1.80 oz.")
    }

    @Test
    fun `fluid container look without the items seam degrades to unknown`() {
        val map = GameMap()
        val pos = Position(100, 100, 7)
        map.getOrCreateTile(pos).addItem(
            Item(
                id = 2006,
                count = 7,
                type = ItemType(id = 2006, name = "vial", article = "a", isFluidContainer = true, weight = 1.8),
            ),
        )
        val player = Player(1, "Hero", 100, 100, position = pos)

        val response = LookPacketHandler.handleLook(player, lookPayload(pos, 2006), map)

        assertThat(lookedText(response)).isEqualTo("You see a vial of unknown.\nIt weighs 1.80 oz.")
    }

    @Test
    fun `rune look renders the vocation gate through the spells and vocations seams`() {
        val map = GameMap()
        val pos = Position(100, 100, 7)
        map.getOrCreateTile(pos).addItem(
            Item(
                id = 3155,
                type = ItemType(
                    id = 3155, name = "adori rune", article = "an", isPickupable = false,
                    type = ItemTypes_t.ITEM_TYPE_RUNE, runeSpellName = "adori", runeLevel = 3, runeMagLevel = 2,
                ),
            ),
        )
        val player = Player(1, "Hero", 100, 100, position = pos)
        val rune = RuneSpell().apply {
            runeId = 3155
            vocSpellMap[3] = false
            vocSpellMap[2] = true
            vocSpellMap[1] = true
        }
        val spells = Spells().apply { registerRuneLuaEvent(rune) }
        val vocations = Vocations().apply {
            loadFromXml(
                """
                <vocations>
                    <vocation id="1" name="Sorcerer"/>
                    <vocation id="2" name="Druid"/>
                    <vocation id="3" name="Paladin"/>
                </vocations>
                """.trimIndent()
            )
        }

        val response = LookPacketHandler.handleLook(
            player, lookPayload(pos, 3155), map,
            spells = spells, vocations = vocations,
        )

        assertThat(lookedText(response)).isEqualTo(
            "You see an adori rune (\"adori\"). It can only be used by sorcerers and druids with level 3 and magic level 2 or higher."
        )
    }

    @Test
    fun `classification look renders the tier effect through the objectCategory seam`() {
        val map = GameMap()
        val pos = Position(100, 100, 7)
        val helmetType = ItemType(
            id = 2457, name = "helmet", article = "a", isPickupable = false,
            upgradeClassification = 4, slotPosition = SLOTP_HEAD,
        )
        map.getOrCreateTile(pos).addItem(Item(id = 2457, type = helmetType).apply { setTier(4) })
        val player = Player(1, "Hero", 100, 100, position = pos)

        val response = LookPacketHandler.handleLook(
            player, lookPayload(pos, 2457), map,
            objectCategory = { ObjectCategory_t.OBJECTCATEGORY_HELMETS },
        )

        assertThat(lookedText(response)).isEqualTo("You see a helmet.\nClassification: 4 Tier: 4 (8.45% Momentum).")
    }

    @Test
    fun `imbuement look renders through the item registry`() {
        val map = GameMap()
        val pos = Position(100, 100, 7)
        val swordType = ItemType(
            id = 2400, name = "sword", article = "a", isPickupable = false, imbuementSlot = 1,
        )
        val imbuements = Imbuements().apply { assertThat(loadFromXml(TEST_IMBUEMENT_XML)).isTrue() }
        val knownImbuementId = imbuements.imbuementMap.keys.single()
        val item = Item(id = 2400, type = swordType)
        item.imbuementRegistry = imbuements
        item.setImbuement(0, knownImbuementId, 18_000)
        map.getOrCreateTile(pos).addItem(item)
        val player = Player(1, "Hero", 100, 100, position = pos)

        val response = LookPacketHandler.handleLook(player, lookPayload(pos, 2400), map)

        assertThat(lookedText(response)).isEqualTo("You see a sword.\nImbuements: (Basic Strike 05:00h).")
    }

    private companion object {
        const val TEST_IMBUEMENT_XML = """
<imbuements>
    <base id="1" name="Basic" price="5000" protectionPrice="10000" percent="90" removecost="15000" duration="72000"/>
    <category id="3" name="Critical Hit" agressive="1"/>
    <imbuement name="Strike" base="1" category="3" iconid="1">
        <attribute key="effect" type="skill" value="critical" bonus="1500" chance="1000"/>
    </imbuement>
</imbuements>
"""
    }
}
