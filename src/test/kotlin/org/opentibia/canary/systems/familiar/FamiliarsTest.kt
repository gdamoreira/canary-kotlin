package org.opentibia.canary.systems.familiar

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class FamiliarsTest {

    private fun realXml(): String = File("data/XML/familiars.xml").readText()

    @Test
    fun `loads every familiar definition from data XML grouped by vocation`() {
        val familiars = Familiars()

        assertThat(familiars.loadFromXml(realXml())).isTrue()

        assertThat(familiars.getFamiliars(0)).isEmpty()

        assertThat(familiars.getFamiliars(1)).hasSize(2)
        assertThat(familiars.getFamiliars(2)).hasSize(2)
        assertThat(familiars.getFamiliars(3)).hasSize(2)
        assertThat(familiars.getFamiliars(4)).hasSize(2)
        assertThat(familiars.getFamiliars(5)).hasSize(2)
        assertThat(familiars.getFamiliars(6)).hasSize(2)
        assertThat(familiars.getFamiliars(7)).hasSize(2)
        assertThat(familiars.getFamiliars(8)).hasSize(2)
        assertThat(familiars.getFamiliars(9)).hasSize(2)
        assertThat(familiars.getFamiliars(10)).hasSize(2)
    }

    @Test
    fun `parses familiar fields matching canary attribute names`() {
        val familiars = Familiars()
        familiars.loadFromXml(realXml())

        val thundergiant = familiars.getFamiliarByLookType(1, 994)!!
        assertThat(thundergiant.name).isEqualTo("Thundergiant")
        assertThat(thundergiant.lookType).isEqualTo(994)
        assertThat(thundergiant.premium).isTrue()
        assertThat(thundergiant.unlocked).isFalse()
        assertThat(thundergiant.type).isEmpty()

        val bladespark = familiars.getFamiliarByLookType(1, 1367)!!
        assertThat(bladespark.name).isEqualTo("Bladespark")
        assertThat(bladespark.premium).isTrue()
        assertThat(bladespark.unlocked).isFalse()
        assertThat(bladespark.type).isEqualTo("quest")

        val grovebeast = familiars.getFamiliarByLookType(2, 993)!!
        assertThat(grovebeast.name).isEqualTo("Grovebeast")
        assertThat(grovebeast.premium).isFalse()

        val emberwing = familiars.getFamiliarByLookType(3, 992)!!
        assertThat(emberwing.name).isEqualTo("Emberwing")
        assertThat(emberwing.premium).isFalse()

        val skullfrost = familiars.getFamiliarByLookType(4, 991)!!
        assertThat(skullfrost.name).isEqualTo("Skullfrost")
        assertThat(skullfrost.premium).isTrue()

        val omniphant = familiars.getFamiliarByLookType(10, 1818)!!
        assertThat(omniphant.name).isEqualTo("Omniphant")

        val moonhunter = familiars.getFamiliarByLookType(9, 1819)!!
        assertThat(moonhunter.name).isEqualTo("Moonhunter")
        assertThat(moonhunter.type).isEqualTo("quest")
    }

    @Test
    fun `returns null for lookType not defined for the vocation`() {
        val familiars = Familiars()
        familiars.loadFromXml(realXml())

        assertThat(familiars.getFamiliarByLookType(1, 993)).isNull()
        assertThat(familiars.getFamiliarByLookType(0, 994)).isNull()
    }

    @Test
    fun `skips entries missing vocation or lookType and vocations above VOCATION_LAST`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <familiars>
                <familiar vocation="1" lookType="994" name="Thundergiant" premium="yes" unlocked="no" enabled="yes" />
                <familiar lookType="991" name="NoVocation" enabled="yes" />
                <familiar vocation="1" name="NoLookType" enabled="yes" />
                <familiar vocation="99" lookType="1" name="InvalidVocation" enabled="yes" />
            </familiars>
        """.trimIndent()

        val familiars = Familiars()
        assertThat(familiars.loadFromXml(xml)).isTrue()

        assertThat(familiars.getFamiliars(1)).hasSize(1)
        assertThat(familiars.getFamiliars(1)[0].name).isEqualTo("Thundergiant")
    }

    @Test
    fun `parity quirk disabled entries are still loaded because canary enabled check is a no-op`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <familiars>
                <familiar vocation="4" lookType="991" name="Skullfrost" premium="yes" unlocked="no" enabled="yes" />
                <familiar vocation="4" lookType="992" name="Disabled" premium="yes" unlocked="no" enabled="no" />
            </familiars>
        """.trimIndent()

        val familiars = Familiars()
        familiars.loadFromXml(xml)

        assertThat(familiars.getFamiliars(4)).hasSize(2)
        assertThat(familiars.getFamiliarByLookType(4, 992)!!.name).isEqualTo("Disabled")
    }
}
