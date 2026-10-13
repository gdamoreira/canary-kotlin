package org.opentibia.canary.game

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.chat.CHANNEL_GUILD
import org.opentibia.canary.chat.ChatClient
import org.opentibia.canary.chat.NoopChatClient
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.players.components.HighscoreCategories_t
import org.opentibia.canary.creatures.players.vip.PlayerVip
import org.opentibia.canary.creatures.players.vip.VipStatus
import org.opentibia.canary.database.PlayerLoginRepository
import org.opentibia.canary.game.movement.areInRange
import org.opentibia.canary.game.movement.getDistanceX
import org.opentibia.canary.game.movement.getDistanceY
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.Zone
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.house.TradeEvents_t
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.protocol.SourceEffect
import org.opentibia.canary.persistence.PlayerStorage
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.social.guild.GuildRepository
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

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

/**
 * `struct HighscoreCategory` (`src/server/server_definitions.hpp:174`): the
 * `m_name` / `m_id` pair exposed by `Game::getHighscoreCategories`.
 */
data class HighscoreCategory(
    val name: String,
    val id: Int,
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
 *  - `beforeCreatureZoneChange` (game.cpp:12417) / `afterCreatureZoneChange` (game.cpp:12447)
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
) {
    private val players = mutableMapOf<Long, Player>()

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

    /** `Creature::getParent()` equivalent (src/creatures/creature.hpp): the tile each placed creature lives on. */
    private val placedCreatures = IdentityHashMap<Creature, Tile>()

    /** `Item::getParent()` (src/items/item.hpp): the tile each item added through this facade lives on. */
    private val itemParents = IdentityHashMap<Item, Tile>()

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

    /** `Player::sendCancelMessage` protocol seam (player.cpp). */
    var sendCancelMessage: (Player, ReturnValue) -> Unit = { _, _ -> }

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
     * (game.cpp:5816, 6001) as an overridable seam.
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

    /** `Game::addPlayer` (src/game/game.cpp:11624). */
    fun addPlayer(player: Player) {
        players[player.id] = player
    }

    /** `Game::removePlayer` (src/game/game.cpp:11631). */
    fun removePlayer(player: Player) {
        players.remove(player.id)
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

    /**
     * Kotlin accessor for Canary's public `Game::map` member (src/game/game.hpp:627) as
     * used at call sites such as `g_game().map.getTile(pos)` (src/game/game.cpp:1117);
     * `Map::getTile` itself is defined at src/map/map.hpp:77.
     */
    fun getTile(position: Position): Tile? = map.getTile(position)

    /**
     * `Game::internalPlaceCreature` (src/game/game.cpp:1495) with the placement
     * fallback of `Map::placeCreature` (src/map/map.cpp:559).
     *
     * A creature is rejected when it already has a parent tile (creature.hpp
     * `getParent()`), and placement falls back to the surrounding tiles of
     * `Map::placeCreature`'s `normalRelList`/`extendedRelList`. `forced` mirrors
     * `forceLogin` and bypasses the tile `queryAdd` result. `creatureCheck` maps to
     * `addCreatureCheck`/`onPlacedCreature` (game.cpp:1516-1519) and the tile-zone
     * transitions run through [beforeCreatureZoneChange]/[afterCreatureZoneChange]
     * with the [tileZones] resolver.
     *
     * Parity gaps: protection-zone, teleport and `isSightClear` checks need tile
     * flags/raycasting that the Kotlin [Tile] does not model; monsters' push/field
     * rules are approximated by a plain occupancy check. `Creature::setID`,
     * `Creature::addList` and `Creature::updateCalculatedStepSpeed`
     * (game.cpp:1512-1514) have no Kotlin counterpart.
     */
    fun internalPlaceCreature(
        creature: Creature,
        pos: Position,
        extendedPos: Boolean = false,
        forced: Boolean = false,
        creatureCheck: Boolean = false,
    ): Boolean {
        if (placedCreatures.containsKey(creature)) {
            return false
        }

        val centerTile = map.getTile(pos)
        val destination = if (centerTile != null && (forced || canAddCreature(centerTile))) {
            centerTile to pos
        } else {
            findFallbackTile(pos, extendedPos, forced)
        }
        if (destination == null) {
            return false
        }

        val toZones = tileZones(destination.second)
        if (beforeCreatureZoneChange(creature, emptySet(), toZones) != ReturnValue.NOERROR) {
            return false
        }

        creature.position = destination.second
        placedCreatures[creature] = destination.first

        if (creatureCheck) {
            addCreatureCheck(creature)
            creature.onPlacedCreature()
        }

        afterCreatureZoneChange(creature, emptySet(), toZones)
        return true
    }

    /**
     * `Game::placeCreature` (src/game/game.cpp:1524): place through
     * [internalPlaceCreature], then fan out the appear notification to every
     * spectator. A player spectator forces [addCreatureCheck], and
     * `Creature::onPlacedCreature` closes the placement.
     *
     * Parity gaps: `Cylinder::postAddNotification` (game.cpp:1543-1546) and the
     * `MONSTER_PERF_TEST_FORCE_ACTIVE` creature-check arm (game.cpp:1539) are
     * not modeled; the spectator search is the facade's [spectatorsOf] subset of
     * `Spectators().find<Creature>(pos, multifloor = true)`.
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

        var hasPlayerSpectators = false
        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val tmpPlayer = spectator as? Player
            if (tmpPlayer != null) {
                tmpPlayer.sendCreatureAppear(creature, creature.position, true)
                hasPlayerSpectators = true
            }
            spectator.onCreatureAppear(creature, true)
        }

        if (hasPlayerSpectators) {
            addCreatureCheck(creature)
        }

        creature.onPlacedCreature()
        return true
    }

    /**
     * `Game::removeCreature` (src/game/game.cpp:1551): rejected for creatures
     * already flagged by `Creature::isRemoved()`, otherwise the tile spectators
     * receive the removal, the master link is cleared, the zone transition and
     * `Creature::setRemoved()` run, the creature leaves the think buckets and
     * every summon is removed first with `setSkillLoss(false)`.
     *
     * Parity gaps: `Tile::getClientIndexOfCreature` is not modeled, so the
     * `sendRemoveTileThing` fanout always uses the no-op `-1` stack position;
     * `Cylinder::postRemoveNotification`, `Creature::removeList` and the
     * team-finder logout cleanup (game.cpp:1599-1617) are not modeled. Canary
     * keeps going when `getTile()` is null; the facade rejects a creature that
     * was never placed instead.
     */
    fun removeCreature(creature: Creature, isLogout: Boolean = true): Boolean {
        if (creature.isRemoved()) {
            return false
        }
        val tile = placedCreatures[creature] ?: return false
        val fromZones = tileZones(tile.position)
        val spectators = spectatorsOf(tile.position, multifloor = true)

        placedCreatures.remove(creature)

        for (spectator in spectators) {
            if (spectator is Player) {
                spectator.sendRemoveTileThing(tile.position, -1)
            }
        }

        for (spectator in spectators) {
            spectator.onRemoveCreature(creature, isLogout)
        }

        val master = creature.getMaster()
        if (master != null && !master.isRemoved()) {
            creature.setMaster(null)
        }

        afterCreatureZoneChange(creature, fromZones, emptySet())

        creature.setRemoved()
        removeCreatureCheck(creature)

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
     * `Creature::onThink(EVENT_CREATURE_THINK_INTERVAL)`. Dead or unflagged
     * entries clear `inCheckCreaturesVector` and are pruned.
     *
     * PARITY GAPS: `Monster::trySchedulePostThink` and the non-monster
     * `Creature::onAttacking` call are not ported; the condition tick
     * (`executeConditions`) is folded into [Creature.onThink] by the Kotlin
     * creature port, so it must not run twice here.
     */
    fun checkCreatures() {
        val bucket = checkCreatureLists[checkCreatureIndex]
        val iterator = bucket.iterator()
        while (iterator.hasNext()) {
            val creature = iterator.next().get()
            if (creature != null && creature.creatureCheck && creature.isAlive()) {
                creature.onThink(EVENT_CREATURE_THINK_INTERVAL)
            } else {
                creature?.inCheckCreaturesVector = false
                iterator.remove()
            }
        }

        checkCreatureIndex = (checkCreatureIndex + 1) % EVENT_CREATURECOUNT
    }

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

    /**
     * `Game::playerStopAutoWalk` (src/game/game.cpp:4567): unknown ids are
     * ignored, otherwise `Player::stopWalk` latches `cancelNextWalk`.
     */
    fun playerStopAutoWalk(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        player.stopWalk()
    }

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

        if (!canThrowObjectTo(tradePartner.position, player.position)) {
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
        if (!canThrowObjectTo(tradePartner.position, player.position)) {
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
    private fun internalGetThing(player: Player, pos: Position, index: Int, itemId: Int, type: StackPosType_t): Item? {
        if (pos.x == 0xFFFF || type != StackPosType_t.STACKPOS_TOPDOWN_ITEM) {
            return null
        }

        val tile = map.getTile(pos) ?: return null
        return topDownItem(tile)
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

    /** `Tile::getTopDownItem` equivalent (src/items/tile.cpp:408): last inserted down item. */
    private fun topDownItem(tile: Tile): Item? = tile.items.lastOrNull()

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
     * `Tile::addThing(int32_t, Thing)` (src/items/tile.cpp:1131). The index is accepted
     * for parity but ignored, exactly like the C++ overload does for plain down items.
     */
    private fun addThing(tile: Tile, item: Item, index: Int) {
        tile.addItem(item)
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
     * Canary reads the map-sector creature lists; the port indexes the facade's
     * own [placedCreatures] until the sector lists are modeled.
     */
    private fun spectatorsOf(centerPos: Position, multifloor: Boolean): List<Creature> {
        val minRangeZ: Int
        val maxRangeZ: Int
        if (!multifloor) {
            minRangeZ = centerPos.z
            maxRangeZ = centerPos.z
        } else if (centerPos.z > Creature.MAP_INIT_SURFACE_LAYER) {
            minRangeZ = maxOf(centerPos.z - Creature.MAP_LAYER_VIEW_LIMIT, 0)
            maxRangeZ = minOf(centerPos.z + Creature.MAP_LAYER_VIEW_LIMIT, MAP_MAX_LAYERS - 1)
        } else if (centerPos.z == Creature.MAP_INIT_SURFACE_LAYER - 1) {
            minRangeZ = 0
            maxRangeZ = Creature.MAP_INIT_SURFACE_LAYER - 1 + Creature.MAP_LAYER_VIEW_LIMIT
        } else if (centerPos.z == Creature.MAP_INIT_SURFACE_LAYER) {
            minRangeZ = 0
            maxRangeZ = Creature.MAP_INIT_SURFACE_LAYER + Creature.MAP_LAYER_VIEW_LIMIT
        } else {
            minRangeZ = 0
            maxRangeZ = Creature.MAP_INIT_SURFACE_LAYER
        }

        val minX = centerPos.x - Creature.MAP_MAX_VIEW_PORT_X
        val maxX = centerPos.x + Creature.MAP_MAX_VIEW_PORT_X
        val minY = centerPos.y - Creature.MAP_MAX_VIEW_PORT_Y
        val maxY = centerPos.y + Creature.MAP_MAX_VIEW_PORT_Y

        val spectators = mutableListOf<Creature>()
        for (creature in placedCreatures.keys) {
            val pos = creature.position
            if (pos.z - minRangeZ !in 0..(maxRangeZ - minRangeZ)) {
                continue
            }
            val offsetZ = centerPos.z - pos.z
            if (pos.x - offsetZ < minX || pos.x - offsetZ > maxX) {
                continue
            }
            if (pos.y - offsetZ < minY || pos.y - offsetZ > maxY) {
                continue
            }
            spectators.add(creature)
        }
        return spectators
    }

    /** `Tile::queryAdd` creature branch (src/items/tile.cpp:700-854). */
    private fun canAddCreature(tile: Tile): Boolean {
        if (tile.ground == null) {
            return false
        }
        return placedCreatures.values.none { it === tile }
    }

    /** `Map::placeCreature` fallback search (src/map/map.cpp:587-663). */
    private fun findFallbackTile(centerPos: Position, extendedPos: Boolean, forced: Boolean): Pair<Tile, Position>? {
        val relList = if (extendedPos) EXTENDED_REL_LIST else NORMAL_REL_LIST
        for ((dx, dy) in relList.shuffled()) {
            val tryPos = Position(centerPos.x + dx, centerPos.y + dy, centerPos.z)
            val tile = map.getTile(tryPos) ?: continue
            if (forced || canAddCreature(tile)) {
                return tile to tryPos
            }
        }
        return null
    }

    companion object {
        /** `INDEX_WHEREEVER` (src/items/cylinder.hpp:18). */
        const val INDEX_WHEREEVER = -1

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

        /** `Creature::EVENT_CREATURECOUNT` (src/creatures/creature.hpp:46). */
        private const val EVENT_CREATURECOUNT = 10

        /** `Creature::EVENT_CREATURE_THINK_INTERVAL` (src/creatures/creature.hpp:47). */
        private const val EVENT_CREATURE_THINK_INTERVAL = 1000

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

        /** `Map::placeCreature` extended search ring (src/map/map.cpp:588). */
        private val EXTENDED_REL_LIST = listOf(
            0 to -2,
            -1 to -1, 0 to -1, 1 to -1,
            -2 to 0, -1 to 0, 1 to 0, 2 to 0,
            -1 to 1, 0 to 1, 1 to 1,
            0 to 2,
        )

        /** `Map::placeCreature` normal search ring (src/map/map.cpp:603). */
        private val NORMAL_REL_LIST = listOf(
            -1 to -1, 0 to -1, 1 to -1,
            -1 to 0, 1 to 0,
            -1 to 1, 0 to 1, 1 to 1,
        )
    }
}
