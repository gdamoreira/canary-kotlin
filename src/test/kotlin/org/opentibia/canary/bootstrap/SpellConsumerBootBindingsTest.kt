package org.opentibia.canary.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.database.DatabaseConfig
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.bindings.MonsterSpellFunctions
import org.opentibia.canary.lua.registry.LuaSpell
import org.opentibia.canary.lua.registry.SpellType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.ListenerBoot
import org.opentibia.canary.network.protocol.InMemoryProtocolSink
import org.opentibia.canary.systems.imbuement.Imbuements
import javax.sql.DataSource

/**
 * Wave 67 D4 production-binding tests over the boot root ([ServerBootstrap]):
 *
 *  - `WorldLuaOperations.attachCombat` must be called once the world map
 *    exists, re-binding the Lua combat entry points and the monster-spell
 *    `canThrowSpell` gate over the booted `g_game().map`.
 *  - the shop-owner composition: `Player::getShopOwner`/`closeShopWindow`
 *    (player.cpp:2933-2944, 6123-6125) must resolve the owner recorded by the
 *    production NPC shop path (`npc:openShopWindow` Lua scripts) so the `0x79`
 *    `Game::playerLookInShop` route works.
 */
class SpellConsumerBootBindingsTest {

    private class RecordingDatabaseBoot(private val name: String) : DatabaseBoot {
        override fun connect(config: DatabaseConfig?): DataSource =
            DatabaseConnectionFactory.createInMemoryDataSource(name)

        override fun migrate(dataSource: DataSource): Int = DatabaseConnectionFactory.migrate(dataSource)

        override fun close(dataSource: DataSource) {
            (dataSource as? AutoCloseable)?.close()
        }
    }

    private class WorldListener(map: GameMap, dispatcher: GameDispatcher) : ListenerBoot {
        override val game: Game = Game(map, dispatcher)

        override fun start() = Unit

        override fun stop() = Unit
    }

    private class BootedRoot(
        val bootstrap: ServerBootstrap,
        val environment: LuaEnvironment,
        val listener: WorldListener,
    )

    private fun boot(): BootedRoot {
        val environment = LuaEnvironment().apply { initState() }
        lateinit var listener: WorldListener
        val bootstrap = ServerBootstrap(
            configBoot = ConfigBoot { _, _ -> ServerConfig(loginPort = 0, gamePort = 0) },
            databaseBoot = RecordingDatabaseBoot("d4_boot_${System.nanoTime()}"),
            mapBoot = MapBoot { _, _, _, _ -> GameMap() },
            scriptingBoot = ScriptingBoot { _, _, _, _ -> environment },
            appearanceBoot = AppearanceBoot { Appearances.load(ByteArray(0)) },
            itemBoot = ItemBoot { _, _ -> Items() },
            imbuementBoot = ImbuementBoot { Imbuements() },
            monsterBoot = MonsterBoot { _, _, _, _ -> Monsters() },
            listenerFactory = ListenerFactory { _, _, map, bootedDispatcher, _, _ ->
                WorldListener(map, bootedDispatcher).also { listener = it }
            },
            dispatcher = GameDispatcher(),
        )
        bootstrap.run()
        return BootedRoot(bootstrap, environment, listener)
    }

    @Test
    fun `boot attaches the Lua combat seams over the world map`() {
        val root = boot()
        try {
            // A blocking tile between caster and target on the booted map: the
            // re-bound `InstantSpell::canThrowSpell` gate (spells.cpp:1299-1310)
            // must fold `Game::canThrowObjectTo` and reject the cast.
            val map = root.listener.game.map
            map.getOrCreateTile(Position(11, 10, 7)).setFlag(TileFlags.TILESTATE_BLOCKPROJECTILE.value)

            val caster = Monster(id = 1L, name = "Rat", maxHealth = 100, health = 100, position = Position(10, 10, 7))
            val blocked = Monster(id = 2L, name = "Hero", maxHealth = 100, health = 100, position = Position(12, 10, 7))
            val clear = Monster(id = 3L, name = "Hero", maxHealth = 100, health = 100, position = Position(10, 11, 7))
            val spell = LuaSpell(name = "Probe", spellType = SpellType.INSTANT, range = -1)

            assertThat(MonsterSpellFunctions.canThrowSpell(caster, blocked, spell)).isFalse()
            assertThat(MonsterSpellFunctions.canThrowSpell(caster, clear, spell)).isTrue()
        } finally {
            root.bootstrap.shutdown()
        }
    }

    @Test
    fun `boot composes the shop owner so the look-in-shop route resolves`() {
        val root = boot()
        try {
            val game = root.listener.game
            val world = root.environment.worldOperations
            assertThat(world).isNotNull

            val customer = Player(id = 7L, name = "Hero", maxHealth = 100, health = 100, position = Position(100, 100, 7))
            val sink = InMemoryProtocolSink(customer)
            customer.protocolSink = sink
            val merchant = Npc(id = 50L, name = "Merchant", position = Position(101, 100, 7))

            world!!.npc.openShopWindow(merchant, customer)

            // `Player::openShopWindow` records the owner and sends the shop frame.
            assertThat(game.getShopOwner(customer)).isSameAs(merchant)
            assertThat(sink.frames).isNotEmpty()

            game.closeShopWindow(customer)

            assertThat(game.getShopOwner(customer)).isNull()
        } finally {
            root.bootstrap.shutdown()
        }
    }
}
