package org.opentibia.canary.creatures.appearance.effects

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AttachedEffectsTest {

    private fun realAttachedEffectsXml(): String {
        val candidates = listOf(
            Path.of("data/XML/attachedeffects.xml"),
            Path.of("../data/XML/attachedeffects.xml"),
            Path.of("../../data/XML/attachedeffects.xml")
        )
        val path = candidates.firstOrNull { Files.exists(it) }
            ?: error("data/XML/attachedeffects.xml not found from ${System.getProperty("user.dir")}")
        return Files.readString(path)
    }

    private fun loaded(): AttachedEffects = AttachedEffects().apply {
        assertThat(loadFromXml(realAttachedEffectsXml())).isTrue()
    }

    @Test
    fun `loads every entry from the real data XML with exact counts and values`() {
        val attachedEffects = loaded()

        assertThat(attachedEffects.getAuras()).hasSize(1)
        assertThat(attachedEffects.getShaders()).hasSize(5)
        assertThat(attachedEffects.getEffects()).hasSize(1)
        assertThat(attachedEffects.getWings()).hasSize(2)

        val aura = attachedEffects.getAuraByID(8)
        assertThat(aura).isNotNull
        assertThat(aura!!.id).isEqualTo(8)
        assertThat(aura.name).isEqualTo("8 aura")

        val rainbow = attachedEffects.getShaderByID(1)
        assertThat(rainbow).isNotNull
        assertThat(rainbow!!.name).isEqualTo("Outfit - Rainbow")
        assertThat(attachedEffects.getShaderByID(5)!!.name).isEqualTo("Outfit - Outline")

        val effect = attachedEffects.getEffectByID(7)
        assertThat(effect).isNotNull
        assertThat(effect!!.name).isEqualTo("7 effect")

        assertThat(attachedEffects.getWingByID(2)!!.name).isEqualTo("2 wings")
        assertThat(attachedEffects.getWingByID(11)!!.name).isEqualTo("11 wings")
    }

    @Test
    fun `classifies each XML element into its own typed collection`() {
        val xml = """
            <attachedeffects>
                <aura id="21" name="Glow"/>
                <shader id="22" name="Waves"/>
                <effect id="23" name="Sparks"/>
                <wing id="24" name="Feathers"/>
            </attachedeffects>
        """.trimIndent()

        val attachedEffects = AttachedEffects()
        assertThat(attachedEffects.loadFromXml(xml)).isTrue()

        assertThat(attachedEffects.getAuras()).containsExactly(Aura(21, "Glow"))
        assertThat(attachedEffects.getShaders()).containsExactly(Shader(22, "Waves"))
        assertThat(attachedEffects.getEffects()).containsExactly(Effect(23, "Sparks"))
        assertThat(attachedEffects.getWings()).containsExactly(Wing(24, "Feathers"))

        val entries: List<AttachedEffect> = listOf(
            attachedEffects.getAuraByID(21)!!,
            attachedEffects.getShaderByID(22)!!,
            attachedEffects.getEffectByID(23)!!,
            attachedEffects.getWingByID(24)!!
        )
        assertThat(entries.filterIsInstance<Aura>()).containsExactly(Aura(21, "Glow"))
        assertThat(entries.filterIsInstance<Shader>()).containsExactly(Shader(22, "Waves"))
        assertThat(entries.filterIsInstance<Effect>()).containsExactly(Effect(23, "Sparks"))
        assertThat(entries.filterIsInstance<Wing>()).containsExactly(Wing(24, "Feathers"))
    }

    @Test
    fun `resolves entries by id only inside their own category`() {
        val attachedEffects = loaded()

        assertThat(attachedEffects.getAuraByID(8)!!.name).isEqualTo("8 aura")
        assertThat(attachedEffects.getShaderByID(3)!!.name).isEqualTo("Outfit - Jelly")
        assertThat(attachedEffects.getEffectByID(7)!!.name).isEqualTo("7 effect")
        assertThat(attachedEffects.getWingByID(11)!!.name).isEqualTo("11 wings")

        assertThat(attachedEffects.getAuraByID(1)).isNull()
        assertThat(attachedEffects.getShaderByID(8)).isNull()
        assertThat(attachedEffects.getEffectByID(2)).isNull()
        assertThat(attachedEffects.getWingByID(7)).isNull()
        assertThat(attachedEffects.getAuraByID(255)).isNull()
    }

    @Test
    fun `narrows id lookups to uint8 like the C++ getters`() {
        val attachedEffects = loaded()

        assertThat(attachedEffects.getAuraByID(8 + 256)!!.name).isEqualTo("8 aura")
        assertThat(attachedEffects.getWingByID(11 + 512)!!.name).isEqualTo("11 wings")
    }

    @Test
    fun `narrows shader ids to uint8 and defaults unparseable ids to zero`() {
        val xml = """
            <attachedeffects>
                <shader id="259" name="Wrapped"/>
                <aura id="not-a-number" name="Broken"/>
                <effect id="70000" name="Out of range"/>
                <wing id="" name="Empty"/>
            </attachedeffects>
        """.trimIndent()

        val attachedEffects = AttachedEffects()
        assertThat(attachedEffects.loadFromXml(xml)).isTrue()

        assertThat(attachedEffects.getShaders()).containsExactly(Shader(3, "Wrapped"))
        assertThat(attachedEffects.getAuras()).containsExactly(Aura(0, "Broken"))
        assertThat(attachedEffects.getEffects()).containsExactly(Effect(0, "Out of range"))
        assertThat(attachedEffects.getWings()).containsExactly(Wing(0, "Empty"))
    }

    @Test
    fun `resolves entries by name case-insensitively`() {
        val attachedEffects = loaded()

        assertThat(attachedEffects.getAuraByName("8 AURA")!!.id).isEqualTo(8)
        assertThat(attachedEffects.getShaderByName("outfit - ghost")!!.id).isEqualTo(2)
        assertThat(attachedEffects.getEffectByName("7 EFFECT")!!.id).isEqualTo(7)
        assertThat(attachedEffects.getWingByName("11 Wings")!!.id).isEqualTo(11)

        assertThat(attachedEffects.getAuraByName("8 shader")).isNull()
        assertThat(attachedEffects.getShaderByName("Outfit - Missing")).isNull()
        assertThat(attachedEffects.getEffectByName("8 aura")).isNull()
        assertThat(attachedEffects.getWingByName("2 aura")).isNull()
    }

    @Test
    fun `reload clears the previous registry before loading`() {
        val attachedEffects = AttachedEffects()
        assertThat(
            attachedEffects.loadFromXml(
                """<attachedeffects><aura id="1" name="Temporary"/></attachedeffects>"""
            )
        ).isTrue()
        assertThat(attachedEffects.getAuras()).hasSize(1)

        assertThat(attachedEffects.reload(realAttachedEffectsXml())).isTrue()
        assertThat(attachedEffects.getAuras()).containsExactly(Aura(8, "8 aura"))
        assertThat(attachedEffects.getShaders()).hasSize(5)
        assertThat(attachedEffects.getEffects()).hasSize(1)
        assertThat(attachedEffects.getWings()).hasSize(2)
    }

    @Test
    fun `invalid XML returns false without touching an already loaded registry`() {
        val attachedEffects = loaded()

        assertThat(attachedEffects.loadFromXml("<attachedeffects><aura id=")).isFalse()

        assertThat(attachedEffects.getAuras()).containsExactly(Aura(8, "8 aura"))
        assertThat(attachedEffects.getShaders()).hasSize(5)
        assertThat(attachedEffects.getEffects()).hasSize(1)
        assertThat(attachedEffects.getWings()).hasSize(2)

        val emptied = loaded()
        assertThat(emptied.reload("<attachedeffects><aura id=")).isFalse()
        assertThat(emptied.getAuras()).isEmpty()
        assertThat(emptied.getShaders()).isEmpty()
        assertThat(emptied.getEffects()).isEmpty()
        assertThat(emptied.getWings()).isEmpty()
    }
}
