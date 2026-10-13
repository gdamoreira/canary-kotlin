package org.opentibia.canary.systems.proficiency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatTarget
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionPlayer
import org.opentibia.canary.combat.condition.ConditionType

/**
 * Transcript tests for the weapon-proficiency gain-on-hit/kill runtime, ported
 * from `src/creatures/players/components/weapon_proficiency.cpp`:
 *
 *  - `applyOn` (weapon_proficiency.cpp:1378-1409), called on hit from
 *    `Combat::doCombat` (combat.cpp:883-884) and on kill from
 *    `Monster::onDeath` (monster.cpp:3280-3281)
 *  - `addExperience` (weapon_proficiency.cpp:738-786) with
 *    `getExperienceArray` (664-680), `nextLevelExperience` (682-715) and
 *    `getMaxExperience` (717-736)
 *  - `getBosstiaryExperience` / `getBestiaryExperience` (788-808), used by
 *    `Monster::onDeath` (monster.cpp:3294-3302)
 *  - `addStat` / `getStat` / `resetStats` (weapon_proficiency.cpp:977-998)
 */
class WeaponProficiencyGainTest {

    // ---- applyOn --------------------------------------------------------------

    @Test
    fun `applyOn heals life on hit by the stored stat`() {
        val f = fixture()
        f.proficiency.addStat(WeaponProficiencyBonus.LIFE_GAIN_ON_HIT, 120.0)

        f.proficiency.applyOn(WeaponProficiencyHealth.LIFE, WeaponProficiencyGain.HIT)

        assertThat(f.player.healthPoints).isEqualTo(500 + 120)
    }

    @Test
    fun `applyOn restores mana on hit by the stored stat`() {
        val f = fixture()
        f.proficiency.addStat(WeaponProficiencyBonus.MANA_GAIN_ON_HIT, 30.0)

        f.proficiency.applyOn(WeaponProficiencyHealth.MANA, WeaponProficiencyGain.HIT)

        assertThat(f.player.manaPoints).isEqualTo(100 + 30)
    }

    @Test
    fun `applyOn uses the kill stats for kill gains`() {
        val f = fixture()
        f.proficiency.addStat(WeaponProficiencyBonus.LIFE_GAIN_ON_HIT, 10.0)
        f.proficiency.addStat(WeaponProficiencyBonus.LIFE_GAIN_ON_KILL, 60.0)
        f.proficiency.addStat(WeaponProficiencyBonus.MANA_GAIN_ON_KILL, 25.0)

        f.proficiency.applyOn(WeaponProficiencyHealth.LIFE, WeaponProficiencyGain.KILL)
        f.proficiency.applyOn(WeaponProficiencyHealth.MANA, WeaponProficiencyGain.KILL)

        assertThat(f.player.healthPoints).isEqualTo(500 + 60)
        assertThat(f.player.manaPoints).isEqualTo(100 + 25)
    }

    @Test
    fun `applyOn ignores non-positive stats`() {
        val f = fixture()

        f.proficiency.applyOn(WeaponProficiencyHealth.LIFE, WeaponProficiencyGain.HIT)

        assertThat(f.player.healthPoints).isEqualTo(500)
        assertThat(f.player.manaPoints).isEqualTo(100)
    }

    @Test
    fun `applyOn truncates a fractional stat to int like the canary cast`() {
        val f = fixture()
        f.proficiency.addStat(WeaponProficiencyBonus.LIFE_GAIN_ON_HIT, 12.75)

        f.proficiency.applyOn(WeaponProficiencyHealth.LIFE, WeaponProficiencyGain.HIT)

        assertThat(f.player.healthPoints).isEqualTo(500 + 12)
    }

    // ---- addExperience --------------------------------------------------------

    @Test
    fun `addExperience creates the state with the gain and notifies the player`() {
        val f = fixture()

        f.proficiency.addExperience(2000, WEAPON_ID)

        assertThat(f.proficiency.getExperience(WEAPON_ID)).isEqualTo(2000)
        assertThat(f.player.sentProficiencies).containsExactly(WEAPON_ID)
    }

