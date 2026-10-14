package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Abilities
import org.opentibia.canary.items.Ammo_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.proficiency.WeaponProficiencyPerfectShotBonus

/**
 * Transcribes the Player equipped-item combat bonus queries
 * (src/creatures/players/player.cpp:874-899, 1051-1069, 7560-7600, 7683-7723):
 *
 * - `Player::getDamageAccuracy` (player.cpp:874-899)
 * - `Player::getCleavePercent` (player.cpp:1051-1069)
 * - `Player::setCleavePercent` (player.cpp:7560-7562)
 * - `Player::getPerfectShotDamage` (player.cpp:7564-7593)
 * - `Player::setPerfectShotDamage` (player.cpp:7595-7600)
 * - `Player::getReflectPercent` (player.cpp:7683-7702)
 * - `Player::getReflectFlat` (player.cpp:7704-7723)
 *
 * The base arrays `Creature::reflectPercent`/`reflectFlat`
 * (creature.hpp:911-912), the proficiency perfect-shot bonus, the equipped-item
 * lookup, `Item::getCharges` (item.hpp:107) and
 * `g_game().transformItem(item, item->getID(), charges - 1)`
 * (player.cpp:1065, 7588, 7697, 7718) are constructor seams per AGENTS.md 1.1.
 * The tests store charges through `Item::setAttribute(CHARGES)`, the port's
 * `Item::getCharges` backing store.
 */
class PlayerAccessoryStatsTest {

    private fun newPlayer() =
        Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun abilityItem(id: Int, configure: Abilities.() -> Unit): Item {
        val type = ItemType(id = id, name = "Item $id")
        type.abilities = Abilities().apply(configure)
        return Item(id, 1, type)
    }

    private fun Item.chargesValue(): Int = (getAttribute(ItemAttribute.CHARGES) as? Long)?.toInt() ?: 0

    private fun Item.setCharges(value: Int) {
        setAttribute(ItemAttribute.CHARGES, value.toLong())
    }

    private fun Item.consumeCharge(transformed: MutableList<Item>) {
        setCharges(chargesValue() - 1)
        transformed.add(this)
    }

    @Test
    fun `getCleavePercent adds equipped abilities and consumes charges only for non-zero values`() {
        // Player::getCleavePercent (player.cpp:1051-1069).
        val charged = abilityItem(1) { cleavePercent = 12 }.apply { setCharges(1) }
        val plain = abilityItem(2) { cleavePercent = 0 }.apply { setCharges(1) }
        val transformed = mutableListOf<Item>()
        val state = PlayerCombatBonusState(
            getEquippedItems = { listOf(charged, plain) },
            getCharges = { it.chargesValue() },
            transformItemCharges = { it.consumeCharge(transformed) },
        )

        state.setCleavePercent(5)
        assertThat(state.getCleavePercent()).isEqualTo(17)
        assertThat(transformed).isEmpty()

        assertThat(state.getCleavePercent(useCharges = true)).isEqualTo(17)
        assertThat(transformed).containsExactly(charged)
        assertThat(state.getCleavePercent(useCharges = true)).isEqualTo(17)
        assertThat(transformed).containsExactly(charged)
    }

    @Test
    fun `setCleavePercent accumulates and floors at zero`() {
        // Player::setCleavePercent (player.cpp:7560-7562).
        val state = PlayerCombatBonusState()

        state.setCleavePercent(5)
        assertThat(state.getCleavePercent()).isEqualTo(5)

        state.setCleavePercent(-3)
        assertThat(state.getCleavePercent()).isEqualTo(2)

        state.setCleavePercent(-10)
        assertThat(state.getCleavePercent()).isZero()

        state.setCleavePercent(4)
        assertThat(state.getCleavePercent()).isEqualTo(4)
    }

    @Test
    fun `getPerfectShotDamage combines the stored map and the proficiency bonus`() {
        // Player::getPerfectShotDamage (player.cpp:7564-7593).
        val state = PlayerCombatBonusState(
            perfectShotBonus = { WeaponProficiencyPerfectShotBonus(range = 4, damage = 12.6) },
        )

        state.setPerfectShotDamage(3, 50)
        assertThat(state.getPerfectShotDamage(3)).isEqualTo(50)

        assertThat(state.getPerfectShotDamage(4)).isEqualTo(13)
        assertThat(state.getPerfectShotDamage(5)).isZero()
    }

    @Test
    fun `getPerfectShotDamage adds matching item abilities and consumes their charges`() {
        // player.cpp:7576-7592.
        val item = abilityItem(1) {
            perfectShotRange = 5
            perfectShotDamage = 20
        }.apply { setCharges(1) }
        val transformed = mutableListOf<Item>()
        val state = PlayerCombatBonusState(
            getEquippedItems = { listOf(item) },
            getCharges = { it.chargesValue() },
            transformItemCharges = { it.consumeCharge(transformed) },
        )

        assertThat(state.getPerfectShotDamage(5)).isEqualTo(20)
        assertThat(state.getPerfectShotDamage(6)).isZero()

        assertThat(state.getPerfectShotDamage(5, useCharges = true)).isEqualTo(20)
        assertThat(transformed).containsExactly(item)
        assertThat(state.getPerfectShotDamage(5, useCharges = true)).isEqualTo(20)
        assertThat(transformed).containsExactly(item)
    }

