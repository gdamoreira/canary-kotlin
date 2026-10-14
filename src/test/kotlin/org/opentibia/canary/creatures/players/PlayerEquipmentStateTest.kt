package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.items.Ammo_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.systems.wheel.WheelStat

/**
 * Transcribes the Player equipment/weapon skill helpers
 * (src/creatures/players/player.cpp:396-870, 3887-3897, 5949-5969, 12923-12941):
 *
 * - `Player::getWeaponType` (player.cpp:466-472)
 * - `Player::getWeaponSkill` (player.cpp:474-514)
 * - `Player::getDistanceAttackSkill` (player.cpp:516-520)
 * - `Player::getAttackSkill` (player.cpp:522-566)
 * - `Player::getWeaponSkillId` (player.cpp:568-586)
 * - `Player::hasQuiverEquipped` (player.cpp:426-429)
 * - `Player::hasWeaponDistanceEquipped` (player.cpp:431-434)
 * - `Player::getQuiverAmmoOfType` (player.cpp:436-451)
 * - `Player::getArmor` (player.cpp:658-671)
 * - `Player::getMantra` (player.cpp:671-694)
 * - `Player::getPartyMantra` (player.cpp:696-710)
 * - `Player::updatePartyMantra` (player.cpp:713-719)
 * - `Player::getShieldAndWeapon` (player.cpp:721-747)
 * - `Player::getDefense` (player.cpp:776-815)
 * - `Player::getDefenseEquipment` (player.cpp:817-838)
 * - `Player::getAttackFactor` (player.cpp:840-851)
 * - `Player::getDefenseFactor` (player.cpp:853-870)
 * - `Player::getDodgeChance` (player.cpp:12923-12941)
 * - `Player::hasShield` (player.cpp:3887-3897)
 * - `Player::getEquippedItems` (player.cpp:5949-5969)
 * - `Player::getAttackSpeed` (player.cpp:7346-7348)
 * - `Player::calculateFlatDamageHealing` (player.cpp:588-610)
 *
 * The vocation registry, wheel and party are the port's real seams; the
 * `g_game()`/`Outfits::getInstance()` calls behind quivers/mounts are injected.
 */
class PlayerEquipmentStateTest {

