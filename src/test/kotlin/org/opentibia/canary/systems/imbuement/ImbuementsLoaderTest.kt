package org.opentibia.canary.systems.imbuement

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.combat.condition.Stat
import java.io.File

/**
 * Transcript tests for the `data/XML/imbuements.xml` loader.
 *
 * Expectations are transcribed from Canary's
 * src/creatures/players/imbuements/imbuements.cpp (`loadFromXml`, `getImbuements`,
 * `getIconID`) and from the real `data/XML/imbuements.xml` shipped with the port.
 */
class ImbuementsLoaderTest {

    private val realXml: String = File("data/XML/imbuements.xml").readText()

    private fun realImbuements(): Imbuements = Imbuements().apply {
        assertThat(loadFromXml(realXml)).isTrue()
    }

    private fun Imbuements.byName(name: String, base: Int): Imbuement =
        imbuementMap.values.single { it.name == name && it.baseId == base }

    private fun load(xml: String): Imbuements = Imbuements().apply {
        assertThat(loadFromXml(xml)).isTrue()
    }

    @Test
    fun `loads every base category and imbuement from the real file`() {
        val imbuements = realImbuements()

        assertThat(imbuements.loaded).isTrue()
        assertThat(imbuements.basesImbuement).hasSize(3)
        assertThat(imbuements.categoriesImbuement).hasSize(20)
        assertThat(imbuements.imbuementMap).hasSize(72)
    }

    @Test
    fun `base tiers match the canary xml values`() {
        val imbuements = realImbuements()

        val basic = imbuements.getBaseByID(1)
        assertThat(basic?.name).isEqualTo("Basic")
        assertThat(basic?.price).isEqualTo(5000)
        assertThat(basic?.protectionPrice).isEqualTo(10000)
        assertThat(basic?.removeCost).isEqualTo(15000)
        assertThat(basic?.duration).isEqualTo(72000)
        assertThat(basic?.percent).isEqualTo(90)

        val intricate = imbuements.getBaseByID(2)
        assertThat(intricate?.name).isEqualTo("Intricate")
        assertThat(intricate?.price).isEqualTo(30000)
        assertThat(intricate?.percent).isEqualTo(70)

        val powerful = imbuements.getBaseByID(3)
        assertThat(powerful?.name).isEqualTo("Powerful")
        assertThat(powerful?.price).isEqualTo(200000)
        assertThat(powerful?.protectionPrice).isEqualTo(50000)
        assertThat(powerful?.percent).isEqualTo(50)

        assertThat(imbuements.getBaseByID(99)).isNull()
    }

    @Test
    fun `categories keep their agressive flag`() {
        val imbuements = realImbuements()

        assertThat(imbuements.getCategoryByID(3)?.name).isEqualTo("Critical Hit")
        assertThat(imbuements.getCategoryByID(3)?.agressive).isTrue()
        assertThat(imbuements.getCategoryByID(19)?.name).isEqualTo("Paralysis Deflection")
        assertThat(imbuements.getCategoryByID(19)?.agressive).isFalse()
    }

    @Test
    fun `sequential ids follow the xml document order`() {
        val imbuements = realImbuements()

        assertThat(imbuements.getImbuement(1)).isEqualTo(imbuements.byName("Scorch", 1))
        assertThat(imbuements.getImbuement(16)).isEqualTo(imbuements.byName("Vampirism", 1))
        assertThat(imbuements.getImbuement(22)).isEqualTo(imbuements.byName("Strike", 1))
        assertThat(imbuements.getImbuement(46)).isEqualTo(imbuements.byName("Vibrancy", 1))
        assertThat(imbuements.getImbuement(72)).isEqualTo(imbuements.byName("Featherweight", 3))
        assertThat(imbuements.getImbuement(0)).isNull()
        assertThat(imbuements.getImbuement(999)).isNull()
    }

    @Test
    fun `damage imbuement converts physical damage into the combat element`() {
        val scorch = realImbuements().byName("Scorch", 1)

        assertThat(scorch.combatType).isEqualTo(CombatType.FIREDAMAGE)
        assertThat(scorch.elementDamage).isEqualTo(10)
        assertThat(scorch.items).containsExactly(9636 to 25)
        assertThat(scorch.scrollId).isEqualTo(0)
        assertThat(scorch.description).isEqualTo("Converts 10% of the physical damage to fire damage.")
    }

    @Test
    fun `higher tiers scale the damage conversion and stack more items`() {
        val imbuements = realImbuements()

        val intricate = imbuements.byName("Scorch", 2)
        assertThat(intricate.elementDamage).isEqualTo(25)
        assertThat(intricate.items).containsExactly(9636 to 25, 5920 to 5)

        val powerful = imbuements.byName("Scorch", 3)
        assertThat(powerful.elementDamage).isEqualTo(50)
        assertThat(powerful.storage).isEqualTo(50494)
    }

