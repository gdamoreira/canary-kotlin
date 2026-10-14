package org.opentibia.canary.config

import java.nio.file.Files
import java.nio.file.Path

/**
 * Bundled-path resolution for the installed distribution.
 *
 * The executable home is the folder that contains `bin/`, `lib/` and the
 * bundled `config.lua` and data folders (`data`, `data-canary`,
 * `data-otservbr-global`, ...). It is detected from `APP_HOME` when the caller
 * exports it, otherwise from the running JAR location (the `lib` folder of the home).
 * When neither applies — `./gradlew run`, tests — the working directory keeps
 * the previous behavior.
 *
 * This is port bootstrap infrastructure (not a Canary symbol): upstream Canary
 * resolves these paths against the process working directory.
 */
internal object AppPaths {

    /** Folder of the executable, or `null` when it cannot be determined (development runs). */
    fun executableHome(environment: Map<String, String> = System.getenv()): Path? =
        environment["APP_HOME"]?.takeIf { it.isNotBlank() }?.let { Path.of(it) } ?: jarHome()

    /**
     * `<executableHome>/config.lua` when it exists, else `<workingDirectory>/config.lua`,
     * else `null`.
     */
    fun defaultConfigFile(environment: Map<String, String> = System.getenv()): Path? {
        val home = executableHome(environment) ?: Path.of(System.getProperty("user.dir") ?: ".")
        return home.resolve("config.lua").takeIf { Files.isRegularFile(it) }
    }

    /**
     * Resolves a bundled directory name (`data`, `data-canary`,
     * `data-otservbr-global`, ...) to `<executableHome>/<name>` when that folder
     * exists next to the executable; otherwise [name] is returned unchanged
     * (working-directory relative, or an explicit path).
     */
    fun resolveDirectory(name: String, environment: Map<String, String> = System.getenv()): String {
        val home = executableHome(environment) ?: return name
        val candidate = home.resolve(name)
        return if (Files.isDirectory(candidate)) candidate.toString() else name
    }

    /** The `lib` folder of an installed distribution; `null` for class-directory runs. */
    private fun jarHome(): Path? =
        runCatching {
            val location = AppPaths::class.java.protectionDomain?.codeSource?.location ?: return null
            val path = Path.of(location.toURI())
            if (path.fileName?.toString()?.endsWith(".jar") == true) path.parent?.parent else null
        }.getOrNull()
}
