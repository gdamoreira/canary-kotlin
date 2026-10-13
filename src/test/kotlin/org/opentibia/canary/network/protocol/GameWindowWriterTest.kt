package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position

class GameWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun statsFrame() = PlayerStatsFrame(
        health = 1000,
        maxHealth = 1200,
        freeCapacity = 400,
        experience = 5_000_000L,
        level = 42,
        levelPercent = 50.0,
        baseXpGain = 100,
        displayGrindingXpBoost = 50,
        displayXpBoostPercent = 25,
        staminaXpBoost = 100,
        mana = 300,
        maxMana = 500,
        soul = 100,
        staminaMinutes = 2520,
        baseSpeed = 220,
        regenerationTicks = 3000,
        offlineTrainingTime = 7_200_000L,
        xpBoostTime = 60,
        manaShield = 7,
        maxManaShield = 9,
    )

    private fun skillsFrame() = PlayerSkillsFrame(
        magicLevel = 10,
        baseMagicLevel = 9,
        loyaltyMagicLevel = 2,
        magicLevelPercent = 12.5,
        skills = listOf(
            SkillEntry(11, 10, 1, 1.25),
            SkillEntry(12, 11, 2, 2.5),
            SkillEntry(13, 12, 3, 3.75),
            SkillEntry(14, 13, 4, 5.0),
            SkillEntry(15, 14, 5, 6.25),
            SkillEntry(16, 15, 6, 7.5),
            SkillEntry(17, 16, 7, 10.0),
        ),
        capacity = 500,
        baseCapacity = 480,
        flatBonus = 7,
        weaponAttack = WeaponAttack.Fist(attackTotal = 12),
        lifeLeechSkill = 5000,
        manaLeechSkill = 1000,
        baseCriticalChance = 0.05,
        baseCriticalDamage = 0.5,
        criticalHitChanceSkill = 2500,
        criticalHitDamageSkill = 1000,
        onslaught = 0.02,
        defense = 30,
        armor = 25,
        partyMantra = 0,
        mitigation = 2.0,
        dodge = 0.03,
        reflectFlat = 5,
        absorbs = listOf(AbsorbEntry(element = 1, modifier = 0.25)),
        momentum = 0.0,
        transcendence = 0.0,
        amplification = 0.0,
    )

    /**
     * Hand-written expectation for [skillsFrame], transcribed straight from
     * `ProtocolGame::AddPlayerSkills` (protocolgame.cpp:9879-10050). Every
     * `addDouble` block is `precision(4)` + `uint32 LE(value * 10000 + INT32_MAX)`.
     */
    private fun expectedSkillsFrame(weapon: ByteArray, absorbs: ByteArray): ByteArray =
        bytes(
            0xA1,
            0x0A, 0x00, // magic level
            0x09, 0x00, // base magic level
            0x02, 0x00, // loyalty magic level
            0xE2, 0x04, // magic level percent 12.5 * 100
            0x0B, 0x00, 0x0A, 0x00, 0x01, 0x00, 0x7D, 0x00, // fist: 11, 10, 1, 1.25%
            0x0C, 0x00, 0x0B, 0x00, 0x02, 0x00, 0xFA, 0x00, // club: 12, 11, 2, 2.5%
            0x0D, 0x00, 0x0C, 0x00, 0x03, 0x00, 0x77, 0x01, // sword: 13, 12, 3, 3.75%
            0x0E, 0x00, 0x0D, 0x00, 0x04, 0x00, 0xF4, 0x01, // axe: 14, 13, 4, 5%
            0x0F, 0x00, 0x0E, 0x00, 0x05, 0x00, 0x71, 0x02, // distance: 15, 14, 5, 6.25%
            0x10, 0x00, 0x0F, 0x00, 0x06, 0x00, 0xEE, 0x02, // shield: 16, 15, 6, 7.5%
            0x11, 0x00, 0x10, 0x00, 0x07, 0x00, 0xE8, 0x03, // fishing: 17, 16, 7, 10%
            0x00, // 13.10 list
            0xF4, 0x01, 0x00, 0x00, // total capacity 500
            0xE0, 0x01, 0x00, 0x00, // base capacity 480
            0x07, 0x00, // flat damage/healing bonus
        ) + weapon + bytes(
            0x04, 0x87, 0x13, 0x00, 0x80, // life leech 5000/10000 = 0.5
            0x04, 0xE7, 0x03, 0x00, 0x80, // mana leech 1000/10000 = 0.1
            0x04, 0xB7, 0x0B, 0x00, 0x80, // critical chance 0.05 + 0.25 = 0.3
            0x04, 0x6F, 0x17, 0x00, 0x80, // critical damage 0.5 + 0.1 = 0.6
            0x04, 0xC7, 0x00, 0x00, 0x80, // onslaught 0.02
            0x1E, 0x00, // defense 30
            0x19, 0x00, // armor 25
            0x00, 0x00, // party mantra
            0x04, 0xC7, 0x00, 0x00, 0x80, // mitigation 2.0 / 100 = 0.02
            0x04, 0x2B, 0x01, 0x00, 0x80, // dodge 0.03
            0x05, 0x00, // damage reflection
        ) + absorbs + bytes(
            0x04, 0xFF, 0xFF, 0xFF, 0x7F, // momentum 0.0
            0x04, 0xFF, 0xFF, 0xFF, 0x7F, // transcendence 0.0
            0x04, 0xFF, 0xFF, 0xFF, 0x7F, // amplification 0.0
        )

    @Test
    fun `writes player stats 0xA0 in Canary current-profile order`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writePlayerStats(out, statsFrame())

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xA0,
                0xE8, 0x03, 0x00, 0x00, // health 1000
                0xB0, 0x04, 0x00, 0x00, // max health 1200
                0x90, 0x01, 0x00, 0x00, // free capacity 400
                0x40, 0x4B, 0x4C, 0x00, 0x00, 0x00, 0x00, 0x00, // experience 5000000
                0x2A, 0x00, // level 42
                0x88, 0x13, // level percent 50 * 100
                0x64, 0x00, // base xp gain
                0x32, 0x00, // low level bonus
                0x19, 0x00, // xp boost percent
                0x64, 0x00, // stamina xp boost
                0x2C, 0x01, 0x00, 0x00, // mana 300
                0xF4, 0x01, 0x00, 0x00, // max mana 500
                0x64, // soul
                0xD8, 0x09, // stamina minutes 2520
                0xDC, 0x00, // base speed 220
                0x03, 0x00, // regeneration condition ticks / 1000
                0x78, 0x00, // offline training time ms / 60 / 1000
                0x3C, 0x00, // xp boost time
                0x01, // store exp boost enabled
                0x07, 0x00, 0x00, 0x00, // mana shield
                0x09, 0x00, 0x00, 0x00, // max mana shield
            )
        )
    }

    @Test
    fun `stats use the infinite capacity marker and clamp the level percent to 10000`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writePlayerStats(
            out,
            statsFrame().copy(hasInfiniteCapacity = true, levelPercent = 200.0),
        )

        val written = out.toByteArray()
        assertThat(written.copyOfRange(9, 13)).containsExactly(*bytes(0x40, 0x42, 0x0F, 0x00))
        assertThat(written.copyOfRange(23, 25)).containsExactly(*bytes(0x10, 0x27))
    }

    @Test
    fun `writes player skills 0xA1 with fist attack and trailing stat blocks`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writePlayerSkills(out, skillsFrame())

        assertThat(out.toByteArray()).containsExactly(
            *expectedSkillsFrame(
                weapon = bytes(
                    0x0C, 0x00, // fist attack total
                    0x00, // physical element
                    0x04, 0xFF, 0xFF, 0xFF, 0x7F, // converted damage 0.0
                    0x00, // no converted element
                ),
                absorbs = bytes(
                    0x01, // absorb entry count
                    0x01, 0x04, 0xC3, 0x09, 0x00, 0x80, // fire 0.25
                ),
            )
        )
    }

    @Test
    fun `writes player skills weapon converted element and empty absorb list`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writePlayerSkills(
            out,
            skillsFrame().copy(
                weaponAttack = WeaponAttack.Weapon(
                    attackTotal = 42,
                    convertedDamage = 0.75,
                    convertedElement = 1,
                ),
                absorbs = emptyList(),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedSkillsFrame(
                weapon = bytes(
                    0x2A, 0x00, // attack total
                    0x00, // physical element
                    0x04, 0x4B, 0x1D, 0x00, 0x80, // converted damage 0.75
                    0x01, // fire element
                ),
                absorbs = bytes(0x00),
            )
        )
    }

    @Test
    fun `writes player skills wand block`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writePlayerSkills(
            out,
            skillsFrame().copy(
                weaponAttack = WeaponAttack.Wand(maxHitChance = 25, element = 2),
                absorbs = emptyList(),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *expectedSkillsFrame(
                weapon = bytes(
                    0x19, 0x00, // max hit chance
                    0x02, // earth element
                    0x04, 0xFF, 0xFF, 0xFF, 0x7F, // 0.0
                    0x00,
                ),
                absorbs = bytes(0x00),
            )
        )
    }

    @Test
    fun `writes inventory item 0x78 and empty slot 0x79`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writeInventoryItem(out, Slots_t.CONST_SLOT_HEAD, Item(id = 1000))
        GameWindowWriter.writeInventoryItem(out, Slots_t.CONST_SLOT_RIGHT, null)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x78, 0x01, 0xE8, 0x03, // head slot item 1000
                0x79, 0x05, // right slot empty
            )
        )
    }

    @Test
    fun `writes stackable and fluid item fields`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writeItem(
            out,
            Item(id = 2160, count = 100, type = ItemType(id = 2160, name = "torch", isStackable = true)),
        )
        val fluid = Item(id = 2006, type = ItemType(id = 2006, name = "vial"))
        fluid.setAttribute(ItemAttribute.FLUIDTYPE, 5L)
        GameWindowWriter.writeItem(out, fluid, ItemWireInfo(isFluidContainer = true))

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x70, 0x08, 0x64, // item 2160, count 100
                0xD6, 0x07, 0x05, // item 2006, fluid 5
            )
        )
    }

    @Test
    fun `writes container special category tier timer charge and wrap kit fields`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writeItem(
            out,
            Item(id = 1987, type = ItemType(id = 1987, name = "backpack", isContainer = true)),
            ItemWireInfo(
                isContainer = true,
                containerSpecial = ContainerSpecial.MANAGER,
                lootFlags = 1,
                obtainFlags = 2,
            ),
        )

        val tiered = Item(id = 1000, type = ItemType(id = 1000, name = "tiered", upgradeClassification = 4))
        tiered.setAttribute(ItemAttribute.TIER, 3L)
        GameWindowWriter.writeItem(out, tiered)

        val decay = Item(id = 1000)
        decay.setAttribute(ItemAttribute.DURATION, 5000L)
        GameWindowWriter.writeItem(out, decay, ItemWireInfo(expire = true, decayTime = 5))

        val charged = Item(id = 1000)
        charged.setAttribute(ItemAttribute.CHARGES, 7L)
        GameWindowWriter.writeItem(out, charged, ItemWireInfo(wearOut = true, charges = 7))

        GameWindowWriter.writeItem(out, Item(id = 1000), ItemWireInfo(isWrapKit = true))

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC3, 0x07, // item 1987
                0x09, // manager special category
                0x01, 0x00, 0x00, 0x00, // loot flags
                0x02, 0x00, 0x00, 0x00, // obtain flags
                0xE8, 0x03, 0x03, // item 1000 with tier 3
                0xE8, 0x03, 0x05, 0x00, 0x00, 0x00, 0x01, // item 1000, duration 5s, brand new
                0xE8, 0x03, 0x07, 0x00, 0x00, 0x00, 0x01, // item 1000, 7 charges, brand new
                0xE8, 0x03, 0x00, 0x00, // item 1000 wrap kit
            )
        )
    }

    @Test
    fun `writes inventory ids 0xF5 with counted amounts and drops unencodable amounts`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writeInventoryIds(
            out,
            listOf(
                InventoryIdEntry(itemId = 2160, tier = 0, amount = 5),
                InventoryIdEntry(itemId = 2160, tier = 1, amount = 64),
                InventoryIdEntry(itemId = 2160, tier = 2, amount = 16384),
                InventoryIdEntry(itemId = 9999, tier = 0, amount = 0x40000000L),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xF5,
                0x03, 0x00, // three encodable items
                0x70, 0x08, 0x00, 0x05, // 2160 tier 0 amount 5
                0x70, 0x08, 0x01, 0x40, 0x40, // 2160 tier 1 amount 64
                0x70, 0x08, 0x02, 0x80, 0x00, 0x40, 0x00, // 2160 tier 2 amount 16384
            )
        )
    }

    @Test
    fun `writes normal container 0x6E with items menu bytes`() {
        val container = Container(
            id = 1987,
            capacity = 20,
            type = ItemType(id = 1987, name = "Backpack", isContainer = true),
        )
        container.addItem(Item(id = 100))
        container.addItem(Item(id = 101))

        val out = Unpooled.buffer()
        GameWindowWriter.writeContainer(
            out,
            container,
            ContainerWindowFrame(
                cid = 3,
                name = "Backpack",
                hasParent = false,
                firstIndex = 0,
                isUnlocked = true,
                isMovable = true,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6E, 0x03,
                0xC3, 0x07, 0x00, // container item 1987, no special category
                0x08, 0x00, 0x42, 0x61, 0x63, 0x6B, 0x70, 0x61, 0x63, 0x6B, // "Backpack"
                0x14, // capacity 20
                0x00, // has parent
                0x00, // depot search
                0x01, // unlocked
                0x00, // pagination
                0x02, 0x00, // container size
                0x00, 0x00, // first index
                0x02, // item count
                0x64, 0x00, // item 100
                0x65, 0x00, // item 101
                0x00, 0x00, // no inbox categories
                0x01, // movable
                0x00, // holding player
            )
        )
    }

    @Test
    fun `writes browse field container as item bag and fixed name`() {
        val container = Container(id = 470, capacity = 10, type = ItemType(id = 470, name = "browse field", isContainer = true))

        val out = Unpooled.buffer()
        GameWindowWriter.writeContainer(
            out,
            container,
            ContainerWindowFrame(
                cid = 0,
                name = "ignored",
                isUnlocked = true,
                isMovable = false,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6E, 0x00,
                0x25, 0x0B, 0x00, // item bag 2853 with container byte
                0x0C, 0x00, 0x42, 0x72, 0x6F, 0x77, 0x73, 0x65, 0x20,
                0x46, 0x69, 0x65, 0x6C, 0x64, // "Browse Field"
                0x0A, // capacity 10
                0x00, // has parent
                0x00, // depot search
                0x01, // unlocked
                0x00, // pagination
                0x00, 0x00, // container size
                0x00, 0x00, // first index
                0x00, // item count
                0x00, 0x00, // no inbox categories
                0x00, // not movable
                0x00, // holding player
            )
        )
    }

    @Test
    fun `writes store inbox category block`() {
        val container = Container(
            id = 23396,
            capacity = 30,
            type = ItemType(id = 23396, name = "Store Inbox", isContainer = true),
        )
        container.addItem(Item(id = 100))

        val out = Unpooled.buffer()
        GameWindowWriter.writeContainer(
            out,
            container,
            ContainerWindowFrame(
                cid = 1,
                name = "Store Inbox",
                isUnlocked = true,
                isStoreInbox = true,
                storeInboxCategories = listOf(ContainerCategory.All, ContainerCategory.Ammunition),
                storeInboxCategory = ContainerCategory.Ammunition,
                isMovable = false,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6E, 0x01,
                0x64, 0x5B, 0x00, // item 23396, no special category
                0x0B, 0x00, 0x53, 0x74, 0x6F, 0x72, 0x65, 0x20,
                0x49, 0x6E, 0x62, 0x6F, 0x78, // "Store Inbox"
                0x1E, // capacity 30
                0x00, // has parent
                0x00, // depot search
                0x01, // unlocked
                0x00, // pagination
                0x01, 0x00, // container size
                0x00, 0x00, // first index
                0x01, // item count
                0x64, 0x00, // item 100
                0x01, // selected category Ammunition
                0x02, // categories size
                0x01, 0x0A, 0x00, 0x41, 0x6D, 0x6D, 0x75, 0x6E,
                0x69, 0x74, 0x69, 0x6F, 0x6E, // "Ammunition"
                0x00, // not movable
                0x00, // holding player
            )
        )
    }

    @Test
    fun `writes text message 0xB4 damage heal experience channel and plain variants`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writeTextMessage(
            out,
            TextMessageFrame(
                type = MessageClass.MESSAGE_DAMAGE_DEALT,
                text = "hit",
                position = Position(100, 200, 7),
                primary = MessageValue(value = 10, color = 180),
                secondary = MessageValue(value = 20, color = 181),
            ),
        )
        GameWindowWriter.writeTextMessage(
            out,
            TextMessageFrame(
                type = MessageClass.MESSAGE_HEALED,
                text = "hea",
                position = Position(10, 20, 7),
                primary = MessageValue(value = 99, color = 172),
                secondary = MessageValue(value = 1, color = 1),
            ),
        )
        GameWindowWriter.writeTextMessage(
            out,
            TextMessageFrame(
                type = MessageClass.MESSAGE_EXPERIENCE,
                text = "exp",
                position = Position(1, 2, 3),
                primary = MessageValue(value = 5_000_000_000L, color = 172),
            ),
        )
        GameWindowWriter.writeTextMessage(
            out,
            TextMessageFrame(type = MessageClass.MESSAGE_GUILD, text = "g", channelId = 7),
        )
        GameWindowWriter.writeTextMessage(
            out,
            TextMessageFrame(type = MessageClass.MESSAGE_STATUS, text = "ok"),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xB4, 0x17, // damage dealt
                0x64, 0x00, 0xC8, 0x00, 0x07, // position
                0x0A, 0x00, 0x00, 0x00, 0xB4, // primary 10 orange
                0x14, 0x00, 0x00, 0x00, 0xB5, // secondary 20
                0x03, 0x00, 0x68, 0x69, 0x74, // "hit"
                0xB4, 0x19, // healed
                0x0A, 0x00, 0x14, 0x00, 0x07, // position
                0x63, 0x00, 0x00, 0x00, 0xAC, // primary 99
                0x03, 0x00, 0x68, 0x65, 0x61, // "hea"
                0xB4, 0x1A, // experience
                0x01, 0x00, 0x02, 0x00, 0x03, // position
                0x00, 0xF2, 0x05, 0x2A, 0x01, 0x00, 0x00, 0x00, // uint64 experience
                0xAC, // color
                0x03, 0x00, 0x65, 0x78, 0x70, // "exp"
                0xB4, 0x21, // guild
                0x07, 0x00, // channel id
                0x01, 0x00, 0x67, // "g"
                0xB4, 0x1E, // status
                0x02, 0x00, 0x6F, 0x6B, // "ok"
            )
        )
    }

    @Test
    fun `text message with MESSAGE_NONE is suppressed`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writeTextMessage(
            out,
            TextMessageFrame(type = MessageClass.MESSAGE_NONE, text = "ignored"),
        )

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `writes remove tile thing 0x6C and ignores out-of-range stack positions`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writeRemoveTileThing(out, Position(100, 200, 7), 3)
        GameWindowWriter.writeRemoveTileThing(out, Position(100, 200, 7), 10)

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6C, 0x64, 0x00, 0xC8, 0x00, 0x07, 0x03,
            )
        )
    }

    @Test
    fun `writes known creature 0x6A with outfit light and walkthrough`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writeAddCreature(
            out,
            Position(1000, 1000, 7),
            3,
            CreatureAppearanceFrame(
                id = 5000L,
                name = "Bob",
                health = 80,
                maxHealth = 100,
                direction = Direction.South,
                outfit = Outfit(
                    lookType = 128,
                    lookHead = 1,
                    lookBody = 2,
                    lookLegs = 3,
                    lookFeet = 4,
                    lookAddons = 5,
                ),
                light = LightInfo(level = 7, color = 215),
                stepSpeed = 200,
                creatureType = CreatureType.PLAYER,
                known = true,
                vocationClientId = 4,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6A,
                0xE8, 0x03, 0xE8, 0x03, 0x07, // position
                0x03, // stack position
                0x62, 0x00, // known marker
                0x88, 0x13, 0x00, 0x00, // id 5000
                0x50, // health 80%
                0x02, // south
                0x80, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, // outfit
                0x00, 0x00, // no mount
                0x07, 0xD7, // light level/color
                0xC8, 0x00, // step speed
                0x00, // no icons
                0x00, // skull
                0x00, // party shield
                0x00, // player creature type
                0x04, // vocation client id
                0x00, // speech bubble
                0xFF, // unmarked
                0x00, // inspection type
                0x01, // not walkthrough
            )
        )
    }

    @Test
    fun `writes unknown hidden summon 0x6A with icons summon master and access light`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writeAddCreature(
            out,
            Position(50, 60, 7),
            4,
            CreatureAppearanceFrame(
                id = 5001L,
                name = "Demon",
                health = 100,
                maxHealth = 100,
                direction = Direction.North,
                outfit = Outfit(lookType = 0, lookTypeEx = 99),
                light = LightInfo(level = 3, color = 100),
                stepSpeed = 180,
                creatureType = CreatureType.MONSTER,
                known = false,
                removedKnown = 77L,
                healthHidden = true,
                masterId = 42L,
                masterIsPlayer = true,
                guildEmblem = 2,
                speechBubble = 7,
                canWalkthrough = false,
                accessPlayer = true,
                icons = listOf(
                    CreatureIcon(serialize = 1, category = 2, count = 3),
                    CreatureIcon(serialize = 5, category = 6, count = 7),
                    CreatureIcon(serialize = 8, category = 9, count = 10),
                    CreatureIcon(serialize = 11, category = 12, count = 13),
                ),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6A,
                0x32, 0x00, 0x3C, 0x00, 0x07, // position
                0x04, // stack position
                0x61, 0x00, // unknown marker
                0x4D, 0x00, 0x00, 0x00, // removed known id 77
                0x89, 0x13, 0x00, 0x00, // id 5001
                0x05, // hidden creature type byte
                0x00, 0x00, // hidden name
                0x00, // hidden health
                0x00, // north
                0x00, 0x00, 0x63, 0x00, 0x00, 0x00, // no look type, look type ex 99, no mount
                0xFF, 0x64, // access player light 255, color 100
                0xB4, 0x00, // step speed
                0x03, // client icon limit
                0x01, 0x02, 0x03, 0x00,
                0x05, 0x06, 0x07, 0x00,
                0x08, 0x09, 0x0A, 0x00,
                0x00, // skull
                0x00, // party shield
                0x02, // guild emblem
                0x05, // hidden type byte wins over the summon promotion
                0x2A, 0x00, 0x00, 0x00, // master id
                0x07, // speech bubble
                0xFF, // unmarked
                0x00, // inspection type
                0x01, // not walkthrough
            )
        )
    }

    @Test
    fun `invisible creature is rendered with the default outfit`() {
        val out = Unpooled.buffer()

        GameWindowWriter.writeAddCreature(
            out,
            Position(10, 10, 7),
            0,
            CreatureAppearanceFrame(
                id = 7L,
                name = "Ghost",
                health = 1,
                maxHealth = 1,
                direction = Direction.North,
                outfit = Outfit(lookType = 128, lookHead = 1),
                isInvisibleOrGhost = true,
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0x6A,
                0x0A, 0x00, 0x0A, 0x00, 0x07, // position
                0x00, // stack position
                0x61, 0x00, // unknown marker
                0x00, 0x00, 0x00, 0x00, // no removed known id
                0x07, 0x00, 0x00, 0x00, // id 7
                0x00, // player creature type byte
                0x05, 0x00, 0x47, 0x68, 0x6F, 0x73, 0x74, // "Ghost"
                0x64, // full health
                0x00, // north
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // default outfit
                0x00, 0xD7, // light level 0, color 215
                0x00, 0x00, // step speed
                0x00, // no icons
                0x00, // skull
                0x00, // party shield
                0x00, // guild emblem
                0x00, // player creature type
                0x00, // vocation client id
                0x00, // speech bubble
                0xFF, // unmarked
                0x00, // inspection type
                0x01, // not walkthrough
            )
        )
    }
}
