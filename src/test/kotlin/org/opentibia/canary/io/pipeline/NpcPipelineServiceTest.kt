package org.opentibia.canary.io.pipeline

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

class NpcPipelineServiceTest {

    @Test
    fun `loads real npc XML definition and populates shop inventory`() {
        val xmlContent = """
            <npc name="Frodo" walkinterval="2000" floorchange="0">
                <health now="100" max="100"/>
                <look type="128" head="20" body="39" legs="45" feet="7"/>
                <parameters>
                    <parameter key="shop_buyable" value="bread,2689,4;cheese,2696,5;"/>
                    <parameter key="shop_sellable" value="empty potion flask,7636,5;"/>
                </parameters>
            </npc>
        """.trimIndent()

        val service = NpcPipelineService()
        val npc = service.parse(xmlContent, spawnPos = Position(32369, 32241, 7))

        assertThat(npc).isNotNull
        assertThat(npc?.name).isEqualTo("Frodo")
        assertThat(npc?.position).isEqualTo(Position(32369, 32241, 7))
        assertThat(npc?.getShopItems()).hasSize(3)

        val bread = npc?.getShopItems()?.find { it.itemId == 2689 }
        assertThat(bread).isNotNull
        assertThat(bread?.name).isEqualTo("bread")
        assertThat(bread?.price).isEqualTo(4)
    }
}
