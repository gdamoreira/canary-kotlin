package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import kotlin.math.ceil
import kotlin.math.pow
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.message.writeTibiaVarCount

/**
 * The `MessageClasses` byte (`src/utils/utils_definitions.hpp:370-408`).
 *
 * Unnamed gaps in the C++ enum (values 1-12, 16, 36 and 45-47) have no Kotlin
 * counterpart; [fromValue] maps any unknown byte to [MESSAGE_NONE], the value
 * `ProtocolGame::sendTextMessage` rejects (protocolgame.cpp:6071-6075).
 */
enum class MessageClass(val value: Int) {
    MESSAGE_NONE(0),
    MESSAGE_GAMEMASTER_CONSOLE(13),
    MESSAGE_LOGIN(17),
    MESSAGE_ADMINISTRATOR(18),
    MESSAGE_EVENT_ADVANCE(19),
    MESSAGE_GAME_HIGHLIGHT(20),
    MESSAGE_FAILURE(21),
    MESSAGE_LOOK(22),
    MESSAGE_DAMAGE_DEALT(23),
    MESSAGE_DAMAGE_RECEIVED(24),
    MESSAGE_HEALED(25),
    MESSAGE_EXPERIENCE(26),
    MESSAGE_DAMAGE_OTHERS(27),
    MESSAGE_HEALED_OTHERS(28),
    MESSAGE_EXPERIENCE_OTHERS(29),
    MESSAGE_STATUS(30),
    MESSAGE_LOOT(31),
    MESSAGE_TRADE(32),
    MESSAGE_GUILD(33),
    MESSAGE_PARTY_MANAGEMENT(34),
    MESSAGE_PARTY(35),
    MESSAGE_REPORT(38),
    MESSAGE_HOTKEY_PRESSED(39),
    MESSAGE_TUTORIAL_HINT(40),
    MESSAGE_THANK_YOU(41),
    MESSAGE_MARKET(42),
    MESSAGE_MANA(43),
    MESSAGE_BEYOND_LAST(44),
    MESSAGE_ATTENTION(48),
    MESSAGE_BOOSTED_CREATURE(49),
    MESSAGE_OFFLINE_TRAINING(50),
    MESSAGE_TRANSACTION(51),
    MESSAGE_POTION(52),
    ;

    companion object {
        fun fromValue(value: Int): MessageClass =
            entries.firstOrNull { it.value == value } ?: MESSAGE_NONE
    }
}

/**
 * `TextMessage::primary`/`secondary` (`src/server/network/protocol/protocolgame.hpp:95-99`);
 * the color defaults to `TEXTCOLOR_NONE` like the C++ `TextColor_t` member.
 */
data class MessageValue(
    val value: Long = 0,
    val color: Int = GameWindowWriter.TEXTCOLOR_NONE,
)

/**
 * The emitted shape of `TextMessage` (`protocolgame.hpp:87-100`): [position] is
 * only written for the damage/healing/experience classes and [channelId] only
 * for the guild/party classes (protocolgame.cpp:6145-6189).
 */
data class TextMessageFrame(
    val type: MessageClass,
    val text: String,
    val position: Position = Position(0, 0, 0),
    val primary: MessageValue = MessageValue(),
    val secondary: MessageValue = MessageValue(),
    val channelId: Int = 0,
)

/**
 * `CreatureType_t` (`src/creatures/creatures_definitions.hpp:489-495`).
 */
enum class CreatureType(val value: Int) {
    PLAYER(0),
    MONSTER(1),
    NPC(2),
    SUMMON_PLAYER(3),
    SUMMON_OTHERS(4),
    HIDDEN(5),
}

/** One `Creature::getIcons()` entry as `addCreatureIcon` serializes it (protocolgame.cpp:4600-4606). */
data class CreatureIcon(
    val serialize: Int,
    val category: Int,
    val count: Int,
)

/**
 * Resolved `Creature`/session state needed by `ProtocolGame::AddCreature`,
 * `ProtocolGame::sendAddCreature` and `ProtocolGame::AddOutfit`
 * (protocolgame.cpp:8606-8638, 9662-9780, 10052-10076).
 *
 * `known`/[removedKnown] are the outputs of
 * `ProtocolGame::checkCreatureAsKnown` (the session's 1300-entry known-set
 * mutation, protocolgame.cpp:2334); [accessPlayer] is `Player::isAccessPlayer()`;
 * [skull]/[partyShield]/[guildEmblem]/[vocationClientId] are the
 * `Player::getSkullClient` / `getPartyShield` / `getGuildEmblem` /
 * `Vocation::getClientId` results; [creatureMark] is
 * `ExpertPvp::getSituationCreatureMark` for the Current profile.
 */
