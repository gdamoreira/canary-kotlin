package org.opentibia.canary.systems.wheel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionGeneric
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.items.Ammo_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.SLOTP_TWO_HAND
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.map.Tile
import org.opentibia.canary.network.protocol.InMemoryProtocolSink

/**
 * Transcribes the remaining `PlayerWheel` APIs of Canary
 * (src/creatures/players/components/wheel/player_wheel.cpp):
 *
 *  - spell upgrades: `getSpellUpgrade` (3863), `upgradeSpell` (3475),
 *    `downgradeSpell` (3489), `resetUpgradedSpells` (3456);
 *  - revelation spell queries: `getSpellAdditionalArea` (918),
 *    `getSpellAdditionalTarget` (944), `getSpellAdditionalDuration` (970);
 *  - `scrollAcquired` (1801) / `unlockScroll` (1809);
 *  - `onThink` (3369) with `checkBattleInstinct` (2941),
 *    `checkPositionalTactics` (2965), `checkBallisticMastery` (3005),
 *    `checkCombatMastery` (3048), `checkDivineEmpowerment` (3094).
 */
class PlayerWheelApisTest {

    /** `OTSYS_TIME()` seam; tests advance it to cross on-think timers. */
    private var now = 1_000_000L

    /** `g_game().reloadCreature(m_player.getPlayer())` records. */
    private val reloads = mutableListOf<Long>()

    /** Inert map access: the injected `findNearbyMonsters` supplies the snapshot. */
    private object NullMapAccess : SpectatorMapAccess {
        override fun getMapSector(x: Int, y: Int): SpectatorSector? = null
    }

    private fun player(id: Long = 1L): Player =
        Player(id, "Hero", maxHealth = 1000, health = 1000, position = Position(100, 100, 7))

    private fun wheel(
        vocationCipId: Int = IOWheel.VOCATION_KNIGHT_CIP,
        player: Player? = null,
        monsters: List<Monster> = emptyList(),
    ): PlayerWheel = PlayerWheel(
        vocationCipId = vocationCipId,
        level = 250,
        player = player,
        otsysTimeMs = { now },
        findNearbyMonsters = { Spectators(NullMapAccess).insertAll(monsters) },
        reloadCreature = { reloads.add(it.id) },
    )

    private fun equip(player: Player, type: ItemType) {
        player.weaponTypeProvider = { WeaponType_t.WEAPON_DISTANCE }
        player.addItem(Item(type.id, type = type), Slots_t.CONST_SLOT_LEFT)
    }

    private fun monster(id: Long): Monster =
        Monster(id, "Rat", maxHealth = 100, health = 100, position = Position(100, 100, 7))

    // --- spell upgrades ---------------------------------------------------------

    @Test
    fun `spell upgrade starts at none and caps at max`() {
        val state = wheel()
        assertThat(state.getSpellUpgrade("Groundshaker")).isEqualTo(WheelSpellGrade.NONE)

        state.upgradeSpell("Groundshaker")
        assertThat(state.getSpellUpgrade("Groundshaker")).isEqualTo(WheelSpellGrade.REGULAR)

        state.upgradeSpell("Groundshaker")
        assertThat(state.getSpellUpgrade("Groundshaker")).isEqualTo(WheelSpellGrade.UPGRADED)

        state.upgradeSpell("Groundshaker")
        assertThat(state.getSpellUpgrade("Groundshaker")).isEqualTo(WheelSpellGrade.MAX)

        state.upgradeSpell("Groundshaker")
        assertThat(state.getSpellUpgrade("Groundshaker")).isEqualTo(WheelSpellGrade.MAX)
    }

    @Test
    fun `spell downgrade erases regular and steps upgraded and max down`() {
        val state = wheel()
        state.upgradeSpell("Groundshaker")
        state.downgradeSpell("Groundshaker")
        assertThat(state.getSpellUpgrade("Groundshaker")).isEqualTo(WheelSpellGrade.NONE)

        state.upgradeSpell("Groundshaker")
        state.upgradeSpell("Groundshaker")
        state.downgradeSpell("Groundshaker")
        assertThat(state.getSpellUpgrade("Groundshaker")).isEqualTo(WheelSpellGrade.REGULAR)

        state.upgradeSpell("Groundshaker")
        state.upgradeSpell("Groundshaker")
        state.downgradeSpell("Groundshaker")
        assertThat(state.getSpellUpgrade("Groundshaker")).isEqualTo(WheelSpellGrade.UPGRADED)
    }

