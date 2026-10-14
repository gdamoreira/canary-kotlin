package org.opentibia.canary.items

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.movement.Teleport
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.house.Door
import org.opentibia.canary.systems.imbuement.ImbuementTypes_t
import org.opentibia.canary.systems.imbuement.Imbuements
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.lang.ref.WeakReference

/** `DIRECTION_NONE` (`src/game/movement/position.hpp:23`). */
const val DIRECTION_NONE = 8

/** `IMBUEMENT_MAX_TIER` (`src/utils/const.hpp:27`). */
const val IMBUEMENT_MAX_TIER = 3

/** `#define ITEM_IMBUEMENT_SLOT 500` (`src/items/item.cpp:33`). */
const val ITEM_IMBUEMENT_SLOT = 500

/** `PLAYERSEX_FEMALE` (`src/creatures/creatures_definitions.hpp:448`). */
const val PLAYERSEX_FEMALE = 0

/** `PLAYERSEX_MALE` (`src/creatures/creatures_definitions.hpp:449`). */
const val PLAYERSEX_MALE = 1

/** C++ spelling of [BedItemPart] (`BedItemPart_t`, src/utils/utils_definitions.hpp:764-768). */
typealias BedItemPart_t = BedItemPart

/** `WeaponAction_t` (`src/utils/utils_definitions.hpp:494-499`). */
enum class WeaponAction_t(val value: Int) {
    WEAPONACTION_NONE(0),
    WEAPONACTION_REMOVECOUNT(1),
    WEAPONACTION_REMOVECHARGE(2),
    WEAPONACTION_MOVE(3),
}

/** `Augment_t` (`src/items/items_definitions.hpp:280-302`). */
enum class Augment_t(val value: Int) {
    None(0),
    ManaCost(1),
    BaseDamage(2),
    BaseHealing(3),
    DurationIncreased(4),
    AdditionalTargets(5),
    Cooldown(6),
    SecondaryGroupCooldown(7),
    AffectedAreaEnlarged(8),
    IncreasedDamageReduction(9),
    EnhancedEffect(12),
    IncreasedSkill(13),
    LifeLeech(14),
    ManaLeech(15),
    CriticalExtraDamage(16),
    CriticalHitChance(17),
    PowerfulImpact(100),
    StrongImpact(101),
    IncreasedDamage(102),
}

/** `AugmentInfo` (`src/items/items_definitions.hpp:651-658`). */
data class AugmentInfo(
    val spellName: String,
    val type: Augment_t,
    val value: Int,
)

/**
 * `struct Abilities` (`src/items/items.hpp:19-93`).
 *
 * The C++ array widths come from `SKILL_LAST + 1` (12 + 1), `STAT_LAST + 1`
 * (4 + 1) and `COMBAT_COUNT` (14).
 */
class Abilities {
    /** `skills[SKILL_LAST + 1]` (items.hpp:31). */
    val skills: IntArray = IntArray(SKILL_COUNT)

    /** `stats[STAT_LAST + 1]` (items.hpp:27). */
    val stats: IntArray = IntArray(STAT_COUNT)

    /** `statsPercent[STAT_LAST + 1]` (items.hpp:28). */
    val statsPercent: IntArray = IntArray(STAT_COUNT)

    /** `fieldAbsorbPercent[COMBAT_COUNT]` (items.hpp:37, `int16_t`). */
    val fieldAbsorbPercent: ShortArray = ShortArray(COMBAT_COUNT)

    /** `absorbPercent[COMBAT_COUNT]` (items.hpp:40, `int16_t`). */
    val absorbPercent: ShortArray = ShortArray(COMBAT_COUNT)

    /** `reflectPercent[COMBAT_COUNT]` (items.hpp:43, `int16_t`). */
    val reflectPercent: ShortArray = ShortArray(COMBAT_COUNT)

    /** `reflectFlat[COMBAT_COUNT]` (items.hpp:49, `int32_t`). */
    val reflectFlat: IntArray = IntArray(COMBAT_COUNT)

    /** `specializedMagicLevel[COMBAT_COUNT]` (items.hpp:51, `int32_t`). */
    val specializedMagicLevel: IntArray = IntArray(COMBAT_COUNT)

    /** `conditionImmunities` (items.hpp:20). */
    val conditionImmunities: IntArray = IntArray(CONDITION_COUNT)

    /** `conditionSuppressions` (items.hpp:21). */
    val conditionSuppressions: IntArray = IntArray(CONDITION_COUNT)

    var speed: Int = 0
    var elementDamage: Int = 0
    var elementType: CombatType = CombatType.NONE
    var magicShieldCapacityPercent: Int = 0
    var magicShieldCapacityFlat: Int = 0
    var cleavePercent: Int = 0
    var perfectShotDamage: Int = 0
    var perfectShotRange: Int = 0
    var manaShield: Boolean = false
    var invisible: Boolean = false
    var regeneration: Boolean = false

    /** `Abilities::setHealthGain`/`getHealthGain` (items.hpp:69-79). */
    var healthGain: Int = 0

    /** `Abilities::setHealthTicks`/`getHealthTicks` (items.hpp:81-91). */
    var healthTicks: Int = 0

    /** `Abilities::setManaGain`/`getManaGain` (items.hpp:93-103). */
    var manaGain: Int = 0

    /** `Abilities::setManaTicks`/`getManaTicks` (items.hpp:105-115). */
    var manaTicks: Int = 0

    companion object {
        /** `SKILL_LAST + 1` with `SKILL_LAST = SKILL_MANA_LEECH_AMOUNT` (12). */
        private const val SKILL_COUNT = 13

        /** `STAT_LAST + 1` with `STAT_LAST = STAT_CAPACITY` (4). */
        private const val STAT_COUNT = 5

        /** `COMBAT_COUNT` (`src/creatures/creatures_definitions.hpp:847`). */
        private const val COMBAT_COUNT = 14

        /** `CONDITION_COUNT` (`src/creatures/creatures_definitions.hpp:397`). */
        private const val CONDITION_COUNT = 40
    }
}

private val logger = KotlinLogging.logger {}

/**
 * Mirror of `ItemType` (`src/items/items.hpp:96-387`).
 *
 * The legacy boolean flags (`isDoor`, `isBed`, `isCarpet`, `isTrashHolder`,
 * `isWrappable`, `isContainer`, `isFluidContainer`, `isSplash`,
 * `isGroundTile`) predate the [type]/[group] transcription and stay for the
 * existing call sites; the predicates added by the item-core wave read [type],
 * mirroring the C++ methods exactly until the registry wave reconciles both
 * models.
 */