data class CreatureAppearanceFrame(
    val id: Long,
    val name: String,
    val health: Int,
    val maxHealth: Int,
    val direction: Direction,
    val outfit: Outfit = Outfit(),
    val light: LightInfo = LightInfo(),
    val stepSpeed: Int = 0,
    val creatureType: CreatureType = CreatureType.PLAYER,
    val known: Boolean = false,
    val removedKnown: Long = 0,
    val healthHidden: Boolean = false,
    val masterId: Long? = null,
    val masterIsPlayer: Boolean = false,
    val vocationClientId: Int = 0,
    val skull: Int = 0,
    val partyShield: Int = 0,
    val guildEmblem: Int = 0,
    val speechBubble: Int = 0,
    val creatureMark: Int = GameWindowWriter.CREATURE_MARK_UNMARKED,
    val canWalkthrough: Boolean = false,
    val accessPlayer: Boolean = false,
    val isInvisibleOrGhost: Boolean = false,
    val icons: List<CreatureIcon> = emptyList(),
)

/** `Player` values written by `ProtocolGame::AddPlayerStats` (protocolgame.cpp:9782-9877). */
data class PlayerStatsFrame(
    val health: Int,
    val maxHealth: Int,
    val freeCapacity: Int,
    val experience: Long,
    val level: Int,
    val levelPercent: Double,
    val baseXpGain: Int,
    val displayGrindingXpBoost: Int,
    val displayXpBoostPercent: Int,
    val staminaXpBoost: Int,
    val mana: Int,
    val maxMana: Int,
    val soul: Int,
    val staminaMinutes: Int,
    val baseSpeed: Int,
    val regenerationTicks: Long = 0,
    val offlineTrainingTime: Long = 0,
    val xpBoostTime: Int = 0,
    val manaShield: Int = 0,
    val maxManaShield: Int = 0,
    val hasInfiniteCapacity: Boolean = false,
)

/** One `skills_t` slot in `Player::getSkillLevel`/`getBaseSkill`/`getLoyaltySkill`/`getSkillPercent` order. */
data class SkillEntry(
    val level: Int,
    val base: Int,
    val loyalty: Int,
    val percent: Double,
)

/**
 * The weapon block of `AddPlayerSkills` (protocolgame.cpp:9939-10015).
 *
 * - [Fist] is the `weapon == nullptr` branch: `attackTotal(flatBonus, 7, fistSkill)`.
 * - [Wand] is `WEAPON_WAND`: `ItemType::maxHitChance`, `getCipbiaElement(combatType)`.
 * - [Weapon] covers `WEAPON_DISTANCE`/`WEAPON_AMMO`/`WEAPON_MISSILE` and melee:
 *   [attackTotal], then either the element conversion ratio or the imbuement
 *   damage (protocolgame.cpp:9947-10004); [convertedElement] is 0 when Canary
 *   falls through to `handleImbuementDamage` without an elemental combat.
 */
sealed interface WeaponAttack {
    data class Fist(val attackTotal: Int) : WeaponAttack
    data class Wand(val maxHitChance: Int, val element: Int) : WeaponAttack
    data class Weapon(
        val attackTotal: Int,
        val convertedDamage: Double,
        val convertedElement: Int,
    ) : WeaponAttack
}

/**
 * One `calculateAbsorbValues` element entry (protocolgame.cpp:310-375):
 * [element] is `getCipbiaElement(indexToCombatType(i))` and [modifier] the
 * `(10000 - damageModifiers[i]) / 10000.` client value.
 */
data class AbsorbEntry(
    val element: Int,
    val modifier: Double,
)

/**
 * The `AddPlayerSkills` payload after the equipment and forge values are
 * resolved from the live player (protocolgame.cpp:9914-10050). Every field is
 * named after the exact `Player` getter Canary reads for that wire value.
 */
data class PlayerSkillsFrame(
    val magicLevel: Int,
    val baseMagicLevel: Int,
    val loyaltyMagicLevel: Int,
    val magicLevelPercent: Double,
    val skills: List<SkillEntry>,
    val capacity: Int,
    val baseCapacity: Int,
    val flatBonus: Int,
    val weaponAttack: WeaponAttack,
    val lifeLeechSkill: Int,
    val manaLeechSkill: Int,
    val baseCriticalChance: Double,
    val baseCriticalDamage: Double,
    val criticalHitChanceSkill: Int,
    val criticalHitDamageSkill: Int,
    val onslaught: Double,
    val defense: Int,
    val armor: Int,
    val partyMantra: Int,
    val mitigation: Double,
    val dodge: Double,
    val reflectFlat: Int,
    val absorbs: List<AbsorbEntry>,
    val momentum: Double,
    val transcendence: Double,
    val amplification: Double,
)

/**
 * `Item::getInventoryItemsId` entry (`ItemsTierCountList`, player.hpp:1639).
 * The C++ container is an `unordered_map`, so iteration order is unspecified;
 * this serializer preserves the caller-provided order.
 */
data class InventoryIdEntry(
    val itemId: Int,
    val tier: Int,
    val amount: Long,
)

/** `ContainerSpecial_t` (`src/enums/container_type.hpp:16-25`). */
enum class ContainerSpecial(val value: Int) {
    NONE(0),
    LOOT_CONTAINER(1),
    CONTENT_COUNTER(2),
    LOOT_HIGHLIGHT(4),
    OBTAIN(8),
    MANAGER(9),
    QUIVER_LOOT(11),
}

