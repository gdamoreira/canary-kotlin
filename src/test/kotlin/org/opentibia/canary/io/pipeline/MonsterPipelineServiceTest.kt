package org.opentibia.canary.io.pipeline

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.imbuement.Imbuements
import kotlin.random.Random

class MonsterPipelineServiceTest {

    @Test
    fun `loads monster definitions and rolls loot according to drop chances`() {
        val xmlContent = """
            <monster name="Dragon" experience="700" speed="200" race="blood">
                <health now="1000" max="1000"/>
                <loot>
                    <item id="2148" countmax="100" chance="100000"/> <!-- Gold Coin, 100% -->
                    <item id="2516" chance="1000"/> <!-- Dragon Shield, 1% -->
                </loot>
            </monster>
        """.trimIndent()

        val service = MonsterPipelineService()
        val monsterType = service.parse(xmlContent)

        assertThat(monsterType).isNotNull
        assertThat(monsterType?.name).isEqualTo("Dragon")
        assertThat(monsterType?.experience).isEqualTo(700L)
        assertThat(monsterType?.lootTable).hasSize(2)

        // Simulate guaranteed drop roll (100% chance item)
        val fixedRandom = Random(123)
        val droppedItems = LootEngine.rollLoot(monsterType!!.lootTable, fixedRandom)

        assertThat(droppedItems).isNotEmpty()
        assertThat(droppedItems.map { it.id }).contains(2148)
    }

    @Test
    fun `rollLoot assigns the imbuement registry to every rolled item`() {
        val imbuements = Imbuements()

        val droppedItems = LootEngine.rollLoot(
            listOf(LootEntry(itemId = 2148, chanceBasisPoints = 100000)),
            Random(123),
            imbuements,
        )

        assertThat(droppedItems).isNotEmpty()
        assertThat(droppedItems).allSatisfy { item ->
            assertThat(item.imbuementRegistry).isSameAs(imbuements)
        }
    }
}
