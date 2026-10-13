package org.opentibia.canary.game.reload

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.npcs.Npcs
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.lua.loader.Scripts

private val logger = KotlinLogging.logger {}

/**
 * First generation of a reloadable registry. Mirrors `Monsters::reloadEpoch`
 * (`src/creatures/monsters/monsters.hpp:266`), which starts at `1` and is reset to
 * `1` after wrapping.
 */
internal const val INITIAL_GENERATION: Long = 1L

/**
 * `enum class Reload_t : uint8_t` (`src/game/functions/game_reload.hpp:12-34`).
 *
 * Values are preserved exactly so [GameReload.getReloadNumber] matches Canary's
 * `magic_enum::enum_integer` (`game_reload.cpp:75-77`) and [GameReload.reloadAll]
 * can iterate the declaration order like `magic_enum::enum_values`.
 */
enum class Reload_t(val value: Int) {
    RELOAD_TYPE_NONE(0),
    RELOAD_TYPE_ALL(1),
    RELOAD_TYPE_CHAT(2),
    RELOAD_TYPE_CONFIG(3),
    RELOAD_TYPE_EVENTS(4),
    RELOAD_TYPE_MODULES(5),
    RELOAD_TYPE_OUTFITS(6),
    RELOAD_TYPE_MOUNTS(7),
    RELOAD_TYPE_FAMILIARS(8),
    RELOAD_TYPE_IMBUEMENTS(9),
    RELOAD_TYPE_VOCATIONS(10),
    RELOAD_TYPE_CORE(11),
    RELOAD_TYPE_GROUPS(12),
    RELOAD_TYPE_SCRIPTS(13),
    RELOAD_TYPE_ITEMS(14),
    RELOAD_TYPE_MONSTERS(15),
    RELOAD_TYPE_NPCS(16),
    RELOAD_TYPE_RAIDS(17),
    RELOAD_TYPE_LAST(18),
}

/**
 * Immutable, generation-stamped snapshot of a reloadable registry.
 *
 * The handle stores only the value published at [generation]; it never reads the
 * mutable registry across a reload boundary. Once the owning reload surface advances
 * its generation, [isStale] becomes true, [get] returns `null` and [use] does not run
 * its block. This is the Kotlin counterpart of the `Monsters::reloadEpoch` check in
 * `Monster::setTarget` (`src/creatures/monsters/monster.cpp:1773-1944`): deferred or
 * retained work must re-resolve through the current generation instead of holding the
 * registry (AGENTS.md deferred-callback lifetime safety).
 */
class ReloadHandle<T> internal constructor(
    private val currentGeneration: () -> Long,
    val generation: Long,
    private val value: T,
) {
    /** `true` once the registry has been reloaded after this handle was issued. */
    val isStale: Boolean get() = currentGeneration() != generation

    /** The published registry, or `null` when this handle is stale (a no-op). */
    fun get(): T? = if (isStale) null else value

    /** Runs [block] against the published registry only while the handle is current. */
    fun <R> use(block: (T) -> R): R? = get()?.let(block)
}

/**
 * Runtime-owned registry slot whose contents are replaced wholesale by a reload.
 *
 * A [ReloadableRegistry] mirrors the Canary `reload()` entry points that clear and
 * re-fill a global registry (`Items::reload`, `Npcs::reload`, `Scripts`): [reload]
 * builds a brand-new value through the injected [loader] and publishes it with a fresh
 * generation. The previous value is never mutated, so any [ReloadHandle] issued before
 * the swap is stale and yields no value. A loader that returns `null` (or throws) keeps
 * the previous value and generation, so a failed reload never leaves callers without a
 * registry. The loader may also return the same instance to model a load that only
 * refreshes external state; the generation still advances, so old handles stay stale.
 */
