package org.opentibia.canary.io.pipeline

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class AssetManagerTest {

    @Test
    fun `loads items, vocations, and map assets in strict dependency order`() {
        val tempDir = File.createTempFile("canary_assets_", "").apply {
            delete()
            mkdirs()
        }

        try {
            // Seed items.xml
            val itemsXml = File(tempDir, "items.xml").apply {
                writeText(
                    """
                    <items>
                        <item id="2160" name="Crystal Coin">
                            <attribute key="weight" value="0.1"/>
                        </item>
                        <item id="2400" name="Magic Sword">
                            <attribute key="weight" value="42.0"/>
                            <attribute key="attack" value="48"/>
                            <attribute key="defense" value="35"/>
                        </item>
                    </items>
                    """.trimIndent()
                )
            }

            // Seed vocations.xml
            val vocationsXml = File(tempDir, "vocations.xml").apply {
                writeText(
                    """
                    <vocations>
                        <vocation id="1" name="Sorcerer" gainhp="5" gainmana="30" gaincap="10"/>
                        <vocation id="4" name="Knight" gainhp="15" gainmana="5" gaincap="25"/>
                    </vocations>
                    """.trimIndent()
                )
            }

            val assetManager = AssetManager()
            val stats = assetManager.loadAssets(
                itemsXmlFile = itemsXml,
                vocationsXmlFile = vocationsXml
            )

            assertThat(stats.itemsCount).isEqualTo(2)
            assertThat(stats.vocationsCount).isEqualTo(2)

            // Verify global registries populated
            val itemType = assetManager.itemRegistry.get(2400)
            assertThat(itemType).isNotNull
            assertThat(itemType?.name).isEqualTo("Magic Sword")
            assertThat(itemType?.weight).isEqualTo(42.0)

            val knight = assetManager.vocations.find { it.id == 4 }
            assertThat(knight).isNotNull
            assertThat(knight?.name).isEqualTo("Knight")
            assertThat(knight?.gainHp).isEqualTo(15)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
