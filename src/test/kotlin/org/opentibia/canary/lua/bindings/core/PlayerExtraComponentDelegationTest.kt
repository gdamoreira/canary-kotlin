package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.appearance.effects.Wing
import org.opentibia.canary.creatures.players.components.Badge
import org.opentibia.canary.creatures.players.components.InMemoryBadgeRegistry
import org.opentibia.canary.creatures.players.components.InMemoryTitleRegistry
import org.opentibia.canary.creatures.players.components.Title
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.concoction.ConcoctionType
import org.opentibia.canary.systems.wheel.WheelOnThink

/**
 * The batch 4 `WorldPlayerExtraOperations` bindings for badges, titles, concoction/food,
 * the Wheel of Destiny, loyalty and the OTCR attached effects must read and write the
 * state holders composed on `PlayerFeatures` instead of keeping their own per-operations
 * maps. Each test drives the exact Canary `player_functions.cpp` method through real Lua
 * chunks and interleaves component reads, asserting the "Lua set + component read agree"
 * contract while the Lua surface stays identical.
 *
 * `instantSkillWOD`/`revealationStageWOD`/`avatarTimer`/`getMapShader`/`setMapShader` are
 * registered by `PlayerFunctions` (batch 3), so the fake world operations are bridged into
 * those registrations exactly like `PlayerExtraBindingsTest` does; the names that
 * `PlayerFunctionsExtra` registers are driven directly.
 */
class PlayerExtraComponentDelegationTest {

    private val badgeRegistry = InMemoryBadgeRegistry(
        listOf(Badge(id = 1, name = "First Steps")),
    )

    private val titleRegistry = InMemoryTitleRegistry(
        listOf(Title(id = 1, maleName = "Hero", femaleName = "Heroine", description = "A brave soul")),
    )

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun newPlayer(): Player = Player(
        id = 7L,
        name = "Delegated",
        maxHealth = 150,
        health = 150,
        position = Position(0, 0, 7),
    )

    /**
     * Bridges the batch 3 registrations for the Wheel/OTCR names onto the batch 4 world
     * operations, mirroring how `WorldLuaOperations` will route them once both groups share
     * one per-player state.
     */
    private class WheelOtcBatch3Adapter(
        private val extra: PlayerExtraOperations,
    ) : PlayerBatch3Operations by WorldPlayerBatch3Operations() {
        override fun getWheelInstantSkill(player: Any, name: String): Boolean? =
            extra.instantSkillWOD(player, name, null)

        override fun setWheelInstantSkill(player: Any, name: String, value: Boolean): Boolean? =
            extra.instantSkillWOD(player, name, value)

        override fun getWheelSpellStage(player: Any, name: String): Int? =
            extra.revelationStageWOD(player, name, null) as? Int

        override fun setWheelSpellStage(player: Any, name: String, value: Int): Boolean? =
            extra.revelationStageWOD(player, name, value) as? Boolean

        override fun getWheelAvatarTimer(player: Any): Long? = extra.avatarTimer(player, null) as? Long

        override fun setWheelAvatarTimer(player: Any, value: Long): Boolean? =
            extra.avatarTimer(player, value) as? Boolean

        override fun getMapShader(player: Any): String? = extra.getMapShader(player)

        override fun setMapShader(player: Any, shaderName: String): Boolean? =
            extra.setMapShader(player, shaderName)
    }

    private fun installed(extra: PlayerExtraOperations, player: Player): LuaEnvironment {
        val environment = env()
        PlayerFunctions.install(environment, batch3 = WheelOtcBatch3Adapter(extra))
        PlayerFunctionsExtra.install(environment, extra)
        environment.globals.set("p", environment.userdata.wrap("Player", player))
        return environment
    }