    @Test
    fun `scroll ids resolve back to their imbuement`() {
        val imbuements = realImbuements()
        val scorch = imbuements.byName("Scorch", 2)

        assertThat(scorch.scrollId).isEqualTo(51739)
        assertThat(imbuements.getImbuementByScrollID(51739)).isEqualTo(scorch)
        assertThat(imbuements.getImbuementByScrollID(1)).isNull()

        val withScroll = imbuements.imbuementMap.values.count { it.scrollId != 0 }
        assertThat(withScroll).isEqualTo(46)
    }

    @Test
    fun `critical strike stores damage bonus and chance in the skill slots`() {
        val strike = realImbuements().byName("Strike", 1)

        assertThat(strike.skills[Skill.SKILL_CRITICAL_HIT_DAMAGE.value]).isEqualTo(1500)
        assertThat(strike.skills[Skill.SKILL_CRITICAL_HIT_CHANCE.value]).isEqualTo(1000)
        assertThat(strike.skills[Skill.SKILL_SWORD.value]).isEqualTo(0)
        assertThat(strike.icon).isEqualTo(1)
        assertThat(strike.premium).isFalse()
    }

    @Test
    fun `life leech stores amount and chance in the skill slots`() {
        val vampirism = realImbuements().byName("Vampirism", 1)

        assertThat(vampirism.skills[Skill.SKILL_LIFE_LEECH_AMOUNT.value]).isEqualTo(500)
        assertThat(vampirism.skills[Skill.SKILL_LIFE_LEECH_CHANCE.value]).isEqualTo(100)
    }

    @Test
    fun `magic level imbuement stores its bonus as a stat`() {
        val epiphany = realImbuements().byName("Epiphany", 1)

        assertThat(epiphany.stats[Stat.STAT_MAGICPOINTS.value]).isEqualTo(1)
        assertThat(epiphany.skills).containsOnly(0)
    }

    @Test
    fun `speed and capacity imbuements store flat bonuses`() {
        val imbuements = realImbuements()

        assertThat(imbuements.byName("Swiftness", 3).speed).isEqualTo(30)
        assertThat(imbuements.byName("Featherweight", 3).capacity).isEqualTo(15)
    }

    @Test
    fun `protection imbuements store the absorb percent at the combat index`() {
        val imbuements = realImbuements()

        val lichShroud = imbuements.byName("Lich Shroud", 2)
        assertThat(lichShroud.absorbPercent[CombatType.DEATHDAMAGE.value]).isEqualTo(5)

        val dragonHide = imbuements.byName("Dragon Hide", 3)
        assertThat(dragonHide.absorbPercent[CombatType.FIREDAMAGE.value]).isEqualTo(15)
        assertThat(dragonHide.absorbPercent[CombatType.HOLYDAMAGE.value]).isEqualTo(0)
    }

    @Test
    fun `vibrancy stores the paralysis removal chance and pvp deflection`() {
        val imbuements = realImbuements()

        val basic = imbuements.byName("Vibrancy", 1)
        assertThat(basic.paralysisRemoveChance).isEqualTo(15)
        assertThat(basic.pvpParalysisDeflect).isTrue()

        assertThat(imbuements.byName("Vibrancy", 2).paralysisRemoveChance).isEqualTo(25)
        assertThat(imbuements.byName("Vibrancy", 3).paralysisRemoveChance).isEqualTo(50)
    }

    @Test
    fun `icon id shifts the icon by the base tier offset`() {
        val imbuements = realImbuements()

        assertThat(imbuements.byName("Scorch", 1).getIconID()).isEqualTo(13)
        assertThat(imbuements.byName("Scorch", 2).getIconID()).isEqualTo(14)
        assertThat(imbuements.byName("Scorch", 3).getIconID()).isEqualTo(15)
    }

    @Test
    fun `getImbuements keeps only entries inside the item category tier`() {
        val imbuements = realImbuements()

        val basicOnly = ImbuementItem(
            id = 2400,
            imbuementSlot = 1,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 1)
        )
        assertThat(imbuements.getImbuements(item = basicOnly).filter { it.category == 3 }.map { it.baseId })
            .containsExactly(1)

        val allTiers = ImbuementItem(
            id = 2400,
            imbuementSlot = 3,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        assertThat(imbuements.getImbuements(item = allTiers).filter { it.category == 3 }.map { it.baseId })
            .containsExactly(1, 2, 3)
    }

    @Test
    fun `getImbuements hides a category the item is already imbued with`() {
        val imbuements = realImbuements()
        val item = ImbuementItem(
            id = 2400,
            imbuementSlot = 3,
            imbuementTypes = mapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3)
        )
        item.setImbuement(0, imbuements.byName("Strike", 1).id, 72000)

