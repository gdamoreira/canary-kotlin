package org.opentibia.canary.creatures.appearance.outfit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import java.io.File

class OutfitsTest {

    private fun realXml(): String = File("data/XML/outfits.xml").readText()

    private fun loaded(): Outfits = Outfits().apply {
        assertThat(loadFromXml(realXml())).isTrue()
    }

    private fun player(sex: PlayerSex_t): OutfitPlayer = OutfitPlayer { sex }

    @Test
    fun `loads every outfit from the real data XML grouped by sex`() {
        val outfits = loaded()

        assertThat(outfits.getOutfits(PlayerSex_t.FEMALE)).hasSize(127)
        assertThat(outfits.getOutfits(PlayerSex_t.MALE)).hasSize(125)
    }

    @Test
    fun `parses canary outfit fields and attribute defaults`() {
        val outfits = loaded()

        val citizen = outfits.getOutfitByName(PlayerSex_t.FEMALE, "Citizen")!!
        assertThat(citizen.lookType).isEqualTo(136)
        assertThat(citizen.premium).isFalse()
        assertThat(citizen.unlocked).isTrue()
        assertThat(citizen.from).isEmpty()

        val pirate = outfits.getOutfitByName(PlayerSex_t.FEMALE, "Pirate")!!
        assertThat(pirate.lookType).isEqualTo(155)
        assertThat(pirate.premium).isTrue()
        assertThat(pirate.unlocked).isFalse()
        assertThat(pirate.from).isEqualTo("quest")

        val maleCitizen = outfits.getOutfitByName(PlayerSex_t.MALE, "Citizen")!!
        assertThat(maleCitizen.lookType).isEqualTo(128)

        val martialArtist = outfits.getOutfitByName(PlayerSex_t.MALE, "Martial Artist")!!
        assertThat(martialArtist.lookType).isEqualTo(1837)
        assertThat(martialArtist.from).isEqualTo("store")
    }

    @Test
    fun `keeps outfits grouped by type rather than document position`() {
        val outfits = loaded()

        val female = outfits.getOutfits(PlayerSex_t.FEMALE)
        assertThat(female.last().name).isEqualTo("Illuminator")
        assertThat(female.last().lookType).isEqualTo(1860)

        val male = outfits.getOutfits(PlayerSex_t.MALE)
        assertThat(male.last().name).isEqualTo("Martial Artist")
    }

    @Test
    fun `resolves outfits by look type for the player sex`() {
        val outfits = loaded()

        assertThat(outfits.getOutfitByLookType(player(PlayerSex_t.FEMALE), 136)?.name).isEqualTo("Citizen")
        assertThat(outfits.getOutfitByLookType(player(PlayerSex_t.MALE), 128)?.name).isEqualTo("Citizen")
        assertThat(outfits.getOutfitByLookType(player(PlayerSex_t.MALE), 136)).isNull()
        assertThat(outfits.getOutfitByLookType(player(PlayerSex_t.FEMALE), 128)).isNull()
        assertThat(outfits.getOutfitByLookType(player(PlayerSex_t.FEMALE), 65535)).isNull()
        assertThat(outfits.getOutfitByLookType(null, 136)).isNull()
    }

    @Test
    fun `resolves the opposite sex outfit when requested`() {
        val outfits = loaded()

        val oppositeForMale = outfits.getOutfitByLookType(player(PlayerSex_t.MALE), 136, isOppositeOutfit = true)
        assertThat(oppositeForMale?.name).isEqualTo("Citizen")
        assertThat(oppositeForMale?.lookType).isEqualTo(136)

        val oppositeForFemale = outfits.getOutfitByLookType(player(PlayerSex_t.FEMALE), 128, isOppositeOutfit = true)
        assertThat(oppositeForFemale?.name).isEqualTo("Citizen")
        assertThat(oppositeForFemale?.lookType).isEqualTo(128)

        assertThat(outfits.getOutfitByLookType(player(PlayerSex_t.MALE), 65535, isOppositeOutfit = true)).isNull()
    }

    @Test
    fun `resolves outfits by exact name`() {
        val outfits = loaded()

        assertThat(outfits.getOutfitByName(PlayerSex_t.FEMALE, "Citizen")?.lookType).isEqualTo(136)
        assertThat(outfits.getOutfitByName(PlayerSex_t.MALE, "Citizen")?.lookType).isEqualTo(128)
        assertThat(outfits.getOutfitByName(PlayerSex_t.FEMALE, "citizen")).isNull()
        assertThat(outfits.getOutfitByName(PlayerSex_t.FEMALE, "No Such Outfit")).isNull()
    }

    @Test
    fun `skips disabled entries and loads entries without the enabled attribute`() {
        val xml = """
            <outfits>
                <outfit type="0" looktype="10" name="EnabledNo" enabled="no" />
                <outfit type="0" looktype="11" name="EnabledZero" enabled="0" />
                <outfit type="0" looktype="12" name="EnabledEmpty" enabled="" />
                <outfit type="0" looktype="13" name="EnabledYes" enabled="yes" />
                <outfit type="0" looktype="14" name="NoEnabledAttribute" />
            </outfits>
        """.trimIndent()

        val outfits = Outfits()
        assertThat(outfits.loadFromXml(xml)).isTrue()

        assertThat(outfits.getOutfits(PlayerSex_t.FEMALE).map { it.name })
            .containsExactly("EnabledYes", "NoEnabledAttribute")

        val defaults = outfits.getOutfitByName(PlayerSex_t.FEMALE, "NoEnabledAttribute")!!
        assertThat(defaults.premium).isFalse()
        assertThat(defaults.unlocked).isTrue()
        assertThat(defaults.from).isEmpty()
    }