    @Test
    fun `upgrading learns the instant spell once and reset forgets it`() {
        val player = player()
        val state = wheel(player = player)

        state.upgradeSpell("Groundshaker")
        assertThat(player.hasLearnedInstantSpell("Groundshaker")).isTrue()

        state.upgradeSpell("Groundshaker")
        assertThat(player.hasLearnedInstantSpell("Groundshaker")).isTrue()

        state.resetUpgradedSpells()
        assertThat(player.hasLearnedInstantSpell("Groundshaker")).isFalse()
        assertThat(state.getSpellUpgrade("Groundshaker")).isEqualTo(WheelSpellGrade.NONE)
    }

    @Test
    fun `monk shrine spells are graded without learning or forgetting`() {
        val player = player()
        val state = wheel(player = player)

        state.upgradeSpell("Mystic Repulse")
        assertThat(state.getSpellUpgrade("Mystic Repulse")).isEqualTo(WheelSpellGrade.REGULAR)
        assertThat(player.hasLearnedInstantSpell("Mystic Repulse")).isFalse()

        state.resetUpgradedSpells()
        assertThat(player.hasLearnedInstantSpell("Mystic Repulse")).isFalse()
    }

    @Test
    fun `already learned spells are graded but not forgotten by reset`() {
        val player = player()
        player.learnInstantSpell("Groundshaker")
        val state = wheel(player = player)

        state.upgradeSpell("Groundshaker")
        assertThat(state.getSpellUpgrade("Groundshaker")).isEqualTo(WheelSpellGrade.REGULAR)

        state.resetUpgradedSpells()
        assertThat(player.hasLearnedInstantSpell("Groundshaker")).isTrue()
    }

    @Test
    fun `downgrading a spell does not forget it`() {
        val player = player()
        val state = wheel(player = player)
        state.upgradeSpell("Groundshaker")
        state.downgradeSpell("Groundshaker")
        assertThat(player.hasLearnedInstantSpell("Groundshaker")).isTrue()
    }

    @Test
    fun `reset clears stages major stats and the focus mastery timer`() {
        val state = wheel()
        state.setStage(WheelStage.GIFT_OF_LIFE, 3)
        state.setMajorStat(WheelMajor.MELEE, 5)
        state.setOnThinkTimer(WheelOnThink.FOCUS_MASTERY, now + 5000)

        state.resetUpgradedSpells()

        assertThat(state.getStage(WheelStage.GIFT_OF_LIFE)).isZero()
        assertThat(state.getMajorStat(WheelMajor.MELEE)).isZero()
        assertThat(state.getOnThinkTimer(WheelOnThink.FOCUS_MASTERY)).isZero()
    }

    // --- additional area/target/duration ----------------------------------------

    @Test
    fun `additional queries need an upgraded spell`() {
        val paladin = wheel(IOWheel.VOCATION_PALADIN_CIP)
        assertThat(paladin.getSpellAdditionalTarget("Divine Dazzle")).isZero()
        assertThat(paladin.getSpellAdditionalDuration("Divine Dazzle")).isZero()
        assertThat(paladin.getSpellAdditionalArea("Mass Healing")).isFalse()
    }

    @Test
    fun `additional target follows the selected grade and falls back`() {
        val knight = wheel(IOWheel.VOCATION_KNIGHT_CIP)
        knight.upgradeSpell("Chivalrous Challenge")
        assertThat(knight.getSpellAdditionalTarget("Chivalrous Challenge")).isZero()
        knight.upgradeSpell("Chivalrous Challenge")
        assertThat(knight.getSpellAdditionalTarget("Chivalrous Challenge")).isEqualTo(1)
        knight.upgradeSpell("Chivalrous Challenge")
        assertThat(knight.getSpellAdditionalTarget("Chivalrous Challenge")).isZero()

        val paladin = wheel(IOWheel.VOCATION_PALADIN_CIP)
        paladin.upgradeSpell("Divine Dazzle")
        assertThat(paladin.getSpellAdditionalTarget("Divine Dazzle")).isEqualTo(1)
        paladin.upgradeSpell("Divine Dazzle")
        assertThat(paladin.getSpellAdditionalTarget("Divine Dazzle")).isEqualTo(1)

        val monk = wheel(IOWheel.VOCATION_MONK_CIP)
        monk.upgradeSpell("Chained Penance")
        monk.upgradeSpell("Chained Penance")
        assertThat(monk.getSpellAdditionalTarget("Chained Penance")).isEqualTo(2)
    }