    @Test
    fun `lua badges and titles read and write the composed components`() {
        val player = newPlayer()
        val environment = installed(
            WorldPlayerExtraOperations(badgeRegistry = badgeRegistry, titleRegistry = titleRegistry),
            player,
        )

        environment.loadChunk(
            """
            badge = p:addBadge(1)
            added = p:addTitle(1)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("badge").toboolean()).isTrue()
        assertThat(player.features.badge.hasBadge(1)).isTrue()
        assertThat(environment.getGlobal("added").toboolean()).isTrue()

        val title = player.features.title
        assertThat(title.isTitleUnlocked(1)).isTrue()

        environment.loadChunk(
            """
            titles = p:getTitles()
            count = #titles
            name = titles[1].name
            description = titles[1].description
            current = p:setCurrentTitle(1)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Heroine")
        assertThat(environment.getGlobal("description").tojstring()).isEqualTo("A brave soul")
        assertThat(environment.getGlobal("current").toboolean()).isTrue()
        assertThat(title.getCurrentTitle()).isEqualTo(1)

        environment.loadChunk("unknown = p:setCurrentTitle(99)")
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
        assertThat(title.getCurrentTitle()).isEqualTo(1)
    }

    @Test
    fun `lua concoction and food read and write the composed state holders`() {
        val player = newPlayer()
        val environment = installed(WorldPlayerExtraOperations(), player)

        environment.loadChunk("updated = p:updateConcoction(36723, 300)")
        assertThat(environment.getGlobal("updated").toboolean()).isTrue()
        assertThat(player.features.concoctions.isConcoctionActive(ConcoctionType.KooldownAid)).isTrue()

        environment.loadChunk("food = p:updateFood(100, 500)")
        assertThat(environment.getGlobal("food").toboolean()).isTrue()
        assertThat(player.features.activeFoods.isFoodActive(100)).isTrue()

        environment.loadChunk("p:updateConcoction(36723, 0)")
        assertThat(player.features.concoctions.isConcoctionActive(ConcoctionType.KooldownAid)).isFalse()
    }

    @Test
    fun `lua wheel instant stage and avatar state read and write the composed wheel`() {
        val player = newPlayer()
        val environment = installed(WorldPlayerExtraOperations(), player)

        environment.loadChunk(
            """
            instantBefore = p:instantSkillWOD("Battle Instinct")
            instantSet = p:instantSkillWOD("Battle Instinct", true)
            stageBefore = p:revelationStageWOD("Beam Mastery")
            stageSet = p:revelationStageWOD("Beam Mastery", 2)
            timerBefore = p:avatarTimer()
            timerSet = p:avatarTimer(900)
            """.trimIndent(),
        )

        val wheel = player.features.wheel
        assertThat(environment.getGlobal("instantBefore").toboolean()).isFalse()
        assertThat(environment.getGlobal("instantSet").toboolean()).isTrue()
        assertThat(wheel.getInstant("Battle Instinct")).isTrue()
        assertThat(environment.getGlobal("stageBefore").toint()).isZero()
        assertThat(environment.getGlobal("stageSet").toboolean()).isTrue()
        assertThat(wheel.getStage("Beam Mastery")).isEqualTo(1)
        assertThat(environment.getGlobal("timerBefore").toint()).isZero()
        assertThat(environment.getGlobal("timerSet").toboolean()).isTrue()
        assertThat(wheel.getOnThinkTimer(WheelOnThink.AVATAR_SPELL)).isEqualTo(900)
    }

    @Test
    fun `lua map shader and custom outfits read and write the composed attached effects`() {
        val player = newPlayer()
        player.features.attachedEffectDefinitions.loadFromXml(
            "<attachedeffects><wing id=\"5\" name=\"Bat\"/></attachedeffects>",
        )
        val environment = installed(WorldPlayerExtraOperations(), player)

        environment.loadChunk(
            """
            shaderBefore = p:getMapShader()
            shaderSet = p:setMapShader("winter")
            """.trimIndent(),
        )

        val attached = player.features.attachedEffects
        assertThat(environment.getGlobal("shaderBefore").tojstring()).isEmpty()
        assertThat(environment.getGlobal("shaderSet").toboolean()).isTrue()
        assertThat(attached.getMapShader()).isEqualTo("winter")

        environment.loadChunk("added = p:addCustomOutfit(\"wing\", 5)")
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(attached.hasWing(Wing(id = 5, name = "Bat"))).isTrue()

        environment.loadChunk("removed = p:removeCustomOutfit(\"wing\", 5)")
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(attached.hasWing(Wing(id = 5, name = "Bat"))).isFalse()
    }

    @Test
    fun `lua loyalty operations read and write the composed loyalty state`() {
        val player = newPlayer()
        val environment = installed(WorldPlayerExtraOperations(), player)

        environment.loadChunk(
            """
            pointsBefore = p:getLoyaltyPoints()
            bonusBefore = p:getLoyaltyBonus()
            titleBefore = p:getLoyaltyTitle()
            bonusSet = p:setLoyaltyBonus(25)
            titleSet = p:setLoyaltyTitle("Knight")
            bonusAfter = p:getLoyaltyBonus()
            titleAfter = p:getLoyaltyTitle()
            """.trimIndent(),
        )

        val loyalty = player.features.loyalty
        assertThat(loyalty).isNotNull
        assertThat(environment.getGlobal("pointsBefore").toint()).isZero()
        assertThat(environment.getGlobal("bonusBefore").toint()).isZero()
        assertThat(environment.getGlobal("titleBefore").tojstring()).isEmpty()
        assertThat(environment.getGlobal("bonusSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("titleSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("bonusAfter").toint()).isEqualTo(25)
        assertThat(environment.getGlobal("titleAfter").tojstring()).isEqualTo("Knight")
        assertThat(loyalty!!.getLoyaltyBonus()).isEqualTo(25)
        assertThat(loyalty.getLoyaltyTitle()).isEqualTo("Knight")
    }
}
