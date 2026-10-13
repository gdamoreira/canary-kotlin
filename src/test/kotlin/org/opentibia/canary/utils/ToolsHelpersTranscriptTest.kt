package org.opentibia.canary.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.creatures.PlayerPronoun_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ObjectCategory_t

/**
 * Transcript tests for the `src/utils/tools.cpp` enum, pronoun, name and stash
 * helpers that do not depend on the item registry or the client protocol.
 */
class ToolsHelpersTranscriptTest {

    @Test
    fun `stringToItemAttribute mirrors the C++ attribute table`() {
        assertThat(Tools.stringToItemAttribute("store")).isEqualTo(ItemAttribute.STORE)
        assertThat(Tools.stringToItemAttribute("aid")).isEqualTo(ItemAttribute.ACTIONID)
        assertThat(Tools.stringToItemAttribute("uid")).isEqualTo(ItemAttribute.UNIQUEID)
        assertThat(Tools.stringToItemAttribute("description")).isEqualTo(ItemAttribute.DESCRIPTION)
        assertThat(Tools.stringToItemAttribute("text")).isEqualTo(ItemAttribute.TEXT)
        assertThat(Tools.stringToItemAttribute("date")).isEqualTo(ItemAttribute.DATE)
        assertThat(Tools.stringToItemAttribute("writer")).isEqualTo(ItemAttribute.WRITER)
        assertThat(Tools.stringToItemAttribute("name")).isEqualTo(ItemAttribute.NAME)
        assertThat(Tools.stringToItemAttribute("article")).isEqualTo(ItemAttribute.ARTICLE)
        assertThat(Tools.stringToItemAttribute("pluralname")).isEqualTo(ItemAttribute.PLURALNAME)
        assertThat(Tools.stringToItemAttribute("weight")).isEqualTo(ItemAttribute.WEIGHT)
        assertThat(Tools.stringToItemAttribute("mantra")).isEqualTo(ItemAttribute.MANTRA)
        assertThat(Tools.stringToItemAttribute("attack")).isEqualTo(ItemAttribute.ATTACK)
        assertThat(Tools.stringToItemAttribute("defense")).isEqualTo(ItemAttribute.DEFENSE)
        assertThat(Tools.stringToItemAttribute("extradefense")).isEqualTo(ItemAttribute.EXTRADEFENSE)
        assertThat(Tools.stringToItemAttribute("armor")).isEqualTo(ItemAttribute.ARMOR)
        assertThat(Tools.stringToItemAttribute("hitchance")).isEqualTo(ItemAttribute.HITCHANCE)
        assertThat(Tools.stringToItemAttribute("shootrange")).isEqualTo(ItemAttribute.SHOOTRANGE)
        assertThat(Tools.stringToItemAttribute("owner")).isEqualTo(ItemAttribute.OWNER)
        assertThat(Tools.stringToItemAttribute("duration")).isEqualTo(ItemAttribute.DURATION)
        assertThat(Tools.stringToItemAttribute("decaystate")).isEqualTo(ItemAttribute.DECAYSTATE)
        assertThat(Tools.stringToItemAttribute("corpseowner")).isEqualTo(ItemAttribute.CORPSEOWNER)
        assertThat(Tools.stringToItemAttribute("charges")).isEqualTo(ItemAttribute.CHARGES)
        assertThat(Tools.stringToItemAttribute("fluidtype")).isEqualTo(ItemAttribute.FLUIDTYPE)
        assertThat(Tools.stringToItemAttribute("doorid")).isEqualTo(ItemAttribute.DOORID)
        assertThat(Tools.stringToItemAttribute("timestamp")).isEqualTo(ItemAttribute.DURATION_TIMESTAMP)
        assertThat(Tools.stringToItemAttribute("amount")).isEqualTo(ItemAttribute.AMOUNT)
        assertThat(Tools.stringToItemAttribute("tier")).isEqualTo(ItemAttribute.TIER)
        assertThat(Tools.stringToItemAttribute("lootmessagesuffix")).isEqualTo(ItemAttribute.LOOTMESSAGE_SUFFIX)
        assertThat(Tools.stringToItemAttribute("unknown")).isEqualTo(ItemAttribute.NONE)
    }