    @Test
    fun `additional area reads the vocation spell table`() {
        val druid = wheel(IOWheel.VOCATION_DRUID_CIP)
        druid.upgradeSpell("Mass Healing")
        assertThat(druid.getSpellAdditionalArea("Mass Healing")).isFalse()
        druid.upgradeSpell("Mass Healing")
        assertThat(druid.getSpellAdditionalArea("Mass Healing")).isTrue()

        val sorcerer = wheel(IOWheel.VOCATION_SORCERER_CIP)
        sorcerer.upgradeSpell("Sap Strength")
        assertThat(sorcerer.getSpellAdditionalArea("Sap Strength")).isTrue()

        val knight = wheel(IOWheel.VOCATION_KNIGHT_CIP)
        knight.upgradeSpell("Groundshaker")
        assertThat(knight.getSpellAdditionalArea("Groundshaker")).isFalse()
    }

    @Test
    fun `additional duration uses the vocation table and has no monk branch`() {
        val paladin = wheel(IOWheel.VOCATION_PALADIN_CIP)
        paladin.upgradeSpell("Divine Dazzle")
        assertThat(paladin.getSpellAdditionalDuration("Divine Dazzle")).isZero()
        paladin.upgradeSpell("Divine Dazzle")
        assertThat(paladin.getSpellAdditionalDuration("Divine Dazzle")).isEqualTo(4)

        val monk = wheel(IOWheel.VOCATION_MONK_CIP)
        monk.upgradeSpell("Chained Penance")
        monk.upgradeSpell("Chained Penance")
        assertThat(monk.getSpellAdditionalDuration("Chained Penance")).isZero()
    }

    @Test
    fun `additional queries ignore spells from other vocations`() {
        val knight = wheel(IOWheel.VOCATION_KNIGHT_CIP)
        knight.upgradeSpell("Divine Dazzle")
        assertThat(knight.getSpellAdditionalTarget("Divine Dazzle")).isZero()
        assertThat(knight.getSpellAdditionalDuration("Divine Dazzle")).isZero()
        assertThat(knight.getSpellAdditionalArea("Divine Dazzle")).isFalse()
    }

    // --- scrolls ----------------------------------------------------------------

    @Test
    fun `unlockScroll accepts known scrolls once and scrollAcquired reflects it`() {
        val state = wheel()
        assertThat(state.scrollAcquired("basic")).isFalse()
        assertThat(state.unlockScroll("basic")).isTrue()
        assertThat(state.scrollAcquired("basic")).isTrue()
        assertThat(state.unlockScroll("basic")).isFalse()
        assertThat(state.unlockScroll("advanced")).isTrue()
        assertThat(state.unlockScroll("unknown")).isFalse()
    }

    // --- onThink ----------------------------------------------------------------

    @Test
    fun `onThink decrements the gift of life cooldown when its timer elapsed`() {
        val state = wheel()
        state.setGiftOfCooldown(10, isOnThink = true)
        state.setOnThinkTimer(WheelOnThink.GIFT_OF_LIFE, now)

        state.onThink()

        assertThat(state.getGiftOfCooldown()).isEqualTo(9)
        assertThat(state.getOnThinkTimer(WheelOnThink.GIFT_OF_LIFE)).isEqualTo(now + 1000)
    }

    @Test
    fun `onThink leaves the gift cooldown alone until its timer elapses`() {
        val state = wheel()
        state.setGiftOfCooldown(10, isOnThink = false)

        state.onThink()

        assertThat(state.getGiftOfCooldown()).isEqualTo(10)
    }

