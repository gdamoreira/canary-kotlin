package org.opentibia.canary.game

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountErrors
import org.opentibia.canary.account.AccountType
import org.opentibia.canary.account.CoinTransactionType
import org.opentibia.canary.account.CoinType
import org.opentibia.canary.chat.CHANNEL_GUILD
import org.opentibia.canary.chat.CHANNEL_PRIVATE
import org.opentibia.canary.chat.ChatClient
import org.opentibia.canary.chat.ChatManager
import org.opentibia.canary.chat.NoopChatClient
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.BlockType
import org.opentibia.canary.combat.Combat
import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.combat.CombatOrigin
import org.opentibia.canary.combat.CombatParams
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.FeatureFlags
import org.opentibia.canary.creatures.ChaseMode
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.FlagAsyncClass_t
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.players.WalkStartPolicy
import org.opentibia.canary.creatures.players.getDodgeChance
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.appearance.outfit.Outfit as OutfitDefinition
import org.opentibia.canary.creatures.appearance.effects.AttachedEffects
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.grouping.TeamFinder
import org.opentibia.canary.creatures.grouping.TeamFinderList
import org.opentibia.canary.creatures.monsters.ForgeClassification
import org.opentibia.canary.creatures.monsters.MonsterForge
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.creatures.players.components.Achievement
import org.opentibia.canary.creatures.players.components.AchievementRegistry
import org.opentibia.canary.creatures.players.components.Badge
import org.opentibia.canary.creatures.players.components.BadgeRegistry
import org.opentibia.canary.creatures.players.components.CyclopediaDatabaseTasks
import org.opentibia.canary.creatures.players.components.CyclopediaQueryResult
import org.opentibia.canary.creatures.players.components.HighscoreCategories_t
import org.opentibia.canary.creatures.players.components.InMemoryAchievementRegistry
import org.opentibia.canary.creatures.players.components.InMemoryBadgeRegistry
import org.opentibia.canary.creatures.players.components.InMemoryTitleRegistry
import org.opentibia.canary.creatures.players.components.NullCyclopediaDatabaseTasks
import org.opentibia.canary.creatures.players.components.PlayerAsyncTask
import org.opentibia.canary.creatures.players.components.Title
import org.opentibia.canary.creatures.players.components.TitleRegistry
import org.opentibia.canary.creatures.players.vip.PlayerVip
import org.opentibia.canary.creatures.players.vip.VipStatus
import org.opentibia.canary.creatures.monsters.MonsterTargeting
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.npcs.NpcInfo
import org.opentibia.canary.creatures.players.PlayerTask
import org.opentibia.canary.creatures.players.playerSendSink
import org.opentibia.canary.creatures.players.sendCreatureReload
import org.opentibia.canary.creatures.players.sendForgingData
import org.opentibia.canary.creatures.players.sendPartyPlayerMana
import org.opentibia.canary.creatures.players.sendPartyPlayerVocation
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.database.Database
import org.opentibia.canary.database.PlayerLoginRepository
import org.opentibia.canary.economy.market.MarketOfferType
import org.opentibia.canary.game.modal.ModalWindow
import org.opentibia.canary.game.modal.ModalWindowManager
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.movement.areInRange
import org.opentibia.canary.game.movement.getDirectionTo
import org.opentibia.canary.game.movement.getDistanceX
import org.opentibia.canary.game.movement.getDistanceY
import org.opentibia.canary.game.movement.getDistanceZ
import org.opentibia.canary.game.movement.getNextPosition
import org.opentibia.canary.game.scheduling.CreatureAsyncTaskBuckets
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.Zone
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.io.appearances.Appearances
import org.opentibia.canary.io.HistoryMarketOffer
import org.opentibia.canary.io.IOBosstiary
import org.opentibia.canary.io.IOMarket
import org.opentibia.canary.io.MarketOffer
import org.opentibia.canary.io.MarketOfferEx
import org.opentibia.canary.io.MarketOfferState
import org.opentibia.canary.io.marketTimeNow
import org.opentibia.canary.items.BedItem
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ContainerIterator
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ITEM_CRYSTAL_COIN
import org.opentibia.canary.items.ITEM_GOLD_COIN
import org.opentibia.canary.items.ITEM_PLATINUM_COIN
import org.opentibia.canary.items.ItemProperty
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.items.SLOTP_AMMO
import org.opentibia.canary.items.SLOTP_ARMOR
import org.opentibia.canary.items.SLOTP_LEFT
import org.opentibia.canary.items.SLOTP_TWO_HAND
import org.opentibia.canary.items.SLOTP_BACKPACK
import org.opentibia.canary.items.SLOTP_FEET
import org.opentibia.canary.items.SLOTP_HAND
import org.opentibia.canary.items.SLOTP_HEAD
import org.opentibia.canary.items.SLOTP_LEGS
import org.opentibia.canary.items.SLOTP_NECKLACE
import org.opentibia.canary.items.SLOTP_RING
import org.opentibia.canary.items.Thing
import org.opentibia.canary.items.containers.Inbox
import org.opentibia.canary.items.containers.depot.DepotChest
import org.opentibia.canary.items.containers.depot.DepotLocker
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Spectators
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.house.AccessHouseLevel
import org.opentibia.canary.map.house.CyclopediaHouseState
import org.opentibia.canary.map.house.House
import org.opentibia.canary.map.house.TradeEvents_t
import org.opentibia.canary.network.protocol.AcceptTransferErrorMessage
import org.opentibia.canary.network.protocol.BidErrorMessage
import org.opentibia.canary.network.protocol.BidSuccessMessage
import org.opentibia.canary.network.protocol.CreatureType
import org.opentibia.canary.network.protocol.ContainerCategory
import org.opentibia.canary.network.protocol.HighscoresWindowSerializer
import org.opentibia.canary.network.protocol.HouseAuctionType
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.protocol.SourceEffect
import org.opentibia.canary.network.protocol.StoreWindowWriter
import org.opentibia.canary.network.protocol.TransferErrorMessage
import org.opentibia.canary.network.protocol.cyclopedia.CyclopediaCharacterWriter
import org.opentibia.canary.network.protocol.cyclopedia.CyclopediaWindowWriter
import org.opentibia.canary.network.protocol.wheel.SaveWheelCommand
import org.opentibia.canary.network.protocol.wheel.WheelGemActionCommand
import org.opentibia.canary.persistence.PlayerStorage
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.social.guild.GuildRepository
import org.opentibia.canary.spells.PlayerSaySpellResult
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.spells.TalkActionResult
import org.opentibia.canary.spells.WorldType
import org.opentibia.canary.systems.forge.Forge
import org.opentibia.canary.systems.forge.ForgeAction_t
import org.opentibia.canary.systems.forge.ForgeRandom
import org.opentibia.canary.systems.bestiary.Charm
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.bestiary.CharmType
import org.opentibia.canary.systems.bestiary.PlayerCharmsByMonster
import org.opentibia.canary.systems.forge.ItemClassification
import org.opentibia.canary.systems.forge.ItemClassifications
import org.opentibia.canary.systems.imbuement.Imbuement
import org.opentibia.canary.systems.imbuement.Imbuements
import org.opentibia.canary.systems.mount.Mount
import org.opentibia.canary.systems.prey.PreyAction
import org.opentibia.canary.systems.prey.PreyOption
import org.opentibia.canary.systems.rewardchest.PlayerRewardChestOwner
import org.opentibia.canary.systems.rewardchest.RewardChest
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.systems.taskhunting.PreyTaskAction
import org.opentibia.canary.systems.wheel.IOWheel
import org.opentibia.canary.systems.wheel.WheelApplication
import org.opentibia.canary.systems.wheel.WheelPlayer
import org.opentibia.canary.systems.wheel.WheelStat
import org.opentibia.canary.utils.Tools
import org.opentibia.canary.utils.WildcardTreeNode
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.IdentityHashMap
import java.util.Random as JavaRandom
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/** `ITEM_STASH_INDEX` (`src/utils/utils_definitions.hpp:552`), the stash pseudo-floor. */
private const val ITEM_STASH_INDEX = 1

/** `LightState_t` (`src/game/game_definitions.hpp:57-62`). */
enum class LightState {
    LIGHT_STATE_DAY,
    LIGHT_STATE_NIGHT,
    LIGHT_STATE_SUNSET,
    LIGHT_STATE_SUNRISE,
}

/**
 * `TradeState_t` (`src/creatures/creatures_definitions.hpp:825-831`): the trade
 * window state machine driven by the `Game` trade handlers.
 */
enum class TradeState_t {
    TRADE_NONE,
    TRADE_INITIATED,
    TRADE_ACCEPT,
    TRADE_ACKNOWLEDGE,
    TRADE_TRANSFER,
}

/** `GameState_t` (`src/game/game_definitions.hpp:28-36`): the `Game::gameState` machine. */
enum class GameState_t {
    GAME_STATE_STARTUP,
    GAME_STATE_INIT,
    GAME_STATE_NORMAL,
    GAME_STATE_CLOSED,
    GAME_STATE_SHUTDOWN,
    GAME_STATE_CLOSING,
    GAME_STATE_MAINTAIN,
}

/** `PvpMode_t` (`src/creatures/creatures_definitions.hpp:818-823`): the expert-PvP stance of `Game::playerSetFightModes`. */
enum class PvpMode_t(val value: Int) {
    PVP_MODE_DOVE(0),
    PVP_MODE_WHITE_HAND(1),
    PVP_MODE_YELLOW_HAND(2),
    PVP_MODE_RED_FIST(3),
}

/**
 * `StackPosType_t` (`src/game/game_definitions.hpp:13-20`): the target selector
 * of `Game::internalGetThing`.
 */
enum class StackPosType_t {
    STACKPOS_MOVE,
    STACKPOS_LOOK,
    STACKPOS_TOPDOWN_ITEM,
    STACKPOS_USEITEM,
    STACKPOS_USETARGET,
    STACKPOS_FIND_THING,
}

/** `QuickLootFilter_t` (src/game/game_definitions.hpp:38-41). */
enum class QuickLootFilter_t(val value: Int) {
    QUICKLOOTFILTER_SKIPPEDLOOT(0),
    QUICKLOOTFILTER_ACCEPTEDLOOT(1),
    ;

    companion object {
        /** Unknown protocol bytes keep the C++ default value (`quickLootFilter {}`). */
        fun fromValue(value: Int): QuickLootFilter_t =
            entries.firstOrNull { it.value == value } ?: QUICKLOOTFILTER_SKIPPEDLOOT
    }
}

/**
 * `struct HighscoreCategory` (`src/server/server_definitions.hpp:174`): the
 * `m_name` / `m_id` pair exposed by `Game::getHighscoreCategories`.
 */
data class HighscoreCategory(
    val name: String,
    val id: Int,
)

/** `HighscoreType_t` (`src/game/game_definitions.hpp:109-111`). */
enum class HighscoreType_t {
    HIGHSCORE_GETENTRIES,
    HIGHSCORE_OURRANK,
}

/** `Summary_t` (`src/enums/player_cyclopedia.hpp:40-48`): the cyclopedia summary category keys. */
enum class Summary_t(val value: Int) {
    HOUSE_ITEMS(9),
    BOOSTS(10),
    PREY_CARDS(12),
    BLESSINGS(14),
    ALL_BLESSINGS(17),
    INSTANT_REWARDS(18),
    HIRELINGS(20),
}

/** `struct PlayerStats` (`src/game/game.hpp:96-99`): the public online-IP statistics. */
data class PlayerStats(
    var totalUniqueIPs: Int = 0,
    var filteredOnlinePlayers: Int = 0,
)

/**
 * Kotlin carrier for the `uint8_t &skill` in/out parameter of
 * `Game::getSkillNameById` (game.cpp:10157): [category] is the resolved
 * category (the C++ function rewrites an unknown skill to `EXPERIENCE`).
 */
data class HighscoreSkillName(
    val name: String,
    val category: Int,
)

/** `FILELOADER_ERRORS` (`src/io/io_definitions.hpp:13-22`). */
enum class FileLoaderError(val value: Int) {
    ERROR_NONE(0),
    ERROR_INVALID_FILE_VERSION(1),
    ERROR_CAN_NOT_OPEN(2),
    ERROR_CAN_NOT_CREATE(3),
    ERROR_EOF(4),
    ERROR_SEEK_ERROR(5),
    ERROR_NOT_OPEN(6),
    ERROR_INVALID_NODE(7),
    ERROR_INVALID_FORMAT(8),
}

/**
 * One `boosted_creature` row read by `Game::loadBoostedCreature`
 * (game.cpp:787-790): the SELECT column order is preserved.
 */
data class BoostedCreatureRecord(
    val date: Int,
    val boostname: String,
    val raceid: Int,
    val looktype: Int,
    val lookfeet: Int,
    val looklegs: Int,
    val lookhead: Int,
    val lookbody: Int,
    val lookaddons: Int,
    val lookmount: Int,
)

/**
 * Faithful facade over the Kotlin world model mirroring Canary's `Game`
 * (src/game/game.hpp:95). Every method transcribes an existing `Game::` member;
 * globals (`g_game()`) are replaced by constructor injection of the [GameMap] and
 * [GameDispatcher] per AGENTS.md 1.1.
 *
 * Implemented surface:
 *  - `internalAddItem` (game.cpp:2693, 2698) / `internalRemoveItem` (game.cpp:2785)
 *  - `internalMoveItem` (game.cpp:2436)
 *  - `internalPlaceCreature` (game.cpp:1495) / `placeCreature` (game.cpp:1524)
 *  - `removeCreature` (game.cpp:1551) / `addCreatureCheck` (game.cpp:7659)
 *  - `removeCreatureCheck` (game.cpp:7687)
 *  - `getPlayers` (game.hpp:577) / `getPlayerByID` (game.cpp:1339)
 *  - `getPlayerByName` (game.cpp:1394)
 *  - `addPlayer` (game.cpp:11624) / `removePlayer` (game.cpp:11631)
 *  - `broadcastMessage` (game.cpp:9526)
 *  - `getWorldLightInfo` (game.cpp:9479) / `gameIsDay` (game.cpp:9483) /
 *    `checkLight` (game.cpp:9402)
 *  - `sendSingleSoundEffect` (game.cpp:7833) / `sendDoubleSoundEffect` (game.cpp:7853)
 *  - `playerStopAutoWalk` (game.cpp:4567)
 *  - trade window (`playerRequestTrade` game.cpp:5797, `internalStartTrade` game.cpp:5951,
 *    `playerAcceptTrade` game.cpp:5986, `getTradeErrorDescription` game.cpp:6088,
 *    `playerLookInTrade` game.cpp:6119, `playerCloseTrade` game.cpp:6178,
 *    `internalCloseTrade` game.cpp:6187)
 *  - shop (`playerBuyItem` game.cpp:6228, `playerSellItem` game.cpp:6284,
 *    `playerCloseShop` game.cpp:6319, `getInteractableShopOwner` game.cpp:196)
 *  - party family (`playerInviteToParty` game.cpp:9657 … `playerEnableSharedPartyExperience`
 *    game.cpp:9778)
 *  - `checkCreatures` (game.cpp:7719)
 *  - guild registry (`getGuild` game.cpp:11727, `getGuildByName` game.cpp:11738,
 *    `addGuild` game.cpp:11750, `removeGuild` game.cpp:11757) and
 *    `sendGuildMotd` (game.cpp:9793)
 *  - VIP (`playerRequestAddVip` game.cpp:7058 … `playerRequestEditVip` game.cpp:7107)
 *  - reports (`playerReportRuleViolationReport` game.cpp:10183,
 *    `playerReportBug` game.cpp:10193)
 *  - highscore helpers (`calculateHighscorePages` game.cpp:10033,
 *    `getHighscoreCategoriesName` game.cpp:12473, `getHighscoreCategories` game.cpp:12477)
 *  - async highscores (`playerHighscores` game.cpp:10124, `getSkillNameById`
 *    game.cpp:10157, `generateHighscoreQuery` game.cpp:9890,
 *    `generateVocationConditionHighscore` game.cpp:9968,
 *    `processHighscoreResults` game.cpp:9986,
 *    `generateHighscoreOrGetCachedQueryForEntries` game.cpp:10084,
 *    `generateHighscoreOrGetCachedQueryForOurRank` game.cpp:10104)
 *  - house auctions (`playerCyclopediaHousesByTown` game.cpp:12762 …
 *    `playerCyclopediaHouseRejectTransfer` game.cpp:13042, `processBankAuction`
 *    game.cpp:13081, `playerUpdateHouseWindow` game.cpp:5780)
 *  - item wrapping (`wrapItem` game.cpp:5472, `unwrapItem` game.cpp:5501,
 *    `transformItem` game.cpp:3401)
 *  - house transfer persistence (`setTransferPlayerHouseItems` game.cpp:12399,
 *    `transferHouseItemsToDepot` game.cpp:12363)
 *  - `getPlayerByGUID` (game.cpp:1419)
 *  - MOTD counter (`saveMotdNum` game.cpp:9613, `getMotdNum`/`getMotdHash`/
 *    `incrementMotdNum` game.hpp:559-567)
 *  - `beforeCreatureZoneChange` (game.cpp:12417) / `afterCreatureZoneChange` (game.cpp:12447)
 *  - movement/targeting (`internalCreatureTurn` game.cpp:7599, `playerTurn` game.cpp:7187,
 *    `internalMoveCreature` game.cpp:1909/1963, `playerMove` game.cpp:4282,
 *    `forcePlayerMove` game.cpp:4296, `playerTeleport` game.cpp:1622,
 *    `internalTeleport` game.cpp:3540, `playerSetAttackedCreature` game.cpp:7001,
 *    `playerCancelAttackAndFollow` game.cpp:6990, `playerFollowCreature` game.cpp:7033,
 *    `playerSetFightModes` game.cpp:7044, `playerLookAt` game.cpp:6358,
 *    `playerLookInBattleList` game.cpp:6393, `canThrowObjectTo` game.cpp:7591,
 *    `isSightClear` game.cpp:7595, `changeSpeed` game.cpp:7747,
 *    `setCreatureSpeed` game.cpp:7759, `changePlayerSpeed` game.cpp:7768)
 *  - market (`playerBrowseMarket` game.cpp:10282, `playerBrowseMarketOwnOffers` game.cpp:10307,
 *    `playerBrowseMarketOwnHistory` game.cpp:10322, `getItemMarketPrice` game.cpp:4027,
 *    `getItemMarketAveragePrice` game.cpp:4078, `loadItemsPrice` game.cpp:1037,
 *    `updatePlayerSaleItems` game.cpp:11596, `playerCreateMarketOffer` game.cpp:10717,
 *    `playerCancelMarketOffer` game.cpp:10819, `playerAcceptMarketOffer` game.cpp:10884)
 *  - quest/forge/bosstiary/task (`playerShowQuestLog` game.cpp:7391,
 *    `playerShowQuestLine` game.cpp:7401, `playerPreyAction` game.cpp:10218,
 *    `playerTaskHuntingAction` game.cpp:10227, `playerBosstiarySlot` game.cpp:11256,
 *    `playerForgeResourceConversion` game.cpp:11226, `playerBrowseForgeHistory` game.cpp:11241,
 *    `playerForgeFuseItems` game.cpp:11178, `playerForgeTransferItemTier` game.cpp:11211)
 *  - wheel (`playerOpenWheel` game.cpp:11525, `playerSaveWheel` game.cpp:11538,
 *    `playerWheelGemAction` game.cpp:11554)
 *  - wheel combat application (`applyWheelOfDestinyHealing` game.cpp:8264,
 *    `applyWheelOfDestinyEffectsToDamage` game.cpp:8287, `getIOWheel` game.cpp:12347)
 *  - monster registry (`addMonster` game.cpp:11671, `removeMonster` game.cpp:11683,
 *    `getMonsterByID` game.cpp:1309)
 *  - fiendish/influenced forge events (`makeInfluencedMonster` game.cpp:11882,
 *    `makeFiendishMonster` game.cpp:11928, `updateFiendishMonsterStatus`
 *    game.cpp:12045, `removeForgeMonster` game.cpp:12057, `removeInfluencedMonster`
 *    game.cpp:12067, `removeFiendishMonster` game.cpp:12084, `updateForgeableMonsters`
 *    game.cpp:12103, `createFiendishMonsters` game.cpp:12139,
 *    `createInfluencedMonsters` game.cpp:12170, `checkForgeEventId` game.cpp:12201,
 *    `addInfluencedMonster` game.cpp:12209)
 *  - show-off socket (`playerSetShowOffSocket` game.cpp:5227)
 *  - NPC/podium (`playerNpcGreet` game.cpp:10236, `getNpcByID` game.cpp:1326,
 *    `playerSetMonsterPodium` game.cpp:11283, `playerRotatePodium` game.cpp:11395)
 *  - `playerAnswerModalWindow` (game.cpp:11144) through the injected
 *    `ModalWindowManager`
 *  - remaining player handlers (`playerApplyImbuement` game.cpp:7116,
 *    `playerClearImbuement` game.cpp:7153, `playerRequestInventoryImbuements` game.cpp:11487,
 *    `playerToggleMount` game.cpp:7219, `playerRequestOutfit` game.cpp:7206,
 *    `playerReceivePing` game.cpp:4454, `playerReceivePingBack` game.cpp:4463,
 *    `playerCloseNpcChannel` game.cpp:4443, `playerBroadcastMessage` game.cpp:4310)
 *  - combat state-change pipeline (`combatChangeHealth` game.cpp:8344,
 *    `applyHealthChange` game.cpp:8327, `checkGiftOfLife` player_wheel.cpp:3155,
 *    `combatChangeMana` game.cpp:9063, `applyPvPDamage` game.cpp:8232,
 *    `pvpLevelDifferenceDamageMultiplier` game.cpp:8243, `applyLifeLeech`
 *    game.cpp:9029, `applyManaLeech` game.cpp:8998, `calculateLeechAmount`
 *    game.cpp:9058, `updatePlayerPartyHuntAnalyzer` game.cpp:8773,
 *    `handleHazardSystemAttack` game.cpp:8193, `addMagicEffect` game.cpp:9343,
 *    `sendDamageMessageAndEffects` game.cpp:8788)
 *  - host seams (`checkCreatures` → `Monster::trySchedulePostThink`
 *    game.cpp:7719, `queueMonsterPostThink` game.cpp:7694, walk seams bound to
 *    the host `Player`; `playerAutoWalk` game.cpp:4472)
 *  - channel handlers (`playerCreatePrivateChannel` game.cpp:4324 …
 *    `playerOpenPrivateChannel` game.cpp:4424) through the injected
 *    `ChatManager`
 *  - world/creature accessors (`getGameState` game.cpp:922, `setGameState`
 *    game.cpp:952, `setWorldType` game.cpp:926, `getCreatureByName`
 *    game.cpp:1356, `getNpcByName` game.cpp:1380, `addNpc` game.cpp:11639,
 *    `removeNpc` game.cpp:11646, `getPlayerNameByGUID` game.cpp:1439,
 *    `getPlayerByNameWildcard` game.cpp:1454, `getPlayersByAccount`
 *    game.cpp:1480, `addDeadPlayer` game.cpp:11607, `removeDeadPlayer`
 *    game.cpp:11611, `getDeadPlayer` game.cpp:11615)
 *  - quick loot/managed containers (`getObjectCategory` game.cpp:3946,
 *    `playerSetManagedContainer` game.cpp:6822,
 *    `playerClearManagedContainer` game.cpp:6867,
 *    `playerOpenManagedContainer` game.cpp:6884,
 *    `findManagedContainer` game.cpp:3757,
 *    `findNextAvailableContainer` game.cpp:3774,
 *    `handleFallbackLogic` game.cpp:3798,
 *    `processMoveOrAddItemToLootContainer` game.cpp:3814,
 *    `processLootItems` game.cpp:3828,
 *    `internalCollectManagedItems` game.cpp:3850,
 *    `playerQuickLootCorpse` game.cpp:3596, `playerQuickLoot` game.cpp:6428,
 *    `playerLootAllCorpses` game.cpp:6575, `playerLootNearby` game.cpp:6766)
 *  - say handlers (`playerSay` game.cpp:7411, `playerSaySpell` game.cpp:7477,
 *    `playerWhisper` game.cpp:7497, `playerYell` game.cpp:7517,
 *    `playerSpeakTo` game.cpp:7537, `playerSpeakToNpc` game.cpp:7563)
 *  - world-state updaters (`changeLight` game.cpp:7808, `updateCreatureIcon`
 *    game.cpp:7815, `reloadCreature` game.cpp:7822, `sendUpdateCreature`
 *    game.cpp:11872, `addPlayerMana` game.cpp:9326, `addPlayerVocation`
 *    game.cpp:9333, `removeMagicEffect` game.cpp:9365, `addDistanceEffect`
 *    game.cpp:9378, `hasEffect` game.cpp:11825, `hasDistanceEffect`
 *    game.cpp:11835, `updateCreatureWalkthrough` game.cpp:9535,
 *    `updateCreatureSkull` game.cpp:9543, `updatePlayerShield`
 *    game.cpp:9553, `updateCreatureType` game.cpp:9559,
 *    `updatePlayerHelpers` game.cpp:9690, `sendAttachedEffect`
 *    game.cpp:12674, `sendDetachEffect` game.cpp:12684,
 *    `updateCreatureShader` game.cpp:12694, `refreshItem` game.cpp:12716,
 *    `playerSetTyping` game.cpp:12704, `kickPlayer` game.cpp:9805)
 *  - records/helpers (`checkPlayersRecord` game.cpp:9625,
 *    `updatePlayersRecord` game.cpp:9638, `loadPlayersRecord` game.cpp:9646,
 *    `groupPlayersByIP` game.cpp:13113, `getPlayerStats` game.cpp:13129,
 *    `forceRemoveCondition` game.cpp:11125, `addBestiaryList`
 *    game.cpp:9517, `getSummaryKeyByType` game.cpp:12596,
 *    `getHirelingSkills`/`getHirelingOutfits` game.cpp:12600-12604,
 *    `getPlayersOnline`/`getMonstersOnline` game.hpp:195-200)
 *  - map seams: `internalPlaceCreature`/`placeCreature`/`removeCreature`
 *    delegate to `Map::placeCreature`/`Map::removeCreature` and every
 *    `Spectators().find` fanout resolves through the map sectors; a placed
 *    monster is registered with [monsterHost] using [monsterTypeProvider] and
 *    unregistered again on removal, so its deferred pipelines activate and
 *    deactivate with the world lifecycle; the forge seams read/write
 *    `MonsterPipelinesHost.typeStateOf` through [MonsterForge]
 *  - stash/depot search (`isTryingToStow` game.cpp:2297, `playerStowItem`
 *    game.cpp:5665, `playerStashWithdraw` game.cpp:5706,
 *    `playerRequestDepotItems` game.cpp:6920, `playerRequestDepotSearchItem`
 *    game.cpp:6945, `playerRequestDepotSearchRetrieve` game.cpp:6960,
 *    `playerRequestOpenContainerFromDepotSearch` game.cpp:6975,
 *    `tryRetrieveStashItems` game.cpp:12342, `addItemStoreInbox`
 *    game.cpp:12231)
 *  - item batch creation (`addItemBatch` game.cpp:2848, `createItemBatch`
 *    game.cpp:2947, `createItem` game.cpp:2977, `internalPlayerAddItem`
 *    game.cpp:2981, `findItemOfType` game.cpp:3013, `internalRemoveItems`
 *    game.cpp:11765)
 *  - use-item/container handlers (`playerUseItem` game.cpp:4739,
 *    `playerUseItemEx` game.cpp:4576, `playerUseWithCreature` game.cpp:4853,
 *    `playerRotateItem` game.cpp:5104, `playerWriteItem` game.cpp:5535,
 *    `playerBrowseField` game.cpp:5599, `playerSeekInContainer` game.cpp:5755,
 *    `playerUpdateContainer` game.cpp:5090, `queuePlayerAutoWalkAfterItemPickup`
 *    game.cpp:4500, `forcePlayerAutoWalk` game.cpp:4548,
 *    `internalGetPosition` game.cpp:1269)
 *  - cyclopedia/registry helpers (`getAchievementById` game.cpp:12493,
 *    `getAchievementByName` game.cpp:12497, `getBadges` game.cpp:12536,
 *    `getBadgeById` game.cpp:12540, `getBadgeByName` game.cpp:12553,
 *    `getTitles` game.cpp:12566, `getTitleById` game.cpp:12570,
 *    `getTitleByName` game.cpp:12583, `logCyclopediaStats` game.cpp:12531,
 *    `getItemsClassification` game.cpp:9463, `getAttachedEffects`
 *    game.cpp:12355, `getTeamFinder` game.cpp:930,
 *    `getOrCreateTeamFinder` game.cpp:939, `removeTeamFinderListed`
 *    game.cpp:948, `getBedBySleeper` game.cpp:11783, `setBedSleeper`
 *    game.cpp:11791, `removeBedSleeper` game.cpp:11795, `getUniqueItem`
 *    game.cpp:11802, `addUniqueItem` game.cpp:11810, `removeUniqueItem`
 *    game.cpp:11818, `internalCreatureChangeVisible` game.cpp:7801,
 *    `updateMonster` game.cpp:11708, `playerFriendSystemAction` game.cpp:9814,
 *    `createPlayerTask` game.cpp:7586, `resetNpcs` game.cpp:777, `dieSafely`
 *    game.cpp:9493, `shutdown` game.cpp:9498)
 *
 * All state is thread-confined: mutating methods must run inside the injected
 * [dispatcher]'s single-threaded `WorldCommit` lane (AGENTS.md 1.2).
 */
class Game(
    val map: GameMap,
    val dispatcher: GameDispatcher,
    private val playerStorage: PlayerStorage? = null,
    /**
     * `IOGuild` persistence seam (src/io/ioguild.cpp): `loadGuild` and
     * `getGuildIdByName` for the `getGuild`/`getGuildByName` `allowOffline`
     * fallback.
     */
    private val guildRepository: GuildRepository? = null,
    /** `g_saveManager().saveGuild` seam (game.cpp:11760). */
    private val saveGuild: (Guild) -> Unit = {},
    /** `Player::client` channel sender seam (`Player::sendChannelMessage`). */
    private val chatClient: ChatClient = NoopChatClient,
    /** `IOLoginData::getGuidByNameEx` seam (game.cpp:7073). */
    private val loginRepository: PlayerLoginRepository? = null,
    /** `Player::vip()` accessor (player.hpp:739). */
    private val playerVip: (Player) -> PlayerVip? = { null },
    /** `Player::isInGhostMode` (player.cpp:568). */
    private val isInGhostMode: (Player) -> Boolean = { false },
    /** `Item::items` registry (src/items/items.hpp:389) used by the shop handlers. */
    private val itemTypes: Items? = null,
    /** `g_database()` (game.cpp:9592-9630) seam for the MOTD counter persistence. */
    private val database: Database? = null,
    /** `g_databaseTasks()` (game.cpp:10152) seam for the async highscore query. */
    private val databaseTasks: CyclopediaDatabaseTasks = NullCyclopediaDatabaseTasks(),
    /** `g_vocations()` (game.cpp:9968, 10021) seam for the highscore queries. */
    private val vocations: Vocations? = null,
    /** `IOLoginData::loadPlayerById` (game.cpp:1436, 12378) seam. */
    private val loadPlayerById: (Long) -> Player? = { null },
    /**
     * `OTSYS_TIME()` (src/utils/tools.hpp): monotonic millisecond clock used by
     * the `InternalGame` teleport guard (game.cpp:220-405). Injectable so tests
     * can drive the rate-limit windows.
     */
    private val otsysTimeMs: () -> Long = { System.currentTimeMillis() },
    /**
     * The initial `Game::m_appearancesPtr` (game.hpp:881) behind
     * [outfitSupportsMount]. The `{CORE_DIRECTORY}/items/appearances.dat`
     * parsing already lives in `io.appearances.Appearances`; hosts load it once
     * and inject the tables, or leave it to [loadAppearanceProtobuf]. `null`
     * keeps the C++ "no appearances loaded" behavior (`true`).
     */
    appearances: Appearances? = null,
    /**
     * `IOMarket::getInstance()` (game.cpp:10297, 4078) seam for the market
     * browse handlers and the average-price query.
     */
    private val ioMarket: IOMarket? = null,
    /** `g_ioBosstiary()` (game.cpp:11270) seam for the bosstiary slot handler. */
    private val ioBosstiary: IOBosstiary? = null,
    /** `g_imbuements()` (game.cpp:7124) seam for the imbuement handlers. */
    private val imbuements: Imbuements? = null,
    /**
     * `g_configManager()` `FORGE_*` values behind the `Game::playerForgeFuseItems`
     * success and bonus rolls (game.cpp:11193-11208); the engine is stateless
     * for this handler.
     */
    private val forge: Forge = Forge(),
    /** `uniform_random` (src/utils/tools.hpp:90) seam for the forge rolls. */
    private val forgeRandom: ForgeRandom = ForgeRandom { minNumber, _ -> minNumber },
    /** `Player::wheel()` command engine (player_wheel.cpp) for the wheel handlers. */
    private val wheelApplication: WheelApplication = WheelApplication(),
    /**
     * `Game::offlineTrainingWindow` / `Player::hasModalWindowOpen` host, the
     * `Game::playerAnswerModalWindow` routing target (game.cpp:11144).
     */
    private val modalWindowManager: ModalWindowManager? = null,
    /**
     * The `MonsterPipelinesHost` owning the deferred monster pipelines
     * (`Monster::trySchedulePostThink`, `Game::queueMonsterPostThink`,
     * game.cpp:7694-7741). `null` keeps the non-monster `checkCreatures`
     * fallback inert until the host is injected.
     */
    private val monsterHost: MonsterPipelinesHost? = null,
    /**
     * `g_monsters().getMonsterType(name, silent)` (`Monsters::getMonsterType`,
     * src/creatures/monsters/monsters.cpp:363-373) seam resolving
     * `Monster::m_monsterType` (src/creatures/monsters/monster.hpp:363) when a
     * monster enters the world: [internalPlaceCreature] passes the result to
     * `MonsterPipelinesHost.register` so the type-driven host seams are bound.
     * The composition root injects the `Monsters` registry; without one the
     * pipeline still activates with inert type-driven seams.
     */
    private val monsterTypeProvider: (String) -> MonsterType? = { null },
    /** `g_chat()` (chat.cpp) seam behind the channel handlers (game.cpp:4324-4441). */
    private val chatManager: ChatManager? = null,
    /** `Game::m_achievements` registry (game.hpp) behind [getAchievementById]/[getAchievementByName]. */
    private val achievementRegistry: AchievementRegistry = InMemoryAchievementRegistry(),
    /** `Game::m_badges` registry (game.cpp:570-597) behind [getBadges]/[getBadgeById]/[getBadgeByName]. */
    private val badgeRegistry: BadgeRegistry = InMemoryBadgeRegistry(),
    /** `Game::m_titles` registry behind [getTitles]/[getTitleById]/[getTitleByName]. */
    private val titleRegistry: TitleRegistry = InMemoryTitleRegistry(),
    /** `Game::itemsClassifications` (game.hpp) behind [getItemsClassification]. */
    private val itemClassifications: ItemClassifications = ItemClassifications(),
    /** `Game::teamFinderMap` owner (game.hpp:140-148) behind the team-finder helpers. */
    private val teamFinderList: TeamFinderList = TeamFinderList(),
    /**
     * `g_configManager()` seam for the `Player` host values the facade copies
     * when a player enters the world. Currently `MAX_MESSAGEBUFFER`
     * (configmanager.cpp:315), read by `Player::removeMessageBuffer`
     * (player.cpp:3514-3543) on every accepted say. `null` leaves the host
     * value the composition root set on the `Player` untouched.
     */
    private val configManager: ConfigManager? = null,
    /**
     * `Creature::getCreatureAsyncTaskBuckets` shared world state
     * (creature.cpp:34-55): the buckets every non-monster creature batch is
     * admitted through by [bindCreatureAsyncTaskHost]. A host can inject the
     * same instance the monster pipeline host uses; when absent the facade
     * owns one over [dispatcher].
     */
    creatureAsyncTaskBuckets: CreatureAsyncTaskBuckets? = null,
) {
    /** `getCreatureAsyncTaskBuckets` world state (creature.cpp:34-55). */
    private val asyncTaskBuckets: CreatureAsyncTaskBuckets =
        creatureAsyncTaskBuckets ?: CreatureAsyncTaskBuckets(dispatcher = { dispatcher })

    private val players = mutableMapOf<Long, Player>()

    /** `Game::monsters` (game.hpp): every live monster, registered by [addMonster]. */
    private val monsters = mutableListOf<Monster>()

    /** `Game::monstersNameIndex` (game.hpp). */
    private val monstersNameIndex = mutableMapOf<String, Int>()

    /** `Game::monstersIdIndex` (game.hpp). */
    private val monstersIdIndex = mutableMapOf<Long, Int>()

    /** `Game::npcs` (game.hpp): every live npc, registered by [addNpc]. */
    private val npcs = mutableListOf<Npc>()

    /** `Game::npcsNameIndex` (game.hpp). */
    private val npcsNameIndex = mutableMapOf<String, Int>()

    /** `Game::npcsIdIndex` (game.hpp). */
    private val npcsIdIndex = mutableMapOf<Long, Int>()

    /** `Game::m_playerNameCache` (game.hpp:825): guid -> resolved name. */
    private val playerNameCache = mutableMapOf<Long, String>()

    /**
     * `Game::wildcardTree` (game.hpp:824): the lowercase player-name prefix
     * tree behind `getPlayerByNameWildcard`, maintained by [addPlayer] and
     * [removePlayer].
     */
    private val wildcardTree = WildcardTreeNode(true)

    /** `Game::m_deadPlayers` (game.hpp:820), keyed by the player name. */
    private val deadPlayers = mutableMapOf<String, WeakReference<Player>>()

    /** `Game::m_attachedEffects` (game.hpp:993) behind [getAttachedEffects]. */
    private val attachedEffects = AttachedEffects()

    /** `Game::bedSleepersMap` (game.hpp) behind [getBedBySleeper]/[setBedSleeper]/[removeBedSleeper]. */
    private val bedSleepersMap = mutableMapOf<Long, BedItem>()

    /** `Game::uniqueItems` (game.hpp) behind [getUniqueItem]/[addUniqueItem]/[removeUniqueItem]. */
    private val uniqueItems = mutableMapOf<Int, Item>()

    /** `Game::browseFields` (game.hpp): tile -> weak browse-field container. */
    private val browseFields = IdentityHashMap<Tile, WeakReference<Container>>()

    /** `Game::gameState` (game.hpp:926), initialised to `GAME_STATE_NORMAL`. */
    private var gameState = GameState_t.GAME_STATE_NORMAL

    /** `Game::worldType` (game.hpp:927), initialised to `WORLD_TYPE_PVP`. */
    private var worldType = WorldType.PVP

    /** `Game::forgeableMonsters` (game.hpp:901). */
    private val forgeableMonsters = mutableListOf<Long>()

    /** `Game::fiendishMonsters` (game.hpp:789). */
    private val fiendishMonsters = linkedSetOf<Long>()

    /** `Game::influencedMonsters` (game.hpp:790). */
    private val influencedMonsters = linkedSetOf<Long>()

    /** `Game::forgeMonsterEventIds` (game.hpp:788): monster id -> scheduled event id. */
    private val forgeMonsterEventIds = mutableMapOf<Long, Long>()

    /** `Game::m_IOWheel` (game.hpp) behind [getIOWheel]. */
    private val ioWheel = IOWheel()

    /** `Game::guilds` (game.hpp:866). */
    private val guilds = mutableMapOf<Long, Guild>()

    /** `Game::checkCreatures` rotation cursor (game.cpp:7721). */
    private var checkCreatureIndex = 0

    /** `Game::m_highscoreCategoriesNames` (game.cpp:705-721). */
    private val highscoreCategoriesNames: Map<Int, String> = linkedMapOf(
        HighscoreCategories_t.ACHIEVEMENTS.value to "Achievement Points",
        HighscoreCategories_t.AXE_FIGHTING.value to "Axe Fighting",
        HighscoreCategories_t.BOSS_POINTS.value to "Boss Points",
        HighscoreCategories_t.CHARMS.value to "Charm Points",
        HighscoreCategories_t.CLUB_FIGHTING.value to "Club Fighting",
        HighscoreCategories_t.DISTANCE_FIGHTING.value to "Distance Fighting",
        HighscoreCategories_t.DROME.value to "Drome Score",
        HighscoreCategories_t.EXPERIENCE.value to "Experience Points",
        HighscoreCategories_t.FISHING.value to "Fishing",
        HighscoreCategories_t.FIST_FIGHTING.value to "Fist Fighting",
        HighscoreCategories_t.GOSHNAR.value to "Goshnar's Taint",
        HighscoreCategories_t.LOYALTY_POINTS.value to "Loyalty Points",
        HighscoreCategories_t.MAGIC_LEVEL.value to "Magic Level",
        HighscoreCategories_t.SHIELDING.value to "Shielding",
        HighscoreCategories_t.SWORD_FIGHTING.value to "Sword Fighting",
    )

    /** `Game::m_highscoreCategories` (game.cpp:723-733). */
    private val highscoreCategories: List<HighscoreCategory> = listOf(
        HighscoreCategory("Experience Points", HighscoreCategories_t.EXPERIENCE.value),
        HighscoreCategory("Fist Fighting", HighscoreCategories_t.FIST_FIGHTING.value),
        HighscoreCategory("Club Fighting", HighscoreCategories_t.CLUB_FIGHTING.value),
        HighscoreCategory("Sword Fighting", HighscoreCategories_t.SWORD_FIGHTING.value),
        HighscoreCategory("Axe Fighting", HighscoreCategories_t.AXE_FIGHTING.value),
        HighscoreCategory("Distance Fighting", HighscoreCategories_t.DISTANCE_FIGHTING.value),
        HighscoreCategory("Shielding", HighscoreCategories_t.SHIELDING.value),
        HighscoreCategory("Fishing", HighscoreCategories_t.FISHING.value),
        HighscoreCategory("Magic Level", HighscoreCategories_t.MAGIC_LEVEL.value),
    )

    /** `Game::tradeItems` (game.hpp:843): the traded item and its offering player id. */
    private val tradeItems = IdentityHashMap<Item, Long>()

    /**
     * Facade storage for `Player::tradeState` / `tradeItem` / `tradePartner`
     * (player.hpp:1762-1768, 1856). The Kotlin [Player] does not carry the trade
     * members yet (PARITY GAP); the handlers below are the only writers.
     */
    private val tradeStates = IdentityHashMap<Player, TradeState_t>()
    private val tradeItemByPlayer = IdentityHashMap<Player, Item?>()
    private val tradePartnerByPlayer = IdentityHashMap<Player, Player?>()

    /**
     * Facade storage for `Player::imbuementTrackerWindowOpen` (player.hpp).
     * The Kotlin [Player] does not carry the member yet (PARITY GAP);
     * [playerRequestInventoryImbuements] is the only writer.
     */
    private val imbuementTrackerWindowOpen = IdentityHashMap<Player, Boolean>()

    /** `Creature::getParent()` equivalent (src/creatures/creature.hpp): the tile each placed creature lives on. */
    private val placedCreatures = IdentityHashMap<Creature, Tile>()

    /** `Item::getParent()` (src/items/item.hpp): the tile each item added through this facade lives on. */
    private val itemParents = IdentityHashMap<Item, Tile>()

    /**
     * `InternalGame::teleportStack()` (game.cpp:230): the thread-confined
     * recursion stack of [internalTeleport]. Canary keys the stack by raw
     * pointer; the port keeps identity semantics with an identity map.
     */
    private val teleportStack = java.util.Collections.newSetFromMap(IdentityHashMap<Thing, Boolean>())

    /**
     * `InternalGame::teleportGuards` (game.cpp:274): per-thing teleport
     * rate-limit state. Confined to the single-threaded dispatcher, so the C++
     * `teleportGuardsMutex` is not needed (AGENTS.md 1.2).
     */
    private val teleportGuards = IdentityHashMap<Thing, TeleportGuardState>()

    /** `InternalGame::TeleportGuardState` (game.cpp:254-266). */
    private class TeleportGuardState {
        var blockedEvents: Int = 0
        var suppressedLogs: Int = 0
        var nextLogMs: Long = 0
        var logDelayMs: Long = TELEPORT_LOG_INITIAL_DELAY_MS
        var windowStartMs: Long = 0
        var windowCount: Int = 0
        var sustainedWindowStartMs: Long = 0
        var sustainedWindowCount: Int = 0
        var blockedUntilMs: Long = 0
        var lastSeenMs: Long = 0
    }

    /**
     * `Game::checkCreatureLists` (`src/game/game.cpp:78`),
     * `EVENT_CREATURECOUNT` think buckets. Canary stores `std::weak_ptr`
     * observers so a removed creature is not kept alive; the port mirrors that
     * with [WeakReference] until `Game::checkCreatures` prunes the buckets.
     */
    private val checkCreatureLists =
        List(EVENT_CREATURECOUNT) { mutableListOf<WeakReference<Creature>>() }

    /** `Game::isDay` (game.hpp:923). */
    private var isDay: Boolean = false

    /** `Game::lightState` / `Game::currentLightState` (game.hpp:929-930). */
    private var lightState: LightState = LightState.LIGHT_STATE_DAY
    private var currentLightState: LightState = lightState

    /** `Game::lightLevel` (game.hpp:931). */
    private var lightLevel: Int = LIGHT_LEVEL_DAY

    /** `Game::lightHour` (game.hpp:932). */
    private var lightHour: Int = SUNRISE + (SUNSET - SUNRISE) / 2

    /** `Game::lightHourDelta` (game.hpp:934). */
    private val lightHourDelta: Int = (LIGHT_DAY_LENGTH * (EVENT_LIGHTINTERVAL_MS / 1000)) / DAY_LENGTH_SECONDS

    /**
     * `Tile::getZones()` seam (`src/items/tile.hpp`) consumed by the
     * `internalPlaceCreature`/`removeCreature` zone transitions. The Kotlin
     * [Tile] does not index zones yet, so the default is empty and a host wires
     * the [org.opentibia.canary.game.zones.Zones] registry here.
     */
    var tileZones: (Position) -> Set<Zone> = { emptySet() }

    /**
     * `g_callbacks().checkCallback(EventCallback_t::zoneBeforeCreatureLeave)`
     * seam (`src/game/game.cpp:12432-12435`). Returns `true` when the creature
     * may leave.
     */
    var checkZoneBeforeCreatureLeave: (Zone, Creature) -> Boolean = { _, _ -> true }

    /**
     * `g_callbacks().checkCallback(EventCallback_t::zoneBeforeCreatureEnter)`
     * seam (`src/game/game.cpp:12438-12443`). Returns `true` when the creature
     * may enter.
     */
    var checkZoneBeforeCreatureEnter: (Zone, Creature) -> Boolean = { _, _ -> true }

    /** `g_callbacks().executeCallback(EventCallback_t::zoneAfterCreatureLeave)` seam (game.cpp:12465-12467). */
    var executeZoneAfterCreatureLeave: (Zone, Creature) -> Unit = { _, _ -> }

    /** `g_callbacks().executeCallback(EventCallback_t::zoneAfterCreatureEnter)` seam (game.cpp:12468-12470). */
    var executeZoneAfterCreatureEnter: (Zone, Creature) -> Unit = { _, _ -> }

    /**
     * `g_globalEvents().getEventMap(GLOBALEVENT_PERIODCHANGE)` dispatch seam
     * (`src/game/game.cpp:9455-9460`); the global-event engine is not ported yet.
     */
    var executePeriodChange: (LightState, LightInfo) -> Unit = { _, _ -> }

    /**
     * `g_events().eventPlayerOnReportRuleViolation` dispatch seam
     * (src/game/game.cpp:10189). Reports are Lua-side dispatch only (AGENTS.md
     * 1.4), so the port exposes the two buckets as injected seams.
     */
    var eventPlayerOnReportRuleViolation: (Player, String, Int, Int, String, String) -> Unit = { _, _, _, _, _, _ -> }

    /** `g_callbacks().executeCallback(playerOnReportRuleViolation, ...)` seam (game.cpp:10190). */
    var callbackPlayerOnReportRuleViolation: (Player, String, Int, Int, String, String) -> Unit = { _, _, _, _, _, _ -> }

    /** `g_events().eventPlayerOnReportBug` dispatch seam (src/game/game.cpp:10199). */
    var eventPlayerOnReportBug: (Player, String, Position, Int) -> Unit = { _, _, _, _ -> }

    /** `g_callbacks().executeCallback(playerOnReportBug, ...)` seam (game.cpp:10200). */
    var callbackPlayerOnReportBug: (Player, String, Position, Int) -> Unit = { _, _, _, _ -> }

    /** `Player::sendCancelMessage` protocol seam (player.cpp:2117); defaults to the player's sink. */
    var sendCancelMessage: (Player, ReturnValue) -> Unit = { player, returnValue ->
        player.sendCancelMessage(returnValue)
    }

    /**
     * `Player::sendTradeItemRequest` protocol seam (player.cpp:2499-2503), used
     * by the trade window handlers.
     */
    var sendTradeItemRequest: (Player, String, Item, Boolean) -> Unit = { _, _, _, _ -> }

    /** `Player::sendTradeClose` protocol seam (player.cpp:2505-2509). */
    var sendTradeClose: (Player) -> Unit = { _ -> }

    /** `Item::onTradeEvent` dispatch seam (src/items/item.hpp:651). */
    var onTradeEvent: (Item, TradeEvents_t, Player) -> Unit = { _, _, _ -> }

    /** `g_events().eventPlayerOnTradeRequest` veto seam (game.cpp:5940). */
    var eventPlayerOnTradeRequest: (Player, Player, Item) -> Boolean = { _, _, _ -> true }

    /** `g_callbacks().checkCallback(playerOnTradeRequest, ...)` veto seam (game.cpp:5944). */
    var callbackPlayerOnTradeRequest: (Player, Player, Item) -> Boolean = { _, _, _ -> true }

    /** `g_events().eventPlayerOnTradeAccept` veto seam (game.cpp:6011). */
    var eventPlayerOnTradeAccept: (Player, Player, Item?, Item?) -> Boolean = { _, _, _, _ -> true }

    /** `g_callbacks().checkCallback(playerOnTradeAccept, ...)` veto seam (game.cpp:6016). */
    var callbackPlayerOnTradeAccept: (Player, Player, Item?, Item?) -> Boolean = { _, _, _, _ -> true }

    /** `g_events().eventPlayerOnLookInTrade` dispatch seam (game.cpp:6149). */
    var eventPlayerOnLookInTrade: (Player, Player, Item, Int) -> Unit = { _, _, _, _ -> }

    /** `g_callbacks().executeCallback(playerOnLookInTrade, ...)` seam (game.cpp:6150). */
    var callbackPlayerOnLookInTrade: (Player, Player, Item, Int) -> Unit = { _, _, _, _ -> }

    /**
     * `Map::canThrowObjectTo` with `SightLine_CheckSightLineAndFloor`
     * (game.cpp:5816, 6001) as an overridable seam. The trade handlers invoke
     * this property explicitly (`this.canThrowObjectTo.invoke(...)`) because the
     * `Game::canThrowObjectTo` method below shares the name.
     */
    var canThrowObjectTo: (Position, Position) -> Boolean = { from, to ->
        map.canThrowObjectTo(from, to, GameMap.SightLine_CheckSightLineAndFloor)
    }

    /** `Player::getShopOwner` accessor (player.hpp:739). */
    var getShopOwner: (Player) -> Npc? = { null }

    /** `Player::closeShopWindow` protocol seam (player.cpp). */
    var closeShopWindow: (Player) -> Unit = { _ -> }

    /** `Player::isUIExhausted` seam (player.cpp); `time` defaults to 0 in Canary. */
    var isUIExhausted: (Player) -> Boolean = { false }

    /** `Player::updateUIExhausted` seam (player.cpp). */
    var updateUIExhausted: (Player) -> Unit = { _ -> }

    /** `g_configManager().getNumber(MAX_CONTAINER)` default (configmanager.cpp:308). */
    var maxContainer: Int = 500

    /** `g_configManager().getBoolean(CYCLOPEDIA_HOUSE_AUCTION)` default (configmanager.cpp:171). */
    var cyclopediaHouseAuction: Boolean = true

    /** `g_configManager().getNumber(DAYS_TO_CLOSE_BID)` default (configmanager.cpp:289). */
    var daysToCloseBid: Int = 7

    /** `g_configManager().getBoolean(TOGGLE_HOUSE_TRANSFER_ON_SERVER_RESTART)` default (configmanager.cpp:154). */
    var toggleHouseTransferOnServerRestart: Boolean = false

    /** `g_configManager().getString(SERVER_MOTD)` default (configmanager.cpp:408). */
    var serverMotd: String = ""

    /** `Player::getBankBalance()` (player.hpp:354) seam. */
    var getBankBalance: (Player) -> Long = { 0L }

    /** `Player::setBankBalance(uint64_t)` (player.hpp:355) seam. */
    var setBankBalance: (Player, Long) -> Unit = { _, _ -> }

    /** `Player::sendResourceBalance(Resource_t, uint64_t)` (player.hpp:1129) protocol seam. */
    var sendResourceBalance: (Player, StoreWindowWriter.Resource, Long) -> Unit = { _, _, _ -> }

    /** `Player::sendHouseAuctionMessage(houseId, type, index, bidSuccess)` (player.hpp:1130) protocol seam. */
    var sendHouseAuctionMessage: (Player, Long, HouseAuctionType, Int, Boolean) -> Unit = { _, _, _, _, _ -> }

    /** `Player::sendCyclopediaHouseList(const HouseMap&)` (player.hpp:1128) protocol seam. */
    var sendCyclopediaHouseList: (Player, Map<Int, House>) -> Unit = { _, _ -> }

    /** `Player::sendCancelMessage(const std::string&)` (player.hpp:1066) protocol seam. */
    var sendCancelMessageText: (Player, String) -> Unit = { player, message ->
        player.sendCancelMessageText(message)
    }

    /** `BedItem::wakeUp(nullptr)` (src/items/bed.cpp:216-255) seam. */
    var wakeUpBed: (BedItem) -> Unit = { _ -> }

    /** `Item::startDecaying()` (src/items/item.cpp:3501-3541) seam. */
    var startDecaying: (Item) -> Unit = { _ -> }

    /** `Player::sendHighscoresNoData()` (player.hpp:1162) protocol seam. */
    var sendHighscoresNoData: (Player) -> Unit = { _ -> }

    /** `Player::sendHighscores(characters, categoryId, vocationId, page, pages, updateTimer)` (player.hpp:1163) protocol seam. */
    var sendHighscores: (
        Player,
        List<HighscoresWindowSerializer.HighscoreCharacterEntry>,
        Int,
        Long,
        Int,
        Int,
        Long,
    ) -> Unit = { _, _, _, _, _, _, _ -> }

    // --- movement/combat/targeting seams (game.cpp:1622-7790) -----------------

    /** `Player::sendCreatureTurn(creature)` (player.cpp:12092) protocol seam used by [internalCreatureTurn]. */
    var sendCreatureTurn: (Player, Creature) -> Unit = { _, _ -> }

    /** `Player::sendChangeSpeed(creature, stepSpeed)` protocol seam used by the speed helpers. */
    var sendChangeSpeed: (Player, Creature, Int) -> Unit = { player, creature, stepSpeed ->
        player.sendChangeSpeed(creature, stepSpeed)
    }

    /** `Player::resetLoginProtection()` (player.cpp) seam used by the walk/target handlers. */
    var resetLoginProtection: (Player) -> Unit = { _ -> }

    /** `Player::resetIdleTime()` (player.cpp) seam used by the walk/turn handlers. */
    var resetIdleTime: (Player) -> Unit = { _ -> }

    /**
     * `Player::setNextWalkTask(task)` (player.cpp:2996) seam bound to the host
     * player. The handlers pass `null` to clear the queued walk task, mirroring
     * the C++ `nullptr` call.
     */
    var setNextWalkTask: (Player, Any?) -> Unit = { player, task -> player.setNextWalkTask(task as? PlayerTask) }

    /**
     * `Player::setNextWalkActionTask(task)` (player.cpp:2983) seam bound to the
     * host player. The handlers pass `null` to clear the queued walk action,
     * mirroring the C++ `nullptr` call.
     */
    var setNextWalkActionTask: (Player, Any?) -> Unit = { player, task -> player.setNextWalkActionTask(task as? PlayerTask) }

    /** `Player::cancelPush()` (player.cpp:7230) seam used by the move/turn handlers. */
    var cancelPush: (Player) -> Unit = { _ -> }

    /**
     * `Creature::startAutoWalk(listDir, ignoreConditions, startPolicy)`
     * (creature.cpp:326-340 / creature.hpp:397), bound to the host player. The
     * `ignoreConditions` flag preserves the C++ call sites' boolean; the port's
     * walk queue owns the start policy.
     */
    var startAutoWalk: (Player, List<Direction>, Boolean) -> Unit = { player, listDir, ignoreConditions ->
        player.startAutoWalk(listDir, ignoreConditions)
    }

    /**
     * `Creature::updateCreatureWalk()` (creature.hpp:412): the async
     * `goToFollowCreature_async` barrier whose dispatcher batch invokes the
     * virtual `goToFollowCreature`; the host player owns the whole pipeline.
     */
    var updateCreatureWalk: (Player) -> Unit = { player -> player.updateCreatureWalk() }

    // --- channel handler seams (game.cpp:4324-4441) ----------------------------

    /** `Player::sendCreatePrivateChannel(channelId, name)` (player.cpp) protocol seam. */
    var sendCreatePrivateChannel: (Player, Int, String) -> Unit = { _, _, _ -> }

    /** `Player::sendChannelsDialog()` (player.cpp) protocol seam. */
    var sendChannelsDialog: (Player) -> Unit = { _ -> }

    /** `Player::sendChannel(channelId, name, users, invitedUsers)` (player.cpp) protocol seam. */
    var sendChannel: (Player, Int, String, Map<Long, Player>?, Map<Long, Player>?) -> Unit = { _, _, _, _, _ -> }

    /** `Player::sendOpenPrivateChannel(receiver)` (player.cpp) protocol seam. */
    var sendOpenPrivateChannel: (Player, String) -> Unit = { _, _ -> }

    /** `IOLoginData::formatPlayerName(name)` (src/io/iologindata.cpp) seam; `null` rejects the name. */
    var formatPlayerName: (String) -> String? = { null }

    /** `Player::receivePing()` (player.cpp:6310) protocol seam. */
    var receivePing: (Player) -> Unit = { _ -> }

    /** `Player::sendPingBack()` (player.cpp) protocol seam. */
    var sendPingBack: (Player) -> Unit = { _ -> }

    /** `Player::setPvpMode(mode)` (player.cpp:6226) seam gated by [expertPvpEnabled]. */
    var setPvpMode: (Player, PvpMode_t) -> Unit = { _, _ -> }

    /**
     * `Combat::canTargetCreature(player, target)` (combat.cpp:321-374) seam used
     * by [playerSetAttackedCreature]. The default allows the target because the
     * Kotlin `Player`/`Creature` do not adapt to `CombatPlayer`/`CombatActor`
     * yet (PARITY GAP); hosts inject the ported validation.
     */
    var canTargetCreature: (Player, Creature) -> ReturnValue = { _, _ -> ReturnValue.NOERROR }

    /**
     * `Creature::isDirectionLocked()` (creature.hpp) seam used by
     * [internalCreatureTurn]; the Kotlin `Creature` does not model the flag yet.
     */
    var isDirectionLocked: (Creature) -> Boolean = { false }

    /** `g_events().eventPlayerOnTurn` veto seam (game.cpp:7193). */
    var eventPlayerOnTurn: (Player, Direction) -> Boolean = { _, _ -> true }

    /** `g_callbacks().checkCallback(playerOnTurn, ...)` veto seam (game.cpp:7197). */
    var callbackPlayerOnTurn: (Player, Direction) -> Boolean = { _, _ -> true }

    /** `g_events().eventPlayerOnLook` dispatch seam (game.cpp:6385). */
    var eventPlayerOnLook: (Player, Position, Thing, Int, Int) -> Unit = { _, _, _, _, _ -> }

    /** `g_callbacks().executeCallback(playerOnLook, ...)` seam (game.cpp:6386). */
    var callbackPlayerOnLook: (Player, Thing, Position, Int) -> Unit = { _, _, _, _ -> }

    /** `g_events().eventPlayerOnLookInBattleList` dispatch seam (game.cpp:6422). */
    var eventPlayerOnLookInBattleList: (Player, Creature, Int) -> Unit = { _, _, _ -> }

    /** `g_callbacks().executeCallback(playerOnLookInBattleList, ...)` seam (game.cpp:6423). */
    var callbackPlayerOnLookInBattleList: (Player, Creature, Int) -> Unit = { _, _, _ -> }

    /** `ExpertPvp::isEnabled()` gate of [playerSetFightModes] (game.cpp:7052). */
    var expertPvpEnabled: Boolean = false

    // --- quest/forge/bosstiary seams (game.cpp:7391-11256) --------------------

    /** `g_events().eventPlayerOnRequestQuestLog` dispatch seam (game.cpp:7396). */
    var eventPlayerOnRequestQuestLog: (Player) -> Unit = { _ -> }

    /** `g_callbacks().executeCallback(playerOnRequestQuestLog, ...)` seam (game.cpp:7397). */
    var callbackPlayerOnRequestQuestLog: (Player) -> Unit = { _ -> }

    /** `g_events().eventPlayerOnRequestQuestLine` dispatch seam (game.cpp:7406). */
    var eventPlayerOnRequestQuestLine: (Player, Int) -> Unit = { _, _ -> }

    /** `g_callbacks().executeCallback(playerOnRequestQuestLine, ...)` seam (game.cpp:7407). */
    var callbackPlayerOnRequestQuestLine: (Player, Int) -> Unit = { _, _ -> }

    /**
     * `Game::removeMoney(player, money, flags, useBalance)` seam (game.cpp:3299)
     * used by [playerBosstiarySlot]. The full coin-collection body over
     * `Cylinder` containers is not ported (PARITY GAP), so hosts inject it.
     */
    var removeMoney: (Player, Long, Int, Boolean) -> Boolean = { _, _, _, _ -> false }

    /** `Player::getSlotBossId(slotId)` (player.cpp:12706) seam; the Kotlin `Player` does not model the boss slots yet. */
    var getSlotBossId: (Player, Int) -> Long = { _, _ -> 0 }

    /** `Player::setSlotBossId(slotId, bossId)` (player.cpp:12695) seam. */
    var setSlotBossId: (Player, Int, Long) -> Unit = { _, _, _ -> }

    /** `Player::getRemoveTimes()` (player.cpp:12722) seam. */
    var getRemoveTimes: (Player) -> Int = { 1 }

    /** `Player::addRemoveTime()` (player.cpp:12714) seam. */
    var addRemoveTime: (Player) -> Unit = { _ -> }

    /** `Player::forgeResourceConversion(actionType)` (player.cpp:11709) seam. */
    var forgeResourceConversion: (Player, ForgeAction_t) -> Unit = { _, _ -> }

    /** `Player::forgeTransferItemTier(actionType, donorItemId, tier, receiveItemId, convergence)` (player.cpp:11542) seam. */
    var forgeTransferItemTier: (Player, ForgeAction_t, Int, Int, Int, Boolean) -> Unit = { _, _, _, _, _, _ -> }

    /** `Player::forgeHistory(page)` (player.cpp:12794) seam. */
    var forgeHistory: (Player, Int) -> Unit = { _, _ -> }

    /**
     * `g_kv().scoped("eventscheduler")->get("forge-chance")` (game.cpp:11194)
     * seam: the event-scheduler success-rate modifier, `null` when unset.
     */
    var getForgeChance: () -> Int? = { null }

    /** `Player::forgeFuseItems(actionType, firstItemId, tier, secondItemId, success, reduceTierLoss, convergence, bonus, coreCount)` (player.cpp:11246) seam. */
    var forgeFuseItems: (Player, ForgeAction_t, Int, Int, Int, Boolean, Boolean, Boolean, Int, Int) -> Unit =
        { _, _, _, _, _, _, _, _, _, _ -> }

    // --- forge monster event seams (game.cpp:11882-12229) ----------------------

    /**
     * `g_configManager()` `FORGE_*` values (configmanager.cpp:258-273) behind the
     * fiendish/influenced limits and the fiendish interval math.
     */
    var featureFlags: FeatureFlags = FeatureFlags()

    /** `g_dispatcher().scheduleEvent(delay, task, context, lane)` (game.cpp:12038) seam returning the event id. */
    var scheduleEvent: (Long, () -> Unit) -> Long = { _, _ -> 0 }

    /** `g_dispatcher().stopEvent(eventId)` (game.cpp:12204) seam. */
    var stopEvent: (Long) -> Unit = { _ -> }

    /** `getTimeNow()` (src/utils/tools.hpp) seam for the fiendish change-time stamp. */
    var getTimeNow: () -> Long = { System.currentTimeMillis() / 1000 }

    /** `normal_random(min, max)` (src/utils/tools.cpp:13) seam for the influenced-monster draw. */
    var normalRandom: (Int, Int) -> Int = { minNumber, _ -> minNumber }

    /** `Monster::getHealthMultiplier()` (monsters.cpp:332) behind `clearFiendishStatus`; the `RATE_*_HEALTH` config is host-injected. */
    var getHealthMultiplier: () -> Double = { 1.0 }

    /**
     * `Creature::registerCreatureEvent(eventName)` (`ForgeSystemMonster`
     * registration, monster.cpp:3886). The event registry is Lua-side
     * (AGENTS.md 1.4), so the dispatch stays an injected seam.
     */
    var registerCreatureEvent: (Monster, String) -> Unit = { _, _ -> }

    /** `Monster::getForgeStack()` (monster.cpp:3903), resolved through [monsterHost]'s [MonsterTypeState]. */
    var getForgeStack: (Monster) -> Int = { monster ->
        monsterHost?.typeStateOf(monster.id)?.let(MonsterForge::getForgeStack) ?: 0
    }

    /** `Monster::canBeForgeMonster()` (monster.cpp:3891), resolved through [monsterHost]'s [MonsterTypeState]. */
    var canBeForgeMonster: (Monster) -> Boolean = { monster ->
        monsterHost?.typeStateOf(monster.id)?.let { state ->
            MonsterForge.canBeForgeMonster(state, monster.isSummon())
        } ?: false
    }

    /** `Monster::setMonsterForgeClassification(classification)` (monster.cpp:3916), resolved through [monsterHost]'s [MonsterTypeState]. */
    var setMonsterForgeClassification: (Monster, ForgeClassification) -> Unit = { monster, classification ->
        monsterHost?.typeStateOf(monster.id)?.let { MonsterForge.setMonsterForgeClassification(it, classification) }
    }

    /**
     * `Monster::configureForgeSystem(stack)` (monster.cpp:3866), resolved
     * through [monsterHost]'s [MonsterTypeState] and [MonsterForge]; the icon
     * and creature-update side effects run through [updateCreatureIcon] and
     * [sendUpdateCreature].
     */
    var configureForgeSystem: (Monster, Int) -> Unit = { monster, stack ->
        monsterHost?.typeStateOf(monster.id)?.let { state ->
            MonsterForge.configureForgeSystem(
                state = state,
                monster = monster,
                stack = stack,
                isSummon = monster.isSummon(),
                normalRandom = normalRandom,
                updateCreatureIcon = { updateCreatureIcon(monster) },
                registerCreatureEvent = { event -> registerCreatureEvent(monster, event) },
                sendUpdateCreature = { sendUpdateCreature(monster) },
            )
        }
    }

    /** `Monster::setTimeToChangeFiendish(time)` (monster.cpp:3920), resolved through [monsterHost]'s [MonsterTypeState]. */
    var setTimeToChangeFiendish: (Monster, Long) -> Unit = { monster, time ->
        monsterHost?.typeStateOf(monster.id)?.let { MonsterForge.setTimeToChangeFiendish(it, time) }
    }

    /** `Monster::clearFiendishStatus()` (monster.cpp:3932), resolved through [monsterHost]'s [MonsterTypeState]. */
    var clearFiendishStatus: (Monster) -> Unit = { monster ->
        monsterHost?.typeStateOf(monster.id)?.let { state ->
            MonsterForge.clearFiendishStatus(
                state = state,
                monster = monster,
                healthMultiplier = getHealthMultiplier(),
                updateCreatureIcon = { updateCreatureIcon(monster) },
                sendUpdateCreature = { sendUpdateCreature(monster) },
            )
        }
    }

    // --- wheel combat seams (game.cpp:8264-8287) -------------------------------

    // The combat state-change pipeline (`combatChangeHealth`, `combatChangeMana`,
    // the leech helpers and their protocol/event seams) is defined below, next to
    // the wheel application helpers it drives.

    // --- show-off socket seams (game.cpp:5227) ---------------------------------

    /** `Player::canWear(lookType, addons)` (player.cpp:6783) seam. */
    var playerCanWear: (Player, Int, Int) -> Boolean = { _, _, _ -> false }

    /** `Outfits::getInstance().getOutfitByLookType(player, lookType)` (outfit.cpp:85) seam. */
    var getOutfitByLookType: (Player, Int) -> OutfitDefinition? = { _, _ -> null }

    /** `Mounts::getMountByClientID(clientId)` (mounts.cpp) seam. */
    var getMountByClientID: (Int) -> Mount? = { null }

    /** `PlayerWheel::sendOpenWheelWindow(ownerId)` protocol seam (game.cpp:11533). */
    var sendOpenWheelWindow: (Player, Long) -> Unit = { _, _ -> }

    /**
     * `Player` view backing the ported [WheelApplication] economy mutations
     * (`WheelPlayer` in `WheelEconomy.kt`). Unwired hosts keep the null-object
     * behavior, matching the packet router's current wiring (PARITY GAP).
     */
    var wheelPlayer: (Player) -> WheelPlayer = { WheelPlayer.UNWIRED }

    // --- NPC greet / podium seams (game.cpp:10236-11480) ----------------------

    /** `Player::canSpeakWithHireling(speechBubble)` (player.cpp:13020) seam. */
    var canSpeakWithHireling: (Player, Int) -> Boolean = { _, _ -> true }

    /** `Npc::getSpeechBubble()` (npc.cpp) seam; the Kotlin [Npc] exposes the bubble through its type. */
    var getNpcSpeechBubble: (Npc) -> Int = { npc -> npc.npcType?.info?.speechBubble ?: NpcInfo.SPEECHBUBBLE_NORMAL }

    /**
     * `Game::internalCreatureSay(creature, type, text, ghostMode, spectatorsPtr)`
     * (src/game/game.cpp:7618-7657). The default is the ported C++ body: empty
     * text dispatches nothing; a missing or empty [spectators] list is resolved
     * from the map (yells span ±18x/±14y across floors, every other type the
     * same-floor ±8x/±6y client viewport); every player spectator is sent
     * through [sendCreatureSayTo] unless [ghostMode] hides the speaker from
     * them; then every spectator receives [onCreatureSayEvent]
     * (`Creature::onCreatureSay`), which reaches `Monster::onCreatureSay`/
     * `Npc::onCreatureSay`.
     *
     * Hosts may rebind the seam (the `ChatManager` speech machinery and tests
     * capture it); the default is the production fanout. The C++ optional `pos`
     * pointer is folded into the speaker position because the Kotlin senders
     * carry no separate position.
     */
    var internalCreatureSay: (Creature, SpeakClasses, String, Boolean, List<Creature>) -> Unit =
        { speaker, type, text, ghostMode, spectators ->
            if (text.isNotEmpty()) {
                val resolved = if (spectators.isNotEmpty()) {
                    spectators
                } else if (type == SpeakClasses.TALKTYPE_YELL || type == SpeakClasses.TALKTYPE_MONSTER_YELL) {
                    // `spectators.find<Creature>(*pos, true, (MAP_MAX_CLIENT_VIEW_PORT_X + 1) * 2, ...)`
                    // (game.cpp:7635-7639).
                    Spectators(map)
                        .find<Creature>(
                            speaker.position,
                            multifloor = true,
                            minRangeX = (Creature.MAP_MAX_CLIENT_VIEW_PORT_X + 1) * 2,
                            maxRangeX = (Creature.MAP_MAX_CLIENT_VIEW_PORT_X + 1) * 2,
                            minRangeY = (Creature.MAP_MAX_CLIENT_VIEW_PORT_Y + 1) * 2,
                            maxRangeY = (Creature.MAP_MAX_CLIENT_VIEW_PORT_Y + 1) * 2,
                        )
                        .data()
                } else {
                    // `spectators.find<Creature>(*pos, false, MAP_MAX_CLIENT_VIEW_PORT_X, ...)`
                    // (game.cpp:7631-7633).
                    Spectators(map)
                        .find<Creature>(
                            speaker.position,
                            multifloor = false,
                            minRangeX = Creature.MAP_MAX_CLIENT_VIEW_PORT_X,
                            maxRangeX = Creature.MAP_MAX_CLIENT_VIEW_PORT_X,
                            minRangeY = Creature.MAP_MAX_CLIENT_VIEW_PORT_Y,
                            maxRangeY = Creature.MAP_MAX_CLIENT_VIEW_PORT_Y,
                        )
                        .data()
                }

                // Send to client (`game.cpp:7643-7650`).
                for (spectator in resolved) {
                    val spectatorPlayer = spectator as? Player ?: continue
                    if (!ghostMode || spectatorPlayer.canSeeCreature(speaker)) {
                        sendCreatureSayTo(spectatorPlayer, speaker, type, text)
                    }
                }

                // Event method (`game.cpp:7652-7655`).
                for (spectator in resolved) {
                    onCreatureSayEvent(spectator, speaker, type, text)
                }
            }
        }

    /** `g_configManager().getBoolean(ONLY_INVITED_CAN_MOVE_HOUSE_ITEMS)` default (configmanager.cpp). */
    var onlyInvitedCanMoveHouseItems: Boolean = false

    /** `InternalGame::playerCanUseItemOnHouseTile(player, item)` (game.cpp) seam. */
    var playerCanUseItemOnHouseTile: (Player, Item) -> Boolean = { _, _ -> true }

    /** `Player::sendUpdateTileItem(tile, pos, item)` protocol seam (game.cpp:11385). */
    var sendUpdateTileItem: (Player, Tile, Position, Item) -> Unit = { _, _, _, _ -> }

    /** `g_monsters().getMonsterTypeByRaceId(raceId, isBoss)` (monsters.cpp:375) seam. */
    var getMonsterTypeByRaceId: (Int, Boolean) -> MonsterType? = { _, _ -> null }

    /** `Creature::getPathTo(toPosition, listDir, 0, 1, true, false/true)` (creature.cpp:449) seam. */
    var getPathTo: (Player, Position, Int, Int, Boolean, Boolean) -> List<Direction>? =
        { _, _, _, _, _, _ -> null }

    /** `Game::queuePlayerAutoWalk(playerId, listDir)` (game.cpp) seam. */
    var queuePlayerAutoWalk: (Player, List<Direction>) -> Boolean = { _, _ -> false }

    /** `Game::playerBrowseField(playerId, pos)` (game.cpp:5104) task seam used by the podium walk tasks. */
    var playerBrowseFieldHandler: (Long, Position) -> Unit = { _, _ -> }

    // --- movement handler seams (game.cpp:1711-2420) ---------------------------

    /** `g_configManager().getNumber(PUSH_DELAY)` default 1000 (configmanager.cpp:337). */
    var pushDelay: Int = 1000

    /** `g_configManager().getNumber(ACTIONS_DELAY_INTERVAL)` default 200 (configmanager.cpp:226). */
    var actionsDelayInterval: Int = 200

    /** `Player::pushEvent(bool)` (player.cpp:7225) seam used by [playerMoveCreature]. */
    var playerPushEvent: (Player, Boolean) -> Unit = { _, _ -> }

    /** `Player::sendItemInspection(itemId, itemCount, item, cyclopedia)` protocol seam (game.cpp:1635, 1652). */
    var sendItemInspection: (Player, Int, Int, Item?, Boolean) -> Unit = { _, _, _, _, _ -> }

    /** `g_events().eventPlayerOnMoveCreature(player, creature, fromPos, toPos)` veto seam (game.cpp:1866). */
    var eventPlayerOnMoveCreature: (Player, Creature, Position, Position) -> Boolean = { _, _, _, _ -> true }

    /** `g_callbacks().checkCallback(playerOnMoveCreature, ...)` veto seam (game.cpp:1870). */
    var callbackPlayerOnMoveCreature: (Player, Creature, Position, Position) -> Boolean = { _, _, _, _ -> true }

    /** `g_events().eventPlayerOnMoveItem(player, item, count, fromPos, toPos, from, to)` veto seam (game.cpp:2237). */
    var eventPlayerOnMoveItem:
        (Player, Item, Int, Position, Position, Cylinder, Cylinder) -> Boolean = { _, _, _, _, _, _, _ ->
            true
        }

    /** `g_callbacks().checkCallback(playerOnMoveItem, ...)` veto seam (game.cpp:2233). */
    var callbackPlayerOnMoveItem:
        (Player, Item, Int, Position, Position, Cylinder, Cylinder) -> Boolean = { _, _, _, _, _, _, _ ->
            true
        }

    /** `g_events().eventPlayerOnItemMoved(...)` notification seam (game.cpp:2290). */
    var eventPlayerOnItemMoved:
        (Player, Item, Int, Position, Position, Cylinder, Cylinder) -> Unit = { _, _, _, _, _, _, _ ->
        }

    /** `g_callbacks().executeCallback(playerOnItemMoved, ...)` notification seam (game.cpp:2291). */
    var callbackPlayerOnItemMoved:
        (Player, Item, Int, Position, Position, Cylinder, Cylinder) -> Unit = { _, _, _, _, _, _, _ ->
        }

    /** `Player::sendPodiumWindow(item, pos, itemId, stackPos)` protocol seam (game.cpp:5218). */
    var sendPodiumWindow: (Player, Item, Position, Int, Int) -> Unit = { _, _, _, _, _ -> }

    /** `Player::sendMonsterPodiumWindow(item, pos, itemId, stackPos)` protocol seam (game.cpp:5213). */
    var sendMonsterPodiumWindow: (Player, Item, Position, Int, Int) -> Unit = { _, _, _, _, _ -> }

    /**
     * `Item::getDescription(itemType, 1, nullptr, count)` renderer seam
     * (`Game::playerLookInShop`, src/game/game.cpp:6353): the default composes
     * the ported renderer with the booted `Item::items` registry, the
     * `g_vocations()` registry and `Game::getObjectCategory`. The `g_spells()`
     * registry has no `Game`-level composition (a host can override the seam
     * with a registry-aware renderer).
     */
    var getItemDescription: (ItemType, Int) -> String = { itemType, count ->
        Item.getDescription(
            itemType,
            1,
            null,
            count,
            items = itemTypes,
            vocations = vocations,
            objectCategory = { item -> getObjectCategory(item) },
        )
    }

    /** `g_events().eventPlayerOnLookInShop(player, itemType, count)` veto seam (game.cpp:6341). */
    var eventPlayerOnLookInShop: (Player, ItemType, Int) -> Boolean = { _, _, _ -> true }

    /** `g_callbacks().checkCallback(playerOnLookInShop, ...)` veto seam (game.cpp:6345). */
    var callbackPlayerOnLookInShop: (Player, ItemType, Int) -> Boolean = { _, _, _ -> true }

    /** `Player::setNextActionTask(task)` (player.cpp:3031) seam used by the retry handlers. */
    var setNextActionTask: (Player, PlayerTask?) -> Unit = { player, task -> player.setNextActionTask(task) }

    /**
     * `Player::getLootPouch()` (player.cpp) seam used by [playerEquipItem]. The
     * Kotlin `Player` does not expose the loot-pouch container (PARITY GAP), so
     * unwired hosts resolve to `null` exactly like a player without a pouch.
     */
    var getPlayerLootPouch: (Player) -> Container? = { null }

    // --- remaining player handler seams (game.cpp:4310-11487) -----------------

    /** `Player::imbuingItem` accessor (player.hpp:1761) seam; the Kotlin `Player` only exposes `hasImbuingItem`/`setImbuingItem`. */
    var getImbuingItem: (Player) -> Item? = { null }

    /**
     * `item->getTopParent() != player` guard of [playerApplyImbuement]
     * (game.cpp:7137). The Kotlin `Player` is not a `Cylinder`, so the inventory
     * parent chain cannot be resolved (PARITY GAP); the default assumes the
     * imbuing item is carried by the player.
     */
    var isImbuingItemCarriedByPlayer: (Player, Item) -> Boolean = { _, _ -> true }

    /** `Player::createScrollImbuement(imbuement)` (player.cpp:2602) seam. */
    var createScrollImbuement: (Player, Imbuement) -> Unit = { _, _ -> }

    /** `Player::onApplyImbuement(imbuement, item, slot)` (player.cpp:2711) seam. */
    var onApplyImbuement: (Player, Imbuement, Item, Int) -> Unit = { _, _, _, _ -> }

    /** `Player::sendImbuementResult(message)` protocol seam (game.cpp:7145). */
    var sendImbuementResult: (Player, String) -> Unit = { _, _ -> }

    /** `Player::onClearImbuement(item, slot)` (player.cpp:2798) seam. */
    var onClearImbuement: (Player, Item, Int) -> Unit = { _, _, _ -> }

    /** `Player::sendInventoryImbuements(itemsWithImbueSlotMap)` protocol seam (game.cpp:11515). */
    var sendInventoryImbuements: (Player, Map<Slots_t, Item>) -> Unit = { _, _ -> }

    /** `Player::toggleMount(mount)` (player.cpp) seam. */
    var toggleMount: (Player, Boolean) -> Unit = { _, _ -> }

    /** `Player::sendOutfitWindow()` (player.cpp) protocol seam used by [playerRequestOutfit]. */
    var sendOutfitWindow: (Player) -> Unit = { _ -> }

    /** `Player::sendPrivateMessage(speaker, type, text)` protocol seam used by [playerBroadcastMessage]. */
    var sendPrivateMessage: (Player, Player, SpeakClasses, String) -> Unit = { _, _, _, _ -> }

    /** `g_configManager().getBoolean(ALLOW_CHANGEOUTFIT)` default (configmanager.cpp:83). */
    var allowChangeOutfit: Boolean = true

    /** `Game::transferHouseItemsToPlayer` (game.hpp:905): house id -> owner GUID. */
    private val transferHouseItemsToPlayer = sortedMapOf<Int, Long>()

    /**
     * Facade storage for `Player::editHouse` / `Player::windowTextId` /
     * `Player::editListId` (player.hpp:1765, 1798-1799). The Kotlin [Player]
     * does not carry the edit-house members yet (PARITY GAP); [setEditHouse] and
     * [getEditHouse] are the only writers.
     */
    private class EditHouseState {
        var house: House? = null
        var windowTextId: Long = 0
        var editListId: Int = 0
    }

    private val editHouseByPlayer = IdentityHashMap<Player, EditHouseState>()

    /** `Game::queryCache` (game.hpp:860): generated highscore queries by cache key. */
    private val queryCache = linkedMapOf<String, QueryHighscoreCacheEntry>()

    /** `Game::highscoreCache` (game.hpp:861): served highscore frames by cache key. */
    private val highscoreCache = linkedMapOf<String, HighscoreCacheEntry>()

    /** `QueryHighscoreCacheEntry` (game.hpp:76-81). */
    private data class QueryHighscoreCacheEntry(
        val query: String,
        val page: Long,
        val entriesPerPage: Int,
    )

    /** `HighscoreCacheEntry` (game.hpp:83-88); [pages] is the C++ `entriesPerPage` field. */
    private data class HighscoreCacheEntry(
        val characters: List<HighscoresWindowSerializer.HighscoreCharacterEntry>,
        val page: Long,
        val pages: Int,
        val timestamp: Long,
    )

    /**
     * Facade storage for the `Item` custom attribute `"unWrapId"`
     * (`src/items/functions/item/custom_attribute.cpp`); the Kotlin [Item] has
     * no custom-attribute store (PARITY GAP). Written by [wrapItem] and read by
     * [unwrapItem].
     */
    private val itemUnWrapIds = IdentityHashMap<Item, Int>()

    /**
     * Facade storage for the string-keyed `Item` custom attributes
     * (`Item::getCustomAttribute`/`setCustomAttribute`, custom_attribute.cpp);
     * the Kotlin [Item] has no custom-attribute store (PARITY GAP). Written by
     * the podium handlers.
     */
    private val itemCustomAttributes = IdentityHashMap<Item, MutableMap<String, Long>>()

    /** `Game::motdNum` (game.hpp:942). */
    private var motdNum: Long = 0

    /** `Game::motdHash` (game.hpp:941). */
    private var motdHash: String = ""

    /**
     * `Game::itemsPriceMap` (game.hpp:835) filled by `Game::loadItemsPrice`
     * (game.cpp). The database loader is not ported, so hosts seed this map.
     */
    var itemsPriceMap: Map<Int, Map<Int, Long>> = emptyMap()

    /** `Player::sendMarketBrowseItem(itemId, buyOffers, sellOffers, tier)` protocol seam (game.cpp:10302). */
    var sendMarketBrowseItem: (Player, Int, List<MarketOffer>, List<MarketOffer>, Int) -> Unit =
        { _, _, _, _, _ -> }

    /** `Player::sendMarketDetail(itemId, tier)` protocol seam (game.cpp:10303). */
    var sendMarketDetail: (Player, Int, Int) -> Unit = { _, _, _ -> }

    /** `Player::sendMarketBrowseOwnOffers(buyOffers, sellOffers)` protocol seam (game.cpp:10318). */
    var sendMarketBrowseOwnOffers: (Player, List<MarketOffer>, List<MarketOffer>) -> Unit = { _, _, _ -> }

    /** `Player::sendMarketBrowseOwnHistory(buyOffers, sellOffers)` protocol seam (game.cpp:10333). */
    var sendMarketBrowseOwnHistory: (Player, List<HistoryMarketOffer>, List<HistoryMarketOffer>) -> Unit =
        { _, _, _ -> }

    // --- market offer seams (game.cpp:1037-11112) -----------------------------

    /** `g_configManager().getBoolean(MARKET_PREMIUM)` default (configmanager.cpp:116). */
    var marketPremium: Boolean = true

    /** `g_configManager().getNumber(MAX_MARKET_OFFERS_AT_A_TIME_PER_PLAYER)` default (configmanager.cpp:314). */
    var maxMarketOffersAtATimePerPlayer: Int = 100

    /** `g_configManager().getNumber(MARKET_OFFER_DURATION)` default (configmanager.cpp:237); seconds. */
    var marketOfferDuration: Long = 30L * 24L * 60L * 60L

    /** `g_configManager().getNumber(FORGE_MAX_ITEM_TIER)` default (configmanager.cpp:268). */
    var forgeMaxItemTier: Int = 10

    /** `Player::getMoney()` (player.cpp) seam: the carried inventory worth. */
    var getMoney: (Player) -> Long = { 0L }

    /** `Player::sendTextMessage(type, text)` protocol seam (player.hpp). */
    var sendTextMessage: (Player, MessageClass, String) -> Unit = { player, type, text ->
        player.sendTextMessageTyped(type, text)
    }

    /** `Player::sendMarketEnter(depotId)` protocol seam (game.cpp:10785, 10852). */
    var sendMarketEnter: (Player, Int) -> Unit = { _, _ -> }

    /** `Player::sendMarketCancelOffer(offer)` protocol seam (game.cpp:10873). */
    var sendMarketCancelOffer: (Player, MarketOfferEx) -> Unit = { _, _ -> }

    /** `Player::sendMarketAcceptOffer(offer)` protocol seam (game.cpp:11120). */
    var sendMarketAcceptOffer: (Player, MarketOfferEx) -> Unit = { _, _ -> }

    /** `g_saveManager().savePlayer(player)` seam (game.cpp:10791). */
    var savePlayer: (Player) -> Unit = { _ -> }

    /** `Player::onReceiveMail()` (player.cpp) seam used by the accepted sell offer (game.cpp:11079). */
    var onReceiveMail: (Player) -> Unit = { _ -> }

    /** `Player::isOffline()` (player.hpp:1179) seam; the Kotlin player carries no client. */
    var isOffline: (Player) -> Boolean = { false }

    /** `Player::getStashItemCount(wareId)` (player.cpp:12798) seam. */
    var getStashItemCount: (Player, Int) -> Int = { _, _ -> 0 }

    /** `Player::withdrawItem(wareId, count)` (player.cpp:12852) seam. */
    var withdrawItem: (Player, Int, Int) -> Boolean = { _, _, _ -> false }

    /**
     * `Player::getLockerItemsAndCountById(depotLocker, tier, itemId)`
     * (player.cpp) seam; the pair is `(itemVector, totalCount)`.
     */
    var getLockerItemsAndCountById: (Player, DepotLocker, Int, Int) -> Pair<List<Item>, Int> =
        { _, _, _, _ -> emptyList<Item>() to 0 }

    /** `Player::updateState()` (player.cpp) seam, the sell-offer item removal tail. */
    var updateState: (Player) -> Unit = { _ -> }

    /**
     * `Player::addItemBatchToPaginedContainer(container, itemId, count, actuallyAdded, flags, tier, isMarket)`
     * (player.hpp) seam. The pair is `(ReturnValue, actuallyAdded)`; the default
     * refuses every insertion because no paginated-container engine is ported
     * (PARITY GAP).
     */
    var addItemBatchToPaginedContainer: (Player, Inbox, Int, Int, Int, Int, Boolean) -> Pair<ReturnValue, Int> =
        { _, _, _, _, _, _, _ -> ReturnValue.DEPOTISFULL to 0 }

    /** `Player::removeItem(item, count)` (player.cpp:10251) seam; the default only removes inventory items. */
    var removeItem: (Player, Item, Int) -> ReturnValue = { player, item, count -> player.removeItem(item, count) }

    /** `Player::getAllSaleItemIdAndCount(inventoryMap)` (player.cpp) seam. */
    var getAllSaleItemIdAndCount: (Player, MutableMap<Int, Int>) -> Map<Int, Int> = { _, map -> map }

    /** `Player::sendSaleItemList(saleItemMap)` protocol seam (game.cpp:11600). */
    var sendSaleItemList: (Player, Map<Int, Int>) -> Unit = { _, _ -> }

    /** `Player::setScheduledSaleUpdate(false)` (player.hpp) seam (game.cpp:11601). */
    var setScheduledSaleUpdate: (Player, Boolean) -> Unit = { _, _ -> }

    /**
     * `Game::addPlayer` (src/game/game.cpp:11624) plus the login composition the
     * eliminated globals require: the `MAX_MESSAGEBUFFER` host value from the
     * injected `g_configManager()` (configmanager.cpp:315) and the shared
     * creature async buckets / `g_dispatcher()` binding every non-monster
     * batch is admitted through (creature.cpp:2031-2054, 2186-2193).
     */
    fun addPlayer(player: Player) {
        players[player.id] = player
        if (player.name.isNotEmpty()) {
            wildcardTree.insert(player.name.lowercase())
        }

        configManager?.let { config ->
            ConfigKeys.byName("MAX_MESSAGEBUFFER")?.let { key ->
                player.maxMessageBuffer = config.getNumber(key, key.defaultValue as Int)
            }
        }
        bindCreatureAsyncTaskHost(player)
    }

    /**
     * Attaches the world dispatcher and routes the async batches of every
     * non-monster creature through the world-shared [asyncTaskBuckets]
     * (`Creature::sendAsyncTasks` -> `Creature::enqueueAsyncTask`,
     * creature.cpp:2186-2193, 2031-2054). Monsters keep the binding the
     * `MonsterPipelinesHost` installs (it carries the pipeline's
     * player-visibility probe and its own `AsyncTaskRunning` rollback), so a
     * placement must not overwrite it.
     */
    private fun bindCreatureAsyncTaskHost(creature: Creature) {
        creature.asyncTaskDispatcher = dispatcher
        if (creature is Monster) {
            return
        }

        val creatureId = creature.id
        creature.scheduleAsyncTaskBatch = { task ->
            asyncTaskBuckets.enqueueAsyncTask(
                creatureId = creatureId,
                task = task,
                // `Creature::isPlayerVisibleForScheduling` base returns true
                // (creature.hpp:831-833); only `Monster` overrides it and keeps
                // the host binding.
                playerVisible = true,
                isPlayerVisibleForScheduling = { true },
                clearAsyncTaskRunning = {
                    creature.setAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning, false)
                },
            )
        }
    }

    /** `Game::removePlayer` (src/game/game.cpp:11631). */
    fun removePlayer(player: Player) {
        players.remove(player.id)
        if (player.name.isNotEmpty()) {
            wildcardTree.remove(player.name.lowercase())
        }
    }

    /** `Game::getPlayers` (src/game/game.hpp:577): online players keyed by creature id. */
    fun getPlayers(): Map<Long, Player> = players

    /**
     * `Game::getPlayerByName` (src/game/game.cpp:1394): empty names return null and
     * lookups are case-insensitive (`asLowerCaseString`). When the player is not
     * online, `allowOffline` falls back to [playerStorage] (`IOLoginData::loadPlayerByName`
     * equivalent); no online flag exists on the Kotlin player yet.
     */
    fun getPlayerByName(name: String, allowOffline: Boolean = false): Player? {
        if (name.isEmpty()) {
            return null
        }

        players.values.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        if (!allowOffline) {
            return null
        }
        return playerStorage?.loadByName(name)
    }

    /**
     * `Game::getPlayerByID` (src/game/game.cpp:1339). The `allowOffline` branch
     * loads through `IOLoginData::loadPlayerById` in Canary; the Kotlin
     * [PlayerStorage] only models name lookups, so an offline id resolves to
     * `null` (PARITY GAP).
     */
    fun getPlayerByID(playerId: Long, allowOffline: Boolean = false): Player? {
        players[playerId]?.let { return it }
        if (!allowOffline) {
            return null
        }
        return null
    }

    /**
     * `Game::getGuild` (src/game/game.cpp:11727): the online guild registry with
     * the `IOGuild::loadGuild` fallback behind `allowOffline`.
     */
    fun getGuild(id: Long, allowOffline: Boolean = false): Guild? {
        guilds[id]?.let { return it }
        if (!allowOffline) {
            return null
        }
        return guildRepository?.loadGuild(id)
    }

    /**
     * `Game::getGuildByName` (src/game/game.cpp:11738): resolves the id through
     * `IOGuild::getGuildIdByName` first, then falls back like [getGuild].
     */
    fun getGuildByName(name: String, allowOffline: Boolean = false): Guild? {
        val id = guildRepository?.getGuildIdByName(name) ?: 0L
        guilds[id]?.let { return it }
        if (!allowOffline) {
            return null
        }
        return guildRepository?.loadGuild(id)
    }

    /** `Game::addGuild` (src/game/game.cpp:11750). */
    fun addGuild(guild: Guild?) {
        if (guild == null) {
            return
        }
        guilds[guild.id] = guild
    }

    /** `Game::removeGuild` (src/game/game.cpp:11757): save through the manager, then erase. */
    fun removeGuild(guildId: Long) {
        val guild = guilds.remove(guildId) ?: return
        saveGuild(guild)
    }

    /** `Game::getGuilds` (src/game/game.hpp:574). */
    fun getGuilds(): Map<Long, Guild> = guilds

    /**
     * `Game::sendGuildMotd` (src/game/game.cpp:9793): the online player's guild
     * MOTD on `CHANNEL_GUILD` with `TALKTYPE_CHANNEL_R1`.
     */
    fun sendGuildMotd(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        val guild = player.getGuild() ?: return
        chatClient.sendChannelMessage(player, "Message of the Day", guild.getMotd(), SpeakClasses.TALKTYPE_CHANNEL_R1, CHANNEL_GUILD)
    }

    /**
     * `Game::playerRequestAddVip` (src/game/game.cpp:7058): names over 25
     * characters are discarded. An offline target resolves through
     * `IOLoginData::getGuidByNameEx` ([loginRepository]) and is added with
     * `VipStatus_t::Offline`; an online ghost is hidden from non-access players.
     */
    fun playerRequestAddVip(playerId: Long, name: String) {
        if (name.length > 25) {
            return
        }

        val player = getPlayerByID(playerId) ?: return
        val vipPlayer = getPlayerByName(name)
        if (vipPlayer == null) {
            val lookup = loginRepository?.getGuidByNameEx(name)
            if (lookup == null) {
                player.sendTextMessageTyped(MessageClass.MESSAGE_FAILURE, "A player with this name does not exist.")
                return
            }

            if (lookup.specialVip && !player.hasFlag(PlayerFlags_t.SpecialVIP)) {
                player.sendTextMessageTyped(MessageClass.MESSAGE_FAILURE, "You can not add this player")
                return
            }

            playerVip(player)?.add(lookup.guid, lookup.name, VipStatus.Offline)
        } else {
            if (vipPlayer.hasFlag(PlayerFlags_t.SpecialVIP) && !player.hasFlag(PlayerFlags_t.SpecialVIP)) {
                player.sendTextMessageTyped(MessageClass.MESSAGE_FAILURE, "You can not add this player")
                return
            }

            if (!isInGhostMode(vipPlayer) || player.isAccessPlayer()) {
                val status = playerVip(vipPlayer)?.getStatus() ?: VipStatus.Online
                playerVip(player)?.add(vipPlayer.getGUID(), vipPlayer.name, status)
            } else {
                playerVip(player)?.add(vipPlayer.getGUID(), vipPlayer.name, VipStatus.Offline)
            }
        }
    }

    /** `Game::playerRequestRemoveVip` (src/game/game.cpp:7098). */
    fun playerRequestRemoveVip(playerId: Long, guid: Long) {
        val player = getPlayerByID(playerId) ?: return
        playerVip(player)?.remove(guid)
    }

    /**
     * `Game::playerRequestEditVip` (src/game/game.cpp:7107). The `vipGroupsId`
     * vector is the `std::vector<uint8_t>` widened to `Int` like the rest of the
     * port.
     */
    fun playerRequestEditVip(
        playerId: Long,
        guid: Long,
        description: String,
        icon: Int,
        notify: Boolean,
        vipGroupsId: List<Int>,
    ) {
        val player = getPlayerByID(playerId) ?: return
        playerVip(player)?.edit(guid, description, icon, notify, vipGroupsId)
    }

    /** `Game::playerReportRuleViolationReport` (src/game/game.cpp:10183). */
    fun playerReportRuleViolationReport(
        playerId: Long,
        targetName: String,
        reportType: Int,
        reportReason: Int,
        comment: String,
        translation: String,
    ) {
        val player = getPlayerByID(playerId) ?: return
        eventPlayerOnReportRuleViolation(player, targetName, reportType, reportReason, comment, translation)
        callbackPlayerOnReportRuleViolation(player, targetName, reportType, reportReason, comment, translation)
    }

    /** `Game::playerReportBug` (src/game/game.cpp:10193). */
    fun playerReportBug(playerId: Long, message: String, position: Position, category: Int) {
        val player = getPlayerByID(playerId) ?: return
        eventPlayerOnReportBug(player, message, position, category)
        callbackPlayerOnReportBug(player, message, position, category)
    }

    /** `Game::getHighscoreCategoriesName` (src/game/game.cpp:12473). */
    fun getHighscoreCategoriesName(): Map<Int, String> = highscoreCategoriesNames

    /** `Game::getHighscoreCategories` (src/game/game.cpp:12477). */
    fun getHighscoreCategories(): List<HighscoreCategory> = highscoreCategories

    /** `Game::getPlayersOnline` (src/game/game.hpp:195). */
    fun getPlayersOnline(): Int = players.size

    /** `Game::getMonstersOnline` (src/game/game.hpp:198). */
    fun getMonstersOnline(): Int = monsters.size

    // --- records / online helpers (game.cpp:9517, 9625-9700, 12608, 13113) -----

    /** `Game::playersRecord` (game.hpp:928). */
    var playersRecord: Int = 0

    /** `g_globalEvents().getEventMap(GLOBALEVENT_RECORD)` dispatch seam (game.cpp:9631). */
    var executeRecord: (Int, Int) -> Unit = { _, _ -> }

    /** `g_database().executeQuery("UPDATE server_config ... players_record")` persistence seam (game.cpp:9638-9644). */
    var savePlayersRecord: (Int) -> Unit = { }

    /** `g_database().executeQuery("INSERT INTO server_config ...")` persistence seam (game.cpp:9653). */
    var createPlayersRecord: () -> Unit = { }

    /** `g_database().storeQuery("SELECT value ... players_record")` persistence seam (game.cpp:9649). */
    var loadPlayersRecordValue: () -> Int? = { null }

    /** `Player::getIP()` (player.hpp) seam behind [groupPlayersByIP]. */
    var getPlayerIP: (Player) -> Long = { 0L }

    /** `Player::getIdleTime()` (player.hpp) seam behind [groupPlayersByIP]. */
    var getPlayerIdleTime: (Player) -> Long = { 0L }

    /**
     * `Game::checkPlayersRecord` (src/game/game.cpp:9625): a new online peak
     * fires the `GLOBALEVENT_RECORD` dispatch and persists the value.
     */
    fun checkPlayersRecord() {
        val playersOnline = getPlayersOnline()
        if (playersOnline > playersRecord) {
            val previousRecord = playersRecord
            playersRecord = playersOnline
            executeRecord(playersRecord, previousRecord)
            updatePlayersRecord()
        }
    }

    /** `Game::updatePlayersRecord` (src/game/game.cpp:9638). */
    fun updatePlayersRecord() {
        savePlayersRecord(playersRecord)
    }

    /** `Game::loadPlayersRecord` (src/game/game.cpp:9646): a missing row is created with `0`. */
    fun loadPlayersRecord() {
        val value = loadPlayersRecordValue()
        if (value != null) {
            playersRecord = value
        } else {
            createPlayersRecord()
        }
    }

    /**
     * `Game::groupPlayersByIP` (src/game/game.cpp:13113): players idle for
     * more than 15 minutes and address-less players are excluded.
     */
    fun groupPlayersByIP(): Map<Long, List<Player>> {
        val groupedPlayers = linkedMapOf<Long, MutableList<Player>>()
        for (player in players.values) {
            val ip = getPlayerIP(player)
            if (ip != 0L && getPlayerIdleTime(player) <= IDLE_THRESHOLD_MS) {
                groupedPlayers.getOrPut(ip) { mutableListOf() }.add(player)
            }
        }
        return groupedPlayers
    }

    /** `Game::getPlayerStats` (src/game/game.cpp:13129): the otservlist 4-per-IP cap. */
    fun getPlayerStats(): PlayerStats {
        val groupedPlayers = groupPlayersByIP()
        val stats = PlayerStats()
        stats.totalUniqueIPs = groupedPlayers.size
        for (group in groupedPlayers.values) {
            stats.filteredOnlinePlayers += minOf(group.size, 4)
        }
        return stats
    }

    /**
     * `Game::forceRemoveCondition` (src/game/game.cpp:11125): the online
     * creature's condition is force-removed, unknown ids are ignored.
     */
    fun forceRemoveCondition(creatureId: Long, conditionType: ConditionType, conditionId: ConditionId) {
        val creature = getCreatureByID(creatureId) ?: return
        creature.removeCondition(conditionType, conditionId, true)
    }

    /** `Game::addBestiaryList` (src/game/game.cpp:9517): first registration wins. */
    fun addBestiaryList(raceid: Int, name: String) {
        if (bestiaryList.containsKey(raceid)) {
            return
        }

        bestiaryList[raceid] = name
    }

    /** `Game::getBestiaryList` (src/game/game.hpp:459). */
    fun getBestiaryList(): Map<Int, String> = bestiaryList

    /** `Game::getSummaryKeyByType` (src/game/game.cpp:12596). */
    fun getSummaryKeyByType(type: Int): String = summaryCategories[type] ?: ""

    /** `Game::getHirelingSkills` (src/game/game.cpp:12600). */
    fun getHirelingSkills(): Map<Int, String> = hirelingSkills

    /** `Game::getHirelingOutfits` (src/game/game.cpp:12604). */
    fun getHirelingOutfits(): Map<Int, String> = hirelingOutfits

    // --- cyclopedia / registry helpers (game.cpp:930-950, 9463-9490, 11708-11845, 12481-12610) ---

    /**
     * `Game::getAchievementById` (src/game/game.cpp:12493): the injected
     * registry resolves the Lua-authored achievement table (AGENTS.md 1.4).
     */
    fun getAchievementById(id: Int): Achievement = achievementRegistry.getAchievementById(id)

    /** `Game::getAchievementByName` (src/game/game.cpp:12497). */
    fun getAchievementByName(name: String): Achievement = achievementRegistry.getAchievementByName(name)

    /** `Game::getBadges` (src/game/game.cpp:12536). */
    fun getBadges(): List<Badge> = badgeRegistry.getBadges()

    /** `Game::getBadgeById` (src/game/game.cpp:12540). */
    fun getBadgeById(id: Int): Badge = badgeRegistry.getBadgeById(id)

    /** `Game::getBadgeByName` (src/game/game.cpp:12553). */
    fun getBadgeByName(name: String): Badge = badgeRegistry.getBadgeByName(name)

    /** `Game::getTitles` (src/game/game.cpp:12566). */
    fun getTitles(): List<Title> = titleRegistry.getTitles()

    /** `Game::getTitleById` (src/game/game.cpp:12570). */
    fun getTitleById(id: Int): Title = titleRegistry.getTitleById(id)

    /** `Game::getTitleByName` (src/game/game.cpp:12583). */
    fun getTitleByName(name: String): Title = titleRegistry.getTitleByName(name)

    /** `Game::logCyclopediaStats` (src/game/game.cpp:12531). */
    fun logCyclopediaStats() {
        logger.info { "Loaded ${badgeRegistry.getBadges().size} badges from Badge System" }
        logger.info { "Loaded ${titleRegistry.getTitles().size} titles from Title System" }
    }

    /**
     * `Game::getItemsClassification` (src/game/game.cpp:9463): the forge
     * classification store lookup with the `create` fallback.
     */
    fun getItemsClassification(id: Int, create: Boolean): ItemClassification? =
        if (create) itemClassifications.createClassification(id) else itemClassifications.getClassification(id)

    /** `Game::getAttachedEffects` (src/game/game.cpp:12355). */
    fun getAttachedEffects(): AttachedEffects = attachedEffects

    /**
     * `Game::getTeamFinder` (src/game/game.cpp:930): `null` is the C++
     * `TeamFinderNull` singleton.
     */
    fun getTeamFinder(player: Player): TeamFinder? = teamFinderList.getTeamFinder(player)

    /** `Game::getOrCreateTeamFinder` (src/game/game.cpp:939). */
    fun getOrCreateTeamFinder(player: Player): TeamFinder = teamFinderList.getOrCreateTeamFinder(player)

    /** `Game::removeTeamFinderListed` (src/game/game.cpp:948). */
    fun removeTeamFinderListed(leaderGuid: Long) {
        teamFinderList.removeTeamFinderListed(leaderGuid)
    }

    /** `Game::getBedBySleeper` (src/game/game.cpp:11783). */
    fun getBedBySleeper(guid: Long): BedItem? = bedSleepersMap[guid]

    /** `Game::setBedSleeper` (src/game/game.cpp:11791). */
    fun setBedSleeper(bed: BedItem, guid: Long) {
        bedSleepersMap[guid] = bed
    }

    /** `Game::removeBedSleeper` (src/game/game.cpp:11795). */
    fun removeBedSleeper(guid: Long) {
        bedSleepersMap.remove(guid)
    }

    /** `Game::getUniqueItem` (src/game/game.cpp:11802). */
    fun getUniqueItem(uniqueId: Int): Item? = uniqueItems[uniqueId]

    /** `Game::addUniqueItem` (src/game/game.cpp:11810): a duplicate id is kept and reported. */
    fun addUniqueItem(uniqueId: Int, item: Item): Boolean {
        if (uniqueItems.containsKey(uniqueId)) {
            logger.warn { "Duplicate unique id: $uniqueId" }
            return false
        }

        uniqueItems[uniqueId] = item
        return true
    }

    /** `Game::removeUniqueItem` (src/game/game.cpp:11818). */
    fun removeUniqueItem(uniqueId: Int) {
        uniqueItems.remove(uniqueId)
    }

    /** `Game::internalCreatureChangeVisible` (src/game/game.cpp:7801): the spectator fanout. */
    fun internalCreatureChangeVisible(creature: Creature, visible: Boolean) {
        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val player = spectator as? Player ?: continue
            sendCreatureChangeVisible(player, creature, visible)
        }
    }

    /**
     * `Game::updateMonster` (src/game/game.cpp:11708): renames the monster's
     * name-index entry; an index miss is a no-op. The C++ stores the raw
     * `monsterType->name`; the port lower-cases it to keep the index invariant
     * [addMonster] and [getCreatureByName] rely on (hardening).
     */
    fun updateMonster(monster: Monster?, monsterType: MonsterType) {
        if (monster == null) {
            return
        }

        val oldName = monster.name.lowercase()
        val index = monstersNameIndex[oldName] ?: return

        monstersNameIndex.remove(oldName)
        monstersNameIndex[monsterType.name.lowercase()] = index
    }

    /**
     * `Game::playerFriendSystemAction` (src/game/game.cpp:9814): only the
     * `0x0E` title action runs; it sets the current title and refreshes the
     * cyclopedia base information and titles windows.
     */
    fun playerFriendSystemAction(player: Player, type: Int, titleId: Int) {
        if (type == 0x0E) {
            setCurrentTitle(player, titleId)
            sendCyclopediaCharacterBaseInformation(player)
            sendCyclopediaCharacterTitles(player)
        }
    }

    /** `Game::dieSafely` (src/game/game.cpp:9493). */
    fun dieSafely(errorMsg: String = "") {
        logger.error { errorMsg }
        shutdown()
    }

    /**
     * `Game::shutdown` (src/game/game.cpp:9498). PARITY GAP: `raids.clear()`
     * has no Kotlin owner.
     */
    fun shutdown() {
        sendWebhook(":red_circle: Server is shutting down...")

        logger.info { "Shutting down..." }
        map.spawnsMonster.clear()
        map.spawnsNpc.clear()

        stopServiceManager()
        closeAllConnections()
        luaCollectGarbage()

        logger.info { "Done!" }
    }

    /** `Player::sendCreatureChangeVisible(creature, visible)` protocol seam (game.cpp:7804). */
    var sendCreatureChangeVisible: (Player, Creature, Boolean) -> Unit = { _, _, _ -> }

    /** `Player::title().setCurrentTitle(titleId)` (player.cpp) seam used by [playerFriendSystemAction]. */
    var setCurrentTitle: (Player, Int) -> Unit = { _, _ -> }

    /** `Player::sendCyclopediaCharacterBaseInformation()` protocol seam (game.cpp:9818). */
    var sendCyclopediaCharacterBaseInformation: (Player) -> Unit = { _ -> }

    /** `Player::sendCyclopediaCharacterTitles()` protocol seam (game.cpp:9819). */
    var sendCyclopediaCharacterTitles: (Player) -> Unit = { _ -> }

    /** `g_webhook().sendMessage(message)` (game.cpp:9499) seam. */
    var sendWebhook: (String) -> Unit = { _ -> }

    /** `serviceManager->stop()` (game.cpp:9505) seam. */
    var stopServiceManager: () -> Unit = {}

    /** `ConnectionManager::getInstance().closeAll()` (game.cpp:9509) seam. */
    var closeAllConnections: () -> Unit = {}

    /** `g_luaEnvironment().collectGarbage()` (game.cpp:9511) seam. */
    var luaCollectGarbage: () -> Unit = {}

    /** `Game::BestiaryList` (game.hpp:876): race id -> lowercase bestiary name. */
    private val bestiaryList = sortedMapOf<Int, String>()

    /** `Game::boostedCreature` (game.hpp:877). */
    private var boostedCreature: String = ""

    /**
     * `Game::m_appearancesPtr` (game.hpp:881): replaced by
     * [loadAppearanceProtobuf], seeded from the constructor table. `null` means
     * "no appearances loaded".
     */
    private var appearancesPtr: Appearances? = appearances

    /** `Game::registeredMagicEffects` (game.hpp:881). */
    private val registeredMagicEffects = mutableListOf<Int>()

    /** `Game::registeredDistanceEffects` (game.hpp:882). */
    private val registeredDistanceEffects = mutableListOf<Int>()

    /** `Game::registeredLookTypes` (game.hpp:883). */
    private val registeredLookTypes = mutableListOf<Int>()

    /** `Game::m_summaryCategories` (game.cpp:735-742). */
    private val summaryCategories: Map<Int, String> = linkedMapOf(
        Summary_t.HOUSE_ITEMS.value to "house-items",
        Summary_t.BOOSTS.value to "xp-boosts",
        Summary_t.PREY_CARDS.value to "prey-cards",
        Summary_t.BLESSINGS.value to "blessings",
        Summary_t.INSTANT_REWARDS.value to "instant-rewards",
        Summary_t.HIRELINGS.value to "hirelings",
    )

    /** `Game::m_hirelingSkills` (game.cpp:744-749). */
    private val hirelingSkills: Map<Int, String> = linkedMapOf(
        1001 to "banker",
        1002 to "cooker",
        1003 to "steward",
        1004 to "trader",
    )

    /** `Game::m_hirelingOutfits` (game.cpp:751-761). */
    private val hirelingOutfits: Map<Int, String> = linkedMapOf(
        2001 to "banker",
        2002 to "cooker",
        2003 to "steward",
        2004 to "trader",
        2005 to "servant",
        2006 to "hydra",
        2007 to "ferumbras",
        2008 to "bonelord",
        2009 to "dragon",
    )

    /**
     * Kotlin accessor for Canary's public `Game::map` member (src/game/game.hpp:627) as
     * used at call sites such as `g_game().map.getTile(pos)` (src/game/game.cpp:1117);
     * `Map::getTile` itself is defined at src/map/map.hpp:77.
     */
    fun getTile(position: Position): Tile? = map.getTile(position)

    /**
     * `Game::internalPlaceCreature` (src/game/game.cpp:1495) delegating the
     * placement to `Map::placeCreature` (src/map/map.cpp:559).
     *
     * A creature is rejected when it already has a parent tile, a missing
     * center tile is rejected before the map call (game.cpp:1499-1502) and the
     * center tile's zones gate the transition. The map fallback resolves the
     * surrounding `normalRelList`/`extendedRelList` tiles, `forced` mirrors
     * `forceLogin`, and `creatureCheck` maps to `addCreatureCheck`/
     * `onPlacedCreature` (game.cpp:1516-1519). The facade's [placedCreatures]
     * index mirrors the tile the creature ended up on for the zone/registry
     * accessors.
     *
     * A monster is registered with the injected [monsterHost] before the map
     * placement so the `Monster::onCreatureAppear` fanout the map layer folds
     * in (GameMap.kt) already reaches its pipeline; a rejected map placement
     * unregisters again and keeps the host inert. The type comes from the
     * injected [monsterTypeProvider] (`Monster::m_monsterType`, monster.hpp:363).
     *
     * Parity gaps: `Creature::setID`, `Creature::addList` and
     * `Creature::updateCalculatedStepSpeed` (game.cpp:1512-1514) have no Kotlin
     * counterpart.
     */
    fun internalPlaceCreature(
        creature: Creature,
        pos: Position,
        extendedPos: Boolean = false,
        forced: Boolean = false,
        creatureCheck: Boolean = false,
    ): Boolean {
        if (creature.getParent() != null) {
            return false
        }

        val centerTile = map.getTile(pos) ?: return false
        val toZones = tileZones(pos)
        if (beforeCreatureZoneChange(creature, emptySet(), toZones) != ReturnValue.NOERROR) {
            return false
        }

        val monster = creature as? Monster
        if (monster != null) {
            monsterHost?.register(monster, monsterTypeProvider(monster.name))
        }

        if (!map.placeCreature(pos, creature, extendedPos, forced, centerTile)) {
            if (monster != null) {
                monsterHost?.unregister(monster.id)
            }
            return false
        }
        placedCreatures[creature] = creature.getTile() ?: centerTile

        // `g_game().addCreatureHealth` reach of `Creature::changeHealth`
        // (creature.cpp:859-861): bind the spectator fanout once the creature
        // is on the map.
        creature.addCreatureHealth = ::addCreatureHealth

        // `Creature::enqueueAsyncTask`'s `g_dispatcher()` and the shared
        // `getCreatureAsyncTaskBuckets` state (creature.cpp:34-55,
        // 2031-2054): a creature entering the world is admitted through the
        // world buckets instead of running its batch on the producer thread.
        bindCreatureAsyncTaskHost(creature)

        if (creatureCheck) {
            addCreatureCheck(creature)
            creature.onPlacedCreature()
        }

        afterCreatureZoneChange(creature, emptySet(), toZones)
        return true
    }

    /**
     * `Game::placeCreature` (src/game/game.cpp:1524): place through
     * [internalPlaceCreature], then fan the appear notification out to every
     * spectator. The appear frames are sent by the placement itself
     * (`Map::placeCreature` folds in the `sendCreatureAppear`/
     * `onCreatureAppear` fanout, GameMap.kt:240-249), so this facade only
     * re-resolves the spectator set for the player-spectator creature check
     * (game.cpp:1530-1541) and closes the placement with
     * `Creature::onPlacedCreature`.
     *
     * Parity gaps: `Cylinder::postAddNotification` (game.cpp:1543-1546) and the
     * `MONSTER_PERF_TEST_FORCE_ACTIVE` creature-check arm (game.cpp:1539) are
     * not modeled.
     */
    fun placeCreature(
        creature: Creature,
        pos: Position,
        extendedPos: Boolean = false,
        forced: Boolean = false,
    ): Boolean {
        if (!internalPlaceCreature(creature, pos, extendedPos, forced)) {
            return false
        }

        val hasPlayerSpectators = spectatorsOf(creature.position, multifloor = true).any { it is Player }
        if (hasPlayerSpectators) {
            addCreatureCheck(creature)
        }

        creature.onPlacedCreature()
        return true
    }

    /**
     * `Game::removeCreature` (src/game/game.cpp:1551): rejected for creatures
     * already flagged by `Creature::isRemoved()`, otherwise the map layer
     * removes the creature from its tile and notifies the tile spectators
     * (`Map::removeCreature` ports the game.cpp:1563-1593 block), the master
     * link is cleared, the zone transition and `Creature::setRemoved()` run,
     * the creature leaves the think buckets and every summon is removed first
     * with `setSkillLoss(false)`. A monster's host entry is dropped after
     * `Creature::setRemoved()` so its pending completions re-resolve to `null`
     * and the bound hooks turn inert (AGENTS.md deferred-callback lifetime
     * safety).
     *
     * Parity gaps: `Cylinder::postRemoveNotification`, `Creature::removeList`
     * and the team-finder logout cleanup (game.cpp:1599-1617) are not modeled.
     * Like Canary, a missing tile keeps the creature-state tail running
     * (game.cpp:1557-1560).
     */
    fun removeCreature(creature: Creature, isLogout: Boolean = true): Boolean {
        if (creature.isRemoved()) {
            return false
        }

        val tile = creature.getTile()
        val fromZones = if (tile != null) tileZones(tile.position) else emptySet()
        if (!map.removeCreature(creature, isLogout)) {
            return false
        }
        placedCreatures.remove(creature)

        val master = creature.getMaster()
        if (master != null && !master.isRemoved()) {
            creature.setMaster(null)
        }

        afterCreatureZoneChange(creature, fromZones, emptySet())

        creature.setRemoved()
        removeCreatureCheck(creature)

        if (creature is Monster) {
            monsterHost?.unregister(creature.id)
        }

        for (summon in creature.getSummons().toList()) {
            summon.setSkillLoss(false)
            removeCreature(summon)
        }

        return true
    }

    /**
     * `Game::addCreatureCheck` (src/game/game.cpp:7659): flags the creature and
     * admits it once into a random `checkCreatureLists` bucket. The C++ event is
     * scheduled on the dispatcher when the caller is not already confined and
     * committed inline otherwise (game.cpp:7676-7684); the port mirrors that
     * with [GameDispatcher.isRunning].
     *
     * Parity gap: when the dispatcher rejects the event Canary resets
     * `inCheckCreaturesVector`; [GameDispatcher.dispatch] has no rejection
     * signal, so a dropped task leaves the flag set.
     */
    fun addCreatureCheck(creature: Creature) {
        if (creature.isRemoved()) {
            return
        }

        creature.creatureCheck = true

        if (creature.inCheckCreaturesVector) {
            return
        }
        creature.inCheckCreaturesVector = true

        val commit = {
            val index = Random.nextInt(EVENT_CREATURECOUNT)
            checkCreatureLists[index].add(WeakReference(creature))
        }
        if (dispatcher.isRunning) {
            dispatcher.dispatch(DispatcherLane.WorldCommit) { commit() }
        } else {
            commit()
        }
    }

    /** `Game::removeCreatureCheck` (src/game/game.cpp:7687). */
    fun removeCreatureCheck(creature: Creature) {
        if (creature.inCheckCreaturesVector) {
            creature.creatureCheck = false
        }
    }

    /**
     * `Game::checkCreatures` (src/game/game.cpp:7719): advances the rotating
     * `checkCreatureLists` cursor and, per flagged alive creature, runs
     * `Creature::onThink(EVENT_CREATURE_THINK_INTERVAL)`. A monster schedules
     * its deferred post-think through the injected [monsterHost]
     * (`Monster::trySchedulePostThink`, game.cpp:7727-7729); non-monsters keep
     * the `onAttacking`/`executeConditions` fallback. Dead or unflagged entries
     * clear `inCheckCreaturesVector` and are pruned.
     *
     * PARITY GAPS: the non-monster `Creature::onAttacking` call is not ported;
     * the condition tick (`executeConditions`) is folded into
     * [Creature.onThink] by the Kotlin creature port, so it must not run twice
     * here.
     */
    fun checkCreatures() {
        val bucket = checkCreatureLists[checkCreatureIndex]
        val iterator = bucket.iterator()
        while (iterator.hasNext()) {
            val creature = iterator.next().get()
            if (creature != null && creature.creatureCheck && creature.isAlive()) {
                creature.onThink(EVENT_CREATURE_THINK_INTERVAL)
                if (creature is Monster) {
                    monsterHost?.trySchedulePostThink(creature)
                }
            } else {
                creature?.inCheckCreaturesVector = false
                iterator.remove()
            }
        }

        checkCreatureIndex = (checkCreatureIndex + 1) % EVENT_CREATURECOUNT
    }

    /**
     * `Game::queueMonsterPostThink` (src/game/game.cpp:7694): admission-gated
     * enqueue of the monster id into the visible/background post-think queue.
     * The queues live in the injected [MonsterPipelinesHost]
     * (game.cpp:85-200 anonymous-namespace helpers); an unwired host rejects.
     */
    fun queueMonsterPostThink(monsterId: Long, playerVisible: Boolean): Boolean =
        monsterHost?.queueMonsterPostThink(monsterId, playerVisible) ?: false

    /**
     * `Game::internalAddItem` (src/game/game.cpp:2693 and 2698 remainder overload).
     *
     * The `Cylinder` flow is reduced to its [Tile] cases: `queryDestination`
     * (tile.cpp:1009, no floor change), `queryAdd` (tile.cpp:691), `queryMaxCount`
     * (tile.cpp:978) and stackable merge. The full-count remainder recursion is
     * inlined because a tile's `queryMaxCount` never caps a single call.
     *
     * Parity gaps: container destinations, mailboxes, hangable/always-on-top
     * ordering, blocking item checks and the `addedItem`/quiver branch of game.cpp
     * need item type data (ground/blockSolid/movable/stackSize/attributes) that the
     * Kotlin [Item]/[org.opentibia.canary.items.ItemType] do not model yet. The stack
     * cap uses Canary's `ItemType::stackSize` default of 100 (src/items/items.hpp:335)
     * because the Kotlin item type does not carry a per-item value.
     */
    fun internalAddItem(
        toTile: Tile?,
        item: Item?,
        index: Int = INDEX_WHEREEVER,
        flags: Int = 0,
        test: Boolean = false,
    ): ReturnValue {
        if (toTile == null || item == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        val toItem = topDownItem(toTile)

        val ret = queryAdd(toTile, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        val maxQueryCount = queryMaxCount(item.count)
        if (test) {
            return ReturnValue.NOERROR
        }

        if (isStackable(item) && toItem != null && sameItemType(item, toItem)) {
            val m = minOf(item.count, maxQueryCount)
            val n = mergeCapacity(toItem, m)
            toItem.count += n

            val remainder = m - n
            if (remainder > 0) {
                if (item.count == remainder) {
                    addThing(toTile, item, index)
                } else {
                    addThing(toTile, cloneItem(item, remainder), index)
                }
            }
        } else {
            addThing(toTile, item, index)
        }

        return ReturnValue.NOERROR
    }

    /**
     * `Game::internalRemoveItem` (src/game/game.cpp:2785). The parent `Cylinder` is
     * resolved from the item's tracked parent tile (the facade's `Item::getParent()`);
     * `count == -1` removes the whole stack, partial stackable counts decrement in
     * place and a full removal unlinks the item.
     *
     * Parity gaps: `Item::canRemove`, map-loaded decay protection and the
     * `FLAG_IGNORENOTMOVABLE`/`Item::isMovable` check cannot be evaluated because the
     * Kotlin [ItemType] does not model movable/decay state.
     */
    fun internalRemoveItem(
        item: Item?,
        count: Int = -1,
        test: Boolean = false,
        flags: Int = 0,
        force: Boolean = false,
    ): ReturnValue {
        if (item == null) {
            return ReturnValue.NOTPOSSIBLE
        }
        val tile = itemParents[item] ?: return ReturnValue.NOTPOSSIBLE

        val removeCount = if (count == -1) item.count else count
        val ret = queryRemove(tile, item, removeCount, flags or CylinderFlags.FLAG_IGNORENOTMOVABLE)
        if (!force && ret != ReturnValue.NOERROR) {
            return ret
        }

        if (!test) {
            removeThing(tile, item, removeCount)
        }
        return ReturnValue.NOERROR
    }

    /**
     * `Game::internalMoveItem` (src/game/game.cpp:2436).
     *
     * Transcribes the straight tile-to-tile path: same-top-item short circuit,
     * `queryRemove`/`queryAdd`/`queryMaxCount`, source removal, stackable merge with
     * the cap of `Item::getStackSize()` and clone-on-partial-move
     * (game.cpp:2566-2611). The `movedItem` out parameter becomes unnecessary because
     * a partial stack move leaves the original on the source and adds a clone on the
     * destination.
     *
     * Parity gaps: the `browseFields` remap (game.cpp:2447-2453),
     * `RETURNVALUE_NEEDEXCHANGE` (hangable/supports, game.cpp:2494-2524), the
     * `count == 255` client fix (game.cpp:2481), `tradeItem`, `actor` notifications and
     * the clone's attributes/decay state are not modeled.
     */
    fun internalMoveItem(
        fromTile: Tile?,
        toTile: Tile?,
        index: Int = INDEX_WHEREEVER,
        item: Item?,
        count: Int = 1,
        flags: Int = 0,
    ): ReturnValue {
        if (fromTile == null || toTile == null || item == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        val toItem = topDownItem(toTile)
        if (item === toItem) {
            return ReturnValue.NOERROR
        }

        var ret = queryRemove(fromTile, item, 1, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        ret = queryAdd(toTile, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        val maxQueryCount = queryMaxCount(count)
        val m = if (isStackable(item)) minOf(count, maxQueryCount) else maxQueryCount

        ret = queryRemove(fromTile, item, m, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        removeThing(fromTile, item, m)

        if (isStackable(item)) {
            val n = if (toItem != null && sameItemType(item, toItem)) mergeCapacity(toItem, m) else 0
            if (n > 0 && toItem != null) {
                toItem.count += n
            }

            val newCount = m - n
            if (newCount > 0) {
                addThing(toTile, cloneItem(item, newCount), index)
            }
        } else {
            addThing(toTile, item, index)
        }

        return ReturnValue.NOERROR
    }

    /**
     * `Game::broadcastMessage` (src/game/game.cpp:9526): an empty text is
     * discarded, otherwise every online player receives the typed message
     * through [Player.sendTextMessageTyped].
     */
    fun broadcastMessage(text: String, type: MessageClass) {
        if (text.isEmpty()) {
            return
        }

        logger.info { "Broadcasted message: $text" }
        for (player in players.values.toList()) {
            player.sendTextMessageTyped(type, text)
        }
    }

    /** `Game::getWorldLightInfo` (src/game/game.cpp:9479): `{ lightLevel, 0xD7 }`. */
    fun getWorldLightInfo(): LightInfo = LightInfo(lightLevel, 0xD7)

    /** `Game::gameIsDay` (src/game/game.cpp:9483): day between 06:00 and 18:00. */
    fun gameIsDay(): Boolean {
        isDay = lightHour >= (6 * 60) && lightHour <= (18 * 60)
        return isDay
    }

    /**
     * `Game::checkLight` (src/game/game.cpp:9402): advances the tibian clock and
     * light level, then fans the world light and/or time out to every online
     * player. The `GLOBALEVENT_PERIODCHANGE` execution is routed through
     * [executePeriodChange] (PARITY GAP: the global-event engine is not ported).
     */
    fun checkLight() {
        lightHour += lightHourDelta
        if (lightHour > LIGHT_DAY_LENGTH) {
            lightHour -= LIGHT_DAY_LENGTH
        }

        if (abs(lightHour - SUNRISE) < 2 * lightHourDelta) {
            lightState = LightState.LIGHT_STATE_SUNRISE
        } else if (abs(lightHour - SUNSET) < 2 * lightHourDelta) {
            lightState = LightState.LIGHT_STATE_SUNSET
        }

        var newLightLevel = lightLevel
        var lightChange = false

        when (lightState) {
            LightState.LIGHT_STATE_SUNRISE -> {
                newLightLevel += (LIGHT_LEVEL_DAY - LIGHT_LEVEL_NIGHT) / 30
                lightChange = true
            }

            LightState.LIGHT_STATE_SUNSET -> {
                newLightLevel -= (LIGHT_LEVEL_DAY - LIGHT_LEVEL_NIGHT) / 30
                lightChange = true
            }

            else -> Unit
        }

        if (newLightLevel <= LIGHT_LEVEL_NIGHT) {
            lightLevel = LIGHT_LEVEL_NIGHT
            lightState = LightState.LIGHT_STATE_NIGHT
        } else if (newLightLevel >= LIGHT_LEVEL_DAY) {
            lightLevel = LIGHT_LEVEL_DAY
            lightState = LightState.LIGHT_STATE_DAY
        } else {
            lightLevel = newLightLevel
        }

        val lightInfo = getWorldLightInfo()
        if (lightChange) {
            for (player in players.values.toList()) {
                player.sendWorldLight(lightInfo)
                player.sendTibiaTime(lightHour)
            }
        } else {
            for (player in players.values.toList()) {
                player.sendTibiaTime(lightHour)
            }
        }

        if (currentLightState != lightState) {
            currentLightState = lightState
            executePeriodChange(lightState, lightInfo)
        }
    }

    /**
     * `Game::sendSingleSoundEffect` (src/game/game.cpp:7833): `SILENCE` is
     * discarded, otherwise every player spectator receives the effect with the
     * `SourceEffect_t` classification of [classifyGraphicalEffectSource].
     */
    fun sendSingleSoundEffect(pos: Position, soundId: SoundEffect, actor: Creature? = null) {
        if (soundId == SoundEffect.SILENCE) {
            return
        }

        for (spectator in spectatorsOf(pos, multifloor = false)) {
            val player = spectator as? Player ?: continue
            player.sendSingleSoundEffect(pos, soundId, classifyGraphicalEffectSource(actor, spectator))
        }
    }

    /**
     * `Game::sendDoubleSoundEffect` (src/game/game.cpp:7853): a silent secondary
     * effect degrades to [sendSingleSoundEffect], otherwise every player
     * spectator receives both effects with the shared source classification.
     */
    fun sendDoubleSoundEffect(
        pos: Position,
        mainSoundEffect: SoundEffect,
        secondarySoundEffect: SoundEffect,
        actor: Creature? = null,
    ) {
        if (secondarySoundEffect == SoundEffect.SILENCE) {
            sendSingleSoundEffect(pos, mainSoundEffect, actor)
            return
        }

        for (spectator in spectatorsOf(pos, multifloor = false)) {
            val player = spectator as? Player ?: continue
            val source = classifyGraphicalEffectSource(actor, spectator)
            player.sendDoubleSoundEffect(pos, mainSoundEffect, source, secondarySoundEffect, source)
        }
    }

    // --- world-state updater seams (game.cpp:7808-7831, 9326-9376, 9535-9590,
    // 11825-11880, 12674-12760) -------------------------------------------------

    /** `Player::sendCreatureLight(creature)` protocol seam (game.cpp:7811). */
    var sendCreatureLight: (Player, Creature) -> Unit = { _, _ -> }

    /** `Player::sendCreatureIcon(creature)` protocol seam (game.cpp:7818). */
    var sendCreatureIcon: (Player, Creature) -> Unit = { _, _ -> }

    /** `Player::reloadCreature(creature)` protocol seam (game.cpp:7829). */
    var reloadCreatureClient: (Player, Creature) -> Unit = { player, creature ->
        player.sendCreatureReload(creature)
    }

    /** `Player::sendUpdateCreature(creature)` protocol seam (game.cpp:11878). */
    var sendUpdateCreatureClient: (Player, Creature) -> Unit = { _, _ -> }

    /** `Player::sendPlayerVocation(target)` protocol seam (game.cpp:9339). */
    var sendPlayerVocation: (Player, Player) -> Unit = { _, _ -> }

    /**
     * `Player::sendMagicEffect(pos, effect, source)` protocol seam of the
     * `source` overload (game.cpp:9352-9363). The no-source path keeps the
     * existing 3-argument [sendMagicEffect] seam.
     */
    var sendMagicEffectWithSource: (Player, Position, Int, SourceEffect) -> Unit = { _, _, _, _ -> }

    /** `Player::removeMagicEffect(pos, effect)` protocol seam (game.cpp:9373). */
    var removeMagicEffectFromClient: (Player, Position, Int) -> Unit = { _, _, _ -> }

    /** `Player::sendDistanceShoot(fromPos, toPos, effect, source)` protocol seam (game.cpp:9397). */
    var sendDistanceShoot: (Player, Position, Position, Int, SourceEffect) -> Unit = { _, _, _, _, _ -> }

    /** `Player::sendCreatureWalkthrough(creature, canWalkthrough)` protocol seam (game.cpp:9539). */
    var sendCreatureWalkthrough: (Player, Creature, Boolean) -> Unit = { _, _, _ -> }

    /** `Player::sendCreatureSkull(creature)` protocol seam (game.cpp:9549). */
    var sendCreatureSkull: (Player, Creature) -> Unit = { _, _ -> }

    /** `Player::sendCreatureShield(creature)` protocol seam (game.cpp:9555). */
    var sendCreatureShield: (Player, Creature) -> Unit = { _, _ -> }

    /** `Player::sendCreatureType(creature, type)` protocol seam (game.cpp:9583-9588). */
    var sendCreatureType: (Player, Creature, CreatureType) -> Unit = { _, _, _ -> }

    /** `Player::sendCreatureHelpers(creatureId, helpers)` protocol seam (game.cpp:9697). */
    var sendCreatureHelpers: (Player, Long, Int) -> Unit = { _, _, _ -> }

    /** `Player::sendPlayerTyping(player, typing)` protocol seam (game.cpp:12711). */
    var sendPlayerTyping: (Player, Player, Int) -> Unit = { _, _, _ -> }

    /** `Player::attachedEffects().sendAttachedEffect(creature, effectId)` seam (game.cpp:12679). */
    var sendAttachedEffectTo: (Player, Creature, Int) -> Unit = { _, _, _ -> }

    /** `Player::attachedEffects().sendDetachEffect(creature, effectId)` seam (game.cpp:12689). */
    var sendDetachEffectTo: (Player, Creature, Int) -> Unit = { _, _, _ -> }

    /** `Player::attachedEffects().sendShader(creature, shaderName)` seam (game.cpp:12699). */
    var sendCreatureShader: (Player, Creature, String) -> Unit = { _, _, _ -> }

    /** `Player::sendUpdateContainerItem(container, slot, item)` protocol seam (game.cpp:12743). */
    var sendUpdateContainerItem: (Player, Container, Int, Item) -> Unit = { _, _, _, _ -> }

    /** `Creature::getType()` (`CreatureType_t`) seam used by [updateCreatureType]. */
    var getCreatureType: (Creature) -> CreatureType = { creature ->
        when (creature) {
            is Player -> CreatureType.PLAYER
            is Monster -> CreatureType.MONSTER
            is Npc -> CreatureType.NPC
            else -> CreatureType.MONSTER
        }
    }

    /**
     * `Creature::isHealthHidden()` (creature.hpp) seam; the Kotlin [Creature]
     * does not carry the visibility flag yet (PARITY GAP).
     */
    var isHealthHidden: (Creature) -> Boolean = { false }

    /**
     * `Creature::getShader()` (creature.hpp) seam; the Kotlin [Creature] does
     * not carry the attached shader yet (PARITY GAP).
     */
    var getCreatureShader: (Creature) -> String = { "" }

    /** `Player::getHelpers()` (player.hpp) seam; the Kotlin [Player] does not model the helpers counter yet (PARITY GAP). */
    var getPlayerHelpers: (Player) -> Int = { 0 }

    /**
     * `Player::getThingIndex(item)` (player.cpp) inventory scan backing
     * [refreshItem]. The Kotlin [Player] is not a `Cylinder` (PARITY GAP), so
     * the default resolves the item through the inventory slots.
     */
    var playerThingIndex: (Player, Item) -> Int = { player, item ->
        Slots_t.entries.firstOrNull { player.getInventoryItem(it) === item }?.value ?: -1
    }

    // --- world-state updaters (game.cpp:7808-7831, 9326-9376, 9535-9590, 11825-11880, 12674-12760) ---

    /** `Game::changeLight` (src/game/game.cpp:7808): the creature light reaches every multifloor player spectator. */
    fun changeLight(creature: Creature) {
        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val player = spectator as? Player ?: continue
            sendCreatureLight(player, creature)
        }
    }

    /** `Game::updateCreatureIcon` (src/game/game.cpp:7815). */
    fun updateCreatureIcon(creature: Creature) {
        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val player = spectator as? Player ?: continue
            sendCreatureIcon(player, creature)
        }
    }

    /**
     * `Game::reloadCreature` (src/game/game.cpp:7822): a null creature is
     * logged, otherwise every same-floor player spectator reloads it.
     */
    fun reloadCreature(creature: Creature?) {
        if (creature == null) {
            logger.error { "[reloadCreature] Creature is nullptr" }
            return
        }

        for (spectator in spectatorsOf(creature.position, multifloor = false)) {
            val player = spectator as? Player ?: continue
            reloadCreatureClient(player, creature)
        }
    }

    /** `Game::sendUpdateCreature` (src/game/game.cpp:11872). */
    fun sendUpdateCreature(creature: Creature?) {
        if (creature == null) {
            return
        }

        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val player = spectator as? Player ?: continue
            sendUpdateCreatureClient(player, creature)
        }
    }

    /**
     * `Party::updatePlayerMana(target, manaPercent)` (party.cpp:846-863): every
     * online member and the leader within `PARTY_LIST_MAX_DISTANCE` receive
     * `Player::sendPartyPlayerMana`.
     */
    var partyUpdatePlayerMana: (Party, Player, Int) -> Unit = mana@{ party, target, manaPercent ->
        val leader = party.getLeader() ?: return@mana
        val maxDistance = partyListMaxDistance()
        for (member in party.getMembers()) {
            if (withinPartyListDistance(target, member, maxDistance)) {
                member.sendPartyPlayerMana(target, manaPercent)
            }
        }
        if (withinPartyListDistance(target, leader, maxDistance)) {
            leader.sendPartyPlayerMana(target, manaPercent)
        }
    }

    /**
     * `Party::updatePlayerVocation(target)` (party.cpp:865-882): every online
     * member and the leader within `PARTY_LIST_MAX_DISTANCE` receive
     * `Player::sendPartyPlayerVocation`.
     */
    var partyUpdatePlayerVocation: (Party, Player) -> Unit = vocation@{ party, target ->
        val leader = party.getLeader() ?: return@vocation
        val maxDistance = partyListMaxDistance()
        for (member in party.getMembers()) {
            if (withinPartyListDistance(target, member, maxDistance)) {
                member.sendPartyPlayerVocation(target)
            }
        }
        if (withinPartyListDistance(target, leader, maxDistance)) {
            leader.sendPartyPlayerVocation(target)
        }
    }

    /** `g_configManager().getNumber(PARTY_LIST_MAX_DISTANCE)` behind the party updates (configmanager.cpp). */
    private fun partyListMaxDistance(): Int = configManager?.let { config ->
        ConfigKeys.byName("PARTY_LIST_MAX_DISTANCE")?.let { key ->
            config.getNumber(key, key.defaultValue as Int)
        }
    } ?: 0

    /** `maxDistance == 0 || (getDistanceX <= max && getDistanceY <= max)` (party.cpp:835). */
    private fun withinPartyListDistance(from: Player, to: Player, maxDistance: Int): Boolean {
        if (maxDistance == 0) {
            return true
        }

        return getDistanceX(from.position, to.position) <= maxDistance &&
            getDistanceY(from.position, to.position) <= maxDistance
    }

    /** `Game::addPlayerMana` (src/game/game.cpp:9326): the party mana percent reaches the online party only. */
    fun addPlayerMana(target: Player) {
        val party = target.getParty() ?: return
        val manaPercent = ceil((target.mana.toDouble() / maxOf(target.maxMana, 1)) * 100).toInt()
        partyUpdatePlayerMana(party, target, manaPercent)
    }

    /** `Game::addPlayerVocation` (src/game/game.cpp:9333). */
    fun addPlayerVocation(target: Player) {
        target.getParty()?.let { partyUpdatePlayerVocation(it, target) }

        for (spectator in spectatorsOf(target.position, multifloor = true)) {
            val player = spectator as? Player ?: continue
            sendPlayerVocation(player, target)
        }
    }

    /** `Game::removeMagicEffect` (src/game/game.cpp:9365). */
    fun removeMagicEffect(pos: Position, effect: Int) {
        for (spectator in spectatorsOf(pos, multifloor = true)) {
            val player = spectator as? Player ?: continue
            removeMagicEffectFromClient(player, pos, effect)
        }
    }

    /**
     * `Game::addDistanceEffect` (src/game/game.cpp:9378-9400): the spectator
     * set merges the player queries at both endpoints.
     */
    fun addDistanceEffect(fromPos: Position, toPos: Position, effect: Int, source: Creature? = null) {
        val spectators = Spectators(map)
            .find<Player>(fromPos, multifloor = false)
            .find<Player>(toPos, multifloor = false)
        for (spectator in spectators) {
            val player = spectator as? Player ?: continue
            sendDistanceShoot(player, fromPos, toPos, effect, classifyGraphicalEffectSource(source, spectator))
        }
    }

    /** `Game::hasEffect` (src/game/game.cpp:11825): every `MagicEffectClasses` value below `CONST_ME_LAST`. */
    fun hasEffect(effectId: Int): Boolean = effectId >= 0 && effectId < CONST_ME_LAST

    /** `Game::hasDistanceEffect` (src/game/game.cpp:11835): every `ShootType_t` value up to `CONST_ANI_LAST`. */
    fun hasDistanceEffect(effectId: Int): Boolean = effectId >= 0 && effectId <= CONST_ANI_LAST

    /** `Game::updateCreatureWalkthrough` (src/game/game.cpp:9535). */
    fun updateCreatureWalkthrough(creature: Creature) {
        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val player = spectator as? Player ?: continue
            sendCreatureWalkthrough(player, creature, player.canWalkthroughEx(creature))
        }
    }

    /** `Game::updateCreatureSkull` (src/game/game.cpp:9543): PvP worlds only. */
    fun updateCreatureSkull(creature: Creature) {
        if (getWorldType() != WorldType.PVP) {
            return
        }

        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val player = spectator as? Player ?: continue
            sendCreatureSkull(player, creature)
        }
    }

    /** `Game::updatePlayerShield` (src/game/game.cpp:9553). */
    fun updatePlayerShield(player: Player) {
        for (spectator in spectatorsOf(player.position, multifloor = true)) {
            val spectatorPlayer = spectator as? Player ?: continue
            sendCreatureShield(spectatorPlayer, player)
        }
    }

    /**
     * `Game::updateCreatureType` (src/game/game.cpp:9559): a player-controlled
     * summon is `CREATURETYPE_SUMMON_OTHERS`/`CREATURETYPE_SUMMON_PLAYER`, a
     * health-hidden creature is `CREATURETYPE_HIDDEN`.
     */
    fun updateCreatureType(creature: Creature?) {
        if (creature == null) {
            return
        }

        var masterPlayer: Player? = null
        var creatureType = getCreatureType(creature)
        if (creatureType == CreatureType.MONSTER) {
            val master = creature.getMaster()
            if (master != null) {
                masterPlayer = master as? Player
                if (masterPlayer != null) {
                    creatureType = CreatureType.SUMMON_OTHERS
                }
            }
        }
        if (isHealthHidden(creature)) {
            creatureType = CreatureType.HIDDEN
        }

        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val player = spectator as? Player ?: continue
            val type = if (creatureType == CreatureType.SUMMON_OTHERS && masterPlayer === player) {
                CreatureType.SUMMON_PLAYER
            } else {
                creatureType
            }
            sendCreatureType(player, creature, type)
        }
    }

    /** `Game::sendAttachedEffect` (src/game/game.cpp:12674). */
    fun sendAttachedEffect(creature: Creature, effectId: Int) {
        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val player = spectator as? Player ?: continue
            sendAttachedEffectTo(player, creature, effectId)
        }
    }

    /** `Game::sendDetachEffect` (src/game/game.cpp:12684). */
    fun sendDetachEffect(creature: Creature, effectId: Int) {
        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val player = spectator as? Player ?: continue
            sendDetachEffectTo(player, creature, effectId)
        }
    }

    /** `Game::updateCreatureShader` (src/game/game.cpp:12694). */
    fun updateCreatureShader(creature: Creature) {
        val shader = getCreatureShader(creature)
        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val player = spectator as? Player ?: continue
            sendCreatureShader(player, creature, shader)
        }
    }

    /** `Game::updatePlayerHelpers` (src/game/game.cpp:9690). */
    fun updatePlayerHelpers(player: Player?) {
        if (player == null) {
            return
        }

        val helpers = getPlayerHelpers(player)
        for (spectator in spectatorsOf(player.position, multifloor = true)) {
            val spectatorPlayer = spectator as? Player ?: continue
            sendCreatureHelpers(spectatorPlayer, player.id, helpers)
        }
    }

    /**
     * `Game::refreshItem` (src/game/game.cpp:12716): inventory items refresh
     * through `sendInventoryItem`, container items through the 2x2 spectator
     * `sendUpdateContainerItem`, tile items through every multifloor player
     * `sendUpdateTileItem`.
     */
    fun refreshItem(item: Item?) {
        if (item == null) {
            return
        }

        val parent = item.getParent() ?: return
        val creature = parent.getCreature()
        if (creature != null) {
            val player = creature as? Player ?: return
            val index = playerThingIndex(player, item)
            if (index > -1) {
                val slot = Slots_t.entries.firstOrNull { it.value == index } ?: return
                player.sendInventoryItem(slot, item)
            }
            return
        }

        val container = parent.getContainer()
        if (container != null) {
            val index = container.getThingIndex(item)
            if (index > -1 && index <= 0xFFFF) {
                val containerPosition = container.getTile()?.position
                if (containerPosition != null) {
                    val spectators = Spectators(map).find<Player>(containerPosition, false, 2, 2, 2, 2)
                    for (spectator in spectators) {
                        val player = spectator as? Player ?: continue
                        sendUpdateContainerItem(player, container, index, item)
                    }
                }
            }
            return
        }

        val tile = parent.getTile() ?: return
        for (spectator in spectatorsOf(tile.position, multifloor = true)) {
            val player = spectator as? Player ?: continue
            sendUpdateTileItem(player, tile, tile.position, item)
        }
    }

    /** `Game::playerSetTyping` (src/game/game.cpp:12704). */
    fun playerSetTyping(playerId: Long, typing: Int) {
        val player = getPlayerByID(playerId) ?: return
        for (spectator in spectatorsOf(player.position, multifloor = true)) {
            val spectatorPlayer = spectator as? Player ?: continue
            sendPlayerTyping(spectatorPlayer, player, typing)
        }
    }

    /**
     * `Game::kickPlayer` (src/game/game.cpp:9805): unknown ids are ignored,
     * otherwise `Player::removePlayer(displayEffect)` disconnects the client.
     * The Kotlin `Player` does not own its connection (PARITY GAP), so the
     * disconnect runs through the injected [playerRemovePlayer] seam.
     */
    fun kickPlayer(playerId: Long, displayEffect: Boolean) {
        val player = getPlayerByID(playerId) ?: return
        playerRemovePlayer(player, displayEffect)
    }

    /** `Player::removePlayer(displayEffect)` (player.cpp) disconnect seam behind [kickPlayer]. */
    var playerRemovePlayer: (Player, Boolean) -> Unit = { _, _ -> }

    /**
     * `Game::playerStopAutoWalk` (src/game/game.cpp:4567): unknown ids are
     * ignored, otherwise `Player::stopWalk` latches `cancelNextWalk`.
     */
    fun playerStopAutoWalk(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        player.stopWalk()
    }

    /**
     * `Game::playerAutoWalk` (src/game/game.cpp:4472): resets the login
     * protection/idle timers, clears the queued walk task and starts the walk
     * with the C++ first-step policy (immediate for a single step).
     */
    fun playerAutoWalk(playerId: Long, listDir: List<Direction>) {
        val player = getPlayerByID(playerId) ?: return

        resetLoginProtection(player)
        resetIdleTime(player)
        setNextWalkTask(player, null)
        val startPolicy =
            if (listDir.size == 1) WalkStartPolicy.ImmediateWhenReady else WalkStartPolicy.RespectDelay
        player.startAutoWalk(listDir, false, startPolicy)
    }

    // --- say handlers (game.cpp:7411-7584) -------------------------------------

    /** `g_talkActions().checkPlayerCanSayTalkAction(player, type, words)` (talkactions.cpp) seam. */
    var checkPlayerCanSayTalkAction: (Player, SpeakClasses, String) -> TalkActionResult =
        { _, _, _ -> TalkActionResult.TALKACTION_CONTINUE }

    /** `g_spells().playerSaySpell(player, words)` (spells.cpp:102) seam. */
    var spellsPlayerSaySpell: (Player, String) -> PlayerSaySpellResult =
        { _, words -> PlayerSaySpellResult(TalkActionResult.TALKACTION_CONTINUE, words) }

    /** `Player::saySpell(type, words, false)` (player.cpp:2591) protocol seam. */
    var playerSaySpellWords: (Player, SpeakClasses, String, Boolean) -> Boolean = { _, _, _, _ -> false }

    /** `g_configManager().getBoolean(PUSH_WHEN_ATTACKING)` default (configmanager.cpp) behind the spell branch. */
    var pushWhenAttacking: Boolean = false

    /** `Player::sendCreatureSay(speaker, type, text)` (player.cpp) protocol seam behind [playerWhisper]. */
    var sendCreatureSayTo: (Player, Creature, SpeakClasses, String) -> Unit = { recipient, speaker, type, text ->
        chatClient.sendCreatureSay(recipient, speaker, type, text)
    }

    /**
     * `Creature::onCreatureSay(speaker, type, text)` event hook
     * (creature.cpp:1563) behind [playerWhisper] and [playerSpeakTo]. The base
     * [Creature] hook is not ported yet (PARITY GAP), so the default dispatches
     * the npc dialogue engine and the monster `creatureSayEvent`
     * (`Monster::onCreatureSay`, monster.cpp:568-598) through [monsterHost].
     */
    var onCreatureSayEvent: (Creature, Creature, SpeakClasses, String) -> Unit = { target, speaker, type, text ->
        when (target) {
            is Npc -> target.onCreatureSay(speaker, type.value, text)
            is Monster -> monsterHost?.onCreatureSay(target, speaker, type, text)
            else -> Unit
        }
    }

    /** `g_chat().talkToChannel(player, type, text, channelId)` (chat.cpp:236) seam. */
    var talkToChannel: (Player, SpeakClasses, String, Int) -> Unit = { player, type, text, channelId ->
        chatManager?.talkToChannel(player, type, text, channelId)
    }

    /**
     * `Game::playerSay` (src/game/game.cpp:7411): resets the login/idle
     * timers, lets the talk-action/spell pipeline consume the input, gates the
     * muted speaker, feeds `Player::removeMessageBuffer` (game.cpp:7436-7438)
     * for every type except the private NPC whisper and dispatches on the talk
     * type. `Game::internalCreatureSay` runs through its seam.
     */
    fun playerSay(playerId: Long, channelId: Int, type: SpeakClasses, receiver: String, text: String) {
        val player = getPlayerByID(playerId) ?: return

        resetLoginProtection(player)
        resetIdleTime(player)

        if (playerSaySpell(player, type, text)) {
            return
        }

        val muteTime = player.isMuted()
        if (muteTime > 0) {
            sendTextMessage(player, MessageClass.MESSAGE_FAILURE, "You are still muted for $muteTime seconds.")
            return
        }

        if (text.isNotEmpty() && text.first() == '/' && player.isAccessPlayer()) {
            return
        }

        if (type != SpeakClasses.TALKTYPE_PRIVATE_PN) {
            player.removeMessageBuffer()
        }

        when (type) {
            SpeakClasses.TALKTYPE_SAY -> internalCreatureSay(player, SpeakClasses.TALKTYPE_SAY, text, false, emptyList())
            SpeakClasses.TALKTYPE_WHISPER -> playerWhisper(player, text)
            SpeakClasses.TALKTYPE_YELL -> playerYell(player, text)
            SpeakClasses.TALKTYPE_PRIVATE_TO, SpeakClasses.TALKTYPE_PRIVATE_RED_TO ->
                playerSpeakTo(player, type, receiver, text)
            SpeakClasses.TALKTYPE_CHANNEL_O, SpeakClasses.TALKTYPE_CHANNEL_Y, SpeakClasses.TALKTYPE_CHANNEL_R1 ->
                talkToChannel(player, type, text, channelId)
            SpeakClasses.TALKTYPE_PRIVATE_PN -> playerSpeakToNpc(player, text)
            SpeakClasses.TALKTYPE_BROADCAST -> playerBroadcastMessage(player, text)
            else -> Unit
        }
    }

    /**
     * `Game::playerSaySpell` (src/game/game.cpp:7477): a talk action break
     * consumes the input, a spell break cancels the push (unless
     * `PUSH_WHEN_ATTACKING`) and sends the spell words, a spell failure
     * consumes the input.
     */
    fun playerSaySpell(player: Player, type: SpeakClasses, text: String): Boolean {
        val talkResult = checkPlayerCanSayTalkAction(player, type, text)
        if (talkResult == TalkActionResult.TALKACTION_BREAK) {
            return true
        }

        val spellResult = spellsPlayerSaySpell(player, text)
        if (spellResult.action == TalkActionResult.TALKACTION_BREAK) {
            if (!pushWhenAttacking) {
                cancelPush(player)
            }
            return playerSaySpellWords(player, type, spellResult.words, false)
        } else if (spellResult.action == TalkActionResult.TALKACTION_FAILED) {
            return true
        }

        return false
    }

    /**
     * `Game::playerWhisper` (src/game/game.cpp:7497-7515): every
     * `MAP_MAX_CLIENT_VIEW_PORT_X`x`MAP_MAX_CLIENT_VIEW_PORT_Y` player
     * spectator outside the 1x1 radius receives `pspsps`, then the event hook
     * runs on every player spectator.
     */
    fun playerWhisper(player: Player, text: String) {
        val spectators = Spectators(map)
            .find<Player>(
                player.position,
                multifloor = false,
                minRangeX = Creature.MAP_MAX_CLIENT_VIEW_PORT_X,
                maxRangeX = Creature.MAP_MAX_CLIENT_VIEW_PORT_X,
                minRangeY = Creature.MAP_MAX_CLIENT_VIEW_PORT_Y,
                maxRangeY = Creature.MAP_MAX_CLIENT_VIEW_PORT_Y,
            )

        for (spectator in spectators) {
            val spectatorPlayer = spectator as? Player ?: continue
            if (!areInRange(player.position, spectatorPlayer.position, 1, 1)) {
                sendCreatureSayTo(spectatorPlayer, player, SpeakClasses.TALKTYPE_WHISPER, "pspsps")
            } else {
                sendCreatureSayTo(spectatorPlayer, player, SpeakClasses.TALKTYPE_WHISPER, text)
            }
        }

        for (spectator in spectators) {
            onCreatureSayEvent(spectator, player, SpeakClasses.TALKTYPE_WHISPER, text)
        }
    }

    /** `Game::playerYell` (src/game/game.cpp:7517): level 1 and an active `CONDITION_YELLTICKS` are rejected. */
    fun playerYell(player: Player, text: String): Boolean {
        if (player.level == 1) {
            sendTextMessage(player, MessageClass.MESSAGE_FAILURE, "You may not yell as long as you are on level 1.")
            return false
        }

        if (player.hasCondition(ConditionType.CONDITION_YELLTICKS)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return false
        }

        if (player.getAccountType().value < AccountType.ACCOUNT_TYPE_GAMEMASTER.value) {
            player.addCondition(
                Condition.createCondition(
                    id = ConditionId.CONDITIONID_DEFAULT,
                    type = ConditionType.CONDITION_YELLTICKS,
                    ticks = YELL_TICKS,
                ),
            )
        }

        internalCreatureSay(player, SpeakClasses.TALKTYPE_YELL, text.uppercase(), false, emptyList())
        return true
    }

    /**
     * `Game::playerSpeakTo` (src/game/game.cpp:7537): the receiver resolves
     * online-only, the red-private speak class is rewritten to the from-side
     * variant, and a ghost receiver is hidden from non-access speakers.
     */
    fun playerSpeakTo(player: Player, type: SpeakClasses, receiver: String, text: String): Boolean {
        val toPlayer = getPlayerByName(receiver) ?: run {
            sendTextMessage(player, MessageClass.MESSAGE_FAILURE, "A player with this name is not online.")
            return false
        }

        val fromType = if (type == SpeakClasses.TALKTYPE_PRIVATE_RED_TO &&
            (player.hasFlag(PlayerFlags_t.CanTalkRedPrivate) ||
                player.getAccountType().value >= AccountType.ACCOUNT_TYPE_GAMEMASTER.value)
        ) {
            SpeakClasses.TALKTYPE_PRIVATE_RED_FROM
        } else {
            SpeakClasses.TALKTYPE_PRIVATE_FROM
        }

        sendPrivateMessage(toPlayer, player, fromType, text)
        onCreatureSayEvent(toPlayer, player, fromType, text)

        if (isInGhostMode(toPlayer) && !player.isAccessPlayer()) {
            sendTextMessage(player, MessageClass.MESSAGE_FAILURE, "A player with this name is not online.")
        } else {
            sendTextMessage(player, MessageClass.MESSAGE_FAILURE, "Message sent to ${toPlayer.name}.")
        }
        return true
    }

    /**
     * `Game::playerSpeakToNpc` (src/game/game.cpp:7563): exhausted speakers
     * are rejected, every nearby npc the player may speak with receives the
     * private-npc say, then the UI exhaust timer is refreshed.
     */
    fun playerSpeakToNpc(player: Player?, text: String) {
        if (player == null) {
            logger.error { "[playerSpeakToNpc] Player is nullptr" }
            return
        }

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        val npcs = Spectators(map).find<Creature>(player.position).filter<Npc>()
        for (spectator in npcs) {
            val npc = spectator as? Npc ?: continue
            if (!canSpeakWithHireling(player, getNpcSpeechBubble(npc))) {
                continue
            }

            npc.onCreatureSay(player, SpeakClasses.TALKTYPE_PRIVATE_PN.value, text)
        }

        updateUIExhausted(player)
    }

    // --- channel handlers (game.cpp:4324-4441) ---------------------------------

    /**
     * `Game::playerCreatePrivateChannel` (src/game/game.cpp:4324): premium only;
     * creates the `CHANNEL_PRIVATE` slot through the injected chat manager and
     * sends it to the owner.
     */
    fun playerCreatePrivateChannel(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        if (!player.isPremium()) {
            return
        }

        val channel = chatManager?.createChannel(player, CHANNEL_PRIVATE) ?: return
        if (!channel.addUser(player)) {
            return
        }

        sendCreatePrivateChannel(player, channel.getId(), channel.getName())
    }

    /** `Game::playerChannelInvite` (src/game/game.cpp:4338). */
    fun playerChannelInvite(playerId: Long, name: String) {
        val player = getPlayerByID(playerId) ?: return
        val channel = chatManager?.getPrivateChannel(player) ?: return
        val invitePlayer = getPlayerByName(name) ?: return
        if (player === invitePlayer) {
            return
        }

        channel.invitePlayer(player, invitePlayer)
    }

    /** `Game::playerChannelExclude` (src/game/game.cpp:4361). */
    fun playerChannelExclude(playerId: Long, name: String) {
        val player = getPlayerByID(playerId) ?: return
        val channel = chatManager?.getPrivateChannel(player) ?: return
        val excludePlayer = getPlayerByName(name) ?: return
        if (player === excludePlayer) {
            return
        }

        channel.excludePlayer(player, excludePlayer)
    }

    /** `Game::playerRequestChannels` (src/game/game.cpp:4384). */
    fun playerRequestChannels(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        sendChannelsDialog(player)
    }

    /**
     * `Game::playerOpenChannel` (src/game/game.cpp:4393): joins the channel and
     * sends it with its users (non-public channels only) and invited users.
     */
    fun playerOpenChannel(playerId: Long, channelId: Int) {
        val player = getPlayerByID(playerId) ?: return
        val channel = chatManager?.addUserToChannel(player, channelId) ?: return

        val invitedUsers = channel.getInvitedUsers()
        val users = if (!channel.isPublicChannel()) channel.getUsers() else null

        sendChannel(player, channel.getId(), channel.getName(), users, invitedUsers)
    }

    /** `Game::playerCloseChannel` (src/game/game.cpp:4415). */
    fun playerCloseChannel(playerId: Long, channelId: Int) {
        val player = getPlayerByID(playerId) ?: return
        chatManager?.removeUserFromChannel(player, channelId)
    }

    /**
     * `Game::playerOpenPrivateChannel` (src/game/game.cpp:4424): the receiver
     * name is validated through the `IOLoginData::formatPlayerName` seam, self
     * channels and unknown names are rejected with the C++ cancel messages.
     */
    fun playerOpenPrivateChannel(playerId: Long, receiver: String) {
        val player = getPlayerByID(playerId) ?: return

        val formattedReceiver = formatPlayerName(receiver)
        if (formattedReceiver == null) {
            sendCancelMessageText(player, "A player with this name does not exist.")
            return
        }

        if (player.name == formattedReceiver) {
            sendCancelMessageText(player, "You cannot set up a private message channel with yourself.")
            return
        }

        sendOpenPrivateChannel(player, formattedReceiver)
    }

    /** `Game::canThrowObjectTo` (src/game/game.cpp:7591): direct `Map::canThrowObjectTo` delegate. */
    fun canThrowObjectTo(
        fromPos: Position,
        toPos: Position,
        lineOfSight: Int = GameMap.SightLine_CheckSightLine,
        rangex: Int = GameMap.MAP_MAX_CLIENT_VIEW_PORT_X,
        rangey: Int = GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y,
    ): Boolean = map.canThrowObjectTo(fromPos, toPos, lineOfSight, rangex, rangey)

    /** `Game::isSightClear` (src/game/game.cpp:7595): direct `Map::isSightClear` delegate. */
    fun isSightClear(fromPos: Position, toPos: Position, floorCheck: Boolean): Boolean =
        map.isSightClear(fromPos, toPos, floorCheck)

    /**
     * `Game::internalCreatureTurn` (src/game/game.cpp:7599): equal directions are
     * a no-op, a player source cancels its push, the direction is only written
     * when not locked, and every player spectator receives `sendCreatureTurn`.
     */
    fun internalCreatureTurn(creature: Creature, dir: Direction): Boolean {
        if (creature.direction == dir) {
            return false
        }

        val player = creature as? Player
        if (player != null) {
            cancelPush(player)
        }

        if (!isDirectionLocked(creature)) {
            creature.direction = dir
        }

        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val spectatorPlayer = spectator as? Player ?: continue
            sendCreatureTurn(spectatorPlayer, creature)
        }
        return true
    }

    /**
     * `Game::playerTurn` (src/game/game.cpp:7187): the event and callback vetoes
     * run before the login-protection/idle resets and the turn itself.
     */
    fun playerTurn(playerId: Long, dir: Direction) {
        val player = getPlayerByID(playerId) ?: return
        if (!eventPlayerOnTurn(player, dir)) {
            return
        }

        if (!callbackPlayerOnTurn(player, dir)) {
            return
        }

        resetLoginProtection(player)
        resetIdleTime(player)
        internalCreatureTurn(player, dir)
    }

    /**
     * `Game::internalMoveCreature(creature, direction)` (src/game/game.cpp:1909).
     * A creature without base speed is `NOTMOVABLE`, the last position is
     * recorded before the move and players additionally try the z-level
     * up/down steps (`TILESTATE_HASHEIGHT`, `TILESTATE_BLOCKSOLID`,
     * `TILESTATE_FLOORCHANGE`).
     */
    fun internalMoveCreature(creature: Creature?, direction: Direction, flags: Int = 0): ReturnValue {
        if (creature == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        if (creature.getBaseSpeed() == 0) {
            return ReturnValue.NOTMOVABLE
        }

        creature.setLastPosition(creature.position)
        val currentPos = creature.position
        var destPos = getNextPosition(direction, currentPos)
        val player = creature as? Player

        // `DIRECTION_DIAGONAL_MASK` (src/enums/creature_definitions.hpp).
        val diagonalMovement = direction.dx != 0 && direction.dy != 0
        var moveFlags = flags
        if (player != null && !diagonalMovement) {
            // try go up
            val tile = creature.getTile()
            if (currentPos.z != 8 && tile != null && tile.hasHeight(3)) {
                var tmpTile = map.getTile(Position(currentPos.x, currentPos.y, currentPos.z - 1))
                if (tmpTile == null || (tmpTile.ground == null && !tmpTile.hasFlag(TileFlags.TILESTATE_BLOCKSOLID.value))) {
                    tmpTile = map.getTile(Position(destPos.x, destPos.y, destPos.z - 1))
                    if (tmpTile != null && tmpTile.ground != null && !tmpTile.hasFlag(TileFlags.TILESTATE_BLOCKSOLID.value)) {
                        moveFlags = moveFlags or CylinderFlags.FLAG_IGNOREBLOCKITEM or CylinderFlags.FLAG_IGNOREBLOCKCREATURE

                        if (!tmpTile.hasFlag(TileFlags.TILESTATE_FLOORCHANGE.value)) {
                            player.direction = direction
                            destPos = Position(destPos.x, destPos.y, destPos.z - 1)
                        }
                    }
                }
            }

            // try go down
            if (currentPos.z != 7 && currentPos.z == destPos.z) {
                var tmpTile = map.getTile(Position(destPos.x, destPos.y, destPos.z))
                if (tmpTile == null || (tmpTile.ground == null && !tmpTile.hasFlag(TileFlags.TILESTATE_BLOCKSOLID.value))) {
                    tmpTile = map.getTile(Position(destPos.x, destPos.y, destPos.z + 1))
                    if (tmpTile != null && tmpTile.hasHeight(3)) {
                        moveFlags = moveFlags or CylinderFlags.FLAG_IGNOREBLOCKITEM or CylinderFlags.FLAG_IGNOREBLOCKCREATURE
                        player.direction = direction
                        destPos = Position(destPos.x, destPos.y, destPos.z + 1)
                    }
                }
            }
        }

        val toTile = map.getTile(destPos) ?: return ReturnValue.NOTPOSSIBLE
        return internalMoveCreature(creature, toTile, moveFlags)
    }

    /**
     * `Game::internalMoveCreature(creature, toTile)` (src/game/game.cpp:1963):
     * rooted creatures are rejected, the tile `queryAdd` and the feared
     * magic-field gate run before `Map::moveCreature`, and the
     * `queryDestination` chain follows floor changes. The facade's
     * [placedCreatures] index is refreshed in place of the unported map-sector
     * creature lists (PARITY GAP).
     */
    fun internalMoveCreature(creature: Creature, toTile: Tile, flags: Int = 0): ReturnValue {
        if (creature.hasCondition(ConditionType.CONDITION_ROOTED)) {
            return ReturnValue.NOTPOSSIBLE
        }

        // check if we can move the creature to the destination
        var ret = toTile.queryAdd(0, creature, 1, flags, null)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        if (creature.hasCondition(ConditionType.CONDITION_FEARED)) {
            val field = toTile.getFieldItem()
            if (field != null && !field.isBlocking() && field.getDamage() != 0) {
                return ReturnValue.NOTPOSSIBLE
            }
        }

        map.moveCreature(creature, toTile)
        placedCreatures[creature] = toTile
        if (creature.getParent() !== toTile) {
            return ReturnValue.NOERROR
        }

        var index = 0
        var toItem: Item? = null
        var toCylinder = toTile
        var fromCylinder: Tile? = null
        var n = 0
        var moveFlags = flags

        while (true) {
            val result = toCylinder.queryDestination(index, creature, toItem, moveFlags)
            val subCylinder = result.cylinder?.getTile() ?: break
            if (subCylinder === toCylinder) {
                break
            }

            map.moveCreature(creature, subCylinder)
            placedCreatures[creature] = subCylinder

            if (creature.getParent() !== subCylinder) {
                // could happen if a script move the creature
                fromCylinder = null
                break
            }

            fromCylinder = toCylinder
            toCylinder = subCylinder
            toItem = result.destItem
            moveFlags = 0

            // to prevent infinite loop
            if (++n >= MAP_MAX_LAYERS) {
                break
            }
        }

        if (fromCylinder != null) {
            val fromPosition = fromCylinder.position
            val toPosition = toCylinder.position
            if (fromPosition.z != toPosition.z && (fromPosition.x != toPosition.x || fromPosition.y != toPosition.y)) {
                val dir = getDirectionTo(fromPosition, toPosition)
                if (dir != null && !(dir.dx != 0 && dir.dy != 0)) {
                    internalCreatureTurn(creature, dir)
                }
            }
        }

        return ReturnValue.NOERROR
    }

    /**
     * `Game::playerMove` (src/game/game.cpp:4282): reset the walk state and
     * admit a single-step non-forced immediate walk.
     */
    fun playerMove(playerId: Long, direction: Direction) {
        val player = getPlayerByID(playerId) ?: return

        resetLoginProtection(player)
        resetIdleTime(player)
        setNextWalkActionTask(player, null)
        cancelPush(player)

        startAutoWalk(player, listOf(direction), false)
    }

    /** `Game::forcePlayerMove` (src/game/game.cpp:4296): as [playerMove] with the forced walk flag. */
    fun forcePlayerMove(playerId: Long, direction: Direction) {
        val player = getPlayerByID(playerId) ?: return

        resetLoginProtection(player)
        resetIdleTime(player)
        setNextWalkActionTask(player, null)
        cancelPush(player)

        startAutoWalk(player, listOf(direction), true)
    }

    /**
     * `Game::playerTeleport` (src/game/game.cpp:1622): only players with
     * `CanMapClickTeleport` may map-click teleport; a non-`NOERROR` result sends
     * the cancel message.
     */
    fun playerTeleport(playerId: Long, newPosition: Position) {
        val player = getPlayerByID(playerId) ?: return
        if (!player.hasFlag(PlayerFlags_t.CanMapClickTeleport)) {
            return
        }

        val returnValue = internalTeleport(player, newPosition, false)
        if (returnValue != ReturnValue.NOERROR) {
            sendCancelMessage(player, returnValue)
        }
    }

    /**
     * `Game::internalTeleport` (src/game/game.cpp:3540): recursion/rate guarded
     * teleport of a creature (`Map::moveCreature`, `pushMove` inverted into
     * `forceTeleport`) or an item (`internalMoveItem`). The guard state is
     * confined to the single-threaded dispatcher (AGENTS.md 1.2), so the C++
     * mutex around `InternalGame::teleportGuards` is not needed.
     */
    fun internalTeleport(thing: Thing?, newPos: Position, pushMove: Boolean = true, flags: Int = 0): ReturnValue {
        if (thing == null) {
            logger.error { "[internalTeleport] thing is nullptr" }
            return ReturnValue.NOTPOSSIBLE
        }

        val now = otsysTimeMs()
        if (!tryInsertTeleportStack(thing)) {
            logTeleportGuardBlock(thing, "recursive", recordTeleportGuardBlock(thing, now))
            return ReturnValue.NOTPOSSIBLE
        }

        try {
            if (shouldBlockTeleportRate(thing, now)) {
                logTeleportGuardBlock(thing, "rate-limited", recordTeleportGuardBlock(thing, now))
                return ReturnValue.NOTPOSSIBLE
            }

            if (newPos == thing.position) {
                return ReturnValue.CONTACTADMINISTRATOR
            } else if (thing.isRemoved()) {
                return ReturnValue.NOTPOSSIBLE
            }

            val toTile = map.getTile(newPos) ?: return ReturnValue.NOTPOSSIBLE

            val creature = thing.getCreature()
            if (creature != null) {
                val ret = toTile.queryAdd(0, creature, 1, CylinderFlags.FLAG_NOLIMIT, null)
                if (ret != ReturnValue.NOERROR) {
                    return ret
                }

                map.moveCreature(creature, toTile, !pushMove)
                placedCreatures[creature] = toTile

                return ReturnValue.NOERROR
            }

            val item = thing.getItem()
            if (item != null) {
                return internalMoveItem(
                    itemParents[item] ?: (item.getParent() as? Tile),
                    toTile,
                    INDEX_WHEREEVER,
                    item,
                    item.count,
                    flags,
                )
            }
            return ReturnValue.NOTPOSSIBLE
        } finally {
            teleportStack.remove(thing)
        }
    }

    /**
     * `Game::playerSetAttackedCreature` (src/game/game.cpp:7001): clearing the
     * current target resets the login protection, an unknown id clears the
     * target, a rejected target sends the combat return value and only an
     * accepted target is stored and followed by `Player::updateCreatureWalk`.
     */
    fun playerSetAttackedCreature(playerId: Long, creatureId: Long) {
        val player = getPlayerByID(playerId) ?: return

        if (player.getAttackedCreature() != null && creatureId == 0L) {
            resetLoginProtection(player)
            player.setAttackedCreature(null)
            player.sendCancelTarget()
            return
        }

        val attackCreature = getCreatureByID(creatureId)
        if (attackCreature == null) {
            player.setAttackedCreature(null)
            player.sendCancelTarget()
            return
        }

        val ret = canTargetCreature(player, attackCreature)
        if (ret != ReturnValue.NOERROR) {
            sendCancelMessage(player, ret)
            player.sendCancelTarget()
            player.setAttackedCreature(null)
            return
        }

        player.setAttackedCreature(attackCreature)
        updateCreatureWalk(player)
    }

    /**
     * `Game::playerCancelAttackAndFollow` (src/game/game.cpp:6990): clears the
     * attack target, then the follow target and stops the queued walk.
     */
    fun playerCancelAttackAndFollow(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return

        playerSetAttackedCreature(playerId, 0)
        playerFollowCreature(playerId, 0)
        player.stopWalk()
    }

    /**
     * `Game::playerFollowCreature` (src/game/game.cpp:7033): the attack target
     * is cleared, `Player::updateCreatureWalk` runs, then the follow target is
     * stored.
     */
    fun playerFollowCreature(playerId: Long, creatureId: Long) {
        val player = getPlayerByID(playerId) ?: return

        player.setAttackedCreature(null)
        updateCreatureWalk(player)
        player.setFollowCreature(getCreatureByID(creatureId))
    }

    /**
     * `Game::playerSetFightModes` (src/game/game.cpp:7044): stores the fight,
     * chase and secure modes; `Player::setFightMode`'s `sendStats`/`sendSkills`
     * client resend is emitted through the player's sink and the PvP stance is
     * only applied when expert PvP is enabled.
     */
    fun playerSetFightModes(
        playerId: Long,
        fightMode: FightMode,
        chaseMode: Boolean,
        secureMode: Boolean,
        pvpMode: PvpMode_t = PvpMode_t.PVP_MODE_DOVE,
    ) {
        val player = getPlayerByID(playerId) ?: return

        player.fightMode = fightMode
        player.sendStats()
        player.sendSkills()
        player.chaseMode = if (chaseMode) ChaseMode.Follow else ChaseMode.StandStill
        player.secureMode = secureMode
        if (expertPvpEnabled) {
            setPvpMode(player, pvpMode)
        }
    }

    /**
     * `Game::playerLookAt` (src/game/game.cpp:6358): resolves the top visible
     * thing of `STACKPOS_LOOK`, rejects unseen positions and dispatches the
     * `playerOnLook` event/callback with the computed look distance.
     */
    fun playerLookAt(playerId: Long, itemId: Int, pos: Position, stackPos: Int) {
        val player = getPlayerByID(playerId) ?: return

        val thing = internalGetThingForLook(player, pos, stackPos, itemId)
        if (thing == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        val thingPos = thing.position
        if (!player.canSee(thingPos)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        val playerPos = player.position
        val lookDistance =
            if (thing !== player) {
                var distance = maxOf(getDistanceX(playerPos, thingPos), getDistanceY(playerPos, thingPos))
                if (playerPos.z != thingPos.z) {
                    distance += 15
                }
                distance
            } else {
                -1
            }

        eventPlayerOnLook(player, pos, thing, stackPos, lookDistance)
        callbackPlayerOnLook(player, thing, pos, lookDistance)
    }

    /**
     * `Game::playerLookInBattleList` (src/game/game.cpp:6393): the creature must
     * be visible to the player and on a seen position; the same distance
     * computation and event/callback dispatch as [playerLookAt] follow.
     */
    fun playerLookInBattleList(playerId: Long, creatureId: Long) {
        val player = getPlayerByID(playerId) ?: return

        val creature = getCreatureByID(creatureId) ?: return
        if (!player.canSeeCreature(creature)) {
            return
        }

        val creaturePos = creature.position
        if (!player.canSee(creaturePos)) {
            return
        }

        val lookDistance =
            if (creature !== player) {
                val playerPos = player.position
                var distance = maxOf(getDistanceX(playerPos, creaturePos), getDistanceY(playerPos, creaturePos))
                if (playerPos.z != creaturePos.z) {
                    distance += 15
                }
                distance
            } else {
                -1
            }

        eventPlayerOnLookInBattleList(player, creature, lookDistance)
        callbackPlayerOnLookInBattleList(player, creature, lookDistance)
    }

    /**
     * `Game::changeSpeed` (src/game/game.cpp:7747): adds [varSpeedDelta] to the
     * current speed variance and notifies the same-floor player spectators.
     */
    fun changeSpeed(creature: Creature, varSpeedDelta: Int) {
        var varSpeed = creature.getSpeed() - creature.getBaseSpeed()
        varSpeed += varSpeedDelta

        creature.setSpeed(varSpeed)

        // Send to clients
        for (spectator in spectatorsOf(creature.position, multifloor = false)) {
            val spectatorPlayer = spectator as? Player ?: continue
            sendChangeSpeed(spectatorPlayer, creature, creature.getStepSpeed())
        }
    }

    /**
     * `Game::setCreatureSpeed` (src/game/game.cpp:7759): replaces the base speed
     * and notifies the same-floor player spectators.
     */
    fun setCreatureSpeed(creature: Creature, speed: Int) {
        creature.setBaseSpeed(speed)

        // Send creature speed to client
        for (spectator in spectatorsOf(creature.position, multifloor = false)) {
            val spectatorPlayer = spectator as? Player ?: continue
            sendChangeSpeed(spectatorPlayer, creature, creature.getStepSpeed())
        }
    }

    /** `Game::changePlayerSpeed` (src/game/game.cpp:7768): the player-only [changeSpeed] call site. */
    fun changePlayerSpeed(player: Player, varSpeedDelta: Int) {
        var varSpeed = player.getSpeed() - player.getBaseSpeed()
        varSpeed += varSpeedDelta

        player.setSpeed(varSpeed)

        // Send new player speed to the spectators
        for (spectator in spectatorsOf(player.position, multifloor = false)) {
            val spectatorPlayer = spectator as? Player ?: continue
            sendChangeSpeed(spectatorPlayer, player, player.getStepSpeed())
        }
    }

    /**
     * `Game::addCreatureHealth` (src/game/game.cpp:7826) less the forge
     * broadcast: every multifloor player spectator of the creature receives
     * `Player::sendCreatureHealth`.
     */
    fun addCreatureHealth(creature: Creature) {
        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val spectatorPlayer = spectator as? Player ?: continue
            spectatorPlayer.sendCreatureHealth(creature)
        }
    }

    /**
     * `Game::internalCreatureChangeOutfit` (src/game/game.cpp:7780). The event
     * and callback vetoes are injected seams; the invisible creature is stored
     * but not broadcast exactly like the C++ early return.
     */
    fun internalCreatureChangeOutfit(creature: Creature, outfit: Outfit) {
        if (!eventCreatureOnChangeOutfit(creature, outfit)) {
            return
        }

        if (!callbackCreatureOnChangeOutfit(creature, outfit)) {
            return
        }

        creature.setCurrentOutfit(outfit)

        if (creature.isInvisible()) {
            return
        }

        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val spectatorPlayer = spectator as? Player ?: continue
            spectatorPlayer.sendCreatureOutfit(creature, outfit)
        }
    }

    /** `g_events().eventCreatureOnChangeOutfit` veto seam (game.cpp:7781). */
    var eventCreatureOnChangeOutfit: (Creature, Outfit) -> Boolean = { _, _ -> true }

    /** `g_callbacks().checkCallback(creatureOnChangeOutfit, ...)` veto seam (game.cpp:7785). */
    var callbackCreatureOnChangeOutfit: (Creature, Outfit) -> Boolean = { _, _ -> true }

    /**
     * `Game::playerInviteToParty` (src/game/game.cpp:9657): inviting yourself
     * is discarded, the target must be online and not already inviting the
     * player, a target already in a party receives the
     * `MESSAGE_PARTY_MANAGEMENT` notice and a non-leader cannot invite.
     * Creating the party mirrors `Party::create` (party.cpp:22-33) through the
     * [Party] constructor.
     */
    fun playerInviteToParty(playerId: Long, invitedId: Long) {
        // Prevent crafted packets from inviting yourself to a party (using OTClient).
        if (playerId == invitedId) {
            return
        }

        val player = getPlayerByID(playerId) ?: return
        val invitedPlayer = getPlayerByID(invitedId) ?: return
        if (invitedPlayer.isInviting(player)) {
            return
        }

        if (invitedPlayer.getParty() != null) {
            player.sendTextMessageTyped(MessageClass.MESSAGE_PARTY_MANAGEMENT, "${invitedPlayer.name} is already in a party.")
            return
        }

        val party = player.getParty() ?: Party(player)
        if (party.getLeader() !== player) {
            return
        }

        party.invitePlayer(invitedPlayer)
    }

    /**
     * `Game::playerJoinParty` (src/game/game.cpp:9701): the target must be a
     * party leader currently inviting the player; a player already in a party
     * receives the `MESSAGE_PARTY_MANAGEMENT` notice.
     */
    fun playerJoinParty(playerId: Long, leaderId: Long) {
        val player = getPlayerByID(playerId) ?: return
        val leader = getPlayerByID(leaderId) ?: return
        if (!leader.isInviting(player)) {
            return
        }

        val party = leader.getParty() ?: return
        if (party.getLeader() !== leader) {
            return
        }

        if (player.getParty() != null) {
            player.sendTextMessageTyped(MessageClass.MESSAGE_PARTY_MANAGEMENT, "You are already in a party.")
            return
        }

        party.joinParty(player)
    }

    /** `Game::playerRevokePartyInvitation` (src/game/game.cpp:9725). */
    fun playerRevokePartyInvitation(playerId: Long, invitedId: Long) {
        val player = getPlayerByID(playerId) ?: return
        val party = player.getParty() ?: return
        if (party.getLeader() !== player) {
            return
        }

        val invitedPlayer = getPlayerByID(invitedId) ?: return
        if (!player.isInviting(invitedPlayer)) {
            return
        }

        party.revokeInvitation(invitedPlayer)
    }

    /**
     * `Game::playerPassPartyLeadership` (src/game/game.cpp:9744). The
     * `Player::isPartner` gate (player.cpp:7790-7796) is transcribed inline:
     * the new leader shares the same party and is not the current player.
     */
    fun playerPassPartyLeadership(playerId: Long, newLeaderId: Long) {
        val player = getPlayerByID(playerId) ?: return
        val party = player.getParty() ?: return
        if (party.getLeader() !== player) {
            return
        }

        val newLeader = getPlayerByID(newLeaderId) ?: return
        if (!isPartner(player, newLeader)) {
            return
        }

        party.passPartyLeadership(newLeader)
    }

    /**
     * `Game::playerLeaveParty` (src/game/game.cpp:9763): a player with no party
     * or in fight outside a protection zone receives the `MESSAGE_FAILURE`
     * text. `Player::getZoneType` (creature.cpp:1856-1862) resolves from the
     * placed tile via the facade's [placedCreatures] index.
     */
    fun playerLeaveParty(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        val party = player.getParty()
        if (party == null || (player.hasCondition(ConditionType.CONDITION_INFIGHT) && zoneTypeOf(player) != ZoneType.ZONE_PROTECTION)) {
            player.sendTextMessageTyped(MessageClass.MESSAGE_FAILURE, "You cannot leave party, contact the administrator.")
            return
        }

        party.leaveParty(player)
    }

    /**
     * `Game::playerEnableSharedPartyExperience` (src/game/game.cpp:9778): the
     * toggle is dropped without a party or in fight outside a protection-zone
     * tile (`Tile::hasFlag(TILESTATE_PROTECTIONZONE)`).
     */
    fun playerEnableSharedPartyExperience(playerId: Long, sharedExpActive: Boolean) {
        val player = getPlayerByID(playerId) ?: return
        val party = player.getParty() ?: return
        val playerTile = placedCreatures[player]
        if (player.hasCondition(ConditionType.CONDITION_INFIGHT) &&
            playerTile != null &&
            !playerTile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        ) {
            return
        }

        party.setSharedExperience(player, sharedExpActive)
    }

    /**
     * `Game::playerRequestTrade` (src/game/game.cpp:5797): validates the trade
     * target, the sight line, the top-down item and its tradeability, then
     * starts the trade through [internalStartTrade]. The trade-request event
     * and callback may veto.
     *
     * PARITY GAPS: `canThrowObjectTo` ranges are resolved through [map];
     * the `ONLY_INVITED_CAN_MOVE_HOUSE_ITEMS` house-tile check needs
     * `HouseTile`/`Item::getRealParent` and is skipped; the out-of-reach
     * auto-walk retry (`Player::getPathTo`, `queuePlayerAutoWalk`,
     * `Player::setNextWalkActionTask`) is not ported, so the no-path
     * `THEREISNOWAY` cancel is the only reachable outcome.
     */
    fun playerRequestTrade(playerId: Long, pos: Position, stackPos: Int, tradePlayerId: Long, itemId: Int) {
        val player = getPlayerByID(playerId) ?: return
        val tradePartner = getPlayerByID(tradePlayerId)
        if (tradePartner == null || tradePartner === player) {
            player.sendTextMessageTyped(MessageClass.MESSAGE_FAILURE, "Sorry, not possible.")
            return
        }

        if (!areInRange(tradePartner.position, player.position, 2, 2, 0)) {
            player.sendTextMessageTyped(MessageClass.MESSAGE_TRADE, "${tradePartner.name} tells you to move closer.")
            return
        }

        if (!this.canThrowObjectTo.invoke(tradePartner.position, player.position)) {
            sendCancelMessage(player, ReturnValue.CREATUREISNOTREACHABLE)
            return
        }

        val tradeThing = internalGetThing(player, pos, stackPos, itemId, StackPosType_t.STACKPOS_TOPDOWN_ITEM)
        if (tradeThing == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        val tradeItem = tradeThing
        if (tradeItem.id != itemId || !tradeItem.isPickupable() || tradeItem.hasAttribute(ItemAttribute.UNIQUEID)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (tradeItem.isRemoved() || itemParents[tradeItem] == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (tradeItem.isStoreItem() || tradeItem.hasOwner()) {
            sendCancelMessage(player, ReturnValue.ITEMUNTRADEABLE)
            return
        }

        val playerPosition = player.position
        val tradeItemPosition = itemParents[tradeItem]!!.position
        if (playerPosition.z != tradeItemPosition.z) {
            sendCancelMessage(
                player,
                if (playerPosition.z > tradeItemPosition.z) ReturnValue.FIRSTGOUPSTAIRS else ReturnValue.FIRSTGODOWNSTAIRS,
            )
            return
        }

        if (!areInRange(tradeItemPosition, playerPosition, 1, 1)) {
            sendCancelMessage(player, ReturnValue.THEREISNOWAY)
            return
        }

        val tradeItemContainer = tradeItem.getContainer()
        if (tradeItemContainer != null) {
            for (tradedItem in tradeItems.keys) {
                if (tradeItem === tradedItem) {
                    player.sendTextMessageTyped(MessageClass.MESSAGE_TRADE, "This item is already being traded.")
                    return
                }

                if (tradeItemContainer.isHoldingItem(tradedItem)) {
                    player.sendTextMessageTyped(MessageClass.MESSAGE_TRADE, "This item is already being traded.")
                    return
                }

                val container = tradedItem.getContainer()
                if (container != null && container.isHoldingItem(tradeItem)) {
                    player.sendTextMessageTyped(MessageClass.MESSAGE_TRADE, "This item is already being traded.")
                    return
                }
            }
        } else {
            for (tradedItem in tradeItems.keys) {
                if (tradeItem === tradedItem) {
                    player.sendTextMessageTyped(MessageClass.MESSAGE_TRADE, "This item is already being traded.")
                    return
                }

                val container = tradedItem.getContainer()
                if (container != null && container.isHoldingItem(tradeItem)) {
                    player.sendTextMessageTyped(MessageClass.MESSAGE_TRADE, "This item is already being traded.")
                    return
                }
            }
        }

        if (tradeItemContainer != null && tradeItemContainer.getItemHoldingCount() + 1 > 100) {
            player.sendTextMessageTyped(MessageClass.MESSAGE_TRADE, "You can not trade more than 100 items.")
            return
        }

        if (tradeItem.isStoreItem()) {
            player.sendTextMessageTyped(MessageClass.MESSAGE_TRADE, "This item cannot be trade.")
            return
        }

        if (tradeItemContainer != null) {
            for (containerItem in tradeItemContainer.getItems(true)) {
                if (containerItem.isStoreItem()) {
                    player.sendTextMessageTyped(MessageClass.MESSAGE_TRADE, "This item cannot be trade.")
                    return
                }
            }
        }

        if (!eventPlayerOnTradeRequest(player, tradePartner, tradeItem)) {
            return
        }

        if (!callbackPlayerOnTradeRequest(player, tradePartner, tradeItem)) {
            return
        }

        internalStartTrade(player, tradePartner, tradeItem)
    }

    /**
     * `Game::internalStartTrade` (src/game/game.cpp:5951): records the offered
     * item on both sides; a fresh partner becomes `TRADE_ACKNOWLEDGE`, an
     * already-offering partner receives the counter offers.
     */
    fun internalStartTrade(player: Player, tradePartner: Player, tradeItem: Item): Boolean {
        val playerState = getTradeState(player)
        if (playerState != TradeState_t.TRADE_NONE &&
            !(playerState == TradeState_t.TRADE_ACKNOWLEDGE && getTradePartner(player) === tradePartner)
        ) {
            sendCancelMessage(player, ReturnValue.YOUAREALREADYTRADING)
            return false
        } else if (getTradeState(tradePartner) != TradeState_t.TRADE_NONE && getTradePartner(tradePartner) !== player) {
            sendCancelMessage(player, ReturnValue.THISPLAYERISALREADYTRADING)
            return false
        }

        if (tradeItem.isStoreItem() || tradeItem.hasOwner()) {
            sendCancelMessage(player, ReturnValue.ITEMUNTRADEABLE)
            return false
        }

        setTradePartner(player, tradePartner)
        setTradeItem(player, tradeItem)
        setTradeState(player, TradeState_t.TRADE_INITIATED)
        tradeItems[tradeItem] = player.id

        sendTradeItemRequest(player, player.name, tradeItem, true)

        if (getTradeState(tradePartner) == TradeState_t.TRADE_NONE) {
            tradePartner.sendTextMessageTyped(MessageClass.MESSAGE_TRANSACTION, "${player.name} wants to trade with you.")
            setTradeState(tradePartner, TradeState_t.TRADE_ACKNOWLEDGE)
            setTradePartner(tradePartner, player)
        } else {
            val counterOfferItem = getTradeItem(tradePartner) ?: return true
            sendTradeItemRequest(player, tradePartner.name, counterOfferItem, false)
            sendTradeItemRequest(tradePartner, player.name, tradeItem, false)
        }

        return true
    }

    /**
     * `Game::playerAcceptTrade` (src/game/game.cpp:5986): the second accept
     * swaps both offered items through the player-cylinder item operations and
     * closes the window; a failed swap reports the trade error on both sides and
     * fires the `ON_TRADE_CANCEL` item events.
     */
    fun playerAcceptTrade(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        val playerState = getTradeState(player)
        if (playerState != TradeState_t.TRADE_ACKNOWLEDGE && playerState != TradeState_t.TRADE_INITIATED) {
            return
        }

        val tradePartner = getTradePartner(player) ?: return
        if (!this.canThrowObjectTo.invoke(tradePartner.position, player.position)) {
            sendCancelMessage(player, ReturnValue.CREATUREISNOTREACHABLE)
            return
        }

        setTradeState(player, TradeState_t.TRADE_ACCEPT)

        if (getTradeState(tradePartner) != TradeState_t.TRADE_ACCEPT) {
            return
        }

        val tradeItem1 = getTradeItem(player)
        val tradeItem2 = getTradeItem(tradePartner)
        if (!eventPlayerOnTradeAccept(player, tradePartner, tradeItem1, tradeItem2)) {
            internalCloseTrade(player)
            return
        }

        if (!callbackPlayerOnTradeAccept(player, tradePartner, tradeItem1, tradeItem2)) {
            internalCloseTrade(player)
            return
        }

        setTradeState(player, TradeState_t.TRADE_TRANSFER)
        setTradeState(tradePartner, TradeState_t.TRADE_TRANSFER)

        tradeItem1?.let { tradeItems.remove(it) }
        tradeItem2?.let { tradeItems.remove(it) }

        var isSuccess = false

        var ret1 = internalAddItem(tradePartner, tradeItem1, INDEX_WHEREEVER, 0, true)
        var ret2 = internalAddItem(player, tradeItem2, INDEX_WHEREEVER, 0, true)
        if (ret1 == ReturnValue.NOERROR && ret2 == ReturnValue.NOERROR) {
            ret1 = internalRemoveItem(tradeItem1, tradeItem1?.count ?: -1, test = true)
            ret2 = internalRemoveItem(tradeItem2, tradeItem2?.count ?: -1, test = true)
            if (ret1 == ReturnValue.NOERROR && ret2 == ReturnValue.NOERROR) {
                val cylinder1 = tradeItem1?.let { itemParents[it] }
                val cylinder2 = tradeItem2?.let { itemParents[it] }

                val count1 = tradeItem1?.count ?: 0
                val count2 = tradeItem2?.count ?: 0

                ret1 = if (tradeItem1 != null && cylinder1 != null) {
                    internalMoveItemToPlayer(cylinder1, tradePartner, tradeItem1, count1, CylinderFlags.FLAG_IGNOREAUTOSTACK)
                } else {
                    ReturnValue.NOTPOSSIBLE
                }
                if (ret1 == ReturnValue.NOERROR && tradeItem1 != null && tradeItem2 != null && cylinder2 != null) {
                    internalMoveItemToPlayer(cylinder2, player, tradeItem2, count2, CylinderFlags.FLAG_IGNOREAUTOSTACK)

                    onTradeEvent(tradeItem1, TradeEvents_t.ON_TRADE_TRANSFER, tradePartner)
                    onTradeEvent(tradeItem2, TradeEvents_t.ON_TRADE_TRANSFER, player)

                    isSuccess = true
                }
            }
        }

        if (!isSuccess) {
            val partnerItem = getTradeItem(tradePartner)
            if (partnerItem != null) {
                tradePartner.sendTextMessageTyped(MessageClass.MESSAGE_TRANSACTION, getTradeErrorDescription(ret1, tradeItem1))
                onTradeEvent(partnerItem, TradeEvents_t.ON_TRADE_CANCEL, tradePartner)
            }

            val playerItem = getTradeItem(player)
            if (playerItem != null) {
                player.sendTextMessageTyped(MessageClass.MESSAGE_TRANSACTION, getTradeErrorDescription(ret2, tradeItem2))
                onTradeEvent(playerItem, TradeEvents_t.ON_TRADE_CANCEL, player)
            }
        }

        resetTrade(player)
        resetTrade(tradePartner)
    }

    /** `Game::getTradeErrorDescription` (src/game/game.cpp:6088). */
    fun getTradeErrorDescription(ret: ReturnValue, item: Item?): String {
        if (item != null) {
            if (ret == ReturnValue.NOTENOUGHCAPACITY) {
                val suffix = if (item.isStackable() && item.count > 1) " these objects." else " this object."
                return "You do not have enough capacity to carry$suffix\n ${item.getWeightDescription()}"
            } else if (ret == ReturnValue.NOTENOUGHROOM || ret == ReturnValue.CONTAINERNOTENOUGHROOM) {
                val suffix = if (item.isStackable() && item.count > 1) " these objects." else " this object."
                return "You do not have enough room to carry$suffix"
            }
        }
        return "Trade could not be completed."
    }

    /**
     * `Game::playerLookInTrade` (src/game/game.cpp:6119): `index == 0` looks at
     * the offered item itself, a higher index walks the container tree of the
     * item in FIFO order.
     *
     * PARITY GAP: the item position resolves through the facade's tile parent
     * ([itemParents]) because the Kotlin [Item] does not expose `getPosition`.
     */
    fun playerLookInTrade(playerId: Long, lookAtCounterOffer: Boolean, index: Int) {
        val player = getPlayerByID(playerId) ?: return
        val tradePartner = getTradePartner(player) ?: return

        val tradeItem = if (lookAtCounterOffer) getTradeItem(tradePartner) else getTradeItem(player)
        if (tradeItem == null) {
            return
        }

        val playerPosition = player.position
        val tradeItemPosition = itemParents[tradeItem]?.position ?: playerPosition
        val lookDistance = maxOf(getDistanceX(playerPosition, tradeItemPosition), getDistanceY(playerPosition, tradeItemPosition))
        if (index == 0) {
            eventPlayerOnLookInTrade(player, tradePartner, tradeItem, lookDistance)
            callbackPlayerOnLookInTrade(player, tradePartner, tradeItem, lookDistance)
            return
        }

        val tradeContainer = tradeItem.getContainer() ?: return

        val containers = mutableListOf(tradeContainer)
        var i = 0
        var remaining = index
        while (i < containers.size) {
            val container = containers[i++]
            for (item in container.getItemList()) {
                item.getContainer()?.let { containers.add(it) }

                if (--remaining == 0) {
                    eventPlayerOnLookInTrade(player, tradePartner, item, lookDistance)
                    callbackPlayerOnLookInTrade(player, tradePartner, item, lookDistance)
                    return
                }
            }
        }
    }

    /** `Game::playerCloseTrade` (src/game/game.cpp:6178). */
    fun playerCloseTrade(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        internalCloseTrade(player)
    }

    /**
     * `Game::internalCloseTrade` (src/game/game.cpp:6187): a transfer in
     * progress is not interrupted; otherwise both sides lose their offer, the
     * `ON_TRADE_CANCEL` item event fires and "Trade cancelled." is sent.
     */
    fun internalCloseTrade(player: Player) {
        val tradePartner = getTradePartner(player)
        if ((tradePartner != null && getTradeState(tradePartner) == TradeState_t.TRADE_TRANSFER) ||
            getTradeState(player) == TradeState_t.TRADE_TRANSFER
        ) {
            return
        }

        getTradeItem(player)?.let { item ->
            tradeItems.remove(item)
            onTradeEvent(item, TradeEvents_t.ON_TRADE_CANCEL, player)
            setTradeItem(player, null)
        }

        setTradeState(player, TradeState_t.TRADE_NONE)
        setTradePartner(player, null)

        player.sendTextMessageTyped(MessageClass.MESSAGE_FAILURE, "Trade cancelled.")
        sendTradeClose(player)

        if (tradePartner != null) {
            getTradeItem(tradePartner)?.let { item ->
                tradeItems.remove(item)
                onTradeEvent(item, TradeEvents_t.ON_TRADE_CANCEL, tradePartner)
                setTradeItem(tradePartner, null)
            }

            setTradeState(tradePartner, TradeState_t.TRADE_NONE)
            setTradePartner(tradePartner, null)

            tradePartner.sendTextMessageTyped(MessageClass.MESSAGE_FAILURE, "Trade cancelled.")
            sendTradeClose(tradePartner)
        }
    }

    /**
     * `Game::playerBuyItem` (src/game/game.cpp:6228): the interacting merchant
     * must sell the item to the player, the UI must not be exhausted and the
     * backpack/tile limits hint the item delivery.
     *
     * PARITY GAPS: `g_configManager().getNumber(MAX_CONTAINER)` is [maxContainer]
     * and the `MAX_CONTAINER_ITEM` per-item ceiling is not modeled.
     */
    fun playerBuyItem(
        playerId: Long,
        itemId: Int,
        count: Int,
        amount: Int,
        ignoreCap: Boolean = false,
        inBackpacks: Boolean = false,
    ) {
        if (amount == 0) {
            return
        }

        val player = getPlayerByID(playerId) ?: return
        val merchant = getInteractableShopOwner(player) ?: return
        val itemType = itemTypes?.get(itemId) ?: return
        if (itemType.id == 0) {
            return
        }

        if ((itemType.isStackable && amount > 10000) || (!itemType.isStackable && amount > 100)) {
            return
        }

        if (!hasShopItemForSale(player, merchant, itemType.id, count)) {
            return
        }

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        if (inBackpacks || itemType.isContainer) {
            val mainBackpack = player.getBackpack()
            if (mainBackpack != null && mainBackpack.getContainerHoldingCount() >= maxContainer) {
                sendCancelMessage(player, ReturnValue.CONTAINERISFULL)
                return
            }

            val tile = placedCreatures[player]
            if (tile != null && tile.getItemCount() >= 20) {
                sendCancelMessage(player, ReturnValue.CONTAINERISFULL)
                return
            }
        }

        merchant.onPlayerBuyItem(player, itemType.id, count, amount, ignoreCap, inBackpacks)
        updateUIExhausted(player)
    }

    /** `Game::playerSellItem` (src/game/game.cpp:6284). */
    fun playerSellItem(playerId: Long, itemId: Int, count: Int, amount: Int, ignoreEquipped: Boolean) {
        if (amount == 0) {
            return
        }

        val player = getPlayerByID(playerId) ?: return
        val merchant = getInteractableShopOwner(player) ?: return
        val itemType = itemTypes?.get(itemId) ?: return
        if (itemType.id == 0) {
            return
        }

        if ((itemType.isStackable && amount > 10000) || (!itemType.isStackable && amount > 100)) {
            return
        }

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        merchant.onPlayerSellItem(player, itemType.id, count, amount, ignoreEquipped)
        updateUIExhausted(player)
    }

    /** `Game::playerCloseShop` (src/game/game.cpp:6319). */
    fun playerCloseShop(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        closeShopWindow(player)
    }

    /**
     * `getInteractableShopOwner` (`src/game/game.cpp:196-212`, anonymous
     * namespace): the stored shop owner must still be within interaction range,
     * otherwise the shop window closes.
     */
    private fun getInteractableShopOwner(player: Player): Npc? {
        val merchant = getShopOwner(player) ?: return null
        if (merchant.canInteract(player.position)) {
            return merchant
        }

        closeShopWindow(player)
        return null
    }

    /** `Player::hasShopItemForSale` (player.cpp:6066-6076). */
    private fun hasShopItemForSale(player: Player, merchant: Npc, itemId: Int, subType: Int): Boolean {
        val itemType = itemTypes?.get(itemId) ?: return false
        val shopList = merchant.getShopItemVector(player.getGUID())
        return shopList.any { shopBlock ->
            shopBlock.itemId == itemId &&
                shopBlock.itemBuyPrice != 0L &&
                (!itemType.isFluidContainer || shopBlock.itemSubType == subType)
        }
    }

    /** `Player::tradeState` getter (player.hpp:718). */
    private fun getTradeState(player: Player): TradeState_t = tradeStates[player] ?: TradeState_t.TRADE_NONE

    /** `Player::setTradeState` (player.cpp:6105-6107). */
    private fun setTradeState(player: Player, state: TradeState_t) {
        tradeStates[player] = state
    }

    /** `Player::tradeItem` accessor (player.hpp:1762). */
    private fun getTradeItem(player: Player): Item? = tradeItemByPlayer[player]

    /** `Player::tradeItem` assignment (player.cpp trade handlers). */
    private fun setTradeItem(player: Player, item: Item?) {
        tradeItemByPlayer[player] = item
    }

    /** `Player::tradePartner` accessor (player.hpp:1768). */
    private fun getTradePartner(player: Player): Player? = tradePartnerByPlayer[player]

    /** `Player::tradePartner` assignment (player.cpp trade handlers). */
    private fun setTradePartner(player: Player, partner: Player?) {
        tradePartnerByPlayer[player] = partner
    }

    /** The trade-window reset tail shared by `playerAcceptTrade` (game.cpp:6076-6084). */
    private fun resetTrade(player: Player) {
        setTradeState(player, TradeState_t.TRADE_NONE)
        setTradeItem(player, null)
        setTradePartner(player, null)
        sendTradeClose(player)
    }

    /**
     * Player-cylinder branch of `Game::internalAddItem` (game.cpp:2693): the
     * `Player::queryAdd` ownership/pickupable/capacity gate plus the
     * `Player::addThing` insertion modelled by `Player::addItem`.
     */
    private fun internalAddItem(
        toPlayer: Player,
        item: Item?,
        index: Int = INDEX_WHEREEVER,
        flags: Int = 0,
        test: Boolean = false,
    ): ReturnValue {
        if (item == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        if (item.hasOwner() && !item.isOwner(toPlayer.getGUID())) {
            return ReturnValue.ITEMISNOTYOURS
        }

        if (!item.isPickupable()) {
            return ReturnValue.CANNOTPICKUP
        }

        if (!playerHasCapacity(toPlayer, item, flags)) {
            return ReturnValue.NOTENOUGHCAPACITY
        }

        if (test) {
            return ReturnValue.NOERROR
        }
        return toPlayer.addItem(item)
    }

    /**
     * `Player::hasCapacity` subset (player.cpp:4557-4571): the flag gates and
     * the free-capacity comparison. The container/base-weight split is reduced
     * to `Item::weight` (PARITY GAP: container contents are not added).
     */
    private fun playerHasCapacity(player: Player, item: Item, flags: Int): Boolean {
        if (player.hasFlag(PlayerFlags_t.CannotPickupItem)) {
            return false
        }

        if (player.hasFlag(PlayerFlags_t.HasInfiniteCapacity) ||
            (flags and CylinderFlags.FLAG_NOLIMIT) != 0
        ) {
            return true
        }

        return item.weight <= player.getFreeCapacity()
    }

    /**
     * Player-cylinder branch of `Game::internalMoveItem` (game.cpp:2436) used
     * by the trade transfer: the source tile removal path is the facade's
     * [queryRemove]/[removeThing] pair and the destination is
     * `Player::addItem` (PARITY GAP: no auto-stack into the trade window, which
     * `FLAG_IGNOREAUTOSTACK` also suppresses in Canary).
     */
    private fun internalMoveItemToPlayer(fromTile: Tile?, toPlayer: Player, item: Item, count: Int, flags: Int): ReturnValue {
        if (fromTile == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        var ret = queryRemove(fromTile, item, 1, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        val maxQueryCount = queryMaxCount(count)
        val m = if (isStackable(item)) minOf(count, maxQueryCount) else maxQueryCount

        ret = queryRemove(fromTile, item, m, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        if (isStackable(item) && m < item.count) {
            removeThing(fromTile, item, m)
            return toPlayer.addItem(cloneItem(item, m))
        }

        removeThing(fromTile, item, m)
        return toPlayer.addItem(item)
    }

    /**
     * `Game::internalGetThing` reduced to the `STACKPOS_TOPDOWN_ITEM` tile
     * branch (game.cpp:1130-1205) needed by [playerRequestTrade]. PARITY GAPS:
     * the look/move/use/find stack-pos branches, containers, depot search and
     * inventory targets are not modeled.
     */
    /**
     * `Game::internalGetThing` (src/game/game.cpp:1130) reduced to the item
     * selectors: the container/inventory `0xFFFF` branches and the tile
     * `STACKPOS_USEITEM`/`STACKPOS_FIND_THING`/`STACKPOS_TOPDOWN_ITEM` picks
     * with the supports-hangable veto.
     *
     * PARITY GAPS: `STACKPOS_LOOK`/`MOVE`/`USETARGET` (creature picks) have
     * separate call sites, the browse-field container veto, the depot-search
     * positions (`0x20`/`0x21`), the `pos.y == 0 && pos.z == 0`
     * `findItemOfType` branch and the held-item checks are not ported.
     */
    private fun internalGetThing(player: Player, pos: Position, index: Int, itemId: Int, type: StackPosType_t): Item? {
        if (pos.x == 0xFFFF) {
            // container
            if ((pos.y and 0x40) != 0) {
                val fromCid = pos.y and 0x0F
                val parentContainer = player.getContainerByID(fromCid) ?: return null
                val containerIndex = player.getContainerIndex(fromCid) + pos.z
                if (parentContainer.isStoreInboxFiltered()) {
                    return parentContainer.getFilteredItemByIndex(containerIndex)
                }
                return parentContainer.getItemByIndex(containerIndex)
            }

            if (pos.y == 0x20 || pos.y == 0x21) {
                return null
            }

            if (pos.y == 0 && pos.z == 0) {
                return null
            }

            val slot = Slots_t.fromValue(pos.y) ?: return null
            return player.getInventoryItem(slot)
        }

        val tile = map.getTile(pos) ?: return null
        val thing = when (type) {
            StackPosType_t.STACKPOS_USEITEM -> tile.getUseItem(index)?.getItem()
            StackPosType_t.STACKPOS_FIND_THING ->
                tile.getUseItem(index)?.getItem() ?: tile.getDoorItem() ?: tile.getTopDownItem()
            StackPosType_t.STACKPOS_TOPDOWN_ITEM -> topDownItem(tile)
            StackPosType_t.STACKPOS_MOVE -> tile.getTopDownItem()?.takeIf { it.isMovable() }
            else -> null
        } ?: return null

        if (tile.hasFlag(TileFlags.TILESTATE_SUPPORTS_HANGABLE.value)) {
            if (tile.hasProperty(ItemProperty.CONST_PROP_ISVERTICAL)) {
                if (player.position.x + 1 == tile.position.x && thing.isHangable()) {
                    return null
                }
            } else if (player.position.y + 1 == tile.position.y && thing.isHangable()) {
                return null
            }
        }
        return thing
    }

    /**
     * `Game::internalGetThing` `STACKPOS_LOOK` branch (game.cpp:1138-1141): the
     * tile's top visible thing. PARITY GAPS: the container/inventory/depot-search
     * branches (`pos.x == 0xFFFF`) and the supports-hangable post-check
     * (game.cpp:1193-1208) are not modeled.
     */
    private fun internalGetThingForLook(player: Player, pos: Position, index: Int, itemId: Int): Thing? {
        if (pos.x == 0xFFFF) {
            return null
        }

        val tile = map.getTile(pos) ?: return null
        return tile.getTopVisibleThing(player)
    }

    /** `InternalGame::tryInsertTeleportStack` (game.cpp:236). */
    private fun tryInsertTeleportStack(thing: Thing): Boolean = teleportStack.add(thing)

    /** `InternalGame::pruneTeleportGuardStates` (game.cpp:296). */
    private fun pruneTeleportGuardStates(now: Long) {
        if (teleportGuards.size <= TELEPORT_MAX_TRACKED_THINGS) {
            return
        }

        val iterator = teleportGuards.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastSeenMs + TELEPORT_GUARD_STATE_TTL_MS <= now) {
                iterator.remove()
            }
        }

        val excess = teleportGuards.entries.iterator()
        while (teleportGuards.size > TELEPORT_MAX_TRACKED_THINGS && excess.hasNext()) {
            excess.next()
            excess.remove()
        }
    }

    /** `InternalGame::shouldBlockTeleportRate` (game.cpp:313): burst and sustained rate windows. */
    private fun shouldBlockTeleportRate(thing: Thing, now: Long): Boolean {
        pruneTeleportGuardStates(now)

        val state = teleportGuards.getOrPut(thing) { TeleportGuardState() }
        state.lastSeenMs = now

        if (state.blockedUntilMs > now) {
            return true
        }

        var exceededBurstLimit = false
        if (state.windowStartMs == 0L || state.windowStartMs + TELEPORT_SPAM_WINDOW_MS <= now) {
            state.windowStartMs = now
            state.windowCount = 1
        } else {
            state.windowCount++
            exceededBurstLimit = state.windowCount > TELEPORT_SPAM_LIMIT
        }

        var exceededSustainedLimit = false
        if (state.sustainedWindowStartMs == 0L || state.sustainedWindowStartMs + TELEPORT_SUSTAINED_WINDOW_MS <= now) {
            state.sustainedWindowStartMs = now
            state.sustainedWindowCount = 1
        } else {
            state.sustainedWindowCount++
            exceededSustainedLimit = state.sustainedWindowCount > TELEPORT_SUSTAINED_LIMIT
        }

        if (exceededBurstLimit || exceededSustainedLimit) {
            state.blockedUntilMs =
                now + (if (exceededSustainedLimit) TELEPORT_SUSTAINED_BLOCK_MS else TELEPORT_SPAM_BLOCK_MS)
            return true
        }

        return false
    }

    /**
     * `InternalGame::recordTeleportGuardBlock` (game.cpp:350): counts the
     * blocked event and returns the exponential-backoff log snapshot.
     */
    private fun recordTeleportGuardBlock(thing: Thing, now: Long): Pair<Long, Int>? {
        pruneTeleportGuardStates(now)

        val state = teleportGuards.getOrPut(thing) { TeleportGuardState() }
        state.lastSeenMs = now
        state.blockedEvents++

        if (state.nextLogMs > now) {
            state.suppressedLogs++
            return null
        }

        val snapshot = state.blockedEvents.toLong() to state.suppressedLogs
        state.suppressedLogs = 0
        state.nextLogMs = now + state.logDelayMs
        state.logDelayMs = minOf(state.logDelayMs * 2, TELEPORT_LOG_MAX_DELAY_MS)
        return snapshot
    }

    /** `InternalGame::logTeleportGuardBlock` (game.cpp:385). */
    private fun logTeleportGuardBlock(thing: Thing, reason: String, snapshot: Pair<Long, Int>?) {
        if (snapshot == null) {
            return
        }

        val creature = thing.getCreature()
        if (creature != null) {
            logger.error {
                "[internalTeleport] Blocked $reason teleport for creature ${creature.name} at position " +
                    "${creature.position} (blocked events: ${snapshot.first}, suppressed logs: ${snapshot.second})"
            }
            return
        }

        val item = thing.getItem()
        if (item != null) {
            logger.error {
                "[internalTeleport] Blocked $reason teleport for item id ${item.id} at position " +
                    "${item.position} (blocked events: ${snapshot.first}, suppressed logs: ${snapshot.second})"
            }
        }
    }

    /** `Player::isPartner` (player.cpp:7790-7796), transcribed for the party handlers. */
    private fun isPartner(player: Player, other: Player?): Boolean {
        if (other == null || other === player) {
            return false
        }
        val party = player.getParty() ?: return false
        return party === other.getParty()
    }

    /** `Creature::getZoneType` (creature.cpp:1856-1862): placed tile, else `ZONE_NORMAL`. */
    private fun zoneTypeOf(player: Player): ZoneType = placedCreatures[player]?.getZoneType() ?: ZoneType.ZONE_NORMAL

    /**
     * `Game::beforeCreatureZoneChange` (src/game/game.cpp:12417): the zone
     * callbacks may veto a leave/enter transition unless [force] is set.
     */
    fun beforeCreatureZoneChange(
        creature: Creature?,
        fromZones: Set<Zone>,
        toZones: Set<Zone>,
        force: Boolean = false,
    ): ReturnValue {
        if (creature == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        val zonesLeaving = fromZones - toZones
        val zonesEntering = toZones - fromZones
        if (zonesLeaving.isEmpty() && zonesEntering.isEmpty()) {
            return ReturnValue.NOERROR
        }

        for (zone in zonesLeaving) {
            val allowed = checkZoneBeforeCreatureLeave(zone, creature)
            if (!force && !allowed) {
                return ReturnValue.NOTPOSSIBLE
            }
        }

        for (zone in zonesEntering) {
            val allowed = checkZoneBeforeCreatureEnter(zone, creature)
            if (!force && !allowed) {
                return ReturnValue.NOTPOSSIBLE
            }
        }
        return ReturnValue.NOERROR
    }

    /**
     * `Game::afterCreatureZoneChange` (src/game/game.cpp:12447): runs the
     * after-leave/enter callbacks for the zones the creature actually changed.
     * `Zone::creatureRemoved` / `Zone::creatureAdded` (`zone.cpp`) are not
     * ported on [Zone] yet (PARITY GAP).
     */
    fun afterCreatureZoneChange(creature: Creature?, fromZones: Set<Zone>, toZones: Set<Zone>) {
        if (creature == null) {
            return
        }

        for (zone in fromZones - toZones) {
            executeZoneAfterCreatureLeave(zone, creature)
        }
        for (zone in toZones - fromZones) {
            executeZoneAfterCreatureEnter(zone, creature)
        }
    }

    /**
     * `Game::getPlayerByGUID` (src/game/game.cpp:1419): online lookup by GUID,
     * with the `IOLoginData::loadPlayerById` fallback behind `allowOffline`.
     * PARITY GAP: the loaded temporary player's `setOnline(false)` has no Kotlin
     * counterpart (the Kotlin [Player] carries no online flag).
     */
    fun getPlayerByGUID(guid: Long, allowOffline: Boolean = false): Player? {
        if (guid == 0L) {
            return null
        }

        players.values.firstOrNull { it.getGUID() == guid }?.let { return it }
        if (!allowOffline) {
            return null
        }
        return loadPlayerById(guid)
    }

    /**
     * `Game::playerCyclopediaHousesByTown` (src/game/game.cpp:12762): an empty
     * [townName] sends the player's owned houses plus the house he currently
     * bids on; otherwise every house of the named town. The `HouseMap` is keyed
     * by client id and iterated in ascending order like the C++ `std::map`.
     */
    fun playerCyclopediaHousesByTown(playerId: Long, townName: String) {
        val player = getPlayerByID(playerId) ?: return

        val houses = sortedMapOf<Int, House>()
        if (townName.isNotEmpty()) {
            for (house in map.houses.getHouses()) {
                val town = map.towns.getTown(house.townId) ?: continue
                if (town.getName() == townName) {
                    houses.putIfAbsent(house.clientId, house)
                }
            }
        } else {
            for (playerHouse in map.houses.getAllHousesByPlayerId(player.getGUID())) {
                houses.putIfAbsent(playerHouse.clientId, playerHouse)
            }

            val house = map.houses.getHouseByBidderName(player.name)
            if (house != null) {
                houses.putIfAbsent(house.clientId, house)
            }
        }
        sendCyclopediaHouseList(player, houses)
    }

    /**
     * `Game::playerCyclopediaHouseBid` (src/game/game.cpp:12802). Faithful to
     * the C++ control flow: a `Player::canBidHouse` error is reported but the
     * bid is still processed; a failed [processBankAuction] reports
     * `NotEnoughMoney` and returns; the success message is sent with the extra
     * `bidSuccess` flag and the town list is refreshed.
     */
    fun playerCyclopediaHouseBid(playerId: Long, houseId: Long, bidValue: Long) {
        if (!cyclopediaHouseAuction) {
            return
        }

        val player = getPlayerByID(playerId) ?: return
        val house = map.houses.getHouseByClientId(houseId.toInt()) ?: return

        var ret = canBidHouse(player, houseId)
        if (ret != BidErrorMessage.NoError) {
            sendHouseAuctionMessage(player, houseId, HouseAuctionType.Bid, ret.value, false)
        }
        ret = BidErrorMessage.NotEnoughMoney
        var retSuccess = BidSuccessMessage.BidSuccess

        if (house.bidderName.isEmpty()) {
            if (!processBankAuction(player, house, bidValue)) {
                sendHouseAuctionMessage(player, houseId, HouseAuctionType.Bid, ret.value, false)
                return
            }
            house.highestBid = 0
            house.internalBid = bidValue
            house.bidHolderLimit = bidValue
            house.bidderName = player.name
            house.bidder = player.getGUID()
            house.calculateBidEndDate(daysToCloseBid)
        } else if (house.bidderName == player.name) {
            if (!processBankAuction(player, house, bidValue, true)) {
                sendHouseAuctionMessage(player, houseId, HouseAuctionType.Bid, ret.value, false)
                return
            }
            house.internalBid = bidValue
            house.bidHolderLimit = bidValue
        } else if (bidValue <= house.internalBid) {
            house.highestBid = bidValue
            retSuccess = BidSuccessMessage.LowerBid
        } else {
            if (!processBankAuction(player, house, bidValue)) {
                sendHouseAuctionMessage(player, houseId, HouseAuctionType.Bid, ret.value, false)
                return
            }
            house.highestBid = house.internalBid + 1
            house.internalBid = bidValue
            house.bidHolderLimit = bidValue
            house.bidderName = player.name
            house.bidder = player.getGUID()
        }

        val town = map.towns.getTown(house.townId) ?: return
        val houseTown = town.getName()
        sendHouseAuctionMessage(player, houseId, HouseAuctionType.Bid, retSuccess.value, true)
        playerCyclopediaHousesByTown(playerId, houseTown)
    }

    /** `Game::playerCyclopediaHouseMoveOut` (src/game/game.cpp:12867). */
    fun playerCyclopediaHouseMoveOut(playerId: Long, houseId: Long, timestamp: Long) {
        if (!cyclopediaHouseAuction) {
            return
        }

        val player = getPlayerByID(playerId)
        if (player == null) {
            logger.warn { "[playerCyclopediaHouseMoveOut] Player $playerId not found while handling house auction request." }
            return
        }

        val house = map.houses.getHouseByClientId(houseId.toInt())
        if (house == null || house.state != CyclopediaHouseState.Rented) {
            sendHouseAuctionMessage(player, houseId, HouseAuctionType.MoveOut, TransferErrorMessage.Internal.value, false)
            return
        }

        if (house.owner != player.getGUID()) {
            sendHouseAuctionMessage(player, houseId, HouseAuctionType.MoveOut, TransferErrorMessage.NotHouseOwner.value, false)
            return
        }

        house.bidEndDate = timestamp
        house.state = CyclopediaHouseState.MoveOut

        sendHouseAuctionMessage(player, houseId, HouseAuctionType.MoveOut, TransferErrorMessage.Success.value, false)
        playerCyclopediaHousesByTown(playerId, "")
    }

    /** `Game::playerCyclopediaHouseCancelMoveOut` (src/game/game.cpp:12896). */
    fun playerCyclopediaHouseCancelMoveOut(playerId: Long, houseId: Long) {
        if (!cyclopediaHouseAuction) {
            return
        }

        val player = getPlayerByID(playerId)
        if (player == null) {
            logger.warn { "[playerCyclopediaHouseCancelMoveOut] Player $playerId not found while handling house auction request." }
            return
        }

        val house = map.houses.getHouseByClientId(houseId.toInt())
        if (house == null || house.state != CyclopediaHouseState.MoveOut) {
            sendHouseAuctionMessage(player, houseId, HouseAuctionType.CancelMoveOut, TransferErrorMessage.Internal.value, false)
            return
        }

        if (house.owner != player.getGUID()) {
            sendHouseAuctionMessage(player, houseId, HouseAuctionType.CancelMoveOut, TransferErrorMessage.NotHouseOwner.value, false)
            return
        }

        house.bidEndDate = 0
        house.state = CyclopediaHouseState.Rented

        sendHouseAuctionMessage(player, houseId, HouseAuctionType.CancelMoveOut, TransferErrorMessage.Success.value, false)
        playerCyclopediaHousesByTown(playerId, "")
    }

    /** `Game::playerCyclopediaHouseTransfer` (src/game/game.cpp:12925). */
    fun playerCyclopediaHouseTransfer(
        playerId: Long,
        houseId: Long,
        timestamp: Long,
        newOwnerName: String,
        bidValue: Long,
    ) {
        if (!cyclopediaHouseAuction) {
            return
        }

        val owner = getPlayerByID(playerId)
        if (owner == null) {
            logger.warn { "[playerCyclopediaHouseTransfer] Player $playerId not found while handling house auction request." }
            return
        }

        val newOwner = getPlayerByName(newOwnerName, true)
        if (newOwner == null) {
            sendHouseAuctionMessage(owner, houseId, HouseAuctionType.Transfer, TransferErrorMessage.CharacterNotExist.value, false)
            return
        }

        val house = map.houses.getHouseByClientId(houseId.toInt())
        if (house == null || house.state != CyclopediaHouseState.Rented) {
            sendHouseAuctionMessage(owner, houseId, HouseAuctionType.Transfer, TransferErrorMessage.Internal.value, false)
            return
        }

        val ret = canTransferHouse(owner, houseId, newOwner.getGUID())
        if (ret != TransferErrorMessage.Success) {
            sendHouseAuctionMessage(owner, houseId, HouseAuctionType.Transfer, ret.value, false)
            return
        }

        house.bidderName = newOwnerName
        house.bidder = newOwner.getGUID()
        house.internalBid = bidValue
        house.bidEndDate = timestamp
        house.state = CyclopediaHouseState.Transfer

        sendHouseAuctionMessage(owner, houseId, HouseAuctionType.Transfer, ret.value, false)
        playerCyclopediaHousesByTown(playerId, "")
    }

    /** `Game::playerCyclopediaHouseCancelTransfer` (src/game/game.cpp:12964). */
    fun playerCyclopediaHouseCancelTransfer(playerId: Long, houseId: Long) {
        if (!cyclopediaHouseAuction) {
            return
        }

        val player = getPlayerByID(playerId)
        if (player == null) {
            logger.warn { "[playerCyclopediaHouseCancelTransfer] Player $playerId not found while handling house auction request." }
            return
        }

        val house = map.houses.getHouseByClientId(houseId.toInt())
        if (house == null || house.state != CyclopediaHouseState.Transfer) {
            sendHouseAuctionMessage(player, houseId, HouseAuctionType.CancelTransfer, TransferErrorMessage.Internal.value, false)
            return
        }

        if (house.owner != player.getGUID()) {
            sendHouseAuctionMessage(player, houseId, HouseAuctionType.CancelTransfer, TransferErrorMessage.NotHouseOwner.value, false)
            return
        }

        if (house.transferStatus) {
            refundTransfer(house)
        }

        house.bidderName = ""
        house.bidder = 0
        house.internalBid = 0
        house.bidEndDate = 0
        house.state = CyclopediaHouseState.Rented
        house.transferStatus = false

        sendHouseAuctionMessage(player, houseId, HouseAuctionType.CancelTransfer, TransferErrorMessage.Success.value, false)
        playerCyclopediaHousesByTown(playerId, "")
    }

    /** `Game::playerCyclopediaHouseAcceptTransfer` (src/game/game.cpp:13008). */
    fun playerCyclopediaHouseAcceptTransfer(playerId: Long, houseId: Long) {
        if (!cyclopediaHouseAuction) {
            return
        }

        val player = getPlayerByID(playerId)
        if (player == null) {
            logger.warn { "[playerCyclopediaHouseAcceptTransfer] Player $playerId not found while handling house auction request." }
            return
        }

        val house = map.houses.getHouseByClientId(houseId.toInt())
        if (house == null || house.state != CyclopediaHouseState.Transfer) {
            sendHouseAuctionMessage(player, houseId, HouseAuctionType.AcceptTransfer, AcceptTransferErrorMessage.Internal.value, false)
            return
        }

        val ret = canAcceptTransferHouse(player, houseId)
        if (ret != AcceptTransferErrorMessage.Success) {
            sendHouseAuctionMessage(player, houseId, HouseAuctionType.AcceptTransfer, ret.value, false)
            return
        }

        if (!processBankAuction(player, house, house.internalBid)) {
            sendHouseAuctionMessage(player, houseId, HouseAuctionType.AcceptTransfer, AcceptTransferErrorMessage.Frozen.value, false)
            return
        }

        house.transferStatus = true

        sendHouseAuctionMessage(player, houseId, HouseAuctionType.AcceptTransfer, ret.value, false)
        playerCyclopediaHousesByTown(playerId, "")
    }

    /** `Game::playerCyclopediaHouseRejectTransfer` (src/game/game.cpp:13042). */
    fun playerCyclopediaHouseRejectTransfer(playerId: Long, houseId: Long) {
        if (!cyclopediaHouseAuction) {
            return
        }

        val player = getPlayerByID(playerId)
        if (player == null) {
            logger.warn { "[playerCyclopediaHouseRejectTransfer] Player $playerId not found while handling house auction request." }
            return
        }

        val house = map.houses.getHouseByClientId(houseId.toInt())
        if (house == null || house.bidder != player.getGUID() || house.state != CyclopediaHouseState.Transfer) {
            sendHouseAuctionMessage(player, houseId, HouseAuctionType.Transfer, TransferErrorMessage.NotHouseOwner.value, false)
            return
        }

        if (house.transferStatus) {
            refundTransfer(house)
        }

        house.bidderName = ""
        house.bidder = 0
        house.internalBid = 0
        house.bidEndDate = 0
        house.state = CyclopediaHouseState.Rented
        house.transferStatus = false

        sendHouseAuctionMessage(player, houseId, HouseAuctionType.RejectTransfer, TransferErrorMessage.Success.value, false)
        playerCyclopediaHousesByTown(playerId, "")
    }

    /**
     * `Game::processBankAuction` (src/game/game.cpp:13081): charges the rent
     * plus bid (or only the bid delta on [replace]) and refunds the previous
     * bidder's holder limit plus rent, online through [setBankBalance] or
     * offline through `IOLoginData::increaseBankBalance`.
     */
    fun processBankAuction(player: Player, house: House, bid: Long, replace: Boolean = false): Boolean {
        if (!replace && getBankBalance(player) < house.getRent() + bid) {
            return false
        }

        if (getBankBalance(player) < bid) {
            return false
        }

        val balance = getBankBalance(player)
        if (replace) {
            setBankBalance(player, balance - (bid - house.internalBid))
        } else {
            setBankBalance(player, balance - (house.getRent() + bid))
        }

        sendResourceBalance(player, StoreWindowWriter.Resource.BANK, getBankBalance(player))

        if (house.bidderName != player.name) {
            val otherPlayer = getPlayerByName(house.bidderName)
            if (otherPlayer == null) {
                val bidderGuid = loginRepository?.getGuidByName(house.bidderName) ?: 0L
                loginRepository?.increaseBankBalance(bidderGuid, house.bidHolderLimit + house.getRent())
            } else {
                setBankBalance(otherPlayer, getBankBalance(otherPlayer) + house.bidHolderLimit + house.getRent())
                sendResourceBalance(otherPlayer, StoreWindowWriter.Resource.BANK, getBankBalance(otherPlayer))
            }
        }

        return true
    }

    /**
     * `Player::canBidHouse` (player.cpp:13052) transcribed into the facade
     * because the Kotlin [Player] does not carry the auction entry point yet
     * (PARITY GAP). The guild-hall branch needs `Player::getGuildRank` and
     * `Guild::getBankBalance`, both ported.
     */
    private fun canBidHouse(player: Player, houseId: Long): BidErrorMessage {
        val house = map.houses.getHouseByClientId(houseId.toInt()) ?: return BidErrorMessage.Internal

        if (player.getPlayerVocationEnum() == Vocation.VOCATION_NONE) {
            return BidErrorMessage.Rookgaard
        }

        if (!player.isPremium()) {
            return BidErrorMessage.Premium
        }

        if ((player.account?.houseBidId ?: 0) != 0) {
            return BidErrorMessage.OnlyOneBid
        }

        if (getBankBalance(player) < (house.getRent() + house.highestBid)) {
            return BidErrorMessage.NotEnoughMoney
        }

        if (house.isGuildhall()) {
            val rank = player.getGuildRank()
            if (rank != null && rank.level != 3) {
                return BidErrorMessage.Guildhall
            }

            val guild = player.getGuild()
            if (guild != null && guild.getBankBalance() < (house.getRent() + house.highestBid)) {
                return BidErrorMessage.NotEnoughGuildMoney
            }
        }

        return BidErrorMessage.NoError
    }

    /** `Player::canTransferHouse` (player.cpp:13088) transcribed into the facade (PARITY GAP). */
    private fun canTransferHouse(player: Player, houseId: Long, newOwnerGuid: Long): TransferErrorMessage {
        val house = map.houses.getHouseByClientId(houseId.toInt()) ?: return TransferErrorMessage.Internal

        if (player.getGUID() != house.owner) {
            return TransferErrorMessage.NotHouseOwner
        }

        if (player.getGUID() == newOwnerGuid) {
            return TransferErrorMessage.AlreadyTheOwner
        }

        val newOwner = getPlayerByGUID(newOwnerGuid, true) ?: return TransferErrorMessage.CharacterNotExist

        if (newOwner.getPlayerVocationEnum() == Vocation.VOCATION_NONE) {
            return TransferErrorMessage.Rookgaard
        }

        if (!newOwner.isPremium()) {
            return TransferErrorMessage.Premium
        }

        if ((newOwner.account?.houseBidId ?: 0) != 0) {
            return TransferErrorMessage.OnlyOneBid
        }

        return TransferErrorMessage.Success
    }

    /** `Player::canAcceptTransferHouse` (player.cpp:13123) transcribed into the facade (PARITY GAP). */
    private fun canAcceptTransferHouse(player: Player, houseId: Long): AcceptTransferErrorMessage {
        val house = map.houses.getHouseByClientId(houseId.toInt()) ?: return AcceptTransferErrorMessage.Internal

        if (player.getGUID() != house.bidder) {
            return AcceptTransferErrorMessage.NotNewOwner
        }

        if (!player.isPremium()) {
            return AcceptTransferErrorMessage.Premium
        }

        if ((player.account?.houseBidId ?: 0) != 0) {
            return AcceptTransferErrorMessage.AlreadyBid
        }

        if (player.getPlayerVocationEnum() == Vocation.VOCATION_NONE) {
            return AcceptTransferErrorMessage.Rookgaard
        }

        if (getBankBalance(player) < (house.getRent() + house.internalBid)) {
            return AcceptTransferErrorMessage.Frozen
        }

        if (house.transferStatus) {
            return AcceptTransferErrorMessage.AlreadyAccepted
        }

        return AcceptTransferErrorMessage.Success
    }

    /**
     * The refund tail shared by `Game::playerCyclopediaHouseCancelTransfer`
     * (game.cpp:12979-12990) and `Game::playerCyclopediaHouseRejectTransfer`
     * (game.cpp:13052-13063): `internalBid + rent` back to the accepted new
     * owner, online or through `IOLoginData::increaseBankBalance`.
     */
    private fun refundTransfer(house: House) {
        val newOwner = getPlayerByGUID(house.bidder)
        val amountPaid = house.internalBid + house.getRent()
        if (newOwner != null) {
            setBankBalance(newOwner, getBankBalance(newOwner) + amountPaid)
            sendResourceBalance(newOwner, StoreWindowWriter.Resource.BANK, getBankBalance(newOwner))
        } else {
            loginRepository?.increaseBankBalance(house.bidder, amountPaid)
        }
    }

    /**
     * `Game::playerUpdateHouseWindow` (src/game/game.cpp:5780): applies the
     * text when the stored edit list, window text id and client list id all
     * match, then clears the edit house (which advances `windowTextId`).
     */
    fun playerUpdateHouseWindow(playerId: Long, listId: Int, windowTextId: Long, text: String) {
        val player = getPlayerByID(playerId) ?: return

        val state = editHouseState(player)
        val house = state.house
        if (house != null && house.canEditAccessList(state.editListId, player) &&
            state.windowTextId == windowTextId && listId == 0
        ) {
            house.setAccessList(state.editListId, text)
        }

        setEditHouse(player, null)
    }

    /**
     * `Player::setEditHouse` (player.cpp:3116-3120) facade over the Kotlin
     * player's missing members (PARITY GAP): the window text id advances on
     * every call, exactly like the C++ `windowTextId++`.
     */
    fun setEditHouse(player: Player, house: House?, listId: Int = 0) {
        val state = editHouseState(player)
        state.windowTextId++
        state.house = house
        state.editListId = listId
    }

    /** `Player::getEditHouse` (player.cpp:3110-3114) facade: the stored house. */
    fun getEditHouse(player: Player): House? = editHouseState(player).house

    private fun editHouseState(player: Player): EditHouseState =
        editHouseByPlayer.getOrPut(player) { EditHouseState() }

    /**
     * `Game::wrapItem` (src/game/game.cpp:5472): wakes and detaches a bed,
     * transforms the item into `ITEM_DECORATION_KIT` and stamps the store
     * attribute, the `unWrapId` custom attribute, the description, the hidden
     * cask charges and the amount.
     *
     * PARITY GAP: the Kotlin [Item] has no custom-attribute store, so
     * `unWrapId` is held by the facade's [itemUnWrapIds] identity map.
     */
    fun wrapItem(item: Item, house: House?): Item? {
        var hiddenCharges = 0
        val amount = item.getItemCount()
        if (Tools.isCaskItem(item.id)) {
            hiddenCharges = item.getSubType()
        }

        if (house != null && (item.type ?: itemTypes?.get(item.id))?.isBed == true) {
            val bed = item.getBed()
            if (bed != null) {
                wakeUpBed(bed)
                house.removeBed(bed)
            }
        }

        val oldItemID = item.id
        val itemName = item.getName()
        val attributeStore = item.getAttribute(ItemAttribute.STORE) as? Long ?: 0L
        val newItem = transformItem(item, Items.ITEM_DECORATION_KIT) ?: return null
        if (attributeStore != 0L) {
            newItem.setAttribute(ItemAttribute.STORE, attributeStore)
        }
        itemUnWrapIds[newItem] = oldItemID
        newItem.setAttribute(
            ItemAttribute.DESCRIPTION,
            "You bought this item in the Store.\nUnwrap it in your own house to create a <$itemName>.",
        )
        if (hiddenCharges > 0) {
            newItem.setAttribute(ItemAttribute.DATE, hiddenCharges.toLong())
        }
        if (amount > 0) {
            newItem.setAttribute(ItemAttribute.AMOUNT, amount.toLong())
        }
        startDecaying(newItem)
        return newItem
    }

    /**
     * `Game::unwrapItem` (src/game/game.cpp:5501): ownership and bed-limit
     * gates, then the transform back to `unWrapId`, restoring the store
     * attribute, registering the bed, the hidden cask charges and clearing the
     * wrap description.
     */
    fun unwrapItem(item: Item, unWrapId: Int, house: House?, player: Player?) {
        if (item.hasOwner() && !(player != null && item.isOwner(player.getGUID()))) {
            if (player != null) {
                sendCancelMessage(player, ReturnValue.ITEMISNOTYOURS)
            }
            return
        }

        val hiddenCharges = (item.getAttribute(ItemAttribute.DATE) as? Long ?: 0L).toInt()
        val newType = itemTypes?.get(unWrapId)
        if (player != null && house != null && newType?.isBed == true &&
            house.maxBeds > -1 && house.getBedCount() >= house.maxBeds.toLong()
        ) {
            sendCancelMessageText(player, "You reached the maximum beds in this house")
            return
        }

        var amount = (item.getAttribute(ItemAttribute.AMOUNT) as? Long ?: 0L).toInt()
        if (amount == 0) {
            amount = 1
        }

        val attributeStore = item.getAttribute(ItemAttribute.STORE) as? Long ?: 0L
        val newItem = transformItem(item, unWrapId, amount) ?: return
        if (attributeStore != 0L) {
            newItem.setAttribute(ItemAttribute.STORE, attributeStore)
        }
        if (house != null && newType?.isBed == true) {
            val bed = newItem.getBed()
            if (bed != null) {
                house.addBed(bed)
            }
        }
        if (hiddenCharges > 0 && Tools.isCaskItem(unWrapId)) {
            newItem.setSubType(hiddenCharges)
        }
        itemUnWrapIds.remove(newItem)
        newItem.removeAttribute(ItemAttribute.DESCRIPTION)
        startDecaying(newItem)
    }

    /**
     * `Game::transformItem` (src/game/game.cpp:3401) reduced to the paths used
     * by [wrapItem]/[unwrapItem] and the charged-item transforms
     * (`g_game().transformItem(item, item->getID(), charges - 1)`,
     * player.cpp:1065, 7588, 7697, 7718): the no-op early return, the
     * always-on-top re-add, the charged/stackable `newCount == 0` removal or
     * `ItemType::decayTo` replacement (game.cpp:3437-3458), the
     * same-`ItemTypes_t` in-place update gated by `ItemType::hasSubType` and
     * the replacement path of `Item::transform` (src/items/item.cpp:3501).
     *
     * [internalRemoveItem] resolves the parent through the facade's tracked
     * tile, so a charged item equipped on a player is not unlinked here
     * (PARITY GAP; Canary removes through the item's `Cylinder`).
     *
     * PARITY GAPS: `Item::canTransform`, the browse-field remap, the decaying
     * `decayTo` branch, `Item::setDefaultSubtype` on a group change, the
     * quiver inventory update and the `postAddNotification` spectator fanout
     * are not modelled.
     */
    fun transformItem(item: Item, newId: Int, newCount: Int = -1): Item? {
        if (item.id == newId && (newCount == -1 || (newCount == item.getSubType() && newCount != 0))) {
            return item
        }

        val cylinder = item.getParent() ?: itemParents[item] ?: return null
        val itemIndex = cylinder.getThingIndex(item)
        if (itemIndex == -1) {
            return item
        }

        val newType = itemTypes?.get(newId) ?: return item
        if (newType.id == 0) {
            return item
        }

        val curType = item.type ?: itemTypes.get(item.id)
        if (((curType?.alwaysOnTopOrder ?: 0) != 0) != (newType.alwaysOnTopOrder != 0)) {
            cylinder.removeThing(item, item.getItemCount())
            item.setID(newId)
            if (newCount != -1) {
                item.setSubType(newCount)
            }
            cylinder.addThing(item)
            startDecaying(item)
            return item
        }

        if (curType != null && curType.type == newType.type) {
            if (newCount == 0 && (item.isStackable() || item.hasAttribute(ItemAttribute.CHARGES))) {
                if (item.isStackable()) {
                    internalRemoveItem(item)
                    return null
                }

                var newItemId = newId
                if (curType.id == newType.id) {
                    newItemId = curType.decayTo
                }

                if (newItemId < 0) {
                    internalRemoveItem(item)
                    return null
                } else if (newItemId != newId) {
                    return replaceItem(item, newItemId, -1)
                } else {
                    return transformItem(item, newItemId)
                }
            }

            val count = if (newCount != -1 && newType.hasSubType()) newCount else item.getSubType()
            cylinder.updateThing(item, newId, count)
            startDecaying(item)
            return item
        }

        return replaceItem(item, newId, newCount)
    }

    /**
     * `Item::transform` (src/items/item.cpp:3501): the old item is replaced in
     * place by a freshly created [newId] instance that inherits the remaining
     * duration. Shared by [transformItem]'s replacement tail and the charged
     * `ItemType::decayTo` prototype path (game.cpp:3452).
     */
    private fun replaceItem(item: Item, newId: Int, newCount: Int): Item? {
        val cylinder = item.getParent() ?: itemParents[item] ?: return null
        val itemIndex = cylinder.getThingIndex(item)
        if (itemIndex == -1) {
            return item
        }

        val createCount = if (newCount == 0) 1 else (newCount and 0xFFFF)
        val newItem = Item.create(newId, createCount, itemTypes)
        val duration = item.getDuration()
        if (duration > 0) {
            newItem.setDuration(duration)
        }

        cylinder.replaceThing(itemIndex, newItem)
        cylinder.postAddNotification(newItem, cylinder, itemIndex)
        item.resetParent()
        cylinder.postRemoveNotification(item, cylinder, itemIndex)
        item.stopDecaying()
        startDecaying(newItem)
        return newItem
    }

    /** `Game::setTransferPlayerHouseItems` (src/game/game.cpp:12399). */
    fun setTransferPlayerHouseItems(houseId: Int, playerId: Long) {
        transferHouseItemsToPlayer[houseId] = playerId
    }

    /**
     * `Game::transferHouseItemsToDepot` (src/game/game.cpp:12363): behind the
     * `TOGGLE_HOUSE_TRANSFER_ON_SERVER_RESTART` config, loads each recorded
     * owner, moves the house items to his depot inbox through
     * `House::tryTransferOwnership(serverStartup = true)`, schedules the new
     * owner `-1` and saves the map once at least one transfer succeeded.
     */
    fun transferHouseItemsToDepot() {
        if (!toggleHouseTransferOnServerRestart) {
            return
        }

        if (transferHouseItemsToPlayer.isNotEmpty()) {
            logger.info { "Initializing house transfer items" }
        }

        var transferSuccess = 0
        for ((houseId, playerGuid) in transferHouseItemsToPlayer) {
            val house = map.houses.getHouse(houseId) ?: continue
            val offlinePlayer = loadPlayerById(playerGuid) ?: continue

            logger.info { "Tranfering items to the inbox from player '${offlinePlayer.name}'" }
            if (house.tryTransferOwnership(offlinePlayer, true)) {
                transferSuccess++
                house.setNewOwnerGuid(-1, true)
            }
        }

        if (transferSuccess > 0) {
            logger.info { "Finished house transfer items from '$transferSuccess' players" }
            transferHouseItemsToPlayer.clear()
            map.save()
        }
    }

    /**
     * `Game::playerHighscores` (src/game/game.cpp:10124): drops a request while
     * the player already has an async highscore task, resolves the SQL through
     * the query cache and schedules it on `g_databaseTasks()`; the callback
     * runs [processHighscoreResults].
     */
    fun playerHighscores(
        player: Player,
        type: HighscoreType_t,
        category: Int,
        vocation: Long,
        worldName: String,
        page: Int,
        entriesPerPage: Int,
    ) {
        if (player.hasAsyncOngoingTask(PlayerAsyncTask.Highscore)) {
            return
        }
        if (entriesPerPage == 0) {
            sendHighscoresNoData(player)
            return
        }

        val resolved = getSkillNameById(category)
        val categoryName = resolved.name
        val resolvedCategory = resolved.category

        val query = when (type) {
            HighscoreType_t.HIGHSCORE_GETENTRIES -> generateHighscoreOrGetCachedQueryForEntries(
                categoryName,
                page,
                entriesPerPage,
                Tools.getVocationIdFromClientId(vocation),
            )

            HighscoreType_t.HIGHSCORE_OURRANK -> generateHighscoreOrGetCachedQueryForOurRank(
                categoryName,
                entriesPerPage,
                player.getGUID(),
                Tools.getVocationIdFromClientId(vocation),
            )
        }
        if (query.isEmpty()) {
            sendHighscoresNoData(player)
            return
        }

        val playerID = player.id
        databaseTasks.store(query) { result, _ ->
            processHighscoreResults(result, playerID, resolvedCategory, vocation, entriesPerPage)
        }
        player.addAsyncOngoingTask(PlayerAsyncTask.Highscore)
    }

    /**
     * `Game::getSkillNameById` (src/game/game.cpp:10157). The C++ `uint8_t&`
     * out-parameter becomes [HighscoreSkillName.category]: an unknown category
     * resolves to `EXPERIENCE` with the `experience` column.
     */
    fun getSkillNameById(skill: Int): HighscoreSkillName = when (skill) {
        HighscoreCategories_t.FIST_FIGHTING.value -> HighscoreSkillName("skill_fist", skill)
        HighscoreCategories_t.CLUB_FIGHTING.value -> HighscoreSkillName("skill_club", skill)
        HighscoreCategories_t.SWORD_FIGHTING.value -> HighscoreSkillName("skill_sword", skill)
        HighscoreCategories_t.AXE_FIGHTING.value -> HighscoreSkillName("skill_axe", skill)
        HighscoreCategories_t.DISTANCE_FIGHTING.value -> HighscoreSkillName("skill_dist", skill)
        HighscoreCategories_t.SHIELDING.value -> HighscoreSkillName("skill_shielding", skill)
        HighscoreCategories_t.FISHING.value -> HighscoreSkillName("skill_fishing", skill)
        HighscoreCategories_t.MAGIC_LEVEL.value -> HighscoreSkillName("maglevel", skill)
        HighscoreCategories_t.BOSS_POINTS.value -> HighscoreSkillName("boss_points", skill)
        else -> HighscoreSkillName("experience", HighscoreCategories_t.EXPERIENCE.value)
    }

    /**
     * `Game::generateHighscoreQuery` (src/game/game.cpp:9890). The `uint32_t`
     * page arithmetic wraps like the C++ code; `GROUP_TYPE_GAMEMASTER` filters
     * the staff out of the ranking.
     */
    fun generateHighscoreQuery(
        categoryName: String,
        page: Int,
        entriesPerPage: Int,
        vocation: Long,
        playerGUID: Long = 0,
    ): String {
        if (categoryName.isEmpty()) {
            logger.error { "Category name cannot be empty." }
            return ""
        }
        if (entriesPerPage == 0) {
            logger.warn { "[generateHighscoreQuery] entriesPerPage cannot be 0." }
            return ""
        }

        val startPage = ((page - 1).toLong() * entriesPerPage) and 0xFFFFFFFFL
        val endPage = (startPage + entriesPerPage) and 0xFFFFFFFFL
        val entriesStr = entriesPerPage.toString()

        val vocationCondition = if (vocation != 0xFFFFFFFFL) generateVocationConditionHighscore(vocation) else ""
        val countVocationCondition =
            if (vocation != 0xFFFFFFFFL) generateVocationConditionHighscore(vocation, " AND ") else ""

        var query = "SELECT `id`, `name`, `level`, `vocation`, `points`, `rank`, `rn` AS `entries`, " +
            "(SELECT COUNT(*) FROM `players` WHERE `group_id` < $GROUP_TYPE_GAMEMASTER$countVocationCondition) AS `total`, "

        query += if (playerGUID != 0L) {
            "(@ourRow DIV $entriesStr) + 1 AS `page` FROM ("
        } else {
            "$page AS `page` FROM ("
        }

        query += "SELECT `id`, `name`, `level`, `vocation`, `$categoryName` AS `points`, " +
            "@curRank := IF(@prevRank = `$categoryName`, @curRank, IF(@prevRank := `$categoryName`, @curRank + 1, @curRank + 1)) AS `rank`, " +
            "(@row := @row + 1) AS `rn`"

        if (playerGUID != 0L) {
            query += ", @ourRow := IF(`id` = $playerGUID, @row - 1, @ourRow) AS `rw`"
        }

        query += " FROM (SELECT `id`, `name`, `level`, `vocation`, `$categoryName` FROM `players` `p`, " +
            "(SELECT @curRank := 0, @prevRank := NULL, @row := 0, @ourRow := 0) `r` " +
            "WHERE `group_id` < $GROUP_TYPE_GAMEMASTER ORDER BY `$categoryName` DESC) `t`"

        if (vocation != 0xFFFFFFFFL) {
            query += vocationCondition
        }

        query += ") `T` WHERE "

        query += if (playerGUID != 0L) {
            "`rn` > ((@ourRow DIV $entriesStr) * $entriesStr) AND `rn` <= (((@ourRow DIV $entriesStr) * $entriesStr) + $entriesStr)"
        } else {
            "`rn` > $startPage AND `rn` <= $endPage"
        }

        return query
    }

    /** `Game::generateVocationConditionHighscore` (src/game/game.cpp:9968). */
    fun generateVocationConditionHighscore(vocation: Long, conditionPrefix: String = ""): String {
        val queryPart = StringBuilder()
        var firstVocation = true

        for (voc in vocations?.getVocations()?.values ?: emptyList()) {
            if (voc.getFromVocation().toLong() == vocation) {
                if (firstVocation) {
                    queryPart.append(conditionPrefix).append("(`vocation` = ").append(voc.getId())
                    firstVocation = false
                } else {
                    queryPart.append(" OR `vocation` = ").append(voc.getId())
                }
            }
        }

        if (!firstVocation) {
            queryPart.append(")")
        }

        return queryPart.toString()
    }

    /** `Game::cacheQueryHighscore` (src/game/game.cpp:10079). */
    private fun cacheQueryHighscore(key: String, query: String, page: Long, entriesPerPage: Int) {
        queryCache[key] = QueryHighscoreCacheEntry(query, page, entriesPerPage)
    }

    /** `Game::generateHighscoreOrGetCachedQueryForEntries` (src/game/game.cpp:10084). */
    fun generateHighscoreOrGetCachedQueryForEntries(
        categoryName: String,
        page: Int,
        entriesPerPage: Int,
        vocation: Long,
    ): String {
        val cacheKey = "Entries_${categoryName}_${page}_${entriesPerPage}_$vocation"
        queryCache[cacheKey]?.let { cached ->
            if (cached.page == page.toLong()) {
                return cached.query
            }
        }

        val newQuery = generateHighscoreQuery(categoryName, page, entriesPerPage, vocation)
        if (newQuery.isNotEmpty()) {
            cacheQueryHighscore(cacheKey, newQuery, page.toLong(), entriesPerPage)
        }

        return newQuery
    }

    /** `Game::generateHighscoreOrGetCachedQueryForOurRank` (src/game/game.cpp:10104). */
    fun generateHighscoreOrGetCachedQueryForOurRank(
        categoryName: String,
        entriesPerPage: Int,
        playerGUID: Long,
        vocation: Long,
    ): String {
        val cacheKey = "OurRank_${categoryName}_${entriesPerPage}_${playerGUID}_$vocation"
        queryCache[cacheKey]?.let { cached ->
            if (cached.page == entriesPerPage.toLong()) {
                return cached.query
            }
        }

        val newQuery = generateHighscoreQuery(categoryName, 0, entriesPerPage, vocation, playerGUID)
        if (newQuery.isNotEmpty()) {
            cacheQueryHighscore(cacheKey, newQuery, entriesPerPage.toLong(), entriesPerPage)
        }

        return newQuery
    }

    /**
     * `Game::processHighscoreResults` (src/game/game.cpp:9986): resets the
     * async task, answers `sendHighscoresNoData` for a missing result and
     * serves the 10-minute [highscoreCache] frame when present, otherwise
     * builds the rows through the injected [vocations] registry.
     *
     * PARITY GAP: the per-row loyalty title stays empty exactly like the C++
     * `// todo get loyalty title from player` branch.
     */
    fun processHighscoreResults(
        result: CyclopediaQueryResult?,
        playerID: Long,
        category: Int,
        vocationCID: Long,
        entriesPerPage: Int,
    ) {
        val player = getPlayerByID(playerID) ?: return

        player.resetAsyncOngoingTask(PlayerAsyncTask.Highscore)

        if (result == null) {
            sendHighscoresNoData(player)
            return
        }

        val page = result.getNumber("page") and 0xFFFFL
        val pages = calculateHighscorePages(result.getNumber("total") and 0xFFFFFFFFL, entriesPerPage)

        val vocationId = Tools.getVocationIdFromClientId(vocationCID)
        val cacheKey = "Highscore_${category}_${vocationId}_${entriesPerPage}_$page"

        val now = Tools.getTimeNow()
        val cached = highscoreCache[cacheKey]
        if (cached != null && now - cached.timestamp < HIGHSCORE_CACHE_EXPIRATION_SECONDS) {
            sendHighscores(player, cached.characters, category, vocationCID, cached.page.toInt(), cached.pages, cached.timestamp)
        } else {
            val characters = mutableListOf<HighscoresWindowSerializer.HighscoreCharacterEntry>()
            do {
                val voc = vocations?.getVocation(result.getNumber("vocation").toInt())
                val characterVocation = voc?.getClientId() ?: 0
                characters.add(
                    HighscoresWindowSerializer.HighscoreCharacterEntry(
                        rank = result.getNumber("rank"),
                        name = result.getString("name"),
                        loyaltyTitle = "",
                        vocation = characterVocation,
                        level = result.getNumber("level").toInt(),
                        id = result.getNumber("id"),
                        points = result.getNumber("points"),
                    ),
                )
            } while (result.next())

            sendHighscores(player, characters, category, vocationCID, page.toInt(), pages, Tools.getTimeNow())
            highscoreCache[cacheKey] = HighscoreCacheEntry(characters, page, pages, now)
        }
    }

    /** `Game::saveMotdNum` (src/game/game.cpp:9613). */
    fun saveMotdNum() {
        val db = database ?: return

        db.executeQuery("UPDATE `server_config` SET `value` = '$motdNum' WHERE `config` = 'motd_num'")
        db.executeQuery(
            "UPDATE `server_config` SET `value` = '${Tools.transformToSHA1(serverMotd)}' WHERE `config` = 'motd_hash'",
        )
    }

    /** `Game::getMotdHash` (src/game/game.hpp:559). */
    fun getMotdHash(): String = motdHash

    /** `Game::getMotdNum` (src/game/game.hpp:562). */
    fun getMotdNum(): Long = motdNum

    /** `Game::incrementMotdNum` (src/game/game.hpp:566). */
    fun incrementMotdNum() {
        motdNum++
    }

    /** `Tile::getTopDownItem` (src/items/tile.cpp:408-413, `TileItemVector::getTopDownItem`). */
    private fun topDownItem(tile: Tile): Item? = tile.getTopDownItem()

    /** `Tile::getThingIndex` equivalent (src/items/tile.cpp:1443): ground is 0, then item list. */
    private fun thingIndex(tile: Tile, item: Item): Int {
        if (item === tile.ground) {
            return 0
        }
        val index = tile.items.indexOfFirst { it === item }
        return if (index < 0) -1 else index + if (tile.ground != null) 1 else 0
    }

    /** `Item::isStackable` (src/items/item.hpp:488). */
    private fun isStackable(item: Item): Boolean = item.type?.isStackable == true

    /** Subset of `Item::equals` (src/items/item.cpp:730): id and stackable flag. */
    private fun sameItemType(item: Item, other: Item?): Boolean =
        other != null && item.id == other.id && isStackable(item) && isStackable(other)

    /** `Item::getStackSize` (src/items/item.hpp:595) with Canary's `stackSize` default of 100. */
    private fun mergeCapacity(toItem: Item, m: Int): Int {
        val capacity = stackSize(toItem) - toItem.count
        return if (capacity <= 0) m else minOf(capacity, m)
    }

    private fun stackSize(item: Item): Int = if (isStackable(item)) DEFAULT_STACK_SIZE else 1

    private fun cloneItem(item: Item, count: Int): Item = Item(id = item.id, count = count, type = item.type)

    /**
     * `Tile::addThing(int32_t, Thing)` (src/items/tile.cpp:1131): down items
     * insert at the front of the down range (reverse insertion order) and
     * always-on-top items order by `alwaysOnTopOrder`, so the tile's
     * `getThingIndex`/`updateThing` see everything this facade adds. The
     * facade's item-to-tile registry (the port's `Cylinder` link used by
     * [internalRemoveItem]) is kept in step.
     */
    private fun addThing(tile: Tile, item: Item, index: Int) {
        tile.addThing(index, item)
        item.setParent(tile)
        itemParents[item] = tile
    }

    /** `Tile::queryAdd` item branch (src/items/tile.cpp:691-962). */
    private fun queryAdd(tile: Tile, flags: Int): ReturnValue {
        if (flags and CylinderFlags.FLAG_NOLIMIT != 0) {
            return ReturnValue.NOERROR
        }
        if (tile.items.size >= MAX_TILE_ITEMS) {
            return ReturnValue.NOTPOSSIBLE
        }
        if (tile.ground == null) {
            return ReturnValue.NOTPOSSIBLE
        }
        return ReturnValue.NOERROR
    }

    /** `Tile::queryMaxCount` (src/items/tile.cpp:978). */
    private fun queryMaxCount(count: Int): Int = maxOf(1, count)

    /** `Tile::queryRemove` (src/items/tile.cpp:983). */
    private fun queryRemove(tile: Tile, item: Item, count: Int, flags: Int): ReturnValue {
        if (thingIndex(tile, item) < 0) {
            return ReturnValue.NOTPOSSIBLE
        }
        if (count == 0) {
            return ReturnValue.NOTPOSSIBLE
        }
        if (isStackable(item) && count > item.count) {
            return ReturnValue.NOTPOSSIBLE
        }
        return ReturnValue.NOERROR
    }

    /** `Tile::removeThing` (src/items/tile.cpp:1344): stackable partial decrement, otherwise unlink. */
    private fun removeThing(tile: Tile, item: Item, count: Int) {
        if (item === tile.ground) {
            tile.ground = null
            item.resetParent()
            itemParents.remove(item)
            return
        }

        if (isStackable(item) && count != item.count) {
            item.count = maxOf(0, item.count - count)
        } else {
            tile.removeItem(item)
            item.resetParent()
            itemParents.remove(item)
        }
    }

    /**
     * Minimal port of `Spectators::getSpectators` creature query
     * (src/map/spectators.cpp:121-201): the creatures placed through this facade
     * inside the default viewport rectangle (`MAP_MAX_VIEW_PORT_X/Y`) of
     * `Spectators::find` (spectators.cpp:205-212), honouring the `multifloor`
     * z-range and the per-floor offset shift (spectators.cpp:122-138, 182-185).
     * The map-sector creature lists are the source of truth (`Spectators`
     * queries the [map] sectors), exactly like every `Spectators().find<...>`
     * call in game.cpp.
     */
    private fun spectatorsOf(centerPos: Position, multifloor: Boolean): List<Creature> =
        Spectators(map).find<Creature>(centerPos, multifloor).data()

    /**
     * `Game::getCreatureByID` (src/game/game.cpp:1296). The port resolves online
     * players through [getPlayerByID] and every other placed creature through
     * the facade's placement index; Canary's typed monster/NPC registries are
     * not modeled (PARITY GAP).
     */
    fun getCreatureByID(creatureId: Long): Creature? {
        getPlayerByID(creatureId)?.let { return it }
        return placedCreatures.keys.firstOrNull { it.id == creatureId }
    }

    /**
     * `Game::playerCloseContainer` (src/game/game.cpp:5028):
     * `Player::closeContainer` plus the `sendCloseContainer` protocol send.
     */
    fun playerCloseContainer(playerId: Long, cid: Int) {
        val player = getPlayerByID(playerId) ?: return
        player.closeContainer(cid)
        player.sendCloseContainer(cid)
    }

    /**
     * `Game::playerMoveUpContainer` (src/game/game.cpp:5038): replaces the open
     * container with its real parent, honouring the paginated-container index.
     *
     * PARITY GAP: the browse-field branch (`g_events().eventPlayerOnBrowseField`,
     * `Game::browseFields`, `Container::createBrowseField`) is not ported, so a
     * top-level container has no parent to move to.
     */
    fun playerMoveUpContainer(playerId: Long, cid: Int) {
        val player = getPlayerByID(playerId) ?: return
        val container = player.getContainerByID(cid) ?: return
        val parentContainer = container.getRealParent() as? Container ?: return

        if (parentContainer.hasOwner() && !parentContainer.isOwner(player)) {
            sendCancelMessage(player, ReturnValue.ITEMISNOTYOURS)
            return
        }

        if (parentContainer.hasPagination() && parentContainer.hasParent()) {
            val indexContainer =
                (parentContainer.getThingIndex(container) / parentContainer.capacity) * parentContainer.capacity
            player.addContainer(cid, parentContainer)
            player.setContainerIndex(cid, indexContainer)
            player.sendContainer(cid, parentContainer, parentContainer.hasParent(), indexContainer)
        } else {
            player.addContainer(cid, parentContainer)
            player.sendContainer(cid, parentContainer, parentContainer.hasParent(), player.getContainerIndex(cid))
        }
    }

    /** `Game::playerSetQuickLootFallback` (src/game/game.cpp:6898). */
    fun playerSetQuickLootFallback(playerId: Long, fallback: Boolean) {
        val player = getPlayerByID(playerId) ?: return
        player.quickLootFallbackToMainContainer = fallback
    }

    /** `Game::playerQuickLootBlackWhitelist` (src/game/game.cpp:6907). */
    fun playerQuickLootBlackWhitelist(playerId: Long, filter: QuickLootFilter_t, itemIds: List<Int>) {
        val player = getPlayerByID(playerId) ?: return
        player.quickLootFilter = filter
        player.quickLootListItemIds = itemIds
    }

    // --- quick loot / managed containers (game.cpp:3596-6930) ------------------

    /** `g_configManager().getBoolean(AUTOBANK)` default (configmanager.cpp:85). */
    var autobank: Boolean = false

    /** `g_configManager().getBoolean(TOGGLE_GOLD_POUCH_ALLOW_ANYTHING)` default (configmanager.cpp). */
    var toggleGoldPouchAllowAnything: Boolean = false

    /** `g_configManager().getBoolean(TOGGLE_GOLD_POUCH_QUICKLOOT_ONLY)` default (configmanager.cpp). */
    var toggleGoldPouchQuickLootOnly: Boolean = false

    /** `g_configManager().getNumber(QUICK_LOOT_MAX_CORPSES)` default (configmanager.cpp:301). */
    var quickLootMaxCorpses: Int = 30

    /** `Player::sendLootStats(item, count)` protocol seam (game.cpp:3643). */
    var sendLootStats: (Player, Item, Int) -> Unit = { _, _, _ -> }

    /** `Player::sendLootContainers()` protocol seam (game.cpp:6852). */
    var sendLootContainers: (Player) -> Unit = { _ -> }

    /** `Item::sendUpdateToClient(player)` / `Container::sendUpdateToClient(player)` protocol seam (game.cpp:3752, 6572). */
    var sendItemUpdateToClient: (Player, Item) -> Unit = { _, _ -> }

    /** `Player::getReward(rewardId, autoOpen)` (player.cpp) seam behind the reward-corpse branch (game.cpp:6559). */
    var getRewardContainer: (Player, Long) -> Container? = { _, _ -> null }

    /**
     * `Player::canOpenCorpse(ownerId)` (player.cpp:1379) seam. The C++
     * `m_party->canOpenCorpse(ownerId)` branch is not ported (PARITY GAP).
     */
    var canOpenCorpse: (Player, Long) -> Boolean = { player, ownerId -> player.id == ownerId }

    /** `Player::canDoAction()` (player.cpp:3432) seam; the Kotlin [Player] has no action timer yet (PARITY GAP). */
    var canDoAction: (Player) -> Boolean = { true }

    /**
     * `Container::getHoldingPlayer()` (container.cpp) seam. `null` models the
     * unported holding-player bookkeeping, so the `playerSetManagedContainer`
     * ownership gate only rejects a resolved foreign holder.
     */
    var containerHoldingPlayer: (Container) -> Player? = { null }

    /**
     * Facade storage for `Player::lastQuickLootNotification` (player.hpp:1876);
     * the Kotlin [Player] does not carry the member yet (PARITY GAP).
     */
    private val lastQuickLootNotification = IdentityHashMap<Player, Long>()

    /**
     * `Game::getObjectCategory(item)` (src/game/game.cpp:3946): a worth makes
     * the item gold, otherwise the item type decides.
     */
    fun getObjectCategory(item: Item?): ObjectCategory_t {
        if (item == null) {
            return ObjectCategory_t.OBJECTCATEGORY_NONE
        }

        if (item.getWorth() != 0L) {
            return ObjectCategory_t.OBJECTCATEGORY_GOLD
        }
        return getObjectCategory(item.type ?: return ObjectCategory_t.OBJECTCATEGORY_OTHERS)
    }

    /** `Game::getObjectCategory(const ItemType &)` (src/game/game.cpp:3962). */
    fun getObjectCategory(itemType: ItemType): ObjectCategory_t {
        if (itemType.weaponType != WeaponType_t.WEAPON_NONE) {
            return when (itemType.weaponType) {
                WeaponType_t.WEAPON_FIST -> ObjectCategory_t.OBJECTCATEGORY_FISTWEAPONS
                WeaponType_t.WEAPON_SWORD -> ObjectCategory_t.OBJECTCATEGORY_SWORDS
                WeaponType_t.WEAPON_CLUB -> ObjectCategory_t.OBJECTCATEGORY_CLUBS
                WeaponType_t.WEAPON_AXE -> ObjectCategory_t.OBJECTCATEGORY_AXES
                WeaponType_t.WEAPON_SHIELD -> ObjectCategory_t.OBJECTCATEGORY_SHIELDS
                WeaponType_t.WEAPON_MISSILE, WeaponType_t.WEAPON_DISTANCE -> ObjectCategory_t.OBJECTCATEGORY_DISTANCEWEAPONS
                WeaponType_t.WEAPON_WAND -> ObjectCategory_t.OBJECTCATEGORY_WANDS
                WeaponType_t.WEAPON_AMMO -> ObjectCategory_t.OBJECTCATEGORY_AMMO
                else -> ObjectCategory_t.OBJECTCATEGORY_DEFAULT
            }
        }

        if (itemType.slotPosition != SLOTP_HAND) {
            return when {
                itemType.slotPosition and SLOTP_HEAD != 0 -> ObjectCategory_t.OBJECTCATEGORY_HELMETS
                itemType.slotPosition and SLOTP_NECKLACE != 0 -> ObjectCategory_t.OBJECTCATEGORY_NECKLACES
                itemType.slotPosition and SLOTP_BACKPACK != 0 -> ObjectCategory_t.OBJECTCATEGORY_CONTAINERS
                itemType.slotPosition and SLOTP_ARMOR != 0 -> ObjectCategory_t.OBJECTCATEGORY_ARMORS
                itemType.slotPosition and SLOTP_LEGS != 0 -> ObjectCategory_t.OBJECTCATEGORY_LEGS
                itemType.slotPosition and SLOTP_FEET != 0 -> ObjectCategory_t.OBJECTCATEGORY_BOOTS
                itemType.slotPosition and SLOTP_RING != 0 -> ObjectCategory_t.OBJECTCATEGORY_RINGS
                else -> ObjectCategory_t.OBJECTCATEGORY_DEFAULT
            }
        }

        return when (itemType.type) {
            ItemTypes_t.ITEM_TYPE_RUNE -> ObjectCategory_t.OBJECTCATEGORY_RUNES
            ItemTypes_t.ITEM_TYPE_CREATUREPRODUCT -> ObjectCategory_t.OBJECTCATEGORY_CREATUREPRODUCTS
            ItemTypes_t.ITEM_TYPE_FOOD -> ObjectCategory_t.OBJECTCATEGORY_FOOD
            ItemTypes_t.ITEM_TYPE_VALUABLE -> ObjectCategory_t.OBJECTCATEGORY_VALUABLES
            ItemTypes_t.ITEM_TYPE_POTION -> ObjectCategory_t.OBJECTCATEGORY_POTIONS
            else -> ObjectCategory_t.OBJECTCATEGORY_OTHERS
        }
    }

    /** `Player::isQuickLootListedItem(item)` (player.cpp:3420). */
    private fun isQuickLootListedItem(player: Player, item: Item): Boolean =
        player.quickLootListItemIds.contains(item.id)

    /** `Item::getCorpseOwner()` (item.hpp:132) through the `CORPSEOWNER` attribute. */
    private fun getCorpseOwner(item: Item): Long =
        (item.getAttribute(ItemAttribute.CORPSEOWNER) as? Number)?.toLong() ?: 0L

    /** `Item::isRewardCorpse()` (item.hpp:140): the owner is `INT32_MAX`. */
    private fun isRewardCorpse(item: Item): Boolean = getCorpseOwner(item) == Int.MAX_VALUE.toLong()

    /**
     * `Game::playerSetManagedContainer` (src/game/game.cpp:6822): only the
     * inventory/container `0xFFFF` position is accepted, the gold pouch is
     * category-locked to gold and loot-only, and the resolved container is
     * registered through `Player::refreshManagedContainer`.
     *
     * PARITY GAP: the `Cylinder::updateThing` refresh of the parent cylinders
     * has no protocol sink here.
     */
    fun playerSetManagedContainer(
        playerId: Long,
        category: ObjectCategory_t,
        pos: Position,
        stackPosition: Int,
        itemId: Int,
        isLootContainer: Boolean,
    ) {
        val player = getPlayerByID(playerId) ?: return
        if (pos.x != 0xFFFF) {
            return
        }

        val thing = internalGetThing(player, pos, stackPosition, itemId, StackPosType_t.STACKPOS_USEITEM)
        if (thing == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        val container = thing.getContainer()
        val allowConfig = toggleGoldPouchAllowAnything || toggleGoldPouchQuickLootOnly
        if (container == null || (container.id == Container.ITEM_GOLD_POUCH && category != ObjectCategory_t.OBJECTCATEGORY_GOLD && !allowConfig)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (container.id == Container.ITEM_GOLD_POUCH && !isLootContainer) {
            sendTextMessage(player, MessageClass.MESSAGE_FAILURE, "You can only set the gold pouch as a loot container.")
            return
        }

        val holder = containerHoldingPlayer(container)
        if (holder != null && holder !== player) {
            sendCancelMessageText(player, "You must be holding the container to set it as a loot container.")
            return
        }

        player.refreshManagedContainer(category, container, isLootContainer)
        sendLootContainers(player)
    }

    /** `Game::playerClearManagedContainer` (src/game/game.cpp:6867). */
    fun playerClearManagedContainer(playerId: Long, category: ObjectCategory_t, isLootContainer: Boolean) {
        val player = getPlayerByID(playerId) ?: return

        player.refreshManagedContainer(category, null, isLootContainer)
        sendLootContainers(player)
    }

    /** `Game::playerOpenManagedContainer` (src/game/game.cpp:6884). */
    fun playerOpenManagedContainer(playerId: Long, category: ObjectCategory_t, isLootContainer: Boolean) {
        val player = getPlayerByID(playerId) ?: return

        val container = player.getManagedContainer(category, isLootContainer) ?: return
        player.sendContainer(container.id and 0xFF, container, container.hasParent(), 0)
    }

    /**
     * `Game::findManagedContainer` (src/game/game.cpp:3757): the category
     * container, else the main backpack once when
     * `quickLootFallbackToMainContainer` is set. The `bool &fallbackConsumed`
     * out parameter becomes the returned pair's second component.
     */
    fun findManagedContainer(
        player: Player,
        fallbackConsumed: Boolean,
        category: ObjectCategory_t,
        isLootContainer: Boolean,
    ): Pair<Container?, Boolean> {
        player.getManagedContainer(category, isLootContainer)?.let { return it to fallbackConsumed }
        if (!player.quickLootFallbackToMainContainer || fallbackConsumed) {
            return null to fallbackConsumed
        }

        val mainBackpack = player.getBackpack() ?: return null to fallbackConsumed
        player.refreshManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, mainBackpack, isLootContainer)
        player.sendInventoryItem(Slots_t.CONST_SLOT_BACKPACK, player.getInventoryItem(Slots_t.CONST_SLOT_BACKPACK))
        return mainBackpack to true
    }

    /**
     * `Game::findNextAvailableContainer` (src/game/game.cpp:3774): advances the
     * iterator to the next container item and fixes up a last empty
     * sub-container. The mutated `lootContainer`/`lastSubContainer` and the
     * returned container collapse into the returned container; the caller keeps
     * the updated [ContainerIterator].
     */
    fun findNextAvailableContainer(containerIterator: ContainerIterator): Container? {
        var lastSubContainer: Container? = null
        while (containerIterator.hasNext()) {
            val current = containerIterator.current()
            val subContainer = current?.getContainer()
            containerIterator.advance()

            if (subContainer != null) {
                return subContainer
            }
        }

        // Fix last empty sub-container
        if (lastSubContainer != null && !lastSubContainer.empty()) {
            val cur = lastSubContainer.getItemByIndex(lastSubContainer.size() - 1)
            return cur?.getContainer()
        }

        return null
    }

    /**
     * `Game::handleFallbackLogic` (src/game/game.cpp:3798): rewinds onto the
     * main backpack when the fallback is set and not yet consumed. The mutated
     * `lootContainer`/`containerIterator` become the returned pair; `null`
     * models the C++ `false` return.
     */
    fun handleFallbackLogic(
        player: Player,
        containerIterator: ContainerIterator,
        fallbackConsumed: Boolean,
    ): Pair<Container, ContainerIterator>? {
        if (fallbackConsumed || !player.quickLootFallbackToMainContainer) {
            return null
        }

        val fallbackItem = player.getInventoryItem(Slots_t.CONST_SLOT_BACKPACK) ?: return null
        val fallbackContainer = fallbackItem.getContainer() ?: return null
        return fallbackContainer to fallbackContainer.iterator()
    }

    /**
     * `Game::processMoveOrAddItemToLootContainer` (src/game/game.cpp:3814):
     * move the item out of its parent when it has one, otherwise add it
     * directly. The `uint32_t &remainderCount` in/out parameter becomes the
     * returned pair's second component.
     */
    fun processMoveOrAddItemToLootContainer(
        item: Item,
        lootContainer: Container,
        remainderCount: Int,
        player: Player,
    ): Pair<ReturnValue, Int> {
        val parent = item.getParent()
        if (parent != null) {
            return internalMoveItemToCylinder(parent, lootContainer, item, item.getItemCount())
        }

        val ret = containerAddItem(lootContainer, item, INDEX_WHEREEVER)
        return ret to remainderCount
    }

    /**
     * `Game::processLootItems` (src/game/game.cpp:3828): move the item into the
     * target container, descending into the container tree (or the fallback
     * backpack) while `CONTAINERNOTENOUGHROOM` comes back and the remainder is
     * not yet zero.
     */
    fun processLootItems(player: Player, lootContainer: Container, item: Item): ReturnValue {
        var target = lootContainer
        var remainderCount = item.getItemCount()
        var fallbackConsumed = false
        var containerIterator = target.iterator()

        var ret: ReturnValue
        do {
            val outcome = processMoveOrAddItemToLootContainer(item, target, remainderCount, player)
            ret = outcome.first
            remainderCount = outcome.second
            if (ret != ReturnValue.CONTAINERNOTENOUGHROOM) {
                return ret
            }

            val nextContainer = findNextAvailableContainer(containerIterator)
            if (nextContainer == null) {
                val fallback = handleFallbackLogic(player, containerIterator, fallbackConsumed)
                if (fallback == null) {
                    break
                }
                target = fallback.first
                containerIterator = fallback.second
                fallbackConsumed = true
            } else {
                target = nextContainer
            }
        } while (remainderCount != 0)

        return ret
    }

    /**
     * `Game::internalCollectManagedItems` (src/game/game.cpp:3850): the
     * AUTOBANK coin deposit, the quick-loot list filter, the managed
     * container resolution and the item move.
     */
    fun internalCollectManagedItems(
        player: Player?,
        item: Item?,
        category: ObjectCategory_t,
        isLootContainer: Boolean = true,
    ): ReturnValue {
        if (player == null || item == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        if (autobank && (item.id == ITEM_GOLD_COIN || item.id == ITEM_PLATINUM_COIN || item.id == ITEM_CRYSTAL_COIN)) {
            val money = when (item.id) {
                ITEM_PLATINUM_COIN -> item.getItemCount().toLong() * 100
                ITEM_CRYSTAL_COIN -> item.getItemCount().toLong() * 10000
                else -> item.getItemCount().toLong()
            }
            val parent = item.getParent()
            if (parent != null) {
                parent.removeThing(item, item.getItemCount())
            } else {
                logger.debug { "Item has no parent" }
                return ReturnValue.NOTPOSSIBLE
            }
            setBankBalance(player, getBankBalance(player) + money)
            return ReturnValue.NOERROR
        }

        if (player.quickLootListItemIds.isNotEmpty()) {
            val isInList = player.quickLootListItemIds.contains(item.id)
            if (player.quickLootFilter == QuickLootFilter_t.QUICKLOOTFILTER_ACCEPTEDLOOT && !isInList) {
                return ReturnValue.NOTPOSSIBLE
            } else if (player.quickLootFilter == QuickLootFilter_t.QUICKLOOTFILTER_SKIPPEDLOOT && isInList) {
                return ReturnValue.NOTPOSSIBLE
            }
        }

        val found = findManagedContainer(player, false, category, isLootContainer)
        val lootContainer = found.first ?: return ReturnValue.NOTPOSSIBLE
        return processLootItems(player, lootContainer, item)
    }

    /**
     * `Game::playerQuickLootCorpse` (src/game/game.cpp:3596): the quick-loot
     * list partition, the per-item managed collection and the loot summary.
     *
     * PARITY GAPS: `Player::sendLootStats` runs through the [sendLootStats]
     * seam, `Container::clearLootHighlight`/`sendUpdateToClient` have no
     * protocol sink, and the object-category name lookup is [Tools].
     */
    fun playerQuickLootCorpse(player: Player?, corpse: Container?, position: Position?) {
        if (player == null || corpse == null) {
            return
        }

        val itemList = mutableListOf<Item>()
        val ignoreListItems = player.quickLootFilter == QuickLootFilter_t.QUICKLOOTFILTER_SKIPPEDLOOT

        var missedAnyGold = false
        var missedAnyItem = false

        val iterator = corpse.iterator()
        while (iterator.hasNext()) {
            val item = iterator.current() ?: break
            iterator.advance()

            val listed = isQuickLootListedItem(player, item)
            if ((listed && ignoreListItems) || (!listed && !ignoreListItems)) {
                if (item.getWorth() != 0L) {
                    missedAnyGold = true
                } else {
                    missedAnyItem = true
                }
                continue
            }

            itemList.add(item)
        }

        var shouldNotifyCapacity = false
        var shouldNotifyNotEnoughRoom = ObjectCategory_t.OBJECTCATEGORY_NONE

        var totalLootedGold = 0L
        var totalLootedItems = 0
        for (item in itemList) {
            val worth = item.getWorth()
            val baseCount = item.getItemCount()
            val category = getObjectCategory(item)

            val ret = internalCollectManagedItems(player, item, category)
            if (ret == ReturnValue.NOTENOUGHCAPACITY) {
                shouldNotifyCapacity = true
            } else if (ret == ReturnValue.CONTAINERNOTENOUGHROOM) {
                shouldNotifyNotEnoughRoom = category
            }

            val success = ret == ReturnValue.NOERROR
            if (worth != 0L) {
                missedAnyGold = missedAnyGold || !success
                if (success) {
                    sendLootStats(player, item, baseCount)
                    totalLootedGold += worth
                } else {
                    // item is not completely moved
                    totalLootedGold += worth - item.getWorth()
                }
            } else {
                missedAnyItem = missedAnyItem || !success
                if (success || item.getItemCount() != baseCount) {
                    totalLootedItems++
                    sendLootStats(player, item, item.getItemCount())
                }
            }
        }

        var hasLootAvailable = false
        val remainingIterator = corpse.iterator()
        while (remainingIterator.hasNext()) {
            val corpseItem = remainingIterator.current() ?: break
            remainingIterator.advance()

            val listed = isQuickLootListedItem(player, corpseItem)
            if ((listed && ignoreListItems) || (!listed && !ignoreListItems)) {
                continue
            }

            hasLootAvailable = true
            break
        }

        if (!hasLootAvailable) {
            corpse.clearLootHighlight(player)
        }

        val message = buildQuickLootSummaryMessage(
            totalLootedGold,
            missedAnyGold,
            totalLootedItems,
            missedAnyItem,
        )
        sendTextMessage(player, MessageClass.MESSAGE_LOOT, message)

        val notification = when {
            shouldNotifyCapacity -> "Attention! The loot you are trying to pick up is too heavy for you to carry."
            shouldNotifyNotEnoughRoom != ObjectCategory_t.OBJECTCATEGORY_NONE ->
                "Attention! The container assigned to category ${Tools.getObjectCategoryName(shouldNotifyNotEnoughRoom)} is full."
            else -> null
        } ?: return

        val now = otsysTimeMs()
        if ((lastQuickLootNotification[player] ?: 0L) + 15000L < now) {
            sendTextMessage(player, MessageClass.MESSAGE_GAME_HIGHLIGHT, notification)
        } else {
            sendTextMessage(player, MessageClass.MESSAGE_EVENT_ADVANCE, notification)
        }

        sendItemUpdateToClient(player, corpse)
        lastQuickLootNotification[player] = now
    }

    /**
     * `Game::playerQuickLoot` (src/game/game.cpp:6428): the position gates,
     * the item/corpse resolution and the managed-collection or
     * corpse-looting dispatch.
     *
     * PARITY GAPS: the `Player::canDoAction`/`createPlayerTask` deferral and
     * the walk-to-corpse auto-walk task scheduling are not ported; the
     * distance gate cancels or walks immediately. The reward-corpse lookup
     * runs through [getRewardContainer].
     */
    fun playerQuickLoot(
        playerId: Long,
        pos: Position,
        itemId: Int,
        stackPos: Int,
        defaultItem: Item?,
        lootAllCorpses: Boolean,
        autoLoot: Boolean,
    ) {
        val player = getPlayerByID(playerId) ?: return

        if (!autoLoot && !canDoAction(player)) {
            return
        }

        if (!autoLoot && pos.x != 0xFFFF) {
            if (!areInRange(pos, player.position, 1, 1, 0)) {
                // need to walk to the corpse first before looting it
                val listDir = getPathTo(player, pos, 0, 1, true, true)
                if (listDir != null) {
                    if (!queuePlayerAutoWalk(player, listDir)) {
                        return
                    }
                    return
                }
                sendCancelMessage(player, ReturnValue.THEREISNOWAY)
                return
            }
        } else if (!player.isPremium()) {
            sendCancelMessageText(player, "You must be premium.")
            return
        }

        if (!autoLoot) {
            player.setNextActionTask(null)
        }

        var item = defaultItem
        if (item == null) {
            item = internalGetThing(player, pos, stackPos, itemId, StackPosType_t.STACKPOS_FIND_THING)
            if (item == null) {
                sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                return
            }
        }

        if (item.getParent() == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        var bulkLoot = lootAllCorpses
        val corpse: Container?
        if (pos.x == 0xFFFF) {
            corpse = item.getParent()?.getContainer()
            if (corpse != null && corpse.id == Container.ITEM_BROWSEFIELD) {
                bulkLoot = true
                item.getContainer()
            }
        } else {
            corpse = item.getContainer()
        }

        if (corpse == null || corpse.hasAttribute(ItemAttribute.UNIQUEID) || corpse.hasAttribute(ItemAttribute.ACTIONID)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (!isRewardCorpse(corpse)) {
            val corpseOwner = getCorpseOwner(corpse)
            if (corpseOwner != 0L && !canOpenCorpse(player, corpseOwner)) {
                sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                return
            }
        }

        if (pos.x == 0xFFFF && !bulkLoot && !isRewardCorpse(corpse)) {
            val worth = item.getWorth()
            val category = getObjectCategory(item)
            val ret = internalCollectManagedItems(player, item, category)

            var message: String? = null
            if (ret == ReturnValue.NOTENOUGHCAPACITY) {
                message = "Attention! The loot you are trying to pick up is too heavy for you to carry."
            } else if (ret == ReturnValue.CONTAINERNOTENOUGHROOM) {
                message = "Attention! The container for ${Tools.getObjectCategoryName(category)} is full."
            } else {
                val prefix = if (ret == ReturnValue.NOERROR) {
                    sendLootStats(player, item, item.getItemCount())
                    "You looted "
                } else {
                    "You could not loot "
                }

                val suffix = if (worth != 0L) "$worth gold." else "1 item."
                sendTextMessage(player, MessageClass.MESSAGE_LOOT, prefix + suffix)
                sendItemUpdateToClient(player, corpse)
                return
            }

            val now = otsysTimeMs()
            if ((lastQuickLootNotification[player] ?: 0L) + 15000L < now) {
                sendTextMessage(player, MessageClass.MESSAGE_GAME_HIGHLIGHT, message)
            } else {
                sendTextMessage(player, MessageClass.MESSAGE_EVENT_ADVANCE, message)
            }

            lastQuickLootNotification[player] = now
        } else {
            if (isRewardCorpse(corpse)) {
                val rewardId = (corpse.getAttribute(ItemAttribute.DATE) as? Number)?.toLong() ?: 0L
                val rewardContainer = getRewardContainer(player, rewardId)
                if (rewardContainer != null) {
                    playerQuickLootCorpse(player, rewardContainer, corpse.getTile()?.position)
                }
            } else {
                if (!lootAllCorpses) {
                    playerQuickLootCorpse(player, corpse, corpse.getTile()?.position)
                } else {
                    playerLootAllCorpses(player, pos, true)
                }
            }
        }

        sendItemUpdateToClient(player, corpse)
    }

    /**
     * `Game::playerLootAllCorpses` (src/game/game.cpp:6575): every lootable
     * corpse on the target tile up to `QUICK_LOOT_MAX_CORPSES`.
     *
     * PARITY GAP: the reward-corpse owner bypass uses [isRewardCorpse] and the
     * `Player::canOpenCorpse` party branch is not ported.
     */
    fun playerLootAllCorpses(player: Player, pos: Position, lootAllCorpses: Boolean) {
        if (!lootAllCorpses) {
            return
        }

        val maxQuickLootCorpses = maxOf(1, quickLootMaxCorpses)
        val tile = map.getTile(pos)
        if (tile == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        var corpses = 0
        for (tileItem in tile.items) {
            val tileCorpse = tileItem.getContainer()
            if (tileCorpse == null || !tileCorpse.isCorpse() ||
                tileCorpse.hasAttribute(ItemAttribute.UNIQUEID) || tileCorpse.hasAttribute(ItemAttribute.ACTIONID)
            ) {
                continue
            }

            if (!isRewardCorpse(tileCorpse)) {
                val owner = getCorpseOwner(tileCorpse)
                if (owner != 0L && !canOpenCorpse(player, owner)) {
                    sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                    logger.debug {
                        "Player ${player.name} cannot loot corpse from id ${tileItem.id} in position ${tileItem.position}"
                    }
                    continue
                }
            }

            corpses++
            playerQuickLootCorpse(player, tileCorpse, tileCorpse.getTile()?.position)
            if (corpses >= maxQuickLootCorpses) {
                break
            }
        }

        if (corpses > 1) {
            sendTextMessage(player, MessageClass.MESSAGE_LOOT, "You looted $corpses corpses.")
        }
    }

    /**
     * `Game::playerLootNearby` (src/game/game.cpp:6766): the 3x3 tile scan
     * around the player, looting each corpse whose managed-collection snapshot
     * changes, with the "found but not looted" distinction of the C++
     * anonymous-namespace helpers.
     */
    fun playerLootNearby(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return

        if (!canDoAction(player)) {
            return
        }
        player.setNextActionTask(null)

        val maxQuickLootCorpses = maxOf(1, quickLootMaxCorpses)
        val playerPos = player.position
        var corpsesLooted = 0
        var anyCorpseFound = false

        var x = -1
        while (x <= 1 && corpsesLooted < maxQuickLootCorpses) {
            var y = -1
            while (y <= 1 && corpsesLooted < maxQuickLootCorpses) {
                val tileX = playerPos.x + x
                val tileY = playerPos.y + y
                if (tileX in 0..0xFFFF && tileY in 0..0xFFFF) {
                    val tile = map.getTile(Position(tileX, tileY, playerPos.z))
                    if (tile != null) {
                        for (tileItem in tile.items) {
                            val outcome = tryNearbyQuickLootCorpse(player, tileItem.getContainer())
                            anyCorpseFound = anyCorpseFound || outcome.foundCorpse
                            if (outcome.looted) {
                                corpsesLooted++
                            }
                            if (corpsesLooted >= maxQuickLootCorpses) {
                                break
                            }
                        }
                    }
                }
                y++
            }
            x++
        }

        sendNearbyQuickLootSummary(player, anyCorpseFound, corpsesLooted)
    }

    /** `NearbyQuickLootSnapshot` (game.cpp:6626-6640). */
    private class NearbyQuickLootSnapshot(
        var goldValue: Long = 0L,
        var stackableAmount: Long = 0L,
        var looseItems: Int = 0,
    ) {
        fun hasLoot(): Boolean = goldValue != 0L || stackableAmount != 0L || looseItems != 0

        fun differsFrom(other: NearbyQuickLootSnapshot): Boolean =
            goldValue != other.goldValue || stackableAmount != other.stackableAmount || looseItems != other.looseItems
    }

    /** `NearbyQuickLootOutcome` (game.cpp:6716-6719). */
    private class NearbyQuickLootOutcome(
        val foundCorpse: Boolean = false,
        val looted: Boolean = false,
    )

    /** `captureNearbyQuickLootSnapshot` (game.cpp:6642-6671). */
    private fun captureNearbyQuickLootSnapshot(player: Player, container: Container?): NearbyQuickLootSnapshot {
        val snapshot = NearbyQuickLootSnapshot()
        if (container == null) {
            return snapshot
        }

        val ignoreListItems = player.quickLootFilter == QuickLootFilter_t.QUICKLOOTFILTER_SKIPPEDLOOT
        val iterator = container.iterator()
        while (iterator.hasNext()) {
            val item = iterator.current() ?: break
            iterator.advance()

            val listed = isQuickLootListedItem(player, item)
            if ((listed && ignoreListItems) || (!listed && !ignoreListItems)) {
                continue
            }

            val worth = item.getWorth()
            if (worth != 0L) {
                snapshot.goldValue += worth
            } else if (item.isStackable() || item.hasAttribute(ItemAttribute.CHARGES)) {
                snapshot.stackableAmount += item.getItemCount()
            } else {
                snapshot.looseItems++
            }
        }

        return snapshot
    }

    /** `isNearbyLootableCorpse` (game.cpp:6692-6704). */
    private fun isNearbyLootableCorpse(player: Player, corpse: Container?): Boolean {
        if (corpse == null || !corpse.isCorpse() ||
            corpse.hasAttribute(ItemAttribute.UNIQUEID) || corpse.hasAttribute(ItemAttribute.ACTIONID)
        ) {
            return false
        }

        if (isRewardCorpse(corpse)) {
            return true
        }

        val owner = getCorpseOwner(corpse)
        return owner == 0L || canOpenCorpse(player, owner)
    }

    /** `resolveNearbyLootContainer` (game.cpp:6706-6714). */
    private fun resolveNearbyLootContainer(player: Player, corpse: Container): Container? {
        if (!isRewardCorpse(corpse)) {
            return corpse
        }

        val rewardId = (corpse.getAttribute(ItemAttribute.DATE) as? Number)?.toLong() ?: 0L
        return getRewardContainer(player, rewardId)
    }

    /** `tryNearbyQuickLootCorpse` (game.cpp:6721-6741). */
    private fun tryNearbyQuickLootCorpse(player: Player, corpse: Container?): NearbyQuickLootOutcome {
        if (!isNearbyLootableCorpse(player, corpse)) {
            return NearbyQuickLootOutcome()
        }

        val lootContainer = resolveNearbyLootContainer(player, corpse!!)
        val lootSnapshotBefore = captureNearbyQuickLootSnapshot(player, lootContainer)
        if (!lootSnapshotBefore.hasLoot()) {
            return NearbyQuickLootOutcome(foundCorpse = true)
        }

        playerQuickLootCorpse(player, lootContainer, corpse.getTile()?.position)
        if (lootContainer !== corpse) {
            sendItemUpdateToClient(player, corpse)
        }
        return NearbyQuickLootOutcome(looted = captureNearbyQuickLootSnapshot(player, lootContainer).differsFrom(lootSnapshotBefore))
    }

    /** `sendNearbyQuickLootSummary` (game.cpp:6743-6763). */
    private fun sendNearbyQuickLootSummary(player: Player, anyCorpseFound: Boolean, corpsesLooted: Int) {
        if (!anyCorpseFound) {
            sendCancelMessageText(player, "No lootable corpses nearby.")
            return
        }

        if (corpsesLooted == 0) {
            sendCancelMessageText(player, "You could not loot any nearby corpses.")
            return
        }

        if (corpsesLooted > 1) {
            sendTextMessage(player, MessageClass.MESSAGE_LOOT, "You looted $corpsesLooted corpses.")
        }
    }

    /** The `playerQuickLootCorpse` summary text (game.cpp:3678-3734). */
    private fun buildQuickLootSummaryMessage(
        totalLootedGold: Long,
        missedAnyGold: Boolean,
        totalLootedItems: Int,
        missedAnyItem: Boolean,
    ): String {
        if (totalLootedGold == 0L && !missedAnyGold && totalLootedItems == 0 && !missedAnyItem) {
            return "No loot."
        }

        val message = StringBuilder()
        val lootedAllGold = totalLootedGold != 0L && !missedAnyGold
        val lootedAllItems = totalLootedItems != 0 && !missedAnyItem
        if (lootedAllGold) {
            if (totalLootedItems != 0 || missedAnyItem) {
                message.append("You looted the complete $totalLootedGold gold")
                when {
                    lootedAllItems -> message.append(" and all dropped items")
                    totalLootedItems != 0 -> message.append(", but you only looted some of the items")
                    missedAnyItem -> message.append(" but none of the dropped items")
                }
            } else {
                message.append("You looted $totalLootedGold gold")
            }
        } else if (lootedAllItems) {
            when {
                totalLootedItems == 1 -> message.append("You looted 1 item")
                totalLootedGold != 0L || missedAnyGold -> message.append("You looted all of the dropped items")
                else -> message.append("You looted all items")
            }
            when {
                totalLootedGold != 0L -> message.append(", but you only looted $totalLootedGold of the dropped gold")
                missedAnyGold -> message.append(" but none of the dropped gold")
            }
        } else if (totalLootedGold != 0L) {
            message.append("You only looted $totalLootedGold of the dropped gold")
            when {
                totalLootedItems != 0 -> message.append(" and some of the dropped items")
                missedAnyItem -> message.append(" but none of the dropped items")
            }
        } else if (totalLootedItems != 0) {
            message.append("You looted some of the dropped items")
            if (missedAnyGold) {
                message.append(" but none of the dropped gold")
            }
        } else if (missedAnyGold) {
            message.append("You looted none of the dropped gold")
            if (missedAnyItem) {
                message.append(" and none of the items")
            }
        } else if (missedAnyItem) {
            message.append("You looted none of the dropped items")
        }
        message.append(".")
        return message.toString()
    }

    /**
     * Container-destination subset of `Game::internalMoveItem` (game.cpp:2436)
     * used by the quick-loot collection: the destination resolution and
     * `queryAdd`/`queryMaxCount` gates, the stackable merge and the source
     * removal/insertion.
     *
     * PARITY GAPS: the `browseFields` remap, `RETURNVALUE_NEEDEXCHANGE`,
     * `tradeItem`/`actor` notifications, clone attribute/decay copying and the
     * `count == 255` client fix.
     */
    private fun internalMoveItemToCylinder(
        fromCylinder: Cylinder,
        toCylinder: Cylinder,
        item: Item,
        count: Int,
    ): Pair<ReturnValue, Int> {
        val destination = toCylinder.queryDestination(INDEX_WHEREEVER, item, null, 0)
        val target = destination.cylinder ?: return ReturnValue.NOTPOSSIBLE to count
        val toItem = destination.destItem

        if (item === toItem) {
            return ReturnValue.NOERROR to count
        }

        var ret = target.queryAdd(destination.index, item, count, 0)
        if (ret != ReturnValue.NOERROR) {
            return ret to count
        }

        if (item.isStackable() && toItem != null && toItem.id == item.id) {
            val maxQueryCount = target.queryMaxCount(destination.index, item, count, 0, 0).maxQueryCount
            val m = minOf(count, maxQueryCount)
            ret = fromCylinder.queryRemove(item, m, 0)
            if (ret != ReturnValue.NOERROR) {
                return ret to count
            }

            fromCylinder.removeThing(item, m)
            val stackSize = item.type?.stackSize ?: DEFAULT_STACK_SIZE
            val n = maxOf(0, minOf(m, stackSize - toItem.count))
            toItem.count += n
            val newCount = m - n
            if (newCount > 0) {
                target.addThing(destination.index, Item(id = item.id, count = newCount, type = item.type))
            }
            return ReturnValue.NOERROR to n + newCount
        }

        ret = fromCylinder.queryRemove(item, count, 0)
        if (ret != ReturnValue.NOERROR) {
            return ret to count
        }

        fromCylinder.removeThing(item, count)
        target.addThing(destination.index, item)
        return ReturnValue.NOERROR to count
    }

    /**
     * `Game::internalAddItem(toCylinder, item, index, flags, test)`
     * (game.cpp:2693) reduced to the container destination, mirroring the
     * facade's tile `internalAddItem` gate order.
     */
    private fun containerAddItem(toCylinder: Cylinder, item: Item, index: Int): ReturnValue {
        val destination = toCylinder.queryDestination(index, item, null, 0)
        val target = destination.cylinder ?: return ReturnValue.NOTPOSSIBLE
        val toItem = destination.destItem

        val ret = target.queryAdd(destination.index, item, item.count, 0)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        if (item.isStackable() && toItem != null && toItem.id == item.id) {
            val stackSize = item.type?.stackSize ?: DEFAULT_STACK_SIZE
            val n = maxOf(0, minOf(item.count, stackSize - toItem.count))
            toItem.count += n
            val remainder = item.count - n
            if (remainder > 0) {
                target.addThing(destination.index, Item(id = item.id, count = remainder, type = item.type))
            }
        } else {
            target.addThing(destination.index, item)
        }

        return ReturnValue.NOERROR
    }

    /**
     * `Game::playerRequestCloseDepotSearch` (src/game/game.cpp:6933): the open
     * depot search is closed and the `Player::sendCloseDepotSearch` reply is
     * emitted through the player's sink.
     */
    fun playerRequestCloseDepotSearch(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        if (!player.isDepotSearchOpen()) {
            return
        }

        player.setDepotSearchIsOpen(0, 0)
        player.playerSendSink?.sendCloseDepotSearch()
    }

    // --- stash / depot-search seams (game.cpp:2297, 5665-5755, 6920-7000, 12231-12355) ---

    /** `g_configManager().getNumber(STASH_MANAGE_AMOUNT)` default 100000 (configmanager.cpp:143). */
    var stashManageAmount: Long = 100000

    /**
     * `Player::stowItem(item, count, allItems)` (player.cpp) seam. The stash
     * movement scan lives in `Player`; hosts inject the ported body.
     */
    var stowItem: (Player, Item, Int, Boolean) -> Unit = { _, _, _, _ -> }

    /**
     * `Player::addItemFromStash(itemId, itemCount)` (player.cpp:9364) seam.
     * The capacity/backpack placement body is not ported into the Kotlin
     * `Player` (PARITY GAP), so the default denies every withdrawal.
     */
    var addItemFromStash: (Player, Int, Long) -> ReturnValue = { _, _, _ ->
        ReturnValue.NOTPOSSIBLE
    }

    /** `Player::sendOpenStash(isNpc)` (player.cpp:8544) protocol seam. */
    var sendOpenStash: (Player, Boolean) -> Unit = { _, _ -> }

    /** `Player::sendForgingData()` protocol seam used by the batch item handlers (player.hpp). */
    var sendForgingData: (Player) -> Unit = { player -> player.sendForgingData() }

    // --- use-item / container handler seams (game.cpp:4500-5800) --------------

    /** `g_configManager().getBoolean(AIMBOT_HOTKEY_ENABLED)` (game.cpp:4582, 4745, 4882). */
    var aimbotHotkeyEnabled: Boolean = false

    /** `g_configManager().getNumber(EX_ACTIONS_DELAY_INTERVAL)` default 1000 (configmanager.cpp:255). */
    var exActionsDelayInterval: Int = 1000

    /**
     * `g_actions().canUse(player, pos)` / `canUse(player, pos, item)`
     * (actions.cpp) seam; the `item` argument is `null` for the plain
     * position overload. The default allows the use so the `Game` gates stay
     * testable without the Lua action registry.
     */
    var actionsCanUse: (Player, Position, Item?) -> ReturnValue = { _, _, _ -> ReturnValue.NOERROR }

    /** `g_actions().useItem(player, pos, index, item, isHotkey)` (actions.cpp) seam. */
    var actionsUseItem: (Player, Position, Int, Item, Boolean) -> Unit = { _, _, _, _, _ -> }

    /**
     * `g_actions().useItemEx(player, fromPos, toPos, toStackPos, item, isHotkey)`
     * (actions.cpp) seam; `creature` is `null` for the plain
     * `Game::playerUseItemEx` call and the target for
     * `Game::playerUseWithCreature`.
     */
    var actionsUseItemEx: (Player, Position, Position, Int, Item, Boolean, Creature?) -> Unit =
        { _, _, _, _, _, _, _ -> }

    /** `Item::isRotatable()` (src/items/item.cpp) seam; defaults to the `rotateTo` type flag. */
    var itemIsRotatable: (Item) -> Boolean = { item -> (item.type?.rotateTo ?: 0) != 0 }

    /** `Item::isInsideDepot(checkStoreInbox)` (src/items/item.cpp) seam. */
    var itemIsInsideDepot: (Item, Boolean) -> Boolean = { _, _ -> false }

    /**
     * `Player::getWriteItem(item, windowTextId, maxTextLength)` (player.cpp)
     * seam. The triple is `(writeItem, internalWindowTextId, maxTextLength)`;
     * the Kotlin `Player` does not carry the write window state (PARITY GAP).
     */
    var getWriteItem: (Player) -> Triple<Item?, Long, Int> = { _ -> Triple(null, 0L, 0) }

    /** `Player::setWriteItem(item)` (player.cpp) seam. */
    var setWriteItem: (Player, Item?) -> Unit = { _, _ -> }

    /**
     * `Player::getCreatureEvents(CREATURE_EVENT_TEXTEDIT)` execution loop
     * (player.cpp, game.cpp:5565): every `executeTextEdit` runs in the Lua
     * event layer (AGENTS.md 1.4), so the aggregate veto is injected. `false`
     * aborts the write exactly like a rejected event.
     */
    var executeTextEditEvents: (Player, Item, String) -> Boolean = { _, _, _ -> true }

    /** `Player::sendUseItemCooldown(delay)` (player.cpp) protocol seam. */
    var sendUseItemCooldown: (Player, Int) -> Unit = { _, _ -> }

    /**
     * `Game::internalMoveItem(fromCylinder, player, INDEX_WHEREEVER, item, count, &movedItem)`
     * pickup branch (game.cpp:4506) used by [queuePlayerAutoWalkAfterItemPickup].
     * The Kotlin `internalMoveItem` is tile-to-tile only and `Player` is not a
     * `Cylinder` (PARITY GAP), so the cylinder-to-player move is injected.
     */
    var pickupItem: (Player, Cylinder, Item, Int) -> ReturnValue = { _, _, _, _ -> ReturnValue.NOTPOSSIBLE }

    /**
     * `g_dispatcher().addWalkEvent(event, delay, playerId)` (game.cpp:4516) seam
     * returning whether the event was accepted. The tuple is
     * `(playerId, delay, event)`.
     */
    var addWalkEvent: (Long, Long, () -> Unit) -> Boolean = { _, _, _ -> false }

    /** `g_callbacks().checkCallback(playerOnRotateItem, player, item, pos)` veto seam (game.cpp:5149). */
    var callbackPlayerOnRotateItem: (Player, Item, Position) -> Boolean = { _, _, _ -> true }

    // --- browse-field seams (game.cpp:5599-5663) -------------------------------

    /** `g_events().eventPlayerOnBrowseField(player, pos)` veto seam (game.cpp:5640). */
    var eventPlayerOnBrowseField: (Player, Position) -> Boolean = { _, _ -> true }

    /** `g_callbacks().checkCallback(playerOnBrowseField, player, pos)` veto seam (game.cpp:5644). */
    var callbackPlayerOnBrowseField: (Player, Position) -> Boolean = { _, _ -> true }

    /**
     * `Container::createBrowseField(tile)` (container.cpp:47) seam. The item
     * layer owns the browse-field container build, so unwired hosts resolve to
     * `null` and [playerBrowseField] returns early (PARITY GAP).
     */
    var containerCreateBrowseField: (Tile) -> Container? = { null }

    /** `Player::onCloseContainer(container)` (player.cpp) protocol seam (game.cpp:5654). */
    var playerOnCloseContainer: (Player, Container) -> Unit = { _, _ -> }

    /** `Player::retrieveAllItemsFromDepotSearch(itemId, tier, isDepot)` (player.cpp:10813) seam. */
    var retrieveAllItemsFromDepotSearch: (Player, Int, Int, Boolean) -> Unit = { _, _, _, _ -> }

    /** `Player::openContainerFromDepotSearch(pos)` (player.cpp) seam. */
    var openContainerFromDepotSearch: (Player, Position) -> Unit = { _, _ -> }

    /**
     * `Game::playerRequestDepotItems` (src/game/game.cpp:6920): the depot-search
     * availability gate, the 500ms UI-exhaustion gate, then
     * `Player::requestDepotItems` plus `Player::updateUIExhausted`.
     */
    fun playerRequestDepotItems(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return

        if (!player.isDepotSearchAvailable()) {
            return
        }

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        player.requestDepotItems()
        updateUIExhausted(player)
    }

    /**
     * `Game::playerRequestDepotSearchItem` (src/game/game.cpp:6945): an open
     * depot search and the 500ms UI-exhaustion gate before the player records
     * the requested `(itemId, tier)` pair.
     */
    fun playerRequestDepotSearchItem(playerId: Long, itemId: Int, tier: Int) {
        val player = getPlayerByID(playerId) ?: return

        if (!player.isDepotSearchOpen()) {
            return
        }

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        player.requestDepotSearchItem(itemId, tier)
        updateUIExhausted(player)
    }

    /**
     * `Game::playerRequestDepotSearchRetrieve` (src/game/game.cpp:6960): the
     * search must be open on the requested item; the `type` byte selects the
     * depot branch (`type == 1`) of `Player::retrieveAllItemsFromDepotSearch`.
     */
    fun playerRequestDepotSearchRetrieve(playerId: Long, itemId: Int, tier: Int, type: Int) {
        val player = getPlayerByID(playerId) ?: return

        if (!player.isDepotSearchOpenOnItem(itemId)) {
            return
        }

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        retrieveAllItemsFromDepotSearch(player, itemId, tier, type == 1)
        updateUIExhausted(player)
    }

    /**
     * `Game::playerRequestOpenContainerFromDepotSearch` (src/game/game.cpp:6975):
     * an open depot search and the 500ms UI-exhaustion gate before
     * `Player::openContainerFromDepotSearch`.
     */
    fun playerRequestOpenContainerFromDepotSearch(playerId: Long, pos: Position) {
        val player = getPlayerByID(playerId) ?: return

        if (!player.isDepotSearchOpen()) {
            return
        }

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        openContainerFromDepotSearch(player, pos)
        updateUIExhausted(player)
    }

    /**
     * `Game::isTryingToStow` (src/game/game.cpp:2297): a locker container at
     * the `ITEM_STASH_INDEX` floor selects the stash destination.
     */
    fun isTryingToStow(toPos: Position, toCylinder: Cylinder): Boolean =
        toCylinder.getContainer() != null && toCylinder.getItem()?.id == DepotLocker.ITEM_LOCKER &&
            toPos.z == ITEM_STASH_INDEX

    /**
     * `Game::playerStowItem` (src/game/game.cpp:5665): the premium, item
     * identity/ownership and range gates, then `Player::stowItem` plus the
     * open depot-search refresh.
     */
    fun playerStowItem(playerId: Long, pos: Position, itemId: Int, stackpos: Int, count: Int, allItems: Boolean) {
        val player = getPlayerByID(playerId) ?: return

        if (!player.isPremium()) {
            sendCancelMessage(player, ReturnValue.YOUNEEDPREMIUMACCOUNT)
            return
        }

        val item = internalGetThing(player, pos, stackpos, itemId, StackPosType_t.STACKPOS_TOPDOWN_ITEM) ?: return

        if (item.id != itemId || item.getItemCount() < count || item.isStoreItem()) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (item.hasOwner() && !item.isOwner(player)) {
            sendCancelMessage(player, ReturnValue.ITEMISNOTYOURS)
            return
        }

        if (pos.x != 0xFFFF && !areInRange(pos, player.position, 1, 1, 0)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        stowItem(player, item, count, allItems)

        if (player.isDepotSearchOpenOnItem(itemId)) {
            player.requestDepotSearchItem(itemId, 0)
        }
    }

    /**
     * `Game::playerStashWithdraw` (src/game/game.cpp:5706): the pickup flag,
     * item/count, 100 free-capacity and `STASH_MANAGE_AMOUNT` gates, then
     * `Player::addItemFromStash` with the partial-withdrawal stash correction,
     * the depot-search refresh and the `sendOpenStash` reply.
     */
    fun playerStashWithdraw(playerId: Long, itemId: Int, count: Long, stackpos: Int) {
        val player = getPlayerByID(playerId) ?: return

        if (player.hasFlag(PlayerFlags_t.CannotPickupItem)) {
            return
        }

        val itemType = itemTypes?.get(itemId)
        if (itemType == null || itemType.id == 0 || count == 0L) {
            return
        }

        if (player.getFreeCapacity() < 100) {
            sendCancelMessage(player, ReturnValue.NOTENOUGHCAPACITY)
            return
        }

        var withdrawCount = count
        if (withdrawCount > stashManageAmount) {
            sendTextMessage(
                player,
                MessageClass.MESSAGE_EVENT_ADVANCE,
                "You can only withdraw up to $stashManageAmount items at a time from the stash.",
            )
            withdrawCount = stashManageAmount
        }

        val previousStashCount = getStashItemCount(player, itemId)
        val ret = addItemFromStash(player, itemId, withdrawCount)
        if (ret != ReturnValue.NOERROR) {
            val currentStashCount = getStashItemCount(player, itemId)
            if (currentStashCount < previousStashCount) {
                val diff = previousStashCount - currentStashCount
                player.stash.addItemOnStash(itemId, diff)
            }
            sendCancelMessage(player, ret)
        }

        if (player.isDepotSearchOpenOnItem(itemId)) {
            player.requestDepotSearchItem(itemId, 0)
        }

        sendOpenStash(player, true)
    }

    /**
     * `Game::tryRetrieveStashItems` (src/game/game.cpp:12342): collect the item
     * into its managed container by object category.
     */
    fun tryRetrieveStashItems(player: Player, item: Item): Boolean {
        val category = getObjectCategory(item)
        return internalCollectManagedItems(player, item, category, false) == ReturnValue.NOERROR
    }

    /**
     * `Game::addItemStoreInbox` (src/game/game.cpp:12231): stamp a decoration
     * kit with the un-wrap id and description, then insert it into the store
     * inbox, falling back to the raw container insertion when the query fails.
     *
     * PARITY GAP: the Kotlin [Item] has no custom-attribute store, so
     * `unWrapId` is held by the facade's custom attribute map.
     */
    fun addItemStoreInbox(player: Player, itemId: Int): Boolean {
        val decoKitType = itemTypes?.get(Items.ITEM_DECORATION_KIT)
        if (decoKitType == null || decoKitType.id == 0) {
            return false
        }

        val decoKit = Item(id = Items.ITEM_DECORATION_KIT, count = 1, type = decoKitType)
        val itemType = itemTypes.getItemType(itemId)
        decoKit.setAttribute(
            ItemAttribute.DESCRIPTION,
            "You bought this item in the Store.\nUnwrap it in your own house to create a <${itemType.name}>.",
        )
        setItemCustomAttribute(decoKit, "unWrapId", itemId.toLong())

        val inboxItem = player.getInventoryItem(Slots_t.CONST_SLOT_STORE_INBOX) ?: return false
        val inboxContainer = inboxItem.getContainer() ?: return false

        if (containerAddItem(inboxContainer, decoKit, INDEX_WHEREEVER) != ReturnValue.NOERROR) {
            inboxContainer.internalAddThing(decoKit)
        }

        return true
    }

    // --- item batch creation (game.cpp:2848-3050) ------------------------------

    /**
     * `Game::addItemBatch` (src/game/game.cpp:2848): the drop-on-map bulk add,
     * or the auto-container/managed-container insertion path, returning
     * `(returnValue, totalAdded, containersCreated)`.
     *
     * PARITY GAPS: the C++ `internalAddItem(Cylinder, ..., remainderCount)`
     * out-parameter is not modeled, so a partial stack insertion reports no
     * remainder item; the `flags` argument is dropped by the reduced
     * [containerAddItem] destination path.
     */
    fun addItemBatch(
        toCylinder: Cylinder,
        items: List<Item>,
        flags: Int = 0,
        dropOnMap: Boolean = true,
        autoContainerId: Int = 0,
    ): Triple<ReturnValue, Long, Int> {
        var totalAdded = 0L
        var containersCreated = 0
        var ret = ReturnValue.NOTPOSSIBLE

        if (dropOnMap) {
            for (item in items) {
                val returnError =
                    internalAddItem(toCylinder.getTile(), item, INDEX_WHEREEVER, CylinderFlags.FLAG_NOLIMIT)
                if (returnError == ReturnValue.NOERROR) {
                    if (item.getContainer() != null) {
                        containersCreated++
                    }
                    totalAdded += item.getItemCount()
                }

                ret = returnError
            }

            return Triple(ret, totalAdded, containersCreated)
        }

        val player = toCylinder.getPlayer()
        var dropping = false

        fun setupDestination(): Cylinder {
            if (autoContainerId == 0) {
                return toCylinder
            }

            val autoType = itemTypes?.get(autoContainerId)
            if (autoType == null || autoType.id == 0) {
                logger.error { "[Game::addItemBatch] Failed to create auto container" }
                return toCylinder
            }

            val autoContainer = Item.create(autoContainerId, 1, itemTypes)
            if (containerAddItem(toCylinder, autoContainer, INDEX_WHEREEVER) != ReturnValue.NOERROR) {
                if (internalAddItem(
                        toCylinder.getTile(),
                        autoContainer,
                        INDEX_WHEREEVER,
                        CylinderFlags.FLAG_NOLIMIT,
                    ) != ReturnValue.NOERROR
                ) {
                    logger.error { "[Game::addItemBatch] Failed to add auto container" }
                    return toCylinder
                }
            }

            val container = autoContainer.getContainer()
            if (container == null) {
                logger.error { "[Game::addItemBatch] Failed to get auto container" }
                return toCylinder
            }

            containersCreated++
            return container
        }

        var destination = setupDestination()

        for (item in items) {
            val container = destination.getContainer()
            if (container != null && container.getFreeSlots() == 0) {
                destination = setupDestination()
            }

            if (!dropping) {
                var addedToAutoContainer = false
                if (autoContainerId != 0) {
                    ret = containerAddItem(destination, item, INDEX_WHEREEVER)
                    addedToAutoContainer = ret == ReturnValue.NOERROR
                }

                if (!addedToAutoContainer) {
                    ret = internalCollectManagedItems(player, item, getObjectCategory(item), false)
                    if (ret != ReturnValue.NOERROR) {
                        ret = containerAddItem(destination, item, INDEX_WHEREEVER)
                    }
                }
            }

            if (dropping || (ret != ReturnValue.NOERROR && dropOnMap)) {
                dropping = true
                ret = internalAddItem(destination.getTile(), item, INDEX_WHEREEVER, CylinderFlags.FLAG_NOLIMIT)
            }

            if (player != null && ret == ReturnValue.NOERROR) {
                sendForgingData(player)
            }
            if (ret != ReturnValue.NOERROR) {
                break
            }
            totalAdded += item.getItemCount()
        }

        return Triple(ret, totalAdded, containersCreated)
    }

    /**
     * `Game::createItemBatch` (src/game/game.cpp:2947): expands the
     * `(itemId, count, subType)` triples into stack-sized items (wrappable
     * types become stamped `ITEM_DECORATION_KIT`s) and hands them to
     * [addItemBatch]. Unknown ids and zero counts are skipped.
     */
    fun createItemBatch(
        toCylinder: Cylinder,
        itemCounts: List<Triple<Int, Long, Int>>,
        flags: Int = 0,
        dropOnMap: Boolean = true,
        autoContainerId: Int = 0,
    ): Triple<ReturnValue, Long, Int> {
        val items = mutableListOf<Item>()
        for ((itemId, count, subType) in itemCounts) {
            val itemType = itemTypes?.get(itemId)
            if (itemType == null || itemType.id <= 0) {
                continue
            }
            if (count == 0L) {
                continue
            }

            var countPerItem = if (itemType.isStackable) itemType.stackSize else 1
            var i = 0L
            while (i < count) {
                val item: Item
                if (itemType.isWrappable) {
                    countPerItem = 1
                    item = Item.create(Items.ITEM_DECORATION_KIT, subType, itemTypes)
                    item.setAttribute(
                        ItemAttribute.DESCRIPTION,
                        "Unwrap this item in your own house to create a <${itemType.name}>.",
                    )
                    setItemCustomAttribute(item, "unWrapId", itemId.toLong())
                } else {
                    item = Item.create(
                        itemId,
                        if (itemType.isStackable) minOf(countPerItem.toLong(), count - i).toInt() else subType,
                        itemTypes,
                    )
                }
                items.add(item)
                i += countPerItem
            }
        }

        return addItemBatch(toCylinder, items, flags, dropOnMap, autoContainerId)
    }

    /**
     * `Game::createItem` (src/game/game.cpp:2977): one-item convenience over
     * [createItemBatch].
     */
    fun createItem(
        toCylinder: Cylinder,
        itemId: Int,
        count: Long,
        subType: Int,
        flags: Int = 0,
        dropOnMap: Boolean = true,
        autoContainerId: Int = 0,
    ): Triple<ReturnValue, Long, Int> =
        createItemBatch(toCylinder, listOf(Triple(itemId, count, subType)), flags, dropOnMap, autoContainerId)

    /**
     * `Game::internalPlayerAddItem` (src/game/game.cpp:2981): the managed
     * collection-first insertion for `CONST_SLOT_WHEREEVER`, the map drop
     * fallback and the forging-data refresh.
     *
     * PARITY GAPS: the `internalAddItem` `remainderCount` out-parameter and the
     * `Player::sendLootStats` remainder notification are not modeled.
     */
    fun internalPlayerAddItem(
        player: Player,
        item: Item,
        dropOnMap: Boolean = true,
        slot: Slots_t = Slots_t.CONST_SLOT_WHEREEVER,
    ): ReturnValue {
        var ret: ReturnValue
        if (slot == Slots_t.CONST_SLOT_WHEREEVER) {
            ret = internalCollectManagedItems(player, item, getObjectCategory(item), false)
            if (ret != ReturnValue.NOERROR) {
                ret = internalAddItem(player, item)
            }
        } else {
            ret = internalAddItem(player, item)
        }

        if (ret != ReturnValue.NOERROR && dropOnMap) {
            ret = internalAddItem(player.getTile(), item, INDEX_WHEREEVER, CylinderFlags.FLAG_NOLIMIT)
        }

        if (ret == ReturnValue.NOERROR) {
            sendForgingData(player)
        }

        return ret
    }

    /**
     * `Game::findItemOfType` (src/game/game.cpp:3013): the shallow cylinder
     * scan, then the breadth-first container descent when `depthSearch` is set.
     */
    fun findItemOfType(cylinder: Cylinder, itemId: Int, depthSearch: Boolean = true, subType: Int = -1): Item? {
        val containers = mutableListOf<Container>()

        var i = cylinder.getFirstIndex()
        val last = cylinder.getLastIndex()
        while (i < last) {
            val thing = cylinder.getThing(i)
            i++
            if (thing == null) {
                continue
            }

            val item = thing.getItem() ?: continue
            if (item.id == itemId && (subType == -1 || subType == item.getSubType())) {
                return item
            }

            if (depthSearch) {
                item.getContainer()?.let { containers.add(it) }
            }
        }

        var index = 0
        while (index < containers.size) {
            val container = containers[index++]
            for (item in container.getItemList()) {
                if (item.id == itemId && (subType == -1 || subType == item.getSubType())) {
                    return item
                }

                item.getContainer()?.let { containers.add(it) }
            }
        }

        return null
    }

    // --- use-item / container handlers (game.cpp:4500-5800) --------------------

    /**
     * `Game::playerUpdateContainer` (src/game/game.cpp:5090): re-send the open
     * container with its parent state and current pagination index.
     */
    fun playerUpdateContainer(playerId: Long, cid: Int) {
        val player = getPlayerByID(playerId) ?: return

        val container = player.getContainerByID(cid) ?: return

        player.sendContainer(cid, container, container.hasParent(), player.getContainerIndex(cid))
    }

    /**
     * `Game::playerRotateItem` (src/game/game.cpp:5104): the item identity,
     * rotatability, unique-id, ownership and house gates, the walk-into-range
     * retry, the `playerOnRotateItem` callback veto and the `rotateTo`
     * transform.
     */
    fun playerRotateItem(playerId: Long, pos: Position, stackPos: Int, itemId: Int) {
        val player = getPlayerByID(playerId) ?: return

        val item = internalGetThing(player, pos, stackPos, itemId, StackPosType_t.STACKPOS_TOPDOWN_ITEM) ?: return

        if (item.id != itemId || !itemIsRotatable(item) || item.hasAttribute(ItemAttribute.UNIQUEID)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (item.hasOwner() && !item.isOwner(player)) {
            sendCancelMessage(player, ReturnValue.ITEMISNOTYOURS)
            return
        }

        if (onlyInvitedCanMoveHouseItems && !playerCanUseItemOnHouseTile(player, item)) {
            sendCancelMessage(player, ReturnValue.CANNOTUSETHISOBJECT)
            return
        }

        if (pos.x != 0xFFFF && !areInRange(pos, player.position, 1, 1, 0)) {
            val listDir = getPathTo(player, pos, 0, 1, true, true)
            if (listDir != null) {
                if (!queuePlayerAutoWalk(player, listDir)) {
                    return
                }
                val task = player.createPlayerTask(
                    400,
                    { playerRotateItem(playerId, pos, stackPos, itemId) },
                    "Game::playerRotateItem",
                )
                player.setNextWalkActionTask(task)
            } else {
                sendCancelMessage(player, ReturnValue.THEREISNOWAY)
            }
            return
        }

        if (!callbackPlayerOnRotateItem(player, item, pos)) {
            return
        }

        val newId = itemTypes?.get(item.id)?.rotateTo ?: 0
        if (newId != 0) {
            transformItem(item, newId)
        }
    }

    /**
     * `Game::playerWriteItem` (src/game/game.cpp:5535): the window text
     * id/length gate, ownership and range checks, the text-edit event veto and
     * the text/writer/date stamp or clear, followed by the `writeOnceItemId`
     * transform.
     *
     * PARITY GAP: `Player` is not a `Cylinder`, so an item carried in the
     * inventory cannot resolve a player top parent; the `topParent` ownership
     * mismatch branch stays inert until the inventory-parent model exists.
     */
    fun playerWriteItem(playerId: Long, windowTextId: Long, text: String) {
        val player = getPlayerByID(playerId) ?: return

        val (writeItem, internalWindowTextId, maxTextLength) = getWriteItem(player)
        if (text.length > maxTextLength || windowTextId != internalWindowTextId) {
            return
        }

        if (writeItem == null || writeItem.isRemoved()) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (writeItem.hasOwner() && !writeItem.isOwner(player)) {
            sendCancelMessage(player, ReturnValue.ITEMISNOTYOURS)
            return
        }

        val topParent = writeItem.getTopParent()
        val owner = topParent?.getPlayer()
        if (owner != null && owner !== player) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (!areInRange(writeItem.position, player.position, 1, 1, 0)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (!executeTextEditEvents(player, writeItem, text)) {
            setWriteItem(player, null)
            return
        }

        if (text.isNotEmpty()) {
            if (writeItem.getAttribute(ItemAttribute.TEXT) != text) {
                writeItem.setAttribute(ItemAttribute.TEXT, text)
                writeItem.setAttribute(ItemAttribute.WRITER, player.name)
                writeItem.setAttribute(ItemAttribute.DATE, getTimeNow())
            }
        } else {
            writeItem.removeAttribute(ItemAttribute.TEXT)
            writeItem.removeAttribute(ItemAttribute.WRITER)
            writeItem.removeAttribute(ItemAttribute.DATE)
        }

        val newId = itemTypes?.get(writeItem.id)?.writeOnceItemId ?: 0
        if (newId != 0) {
            transformItem(writeItem, newId)
        }

        setWriteItem(player, null)
    }

    /**
     * `Game::playerSeekInContainer` (src/game/game.cpp:5755): pagination-only
     * seek; the store inbox also stamps the `STORE_INBOX_CATEGORY` attribute
     * with the requested category name.
     */
    fun playerSeekInContainer(playerId: Long, containerId: Int, index: Int, containerCategory: Int) {
        val player = getPlayerByID(playerId) ?: return

        val container = player.getContainerByID(containerId) ?: return
        if (!container.hasPagination()) {
            return
        }

        if (container.isStoreInbox()) {
            val enumName = ContainerCategory.entries.getOrNull(containerCategory)?.name ?: ""
            container.setAttribute(ItemAttribute.STORE_INBOX_CATEGORY, enumName)
            logger.debug { "Setting new container with store inbox category name $enumName" }
        }

        if ((index % container.capacity) != 0 || index >= container.size()) {
            return
        }

        player.setContainerIndex(containerId, index)
        player.sendContainer(containerId, container, container.hasParent(), index)
    }

    /**
     * `Game::playerUseItem` (src/game/game.cpp:4739): the hotkey, item identity,
     * multi-use, house, exhaustion and action-delay gates, the walk-into-range
     * retry, the depot-search refresh and the `g_actions().useItem` dispatch.
     */
    fun playerUseItem(playerId: Long, pos: Position, stackPos: Int, index: Int, itemId: Int) {
        val player = getPlayerByID(playerId) ?: return

        val isHotkey = pos.x == 0xFFFF && pos.y == 0 && pos.z == 0
        if (isHotkey && !aimbotHotkeyEnabled) {
            return
        }

        val item = internalGetThing(player, pos, stackPos, itemId, StackPosType_t.STACKPOS_FIND_THING)
        if (item == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (item.type?.isMultiUse() == true || item.id != itemId) {
            sendCancelMessage(player, ReturnValue.CANNOTUSETHISOBJECT)
            return
        }

        val canUseHouseItem = !onlyInvitedCanMoveHouseItems || playerCanUseItemOnHouseTile(player, item)
        if (!canUseHouseItem && item.hasOwner() && !item.isOwner(player)) {
            sendCancelMessage(player, ReturnValue.ITEMISNOTYOURS)
            return
        } else if (!canUseHouseItem) {
            sendCancelMessage(player, ReturnValue.CANNOTUSETHISOBJECT)
            return
        }

        val canTriggerExhaustion = itemTypes?.get(item.id)?.triggerExhaustion() ?: false
        if (canTriggerExhaustion && player.walkExhausted()) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        var ret = actionsCanUse(player, pos, null)
        if (ret != ReturnValue.NOERROR) {
            if (ret == ReturnValue.TOOFARAWAY) {
                val listDir = getPathTo(player, pos, 0, 1, true, true)
                if (listDir != null) {
                    if (!queuePlayerAutoWalk(player, listDir)) {
                        return
                    }
                    val task = player.createPlayerTask(
                        400,
                        { playerUseItem(playerId, pos, stackPos, index, itemId) },
                        "Game::playerUseItem",
                    )
                    if (canTriggerExhaustion) {
                        player.setNextPotionActionTask(task)
                    } else {
                        player.setNextWalkActionTask(task)
                    }
                    return
                }

                ret = ReturnValue.THEREISNOWAY
            }

            sendCancelMessage(player, ret)
            return
        }

        var canDoAction = player.canDoAction()
        if (canTriggerExhaustion) {
            canDoAction = player.canDoPotionAction()
        }

        if (!canDoAction) {
            val delay = if (canTriggerExhaustion) player.getNextPotionActionTime() else player.getNextActionTime()
            val task = player.createPlayerTask(
                delay,
                { playerUseItem(playerId, pos, stackPos, index, itemId) },
                "Game::playerUseItem",
            )
            if (canTriggerExhaustion) {
                player.setNextPotionActionTask(task)
            } else {
                player.setNextActionTask(task)
            }
            return
        }

        player.resetLoginProtection()
        player.resetIdleTime()
        player.setNextActionTask(null)

        var refreshDepotSearch = false
        if (player.isDepotSearchOpenOnItem(itemId) && itemIsInsideDepot(item, true)) {
            refreshDepotSearch = true
        }

        actionsUseItem(player, pos, index, item, isHotkey)

        if (refreshDepotSearch) {
            player.requestDepotSearchItem(itemId, stackPos)
        }
    }

    /**
     * `Game::playerUseItemEx` (src/game/game.cpp:4576): the hotkey, multi-use
     * source item, house, exhaustion and action-delay gates, the
     * pickup-then-walk retry, the depot-search refresh and the
     * `g_actions().useItemEx` dispatch.
     */
    fun playerUseItemEx(
        playerId: Long,
        fromPos: Position,
        fromStackPos: Int,
        fromItemId: Int,
        toPos: Position,
        toStackPos: Int,
        toItemId: Int,
    ) {
        val player = getPlayerByID(playerId) ?: return

        val isHotkey = fromPos.x == 0xFFFF && fromPos.y == 0 && fromPos.z == 0
        if (isHotkey && !aimbotHotkeyEnabled) {
            return
        }

        val item = internalGetThing(player, fromPos, fromStackPos, fromItemId, StackPosType_t.STACKPOS_FIND_THING)
        if (item == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (item.type?.isMultiUse() != true || item.id != fromItemId) {
            sendCancelMessage(player, ReturnValue.CANNOTUSETHISOBJECT)
            return
        }

        val canUseHouseItem = !onlyInvitedCanMoveHouseItems || playerCanUseItemOnHouseTile(player, item)
        if (!canUseHouseItem && item.hasOwner() && !item.isOwner(player)) {
            sendCancelMessage(player, ReturnValue.ITEMISNOTYOURS)
            return
        } else if (!canUseHouseItem) {
            sendCancelMessage(player, ReturnValue.CANNOTUSETHISOBJECT)
            return
        }

        var walkToPos = fromPos
        var ret = actionsCanUse(player, fromPos, null)
        if (ret == ReturnValue.NOERROR) {
            ret = actionsCanUse(player, toPos, item)
            if (ret == ReturnValue.TOOFARAWAY) {
                walkToPos = toPos
            }
        }

        val canTriggerExhaustion = itemTypes?.get(item.id)?.triggerExhaustion() ?: false
        if (canTriggerExhaustion && player.walkExhausted()) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        if (ret != ReturnValue.NOERROR) {
            if (ret == ReturnValue.TOOFARAWAY) {
                val requiresPickup = fromPos.x != 0xFFFF &&
                    toPos.x != 0xFFFF &&
                    areInRange(fromPos, player.position, 1, 1, 0) &&
                    !areInRange(fromPos, toPos, 1, 1, 0)
                if (requiresPickup) {
                    val parent = item.getParent()
                    if (parent == null) {
                        sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                        return
                    }
                    queuePlayerAutoWalkAfterItemPickup(
                        playerId,
                        walkToPos,
                        1,
                        parent,
                        item,
                        item.getItemCount(),
                    ) { walkPlayer, itemPos, itemStackPos ->
                        val task = walkPlayer.createPlayerTask(
                            400,
                            { playerUseItemEx(playerId, itemPos, itemStackPos, fromItemId, toPos, toStackPos, toItemId) },
                            "Game::playerUseItemEx",
                        )
                        if (canTriggerExhaustion) {
                            walkPlayer.setNextPotionActionTask(task)
                        } else {
                            walkPlayer.setNextWalkActionTask(task)
                        }
                    }
                    return
                }

                val listDir = getPathTo(player, walkToPos, 0, 1, true, true)
                if (listDir != null) {
                    if (!queuePlayerAutoWalk(player, listDir)) {
                        return
                    }
                    val task = player.createPlayerTask(
                        400,
                        { playerUseItemEx(playerId, fromPos, fromStackPos, fromItemId, toPos, toStackPos, toItemId) },
                        "Game::playerUseItemEx",
                    )
                    if (canTriggerExhaustion) {
                        player.setNextPotionActionTask(task)
                    } else {
                        player.setNextWalkActionTask(task)
                    }
                } else {
                    sendCancelMessage(player, ReturnValue.THEREISNOWAY)
                }
                return
            }

            sendCancelMessage(player, ret)
            return
        }

        var canDoAction = player.canDoAction()
        if (canTriggerExhaustion) {
            canDoAction = player.canDoPotionAction()
        }

        if (!canDoAction) {
            val delay = if (canTriggerExhaustion) player.getNextPotionActionTime() else player.getNextActionTime()
            val task = player.createPlayerTask(
                delay,
                { playerUseItemEx(playerId, fromPos, fromStackPos, fromItemId, toPos, toStackPos, toItemId) },
                "Game::playerUseItemEx",
            )
            if (canTriggerExhaustion) {
                player.setNextPotionActionTask(task)
            } else {
                player.setNextActionTask(task)
            }
            return
        }

        player.resetLoginProtection()
        player.resetIdleTime()
        if (canTriggerExhaustion) {
            player.setNextPotionActionTask(null)
        } else {
            player.setNextActionTask(null)
        }

        var mustReloadDepotSearch = false
        if (player.isDepotSearchOpenOnItem(fromItemId)) {
            if (itemIsInsideDepot(item, true)) {
                mustReloadDepotSearch = true
            } else {
                val targetItem =
                    internalGetThing(player, toPos, toStackPos, toItemId, StackPosType_t.STACKPOS_FIND_THING)
                if (targetItem != null && itemIsInsideDepot(targetItem, true)) {
                    mustReloadDepotSearch = true
                }
            }
        }

        actionsUseItemEx(player, fromPos, toPos, toStackPos, item, isHotkey, null)

        if (mustReloadDepotSearch) {
            player.requestDepotSearchItem(fromItemId, fromStackPos)
        }
    }

    /**
     * `Game::playerUseWithCreature` (src/game/game.cpp:4853): the 7x5 range,
     * hotkey/multi-use/house/exhaustion gates, the familiar rune/potion
     * cooldown refusal and the creature-targeted `g_actions().useItemEx`.
     */
    fun playerUseWithCreature(playerId: Long, fromPos: Position, fromStackPos: Int, creatureId: Long, itemId: Int) {
        val player = getPlayerByID(playerId) ?: return

        val creature = getCreatureByID(creatureId) ?: return
        if (!areInRange(creature.position, player.position, 7, 5, 0)) {
            return
        }

        val isHotkey = fromPos.x == 0xFFFF && fromPos.y == 0 && fromPos.z == 0
        if (!aimbotHotkeyEnabled && isHotkey) {
            sendCancelMessage(player, ReturnValue.DIRECTPLAYERSHOOT)
            return
        }

        val item = internalGetThing(player, fromPos, fromStackPos, itemId, StackPosType_t.STACKPOS_FIND_THING)
        if (item == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (item.type?.isMultiUse() != true || item.id != itemId) {
            sendCancelMessage(player, ReturnValue.CANNOTUSETHISOBJECT)
            return
        }

        if (item.hasOwner() && !item.isOwner(player)) {
            sendCancelMessage(player, ReturnValue.ITEMISNOTYOURS)
            return
        }

        val canUseHouseItem = !onlyInvitedCanMoveHouseItems || playerCanUseItemOnHouseTile(player, item)
        if (!canUseHouseItem && item.hasOwner() && !item.isOwner(player)) {
            sendCancelMessage(player, ReturnValue.ITEMISNOTYOURS)
            return
        } else if (!canUseHouseItem) {
            sendCancelMessage(player, ReturnValue.ITEMCANNOTBEMOVEDTHERE)
            return
        }

        val itemType = itemTypes?.get(item.id)
        val canTriggerExhaustion = itemType?.triggerExhaustion() ?: false
        if (canTriggerExhaustion && player.walkExhausted()) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        val monster = creature as? Monster
        if (monster != null && player.isFamiliarProvider(monster) && creature.getMaster() === player &&
            (itemType?.isRune() == true || itemType?.type == ItemTypes_t.ITEM_TYPE_POTION)
        ) {
            player.setNextPotionAction(otsysTimeMs() + exActionsDelayInterval)

            if (itemType.isMultiUse()) {
                sendUseItemCooldown(player, exActionsDelayInterval)
            }

            sendCancelMessage(player, ReturnValue.CANNOTUSETHISOBJECT)
            return
        }

        val toPos = creature.position
        var walkToPos = fromPos
        var ret = actionsCanUse(player, fromPos, null)
        if (ret == ReturnValue.NOERROR) {
            ret = actionsCanUse(player, toPos, item)
            if (ret == ReturnValue.TOOFARAWAY) {
                walkToPos = toPos
            }
        }

        if (ret != ReturnValue.NOERROR) {
            if (ret == ReturnValue.TOOFARAWAY) {
                val requiresPickup = fromPos.x != 0xFFFF &&
                    areInRange(fromPos, player.position, 1, 1, 0) &&
                    !areInRange(fromPos, toPos, 1, 1, 0)
                if (requiresPickup) {
                    val parent = item.getParent()
                    if (parent == null) {
                        sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                        return
                    }
                    queuePlayerAutoWalkAfterItemPickup(
                        playerId,
                        walkToPos,
                        1,
                        parent,
                        item,
                        item.getItemCount(),
                    ) { walkPlayer, itemPos, itemStackPos ->
                        val task = walkPlayer.createPlayerTask(
                            400,
                            { playerUseWithCreature(playerId, itemPos, itemStackPos, creatureId, itemId) },
                            "Game::playerUseWithCreature",
                        )
                        if (canTriggerExhaustion) {
                            walkPlayer.setNextPotionActionTask(task)
                        } else {
                            walkPlayer.setNextWalkActionTask(task)
                        }
                    }
                    return
                }

                val listDir = getPathTo(player, walkToPos, 0, 1, true, true)
                if (listDir != null) {
                    if (!queuePlayerAutoWalk(player, listDir)) {
                        return
                    }
                    val task = player.createPlayerTask(
                        400,
                        { playerUseWithCreature(playerId, fromPos, fromStackPos, creatureId, itemId) },
                        "Game::playerUseWithCreature",
                    )
                    if (canTriggerExhaustion) {
                        player.setNextPotionActionTask(task)
                    } else {
                        player.setNextWalkActionTask(task)
                    }
                } else {
                    sendCancelMessage(player, ReturnValue.THEREISNOWAY)
                }
                return
            }

            sendCancelMessage(player, ret)
            return
        }

        var canDoAction = player.canDoAction()
        if (canTriggerExhaustion) {
            canDoAction = player.canDoPotionAction()
        }

        if (!canDoAction) {
            val delay = if (canTriggerExhaustion) player.getNextPotionActionTime() else player.getNextActionTime()
            val task = player.createPlayerTask(
                delay,
                { playerUseWithCreature(playerId, fromPos, fromStackPos, creatureId, itemId) },
                "Game::playerUseWithCreature",
            )
            if (canTriggerExhaustion) {
                player.setNextPotionActionTask(task)
            } else {
                player.setNextActionTask(task)
            }
            return
        }

        player.resetLoginProtection()
        player.resetIdleTime()
        if (canTriggerExhaustion) {
            player.setNextPotionActionTask(null)
        } else {
            player.setNextActionTask(null)
        }

        actionsUseItemEx(
            player,
            fromPos,
            creature.position,
            creature.getParent()?.getThingIndex(creature) ?: 0,
            item,
            isHotkey,
            creature,
        )
    }

    /**
     * `Game::queuePlayerAutoWalkAfterItemPickup` (src/game/game.cpp:4500): the
     * accepted walk event moves the item to the player, resolves its new
     * inventory position, re-checks the path and continues the walk; a rejected
     * event clears the queued walk tasks and sends the cancel-walk reply.
     */
    fun queuePlayerAutoWalkAfterItemPickup(
        playerId: Long,
        walkToPos: Position,
        maxTargetDistance: Int,
        fromCylinder: Cylinder,
        item: Item?,
        count: Int,
        afterPickup: (Player, Position, Int) -> Unit,
    ) {
        val accepted = addWalkEvent(playerId, 0L) {
            val player = getPlayerByID(playerId) ?: return@addWalkEvent
            val movedItem = item ?: return@addWalkEvent

            val ret = pickupItem(player, fromCylinder, movedItem, count)
            if (ret != ReturnValue.NOERROR) {
                sendCancelMessage(player, ret)
                return@addWalkEvent
            }

            val (itemPos, itemStackPos) = internalGetPosition(movedItem)

            val listDir = getPathTo(player, walkToPos, 0, maxTargetDistance, true, true)
            if (listDir == null) {
                sendCancelMessage(player, ReturnValue.THEREISNOWAY)
                return@addWalkEvent
            }

            afterPickup(player, itemPos, itemStackPos)
            playerAutoWalk(playerId, listDir)
        }

        if (!accepted) {
            val player = getPlayerByID(playerId) ?: return
            player.setNextWalkTask(null)
            player.setNextWalkActionTask(null)
            player.sendCancelWalk()
        }
    }

    /** `Game::forcePlayerAutoWalk` (src/game/game.cpp:4548). PARITY GAP: the C++ `WalkStartPolicy` is not carried by the [startAutoWalk] seam. */
    fun forcePlayerAutoWalk(playerId: Long, listDir: List<Direction>) {
        val player = getPlayerByID(playerId) ?: return

        player.stopEventWalk()
        player.sendCancelTarget()
        player.setFollowCreature(null)

        player.resetLoginProtection()
        player.resetIdleTime()
        player.setNextWalkTask(null)

        startAutoWalk(player, listDir, true)
    }

    /**
     * `Game::internalGetPosition` (src/game/game.cpp:1269): the top-parent
     * branch resolves the container id/first index for player-held items and
     * the tile position/thing index otherwise. The C++ out-parameters are
     * folded into a `(position, stackPos)` pair.
     *
     * PARITY GAP: `Player` is not a `Cylinder`, so the inventory slot is
     * resolved by scanning the player's inventory instead of
     * `Player::getThingIndex`.
     */
    fun internalGetPosition(item: Item): Pair<Position, Int> {
        var pos = Position(0, 0, 0)
        var stackpos = 0

        val topParent = item.getTopParent()
        if (topParent != null) {
            val player = topParent.getPlayer()
            if (player != null) {
                pos = Position(0xFFFF, 0, 0)
                val container = item.getParent()?.getContainer()
                if (container != null) {
                    val containerId = player.getContainerID(container)
                    stackpos = container.getThingIndex(item)
                    pos = Position(0xFFFF, 0x40 or containerId, stackpos)
                } else {
                    val slot = Slots_t.entries.firstOrNull { player.getInventoryItem(it) === item }?.value ?: 0
                    stackpos = slot
                    pos = Position(0xFFFF, slot, 0)
                }
            } else {
                val tile = topParent.getTile()
                if (tile != null) {
                    pos = tile.position
                    stackpos = tile.getThingIndex(item)
                }
            }
        }

        return pos to stackpos
    }

    // --- remaining small helpers (game.cpp:777-7586, 11765) --------------------

    /**
     * `Game::internalRemoveItems` (src/game/game.cpp:11765): for stackable
     * vectors the requested amount is consumed across the list, otherwise every
     * item is removed whole.
     */
    fun internalRemoveItems(itemVector: List<Item>, amount: Long, stackable: Boolean) {
        var remaining = amount
        if (stackable) {
            for (item in itemVector) {
                if (item.getItemCount() > remaining) {
                    internalRemoveItem(item, remaining.toInt())
                    break
                } else {
                    remaining -= item.getItemCount()
                    internalRemoveItem(item)
                }
            }
        } else {
            for (item in itemVector) {
                internalRemoveItem(item)
            }
        }
    }

    /** `Game::createPlayerTask` (src/game/game.cpp:7586): the `Player::createPlayerTask` factory. */
    fun createPlayerTask(delay: Long, f: () -> Unit, context: String): PlayerTask =
        PlayerTask(delay, context, f)

    /** `Game::resetNpcs` (src/game/game.cpp:777): close every shop window and clear the interactions. */
    fun resetNpcs() {
        for (npc in npcs) {
            npc.closeAllShopWindows()
            npc.resetPlayerInteractions()
        }
    }

    /**
     * `Game::playerBrowseField` (src/game/game.cpp:5599): the floor and range
     * gates with the walk retry, the `playerOnBrowseField` vetoes, the
     * cached browse-field container under the position-derived dummy id, and
     * the close-or-open of that container.
     *
     * PARITY GAPS: `Container::createBrowseField` runs through
     * [containerCreateBrowseField] and the browse-field remap of
     * `Game::transformItem` is not ported.
     */
    fun playerBrowseField(playerId: Long, pos: Position) {
        val player = getPlayerByID(playerId) ?: return

        val playerPos = player.position
        if (playerPos.z != pos.z) {
            sendCancelMessage(
                player,
                if (playerPos.z > pos.z) ReturnValue.FIRSTGOUPSTAIRS else ReturnValue.FIRSTGODOWNSTAIRS,
            )
            return
        }

        if (!areInRange(playerPos, pos, 1, 1)) {
            val listDir = getPathTo(player, pos, 0, 1, true, true)
            if (listDir != null) {
                if (!queuePlayerAutoWalk(player, listDir)) {
                    return
                }
                val task = player.createPlayerTask(
                    400,
                    { playerBrowseField(playerId, pos) },
                    "Game::playerBrowseField",
                )
                player.setNextWalkActionTask(task)
            } else {
                sendCancelMessage(player, ReturnValue.THEREISNOWAY)
            }
            return
        }

        val tile = map.getTile(pos) ?: return

        if (!eventPlayerOnBrowseField(player, pos)) {
            return
        }

        if (!callbackPlayerOnBrowseField(player, tile.position)) {
            return
        }

        val container = browseFields[tile]?.get()
            ?: containerCreateBrowseField(tile)?.also { browseFields[tile] = WeakReference(it) }
            ?: return

        val dummyContainerId = 0xF - ((pos.x % 3) * 3 + (pos.y % 3))
        val openContainer = player.getContainerByID(dummyContainerId)
        if (openContainer != null) {
            playerOnCloseContainer(player, openContainer)
            player.closeContainer(dummyContainerId)
        } else {
            player.addContainer(dummyContainerId, container)
            player.sendContainer(dummyContainerId, container, false, 0)
        }
    }

    /** `Game::playerLeaveMarket` (src/game/game.cpp:10273). */
    fun playerLeaveMarket(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        player.setInMarket(false)
    }

    /**
     * `Game::playerBrowseMarket` (src/game/game.cpp:10282): only players inside
     * the market may browse, unknown items resolve to the empty
     * `Item::items[0]` entry, items without `wareId` are not marketable, and a
     * successful browse sends the active buy/sell offers plus the detail.
     */
    fun playerBrowseMarket(playerId: Long, itemId: Int, tier: Int) {
        val player = getPlayerByID(playerId) ?: return

        if (!player.isInMarket()) {
            return
        }

        val itemType = itemTypes?.get(itemId) ?: return
        if (itemType.id == 0) {
            return
        }

        if (itemType.wareId == 0) {
            return
        }

        val market = ioMarket ?: return
        val buyOffers = market.getActiveOffers(MarketOfferType.Buy, itemType.id, tier)
        val sellOffers = market.getActiveOffers(MarketOfferType.Sell, itemType.id, tier)
        sendMarketBrowseItem(player, itemType.id, buyOffers, sellOffers, tier)
        sendMarketDetail(player, itemType.id, tier)
    }

    /** `Game::playerBrowseMarketOwnOffers` (src/game/game.cpp:10307): own buy/sell offers of a player inside the market. */
    fun playerBrowseMarketOwnOffers(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return

        if (!player.isInMarket()) {
            return
        }

        val market = ioMarket ?: return
        val buyOffers = market.getOwnOffers(MarketOfferType.Buy, player.getGUID())
        val sellOffers = market.getOwnOffers(MarketOfferType.Sell, player.getGUID())
        sendMarketBrowseOwnOffers(player, buyOffers, sellOffers)
    }

    /** `Game::playerBrowseMarketOwnHistory` (src/game/game.cpp:10322): own buy/sell history of a player inside the market. */
    fun playerBrowseMarketOwnHistory(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return

        if (!player.isInMarket()) {
            return
        }

        val market = ioMarket ?: return
        val buyOffers = market.getOwnHistory(MarketOfferType.Buy, player.getGUID())
        val sellOffers = market.getOwnHistory(MarketOfferType.Sell, player.getGUID())
        sendMarketBrowseOwnHistory(player, buyOffers, sellOffers)
    }

    /**
     * `Game::getItemMarketPrice` (src/game/game.cpp:4027): gold/platinum/crystal
     * coins convert at 1/100/10000, a tier-0 `itemsPriceMap` entry wins over the
     * NPC buy/sell price, and unknown items contribute nothing.
     */
    fun getItemMarketPrice(itemMap: Map<Int, Long>, buyPrice: Boolean): Long {
        var total = 0L

        for ((itemId, count) in itemMap) {
            when (itemId) {
                ITEM_GOLD_COIN -> {
                    total += count
                    continue
                }

                ITEM_PLATINUM_COIN -> {
                    total += 100 * count
                    continue
                }

                ITEM_CRYSTAL_COIN -> {
                    total += 10000 * count
                    continue
                }
            }

            val marketPrice = itemsPriceMap[itemId]?.get(0)
            if (marketPrice != null) {
                total += marketPrice * count
                continue
            }

            val itemType = itemTypes?.get(itemId)
            val npcPrice = if (itemType != null) {
                if (buyPrice) itemType.buyPrice else itemType.sellPrice
            } else {
                0L
            }
            total += npcPrice * count
        }

        return total
    }

    /**
     * `Game::getItemMarketAveragePrice` (src/game/game.cpp:4078): the fixed coin
     * values, otherwise the `IOMarket` statistics averages blended with the
     * `(purchase & sale) + ((purchase ^ sale) >> 1)` formula.
     */
    fun getItemMarketAveragePrice(itemId: Int, tier: Int): Long {
        if (itemId == ITEM_GOLD_COIN) {
            return 1
        }
        if (itemId == ITEM_PLATINUM_COIN) {
            return 100
        }
        if (itemId == ITEM_CRYSTAL_COIN) {
            return 10000
        }

        val snapshot = ioMarket?.getStatistics(itemId, tier) ?: return 0

        var purchaseAverage = 0L
        var saleAverage = 0L
        var hasPurchaseData = false
        var hasSaleData = false

        val purchase = snapshot.purchase
        if (purchase != null && purchase.numTransactions > 0) {
            purchaseAverage = purchase.totalPrice / purchase.numTransactions
            hasPurchaseData = true
        }

        val sale = snapshot.sale
        if (sale != null && sale.numTransactions > 0) {
            saleAverage = sale.totalPrice / sale.numTransactions
            hasSaleData = true
        }

        return when {
            hasPurchaseData && hasSaleData -> (purchaseAverage and saleAverage) + ((purchaseAverage xor saleAverage) shr 1)
            hasPurchaseData -> purchaseAverage
            hasSaleData -> saleAverage
            else -> 0
        }
    }

    /**
     * `Game::loadItemsPrice` (src/game/game.cpp:1037): refreshes the `IOMarket`
     * statistics, then replaces the per-item tier map with the purchase
     * average price (`totalPrice / numTransactions`) and merges the highest
     * active buy offer per tier. Items absent from the statistics keep their
     * previous price map, like the C++ in-place member update.
     */
    fun loadItemsPrice() {
        val market = ioMarket ?: return
        market.updateStatistics()

        val newPriceMap = linkedMapOf<Int, MutableMap<Int, Long>>()
        for ((itemId, tiers) in itemsPriceMap) {
            newPriceMap[itemId] = tiers.toMutableMap()
        }

        for ((itemId, itemStats) in market.getPurchaseStatistics()) {
            val tierToPrice = linkedMapOf<Int, Long>()
            for ((tier, tierStats) in itemStats) {
                tierToPrice[tier] = tierStats.totalPrice / tierStats.numTransactions
            }
            newPriceMap[itemId] = tierToPrice
        }

        for (offer in market.getActiveOffers(MarketOfferType.Buy)) {
            val tierToPrice = newPriceMap.getOrPut(offer.itemId) { linkedMapOf() }
            tierToPrice[offer.tier] = maxOf(tierToPrice[offer.tier] ?: 0L, offer.price)
        }

        itemsPriceMap = newPriceMap
    }

    /**
     * `Game::updatePlayerSaleItems` (src/game/game.cpp:11596): send the
     * player's sale item list and clear the scheduled-sale-update flag.
     */
    fun updatePlayerSaleItems(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return

        val inventoryMap = linkedMapOf<Int, Int>()
        sendSaleItemList(player, getAllSaleItemIdAndCount(player, inventoryMap))
        setScheduledSaleUpdate(player, false)
    }

    /**
     * `Game::checkCanInitCreateMarketOffer` (src/game/game.cpp:10653, anonymous
     * namespace): the ordered creation gates. The C++ `type` is a raw byte and
     * validates the `MARKETACTION_*` cast; the Kotlin enum makes that check
     * unreachable.
     */
    private fun checkCanInitCreateMarketOffer(
        player: Player?,
        type: MarketOfferType,
        itemType: ItemType,
        amount: Int,
        price: Long,
        offerStatus: StringBuilder,
    ): Boolean {
        if (player == null) {
            offerStatus.append("Failed to load player")
            return false
        }

        if (player.account == null) {
            offerStatus.append("Failed to load player account")
            return false
        }

        if (!player.isInMarket()) {
            offerStatus.append("Failed to load market for player ${player.name}")
            return false
        }

        if (price == 0L) {
            offerStatus.append("Failed to process price for player ${player.name}")
            return false
        }

        if (price > MAX_MARKET_OFFER_PRICE) {
            offerStatus.append("Player ${player.name} is trying to sell an item with a higher than allowed value")
            return false
        }

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return false
        }

        if (itemType.id == 0 || itemType.wareId == 0) {
            offerStatus.append("Failed to load offer or item id")
            return false
        }

        if (amount == 0 || (!itemType.isStackable && amount > 2000) || (itemType.isStackable && amount > 64000)) {
            offerStatus.append("Failed to load amount $amount for player ${player.name}")
            return false
        }

        logger.debug { "[checkCanInitCreateMarketOffer] - Offer amount: $amount" }

        if (marketPremium && !player.isPremium()) {
            sendTextMessage(player, MessageClass.MESSAGE_MARKET, "Only premium accounts may create offers for that object.")
            return false
        }

        val maxOfferCount = maxMarketOffersAtATimePerPlayer
        if (maxOfferCount != 0 && (ioMarket?.getPlayerOfferCount(player.getGUID()) ?: 0) >= maxOfferCount) {
            offerStatus.append("Player ${player.name}excedeed max offer count $maxOfferCount")
            return false
        }

        return true
    }

    /**
     * `Game::playerCreateMarketOffer` (src/game/game.cpp:10717): validates the
     * request, removes the sold items (stash then depot locker) or charges the
     * buy price plus the 2% fee, persists the offer through [IOMarket] and
     * refreshes the browse window. Metrics counters and the `g_saveManager`
     * global are not ported (PARITY GAPS); [savePlayer] is the injected seam.
     */
    fun playerCreateMarketOffer(
        playerId: Long,
        type: MarketOfferType,
        itemId: Int,
        amount: Int,
        price: Long,
        tier: Int,
        anonymous: Boolean,
    ) {
        val offerStatus = StringBuilder()
        val player = getPlayerByID(playerId)
        val itemType = itemTypes?.get(itemId) ?: ItemType(id = 0, name = "")

        if (!checkCanInitCreateMarketOffer(player, type, itemType, amount, price, offerStatus)) {
            logger.error {
                "[playerCreateMarketOffer] - Player ${player?.name ?: playerId} had an error on init offer on the market, " +
                    "error code: $offerStatus"
            }
            return
        }

        checkNotNull(player)

        var offerTier = tier
        if (offerTier > forgeMaxItemTier) {
            offerTier = forgeMaxItemTier
        }

        if (offerTier > 0 && itemType.upgradeClassification == 0) {
            offerTier = 0
        }

        val totalPrice = price * amount
        val totalFee = (totalPrice * 0.02).toLong()
        val minFee = 20L
        var maxFee = minOf(1_000_000L, totalFee)
        if (maxFee < minFee) {
            maxFee = minFee
        }
        val fee = totalFee.coerceIn(minFee, maxFee)

        if (type == MarketOfferType.Sell) {
            if (fee > getMoney(player) + getBankBalance(player)) {
                offerStatus.append("Fee is greater than player money")
                return
            }

            val depotLocker = player.getDepotLocker(player.getLastDepotId())
            if (itemType.id == ITEM_STORE_COIN) {
                val account = player.account ?: return
                val (transferableCoins, _) = account.getCoins(CoinType.Transferable)
                if (amount.toUInt() > transferableCoins) {
                    offerStatus.append("Amount is greater than coins for player ${player.name}")
                    return
                }

                account.removeCoins(CoinType.Transferable, amount.toUInt(), "")
            } else if (!removeOfferItems(player, depotLocker, itemType, amount, offerTier, offerStatus)) {
                logger.error {
                    "[playerCreateMarketOffer] failed to remove item with id ${itemType.id}, from player ${player.name}, " +
                        "errorcode: $offerStatus"
                }
                return
            }

            removeMoney(player, fee, 0, true)
        } else {
            var totalPriceWithFee = price * amount
            totalPriceWithFee += fee
            if (totalPriceWithFee > getMoney(player) + getBankBalance(player)) {
                offerStatus.append("Fee is greater than player money (buy offer)")
                return
            }

            removeMoney(player, totalPriceWithFee, 0, true)
        }

        sendMarketEnter(player, player.getLastDepotId())

        if (offerStatus.isNotEmpty()) {
            if (offerStatus.toString() == ITEM_MARKET_INCORRECT_MESSAGE) {
                sendTextMessage(player, MessageClass.MESSAGE_MARKET, offerStatus.toString())
            } else {
                sendTextMessage(
                    player,
                    MessageClass.MESSAGE_MARKET,
                    "There was an error processing your offer, please contact the administrator.",
                )
            }
            logger.error {
                "[playerCreateMarketOffer] - Player ${player.name} had an error creating an offer on the market, " +
                    "error code: $offerStatus"
            }
            return
        }

        val market = ioMarket ?: return
        market.createOffer(player.getGUID(), type, itemType.id.toLong(), amount, price, offerTier, anonymous)

        val buyOffers = market.getActiveOffers(MarketOfferType.Buy, itemType.id, offerTier)
        val sellOffers = market.getActiveOffers(MarketOfferType.Sell, itemType.id, offerTier)
        sendMarketBrowseItem(player, itemType.id, buyOffers, sellOffers, offerTier)

        updateUIExhausted(player)
        savePlayer(player)
    }

    /**
     * `Game::removeOfferItems` (src/game/game.cpp:10338, anonymous namespace):
     * tier-0 offers take the count from the stash first, then the remaining
     * count is removed from the depot locker item vector.
     *
     * PARITY GAP: the Kotlin [Player] only removes inventory items directly;
     * [removeItem] is the injected seam for locker-cylinder removal.
     */
    private fun removeOfferItems(
        player: Player,
        depotLocker: DepotLocker,
        itemType: ItemType,
        amount: Int,
        tier: Int,
        offerStatus: StringBuilder,
    ): Boolean {
        var removeAmount = amount
        if (tier == 0) {
            val stashItemCount = getStashItemCount(player, itemType.wareId)
            if (stashItemCount > 0) {
                if (removeAmount > stashItemCount && withdrawItem(player, itemType.wareId, stashItemCount)) {
                    removeAmount -= stashItemCount
                } else if (withdrawItem(player, itemType.wareId, removeAmount)) {
                    removeAmount = 0
                } else {
                    offerStatus.append("Failed to remove stash items from player ${player.name}")
                    return false
                }
            }
        }

        val (itemVector, totalCount) = getLockerItemsAndCountById(player, depotLocker, tier, itemType.id)
        if (removeAmount > 0) {
            if (totalCount == 0 || itemVector.isEmpty()) {
                offerStatus.append("Player ${player.name} not have item for create offer")
                return false
            }

            var removedCount = 0
            for (item in itemVector) {
                if (removedCount >= removeAmount) {
                    break
                }

                val thisRemove = minOf(removeAmount - removedCount, item.getItemCount())
                val ret = removeItem(player, item, thisRemove)
                if (ret != ReturnValue.NOERROR) {
                    offerStatus.append(
                        "Failed to remove: $amount items of id: ${itemType.id} from player ${player.name} error: $ret",
                    )
                    return false
                }

                removedCount += thisRemove
            }

            updateState(player)

            if (removedCount < removeAmount) {
                logger.error { "Player ${player.name} tried to sell an item ${itemType.id} without this item" }
                offerStatus.append(ITEM_MARKET_INCORRECT_MESSAGE)
                return false
            }
        }
        return true
    }

    /**
     * `Game::playerCancelMarketOffer` (src/game/game.cpp:10819): refunds a buy
     * offer to the bank or returns the sold items to the inbox, then moves the
     * offer to the cancelled history.
     */
    fun playerCancelMarketOffer(playerId: Long, timestamp: Long, counter: Int) {
        val player = getPlayerByID(playerId)
        if (player == null || player.account == null) {
            return
        }

        if (!player.isInMarket()) {
            return
        }

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        val market = ioMarket ?: return
        val offer = market.getOfferByCounter(timestamp, counter)
        if (offer.id == 0L || offer.playerId != player.getGUID()) {
            return
        }

        var offerTier = offer.tier
        if (offer.type == MarketOfferType.Buy) {
            setBankBalance(player, getBankBalance(player) + offer.price * offer.amount)
            sendMarketEnter(player, player.getLastDepotId())
        } else {
            val itemType = itemTypes?.get(offer.itemId) ?: return
            if (itemType.id == 0) {
                return
            }

            offerTier = if (itemType.upgradeClassification > 0) minOf(offer.tier, forgeMaxItemTier) else 0

            if (itemType.id == ITEM_STORE_COIN) {
                player.account?.addCoins(CoinType.Transferable, offer.amount.toUInt(), "")
            } else {
                val inboxCheckResult = queryItemInsertion(player, itemType.id, offer.amount, offerTier)
                if (handleInboxPrecheckFailure(player, inboxCheckResult, "playerCancelMarketOffer")) {
                    return
                }

                val inboxInsertResult = processItemInsertion(player, itemType.id, offer.amount, offerTier)
                if (inboxInsertResult != ReturnValue.NOERROR) {
                    sendTextMessage(
                        player,
                        MessageClass.MESSAGE_MARKET,
                        "There was an error returning your items, please contact the administrator.",
                    )
                    logger.error {
                        "[playerCancelMarketOffer] - Failed to return cancelled offer item ${itemType.id} total amount " +
                            "${offer.amount} to inbox for player ${player.name}, error code: $inboxInsertResult"
                    }
                    return
                }
            }
        }

        market.moveOfferToHistory(offer.id, MarketOfferState.Cancelled)

        val cancelledOffer =
            offer.copy(tier = offerTier, amount = 0, timestamp = offer.timestamp + marketOfferDuration)
        sendMarketCancelOffer(player, cancelledOffer)
        sendMarketEnter(player, player.getLastDepotId())
        updateUIExhausted(player)
        savePlayer(player)
    }

    /**
     * `Game::playerAcceptMarketOffer` (src/game/game.cpp:10884): matches a
     * market offer counter, moves the goods between seller locker and buyer
     * inbox (or the store-coin accounts), settles the price on the bank
     * balances and writes both history rows.
     *
     * PARITY GAPS: the item movement and inbox insertions are injected seams
     * ([getLockerItemsAndCountById], [addItemBatchToPaginedContainer],
     * [removeItem]) because the Kotlin [Player] does not model the locker
     * cylinder removal nor the paginated inbox engine; the metrics counters
     * are not ported.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun playerAcceptMarketOffer(playerId: Long, timestamp: Long, counter: Int, amount: Int) {
        val offerStatus = StringBuilder()
        val player = getPlayerByID(playerId)
        if (player == null || player.account == null) {
            offerStatus.append("Failed to load player")
            return
        }

        if (!player.isInMarket()) {
            offerStatus.append("Failed to load market")
            return
        }

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        val market = ioMarket ?: return
        val offer = market.getOfferByCounter(timestamp, counter)
        if (offer.id == 0L) {
            offerStatus.append("Failed to load offer id")
            return
        }

        val itemType = itemTypes?.get(offer.itemId) ?: ItemType(id = 0, name = "")
        if (itemType.id == 0) {
            offerStatus.append("Failed to load item id")
            return
        }

        val offerTier = if (itemType.upgradeClassification > 0) minOf(offer.tier, forgeMaxItemTier) else 0

        if (amount == 0 || (!itemType.isStackable && amount > 2000) || (itemType.isStackable && amount > 64000) ||
            amount > offer.amount
        ) {
            offerStatus.append("Invalid offer amount $amount for player ${player.name}")
            return
        }

        val totalPrice = offer.price * amount

        if (offer.type == MarketOfferType.Buy) {
            val depotLocker = player.getDepotLocker(player.getLastDepotId())

            val buyerPlayer = getPlayerByGUID(offer.playerId, true)
            if (buyerPlayer == null) {
                offerStatus.append("Failed to load buyer player ${player.name}")
                return
            }

            val buyerPlayerAccount = buyerPlayer.account
            if (buyerPlayerAccount == null) {
                sendTextMessage(player, MessageClass.MESSAGE_MARKET, "Cannot accept offer.")
                return
            }

            val playerAccount = player.account ?: return
            if (player === buyerPlayer || playerAccount === buyerPlayerAccount) {
                sendTextMessage(player, MessageClass.MESSAGE_MARKET, "You cannot accept your own offer.")
                return
            }

            if (itemType.id != ITEM_STORE_COIN) {
                val inboxCheckResult = queryItemInsertion(buyerPlayer, itemType.id, amount, offerTier)
                if (handleBuyerInboxPrecheckFailure(player, buyerPlayer, inboxCheckResult, "playerAcceptMarketOffer")) {
                    return
                }

                val inboxInsertResult = processItemInsertion(buyerPlayer, itemType.id, amount, offerTier)
                if (inboxInsertResult != ReturnValue.NOERROR) {
                    offerStatus.append(
                        "Failed to add item ${itemType.id} total amount $amount to inbox for player " +
                            "${buyerPlayer.name} error: $inboxInsertResult",
                    )
                }
            }

            if (offerStatus.isNotEmpty()) {
                sendTextMessage(
                    player,
                    MessageClass.MESSAGE_MARKET,
                    "There was an error processing your offer, please contact the administrator.",
                )
                logger.error {
                    "[playerAcceptMarketOffer] - Player ${player.name} had an error accepting an offer on the market, " +
                        "error code: $offerStatus"
                }
                sendMarketEnter(player, player.getLastDepotId())
                return
            }

            if (itemType.id == ITEM_STORE_COIN) {
                val (transferableCoins, error) = playerAccount.getCoins(CoinType.Transferable)
                if (error != AccountErrors.Ok) {
                    offerStatus.append("Failed to load transferable coins for player ${player.name}")
                    return
                }

                if (amount.toUInt() > transferableCoins) {
                    offerStatus.append("Amount is greater than coins")
                    return
                }

                playerAccount.removeCoins(CoinType.Transferable, amount.toUInt(), "Sold on Market")
            } else if (!removeOfferItems(player, depotLocker, itemType, amount, offerTier, offerStatus)) {
                val rollbackResult =
                    if (rollbackInboxInsertion(buyerPlayer, itemType.id, amount, offerTier)) {
                        ReturnValue.NOERROR
                    } else {
                        ReturnValue.NOTPOSSIBLE
                    }
                if (rollbackResult != ReturnValue.NOERROR) {
                    offerStatus.append("; rollback from buyer inbox failed: $rollbackResult")
                    logger.error {
                        "[playerAcceptMarketOffer] - Failed to rollback delivered market items ${itemType.id} amount " +
                            "$amount from buyer ${buyerPlayer.name} inbox after seller removal error, rollback code: " +
                            rollbackResult
                    }
                } else {
                    logger.warn {
                        "[playerAcceptMarketOffer] - Rolled back delivered market items ${itemType.id} amount $amount " +
                            "from buyer ${buyerPlayer.name} inbox after seller removal error"
                    }
                }
                logger.error {
                    "[playerAcceptMarketOffer] failed to remove item with id ${itemType.id}, from player " +
                        "${player.name}, errorcode: $offerStatus"
                }
            }

            if (offerStatus.isNotEmpty()) {
                if (offerStatus.toString() == ITEM_MARKET_INCORRECT_MESSAGE) {
                    sendTextMessage(player, MessageClass.MESSAGE_MARKET, offerStatus.toString())
                } else {
                    sendTextMessage(
                        player,
                        MessageClass.MESSAGE_MARKET,
                        "There was an error processing your offer, please contact the administrator.",
                    )
                }
                logger.error {
                    "[playerAcceptMarketOffer] - Player ${player.name} had an error accepting an offer on the market, " +
                        "error code: $offerStatus"
                }
                sendMarketEnter(player, player.getLastDepotId())
                return
            }

            if (itemType.id == ITEM_STORE_COIN) {
                buyerPlayer.account?.addCoins(CoinType.Transferable, amount.toUInt(), "Purchased on Market")
            }

            if (offerStatus.isEmpty()) {
                setBankBalance(player, getBankBalance(player) + totalPrice)

                if (isOffline(buyerPlayer)) {
                    savePlayer(buyerPlayer)
                }
            }
        } else if (offer.type == MarketOfferType.Sell) {
            val sellerPlayer = getPlayerByGUID(offer.playerId, true)
            if (sellerPlayer == null) {
                offerStatus.append("Failed to load seller player")
                return
            }

            if (player === sellerPlayer || player.account === sellerPlayer.account) {
                sendTextMessage(player, MessageClass.MESSAGE_MARKET, "You cannot accept your own offer.")
                return
            }

            if (totalPrice > getBankBalance(player) + getMoney(player)) {
                return
            }

            if (itemType.id != ITEM_STORE_COIN) {
                val inboxCheckResult = queryItemInsertion(player, itemType.id, amount, offerTier)
                if (handleInboxPrecheckFailure(player, inboxCheckResult, "playerAcceptMarketOffer")) {
                    return
                }
            }

            val buyerWithdrawnFunds = withdrawMarketFunds(player, totalPrice)
            if (buyerWithdrawnFunds == null) {
                sendTextMessage(
                    player,
                    MessageClass.MESSAGE_MARKET,
                    "There was an error processing your offer, please contact the administrator.",
                )
                logger.error {
                    "[playerAcceptMarketOffer] - Failed to debit buyer funds for accepted market offer " +
                        "(player: ${player.name}, price: $totalPrice)"
                }
                return
            }

            if (itemType.id == ITEM_STORE_COIN) {
                player.account?.addCoins(CoinType.Transferable, amount.toUInt(), "Purchased on Market")
            } else {
                val inboxInsertResult = processItemInsertion(player, itemType.id, amount, offerTier)
                if (inboxInsertResult != ReturnValue.NOERROR) {
                    refundMarketFunds(player, buyerWithdrawnFunds)
                    offerStatus.append(
                        "Failed to add item ${itemType.id} total amount $amount to inbox for player " +
                            "${player.name} error: $inboxInsertResult",
                    )
                }
            }

            if (offerStatus.isEmpty()) {
                setBankBalance(sellerPlayer, getBankBalance(sellerPlayer) + totalPrice)
                if (itemType.id == ITEM_STORE_COIN) {
                    sellerPlayer.account?.registerCoinTransaction(
                        CoinTransactionType.Remove,
                        CoinType.Transferable,
                        amount.toUInt(),
                        "Sold on Market",
                    )
                }

                if (itemType.id != ITEM_STORE_COIN) {
                    onReceiveMail(player)
                }

                if (isOffline(sellerPlayer)) {
                    savePlayer(sellerPlayer)
                }
            }
        }

        sendMarketEnter(player, player.getLastDepotId())

        if (offerStatus.isNotEmpty()) {
            sendTextMessage(
                player,
                MessageClass.MESSAGE_MARKET,
                "There was an error processing your offer, please contact the administrator.",
            )
            logger.error {
                "[playerAcceptMarketOffer] - Player ${player.name} had an error accepting an offer on the market, " +
                    "error code: $offerStatus"
            }
            return
        }

        market.appendHistory(
            player.getGUID(),
            if (offer.type == MarketOfferType.Buy) MarketOfferType.Sell else MarketOfferType.Buy,
            offer.itemId,
            amount,
            offer.price,
            marketTimeNow(),
            offerTier,
            MarketOfferState.AcceptedEx,
        )
        market.appendHistory(
            offer.playerId,
            offer.type,
            offer.itemId,
            amount,
            offer.price,
            marketTimeNow(),
            offerTier,
            MarketOfferState.Accepted,
        )

        val remainingAmount = offer.amount - amount
        if (remainingAmount == 0) {
            market.deleteOffer(offer.id)
        } else {
            market.acceptOffer(offer.id, amount)
        }

        sendMarketAcceptOffer(
            player,
            offer.copy(amount = remainingAmount, tier = offerTier, timestamp = offer.timestamp + marketOfferDuration),
        )
        updateUIExhausted(player)
        savePlayer(player)
    }

    /** `Game::queryItemInsertion` (src/game/game.cpp:10400, anonymous namespace). */
    private fun queryItemInsertion(recipient: Player, itemId: Int, amount: Int, tier: Int): ReturnValue {
        val (result, _) = addItemBatchToPaginedContainer(
            recipient,
            recipient.inbox,
            itemId,
            amount,
            CylinderFlags.FLAG_NOLIMIT,
            tier,
            true,
        )
        return result
    }

    /**
     * `Game::processItemInsertion` (src/game/game.cpp:10458, anonymous
     * namespace): a mismatch between requested and actually added items rolls
     * the partial insertion back and reports `NOTPOSSIBLE`.
     */
    private fun processItemInsertion(recipient: Player, itemId: Int, amount: Int, tier: Int): ReturnValue {
        val (returnValue, actuallyAdded) = addItemBatchToPaginedContainer(
            recipient,
            recipient.inbox,
            itemId,
            amount,
            0,
            tier,
            false,
        )
        if (returnValue != ReturnValue.NOERROR) {
            logger.warn {
                "processItemInsertion - Failed to add item $itemId total amount $amount to inbox for player " +
                    "${recipient.name}, error code: $returnValue"
            }
            return returnValue
        }

        if (actuallyAdded != amount) {
            logger.error {
                "processItemInsertion - Atomic inbox insertion mismatch for item $itemId total amount $amount, " +
                    "currently added: $actuallyAdded to inbox for player ${recipient.name}"
            }
            if (actuallyAdded > 0 && !rollbackInboxInsertion(recipient, itemId, actuallyAdded, tier)) {
                logger.error {
                    "processItemInsertion - Failed to rollback partial inbox insertion mismatch for item $itemId " +
                        "total amount $amount, currently added: $actuallyAdded to inbox for player ${recipient.name}"
                }
            }
            return ReturnValue.NOTPOSSIBLE
        }

        return ReturnValue.NOERROR
    }

    /**
     * `Game::rollbackInboxInsertion` (src/game/game.cpp:10415, anonymous
     * namespace): removes up to [amount] inbox items of [itemId] and [tier].
     *
     * PARITY GAP: the C++ `Item::equals` probe also compares every integer
     * attribute and the store/owner flags; the Kotlin port matches id and tier
     * and removes through the [removeItem] seam.
     */
    private fun rollbackInboxInsertion(recipient: Player, itemId: Int, amount: Int, tier: Int): Boolean {
        if (amount == 0) {
            return true
        }

        val inbox = recipient.inbox
        val candidates = inbox.getItemList().filter { it.id == itemId && it.getTier() == tier }
        var removedCount = 0
        for (rollbackItem in candidates) {
            if (removedCount >= amount) {
                break
            }

            val toRemove = minOf(amount - removedCount, rollbackItem.getItemCount())
            val removeResult = removeItem(recipient, rollbackItem, toRemove)
            if (removeResult != ReturnValue.NOERROR) {
                logger.error {
                    "rollbackInboxInsertion - Failed to rollback inbox item $itemId amount $toRemove for player " +
                        "${recipient.name}, error code: $removeResult"
                }
                return false
            }

            removedCount += toRemove
        }

        if (removedCount < amount) {
            logger.error {
                "rollbackInboxInsertion - Incomplete inbox rollback for player ${recipient.name}, item $itemId, " +
                    "expected remove $amount, removed $removedCount"
            }
            return false
        }

        return true
    }

    /** `Game::sendInboxSpaceMessage` (src/game/game.cpp:10469, anonymous namespace). */
    private fun sendInboxSpaceMessage(recipient: Player) {
        if (!isOffline(recipient)) {
            sendTextMessage(recipient, MessageClass.MESSAGE_MARKET, "Not enough space in your inbox.")
        }
    }

    /** `Game::sendBuyerInboxSpaceMessage` (src/game/game.cpp:10475, anonymous namespace). */
    private fun sendBuyerInboxSpaceMessage(seller: Player, buyer: Player) {
        sendTextMessage(seller, MessageClass.MESSAGE_MARKET, "The buyer does not have enough space in the inbox.")
        sendInboxSpaceMessage(buyer)
    }

    /** `Game::isInboxCapacityError` (src/game/game.cpp:10483, anonymous namespace). */
    private fun isInboxCapacityError(returnValue: ReturnValue): Boolean =
        returnValue == ReturnValue.DEPOTISFULL ||
            returnValue == ReturnValue.CONTAINERNOTENOUGHROOM ||
            returnValue == ReturnValue.NOTENOUGHROOM

    /** `Game::handleInboxPrecheckFailure` (src/game/game.cpp:10489, anonymous namespace). */
    private fun handleInboxPrecheckFailure(recipient: Player, returnValue: ReturnValue, context: String): Boolean {
        if (returnValue == ReturnValue.NOERROR) {
            return false
        }

        if (isInboxCapacityError(returnValue)) {
            sendInboxSpaceMessage(recipient)
        } else {
            if (!isOffline(recipient)) {
                sendTextMessage(
                    recipient,
                    MessageClass.MESSAGE_MARKET,
                    "There was an error processing your offer, please contact the administrator.",
                )
            }

            logger.warn {
                "$context - Unexpected inbox precheck failure for player ${recipient.name}, error code: $returnValue"
            }
        }

        return true
    }

    /** `Game::handleBuyerInboxPrecheckFailure` (src/game/game.cpp:10507, anonymous namespace). */
    private fun handleBuyerInboxPrecheckFailure(
        seller: Player,
        buyer: Player,
        returnValue: ReturnValue,
        context: String,
    ): Boolean {
        if (returnValue == ReturnValue.NOERROR) {
            return false
        }

        if (isInboxCapacityError(returnValue)) {
            sendBuyerInboxSpaceMessage(seller, buyer)
        } else {
            sendTextMessage(
                seller,
                MessageClass.MESSAGE_MARKET,
                "There was an error processing your offer, please contact the administrator.",
            )
            if (!isOffline(buyer)) {
                sendTextMessage(
                    buyer,
                    MessageClass.MESSAGE_MARKET,
                    "There was an error processing your offer, please contact the administrator.",
                )
            }

            logger.warn {
                "$context - Unexpected buyer inbox precheck failure (seller: ${seller.name}, buyer: ${buyer.name}), " +
                    "error code: $returnValue"
            }
        }

        return true
    }

    /**
     * `Game::removeInventoryMoneyToBank` (src/game/game.cpp:10560, anonymous
     * namespace): removes carried money worth [money] from the inventory and
     * banks any change from an oversized coin stack. Returns whether the
     * inventory held enough worth.
     *
     * PARITY GAP: the C++ skip `item->getContainer()` becomes
     * `getParent() is Container`, and `Item::getWorth`/`Game::internalRemoveItem`
     * are the ported item methods.
     */
    private fun removeInventoryMoneyToBank(player: Player, money: Long): Boolean {
        if (money == 0L) {
            return true
        }

        val moneyMap = sortedMapOf<Long, MutableList<Item>>()
        var inventoryMoney = 0L

        for (item in player.getAllInventoryItems()) {
            if (item.getParent() is Container) {
                continue
            }

            val worth = item.getWorth()
            if (worth == 0L) {
                continue
            }

            inventoryMoney += worth
            moneyMap.getOrPut(worth) { mutableListOf() }.add(item)
        }

        if (inventoryMoney < money) {
            return false
        }

        var remaining = money
        for ((worth, items) in moneyMap) {
            for (item in items) {
                if (worth < remaining) {
                    internalRemoveItem(item)
                    remaining -= worth
                    continue
                }

                if (worth > remaining) {
                    val singleWorth = worth / item.getItemCount()
                    val removeCount = kotlin.math.ceil(remaining / singleWorth.toDouble()).toInt()
                    val change = singleWorth * removeCount - remaining
                    internalRemoveItem(item, removeCount)
                    if (change > 0) {
                        setBankBalance(player, getBankBalance(player) + change)
                    }
                    return true
                }

                internalRemoveItem(item)
                return true
            }
        }

        return true
    }

    /**
     * `Game::withdrawMarketFunds` (src/game/game.cpp:10593, anonymous
     * namespace): debits the bank first, then the inventory; a failed inventory
     * withdrawal restores the bank and returns `null`.
     */
    private fun withdrawMarketFunds(player: Player, amount: Long): WithdrawnFunds? {
        if (amount == 0L) {
            return WithdrawnFunds()
        }

        var withdrawnFunds = WithdrawnFunds()
        withdrawnFunds.bank = minOf(getBankBalance(player), amount)
        if (withdrawnFunds.bank > 0) {
            setBankBalance(player, getBankBalance(player) - withdrawnFunds.bank)
        }

        withdrawnFunds.inventory = amount - withdrawnFunds.bank
        if (withdrawnFunds.inventory > 0 && !removeInventoryMoneyToBank(player, withdrawnFunds.inventory)) {
            if (withdrawnFunds.bank > 0) {
                setBankBalance(player, getBankBalance(player) + withdrawnFunds.bank)
            }
            return null
        }

        return withdrawnFunds
    }

    /** `Game::refundMarketFunds` (src/game/game.cpp:10622, anonymous namespace). */
    private fun refundMarketFunds(player: Player, withdrawnFunds: WithdrawnFunds) {
        if (withdrawnFunds.total() == 0L) {
            return
        }

        setBankBalance(player, getBankBalance(player) + withdrawnFunds.total())
    }

    /** `Game::WithdrawnFunds` (src/game/game.cpp:10544, anonymous namespace). */
    private data class WithdrawnFunds(
        var bank: Long = 0,
        var inventory: Long = 0,
    ) {
        fun total(): Long = bank + inventory
    }

    /** `Game::playerCloseImbuementWindow` (src/game/game.cpp:7178). */
    fun playerCloseImbuementWindow(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        player.setImbuingItem(null)
    }

    /** `Game::playerShowQuestLog` (src/game/game.cpp:7391): dispatch the request to the event and callback buckets. */
    fun playerShowQuestLog(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return

        eventPlayerOnRequestQuestLog(player)
        callbackPlayerOnRequestQuestLog(player)
    }

    /** `Game::playerShowQuestLine` (src/game/game.cpp:7401): dispatch the quest id to the event and callback buckets. */
    fun playerShowQuestLine(playerId: Long, questId: Int) {
        val player = getPlayerByID(playerId) ?: return

        eventPlayerOnRequestQuestLine(player, questId)
        callbackPlayerOnRequestQuestLine(player, questId)
    }

    /**
     * `Game::playerPreyAction` (src/game/game.cpp:10218): `IOPrey::parsePreyAction`
     * over the player's prey state. Unknown action/option bytes cannot be cast to
     * the C++ enum and are ignored.
     */
    fun playerPreyAction(playerId: Long, slot: Int, action: Int, option: Int, index: Int, raceId: Int) {
        val player = getPlayerByID(playerId) ?: return

        val preyAction = PreyAction.entries.firstOrNull { it.value == action } ?: return
        val preyOption = PreyOption.entries.firstOrNull { it.value == option } ?: return

        player.features.preyEngine.parsePreyAction(
            player = player.features.prey,
            slotId = slot,
            action = preyAction,
            option = preyOption,
            index = index,
            raceId = raceId,
        )
    }

    /**
     * `Game::playerTaskHuntingAction` (src/game/game.cpp:10227):
     * `IOPrey::parseTaskHuntingAction` over the player's task-hunting state.
     */
    fun playerTaskHuntingAction(playerId: Long, slot: Int, action: Int, upgrade: Boolean, raceId: Int) {
        val player = getPlayerByID(playerId) ?: return

        val taskAction = PreyTaskAction.entries.firstOrNull { it.value == action } ?: return

        player.features.taskHuntingEngine.parseTaskHuntingAction(
            player = player.features.taskHunting,
            slotId = slot,
            action = taskAction,
            upgrade = upgrade,
            raceId = raceId,
        )
    }

    /**
     * `Game::playerBosstiarySlot` (src/game/game.cpp:11256): an exhausted player
     * is rejected; clearing a slot (`selectedBossId == 0`) that does not hold
     * the boosted boss charges `IOBosstiary::calculteRemoveBoss` through
     * `Game::removeMoney(..., useBalance = true)` and advances the removal
     * counter; the requested boss id is stored either way.
     */
    fun playerBosstiarySlot(playerId: Long, slotId: Int, selectedBossId: Long) {
        val player = getPlayerByID(playerId) ?: return

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        updateUIExhausted(player)

        val bossIdSlot = getSlotBossId(player, slotId)

        val boostedBossId = ioBosstiary?.getBoostedBossId()?.toLong() ?: 0L
        if (selectedBossId == 0L && bossIdSlot != boostedBossId) {
            val removeTimes = getRemoveTimes(player)
            val removePrice = ioBosstiary?.calculteRemoveBoss(removeTimes)?.toLong() ?: 0L
            removeMoney(player, removePrice, 0, true)
            addRemoveTime(player)
        }

        setSlotBossId(player, slotId, selectedBossId)
    }

    /**
     * `Game::playerForgeResourceConversion` (src/game/game.cpp:11226): the
     * exhaustion gate runs before `Player::forgeResourceConversion`.
     */
    fun playerForgeResourceConversion(playerId: Long, actionType: ForgeAction_t) {
        val player = getPlayerByID(playerId) ?: return

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        updateUIExhausted(player)
        forgeResourceConversion(player, actionType)
    }

    /** `Game::playerBrowseForgeHistory` (src/game/game.cpp:11241): the exhaustion gate runs before `Player::forgeHistory`. */
    fun playerBrowseForgeHistory(playerId: Long, page: Int) {
        val player = getPlayerByID(playerId) ?: return

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        updateUIExhausted(player)
        forgeHistory(player, page)
    }

    /**
     * `Game::playerForgeTransferItemTier` (src/game/game.cpp:11211): the
     * exhaustion gate runs before `Player::forgeTransferItemTier`.
     */
    fun playerForgeTransferItemTier(
        playerId: Long,
        actionType: ForgeAction_t,
        donorItemId: Int,
        tier: Int,
        receiveItemId: Int,
        convergence: Boolean,
    ) {
        val player = getPlayerByID(playerId) ?: return

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        updateUIExhausted(player)
        forgeTransferItemTier(player, actionType, donorItemId, tier, receiveItemId, convergence)
    }

    /**
     * `Game::playerForgeFuseItems` (src/game/game.cpp:11178): the exhaustion
     * gate runs first, then the success roll (base rate adjusted by the
     * eventscheduler `forge-chance` KV and the used-core bonus) and the bonus
     * roll are consumed before `Player::forgeFuseItems` runs. Metrics latency
     * measurement is not ported.
     */
    fun playerForgeFuseItems(
        playerId: Long,
        actionType: ForgeAction_t,
        firstItemId: Int,
        tier: Int,
        secondItemId: Int,
        usedCore: Boolean,
        reduceTierLoss: Boolean,
        convergence: Boolean,
    ) {
        val player = getPlayerByID(playerId) ?: return

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        updateUIExhausted(player)

        val coreCount = (if (usedCore) 1 else 0) + (if (reduceTierLoss) 1 else 0)
        val finalRate = forge.finalSuccessRate(usedCore, getForgeChance())
        val success = forge.rollSuccess(forgeRandom.uniformRandom(1, 100), finalRate)

        val chance = forgeRandom.uniformRandom(0, 10000)
        val bonus = if (convergence) 0 else forge.forgeBonus(chance)

        forgeFuseItems(
            player,
            actionType,
            firstItemId,
            tier,
            secondItemId,
            success,
            reduceTierLoss,
            convergence,
            bonus,
            coreCount,
        )
    }

    /**
     * `Game::playerOpenWheel` (src/game/game.cpp:11525): a player may only open
     * their own wheel, the exhaustion gate runs before the window is sent, and
     * the marker is refreshed afterwards.
     */
    fun playerOpenWheel(playerId: Long, ownerId: Long) {
        val player = getPlayerByID(playerId) ?: return

        if (playerId != ownerId) {
            logger.error { "[playerOpenWheel] player ${player.name} is trying to open wheel of another player" }
            return
        }

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        sendOpenWheelWindow(player, ownerId)
        updateUIExhausted(player)
    }

    /**
     * `Game::playerSaveWheel` (src/game/game.cpp:11538): the exhaustion gate
     * runs before `PlayerWheel::saveSlotPointsOnPressSaveButton`, which is the
     * ported [WheelApplication.applySave]. The window reply packets are not
     * ported (PARITY GAP), so the command mutates the wheel state only.
     */
    fun playerSaveWheel(playerId: Long, command: SaveWheelCommand) {
        val player = getPlayerByID(playerId) ?: return

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        updateUIExhausted(player)
        wheelApplication.applySave(player.features.wheel, command)
    }

    /**
     * `Game::playerWheelGemAction` (src/game/game.cpp:11554): the parsed gem
     * command is dispatched to the ported `PlayerWheel` handlers through
     * [WheelApplication.applyGemAction]; the exhaustion gate wraps the dispatch
     * and the marker is refreshed afterwards. The reply packets are not ported
     * (PARITY GAP), so the command mutates the wheel state only.
     */
    fun playerWheelGemAction(playerId: Long, command: WheelGemActionCommand) {
        val player = getPlayerByID(playerId) ?: return

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        wheelApplication.applyGemAction(player.features.wheel, command, wheelPlayer(player))
        updateUIExhausted(player)
    }

    /**
     * `Game::getNpcByID` (src/game/game.cpp:1326): the `npcsIdIndex` registry
     * lookup. Until the spawn pipeline registers every placed NPC through
     * [addNpc] (PARITY GAP), ids missing from the registry fall back to the
     * placed-creature scan.
     */
    fun getNpcByID(npcId: Long): Npc? {
        if (npcId == 0L) {
            return null
        }

        npcsIdIndex[npcId]?.let { return npcs.getOrNull(it) }
        return placedCreatures.keys.firstOrNull { it is Npc && it.id == npcId } as? Npc
    }

    /**
     * `Game::playerNpcGreet` (src/game/game.cpp:10236): the exhaustion gate and
     * the hireling speech check run first, then the greeting is said to the
     * player and NPC spectators of the player's position; trade/sailor NPCs
     * answer with their private keyword. `Spectators` becomes the facade's
     * [spectatorsOf] plus the greeted NPC.
     */
    fun playerNpcGreet(playerId: Long, npcId: Long) {
        val player = getPlayerByID(playerId) ?: return

        val npc = getNpcByID(npcId) ?: return

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        val speechBubble = getNpcSpeechBubble(npc)
        if (!canSpeakWithHireling(player, speechBubble)) {
            return
        }

        val spectators = (spectatorsOf(player.position, multifloor = true).filterIsInstance<Player>() + npc).distinct()
        internalCreatureSay(player, SpeakClasses.TALKTYPE_SAY, "hi", false, spectators)

        val npcsSpectators = spectators.filterIsInstance<Npc>()

        if (speechBubble == SPEECHBUBBLE_TRADE) {
            internalCreatureSay(player, SpeakClasses.TALKTYPE_PRIVATE_PN, "trade", false, npcsSpectators)
        } else if (speechBubble == SPEECHBUBBLE_SAILOR) {
            internalCreatureSay(player, SpeakClasses.TALKTYPE_PRIVATE_PN, "sail", false, npcsSpectators)
        }

        updateUIExhausted(player)
    }

    /**
     * `Game::playerAnswerModalWindow` (src/game/game.cpp:11144): the player
     * lookup wraps the ported [ModalWindowManager.playerAnswerModalWindow]
     * (the offline-training branch and the `onModalWindow` creature events).
     */
    fun playerAnswerModalWindow(playerId: Long, modalWindowId: Long, button: Int, choice: Int) {
        val player = getPlayerByID(playerId) ?: return

        modalWindowManager?.playerAnswerModalWindow(player, modalWindowId, button, choice)
    }

    /**
     * `Item::getCustomAttribute` facade (`src/items/functions/item/custom_attribute.cpp`):
     * the Kotlin [Item] has no string-keyed custom attribute store (PARITY GAP),
     * so the podium attributes live in this identity map.
     */
    internal fun getItemCustomAttribute(item: Item, key: String): Long? = itemCustomAttributes[item]?.get(key)

    /** `Item::setCustomAttribute` facade (`src/items/functions/item/custom_attribute.cpp`). */
    internal fun setItemCustomAttribute(item: Item, key: String, value: Long) {
        itemCustomAttributes.getOrPut(item) { mutableMapOf() }[key] = value
    }

    /** `Item::removeCustomAttribute` facade (`src/items/functions/item/custom_attribute.cpp`). */
    internal fun removeItemCustomAttribute(item: Item, key: String) {
        itemCustomAttributes[item]?.remove(key)
    }

    /**
     * `Game::playerSetMonsterPodium` (src/game/game.cpp:11283): validates the
     * top-down podium item, walks the player into range when needed, stores the
     * monster race/outfit custom attributes and renames the podium. The
     * `Item::getCustomAttribute` store is the [getItemCustomAttribute] facade
     * and the monster registry is the [getMonsterTypeByRaceId] seam.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun playerSetMonsterPodium(
        playerId: Long,
        monsterRaceId: Int,
        pos: Position,
        stackPos: Int,
        itemId: Int,
        direction: Int,
        podiumAndMonsterVisible: Pair<Int, Int>,
    ) {
        val player = getPlayerByID(playerId) ?: return
        if (pos.x == 0xFFFF) {
            return
        }

        val item = internalGetThing(player, pos, stackPos, itemId, StackPosType_t.STACKPOS_TOPDOWN_ITEM) ?: return

        if (item.id != itemId || item.type?.isPodium != true || item.hasAttribute(ItemAttribute.UNIQUEID)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        val tile = item.getParent()?.getTile()
        if (tile == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (!areInRange(pos, player.position, 1, 1, 0)) {
            val listDir = getPathTo(player, pos, 0, 1, true, false)
            if (listDir != null) {
                if (!queuePlayerAutoWalk(player, listDir)) {
                    return
                }
                val task = PlayerTask(400, "Game::playerSetMonsterPodium") { playerBrowseFieldHandler(playerId, pos) }
                setNextWalkActionTask(player, task)
            } else {
                sendCancelMessage(player, ReturnValue.THEREISNOWAY)
            }
            return
        }

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        if (onlyInvitedCanMoveHouseItems && !playerCanUseItemOnHouseTile(player, item)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        var raceId = monsterRaceId
        if (monsterRaceId != 0) {
            setItemCustomAttribute(item, "PodiumMonsterRaceId", monsterRaceId.toLong())
        } else {
            raceId = (getItemCustomAttribute(item, "PodiumMonsterRaceId") ?: 0L).toInt()
        }

        val mType = getMonsterTypeByRaceId(raceId, itemId == ITEM_PODIUM_OF_VIGOUR)
        if (mType == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            logger.debug { "[playerSetMonsterPodium] player ${player.name} is trying to add invalid monster to podium" }
            return
        }

        val (podiumVisible, monsterVisible) = podiumAndMonsterVisible
        var changeTentuglyName = false
        val monsterOutfit = mType.info.outfit.copy()
        if ((monsterOutfit.lookType != 0 || monsterOutfit.lookTypeEx != 0) && monsterVisible != 0) {
            // "Tantugly's Head" boss have to send other looktype to the podium
            if (monsterOutfit.lookTypeEx == TENTUGLY_HEAD_LOOK_TYPE_EX) {
                monsterOutfit.lookTypeEx = TENTUGLY_PODIUM_LOOK_TYPE_EX
                changeTentuglyName = true
            }
            setItemCustomAttribute(item, "LookTypeEx", monsterOutfit.lookTypeEx.toLong())
            setItemCustomAttribute(item, "LookType", monsterOutfit.lookType.toLong())
            setItemCustomAttribute(item, "LookHead", monsterOutfit.lookHead.toLong())
            setItemCustomAttribute(item, "LookBody", monsterOutfit.lookBody.toLong())
            setItemCustomAttribute(item, "LookLegs", monsterOutfit.lookLegs.toLong())
            setItemCustomAttribute(item, "LookFeet", monsterOutfit.lookFeet.toLong())
            setItemCustomAttribute(item, "LookAddons", monsterOutfit.lookAddons.toLong())
        } else {
            removeItemCustomAttribute(item, "LookType")
        }

        setItemCustomAttribute(item, "PodiumVisible", podiumVisible.toLong())
        setItemCustomAttribute(item, "LookDirection", direction.toLong())
        setItemCustomAttribute(item, "MonsterVisible", monsterVisible.toLong())

        item.removeAttribute(ItemAttribute.NAME)
        if (monsterVisible != 0) {
            val name = StringBuilder(item.getName())
            name.append(" displaying ")
            if (changeTentuglyName) {
                name.append("Tentugly")
            } else {
                name.append(mType.name)
            }
            item.setAttribute(ItemAttribute.NAME, name.toString())
        }

        for (spectator in spectatorsOf(pos, multifloor = true)) {
            val playerSpectator = spectator as? Player ?: continue
            sendUpdateTileItem(playerSpectator, tile, pos, item)
        }

        updateUIExhausted(player)
    }

    /**
     * `Game::playerRotatePodium` (src/game/game.cpp:11395): rotates a bestiary
     * podium by re-running [playerSetMonsterPodium] with the next direction, or
     * hands the stored outfit of a renown podium to [playerSetShowOffSocket].
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun playerRotatePodium(playerId: Long, pos: Position, stackPos: Int, itemId: Int) {
        val player = getPlayerByID(playerId) ?: return

        val item = internalGetThing(player, pos, stackPos, itemId, StackPosType_t.STACKPOS_TOPDOWN_ITEM) ?: return

        if (item.id != itemId || item.hasAttribute(ItemAttribute.UNIQUEID)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (pos.x != 0xFFFF && !areInRange(pos, player.position, 1, 1, 0)) {
            val listDir = getPathTo(player, pos, 0, 1, true, true)
            if (listDir != null) {
                if (!queuePlayerAutoWalk(player, listDir)) {
                    return
                }
                val task = PlayerTask(400, "Game::playerRotatePodium") {
                    playerRotatePodium(playerId, pos, stackPos, itemId)
                }
                setNextWalkActionTask(player, task)
            } else {
                sendCancelMessage(player, ReturnValue.THEREISNOWAY)
            }
            return
        }

        if (onlyInvitedCanMoveHouseItems && !playerCanUseItemOnHouseTile(player, item)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        val podiumRaceId = (getItemCustomAttribute(item, "PodiumMonsterRaceId") ?: 0L).toInt() and 0xFFFF
        val lookDirection = getItemCustomAttribute(item, "LookDirection")
        val directionValue = if (lookDirection != null) {
            if (lookDirection >= 3) 0 else (lookDirection + 1).toInt()
        } else {
            2
        }
        val isPodiumVisible = (getItemCustomAttribute(item, "PodiumVisible") ?: 0L) != 0L
        val isMonsterVisible = (getItemCustomAttribute(item, "MonsterVisible") ?: 0L) != 0L

        // Rotate monster podium (bestiary or bosstiary) to the new direction
        val isPodiumOfRenown = itemId == ITEM_PODIUM_OF_RENOWN1 || itemId == ITEM_PODIUM_OF_RENOWN2
        if (!isPodiumOfRenown) {
            val lookTypeEx = getItemCustomAttribute(item, "LookTypeEx")
            if (!isMonsterVisible || podiumRaceId == 0 ||
                (lookTypeEx != null && lookTypeEx == TENTUGLY_PODIUM_LOOK_TYPE_EX.toLong())
            ) {
                sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                return
            }

            playerSetMonsterPodium(
                playerId,
                podiumRaceId,
                pos,
                stackPos,
                itemId,
                directionValue,
                (if (isPodiumVisible) 1 else 0) to (if (isMonsterVisible) 1 else 0),
            )
            return
        }

        val newOutfit = Outfit()
        newOutfit.lookType = (getItemCustomAttribute(item, "LookType") ?: 0L).toInt()
        newOutfit.lookAddons = (getItemCustomAttribute(item, "LookAddons") ?: 0L).toInt()
        newOutfit.lookHead = (getItemCustomAttribute(item, "LookHead") ?: 0L).toInt()
        newOutfit.lookBody = (getItemCustomAttribute(item, "LookBody") ?: 0L).toInt()
        newOutfit.lookLegs = (getItemCustomAttribute(item, "LookLegs") ?: 0L).toInt()
        newOutfit.lookFeet = (getItemCustomAttribute(item, "LookFeet") ?: 0L).toInt()

        newOutfit.lookMount = (getItemCustomAttribute(item, "LookMount") ?: 0L).toInt()
        newOutfit.lookMountHead = (getItemCustomAttribute(item, "LookMountHead") ?: 0L).toInt()
        newOutfit.lookMountBody = (getItemCustomAttribute(item, "LookMountBody") ?: 0L).toInt()
        newOutfit.lookMountLegs = (getItemCustomAttribute(item, "LookMountLegs") ?: 0L).toInt()
        newOutfit.lookMountFeet = (getItemCustomAttribute(item, "LookMountFeet") ?: 0L).toInt()
        if (newOutfit.lookType == 0 && newOutfit.lookMount == 0) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        playerSetShowOffSocket(
            player.id,
            newOutfit,
            pos,
            stackPos,
            itemId,
            if (isPodiumVisible) 1 else 0,
            directionValue,
        )
    }

    /**
     * `Game::playerSetShowOffSocket` (src/game/game.cpp:5227): validates the
     * top-down renown-podium item, walks the player into range, validates the
     * outfit (`Player::canWear`) and mount (`Mounts::getMountByClientID` +
     * `Player::hasMount`), stores the `PastLookType`/`PastLookMount` custom
     * attributes, renames the podium and fans the tile-item update out to every
     * player spectator.
     *
     * The `outfit` argument is mutated exactly like the C++ reference: a
     * refused look type/addon pair and an unknown or unowned mount are zeroed
     * in place. `Item::getCustomAttribute` lives in [getItemCustomAttribute],
     * `Outfits::getInstance().getOutfitByLookType` in [getOutfitByLookType] and
     * `Player::canWear` in [playerCanWear].
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun playerSetShowOffSocket(
        playerId: Long,
        outfit: Outfit,
        pos: Position,
        stackPos: Int,
        itemId: Int,
        podiumVisible: Int,
        direction: Int,
    ) {
        val player = getPlayerByID(playerId) ?: return
        if (pos.x == 0xFFFF) {
            return
        }

        val item = internalGetThing(player, pos, stackPos, itemId, StackPosType_t.STACKPOS_TOPDOWN_ITEM) ?: return

        if (item.id != itemId || item.type?.isPodium != true || item.hasAttribute(ItemAttribute.UNIQUEID)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (item.hasOwner() && !item.isOwner(player)) {
            sendCancelMessage(player, ReturnValue.ITEMISNOTYOURS)
            return
        }

        if (onlyInvitedCanMoveHouseItems && !playerCanUseItemOnHouseTile(player, item)) {
            sendCancelMessage(player, ReturnValue.CANNOTUSETHISOBJECT)
            return
        }

        val tile = item.getParent()?.getTile()
        if (tile == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (!areInRange(pos, player.position, 1, 1, 0)) {
            val listDir = getPathTo(player, pos, 0, 1, true, false)
            if (listDir != null) {
                if (!queuePlayerAutoWalk(player, listDir)) {
                    return
                }
                val task = PlayerTask(400, "Game::playerSetShowOffSocket") { playerBrowseFieldHandler(playerId, pos) }
                setNextWalkActionTask(player, task)
            } else {
                sendCancelMessage(player, ReturnValue.THEREISNOWAY)
            }
            return
        }

        if (onlyInvitedCanMoveHouseItems && !playerCanUseItemOnHouseTile(player, item)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (outfit.lookType != 0) {
            setItemCustomAttribute(item, "PastLookType", outfit.lookType.toLong())
        }

        if (outfit.lookMount != 0) {
            setItemCustomAttribute(item, "PastLookMount", outfit.lookMount.toLong())
        }

        if (!playerCanWear(player, outfit.lookType, outfit.lookAddons)) {
            outfit.lookType = 0
            outfit.lookAddons = 0
        }

        val mount = getMountByClientID(outfit.lookMount)
        if (mount == null || !player.features.mounts.hasMount(mount) || player.isWearingSupportOutfit()) {
            outfit.lookMount = 0
        }

        if (outfit.lookType != 0) {
            setItemCustomAttribute(item, "LookType", outfit.lookType.toLong())
            setItemCustomAttribute(item, "LookHead", outfit.lookHead.toLong())
            setItemCustomAttribute(item, "LookBody", outfit.lookBody.toLong())
            setItemCustomAttribute(item, "LookLegs", outfit.lookLegs.toLong())
            setItemCustomAttribute(item, "LookFeet", outfit.lookFeet.toLong())
            setItemCustomAttribute(item, "LookAddons", outfit.lookAddons.toLong())
        } else if ((getItemCustomAttribute(item, "PastLookType") ?: 0L) > 0L) {
            removeItemCustomAttribute(item, "LookType")
            removeItemCustomAttribute(item, "PastLookType")
        }

        if (outfit.lookMount != 0) {
            setItemCustomAttribute(item, "LookMount", outfit.lookMount.toLong())
            setItemCustomAttribute(item, "LookMountHead", outfit.lookMountHead.toLong())
            setItemCustomAttribute(item, "LookMountBody", outfit.lookMountBody.toLong())
            setItemCustomAttribute(item, "LookMountLegs", outfit.lookMountLegs.toLong())
            setItemCustomAttribute(item, "LookMountFeet", outfit.lookMountFeet.toLong())
        } else if ((getItemCustomAttribute(item, "PastLookMount") ?: 0L) > 0L) {
            removeItemCustomAttribute(item, "LookMount")
            removeItemCustomAttribute(item, "PastLookMount")
        }

        setItemCustomAttribute(item, "PodiumVisible", podiumVisible.toLong())
        setItemCustomAttribute(item, "LookDirection", direction.toLong())

        // Change Podium name
        if (outfit.lookType != 0 || outfit.lookMount != 0) {
            val name = StringBuilder()
            name.append(item.getName())
            name.append(" displaying the ")
            var outfited = false
            if (outfit.lookType != 0) {
                val outfitInfo = getOutfitByLookType(player, outfit.lookType)
                if (outfitInfo == null) {
                    return
                }

                name.append(outfitInfo.name)
                name.append(" outfit")
                outfited = true
            }

            if (outfit.lookMount != 0) {
                if (outfited) {
                    name.append(" on the ")
                }
                name.append(mount?.name)
                name.append(" mount")
            }
            item.setAttribute(ItemAttribute.NAME, name.toString())
        } else {
            item.removeAttribute(ItemAttribute.NAME)
        }

        // Send to client
        for (spectator in spectatorsOf(pos, multifloor = true)) {
            val playerSpectator = spectator as? Player ?: continue
            sendUpdateTileItem(playerSpectator, tile, pos, item)
        }
    }

    /**
     * `Game::playerApplyImbuement` (src/game/game.cpp:7116): the exhaustion gate
     * runs first, unknown imbuements are ignored, a player without an imbuing
     * item receives the scroll imbuement, and an imbuing item that left the
     * player reports the protocol error instead of applying.
     */
    fun playerApplyImbuement(playerId: Long, imbuementid: Int, slot: Int) {
        val player = getPlayerByID(playerId) ?: return

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        updateUIExhausted(player)

        val imbuement = imbuements?.getImbuement(imbuementid) ?: return

        if (!player.hasImbuingItem()) {
            createScrollImbuement(player, imbuement)
            return
        }

        val item = getImbuingItem(player) ?: return

        if (!isImbuingItemCarriedByPlayer(player, item)) {
            logger.error {
                "[playerApplyImbuement] - An error occurred while player with name ${player.name} try to apply imbuement"
            }
            sendImbuementResult(
                player,
                "An error has occurred, reopen the imbuement window. If the problem persists, contact your administrator.",
            )
            return
        }

        onApplyImbuement(player, imbuement, item, slot)
    }

    /**
     * `Game::playerClearImbuement` (src/game/game.cpp:7153): the exhaustion gate
     * runs first and the carried imbuing item must exist before
     * `Player::onClearImbuement` clears the slot.
     */
    fun playerClearImbuement(playerid: Long, slot: Int) {
        val player = getPlayerByID(playerid) ?: return

        if (isUIExhausted(player)) {
            sendCancelMessage(player, ReturnValue.YOUAREEXHAUSTED)
            return
        }

        updateUIExhausted(player)

        if (!player.hasImbuingItem()) {
            return
        }

        val item = getImbuingItem(player) ?: return

        onClearImbuement(player, item, slot)
    }

    /**
     * `Game::playerRequestInventoryImbuements` (src/game/game.cpp:11487): the
     * tracker flag is stored first, a closed tracker sends nothing, and an open
     * tracker collects every equipped inventory item. The C++
     * `Item::getImbuementInfo` validation loop has no observable effect on the
     * collected map.
     */
    fun playerRequestInventoryImbuements(playerId: Long, isTrackerOpen: Boolean) {
        val player = getPlayerByID(playerId) ?: return
        if (player.isRemoved()) {
            return
        }

        imbuementTrackerWindowOpen[player] = isTrackerOpen
        if (!isTrackerOpen) {
            return
        }

        val itemsWithImbueSlotMap = linkedMapOf<Slots_t, Item>()
        for (inventorySlotValue in Slots_t.FIRST..Slots_t.LAST) {
            val inventorySlot = Slots_t.fromValue(inventorySlotValue) ?: continue
            val item = player.getInventoryItem(inventorySlot) ?: continue
            itemsWithImbueSlotMap[inventorySlot] = item
        }

        sendInventoryImbuements(player, itemsWithImbueSlotMap)
    }

    /** `Game::playerToggleMount` (src/game/game.cpp:7219): direct `Player::toggleMount` delegate. */
    fun playerToggleMount(playerId: Long, mount: Boolean) {
        val player = getPlayerByID(playerId) ?: return

        toggleMount(player, mount)
    }

    /**
     * `Game::playerRequestOutfit` (src/game/game.cpp:7206): gated by
     * `ALLOW_CHANGEOUTFIT`, otherwise the outfit window is sent.
     */
    fun playerRequestOutfit(playerId: Long) {
        if (!allowChangeOutfit) {
            return
        }

        val player = getPlayerByID(playerId) ?: return

        sendOutfitWindow(player)
    }

    /** `Game::playerReceivePing` (src/game/game.cpp:4454): direct `Player::receivePing` delegate. */
    fun playerReceivePing(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return

        receivePing(player)
    }

    /** `Game::playerReceivePingBack` (src/game/game.cpp:4463): direct `Player::sendPingBack` delegate. */
    fun playerReceivePingBack(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return

        sendPingBack(player)
    }

    /**
     * `Game::playerCloseNpcChannel` (src/game/game.cpp:4443): every NPC on the
     * player's floor receives `Npc::onPlayerCloseChannel`.
     */
    fun playerCloseNpcChannel(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return

        for (spectator in spectatorsOf(player.position, multifloor = false)) {
            val npc = spectator as? Npc ?: continue
            npc.onPlayerCloseChannel(player)
        }
    }

    /**
     * `Game::playerBroadcastMessage` (src/game/game.cpp:4310): requires
     * `CanBroadcast` and sends `TALKTYPE_BROADCAST` to every registered player.
     */
    fun playerBroadcastMessage(player: Player, text: String): Boolean {
        if (!player.hasFlag(PlayerFlags_t.CanBroadcast)) {
            return false
        }

        logger.info { "${player.name} broadcasted: $text" }

        for (target in players.values) {
            sendPrivateMessage(target, player, SpeakClasses.TALKTYPE_BROADCAST, text)
        }

        return true
    }

    // --- monster registry (game.cpp:11671-11706, 1309) --------------------------

    /**
     * `Game::getMonsterByID` (src/game/game.cpp:1309): monsters registered by
     * [addMonster]; unknown or zero ids resolve to `null`.
     */
    fun getMonsterByID(id: Long): Monster? {
        if (id == 0L) {
            return null
        }

        val index = monstersIdIndex[id] ?: return null
        if (index >= monsters.size) {
            return null
        }

        return monsters[index]
    }

    /**
     * `Game::addMonster` (src/game/game.cpp:11671). Canary registers a monster
     * from its constructor; the Kotlin [Monster] does not self-register yet
     * (PARITY GAP), so hosts call this explicitly.
     */
    fun addMonster(monster: Monster?) {
        if (monster == null) {
            return
        }

        val lowerName = monster.name.lowercase()
        monsters.add(monster)
        val index = monsters.size - 1
        monstersNameIndex[lowerName] = index
        monstersIdIndex[monster.id] = index
    }

    /** `Game::removeMonster` (src/game/game.cpp:11683): swap-with-last removal. */
    fun removeMonster(monster: Monster?) {
        if (monster == null) {
            return
        }

        val monsterId = monster.id
        val monsterLowerName = monster.name.lowercase()
        val index = monstersIdIndex[monsterId] ?: return
        monstersNameIndex.remove(monsterLowerName)
        monstersIdIndex.remove(monsterId)

        if (index != monsters.size - 1) {
            monsters[index] = monsters[monsters.size - 1]
            val movedMonster = monsters[index]
            monstersNameIndex[movedMonster.name.lowercase()] = index
            monstersIdIndex[movedMonster.id] = index
        }

        monsters.removeAt(monsters.size - 1)
    }

    // --- fiendish/influenced forge events (game.cpp:11882-12229) ----------------

    /** `Game::getFiendishMonsters` (game.hpp:676): a snapshot copy, exactly like the C++ `auto` return. */
    fun getFiendishMonsters(): Set<Long> = fiendishMonsters.toSet()

    /** `Game::getInfluencedMonsters` (game.hpp:680): a snapshot copy. */
    fun getInfluencedMonsters(): Set<Long> = influencedMonsters.toSet()

    /**
     * `Game::makeInfluencedMonster` (src/game/game.cpp:11882): draws forgeable
     * monsters until a monster without a forge stack is found, classifies it as
     * influenced and registers it; returns the monster id or `0`.
     */
    fun makeInfluencedMonster(): Long {
        if (forgeableMonsters.isEmpty() || influencedMonsters.size >= featureFlags.forgeInfluencedCreaturesLimit) {
            return 0
        }

        val maxTries = forgeableMonsters.size
        var tries = 0
        var monster: Monster? = null
        while (true) {
            if (tries == maxTries) {
                return 0
            }

            tries++

            val random = normalRandom(0, forgeableMonsters.size - 1)
            val monsterId = forgeableMonsters[random]
            monster = getMonsterByID(monsterId)
            if (monster == null) {
                continue
            }

            // Avoiding replace forgeable monster with another
            if (getForgeStack(monster) == 0) {
                val index = forgeableMonsters.indexOf(monsterId)
                if (index == -1) {
                    monster = null
                    continue
                }
                forgeableMonsters.removeAt(index)
                break
            }
        }

        val selected = monster
        if (canBeForgeMonster(selected)) {
            setMonsterForgeClassification(selected, ForgeClassification.Influenced)
            configureForgeSystem(selected, 0)
            influencedMonsters.add(selected.id)
            return selected.id
        }

        return 0
    }

    /**
     * `Game::makeFiendishMonster` (src/game/game.cpp:11928): optionally rebuilds
     * the forgeable list (evicting lost fiendish ids and making room at the
     * limit), draws a monster, classifies it as fiendish and schedules
     * [updateFiendishMonsterStatus] after the configured interval.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun makeFiendishMonster(forgeableMonsterId: Long = 0, createForgeableMonsters: Boolean = false): Long {
        if (createForgeableMonsters) {
            forgeableMonsters.clear()
            // If the forgeable monsters haven't been created
            // Then we'll create them so they don't return in the next if (forgeableMonsters.empty())
            for (monster in monsters) {
                val monsterTile = monster.getTile() ?: continue
                if (canBeForgeMonster(monster) && !monsterTile.hasFlag(TileFlags.TILESTATE_NOLOGOUT.value)) {
                    forgeableMonsters.add(monster.id)
                }
            }
            for (monsterId in getFiendishMonsters()) {
                // If the fiendish is no longer on the map, we remove it from the vector
                val monster = getMonsterByID(monsterId)
                if (monster == null) {
                    removeFiendishMonster(monsterId)
                    continue
                }

                // If you're trying to create a new fiendish and it's already max size, let's remove one of them
                if (getFiendishMonsters().size >= featureFlags.forgeFiendishCreaturesLimit) {
                    clearFiendishStatus(monster)
                    removeFiendishMonster(monsterId)
                    break
                }
            }
        }

        if (forgeableMonsters.isEmpty() || fiendishMonsters.size >= featureFlags.forgeFiendishCreaturesLimit) {
            return 0
        }

        val maxTries = forgeableMonsters.size
        var tries = 0
        var monster: Monster? = null
        while (true) {
            if (tries == maxTries) {
                return 0
            }

            tries++

            val random = forgeRandom.uniformRandom(0, forgeableMonsters.size - 1)
            var fiendishMonsterId = forgeableMonsterId
            if (fiendishMonsterId == 0L) {
                fiendishMonsterId = forgeableMonsters[random]
            }
            monster = getMonsterByID(fiendishMonsterId)
            if (monster == null) {
                continue
            }

            // Avoiding replace forgeable monster with another
            if (getForgeStack(monster) == 0) {
                val index = forgeableMonsters.indexOf(fiendishMonsterId)
                if (index == -1) {
                    monster = null
                    continue
                }
                forgeableMonsters.removeAt(index)
                break
            }
        }

        // Get interval time to fiendish
        val saveIntervalType = featureFlags.forgeFiendishIntervalType
        val saveIntervalConfigTime = featureFlags.forgeFiendishIntervalTime.toIntOrNull() ?: 0
        var intervalTime = 0
        val timeToChangeFiendish: Long
        when (saveIntervalType) {
            "second" -> {
                intervalTime = 1000
                timeToChangeFiendish = 1
            }
            "minute" -> {
                intervalTime = 60 * 1000
                timeToChangeFiendish = 60
            }
            "hour" -> {
                intervalTime = 60 * 60 * 1000
                timeToChangeFiendish = 3600
            }
            else -> {
                timeToChangeFiendish = 3600
            }
        }

        val finalTime = if (intervalTime == 0) {
            logger.warn { "Fiendish interval type is wrong, setting default time to 1h" }
            3600L * 1000
        } else {
            saveIntervalConfigTime.toLong() * intervalTime
        }

        val selected = monster
        if (canBeForgeMonster(selected)) {
            setMonsterForgeClassification(selected, ForgeClassification.Fiendish)
            configureForgeSystem(selected, 0)
            setTimeToChangeFiendish(selected, timeToChangeFiendish + getTimeNow())
            fiendishMonsters.add(selected.id)

            val monsterId = selected.id
            val monsterName = selected.name
            forgeMonsterEventIds[monsterId] = scheduleEvent(finalTime) {
                updateFiendishMonsterStatus(monsterId, monsterName)
            }
            return monsterId
        }

        return 0
    }

    /**
     * `Game::updateFiendishMonsterStatus` (src/game/game.cpp:12045): clears the
     * fiendish status, unregisters the monster without scheduling a
     * replacement and rolls a new fiendish monster.
     */
    fun updateFiendishMonsterStatus(monsterId: Long, monsterName: String) {
        val monster = getMonsterByID(monsterId)
        if (monster == null) {
            logger.warn {
                "[updateFiendishMonsterStatus] Failed to update monster with id $monsterId and name $monsterName, monster not found"
            }
            return
        }

        clearFiendishStatus(monster)
        removeFiendishMonster(monsterId, false)
        makeFiendishMonster()
    }

    /** `Game::removeForgeMonster` (src/game/game.cpp:12057): dispatch by classification; always returns true. */
    fun removeForgeMonster(
        id: Long,
        monsterForgeClassification: ForgeClassification,
        create: Boolean = true,
    ): Boolean {
        when (monsterForgeClassification) {
            ForgeClassification.Fiendish -> removeFiendishMonster(id, create)
            ForgeClassification.Influenced -> removeInfluencedMonster(id, create)
            ForgeClassification.Normal -> Unit
        }

        return true
    }

    /** `Game::removeInfluencedMonster` (src/game/game.cpp:12067). */
    fun removeInfluencedMonster(id: Long, create: Boolean = false): Boolean {
        if (influencedMonsters.remove(id)) {
            if (create) {
                scheduleEvent(10L * 1000) { makeInfluencedMonster() }
            }
        } else {
            logger.warn {
                "[removeInfluencedMonster] - Failed to remove a Influenced Monster, error code: monster id not exist in the influenced monsters map"
            }
        }
        return false
    }

    /** `Game::removeFiendishMonster` (src/game/game.cpp:12084). */
    fun removeFiendishMonster(id: Long, create: Boolean = true): Boolean {
        if (fiendishMonsters.remove(id)) {
            checkForgeEventId(id)

            if (create) {
                scheduleEvent(270L * 1000) { makeFiendishMonster(0, false) }
            }
        } else {
            logger.warn {
                "[removeFiendishMonster] - Failed to remove a Fiendish Monster, error code: monster id not exist in the fiendish monsters map"
            }
        }

        return false
    }

    /**
     * `Game::updateForgeableMonsters` (src/game/game.cpp:12103): rebuilds the
     * forgeable list from the live monsters, prunes lost fiendish/influenced
     * ids and refills both classifications up to their configured limits.
     */
    fun updateForgeableMonsters() {
        forgeableMonsters.clear()
        for (monster in monsters) {
            val monsterTile = monster.getTile() ?: continue
            if (canBeForgeMonster(monster) && !monsterTile.hasFlag(TileFlags.TILESTATE_NOLOGOUT.value)) {
                forgeableMonsters.add(monster.id)
            }
        }

        for (monsterId in getFiendishMonsters()) {
            if (getMonsterByID(monsterId) == null) {
                removeFiendishMonster(monsterId)
            }
        }

        for (monsterId in getInfluencedMonsters()) {
            if (getMonsterByID(monsterId) == null) {
                removeInfluencedMonster(monsterId, false)
            }
        }

        if (fiendishMonsters.size < featureFlags.forgeFiendishCreaturesLimit) {
            createFiendishMonsters()
        }

        if (influencedMonsters.size < featureFlags.forgeInfluencedCreaturesLimit) {
            createInfluencedMonsters()
        }
    }

    /** `Game::createFiendishMonsters` (src/game/game.cpp:12139), including the no-progress abort. */
    fun createFiendishMonsters() {
        val fiendishLimit = featureFlags.forgeFiendishCreaturesLimit
        if (fiendishMonsters.size >= fiendishLimit) {
            return
        }

        var noProgressAttempts = 0
        val remaining = fiendishLimit - fiendishMonsters.size
        var maxAttemptsWithoutProgress = remaining * 4
        if (maxAttemptsWithoutProgress < 25) {
            maxAttemptsWithoutProgress = 25
        }

        while (fiendishMonsters.size < fiendishLimit) {
            val previousSize = fiendishMonsters.size

            makeFiendishMonster()

            if (fiendishMonsters.size > previousSize) {
                noProgressAttempts = 0
                continue
            }

            noProgressAttempts++
            if (noProgressAttempts >= maxAttemptsWithoutProgress) {
                logger.warn {
                    "[createFiendishMonsters] - Aborting fiendish creation due to no progress. " +
                        "Size: ${fiendishMonsters.size}, max: $fiendishLimit, attempts: $noProgressAttempts."
                }
                return
            }
        }
    }

    /** `Game::createInfluencedMonsters` (src/game/game.cpp:12170), including the no-progress abort. */
    fun createInfluencedMonsters() {
        val influencedLimit = featureFlags.forgeInfluencedCreaturesLimit
        if (influencedMonsters.size >= influencedLimit) {
            return
        }

        var noProgressAttempts = 0
        val remaining = influencedLimit - influencedMonsters.size
        var maxAttemptsWithoutProgress = remaining * 4
        if (maxAttemptsWithoutProgress < 25) {
            maxAttemptsWithoutProgress = 25
        }

        while (influencedMonsters.size < influencedLimit) {
            val previousSize = influencedMonsters.size

            makeInfluencedMonster()

            if (influencedMonsters.size > previousSize) {
                noProgressAttempts = 0
                continue
            }

            noProgressAttempts++
            if (noProgressAttempts >= maxAttemptsWithoutProgress) {
                logger.warn {
                    "[createInfluencedMonsters] - Aborting influenced creation due to no progress. " +
                        "Size: ${influencedMonsters.size}, max: $influencedLimit, attempts: $noProgressAttempts."
                }
                return
            }
        }
    }

    /** `Game::checkForgeEventId` (src/game/game.cpp:12201): stops and forgets the scheduled event. */
    fun checkForgeEventId(monsterId: Long) {
        val eventId = forgeMonsterEventIds.remove(monsterId) ?: return
        stopEvent(eventId)
    }

    /**
     * `Game::addInfluencedMonster` (src/game/game.cpp:12209): prunes lost ids,
     * enforces the influenced limit for new ids only, then classifies,
     * configures and registers the monster.
     */
    fun addInfluencedMonster(monster: Monster?, stack: Int = 0): Boolean {
        if (monster != null && canBeForgeMonster(monster)) {
            influencedMonsters.removeAll { getMonsterByID(it) == null }

            val monsterId = monster.id
            val alreadyInfluenced = influencedMonsters.contains(monsterId)
            if (!alreadyInfluenced && (influencedMonsters.size + 1) > featureFlags.forgeInfluencedCreaturesLimit) {
                return false
            }

            setMonsterForgeClassification(monster, ForgeClassification.Influenced)
            configureForgeSystem(monster, stack)
            influencedMonsters.add(monsterId)
            return true
        }
        return false
    }

    // --- wheel of destiny combat application (game.cpp:8264-8325, 12347) --------

    /**
     * `Game::applyWheelOfDestinyHealing` (src/game/game.cpp:8264): scales the
     * primary value by the healing multiplier, adds the attacker's wheel
     * `HEALING` stat to both values, emits the `healingLink` self-heal through
     * the [combatChangeHealth] seam and applies the `Blessing of the Grove`
     * target bonus.
     */
    fun applyWheelOfDestinyHealing(damage: CombatDamage, attackerPlayer: Player?, target: Creature?) {
        damage.primaryValue += ((damage.primaryValue * damage.healingMultiplier) / 100.0).toInt()

        if (attackerPlayer != null) {
            damage.primaryValue += attackerPlayer.wheel().getStat(WheelStat.HEALING)

            if (damage.secondaryValue != 0) {
                damage.secondaryValue += attackerPlayer.wheel().getStat(WheelStat.HEALING)
            }

            if (damage.healingLink > 0) {
                val tmpDamage = CombatDamage()
                tmpDamage.primaryValue = (damage.primaryValue * damage.healingLink) / 100
                tmpDamage.primaryType = CombatType.HEALING
                combatChangeHealth(attackerPlayer, attackerPlayer, tmpDamage, false)
            }

            if (attackerPlayer.wheel().getInstant("Blessing of the Grove")) {
                val healingBonus = attackerPlayer.wheel().checkBlessingGroveHealingByTarget(target)
                damage.primaryValue += ((damage.primaryValue * healingBonus) / 100.0).toInt()
            }
        }
    }

    /**
     * `Game::applyWheelOfDestinyEffectsToDamage` (src/game/game.cpp:8287): the
     * damage multiplier, the attacker's wheel `DAMAGE` stat and the
     * Twin Burst / Executioner's Throw / Divine Grenade instant-spell bonuses.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun applyWheelOfDestinyEffectsToDamage(damage: CombatDamage, attackerPlayer: Player?, target: Creature?) {
        // If damage is 0, it means the target is immune to the damage type, or that we missed.
        if (damage.primaryValue == 0 && damage.secondaryValue == 0) {
            return
        }

        if (damage.damageMultiplier > 0) {
            damage.primaryValue += ((damage.primaryValue * damage.damageMultiplier) / 100.0).toInt()
            damage.secondaryValue += ((damage.secondaryValue * damage.damageMultiplier) / 100.0).toInt()
        }

        if (attackerPlayer != null) {
            damage.primaryValue -= attackerPlayer.wheel().getStat(WheelStat.DAMAGE)
            if (damage.secondaryValue != 0) {
                damage.secondaryValue -= attackerPlayer.wheel().getStat(WheelStat.DAMAGE)
            }
            if (damage.instantSpellName == "Ice Burst" || damage.instantSpellName == "Terra Burst") {
                val damageBonus = attackerPlayer.wheel().checkTwinBurstByTarget(target)
                if (damageBonus != 0) {
                    damage.primaryValue += ((damage.primaryValue * damageBonus) / 100.0).toInt()
                    damage.secondaryValue += ((damage.secondaryValue * damageBonus) / 100.0).toInt()
                }
            }
            if (damage.instantSpellName == "Executioner's Throw") {
                val damageBonus = attackerPlayer.wheel().checkExecutionersThrow(target)
                if (damageBonus != 0) {
                    damage.primaryValue += ((damage.primaryValue * damageBonus) / 100.0).toInt()
                    damage.secondaryValue += ((damage.secondaryValue * damageBonus) / 100.0).toInt()
                }
            }
            if (damage.instantSpellName == "Divine Grenade") {
                val damageBonus = attackerPlayer.wheel().checkDivineGrenade(target)
                if (damageBonus != 0) {
                    damage.primaryValue += ((damage.primaryValue * damageBonus) / 100.0).toInt()
                    damage.secondaryValue += ((damage.secondaryValue * damageBonus) / 100.0).toInt()
                }
            }
        }
    }

    /** `Game::getIOWheel` (src/game/game.cpp:12347). */
    fun getIOWheel(): IOWheel = ioWheel

    // --- game state / world type (game.cpp:922-928, game.hpp:136-140) -----------

    /** `Game::getGameState` (src/game/game.cpp:922). */
    fun getGameState(): GameState_t = gameState

    /**
     * `Game::setGameState` (src/game/game.cpp:952). Reduced to the state
     * assignment and the two guard clauses: the `GAME_STATE_INIT` world
     * bootstrap (map spawns, raids, mounts, MOTD/record load), the
     * `GAME_STATE_SHUTDOWN` save/kick-all and the `GAME_STATE_CLOSED` kick of
     * non-`CanAlwaysLogin` players are not wired (PARITY GAP).
     */
    fun setGameState(newState: GameState_t) {
        if (gameState == GameState_t.GAME_STATE_SHUTDOWN) {
            return
        }

        if (gameState == newState) {
            return
        }

        gameState = newState
    }

    /** `Game::setWorldType` (src/game/game.cpp:926). */
    fun setWorldType(type: WorldType) {
        worldType = type
    }

    /** `Game::getWorldType` (src/game/game.hpp:136-138). */
    fun getWorldType(): WorldType = worldType

    // --- creature name/id lookups (game.cpp:1356-1454, 11639) -------------------

    /**
     * `Game::getCreatureByName` (src/game/game.cpp:1356): online player, then
     * npc, then monster, all through the lowercase name indexes.
     */
    fun getCreatureByName(creatureName: String): Creature? {
        if (creatureName.isEmpty()) {
            return null
        }

        val lowerCaseName = creatureName.lowercase()
        players.values.firstOrNull { it.name.lowercase() == lowerCaseName }?.let { return it }
        npcsNameIndex[lowerCaseName]?.let { return npcs[it] }
        monstersNameIndex[lowerCaseName]?.let { return monsters[it] }
        return null
    }

    /** `Game::getNpcByName` (src/game/game.cpp:1380). */
    fun getNpcByName(npcName: String): Npc? {
        if (npcName.isEmpty()) {
            return null
        }

        val it = npcsNameIndex[npcName.lowercase()] ?: return null
        return npcs[it]
    }

    /** `Game::addNpc` (src/game/game.cpp:11639). */
    fun addNpc(npc: Npc?) {
        if (npc == null) {
            return
        }
        npcs.add(npc)
        val index = npcs.size - 1
        npcsNameIndex[npc.name.lowercase()] = index
        npcsIdIndex[npc.id] = index
    }

    /** `Game::removeNpc` (src/game/game.cpp:11646). */
    fun removeNpc(npc: Npc?) {
        if (npc == null) {
            return
        }

        val npcId = npc.id
        npcsNameIndex.remove(npc.name.lowercase())
        val it = npcsIdIndex.remove(npcId) ?: return

        if (it < npcs.size) {
            npcs[it] = npcs.last()
            val moved = npcs.removeAt(npcs.size - 1)
            if (npcs.isNotEmpty()) {
                npcsNameIndex[moved.name.lowercase()] = it
                npcsIdIndex[moved.id] = it
            }
        }
    }

    /** `Game::getNpcs` read view (game.hpp). */
    fun getNpcs(): List<Npc> = npcs.toList()

    /**
     * `Game::getPlayerNameByGUID` (src/game/game.cpp:1439) with the
     * `m_playerNameCache` memo; the `allowOffline` load goes through the
     * injected `loadPlayerById` seam.
     */
    fun getPlayerNameByGUID(guid: Long): String {
        if (guid == 0L) {
            return ""
        }
        playerNameCache[guid]?.let { return it }
        val name = getPlayerByGUID(guid, true)?.name ?: ""
        if (name.isNotEmpty()) {
            playerNameCache[guid] = name
        }
        return name
    }

    /**
     * `Game::getPlayerByNameWildcard` (src/game/game.cpp:1454). The C++
     * `player` out parameter becomes the second element of the returned pair.
     */
    fun getPlayerByNameWildcard(s: String): Pair<ReturnValue, Player?> {
        val length = s.length
        if (length == 0 || length > 29) {
            return ReturnValue.PLAYERWITHTHISNAMEISNOTONLINE to null
        }

        val player: Player?
        if (s.last() == '~') {
            val query = s.substring(0, length - 1).lowercase()
            val result = wildcardTree.findOne(query)
            if (result.value != ReturnValue.NOERROR) {
                return result.value to null
            }
            player = result.name?.let { getPlayerByName(it) }
        } else {
            player = getPlayerByName(s)
        }

        if (player == null) {
            return ReturnValue.PLAYERWITHTHISNAMEISNOTONLINE to null
        }

        return ReturnValue.NOERROR to player
    }

    /**
     * `Game::getPlayersByAccount` (src/game/game.cpp:1480): every account
     * character name resolved through [getPlayerByName] with the
     * `allowOffline` fallback.
     */
    fun getPlayersByAccount(acc: Account, allowOffline: Boolean = false): List<Player> {
        val (accountPlayers, error) = acc.getAccountPlayers()
        if (error != AccountErrors.Ok) {
            return emptyList()
        }

        val ret = mutableListOf<Player>()
        for ((name, _) in accountPlayers) {
            getPlayerByName(name, allowOffline)?.let { ret.add(it) }
        }
        return ret
    }

    // --- dead player registry (game.cpp:11607-11621) -----------------------------

    /** `Game::addDeadPlayer` (src/game/game.cpp:11607). */
    fun addDeadPlayer(player: Player) {
        deadPlayers[player.name] = WeakReference(player)
    }

    /** `Game::removeDeadPlayer` (src/game/game.cpp:11611). */
    fun removeDeadPlayer(playerName: String) {
        deadPlayers.remove(playerName)
    }

    /** `Game::getDeadPlayer` (src/game/game.cpp:11615). */
    fun getDeadPlayer(playerName: String): Player? = deadPlayers[playerName]?.get()

    // --- combat state-change pipeline (game.cpp:8193-9080) -----------------------

    /** `Creature::isAttackable()` (src/creatures/creature.cpp); seam for combat. */
    var isAttackable: (Creature) -> Boolean = { true }

    /** `Player::getSkullClient(creature)` (player.cpp:7127); default reads the attached PvP component. */
    var getSkullClient: (Player, Creature) -> Skulls_t = { player, creature ->
        player.pvpPlayer.getSkullClient(creature)
    }

    /** `Player::updateImpactTracker(type, amount)` (player.cpp) protocol/stats seam. */
    var updateImpactTracker: (Player, CombatType, Int) -> Unit = { _, _, _ -> }

    /** `Party::addPlayerHealing(player, amount)` (party.cpp:...) seam for the party hunt analyzer. */
    var addPlayerHealing: (Player, Int) -> Unit = { _, _ -> }

    /** `Party::addPlayerDamage(player, amount)` (party.cpp:...) seam for the party hunt analyzer. */
    var addPlayerDamage: (Player, Int) -> Unit = { _, _ -> }

    /**
     * `CreatureEvent::executeHealthChange` dispatch seam (game.cpp:8361-8368):
     * returns true when at least one listener ran, in which case the C++
     * recursion repeats the hit with `ORIGIN_NONE`.
     */
    var executeCreatureHealthChangeEvents: (Creature, Creature?, CombatDamage) -> Boolean = { _, _, _ -> false }

    /**
     * `CreatureEvent::executeManaChange` dispatch seam (game.cpp:9106-9115):
     * returns true when at least one listener ran.
     */
    var executeCreatureManaChangeEvents: (Creature, Creature?, CombatDamage) -> Boolean = { _, _, _ -> false }

    /**
     * `CreatureEvent::executeOnPrepareDeath` dispatch seam (game.cpp:8740-8744):
     * returns the (possibly rewritten) damage, or `null` to veto the hit.
     */
    var executeOnPrepareDeath: (Creature, Creature?, Int) -> Int? = { _, _, realDamage -> realDamage }

    /** `Monster::getHazard()` (monster.cpp) seam behind [handleHazardSystemAttack]. */
    var monsterHasHazard: (Monster) -> Boolean = { false }

    /** `Player::parseAttackDealtHazardSystem` / `parseAttackRecvHazardSystem` (player.cpp:5513-5647) seam. */
    var parseHazardSystemAttack: (CombatDamage, Player, Monster, Boolean) -> Unit = { _, _, _, _ -> }

    /** `PlayerWheel::checkDrainBodyLeech(target, skill)` (player_wheel.cpp:3257) seam. */
    var checkDrainBodyLeech: (Creature?, skills_t) -> Int = { _, _ -> 0 }

    /** `Player::vocation` (player.hpp) accessor seam; the Kotlin `Player` keeps the vocation id + registry. */
    var getVocation: (Player) -> Vocation? = { player -> player.vocations?.getVocation(player.vocationId) }

    /** `g_configManager().getNumber(PVP_MAX_LEVEL_DIFFERENCE)` (configmanager.cpp) seam. */
    var pvpMaxLevelDifference: Int = 0

    /** `g_configManager().getFloat(PVP_RATE_DAMAGE_TAKEN_PER_LEVEL)` (configmanager.cpp) seam. */
    var pvpRateDamageTakenPerLevel: Float = 0.0f

    /** `g_configManager().getFloat(PVP_RATE_DAMAGE_REDUCTION_PER_LEVEL)` (configmanager.cpp) seam. */
    var pvpRateDamageReductionPerLevel: Float = 0.0f

    /** `PlayerWheel::reduceAllSpellsCooldownTimer(value)` (player_wheel.cpp:3423) seam. */
    var reduceAllSpellsCooldownTimer: (Player, Int) -> Unit = { _, _ -> }

    /** `PlayerWheel::sendGiftOfLifeCooldown()` (player_wheel.cpp:1590) protocol seam. */
    var sendGiftOfLifeCooldown: (Player) -> Unit = { _ -> }

    /** `Player::sendMagicEffect(pos, effect, source)` protocol seam. */
    var sendMagicEffect: (Player, Position, Int) -> Unit = { _, _, _ -> }

    /** `Game::combatGetTypeInfo(type, target, color, effect)` (game.cpp:8072) seam; returns the hit effect (0 = `CONST_ME_NONE`). */
    var combatGetTypeInfo: (CombatType, Creature) -> Int = { _, _ -> 0 }

    /** `g_events().eventCreatureOnDrainHealth` dispatch seam (game.cpp:8533). */
    var eventCreatureOnDrainHealth: (
        Creature,
        Creature?,
        CombatType,
        Int,
        CombatType,
        Int,
    ) -> Unit = { _, _, _, _, _, _ -> }

    /** `g_callbacks().executeCallback(creatureOnDrainHealth, ...)` seam (game.cpp:8534). */
    var callbackCreatureOnDrainHealth: (
        Creature,
        Creature?,
        CombatType,
        Int,
        CombatType,
        Int,
    ) -> Unit = { _, _, _, _, _, _ -> }

    /**
     * `Game::addMagicEffect(pos, effect)` (src/game/game.cpp:9343): every
     * multifloor player spectator of [pos] receives the effect.
     */
    fun addMagicEffect(pos: Position, effect: Int) {
        val spectators = spectatorsOf(pos, multifloor = true)
        addMagicEffect(spectators, pos, effect)
    }

    /** `Game::addMagicEffect(pos, effect, source)` (src/game/game.cpp:9352). */
    fun addMagicEffect(pos: Position, effect: Int, source: Creature?) {
        val spectators = spectatorsOf(pos, multifloor = true)
        addMagicEffect(spectators, pos, effect, source)
    }

    /**
     * `Game::addMagicEffect(spectators, pos, effect)` (src/game/game.cpp:9348).
     * The `source` overload's `SourceEffect_t` classification is not carried by
     * the seam (PARITY GAP).
     */
    fun addMagicEffect(spectators: List<Creature>, pos: Position, effect: Int) {
        for (spectator in spectators) {
            val tmpPlayer = spectator as? Player ?: continue
            sendMagicEffect(tmpPlayer, pos, effect)
        }
    }

    /**
     * `Game::addMagicEffect(spectators, pos, effect, source)`
     * (src/game/game.cpp:9357): each player spectator receives the effect with
     * the [classifyGraphicalEffectSource] classification.
     */
    fun addMagicEffect(spectators: List<Creature>, pos: Position, effect: Int, source: Creature?) {
        for (spectator in spectators) {
            val tmpPlayer = spectator as? Player ?: continue
            sendMagicEffectWithSource(tmpPlayer, pos, effect, classifyGraphicalEffectSource(source, spectator))
        }
    }

    /**
     * `Game::handleHazardSystemAttack` (src/game/game.cpp:8193): only a
     * non-zero primary hit against a hazardous monster reaches the player
     * hazard parser; the parser itself is the injected seam.
     */
    fun handleHazardSystemAttack(damage: CombatDamage, player: Player, monster: Monster, isPlayerAttacker: Boolean) {
        if (damage.primaryValue != 0 && monsterHasHazard(monster)) {
            parseHazardSystemAttack(damage, player, monster, isPlayerAttacker)
        }
    }

    /**
     * `Game::applyPvPDamage` (src/game/game.cpp:8232): scales both components
     * by the defender's receive multiplier, the attacker's dealt multiplier and
     * the level-difference rate.
     */
    fun applyPvPDamage(damage: CombatDamage, attacker: Player, target: Player) {
        val targetDamageReceivedMultiplier = getVocation(target)?.pvpDamageReceivedMultiplier ?: 1.0f
        val attackerDamageDealtMultiplier = getVocation(attacker)?.pvpDamageDealtMultiplier ?: 1.0f
        val levelDifferenceDamageMultiplier = pvpLevelDifferenceDamageMultiplier(attacker, target)

        val pvpDamageMultiplier = targetDamageReceivedMultiplier * attackerDamageDealtMultiplier * levelDifferenceDamageMultiplier

        damage.primaryValue = Math.round(damage.primaryValue * pvpDamageMultiplier)
        damage.secondaryValue = Math.round(damage.secondaryValue * pvpDamageMultiplier)
    }

    /** `Game::pvpLevelDifferenceDamageMultiplier` (src/game/game.cpp:8243). */
    fun pvpLevelDifferenceDamageMultiplier(attacker: Player, target: Player): Float {
        var levelDifference = kotlin.math.abs(target.level - attacker.level)
        val isLowerLevel = target.level < attacker.level

        levelDifference = minOf(levelDifference, pvpMaxLevelDifference)

        var levelDiffRate = 1.0f
        if (isLowerLevel) {
            val rateDamageTakenByLevel = pvpRateDamageTakenPerLevel / 100
            levelDiffRate += levelDifference * rateDamageTakenByLevel
        } else {
            val rateDamageReductionByLevel = pvpRateDamageReductionPerLevel / 100
            levelDiffRate -= levelDifference * rateDamageReductionByLevel
        }

        return levelDiffRate
    }

    /**
     * `Game::applyHealthChange` (src/game/game.cpp:8327): the Wheel of Destiny
     * "Gift of Life" gate. When the incoming hit overkills the player inside
     * the stage's overkill percentage and the cooldown is clear, the gift
     * triggers ([checkGiftOfLife]) and the post-trigger health is returned.
     */
    fun applyHealthChange(damage: CombatDamage, target: Creature): Int {
        var targetHealth = target.health

        val targetPlayer = target as? Player
        if (targetPlayer != null) {
            if (targetPlayer.wheel().getInstant("Gift of Life") &&
                targetPlayer.wheel().getGiftOfCooldown() == 0 &&
                (damage.primaryValue + damage.secondaryValue) >= targetHealth
            ) {
                var overkillMultiplier = (damage.primaryValue + damage.secondaryValue) - targetHealth
                overkillMultiplier = (overkillMultiplier * 100) / targetPlayer.maxHealth
                if (overkillMultiplier <= targetPlayer.wheel().getGiftOfLifeValue()) {
                    checkGiftOfLife(targetPlayer)
                    targetHealth = target.health
                }
            }
        }
        return targetHealth
    }

    /**
     * `PlayerWheel::checkGiftOfLife` (src/creatures/players/components/wheel/player_wheel.cpp:3155),
     * hosted on the `Game` facade because its body drives the game combat,
     * effect and cooldown services. Heals the stage fraction of max health,
     * emits the event message and water-drop effect, reduces every spell
     * cooldown by 60s and arms the total cooldown.
     */
    fun checkGiftOfLife(player: Player) {
        val giftDamage = CombatDamage()
        giftDamage.primaryValue = (player.maxHealth * player.wheel().getGiftOfLifeValue()) / 100
        giftDamage.primaryType = CombatType.HEALING
        sendTextMessage(
            player,
            MessageClass.MESSAGE_EVENT_ADVANCE,
            "That was close! Fortunately, your were saved by the Gift of Life.",
        )
        addMagicEffect(player.position, CONST_ME_WATER_DROP)
        combatChangeHealth(player, player, giftDamage, false)
        reduceAllSpellsCooldownTimer(player, GIFT_OF_LIFE_COOLDOWN_REDUCTION)
        player.wheel().setGiftOfCooldown(player.wheel().getGiftOfLifeTotalCooldown(), false)
        sendGiftOfLifeCooldown(player)
    }

    /**
     * `Game::combatChangeHealth` (src/game/game.cpp:8344): the health
     * state-change pipeline.
     *
     * Faithful reductions (PARITY GAPS): the perfect-shot bonus, the
     * manashield drain, the defensive charm rolls and the hazard dodge
     * notifications stay in their owning systems; the creature-event and
     * protocol interactions run through the injected seams; the target's
     * `getNameDescription` is reduced to `Creature::getName`.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun combatChangeHealth(
        attacker: Creature?,
        target: Creature,
        damage: CombatDamage,
        isEvent: Boolean = false,
    ): Boolean {
        val targetPos = target.position
        if (damage.primaryValue > 0) {
            if (target.health <= 0) {
                return false
            }

            val attackerPlayer = attacker as? Player
            val targetPlayer = target as? Player
            if (isUnmarkedBlackSkull(attackerPlayer, targetPlayer)) {
                return false
            }

            if (damage.origin != CombatOrigin.NONE &&
                executeCreatureHealthChangeEvents(target, attacker, damage)
            ) {
                damage.origin = CombatOrigin.NONE
                return combatChangeHealth(attacker, target, damage, isEvent)
            }

            // Wheel of destiny combat healing
            applyWheelOfDestinyHealing(damage, attackerPlayer, target)

            val healthBefore = target.health
            target.gainHealth(attacker, damage.primaryValue)
            val realHealthChange = target.health - healthBefore

            if (realHealthChange > 0 && !target.isInGhostMode()) {
                if (targetPlayer != null) {
                    updateImpactTracker(targetPlayer, CombatType.HEALING, realHealthChange)
                }

                // Party hunt analyzer
                if (attackerPlayer != null && attackerPlayer.getParty() != null) {
                    addPlayerHealing(attackerPlayer, realHealthChange)
                }

                val damageString = "$realHealthChange ${if (realHealthChange != 1) "hitpoints." else "hitpoint."}"

                var spectatorMessage = ""
                for (spectator in spectatorsOf(targetPos, multifloor = false)) {
                    val tmpPlayer = spectator as? Player ?: continue
                    val messageType: MessageClass
                    val messageText: String
                    if (tmpPlayer === attackerPlayer && attackerPlayer !== targetPlayer) {
                        messageType = MessageClass.MESSAGE_HEALED
                        messageText = "You heal ${target.name} for $damageString"
                    } else if (tmpPlayer === targetPlayer) {
                        val prefix = when {
                            attacker == null -> "You were healed"
                            targetPlayer === attackerPlayer -> "You heal yourself"
                            else -> "You were healed by ${attacker.name}"
                        }
                        messageType = MessageClass.MESSAGE_HEALED
                        messageText = "$prefix for $damageString"
                    } else {
                        if (spectatorMessage.isEmpty()) {
                            spectatorMessage = if (attacker == null) {
                                "${ucfirst(target.name)} was healed"
                            } else {
                                val pronoun = if (attacker === target) "itself" else target.name
                                "${ucfirst(attacker.name)} healed $pronoun"
                            } + " for $damageString"
                        }
                        messageType = MessageClass.MESSAGE_HEALED_OTHERS
                        messageText = spectatorMessage
                    }
                    sendTextMessage(tmpPlayer, messageType, messageText)
                }
            }
            return true
        }

        if (!isAttackable(target)) {
            if (!target.isInGhostMode()) {
                addMagicEffect(targetPos, CONST_ME_POFF)
            }
            return true
        }

        val attackerPlayer = attacker as? Player
        val targetPlayer = target as? Player
        if (isUnmarkedBlackSkull(attackerPlayer, targetPlayer)) {
            return false
        }

        if (damage.origin != CombatOrigin.NONE && executeCreatureHealthChangeEvents(target, attacker, damage)) {
            damage.origin = CombatOrigin.NONE
            return combatChangeHealth(attacker, target, damage, isEvent)
        }

        // Wheel of destiny apply combat effects
        applyWheelOfDestinyEffectsToDamage(damage, attackerPlayer, target)

        damage.primaryValue = kotlin.math.abs(damage.primaryValue)
        damage.secondaryValue = kotlin.math.abs(damage.secondaryValue)

        val targetMonster = target as? Monster
        val attackerMonster = attacker as? Monster

        // PARITY GAP: the perfect-shot bonus (game.cpp:8500-8526) needs the
        // player weapon/quiver accessors and is not ported.

        if (!isEvent) {
            eventCreatureOnDrainHealth(
                target,
                attacker,
                damage.primaryType,
                damage.primaryValue,
                damage.secondaryType,
                damage.secondaryValue,
            )
            callbackCreatureOnDrainHealth(
                target,
                attacker,
                damage.primaryType,
                damage.primaryValue,
                damage.secondaryType,
                damage.secondaryValue,
            )
        }
        if (damage.origin != CombatOrigin.NONE && attacker != null && damage.primaryType != CombatType.HEALING) {
            damage.primaryValue = damage.primaryValue * attacker.getBuff(Buff.BUFF_DAMAGEDEALT.value) / 100
            damage.secondaryValue = damage.secondaryValue * attacker.getBuff(Buff.BUFF_DAMAGEDEALT.value) / 100
        }
        if (damage.origin != CombatOrigin.NONE && damage.primaryType != CombatType.HEALING) {
            damage.primaryValue = damage.primaryValue * target.getBuff(Buff.BUFF_DAMAGERECEIVED.value) / 100
            damage.secondaryValue = damage.secondaryValue * target.getBuff(Buff.BUFF_DAMAGERECEIVED.value) / 100
        }
        val healthChange = damage.primaryValue + damage.secondaryValue
        if (healthChange == 0) {
            return true
        }

        var spectators = spectatorsOf(targetPos, multifloor = true)

        if (targetPlayer != null && attackerMonster != null) {
            handleHazardSystemAttack(damage, targetPlayer, attackerMonster, false)
        } else if (attackerPlayer != null && targetMonster != null) {
            handleHazardSystemAttack(damage, attackerPlayer, targetMonster, true)

            // Monster type onPlayerAttack event (combat.cpp:898-899).
            monsterHost?.onAttackedByPlayer(targetMonster, attackerPlayer)

            if ((damage.primaryValue == 0 && damage.secondaryValue == 0) || damage.hazardDodge) {
                notifySpectators(spectators, targetPos, attackerPlayer, targetMonster)
                return true
            }
        }

        if (damage.fatal) {
            addMagicEffect(spectators, targetPos, CONST_ME_FATAL)
        } else if (damage.critical) {
            addMagicEffect(spectators, targetPos, CONST_ME_CRITICAL_DAMAGE)
        }

        // PARITY GAPS: the defensive charm rune rolls (game.cpp:8570-8598),
        // the manashield drain (game.cpp:8607-8700) and the mana-shield charm
        // inversion of `combatChangeMana` belong to the bestiary/condition
        // systems and are not wired here.

        var realDamage = damage.primaryValue + damage.secondaryValue
        if (realDamage == 0) {
            return true
        }

        // Apply Custom PvP Damage (must be placed here to avoid recursive calls)
        if (attackerPlayer != null && targetPlayer != null) {
            applyPvPDamage(damage, attackerPlayer, targetPlayer)
        }

        var targetHealth = target.health
        realDamage = minOf(targetHealth, damage.primaryValue + damage.secondaryValue)
        if (realDamage == 0) {
            return true
        } else if (realDamage >= targetHealth) {
            val preparedDamage = executeOnPrepareDeath(target, attacker, realDamage) ?: return false
            realDamage = preparedDamage
        }

        targetHealth = applyHealthChange(damage, target)
        if (damage.primaryValue >= targetHealth) {
            damage.primaryValue = targetHealth
            damage.secondaryValue = 0
        } else if (damage.secondaryValue != 0) {
            damage.secondaryValue = minOf(damage.secondaryValue, targetHealth - damage.primaryValue)
        }

        // `Creature::drainHealth(attacker, realDamage)`; the attacker's
        // `onTargetCreatureLossHealth` hook is not modelled (PARITY GAP).
        target.changeHealth(-realDamage)

        // PARITY GAP: `Monster::randomStepping` field-damage suppression.

        if (spectators.isEmpty()) {
            spectators = spectatorsOf(targetPos, multifloor = true)
        }

        addCreatureHealth(target)

        sendDamageMessageAndEffects(
            attacker,
            target,
            damage,
            targetPos,
            attackerPlayer,
            targetPlayer,
            spectators,
            realDamage,
        )

        if (attackerPlayer != null) {
            if (!damage.extension && damage.origin != CombatOrigin.CONDITION) {
                // PARITY GAP: `applyCharmRune` (bestiary charm procs), life and
                // mana leech run below.
                applyLifeLeech(attackerPlayer, targetMonster, target, damage, realDamage)
                applyManaLeech(attackerPlayer, targetMonster, target, damage, realDamage)
            }
            updatePlayerPartyHuntAnalyzer(damage, attackerPlayer)
        }

        return true
    }

    /**
     * `Game::combatChangeMana` (src/game/game.cpp:9063): the mana state change.
     * The `CHARM_VOIDINVERSION` inversion and the creature-event callback seam
     * are reduced to the injected mana-change event dispatch (PARITY GAP for
     * the charm path).
     */
    fun combatChangeMana(attacker: Creature?, target: Creature, damage: CombatDamage): Boolean {
        var manaChange = damage.primaryValue + damage.secondaryValue
        val attackerPlayer = attacker as? Player
        val targetPlayer = target as? Player

        if (manaChange > 0) {
            if (isUnmarkedBlackSkull(attackerPlayer, targetPlayer)) {
                return false
            }

            if (damage.origin != CombatOrigin.NONE && executeCreatureManaChangeEvents(target, attacker, damage)) {
                damage.origin = CombatOrigin.NONE
                return combatChangeMana(attacker, target, damage)
            }

            val manaBefore = target.mana
            target.mana = minOf(target.maxMana, target.mana + manaChange)
            val realManaChange = target.mana - manaBefore
            if (realManaChange > 0 && !target.isInGhostMode()) {
                val damageString = "$realManaChange mana"
                for (spectator in spectatorsOf(target.position, multifloor = false)) {
                    val tmpPlayer = spectator as? Player ?: continue
                    if (tmpPlayer.position.z != target.position.z) {
                        continue
                    }
                    val messageText = when {
                        tmpPlayer === targetPlayer -> "You were restored for $damageString."
                        attacker == null -> "${ucfirst(target.name)} was restored for $damageString."
                        else -> "${ucfirst(attacker.name)} restored ${target.name} for $damageString."
                    }
                    sendTextMessage(tmpPlayer, MessageClass.MESSAGE_MANA, messageText)
                }
            }
            return true
        }

        if (manaChange < 0) {
            val manaBefore = target.mana
            target.mana = maxOf(0, target.mana + manaChange)
            val realManaChange = manaBefore - target.mana
            if (realManaChange > 0 && !target.isInGhostMode()) {
                val damageString = "$realManaChange mana"
                for (spectator in spectatorsOf(target.position, multifloor = false)) {
                    val tmpPlayer = spectator as? Player ?: continue
                    if (tmpPlayer.position.z != target.position.z) {
                        continue
                    }
                    val messageText = when {
                        tmpPlayer === attackerPlayer && attackerPlayer !== targetPlayer ->
                            "${ucfirst(target.name)} loses $damageString due to your attack."
                        tmpPlayer === targetPlayer -> "You lose $damageString."
                        else -> "${ucfirst(target.name)} loses $damageString due to an attack by " +
                            "${attacker?.name ?: "something"}."
                    }
                    sendTextMessage(tmpPlayer, MessageClass.MESSAGE_MANA, messageText)
                }
            }
        }

        return true
    }

    /**
     * `Game::sendDamageMessageAndEffects` (src/game/game.cpp:8788): the
     * `sendEffects` + `sendMessages` pair gated by [shouldSendMessage].
     */
    fun sendDamageMessageAndEffects(
        attacker: Creature?,
        target: Creature,
        damage: CombatDamage,
        targetPos: Position,
        attackerPlayer: Player?,
        targetPlayer: Player?,
        spectators: List<Creature>,
        realDamage: Int,
    ) {
        sendEffects(attacker, target, damage, targetPos, spectators)

        if (shouldSendMessage()) {
            sendMessages(attacker, target, damage, targetPos, attackerPlayer, targetPlayer, spectators, realDamage)
        }
    }

    /**
     * `Game::shouldSendMessage` (src/game/game.cpp:8797): Canary gates the
     * fanout on the `TextMessage` colors assigned by `combatGetTypeInfo`. The
     * Kotlin message model carries no colors, so the reduced port always
     * passes (PARITY GAP).
     */
    private fun shouldSendMessage(): Boolean = true

    /** `Game::sendEffects` (src/game/game.cpp:8943) over the injected [combatGetTypeInfo]. */
    private fun sendEffects(
        attacker: Creature?,
        target: Creature,
        damage: CombatDamage,
        targetPos: Position,
        spectators: List<Creature>,
    ) {
        if (damage.primaryValue != 0) {
            val hitEffect = combatGetTypeInfo(damage.primaryType, target)
            if (hitEffect != CONST_ME_NONE) {
                addMagicEffect(spectators, targetPos, hitEffect)
            }
        }

        if (damage.secondaryValue != 0) {
            val hitEffect = combatGetTypeInfo(damage.secondaryType, target)
            if (hitEffect != CONST_ME_NONE) {
                addMagicEffect(spectators, targetPos, hitEffect)
            }
        }
    }

    /**
     * `Game::sendMessages` (src/game/game.cpp:8803). The input analyzer update
     * and the low-blow/savage charm notes are not ported (PARITY GAP).
     */
    private fun sendMessages(
        attacker: Creature?,
        target: Creature,
        damage: CombatDamage,
        targetPos: Position,
        attackerPlayer: Player?,
        targetPlayer: Player?,
        spectators: List<Creature>,
        realDamage: Int,
    ) {
        if (attackerPlayer != null) {
            updateImpactTracker(attackerPlayer, damage.primaryType, damage.primaryValue)
            if (damage.secondaryType != CombatType.NONE) {
                updateImpactTracker(attackerPlayer, damage.secondaryType, damage.secondaryValue)
            }
        }

        val damageString = "$realDamage ${if (realDamage != 1) "hitpoints" else "hitpoint"}"

        var spectatorMessage = ""
        for (spectator in spectators) {
            val tmpPlayer = spectator as? Player ?: continue
            if (tmpPlayer.position.z != targetPos.z) {
                continue
            }

            val messageType: MessageClass
            val messageText: String
            if (tmpPlayer === attackerPlayer && attackerPlayer !== targetPlayer) {
                messageType = MessageClass.MESSAGE_DAMAGE_DEALT
                messageText = buildMessageAsAttacker(target, damage, damageString)
            } else if (tmpPlayer === targetPlayer) {
                messageType = MessageClass.MESSAGE_DAMAGE_RECEIVED
                messageText = buildMessageAsTarget(attacker, damage, attackerPlayer, targetPlayer, damageString)
            } else {
                if (spectatorMessage.isEmpty()) {
                    spectatorMessage = buildMessageAsSpectator(attacker, target, damage, damageString)
                }
                messageType = MessageClass.MESSAGE_DAMAGE_OTHERS
                messageText = spectatorMessage
            }
            sendTextMessage(tmpPlayer, messageType, messageText)
        }
    }

    /** `Game::buildMessageAsSpectator` (src/game/game.cpp:8834). */
    private fun buildMessageAsSpectator(
        attacker: Creature?,
        target: Creature,
        damage: CombatDamage,
        damageString: String,
    ): String {
        val attackMsg = if (damage.critical) "critical " else ""
        val article = if (damage.critical) "a" else "an"
        val builder = StringBuilder("${ucfirst(target.name)} loses $damageString")
        if (attacker != null) {
            builder.append(" due to ")
            if (attacker === target) {
                builder.append("its own ${attackMsg}attack")
            } else {
                builder.append("$article ${attackMsg}attack by ${attacker.name}")
            }
        }
        builder.append('.')
        if (damage.extension) {
            builder.append(' ').append(damage.exString)
        }
        return builder.toString()
    }

    /**
     * `Game::buildMessageAsTarget` (src/game/game.cpp:8870). The Soulpit
     * critical suffix needs `Monster::getSoulPit`/`getForgeStack` (PARITY GAP).
     */
    private fun buildMessageAsTarget(
        attacker: Creature?,
        damage: CombatDamage,
        attackerPlayer: Player?,
        targetPlayer: Player?,
        damageString: String,
    ): String {
        val attackMsg = if (damage.critical) "critical " else ""
        val article = if (damage.critical) "a" else "an"

        val builder = StringBuilder("You lose $damageString")
        if (attacker == null) {
            builder.append('.')
        } else if (targetPlayer === attackerPlayer) {
            builder.append(" due to your own ${attackMsg}attack.")
        } else {
            builder.append(" due to $article ${attackMsg}attack by ${attacker.name}.")
        }
        if (damage.extension) {
            builder.append(' ').append(damage.exString)
        }
        return builder.toString()
    }

    /** `Game::buildMessageAsAttacker` (src/game/game.cpp:8900). */
    private fun buildMessageAsAttacker(target: Creature, damage: CombatDamage, damageString: String): String {
        val builder = StringBuilder(
            "${ucfirst(target.name)} loses $damageString due to your " +
                "${if (damage.critical) "critical " else " "}attack.",
        )
        if (damage.extension) {
            builder.append(' ').append(damage.exString)
        }
        if (damage.fatal) {
            builder.append(" (Onslaught)")
        }
        return builder.toString()
    }

    /**
     * `Game::notifySpectators` (src/game/game.cpp:8203): the hazard dodge
     * fanout. The cancel message is not ported (PARITY GAP).
     */
    private fun notifySpectators(
        spectators: List<Creature>,
        targetPos: Position,
        attackerPlayer: Player,
        targetMonster: Monster,
    ) {
        if (spectators.isEmpty()) {
            return
        }

        for (spectator in spectators) {
            val tmpPlayer = spectator as? Player ?: continue
            if (tmpPlayer.position.z != targetPos.z) {
                continue
            }

            val text = if (tmpPlayer === attackerPlayer) {
                "${ucfirst(targetMonster.name)} has dodged your attack. (Hazard)"
            } else {
                "${ucfirst(targetMonster.name)} has dodged an attack by ${attackerPlayer.name}. (Hazard)"
            }
            sendTextMessage(tmpPlayer, MessageClass.MESSAGE_DAMAGE_OTHERS, text)
        }
        addMagicEffect(targetPos, CONST_ME_DODGE)
    }

    /**
     * `Game::calculateLeechAmount` (src/game/game.cpp:9058):
     * `realDamage * skill/10000 * (0.1 * targets + 0.9) / targets`, rounded and
     * clamped to `[0, realDamage]`.
     */
    fun calculateLeechAmount(realDamage: Int, skillAmount: Int, targetsAffected: Int): Int {
        val intermediateResult = realDamage * (skillAmount / 10000.0) *
            (0.1 * targetsAffected + 0.9) / targetsAffected
        return Math.round(intermediateResult).toInt().coerceIn(0, realDamage)
    }

    /**
     * `Game::applyLifeLeech` (src/game/game.cpp:9029). The `CHARM_VAMP` bonus
     * is not ported (PARITY GAP); the wheel drain-body bonus is the
     * [checkDrainBodyLeech] seam.
     */
    fun applyLifeLeech(
        attackerPlayer: Player,
        targetMonster: Monster?,
        target: Creature?,
        damage: CombatDamage,
        realDamage: Int,
    ) {
        val wheelLeechAmount = checkDrainBodyLeech(target, skills_t.SKILL_LIFE_LEECH_AMOUNT)
        val lifeSkill = attackerPlayer.getSkillLevel(skills_t.SKILL_LIFE_LEECH_AMOUNT).toLong() +
            wheelLeechAmount + damage.lifeLeech

        val boundedLifeSkill = lifeSkill.coerceIn(0, UINT16_MAX.toLong()).toInt()
        if (boundedLifeSkill == 0) {
            return
        }

        val tmpDamage = CombatDamage()
        tmpDamage.origin = CombatOrigin.SPELL
        tmpDamage.primaryType = CombatType.HEALING
        tmpDamage.primaryValue = calculateLeechAmount(realDamage, boundedLifeSkill, damage.affected)

        combatChangeHealth(null, attackerPlayer, tmpDamage, false)
    }

    /**
     * `Game::applyManaLeech` (src/game/game.cpp:8998). The `CHARM_VOID` bonus
     * is not ported (PARITY GAP); the wheel drain-body bonus is the
     * [checkDrainBodyLeech] seam.
     */
    fun applyManaLeech(
        attackerPlayer: Player,
        targetMonster: Monster?,
        target: Creature?,
        damage: CombatDamage,
        realDamage: Int,
    ) {
        val wheelLeechAmount = checkDrainBodyLeech(target, skills_t.SKILL_MANA_LEECH_AMOUNT)
        val manaSkill = attackerPlayer.getSkillLevel(skills_t.SKILL_MANA_LEECH_AMOUNT).toLong() +
            wheelLeechAmount + damage.manaLeech

        val boundedManaSkill = manaSkill.coerceIn(0, UINT16_MAX.toLong()).toInt()
        if (boundedManaSkill == 0) {
            return
        }

        val tmpDamage = CombatDamage()
        tmpDamage.origin = CombatOrigin.SPELL
        tmpDamage.primaryType = CombatType.MANADRAIN
        tmpDamage.primaryValue = calculateLeechAmount(realDamage, boundedManaSkill, damage.affected)

        combatChangeMana(null, attackerPlayer, tmpDamage)
    }

    /**
     * `Game::updatePlayerPartyHuntAnalyzer` (src/game/game.cpp:8773): forwards
     * both damage components to the player's party hunt analyzer through the
     * [addPlayerDamage] seam (`Party` does not carry the analyzer yet, PARITY GAP).
     */
    fun updatePlayerPartyHuntAnalyzer(damage: CombatDamage, player: Player?) {
        if (player == null) {
            return
        }

        if (player.getParty() != null) {
            if (damage.primaryValue != 0) {
                addPlayerDamage(player, damage.primaryValue)
            }
            if (damage.secondaryValue != 0) {
                addPlayerDamage(player, damage.secondaryValue)
            }
        }
    }

    /**
     * `Player::getSkull()` / `Player::getSkullClient` black-skull gate used by
     * the health, mana and healing paths (game.cpp:8355, 8381, 9093).
     */
    private fun isUnmarkedBlackSkull(attackerPlayer: Player?, targetPlayer: Player?): Boolean =
        attackerPlayer != null &&
            targetPlayer != null &&
            attackerPlayer.getSkull() == Skulls_t.SKULL_BLACK &&
            getSkullClient(attackerPlayer, targetPlayer) == Skulls_t.SKULL_NONE

    /** `ucfirst` (src/utils/tools.hpp). */
    private fun ucfirst(value: String): String =
        value.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    // --- movement handlers (game.cpp:1115-2420) --------------------------------

    /**
     * `Game::internalGetCylinder` (src/game/game.cpp:1115): map tiles outside
     * the `0xFFFF` pseudo-position and open containers for the `0x40` container
     * ids.
     *
     * PARITY GAP: the C++ inventory branch returns the `Player` cylinder, but
     * the Kotlin `Player` is intentionally not a `Cylinder`, so inventory
     * positions resolve to `null` (see [pickupItem] for the injected
     * inventory-move counterpart).
     */
    fun internalGetCylinder(player: Player, pos: Position): Cylinder? {
        if (pos.x != 0xFFFF) {
            return map.getTile(pos)
        }

        if ((pos.y and 0x40) != 0) {
            val fromCid = pos.y and 0x0F
            return player.getContainerByID(fromCid)
        }

        return null
    }

    /**
     * `Game::checkMoveItemToCylinder` (src/game/game.cpp:2301): the gold-pouch,
     * store-inbox, store-item, house-access and nested-store-item gates before
     * a cylinder move is attempted. `TOGGLE_GOLD_POUCH_*` are the
     * [toggleGoldPouchQuicklootOnly]/[toggleGoldPouchAllowAnything] seams.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun checkMoveItemToCylinder(
        player: Player?,
        fromCylinder: Cylinder?,
        toCylinder: Cylinder?,
        item: Item?,
        toPos: Position,
    ): ReturnValue {
        if (player == null || toCylinder == null || item == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        val toCylinderContainer = toCylinder.getContainer()
        if (toCylinderContainer != null) {
            var containerID = toCylinderContainer.id

            // check the store inbox index if gold pouch forces it as containerID
            if (containerID == Container.ITEM_STORE_INBOX) {
                val cylinderItem = toCylinderContainer.getItemByIndex(toPos.z)
                if (cylinderItem != null && cylinderItem.id == Container.ITEM_GOLD_POUCH) {
                    containerID = Container.ITEM_GOLD_POUCH
                }
            }

            val containerToStow = isTryingToStow(toPos, toCylinder)

            if (containerID == Container.ITEM_GOLD_POUCH && !containerToStow) {
                if (toggleGoldPouchQuickLootOnly) {
                    return ReturnValue.CONTAINERNOTENOUGHROOM
                }

                if (!toggleGoldPouchAllowAnything && item.id != ITEM_GOLD_COIN &&
                    item.id != ITEM_PLATINUM_COIN && item.id != ITEM_CRYSTAL_COIN
                ) {
                    return ReturnValue.ITEMCANNOTBEMOVEDPOUCH
                }

                // prevent move up from pouch to store inbox.
                if (!item.canBeMovedToStore() && fromCylinder?.getContainer()?.id == Container.ITEM_GOLD_POUCH) {
                    return ReturnValue.NOTBOUGHTINSTORE
                }

                return ReturnValue.NOERROR
            }

            val topParentContainer = toCylinderContainer.getRootContainer()
            val parentContainer = topParentContainer?.getParent()?.getContainer()
            val isStoreInbox = parentContainer?.isStoreInbox() == true
            val topParentIsStoreInbox = topParentContainer?.isStoreInbox() == true
            val directIsStoreInbox = containerID == Container.ITEM_STORE_INBOX
            val fromContainer = fromCylinder?.getContainer()
            val fromRoot = fromContainer?.getRootContainer()
            val fromIsStoreInbox = (fromContainer != null && fromContainer.isStoreInbox()) ||
                (fromRoot != null && fromRoot.isStoreInbox())
            val toIsStoreInbox = directIsStoreInbox || topParentIsStoreInbox

            if (item.id == Container.ITEM_GOLD_POUCH && !containerToStow) {
                // Loot pouch can only be moved inside Store Inbox (internal reordering).
                if (!(fromIsStoreInbox && toIsStoreInbox)) {
                    return ReturnValue.ITEMCANNOTBEMOVEDTHERE
                }
            }

            if (!item.canBeMovedToStore()) {
                if (directIsStoreInbox) {
                    if (!(fromIsStoreInbox && (item.isMovable() || item.id == Container.ITEM_GOLD_POUCH))) {
                        return ReturnValue.NOTBOUGHTINSTORE
                    }
                }

                if (topParentIsStoreInbox) {
                    // Internal reorganization of the Store Inbox: only allow movable items, or the
                    // gold pouch, when the source is also within the Store Inbox.
                    if (!(fromIsStoreInbox && (item.isMovable() || item.id == Container.ITEM_GOLD_POUCH))) {
                        return ReturnValue.ITEMCANNOTBEMOVEDTHERE
                    }
                }
            }

            if (item.isStoreItem() && !containerToStow) {
                var isValidMoveItem = false
                val fromHouseTile = fromCylinder?.getTile()
                val house = fromHouseTile?.getHouse()
                if (house != null && house.getHouseAccessLevel(player) < AccessHouseLevel.HOUSE_OWNER) {
                    return ReturnValue.NOTPOSSIBLE
                }

                if (containerID == Container.ITEM_STORE_INBOX || containerID == DepotLocker.ITEM_DEPOT ||
                    toCylinderContainer is DepotChest
                ) {
                    isValidMoveItem = true
                }

                if (parentContainer != null && (parentContainer is DepotChest || isStoreInbox)) {
                    isValidMoveItem = true
                }

                if (topParentContainer != null && topParentContainer.isStoreInbox()) {
                    isValidMoveItem = true
                }

                if (item.id == Container.ITEM_GOLD_POUCH) {
                    isValidMoveItem = true
                }

                if (!isValidMoveItem) {
                    return ReturnValue.ITEMCANNOTBEMOVEDTHERE
                }

                if (item.hasOwner() && !item.isOwner(player)) {
                    return ReturnValue.ITEMISNOTYOURS
                }
            }

            if (item.getContainer() != null && !item.isStoreItem()) {
                for (containerItem in item.getContainer()!!.getItems(true)) {
                    if (!containerItem.isStoreItem() || containerToStow) {
                        continue
                    }

                    val parentOfTop = topParentContainer?.getParent()?.getContainer()
                    val outsideStoreContainers = containerID != Container.ITEM_GOLD_POUCH &&
                        containerID != DepotLocker.ITEM_DEPOT &&
                        containerID != Container.ITEM_STORE_INBOX
                    if (outsideStoreContainers ||
                        (parentOfTop != null && (!(parentOfTop is DepotChest) || parentOfTop.id != Container.ITEM_STORE_INBOX))
                    ) {
                        return ReturnValue.NOTPOSSIBLE
                    }
                }
            }
        } else if (toCylinder.getTile() != null) {
            val toHouseTile = toCylinder.getTile()
            val house = toHouseTile?.getHouse()
            if (fromCylinder?.getContainer() != null) {
                if (item.isStoreItem()) {
                    if (house != null && house.getHouseAccessLevel(player) < AccessHouseLevel.HOUSE_OWNER) {
                        return ReturnValue.NOTPOSSIBLE
                    }
                }
                if (item.getContainer() != null && !item.isStoreItem()) {
                    for (containerItem in item.getContainer()!!.getItems(true)) {
                        if (containerItem.isStoreItem()) {
                            return ReturnValue.NOTPOSSIBLE
                        }
                    }
                }

                if (item.isStoreItem() && house == null) {
                    return ReturnValue.ITEMCANNOTBEMOVEDTHERE
                }
            }
        }

        return ReturnValue.NOERROR
    }

    /** `Game::playerMoveThing` (src/game/game.cpp:1711). */
    @Suppress("ReturnCount")
    fun playerMoveThing(
        playerId: Long,
        fromPos: Position,
        itemId: Int,
        fromStackPos: Int,
        toPos: Position,
        count: Int,
    ) {
        val player = getPlayerByID(playerId) ?: return

        // Prevent the player from being able to move the item within the imbuement window
        if (player.hasImbuingItem()) {
            return
        }

        var fromIndex: Int
        if (fromPos.x == 0xFFFF) {
            if ((fromPos.y and 0x40) != 0) {
                fromIndex = fromPos.z
            } else if ((fromPos.y == 0x20 || fromPos.y == 0x21) && !player.isDepotSearchOpenOnItem(itemId)) {
                // '0x20' -> From depot.
                // '0x21' -> From inbox.
                // Both only when the item is being moved from depot search window.
                sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                return
            } else {
                fromIndex = fromPos.y
            }
        } else {
            fromIndex = fromStackPos
        }

        val moveItem = internalGetThing(player, fromPos, fromIndex, itemId, StackPosType_t.STACKPOS_MOVE)
        val creature = if (fromPos.x != 0xFFFF) map.getTile(fromPos)?.getTopVisibleCreature(player) else null
        if (moveItem != null) {
            val toCylinder = internalGetCylinder(player, toPos)
            if (toCylinder == null) {
                sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                return
            }

            playerMoveItem(player, fromPos, itemId, fromStackPos, toPos, count, moveItem, toCylinder)
        } else if (creature != null) {
            val tile = map.getTile(toPos)
            if (tile == null) {
                sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                return
            }

            if (areInRange(creature.position, player.position, 1, 1, 0)) {
                val task = createPlayerTask(
                    pushDelay.toLong(),
                    {
                        playerMoveCreatureByID(player.id, creature.id, creature.position, tile.position)
                    },
                    "Game::playerMoveThing",
                )
                player.setNextActionPushTask(task)
            } else {
                playerMoveCreature(player, creature, creature.position, tile)
            }
        } else {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
        }
    }

    /** `Game::playerMoveCreatureByID` (src/game/game.cpp:1776). */
    fun playerMoveCreatureByID(playerId: Long, movingCreatureId: Long, movingCreatureOrigPos: Position, toPos: Position) {
        val player = getPlayerByID(playerId) ?: return
        val movingCreature = getCreatureByID(movingCreatureId) ?: return
        val toTile = map.getTile(toPos) ?: run {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        playerMoveCreature(player, movingCreature, movingCreatureOrigPos, toTile)
    }

    /**
     * `Game::playerMoveCreature` (src/game/game.cpp:1796): the walk-event body
     * with the action-delay retry, walk-to-creature, pushability/ghost gates,
     * throw distance, zone and crowd gates, then `internalMoveCreature`.
     * `g_dispatcher().addWalkEvent` is the [addWalkEvent] seam and
     * `Player::pushEvent` the [playerPushEvent] seam.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun playerMoveCreature(player: Player, movingCreature: Creature, movingCreatureOrigPos: Position, toTile: Tile) {
        val accepted = addWalkEvent(player.id, 0L) {
            if (!player.canDoAction()) {
                val task = createPlayerTask(
                    600,
                    {
                        playerMoveCreatureByID(player.id, movingCreature.id, movingCreatureOrigPos, toTile.position)
                    },
                    "Game::playerMoveCreature",
                )
                player.setNextActionPushTask(task)
                return@addWalkEvent
            }

            player.setNextActionTask(null)

            if (!areInRange(movingCreatureOrigPos, player.position, 1, 1, 0)) {
                // need to walk to the creature first before moving it
                val listDir = getPathTo(player, movingCreatureOrigPos, 0, 1, true, true)
                if (listDir != null) {
                    if (!queuePlayerAutoWalk(player, listDir)) {
                        return@addWalkEvent
                    }
                    val task = createPlayerTask(
                        600,
                        {
                            playerMoveCreatureByID(player.id, movingCreature.id, movingCreatureOrigPos, toTile.position)
                        },
                        "Game::playerMoveCreature",
                    )
                    playerPushEvent(player, true)
                    player.setNextActionPushTask(task)
                } else {
                    sendCancelMessage(player, ReturnValue.THEREISNOWAY)
                }
                return@addWalkEvent
            }

            playerPushEvent(player, false)
            val monster = movingCreature as? Monster
            val isFamiliar = monster != null && player.isFamiliarProvider(monster)

            if (!isFamiliar && ((!movingCreature.isPushable() && !player.hasFlag(PlayerFlags_t.CanPushAllCreatures)) ||
                    (movingCreature.isInGhostMode() && !player.isAccessPlayer()))
            ) {
                sendCancelMessage(player, ReturnValue.NOTMOVABLE)
                return@addWalkEvent
            }

            // check throw distance
            val movingCreaturePos = movingCreature.position
            val toPos = toTile.position
            if (getDistanceX(movingCreaturePos, toPos) > movingCreature.getThrowRange() ||
                getDistanceY(movingCreaturePos, toPos) > movingCreature.getThrowRange() ||
                getDistanceZ(movingCreaturePos, toPos) * 4 > movingCreature.getThrowRange()
            ) {
                sendCancelMessage(player, ReturnValue.DESTINATIONOUTOFREACH)
                return@addWalkEvent
            }

            if (!areInRange(movingCreaturePos, player.position, 1, 1, 0)) {
                sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                return@addWalkEvent
            }

            if (player !== movingCreature) {
                if (toTile.hasFlag(TileFlags.TILESTATE_BLOCKPATH.value)) {
                    sendCancelMessage(player, ReturnValue.NOTENOUGHROOM)
                    return@addWalkEvent
                } else if ((movingCreature.getZoneType() == ZoneType.ZONE_PROTECTION &&
                        !toTile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)) ||
                    (movingCreature.getZoneType() == ZoneType.ZONE_NOPVP &&
                        !toTile.hasFlag(TileFlags.TILESTATE_NOPVPZONE.value))
                ) {
                    sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                    return@addWalkEvent
                } else {
                    for (tileCreature in toTile.getCreatures()) {
                        if (!tileCreature.isInGhostMode()) {
                            sendCancelMessage(player, ReturnValue.NOTENOUGHROOM)
                            return@addWalkEvent
                        }
                    }

                    val movingNpc = movingCreature as? Npc
                    if (movingNpc != null && movingNpc.canInteract(toPos)) {
                        sendCancelMessage(player, ReturnValue.NOTENOUGHROOM)
                        return@addWalkEvent
                    }
                }

                movingCreature.setLastPosition(movingCreature.position)
            }

            if (!eventPlayerOnMoveCreature(player, movingCreature, movingCreaturePos, toPos)) {
                return@addWalkEvent
            }

            if (!callbackPlayerOnMoveCreature(player, movingCreature, movingCreaturePos, toPos)) {
                return@addWalkEvent
            }

            val ret = internalMoveCreature(movingCreature, toTile)
            if (ret != ReturnValue.NOERROR) {
                sendCancelMessage(player, ret)
            }
            player.setLastPosition(player.position)
        }

        if (!accepted) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
        }
    }

    /** `Game::playerMoveItemByPlayerID` (src/game/game.cpp:2031). */
    fun playerMoveItemByPlayerID(
        playerId: Long,
        fromPos: Position,
        itemId: Int,
        fromStackPos: Int,
        toPos: Position,
        count: Int,
    ) {
        val player = getPlayerByID(playerId) ?: return
        playerMoveItem(player, fromPos, itemId, fromStackPos, toPos, count, null, null)
    }

    /**
     * `Game::playerMoveItem` (src/game/game.cpp:2039): the action-delay retry,
     * item resolution, cylinder resolution, [checkMoveItemToCylinder], floor
     * and range gates, hangable walk-around, throw gates, event vetoes, the
     * stash redirect and the cylinder move.
     *
     * PARITY GAPS: `Game::internalMoveItem` is split between the tile-based
     * public overload and the private [internalMoveItemCylinder]; the C++
     * `RETURNVALUE_NEEDEXCHANGE`/trade-item branches and the quiver inventory
     * refresh are not modelled.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun playerMoveItem(
        player: Player,
        fromPos: Position,
        itemId: Int,
        fromStackPos: Int,
        toPos: Position,
        count: Int,
        item: Item?,
        toCylinder: Cylinder?,
    ) {
        if (!player.canDoAction()) {
            val delay = player.getNextActionTime()
            val task = createPlayerTask(
                delay,
                {
                    playerMoveItemByPlayerID(player.id, fromPos, itemId, fromStackPos, toPos, count)
                },
                "Game::playerMoveItem",
            )
            setNextActionTask(player, task)
            return
        }

        setNextActionTask(player, null)

        var moveItem = item
        if (moveItem == null) {
            var fromIndex: Int
            if (fromPos.x == 0xFFFF) {
                if ((fromPos.y and 0x40) != 0) {
                    fromIndex = fromPos.z
                } else if ((fromPos.y == 0x20 || fromPos.y == 0x21) && !player.isDepotSearchOpenOnItem(itemId)) {
                    sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                    return
                } else {
                    fromIndex = fromPos.y
                }
            } else {
                fromIndex = fromStackPos
            }

            moveItem = internalGetThing(player, fromPos, fromIndex, itemId, StackPosType_t.STACKPOS_MOVE)
            if (moveItem == null) {
                sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                return
            }
        }

        if (moveItem.id != itemId) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        var fromCylinder: Cylinder?
        if (fromPos.x == 0xFFFF && (fromPos.y == 0x20 || fromPos.y == 0x21)) {
            // '0x20' -> From depot.
            // '0x21' -> From inbox.
            // Both only when the item is being moved from depot search window.
            if (!player.isDepotSearchOpenOnItem(itemId)) {
                sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                return
            }

            fromCylinder = moveItem.getParent()
        } else {
            fromCylinder = internalGetCylinder(player, fromPos)
        }

        if (fromCylinder == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        var target = toCylinder
        if (target == null) {
            target = internalGetCylinder(player, toPos)
            if (target == null) {
                sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                return
            }
        }

        val check = checkMoveItemToCylinder(player, fromCylinder, target, moveItem, toPos)
        if (check != ReturnValue.NOERROR) {
            sendCancelMessage(player, check)
            return
        }

        val playerPos = player.position
        val cylinderTile = fromCylinder.getTile()
        val mapFromPos = cylinderTile?.position ?: moveItem.position
        if (playerPos.z != mapFromPos.z) {
            sendCancelMessage(
                player,
                if (playerPos.z > mapFromPos.z) ReturnValue.FIRSTGOUPSTAIRS else ReturnValue.FIRSTGODOWNSTAIRS,
            )
            return
        }

        if (!areInRange(playerPos, mapFromPos, 1, 1)) {
            // need to walk to the item first before using it
            val listDir = getPathTo(player, moveItem.position, 0, 1, true, true)
            if (listDir != null) {
                if (!queuePlayerAutoWalk(player, listDir)) {
                    return
                }
                val task = createPlayerTask(
                    400,
                    {
                        playerMoveItemByPlayerID(player.id, fromPos, itemId, fromStackPos, toPos, count)
                    },
                    "Game::playerMoveItem",
                )
                setNextWalkActionTask(player, task)
            } else {
                sendCancelMessage(player, ReturnValue.THEREISNOWAY)
            }
            return
        }

        val toCylinderTile = target.getTile()
        val mapToPos = toCylinderTile?.position ?: toPos

        // hangable item specific code
        if (moveItem.isHangable() && toCylinderTile != null &&
            toCylinderTile.hasFlag(TileFlags.TILESTATE_SUPPORTS_HANGABLE.value)
        ) {
            // destination supports hangable objects so need to move there first
            val vertical = toCylinderTile.hasProperty(ItemProperty.CONST_PROP_ISVERTICAL)
            if (vertical) {
                if (playerPos.x + 1 == mapToPos.x) {
                    sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                    return
                }
            } else {
                if (playerPos.y + 1 == mapToPos.y) {
                    sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
                    return
                }
            }

            if (!areInRange(playerPos, mapToPos, 1, 1, 0)) {
                val walkPos = if (vertical) {
                    Position(mapToPos.x + 1, mapToPos.y, mapToPos.z)
                } else {
                    Position(mapToPos.x, mapToPos.y + 1, mapToPos.z)
                }

                val requiresPickup = fromPos.x != 0xFFFF &&
                    areInRange(mapFromPos, playerPos, 1, 1) &&
                    !areInRange(mapFromPos, walkPos, 1, 1, 0)
                if (requiresPickup) {
                    queuePlayerAutoWalkAfterItemPickup(player.id, walkPos, 0, fromCylinder, moveItem, count) {
                            walkPlayer,
                            itemPos,
                            itemStackPos,
                        ->
                        val task = createPlayerTask(
                            400,
                            {
                                playerMoveItemByPlayerID(player.id, itemPos, itemId, itemStackPos, toPos, count)
                            },
                            "Game::playerMoveItem",
                        )
                        walkPlayer.setNextWalkActionTask(task)
                    }
                    return
                }

                val listDir = getPathTo(player, walkPos, 0, 0, true, true)
                if (listDir != null) {
                    if (!queuePlayerAutoWalk(player, listDir)) {
                        return
                    }
                    val task = createPlayerTask(
                        400,
                        {
                            playerMoveItemByPlayerID(player.id, fromPos, itemId, fromStackPos, toPos, count)
                        },
                        "Game::playerMoveItem",
                    )
                    setNextWalkActionTask(player, task)
                } else {
                    sendCancelMessage(player, ReturnValue.THEREISNOWAY)
                }
                return
            }
        }

        val throwRange = moveItem.getThrowRange()
        if (getDistanceX(playerPos, mapToPos) > throwRange ||
            getDistanceY(playerPos, mapToPos) > throwRange ||
            getDistanceZ(mapFromPos, mapToPos) * 4 > throwRange
        ) {
            sendCancelMessage(player, ReturnValue.DESTINATIONOUTOFREACH)
            return
        }

        if (!canThrowObjectTo.invoke(mapFromPos, mapToPos)) {
            sendCancelMessage(player, ReturnValue.CANNOTTHROW)
            return
        }

        if (!callbackPlayerOnMoveItem(player, moveItem, count, fromPos, toPos, fromCylinder, target)) {
            return
        }

        if (!eventPlayerOnMoveItem(player, moveItem, count, fromPos, toPos, fromCylinder, target)) {
            return
        }

        var toIndex = 0
        if (toPos.x == 0xFFFF) {
            toIndex = if ((toPos.y and 0x40) != 0) toPos.z else toPos.y
        }

        if (moveItem.type?.wrapable == true || moveItem.isStoreItem() ||
            (moveItem.hasOwner() && !moveItem.isOwner(player))
        ) {
            val toHouseTile = toCylinderTile
            val fromHouseTile = cylinderTile

            if (fromHouseTile != null && fromHouseTile.isHouse()) {
                val fromHouse = fromHouseTile.getHouse()
                val toHouse = toHouseTile?.getHouse()
                if (fromHouse == null || toHouse == null || toHouse.id != fromHouse.id) {
                    sendCancelMessageText(player, "You cannot move this item out of this house.")
                    return
                }
            }
        }

        if (isTryingToStow(toPos, target)) {
            stowItem(player, moveItem, count, false)
            return
        }

        val fromContainer = fromCylinder.getContainer()
        val toContainer = target.getContainer()
        val fromRoot = fromContainer?.getRootContainer()
        val toRoot = toContainer?.getRootContainer()
        val fromIsStoreInbox = fromContainer != null && (fromContainer.isStoreInbox() || fromRoot?.isStoreInbox() == true)
        val toIsStoreInbox = toContainer != null && (toContainer.isStoreInbox() || toRoot?.isStoreInbox() == true)
        val isLootPouchStoreInboxReorder = moveItem.id == Container.ITEM_GOLD_POUCH &&
            fromIsStoreInbox && toIsStoreInbox

        if ((!moveItem.isPushable() && !isLootPouchStoreInboxReorder) ||
            moveItem.hasAttribute(ItemAttribute.UNIQUEID)
        ) {
            sendCancelMessage(player, ReturnValue.NOTMOVABLE)
            return
        }

        val moveFlags = if (isLootPouchStoreInboxReorder) CylinderFlags.FLAG_IGNORENOTMOVABLE else 0
        val ret = internalMoveItemCylinder(fromCylinder, target, toIndex, moveItem, count, moveFlags, player)
        if (ret != ReturnValue.NOERROR) {
            sendCancelMessage(player, ret)
        } else if (toContainer != null && fromContainer != null &&
            fromContainer.countsToLootAnalyzerBalance() &&
            toContainer.getItem().getTopParent()?.getPlayer() === player
        ) {
            sendLootStats(player, moveItem, count)
        }

        cancelPush(player)

        eventPlayerOnItemMoved(player, moveItem, count, fromPos, toPos, fromCylinder, target)
        callbackPlayerOnItemMoved(player, moveItem, count, fromPos, toPos, fromCylinder, target)
    }

    /**
     * Cylinder-to-cylinder subset of `Game::internalMoveItem`
     * (src/game/game.cpp:2436) used by the move handlers: the
     * `queryDestination` floor/container chain, the `queryRemove(1)` /
     * `queryAdd` gates, `queryMaxCount` and the stackable merge.
     *
     * PARITY GAPS: `RETURNVALUE_NEEDEXCHANGE`, the `tradeItem` guard, the
     * `movedItem` out parameter, the quiver inventory refresh and the
     * post-notification bracelets beyond the facade's own calls are not
     * modelled.
     */
    @Suppress("LongMethod")
    private fun internalMoveItemCylinder(
        fromCylinder: Cylinder,
        toCylinder: Cylinder,
        index: Int,
        item: Item,
        count: Int,
        flags: Int,
        actor: Creature? = null,
    ): ReturnValue {
        var to = toCylinder
        var idx = index
        var toItem: Item? = null
        var moveFlags = flags
        var floorN = 0

        while (true) {
            val destination = to.queryDestination(idx, item, toItem, moveFlags)
            val subCylinder = destination.cylinder ?: return ReturnValue.NOTPOSSIBLE
            idx = destination.index
            toItem = destination.destItem
            if (subCylinder === to) {
                moveFlags = destination.flags
                break
            }

            to = subCylinder
            moveFlags = 0

            // to prevent infinite loop
            if (++floorN >= MAP_MAX_LAYERS) {
                break
            }
        }

        // destination is the same as the source?
        if (item === toItem) {
            return ReturnValue.NOERROR
        }

        // 'Move up' stackable items fix
        //  Cip's client never sends the count of stackables when using "Move up" menu option
        var moveCount = count
        if (item.isStackable() && count == 255 && fromCylinder.getParent() === to) {
            moveCount = item.getItemCount()
        }

        // check if we can remove this item (using count of 1 since we don't know how much we can move yet)
        var ret = fromCylinder.queryRemove(item, 1, flags, actor)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        // check if we can add this item
        ret = to.queryAdd(idx, item, moveCount, moveFlags, actor)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        // check how much we can move
        val maxQuery = to.queryMaxCount(idx, item, moveCount, 0, moveFlags)
        if (maxQuery.returnValue != ReturnValue.NOERROR && maxQuery.maxQueryCount == 0) {
            return maxQuery.returnValue
        }

        val m = if (item.isStackable()) minOf(moveCount, maxQuery.maxQueryCount) else maxQuery.maxQueryCount

        ret = fromCylinder.queryRemove(item, m, flags, actor)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        val itemIndex = fromCylinder.getThingIndex(item)
        var moveItem: Item? = item
        var updateItem: Item? = null
        fromCylinder.removeThing(item, m)

        // update item(s)
        if (item.isStackable()) {
            val n = if (toItem != null && sameItemType(item, toItem)) {
                val stackSize = toItem.type?.stackSize ?: DEFAULT_STACK_SIZE
                minOf(stackSize - toItem.count, m).coerceAtLeast(0)
            } else {
                0
            }

            if (n > 0 && toItem != null) {
                to.updateThing(toItem, toItem.id, toItem.count + n)
                updateItem = toItem
            }

            val newCount = m - n
            moveItem = if (newCount > 0) cloneItem(item, newCount) else null
        }

        // add item
        if (moveItem != null) {
            to.addThing(idx, moveItem)
        }

        if (itemIndex != -1) {
            fromCylinder.postRemoveNotification(item, to, itemIndex)
        }

        if (moveItem != null) {
            val moveItemIndex = to.getThingIndex(moveItem)
            if (moveItemIndex != -1) {
                to.postAddNotification(moveItem, fromCylinder, moveItemIndex)
            }
            startDecaying(moveItem)
        }

        if (updateItem != null) {
            val updateItemIndex = to.getThingIndex(updateItem)
            if (updateItemIndex != -1) {
                to.postAddNotification(updateItem, fromCylinder, updateItemIndex)
            }
            startDecaying(updateItem)
        }

        return ReturnValue.NOERROR
    }

    /** `Game::playerInspectItem(player, pos)` (src/game/game.cpp:1635). */
    fun playerInspectItem(player: Player, pos: Position) {
        val item = internalGetThing(player, pos, 0, 0, StackPosType_t.STACKPOS_TOPDOWN_ITEM) ?: run {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        sendItemInspection(player, item.id, item.getItemCount(), item, false)
    }

    /** `Game::playerInspectItem(player, itemId, itemCount, cyclopedia)` (src/game/game.cpp:1652). */
    fun playerInspectItem(player: Player, itemId: Int, itemCount: Int, cyclopedia: Boolean) {
        sendItemInspection(player, itemId, itemCount, null, cyclopedia)
    }

    /**
     * `Game::playerConfigureShowOffSocket` (src/game/game.cpp:5161): the
     * renown/bestiary podium validation, the walk-to-podium retry and the
     * `sendPodiumWindow`/`sendMonsterPodiumWindow` branch.
     */
    @Suppress("ReturnCount")
    fun playerConfigureShowOffSocket(playerId: Long, pos: Position, stackPos: Int, itemId: Int) {
        val player = getPlayerByID(playerId) ?: return
        if (pos.x == 0xFFFF) {
            return
        }

        val item = internalGetThing(player, pos, stackPos, itemId, StackPosType_t.STACKPOS_TOPDOWN_ITEM) ?: return

        if (item.id != itemId || item.type?.isPodium != true || item.hasAttribute(ItemAttribute.UNIQUEID)) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (item.hasOwner() && !item.isOwner(player)) {
            sendCancelMessage(player, ReturnValue.ITEMISNOTYOURS)
            return
        }

        if (onlyInvitedCanMoveHouseItems && !playerCanUseItemOnHouseTile(player, item)) {
            sendCancelMessage(player, ReturnValue.CANNOTUSETHISOBJECT)
            return
        }

        val isPodiumOfRenown = itemId == ITEM_PODIUM_OF_RENOWN1 || itemId == ITEM_PODIUM_OF_RENOWN2
        if (!areInRange(pos, player.position, 1, 1, 0)) {
            val listDir = getPathTo(player, pos, 0, 1, true, false)
            if (listDir != null) {
                if (!queuePlayerAutoWalk(player, listDir)) {
                    return
                }
                val task = if (isPodiumOfRenown) {
                    createPlayerTask(
                        400,
                        { sendPodiumWindow(player, item, pos, itemId, stackPos) },
                        "Game::playerConfigureShowOffSocket",
                    )
                } else {
                    createPlayerTask(
                        400,
                        { sendMonsterPodiumWindow(player, item, pos, itemId, stackPos) },
                        "Game::playerConfigureShowOffSocket",
                    )
                }
                setNextWalkActionTask(player, task)
            } else {
                sendCancelMessage(player, ReturnValue.THEREISNOWAY)
            }
            return
        }

        if (isPodiumOfRenown) {
            sendPodiumWindow(player, item, pos, itemId, stackPos)
        } else {
            sendMonsterPodiumWindow(player, item, pos, itemId, stackPos)
        }
    }

    /**
     * `Game::playerWrapableItem` (src/game/game.cpp:5361): the house/owner
     * gates, the walk-to-item retry, the holding-player and auto-carpet
     * unwrap gates, then [wrapItem]/[unwrapItem].
     *
     * PARITY GAP: `Item::getHoldingPlayer` is the facade's
     * `getTopParent()?.getPlayer()` and `unWrapId` the [itemUnWrapIds] map
     * written by [wrapItem].
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun playerWrapableItem(playerId: Long, pos: Position, stackPos: Int, itemId: Int) {
        val player = getPlayerByID(playerId) ?: return

        val item = internalGetThing(player, pos, stackPos, itemId, StackPosType_t.STACKPOS_FIND_THING) ?: return

        val tile = map.getTile(item.position)
        if (tile == null) {
            logger.error { "Game::playerWrapableItem: Invalid tile on position: $pos" }
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (!tile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value) || !tile.isHouse()) {
            sendCancelMessageText(player, "You may construct this only inside a house.")
            return
        }

        val house = tile.getHouse()
        if (house == null) {
            sendCancelMessageText(player, "You may construct this only inside a house.")
            return
        }

        if (house.getHouseAccessLevel(player) < AccessHouseLevel.HOUSE_OWNER) {
            sendCancelMessageText(player, "You are not allowed to construct this here.")
            return
        }

        if (item.id != itemId || item.hasAttribute(ItemAttribute.UNIQUEID) ||
            (item.type?.wrapable != true && item.id != Items.ITEM_DECORATION_KIT)
        ) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (item.hasOwner() && !item.isOwner(player)) {
            sendCancelMessage(player, ReturnValue.ITEMISNOTYOURS)
            return
        }

        if (onlyInvitedCanMoveHouseItems && !playerCanUseItemOnHouseTile(player, item)) {
            sendCancelMessage(player, ReturnValue.CANNOTUSETHISOBJECT)
            return
        }

        if (pos.x != 0xFFFF && !areInRange(pos, player.position, 1, 1, 0)) {
            val listDir = getPathTo(player, pos, 0, 1, true, true)
            if (listDir != null) {
                if (!queuePlayerAutoWalk(player, listDir)) {
                    return
                }
                val task = createPlayerTask(
                    400,
                    { playerWrapableItem(playerId, pos, stackPos, itemId) },
                    "Game::playerWrapableItem",
                )
                setNextWalkActionTask(player, task)
            } else {
                sendCancelMessage(player, ReturnValue.THEREISNOWAY)
            }
            return
        }

        val container = item.getContainer()
        if (container != null && container.getItemHoldingCount() > 0) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        val topItem = tile.getTopTopItem()
        val holdingPlayer = item.getTopParent()?.getPlayer()
        val unwrappable = holdingPlayer != null && item.id == Items.ITEM_DECORATION_KIT
        val blockedUnwrap = topItem != null && topItem.canReceiveAutoCarpet() &&
            !item.hasProperty(ItemProperty.CONST_PROP_IMMOVABLEBLOCKSOLID)

        if (unwrappable || blockedUnwrap) {
            sendCancelMessageText(player, "You can only wrap/unwrap on the floor.")
            return
        }

        val unWrapId = itemUnWrapIds[item] ?: 0

        // Prevent to wrap a filled bath tube
        if (item.id == ITEM_FILLED_BATH_TUBE) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        if (item.type?.wrapable == true && item.id != Items.ITEM_DECORATION_KIT) {
            wrapItem(item, house)
        } else if (item.id == Items.ITEM_DECORATION_KIT && unWrapId != 0) {
            unwrapItem(item, unWrapId, house, player)
        }
        addMagicEffect(pos, CONST_ME_POFF)
    }

    /**
     * `Game::playerLookInShop` (src/game/game.cpp:6328): the interacting
     * merchant, the item-type and event gates, the look description and the
     * merchant check callback.
     *
     * PARITY GAP: the `Item::getDescription` renderer is the
     * [getItemDescription] seam (the Kotlin item layer has no renderer).
     */
    fun playerLookInShop(playerId: Long, itemId: Int, count: Int) {
        val player = getPlayerByID(playerId) ?: return
        val merchant = getInteractableShopOwner(player) ?: return

        val itemType = itemTypes?.get(itemId) ?: return
        if (itemType.id == 0) {
            return
        }

        if (!eventPlayerOnLookInShop(player, itemType, count)) {
            return
        }

        if (!callbackPlayerOnLookInShop(player, itemType, count)) {
            return
        }

        sendTextMessage(player, MessageClass.MESSAGE_LOOK, "You see ${getItemDescription(itemType, count)}")
        merchant.onPlayerCheckItem(player, itemType.id, count)
    }

    /**
     * `Game::playerEquipItem` (src/game/game.cpp:4159): the slot, cooldown,
     * action-delay and fear gates, the backpack search with the tier filter,
     * the two-handed/quiver unequips and the inventory move.
     *
     * PARITY GAPS: the loot-pouch fallback (`Player::getLootPouch` is not
     * modelled) and `Game::internalMoveItem(..., player, slot, ...)` run
     * through [moveItemToPlayer] because the Kotlin `Player` is not a
     * `Cylinder`.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun playerEquipItem(playerId: Long, itemId: Int, hasTier: Boolean = false, tier: Int = 0) {
        val player = getPlayerByID(playerId) ?: return

        val itemType = itemTypes?.get(itemId) ?: return
        val slot = getSlotType(itemType)

        if (slot == Slots_t.CONST_SLOT_NECKLACE) {
            if (!player.canEquipNecklace()) {
                return
            }
        } else if (slot == Slots_t.CONST_SLOT_RING) {
            if (!player.canEquipRing()) {
                return
            }
        } else if (!player.canDoAction()) {
            val delay = player.getNextActionTime()
            if (delay > 0) {
                val task = createPlayerTask(
                    delay,
                    { playerEquipItem(playerId, itemId, hasTier, tier) },
                    "Game::playerEquipItem",
                )
                player.setNextActionTask(task)
            }
            return
        }

        if (player.hasCondition(ConditionType.CONDITION_FEARED)) {
            sendTextMessage(player, MessageClass.MESSAGE_FAILURE, "You are feared.")
            return
        }

        val backpack = player.getInventoryItem(Slots_t.CONST_SLOT_BACKPACK)?.getContainer()

        val slotItem = player.getInventoryItem(slot)
        var equipItem = searchForItem(backpack, itemType.id, hasTier, tier)
        if (equipItem == null) {
            equipItem = searchForItem(getPlayerLootPouch(player), itemType.id, hasTier, tier)
        }
        var ret = ReturnValue.NOERROR

        if (slotItem != null && slotItem.id == itemType.id && (!hasTier || slotItem.getTier() == tier)) {
            ret = internalCollectManagedItems(player, slotItem, getObjectCategory(slotItem), false)
        } else if (equipItem != null) {
            // Shield slot item
            val rightItem = player.getInventoryItem(Slots_t.CONST_SLOT_RIGHT)

            // Check Ammo item
            if (itemType.weaponType == WeaponType_t.WEAPON_AMMO) {
                if (rightItem != null && rightItem.isQuiver()) {
                    val quiver = rightItem.getContainer()
                    if (quiver != null) {
                        ret = internalMoveItemCylinder(
                            equipItem.getParent() ?: return,
                            quiver,
                            0,
                            equipItem,
                            equipItem.getItemCount(),
                            0,
                        )
                    }
                }
            } else {
                val leftItem = player.getInventoryItem(Slots_t.CONST_SLOT_LEFT)

                val slotPosition = equipItem.getSlotPosition()
                // Checks if a two-handed item is being equipped in the left slot when the right slot is already occupied and move to backpack
                if ((slotPosition and SLOTP_LEFT) != 0 &&
                    (slotPosition and SLOTP_TWO_HAND) != 0 &&
                    rightItem != null &&
                    itemType.weaponType != WeaponType_t.WEAPON_DISTANCE &&
                    !rightItem.isQuiver() &&
                    (leftItem == null || leftItem.getWeaponType() != WeaponType_t.WEAPON_DISTANCE)
                ) {
                    ret = internalCollectManagedItems(player, rightItem, getObjectCategory(rightItem), false)
                }

                // Check if trying to equip a quiver while another quiver is already equipped in the right slot
                if (slot == Slots_t.CONST_SLOT_RIGHT && rightItem != null && rightItem.isQuiver() &&
                    itemType.isQuiver()
                ) {
                    // Replace the existing quiver with the new one
                    ret = moveItemToPlayer(player, rightItem, Slots_t.CONST_SLOT_WHEREEVER)
                    if (ret != ReturnValue.NOERROR) {
                        sendCancelMessage(player, ret)
                        return
                    }
                } else {
                    // Check if trying to equip a shield while a two-handed weapon is equipped in the left slot
                    if (slot == Slots_t.CONST_SLOT_RIGHT && leftItem != null &&
                        (leftItem.getSlotPosition() and SLOTP_TWO_HAND) != 0
                    ) {
                        // Unequip the two-handed weapon from the left slot
                        ret = moveItemToPlayer(player, leftItem, Slots_t.CONST_SLOT_WHEREEVER)
                        if (ret != ReturnValue.NOERROR) {
                            sendCancelMessage(player, ret)
                            return
                        }
                    }
                }

                if (slotItem != null) {
                    ret = internalCollectManagedItems(player, slotItem, getObjectCategory(slotItem), false)
                    // If the item is not collected, move it to any available container slot
                    if (ret != ReturnValue.NOERROR) {
                        ret = moveItemToPlayer(player, slotItem, Slots_t.CONST_SLOT_WHEREEVER)
                    }
                }

                ret = moveItemToPlayer(player, equipItem, slot)
            }
        }

        if (ret != ReturnValue.NOERROR) {
            sendCancelMessage(player, ret)
            return
        }

        val now = otsysTimeMs()
        if (slot == Slots_t.CONST_SLOT_NECKLACE) {
            player.setNextNecklaceAction(now + actionsDelayInterval)
        } else if (slot == Slots_t.CONST_SLOT_RING) {
            player.setNextRingAction(now + actionsDelayInterval)
        } else {
            player.setNextAction(now + actionsDelayInterval)
        }
    }

    /** `getSlotType(itemType)` (`src/game/game.cpp:4132`, anonymous namespace). */
    private fun getSlotType(itemType: ItemType): Slots_t {
        var slot = Slots_t.CONST_SLOT_RIGHT
        if (itemType.weaponType != WeaponType_t.WEAPON_SHIELD) {
            val slotPosition = itemType.slotPosition
            slot = when {
                (slotPosition and SLOTP_HEAD) != 0 -> Slots_t.CONST_SLOT_HEAD
                (slotPosition and SLOTP_NECKLACE) != 0 -> Slots_t.CONST_SLOT_NECKLACE
                (slotPosition and SLOTP_ARMOR) != 0 -> Slots_t.CONST_SLOT_ARMOR
                (slotPosition and SLOTP_LEGS) != 0 -> Slots_t.CONST_SLOT_LEGS
                (slotPosition and SLOTP_FEET) != 0 -> Slots_t.CONST_SLOT_FEET
                (slotPosition and SLOTP_RING) != 0 -> Slots_t.CONST_SLOT_RING
                (slotPosition and SLOTP_AMMO) != 0 -> Slots_t.CONST_SLOT_AMMO
                (slotPosition and SLOTP_TWO_HAND) != 0 || (slotPosition and SLOTP_LEFT) != 0 -> Slots_t.CONST_SLOT_LEFT
                else -> Slots_t.CONST_SLOT_RIGHT
            }
        }

        return slot
    }

    /** `searchForItem(container, itemId, hasTier, tier)` (src/game/game.cpp:4118, anonymous namespace). */
    private fun searchForItem(container: Container?, itemId: Int, hasTier: Boolean, tier: Int): Item? {
        if (container == null) {
            return null
        }

        val iterator = container.iterator()
        while (iterator.hasNext()) {
            val item = iterator.current() ?: break
            iterator.advance()
            if (item.id == itemId && (!hasTier || item.getTier() == tier)) {
                return item
            }
        }

        return null
    }

    /**
     * `Game::internalMoveItem(..., player, slot, ...)` reduced to the Kotlin
     * `Player` facade: the item leaves its source container (or inventory slot)
     * and is placed through `Player::addItem`, restoring the container on
     * failure.
     */
    private fun moveItemToPlayer(player: Player, item: Item, slot: Slots_t): ReturnValue {
        val source = item.getParent()?.getContainer()
        if (source != null) {
            val ret = source.queryRemove(item, item.getItemCount(), 0)
            if (ret != ReturnValue.NOERROR) {
                return ret
            }
            source.removeThing(item, item.getItemCount())
        } else if (inventorySlotOf(player, item) != null) {
            player.removeItem(item, 0)
        }

        val add = player.addItem(item, slot)
        if (add != ReturnValue.NOERROR && source != null) {
            source.addThing(item)
        }
        return add
    }

    /** The inventory slot currently holding [item], or `null`. */
    private fun inventorySlotOf(player: Player, item: Item): Slots_t? =
        Slots_t.entries.firstOrNull { it.value in Slots_t.FIRST..Slots_t.LAST && player.getInventoryItem(it) === item }

    // --- final member seams (game.cpp:770-12608) --------------------------------

    /** `g_dispatcher().cycleEvent(delay, event)` seam used by [start]. */
    var cycleEvent: (Long, () -> Unit) -> Long = { _, _ -> 0 }

    /** `g_luaEnvironment().collectGarbage()` (game.cpp:891) seam (Lua boundary, AGENTS.md 1.4). */
    var collectLuaGarbage: () -> Unit = { }

    /** `g_configManager().getNumber(MARKET_REFRESH_PRICES)` default 30 (configmanager.cpp:62). */
    var marketRefreshPricesIntervalMinutes: Int = 30

    /** `g_database().storeQuery("... motd_num")` seam (game.cpp:9594). */
    var loadMotdNumValue: () -> Long? = { null }

    /** `g_database().storeQuery("... motd_hash")` seam (game.cpp:9602). */
    var loadMotdHashValue: () -> String? = { null }

    /** `g_database().storeQuery("SELECT COUNT(*) ... players_online")` seam (game.cpp:12616). */
    var countPlayersOnlineValue: () -> Int? = { null }

    /** `g_database().executeQuery("DELETE FROM players_online")` seam (game.cpp:12623). */
    var deletePlayersOnline: () -> Boolean = { false }

    /** `DBInsert ... INSERT IGNORE INTO players_online` seam (game.cpp:12636). */
    var insertPlayersOnline: (List<Long>) -> Boolean = { false }

    /** `DELETE FROM players_online WHERE player_id NOT IN (...)` seam (game.cpp:12651). */
    var prunePlayersOnline: (List<Long>) -> Boolean = { false }

    /** `g_configManager().getNumber(KICK_AFTER_MINUTES)` default 15 (configmanager.cpp:293). */
    var kickAfterMinutes: Int = 15

    /** `Player::client != nullptr` (game.cpp:12274) seam. */
    var playerHasClient: (Player) -> Boolean = { true }

    /** `Player::disconnect()` (player.cpp) seam used by [playerCheckActivity]. */
    var disconnectPlayer: (Player) -> Unit = { _ -> }

    /** `Player::getCreatureEvents(CREATURE_EVENT_EXTENDED_OPCODE)` execution seam (game.cpp:11121). */
    var executeExtendedOpcode: (Player, Int, String) -> Unit = { _, _, _ -> }

    /** `Player::canAutoWalk(pos, function, delay)` (player.cpp:12589) seam (game.cpp:12315). */
    var playerCanAutoWalk: (Player, Position, () -> Unit, Long) -> Boolean = { _, _, _, _ -> false }

    /** `client_assertions.txt` append target of `Game::playerDebugAssert` (game.cpp:10211). */
    var debugAssertionsFile: String = "client_assertions.txt"

    /** `g_monsters().getMonsterType(name)` (monsters.cpp) seam used by [applyCharmRune]. */
    var getMonsterTypeByName: (String) -> MonsterType? = { null }

    /** `g_iobestiary().getCharmFromTarget(player, monsterType)` (iobestiary.cpp:412) seam. */
    var getCharmFromTarget: (Player, MonsterType) -> PlayerCharmsByMonster = { _, _ -> PlayerCharmsByMonster() }

    /** `g_iobestiary().getBestiaryCharm(charmRune)` (iobestiary.cpp:231) seam. */
    var getBestiaryCharm: (CharmRune) -> Charm? = { null }

    /** `Player::getCharmTier(charmRune)` (player.hpp) seam. */
    var getCharmTier: (Player, CharmRune) -> Int = { _, _ -> 0 }

    /** `Player::getCharmChanceModifier()` (condition.cpp:1015) seam. */
    var getCharmChanceModifier: (Player) -> Int = { 0 }

    /** `g_iobestiary().parseCharmCombat(charm, player, target, realDamage)` (iobestiary.cpp:191) seam. */
    var parseCharmCombat: (Charm, Player, Creature?, Int) -> Unit = { _, _, _, _ -> }

    /** `Mounts::getMountByID(id)` (mounts.cpp) seam behind [resolveRandomMountClientId]. */
    var getMountByID: (Int) -> Mount? = { null }

    /**
     * `Player::attachedEffects()` wing/effect/aura/shader application of
     * `Game::playerChangeOutfit` (game.cpp:7306-7386) seam. The Kotlin [Player]
     * does not carry the attached-effect state yet (PARITY GAP), so the
     * registry lookups, `diswing`/`diseffect`/`disaura`/`disshader` and the
     * `sendShader` calls are injected.
     */
    var playerApplyAttachedEffects: (Player, Outfit) -> Unit = { _, _ -> }

    /** `g_configManager().getBoolean(TOGGLE_MOUNT_IN_PZ)` default false (configmanager.cpp:157). */
    var toggleMountInPz: Boolean = false

    /** `Game::updatePlayersOnline` `m_deathTime` (game.cpp:12280) facade storage. */
    private val deathTimeByPlayer = IdentityHashMap<Player, Int>()

    /** `Game::mapLuaItemsStored` (game.hpp:874): position -> item id. */
    private val mapLuaItemsStored = linkedMapOf<Position, Int>()

    /**
     * `Game::offlineTrainingWindow` (game.hpp:914): the hardcoded offline
     * training modal window.
     */
    private val offlineTrainingWindow: ModalWindow by lazy(LazyThreadSafetyMode.NONE) {
        ModalWindow(
            ModalWindowManager.OFFLINE_TRAINING_WINDOW_ID,
            "Choose a Skill",
            "Please choose a skill:",
        ).also { window ->
            window.choices.add("Fist Fighting and Shielding" to skills_t.SKILL_FIST.value)
            window.choices.add("Sword Fighting and Shielding" to skills_t.SKILL_SWORD.value)
            window.choices.add("Axe Fighting and Shielding" to skills_t.SKILL_AXE.value)
            window.choices.add("Club Fighting and Shielding" to skills_t.SKILL_CLUB.value)
            window.choices.add("Distance Fighting and Shielding" to skills_t.SKILL_DISTANCE.value)
            window.choices.add("Magic Level and Shielding" to skills_t.SKILL_MAGLEVEL.value)
            window.buttons.add("Okay" to 1)
            window.buttons.add("Cancel" to 0)
            window.defaultEscapeButton = 1
            window.defaultEnterButton = 0
            window.priority = true
        }
    }

    // --- reward chest (game.cpp:3898, 12296) ------------------------------------

    /**
     * `Game::collectRewardChestItems` (src/game/game.cpp:3898): the empty-chest
     * flag, the per-item capacity break, the `maxMoveItems` limit and the
     * `internalCollectManagedItems` counter. `Player::rewardMap` re-parenting
     * stays with [RewardChest]; the collection loop lives in
     * `RewardChest.collectRewardChestItems` and only needs the owner view
     * built here.
     */
    fun collectRewardChestItems(player: Player, maxMoveItems: Int = 0): ReturnValue {
        val rewardChest = player.getRewardChest()
        if (rewardChest.empty) {
            logger.debug { "Reward chest is empty" }
            return ReturnValue.REWARDCHESTISEMPTY
        }

        val owner = PlayerRewardChestOwner(
            capacityProvider = { player.getFreeCapacity() },
            cancelMessageSink = { ret -> sendCancelMessage(player, ret) },
            textMessageSink = { text -> sendTextMessage(player, MessageClass.MESSAGE_EVENT_ADVANCE, text) },
            collectManagedItem = { item -> internalCollectManagedItems(player, item, getObjectCategory(item)) },
        )
        return rewardChest.collectRewardChestItems(owner, maxMoveItems)
    }

    /**
     * `Game::playerRewardChestCollect` (src/game/game.cpp:12296): the chest
     * lookup, the `Player::canAutoWalk` retry, the empty-chest cancel and
     * [collectRewardChestItems].
     *
     * PARITY GAPS: `Player::rewardMap` re-parenting cannot be resolved because
     * the map lives inside [RewardChest], and the C++ `quickLootMutex` is not
     * needed under the single-threaded WorldCommit confinement (AGENTS.md 1.2).
     */
    fun playerRewardChestCollect(playerId: Long, pos: Position, itemId: Int, stackPos: Int, maxMoveItems: Int = 0) {
        val player = getPlayerByID(playerId) ?: return

        val item = internalGetThing(player, pos, stackPos, itemId, StackPosType_t.STACKPOS_FIND_THING) ?: run {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        val container = item.getContainer()
        if (item.id != RewardChest.ITEM_REWARD_CHEST || container == null) {
            sendCancelMessage(player, ReturnValue.NOTPOSSIBLE)
            return
        }

        val function = { playerRewardChestCollect(playerId, pos, itemId, stackPos, maxMoveItems) }
        if (playerCanAutoWalk(player, item.position, function, 500)) {
            return
        }

        val playerRewardChest = player.getRewardChest()
        if (playerRewardChest.empty) {
            sendCancelMessage(player, ReturnValue.REWARDCHESTISEMPTY)
            return
        }

        playerRewardChest.setParent(container.getParent()?.getTile())

        val returnValue = collectRewardChestItems(player, maxMoveItems)
        if (returnValue != ReturnValue.NOERROR) {
            sendCancelMessage(player, returnValue)
        }
    }

    // --- achievements (game.cpp:12481-12527) ------------------------------------

    /** `Game::registerAchievement` (src/game/game.cpp:12481). */
    fun registerAchievement(id: Int, name: String, description: String, secret: Boolean, grade: Int, points: Int) {
        achievementRegistry.registerAchievement(id, name, description, secret, grade, points)
    }

    /** `Game::getSecretAchievements` (src/game/game.cpp:12505). */
    fun getSecretAchievements(): List<Achievement> = achievementRegistry.getSecretAchievements()

    /** `Game::getPublicAchievements` (src/game/game.cpp:12516). */
    fun getPublicAchievements(): List<Achievement> = achievementRegistry.getPublicAchievements()

    /** `Game::getAchievements` (src/game/game.cpp:12527). */
    fun getAchievements(): List<Achievement> = achievementRegistry.getAchievements()

    // --- startup / motd / online bookkeeping (game.cpp:862-12608) --------------

    /**
     * `Game::loadMotdNum` (src/game/game.cpp:9592): the persisted counter and
     * hash; a changed `SERVER_MOTD` hash increments the counter and a missing
     * row seeds `server_config` through the [database].
     */
    fun loadMotdNum() {
        val value = loadMotdNumValue()
        if (value != null) {
            motdNum = value
        } else {
            database?.executeQuery("INSERT INTO `server_config` (`config`, `value`) VALUES ('motd_num', '0')")
        }

        val hash = loadMotdHashValue()
        if (hash != null) {
            motdHash = hash
            if (motdHash != Tools.transformToSHA1(serverMotd)) {
                motdNum++
            }
        } else {
            database?.executeQuery("INSERT INTO `server_config` (`config`, `value`) VALUES ('motd_hash', '')")
        }
    }

    /** `Game::getLightHour` (src/game/game.hpp:551). */
    fun getLightHour(): Int = lightHour

    /**
     * `Game::start` (src/game/game.cpp:862): the tibian clock seed and the
     * maintenance/cycle event registrations. The protocol `ServiceManager`
     * registration has no Kotlin counterpart (PARITY GAP: the network bootstrap
     * owns the listeners).
     */
    fun start() {
        val now = getTimeNow()
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now * 1000
        val minutes = calendar.get(java.util.Calendar.MINUTE)
        lightHour = (minutes * LIGHT_DAY_LENGTH) / 60

        scheduleEvent(EVENT_MS + 1000L) { createFiendishMonsters() }
        scheduleEvent(EVENT_MS + 1000L) { createInfluencedMonsters() }
        cycleEvent(EVENT_MS.toLong()) { updateForgeableMonsters() }
        cycleEvent(EVENT_LIGHTINTERVAL_MS.toLong()) { checkLight() }
        cycleEvent(EVENT_CHECK_CREATURE_INTERVAL_MS) { checkCreatures() }
        cycleEvent(EVENT_LUA_GARBAGE_COLLECTION_MS) { collectLuaGarbage() }

        val marketItemsPriceIntervalMinutes = marketRefreshPricesIntervalMinutes
        if (marketItemsPriceIntervalMinutes > 0) {
            var marketItemsPriceIntervalMS = marketItemsPriceIntervalMinutes * 60000L
            if (marketItemsPriceIntervalMS < 60000L) {
                marketItemsPriceIntervalMS = 60000L
            }
            cycleEvent(marketItemsPriceIntervalMS) { loadItemsPrice() }
        }

        cycleEvent(UPDATE_PLAYERS_ONLINE_DB_MS) { updatePlayersOnline() }
    }

    /** `Game::resetMonsters` (src/game/game.cpp:770): clear every monster target/friend list. */
    fun resetMonsters() {
        for (monster in monsters.toList()) {
            val state = monsterHost?.entryOf(monster.id)?.targetState ?: continue
            MonsterTargeting.clearTargetList(state)
            MonsterTargeting.clearFriendList(state)
        }
    }

    /**
     * `Game::addMoney` (src/game/game.cpp:3367): crystal/platinum/gold
     * conversion with the `addCoins`/`addCoinStack` helpers. The returned pair
     * is `(totalAdded, returnValue)` and `totalAdded` counts the added gold
     * value.
     *
     * PARITY GAP: the Kotlin `internalAddItem` facade reports no remainder, so
     * a partially placed stack is treated as fully placed and the
     * `FLAG_DROPONMAP` branch only triggers on a placement error.
     */
    fun addMoney(cylinder: Cylinder?, money: Long, flags: Int = 0): Pair<Long, ReturnValue> {
        if (cylinder == null) {
            logger.error { "[addMoney] cylinder is nullptr" }
            return 0L to ReturnValue.NOTPOSSIBLE
        }

        if (money == 0L) {
            return 0L to ReturnValue.NOERROR
        }

        var remaining = money
        var totalAdded = 0L

        val crystalCoins = remaining / 10000
        remaining -= crystalCoins * 10000
        var (added, returnValue) = addCoins(cylinder, ITEM_CRYSTAL_COIN, crystalCoins, 10000, flags)
        totalAdded += added
        if (returnValue != ReturnValue.NOERROR) {
            return totalAdded to returnValue
        }

        val platinumCoins = remaining / 100
        remaining -= platinumCoins * 100
        val platinum = addCoins(cylinder, ITEM_PLATINUM_COIN, platinumCoins, 100, flags)
        totalAdded += platinum.first
        returnValue = platinum.second
        if (returnValue != ReturnValue.NOERROR) {
            return totalAdded to returnValue
        }

        if (remaining > 0) {
            val gold = addCoins(cylinder, ITEM_GOLD_COIN, remaining, 1, flags)
            totalAdded += gold.first
            returnValue = gold.second
        }

        return totalAdded to returnValue
    }

    /** `addCoins(game, cylinder, itemId, count, unitValue, flags, totalAdded)` (game.cpp:3276). */
    private fun addCoins(
        cylinder: Cylinder,
        itemId: Int,
        count: Long,
        unitValue: Long,
        flags: Int,
    ): Pair<Long, ReturnValue> {
        var remaining = count
        var totalAdded = 0L
        while (remaining > 0) {
            val createCount = minOf(100L, remaining)
            val (added, ret) = addCoinStack(cylinder, itemId, createCount, unitValue, flags)
            totalAdded += added
            if (ret != ReturnValue.NOERROR) {
                return totalAdded to ret
            }
            remaining -= createCount
        }

        return totalAdded to ReturnValue.NOERROR
    }

    /**
     * `addCoinStack(game, cylinder, itemId, count, unitValue, flags, totalAdded)`
     * (game.cpp:3241): places one stack, counting the added value and falling
     * back to the cylinder tile under `FLAG_DROPONMAP`.
     */
    private fun addCoinStack(
        cylinder: Cylinder,
        itemId: Int,
        count: Long,
        unitValue: Long,
        flags: Int,
    ): Pair<Long, ReturnValue> {
        val outcome = addItemToCylinder(cylinder, itemId, count.toInt(), flags)
        var totalAdded = outcome.placed.toLong() * unitValue

        val fullyPlaced = outcome.ret == ReturnValue.NOERROR && outcome.remainder == 0
        if (fullyPlaced) {
            return totalAdded to ReturnValue.NOERROR
        }

        val tile = cylinder.getTile()
        if ((flags and CylinderFlags.FLAG_DROPONMAP) == 0 || tile == null) {
            return totalAdded to (if (outcome.ret != ReturnValue.NOERROR) outcome.ret else ReturnValue.NOTENOUGHROOM)
        }

        val dropCount = count.toInt() - outcome.placed
        if (dropCount == 0) {
            return totalAdded to (if (outcome.ret != ReturnValue.NOERROR) outcome.ret else ReturnValue.NOTENOUGHROOM)
        }

        val dropOutcome = addItemToCylinder(tile, itemId, dropCount, CylinderFlags.FLAG_NOLIMIT)
        totalAdded += dropOutcome.placed.toLong() * unitValue
        if (dropOutcome.ret != ReturnValue.NOERROR) {
            return totalAdded to dropOutcome.ret
        }
        if (dropOutcome.remainder != 0) {
            return totalAdded to (if (outcome.ret != ReturnValue.NOERROR) outcome.ret else ReturnValue.NOTENOUGHROOM)
        }

        return totalAdded to ReturnValue.NOERROR
    }

    /** `AddItemOutcome` of `addItemToCylinder` (game.cpp:3212). */
    private class AddItemOutcome(val placed: Int, val remainder: Int, val ret: ReturnValue)

    /** `addItemToCylinder(game, cylinder, itemId, count, flags)` (game.cpp:3221). */
    private fun addItemToCylinder(cylinder: Cylinder, itemId: Int, count: Int, flags: Int): AddItemOutcome {
        val item = Item.create(itemId, count, itemTypes)
        val ret = when (cylinder) {
            is Tile -> internalAddItem(cylinder, item, INDEX_WHEREEVER, flags)
            else -> containerAddItem(cylinder, item, INDEX_WHEREEVER)
        }

        // The Kotlin internalAddItem facade does not expose the placed remainder (PARITY GAP).
        return if (ret == ReturnValue.NOERROR) {
            AddItemOutcome(count, 0, ret)
        } else {
            AddItemOutcome(0, count, ret)
        }
    }

    /**
     * `Game::updatePlayersOnline` (src/game/game.cpp:12608): the empty-table
     * count/delete path and the `INSERT IGNORE`/`NOT IN` prune path, both
     * through persistence seams.
     *
     * PARITY GAP: the C++ `DBTransaction` wrapper is not modelled because the
     * Kotlin `Database` owns transactions and the method runs on the single
     * WorldCommit lane.
     */
    fun updatePlayersOnline() {
        if (players.isEmpty()) {
            val count = countPlayersOnlineValue()
            if (count == null) {
                logger.error { "[Game::updatePlayersOnline] Failed to count players_online records." }
                return
            }

            if (count == 0) {
                return
            }

            if (!deletePlayersOnline()) {
                logger.error { "[Game::updatePlayersOnline] Failed to clear players_online records." }
            }
            return
        }

        val onlinePlayerIds = mutableListOf<Long>()
        for (player in players.values) {
            onlinePlayerIds.add(player.getGUID())
        }

        if (!insertPlayersOnline(onlinePlayerIds)) {
            logger.error { "[Game::updatePlayersOnline] Failed to insert players_online records." }
            return
        }

        if (!prunePlayersOnline(onlinePlayerIds)) {
            logger.error { "[Game::updatePlayersOnline] Failed to prune offline players_online records." }
        }
    }

    /**
     * `Game::playerCheckActivity` (src/game/game.cpp:12263): the death-screen
     * IP gate, the dead-and-connected guard, the non-access death timer and the
     * 1s re-arm. `Player::m_deathTime` is the [deathTimeByPlayer] facade.
     */
    fun playerCheckActivity(playerName: String, interval: Int) {
        val player = getPlayerByName(playerName) ?: return

        if (getPlayerIP(player) == 0L) {
            removeDeadPlayer(playerName)
            logger.info { "Player with name '$playerName' has logged out due to exited in death screen" }
            disconnectPlayer(player)
            return
        }

        if (!player.isDead || !playerHasClient(player)) {
            return
        }

        if (!player.isAccessPlayer()) {
            val deathTime = (deathTimeByPlayer[player] ?: 0) + interval
            deathTimeByPlayer[player] = deathTime
            if (deathTime > kickAfterMinutes * 60000 + 60000) {
                removeDeadPlayer(playerName)
                logger.info { "Player with name '$playerName' has logged out due to inactivity after death" }
                disconnectPlayer(player)
                return
            }
        }

        dispatcher.schedule(1000, DispatcherLane.PlayerAction) { playerCheckActivity(playerName, interval) }
    }

    /** `Game::setCreateLuaItems` (src/game/game.hpp:672). */
    fun setCreateLuaItems(position: Position, itemId: Int) {
        mapLuaItemsStored[position] = itemId
    }

    /**
     * `Game::createLuaItemsOnMap` (src/game/game.cpp:11845): create the stored
     * items, skipping missing tiles and positions that already hold the item.
     */
    fun createLuaItemsOnMap() {
        for ((position, itemId) in mapLuaItemsStored) {
            val item = Item.create(itemId, 1, itemTypes)
            if (item.id == 0) {
                logger.warn { "[Game::createLuaItemsOnMap] - Cannot create item with id $itemId" }
                continue
            }

            if (position.x != 0) {
                val tile = map.getTile(position)
                if (tile == null) {
                    logger.warn { "[Game::createLuaItemsOnMap] - Tile is wrong or not found position: $position" }
                    continue
                }

                // If the item already exists on the map, then ignore it and send warning
                if (findItemOfType(tile, itemId, false, -1) != null) {
                    logger.warn {
                        "[Game::createLuaItemsOnMap] - Cannot create item with id $itemId on position $position, item already exists"
                    }
                    continue
                }

                internalAddItem(tile, item, INDEX_WHEREEVER, CylinderFlags.FLAG_NOLIMIT)
            }
        }
    }

    /**
     * `Game::parsePlayerExtendedOpcode` (src/game/game.cpp:11114): every
     * `CREATURE_EVENT_EXTENDED_OPCODE` creature event executes the opcode. The
     * event loop is the [executeExtendedOpcode] seam (Lua event layer, AGENTS.md
     * 1.4).
     */
    fun parsePlayerExtendedOpcode(playerId: Long, opcode: Int, buffer: String) {
        val player = getPlayerByID(playerId) ?: return
        executeExtendedOpcode(player, opcode, buffer)
    }

    /**
     * `Game::playerDebugAssert` (src/game/game.cpp:10203): append the client
     * assertion to [debugAssertionsFile] with the formatted time, player name
     * and IP. I/O failures are logged instead of crashing the world lane.
     */
    fun playerDebugAssert(playerId: Long, assertLine: String, date: String, description: String, comment: String) {
        val player = getPlayerByID(playerId) ?: return

        val header = "----- ${Tools.formatDate(getTimeNow())} - ${player.name} " +
            "(${Tools.convertIPToString(getPlayerIP(player))}) -----"
        try {
            java.io.File(debugAssertionsFile).appendText(
                "$header\n$assertLine\n$date\n$description\n$comment\n",
            )
        } catch (e: java.io.IOException) {
            logger.error(e) { "[Game::playerDebugAssert] Failed to append to $debugAssertionsFile" }
        }
    }

    /**
     * `Game::sendOfflineTrainingDialog` (src/game/game.cpp:11134): send the
     * hardcoded [offlineTrainingWindow] unless the player already has it open.
     */
    fun sendOfflineTrainingDialog(player: Player?) {
        if (player == null) {
            return
        }

        val manager = modalWindowManager ?: return
        if (!manager.hasModalWindowOpen(player, ModalWindowManager.OFFLINE_TRAINING_WINDOW_ID)) {
            manager.sendModalWindow(player, offlineTrainingWindow)
        }
    }

    /** `Game::resolveRandomMountClientId` (src/game/game.cpp:10041). */
    fun resolveRandomMountClientId(randomMountId: Int): Int {
        val randomMount = if (randomMountId != 0) getMountByID(randomMountId) else null
        return randomMount?.clientId ?: 0
    }

    /**
     * `Game::outfitSupportsMount` (src/game/game.cpp:10071): consult the
     * injected appearance tables; without them every outfit is accepted exactly
     * like the C++ `!m_appearancesPtr` branch.
     */
    fun outfitSupportsMount(lookType: Int): Boolean {
        val appearances = this.appearancesPtr ?: return true
        return appearances.outfitSupportsMount(lookType)
    }

    /**
     * `Game::playerChangeOutfit` config overload (src/game/game.cpp:7228): the
     * `ALLOW_CHANGEOUTFIT` gate before the player lookup.
     */
    fun playerChangeOutfit(playerId: Long, outfit: Outfit, setMount: Boolean, isMountRandomized: Int = 0) {
        if (!allowChangeOutfit) {
            return
        }

        val player = getPlayerByID(playerId) ?: return
        playerChangeOutfit(player, outfit, setMount, isMountRandomized)
    }

    /**
     * `Game::playerChangeOutfit` (src/game/game.cpp:7237): the support-outfit
     * and random-mount gates, the outfit-existence and mount-support gates, the
     * mount speed change and the `Player::canWear` outfit application.
     *
     * PARITY GAP: the wing/effect/aura/shader block runs through
     * [playerApplyAttachedEffects] because the Kotlin `Player` does not carry
     * the attached-effect state.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun playerChangeOutfit(player: Player?, outfit: Outfit, setMount: Boolean, isMountRandomized: Int = 0) {
        if (player == null) {
            return
        }

        var randomized = isMountRandomized
        if (player.isWearingSupportOutfit()) {
            outfit.lookMount = 0
            randomized = 0
        }

        player.features.mounts.setRandomMount(randomized)

        if (randomized != 0 && setMount && player.features.mounts.hasAnyMount()) {
            outfit.lookMount = resolveRandomMountClientId(player.features.mounts.getRandomMountId())
            if (outfit.lookMount == 0) {
                randomized = 0
                player.features.mounts.setRandomMount(0)
            }
        }

        val playerOutfit = getOutfitByLookType(player, outfit.lookType)
        if (playerOutfit == null || !setMount) {
            outfit.lookMount = 0
            randomized = 0
            player.features.mounts.setRandomMount(0)
        }

        if (outfit.lookMount != 0 && !outfitSupportsMount(outfit.lookType)) {
            outfit.lookMount = 0
            randomized = 0
            player.features.mounts.setRandomMount(0)
        }

        if (outfit.lookMount != 0) {
            val mount = getMountByClientID(outfit.lookMount) ?: return
            if (!player.features.mounts.hasMount(mount)) {
                return
            }

            val playerTile = player.getTile() ?: return
            val blockedInProtectionZone = !toggleMountInPz &&
                playerTile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
            if (blockedInProtectionZone) {
                outfit.lookMount = 0
            } else {
                var deltaSpeedChange = mount.speed
                val prevMount = if (player.features.mounts.isMounted()) {
                    getMountByID(player.features.mounts.getCurrentMount())
                } else {
                    null
                }
                if (prevMount != null) {
                    deltaSpeedChange -= prevMount.speed
                }
                changeSpeed(player, deltaSpeedChange)
            }

            player.features.mounts.setCurrentMount(mount.id)
        } else if (player.features.mounts.isMounted()) {
            player.features.mounts.dismount()
        }

        if (playerCanWear(player, outfit.lookType, outfit.lookAddons)) {
            player.setDefaultOutfit(outfit)

            if (player.hasCondition(ConditionType.CONDITION_OUTFIT)) {
                return
            }

            internalCreatureChangeOutfit(player, outfit)
        }

        playerApplyAttachedEffects(player, outfit)
    }

    /**
     * `Game::applyCharmRune` (src/game/game.cpp:8973): the major/minor charm
     * lookup, the tier chance plus the (cripple-exempt) chance modifier and the
     * `uniform_random(1, 100)` offensive proc. The `g_iobestiary()` lookups and
     * `parseCharmCombat` dispatch are the injected seams (AGENTS.md 1.4 keeps
     * the charm tables Lua-authored).
     */
    fun applyCharmRune(targetMonster: Monster?, attackerPlayer: Player?, target: Creature?, realDamage: Int) {
        if (targetMonster == null || attackerPlayer == null) {
            return
        }

        val monsterType = getMonsterTypeByName(targetMonster.name) ?: return
        val charms = getCharmFromTarget(attackerPlayer, monsterType)

        for (charmType in listOf(charms.major, charms.minor)) {
            if (charmType == CharmRune.CHARM_NONE) {
                continue
            }

            val charm = getBestiaryCharm(charmType) ?: continue
            val charmTier = getCharmTier(attackerPlayer, charmType)
            val chance = charm.getChance(charmTier).toInt() +
                (if (charm.id == CharmRune.CHARM_CRIPPLE) 0 else getCharmChanceModifier(attackerPlayer))

            val rng = normalRandom(1, 100)
            if (charm.type == CharmType.CHARM_OFFENSIVE && chance >= rng) {
                parseCharmCombat(charm, attackerPlayer, target, realDamage)
            }
        }
    }

    // --- wave-50 final members (game.cpp:785-9888) ------------------------------

    /**
     * `Game::loadBoostedCreature` (src/game/game.cpp:785-860): a stored row for
     * the current local day keeps its boosted name; otherwise a bestiary race
     * different from the stored one is selected, resolved through
     * `g_monsters().getMonsterType` and persisted with its outfit.
     *
     * PARITY GAPS: the `SELECT`/`UPDATE` statements are the
     * [storeBoostedCreature]/[updateBoostedCreature] seams; `localtime` maps to
     * the host default time zone; `db.escapeString` uses the injected
     * [Database] and falls back to verbatim single quotes without one.
     */
    fun loadBoostedCreature() {
        val result = storeBoostedCreature()
        if (result == null) {
            logger.warn { "[Game::loadBoostedCreature] - Failed to detect boosted creature database. (CODE 01)" }
            return
        }

        val ltm = Calendar.getInstance().also { it.timeInMillis = getTimeNow() * 1000 }
        if (result.date == ltm.get(Calendar.DAY_OF_MONTH)) {
            setBoostedName(result.boostname)
            return
        }

        val monsterList = getBestiaryList()
        var selectedRaceId = 0
        var selectedName = ""
        if (monsterList.isNotEmpty()) {
            val monsters = monsterList.entries.filter { it.key != result.raceid }
            if (monsters.isNotEmpty()) {
                val selected = monsters[normalRandom(0, monsters.size - 1)]
                selectedRaceId = selected.key
                selectedName = selected.value
            }
        }

        if (selectedRaceId == 0) {
            logger.warn { "[Game::loadBoostedCreature] - It was not possible to generate a new boosted creature->" }
            return
        }

        val monsterType = getMonsterTypeByName(selectedName)
        if (monsterType == null) {
            logger.warn {
                "[Game::loadBoostedCreature] - It was not possible to generate a new boosted creature-> " +
                    "Monster '$selectedName' not found."
            }
            return
        }

        setBoostedName(selectedName)

        val escapedName = database?.escapeString(selectedName) ?: "'$selectedName'"
        val query = "UPDATE `boosted_creature` SET " +
            "`date` = '${ltm.get(Calendar.DAY_OF_MONTH)}'," +
            "`boostname` = $escapedName," +
            "`looktype` = ${monsterType.info.outfit.lookType}," +
            "`lookfeet` = ${monsterType.info.outfit.lookFeet}," +
            "`looklegs` = ${monsterType.info.outfit.lookLegs}," +
            "`lookhead` = ${monsterType.info.outfit.lookHead}," +
            "`lookbody` = ${monsterType.info.outfit.lookBody}," +
            "`lookaddons` = ${monsterType.info.outfit.lookAddons}," +
            "`lookmount` = ${monsterType.info.outfit.lookMount}," +
            "`raceid` = '$selectedRaceId'"

        if (!updateBoostedCreature(query)) {
            logger.warn { "[Game::loadBoostedCreature] - Failed to detect boosted creature database. (CODE 02)" }
        }
    }

    /** `Game::setBoostedName` (src/game/game.hpp:463-467). */
    fun setBoostedName(name: String) {
        boostedCreature = name
    }

    /** `Game::getBoostedMonsterName` (src/game/game.hpp:469-471). */
    fun getBoostedMonsterName(): String = boostedCreature

    /**
     * `Game::combatBlockHit` (src/game/game.cpp:7874-8070): the dodge roll,
     * absorb/increase message flags, dealt/received buffs, mantra absorb,
     * `Creature::blockHit`, damage reflection and charm parry, returning
     * whether both damage components were blocked.
     *
     * PARITY GAPS: `target->getMonster()`/`attacker->getMonster()` map to the
     * Kotlin `Monster` type; `attacker->getMaxHealth()` is reduced to
     * `Creature::maxHealth` (the `Player` override adding
     * `varStats[STAT_MAXHITPOINTS]` and the wheel health is not modelled);
     * `Combat::doCombatHealth` and `Combat::applyMantraAbsorb` are the
     * [combatDoCombatHealth]/[combatApplyMantraAbsorb] seams.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun combatBlockHit(
        damage: CombatDamage,
        attacker: Creature?,
        target: Creature,
        checkDefense: Boolean,
        checkArmor: Boolean,
        field: Boolean,
        condition: Boolean = false,
    ): Boolean {
        if (damage.primaryType == CombatType.NONE && damage.secondaryType == CombatType.NONE) {
            return true
        }

        val targetPlayer = target as? Player
        if (targetPlayer != null && targetPlayer.isInGhostMode()) {
            return true
        }

        if (damage.primaryValue > 0 || damage.primaryType == CombatType.AGONYDAMAGE) {
            return false
        }

        // Skill dodge (ruse)
        if (targetPlayer != null) {
            val chance = getDodgeChance(targetPlayer)
            if ((chance > 0 && uniformRandom(0, 10000) < chance) || damage.hazardDodge) {
                sendBlockEffect(BlockType.DODGE, damage.primaryType, target.position, attacker)
                sendTextMessage(targetPlayer, MessageClass.MESSAGE_ATTENTION, "You dodged an attack.")
                return true
            }
        }

        var canHeal = false
        val damageHeal = CombatDamage(primaryType = CombatType.HEALING)

        var damageAbsorbMessage = false
        var damageIncreaseMessage = false

        var canReflect = false
        val damageReflected = CombatDamage()
        val damageReflectedParams = CombatParams()

        var primaryBlockType = BlockType.NONE
        var secondaryBlockType = BlockType.NONE

        val targetMonster = target as? Monster

        if (damage.primaryType != CombatType.NONE) {
            damage.primaryValue = -damage.primaryValue

            // Damage healing primary
            if (attacker != null) {
                if (targetMonster != null) {
                    val primaryHealing = getHealingCombatValue(targetMonster, damage.primaryType)
                    if (primaryHealing > 0) {
                        damageHeal.primaryValue = ceil(damage.primaryValue * (primaryHealing / 100.0)).toInt()
                        canHeal = true
                    }
                }
                if (targetPlayer != null && getAbsorbPercent(attacker, damage.primaryType) != 0) {
                    damageAbsorbMessage = true
                }
                if (attacker is Player && getIncreasePercent(attacker, damage.primaryType) != 0) {
                    damageIncreaseMessage = true
                }
                damage.primaryValue =
                    (damage.primaryValue * (attacker.getBuff(Buff.BUFF_DAMAGEDEALT.value) / 100.0)).toInt()
            }
            damage.primaryValue = (damage.primaryValue * (target.getBuff(Buff.BUFF_DAMAGERECEIVED.value) / 100.0)).toInt()

            if (!condition) {
                damage.primaryValue = combatApplyMantraAbsorb(targetPlayer, damage.primaryType, damage.primaryValue)
                damage.primaryValue = maxOf(damage.primaryValue, 0)
            }

            val (blockedPrimaryDamage, primaryBlock) = combatTargetBlockHit(
                attacker,
                target,
                damage.primaryType,
                damage.primaryValue,
                checkDefense,
                checkArmor,
                field,
            )
            damage.primaryValue = -blockedPrimaryDamage
            primaryBlockType = primaryBlock
            sendBlockEffect(primaryBlockType, damage.primaryType, target.position, attacker)

            // Damage reflection primary
            if (!damage.extension && attacker != null) {
                val attackerMonster = attacker as? Monster
                if (attackerMonster != null && targetPlayer != null && damage.primaryType != CombatType.HEALING) {
                    // Charm rune (target as player)
                    val mType = getMonsterTypeByName(attackerMonster.name)
                    if (mType != null) {
                        val activeCharm = getCharmFromTarget(targetPlayer, mType).major
                        if (activeCharm == CharmRune.CHARM_PARRY) {
                            val charm = getBestiaryCharm(activeCharm)
                            val charmTier = getCharmTier(targetPlayer, activeCharm)
                            if (charm != null && charm.type == CharmType.CHARM_DEFENSIVE &&
                                charm.getChance(charmTier) >= normalRandom(1, 10000) / 100.0
                            ) {
                                parseCharmCombat(
                                    charm,
                                    targetPlayer,
                                    attacker,
                                    damage.primaryValue + damage.secondaryValue,
                                )
                            }
                        }
                    }
                }
                val primaryReflectPercent = getReflectPercent(target, damage.primaryType, true)
                val primaryReflectFlat = getReflectFlat(target, damage.primaryType, true)
                if (primaryReflectPercent > 0 || primaryReflectFlat > 0) {
                    val distanceX = getDistanceX(target.position, attacker.position)
                    val distanceY = getDistanceY(target.position, attacker.position)
                    if (targetMonster != null || damage.primaryType != CombatType.PHYSICALDAMAGE ||
                        primaryReflectPercent > 0 || maxOf(distanceX, distanceY) < 2
                    ) {
                        val reflectFlat = -primaryReflectFlat
                        val reflectPercent = ceil(damage.primaryValue * primaryReflectPercent / 100.0).toInt()
                        val reflectLimit = ceil(attacker.maxHealth * 0.01).toInt()
                        damageReflected.primaryValue = maxOf(-reflectLimit, reflectFlat + reflectPercent)
                        damageReflected.primaryType =
                            if (targetPlayer != null) CombatType.NEUTRALDAMAGE else damage.primaryType
                        if (damageReflected.exString.isNotEmpty()) {
                            damageReflected.exString += ", "
                        }
                        damageReflected.extension = true
                        damageReflected.exString += " (damage reflection)"
                        damageReflectedParams.combatType = damage.primaryType
                        damageReflectedParams.aggressive = true
                        canReflect = true
                    }
                }
            }
        }

        if (damage.secondaryType != CombatType.NONE) {
            damage.secondaryValue = -damage.secondaryValue

            // Damage healing secondary
            if (attacker != null) {
                if (targetMonster != null) {
                    val secondaryHealing = getHealingCombatValue(targetMonster, damage.secondaryType)
                    if (secondaryHealing > 0) {
                        damageHeal.primaryValue += ceil(damage.secondaryValue * (secondaryHealing / 100.0)).toInt()
                        canHeal = true
                    }
                }
                if (targetPlayer != null && getAbsorbPercent(attacker, damage.secondaryType) != 0) {
                    damageAbsorbMessage = true
                }
                if (attacker is Player && getIncreasePercent(attacker, damage.secondaryType) != 0) {
                    damageIncreaseMessage = true
                }
                damage.secondaryValue =
                    (damage.secondaryValue * (attacker.getBuff(Buff.BUFF_DAMAGEDEALT.value) / 100.0)).toInt()
            }
            damage.secondaryValue =
                (damage.secondaryValue * (target.getBuff(Buff.BUFF_DAMAGERECEIVED.value) / 100.0)).toInt()

            if (!condition) {
                damage.secondaryValue =
                    combatApplyMantraAbsorb(targetPlayer, damage.secondaryType, damage.secondaryValue)
                damage.secondaryValue = maxOf(damage.secondaryValue, 0)
            }

            val (blockedSecondaryDamage, secondaryBlock) = combatTargetBlockHit(
                attacker,
                target,
                damage.secondaryType,
                damage.secondaryValue,
                false,
                false,
                field,
            )
            damage.secondaryValue = -blockedSecondaryDamage
            secondaryBlockType = secondaryBlock
            sendBlockEffect(secondaryBlockType, damage.secondaryType, target.position, attacker)

            // Damage reflection secondary
            if (!damage.extension && attacker != null && targetMonster != null) {
                val secondaryReflectPercent = getReflectPercent(target, damage.secondaryType, true).toInt()
                val secondaryReflectFlat = getReflectFlat(target, damage.secondaryType, true)
                if (secondaryReflectPercent > 0 || secondaryReflectFlat > 0) {
                    if (!canReflect) {
                        val reflectFlat = -secondaryReflectFlat
                        val reflectPercent = ceil(damage.primaryValue * secondaryReflectPercent / 100.0).toInt()
                        val reflectLimit = ceil(attacker.maxHealth * 0.01).toInt()
                        damageReflected.primaryValue = maxOf(-reflectLimit, reflectFlat + reflectPercent)
                        damageReflected.primaryType = damage.secondaryType
                        if (damageReflected.exString.isNotEmpty()) {
                            damageReflected.exString += ", "
                        }
                        damageReflected.extension = true
                        damageReflected.exString += " (damage reflection)"
                        damageReflectedParams.combatType = damage.primaryType
                        damageReflectedParams.aggressive = true
                        canReflect = true
                    } else {
                        damageReflected.secondaryType = damage.secondaryType
                        damageReflected.primaryValue =
                            ceil(damage.secondaryValue * secondaryReflectPercent / 100.0).toInt() +
                            maxOf(
                                -ceil(attacker.maxHealth * 0.01).toInt(),
                                maxOf(damage.secondaryValue, -secondaryReflectFlat),
                            )
                    }
                }
            }
        }

        if (damage.primaryType == CombatType.HEALING) {
            damage.primaryValue = (damage.primaryValue * (target.getBuff(Buff.BUFF_HEALINGRECEIVED.value) / 100.0)).toInt()
        }

        if (damageAbsorbMessage) {
            if (damage.exString.isNotEmpty()) {
                damage.exString += ", "
            }
            damage.exString += "active elemental resiliance"
        }

        if (damageIncreaseMessage) {
            if (damage.exString.isNotEmpty()) {
                damage.exString += ", "
            }
            damage.exString += "active elemental amplification"
        }

        if (canReflect && attacker != null) {
            combatDoCombatHealth(target, attacker, damageReflected, damageReflectedParams)
        }
        if (canHeal) {
            combatChangeHealth(null, target, damageHeal, false)
        }
        return primaryBlockType != BlockType.NONE && secondaryBlockType != BlockType.NONE
    }

    /**
     * `InternalGame::sendBlockEffect` (src/game/game.cpp:413-453): the block
     * type selects the magic effect; `BLOCK_IMMUNITY` resolves the combat type
     * through the switch and every non-`BLOCK_NONE` block plays the no-damage
     * sound. `COMBAT_UNDEFINEDDAMAGE` returns before the sound, exactly like
     * the C++ `return`.
     */
    private fun sendBlockEffect(
        blockType: BlockType,
        combatType: CombatType,
        targetPos: Position,
        source: Creature?,
    ) {
        when (blockType) {
            BlockType.DEFENSE -> addMagicEffect(targetPos, CONST_ME_POFF, source)
            BlockType.ARMOR -> addMagicEffect(targetPos, CONST_ME_BLOCKHIT, source)
            BlockType.DODGE -> addMagicEffect(targetPos, CONST_ME_DODGE, source)
            BlockType.IMMUNITY -> {
                val hitEffect = when (combatType) {
                    CombatType.UNDEFINEDDAMAGE -> return
                    CombatType.ENERGYDAMAGE,
                    CombatType.FIREDAMAGE,
                    CombatType.PHYSICALDAMAGE,
                    CombatType.ICEDAMAGE,
                    CombatType.DEATHDAMAGE,
                    -> CONST_ME_BLOCKHIT
                    CombatType.EARTHDAMAGE -> CONST_ME_GREEN_RINGS
                    CombatType.HOLYDAMAGE -> CONST_ME_HOLYDAMAGE
                    else -> CONST_ME_POFF
                }
                addMagicEffect(targetPos, hitEffect, source)
            }
            BlockType.NONE -> {}
        }

        if (blockType != BlockType.NONE) {
            sendSingleSoundEffect(targetPos, SoundEffect.NO_DAMAGE, source)
        }
    }

    /**
     * The default `Creature::blockHit` core behind [combatTargetBlockHit]:
     * [Combat.blockHit] (absorb/increase, immunity, defense, armor) followed by
     * [Combat.mitigateDamage] (creature.cpp:944-1009).
     *
     * PARITY GAPS: the `attacker->onAttackedCreature`/
     * `onAttackedCreatureBlockHit` callbacks, `onBlockHit`, `onTakeDamage` and
     * `onAttacked` hooks stay with the creature-event system;
     * `attacker->getIncreasePercent` is read through the seam.
     */
    private fun blockHitCore(
        attacker: Creature?,
        target: Creature,
        combatType: CombatType,
        damage: Int,
        checkDefense: Boolean,
        checkArmor: Boolean,
    ): Pair<Int, BlockType> {
        var checkDefenseNow = checkDefense
        var checkArmorNow = checkArmor
        if (target is Monster && disableMonsterArmor) {
            checkDefenseNow = false
            checkArmorNow = false
        }
        val immune = combatIsImmune(target, combatType)
        val hasDefense = if (!immune && (checkDefenseNow || checkArmorNow)) combatHasDefense(target) else false

        val (blockedDamage, blockType) = Combat.blockHit(
            damage = damage,
            random = combatRandom,
            checkDefense = checkDefenseNow,
            checkArmor = checkArmorNow,
            hasDefense = hasDefense,
            canUseDefense = combatCanUseDefense(target),
            defense = getDefense(target),
            armor = getArmor(target),
            immune = immune,
            combatType = combatType,
            absorbPercent = getAbsorbPercent(target, combatType),
            absorbFlat = getAbsorbFlat(target, combatType),
            increasePercent = if (attacker != null) getIncreasePercent(attacker, combatType) else 0,
        )

        if (blockedDamage == 0) {
            return blockedDamage to blockType
        }
        return Combat.mitigateDamage(combatType, blockedDamage, getMitigation(target), blockType)
    }

    /**
     * `Game::playerCyclopediaCharacterInfo` (src/game/game.cpp:9823-9888): only
     * the requesting character's own info is served; every other id answers the
     * `sendCyclopediaCharacterNoData(type, 2)` refusal.
     *
     * PARITY GAPS: `Player::sendCyclopediaCharacter*` packets and the
     * `PlayerCyclopedia` death/kill queries are the injected seams; the
     * `CYCLOPEDIA_CHARACTERINFO_COMBATSTATS` case falls through the C++
     * `default` label to the `sendCyclopediaCharacterNoData(type, 1)` refusal.
     */
    fun playerCyclopediaCharacterInfo(
        player: Player,
        characterID: Int,
        characterInfoType: Int,
        entriesPerPage: Int,
        page: Int,
    ) {
        val playerID = player.id.toInt()
        if (playerID != characterID) {
            // For now allow viewing only our character since we don't have tournaments supported
            sendCyclopediaCharacterNoData(player, characterInfoType, 2)
            return
        }

        when (characterInfoType) {
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_BASEINFORMATION ->
                sendCyclopediaCharacterBaseInformation(player)

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_GENERALSTATS ->
                sendCyclopediaCharacterGeneralStats(player)

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_RECENTDEATHS ->
                player.cyclopedia().loadDeathHistory(page, entriesPerPage)

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_RECENTPVPKILLS ->
                player.cyclopedia().loadRecentKills(page, entriesPerPage)

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_ACHIEVEMENTS ->
                sendUnlockedSecretAchievements(player)

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_ITEMSUMMARY -> {
                val inventoryItems = getInventoryItemsId(player)
                val storeInboxItems = getStoreInboxItemsId(player)
                val stashItems = getStashItems(player)
                val depotBoxItems = getDepotChestItemsId(player)
                val inboxItems = getDepotInboxItemsId(player)

                sendCyclopediaCharacterItemSummary(
                    player,
                    inventoryItems,
                    storeInboxItems,
                    stashItems,
                    depotBoxItems,
                    inboxItems,
                )
            }

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_OUTFITSMOUNTS ->
                sendCyclopediaCharacterOutfitsMounts(player)

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_STORESUMMARY ->
                sendCyclopediaCharacterStoreSummary(player)

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_INSPECTION ->
                sendCyclopediaCharacterInspection(player)

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_BADGES ->
                sendCyclopediaCharacterBadges(player)

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_TITLES ->
                sendCyclopediaCharacterTitles(player)

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_WHEEL ->
                playerOpenWheel(playerID.toLong(), characterID.toLong())

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_OFFENCESTATS ->
                sendCyclopediaCharacterOffenceStats(player)

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_DEFENCESTATS ->
                sendCyclopediaCharacterDefenceStats(player)

            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_MISCSTATS ->
                sendCyclopediaCharacterMiscStats(player)

            else -> sendCyclopediaCharacterNoData(player, characterInfoType, 1)
        }
    }

    /**
     * `Game::loadMainMap` (src/game/game.cpp:1058-1062): the despawn config and
     * the main-map `Map::loadMap` call over
     * `DATA_DIRECTORY + "/world/" + filename + ".otbm"`.
     *
     * PARITY GAP: `g_configManager()` values and the `Map::loadMap` call are
     * the [dataDirectory]/[mapLoadMap] seams.
     */
    fun loadMainMap(filename: String) {
        setMonsterDespawnRangeAndRadius(defaultDespawnRange, defaultDespawnRadius)
        mapLoadMap("$dataDirectory/world/$filename.otbm", true, true, true, true, true, Position(0, 0, 0))
    }

    /**
     * `Game::loadCustomMaps` (src/game/game.cpp:1064-1109): directory creation,
     * the `.otbm` sweep with the 50-map cap, the `#`/main-map skips and the
     * final `Map::loadHouseInfo`.
     *
     * PARITY GAPS: `std::filesystem` is the
     * [ensureCustomMapDirectory]/[listCustomMapFiles] seam pair; the log lines
     * keep the C++ messages; `std::ios_base::failure` maps to [IOException].
     */
    fun loadCustomMaps(customMapPath: String) {
        setMonsterDespawnRangeAndRadius(defaultDespawnRange, defaultDespawnRadius)

        if (!ensureCustomMapDirectory(customMapPath)) {
            throw IOException("Failed to create custom map directory $customMapPath")
        }

        var customMapIndex = 0
        for (realPath in listCustomMapFiles(customMapPath)) {
            // `std::filesystem::path::extension()`/`stem()`: a leading dot is
            // part of the stem, not an extension.
            val name = File(realPath).name
            val dotIndex = name.lastIndexOf('.')
            val extension = if (dotIndex > 0) name.substring(dotIndex) else ""
            if (extension != ".otbm") {
                continue
            }

            val filename = if (dotIndex > 0) name.substring(0, dotIndex) else name

            // Do not load more maps than possible
            if (customMapIndex >= 50) {
                logger.warn { "Maximum number of custom maps loaded. Custom map $filename [ignored]" }
                continue
            }

            // Filenames that start with a # are ignored.
            if (filename.startsWith('#')) {
                logger.info { "Custom map $filename [disabled]" }
                continue
            }

            // Avoid loading main map again.
            if (filename == mapName) {
                logger.warn { "Custom map $filename is main map" }
                continue
            }

            mapLoadMapCustom(filename, true, true, true, true, customMapIndex)

            customMapIndex++
        }

        // Must be done after all maps have been loaded
        mapLoadHouseInfo()
    }

    /** `Game::loadMap` (src/game/game.cpp:1111-1113). */
    fun loadMap(path: String, pos: Position = Position(0, 0, 0)) {
        mapLoadMap(path, false, false, false, false, false, pos)
    }

    /**
     * `Game::loadAppearanceProtobuf` (src/game/game.cpp:1657-1708): parse
     * `appearances.dat`, merge the item registry and, under
     * `WARN_UNSAFE_SCRIPTS`, register the effect/missile/outfit ids.
     *
     * PARITY GAPS: `ParseFromIstream` is the [parseAppearances] seam:
     * a `null` parse resets `m_appearancesPtr`, so the "cannot open" case
     * shares the C++ parse-failure reset instead of preserving the previous
     * table; `outfitMountSupportedLookTypes` is recomputed by the parsed
     * [Appearances]; `Item::items.loadFromProtobuf` is the
     * [loadItemsFromProtobuf] seam.
     */
    fun loadAppearanceProtobuf(file: String): FileLoaderError {
        val parsed = parseAppearances(file)
        if (parsed == null) {
            appearancesPtr = null
            return FileLoaderError.ERROR_NOT_OPEN
        }

        appearancesPtr = parsed

        // Parsing all items into ItemType
        loadItemsFromProtobuf(parsed)

        // Only iterate other objects if necessary
        if (warnUnsafeScripts) {
            // Registering magic effects
            registeredMagicEffects += parsed.effects.keys

            // Registering distance effects
            registeredDistanceEffects += parsed.missiles.keys

            // Registering outfits
            registeredLookTypes += parsed.outfits.keys
        }

        return FileLoaderError.ERROR_NONE
    }

    /** `Game::isMagicEffectRegistered` (src/game/game.hpp:657-659). */
    fun isMagicEffectRegistered(type: Int): Boolean = type in registeredMagicEffects

    /** `Game::isDistanceEffectRegistered` (src/game/game.hpp:661-665). */
    fun isDistanceEffectRegistered(type: Int): Boolean = type in registeredDistanceEffects

    /** `Game::isLookTypeRegistered` (src/game/game.hpp:667-669). */
    fun isLookTypeRegistered(type: Int): Boolean = type in registeredLookTypes

    // --- wave-50 final member seams ---------------------------------------------

    /** `g_database().storeQuery("SELECT ... FROM boosted_creature")` seam (game.cpp:787). */
    var storeBoostedCreature: () -> BoostedCreatureRecord? = { null }

    /** `db.executeQuery("UPDATE boosted_creature ...")` seam (game.cpp:856). */
    var updateBoostedCreature: (String) -> Boolean = { query -> database?.executeQuery(query) == true }

    /** `Monster::despawnRange = ...; Monster::despawnRadius = ...` seam (game.cpp:1059-1060, 1065-1066). */
    var setMonsterDespawnRangeAndRadius: (Int, Int) -> Unit = { _, _ -> }

    /** `g_configManager().getString(DATA_DIRECTORY)` default "data-otservbr-global" (configmanager.cpp:395). */
    var dataDirectory: String = "data-otservbr-global"

    /** `g_configManager().getString(MAP_NAME)` default "canary" (configmanager.cpp:73). */
    var mapName: String = "canary"

    /** `g_configManager().getNumber(DEFAULT_DESPAWNRANGE)` default 2 (configmanager.cpp:248). */
    var defaultDespawnRange: Int = 2

    /** `g_configManager().getNumber(DEFAULT_DESPAWNRADIUS)` default 50 (configmanager.cpp:247). */
    var defaultDespawnRadius: Int = 50

    /** `std::filesystem::exists`/`create_directory` seam (game.cpp:1070); false means creation failed. */
    var ensureCustomMapDirectory: (String) -> Boolean = { true }

    /** `std::filesystem::directory_iterator(customMapPath)` `entry.path()` seam (game.cpp:1075). */
    var listCustomMapFiles: (String) -> List<String> = { emptyList() }

    /** `Map::loadMap(identifier, mainMap, loadHouses, loadMonsters, loadNpcs, loadZones, pos)` (src/map/map.cpp:157) seam. */
    var mapLoadMap: (String, Boolean, Boolean, Boolean, Boolean, Boolean, Position) -> Unit =
        { _, _, _, _, _, _, _ -> }

    /** `Map::loadMapCustom(mapName, loadHouses, loadMonsters, loadNpcs, loadZones, customMapIndex)` (src/map/map.cpp:231) seam. */
    var mapLoadMapCustom: (String, Boolean, Boolean, Boolean, Boolean, Int) -> Unit = { _, _, _, _, _, _ -> }

    /** `Map::loadHouseInfo` (src/map/map.cpp:257) seam. */
    var mapLoadHouseInfo: () -> Unit = {}

    /** `g_configManager().getBoolean(WARN_UNSAFE_SCRIPTS)` default true (configmanager.cpp:169). */
    var warnUnsafeScripts: Boolean = true

    /** `m_appearancesPtr->ParseFromIstream(&fileStream)` seam (game.cpp:1672); null is the parse/open failure. */
    var parseAppearances: (String) -> Appearances? = { file ->
        val handle = File(file)
        if (handle.exists()) Appearances.load(handle) else null
    }

    /** `Item::items.loadFromProtobuf()` (game.cpp:1680) seam over the injected [itemTypes]. */
    var loadItemsFromProtobuf: (Appearances) -> Unit = { parsed -> itemTypes?.loadFromProtobuf(parsed) }

    /** `uniform_random(min, max)` (src/utils/tools.hpp) seam for the dodge roll (game.cpp:7891). */
    var uniformRandom: (Int, Int) -> Int = { minNumber, _ -> minNumber }

    /** `Player::getDodgeChance()` (player.cpp:12923) seam (game.cpp:7890). */
    var getDodgeChance: (Player) -> Int = { player -> player.getDodgeChance() }

    /** `Monster::getHealingCombatValue(type)` (monster.cpp:335) seam (game.cpp:7915, 7991). */
    var getHealingCombatValue: (Monster, CombatType) -> Int = { _, _ -> 0 }

    /** `Creature::getAbsorbPercent(combat)` (creature.cpp:1979) seam (game.cpp:7921, 7996). */
    var getAbsorbPercent: (Creature, CombatType) -> Int = { _, _ -> 0 }

    /** `Creature::getAbsorbFlat(combat)` (creature.cpp:1962) seam (creature.cpp:930). */
    var getAbsorbFlat: (Creature, CombatType) -> Int = { _, _ -> 0 }

    /** `Creature::getIncreasePercent(combat)` (creature.cpp:1996) seam (game.cpp:7924, 7999). */
    var getIncreasePercent: (Creature, CombatType) -> Int = { _, _ -> 0 }

    /** `Creature::getReflectPercent(combat, useCharges)` (creature.cpp) seam (game.cpp:7957, 8017). */
    var getReflectPercent: (Creature, CombatType, Boolean) -> Double = { _, _, _ -> 0.0 }

    /** `Creature::getReflectFlat(combat, useCharges)` (creature.cpp) seam (game.cpp:7958, 8018). */
    var getReflectFlat: (Creature, CombatType, Boolean) -> Int = { _, _, _ -> 0 }

    /** `Creature::blockHit`'s `blockCount > 0` local (creature.cpp:961-964). */
    var combatHasDefense: (Creature) -> Boolean = { false }

    /** `Creature::canUseDefense` (creature.cpp:969). */
    var combatCanUseDefense: (Creature) -> Boolean = { false }

    /** `Creature::getDefense` (creature.cpp:967). */
    var getDefense: (Creature) -> Int = { 0 }

    /** `Creature::getArmor` (creature.cpp:977). */
    var getArmor: (Creature) -> Int = { 0 }

    /** `Creature::isImmune(combat)` (creature.cpp:955). */
    var combatIsImmune: (Creature, CombatType) -> Boolean = { _, _ -> false }

    /** `Creature::getMitigation` (creature.cpp:910-922). */
    var getMitigation: (Creature) -> Double = { 0.0 }

    /** `g_configManager().getBoolean(DISABLE_MONSTER_ARMOR)` boundary (creature.cpp:950). */
    var disableMonsterArmor: Boolean = false

    /**
     * `Combat::applyMantraAbsorb(targetPlayer, type, value)` (combat.cpp:739)
     * seam (game.cpp:7932, 8007). The default is the inert identity because
     * `Player::getMantra` (player.cpp:671) is not modelled yet; hosts can bind
     * the ported [Combat.applyMantraAbsorb] over their `CombatTarget` adapter.
     */
    var combatApplyMantraAbsorb: (Player?, CombatType, Int) -> Int = { _, _, value -> value }

    /** `Combat::doCombatHealth(target, attacker, damageReflected, params)` (game.cpp:8064) seam. */
    var combatDoCombatHealth: (Creature, Creature, CombatDamage, CombatParams) -> Unit = { _, _, _, _ -> }

    /** `uniform_random` generator of `Creature::blockHit` (creature.cpp:968, 979). */
    var combatRandom: JavaRandom = JavaRandom()

    /**
     * `target->blockHit(attacker, combatType, damage, checkDefense, checkArmor, field)`
     * (src/creatures/creature.cpp:944-1009) seam. Returns the mutated damage
     * and block type; the default composes the ported [Combat.blockHit] core
     * and [Combat.mitigateDamage] over the target-state seams above.
     */
    var combatTargetBlockHit: (Creature?, Creature, CombatType, Int, Boolean, Boolean, Boolean) -> Pair<Int, BlockType> =
        { attacker, target, combatType, damage, checkDefense, checkArmor, _ ->
            blockHitCore(attacker, target, combatType, damage, checkDefense, checkArmor)
        }

    /** `Player::sendCyclopediaCharacterNoData(type, error)` protocol seam (game.cpp:9827, 9885). */
    var sendCyclopediaCharacterNoData: (Player, Int, Int) -> Unit = { _, _, _ -> }

    /** `Player::sendCyclopediaCharacterGeneralStats()` protocol seam (game.cpp:9836). */
    var sendCyclopediaCharacterGeneralStats: (Player) -> Unit = { _ -> }

    /** `Player::achiev().sendUnlockedSecretAchievements()` protocol seam (game.cpp:9845). */
    var sendUnlockedSecretAchievements: (Player) -> Unit = { _ -> }

    /** `Player::getInventoryItemsId(true)` seam (game.cpp:9848). */
    var getInventoryItemsId: (Player) -> List<CyclopediaCharacterWriter.ItemSummaryEntry> = { emptyList() }

    /** `Player::getStoreInboxItemsId()` seam (game.cpp:9849). */
    var getStoreInboxItemsId: (Player) -> List<CyclopediaCharacterWriter.ItemSummaryEntry> = { emptyList() }

    /** `Player::getStashItems()` seam (game.cpp:9850); the C++ `StashItemList` map. */
    var getStashItems: (Player) -> Map<Int, Int> = { emptyMap() }

    /** `Player::getDepotChestItemsId()` seam (game.cpp:9851). */
    var getDepotChestItemsId: (Player) -> List<CyclopediaCharacterWriter.ItemSummaryEntry> = { emptyList() }

    /** `Player::getDepotInboxItemsId()` seam (game.cpp:9852). */
    var getDepotInboxItemsId: (Player) -> List<CyclopediaCharacterWriter.ItemSummaryEntry> = { emptyList() }

    /** `Player::sendCyclopediaCharacterItemSummary(...)` protocol seam (game.cpp:9854). */
    var sendCyclopediaCharacterItemSummary: (
        Player,
        List<CyclopediaCharacterWriter.ItemSummaryEntry>,
        List<CyclopediaCharacterWriter.ItemSummaryEntry>,
        Map<Int, Int>,
        List<CyclopediaCharacterWriter.ItemSummaryEntry>,
        List<CyclopediaCharacterWriter.ItemSummaryEntry>,
    ) -> Unit = { _, _, _, _, _, _ -> }

    /** `Player::sendCyclopediaCharacterOutfitsMounts()` protocol seam (game.cpp:9858). */
    var sendCyclopediaCharacterOutfitsMounts: (Player) -> Unit = { _ -> }

    /** `Player::sendCyclopediaCharacterStoreSummary()` protocol seam (game.cpp:9861). */
    var sendCyclopediaCharacterStoreSummary: (Player) -> Unit = { _ -> }

    /** `Player::sendCyclopediaCharacterInspection()` protocol seam (game.cpp:9864). */
    var sendCyclopediaCharacterInspection: (Player) -> Unit = { _ -> }

    /** `Player::sendCyclopediaCharacterBadges()` protocol seam (game.cpp:9867). */
    var sendCyclopediaCharacterBadges: (Player) -> Unit = { _ -> }

    /** `Player::sendCyclopediaCharacterOffenceStats()` protocol seam (game.cpp:9876). */
    var sendCyclopediaCharacterOffenceStats: (Player) -> Unit = { _ -> }

    /** `Player::sendCyclopediaCharacterDefenceStats()` protocol seam (game.cpp:9879). */
    var sendCyclopediaCharacterDefenceStats: (Player) -> Unit = { _ -> }

    /** `Player::sendCyclopediaCharacterMiscStats()` protocol seam (game.cpp:9882). */
    var sendCyclopediaCharacterMiscStats: (Player) -> Unit = { _ -> }

    companion object {
        /** `INDEX_WHEREEVER` (src/items/cylinder.hpp:18). */
        const val INDEX_WHEREEVER = -1

        /** `CONST_ME_NONE` (src/utils/utils_definitions.hpp:60). */
        const val CONST_ME_NONE = 0

        /** `CONST_ME_POFF` (src/utils/utils_definitions.hpp:63). */
        const val CONST_ME_POFF = 3

        /** `CONST_ME_BLOCKHIT` (src/utils/utils_definitions.hpp:64). */
        const val CONST_ME_BLOCKHIT = 4

        /** `CONST_ME_GREEN_RINGS` (src/utils/utils_definitions.hpp:69). */
        const val CONST_ME_GREEN_RINGS = 9

        /** `CONST_ME_HOLYDAMAGE` (src/utils/utils_definitions.hpp:100). */
        const val CONST_ME_HOLYDAMAGE = 40

        /** `CONST_ME_CRITICAL_DAMAGE` (src/utils/utils_definitions.hpp:230). */
        const val CONST_ME_CRITICAL_DAMAGE = 173

        /** `CONST_ME_FATAL` (src/utils/utils_definitions.hpp:287). */
        const val CONST_ME_FATAL = 230

        /** `CONST_ME_DODGE` (src/utils/utils_definitions.hpp:288). */
        const val CONST_ME_DODGE = 231

        /** `CONST_ME_WATER_DROP` (src/utils/utils_definitions.hpp:300). */
        const val CONST_ME_WATER_DROP = 243

        /** `reductionTimer` of `PlayerWheel::checkGiftOfLife` (player_wheel.cpp:3164). */
        private const val GIFT_OF_LIFE_COOLDOWN_REDUCTION = 60000

        /** `std::numeric_limits<uint16_t>::max()` (game.cpp:9011, 9032). */
        private const val UINT16_MAX = 65535

        /** `ITEM_STORE_COIN` (src/utils/utils_definitions.hpp:611). */
        private const val ITEM_STORE_COIN = 22118

        /** `checkCanInitCreateMarketOffer` price ceiling (src/game/game.cpp:10677). */
        private const val MAX_MARKET_OFFER_PRICE = 999_999_999_999L

        /** `removeOfferItems` wrong-item status (src/game/game.cpp:10394). */
        private const val ITEM_MARKET_INCORRECT_MESSAGE =
            "The item you tried to market is not correct. Check the item again."

        /** `SPEECHBUBBLE_TRADE` (`src/creatures/creatures_definitions.hpp:336`). */
        private const val SPEECHBUBBLE_TRADE = 2

        /** `SPEECHBUBBLE_SAILOR` (`src/creatures/creatures_definitions.hpp:339`). */
        private const val SPEECHBUBBLE_SAILOR = 5

        /** `ITEM_PODIUM_OF_VIGOUR` (`src/utils/utils_definitions.hpp:701`). */
        private const val ITEM_PODIUM_OF_VIGOUR = 38707

        /** `ITEM_PODIUM_OF_RENOWN1` (`src/utils/utils_definitions.hpp:699`). */
        private const val ITEM_PODIUM_OF_RENOWN1 = 35973

        /** `ITEM_PODIUM_OF_RENOWN2` (`src/utils/utils_definitions.hpp:700`). */
        private const val ITEM_PODIUM_OF_RENOWN2 = 35974

        /** `ITEM_FILLED_BATH_TUBE` (`src/utils/utils_definitions.hpp:665`). */
        private const val ITEM_FILLED_BATH_TUBE = 26077

        /** `Tantugly's Head` monster outfit lookTypeEx (game.cpp:11329). */
        private const val TENTUGLY_HEAD_LOOK_TYPE_EX = 35105

        /** Podium lookTypeEx sent for the `Tantugly's Head` boss (game.cpp:11330). */
        private const val TENTUGLY_PODIUM_LOOK_TYPE_EX = 39003

        /**
         * `Game::calculateHighscorePages` (src/game/game.cpp:10033): a zero page
         * size yields zero pages, otherwise the rounded-up division.
         */
        fun calculateHighscorePages(totalEntries: Long, entriesPerPage: Int): Int {
            if (entriesPerPage == 0) {
                return 0
            }
            return ((totalEntries + entriesPerPage - 1) / entriesPerPage).toInt()
        }

        /** `GROUP_TYPE_GAMEMASTER` (`src/enums/account_group_type.hpp:21`). */
        private const val GROUP_TYPE_GAMEMASTER = 4

        /** `Game::HIGHSCORE_CACHE_EXPIRATION_TIME` (game.hpp:73). */
        private const val HIGHSCORE_CACHE_EXPIRATION_SECONDS = 600L

        /** `Game::DAY_LENGTH_SECONDS` (src/game/game.hpp:916). */
        private const val DAY_LENGTH_SECONDS = 3600

        /** `Game::LIGHT_DAY_LENGTH` (src/game/game.hpp:917). */
        private const val LIGHT_DAY_LENGTH = 1440

        /** `Game::LIGHT_LEVEL_DAY` (src/game/game.hpp:918). */
        private const val LIGHT_LEVEL_DAY = 250

        /** `Game::LIGHT_LEVEL_NIGHT` (src/game/game.hpp:919). */
        private const val LIGHT_LEVEL_NIGHT = 40

        /** `Game::SUNSET` (src/game/game.hpp:920). */
        private const val SUNSET = 1050

        /** `Game::SUNRISE` (src/game/game.hpp:921). */
        private const val SUNRISE = 360

        /** `EVENT_LIGHTINTERVAL_MS` (src/game/game.hpp:66). */
        private const val EVENT_LIGHTINTERVAL_MS = 10000

        /** `EVENT_MS` (src/game/game.hpp:64). */
        private const val EVENT_MS = 10000

        /** `EVENT_CHECK_CREATURE_INTERVAL` (src/creatures/creature.hpp:47). */
        private const val EVENT_CHECK_CREATURE_INTERVAL_MS = 1000L

        /** `EVENT_LUA_GARBAGE_COLLECTION` (src/game/game.hpp:69). */
        private const val EVENT_LUA_GARBAGE_COLLECTION_MS = 600_000L

        /** `UPDATE_PLAYERS_ONLINE_DB` (src/game/game.hpp:74). */
        private const val UPDATE_PLAYERS_ONLINE_DB_MS = 600_000L

        /** `Creature::EVENT_CREATURECOUNT` (src/creatures/creature.hpp:46). */
        private const val EVENT_CREATURECOUNT = 10

        /** `Creature::EVENT_CREATURE_THINK_INTERVAL` (src/creatures/creature.hpp:47). */
        private const val EVENT_CREATURE_THINK_INTERVAL = 1000

        /** `InternalGame::TELEPORT_SPAM_WINDOW_MS` (src/game/game.cpp:211). */
        private const val TELEPORT_SPAM_WINDOW_MS = 1000L

        /** `InternalGame::TELEPORT_SPAM_LIMIT` (src/game/game.cpp:212). */
        private const val TELEPORT_SPAM_LIMIT = 10

        /** `InternalGame::TELEPORT_SPAM_BLOCK_MS` (src/game/game.cpp:213). */
        private const val TELEPORT_SPAM_BLOCK_MS = 1000L

        /** `InternalGame::TELEPORT_SUSTAINED_WINDOW_MS` (src/game/game.cpp:214). */
        private const val TELEPORT_SUSTAINED_WINDOW_MS = 10000L

        /** `InternalGame::TELEPORT_SUSTAINED_LIMIT` (src/game/game.cpp:215). */
        private const val TELEPORT_SUSTAINED_LIMIT = 30

        /** `InternalGame::TELEPORT_SUSTAINED_BLOCK_MS` (src/game/game.cpp:216). */
        private const val TELEPORT_SUSTAINED_BLOCK_MS = 5000L

        /** `InternalGame::TELEPORT_GUARD_STATE_TTL_MS` (src/game/game.cpp:218). */
        private const val TELEPORT_GUARD_STATE_TTL_MS = 60000L

        /** `InternalGame::TELEPORT_LOG_INITIAL_DELAY_MS` (src/game/game.cpp:219). */
        private const val TELEPORT_LOG_INITIAL_DELAY_MS = 1000L

        /** `InternalGame::TELEPORT_LOG_MAX_DELAY_MS` (src/game/game.cpp:220). */
        private const val TELEPORT_LOG_MAX_DELAY_MS = 60000L

        /** `InternalGame::TELEPORT_MAX_TRACKED_THINGS` (src/game/game.cpp:221). */
        private const val TELEPORT_MAX_TRACKED_THINGS = 4096

        /** `MAP_MAX_LAYERS` (src/map/map_const.hpp:17). */
        private const val MAP_MAX_LAYERS = 16

        /**
         * `classifyGraphicalEffectSource` (`src/game/game.cpp:96-108`,
         * anonymous namespace): the `SourceEffect_t` a spectator receives for a
         * graphical effect source.
         */
        private fun classifyGraphicalEffectSource(source: Creature?, spectator: Creature): SourceEffect {
            if (source == null || source is Npc) {
                return SourceEffect.GLOBAL
            }
            if (source === spectator) {
                return SourceEffect.OWN
            }
            if (source is Player) {
                return SourceEffect.OTHERS
            }
            return SourceEffect.CREATURES
        }

        /** `ItemType::stackSize` default (src/items/items.hpp:335). */
        private const val DEFAULT_STACK_SIZE = 100

        /** `Tile::queryAdd` item list ceiling (src/items/tile.cpp:899). */
        private const val MAX_TILE_ITEMS = 0x3E8

        /** `Game::playerYell` `CONDITION_YELLTICKS` duration (game.cpp:7529). */
        private const val YELL_TICKS = 30_000

        /** `Game::groupPlayersByIP` idle threshold (game.cpp:13117). */
        private const val IDLE_THRESHOLD_MS = 15L * 60L * 1000L

        /** `CONST_ME_LAST` (src/utils/utils_definitions.hpp:271): one past the last magic effect. */
        private const val CONST_ME_LAST = 304

        /** `CONST_ANI_LAST = CONST_ANI_CHERRYBOMB` (src/utils/utils_definitions.hpp:340). */
        private const val CONST_ANI_LAST = 62
    }
}