    @Test
    fun `stringToSpellGroup mirrors the lowercase table`() {
        assertThat(Tools.stringToSpellGroup("attack")).isEqualTo(SpellGroup.SPELLGROUP_ATTACK)
        assertThat(Tools.stringToSpellGroup("1")).isEqualTo(SpellGroup.SPELLGROUP_ATTACK)
        assertThat(Tools.stringToSpellGroup("healing")).isEqualTo(SpellGroup.SPELLGROUP_HEALING)
        assertThat(Tools.stringToSpellGroup("2")).isEqualTo(SpellGroup.SPELLGROUP_HEALING)
        assertThat(Tools.stringToSpellGroup("support")).isEqualTo(SpellGroup.SPELLGROUP_SUPPORT)
        assertThat(Tools.stringToSpellGroup("special")).isEqualTo(SpellGroup.SPELLGROUP_SPECIAL)
        assertThat(Tools.stringToSpellGroup("crippling")).isEqualTo(SpellGroup.SPELLGROUP_CRIPPLING)
        assertThat(Tools.stringToSpellGroup("focus")).isEqualTo(SpellGroup.SPELLGROUP_FOCUS)
        assertThat(Tools.stringToSpellGroup("ultimatestrikes")).isEqualTo(SpellGroup.SPELLGROUP_ULTIMATESTRIKES)
        assertThat(Tools.stringToSpellGroup("burstsofnature")).isEqualTo(SpellGroup.SPELLGROUP_BURSTS_OF_NATURE)
        assertThat(Tools.stringToSpellGroup("greatbeams")).isEqualTo(SpellGroup.SPELLGROUP_GREAT_BEAMS)
        assertThat(Tools.stringToSpellGroup("virtue")).isEqualTo(SpellGroup.SPELLGROUP_VIRTUE)
        assertThat(Tools.stringToSpellGroup("Healing")).isEqualTo(SpellGroup.SPELLGROUP_HEALING)
        assertThat(Tools.stringToSpellGroup("5")).isEqualTo(SpellGroup.SPELLGROUP_NONE)
        assertThat(Tools.stringToSpellGroup("unknown")).isEqualTo(SpellGroup.SPELLGROUP_NONE)
    }

    @Test
    fun `getSkillName mirrors the skill id table`() {
        assertThat(Tools.getSkillName(0)).isEqualTo("fist fighting")
        assertThat(Tools.getSkillName(1)).isEqualTo("club fighting")
        assertThat(Tools.getSkillName(2)).isEqualTo("sword fighting")
        assertThat(Tools.getSkillName(3)).isEqualTo("axe fighting")
        assertThat(Tools.getSkillName(4)).isEqualTo("distance fighting")
        assertThat(Tools.getSkillName(5)).isEqualTo("shielding")
        assertThat(Tools.getSkillName(6)).isEqualTo("fishing")
        assertThat(Tools.getSkillName(7)).isEqualTo("critical hit chance")
        assertThat(Tools.getSkillName(8)).isEqualTo("critical extra damage")
        assertThat(Tools.getSkillName(9)).isEqualTo("life leech chance")
        assertThat(Tools.getSkillName(10)).isEqualTo("life leech")
        assertThat(Tools.getSkillName(11)).isEqualTo("mana leech chance")
        assertThat(Tools.getSkillName(12)).isEqualTo("mana leech")
        assertThat(Tools.getSkillName(13)).isEqualTo("magic level")
        assertThat(Tools.getSkillName(14)).isEqualTo("level")
        assertThat(Tools.getSkillName(15)).isEqualTo("unknown")
        assertThat(Tools.getSkillName(255)).isEqualTo("unknown")
    }

    @Test
    fun `getWeaponName mirrors the weapon type switch`() {
        assertThat(Tools.getWeaponName(WeaponType_t.WEAPON_FIST)).isEqualTo("fist")
        assertThat(Tools.getWeaponName(WeaponType_t.WEAPON_SWORD)).isEqualTo("sword")
        assertThat(Tools.getWeaponName(WeaponType_t.WEAPON_CLUB)).isEqualTo("club")
        assertThat(Tools.getWeaponName(WeaponType_t.WEAPON_AXE)).isEqualTo("axe")
        assertThat(Tools.getWeaponName(WeaponType_t.WEAPON_DISTANCE)).isEqualTo("distance")
        assertThat(Tools.getWeaponName(WeaponType_t.WEAPON_WAND)).isEqualTo("wand")
        assertThat(Tools.getWeaponName(WeaponType_t.WEAPON_AMMO)).isEqualTo("ammunition")
        assertThat(Tools.getWeaponName(WeaponType_t.WEAPON_MISSILE)).isEqualTo("missile")
        assertThat(Tools.getWeaponName(WeaponType_t.WEAPON_NONE)).isEqualTo("")
        assertThat(Tools.getWeaponName(WeaponType_t.WEAPON_SHIELD)).isEqualTo("")
    }