data class ItemType(
    var id: Int,
    var name: String,
    var isStackable: Boolean = false,
    var isContainer: Boolean = false,
    var capacity: Int = 0,
    var weight: Double = 0.0,
    /** Mirrors `ItemType::upgradeClassification` (src/items/items.hpp:329). */
    var upgradeClassification: Int = 0,
    var isPickupable: Boolean = true,
    /** Mirrors `ItemType::magicEffect` (src/items/items.hpp:318); default `CONST_ME_NONE` (0). */
    var magicEffect: Int = 0,
    /** `ItemType::movable` (src/items/items.hpp:288), the house-serialization guard. */
    var isMovable: Boolean = false,
    /** `ItemType::isBed()` (src/items/items.hpp:378). */
    var isBed: Boolean = false,
    /** `ItemType::bedPartOf` (src/items/items.hpp:468), the partner id `BedItem::isBedComplete` checks. */
    var bedPartOf: Int = 0,
    /** `ItemType::isCarpet()` (src/items/items.hpp:420). */
    var isCarpet: Boolean = false,
    /** `ItemType::isTrashHolder()` (src/items/items.hpp:410). */
    var isTrashHolder: Boolean = false,
    /** `ItemType::isDoor()` (src/items/items.hpp:414). */
    var isDoor: Boolean = false,
    /** `ItemType::isWrappable()` (src/items/items.hpp:411). */
    var isWrappable: Boolean = false,
    /** `ItemType::canWriteText` (src/items/items.hpp:342). */
    var canWriteText: Boolean = false,
    /** `ItemType::m_transformOnUse` (src/items/items.hpp:347). */
    var transformOnUse: Int = 0,
    /** `ItemType::isFluidContainer()` (src/items/items.hpp:416). */
    var isFluidContainer: Boolean = false,
    /** `ItemType::isSplash()` (src/items/items.hpp:417). */
    var isSplash: Boolean = false,
    /** `ItemType::isGroundTile()` (src/items/items.hpp:407). */
    var isGroundTile: Boolean = false,
    /** `ItemType::charges` (src/items/items.hpp:290), used by `getSubType`/`setSubType`. */
    var charges: Int = 0,

    // ---- Canary ItemType parse state (src/items/items.hpp:262-407) ----
    /** `ItemType::group` (items.hpp:262); default `ITEM_GROUP_NONE`. */
    var group: ItemGroup_t = ItemGroup_t.ITEM_GROUP_NONE,
    /** `ItemType::type` (items.hpp:263); default `ITEM_TYPE_NONE`. */
    var type: ItemTypes_t = ItemTypes_t.ITEM_TYPE_NONE,
    /** `ItemType::article` (items.hpp:266). */
    var article: String = "",
    /** `ItemType::pluralName` (items.hpp:267). */
    var pluralName: String = "",
    /** `ItemType::description` (items.hpp:268). */
    var description: String = "",
    /** `ItemType::runeSpellName` (items.hpp:269). */
    var runeSpellName: String = "",
    /** `ItemType::vocationString` (items.hpp:270). */
    var vocationString: String = "",
    /** `ItemType::m_primaryType` (items.hpp:271). */
    var primaryType: String = "",
    /** `std::unique_ptr<Abilities> abilities` (items.hpp:273); null until `getAbilities()`. */
    @get:JvmName("abilitiesValue")
    var abilities: Abilities? = null,
    /** `std::shared_ptr<ConditionDamage> conditionDamage` (items.hpp:274). */
    var conditionDamage: ConditionDamage? = null,
    /** `ItemType::levelDoor` (items.hpp:276). */
    var levelDoor: Long = 0,
    /** `ItemType::decayTime` (items.hpp:277). */
    var decayTime: Long = 0,
    /** `ItemType::wieldInfo` (items.hpp:278). */
    var wieldInfo: Long = 0,
    /** `ItemType::minReqLevel` (items.hpp:279). */
    var minReqLevel: Long = 0,
    /** `ItemType::minReqMagicLevel` (items.hpp:280). */
    var minReqMagicLevel: Long = 0,
    /** `ItemType::buyPrice` (items.hpp:281). */
    var buyPrice: Long = 0,
    /** `ItemType::sellPrice` (items.hpp:282). */
    var sellPrice: Long = 0,
    /** `ItemType::maxHitChance` (items.hpp:286); `-1` disables the table. */
    var maxHitChance: Int = -1,
    /** `ItemType::decayTo` (items.hpp:287); `-1` means "does not decay". */
    var decayTo: Int = -1,
    /** `ItemType::attack` (items.hpp:288). */
    var attack: Int = 0,
    /** `ItemType::defense` (items.hpp:289). */
    var defense: Int = 0,
    /** `ItemType::extraDefense` (items.hpp:290). */
    var extraDefense: Int = 0,
    /** `ItemType::armor` (items.hpp:291). */
    var armor: Int = 0,
    /** `ItemType::mantra` (items.hpp:292). */
    var mantra: Int = 0,
    /** `ItemType::rotateTo` (items.hpp:293). */
    var rotateTo: Int = 0,
    /** `ItemType::runeMagLevel` (items.hpp:294). */
    var runeMagLevel: Int = 0,
    /** `ItemType::runeLevel` (items.hpp:295). */
    var runeLevel: Int = 0,
    /** `ItemType::wrapableTo` (items.hpp:296). */
    var wrapableTo: Int = 0,
    /** `ItemType::combatType` (items.hpp:298). */
    var combatType: CombatType = CombatType.NONE,
    /** `ItemType::elementalBond` (items.hpp:299). */
    var elementalBond: CombatType = CombatType.NONE,
    /** `uint16_t transformToOnUse[2]` (items.hpp:303); index by `PLAYERSEX_*`. */
    val transformToOnUse: IntArray = IntArray(2),
    /** `ItemType::transformToFree` (items.hpp:303). */
    var transformToFree: Int = 0,
    /** `ItemType::destroyTo` (items.hpp:304). */
    var destroyTo: Int = 0,
    /** `ItemType::maxTextLen` (items.hpp:305). */
    var maxTextLen: Int = 0,
    /** `ItemType::writeOnceItemId` (items.hpp:306). */
    var writeOnceItemId: Int = 0,
    /** `ItemType::transformEquipTo` (items.hpp:307). */
    var transformEquipTo: Int = 0,
    /** `ItemType::transformDeEquipTo` (items.hpp:308). */
    var transformDeEquipTo: Int = 0,
    /** `ItemType::maxItems` (items.hpp:309), default 8. */
    var maxItems: Int = 8,
    /** `ItemType::slotPosition` (items.hpp:310), default `SLOTP_HAND`. */
    var slotPosition: Int = SLOTP_HAND,
    /** `ItemType::speed` (items.hpp:311). */
    var speed: Int = 0,
    /** `ItemType::wareId` (items.hpp:312). */
    var wareId: Int = 0,
    /** `ItemType::bedPartnerDir` (items.hpp:319); `DIRECTION_NONE` until parsed. */
    var bedPartnerDir: Int = DIRECTION_NONE,
    /** `ItemType::bedPart` (items.hpp:320). */
    var bedPart: BedItemPart_t = BedItemPart_t.BED_NONE_PART,
    /** `ItemType::weaponType` (items.hpp:321). */
    var weaponType: WeaponType_t = WeaponType_t.WEAPON_NONE,
    /** `ItemType::ammoType` (items.hpp:322). */
    var ammoType: Int = 0,
    /** `ItemType::shootType` (items.hpp:323). */
    var shootType: Int = 0,
    /** `ItemType::corpseType` (items.hpp:324). */
    var corpseType: Int = 0,
    /** `ItemType::fluidSource` (items.hpp:325). */
    var fluidSource: Int = 0,
    /** `ItemType::floorChange` (items.hpp:326). */
    var floorChange: Int = 0,
    /** `ItemType::imbuementTypes` (items.hpp:327). */
    val imbuementTypes: MutableMap<ImbuementTypes_t, Int> = linkedMapOf(),
    /** `ItemType::alwaysOnTopOrder` (items.hpp:330). */
    var alwaysOnTopOrder: Int = 0,
    /** `ItemType::lightLevel` (items.hpp:331). */
    var lightLevel: Int = 0,
    /** `ItemType::lightColor` (items.hpp:332). */
    var lightColor: Int = 0,
    /** `ItemType::shootRange` (items.hpp:333), default 1. */
    var shootRange: Int = 1,
    /** `ItemType::imbuementSlot` (items.hpp:334). */
    var imbuementSlot: Int = 0,
    /** `ItemType::stackSize` (items.hpp:335), default 100. */
    var stackSize: Int = 100,
    /** `ItemType::proficiencyId` (items.hpp:337). */
    var proficiencyId: Int = 0,
    /** `ItemType::hitChance` (items.hpp:339). */
    var hitChance: Int = 0,
    /** `std::vector<std::shared_ptr<AugmentInfo>> augments` (items.hpp:341). */
    val augments: MutableList<AugmentInfo> = mutableListOf(),
    /** `ItemType::wearOut` (items.hpp:344). */
    var wearOut: Boolean = false,
    /** `ItemType::clockExpire` (items.hpp:345). */
    var clockExpire: Boolean = false,
    /** `ItemType::expire` (items.hpp:346). */
    var expire: Boolean = false,
    /** `ItemType::expireStop` (items.hpp:347). */
    var expireStop: Boolean = false,
    /** `ItemType::forceUse` (items.hpp:349). */
    var forceUse: Boolean = false,
    /** `ItemType::hasHeight` (items.hpp:350). */
    var hasHeight: Boolean = false,
    /** `ItemType::walkStack` (items.hpp:351), default true. */
    var walkStack: Boolean = true,
    /** `ItemType::blockSolid` (items.hpp:352). */
    var blockSolid: Boolean = false,
    /** `ItemType::blockPickupable` (items.hpp:353). */
    var blockPickupable: Boolean = false,
    /** `ItemType::blockProjectile` (items.hpp:354). */
    var blockProjectile: Boolean = false,
    /** `ItemType::blockPathFind` (items.hpp:355). */
    var blockPathFind: Boolean = false,
    /** `ItemType::showDuration` (items.hpp:356). */
    var showDuration: Boolean = false,
    /** `ItemType::showCharges` (items.hpp:357). */
    var showCharges: Boolean = false,
    /** `ItemType::showAttributes` (items.hpp:358). */
    var showAttributes: Boolean = false,
    /** `ItemType::replaceable` (items.hpp:359), default true. */
    var replaceable: Boolean = true,
    /** `ItemType::rotatable` (items.hpp:360). */
    var rotatable: Boolean = false,
    /** `ItemType::wrapable` (items.hpp:361); set by `wrapableto`. */
    var wrapable: Boolean = false,
    /** `ItemType::wrapContainer` (items.hpp:362). */
    var wrapContainer: Boolean = false,
    /** `ItemType::multiUse` (items.hpp:363). */
    var multiUse: Boolean = false,
    /** `ItemType::canReadText` (items.hpp:365). */
    var canReadText: Boolean = false,
    /** `ItemType::isVertical` (items.hpp:367). */
    var isVertical: Boolean = false,
    /** `ItemType::isHorizontal` (items.hpp:368). */
    var isHorizontal: Boolean = false,
    /** `ItemType::isHangable` (items.hpp:369). */
    var isHangable: Boolean = false,
    /** `ItemType::allowDistRead` (items.hpp:370). */
    var allowDistRead: Boolean = false,
    /** `ItemType::lookThrough` (items.hpp:371). */
    var lookThrough: Boolean = false,
    /** `ItemType::stopTime` (items.hpp:372). */
    var stopTime: Boolean = false,
    /** `ItemType::hasDeEquipEvent` (items.hpp:373). */
    var hasDeEquipEvent: Boolean = false,
    /** `ItemType::showCount` (items.hpp:374), default true. */
    var showCount: Boolean = true,
    /** `ItemType::animationType` (src/items/items.hpp:302). */
    var animationType: ItemAnimation_t = ItemAnimation_t.ANIMATION_NONE,
    /** `ItemType::stackable` (items.hpp:375); the XML parse does not set it. */
    @set:JvmName("setParsedStackable")
    var stackable: Boolean = false,
    /** `ItemType::isPodium` (items.hpp:376). */
    var isPodium: Boolean = false,
    /** `ItemType::isCorpse` (items.hpp:377). */
    var isCorpse: Boolean = false,
    /** `ItemType::loaded` (items.hpp:378). */
    var loaded: Boolean = false,
    /** `ItemType::spellbook` (items.hpp:379). */
    var spellbook: Boolean = false,
    /** `ItemType::isWrapKit` (items.hpp:380). */
    var isWrapKit: Boolean = false,
    /** `ItemType::m_canBeUsedByGuests` (items.hpp:381). */
    var canBeUsedByGuests: Boolean = false,
    /** `ItemType::isDualWielding` (items.hpp:384). */
    var isDualWielding: Boolean = false,
    /** `ItemType::m_isMagicShieldPotion` (items.hpp:385). */
    var isMagicShieldPotion: Boolean = false,
) {

    /** `ItemType::isWeapon` (src/items/items.hpp:195). */
    fun isWeapon(): Boolean =
        weaponType != WeaponType_t.WEAPON_NONE &&
            weaponType != WeaponType_t.WEAPON_SHIELD &&
            weaponType != WeaponType_t.WEAPON_AMMO

    /** `ItemType::isWand` (src/items/items.hpp:198). */
    fun isWand(): Boolean = weaponType == WeaponType_t.WEAPON_WAND

    /** `ItemType::isShield` (src/items/items.hpp:121). */
    fun isShield(): Boolean = type == ItemTypes_t.ITEM_TYPE_SHIELD && !isSpellBook()

    /** `ItemType::isSpellBook` (src/items/items.hpp:124). */
    fun isSpellBook(): Boolean = spellbook

    /** `ItemType::isArmor` (src/items/items.hpp:201). */
    fun isArmor(): Boolean = slotPosition and SLOTP_ARMOR != 0

    /** `ItemType::isHelmet` (src/items/items.hpp:204). */
    fun isHelmet(): Boolean = slotPosition and SLOTP_HEAD != 0

    /** `ItemType::isLegs` (src/items/items.hpp:207). */
    fun isLegs(): Boolean = slotPosition and SLOTP_LEGS != 0

    /** `ItemType::isBoots` (src/items/items.hpp:210). */
    fun isBoots(): Boolean = slotPosition and SLOTP_FEET != 0

    /** `ItemType::isRanged` (src/items/items.hpp:213). */
    fun isRanged(): Boolean =
        weaponType == WeaponType_t.WEAPON_DISTANCE && weaponType != WeaponType_t.WEAPON_NONE

    /** `ItemType::isMissile` (src/items/items.hpp:216). */
    fun isMissile(): Boolean =
        weaponType == WeaponType_t.WEAPON_MISSILE && weaponType != WeaponType_t.WEAPON_NONE

    /** `ItemType::isKey` (src/items/items.hpp:137). */
    fun isKey(): Boolean = type == ItemTypes_t.ITEM_TYPE_KEY

    /** `ItemType::isRune` (src/items/items.hpp:161). */
    fun isRune(): Boolean = type == ItemTypes_t.ITEM_TYPE_RUNE

    /** `ItemType::isQuiver` (src/items/items.hpp:170). */
    fun isQuiver(): Boolean = type == ItemTypes_t.ITEM_TYPE_QUIVER

    /** `ItemType::isRing` (src/items/items.hpp:173). */
    fun isRing(): Boolean = type == ItemTypes_t.ITEM_TYPE_RING

    /** `ItemType::isAmulet` (src/items/items.hpp:176). */
    fun isAmulet(): Boolean = type == ItemTypes_t.ITEM_TYPE_AMULET

    /** `ItemType::isAmmo` (src/items/items.hpp:179). */
    fun isAmmo(): Boolean = type == ItemTypes_t.ITEM_TYPE_AMMO

    /** `ItemType::isLadder` (src/items/items.hpp:182). */
    fun isLadder(): Boolean = type == ItemTypes_t.ITEM_TYPE_LADDER

    /** `ItemType::isDummy` (src/items/items.hpp:185). */
    fun isDummy(): Boolean = type == ItemTypes_t.ITEM_TYPE_DUMMY

    /** `ItemType::isDepot` (src/items/items.hpp:140). */
    fun isDepot(): Boolean = type == ItemTypes_t.ITEM_TYPE_DEPOT

    /** `ItemType::isRewardChest` (src/items/items.hpp:143). */
    fun isRewardChest(): Boolean = type == ItemTypes_t.ITEM_TYPE_REWARDCHEST

    /** `ItemType::isMailbox` (src/items/items.hpp:149). */
    fun isMailbox(): Boolean = type == ItemTypes_t.ITEM_TYPE_MAILBOX

    /** `ItemType::isMagicField` (src/items/items.hpp:131). */
    fun isMagicField(): Boolean = type == ItemTypes_t.ITEM_TYPE_MAGICFIELD

    /** `ItemType::isTeleport` (src/items/items.hpp:134). */
    fun isTeleport(): Boolean = type == ItemTypes_t.ITEM_TYPE_TELEPORT

    /** `ItemType::isMultiUse` (src/items/items.hpp:167). */
    fun isMultiUse(): Boolean = multiUse

    /** `ItemType::hasSubType` (src/items/items.hpp:188). */
    fun hasSubType(): Boolean = isFluidContainer || isSplash || isStackable || charges != 0

    /** `ItemType::isActiveEquipment` (src/items/items.hpp:191). */
    fun isActiveEquipment(): Boolean =
        transformDeEquipTo != 0 && (hasDeEquipEvent || (decayTime != 0L && decayTo == transformDeEquipTo))

    /** `ItemType::getPluralName` (src/items/items.hpp:239). */
    @JvmName("resolvePluralName")
    fun getPluralName(): String {
        if (pluralName.isNotEmpty()) {
            return pluralName
        }
        if (!showCount) {
            return name
        }
        return name + "s"
    }

    /** `ItemType::hasImbuementType` (src/items/item.hpp:720). */
    fun hasImbuementType(imbuementType: ImbuementTypes_t, imbuementTier: Int): Boolean {
        val maxTier = imbuementTypes[imbuementType] ?: return false
        return maxTier >= imbuementTier
    }

    /** `ItemType::getAbilities()` (src/items/items.hpp:196-201). */
    /** `ItemType::parseAugmentDescription` (src/items/items.cpp:82-97). */
    fun parseAugmentDescription(inspect: Boolean = false): String {
        if (augments.isEmpty()) {
            return ""
        }

        val descriptions = augments.joinToString(", ") { getFormattedAugmentDescription(it) }
        return if (inspect) {
            "$descriptions."
        } else {
            "\nAugments: ($descriptions)."
        }
    }

    /** `ItemType::getFormattedAugmentDescription` (src/items/items.cpp:99-116). */
    fun getFormattedAugmentDescription(augmentInfo: AugmentInfo): String {
        val augmentName = Items.getAugmentNameByType(augmentInfo.type)
        val augmentSpellNameCapitalized = capitalizeWordsIgnoringString(augmentInfo.spellName, " of ")

        val signal = if (augmentInfo.value > 0) '-' else '+'

        if (Items.isAugmentWithoutValueDescription(augmentInfo.type)) {
            return "$augmentSpellNameCapitalized -> $augmentName"
        }
        if (augmentInfo.type == Augment_t.Cooldown) {
            return "$augmentSpellNameCapitalized -> $signal${augmentInfo.value / 1000}s $augmentName"
        }
        return "$augmentSpellNameCapitalized -> ${signedPercent(augmentInfo.value)}% $augmentName"
    }

    /** `ItemType::triggerExhaustion` (src/items/items.cpp:127-129). */
    fun triggerExhaustion(): Boolean =
        type == ItemTypes_t.ITEM_TYPE_RUNE ||
            (type == ItemTypes_t.ITEM_TYPE_POTION && !isMagicShieldPotion)


    fun getAbilities(): Abilities = abilities ?: Abilities().also { abilities = it }

    /** `ItemType::setImbuementType` (src/items/items.cpp:123-125). */
    fun setImbuementType(imbuementType: ImbuementTypes_t, slotMaxTier: Int) {
        imbuementTypes[imbuementType] = minOf(IMBUEMENT_MAX_TIER, slotMaxTier)
    }

    /** `ItemType::addAugment` (src/items/items.cpp:118-121). */
    fun addAugment(spellName: String, augmentType: Augment_t, value: Int) {
        augments.add(AugmentInfo(spellName, augmentType, value))
    }
}


