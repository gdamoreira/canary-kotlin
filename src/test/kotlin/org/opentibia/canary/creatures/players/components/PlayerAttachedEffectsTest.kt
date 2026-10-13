package org.opentibia.canary.creatures.players.components

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.appearance.effects.AttachedEffects
import org.opentibia.canary.creatures.appearance.effects.Aura
import org.opentibia.canary.creatures.appearance.effects.Effect
import org.opentibia.canary.creatures.appearance.effects.Shader
import org.opentibia.canary.creatures.appearance.effects.Wing
import org.opentibia.canary.creatures.appearance.outfit.Outfits
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the Canary `PlayerAttachedEffects` component.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/players/components/player_attached_effects.hpp:19-136
 *  - src/creatures/players/components/player_attached_effects.cpp:18-874
 *  - src/utils/const.hpp:54-68 (`PSTRG_WING_*`, `PSTRG_EFFECT_*`, `PSTRG_AURA_*`)
 *  - src/creatures/appearance/attached_effects/attached_effects.hpp:12-40
 *
 * `g_game()`, `Outfits::getInstance()` and `m_player.client` are
 * constructor-injected seams (AGENTS.md 1.1); `OTSYS_TIME()` is the [now] seam
 * and the global `uniform_random` is the [random] seam.
 */
class PlayerAttachedEffectsTest {

    // ---- wings -----------------------------------------------------------------

    @Test
    fun `getLastWing prefers the current-wing storage over kv and narrows to uint8`() {
        val f = fixture()
        f.player.storage[PlayerStorage.PSTRG_WING_CURRENTWING] = 5
        f.player.kv.set("last-wing", 7L)
        assertThat(f.effects.getLastWing()).isEqualTo(5)

        f.player.storage.remove(PlayerStorage.PSTRG_WING_CURRENTWING)
        assertThat(f.effects.getLastWing()).isEqualTo(7)

        f.player.kv.set("last-wing", 300L)
        assertThat(f.effects.getLastWing()).isEqualTo(44)
    }

    @Test
    fun `getLastWing returns zero without storage or kv`() {
        assertThat(fixture().effects.getLastWing()).isEqualTo(0)
    }

    @Test
    fun `getCurrentWing only reads the current-wing storage`() {
        val f = fixture()
        f.player.kv.set("last-wing", 9L)
        assertThat(f.effects.getCurrentWing()).isEqualTo(0)

        f.player.storage[PlayerStorage.PSTRG_WING_CURRENTWING] = 4
        assertThat(f.effects.getCurrentWing()).isEqualTo(4)
    }

    @Test
    fun `setCurrentWing writes the current-wing storage key`() {
        val f = fixture()

        f.effects.setCurrentWing(6)

        assertThat(f.player.storage[PlayerStorage.PSTRG_WING_CURRENTWING]).isEqualTo(6)
    }

    @Test
    fun `isWinged reads the component default outfit wing`() {
        val f = fixture()
        assertThat(f.effects.isWinged()).isFalse()

        f.effects.tameWing(3)
        f.player.kv.set("last-wing", 3L)
        assertThat(f.effects.toggleWing(true)).isTrue()
        assertThat(f.effects.isWinged()).isTrue()
    }

    @Test
    fun `tameWing packs the wing bit into the range key`() {
        val f = fixture()

        assertThat(f.effects.tameWing(3)).isTrue()

        assertThat(f.player.storage[PlayerStorage.PSTRG_WING_RANGE_START]).isEqualTo(1 shl 2)
        assertThat(f.effects.hasWing(WING_A)).isTrue()
        assertThat(f.effects.hasWing(WING_B)).isFalse()
        assertThat(f.effects.tameWing(99)).isFalse()
    }

    @Test
    fun `tameWing ors into an existing packed value`() {
        val f = fixture()
        f.player.storage[PlayerStorage.PSTRG_WING_RANGE_START] = 1

        f.effects.tameWing(3)

        assertThat(f.player.storage[PlayerStorage.PSTRG_WING_RANGE_START]).isEqualTo(1 or (1 shl 2))
    }

    @Test
    fun `hasWing is always true for access players`() {
        val f = fixture()
        f.player.accessPlayer = true

        assertThat(f.effects.hasWing(WING_A)).isTrue()
    }