        val engine = ImbuementEngine(imbuements)
        assertThat(imbuements.getImbuements(item = item).filter { it.category == 3 }).isEmpty()
        assertThat(engine.hasImbuementCategoryId(item, 3)).isTrue()
    }

    @Test
    fun `getImbuements with the scroll flag keeps only scroll imbuements`() {
        val imbuements = realImbuements()

        val scrolls = imbuements.getImbuements(scroll = true, toggleShrineStorage = false)
        assertThat(scrolls).hasSize(46)
        assertThat(scrolls).allMatch { it.scrollId != 0 }
    }

    @Test
    fun `getImbuements skips shrine storages the player has disabled`() {
        val imbuements = realImbuements()
        val player = FakeImbuementPlayer()
        player.storage[50494] = -1
        val disabled = imbuements.imbuementMap.values.filter { it.storage == 50494 }
        assertThat(disabled).hasSize(3)

        val filtered = imbuements.getImbuements(player = player, toggleShrineStorage = true)
        assertThat(filtered.map { it.id }).doesNotContainAnyElementsOf(disabled.map { it.id })
        assertThat(filtered).hasSize(72 - disabled.size)

        val unfiltered = imbuements.getImbuements(player = player, toggleShrineStorage = false)
        assertThat(unfiltered.map { it.id }).contains(3)
        assertThat(unfiltered).hasSize(72)
    }

    @Test
    fun `reload clears the previous entries before loading again`() {
        val imbuements = realImbuements()

        assertThat(imbuements.reload(realXml)).isTrue()
        assertThat(imbuements.basesImbuement).hasSize(3)
        assertThat(imbuements.categoriesImbuement).hasSize(20)
        assertThat(imbuements.imbuementMap).hasSize(72)
    }

    @Test
    fun `malformed xml fails to load and keeps loaded false`() {
        val imbuements = Imbuements()

        assertThat(imbuements.loadFromXml("<imbuements><base")).isFalse()
        assertThat(imbuements.loaded).isFalse()
    }

    @Test
    fun `imbuement without an existing base is skipped`() {
        val imbuements = load(
            """
            <imbuements>
                <base id="1" name="Basic" duration="72000"/>
                <category id="3" name="Critical Hit"/>
                <imbuement name="Strike" base="9" category="3" iconid="1"/>
            </imbuements>
            """.trimIndent()
        )

        assertThat(imbuements.imbuementMap).isEmpty()
    }

    @Test
    fun `imbuement missing its icon id stays registered with the default icon`() {
        val imbuements = load(
            """
            <imbuements>
                <base id="1" name="Basic" duration="72000"/>
                <category id="3" name="Critical Hit"/>
                <imbuement name="Strike" base="1" category="3">
                    <attribute key="effect" type="skill" value="critical" bonus="1500" chance="1000"/>
                </imbuement>
            </imbuements>
            """.trimIndent()
        )

        val imbuement = imbuements.getImbuement(1)
        assertThat(imbuement).isNotNull()
        assertThat(imbuement?.icon).isEqualTo(1)
        assertThat(imbuement?.name).isEmpty()
    }

    @Test
    fun `duplicate item entries in the same imbuement are ignored`() {
        val imbuements = load(
            """
            <imbuements>
                <base id="1" name="Basic" duration="72000"/>
                <category id="3" name="Critical Hit"/>
                <imbuement name="Strike" base="1" category="3" iconid="1">
                    <attribute key="item" value="11444" count="20"/>
                    <attribute key="item" value="11444" count="5"/>
                </imbuement>
            </imbuements>
            """.trimIndent()
        )

        assertThat(imbuements.getImbuement(1)?.items).containsExactly(11444 to 20)
    }

    @Test
    fun `duplicate scroll ids map to the last imbuement`() {
        val imbuements = load(
            """
            <imbuements>
                <base id="1" name="Basic" duration="72000"/>
                <base id="2" name="Intricate" duration="72000"/>
                <category id="3" name="Critical Hit"/>
                <imbuement name="Strike" base="1" category="3" iconid="1">
                    <attribute key="scroll" value="100"/>
                </imbuement>
                <imbuement name="Strike" base="2" category="3" iconid="1">
                    <attribute key="scroll" value="100"/>
                </imbuement>
            </imbuements>
            """.trimIndent()
        )

        assertThat(imbuements.getImbuementByScrollID(100)).isEqualTo(imbuements.getImbuement(2))
    }

    @Test
    fun `missing description attribute defaults the description to the name`() {
        val imbuements = load(
            """
            <imbuements>
                <base id="1" name="Basic" duration="72000"/>
                <category id="3" name="Critical Hit"/>
                <imbuement name="Strike" base="1" category="3" iconid="1">
                    <attribute key="description"/>
                </imbuement>
            </imbuements>
            """.trimIndent()
        )

        assertThat(imbuements.getImbuement(1)?.description).isEqualTo("Strike")
    }
}