/**
 * `ItemType` fields `ProtocolGame::AddItem(NetworkMessage&, item)`
 * (protocolgame.cpp:776-916) needs that the Kotlin [ItemType] does not carry
 * yet; [from] reads the fields the port does know about.
 */
data class ItemWireInfo(
    val isStackable: Boolean = false,
    val isSplash: Boolean = false,
    val isFluidContainer: Boolean = false,
    val isContainer: Boolean = false,
    val containerSpecial: ContainerSpecial = ContainerSpecial.NONE,
    val lootFlags: Int = 0,
    val obtainFlags: Int = 0,
    val ammoTotal: Int = 0,
    val upgradeClassification: Int = 0,
    val expire: Boolean = false,
    val decayTime: Int = 0,
    val wearOut: Boolean = false,
    val charges: Int = 0,
    val isWrapKit: Boolean = false,
) {
    companion object {
        fun from(type: ItemType?): ItemWireInfo = ItemWireInfo(
            isStackable = type?.isStackable ?: false,
            isContainer = type?.isContainer ?: false,
            upgradeClassification = type?.upgradeClassification ?: 0,
        )
    }
}

/**
 * `ProtocolGame::sendContainer(uint8_t, container, hasParent, firstIndex)`
 * (protocolgame.cpp:6336-6467) state the Kotlin [Container] does not own.
 * `storeInboxCategories`/`storeInboxCategory` mirror
 * `Container::getStoreInboxValidCategories` and the `STORE_INBOX_CATEGORY`
 * attribute; `storeInboxFilteredItems` mirrors `getStoreInboxFilteredItems`.
 */
data class ContainerWindowFrame(
    val cid: Int,
    val name: String,
    val hasParent: Boolean = false,
    val firstIndex: Int = 0,
    val isDepotSearchAvailable: Boolean = false,
    val isInsideDepot: Boolean = false,
    val isUnlocked: Boolean = true,
    val hasPagination: Boolean = false,
    val isStoreInbox: Boolean = false,
    val storeInboxFilteredItems: List<Item> = emptyList(),
    val storeInboxCategories: List<ContainerCategory> = emptyList(),
    val storeInboxCategory: ContainerCategory? = null,
    val isMovable: Boolean = true,
    val hasHoldingPlayer: Boolean = false,
)

/**
 * `ContainerCategory_t` (`src/items/items_definitions.hpp:302-424`). The store
 * inbox sends the enum name run through `toStartCaseWithSpace`
 * (`src/utils/tools.cpp:412-425`), exposed as [displayName].
 */
enum class ContainerCategory {
    All,
    Ammunition,
    AmuletsAndNecklaces,
    Animals,
    Annelids,
    Arachnids,
    Armors,
    ArtificialTiles,
    AstralShapers,
    AttackRunes,
    AxeWeapons,
    Bats,
    Bears,
    Birds,
    BlessingCharms,
    Blobs,
    Books,
    Boots,
    Bushes,
    Cactuses,
    Canines,
    Casks,
    Closets,
    ClothingAccessories,
    ClubWeapons,
    Coffins,
    Constructions,
    Containers,
    ContestPrizes,
    CreatureProducts,
    Decoration,
    Demons,
    DistanceWeapons,
    DocumentsAndPapers,
    DollsAndBears,
    Doors,
    Dragons,
    Dreamhaunters,
    Dropdowns,
    EnchantedItems,
    EventCreatures,
    ExerciseWeapons,
    FansiteItems,
    Ferns,
    Fields,
    Flags,
    FloorDecorations,
    FloraAndMinerals,
    Flowers,
    FluidContainers,
    Food,
    Furniture,
    GameTokens,
    Ghosts,
    Glires,
    Grass,
    HealingRunes,
    Helmets,
    HiveBorn,
    Illumination,
    Keys,
    KitchenTools,
    Ladders,
    Legs,
    LightSources,
    Liquids,
    MachinesObjects,
    Machines,
    MagicalItems,
    Metals,
    Mollusks,
    Mushrooms,
    MusicalInstruments,
    NaturalProducts,
    NaturalTiles,
    OtherItems,
    Outlaws,
    PaintingEquipment,
    PartyItems,
    Pillars,
    PlantsAndHerbs,
    Plants,
    Portals,
    QuestItems,
    QuestObjects,
    Quivers,
    Refuse,
    Remains,
    Rings,
    Rocks,
    Rods,
    Rubbish,
    Shields,
    ShrinesAndAltars,
    Signs,
    Skeletons,
    Spellbooks,
    Stairs,
    Statues,
    SupportRunes,
    SwordWeapons,
    Tables,
    TamingItems,
    Teleporters,
    ToolsObjects,
    Tools,
    TortureInstruments,
    TournamentRewards,
    TrainingWeapons,
    Transportation,
    Traps,
    Trees,
    Trophies,
    UndeadHumanoids,
    Ungulates,
    Utilities,
    Valuables,
    WallHangings,
    Walls,
    Wands,
    Windows,
    ;

    val value: Int get() = ordinal