    private fun newPlayer(vocations: Vocations? = null, vocationId: Int = 1): Player {
        val player = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))
        if (vocations != null) {
            player.vocations = vocations
            player.vocationId = vocationId
        }
        return player
    }

    private fun createVocations(
        armorMultiplier: String = "1",
        defenseMultiplier: String = "1",
        clientId: Int = 1,
        attackSpeed: Int = 1500,
    ): Vocations {
        val xml = """
            <vocations>
              <vocation id="1" name="Knight" clientid="$clientId" gaincap="25" gainhp="15" gainmana="5" attackspeed="$attackSpeed">
                <skill id="0" multiplier="1.5"/>
                <skill id="1" multiplier="2"/>
                <skill id="2" multiplier="2"/>
                <skill id="3" multiplier="2"/>
                <skill id="4" multiplier="2"/>
                <skill id="5" multiplier="1.5"/>
                <formula armor="$armorMultiplier" defense="$defenseMultiplier"/>
              </vocation>
            </vocations>
        """.trimIndent()
        val vocations = Vocations()
        vocations.loadFromXml(xml)
        return vocations
    }

    private fun Player.grantSkill(skill: skills_t, level: Int) {
        val vocation = vocations?.getVocation(vocationId) ?: error("vocation registry required")
        addSkillTries(skill, vocation.getTotalSkillTries(skill.value, level))
    }

    private fun equipment(id: Int, name: String): ItemType = ItemType(id = id, name = name)

    private fun equip(player: Player, item: Item, slot: Slots_t) {
        assertThat(player.addItem(item, slot)).isEqualTo(ReturnValue.NOERROR)
    }

    // --- Player::getWeaponType / getWeaponSkill / getWeaponSkillId ---------------

    @Test
    fun `getWeaponType falls back to WEAPON_NONE without a weapon and reads the item type`() {
        // Player::getWeaponType (player.cpp:466-472) over Item::getWeaponType.
        val player = newPlayer()
        assertThat(player.getWeaponType()).isEqualTo(WeaponType_t.WEAPON_NONE)

        val swordType = equipment(10, "Sword").apply { weaponType = WeaponType_t.WEAPON_SWORD }
        val sword = Item(10, 1, swordType)
        player.weaponTypeProvider = { id -> if (id == 10) WeaponType_t.WEAPON_SWORD else WeaponType_t.WEAPON_NONE }
        equip(player, sword, Slots_t.CONST_SLOT_LEFT)

        assertThat(player.getWeaponType()).isEqualTo(WeaponType_t.WEAPON_SWORD)
    }

    @Test
    fun `getWeaponSkill maps each weapon family to its skill and null to fist`() {
        // Player::getWeaponSkill (player.cpp:474-514).
        val player = newPlayer(createVocations())
        player.grantSkill(skills_t.SKILL_FIST, 11)
        player.grantSkill(skills_t.SKILL_SWORD, 15)
        player.grantSkill(skills_t.SKILL_AXE, 12)
        player.grantSkill(skills_t.SKILL_DISTANCE, 10)

        fun item(weaponType: WeaponType_t) = Item(1, 1, equipment(1, "W").apply { this.weaponType = weaponType })

        assertThat(player.getWeaponSkill(null)).isEqualTo(11)
        assertThat(player.getWeaponSkill(item(WeaponType_t.WEAPON_SWORD))).isEqualTo(15)
        assertThat(player.getWeaponSkill(item(WeaponType_t.WEAPON_AXE))).isEqualTo(12)
        assertThat(player.getWeaponSkill(item(WeaponType_t.WEAPON_MISSILE))).isEqualTo(10)
        assertThat(player.getWeaponSkill(item(WeaponType_t.WEAPON_DISTANCE))).isEqualTo(10)
        assertThat(player.getWeaponSkill(item(WeaponType_t.WEAPON_WAND))).isZero()
    }

    @Test
    fun `getWeaponSkillId returns the client skill ids 8 to 11`() {
        // Player::getWeaponSkillId (player.cpp:568-586).
        val player = newPlayer()

        fun id(weaponType: WeaponType_t) =
            player.getWeaponSkillId(Item(1, 1, equipment(1, "W").apply { this.weaponType = weaponType }))

        assertThat(id(WeaponType_t.WEAPON_SWORD)).isEqualTo(8)
        assertThat(id(WeaponType_t.WEAPON_CLUB)).isEqualTo(9)
        assertThat(id(WeaponType_t.WEAPON_AXE)).isEqualTo(10)
        assertThat(id(WeaponType_t.WEAPON_FIST)).isEqualTo(11)
    }

    @Test
    fun `getDistanceAttackSkill applies the getWeaponSkill skill factor`() {
        // Player::getDistanceAttackSkill (player.cpp:516-520).
        val player = newPlayer()

        assertThat(player.getDistanceAttackSkill(24, 10)).isZero()
        assertThat(player.getDistanceAttackSkill(38, 10)).isEqualTo(5)
    }

    @Test
    fun `getAttackSkill scales the weapon attack by the attack skill and null returns fist`() {
        // Player::getAttackSkill (player.cpp:522-566).
        val player = newPlayer(createVocations())
        player.grantSkill(skills_t.SKILL_FIST, 11)
        player.grantSkill(skills_t.SKILL_SWORD, 38)
        val sword = Item(1, 1, equipment(1, "Sword").apply {
            weaponType = WeaponType_t.WEAPON_SWORD
            attack = 40
        })

        assertThat(player.getAttackSkill(null)).isEqualTo(11)
        assertThat(player.getAttackSkill(sword)).isEqualTo(20)
    }

    // --- quiver helpers ----------------------------------------------------------

    @Test
    fun `hasQuiverEquipped requires a quiver type with a container`() {
        // Player::hasQuiverEquipped (player.cpp:426-429).
        val player = newPlayer()
        assertThat(player.hasQuiverEquipped()).isFalse()

        val quiverType = equipment(20, "Quiver").apply { type = ItemTypes_t.ITEM_TYPE_QUIVER }
        equip(player, Container(20, 10, quiverType), Slots_t.CONST_SLOT_RIGHT)

        assertThat(player.hasQuiverEquipped()).isTrue()
    }

    @Test
    fun `hasWeaponDistanceEquipped only checks the left slot`() {
        // Player::hasWeaponDistanceEquipped (player.cpp:431-434).
        val player = newPlayer()
        val bowType = equipment(30, "Bow").apply { weaponType = WeaponType_t.WEAPON_DISTANCE }
        equip(player, Item(30, 1, bowType), Slots_t.CONST_SLOT_RIGHT)
        assertThat(player.hasWeaponDistanceEquipped()).isFalse()

        val leftPlayer = newPlayer()
        equip(leftPlayer, Item(30, 1, bowType), Slots_t.CONST_SLOT_LEFT)
        assertThat(leftPlayer.hasWeaponDistanceEquipped()).isTrue()
    }

    @Test
    fun `getQuiverAmmoOfType skips ammo above the player level`() {
        // Player::getQuiverAmmoOfType (player.cpp:436-451).
        val quiverType = equipment(20, "Quiver").apply { type = ItemTypes_t.ITEM_TYPE_QUIVER }
        val quiver = Container(20, 10, quiverType)
        val highArrow = Item(21, 1, equipment(21, "High Arrow").apply {
            ammoType = Ammo_t.AMMO_ARROW.value
            minReqLevel = 60
        })
        val arrow = Item(22, 1, equipment(22, "Arrow").apply {
            ammoType = Ammo_t.AMMO_ARROW.value
            minReqLevel = 40
        })
        quiver.addItem(highArrow)
        quiver.addItem(arrow)

        val player = newPlayer()
        equip(player, quiver, Slots_t.CONST_SLOT_RIGHT)
        player.level = 50

        val bowType = equipment(30, "Bow").apply { ammoType = Ammo_t.AMMO_ARROW.value }
        assertThat(player.getQuiverAmmoOfType(bowType)).isSameAs(arrow)

        player.level = 39
        assertThat(player.getQuiverAmmoOfType(bowType)).isNull()
    }

    @Test
    fun `getQuiverAmmoOfType without a quiver returns null`() {
        // player.cpp:437-439: `hasQuiverEquipped()` gate.
        val player = newPlayer()
        assertThat(
            player.getQuiverAmmoOfType(equipment(30, "Bow").apply { ammoType = Ammo_t.AMMO_ARROW.value }),
        ).isNull()
    }

    // --- armor / mantra ----------------------------------------------------------

    @Test
    fun `getArmor sums the seven armor slots and truncates the vocation multiplier`() {
        // Player::getArmor (player.cpp:658-671).
        val player = newPlayer(createVocations(armorMultiplier = "1.5"))
        equip(player, Item(1, 1, equipment(1, "Helmet").apply { armor = 2 }), Slots_t.CONST_SLOT_HEAD)
        equip(player, Item(2, 1, equipment(2, "Armor").apply { armor = 3 }), Slots_t.CONST_SLOT_ARMOR)
        equip(player, Item(3, 1, equipment(3, "Boots").apply { armor = 1 }), Slots_t.CONST_SLOT_FEET)
        equip(player, Item(4, 1, equipment(4, "Backpack")), Slots_t.CONST_SLOT_BACKPACK)

        assertThat(player.getArmor()).isEqualTo(6)
    }

    @Test
    fun `getMantra sums only the armor slot mantras`() {
        // Player::getMantra (player.cpp:671-694).
        val player = newPlayer()
        equip(player, Item(1, 1, equipment(1, "Helmet").apply { mantra = 4 }), Slots_t.CONST_SLOT_HEAD)
        equip(player, Item(2, 1, equipment(2, "Amulet").apply { mantra = 5 }), Slots_t.CONST_SLOT_NECKLACE)
        equip(player, Item(3, 1, equipment(3, "Backpack").apply { mantra = 9 }), Slots_t.CONST_SLOT_BACKPACK)

        assertThat(player.getMantra()).isEqualTo(9)
    }

    @Test
    fun `getPartyMantra prefers item mantra then the party buff`() {
        // Player::getPartyMantra (player.cpp:696-710).
        val player = newPlayer()
        assertThat(player.getPartyMantra(buffMantra = 150)).isZero()

        player.setParty(Party())
        assertThat(player.getPartyMantra(buffMantra = 150)).isEqualTo(50)

        equip(player, Item(1, 1, equipment(1, "Helmet").apply { mantra = 7 }), Slots_t.CONST_SLOT_HEAD)
        assertThat(player.getPartyMantra(buffMantra = 150)).isEqualTo(7)
    }

    @Test
    fun `updatePartyMantra runs only for monk cip vocations in a party`() {
        // Player::updatePartyMantra (player.cpp:713-719).
        var calls = 0
        val knight = newPlayer(createVocations(clientId = 1))
        knight.setParty(Party())
        knight.updatePartyMantra { calls++ }
        assertThat(calls).isZero()

        val monk = newPlayer(createVocations(clientId = 5))
        monk.updatePartyMantra { calls++ }
        assertThat(calls).isZero()

        monk.setParty(Party())
        monk.updatePartyMantra { calls++ }
        assertThat(calls).isEqualTo(1)
    }

    // --- shield / weapon / defense ----------------------------------------------

    @Test
    fun `getShieldAndWeapon keeps the highest defense shield and the last weapon`() {
        // Player::getShieldAndWeapon (player.cpp:721-747).
        val shieldA = Item(1, 1, equipment(1, "Shield A").apply {
            weaponType = WeaponType_t.WEAPON_SHIELD
            defense = 5
        })
        val shieldB = Item(2, 1, equipment(2, "Shield B").apply {
            weaponType = WeaponType_t.WEAPON_SHIELD
            defense = 7
        })
        val sword = Item(3, 1, equipment(3, "Sword").apply { weaponType = WeaponType_t.WEAPON_SWORD })

        val player = newPlayer()
        equip(player, shieldA, Slots_t.CONST_SLOT_RIGHT)
        equip(player, shieldB, Slots_t.CONST_SLOT_LEFT)
        assertThat(player.getShieldAndWeapon().shield).isSameAs(shieldB)
        assertThat(player.getShieldAndWeapon().weapon).isNull()

        val armed = newPlayer()
        equip(armed, sword, Slots_t.CONST_SLOT_LEFT)
        val weapon = armed.getShieldAndWeapon()
        assertThat(weapon.weapon).isSameAs(sword)
        assertThat(weapon.shield).isNull()
    }

    @Test
    fun `getDefense applies shield and weapon defense with the fight mode factor`() {
        // Player::getDefense (player.cpp:776-815). With shield + sword,
        // defenseValue = shield.defense + weapon.extraDefense (5 + 2), the
        // scaling factor is 0.16 and `sendToClient` pins the Attack factor to 0.5:
        // ((12 / 4.0 + 2.23) * 7 * 0.5 * 0.16) = 2.9288 -> 2.
        val player = newPlayer(createVocations())
        player.grantSkill(skills_t.SKILL_SWORD, 14)
        player.grantSkill(skills_t.SKILL_SHIELD, 12)

        val sword = Item(1, 1, equipment(1, "Sword").apply {
            weaponType = WeaponType_t.WEAPON_SWORD
            defense = 10
            extraDefense = 2
        })
        val shield = Item(2, 1, equipment(2, "Shield").apply {
            weaponType = WeaponType_t.WEAPON_SHIELD
            defense = 5
        })
        equip(player, sword, Slots_t.CONST_SLOT_RIGHT)
        equip(player, shield, Slots_t.CONST_SLOT_LEFT)

        assertThat(player.getDefense(sendToClient = true)).isEqualTo(2)
    }

    @Test
    fun `getDefense uses the fist skill and 0_15 scaling without equipment`() {
        // player.cpp:776-815 with the default level-10 fist skill:
        // ((10 / 4.0 + 2.23) * 7 * factor * 0.15). The zero-skill fight-mode
        // fallback (player.cpp:800-810) needs a mutated skill table, which the
        // port's private `skills` array cannot express.
        val player = newPlayer()

        player.fightMode = FightMode.Attack
        assertThat(player.getDefense(sendToClient = true)).isEqualTo(2)

        player.fightMode = FightMode.Balanced
        assertThat(player.getDefense(sendToClient = true)).isEqualTo(3)

        player.fightMode = FightMode.Defense
        assertThat(player.getDefense()).isEqualTo(4)
    }

    @Test
    fun `getDefenseEquipment adds shield, wheel and proficiency values`() {
        // Player::getDefenseEquipment (player.cpp:817-838).
        val player = newPlayer()
        val sword = Item(1, 1, equipment(1, "Sword").apply {
            weaponType = WeaponType_t.WEAPON_SWORD
            defense = 10
            extraDefense = 2
        })
        val shield = Item(2, 1, equipment(2, "Shield").apply {
            weaponType = WeaponType_t.WEAPON_SHIELD
            defense = 5
        })
        equip(player, sword, Slots_t.CONST_SLOT_RIGHT)
        equip(player, shield, Slots_t.CONST_SLOT_LEFT)

        // 12 (weapon) -> 5 + 2 (shield + weapon extra) = 7, +3 wheel, +1.5 proficiency.
        assertThat(player.getDefenseEquipment(wheelDefense = 3, proficiencyDefense = 1.5)).isEqualTo(11)
    }

    @Test
    fun `getAttackFactor and getCombatTacticsMitigation follow the fight mode`() {
        // Player::getAttackFactor (player.cpp:840-851),
        // Player::getCombatTacticsMitigation (player.cpp:754-772).
        val player = newPlayer()

        player.fightMode = FightMode.Attack
        assertThat(player.getAttackFactor()).isEqualTo(1.0f)
        assertThat(player.getCombatTacticsMitigation()).isEqualTo(0.8)

        player.fightMode = FightMode.Balanced
        assertThat(player.getAttackFactor()).isEqualTo(0.75f)
        assertThat(player.getCombatTacticsMitigation()).isEqualTo(1.0)

        player.fightMode = FightMode.Defense
        assertThat(player.getAttackFactor()).isEqualTo(0.5f)
        assertThat(player.getCombatTacticsMitigation()).isEqualTo(1.2)
    }

    @Test
    fun `getDefenseFactor honors the client pin and the attack cooldown`() {
        // Player::getDefenseFactor (player.cpp:853-870).
        val player = newPlayer(createVocations())
        player.fightMode = FightMode.Attack

        assertThat(player.getDefenseFactor(sendToClient = true)).isEqualTo(0.5f)
        assertThat(player.getDefenseFactor(sendToClient = true, attackSpeed = 1500, lastAttack = 0, now = 10_000))
            .isEqualTo(0.5f)
        assertThat(player.getDefenseFactor(sendToClient = false, attackSpeed = 1500, lastAttack = 9000, now = 10_000))
            .isEqualTo(0.5f)
        assertThat(player.getDefenseFactor(sendToClient = false, attackSpeed = 1500, lastAttack = 8000, now = 10_000))
            .isEqualTo(1.0f)

        player.fightMode = FightMode.Balanced
        assertThat(player.getDefenseFactor(sendToClient = true)).isEqualTo(0.75f)

        player.fightMode = FightMode.Defense
        assertThat(player.getDefenseFactor(sendToClient = false, attackSpeed = 1500, lastAttack = 9999, now = 10_000))
            .isEqualTo(1.0f)
    }

    @Test
    fun `getAttackSpeed reads the vocation attack speed`() {
        // Player::getAttackSpeed (player.cpp:7346-7348).
        assertThat(newPlayer().getAttackSpeed()).isZero()
        assertThat(newPlayer(createVocations(attackSpeed = 1500)).getAttackSpeed()).isEqualTo(1500)
    }

    @Test
    fun `hasShield only accepts weapon type shields in both hands`() {
        // Player::hasShield (player.cpp:3887-3897).
        val player = newPlayer()
        assertThat(player.hasShield()).isFalse()

        equip(
            player,
            Item(1, 1, equipment(1, "Sword").apply { weaponType = WeaponType_t.WEAPON_SWORD }),
            Slots_t.CONST_SLOT_LEFT,
        )
        assertThat(player.hasShield()).isFalse()

        equip(
            player,
            Item(2, 1, equipment(2, "Shield").apply { weaponType = WeaponType_t.WEAPON_SHIELD }),
            Slots_t.CONST_SLOT_RIGHT,
        )
        assertThat(player.hasShield()).isTrue()
    }

    @Test
    fun `getEquippedItems lists the nine equipment slots in order`() {
        // Player::getEquippedItems (player.cpp:5949-5969).
        val player = newPlayer()
        val helmet = Item(1, 1, equipment(1, "Helmet"))
        val backpack = Item(2, 1, equipment(2, "Backpack"))
        val ring = Item(3, 1, equipment(3, "Ring"))
        equip(player, helmet, Slots_t.CONST_SLOT_HEAD)
        equip(player, backpack, Slots_t.CONST_SLOT_BACKPACK)
        equip(player, ring, Slots_t.CONST_SLOT_RING)
        equip(player, Item(4, 1, equipment(4, "Ammo")), Slots_t.CONST_SLOT_AMMO)

        assertThat(player.getEquippedItems()).containsExactly(helmet, backpack, ring)
    }

    // --- dodge / flat healing ----------------------------------------------------

    @Test
    fun `getDodgeChance adds the boots amplification and the wheel dodge`() {
        // Player::getDodgeChance (player.cpp:12923-12941). Tier 1 ruse armor:
        // 0.0307576 + 0.440697 + 0.026 = 0.4974546 -> 49. Tier 1 amplification
        // boots: 0.4 + 1.7 + 0.4 = 2.5 -> 49 * 0.025 = 1.225 -> 1.
        val player = newPlayer()
        assertThat(player.getDodgeChance()).isZero()

        val armor = Item(1, 1, equipment(1, "Armor").apply { upgradeClassification = 1 })
        armor.setTier(1)
        equip(player, armor, Slots_t.CONST_SLOT_ARMOR)
        assertThat(player.getDodgeChance()).isEqualTo(49)

        val boots = Item(2, 1, equipment(2, "Boots").apply { upgradeClassification = 1 })
        boots.setTier(1)
        equip(player, boots, Slots_t.CONST_SLOT_FEET)
        assertThat(player.getDodgeChance()).isEqualTo(50)

        player.wheel().addStat(WheelStat.DODGE, 7)
        assertThat(player.getDodgeChance()).isEqualTo(57)
    }

    @Test
    fun `calculateFlatDamageHealing follows the tiered level curve`() {
        // Player::calculateFlatDamageHealing (player.cpp:588-610).
        val player = newPlayer()

        player.level = 400
        assertThat(player.calculateFlatDamageHealing()).isEqualTo(80)

        player.level = 1000
        assertThat(player.calculateFlatDamageHealing()).isEqualTo(184)

        player.level = 1200
        assertThat(player.calculateFlatDamageHealing()).isEqualTo(298)
    }

    @Test
    fun `getWeapon requires a registered weapon type and falls back to the right hand`() {
        // Player::getWeapon (player.cpp:453-464) is the port member; this pins
        // the provider-driven behavior the helper extensions build on.
        val player = newPlayer()
        val club = Item(5, 1, equipment(5, "Club").apply { weaponType = WeaponType_t.WEAPON_CLUB })
        equip(player, club, Slots_t.CONST_SLOT_RIGHT)
        player.weaponTypeProvider = { id -> if (id == 5) WeaponType_t.WEAPON_CLUB else WeaponType_t.WEAPON_NONE }

        assertThat(player.getWeapon()).isSameAs(club)
        assertThat(player.getWeaponType()).isEqualTo(WeaponType_t.WEAPON_CLUB)
        assertThat(player.getWeaponId()).isEqualTo(5)
    }
}