    @Test
    fun `getWeaponType mirrors the name table`() {
        assertThat(Tools.getWeaponType("none")).isEqualTo(WeaponType_t.WEAPON_NONE)
        assertThat(Tools.getWeaponType("fist")).isEqualTo(WeaponType_t.WEAPON_FIST)
        assertThat(Tools.getWeaponType("sword")).isEqualTo(WeaponType_t.WEAPON_SWORD)
        assertThat(Tools.getWeaponType("club")).isEqualTo(WeaponType_t.WEAPON_CLUB)
        assertThat(Tools.getWeaponType("axe")).isEqualTo(WeaponType_t.WEAPON_AXE)
        assertThat(Tools.getWeaponType("shield")).isEqualTo(WeaponType_t.WEAPON_SHIELD)
        assertThat(Tools.getWeaponType("distance")).isEqualTo(WeaponType_t.WEAPON_DISTANCE)
        assertThat(Tools.getWeaponType("wand")).isEqualTo(WeaponType_t.WEAPON_WAND)
        assertThat(Tools.getWeaponType("ammo")).isEqualTo(WeaponType_t.WEAPON_AMMO)
        assertThat(Tools.getWeaponType("missile")).isEqualTo(WeaponType_t.WEAPON_MISSILE)
        assertThat(Tools.getWeaponType("bow")).isEqualTo(WeaponType_t.WEAPON_NONE)
        assertThat(Tools.getWeaponType("Fist")).isEqualTo(WeaponType_t.WEAPON_NONE)
    }

    @Test
    fun `combat name maps mirror the C++ tables`() {
        assertThat(Tools.getCombatName(CombatType.PHYSICALDAMAGE)).isEqualTo("physical")
        assertThat(Tools.getCombatName(CombatType.DROWNDAMAGE)).isEqualTo("drown")
        assertThat(Tools.getCombatName(CombatType.AGONYDAMAGE)).isEqualTo("agony")
        assertThat(Tools.getCombatName(CombatType.NEUTRALDAMAGE)).isEqualTo("neutral")
        assertThat(Tools.getCombatName(CombatType.NONE)).isEqualTo("unknown")

        assertThat(Tools.getCombatTypeByName("physical")).isEqualTo(CombatType.PHYSICALDAMAGE)
        assertThat(Tools.getCombatTypeByName("drown")).isEqualTo(CombatType.DROWNDAMAGE)
        assertThat(Tools.getCombatTypeByName("unknown")).isEqualTo(CombatType.NONE)
        assertThat(Tools.getCombatTypeByName("")).isEqualTo(CombatType.NONE)
    }

    @Test
    fun `combat type index and name conversions match magic_enum semantics`() {
        assertThat(Tools.combatTypeToIndex(CombatType.PHYSICALDAMAGE)).isEqualTo(0)
        assertThat(Tools.combatTypeToIndex(CombatType.NEUTRALDAMAGE)).isEqualTo(13)
        assertThat(Tools.combatTypeToIndex(CombatType.COUNT)).isEqualTo(CombatType.NONE.value)
        assertThat(Tools.combatTypeToIndex(CombatType.NONE)).isEqualTo(CombatType.NONE.value)

        assertThat(Tools.combatTypeToName(CombatType.PHYSICALDAMAGE)).isEqualTo("combat physicaldamage")
        assertThat(Tools.combatTypeToName(CombatType.NEUTRALDAMAGE)).isEqualTo("combat neutraldamage")
        assertThat(Tools.combatTypeToName(CombatType.COUNT)).isEqualTo("")
        assertThat(Tools.combatTypeToName(CombatType.NONE)).isEqualTo("")

        assertThat(Tools.indexToCombatType(0)).isEqualTo(CombatType.PHYSICALDAMAGE)
        assertThat(Tools.indexToCombatType(3)).isEqualTo(CombatType.ENERGYDAMAGE)
        assertThat(Tools.indexToCombatType(13)).isEqualTo(CombatType.NEUTRALDAMAGE)
        assertThat(Tools.indexToCombatType(14)).isEqualTo(CombatType.COUNT)
        assertThat(Tools.indexToCombatType(255)).isEqualTo(CombatType.NONE)
        assertThat(Tools.indexToCombatType(100)).isNull()
    }