    @Test
    fun `skips entries missing type or looktype and types above PLAYERSEX_LAST`() {
        val xml = """
            <outfits>
                <outfit looktype="10" name="NoType" />
                <outfit type="0" name="NoLookType" />
                <outfit type="2" looktype="11" name="TypeAboveLast" />
                <outfit type="99" looktype="12" name="Type99" />
                <outfit type="0" looktype="13" name="Kept" />
            </outfits>
        """.trimIndent()

        val outfits = Outfits()
        assertThat(outfits.loadFromXml(xml)).isTrue()

        assertThat(outfits.getOutfits(PlayerSex_t.FEMALE).map { it.name }).containsExactly("Kept")
        assertThat(outfits.getOutfits(PlayerSex_t.MALE)).isEmpty()
    }

    @Test
    fun `treats unparsable type and looktype values as zero like pugicast`() {
        val xml = """
            <outfits>
                <outfit type="abc" looktype="20" name="BadType" />
                <outfit type="" looktype="21" name="EmptyType" />
                <outfit type="0" looktype="abc" name="BadLookType" />
                <outfit type="0" looktype="70000" name="OverflowLookType" />
                <outfit type="0" looktype="-1" name="NegativeLookType" />
            </outfits>
        """.trimIndent()

        val outfits = Outfits()
        outfits.loadFromXml(xml)

        val female = outfits.getOutfits(PlayerSex_t.FEMALE)
        assertThat(female.map { it.name }).containsExactly(
            "BadType",
            "EmptyType",
            "BadLookType",
            "OverflowLookType",
            "NegativeLookType"
        )
        assertThat(female.map { it.lookType }).containsExactly(20, 21, 0, 0, 0)
    }

    @Test
    fun `filters unregistered look types only when unsafe scripts are warned`() {
        val xml = """
            <outfits>
                <outfit type="0" looktype="136" name="Registered" />
                <outfit type="0" looktype="999" name="Unregistered" />
                <outfit type="0" looktype="0" name="ZeroIsAlwaysAllowed" />
            </outfits>
        """.trimIndent()

        val guarded = Outfits(warnUnsafeScripts = true, isLookTypeRegistered = { it == 136 })
        guarded.loadFromXml(xml)
        assertThat(guarded.getOutfits(PlayerSex_t.FEMALE).map { it.name })
            .containsExactly("Registered", "ZeroIsAlwaysAllowed")

        val unguarded = Outfits(warnUnsafeScripts = false, isLookTypeRegistered = { false })
        unguarded.loadFromXml(xml)
        assertThat(unguarded.getOutfits(PlayerSex_t.FEMALE).map { it.name })
            .containsExactly("Registered", "Unregistered", "ZeroIsAlwaysAllowed")
    }

    @Test
    fun `reload clears the previous registry before loading`() {
        val outfits = Outfits()
        outfits.loadFromXml("""<outfits><outfit type="0" looktype="1" name="Solo" /></outfits>""")
        assertThat(outfits.getOutfits(PlayerSex_t.FEMALE)).hasSize(1)

        assertThat(outfits.reload(realXml())).isTrue()
        assertThat(outfits.getOutfits(PlayerSex_t.FEMALE)).hasSize(127)
        assertThat(outfits.getOutfits(PlayerSex_t.MALE)).hasSize(125)
        assertThat(outfits.getOutfitByName(PlayerSex_t.FEMALE, "Solo")).isNull()
    }

    @Test
    fun `loads repeatedly appending until reload clears like canary`() {
        val outfits = Outfits()
        outfits.loadFromXml("""<outfits><outfit type="0" looktype="1" name="Solo" /></outfits>""")
        outfits.loadFromXml("""<outfits><outfit type="0" looktype="2" name="Solo2" /></outfits>""")

        assertThat(outfits.getOutfits(PlayerSex_t.FEMALE)).hasSize(2)
    }

    @Test
    fun `builds an outfit window row with the owner addon byte`() {
        val outfits = loaded()
        val player = player(PlayerSex_t.FEMALE)
        val citizen = outfits.getOutfitByLookType(player, 136)!!

        val playerOutfits = PlayerOutfits(outfits, player = player)
        playerOutfits.addOutfit(136, 3)

        val addons = playerOutfits.getOutfitAddons(citizen)!!
        val row = ProtocolOutfit(citizen.name, citizen.lookType, addons)

        assertThat(row.name).isEqualTo("Citizen")
        assertThat(row.lookType).isEqualTo(136)
        assertThat(row.addons).isEqualTo(3)
    }

    @Test
    fun `returns false when the XML cannot be parsed`() {
        val outfits = Outfits()
        assertThat(outfits.loadFromXml("<outfits><outfit")).isFalse()
    }

    @Test
    fun `loads nothing when the document has no outfits root`() {
        val outfits = Outfits()

        assertThat(outfits.loadFromXml("""<?xml version="1.0"?><other/>""")).isTrue()
        assertThat(outfits.getOutfits(PlayerSex_t.FEMALE)).isEmpty()
        assertThat(outfits.getOutfits(PlayerSex_t.MALE)).isEmpty()
    }
}
