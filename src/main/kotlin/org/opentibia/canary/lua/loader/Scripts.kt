package org.opentibia.canary.lua.loader

import io.github.oshai.kotlinlogging.KotlinLogging
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
     * best-effort: a missing file is logged and boot continues, mirroring Canary's
     * tolerance for an absent/partial data pack.
     */
    fun loadLibs(coreDir: Path? = coreDirectory) {
        val dir = coreDir ?: return
        val libs = dir.resolve("libs").resolve("libs.lua")
        if (!Files.isRegularFile(libs)) {
            logger.warn { "Canary libs not found, skipping: $libs" }
            return
        }
        try {
            loadFile(libs)
        } catch (e: Exception) {
            logger.error { "Failed to load Canary libs $libs: ${e.message}" }
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
}