    /** `toStartCaseWithSpace(magic_enum::enum_name(value))` (`src/utils/tools.cpp:412`). */
    val displayName: String get() = startCaseWithSpace(name)
}

private fun startCaseWithSpace(value: String): String {
    val result = StringBuilder()
    for ((index, char) in value.withIndex()) {
        if (index == 0 || char.isUpperCase()) {
            if (index > 0) {
                result.append(' ')
            }
            result.append(char.uppercaseChar())
        } else {
            result.append(char.lowercaseChar())
        }
    }
    return result.toString()
}

/**
 * Outbound `ProtocolGame` core frame serialization for the Canary
 * `ProtocolProfileId::Current` profile (`protocol_new` / 15.x: `oldProtocol ==
 * false`, no cipsoft860/1100 profile), ported from
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendStats` / `AddPlayerStats`   (0xA0, protocolgame.cpp:5813, 9782-9877)
 *  - `ProtocolGame::sendSkills` / `AddPlayerSkills` (0xA1, protocolgame.cpp:8161, 9879-10050)
 *  - `ProtocolGame::sendInventoryItem`              (0x78 / 0x79, protocolgame.cpp:8876-8891)
 *  - `ProtocolGame::AddItem(NetworkMessage&, Item)` (protocolgame.cpp:776-916)
 *  - `ProtocolGame::sendInventoryIds`               (0xF5, protocolgame.cpp:8893-8932)
 *  - `ProtocolGame::sendContainer`                  (0x6E, protocolgame.cpp:6336-6467)
 *  - `ProtocolGame::sendTextMessage`                (0xB4, protocolgame.cpp:6070-6189)
 *  - `ProtocolGame::sendAddCreature` + `AddCreature`(0x6A + self 0x17/0x0A, protocolgame.cpp:8606-8668, 9662-9780)
 *  - `ProtocolGame::RemoveTileThing`                (0x6C, protocolgame.cpp:10390-10398)
 *  - `ProtocolGame::AddOutfit`                      (protocolgame.cpp:10052-10076)
 *
 * Every multi-byte field is Little-Endian (`NetworkMessage::add<T>`), strings
 * are `uint16` length + UTF-8 (`NetworkMessage::addString`) and doubles are a
 * precision byte plus `(value * 10^precision) + INT32_MAX` as `uint32_t`
 * (`NetworkMessage::addDouble`, src/server/network/message/networkmessage.cpp:174).
 *
 * The old-protocol, Tibia 11.00 and cipsoft860 layouts differ and are not
 * emitted here (see PARITY GAPS in the wave report). Values Canary computes
 * from live player/engine state are passed in by the caller through the frame
 * types above, matching how `BestiaryWindowSerializer`/`WheelWindowSerializer`
 * structure their inputs.
 */
object GameWindowWriter {

    const val PACKET_STATS: Int = 0xA0
    const val PACKET_SKILLS: Int = 0xA1
    const val PACKET_INVENTORY_ITEM: Int = 0x78
    const val PACKET_INVENTORY_ITEM_REMOVE: Int = 0x79
    const val PACKET_INVENTORY_IDS: Int = 0xF5
    const val PACKET_CONTAINER: Int = 0x6E
    const val PACKET_TEXT_MESSAGE: Int = 0xB4
    const val PACKET_ADD_CREATURE: Int = 0x6A
    const val PACKET_REMOVE_TILE_THING: Int = 0x6C

    /** `sendAddCreature` self branch `version >= 980` opcode (protocolgame.cpp:8641). */
    const val PACKET_SELF_APPEAR: Int = 0x17

    /** `sendAddCreature` self branch `version < 980` opcode (protocolgame.cpp:8641). */
    const val PACKET_SELF_APPEAR_LEGACY: Int = 0x0A

    /** `SERVER_BEAT` (`src/game/game.hpp:64`). */
    const val SERVER_BEAT: Int = 0x32

    /** `TEXTCOLOR_NONE` (`src/utils/utils_definitions.hpp:460`). */
    const val TEXTCOLOR_NONE: Int = 255

    /** `CREATURE_MARK_UNMARKED = TEXTCOLOR_NONE` (`src/utils/utils_definitions.hpp:467`). */
    const val CREATURE_MARK_UNMARKED: Int = 255

    private const val ITEM_BROWSEFIELD = 470
    private const val ITEM_BAG = 2853
    private const val ITEM_STORE_INBOX = 23396

    private const val CREATURETYPE_MONSTER = 1
    private const val CREATURETYPE_SUMMON_PLAYER = 3
    private const val CREATURETYPE_HIDDEN = 5

    private const val CIPBIA_ELEMENTAL_PHYSICAL = 0

    private const val MAX_CLIENT_ICONS = 3
    private const val MAX_STACK_COUNT = 0xFF
    private const val MAX_ITEM_AMOUNT_EXCLUSIVE = 0x40000000L

