package org.opentibia.canary.io.xml

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry

class XmlLoaderTest {

    @Test
    fun `parses items XML and decorates registered item types`() {
        val registry = ItemTypeRegistry()
        registry.register(ItemType(id = 2400, name = "Magic Sword"))

        val xml = """
            <items>
                <item id="2400" name="Magic Sword">
                    <attribute key="weight" value="42.0"/>
                    <attribute key="attack" value="48"/>
                    <attribute key="defense" value="35"/>
                </item>
            </items>
        """.trimIndent()

        XmlItemLoader.load(xml, registry)

        val itemType = registry.get(2400)
        assertThat(itemType).isNotNull
        assertThat(itemType?.name).isEqualTo("Magic Sword")
        assertThat(itemType?.weight).isEqualTo(42.0)
    }

    @Test
    fun `parses vocations XML with stat progression per level`() {
        val xml = """
            <vocations>
                <vocation id="1" name="Sorcerer" gainhp="5" gainmana="30" gaincap="10"/>
                <vocation id="4" name="Knight" gainhp="15" gainmana="5" gaincap="25"/>
            </vocations>
        """.trimIndent()

        val vocations = XmlVocationLoader.load(xml)
        assertThat(vocations).hasSize(2)

        val knight = vocations.find { it.id == 4 }
        assertThat(knight).isNotNull
        assertThat(knight?.name).isEqualTo("Knight")
        assertThat(knight?.gainHp).isEqualTo(15)
        assertThat(knight?.gainMana).isEqualTo(5)
        assertThat(knight?.gainCap).isEqualTo(25)
    }
}
