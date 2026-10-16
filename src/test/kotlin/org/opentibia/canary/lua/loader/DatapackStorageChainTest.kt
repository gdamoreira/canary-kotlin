package org.opentibia.canary.lua.loader

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.lua.bindings.library.ConfigFunctions
import org.opentibia.canary.lua.bindings.library.WorldConfigOperations
import org.opentibia.canary.lua.runtime.LuaEnvironment
import java.nio.file.Path

/**
 * The `data/global.lua` line-3 chain (`dofile(DATA_DIRECTORY .. "/lib/lib.lua")`)
 * loads the datapack storage tree; the quest scripts read deep paths such as
 * `Storage.Quest.U12_20.KilmareshQuest.Set.Ritual`.
 */
class DatapackStorageChainTest {
    @Test
    fun `the global datapack storage tree loads through core lua`() {
        val environment = LuaEnvironment().apply { initState() }
        ConfigFunctions.install(
            environment,
            WorldConfigOperations(
                ConfigManager(ConfigKeys.all.associateWith { it.luaIdentifier }),
            ),
        )
        Scripts(environment).apply {
            setDirectories(Path.of("data-otservbr-global"), Path.of("data"))
        }.loadCore()

        val storage = environment.getGlobal("Storage")
        assertThat(storage.istable()).isTrue()
        assertThat(storage.get("Quest").istable()).isTrue()
        val ritual = storage.get("Quest").get("U12_20").get("KilmareshQuest").get("Set").get("Ritual")
        assertThat(ritual.toint()).isEqualTo(46910)
    }
}
