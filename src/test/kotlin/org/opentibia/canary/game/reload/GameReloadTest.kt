package org.opentibia.canary.game.reload

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ConfigSource
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.npcs.Npcs
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.lua.loader.Scripts
import org.opentibia.canary.lua.runtime.LuaEnvironment

class GameReloadTest {

    private fun newReload(
        configManager: ConfigManager = ConfigManager(emptyMap()),
        itemTypes: ItemTypeRegistry = ItemTypeRegistry(),
        itemTypeLoader: () -> ItemTypeRegistry? = { null },
        monsters: Monsters = Monsters(),
        monsterScriptLoader: () -> Boolean = { true },
        npcs: Npcs = Npcs(),
        npcLoader: () -> Npcs? = { null },
        scripts: Scripts = Scripts(LuaEnvironment()),
        luaLibsLoader: () -> Boolean = { true },
    ): GameReload = GameReload(
        configManager = configManager,
        itemTypes = ReloadableRegistry(itemTypes, itemTypeLoader),
        monsters = monsters,
        monsterScriptLoader = monsterScriptLoader,
        npcs = ReloadableRegistry(npcs, npcLoader),
        luaLibs = ReloadableRegistry(scripts) { if (luaLibsLoader()) scripts else null },
    )

    @Test
    fun `reload enum mirrors Reload_t values exactly`() {
        assertThat(Reload_t.entries.map { it.value }).containsExactlyElementsOf((0..18).toList())
        assertThat(Reload_t.RELOAD_TYPE_NONE.value).isZero()
        assertThat(Reload_t.RELOAD_TYPE_ALL.value).isEqualTo(1)
        assertThat(Reload_t.RELOAD_TYPE_LAST.value).isEqualTo(18)
    }

    @Test
    fun `getReloadNumber returns the underlying enum integer`() {
        val reload = newReload()

        assertThat(reload.getReloadNumber(Reload_t.RELOAD_TYPE_ITEMS)).isEqualTo(14)
        assertThat(reload.getReloadNumber(Reload_t.RELOAD_TYPE_LAST)).isEqualTo(18)
    }