    /** `AddPlayerStats` current-profile layout (protocolgame.cpp:9782-9877). */
    fun writePlayerStats(out: ByteBuf, stats: PlayerStatsFrame) {
        out.writeByte(PACKET_STATS)

        out.writeIntLE(stats.health)
        out.writeIntLE(stats.maxHealth)
        out.writeIntLE(if (stats.hasInfiniteCapacity) 1_000_000 else stats.freeCapacity)
        out.writeLongLE(stats.experience)
        out.writeShortLE(stats.level)

        val levelPercent = minOf((stats.levelPercent * 100).toInt() and 0xFFFF, 10000)
        out.writeShortLE(levelPercent)

        out.writeShortLE(stats.baseXpGain)
        out.writeShortLE(stats.displayGrindingXpBoost)
        out.writeShortLE(stats.displayXpBoostPercent)
        out.writeShortLE(stats.staminaXpBoost)

        out.writeIntLE(stats.mana)
        out.writeIntLE(stats.maxMana)

        out.writeByte(stats.soul)
        out.writeShortLE(stats.staminaMinutes)
        out.writeShortLE(stats.baseSpeed)
        out.writeShortLE((stats.regenerationTicks / 1000).toInt())
        out.writeShortLE((stats.offlineTrainingTime / 60 / 1000).toInt())
        out.writeShortLE(stats.xpBoostTime)
        out.writeByte(1)
        out.writeIntLE(stats.manaShield)
        out.writeIntLE(stats.maxManaShield)
    }

    /** `AddPlayerSkills` current-profile layout (protocolgame.cpp:9879-10050). */
    fun writePlayerSkills(out: ByteBuf, skills: PlayerSkillsFrame) {
        out.writeByte(PACKET_SKILLS)

        out.writeShortLE(skills.magicLevel)
        out.writeShortLE(skills.baseMagicLevel)
        out.writeShortLE(skills.loyaltyMagicLevel)
        out.writeShortLE((skills.magicLevelPercent * 100).toInt())

        for (skill in skills.skills) {
            out.writeShortLE(minOf(skill.level, 0xFFFF))
            out.writeShortLE(skill.base)
            out.writeShortLE(skill.loyalty)
            out.writeShortLE((skill.percent * 100).toInt())
        }

        // 13.10 list marker (protocolgame.cpp:9929-9930).
        out.writeByte(0)

        out.writeIntLE(skills.capacity)
        out.writeIntLE(skills.baseCapacity)
        out.writeShortLE(skills.flatBonus)

        writeWeaponAttack(out, skills.weaponAttack)

        out.writeCanaryDouble(skills.lifeLeechSkill / 10000.0)
        out.writeCanaryDouble(skills.manaLeechSkill / 10000.0)
        out.writeCanaryDouble(
            minOf(skills.baseCriticalChance + skills.criticalHitChanceSkill / 10000.0, 1.0),
        )
        out.writeCanaryDouble(skills.baseCriticalDamage + skills.criticalHitDamageSkill / 10000.0)
        out.writeCanaryDouble(skills.onslaught)

        out.writeShortLE(skills.defense)
        out.writeShortLE(skills.armor)
        out.writeShortLE(skills.partyMantra)
        out.writeCanaryDouble(skills.mitigation / 100.0)
        out.writeCanaryDouble(skills.dodge)
        out.writeShortLE(skills.reflectFlat)

        out.writeByte(skills.absorbs.size)
        for (absorb in skills.absorbs) {
            out.writeByte(absorb.element)
            out.writeCanaryDouble(absorb.modifier)
        }

        out.writeCanaryDouble(skills.momentum)
        out.writeCanaryDouble(skills.transcendence)
        out.writeCanaryDouble(skills.amplification)
    }

    /** `sendInventoryItem` (protocolgame.cpp:8876-8891). */
    fun writeInventoryItem(
        out: ByteBuf,
        slot: Slots_t,
        item: Item?,
        info: ItemWireInfo = ItemWireInfo.from(item?.type),
    ) {
        if (item != null) {
            out.writeByte(PACKET_INVENTORY_ITEM)
            out.writeByte(slot.value)
            writeItem(out, item, info)
        } else {
            out.writeByte(PACKET_INVENTORY_ITEM_REMOVE)
            out.writeByte(slot.value)
        }
    }

    /** `sendInventoryIds` (protocolgame.cpp:8893-8932). Amounts >= 0x40000000 are dropped. */
    fun writeInventoryIds(out: ByteBuf, items: List<InventoryIdEntry>) {
        val encodable = items.filter { it.amount in 0 until MAX_ITEM_AMOUNT_EXCLUSIVE }

        out.writeByte(PACKET_INVENTORY_IDS)
        out.writeShortLE(encodable.size)
        for (entry in encodable) {
            out.writeShortLE(entry.itemId)
            out.writeByte(entry.tier)
            out.writeTibiaVarCount(entry.amount)
        }
    }