class ReloadableRegistry<T>(
    initial: T,
    private val loader: () -> T?,
) {
    @Volatile
    private var published: T = initial

    /**
     * Generation of the currently published value. `internal` (mirroring
     * `Monsters.reloadEpoch`) so tests can exercise the unsigned wrap.
     */
    @Volatile
    internal var generation: Long = INITIAL_GENERATION

    /** A generation-stamped handle to the currently published value. */
    fun handle(): ReloadHandle<T> = ReloadHandle({ generation }, generation, published)

    /**
     * Publishes a fresh value from [loader] and advances the generation. Returns
     * `false` (keeping the previous value) when the loader fails, throws, or
     * returns `null`.
     */
    fun reload(): Boolean {
        val replacement = try {
            loader()
        } catch (exception: Exception) {
            logger.error(exception) { "Registry reload failed; keeping the previous generation" }
            null
        } ?: return false

        published = replacement
        generation = nextGeneration(generation)
        return true
    }
}

/**
 * `class GameReload` (`src/game/functions/game_reload.hpp:36-68`), the runtime reload
 * surface of `src/game/functions/game_reload.cpp`.
 *
 * The C++ statics become constructor-injected dependencies (AGENTS.md 1.1): the
 * [ConfigManager] and [Monsters] singletons are injected, and the ported item/NPC/Lua
 * registries are passed as [ReloadableRegistry] slots so a reload replaces them with a
 * new generation instead of mutating a captured reference. Since the Kotlin
 * `ItemTypeRegistry`/`Npcs` have no `clear()`, `reloadItems`/`reloadNpcs` publish the
 * registry built by their slot loader (the counterpart of Canary's `clear()` + re-load).
 * [Monsters] keeps Canary's in-place `clear()`, and `Monsters::reloadEpoch` is the
 * generation behind [monstersHandle].
 *
 * Parity gaps: `RELOAD_TYPE_CHAT`, `EVENTS`, `MODULES`, `OUTFITS`, `MOUNTS`,
 * `FAMILIARS`, `IMBUEMENTS`, `VOCATIONS`, `GROUPS` and `RAIDS` have no ported reload
 * counterpart here and return `false` like Canary's `default` branch. `reloadConfig`
 * does not bump the MOTD number (`configmanager.cpp:436-438`, needs `Game`) and
 * `reloadItems` does not invalidate the map navigation epoch (`game_reload.cpp:206`);
 * both are noted at the call sites. `reloadScripts`/`reloadCore` delegate all Lua
 * loading to the injected [luaLibs] loader: Lua stays Lua (AGENTS.md 1.4), so no script
 * logic is ported.
 */