    @Test
    fun `addExperience scales the gain by the configured multiplier`() {
        val f = fixture(gainMultiplier = 0.5)

        f.proficiency.addExperience(2000, WEAPON_ID)

        assertThat(f.proficiency.getExperience(WEAPON_ID)).isEqualTo(1000)
    }

    @Test
    fun `addExperience returns without state when the scaled gain is zero`() {
        val f = fixture(gainMultiplier = 0.01)

        f.proficiency.addExperience(10, WEAPON_ID)

        assertThat(f.proficiency.getExperience(WEAPON_ID)).isEqualTo(0)
        assertThat(f.player.sentProficiencies).isEmpty()
    }

    @Test
    fun `addExperience uses the equipped weapon when the id is zero`() {
        val f = fixture()
        f.player.weaponId = WEAPON_ID

        f.proficiency.addExperience(2000)

        assertThat(f.proficiency.getExperience(WEAPON_ID)).isEqualTo(2000)
    }

    @Test
    fun `addExperience ignores an invalid weapon id`() {
        val f = fixture(itemCount = 2)

        f.proficiency.addExperience(2000, 5)

        assertThat(f.player.sentProficiencies).isEmpty()
        assertThat(f.debugLogs).containsExactly("[addExperience] - Weapon ID '5' has no proficiency assigned")
    }

    @Test
    fun `addExperience ignores a weapon without an assigned proficiency`() {
        val f = fixture(itemsAt = mapOf(WEAPON_ID to FakeItemType(proficiencyId = 0)))

        f.proficiency.addExperience(2000, WEAPON_ID)

        assertThat(f.player.sentProficiencies).isEmpty()
        assertThat(f.debugLogs).containsExactly("[addExperience] - Weapon ID '100' has no proficiency assigned")
    }

    @Test
    fun `addExperience accumulates on the stored state`() {
        val f = fixture()
        f.proficiency.addExperience(1000, WEAPON_ID)

        f.proficiency.addExperience(2000, WEAPON_ID)

        assertThat(f.proficiency.getExperience(WEAPON_ID)).isEqualTo(3000)
        assertThat(f.player.sentProficiencies).containsExactly(WEAPON_ID, WEAPON_ID)
    }

    @Test
    fun `addExperience caps at the mastery maximum and sets mastered`() {
        val f = fixture()
        f.proficiency.addExperience(29_999_000, WEAPON_ID)

        f.proficiency.addExperience(2000, WEAPON_ID)

        assertThat(f.proficiency.getExperience(WEAPON_ID)).isEqualTo(30_000_000)
    }

    @Test
    fun `addExperience clamps the first insert to the mastery maximum`() {
        val f = fixture()

        f.proficiency.addExperience(40_000_000, WEAPON_ID)

        assertThat(f.proficiency.getExperience(WEAPON_ID)).isEqualTo(30_000_000)
    }

    @Test
    fun `getExperience falls back to the equipped weapon`() {
        val f = fixture()
        f.player.weaponId = WEAPON_ID
        f.proficiency.addExperience(2000, WEAPON_ID)

        assertThat(f.proficiency.getExperience()).isEqualTo(2000)
    }

    // ---- experience tables ----------------------------------------------------