    @Test
    fun `onThink zeroes major stats and notifies the client when idle`() {
        val player = player()
        val sink = InMemoryProtocolSink(player)
        player.protocolSink = sink
        val state = wheel(player = player)
        state.setMajorStat(WheelMajor.MELEE, 5)
        state.setMajorStat(WheelMajor.CRITICAL_DMG_2, 7)
        state.setStage(WheelStage.GIFT_OF_LIFE, 2)

        state.onThink()

        assertThat(state.getMajorStat(WheelMajor.MELEE)).isZero()
        assertThat(state.getMajorStat(WheelMajor.CRITICAL_DMG_2)).isZero()
        assertThat(sink.frames).isNotEmpty()
        assertThat(reloads).containsExactly(player.id)
        assertThat(state.getStage(WheelStage.GIFT_OF_LIFE)).isEqualTo(2)
    }

    @Test
    fun `onThink force runs the instant checks even while idle`() {
        val state = wheel()
        state.setSpellInstant("Positional Tactics", true)
        state.setMajorStat(WheelMajor.MELEE, 5)

        state.onThink()
        assertThat(state.getMajorStat(WheelMajor.DISTANCE)).isZero()

        state.onThink(force = true)
        assertThat(state.getMajorStat(WheelMajor.DISTANCE)).isEqualTo(3)
    }

    @Test
    fun `onThink respects the per-check timers while in fight`() {
        val player = player()
        player.addCondition(
            ConditionGeneric(ConditionId.CONDITIONID_DEFAULT, ConditionType.CONDITION_INFIGHT, ticks = 100_000),
        )
        val state = wheel(player = player)
        state.setSpellInstant("Positional Tactics", true)

        state.onThink()
        assertThat(state.getMajorStat(WheelMajor.DISTANCE)).isEqualTo(3)
        assertThat(state.getOnThinkTimer(WheelOnThink.POSITIONAL_TACTICS)).isEqualTo(now + 2000)

        state.setMajorStat(WheelMajor.DISTANCE, 0)
        now += 1000
        state.onThink()
        assertThat(state.getMajorStat(WheelMajor.DISTANCE)).isZero()

        now += 1001
        state.onThink()
        assertThat(state.getMajorStat(WheelMajor.DISTANCE)).isEqualTo(3)
    }

    // --- onThink checks ---------------------------------------------------------

    @Test
    fun `battle instinct scales melee and shield with nearby monsters`() {
        val state = wheel(player = player(), monsters = List(6) { monster(it.toLong()) })

        assertThat(state.checkBattleInstinct()).isTrue()

        assertThat(state.getMajorStat(WheelMajor.MELEE)).isEqualTo(2)
        assertThat(state.getMajorStat(WheelMajor.SHIELD)).isEqualTo(12)
        assertThat(state.getOnThinkTimer(WheelOnThink.BATTLE_INSTINCT)).isEqualTo(now + 2000)
    }

    @Test
    fun `battle instinct clears the skills when fewer than five monsters are nearby`() {
        val state = wheel(player = player(), monsters = List(4) { monster(it.toLong()) })
        state.setMajorStat(WheelMajor.MELEE, 3)
        state.setMajorStat(WheelMajor.SHIELD, 18)

        assertThat(state.checkBattleInstinct()).isTrue()

        assertThat(state.getMajorStat(WheelMajor.MELEE)).isZero()
        assertThat(state.getMajorStat(WheelMajor.SHIELD)).isZero()
    }

    @Test
    fun `positional tactics swaps distance and specialized magic with monsters nearby`() {
        val empty = wheel(player = player())
        empty.checkPositionalTactics()
        assertThat(empty.getMajorStat(WheelMajor.DISTANCE)).isEqualTo(3)
        assertThat(empty.getSpecializedMagic(CombatType.HOLYDAMAGE)).isZero()
        assertThat(empty.getSpecializedMagic(CombatType.HEALING)).isZero()

        val crowded = wheel(player = player(), monsters = listOf(monster(9L)))
        crowded.checkPositionalTactics()
        assertThat(crowded.getMajorStat(WheelMajor.DISTANCE)).isZero()
        assertThat(crowded.getSpecializedMagic(CombatType.HOLYDAMAGE)).isEqualTo(3)
        assertThat(crowded.getSpecializedMagic(CombatType.HEALING)).isEqualTo(3)
    }