    /** `AddItem(NetworkMessage&, const std::shared_ptr<Item>&)` current-profile layout (protocolgame.cpp:776-916). */
    fun writeItem(out: ByteBuf, item: Item, info: ItemWireInfo = ItemWireInfo.from(item.type)) {
        out.writeShortLE(item.id)

        if (info.isStackable) {
            out.writeByte(minOf(MAX_STACK_COUNT, item.count))
        }

        if (info.isSplash || info.isFluidContainer) {
            out.writeByte(item.intAttribute(ItemAttribute.FLUIDTYPE).toInt() and 0xFF)
        }

        if (info.isContainer) {
            out.writeByte(info.containerSpecial.value)
            when (info.containerSpecial) {
                ContainerSpecial.MANAGER -> {
                    out.writeIntLE(info.lootFlags)
                    out.writeIntLE(info.obtainFlags)
                }

                ContainerSpecial.CONTENT_COUNTER -> out.writeIntLE(info.ammoTotal)

                ContainerSpecial.QUIVER_LOOT -> {
                    out.writeIntLE(info.lootFlags)
                    out.writeIntLE(info.ammoTotal)
                    out.writeIntLE(info.obtainFlags)
                }

                else -> Unit
            }
        }

        if (info.upgradeClassification > 0) {
            out.writeByte(item.intAttribute(ItemAttribute.TIER).toInt() and 0xFF)
        }

        if (info.expire) {
            if (item.hasAttribute(ItemAttribute.DURATION)) {
                val durationSeconds = item.getDuration() / 1000
                out.writeIntLE(durationSeconds)
                out.writeByte(if (durationSeconds == info.decayTime) 0x01 else 0x00)
            } else {
                out.writeIntLE(info.decayTime)
                out.writeByte(0x01)
            }
        }

        if (info.wearOut) {
            val subType = item.intAttribute(ItemAttribute.CHARGES).toInt()
            if (subType == 0) {
                out.writeIntLE(info.charges)
                out.writeByte(0x01)
            } else {
                out.writeIntLE(subType)
                out.writeByte(if (subType == info.charges) 0x01 else 0x00)
            }
        }

        if (info.isWrapKit) {
            // `unWrapId` custom attribute is not ported; Canary writes 0 when unset.
            out.writeShortLE(0)
        }
    }

    /** `sendContainer` current-profile layout (protocolgame.cpp:6336-6467). */
    fun writeContainer(out: ByteBuf, container: Container, frame: ContainerWindowFrame) {
        out.writeByte(PACKET_CONTAINER)
        out.writeByte(frame.cid)

        if (container.id == ITEM_BROWSEFIELD) {
            // `AddItem(msg, ITEM_BAG, 1, container->getTier())`: the bag is a
            // container with classification 0, so only the id and container byte
            // reach the wire (protocolgame.cpp:6350-6352).
            out.writeShortLE(ITEM_BAG)
            out.writeByte(0x00)
            out.writeTibiaString("Browse Field")
        } else {
            writeItem(out, container)
            out.writeTibiaString(frame.name)
        }

        out.writeByte(container.capacity and 0xFF)
        out.writeByte(if (frame.hasParent) 0x01 else 0x00)
        out.writeByte(if (frame.isDepotSearchAvailable && frame.isInsideDepot) 0x01 else 0x00)
        out.writeByte(if (frame.isUnlocked) 0x01 else 0x00)
        out.writeByte(if (frame.hasPagination) 0x01 else 0x00)

        val containerSize = if (frame.storeInboxFilteredItems.isNotEmpty()) {
            frame.storeInboxFilteredItems.size
        } else {
            container.items.size
        }
        out.writeShortLE(containerSize)
        out.writeShortLE(frame.firstIndex)

        val maxItemsToSend = if (frame.hasPagination && frame.firstIndex > 0) {
            minOf(container.capacity, maxOf(0, containerSize - frame.firstIndex))
        } else {
            container.capacity
        }

        if (frame.firstIndex >= containerSize) {
            out.writeByte(0x00)
        } else if (container.id == ITEM_STORE_INBOX && frame.storeInboxFilteredItems.isNotEmpty()) {
            out.writeByte(minOf(maxItemsToSend, containerSize))
            for (item in frame.storeInboxFilteredItems) {
                writeItem(out, item)
            }
        } else {
            out.writeByte(minOf(maxItemsToSend, containerSize))

            var index = frame.firstIndex
            var written = 0
            while (written < maxItemsToSend && index < container.items.size) {
                writeItem(out, container.items[index])
                written++
                index++
            }
        }

        if (frame.isStoreInbox) {
            val category = frame.storeInboxCategory
            if (category != null) {
                writeContainerCategory(out, frame.storeInboxCategories, category)
            } else {
                writeContainerCategory(out, frame.storeInboxCategories)
            }
        } else {
            out.writeByte(0x00)
            out.writeByte(0x00)
        }

        out.writeByte(if (frame.isMovable) 0x01 else 0x00)
        out.writeByte(if (frame.hasHoldingPlayer) 0x01 else 0x00)
    }