    @Test
    fun `hasAnyWing and getRandomWingId consider the tamed wings`() {
        val f = fixture()
        assertThat(f.effects.hasAnyWing()).isFalse()
        assertThat(f.effects.getRandomWingId()).isEqualTo(0)

        f.effects.tameWing(3)
        f.effects.tameWing(5)
        f.nextRandom = 1

        assertThat(f.effects.hasAnyWing()).isTrue()
        assertThat(f.effects.getRandomWingId()).isEqualTo(5)
    }

    @Test
    fun `untameWing clears the bit and only diswings the current wing`() {
        val f = fixture()
        f.effects.tameWing(3)
        f.player.kv.set("last-wing", 3L)
        f.effects.toggleWing(true)
        f.player.outfitChanges.clear()

        assertThat(f.effects.untameWing(3)).isTrue()

        assertThat(f.player.storage[PlayerStorage.PSTRG_WING_RANGE_START]).isEqualTo(0)
        assertThat(f.player.storage[PlayerStorage.PSTRG_WING_CURRENTWING]).isEqualTo(0)
        assertThat(f.player.kv.get("last-wing")).isEqualTo(0L)
        assertThat(f.player.outfit.lookWing).isEqualTo(0)
        assertThat(f.player.outfitChanges).hasSize(1)
    }

    @Test
    fun `toggleWing refuses inside the exhaustion window and allows the bypass flag`() {
        val f = fixture()
        f.effects.tameWing(3)
        f.player.kv.set("last-wing", 3L)
        f.player.nowMillis = 10_000L
        assertThat(f.effects.toggleWing(true)).isTrue()

        f.player.nowMillis = 10_500L
        assertThat(f.effects.toggleWing(true)).isFalse()
        assertThat(f.player.cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)

        f.player.cancels.clear()
        f.effects.setWasWinged(true)
        assertThat(f.effects.toggleWing(true)).isFalse()
        assertThat(f.player.cancels).isEmpty()
    }

    @Test
    fun `toggleWing requires the outfit look type to be registered`() {
        val f = fixture(outfitXml = "<outfits></outfits>")
        f.effects.tameWing(3)
        f.player.kv.set("last-wing", 3L)

        assertThat(f.effects.toggleWing(true)).isFalse()
    }

    @Test
    fun `toggleWing opens the outfit window when no last wing exists`() {
        val f = fixture()

        assertThat(f.effects.toggleWing(true)).isFalse()

        assertThat(f.player.outfitWindows).isEqualTo(1)
    }

    @Test
    fun `toggleWing equips the last tamed wing and persists it`() {
        val f = fixture()
        f.effects.tameWing(3)
        f.player.kv.set("last-wing", 3L)

        assertThat(f.effects.toggleWing(true)).isTrue()

        assertThat(f.player.outfit.lookWing).isEqualTo(3)
        assertThat(f.player.storage[PlayerStorage.PSTRG_WING_CURRENTWING]).isEqualTo(3)
        assertThat(f.player.kv.get("last-wing")).isEqualTo(3L)
        assertThat(f.player.outfitChanges).hasSize(1)
        assertThat(f.player.cancels).isEmpty()
    }

    @Test
    fun `toggleWing with a random mount picks a tamed wing`() {
        val f = fixture()
        f.effects.tameWing(3)
        f.effects.tameWing(5)
        f.player.kv.set("last-wing", 3L)
        f.player.randomMounted = true
        f.nextRandom = 1

        assertThat(f.effects.toggleWing(true)).isTrue()

        assertThat(f.player.outfit.lookWing).isEqualTo(5)
    }

    @Test
    fun `toggleWing clears the state when the last wing is no longer tamed`() {
        val f = fixture()
        f.player.storage[PlayerStorage.PSTRG_WING_CURRENTWING] = 5
        f.player.kv.set("last-wing", 5L)

        assertThat(f.effects.toggleWing(true)).isFalse()

        assertThat(f.player.storage[PlayerStorage.PSTRG_WING_CURRENTWING]).isEqualTo(0)
        assertThat(f.player.kv.get("last-wing")).isEqualTo(0L)
        assertThat(f.player.outfitWindows).isEqualTo(1)
    }

