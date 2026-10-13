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

    fun setDirectories(dataDir: Path, coreDir: Path) {
        environment.globals.set("DATA_DIRECTORY", dataDir.toAbsolutePath().toString())
        environment.globals.set("CORE_DIRECTORY", coreDir.toAbsolutePath().toString())
        coreDirectory = coreDir
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

    fun loadScripts(dir: Path) {
        loadLibs()
        if (!Files.isDirectory(dir)) {
            logger.warn { "Scripts directory does not exist: $dir" }
            return
        }
        Files.walk(dir).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".lua") }
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
            try {
                environment.loadFile(Path.of(path))
            } catch (e: Exception) {
                logger.error { "Failed to load Lua lib $path: ${e.message}" }
            }
            return LuaValue.NONE
        }
    }
}