    @Test
    fun `getObjectCategoryName mirrors the C++ switch`() {
        assertThat(Tools.getObjectCategoryName(ObjectCategory_t.OBJECTCATEGORY_ARMORS)).isEqualTo("Armors")
        assertThat(Tools.getObjectCategoryName(ObjectCategory_t.OBJECTCATEGORY_NECKLACES)).isEqualTo("Amulets")
        assertThat(Tools.getObjectCategoryName(ObjectCategory_t.OBJECTCATEGORY_AMMO)).isEqualTo("Weapons: Ammunition")
        assertThat(Tools.getObjectCategoryName(ObjectCategory_t.OBJECTCATEGORY_DISTANCEWEAPONS)).isEqualTo("Weapons: Distance")
        assertThat(Tools.getObjectCategoryName(ObjectCategory_t.OBJECTCATEGORY_WANDS)).isEqualTo("Weapons: Wands")
        assertThat(Tools.getObjectCategoryName(ObjectCategory_t.OBJECTCATEGORY_PREMIUMSCROLLS)).isEqualTo("Premium Scrolls")
        assertThat(Tools.getObjectCategoryName(ObjectCategory_t.OBJECTCATEGORY_TIBIACOINS)).isEqualTo("Tibia Coins")
        assertThat(Tools.getObjectCategoryName(ObjectCategory_t.OBJECTCATEGORY_CREATUREPRODUCTS)).isEqualTo("Creature Products")
        assertThat(Tools.getObjectCategoryName(ObjectCategory_t.OBJECTCATEGORY_QUIVERS)).isEqualTo("Quiver")
        assertThat(Tools.getObjectCategoryName(ObjectCategory_t.OBJECTCATEGORY_FISTWEAPONS)).isEqualTo("Fist Weapons")
        assertThat(Tools.getObjectCategoryName(ObjectCategory_t.OBJECTCATEGORY_DEFAULT)).isEqualTo("Unassigned Loot")
        assertThat(Tools.getObjectCategoryName(ObjectCategory_t.OBJECTCATEGORY_NONE)).isEqualTo("")
    }

    @Test
    fun `isValidObjectCategory accepts every ported category`() {
        ObjectCategory_t.entries.forEach { category ->
            assertThat(Tools.isValidObjectCategory(category)).isTrue()
        }
    }

    @Test
    fun `isCaskItem accepts the documented id ranges`() {
        assertThat(Tools.isCaskItem(25878)).isFalse()
        assertThat(Tools.isCaskItem(25879)).isTrue()
        assertThat(Tools.isCaskItem(25883)).isTrue()
        assertThat(Tools.isCaskItem(25884)).isFalse()
        assertThat(Tools.isCaskItem(25889)).isTrue()
        assertThat(Tools.isCaskItem(25893)).isTrue()
        assertThat(Tools.isCaskItem(25899)).isTrue()
        assertThat(Tools.isCaskItem(25902)).isTrue()
        assertThat(Tools.isCaskItem(25903)).isFalse()
    }

    @Test
    fun `validateName mirrors the Canary regex and token checks`() {
        assertThat(Tools.validateName("Bob")).isEqualTo(NameEval.VALID)
        assertThat(Tools.validateName("O'Neil")).isEqualTo(NameEval.VALID)
        assertThat(Tools.validateName("Owner")).isEqualTo(NameEval.VALID)
        assertThat(Tools.validateName("owner")).isEqualTo(NameEval.INVALID_FORBIDDEN)
        assertThat(Tools.validateName("Ab")).isEqualTo(NameEval.INVALID_LENGTH)
        assertThat(Tools.validateName("")).isEqualTo(NameEval.INVALID_LENGTH)
        assertThat(Tools.validateName("abcdefghijklmnopqrs")).isEqualTo(NameEval.INVALID_LENGTH)
        assertThat(Tools.validateName("Name1")).isEqualTo(NameEval.INVALID_CHARACTER)
        assertThat(Tools.validateName("A B")).isEqualTo(NameEval.INVALID_TOKEN_LENGTH)
        assertThat(Tools.validateName("g m")).isEqualTo(NameEval.INVALID_TOKEN_LENGTH)
    }

