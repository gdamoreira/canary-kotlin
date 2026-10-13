package org.opentibia.canary.creatures.npcs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.monsters.RespawnPeriod
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.npcshop.ShopBlock

/**
 * Transcripts of `NpcType`/`Npcs` (src/creatures/npcs/npcs.cpp,
 * src/creatures/npcs/npcs.hpp). Day/night state is injected because Canary reads
 * `g_game().gameIsDay()`.
 */
class NpcsRegistryTest {

    @Test
    fun `NpcType constructor seeds name, lower name, type name and description`() {
        val npcType = NpcType("Frodo")

        assertThat(npcType.name).isEqualTo("Frodo")
        assertThat(npcType.getLowerName()).isEqualTo("frodo")
        assertThat(npcType.typeName).isEqualTo("Frodo")
        assertThat(npcType.nameDescription).isEqualTo("Frodo")
    }

    @Test
    fun `NpcType info defaults match the C++ NpcInfo aggregate`() {
        val info = NpcType("Frodo").info

        assertThat(info.currencyId).isEqualTo(NpcInfo.ITEM_GOLD_COIN)
        assertThat(info.baseSpeed).isEqualTo(55)
        assertThat(info.walkInterval).isEqualTo(2000)
        assertThat(info.walkRadius).isEqualTo(10)
        assertThat(info.health).isEqualTo(100)
        assertThat(info.healthMax).isEqualTo(100)
        assertThat(info.speechBubble).isEqualTo(1)
        assertThat(info.eventType).isEqualTo(NpcsEvent.NPCS_EVENT_NONE)
        assertThat(info.playerBuyEvent).isEqualTo(-1)
        assertThat(info.playerSellEvent).isEqualTo(-1)
        assertThat(info.playerLookEvent).isEqualTo(-1)
        assertThat(info.playerCloseChannel).isEqualTo(-1)
        assertThat(info.creatureSayEvent).isEqualTo(-1)
        assertThat(info.shopItemVector).isEmpty()
        assertThat(info.scripts).isEmpty()
    }

    @Test
    fun `canSpawn rejects a night npc during the day unless underground below the surface`() {
        val npcType = NpcType("Dreamer", gameIsDay = { true })
        npcType.info.respawnType.period = RespawnPeriod.RESPAWNPERIOD_NIGHT

        assertThat(npcType.canSpawn(Position(100, 100, 7))).isFalse()
        assertThat(npcType.canSpawn(Position(100, 100, 8))).isFalse()

        npcType.info.respawnType.underground = true
        assertThat(npcType.canSpawn(Position(100, 100, 8))).isTrue()
        assertThat(npcType.canSpawn(Position(100, 100, 7))).isFalse()
    }

    @Test
    fun `canSpawn rejects a day npc during the night unless underground below the surface`() {
        val npcType = NpcType("Sunny", gameIsDay = { false })
        npcType.info.respawnType.period = RespawnPeriod.RESPAWNPERIOD_DAY

        assertThat(npcType.canSpawn(Position(100, 100, 7))).isFalse()

        npcType.info.respawnType.underground = true
        assertThat(npcType.canSpawn(Position(100, 100, 8))).isTrue()
    }

    @Test
    fun `canSpawn ignores day and night for an always period npc anywhere`() {
        val npcType = NpcType("Anywhere", gameIsDay = { false })
        npcType.info.respawnType.period = RespawnPeriod.RESPAWNPERIOD_ALL

        assertThat(npcType.canSpawn(Position(100, 100, 7))).isTrue()
        assertThat(npcType.canSpawn(Position(100, 100, 2))).isTrue()
    }

    @Test
    fun `loadCallback stores the event id in the slot selected by eventType`() {
        val cases = listOf(
            NpcsEvent.NPCS_EVENT_THINK to { t: NpcType -> t.info.thinkEvent },
            NpcsEvent.NPCS_EVENT_APPEAR to { t: NpcType -> t.info.creatureAppearEvent },
            NpcsEvent.NPCS_EVENT_DISAPPEAR to { t: NpcType -> t.info.creatureDisappearEvent },
            NpcsEvent.NPCS_EVENT_MOVE to { t: NpcType -> t.info.creatureMoveEvent },
            NpcsEvent.NPCS_EVENT_SAY to { t: NpcType -> t.info.creatureSayEvent },
            NpcsEvent.NPCS_EVENT_PLAYER_BUY to { t: NpcType -> t.info.playerBuyEvent },
            NpcsEvent.NPCS_EVENT_PLAYER_SELL to { t: NpcType -> t.info.playerSellEvent },
            NpcsEvent.NPCS_EVENT_PLAYER_CHECK_ITEM to { t: NpcType -> t.info.playerLookEvent },
            NpcsEvent.NPCS_EVENT_PLAYER_CLOSE_CHANNEL to { t: NpcType -> t.info.playerCloseChannel },
        )

        for ((event, slot) in cases) {
            val npcType = NpcType(event.name)
            npcType.info.eventType = event

            assertThat(npcType.loadCallback(42, scriptInterface = "script")).isTrue()
            assertThat(slot(npcType)).describedAs("$event slot").isEqualTo(42)
            assertThat(npcType.info.scriptInterface).isEqualTo("script")
        }
    }

    @Test
    fun `loadCallback fails when the script interface reports no event`() {
        val npcType = NpcType("Frodo")
        npcType.info.eventType = NpcsEvent.NPCS_EVENT_SAY

        assertThat(npcType.loadCallback(-1)).isFalse()
        assertThat(npcType.info.creatureSayEvent).isEqualTo(-1)
    }

    @Test
    fun `loadShop ignores a duplicate shop block and appends new ones`() {
        val npcType = NpcType("Frodo")
        val bread = ShopBlock(itemId = 2689, itemName = "bread", itemSubType = 1, itemBuyPrice = 4, itemSellPrice = 2)
        val cheese = ShopBlock(itemId = 2696, itemName = "cheese", itemBuyPrice = 5)

        assertThat(npcType.info.shopItemVector).isEmpty()

        npcType.loadShop(npcType, bread)
        npcType.loadShop(npcType, bread)
        npcType.loadShop(npcType, cheese)

        assertThat(npcType.info.shopItemVector).containsExactly(bread, cheese)
    }

    @Test
    fun `registry lookup is case insensitive and create only builds missing types`() {
        val npcs = Npcs()

        assertThat(npcs.getNpcType("Frodo")).isNull()
        assertThat(npcs.getNpcType("frodo")).isNull()

        val created = npcs.getNpcType("Frodo", create = true)
        assertThat(created).isNotNull
        assertThat(created?.name).isEqualTo("Frodo")

        assertThat(npcs.getNpcType("frodo")).isSameAs(created)
        assertThat(npcs.getNpcType("FRODO", create = true)).isSameAs(created)

        assertThat(npcs.getNpcType("Sam")).isNull()
        val sam = npcs.getNpcType("sam", create = true)
        assertThat(sam).isNotNull
        assertThat(sam?.name).isEqualTo("sam")
        assertThat(sam).isNotSameAs(created)
    }
}
