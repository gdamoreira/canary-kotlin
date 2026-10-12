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
    fun setDirectories(dataDir: Path, coreDir: Path) {
        environment.globals.set("DATA_DIRECTORY", dataDir.toAbsolutePath().toString())
        environment.globals.set("CORE_DIRECTORY", coreDir.toAbsolutePath().toString())
    }

    fun loadFile(path: Path) {
        environment.loadFile(path)
    }

    fun loadScripts(dir: Path) {
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
