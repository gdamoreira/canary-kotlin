package org.opentibia.canary.lua.loader

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.lua.runtime.LuaEnvironment
import java.nio.file.Files
import java.nio.file.Path

/**
 * Datapack libs require modules through `DATA_DIRECTORY .. ".lib.core.quests"`
 * (`data/libs/functions/quests.lua:1`). LuaJ's stock `require` converts every
 * dot in the module name to a slash, which mangles the absolute prefix when the
 * install path contains dots — the shim resolves the directory-prefixed form
 * directly.
 */
class ScriptsRequireTest {

    @TempDir
    lateinit var root: Path

    private fun environment(dataDir: Path, coreDir: Path): LuaEnvironment {
        val environment = LuaEnvironment().apply { initState() }
        Scripts(environment).setDirectories(dataDir, coreDir)
        return environment
    }

    private fun eval(environment: LuaEnvironment, chunk: String) =
        environment.globals.load(chunk, "require-test").call()

    @Test
    fun `directory-prefixed require resolves the dotted module path`() {
        val dataDir = root.resolve("data.with.dots")
        Files.createDirectories(dataDir.resolve("lib/core"))
        Files.writeString(dataDir.resolve("lib/core/quests.lua"), "return { marker = 42 }")

        val environment = environment(dataDir, root.resolve("data.with.dots/core"))
        val module = eval(environment, """return require(DATA_DIRECTORY .. ".lib.core.quests")""")

        assertThat(module.get("marker").toint()).isEqualTo(42)
    }

    @Test
    fun `required modules are cached per module name`() {
        val dataDir = root.resolve("data.with.dots")
        Files.createDirectories(dataDir.resolve("lib/core"))
        Files.writeString(dataDir.resolve("lib/core/cached.lua"), "return { hits = 1 }")

        val environment = environment(dataDir, root.resolve("core"))
        val first = eval(environment, """return require(DATA_DIRECTORY .. ".lib.core.cached")""")
        val second = eval(environment, """return require(DATA_DIRECTORY .. ".lib.core.cached")""")

        assertThat(first).isSameAs(second)
    }

    @Test
    fun `core directory prefixed require also resolves`() {
        val coreDir = root.resolve("core.with.dots")
        Files.createDirectories(coreDir.resolve("lib"))
        Files.writeString(coreDir.resolve("lib/helpers.lua"), "return 'helpers'")

        val environment = environment(root.resolve("data-canary"), coreDir)
        val module = eval(environment, """return require(CORE_DIRECTORY .. ".lib.helpers")""")

        assertThat(module.tojstring()).isEqualTo("helpers")
    }

    @Test
    fun `non-directory requires still delegate to the stock loader`() {
        val dataDir = root.resolve("data")
        Files.createDirectories(dataDir)
        val environment = environment(dataDir, root.resolve("core"))

        val missing = eval(
            environment,
            """local ok, err = pcall(require, "no.such.module")
               return ok""",
        )

        assertThat(missing.toboolean()).isFalse()
    }
}
