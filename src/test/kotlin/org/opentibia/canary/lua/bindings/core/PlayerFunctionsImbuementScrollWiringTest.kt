package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.imbuement.FakeImbuementPlayer
import org.opentibia.canary.systems.imbuement.ImbuementEngine
import org.opentibia.canary.systems.imbuement.ImbuementTypes_t
import org.opentibia.canary.systems.imbuement.Imbuements

/**
 * LuaJ transcript tests for the real imbuement-scroll wiring of
 * `PlayerFunctions::luaPlayerApplyImbuementScroll`/`luaPlayerClearAllImbuements`
 * (src/lua/functions/creatures/player/player_functions.cpp:3533-3606) over
 * `Player::applyScrollImbuement`/`Player::clearAllImbuements`
 * (src/creatures/players/player.cpp:2564,2676) and the [ImbuementEngine].
 *
 * `WorldPlayerBatch3Operations` resolves the engine from the injected seam or
 * from the registry assigned to the live [Item] (`Item.imbuementRegistry`) and
 * writes the packed `(duration << 8) | imbuementId` custom attribute
 * (`#define ITEM_IMBUEMENT_SLOT 500`, src/items/item.cpp:33) through the item's
 * own store.
 */
class PlayerFunctionsImbuementScrollWiringTest {

    private val imbuements = Imbuements().apply {
        assertThat(loadFromXml(TEST_XML)).isTrue()
    }

    private val engine = ImbuementEngine(imbuements)

    private val strike = imbuements.imbuementMap.values.single { it.name == "Strike" && it.baseId == 2 }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun hero(): Player = Player(
        id = 42L,
        name = "Scroll Hero",
        maxHealth = 150,
        health = 150,
        position = Position(10, 10, 7),
        capacity = 500.0,
    )

    private fun sword(registry: Imbuements? = null): Item = Item(
        id = 2400,
        type = ItemType(
            id = 2400,
            name = "Sword",
            imbuementSlot = 2,
            imbuementTypes = linkedMapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3),
        ),
    ).also { it.imbuementRegistry = registry }

    private fun LuaEnvironment.bind(player: Player, item: Item, scroll: Item) {
        globals.set("p", userdata.wrap("Player", player))
        globals.set("item", userdata.wrap("Item", item))
        globals.set("scroll", userdata.wrap("Item", scroll))
    }

    @Test
    fun `applyImbuementScroll runs the engine resolved from the item registry`() {
        val environment = env()
        PlayerFunctions.install(environment, StubPlayerOperations(), WorldPlayerBatch3Operations())
        val player = hero()
        val item = sword(imbuements)
        val scroll = Item(id = 51742)
        player.addItem(scroll)
        environment.bind(player, item, scroll)

        environment.loadChunk("applied = p:applyImbuementScroll(item, scroll)")

        assertThat(environment.getGlobal("applied").isnil()).isTrue()
        assertThat(item.getImbuementAttribute(0)).isEqualTo((72000L shl 8) or strike.id.toLong())
        assertThat(item.hasImbuements()).isTrue()
        assertThat(player.getItemCount(51742)).isZero()
    }

    @Test
    fun `clearAllImbuements clears the real item store through the registry engine`() {
        val environment = env()
        PlayerFunctions.install(environment, StubPlayerOperations(), WorldPlayerBatch3Operations())
        val player = hero()
        val item = sword(imbuements)
        val scroll = Item(id = 51742)
        player.addItem(scroll)
        environment.bind(player, item, scroll)

        environment.loadChunk(
            """
            applied = p:applyImbuementScroll(item, scroll)
            cleared = p:clearAllImbuements(item)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("applied").isnil()).isTrue()
        assertThat(environment.getGlobal("cleared").toboolean()).isTrue()
        assertThat(item.getImbuementAttribute(0)).isZero()
        assertThat(item.hasImbuements()).isFalse()
    }

    @Test
    fun `injected engine and player factory drive the Canary primitives`() {
        val environment = env()
        val fake = FakeImbuementPlayer()
        PlayerFunctions.install(
            environment,
            StubPlayerOperations(),
            WorldPlayerBatch3Operations(
                imbuementEngine = engine,
                imbuementPlayerFactory = { fake },
            ),
        )
        val player = hero()
        val item = sword()
        val scroll = Item(id = 51742)
        environment.bind(player, item, scroll)

        environment.loadChunk(
            """
            applied = p:applyImbuementScroll(item, scroll)
            cleared = p:clearAllImbuements(item)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("applied").isnil()).isTrue()
        assertThat(fake.internalRemovedItems).hasSize(1)
        assertThat(fake.internalRemovedItems.single().first.sourceItem).isSameAs(scroll)
        assertThat(fake.internalRemovedItems.single().second).isEqualTo(1)
        assertThat(item.getImbuementAttribute(0)).isZero()
        assertThat(environment.getGlobal("cleared").toboolean()).isTrue()
    }

    @Test
    fun `applyScrollImbuement leaves the slot empty when the scroll cannot be removed`() {
        val environment = env()
        val fake = FakeImbuementPlayer().apply { internalRemoveItemResult = false }
        PlayerFunctions.install(
            environment,
            StubPlayerOperations(),
            WorldPlayerBatch3Operations(
                imbuementEngine = engine,
                imbuementPlayerFactory = { fake },
            ),
        )
        val player = hero()
        val item = sword()
        val scroll = Item(id = 51742)
        environment.bind(player, item, scroll)

        environment.loadChunk("applied = p:applyImbuementScroll(item, scroll)")

        assertThat(environment.getGlobal("applied").isnil()).isTrue()
        assertThat(item.hasImbuements()).isFalse()
    }

    @Test
    fun `unwired seam keeps the documented inert results`() {
        val environment = env()
        PlayerFunctions.install(environment, StubPlayerOperations(), WorldPlayerBatch3Operations())
        val player = hero()
        val item = sword()
        val scroll = Item(id = 51742)
        environment.bind(player, item, scroll)

        environment.loadChunk(
            """
            applied = p:applyImbuementScroll(item, scroll)
            cleared = p:clearAllImbuements(item)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("applied").toboolean()).isTrue()
        assertThat(environment.getGlobal("cleared").toboolean()).isFalse()
        assertThat(item.hasImbuements()).isFalse()
    }

    private companion object {
        const val TEST_XML = """
        <imbuements>
            <base id="1" name="Basic" price="5000" protectionPrice="10000" percent="90" removecost="15000" duration="72000"/>
            <base id="2" name="Intricate" price="30000" protectionPrice="30000" percent="70" removecost="15000" duration="72000"/>
            <base id="3" name="Powerful" price="200000" protectionPrice="50000" percent="50" removecost="15000" duration="72000"/>
            <category id="1" name="Life Leech" agressive="1"/>
            <category id="3" name="Critical Hit" agressive="1"/>
            <imbuement name="Strike" base="2" category="3" iconid="1" premium="1" storage="0">
                <attribute key="description" value="Raises crit hit damage by 25 percent and crit hit chance by 10 percent."/>
                <attribute key="effect" type="skill" value="critical" bonus="2500" chance="1000"/>
                <attribute key="item" value="11444" count="20"/>
                <attribute key="scroll" value="51742"/>
            </imbuement>
        </imbuements>
        """
    }
}
