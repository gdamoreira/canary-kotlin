package org.opentibia.canary.systems.imbuement

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType

/**
 * Composition tests for running the [ImbuementEngine] over a real [Item]:
 * `ImbuementItem(item)` must read the item's slot count and `imbuementTypes`
 * and write the `ITEM_IMBUEMENT_SLOT + slot` custom attributes through the
 * item's own store, so the state survives serialization
 * (`Item::setImbuement`, src/items/item.cpp:485-488; `Item::getImbuementInfo`,
 * src/items/item.cpp:472-483; `Item::decayImbuementTime`/`clearImbuement`,
 * src/items/item.hpp:713-717).
 */
class ImbuementItemRealItemTest {

    private val imbuements = Imbuements().apply {
        assertThat(loadFromXml(TEST_XML)).isTrue()
    }
    private val engine = ImbuementEngine(imbuements)

    private val strike = imbuements.imbuementMap.values.single { it.name == "Strike" && it.baseId == 2 }

    private fun sword(): Item = Item(
        id = 2400,
        type = ItemType(
            id = 2400,
            name = "Sword",
            imbuementSlot = 2,
            imbuementTypes = linkedMapOf(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT to 3),
        ),
    )

    @Test
    fun `imbuement item reads the slot count and types from the real item`() {
        val item = sword()
        val view = ImbuementItem(item)

        assertThat(view.id).isEqualTo(2400)
        assertThat(view.getImbuementSlot()).isEqualTo(2)
        assertThat(view.hasImbuementType(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT, 3)).isTrue()
        assertThat(view.hasImbuementType(ImbuementTypes_t.IMBUEMENT_CRITICAL_HIT, 4)).isFalse()
        assertThat(view.hasImbuementType(ImbuementTypes_t.IMBUEMENT_LIFE_LEECH, 1)).isFalse()
    }

    @Test
    fun `applyScrollImbuement writes the packed custom attribute on the real item`() {
        val item = sword()
        val view = ImbuementItem(item)
        val player = FakeImbuementPlayer()
        view.parent = player
        view.holdingPlayer = player
        val scroll = ImbuementItem(Item(id = 51742))

        engine.applyScrollImbuement(view, scroll, player)

        assertThat(item.hasImbuementAttribute("500")).isTrue()
        assertThat(item.getCustomAttribute("500")?.getInteger()).isEqualTo((72000L shl 8) or strike.id.toLong())
        assertThat(engine.getImbuementInfo(view, 0)?.imbuement).isEqualTo(strike)
    }

    @Test
    fun `onApplyImbuement and onClearImbuement mutate the real item store`() {
        val item = sword()
        val view = ImbuementItem(item)
        val player = FakeImbuementPlayer().apply {
            itemCounts[11444] = 20
        }
        view.parent = player
        view.holdingPlayer = player

        engine.onApplyImbuement(strike, view, 0, player)

        assertThat(item.getImbuementAttribute(0)).isEqualTo((72000L shl 8) or strike.id.toLong())
        assertThat(player.removedMoney).containsExactly(30000L)
        assertThat(player.skillBonuses[Skill.SKILL_CRITICAL_HIT_DAMAGE]).isEqualTo(2500)

        engine.onClearImbuement(view, 0, player)

        assertThat(item.getImbuementAttribute(0)).isZero()
        assertThat(item.hasImbuements()).isFalse()
        assertThat(player.removedMoney).containsExactly(30000L, 15000L)
    }

    @Test
    fun `decay tracker updates the real item custom attribute`() {
        val item = sword()
        val view = ImbuementItem(item)
        val player = FakeImbuementPlayer().apply {
            itemCounts[11444] = 20
        }
        view.parent = player
        view.holdingPlayer = player
        var now = 1_000_000L
        val decay = ImbuementDecay(imbuements) { now }
        val engineWithDecay = ImbuementEngine(imbuements, decay)

        engineWithDecay.onApplyImbuement(strike, view, 0, player)
        now += 5_000

        decay.checkImbuementDecay()

        assertThat(item.getImbuementAttribute(0)).isEqualTo(((72000 - 5) shl 8).toLong() or strike.id.toLong())
    }
}

private const val TEST_XML = """
<imbuements>
    <base id="1" name="Basic" price="5000" protectionPrice="10000" percent="90" removecost="15000" duration="72000"/>
    <base id="2" name="Intricate" price="30000" protectionPrice="30000" percent="70" removecost="15000" duration="72000"/>
    <base id="3" name="Powerful" price="200000" protectionPrice="50000" percent="50" removecost="15000" duration="72000"/>
    <category id="1" name="Life Leech" agressive="1"/>
    <category id="3" name="Critical Hit" agressive="1"/>
    <imbuement name="Strike" base="2" category="3" iconid="1" premium="1" storage="0">
        <attribute key="description" value="Raises crit hit damage by 25 percent and crit hit chance by 10 percent."/>
        <attribute key="effect" type="skill" value="critical" bonus="2500" chance="1000"/>
        <attribute key="item" value="11444" count="20"/>
        <attribute key="scroll" value="51742"/>
    </imbuement>
</imbuements>
"""