/**
 * Mirrors Canary `ItemAttribute_t` (src/enums/item_attribute.hpp:12) with the exact
 * underlying values. Integer vs string classification mirrors `ItemAttributeHelper`
 * (src/items/functions/item/attribute.hpp:15).
 */
enum class ItemAttribute(val value: Long) {
    NONE(0),
    ACTIONID(1),
    UNIQUEID(2),
    DESCRIPTION(3),
    TEXT(4),
    DATE(5),
    WRITER(6),
    NAME(7),
    ARTICLE(8),
    PLURALNAME(9),
    WEIGHT(10),
    ATTACK(11),
    DEFENSE(12),
    EXTRADEFENSE(13),
    ARMOR(14),
    HITCHANCE(15),
    SHOOTRANGE(16),
    OWNER(17),
    DURATION(18),
    DECAYSTATE(19),
    CORPSEOWNER(20),
    CHARGES(21),
    FLUIDTYPE(22),
    DOORID(23),
    SPECIAL(24),
    IMBUEMENT_SLOT(25),
    OPENCONTAINER(26),
    QUICKLOOTCONTAINER(27),
    DURATION_TIMESTAMP(28),
    AMOUNT(29),
    TIER(30),
    STORE(31),
    CUSTOM(32),
    LOOTMESSAGE_SUFFIX(33),
    STORE_INBOX_CATEGORY(34),
    OBTAINCONTAINER(35),
    AUGMENTS(36),
    MANTRA(37);

    companion object {
        fun isAttributeInteger(attribute: ItemAttribute): Boolean = when (attribute) {
            STORE, ACTIONID, UNIQUEID, DATE, WEIGHT, ATTACK, MANTRA, DEFENSE, EXTRADEFENSE,
            ARMOR, HITCHANCE, SHOOTRANGE, OWNER, DURATION, DECAYSTATE, CORPSEOWNER, CHARGES,
            FLUIDTYPE, DOORID, IMBUEMENT_SLOT, OPENCONTAINER, QUICKLOOTCONTAINER, OBTAINCONTAINER,
            DURATION_TIMESTAMP, TIER, AMOUNT -> true

            else -> false
        }

        fun isAttributeString(attribute: ItemAttribute): Boolean = when (attribute) {
            DESCRIPTION, TEXT, WRITER, NAME, ARTICLE, PLURALNAME, SPECIAL,
            LOOTMESSAGE_SUFFIX, STORE_INBOX_CATEGORY -> true

            else -> false
        }
    }
}

/** Mirrors Canary `ItemDecayState_t` (src/enums/item_attribute.hpp:53). */
enum class ItemDecayState(val value: Int) {
    DECAYING_FALSE(0),
    DECAYING_TRUE(1),
    DECAYING_PENDING(2),
    DECAYING_STOPPING(3);

    companion object {
        fun fromValue(value: Int): ItemDecayState =
            entries.firstOrNull { it.value == value } ?: DECAYING_FALSE
    }
}

/** `Attr_ReadValue` (src/items/item.hpp:69-73). */
enum class AttrReadValue {
    ATTR_READ_CONTINUE,
    ATTR_READ_END,
    ATTR_READ_ERROR,
}

// AttrTypes_t wire codes (src/items/items_definitions.hpp:202-252).
internal const val ATTR_NONE = 0
internal const val ATTR_STORE = 1
internal const val ATTR_ACTION_ID = 4
internal const val ATTR_UNIQUE_ID = 5
internal const val ATTR_TEXT = 6
internal const val ATTR_DESC = 7
internal const val ATTR_TELE_DEST = 8
internal const val ATTR_DEPOT_ID = 10
internal const val ATTR_RUNE_CHARGES = 12
internal const val ATTR_HOUSEDOORID = 14
internal const val ATTR_COUNT = 15
internal const val ATTR_DURATION = 16
internal const val ATTR_DECAYING_STATE = 17
internal const val ATTR_WRITTENDATE = 18
internal const val ATTR_WRITTENBY = 19
internal const val ATTR_SLEEPERGUID = 20
internal const val ATTR_SLEEPSTART = 21
internal const val ATTR_CHARGES = 22
internal const val ATTR_CONTAINER_ITEMS = 23
internal const val ATTR_NAME = 24
internal const val ATTR_ARTICLE = 25
internal const val ATTR_PLURALNAME = 26
internal const val ATTR_WEIGHT = 27
internal const val ATTR_ATTACK = 28
internal const val ATTR_DEFENSE = 29
internal const val ATTR_EXTRADEFENSE = 30
internal const val ATTR_ARMOR = 31
internal const val ATTR_HITCHANCE = 32
internal const val ATTR_SHOOTRANGE = 33
internal const val ATTR_SPECIAL = 34
internal const val ATTR_IMBUEMENT_SLOT = 35
internal const val ATTR_OPENCONTAINER = 36
internal const val ATTR_CUSTOM_ATTRIBUTES = 37
internal const val ATTR_QUICKLOOTCONTAINER = 38
internal const val ATTR_AMOUNT = 39
internal const val ATTR_TIER = 40
internal const val ATTR_CUSTOM = 41
internal const val ATTR_STORE_INBOX_CATEGORY = 42
internal const val ATTR_OWNER = 43
internal const val ATTR_OBTAINCONTAINER = 44
internal const val ATTR_MANTRA = 45

/**
 * Mirror of Canary's `CustomAttribute` (`src/items/functions/item/custom_attribute.hpp:14-84`):
 * a string key plus an `int64_t`/`std::string`/`double`/`bool` variant. The wire
 * type codes come from `CustomAttribute::serialize`/`unserialize`
 * (custom_attribute.cpp:88-153).
 */
class CustomAttribute {
    private val stringKey: String

    /** `@PublishedApi` so [hasValue] can reify the C++ `std::variant` check. */
    @PublishedApi
    internal var value: Any?
        private set

    /** `CustomAttribute()` default constructor: the variant holds `int64_t` 0. */
    constructor() {
        stringKey = ""
        value = 0L
    }

    constructor(stringKey: String, value: Long) {
        this.stringKey = stringKey
        this.value = value
    }

    constructor(stringKey: String, value: String) {
        this.stringKey = stringKey
        this.value = value
    }

    constructor(stringKey: String, value: Double) {
        this.stringKey = stringKey
        this.value = value
    }

    constructor(stringKey: String, value: Boolean) {
        this.stringKey = stringKey
        this.value = value
    }

    /** C++ implicit copy constructor. */
    constructor(other: CustomAttribute) {
        stringKey = other.stringKey
        value = other.value
    }

    /** `CustomAttribute::getStringKey` (custom_attribute.cpp:34). */
    fun getStringKey(): String = stringKey

    /** `CustomAttribute::getInteger` (custom_attribute.cpp:38): 0 for another variant. */
    fun getInteger(): Long = value as? Long ?: 0L

    /** `CustomAttribute::getString` (custom_attribute.cpp:47): "" for another variant. */
    fun getString(): String = value as? String ?: ""

    /** `CustomAttribute::getDouble` (custom_attribute.cpp:56): 0.0 for another variant. */
    fun getDouble(): Double = value as? Double ?: 0.0

    /** `CustomAttribute::getBool` (custom_attribute.cpp:65): false for another variant. */
    fun getBool(): Boolean = value as? Boolean ?: false

    /** `CustomAttribute::setValue(int64_t)` (custom_attribute.hpp:49): keeps the variant type. */
    fun setValue(newValue: Long) {
        if (value is Long) {
            value = newValue
        }
    }