    @Test
    fun `getExperienceArray selects crossbow knight and standard tables`() {
        val crossbow = fixture(itemsAt = mapOf(WEAPON_ID to FakeItemType(ammoType = AMMO_BOLT)))
        val knight = fixture(
            itemsAt = mapOf(WEAPON_ID to FakeItemType(weaponType = WEAPON_SWORD, vocationString = "Elite Knight")),
        )
        val standard = fixture(
            itemsAt = mapOf(WEAPON_ID to FakeItemType(weaponType = WEAPON_WAND, vocationString = "Master Sorcerer")),
        )

        assertThat(crossbow.proficiency.getExperienceArray(WEAPON_ID))
            .isSameAs(WeaponProficiencyExperience.CROSSBOW_EXPERIENCE)
        assertThat(knight.proficiency.getExperienceArray(WEAPON_ID))
            .isSameAs(WeaponProficiencyExperience.KNIGHT_EXPERIENCE)
        assertThat(standard.proficiency.getExperienceArray(WEAPON_ID))
            .isSameAs(WeaponProficiencyExperience.STANDARD_EXPERIENCE)
    }

    @Test
    fun `getExperienceArray falls back to standard for an invalid weapon and logs`() {
        val f = fixture(itemCount = 2)

        assertThat(f.proficiency.getExperienceArray(5)).isSameAs(WeaponProficiencyExperience.STANDARD_EXPERIENCE)
        assertThat(f.errorLogs).containsExactly("[getExperienceArray] - Invalid weapon ID: 5")
    }

    @Test
    fun `nextLevelExperience returns the first requirement for an unknown weapon state`() {
        val f = fixture()

        assertThat(f.proficiency.nextLevelExperience(WEAPON_ID)).isEqualTo(1750)
    }

    @Test
    fun `nextLevelExperience returns the remaining requirement for a stored state`() {
        val f = fixture()
        f.proficiency.addExperience(1750, WEAPON_ID)

        assertThat(f.proficiency.nextLevelExperience(WEAPON_ID)).isEqualTo(25_000 - 1750)
    }

    @Test
    fun `nextLevelExperience returns zero at the maximum`() {
        val f = fixture()
        f.proficiency.addExperience(30_000_000, WEAPON_ID)

        assertThat(f.proficiency.nextLevelExperience(WEAPON_ID)).isEqualTo(0)
    }

    @Test
    fun `getMaxExperience is the last mastery tier`() {
        val f = fixture()

        assertThat(f.proficiency.getMaxExperience(WEAPON_ID)).isEqualTo(30_000_000)
    }

    // ---- kill path ------------------------------------------------------------

    @Test
    fun `monster onDeath kill path applies the kill gains and the boss and bestiary experience`() {
        val f = fixture()
        f.proficiency.addStat(WeaponProficiencyBonus.LIFE_GAIN_ON_KILL, 50.0)
        f.proficiency.addStat(WeaponProficiencyBonus.MANA_GAIN_ON_KILL, 25.0)

        // Monster::onDeath (monster.cpp:3280-3301).
        f.proficiency.applyOn(WeaponProficiencyHealth.LIFE, WeaponProficiencyGain.KILL)
        f.proficiency.applyOn(WeaponProficiencyHealth.MANA, WeaponProficiencyGain.KILL)

        val bossExperience = f.proficiency.getBosstiaryExperience(BosstiaryRarity.RARITY_BANE)
        if (bossExperience > 0) {
            f.proficiency.addExperience(bossExperience, WEAPON_ID)
        }
        val bestiaryExperience = f.proficiency.getBestiaryExperience(3)
        if (bestiaryExperience > 0) {
            f.proficiency.addExperience(bestiaryExperience, WEAPON_ID)
        }

        assertThat(f.player.healthPoints).isEqualTo(500 + 50)
        assertThat(f.player.manaPoints).isEqualTo(100 + 25)
        assertThat(bossExperience).isEqualTo(500)
        assertThat(bestiaryExperience).isEqualTo(100)
        assertThat(f.proficiency.getExperience(WEAPON_ID)).isEqualTo(600)
    }

    // ---- stats ----------------------------------------------------------------