    @Test
    fun `toggleWing refuses while the outfit condition is active`() {
        val f = fixture()
        f.effects.tameWing(3)
        f.player.kv.set("last-wing", 3L)
        f.player.outfitCondition = true

        assertThat(f.effects.toggleWing(true)).isFalse()

        assertThat(f.player.cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `toggleWing off diswings the player`() {
        val f = fixture()
        f.effects.tameWing(3)
        f.player.kv.set("last-wing", 3L)
        f.effects.toggleWing(true)
        f.player.outfitChanges.clear()
        f.player.nowMillis += 3000

        assertThat(f.effects.toggleWing(false)).isTrue()

        assertThat(f.player.outfit.lookWing).isEqualTo(0)
        assertThat(f.player.outfitChanges).hasSize(1)
    }

    // ---- auras -----------------------------------------------------------------

    @Test
    fun `tameAura and untameAura clear the packed bit`() {
        val f = fixture()

        assertThat(f.effects.tameAura(2)).isTrue()
        assertThat(f.effects.hasAura(AURA_A)).isTrue()
        assertThat(f.player.storage[PlayerStorage.PSTRG_AURA_RANGE_START]).isEqualTo(1 shl 1)

        assertThat(f.effects.untameAura(2)).isTrue()
        assertThat(f.effects.hasAura(AURA_A)).isFalse()
    }

    @Test
    fun `toggleAura equips the last tamed aura and persists it`() {
        val f = fixture()
        f.effects.tameAura(2)
        f.player.kv.set("last-aura", 2L)

        assertThat(f.effects.toggleAura(true)).isTrue()

        assertThat(f.player.outfit.lookAura).isEqualTo(2)
        assertThat(f.player.storage[PlayerStorage.PSTRG_AURA_CURRENTAURA]).isEqualTo(2)
        assertThat(f.player.kv.get("last-aura")).isEqualTo(2L)
    }

    @Test
    fun `getRandomAuraId returns zero when none is tamed and picks otherwise`() {
        val f = fixture()
        assertThat(f.effects.getRandomAuraId()).isEqualTo(0)

        f.effects.tameAura(2)
        f.nextRandom = 0
        assertThat(f.effects.getRandomAuraId()).isEqualTo(2)
    }

    // ---- effects ---------------------------------------------------------------

    @Test
    fun `tameEffect packs the effect bit and toggleEffect equips it`() {
        val f = fixture()

        assertThat(f.effects.tameEffect(4)).isTrue()
        assertThat(f.effects.hasEffect(EFFECT_A)).isTrue()
        assertThat(f.player.storage[PlayerStorage.PSTRG_EFFECT_RANGE_START]).isEqualTo(1 shl 3)

        f.player.kv.set("last-effect", 4L)
        assertThat(f.effects.toggleEffect(true)).isTrue()

        assertThat(f.player.outfit.lookEffect).isEqualTo(4)
        assertThat(f.player.storage[PlayerStorage.PSTRG_EFFECT_CURRENTEFFECT]).isEqualTo(4)
        assertThat(f.player.kv.get("last-effect")).isEqualTo(4L)
    }

    @Test
    fun `getRandomEffectId only samples tamed effects`() {
        val f = fixture()
        f.effects.tameEffect(4)
        f.nextRandom = 0

        assertThat(f.effects.getRandomEffectId()).isEqualTo(4)
        assertThat(f.effects.getCurrentEffect()).isEqualTo(0)
    }

    // ---- shaders ---------------------------------------------------------------

    @Test
    fun `tameShader and untameShader manage the shader set`() {
        val f = fixture()

        assertThat(f.effects.tameShader(6)).isTrue()
        assertThat(f.effects.tameShader(6)).isFalse()
        assertThat(f.effects.hasShader(SHADER_A)).isTrue()
        assertThat(f.effects.hasShaders()).isTrue()
        assertThat(f.effects.tameShader(99)).isFalse()

        assertThat(f.effects.untameShader(6)).isTrue()
        assertThat(f.effects.hasShader(SHADER_A)).isFalse()
        assertThat(f.effects.hasShaders()).isFalse()
    }

    @Test
    fun `getCurrentShader_NAME resolves the registry name or the default`() {
        val f = fixture()
        assertThat(f.effects.getCurrentShader_NAME()).isEqualTo("Outfit - Default")

        f.effects.setCurrentShader(6)
        assertThat(f.effects.getCurrentShader_NAME()).isEqualTo("ShaderA")

        f.effects.setCurrentShader(99)
        assertThat(f.effects.getCurrentShader_NAME()).isEqualTo("Outfit - Default")
    }

    @Test
    fun `toggleShader equips a tamed current shader`() {
        val f = fixture()
        f.effects.tameShader(6)
        f.effects.setCurrentShader(6)

        assertThat(f.effects.toggleShader(true)).isTrue()

        assertThat(f.player.outfit.lookShader).isEqualTo(6)
        assertThat(f.player.outfitChanges).hasSize(1)
    }

    @Test
    fun `toggleShader refuses without a current shader and opens the outfit window`() {
        val f = fixture()

        assertThat(f.effects.toggleShader(true)).isFalse()

        assertThat(f.player.outfitWindows).isEqualTo(1)
    }

    @Test
    fun `toggleShader off dissaders the player`() {
        val f = fixture()
        f.effects.tameShader(6)
        f.effects.setCurrentShader(6)
        f.effects.toggleShader(true)
        f.player.outfitChanges.clear()
        f.player.nowMillis += 3000

        assertThat(f.effects.toggleShader(false)).isTrue()

        assertThat(f.player.outfit.lookShader).isEqualTo(0)
        assertThat(f.player.outfitChanges).hasSize(1)
    }

    // ---- custom outfits ----------------------------------------------------------

    @Test
    fun `addCustomOutfit resolves ids and names per type`() {
        val f = fixture()

        assertThat(f.effects.addCustomOutfit("wing", AttachedEffectSelector.Id(3))).isTrue()
        assertThat(f.effects.addCustomOutfit("aura", AttachedEffectSelector.Name("AuraA"))).isTrue()
        assertThat(f.effects.addCustomOutfit("effect", AttachedEffectSelector.Name("EffectA"))).isTrue()
        assertThat(f.effects.addCustomOutfit("shader", AttachedEffectSelector.Name("ShaderA"))).isTrue()
        assertThat(f.effects.addCustomOutfit("cape", AttachedEffectSelector.Id(1))).isFalse()
        assertThat(f.effects.addCustomOutfit("wing", AttachedEffectSelector.Name("Nope"))).isFalse()
    }

    @Test
    fun `removeCustomOutfit keeps the canary wings name-branch mismatch`() {
        val f = fixture()
        f.effects.tameWing(3)
        f.effects.tameWing(5)

        assertThat(f.effects.removeCustomOutfit("wing", AttachedEffectSelector.Id(3))).isTrue()
        assertThat(f.effects.hasWing(WING_A)).isFalse()

        assertThat(f.effects.removeCustomOutfit("wings", AttachedEffectSelector.Name("WingB"))).isFalse()
        assertThat(f.effects.hasWing(WING_B)).isTrue()
    }

    // ---- OTCR senders ------------------------------------------------------------

    @Test
    fun `sendAttachedEffect forwards attached detach and shader packets to the client`() {
        val f = fixture(withClient = true)
        val creature = ProbeCreature()

        f.effects.sendAttachedEffect(creature, 22)
        f.effects.sendDetachEffect(creature, 23)
        f.effects.sendShader(creature, "ShaderA")

        assertThat(f.client!!.sentAttached).containsExactly(creature to 22)
        assertThat(f.client.sentDetached).containsExactly(creature to 23)
        assertThat(f.client.sentShaders).containsExactly(creature to "ShaderA")
    }

    @Test
    fun `sendAttachedEffect does nothing without a client or a creature`() {
        val f = fixture()
        f.effects.sendAttachedEffect(ProbeCreature(), 22)
        f.effects.sendMapShader("ShaderA")

        assertThat(f.effects.getMapShader()).isEqualTo("")
    }

    @Test
    fun `map shader is stored and forwarded to the client`() {
        val f = fixture(withClient = true)

        f.effects.setMapShader("ShaderA")
        f.effects.sendMapShader("ShaderA")

        assertThat(f.effects.getMapShader()).isEqualTo("ShaderA")
        assertThat(f.client!!.sentMapShaders).containsExactly("ShaderA")
    }

    // ---- fixture -----------------------------------------------------------------

    private class Fixture(
        val player: FakePlayer,
        val client: FakeClient?,
        val effects: PlayerAttachedEffects,
    ) {
        /** The scripted value [AttachedEffectsRandom] returns. */
        var nextRandom: Int
            get() = player.nextRandom
            set(value) {
                player.nextRandom = value
            }
    }

    private fun fixture(
        outfitXml: String = OUTFITS_XML,
        withClient: Boolean = false,
    ): Fixture {
        val player = FakePlayer()
        val client = if (withClient) FakeClient() else null
        val effects = PlayerAttachedEffects(
            player = player,
            attachedEffects = registry(),
            outfits = Outfits().apply { loadFromXml(outfitXml) },
            client = client,
            random = { _, _ -> player.nextRandom },
            now = { player.nowMillis },
        )
        return Fixture(player, client, effects)
    }

    private class FakePlayer : AttachedEffectsPlayer {
        var outfit = Outfit(lookType = 136)
        var accessPlayer = false
        var randomMounted = false
        var outfitCondition = false
        var nextRandom = 0
        var nowMillis = 10_000L
        val storage = mutableMapOf<Int, Int>()
        val kvStorage: KV = InMemoryKV()
        val cancels = mutableListOf<ReturnValue>()
        var outfitWindows = 0
        val outfitChanges = mutableListOf<Outfit>()

        val kv: KV get() = kvStorage

        override fun getSex(): PlayerSex_t = PlayerSex_t.MALE

        override fun getDefaultOutfit(): Outfit = outfit

        override fun setDefaultOutfit(outfit: Outfit) {
            this.outfit = outfit.copy()
        }

        override fun getStorageValue(key: Int): Int = storage[key] ?: -1

        override fun addStorageValue(key: Int, value: Int) {
            storage[key] = value
        }

        override fun kv(): KV = kvStorage

        override fun isAccessPlayer(): Boolean = accessPlayer

        override fun isRandomMounted(): Boolean = randomMounted

        override fun hasCondition(conditionType: ConditionType): Boolean =
            conditionType == ConditionType.CONDITION_OUTFIT && outfitCondition

        override fun sendCancelMessage(returnValue: ReturnValue) {
            cancels.add(returnValue)
        }

        override fun sendOutfitWindow() {
            outfitWindows++
        }

        override fun internalCreatureChangeOutfit(outfit: Outfit) {
            outfitChanges.add(outfit.copy())
        }
    }

    private class FakeClient : AttachedEffectsClient {
        val sentAttached = mutableListOf<Pair<Creature, Int>>()
        val sentDetached = mutableListOf<Pair<Creature, Int>>()
        val sentShaders = mutableListOf<Pair<Creature, String>>()
        val sentMapShaders = mutableListOf<String>()

        override fun sendAttachedEffect(creature: Creature, effectId: Int) {
            sentAttached.add(creature to effectId)
        }

        override fun sendDetachEffect(creature: Creature, effectId: Int) {
            sentDetached.add(creature to effectId)
        }

        override fun sendShader(creature: Creature, shaderName: String) {
            sentShaders.add(creature to shaderName)
        }

        override fun sendMapShader(shaderName: String) {
            sentMapShaders.add(shaderName)
        }
    }

    private class ProbeCreature : Creature(
        id = 1,
        name = "Probe",
        maxHealth = 100,
        health = 100,
        position = Position(1, 1, 7),
        direction = Direction.South,
    )

    private companion object {
        val WING_A = Wing(3, "WingA")
        val WING_B = Wing(5, "WingB")
        val AURA_A = Aura(2, "AuraA")
        val EFFECT_A = Effect(4, "EffectA")
        val SHADER_A = Shader(6, "ShaderA")

        const val OUTFITS_XML = "<outfits><outfit type=\"1\" looktype=\"136\" name=\"Citizen\"/></outfits>"

        const val ATTACHED_EFFECTS_XML = """
            <attachedeffects>
              <wing id="3" name="WingA"/>
              <wing id="5" name="WingB"/>
              <aura id="2" name="AuraA"/>
              <effect id="4" name="EffectA"/>
              <shader id="6" name="ShaderA"/>
            </attachedeffects>
        """

        fun registry(): AttachedEffects = AttachedEffects().apply { loadFromXml(ATTACHED_EFFECTS_XML) }
    }
}