    /** `CustomAttribute::setValue(const std::string &)` (custom_attribute.hpp:54). */
    fun setValue(newValue: String) {
        if (value is String) {
            value = newValue
        }
    }

    /** `CustomAttribute::setValue(double)` (custom_attribute.hpp:59). */
    fun setValue(newValue: Double) {
        if (value is Double) {
            value = newValue
        }
    }

    /** `CustomAttribute::setValue(bool)` (custom_attribute.hpp:64). */
    fun setValue(newValue: Boolean) {
        if (value is Boolean) {
            value = newValue
        }
    }

    /** `CustomAttribute::hasValue<T>` (custom_attribute.hpp:70). */
    inline fun <reified T> hasValue(): Boolean = value is T

    /** `CustomAttribute::serialize` (custom_attribute.cpp:88). */
    fun serialize(propWriteStream: PropWriteStream) {
        when (val current = value) {
            is String -> {
                propWriteStream.writeU8(CUSTOM_ATTRIBUTE_STRING)
                propWriteStream.writeString(current)
            }

            is Long -> {
                propWriteStream.writeU8(CUSTOM_ATTRIBUTE_INTEGER)
                propWriteStream.writeI64(current)
            }

            is Double -> {
                propWriteStream.writeU8(CUSTOM_ATTRIBUTE_DOUBLE)
                propWriteStream.writeU64(java.lang.Double.doubleToRawLongBits(current))
            }

            is Boolean -> {
                propWriteStream.writeU8(CUSTOM_ATTRIBUTE_BOOLEAN)
                propWriteStream.writeU8(if (current) 1 else 0)
            }
        }
    }

    /** `CustomAttribute::unserialize` (custom_attribute.cpp:105). */
    fun unserialize(propStream: PropStream): Boolean {
        val type = propStream.readU8() ?: return false
        return when (type) {
            CUSTOM_ATTRIBUTE_STRING -> {
                value = propStream.readString() ?: return false
                true
            }

            CUSTOM_ATTRIBUTE_INTEGER -> {
                value = propStream.readI64() ?: return false
                true
            }

            CUSTOM_ATTRIBUTE_DOUBLE -> {
                val bits = propStream.readU64() ?: return false
                value = java.lang.Double.longBitsToDouble(bits)
                true
            }

            CUSTOM_ATTRIBUTE_BOOLEAN -> {
                val bool = propStream.readU8() ?: return false
                value = bool != 0
                true
            }

            else -> false
        }
    }

    private companion object {
        /** CustomAttribute wire variants (custom_attribute.cpp:112-148). */
        const val CUSTOM_ATTRIBUTE_STRING = 1
        const val CUSTOM_ATTRIBUTE_INTEGER = 2
        const val CUSTOM_ATTRIBUTE_DOUBLE = 3
        const val CUSTOM_ATTRIBUTE_BOOLEAN = 4
    }
}