    /** `sendTextMessage` current-profile layout (protocolgame.cpp:6070-6189). */
    fun writeTextMessage(out: ByteBuf, message: TextMessageFrame) {
        if (message.type == MessageClass.MESSAGE_NONE) {
            return
        }

        out.writeByte(PACKET_TEXT_MESSAGE)
        out.writeByte(message.type.value)

        when (message.type) {
            MessageClass.MESSAGE_DAMAGE_DEALT,
            MessageClass.MESSAGE_DAMAGE_RECEIVED,
            MessageClass.MESSAGE_DAMAGE_OTHERS,
            -> {
                out.writePosition(message.position)
                out.writeIntLE(message.primary.value.toInt())
                out.writeByte(message.primary.color)
                out.writeIntLE(message.secondary.value.toInt())
                out.writeByte(message.secondary.color)
            }

            MessageClass.MESSAGE_HEALED,
            MessageClass.MESSAGE_HEALED_OTHERS,
            -> {
                out.writePosition(message.position)
                out.writeIntLE(message.primary.value.toInt())
                out.writeByte(message.primary.color)
            }

            MessageClass.MESSAGE_EXPERIENCE,
            MessageClass.MESSAGE_EXPERIENCE_OTHERS,
            -> {
                out.writePosition(message.position)
                out.writeLongLE(message.primary.value)
                out.writeByte(message.primary.color)
            }

            MessageClass.MESSAGE_GUILD,
            MessageClass.MESSAGE_PARTY_MANAGEMENT,
            MessageClass.MESSAGE_PARTY,
            -> out.writeShortLE(message.channelId)

            else -> Unit
        }

        out.writeTibiaString(message.text)
    }

    /**
     * `sendAddCreature` for the `creature != player` branch
     * (protocolgame.cpp:8606-8638) followed by `AddCreature`
     * (protocolgame.cpp:9662-9780). The caller owns the `canSee(pos)` gate;
     * `stackpos >= 10` is suppressed here like Canary does.
     */
    fun writeAddCreature(
        out: ByteBuf,
        pos: Position,
        stackpos: Int,
        appearance: CreatureAppearanceFrame,
    ) {
        if (stackpos >= 10) {
            return
        }

        out.writeByte(PACKET_ADD_CREATURE)
        out.writePosition(pos)
        out.writeByte(stackpos)
        writeCreature(out, appearance)
    }

    /** `sendAddCreature` self branch opcode (protocolgame.cpp:8641). */
    fun selfAppearOpcode(clientVersion: Int): Int =
        if (clientVersion >= 980) PACKET_SELF_APPEAR else PACKET_SELF_APPEAR_LEGACY

    /**
     * `sendAddCreature` for the `creature == player` branch
     * (protocolgame.cpp:8639-8668): the self appear opcode, the player id, the
     * server beat and the `version >= 1054` expert-PvP controls byte.
     *
     * PARITY GAPS: the `version >= 981` speed-formula doubles, the `>= 1058`
     * expert-mode byte, the `>= 1080` store images URL / coin-packet pair and
     * the current-profile exiva-button byte are not modeled (the port's login
     * tail does not resolve the `g_configManager()`/world-type values behind
     * them), so this frame carries only the prefix fields above exactly like
     * the pre-existing login frame it replaces.
     */
    fun writeSelfAddCreature(
        out: ByteBuf,
        playerId: Long,
        clientVersion: Int = CLIENT_VERSION,
        expertPvpControlsEnabled: Boolean = false,
    ) {
        out.writeByte(selfAppearOpcode(clientVersion))
        out.writeIntLE(playerId.toInt())
        out.writeShortLE(SERVER_BEAT)

        if (clientVersion >= 1054) {
            out.writeByte(if (expertPvpControlsEnabled) 0x01 else 0x00)
        }
    }

    /** `RemoveTileThing` (protocolgame.cpp:10390-10398). */
    fun writeRemoveTileThing(out: ByteBuf, pos: Position, stackpos: Int) {
        if (stackpos >= 10) {
            return
        }

        out.writeByte(PACKET_REMOVE_TILE_THING)
        out.writePosition(pos)
        out.writeByte(stackpos)
    }

    /** `AddOutfit` current-profile layout (protocolgame.cpp:10052-10076). */
    fun writeOutfit(out: ByteBuf, outfit: Outfit, addMount: Boolean = true) {
        out.writeShortLE(outfit.lookType)
        if (outfit.lookType != 0) {
            out.writeByte(outfit.lookHead)
            out.writeByte(outfit.lookBody)
            out.writeByte(outfit.lookLegs)
            out.writeByte(outfit.lookFeet)
            out.writeByte(outfit.lookAddons)
        } else {
            out.writeShortLE(outfit.lookTypeEx)
        }

        if (addMount) {
            out.writeShortLE(outfit.lookMount)
            if (outfit.lookMount != 0) {
                out.writeByte(outfit.lookMountHead)
                out.writeByte(outfit.lookMountBody)
                out.writeByte(outfit.lookMountLegs)
                out.writeByte(outfit.lookMountFeet)
            }
        }
    }

