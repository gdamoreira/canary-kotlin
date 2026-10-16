package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager

class ConfigDirectoryProbeTest {
    @Test
    fun `configKeys indices resolve the registered defaults`() {
        val cm = ConfigManager(ConfigKeys.all.associateWith { it.luaIdentifier })
        val ops = WorldConfigOperations(cm)

        val coreIdx = CONFIG_KEY_NAMES.indexOf("CORE_DIRECTORY")
        val dataIdx = CONFIG_KEY_NAMES.indexOf("DATA_DIRECTORY")
        println("CORE_DIRECTORY idx=$coreIdx -> ${ops.getString(coreIdx)}")
        println("DATA_DIRECTORY idx=$dataIdx -> ${ops.getString(dataIdx)}")
        assertThat(ops.getString(coreIdx)).isEqualTo("data")
        assertThat(ops.getString(dataIdx)).isEqualTo("data-otservbr-global")
    }
}