    @Test
    fun `ballistic mastery grants bolt critical damage and arrow elemental damage`() {
        val boltPlayer = player()
        equip(boltPlayer, ItemType(100, "crossbow", ammoType = Ammo_t.AMMO_BOLT.value))
        val bolt = wheel(player = boltPlayer)
        bolt.checkBallisticMastery()
        assertThat(bolt.getMajorStat(WheelMajor.CRITICAL_DMG)).isEqualTo(1000)
        assertThat(bolt.getMajorStat(WheelMajor.PHYSICAL_DMG)).isZero()
        assertThat(bolt.getMajorStat(WheelMajor.HOLY_DMG)).isZero()

        val arrowPlayer = player()
        equip(arrowPlayer, ItemType(101, "bow", ammoType = Ammo_t.AMMO_ARROW.value))
        val arrow = wheel(player = arrowPlayer)
        arrow.checkBallisticMastery()
        assertThat(arrow.getMajorStat(WheelMajor.CRITICAL_DMG)).isZero()
        assertThat(arrow.getMajorStat(WheelMajor.PHYSICAL_DMG)).isEqualTo(2)
        assertThat(arrow.getMajorStat(WheelMajor.HOLY_DMG)).isEqualTo(2)

        val unarmed = wheel()
        assertThat(unarmed.checkBallisticMastery()).isFalse()
        assertThat(unarmed.getMajorStat(WheelMajor.CRITICAL_DMG)).isZero()
    }

    @Test
    fun `combat mastery swaps critical damage and defense by weapon hands and stage`() {
        val twoHandPlayer = player()
        equip(twoHandPlayer, ItemType(102, "big axe", slotPosition = SLOTP_TWO_HAND))
        val twoHand = wheel(player = twoHandPlayer)
        twoHand.setStage(WheelStage.COMBAT_MASTERY, 2)

        assertThat(twoHand.checkCombatMastery()).isTrue()
        assertThat(twoHand.getMajorStat(WheelMajor.CRITICAL_DMG_2)).isEqualTo(800)
        assertThat(twoHand.getMajorStat(WheelMajor.DEFENSE)).isZero()

        val oneHand = wheel(player = player())
        oneHand.setStage(WheelStage.COMBAT_MASTERY, 3)

        assertThat(oneHand.checkCombatMastery()).isTrue()
        assertThat(oneHand.getMajorStat(WheelMajor.CRITICAL_DMG_2)).isZero()
        assertThat(oneHand.getMajorStat(WheelMajor.DEFENSE)).isEqualTo(30)
    }

    @Test
    fun `divine empowerment reads the owned tile item and scales by stage`() {
        val player = player()
        val tile = Tile(Position(100, 100, 7))
        val field = Item(40450)
        field.setOwner(player.getGUID())
        tile.addItem(field)
        player.setParent(tile)
        val state = wheel(player = player)
        state.setStage(WheelStage.DIVINE_EMPOWERMENT, 2)

        assertThat(state.checkDivineEmpowerment()).isTrue()
        assertThat(state.getMajorStat(WheelMajor.DAMAGE)).isEqualTo(10)
        assertThat(state.getOnThinkTimer(WheelOnThink.DIVINE_EMPOWERMENT)).isEqualTo(now + 1000)
    }

    @Test
    fun `divine empowerment ignores unowned items`() {
        val player = player()
        val tile = Tile(Position(100, 100, 7))
        tile.addItem(Item(40450))
        player.setParent(tile)
        val state = wheel(player = player)
        state.setStage(WheelStage.DIVINE_EMPOWERMENT, 3)

        assertThat(state.checkDivineEmpowerment()).isFalse()
        assertThat(state.getMajorStat(WheelMajor.DAMAGE)).isZero()
    }

    // --- specialized magic ------------------------------------------------------

    @Test
    fun `specialized magic is stored per combat type and ignores none`() {
        val state = wheel()
        state.setSpecializedMagic(CombatType.HOLYDAMAGE, 7)
        assertThat(state.getSpecializedMagic(CombatType.HOLYDAMAGE)).isEqualTo(7)

        state.setSpecializedMagic(CombatType.NONE, 7)
        assertThat(state.getSpecializedMagic(CombatType.NONE)).isZero()
    }
}