    private fun writeWeaponAttack(out: ByteBuf, attack: WeaponAttack) {
        when (attack) {
            is WeaponAttack.Wand -> {
                out.writeShortLE(attack.maxHitChance)
                out.writeByte(attack.element)
                out.writeCanaryDouble(0.0)
                out.writeByte(0x00)
            }

            is WeaponAttack.Weapon -> {
                out.writeShortLE(attack.attackTotal)
                out.writeByte(CIPBIA_ELEMENTAL_PHYSICAL)
                out.writeCanaryDouble(attack.convertedDamage)
                out.writeByte(attack.convertedElement)
            }

            is WeaponAttack.Fist -> {
                out.writeShortLE(attack.attackTotal)
                out.writeByte(CIPBIA_ELEMENTAL_PHYSICAL)
                out.writeCanaryDouble(0.0)
                out.writeByte(0x00)
            }
        }
    }

    private fun writeContainerCategory(
        out: ByteBuf,
        categories: List<ContainerCategory>,
        type: ContainerCategory = ContainerCategory.All,
    ) {
        out.writeByte(type.value)
        out.writeByte(categories.size)
        for (category in categories) {
            if (category == ContainerCategory.All) {
                continue
            }
            out.writeByte(category.value)
            out.writeTibiaString(category.displayName)
        }
    }

    /**
     * `AddCreature(NetworkMessage&, creature, known, remove)` current-profile
     * layout (protocolgame.cpp:9662-9780) without the 0x6A frame prefix. The
     * session-owned `checkCreatureAsKnown` result and the player-derived fields
     * arrive through [appearance]; `GetTileDescription` and `sendUpdateTileCreature`
     * call this directly.
     */
    fun writeCreature(out: ByteBuf, appearance: CreatureAppearanceFrame) {
        var creatureType = appearance.creatureType

        if (appearance.known) {
            out.writeShortLE(0x62)
            out.writeIntLE(appearance.id.toInt())
        } else {
            out.writeShortLE(0x61)
            out.writeIntLE(appearance.removedKnown.toInt())
            out.writeIntLE(appearance.id.toInt())

            if (appearance.healthHidden) {
                out.writeByte(CREATURETYPE_HIDDEN)
            } else {
                out.writeByte(creatureType.value)
            }

            if (creatureType == CreatureType.SUMMON_PLAYER) {
                out.writeIntLE((appearance.masterId ?: 0L).toInt())
            }

            if (appearance.healthHidden) {
                out.writeTibiaString("")
            } else {
                out.writeTibiaString(appearance.name)
            }
        }

        if (appearance.healthHidden) {
            out.writeByte(0x00)
        } else {
            val healthPercent = ceil(
                (appearance.health.toDouble() / maxOf(appearance.maxHealth, 1)) * 100,
            )
            out.writeByte(healthPercent.toInt() and 0xFF)
        }

        out.writeByte(appearance.direction.byteValue.toInt() and 0xFF)

        if (!appearance.isInvisibleOrGhost) {
            writeOutfit(out, appearance.outfit)
        } else {
            writeOutfit(out, Outfit())
        }

        out.writeByte(if (appearance.accessPlayer) 0xFF else appearance.light.level)
        out.writeByte(appearance.light.color)

        out.writeShortLE(appearance.stepSpeed)

        writeCreatureIcons(out, appearance.icons)

        out.writeByte(appearance.skull)
        out.writeByte(appearance.partyShield)

        if (!appearance.known) {
            out.writeByte(appearance.guildEmblem)
        }

        if (creatureType == CreatureType.MONSTER && appearance.masterIsPlayer) {
            creatureType = CreatureType.SUMMON_PLAYER
        }

        if (appearance.healthHidden) {
            out.writeByte(CREATURETYPE_HIDDEN)
        } else {
            out.writeByte(creatureType.value)
        }

        if (creatureType == CreatureType.SUMMON_PLAYER) {
            out.writeIntLE((appearance.masterId ?: 0L).toInt())
        }

        if (creatureType == CreatureType.PLAYER) {
            out.writeByte(appearance.vocationClientId)
        }

        out.writeByte(appearance.speechBubble)
        out.writeByte(appearance.creatureMark)
        out.writeByte(0x00) // inspection type
        out.writeByte(if (appearance.canWalkthrough) 0x00 else 0x01)
    }

    private fun writeCreatureIcons(out: ByteBuf, icons: List<CreatureIcon>) {
        val count = minOf(MAX_CLIENT_ICONS, icons.size)
        out.writeByte(count)
        for (index in 0 until count) {
            val icon = icons[index]
            out.writeByte(icon.serialize)
            out.writeByte(icon.category)
            out.writeShortLE(icon.count)
        }
    }

    private fun ByteBuf.writePosition(pos: Position) {
        writeShortLE(pos.x)
        writeShortLE(pos.y)
        writeByte(pos.z)
    }

    /**
     * `NetworkMessage::addDouble` (`src/server/network/message/networkmessage.cpp:174`):
     * precision byte plus `(value * 10^precision) + INT32_MAX` as `uint32_t`.
     */
    private fun ByteBuf.writeCanaryDouble(value: Double, precision: Int = 4) {
        writeByte(precision)
        val scaled = value * 10.0.pow(precision) + Int.MAX_VALUE
        writeIntLE(scaled.toLong().toInt())
    }

    private fun Item.intAttribute(attribute: ItemAttribute): Long =
        getAttribute(attribute) as? Long ?: 0L
}
