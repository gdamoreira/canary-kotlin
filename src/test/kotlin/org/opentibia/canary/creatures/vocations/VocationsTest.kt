package org.opentibia.canary.creatures.vocations

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position
import java.nio.file.Files
import java.nio.file.Path

class VocationsTest {

    private fun realXml(): String {
        val candidates = listOf(
            Path.of("data/XML/vocations.xml"),
            Path.of("../data/XML/vocations.xml"),
            Path.of("../../data/XML/vocations.xml"),
        )
        val path = candidates.firstOrNull { Files.exists(it) }
            ?: error("data/XML/vocations.xml not found from ${System.getProperty("user.dir")}")
        return Files.readString(path)
    }

    private fun loaded(): Vocations = Vocations().apply {
        assertThat(loadFromXml(realXml())).isTrue()
    }

    private fun newPlayer() = Player(
        id = 7,
        name = "Vocation Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    @Test
    fun `loads every vocation from the real data XML`() {
        val vocations = loaded()
        assertThat(vocations.getVocations()).hasSize(11)

        assertThat(vocations.getVocation(0)!!.getVocName()).isEqualTo("None")
        assertThat(vocations.getVocation(1)!!.getVocName()).isEqualTo("Sorcerer")
        assertThat(vocations.getVocation(2)!!.getVocName()).isEqualTo("Druid")
        assertThat(vocations.getVocation(3)!!.getVocName()).isEqualTo("Paladin")
        assertThat(vocations.getVocation(4)!!.getVocName()).isEqualTo("Knight")
        assertThat(vocations.getVocation(5)!!.getVocName()).isEqualTo("Master Sorcerer")
        assertThat(vocations.getVocation(6)!!.getVocName()).isEqualTo("Elder Druid")
        assertThat(vocations.getVocation(7)!!.getVocName()).isEqualTo("Royal Paladin")
        assertThat(vocations.getVocation(8)!!.getVocName()).isEqualTo("Elite Knight")
        assertThat(vocations.getVocation(9)!!.getVocName()).isEqualTo("Monk")
        assertThat(vocations.getVocation(10)!!.getVocName()).isEqualTo("Exalted Monk")
    }

    @Test
    fun `parses canary field names including gains and requirements`() {
        val knight = loaded().getVocation(4)!!

        assertThat(knight.getClientId()).isEqualTo(1)
        assertThat(knight.getBaseId()).isEqualTo(4)
        assertThat(knight.getVocDescription()).isEqualTo("a knight")
        assertThat(knight.getMagicShield()).isFalse()
        assertThat(knight.getCapGain()).isEqualTo(25 * 100)
        assertThat(knight.getHPGain()).isEqualTo(15)
        assertThat(knight.getManaGain()).isEqualTo(5)
        assertThat(knight.getBaseSpeed()).isEqualTo(110)
        assertThat(knight.getSoulMax()).isEqualTo(100)
        assertThat(knight.getBaseAttackSpeed()).isEqualTo(2000)
        assertThat(knight.getFromVocation()).isEqualTo(4)
        assertThat(knight.canCombat()).isTrue()

        val masterSorcerer = loaded().getVocation(5)!!
        assertThat(masterSorcerer.getMagicShield()).isTrue()
        assertThat(masterSorcerer.getFromVocation()).isEqualTo(1)
        assertThat(masterSorcerer.getSoulMax()).isEqualTo(200)
        assertThat(masterSorcerer.getAvatarLookType()).isEqualTo(1595)
    }

    @Test
    fun `maps internal ids to client ids`() {
        val vocations = loaded()

        assertThat(vocations.getVocation(0)!!.getClientId()).isEqualTo(0)
        assertThat(vocations.getVocation(1)!!.getClientId()).isEqualTo(3)
        assertThat(vocations.getVocation(2)!!.getClientId()).isEqualTo(4)
        assertThat(vocations.getVocation(3)!!.getClientId()).isEqualTo(2)
        assertThat(vocations.getVocation(4)!!.getClientId()).isEqualTo(1)
        assertThat(vocations.getVocation(5)!!.getClientId()).isEqualTo(13)
        assertThat(vocations.getVocation(6)!!.getClientId()).isEqualTo(14)
        assertThat(vocations.getVocation(7)!!.getClientId()).isEqualTo(12)
        assertThat(vocations.getVocation(8)!!.getClientId()).isEqualTo(11)
        assertThat(vocations.getVocation(9)!!.getClientId()).isEqualTo(5)
        assertThat(vocations.getVocation(10)!!.getClientId()).isEqualTo(15)
    }

    @Test
    fun `getReqSkillTries uses the xml skill multiplier`() {
        val knight = loaded().getVocation(4)!!

        assertThat(knight.getReqSkillTries(Vocation.SKILL_FIST, 10)).isEqualTo(0)
        assertThat(knight.getReqSkillTries(Vocation.SKILL_FIST, 11)).isEqualTo(50)
        assertThat(knight.getReqSkillTries(Vocation.SKILL_FIST, 12)).isEqualTo(55)
        assertThat(knight.getReqSkillTries(Vocation.SKILL_FIST, 13)).isEqualTo(60)

        assertThat(knight.getReqSkillTries(Vocation.SKILL_DISTANCE, 11)).isEqualTo(30)
        assertThat(knight.getReqSkillTries(Vocation.SKILL_DISTANCE, 12)).isEqualTo(41)

        assertThat(knight.getReqSkillTries(Vocation.SKILL_SHIELD, 11)).isEqualTo(100)
        assertThat(knight.getReqSkillTries(Vocation.SKILL_SHIELD, 12)).isEqualTo(110)
    }

    @Test
    fun `getReqMana uses the xml mana multiplier`() {
        val vocations = loaded()

        val knight = vocations.getVocation(4)!!
        assertThat(knight.getReqMana(0)).isEqualTo(0)
        assertThat(knight.getReqMana(1)).isEqualTo(1600)
        assertThat(knight.getReqMana(2)).isEqualTo(4800)
        assertThat(knight.getReqMana(3)).isEqualTo(14400)

        val sorcerer = vocations.getVocation(1)!!
        assertThat(sorcerer.getReqMana(2)).isEqualTo(1760)
        assertThat(sorcerer.getReqMana(3)).isEqualTo(1936)
    }

    @Test
    fun `resolves vocations by id and name`() {
        val vocations = loaded()

        assertThat(vocations.getVocationByName("knight")!!.getId()).isEqualTo(4)
        assertThat(vocations.getVocationByName("KNIGHT")!!.getId()).isEqualTo(4)
        assertThat(vocations.getVocationByName("Master Sorcerer")!!.getId()).isEqualTo(5)
        assertThat(vocations.getVocationId("elite knight")).isEqualTo(8)
        assertThat(vocations.getVocationId("No Such Vocation")).isEqualTo(Vocation.VOCATION_NONE)
        assertThat(vocations.getVocationByName("No Such Vocation")).isNull()
        assertThat(vocations.getVocation(99)).isNull()
    }

    @Test
    fun `getPromotedVocation returns the fromvoc successor`() {
        val vocations = loaded()

        assertThat(vocations.getPromotedVocation(1)).isEqualTo(5)
        assertThat(vocations.getPromotedVocation(2)).isEqualTo(6)
        assertThat(vocations.getPromotedVocation(3)).isEqualTo(7)
        assertThat(vocations.getPromotedVocation(4)).isEqualTo(8)
        assertThat(vocations.getPromotedVocation(9)).isEqualTo(10)
        assertThat(vocations.getPromotedVocation(8)).isEqualTo(Vocation.VOCATION_NONE)
    }

    @Test
    fun `player normalizes the internal id through the registry when available`() {
        val vocations = loaded()
        val player = newPlayer()
        player.vocations = vocations

        player.vocationId = 4 // Knight (internal)
        assertThat(player.getPlayerVocationEnum()).isEqualTo(Vocation.VOCATION_KNIGHT_CIP)

        player.vocationId = 5 // Master Sorcerer (internal)
        assertThat(player.getPlayerVocationEnum()).isEqualTo(Vocation.VOCATION_SORCERER_CIP)

        player.vocationId = 9 // Monk (internal)
        assertThat(player.getPlayerVocationEnum()).isEqualTo(Vocation.VOCATION_MONK_CIP)

        player.vocationId = 0 // None
        assertThat(player.getPlayerVocationEnum()).isEqualTo(Vocation.VOCATION_NONE)

        player.vocationId = 99 // unknown internal id
        assertThat(player.getPlayerVocationEnum()).isEqualTo(Vocation.VOCATION_NONE)
    }

    @Test
    fun `player still passes the raw id through when no registry is wired`() {
        val player = newPlayer()
        player.vocationId = 1

        assertThat(player.getPlayerVocationEnum()).isEqualTo(1)
    }

    @Test
    fun `skips vocation nodes missing an id and tolerates bad documents`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <vocations>
                <vocation clientid="1" name="NoId" />
                <vocation id="4" clientid="1" name="Knight" />
            </vocations>
        """.trimIndent()

        val vocations = Vocations()
        assertThat(vocations.loadFromXml(xml)).isTrue()
        assertThat(vocations.getVocations()).hasSize(1)
        assertThat(vocations.getVocation(4)!!.getVocName()).isEqualTo("Knight")

        assertThat(Vocations().loadFromXml("<vocations><vocation")).isFalse()
        assertThat(Vocations().loadFromXml("<?xml version=\"1.0\"?><other/>")).isFalse()
    }

    @Test
    fun `reload clears the previous registry before loading`() {
        val vocations = Vocations()
        assertThat(vocations.loadFromXml("<vocations><vocation id=\"4\" name=\"Solo\"/></vocations>")).isTrue()
        assertThat(vocations.getVocations()).hasSize(1)

        assertThat(vocations.reload(realXml())).isTrue()
        assertThat(vocations.getVocations()).hasSize(11)
        assertThat(vocations.getVocation(4)!!.getVocName()).isEqualTo("Knight")
    }
}