    @Test
    fun `getStat accumulates and resetStats clears`() {
        val f = fixture()

        f.proficiency.addStat(WeaponProficiencyBonus.ATTACK_DAMAGE, 5.0)
        f.proficiency.addStat(WeaponProficiencyBonus.ATTACK_DAMAGE, 2.5)
        assertThat(f.proficiency.getStat(WeaponProficiencyBonus.ATTACK_DAMAGE)).isEqualTo(7.5)

        f.proficiency.resetStats()
        assertThat(f.proficiency.getStat(WeaponProficiencyBonus.ATTACK_DAMAGE)).isEqualTo(0.0)
    }

    // ---- fixture --------------------------------------------------------------

    private fun fixture(
        gainMultiplier: Double = 1.0,
        itemsAt: Map<Int, ProficiencyItemType> = mapOf(WEAPON_ID to FakeItemType(weaponType = WEAPON_SWORD)),
        itemCount: Int = WEAPON_ID + 1,
    ): Fixture = Fixture(gainMultiplier, itemsAt, itemCount)

    private class Fixture(
        gainMultiplier: Double,
        itemsAt: Map<Int, ProficiencyItemType>,
        itemCount: Int,
    ) {
        val player = FakeGainPlayer()
        val errorLogs = mutableListOf<String>()
        val debugLogs = mutableListOf<String>()
        val proficiency = WeaponProficiency(
            player = player,
            items = ListWeaponProficiencyItems(itemCount, itemsAt),
            proficiencies = mapOf(1 to Proficiency(id = 1, maxLevel = 5)),
            gainMultiplier = gainMultiplier,
            logError = { errorLogs.add(it) },
            logDebug = { debugLogs.add(it) },
        )
    }

    private class ListWeaponProficiencyItems(
        override val count: Int,
        private val itemsAt: Map<Int, ProficiencyItemType>,
    ) : WeaponProficiencyItems {
        override fun get(weaponId: Int): ProficiencyItemType =
            itemsAt[weaponId] ?: FakeItemType(proficiencyId = 0)
    }

    private class FakeItemType(
        override val ammoType: Int = 0,
        override val weaponType: Int = 0,
        override val vocationString: String = "",
        override val proficiencyId: Int = 1,
    ) : ProficiencyItemType

    private class FakeGainPlayer : WeaponProficiencyPlayer, CombatTarget {
        var weaponId = 0
        var healthPoints = 500
        var manaPoints = 100
        val sentProficiencies = mutableListOf<Int>()

        override fun getSkillLevel(skill: Int): Int = 0

        override fun getWeapon(ignoreWeaponProficiency: Boolean): Boolean = true

        override fun getWeaponId(ignoreWeaponProficiency: Boolean): Int = weaponId

        override fun sendWeaponProficiency(weaponId: Int) {
            sentProficiencies.add(weaponId)
        }

        override fun getCreature(): CombatTarget = this

        override fun getHealth(): Int = healthPoints

        override fun getMaxHealth(): Int = 1000

        override fun addHealth(amount: Int): Int {
            healthPoints += amount
            return healthPoints
        }

        override fun drainHealth(amount: Int): Int {
            healthPoints -= amount
            return healthPoints
        }

        override fun getMana(): Int = manaPoints

        override fun getMaxMana(): Int = 1000

        override fun changeMana(amount: Int): Int {
            manaPoints += amount
            return manaPoints
        }

        override fun isAttackable(): Boolean = true

        override fun isImmune(combatType: CombatType): Boolean = false

        override fun getDefense(): Int = 0

        override fun getArmor(): Int = 0

        override fun hasDefense(): Boolean = false

        override fun canUseDefense(): Boolean = false

        override fun getMitigation(): Double = 0.0

        override fun getID(): Int = 1

        override fun getPlayer(): ConditionPlayer? = null

        override fun isImmune(conditionType: ConditionType): Boolean = false

        override fun addCombatCondition(condition: Condition, attackerPlayer: Boolean): Boolean = false
    }

    private companion object {
        const val WEAPON_ID = 100
        const val AMMO_BOLT = 1
        const val WEAPON_SWORD = 1
        const val WEAPON_WAND = 6
    }
}