    @Test
    fun `setPerfectShotDamage stores the accumulated damage for the range`() {
        // Player::setPerfectShotDamage (player.cpp:7595-7600).
        val state = PlayerCombatBonusState()

        state.setPerfectShotDamage(3, 50)
        state.setPerfectShotDamage(3, -10)
        assertThat(state.getPerfectShotDamage(3)).isEqualTo(40)

        state.setPerfectShotDamage(3, -40)
        assertThat(state.getPerfectShotDamage(3)).isZero()

        state.setPerfectShotDamage(3, 7)
        assertThat(state.getPerfectShotDamage(3)).isEqualTo(7)
    }

    @Test
    fun `getReflectPercent adds the base array and matching item abilities`() {
        // Player::getReflectPercent (player.cpp:7683-7702).
        val fire = CombatType.FIREDAMAGE
        val item = abilityItem(1) { reflectPercent[fire.ordinal] = 15 }.apply { setCharges(1) }
        val transformed = mutableListOf<Item>()
        val state = PlayerCombatBonusState(
            getEquippedItems = { listOf(item) },
            getCharges = { it.chargesValue() },
            transformItemCharges = { it.consumeCharge(transformed) },
            baseReflectPercent = { index -> if (index == fire.ordinal) 10.0 else 0.0 },
        )

        assertThat(state.getReflectPercent(fire)).isEqualTo(25.0)
        assertThat(state.getReflectPercent(CombatType.ICEDAMAGE)).isZero()
        assertThat(transformed).isEmpty()

        assertThat(state.getReflectPercent(fire, useCharges = true)).isEqualTo(25.0)
        assertThat(transformed).containsExactly(item)
        assertThat(state.getReflectPercent(fire, useCharges = true)).isEqualTo(25.0)
        assertThat(transformed).containsExactly(item)
    }

    @Test
    fun `getReflectFlat adds the base array and matching item abilities`() {
        // Player::getReflectFlat (player.cpp:7704-7723).
        val energy = CombatType.ENERGYDAMAGE
        val item = abilityItem(1) { reflectFlat[energy.ordinal] = 7 }.apply { setCharges(1) }
        val transformed = mutableListOf<Item>()
        val state = PlayerCombatBonusState(
            getEquippedItems = { listOf(item) },
            getCharges = { it.chargesValue() },
            transformItemCharges = { it.consumeCharge(transformed) },
            baseReflectFlat = { index -> if (index == energy.ordinal) 5 else 0 },
        )

        assertThat(state.getReflectFlat(energy)).isEqualTo(12)
        assertThat(state.getReflectFlat(CombatType.ICEDAMAGE)).isZero()

        assertThat(state.getReflectFlat(energy, useCharges = true)).isEqualTo(12)
        assertThat(transformed).containsExactly(item)
        assertThat(state.getReflectFlat(energy, useCharges = true)).isEqualTo(12)
        assertThat(transformed).containsExactly(item)
    }

    @Test
    fun `getDamageAccuracy uses the six bolt and arrow values capped at 90`() {
        // Player::getDamageAccuracy (player.cpp:874-899), ammo branch.
        val player = newPlayer()
        val bolt = ItemType(id = 1, name = "Bolt", ammoType = Ammo_t.AMMO_BOLT.value)

        val accuracy = player.getDamageAccuracy(bolt, distanceSkill = 10)

        assertThat(accuracy).hasSize(6)
        assertThat(accuracy[0]).isCloseTo(13.2, within(1e-4))
        assertThat(accuracy[1]).isCloseTo(32.0, within(1e-4))
        assertThat(accuracy[2]).isCloseTo(20.0, within(1e-4))
        assertThat(accuracy[3]).isCloseTo(15.5, within(1e-4))
        assertThat(accuracy[4]).isCloseTo(13.2, within(1e-4))
        assertThat(accuracy[5]).isCloseTo(10.0, within(1e-4))

        val capped = player.getDamageAccuracy(
            ItemType(id = 2, name = "Arrow", ammoType = Ammo_t.AMMO_ARROW.value),
            distanceSkill = 100,
        )
        assertThat(capped).containsOnly(90.0)
    }

    @Test
    fun `getDamageAccuracy uses the seven fallback values capped at 75`() {
        // player.cpp:887-897, non-ammo branch.
        val player = newPlayer()
        val spear = ItemType(id = 1, name = "Spear", ammoType = Ammo_t.AMMO_SPEAR.value)

        val accuracy = player.getDamageAccuracy(spear, distanceSkill = 10)

        assertThat(accuracy).hasSize(7)
        assertThat(accuracy[0]).isEqualTo(11.0)
        assertThat(accuracy[1]).isCloseTo(43.2, within(1e-4))
        assertThat(accuracy[2]).isCloseTo(24.8, within(1e-4))
        assertThat(accuracy[3]).isCloseTo(16.25, within(1e-4))
        assertThat(accuracy[4]).isEqualTo(11.0)
        assertThat(accuracy[5]).isCloseTo(10.4, within(1e-4))
        assertThat(accuracy[6]).isCloseTo(8.4, within(1e-4))

        val capped = player.getDamageAccuracy(spear, distanceSkill = 100)
        assertThat(capped.slice(0 until 6)).containsOnly(75.0)
        assertThat(capped[6]).isCloseTo(71.4, within(1e-4))
    }

    @Test
    fun `getDamageAccuracy reads the distance skill from the player by default`() {
        // player.cpp:876: `getSkillLevel(SKILL_DISTANCE)`; fresh players start at level 10.
        val player = newPlayer()
        val spear = ItemType(id = 1, name = "Spear", ammoType = Ammo_t.AMMO_SPEAR.value)

        assertThat(player.getDamageAccuracy(spear)).isEqualTo(player.getDamageAccuracy(spear, distanceSkill = 10))
    }
}