open class Item(
    var id: Int,
    count: Int = 0,
    val type: ItemType? = null
) : Thing {
    /** `Item::count` (src/items/item.hpp:775), the number of stacked items. */
    var count: Int = count

    private val attributes = mutableMapOf<ItemAttribute, Any>()

    /**
     * `ItemAttribute::customAttributeMap` (src/items/functions/item/attribute.hpp:166):
     * a `std::map<std::string, CustomAttribute>` keyed case-insensitively, so the
     * ascending key iteration order is mirrored by `sortedMapOf`.
     */
    private val customAttributes = sortedMapOf<String, CustomAttribute>()

    /** `Item::m_parent` (src/items/item.hpp:772); weak like the C++ `std::weak_ptr`. */
    private var parent: WeakReference<Cylinder>? = null

    /** `Item::decayDisabled` (src/items/item.hpp:779); `isDecayDisabled` reads it. */
    var decayDisabled: Boolean = false

    init {
        val itemCount = count
        this.count = 1
        val itemType = type
        if (itemType == null) {
            if (itemCount != 0) {
                this.count = itemCount
            }
        } else {
            val itemCharges = itemType.charges
            when {
                itemType.isFluidContainer || itemType.isSplash ->
                    setAttribute(ItemAttribute.FLUIDTYPE, itemCount.coerceIn(0, FLUIDS_COUNT).toLong())

                itemType.isStackable -> this.count = when {
                    itemCount != 0 -> itemCount
                    itemCharges != 0 -> itemCharges
                    else -> 1
                }

                itemCharges != 0 ->
                    setAttribute(ItemAttribute.CHARGES, if (itemCount != 0) itemCount else itemCharges)

                else -> this.count = 1
            }
            setDefaultDuration()
        }
    }

    override fun getDescription(lookDistance: Int): String = ""

    /**
     * `Item::getTopParent` (src/items/item.cpp:832-847): walks up the parent
     * chain and returns the deepest cylinder that still has a parent; when the
     * item itself is not a cylinder and the parent is the root, the root is
     * returned (`prevaux ?: aux`).
     */
    fun getTopParent(): Cylinder? {
        val aux = getParent() ?: return getCylinder()
        var previous: Cylinder? = getCylinder()
        var current = aux
        while (current.getParent() != null) {
            previous = current
            current = current.getParent()!!
        }
        return previous ?: current
    }

    /** `Item::getTile` (src/items/item.cpp:850-857). */
    override fun getTile(): Tile? {
        var cylinder = getTopParent()
        if (cylinder?.getParent() != null) {
            cylinder = cylinder.getParent()!!
        }
        return cylinder as? Tile
    }

    override fun getItem(): Item = this

    /** `Item::getThrowRange` (src/items/item.hpp:327-329). */
    override fun getThrowRange(): Int = if (isPickupable()) 15 else 2

    /** `Item::isPushable` (src/items/item.hpp:324-326). */
    override fun isPushable(): Boolean = isMovable()

    /** `Item::getTeleport` (src/items/item.hpp:242-244). */
    open fun getTeleport(): Teleport? = null

    /** `Item::getTrashHolder` (src/items/item.hpp:245-247). */
    open fun getTrashHolder(): TrashHolder? = null

    /** `Item::getMagicField` (src/items/item.hpp:254-256). */
    open fun getMagicField(): MagicField? = null

    /** `Item::setID` (src/items/item.cpp:793-813); the decay re-stamp is a PARITY GAP. */
    fun setID(newId: Int) {
        id = newId
    }

    /** `Item::isBlocking` (src/items/item.hpp:485-487). */
    fun isBlocking(): Boolean = type?.blockSolid == true

    /** `Item::isStackable` (src/items/item.hpp:488-490). */
    fun isStackable(): Boolean = type?.isStackable == true

    /** `Item::isAlwaysOnTop` (src/items/item.hpp:496-498). */
    fun isAlwaysOnTop(): Boolean = (type?.alwaysOnTopOrder ?: 0) != 0

    /** `Item::isGroundTile` (src/items/item.hpp:499-501). */
    fun isGroundTile(): Boolean = type?.isGroundTile == true

    /** `Item::isMagicField` (src/items/item.hpp:502-504). */
    fun isMagicField(): Boolean = type?.isMagicField() == true

    /** `Item::isMovable` (src/items/item.hpp:508-510). */
    fun isMovable(): Boolean = type?.isMovable == true

    /** `Item::isPickupable` (src/items/item.hpp:514-516). */
    fun isPickupable(): Boolean = type?.isPickupable == true

    /** `Item::isHangable` (src/items/item.hpp:520-522). */
    fun isHangable(): Boolean = type?.isHangable == true

    /** `Item::isCarpet` (src/items/item.hpp:562-564). */
    fun isCarpet(): Boolean = type?.isCarpet == true

    /** `Item::canReceiveAutoCarpet` (src/items/item.hpp:565-567). */
    fun canReceiveAutoCarpet(): Boolean = isBlocking() && isAlwaysOnTop() && type?.hasHeight != true

    /** `Item::getItemCount` (src/items/item.hpp:603-605). */
    fun getItemCount(): Int = count

    /** `Item::setItemCount` (src/items/item.hpp:610-612). */
    fun setItemCount(n: Int) {
        count = n
    }

    /** `Item::isOwner(Creature)` (src/items/item.cpp:1549-1561); the player GUID fallback is a PARITY GAP. */
    fun isOwner(owner: Creature?): Boolean {
        if (owner == null) {
            return false
        }
        return isOwner(owner.id)
    }

    open val weight: Double
        get() = (type?.weight ?: 0.0) * if (type?.isStackable == true) count else 1

    /** Mirrors `Item::getClassification` (src/items/item.hpp:753). */
    fun getClassification(): Int = type?.upgradeClassification ?: 0

    /** `Item::getDefaultDuration` (src/items/item.hpp:638). */
    fun getDefaultDuration(): Int = ((type?.decayTime ?: 0L) * 1000L).toInt()

    /** `Item::setDefaultDuration` (src/items/item.hpp:632). */
    fun setDefaultDuration() {
        val duration = getDefaultDuration()
        if (duration != 0) {
            setDuration(duration)
        }
    }

    /** `Item::getParent` (src/items/item.hpp:681). */
    override fun getParent(): Cylinder? = parent?.get()

    /** `Item::setParent` (src/items/item.hpp:684). */
    override fun setParent(cylinder: Cylinder?) {
        parent = cylinder?.let { WeakReference(it) }
    }

    /** `Item::resetParent` (src/items/item.hpp:687). */
    fun resetParent() {
        parent = null
    }

    /** `Item::isRemoved` (src/items/item.cpp:859). */
    override fun isRemoved(): Boolean {
        val currentParent = getParent() ?: return true
        return currentParent.isRemoved()
    }

    /** `Item::canDecay` (src/items/item.cpp:3438). */
    open fun canDecay(): Boolean {
        val itemType = type ?: return false
        if (itemType.decayTo < 0 || itemType.decayTime == 0L || isDecayDisabled()) {
            return false
        }
        if (hasAttribute(ItemAttribute.UNIQUEID)) {
            return false
        }
        return !isRemoved()
    }

    /** `Item::canRemove` (src/items/item.hpp:644). */
    open fun canRemove(): Boolean = true

    /** `Item::onRemoved` (src/items/item.cpp:785-791); game side effects are a PARITY GAP. */
    open fun onRemoved() {
        // ScriptEnvironment/unique-item bookkeeping needs g_game (PARITY GAP).
    }

    /** `Item::stopDecaying` (src/items/item.cpp:3497-3499); the decay engine is wired by its owner. */
    open fun stopDecaying() {
        // g_decay().stopDecay(item) needs the decay scheduler (PARITY GAP).
    }

    /** `ItemProperties::isStoreItem` (src/items/item.hpp:113). */
    fun isStoreItem(): Boolean = intAttribute(ItemAttribute.STORE) > 0

    /** `Item::isDecayDisabled` (src/items/item.hpp:572). */
    fun isDecayDisabled(): Boolean = decayDisabled

    /** `Item::setDefaultSubtype` (src/items/item.cpp:770). */
    fun setDefaultSubtype() {
        count = 1
        val itemCharges = type?.charges ?: 0
        if (itemCharges != 0) {
            if (type?.isStackable == true) {
                count = itemCharges
            } else {
                setAttribute(ItemAttribute.CHARGES, itemCharges.toLong())
            }
        }
    }

    /** `Item::getWeaponType` (src/items/item.hpp:339). */
    fun getWeaponType(): WeaponType_t = type?.weaponType ?: WeaponType_t.WEAPON_NONE

    /** `Item::isCorpse` (src/items/item.hpp:511). */
    fun isCorpse(): Boolean = type?.isCorpse == true

    /** `Item::isQuiver` (src/items/item.hpp:544). */
    fun isQuiver(): Boolean = type?.isQuiver() == true

    /** `ItemProperties::getItemAmount` (src/items/item.hpp). */
    fun getItemAmount(): Long = count.toLong()

    /** `Item::getAmmoType` (src/items/item.hpp:342). */
    fun getAmmoType(): Ammo_t {
        val ammoType = type?.ammoType ?: 0
        return Ammo_t.entries.firstOrNull { it.value == ammoType } ?: Ammo_t.AMMO_NONE
    }

    /** `Item::getShootRange` (src/items/item.hpp:345). */
    fun getShootRange(): Int {
        if (hasAttribute(ItemAttribute.SHOOTRANGE)) {
            return intAttribute(ItemAttribute.SHOOTRANGE).toInt() and 0xFF
        }
        return type?.shootRange ?: 1
    }

    /** `Item::getAttack` (src/items/item.hpp:400). */
    fun getAttack(): Int =
        if (hasAttribute(ItemAttribute.ATTACK)) {
            intAttribute(ItemAttribute.ATTACK).toInt()
        } else {
            type?.attack ?: 0
        }

    /** `Item::getArmor` (src/items/item.hpp:406). */
    fun getArmor(): Int =
        if (hasAttribute(ItemAttribute.ARMOR)) {
            intAttribute(ItemAttribute.ARMOR).toInt()
        } else {
            type?.armor ?: 0
        }

    /** `Item::getDefense` (src/items/item.hpp:412). */
    fun getDefense(): Int =
        if (hasAttribute(ItemAttribute.DEFENSE)) {
            intAttribute(ItemAttribute.DEFENSE).toInt()
        } else {
            type?.defense ?: 0
        }

    /** `Item::getExtraDefense` (src/items/item.hpp:418). */
    fun getExtraDefense(): Int =
        if (hasAttribute(ItemAttribute.EXTRADEFENSE)) {
            intAttribute(ItemAttribute.EXTRADEFENSE).toInt()
        } else {
            type?.extraDefense ?: 0
        }

    /** `Item::getMantra` (src/items/item.hpp:472). */
    fun getMantra(): Int =
        if (hasAttribute(ItemAttribute.MANTRA)) {
            intAttribute(ItemAttribute.MANTRA).toInt()
        } else {
            type?.mantra ?: 0
        }

    /** `Item::getHitChance` (src/items/item.hpp:456). */
    fun getHitChance(): Int =
        if (hasAttribute(ItemAttribute.HITCHANCE)) {
            intAttribute(ItemAttribute.HITCHANCE).toByte().toInt()
        } else {
            type?.hitChance ?: 0
        }

    /** `Item::getImbuementSlot` (src/items/item.hpp:447). */
    fun getImbuementSlot(): Int =
        if (hasAttribute(ItemAttribute.IMBUEMENT_SLOT)) {
            intAttribute(ItemAttribute.IMBUEMENT_SLOT).toInt() and 0xFF
        } else {
            type?.imbuementSlot ?: 0
        }

    /** `Item::getSlotPosition` (src/items/item.hpp:453). */
    fun getSlotPosition(): Int = type?.slotPosition ?: SLOTP_HAND

    /** `Item::getStackSize` (src/items/item.hpp:595). */
    fun getStackSize(): Int = if (type?.isStackable == true) type.stackSize else 1

    /** `Item::getBaseWeight` (src/items/item.hpp:353), in hundredths of an ounce like the C++ type. */
    fun getBaseWeight(): Int {
        if (hasAttribute(ItemAttribute.WEIGHT)) {
            return (intAttribute(ItemAttribute.WEIGHT) and 0xFFFFFFFFL).toInt()
        }
        return Math.round((type?.weight ?: 0.0) * 100.0).toInt()
    }

    /** `Item::getTier` (src/items/item.cpp:605). */
    fun getTier(maxItemTier: Int = FORGE_MAX_ITEM_TIER_DEFAULT): Int {
        if (!hasAttribute(ItemAttribute.TIER)) {
            return 0
        }

        val tier = intAttribute(ItemAttribute.TIER).toInt() and 0xFF
        if (tier > maxItemTier) {
            logger.error { "getTier - Item ${getName()} have a wrong tier $tier" }
            return 0
        }

        return tier
    }

    /** `Item::setTier` (src/items/item.cpp:619). */
    fun setTier(tier: Int, maxItemTier: Int = FORGE_MAX_ITEM_TIER_DEFAULT) {
        if (tier > maxItemTier) {
            logger.error { "setTier - It is not possible to set a tier higher than $maxItemTier" }
            return
        }

        if ((type?.upgradeClassification ?: 0) != 0) {
            setAttribute(ItemAttribute.TIER, tier.toLong())
        }
    }

    /** `Item::getDodgeChance` (src/items/item.cpp:545); the coefficients default to `RUSE_CHANCE_FORMULA_*`. */
    fun getDodgeChance(
        a: Double = RUSE_CHANCE_FORMULA_A,
        b: Double = RUSE_CHANCE_FORMULA_B,
        c: Double = RUSE_CHANCE_FORMULA_C
    ): Double = tierChance(a, b, c)

    /** `Item::getFatalChance` (src/items/item.cpp:557); the coefficients default to `ONSLAUGHT_CHANCE_FORMULA_*`. */
    fun getFatalChance(
        a: Double = ONSLAUGHT_CHANCE_FORMULA_A,
        b: Double = ONSLAUGHT_CHANCE_FORMULA_B,
        c: Double = ONSLAUGHT_CHANCE_FORMULA_C
    ): Double = tierChance(a, b, c)

    /** `Item::getMomentumChance` (src/items/item.cpp:569); the coefficients default to `MOMENTUM_CHANCE_FORMULA_*`. */
    fun getMomentumChance(
        a: Double = MOMENTUM_CHANCE_FORMULA_A,
        b: Double = MOMENTUM_CHANCE_FORMULA_B,
        c: Double = MOMENTUM_CHANCE_FORMULA_C
    ): Double = tierChance(a, b, c)

    /** `Item::getTranscendenceChance` (src/items/item.cpp:581); the coefficients default to `TRANSCENDENCE_CHANCE_FORMULA_*`. */
    fun getTranscendenceChance(
        a: Double = TRANSCENDENCE_CHANCE_FORMULA_A,
        b: Double = TRANSCENDENCE_CHANCE_FORMULA_B,
        c: Double = TRANSCENDENCE_CHANCE_FORMULA_C
    ): Double = tierChance(a, b, c)

    /** `Item::getAmplificationChance` (src/items/item.cpp:593); the coefficients default to `AMPLIFICATION_CHANCE_FORMULA_*`. */
    fun getAmplificationChance(
        a: Double = AMPLIFICATION_CHANCE_FORMULA_A,
        b: Double = AMPLIFICATION_CHANCE_FORMULA_B,
        c: Double = AMPLIFICATION_CHANCE_FORMULA_C
    ): Double = tierChance(a, b, c)

    /** `Item::getWorth` (src/items/item.cpp:3456), in gold. */
    fun getWorth(): Long = when (id) {
        ITEM_GOLD_COIN -> count.toLong()
        ITEM_PLATINUM_COIN -> count.toLong() * 100
        ITEM_CRYSTAL_COIN -> count.toLong() * 10000
        else -> 0L
    }

    /** `Item::getForgeSlivers` (src/items/item.cpp:3472). */
    fun getForgeSlivers(): Long = if (id == ITEM_FORGE_SLIVER) count.toLong() else 0L

    /** `Item::getForgeCores` (src/items/item.cpp:3480). */
    fun getForgeCores(): Long = if (id == ITEM_FORGE_CORE) count.toLong() else 0L

    /** `Item::getLightInfo` (src/items/item.cpp:3488). */
    fun getLightInfo(): LightInfo =
        LightInfo(level = type?.lightLevel ?: 0, color = type?.lightColor ?: 0)

    /** `Item::hasProperty` (src/items/item.cpp:1585). */
    fun hasProperty(prop: ItemProperty): Boolean {
        val itemType = type ?: return false
        return when (prop) {
            ItemProperty.CONST_PROP_BLOCKSOLID -> itemType.blockSolid
            ItemProperty.CONST_PROP_MOVABLE -> canBeMoved()
            ItemProperty.CONST_PROP_HASHEIGHT -> itemType.hasHeight
            ItemProperty.CONST_PROP_BLOCKPROJECTILE -> itemType.blockProjectile
            ItemProperty.CONST_PROP_BLOCKPATH -> itemType.blockPathFind
            ItemProperty.CONST_PROP_ISVERTICAL -> itemType.isVertical
            ItemProperty.CONST_PROP_ISHORIZONTAL -> itemType.isHorizontal
            ItemProperty.CONST_PROP_IMMOVABLEBLOCKSOLID -> itemType.blockSolid && !canBeMoved()
            ItemProperty.CONST_PROP_IMMOVABLEBLOCKPATH -> itemType.blockPathFind && !canBeMoved()
            ItemProperty.CONST_PROP_IMMOVABLENOFIELDBLOCKPATH ->
                itemType.type != ItemTypes_t.ITEM_TYPE_MAGICFIELD && itemType.blockPathFind && !canBeMoved()

            ItemProperty.CONST_PROP_NOFIELDBLOCKPATH ->
                itemType.type != ItemTypes_t.ITEM_TYPE_MAGICFIELD && itemType.blockPathFind

            ItemProperty.CONST_PROP_SUPPORTHANGABLE -> itemType.isHorizontal || itemType.isVertical
        }
    }

    /** `Item::canBeMoved` (src/items/item.cpp:1617). */
    fun canBeMoved(): Boolean {
        if (hasAttribute(ItemAttribute.UNIQUEID)) {
            return false
        }
        if (hasAttribute(ItemAttribute.ACTIONID) &&
            intAttribute(ItemAttribute.ACTIONID) and 0xFFFFL == IMMOVABLE_ACTION_ID.toLong()
        ) {
            return false
        }
        return type?.isMovable == true
    }

    /** `Item::setOwner(uint32_t owner)` (src/items/item.hpp:273). */
    fun setOwner(owner: Long) {
        setAttribute(ItemAttribute.OWNER, owner and 0xFFFFFFFFL)
    }

    /**
     * `Item::getOwnerId` (src/items/item.cpp:1563).
     */
    open fun getOwnerId(): Long = intAttribute(ItemAttribute.OWNER) and 0xFFFFFFFFL

    /**
     * `Item::isOwner(uint32_t ownerId)` (src/items/item.cpp:818). The
     * player-id/GUID lookups need `g_game()` and return the direct equality
     * result only (PARITY GAP).
     */
    fun isOwner(ownerId: Long): Boolean = getOwnerId() == ownerId

    /** `Item::hasOwner` (src/items/item.hpp:287). */
    fun hasOwner(): Boolean = getOwnerId() != 0L

    /** `Item::canBeMovedToStore` (src/items/item.hpp:291). */
    fun canBeMovedToStore(): Boolean = isStoreItem() || hasOwner()

    /** `Item::getName` (src/items/item.hpp:576). */
    fun getName(): String =
        if (hasAttribute(ItemAttribute.NAME)) stringAttribute(ItemAttribute.NAME) else type?.name ?: ""

    /** `Item::getPluralName` (src/items/item.hpp:582). */
    fun getPluralName(): String =
        if (hasAttribute(ItemAttribute.PLURALNAME)) {
            stringAttribute(ItemAttribute.PLURALNAME)
        } else {
            type?.getPluralName() ?: ""
        }

    /** `Item::getArticle` (src/items/item.hpp:588). */
    fun getArticle(): String =
        if (hasAttribute(ItemAttribute.ARTICLE)) stringAttribute(ItemAttribute.ARTICLE) else type?.article ?: ""

    /** `Item::getNameDescription` (src/items/item.cpp:3388). */
    fun getNameDescription(): String = getNameDescription(type ?: ItemType(id, ""), this)

    /** `Item::getWeightDescription(uint32_t weight)` (src/items/item.cpp:3415). */
    fun getWeightDescription(weight: Int): String =
        getWeightDescription(type ?: ItemType(id, ""), weight, count)

    /** `Item::getWeightDescription()` (src/items/item.cpp:3420). */
    fun getWeightDescription(): String {
        val hundredths = Math.round(weight * 100.0).toInt()
        if (hundredths == 0) {
            return ""
        }
        return getWeightDescription(hundredths)
    }

    /** `Item::clone` (src/items/item.cpp:716). */
    open fun clone(): Item {
        val copy = createLike(type, id, count)
        copy.attributes.putAll(attributes)
        for ((key, customAttribute) in customAttributes) {
            copy.customAttributes[key] = CustomAttribute(customAttribute)
        }
        copy.imbuementRegistry = imbuementRegistry
        return copy
    }

    /** `Item::equals` (src/items/item.cpp:730). */
    fun equals(compareItem: Item?): Boolean {
        if (compareItem == null) {
            return false
        }

        if (id != compareItem.id) {
            return false
        }

        if (isStoreItem() != compareItem.isStoreItem()) {
            return false
        }

        if (getOwnerId() != compareItem.getOwnerId()) {
            return false
        }

        for ((attribute, value) in attributes) {
            if (attribute == ItemAttribute.STORE) {
                continue
            }

            for ((compareAttribute, compareValue) in compareItem.attributes) {
                if (attribute != compareAttribute) {
                    continue
                }

                if (ItemAttribute.isAttributeInteger(attribute) &&
                    (value as? Number)?.toLong() != (compareValue as? Number)?.toLong()
                ) {
                    return false
                }

                if (ItemAttribute.isAttributeString(attribute) && value != compareValue) {
                    return false
                }
            }
        }

        return true
    }

    /** `Item::hasMarketAttributes` (src/items/item.cpp:3539). */
    fun hasMarketAttributes(): Boolean {
        if (attributes.isEmpty() && customAttributes.isEmpty()) {
            return true
        }

        for ((attribute, value) in attributes) {
            when (attribute) {
                ItemAttribute.CHARGES -> {
                    if ((value as? Number)?.toInt() != (type?.charges ?: 0)) {
                        return false
                    }
                }

                ItemAttribute.DURATION -> {
                    if ((value as? Number)?.toLong() != getDefaultDuration().toLong()) {
                        return false
                    }
                }

                ItemAttribute.TIER -> {
                    if ((value as? Number)?.toInt() != getTier()) {
                        return false
                    }
                }

                else -> {}
            }
        }

        return !hasImbuements() && !isStoreItem() && !hasOwner()
    }

    /** `Item::isStowable` (src/items/item.hpp:491). */
    fun isStowable(): Boolean {
        val itemType = type ?: return false
        val wareId = itemType.wareId
        return hasMarketAttributes() &&
            getTier() == 0 &&
            wareId > 0 &&
            !itemType.isContainer &&
            wareId == itemType.id
    }

    /** `Item::isItemStorable` (src/items/item.cpp:891). */
    fun isItemStorable(): Boolean {
        if (isStoreItem() || hasOwner() || canDecay()) {
            return false
        }

        val container = getContainer()
        val containerHasItems = container != null && container.items.isNotEmpty()
        return isStowable() || containerHasItems
    }

    private fun tierChance(a: Double, b: Double, c: Double): Double {
        if (getTier() == 0) {
            return 0.0
        }
        val tier = getTier().toDouble()
        return a * tier * tier + b * tier + c
    }

    fun hasAttribute(attribute: ItemAttribute): Boolean = attributes.containsKey(attribute)

    fun getAttribute(attribute: ItemAttribute): Any? = when {
        ItemAttribute.isAttributeInteger(attribute) -> attributes[attribute] as? Long ?: 0L
        ItemAttribute.isAttributeString(attribute) -> attributes[attribute] as? String ?: ""
        else -> null
    }

    fun setAttribute(attribute: ItemAttribute, value: Any?) {
        when {
            ItemAttribute.isAttributeInteger(attribute) -> {
                val intValue = (value as? Number)?.toLong() ?: return
                attributes[attribute] = intValue
            }

            ItemAttribute.isAttributeString(attribute) -> {
                val stringValue = value as? String ?: return
                if (stringValue.isEmpty()) return
                attributes[attribute] = stringValue
            }
        }
    }

    fun removeAttribute(attribute: ItemAttribute): Boolean = attributes.remove(attribute) != null

    // --- Custom attributes (src/items/functions/item/attribute.cpp:102-153) ----

    /** `ItemProperties::getCustomAttributeMap` (attribute.cpp:105). */
    fun getCustomAttributeMap(): Map<String, CustomAttribute> = customAttributes

    /**
     * `ItemProperties::getCustomAttribute` (item.hpp:78, attribute.cpp:114):
     * the lookup is case-insensitive (`asLowerCaseString`).
     */
    fun getCustomAttribute(attributeName: String): CustomAttribute? =
        customAttributes[attributeName.lowercase()]

    /** `ItemProperties::setCustomAttribute(key, int64_t)` (attribute.cpp:121). */
    fun setCustomAttribute(key: String, value: Long) {
        customAttributes[key.lowercase()] = CustomAttribute(key, value)
    }

    /** `ItemProperties::setCustomAttribute(key, const std::string &)` (attribute.cpp:126). */
    fun setCustomAttribute(key: String, value: String) {
        customAttributes[key.lowercase()] = CustomAttribute(key, value)
    }

    /** `ItemProperties::setCustomAttribute(key, double)` (attribute.cpp:131). */
    fun setCustomAttribute(key: String, value: Double) {
        customAttributes[key.lowercase()] = CustomAttribute(key, value)
    }

    /** `ItemProperties::setCustomAttribute(key, bool)` (attribute.cpp:136). */
    fun setCustomAttribute(key: String, value: Boolean) {
        customAttributes[key.lowercase()] = CustomAttribute(key, value)
    }

    /** `ItemProperties::addCustomAttribute` (attribute.cpp:141). */
    fun addCustomAttribute(key: String, customAttribute: CustomAttribute) {
        customAttributes[key.lowercase()] = customAttribute
    }

    /** `ItemProperties::hasCustomAttribute` (item.hpp:95). */
    fun hasCustomAttribute(): Boolean = customAttributes.isNotEmpty()

    /** `ItemProperties::removeCustomAttribute` (attribute.cpp:145). */
    fun removeCustomAttribute(attributeName: String): Boolean =
        customAttributes.remove(attributeName.lowercase()) != null

    // --- Imbuement custom attributes (src/items/item.cpp:453-488, item.hpp:713-735) ----

    /**
     * `g_imbuements()` registry seam (src/items/item.cpp:478): when assigned,
     * [hasImbuements] resolves the packed imbuement id against it exactly like
     * `Item::getImbuementInfo`; when null the custom-attribute presence is used.
     * The registry is runtime-owned and only borrowed here; the composition root
     * assigns the loaded registry to the items it composes.
     */
    var imbuementRegistry: Imbuements? = null

    /** `Item::hasImbuementAttribute` (src/items/item.cpp:453). */
    fun hasImbuementAttribute(attributeSlot: String): Boolean = getCustomAttribute(attributeSlot) != null

    /**
     * `Item::getImbuementInfo`'s `attribute->getAttribute<uint32_t>()` read
     * (src/items/item.cpp:478): null when the slot has no attribute, otherwise
     * the packed `(duration << 8) | imbuementId` clamped to `uint32_t`.
     */
    fun getImbuementAttribute(slot: Int): Long? =
        getCustomAttribute("${ITEM_IMBUEMENT_SLOT + slot}")?.getInteger()?.and(0xFFFFFFFFL)

    /** `Item::setImbuement` (src/items/item.cpp:485): `(duration << 8) | imbuementId`, 0 for an empty slot. */
    fun setImbuement(slot: Int, imbuementId: Int, duration: Int) {
        val packed = if (duration > 0) (duration.toLong() shl 8) or imbuementId.toLong() else 0L
        setCustomAttribute("${ITEM_IMBUEMENT_SLOT + slot}", packed)
    }

    /** `Item::decayImbuementTime` (src/items/item.hpp:713). */
    fun decayImbuementTime(slot: Int, imbuementId: Int, duration: Int) = setImbuement(slot, imbuementId, duration)

    /** `Item::clearImbuement` (src/items/item.hpp:716). */
    fun clearImbuement(slot: Int, imbuementId: Int) = setImbuement(slot, imbuementId, 0)

    /**
     * `Item::hasImbuements` (src/items/item.hpp:728-737) over
     * `Item::getImbuementInfo` (src/items/item.cpp:472-483): the packed slot
     * value counts only when its duration (`value >> 8`) is non-zero and, when
     * [imbuementRegistry] is assigned, the imbuement id (`value & 0xFF`) resolves
     * through `g_imbuements().getImbuement`. Without the registry seam only the
     * packed encoding is validated.
     */
    fun hasImbuements(): Boolean {
        for (slot in 0 until getImbuementSlot()) {
            val packed = getImbuementAttribute(slot) ?: continue
            if (packed == 0L || (packed ushr 8) == 0L) {
                continue
            }
            val registry = imbuementRegistry
            if (registry == null || registry.getImbuement((packed and 0xFF).toInt()) != null) {
                return true
            }
        }
        return false
    }

    /** Mirrors `item:getActionId()` / `item:setActionId()` (src/lua/functions/items/item_functions.cpp:291). */
    fun getActionId(): Int = ((attributes[ItemAttribute.ACTIONID] as? Long ?: 0L) and 0xFFFFL).toInt()

    fun setActionId(actionId: Int) {
        setAttribute(ItemAttribute.ACTIONID, actionId.toLong() and 0xFFFFL)
    }

    /** Mirrors `item:getUniqueId()` (src/lua/functions/items/item_functions.cpp:276). */
    fun getUniqueId(): Long = (attributes[ItemAttribute.UNIQUEID] as? Long ?: 0L) and 0xFFFFL

    fun setUniqueId(uniqueId: Long) {
        setAttribute(ItemAttribute.UNIQUEID, uniqueId and 0xFFFFL)
    }

    fun setDuration(time: Int) {
        setAttribute(ItemAttribute.DURATION, maxOf(0, time).toLong())
    }

    /** Mirrors `ItemProperties::getDuration` (src/items/item.cpp:3621). */
    fun getDuration(): Int {
        val decayState = getDecaying()
        return if (decayState == ItemDecayState.DECAYING_TRUE || decayState == ItemDecayState.DECAYING_STOPPING) {
            val timestamp = attributes[ItemAttribute.DURATION_TIMESTAMP] as? Long ?: 0L
            maxOf(0L, timestamp - otsysTime()).toInt()
        } else {
            (attributes[ItemAttribute.DURATION] as? Long ?: 0L).toInt()
        }
    }

    /** Mirrors `ItemProperties::setDecaying` (src/items/item.hpp:121). */
    fun setDecaying(decayState: ItemDecayState) {
        setAttribute(ItemAttribute.DECAYSTATE, decayState.value.toLong())
        if (decayState == ItemDecayState.DECAYING_FALSE) {
            removeAttribute(ItemAttribute.DURATION_TIMESTAMP)
        }
    }

    fun getDecaying(): ItemDecayState =
        ItemDecayState.fromValue((attributes[ItemAttribute.DECAYSTATE] as? Long ?: 0L).toInt())

    /** `Item::loadedFromMap` (src/items/item.hpp:777). */
    var loadedFromMap: Boolean = false

    /** `Item::isLoadedFromMap` (src/items/item.hpp:671-673). */
    fun isLoadedFromMap(): Boolean = loadedFromMap

    /** `Item::getSubType` (src/items/item.cpp:867-877). */
    fun getSubType(): Int {
        val itemType = type
        return when {
            itemType?.isFluidContainer == true || itemType?.isSplash == true ->
                ((attributes[ItemAttribute.FLUIDTYPE] as? Number)?.toInt() ?: 0)

            itemType?.isStackable == true -> count
            (itemType?.charges ?: 0) != 0 -> ((attributes[ItemAttribute.CHARGES] as? Number)?.toInt() ?: 0)
            else -> count
        }
    }

    /** `Item::setSubType` (src/items/item.cpp:899-910). */
    fun setSubType(n: Int) {
        val itemType = type
        when {
            itemType?.isFluidContainer == true || itemType?.isSplash == true ->
                setAttribute(ItemAttribute.FLUIDTYPE, n.toLong())

            itemType?.isStackable == true -> count = n
            (itemType?.charges ?: 0) != 0 -> setAttribute(ItemAttribute.CHARGES, n.toLong())
            else -> count = n
        }
    }

    /** `Item::getBed` (src/items/item.hpp:257). */
    open fun getBed(): BedItem? = null

    /** `Item::getDoor` (src/items/item.hpp:253). */
    open fun getDoor(): Door? = null

    /** `Item::isSavedToHouses` (src/items/item.cpp:2332-2335). */
    fun isSavedToHouses(): Boolean {
        val itemType = type ?: return false
        val container = getContainer()
        return itemType.isMovable ||
            itemType.isWrappable ||
            itemType.isCarpet ||
            getDoor() != null ||
            (container != null && !container.isEmpty()) ||
            itemType.canWriteText ||
            getBed() != null ||
            itemType.transformOnUse != 0
    }

    /** `Item::serializeAttr` (src/items/item.cpp:1368-1539). */
    open fun serializeAttr(propWriteStream: PropWriteStream) {
        val itemType = type

        val timeStamp = intAttribute(ItemAttribute.STORE)
        if (timeStamp != 0L) {
            propWriteStream.writeU8(ATTR_STORE)
            propWriteStream.writeI64(timeStamp)
        }

        if (itemType?.isStackable == true || itemType?.isFluidContainer == true || itemType?.isSplash == true) {
            propWriteStream.writeU8(ATTR_COUNT)
            propWriteStream.writeU8(getSubType())
        }

        val charges = intAttribute(ItemAttribute.CHARGES)
        if (charges != 0L) {
            propWriteStream.writeU8(ATTR_CHARGES)
            propWriteStream.writeU16(charges.toInt())
        }

        if (itemType?.isMovable == true) {
            val actionId = intAttribute(ItemAttribute.ACTIONID)
            if (actionId != 0L) {
                propWriteStream.writeU8(ATTR_ACTION_ID)
                propWriteStream.writeU16(actionId.toInt())
            }
        }

        val text = stringAttribute(ItemAttribute.TEXT)
        if (text.isNotEmpty()) {
            propWriteStream.writeU8(ATTR_TEXT)
            propWriteStream.writeString(text)
        }

        val writtenDate = intAttribute(ItemAttribute.DATE)
        if (writtenDate != 0L) {
            propWriteStream.writeU8(ATTR_WRITTENDATE)
            propWriteStream.writeU64(writtenDate)
        }

        val writer = stringAttribute(ItemAttribute.WRITER)
        if (writer.isNotEmpty()) {
            propWriteStream.writeU8(ATTR_WRITTENBY)
            propWriteStream.writeString(writer)
        }

        val description = stringAttribute(ItemAttribute.DESCRIPTION)
        if (description.isNotEmpty()) {
            propWriteStream.writeU8(ATTR_DESC)
            propWriteStream.writeString(description)
        }

        if (attributes.containsKey(ItemAttribute.DURATION)) {
            propWriteStream.writeU8(ATTR_DURATION)
            propWriteStream.writeI32(getDuration())
        }

        val decayState = getDecaying()
        if (decayState == ItemDecayState.DECAYING_TRUE || decayState == ItemDecayState.DECAYING_PENDING) {
            propWriteStream.writeU8(ATTR_DECAYING_STATE)
            propWriteStream.writeU8(decayState.value)
        }

        if (attributes.containsKey(ItemAttribute.NAME)) {
            propWriteStream.writeU8(ATTR_NAME)
            propWriteStream.writeString(stringAttribute(ItemAttribute.NAME))
        }

        if (attributes.containsKey(ItemAttribute.ARTICLE)) {
            propWriteStream.writeU8(ATTR_ARTICLE)
            propWriteStream.writeString(stringAttribute(ItemAttribute.ARTICLE))
        }

        if (attributes.containsKey(ItemAttribute.PLURALNAME)) {
            propWriteStream.writeU8(ATTR_PLURALNAME)
            propWriteStream.writeString(stringAttribute(ItemAttribute.PLURALNAME))
        }

        if (attributes.containsKey(ItemAttribute.WEIGHT)) {
            propWriteStream.writeU8(ATTR_WEIGHT)
            propWriteStream.writeU32(intAttribute(ItemAttribute.WEIGHT) and 0xFFFFFFFFL)
        }

        if (attributes.containsKey(ItemAttribute.ATTACK)) {
            propWriteStream.writeU8(ATTR_ATTACK)
            propWriteStream.writeI32(intAttribute(ItemAttribute.ATTACK).toInt())
        }

        if (attributes.containsKey(ItemAttribute.DEFENSE)) {
            propWriteStream.writeU8(ATTR_DEFENSE)
            propWriteStream.writeI32(intAttribute(ItemAttribute.DEFENSE).toInt())
        }

        if (attributes.containsKey(ItemAttribute.EXTRADEFENSE)) {
            propWriteStream.writeU8(ATTR_EXTRADEFENSE)
            propWriteStream.writeI32(intAttribute(ItemAttribute.EXTRADEFENSE).toInt())
        }

        if (attributes.containsKey(ItemAttribute.MANTRA)) {
            propWriteStream.writeU8(ATTR_MANTRA)
            propWriteStream.writeI32(intAttribute(ItemAttribute.MANTRA).toInt())
        }

        if (attributes.containsKey(ItemAttribute.IMBUEMENT_SLOT)) {
            propWriteStream.writeU8(ATTR_IMBUEMENT_SLOT)
            propWriteStream.writeI32(intAttribute(ItemAttribute.IMBUEMENT_SLOT).toInt())
        }

        if (attributes.containsKey(ItemAttribute.OPENCONTAINER)) {
            propWriteStream.writeU8(ATTR_OPENCONTAINER)
            propWriteStream.writeU8(intAttribute(ItemAttribute.OPENCONTAINER).toInt())
        }

        if (attributes.containsKey(ItemAttribute.ARMOR)) {
            propWriteStream.writeU8(ATTR_ARMOR)
            propWriteStream.writeI32(intAttribute(ItemAttribute.ARMOR).toInt())
        }

        if (attributes.containsKey(ItemAttribute.HITCHANCE)) {
            propWriteStream.writeU8(ATTR_HITCHANCE)
            propWriteStream.writeU8(intAttribute(ItemAttribute.HITCHANCE).toInt())
        }

        if (attributes.containsKey(ItemAttribute.SHOOTRANGE)) {
            propWriteStream.writeU8(ATTR_SHOOTRANGE)
            propWriteStream.writeU8(intAttribute(ItemAttribute.SHOOTRANGE).toInt())
        }

        if (attributes.containsKey(ItemAttribute.SPECIAL)) {
            propWriteStream.writeU8(ATTR_SPECIAL)
            propWriteStream.writeString(stringAttribute(ItemAttribute.SPECIAL))
        }

        if (attributes.containsKey(ItemAttribute.QUICKLOOTCONTAINER)) {
            propWriteStream.writeU8(ATTR_QUICKLOOTCONTAINER)
            propWriteStream.writeU32(intAttribute(ItemAttribute.QUICKLOOTCONTAINER) and 0xFFFFFFFFL)
        }

        if (attributes.containsKey(ItemAttribute.TIER)) {
            propWriteStream.writeU8(ATTR_TIER)
            propWriteStream.writeU8(getTier())
        }

        if (attributes.containsKey(ItemAttribute.AMOUNT)) {
            propWriteStream.writeU8(ATTR_AMOUNT)
            propWriteStream.writeU16(intAttribute(ItemAttribute.AMOUNT).toInt())
        }

        if (attributes.containsKey(ItemAttribute.STORE_INBOX_CATEGORY)) {
            propWriteStream.writeU8(ATTR_STORE_INBOX_CATEGORY)
            propWriteStream.writeString(stringAttribute(ItemAttribute.STORE_INBOX_CATEGORY))
        }

        if (attributes.containsKey(ItemAttribute.OWNER)) {
            propWriteStream.writeU8(ATTR_OWNER)
            propWriteStream.writeU32(intAttribute(ItemAttribute.OWNER) and 0xFFFFFFFFL)
        }

        if (hasCustomAttribute()) {
            propWriteStream.writeU8(ATTR_CUSTOM)
            propWriteStream.writeU64(customAttributes.size.toLong())
            for ((attributeKey, customAttribute) in customAttributes) {
                propWriteStream.writeString(attributeKey)
                customAttribute.serialize(propWriteStream)
            }
        }

        if (attributes.containsKey(ItemAttribute.OBTAINCONTAINER)) {
            propWriteStream.writeU8(ATTR_OBTAINCONTAINER)
            propWriteStream.writeU32(intAttribute(ItemAttribute.OBTAINCONTAINER) and 0xFFFFFFFFL)
        }
    }

    /** `Item::readAttr` (src/items/item.cpp:912-1349). */
    open fun readAttr(attr: Int, propStream: PropStream): AttrReadValue {
        when (attr) {
            ATTR_STORE -> {
                val timeStamp = propStream.readI64() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.STORE, timeStamp)
            }

            ATTR_COUNT, ATTR_RUNE_CHARGES -> {
                val charges = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
                setSubType(charges)
            }

            ATTR_ACTION_ID -> {
                val actionId = propStream.readU16() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.ACTIONID, actionId.toLong())
            }

            ATTR_UNIQUE_ID -> {
                val uniqueId = propStream.readU16() ?: return AttrReadValue.ATTR_READ_ERROR
                setUniqueId(uniqueId.toLong())
            }

            ATTR_TEXT -> {
                val text = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.TEXT, text)
            }

            ATTR_WRITTENDATE -> {
                val writtenDate = propStream.readU64() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.DATE, writtenDate)
            }

            ATTR_WRITTENBY -> {
                val writer = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.WRITER, writer)
            }

            ATTR_DESC -> {
                val text = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.DESCRIPTION, text)
            }

            ATTR_CHARGES -> {
                val charges = propStream.readU16() ?: return AttrReadValue.ATTR_READ_ERROR
                setSubType(charges)
            }

            ATTR_DURATION -> {
                val duration = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setDuration(duration)
            }

            ATTR_DECAYING_STATE -> {
                val state = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
                if (state != ItemDecayState.DECAYING_FALSE.value) {
                    setDecaying(ItemDecayState.DECAYING_PENDING)
                }
            }

            ATTR_NAME -> {
                val name = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.NAME, name)
            }

            ATTR_ARTICLE -> {
                val article = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.ARTICLE, article)
            }

            ATTR_PLURALNAME -> {
                val pluralName = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.PLURALNAME, pluralName)
            }

            ATTR_WEIGHT -> {
                val weight = propStream.readU32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.WEIGHT, weight)
            }

            ATTR_ATTACK -> {
                val attack = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.ATTACK, attack.toLong())
            }

            ATTR_DEFENSE -> {
                val defense = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.DEFENSE, defense.toLong())
            }

            ATTR_EXTRADEFENSE -> {
                val extraDefense = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.EXTRADEFENSE, extraDefense.toLong())
            }

            ATTR_MANTRA -> {
                val mantra = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.MANTRA, mantra.toLong())
            }

            ATTR_IMBUEMENT_SLOT -> {
                val imbuementSlot = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.IMBUEMENT_SLOT, imbuementSlot.toLong())
            }

            ATTR_OPENCONTAINER -> {
                val openContainer = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.OPENCONTAINER, openContainer.toLong())
            }

            ATTR_ARMOR -> {
                val armor = propStream.readI32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.ARMOR, armor.toLong())
            }

            ATTR_HITCHANCE -> {
                val hitChance = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.HITCHANCE, hitChance.toByte().toLong())
            }

            ATTR_SHOOTRANGE -> {
                val shootRange = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.SHOOTRANGE, shootRange.toLong())
            }

            ATTR_SPECIAL -> {
                val special = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.SPECIAL, special)
            }

            ATTR_QUICKLOOTCONTAINER -> {
                val flags = propStream.readU32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.QUICKLOOTCONTAINER, flags)
            }

            // These are handled through derived classes; when they reach the
            // base item the items.xml changed since the map was saved, so the
            // payload is skipped (src/items/item.cpp:1184-1210).
            ATTR_DEPOT_ID -> {
                if (!propStream.skip(2)) return AttrReadValue.ATTR_READ_ERROR
            }

            ATTR_HOUSEDOORID -> {
                if (!propStream.skip(1)) return AttrReadValue.ATTR_READ_ERROR
            }

            ATTR_SLEEPERGUID -> {
                if (!propStream.skip(4)) return AttrReadValue.ATTR_READ_ERROR
            }

            ATTR_SLEEPSTART -> {
                if (!propStream.skip(4)) return AttrReadValue.ATTR_READ_ERROR
            }

            ATTR_TELE_DEST -> {
                if (!propStream.skip(5)) return AttrReadValue.ATTR_READ_ERROR
            }

            ATTR_TIER -> {
                val tier = propStream.readU8() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.TIER, tier.toLong())
            }

            ATTR_AMOUNT -> {
                val amount = propStream.readU16() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.AMOUNT, amount.toLong())
            }

            ATTR_STORE_INBOX_CATEGORY -> {
                val category = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.STORE_INBOX_CATEGORY, category)
            }

            ATTR_OWNER -> {
                val ownerId = propStream.readU32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.OWNER, ownerId)
            }

            ATTR_OBTAINCONTAINER -> {
                val flags = propStream.readU32() ?: return AttrReadValue.ATTR_READ_ERROR
                setAttribute(ItemAttribute.OBTAINCONTAINER, flags)
            }

            // Deprecated, all items that still exist with this attribute will
            // work normally, but new items will be created with the new system,
            // using ATTR_CUSTOM (src/items/item.cpp:1240-1268).
            ATTR_CUSTOM_ATTRIBUTES -> {
                val size = propStream.readU64() ?: return AttrReadValue.ATTR_READ_ERROR
                for (index in 0 until size) {
                    val key = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR

                    val customAttribute = CustomAttribute()
                    if (!customAttribute.unserialize(propStream)) {
                        return AttrReadValue.ATTR_READ_ERROR
                    }
                    addCustomAttribute(key, customAttribute)
                    removeAttribute(ItemAttribute.CUSTOM)

                    // Migrate wrapable items to the new store attribute.
                    if (getCustomAttribute("unWrapId") != null && intAttribute(ItemAttribute.STORE) == 0L) {
                        setAttribute(ItemAttribute.STORE, System.currentTimeMillis() / 1000)
                    }
                }
            }

            ATTR_CUSTOM -> {
                val size = propStream.readU64() ?: return AttrReadValue.ATTR_READ_ERROR
                for (index in 0 until size) {
                    val key = propStream.readString() ?: return AttrReadValue.ATTR_READ_ERROR

                    val customAttribute = CustomAttribute()
                    if (!customAttribute.unserialize(propStream)) {
                        return AttrReadValue.ATTR_READ_ERROR
                    }
                    addCustomAttribute(key, customAttribute)
                }
            }

            ATTR_CONTAINER_ITEMS -> return AttrReadValue.ATTR_READ_ERROR
            else -> return AttrReadValue.ATTR_READ_ERROR
        }

        return AttrReadValue.ATTR_READ_CONTINUE
    }

    /** `Item::unserializeAttr` (src/items/item.cpp:1351-1366). */
    fun unserializeAttr(propStream: PropStream): Boolean {
        while (true) {
            val attrType = propStream.readU8() ?: break
            if (attrType == ATTR_NONE) {
                break
            }
            when (readAttr(attrType, propStream)) {
                AttrReadValue.ATTR_READ_ERROR -> return false
                AttrReadValue.ATTR_READ_END -> return true
                AttrReadValue.ATTR_READ_CONTINUE -> {}
            }
        }
        return true
    }

    private fun intAttribute(attribute: ItemAttribute): Long =
        (attributes[attribute] as? Number)?.toLong() ?: 0L

    private fun stringAttribute(attribute: ItemAttribute): String =
        attributes[attribute] as? String ?: ""

    private fun otsysTime(): Long = System.currentTimeMillis()

    companion object {
        /** `IMMOVABLE_ACTION_ID` (src/utils/const.hpp:73). */
        const val IMMOVABLE_ACTION_ID = 100

        /**
         * `Item::CreateItem` (src/items/item.cpp:393-472), including the derived
         * item types. [imbuements] is the `g_imbuements()` composition seam:
         * when supplied, the created item validates its packed imbuement ids
         * through the registry exactly like `Item::getImbuementInfo`
         * (src/items/item.cpp:472-483); when omitted the item keeps the
         * custom-attribute-presence fallback until a hub assigns a registry.
         * The composition root (Game/GameHandler map, persistence and loot
         * hubs) must pass the loaded registry here.
         */
        fun create(
            id: Int,
            count: Int = 1,
            registry: ItemTypeRegistry? = null,
            imbuements: Imbuements? = null,
        ): Item {
            val item = createLike(registry?.get(id), id, count)
            if (imbuements != null) {
                item.imbuementRegistry = imbuements
            }
            return item
        }

        private fun createLike(itemType: ItemType?, id: Int, count: Int): Item = when {
            itemType?.isContainer == true -> Container(id, capacity = itemType.capacity, type = itemType)
            itemType?.isTrashHolder == true -> TrashHolder(id, type = itemType)
            itemType?.isDoor == true -> Door(id, type = itemType)
            itemType?.isBed == true -> BedItem(id, type = itemType)
            else -> Item(id, count = count, type = itemType)
        }

        /** `Item::getNameDescription` (src/items/item.cpp:3357). */
        fun getNameDescription(
            it: ItemType,
            item: Item? = null,
            subType: Int = -1,
            addArticle: Boolean = true
        ): String {
            var effectiveSubType = subType
            if (item != null) {
                effectiveSubType = item.getSubType()
            }

            val name = item?.getName() ?: it.name
            if (name.isEmpty()) {
                return "an item of type ${it.id}"
            }

            val description = StringBuilder()
            if (it.isStackable && effectiveSubType > 1) {
                if (it.showCount) {
                    description.append(effectiveSubType).append(' ')
                }
                description.append(item?.getPluralName() ?: it.getPluralName())
            } else {
                if (addArticle) {
                    val article = item?.getArticle() ?: it.article
                    if (article.isNotEmpty()) {
                        description.append(article).append(' ')
                    }
                }
                description.append(name)
            }
            return description.toString()
        }

        /** `Item::getWeightDescription` (src/items/item.cpp:3393), [weight] in hundredths of an ounce. */
        fun getWeightDescription(it: ItemType, weight: Int, count: Int = 1): String {
            val description = StringBuilder()
            if (it.isStackable && count > 1 && it.showCount) {
                description.append("They weigh ")
            } else {
                description.append("It weighs ")
            }

            appendWeight(description, weight)
            description.append(" oz.")
            return description.toString()
        }

        /** `Item::parseShowDurationSpeed` (src/items/item.cpp:2441); the C++ `begin` out-parameter is threaded through the pair. */
        fun parseShowDurationSpeed(speed: Int, begin: Boolean): Pair<String, Boolean> {
            val prefix = if (begin) " (speed " else ", speed "
            val signed = if (speed >= 0) "+$speed" else speed.toString()
            return prefix + signed to false
        }

        /** `Item::parseShowDuration` (src/items/item.cpp:2454). */
        fun parseShowDuration(item: Item?): String {
            if (item == null) {
                return ""
            }

            val description = StringBuilder()
            val duration = item.getDuration() / 1000
            if (item.hasAttribute(ItemAttribute.DURATION) && duration > 0) {
                description.append(" that will expire in ")
                when {
                    duration >= 86400 -> {
                        val days = duration / 86400
                        val hours = (duration % 86400) / 3600
                        description.append(days).append(" day").append(if (days != 1) "s" else "")
                        if (hours > 0) {
                            description.append(" and ").append(hours).append(" hour")
                                .append(if (hours != 1) "s" else "")
                        }
                    }

                    duration >= 3600 -> {
                        val hours = duration / 3600
                        val minutes = (duration % 3600) / 60
                        description.append(hours).append(" hour").append(if (hours != 1) "s" else "")
                        if (minutes > 0) {
                            description.append(" and ").append(minutes).append(" minute")
                                .append(if (minutes != 1) "s" else "")
                        }
                    }

                    duration >= 60 -> {
                        val minutes = duration / 60
                        val seconds = duration % 60
                        description.append(minutes).append(" minute").append(if (minutes != 1) "s" else "")
                        if (seconds > 0) {
                            description.append(" and ").append(seconds).append(" second")
                                .append(if (seconds != 1) "s" else "")
                        }
                    }

                    else -> description.append(duration).append(" second").append(if (duration != 1) "s" else "")
                }
            } else {
                description.append(" that is brand-new")
            }

            return description.toString()
        }

        private fun appendWeight(description: StringBuilder, weight: Int) {
            when {
                weight < 10 -> description.append("0.0").append(weight)
                weight < 100 -> description.append("0.").append(weight)
                else -> {
                    val weightString = weight.toString()
                    description.append(weightString, 0, weightString.length - 2)
                    description.append('.')
                    description.append(weightString, weightString.length - 2, weightString.length)
                }
            }
        }

        /** `Item::countByType` (src/items/item.hpp:614-624). */
        fun countByType(item: Item?, subType: Int): Int {
            if (item == null) {
                return 0
            }
            if (subType == -1 || subType == item.getSubType()) {
                return item.getItemCount()
            }
            return 0
        }
    }
}

/** `fmt::format("{:+}", value)` for the int32 augment value. */
private fun signedPercent(value: Int): String = if (value >= 0) "+$value" else "$value"