    @Test
    fun `init reports false for reload types whose registries are not ported`() {
        val reload = newReload()

        assertThat(reload.init(Reload_t.RELOAD_TYPE_NONE)).isFalse()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_CHAT)).isFalse()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_EVENTS)).isFalse()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_MODULES)).isFalse()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_OUTFITS)).isFalse()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_MOUNTS)).isFalse()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_FAMILIARS)).isFalse()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_IMBUEMENTS)).isFalse()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_VOCATIONS)).isFalse()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_GROUPS)).isFalse()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_RAIDS)).isFalse()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_LAST)).isFalse()
    }

    @Test
    fun `reloadItems publishes a freshly loaded registry and advances the generation`() {
        val initial = ItemTypeRegistry().apply { register(ItemType(1, "old")) }
        val replacement = ItemTypeRegistry().apply { register(ItemType(2, "new")) }
        val reload = newReload(itemTypes = initial, itemTypeLoader = { replacement })

        val before = reload.itemTypesHandle()
        assertThat(before.generation).isEqualTo(1L)
        assertThat(before.get()?.get(1)?.name).isEqualTo("old")

        assertThat(reload.init(Reload_t.RELOAD_TYPE_ITEMS)).isTrue()

        val after = reload.itemTypesHandle()
        assertThat(after.generation).isEqualTo(2L)
        assertThat(after.get()?.get(2)?.name).isEqualTo("new")
        assertThat(after.get()?.get(1)).isNull()
        assertThat(before.isStale).isTrue()
    }

    @Test
    fun `a handle captured before reload becomes a stale no-op`() {
        val items = ItemTypeRegistry().apply { register(ItemType(1, "old")) }
        val reload = newReload(itemTypes = items, itemTypeLoader = { ItemTypeRegistry() })

        val handle = reload.itemTypesHandle()
        assertThat(handle.isStale).isFalse()
        assertThat(handle.get()).isSameAs(items)
        assertThat(handle.use { it.get(1)?.name }).isEqualTo("old")

        reload.init(Reload_t.RELOAD_TYPE_ITEMS)

        assertThat(handle.isStale).isTrue()
        assertThat(handle.get()).isNull()
        assertThat(handle.use { "must not run" }).isNull()
    }

    @Test
    fun `a current handle resolves its value and runs the lookup block`() {
        val items = ItemTypeRegistry().apply { register(ItemType(1, "one")) }
        val handle = newReload(itemTypes = items).itemTypesHandle()

        assertThat(handle.use { it.get(1)?.name }).isEqualTo("one")
    }

    @Test
    fun `a failed load keeps the previous registry and generation`() {
        val initial = ItemTypeRegistry().apply { register(ItemType(1, "old")) }
        val reload = newReload(itemTypes = initial, itemTypeLoader = { null })

        assertThat(reload.init(Reload_t.RELOAD_TYPE_ITEMS)).isFalse()

        val handle = reload.itemTypesHandle()
        assertThat(handle.generation).isEqualTo(1L)
        assertThat(handle.get()).isSameAs(initial)
    }

    @Test
    fun `a throwing loader keeps the previous registry and does not propagate`() {
        val initial = ItemTypeRegistry()
        val reload = newReload(itemTypes = initial, itemTypeLoader = { error("boom") })

        assertThat(reload.init(Reload_t.RELOAD_TYPE_ITEMS)).isFalse()
        assertThat(reload.itemTypesHandle().get()).isSameAs(initial)
    }

    @Test
    fun `reloading items twice is idempotent and advances one generation per run`() {
        val source = mutableMapOf(1 to "one", 2 to "two")
        val loader = {
            ItemTypeRegistry().apply {
                source.forEach { (id, name) -> register(ItemType(id, name)) }
            }
        }
        val reload = newReload(itemTypes = loader(), itemTypeLoader = loader)

        assertThat(reload.init(Reload_t.RELOAD_TYPE_ITEMS)).isTrue()
        val afterFirst = reload.itemTypesHandle()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_ITEMS)).isTrue()
        val afterSecond = reload.itemTypesHandle()

        assertThat(afterSecond.generation).isEqualTo(3L)
        assertThat(afterFirst.isStale).isTrue()
        assertThat(afterSecond.get()?.get(1)?.name).isEqualTo("one")
        assertThat(afterSecond.get()?.get(2)?.name).isEqualTo("two")
    }

    @Test
    fun `registry generation wraps from the unsigned maximum to one`() {
        val registry = ReloadableRegistry(Unit) { Unit }
        registry.generation = -1L

        assertThat(registry.reload()).isTrue()

        assertThat(registry.generation).isEqualTo(1L)
    }

    @Test
    fun `reloadConfig re-reads the config source and invalidates stale handles`() {
        val source = mutableMapOf("serverName" to "First")
        val configManager = ConfigManager(
            mapOf("serverName" to "serverName"),
            properties = ConfigSource { source.toMap() },
        )
        val reload = newReload(configManager = configManager)

        val handle = reload.configHandle()
        assertThat(handle.get()?.getString("serverName")).isEqualTo("First")

        source["serverName"] = "Second"
        assertThat(reload.init(Reload_t.RELOAD_TYPE_CONFIG)).isTrue()

        assertThat(handle.isStale).isTrue()
        assertThat(handle.get()).isNull()
        assertThat(reload.configHandle().generation).isEqualTo(2L)
        assertThat(reload.configHandle().get()?.getString("serverName")).isEqualTo("Second")
    }

    @Test
    fun `reloadMonsters clears the registry, advances the epoch and re-runs the scripts`() {
        val monsters = Monsters()
        monsters.tryAddMonsterType("Demon", MonsterType("Demon"))
        var scriptRuns = 0
        val reload = newReload(
            monsters = monsters,
            monsterScriptLoader = {
                scriptRuns++
                monsters.tryAddMonsterType("Dragon", MonsterType("Dragon"))
                true
            },
        )

        val handle = reload.monstersHandle()
        assertThat(handle.generation).isEqualTo(1L)
        assertThat(handle.get()?.getMonsterType("Demon")?.name).isEqualTo("Demon")

        assertThat(reload.init(Reload_t.RELOAD_TYPE_MONSTERS)).isTrue()

        assertThat(scriptRuns).isEqualTo(1)
        assertThat(handle.isStale).isTrue()
        assertThat(handle.get()).isNull()
        assertThat(monsters.getReloadEpoch()).isEqualTo(2L)
        assertThat(monsters.getMonsterType("Demon", silent = true)).isNull()
        assertThat(reload.monstersHandle().get()?.getMonsterType("Dragon")?.name).isEqualTo("Dragon")
    }

    @Test
    fun `a failed monster script load still advances the epoch after the clear`() {
        val monsters = Monsters()
        val reload = newReload(monsters = monsters, monsterScriptLoader = { false })
        val handle = reload.monstersHandle()

        assertThat(reload.init(Reload_t.RELOAD_TYPE_MONSTERS)).isFalse()

        assertThat(handle.isStale).isTrue()
        assertThat(monsters.getReloadEpoch()).isEqualTo(2L)
    }

    @Test
    fun `reloadMonsters twice stays idempotent through the injected loader`() {
        val monsters = Monsters()
        var scriptRuns = 0
        val reload = newReload(
            monsters = monsters,
            monsterScriptLoader = {
                scriptRuns++
                monsters.tryAddMonsterType("Dragon", MonsterType("Dragon"))
                true
            },
        )

        assertThat(reload.init(Reload_t.RELOAD_TYPE_MONSTERS)).isTrue()
        assertThat(reload.init(Reload_t.RELOAD_TYPE_MONSTERS)).isTrue()

        assertThat(scriptRuns).isEqualTo(2)
        assertThat(monsters.getReloadEpoch()).isEqualTo(3L)
        assertThat(monsters.getMonsterType("Dragon")?.name).isEqualTo("Dragon")
        assertThat(monsters.getMonsterType("dragon")?.name).isEqualTo("Dragon")
    }

    @Test
    fun `reloadNpcs publishes a freshly loaded registry`() {
        val initial = Npcs()
        val replacement = Npcs().apply { getNpcType("Guide", create = true) }
        val reload = newReload(npcs = initial, npcLoader = { replacement })
        val handle = reload.npcsHandle()

        assertThat(reload.init(Reload_t.RELOAD_TYPE_NPCS)).isTrue()

        assertThat(handle.isStale).isTrue()
        assertThat(reload.npcsHandle().get()?.getNpcType("Guide")?.name).isEqualTo("Guide")
        assertThat(reload.npcsHandle().get()?.getNpcType("Missing")).isNull()
    }

    @Test
    fun `reloadScripts reloads lua libs then cascades to monsters, npcs and items`() {
        val calls = mutableListOf<String>()
        val reload = newReload(
            itemTypeLoader = { calls.add("items"); ItemTypeRegistry() },
            monsterScriptLoader = { calls.add("monsters"); true },
            npcLoader = { calls.add("npcs"); Npcs() },
            luaLibsLoader = { calls.add("libs"); true },
        )

        assertThat(reload.init(Reload_t.RELOAD_TYPE_SCRIPTS)).isTrue()

        assertThat(calls).containsExactly("libs", "monsters", "npcs", "items")
    }

    @Test
    fun `reloadScripts reports success even when a loader fails, mirroring Canary`() {
        val reload = newReload(
            itemTypeLoader = { null },
            monsterScriptLoader = { false },
            npcLoader = { null },
            luaLibsLoader = { false },
        )

        assertThat(reload.init(Reload_t.RELOAD_TYPE_SCRIPTS)).isTrue()
    }

    @Test
    fun `reloadCore reloads the lua libraries and reports the loader result`() {
        var runs = 0
        val reload = newReload(luaLibsLoader = { runs++; true })
        val handle = reload.luaLibsHandle()

        assertThat(reload.init(Reload_t.RELOAD_TYPE_CORE)).isTrue()

        assertThat(runs).isEqualTo(1)
        assertThat(handle.isStale).isTrue()
        assertThat(reload.luaLibsHandle().generation).isEqualTo(2L)
    }

    @Test
    fun `a failed reloadCore keeps the previous lua libs generation`() {
        val reload = newReload(luaLibsLoader = { false })
        val handle = reload.luaLibsHandle()

        assertThat(reload.init(Reload_t.RELOAD_TYPE_CORE)).isFalse()

        assertThat(handle.isStale).isFalse()
        assertThat(handle.get()).isNotNull()
    }

    @Test
    fun `reloadAll runs every ported reload including the script cascade`() {
        var configReads = 0
        val configManager = ConfigManager(
            mapOf("serverName" to "serverName"),
            properties = ConfigSource { configReads++; emptyMap() },
        )
        val calls = mutableListOf<String>()
        val reload = newReload(
            configManager = configManager,
            itemTypeLoader = { calls.add("items"); ItemTypeRegistry() },
            monsterScriptLoader = { calls.add("monsters"); true },
            npcLoader = { calls.add("npcs"); Npcs() },
            luaLibsLoader = { calls.add("libs"); true },
        )

        assertThat(reload.init(Reload_t.RELOAD_TYPE_ALL)).isTrue()

        assertThat(configReads).isEqualTo(2)
        assertThat(calls.count { it == "libs" }).isEqualTo(2)
        assertThat(calls.count { it == "monsters" }).isEqualTo(2)
        assertThat(calls.count { it == "npcs" }).isEqualTo(2)
        assertThat(calls.count { it == "items" }).isEqualTo(2)
    }
}