class GameReload(
    private val configManager: ConfigManager,
    private val itemTypes: ReloadableRegistry<ItemTypeRegistry>,
    private val monsters: Monsters,
    private val monsterScriptLoader: () -> Boolean,
    private val npcs: ReloadableRegistry<Npcs>,
    private val luaLibs: ReloadableRegistry<Scripts>,
) {
    @Volatile
    private var configGeneration: Long = INITIAL_GENERATION

    /** `GameReload::init` (`src/game/functions/game_reload.cpp:34-73`). */
    fun init(reloadType: Reload_t): Boolean = when (reloadType) {
        Reload_t.RELOAD_TYPE_ALL -> reloadAll()
        Reload_t.RELOAD_TYPE_CONFIG -> reloadConfig()
        Reload_t.RELOAD_TYPE_CORE -> reloadCore()
        Reload_t.RELOAD_TYPE_SCRIPTS -> reloadScripts()
        Reload_t.RELOAD_TYPE_ITEMS -> reloadItems()
        Reload_t.RELOAD_TYPE_MONSTERS -> reloadMonsters()
        Reload_t.RELOAD_TYPE_NPCS -> reloadNpcs()
        else -> false
    }

    /** `GameReload::getReloadNumber` (`src/game/functions/game_reload.cpp:75-77`). */
    fun getReloadNumber(reloadType: Reload_t): Int = reloadType.value

    /** Generation-stamped handle to the injected [ConfigManager]; stale after a config reload. */
    fun configHandle(): ReloadHandle<ConfigManager> =
        ReloadHandle({ configGeneration }, configGeneration, configManager)

    /** Generation-stamped handle to the currently published item registry. */
    fun itemTypesHandle(): ReloadHandle<ItemTypeRegistry> = itemTypes.handle()

    /** Generation-stamped handle to [Monsters], validated against `Monsters::reloadEpoch`. */
    fun monstersHandle(): ReloadHandle<Monsters> =
        ReloadHandle({ monsters.getReloadEpoch() }, monsters.getReloadEpoch(), monsters)

    /** Generation-stamped handle to the currently published NPC registry. */
    fun npcsHandle(): ReloadHandle<Npcs> = npcs.handle()

    /** Generation-stamped handle to the currently published Lua loader. */
    fun luaLibsHandle(): ReloadHandle<Scripts> = luaLibs.handle()

    /**
     * `GameReload::reloadAll` (`src/game/functions/game_reload.cpp:94-107`): iterates
     * every [Reload_t] value in declaration order except `RELOAD_TYPE_ALL` and returns
     * `true` when at least one reload succeeded.
     */
    private fun reloadAll(): Boolean {
        var anyReloaded = false
        for (reloadType in Reload_t.entries) {
            logger.info { "Reloading: ${reloadType.name}" }
            if (reloadType != Reload_t.RELOAD_TYPE_ALL && init(reloadType)) {
                anyReloaded = true
            }
        }
        return anyReloaded
    }

    /** `GameReload::reloadConfig` (`src/game/functions/game_reload.cpp:115-119`). */
    private fun reloadConfig(): Boolean {
        val result = configManager.reload()
        if (result) {
            configGeneration = nextGeneration(configGeneration)
        }
        // Canary also increments the MOTD number when the hash changed
        // (configmanager.cpp:436-438); `Game` is not wired here yet (parity gap).
        logReloadStatus("Config", result)
        return result
    }

    /**
     * `GameReload::reloadCore` (`src/game/functions/game_reload.cpp:164-177`): loads the
     * core Lua libraries through the injected [luaLibs] loader and only logs failures.
     */
    private fun reloadCore(): Boolean {
        val result = luaLibs.reload()
        if (!result) {
            logReloadStatus("Core", false)
        }
        return result
    }

    /**
     * `GameReload::reloadScripts` (`src/game/functions/game_reload.cpp:185-202`): reloads
     * the Lua libraries/scripts, then cascades to monsters, NPCs and items. Canary
     * ignores the individual load results and always reports success, so this mirrors it.
     */
    private fun reloadScripts(): Boolean {
        luaLibs.reload()
        reloadMonsters()
        reloadNpcs()
        reloadItems()
        logReloadStatus("Scripts", true)
        return true
    }

    /** `GameReload::reloadItems` (`src/game/functions/game_reload.cpp:204-209`). */
    private fun reloadItems(): Boolean {
        val result = itemTypes.reload()
        // Canary invalidates the map navigation epoch here (game_reload.cpp:206);
        // `GameMap` is not wired here yet (parity gap).
        logReloadStatus("Items", result)
        return result
    }

    /**
     * `GameReload::reloadMonsters` (`src/game/functions/game_reload.cpp:211-226`): clears
     * the registry (advancing `Monsters::reloadEpoch`) and re-runs the injected monster
     * script loader. A failed load still leaves the epoch advanced, exactly like the
     * C++ `clear()` sequence.
     */
    private fun reloadMonsters(): Boolean {
        monsters.clear()
        val result = monsterScriptLoader()
        logReloadStatus("Monsters", result)
        return result
    }

    /** `GameReload::reloadNpcs` (`src/game/functions/game_reload.cpp:228-232`). */
    private fun reloadNpcs(): Boolean {
        val result = npcs.reload()
        logReloadStatus("NPCs", result)
        return result
    }
}

/** `logReloadStatus` (`src/game/functions/game_reload.cpp:80-86`). */
private fun logReloadStatus(name: String, result: Boolean) {
    if (result) {
        logger.info { "Reloaded: $name" }
    } else {
        logger.error { "Failed to reload: $name" }
    }
}

/**
 * Unsigned 64-bit wrap of the generation counter, mirroring
 * `Monsters::clear` (`src/creatures/monsters/monsters.cpp:352-357`): `UInt64.MAX_VALUE`
 * is `-1L` in two's complement, so an increment to `0` resets to [INITIAL_GENERATION]
 * and no live handle ever validates against generation `0`.
 */
private fun nextGeneration(generation: Long): Long {
    val next = generation + 1
    return if (next == 0L) INITIAL_GENERATION else next
}
