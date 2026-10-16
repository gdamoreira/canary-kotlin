package org.opentibia.canary.lua.loader

import io.github.oshai.kotlinlogging.KotlinLogging
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.opentibia.canary.lua.runtime.LuaEnvironment
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

private val logger = KotlinLogging.logger {}

/**
 * Mirrors Canary's Scripts loader (src/lua/scripts/scripts.cpp): loads the Lua data
 * tree into the shared environment. A failing file is logged and skipped so a broken
 * script never prevents the server from booting.
 */
class Scripts(private val environment: LuaEnvironment) {
    private var coreDirectory: Path? = null
    private var loadedLibs: Path? = null
    private val loadedScriptFiles = mutableSetOf<Path>()

    fun setDirectories(dataDir: Path, coreDir: Path) {
        val resolvedData = dataDir.toAbsolutePath().normalize()
        val resolvedCore = coreDir.toAbsolutePath().normalize()
        environment.globals.set("DATA_DIRECTORY", resolvedData.toString())
        environment.globals.set("CORE_DIRECTORY", resolvedCore.toString())
        coreDirectory = coreDir
        installDirectoryRequire(resolvedData, resolvedCore)
    }

    /**
     * Datapack libs require modules through `DATA_DIRECTORY .. ".lib.core.quests"`
     * (`data/libs/functions/quests.lua:1`). LuaJ's stock `require` converts every `.` in
     * the module name to a slash, which mangles the absolute prefix whenever the install
     * path contains dots; the shim resolves a directory-prefixed name directly against
     * the file tree and delegates everything else to the stock loader.
     */
    private fun installDirectoryRequire(dataDir: Path, coreDir: Path) {
        val globals = environment.globals
        val original = globals.get("require")
        val directories = listOf(dataDir, coreDir)
        globals.set("require", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val name = args.arg1().tojstring()
                for (directory in directories) {
                    val prefix = directory.toString()
                    if (!name.startsWith(prefix)) {
                        continue
                    }
                    val relative = name.substring(prefix.length).removePrefix(".").replace('.', '/')
                    val file = directory.resolve("$relative.lua")
                    if (Files.isRegularFile(file)) {
                        return loadModule(name, file)
                    }
                }
                return original.invoke(args)
            }
        })
    }

    /** Loads and caches a directory-prefixed module under its full module name. */
    private fun loadModule(name: String, file: Path): LuaValue {
        val globals = environment.globals
        val loaded = globals.get("package").get("loaded")
        val cached = if (loaded.istable()) loaded.get(name) else LuaValue.NIL
        if (!cached.isnil()) {
            return cached
        }
        val result = globals.load(Files.readString(file), "@$file").call()
        val value = if (result.isnil()) LuaValue.TRUE else result
        if (loaded.istable()) {
            loaded.set(name, value)
        }
        return value
    }

    fun loadFile(path: Path) {
        environment.loadFile(path)
    }

    /**
     * Optionally loads Canary's core Lua libraries (`<coreDir>/libs/libs.lua`), which
     * define helpers such as `table.contains` used by the game scripts. The step is
     * best-effort: a missing aggregate is logged and boot continues, and each library
     * file the aggregate `dofile`s is loaded in isolation so a library whose engine
     * bindings have not landed yet is logged and skipped without aborting the rest.
     */
    fun loadLibs(coreDir: Path? = coreDirectory) {
        val dir = coreDir ?: return
        val libs = dir.resolve("libs").resolve("libs.lua").toAbsolutePath().normalize()
        if (loadedLibs == libs) return
        if (!Files.isRegularFile(libs)) {
            logger.warn { "Canary libs not found, skipping: $libs" }
            return
        }
        val previousDofile = environment.globals.get("dofile")
        environment.globals.set("dofile", TolerantDofile(environment))
        try {
            loadFile(libs)
            loadedLibs = libs
        } catch (e: Exception) {
            logger.error { "Failed to load Canary libs $libs: ${e.message}" }
        } finally {
            environment.globals.set("dofile", previousDofile)
        }
    }

    /**
     * Loads Canary's core Lua entry (`<coreDir>/core.lua`,
     * `CanaryServer::loadModules`, src/canary_server.cpp:569-571). `core.lua`
     * `dofile`s `global.lua` (the `SERVER_NAME`/`IsRetroPVP`/`PARTY_PROTECTION`
     * globals), the lib aggregate and `stages.lua`; the tolerant `dofile` keeps
     * a failing file from aborting the rest of the chain.
     */
    fun loadCore(coreDir: Path? = coreDirectory) {
        val dir = coreDir ?: return
        val core = dir.resolve("core.lua").toAbsolutePath().normalize()
        if (loadedLibs == core) return
        if (!Files.isRegularFile(core)) {
            logger.warn { "Canary core.lua not found, skipping: $core" }
            return
        }
        val previousDofile = environment.globals.get("dofile")
        environment.globals.set("dofile", TolerantDofile(environment))
        try {
            loadFile(core)
            loadedLibs = core
        } catch (e: Exception) {
            logger.error { "Failed to load Canary core.lua $core: ${e.message}" }
        } finally {
            environment.globals.set("dofile", previousDofile)
        }
    }

    /**
     * `Npcs::load(true, false)` (src/creatures/npcs/npcs.cpp:106-110):
     * `<coreDir>/npclib/load.lua` chains the NPC library before the NPC
     * scripts register their types.
     */
    fun loadNpcLib(coreDir: Path? = coreDirectory) {
        val dir = coreDir ?: return
        val load = dir.resolve("npclib").resolve("load.lua")
        if (!Files.isRegularFile(load)) {
            logger.warn { "Canary npclib not found, skipping: $load" }
            return
        }
        try {
            loadFile(load)
        } catch (e: Exception) {
            logger.error { "Failed to load npclib $load: ${e.message}" }
        }
    }

    /**
     * `g_modules().loadFromXml()` (`CanaryServer::loadModules`,
     * src/canary_server.cpp:585-587): every `modules.xml` module script loads
     * once (defining globals such as `DailyReward`), after the core scripts and
     * before the datapack scripts.
     *
     * PARITY GAP: the `recvbyte` opcode dispatch of `Modules::executeOnRecvbyte`
     * is not wired; the module packets stay unhandled.
     */
    fun loadModules(coreDir: Path? = coreDirectory) {
        val dir = coreDir ?: return
        // `BaseEvents::loadFromXml` (src/lua/global/baseevents.cpp:23-33): the
        // `<base>/lib/<base>.lua` aggregate loads first.
        val baseLib = dir.resolve("modules").resolve("lib").resolve("modules.lua")
        if (Files.isRegularFile(baseLib)) {
            try {
                loadFile(baseLib)
            } catch (e: Exception) {
                logger.error { "Failed to load module lib $baseLib: ${e.message}" }
            }
        }

        val modulesXml = dir.resolve("modules").resolve("modules.xml")
        if (!Files.isRegularFile(modulesXml)) {
            logger.warn { "Canary modules.xml not found, skipping: $modulesXml" }
            return
        }
        val content = Files.readString(modulesXml)
        val scripts = Regex("script=\"([^\"]+)\"").findAll(content)
            .map { it.groupValues[1] }
            .toSortedSet()
        for (script in scripts) {
            // `scriptFile = "scripts/" + script` (baseevents.cpp:57).
            val file = dir.resolve("modules").resolve("scripts").resolve(script).toAbsolutePath().normalize()
            if (!Files.isRegularFile(file)) {
                logger.warn { "Module script not found, skipping: $file" }
                continue
            }
            try {
                loadFile(file)
            } catch (e: Exception) {
                logger.error { "Failed to load module script $file: ${e.message}" }
            }
        }
    }

    fun loadScripts(dir: Path) {
        loadCore()
        if (!Files.isDirectory(dir)) {
            logger.warn { "Scripts directory does not exist: $dir" }
            return
        }
        Files.walk(dir).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".lua") }
                .map { it.toAbsolutePath().normalize() }
                .filter { loadedScriptFiles.add(it) }
                .sorted()
                .forEach { file ->
                    try {
                        loadFile(file)
                    } catch (e: Exception) {
                        logger.error { "Failed to load script $file: ${e.message}" }
                    }
                }
        }
    }

    /**
     * `dofile` replacement used only while `libs.lua` runs: loads each referenced
     * library file on its own, logging and skipping a file that cannot execute.
     */
    private class TolerantDofile(private val environment: LuaEnvironment) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val path = args.checkjstring(1)
            return try {
                // The standard `dofile` returns the chunk's value; datapack libs
                // capture it (`local catalog = dofile(...)`).
                environment.loadFile(Path.of(path))
            } catch (e: Exception) {
                logger.error { "Failed to load Lua lib $path: ${e.message}" }
                LuaValue.NIL
            }
        }
    }
}