    @Test
    fun `pronoun helpers mirror the pronoun and sex switches`() {
        val name = "Bob"
        assertThat(Tools.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_THEY, PlayerSex_t.MALE, name)).isEqualTo("they")
        assertThat(Tools.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_SHE, PlayerSex_t.MALE, name)).isEqualTo("she")
        assertThat(Tools.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_HE, PlayerSex_t.FEMALE, name)).isEqualTo("he")
        assertThat(Tools.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_ZE, PlayerSex_t.MALE, name)).isEqualTo("ze")
        assertThat(Tools.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_NAME, PlayerSex_t.MALE, name)).isEqualTo("Bob")
        assertThat(Tools.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, PlayerSex_t.FEMALE, name)).isEqualTo("she")
        assertThat(Tools.getPlayerSubjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, PlayerSex_t.MALE, name)).isEqualTo("he")

        assertThat(Tools.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_THEY, PlayerSex_t.MALE, name)).isEqualTo("them")
        assertThat(Tools.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_SHE, PlayerSex_t.MALE, name)).isEqualTo("her")
        assertThat(Tools.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_HE, PlayerSex_t.FEMALE, name)).isEqualTo("him")
        assertThat(Tools.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_ZE, PlayerSex_t.MALE, name)).isEqualTo("zir")
        assertThat(Tools.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_NAME, PlayerSex_t.MALE, name)).isEqualTo("Bob")
        assertThat(Tools.getPlayerObjectPronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, PlayerSex_t.FEMALE, name)).isEqualTo("her")

        assertThat(Tools.getPlayerPossessivePronoun(PlayerPronoun_t.PLAYERPRONOUN_THEY, PlayerSex_t.MALE, name)).isEqualTo("their")
        assertThat(Tools.getPlayerPossessivePronoun(PlayerPronoun_t.PLAYERPRONOUN_SHE, PlayerSex_t.MALE, name)).isEqualTo("her")
        assertThat(Tools.getPlayerPossessivePronoun(PlayerPronoun_t.PLAYERPRONOUN_HE, PlayerSex_t.FEMALE, name)).isEqualTo("his")
        assertThat(Tools.getPlayerPossessivePronoun(PlayerPronoun_t.PLAYERPRONOUN_ZE, PlayerSex_t.MALE, name)).isEqualTo("zir")
        assertThat(Tools.getPlayerPossessivePronoun(PlayerPronoun_t.PLAYERPRONOUN_NAME, PlayerSex_t.MALE, name)).isEqualTo("Bob's")
        assertThat(Tools.getPlayerPossessivePronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, PlayerSex_t.FEMALE, name)).isEqualTo("her")

        assertThat(Tools.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_THEY, PlayerSex_t.MALE, name)).isEqualTo("themself")
        assertThat(Tools.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_SHE, PlayerSex_t.MALE, name)).isEqualTo("herself")
        assertThat(Tools.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_HE, PlayerSex_t.FEMALE, name)).isEqualTo("himself")
        assertThat(Tools.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_ZE, PlayerSex_t.MALE, name)).isEqualTo("zirself")
        assertThat(Tools.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_NAME, PlayerSex_t.MALE, name)).isEqualTo("Bob")
        assertThat(Tools.getPlayerReflexivePronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, PlayerSex_t.FEMALE, name)).isEqualTo("herself")

        assertThat(Tools.getVerbForPronoun(PlayerPronoun_t.PLAYERPRONOUN_THEY)).isEqualTo("are")
        assertThat(Tools.getVerbForPronoun(PlayerPronoun_t.PLAYERPRONOUN_THEY, true)).isEqualTo("were")
        assertThat(Tools.getVerbForPronoun(PlayerPronoun_t.PLAYERPRONOUN_HE)).isEqualTo("is")
        assertThat(Tools.getVerbForPronoun(PlayerPronoun_t.PLAYERPRONOUN_UNSET, true)).isEqualTo("was")
    }

    @Test
    fun `convertWheelGemAffinityToDomain mirrors the affinity switch`() {
        assertThat(Tools.convertWheelGemAffinityToDomain(0)).isEqualTo(1)
        assertThat(Tools.convertWheelGemAffinityToDomain(1)).isEqualTo(3)
        assertThat(Tools.convertWheelGemAffinityToDomain(2)).isEqualTo(0)
        assertThat(Tools.convertWheelGemAffinityToDomain(3)).isEqualTo(2)
        assertThat(Tools.convertWheelGemAffinityToDomain(4)).isEqualTo(0)
    }

    @Test
    fun `getStashSize ceils each stack and sums the item list`() {
        assertThat(Tools.getStashSize(mapOf(2160 to 100)) { 100 }).isEqualTo(1)
        assertThat(Tools.getStashSize(mapOf(2160 to 101)) { 100 }).isEqualTo(2)
        assertThat(Tools.getStashSize(mapOf(2160 to 1, 2152 to 5)) { 100 }).isEqualTo(2)
        assertThat(Tools.getStashSize(emptyMap()) { 100 }).isEqualTo(0)
    }

    @Test
    fun `getMaxValuePerSkill scales the critical chance`() {
        val maxValues = Tools.getMaxValuePerSkill(2)
        assertThat(maxValues).containsEntry(9, 100)
        assertThat(maxValues).containsEntry(11, 100)
        assertThat(maxValues).containsEntry(7, 200)
    }
}
