package org.opentibia.canary.io.iologindata

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountErrors
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.creatures.OpenContainer
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerPronoun_t
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.creatures.players.pvp.Kill
import org.opentibia.canary.creatures.players.vip.VipEntry
import org.opentibia.canary.creatures.players.vip.VipGroup
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.database.AccountLoginRepository
import org.opentibia.canary.database.FeatureStateRepository
import org.opentibia.canary.database.GuidNameEx
import org.opentibia.canary.database.PlayerContainerItemState
import org.opentibia.canary.database.PlayerDepotRepository
import org.opentibia.canary.database.PlayerItemState
import org.opentibia.canary.database.PlayerItemsRepository
import org.opentibia.canary.database.PlayerKillsRepository
import org.opentibia.canary.database.PlayerLoginRepository
import org.opentibia.canary.database.PlayerRecord
import org.opentibia.canary.database.PlayerRewardsRepository
import org.opentibia.canary.database.PlayerSpellsRepository
import org.opentibia.canary.database.PlayerStashRepository
import org.opentibia.canary.database.PlayerStorageRepository
import org.opentibia.canary.database.VipLoginRepository
import org.opentibia.canary.database.deserializeItemAttributes
import org.opentibia.canary.database.serializeItemAttributes
import org.opentibia.canary.io.IOBestiaryPersistence
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.kv.ValueWrapper
import org.opentibia.canary.map.Position
import org.opentibia.canary.persistence.PlayerFeatureStateLoader
import org.opentibia.canary.systems.imbuement.ImbuementItem
import org.opentibia.canary.systems.imbuement.Imbuements
import org.opentibia.canary.systems.prey.IOPreyPersistence
import org.opentibia.canary.systems.rewardchest.Reward
import org.opentibia.canary.utils.Tools

private val logger = KotlinLogging.logger {}

/**
 * `IOLoginDataLoad::ItemsMap` (`src/io/functions/iologindata_load_player.hpp:50`):
 * the stored row id (`sid`) mapped to the materialized item and its parent
 * (`pid`). Canary's `std::map` ordering (ascending `sid`) is the caller's map.
 */
typealias ItemsMap = MutableMap<Int, Pair<Item, Int>>

/**
 * Kotlin port of `IOLoginData` (src/io/iologindata.cpp, 24 methods) wired to
 * the JDBC repositories through constructor injection instead of the
 * `g_database()` / `g_accountRepository()` / `g_playerStorageRepository()`
 * globals (AGENTS.md 1.1).
 *
 * The class keeps the C++ method names and their observable behavior:
 * authentication ([gameWorldAuthentication]), account type ([getAccountType]),
 * player load/save ([loadPlayerById], [loadPlayerByName], [loadPlayer],
 * [loadOnlyDataForOnlinePlayer], [savePlayer], [saveOnlyDataForOnlinePlayer]),
 * GUID lookups ([getNameByGuid], [getGuidByName], [getGuidByNameEx],
 * [formatPlayerName]), bank ([increaseBankBalance]) and the VIP statics
 * (`getVIPEntries`/`addVIPEntry`/`editVIPEntry`/`removeVIPEntry`/
 * `getVIPGroupEntries`/`addVIPGroupEntry`/`editVIPGroupEntry`/
 * `removeVIPGroupEntry`/`addGuidVIPGroupEntry`/`removeGuidVIPGroupEntry`).
 * [savePlayerGuard] stays private like the C++ helper.
 *
 * The `IOLoginDataLoad` item paths also materialize the stored rows into live
 * items through [loadItems] (plus [loadPlayerInventoryItems],
 * [loadPlayerDepotItems] and [loadPlayerInboxItems]), rebuilding the
 * `Container` trees in Canary's descending-`sid` order and re-arming decay
 * and imbuement decay through the injected seams. [loadPlayer] drives the
 * whole login load: the persisted row (vitals/experience/blessings), the
 * conditions, default outfit, skull, skills, kills, guild and stash, the
 * bestiary charms, inventory, store inbox, depot chests, reward chest, inbox,
 * storage map, VIP, prey/task hunting and instant spell list, plus the
 * advanced feature state and exiva restrictions when `disableIrrelevantInfo`
 * is unset. The part-1 sub-loads live in [IOLoginDataSubloads] and the
 * part-2 sub-loads in [IOLoginDataSubloads2], both reached through injected
 * seams. The `loadPlayerAnimusMastery` blob is loaded through
 * [IOLoginDataSubloads2.loadPlayerAnimusMastery] over the single
 * `players.animus_mastery` column (V21).
 *
 * The live item walks are ported as [saveItems] plus [savePlayerItem(player)]
 * (inventory), [savePlayerDepotItems(player)], [saveRewardItems(player)] and
 * [savePlayerInbox(player)]: the rows are built from the player's live trees
 * with Canary's BFS `sid` assignment from 101 and the `OPENCONTAINER`
 * attributes are re-armed from `Player::getOpenContainers` before the
 * attributes are captured. The reward chest (`Player::getRewardChest`,
 * player.cpp:2058-2063), inbox (`Player::inbox`, player.hpp:1760) and depot
 * chests (`Player::depotChests`, player.hpp:1673) sinks are reached through
 * the `Player` itself; the depot walk defaults to `Player::getDepotChests` and
 * the loader materializes the same chests through
 * `Player::getDepotChest(pid, true)`. [savePlayerGuard] calls all of the live
 * walks; the row-based overloads remain for callers that already hold the
 * `(pid, sid)` rows.
 *
 * The remaining guard saves reuse the wave-36/40 persistence classes:
 * [savePlayerStash(player)] walks `Player::getStashItems` with the C++
 * decay/ware filters, [savePlayerBestiarySystem(player)] writes the charm
 * state and cyclopedia tracker through [IOBestiaryPersistence],
 * [savePlayerFirst(player)] also writes the `conditions` stream
 * (iologindata_save_player.cpp:279-291) and the `players.animus_mastery` blob
 * (:293-299) through [PlayerConditionPersistence] and [IOBestiaryPersistence],
 * [savePlayerPreyClass(player)] and [savePlayerTaskHuntingClass(player)] write
 * the prey/task slots through [IOPreyPersistence],
 * [savePlayerExivaRestrictions(player)] writes the `exiva-restrictions` scope
 * through the composed `Player.kv` store (iologindata_save_player.cpp:878-907,
 * called by [savePlayerFirst] before the record write like the C++ :201-203),
 * and [PlayerSaveTransaction] shares one JDBC transaction across every repository
 * constructed over it (the port's `DBTransaction::executeWithinTransaction`
 * equivalent).
 *
 * The depot walk resolves the same chest instances on both directions:
 * `savePlayerDepotItems(player)` defaults to `Player::getDepotChests`
 * (src/creatures/players/player.hpp:1673) and `loadPlayerDepotItems(player,
 * rows, getDepotChest)` must be passed `{ player.getDepotChest(it, true) }`,
 * the accessor `Player::getDepotLocker` (player.cpp:2024-2058) populates for
 * boxes `1..DEPOT_BOXES`.
 *
 * PARITY GAPS (reported by the wave): the shared transaction of [savePlayer]
 * only covers repositories constructed over the supplied [PlayerSaveTransaction]
 * (the composition root owns the wiring); with the default `null` every JDBC
 * repository still opens and commits its own connection, so the guard cannot be
 * rolled back as a whole; the `players.conditions` write of [savePlayerFirst]
 * only runs when the composition root supplies [subloadRepository]; the
 * `Player::isOffline()` guard of [saveOnlyDataForOnlinePlayer] is an injected
 * predicate the composition root binds to the session presence (a direct
 * construction keeps the `{ false }` flush), and the
 * row-based overloads of the walks remain for callers that already hold the
 * persisted rows; [savePlayerStash(player)] applies the decay/ware filters only
 * when the `ItemTypeRegistry` seam is wired (an unknown item id stays unfiltered, exactly
 * like the default `Item::items` entry); the reward proxy is a real `Container`
 * ([Reward]), so `saveItems`/`childItems` resolve its children through
 * `getContainer()` like every other container and [saveRewardItems(player)]
 * serializes the DATE attribute set by `RewardChest.getReward` without a
 * save-time patch (the chest itself is not a `Cylinder` yet, so the reward's
 * parent link to the chest is not reachable; RewardChest.kt gap); and the item
 * type registry, imbuement registry and decay/imbuement-decay seams default to
 * unwired until the composition root supplies them.
 */
class IOLoginData(
    private val players: PlayerLoginRepository,
    private val accounts: AccountLoginRepository,
    private val vip: VipLoginRepository,
    private val storageRepository: PlayerStorageRepository,
    private val items: PlayerItemsRepository,
    private val depots: PlayerDepotRepository,
    private val spells: PlayerSpellsRepository,
    private val kills: PlayerKillsRepository,
    private val stash: PlayerStashRepository,
    private val rewards: PlayerRewardsRepository,
    private val features: FeatureStateRepository? = null,
    private val authType: String = AUTH_TYPE_PASSWORD,
    private val itemTypes: ItemTypeRegistry? = null,
    private val imbuements: Imbuements? = null,
    private val startDecaying: (Item) -> Unit = {},
    private val startImbuementDecay: (ImbuementItem) -> Unit = {},
    /**
     * `Player::depotChests` (src/creatures/players/player.hpp:1673): the
     * materialized depot chest map keyed by `pid` that
     * `IOLoginDataSave::savePlayerDepotItems`
     * (iologindata_save_player.cpp:583-586) iterates. The default resolves
     * `Player::getDepotChests` (the public C++ field's accessor), which the
     * loader materializes into through `Player::getDepotChest(pid, true)`, so a
     * save/load round trip walks the same chest instances; `null` keeps the
     * walk inert for callers that need the pre-wiring behavior.
     */
    private val depotChests: ((Player) -> Map<Int, Container>)? = { player -> player.getDepotChests() },
    /**
     * `IOLoginDataSave::savePlayerBestiarySystem`
     * (src/io/functions/iologindata_save_player.cpp:478-532) sink: the wave-36
     * [IOBestiaryPersistence] class. `null` keeps the bestiary save inert like
     * the other unwired seams.
     */
    private val bestiaryPersistence: IOBestiaryPersistence? = null,
    /**
     * `IOLoginDataLoad::loadPlayerAnimusMastery` (iologindata_load_player.cpp:
     * 277-289) and the inline `players.animus_mastery` write of
     * `IOLoginDataSave::savePlayerFirst` (iologindata_save_player.cpp:293-299)
     * codec seam. The bytes go through [IOBestiaryPersistence]'s interim
     * `player_animus_mastery` mapping (the port schema has no
     * `players.animus_mastery` column yet, see
     * [IOBestiaryPersistence.loadAnimusMasteryBlob]); a `null`
     * [bestiaryPersistence] keeps both directions inert like the other unwired
     * seams.
     */
    /**
     * `IOLoginDataSave::savePlayerPreyClass` / `savePlayerTaskHuntingClass`
     * (src/io/functions/iologindata_save_player.cpp:661-775) sink: the wave-36
     * [IOPreyPersistence] class carrying both slot families. `null` keeps both
     * saves inert like the other unwired seams.
     */
    private val preyPersistence: IOPreyPersistence? = null,
    /**
     * `DBTransaction::executeWithinTransaction` scope of `IOLoginData::savePlayer`
     * (src/io/iologindata.cpp:195-211): construct every repository and
     * persistence class of the save path over this [PlayerSaveTransaction] to
     * have the whole guard share one JDBC transaction. `null` keeps the
     * pre-existing per-repository commits (PARITY GAP).
     */
    private val saveTransaction: PlayerSaveTransaction? = null,
    /**
     * `g_vocations()` lookup `IOLoginDataLoad::loadPlayerSkill` resolves through
     * `Player::getVocation()` (iologindata_load_player.cpp:350-360). The port
     * replaces the global with injection (AGENTS.md 1.1); the player's own
     * [Player.vocations] registry takes precedence when set, otherwise this
     * loader-owned registry is used. `null` keeps the C++ "no vocation" early
     * return, leaving the persisted skills unapplied.
     */
    private val vocations: Vocations? = null,
    /**
     * `g_game().groups.getGroup(id)` registry read (`Groups::getGroup`,
     * src/creatures/players/grouping/groups.cpp:78-169) that
     * `IOLoginDataLoad::loadPlayerBasicInfo`
     * (src/io/functions/iologindata_load_player.cpp:140-145) resolves the
     * persisted `players.group_id` through before calling `Player::setGroup`
     * (src/creatures/players/player.hpp:519-521). The port replaces the global
     * with injection (AGENTS.md 1.1); the composition root binds the booted
     * `Groups` registry. Unwired, or with an id the registry does not know, the
     * player keeps its default group exactly like the `getGuild` miss of
     * `IOLoginDataSubloads.loadPlayerGuild` — group 1
     * (`GroupType::GROUP_TYPE_NORMAL`,
     * src/enums/account_group_type.hpp:17) therefore stays the port default
     * until the `Groups::load` XML port lands (PARITY GAP: Canary fails the
     * load for an id no group matches, while the port cannot fail without a
     * registry).
     */
    private val groups: (Int) -> Group? = { null },
    /**
     * The six `IOLoginDataLoad` sub-loads of `IOLoginData::loadPlayer`
     * (conditions, skull, guild, storage map, VIP and exiva restrictions;
     * src/io/iologindata.cpp:115-161, 192). `null` keeps them inert like the
     * other unwired seams; the composition root constructs the sub-loads over
     * its repositories (AGENTS.md 1.1).
     */
    private val subloads: IOLoginDataSubloads? = null,
    /**
     * `g_monsters()` registry whose `getMonsterTypeByRaceId`
     * (monsters.cpp:375-388) resolves the persisted bestiary tracker race ids.
     * The composition root passes the booted registry; when only the lambda
     * seam is supplied the registry stays unused.
     */
    private val monsters: Monsters? = null,
    /**
     * `g_monsters().getMonsterTypeByRaceId` (monsters.cpp:375-388) resolution of
     * the persisted bestiary tracker race ids that
     * `IOLoginDataLoad::loadPlayerBestiaryCharms`
     * (iologindata_load_player.cpp:508-519) re-adds through
     * `Player::addMonsterToCyclopediaTrackerList`; an unresolved id is dropped
     * like the C++ `if (monsterType)` guard. Defaults to the injected
     * [monsters] registry lookup.
     */
    private val monsterTypeByRaceId: (raceId: Int, isBoss: Boolean) -> MonsterType? = { raceId, isBoss ->
        monsters?.getMonsterTypeByRaceId(raceId, isBoss)
    },
    /**
     * `IOLoginDataSave::savePlayerFirst` `players`.`conditions` write
     * (iologindata_save_player.cpp:279-291). `null` keeps the pre-wiring
     * no-write behavior (PARITY GAP); the composition root constructs the same
     * [PlayerSubloadRepository] it feeds to [IOLoginDataSubloads].
     */
    private val subloadRepository: PlayerSubloadRepository? = null,
    /**
     * `Player::isOffline()` (src/creatures/players/player.hpp:481) gate of
     * `IOLoginData::saveOnlyDataForOnlinePlayer` (iologindata.cpp:269-273).
     *
     * Canary derives it from `getID() == 0`: a player object loaded off-line
     * through `IOLoginData::loadPlayerById`/`loadPlayerByName` never receives
     * the runtime creature id `Game::internalPlaceCreature` (game.cpp:1512)
     * assigns through `Player::setID` (player.cpp:225-233). The port models the
     * same presence through the session sink the listener installs on login and
     * clears on disconnect (`Player::client`, `Player.protocolSink`); the
     * composition root passes that predicate. The `{ false }` default keeps the
     * online-only flush running for direct constructions and tests.
     */
    private val isOffline: (Player) -> Boolean = { false },
) {

    /** The `Condition::serialize` codec of [savePlayerFirst] (condition.cpp:277-301). */
    private val playerConditions = PlayerConditionPersistence()

    private val featureLoader: PlayerFeatureStateLoader? by lazy(LazyThreadSafetyMode.NONE) {
        features?.let { PlayerFeatureStateLoader(it) }
    }

    /**
     * Wave-56 part 2 of the login sub-loads (bestiary charms, store inbox,
     * reward chest, prey/task hunting), separated into
     * [IOLoginDataSubloads2] so the part-1 sub-loads stay in this file.
     */
    private val subloads2: IOLoginDataSubloads2 by lazy(LazyThreadSafetyMode.NONE) {
        IOLoginDataSubloads2(
            loginData = this,
            bestiaryPersistence = bestiaryPersistence,
            preyPersistence = preyPersistence,
            itemTypes = itemTypes,
            imbuements = imbuements,
            monsterTypeByRaceId = monsterTypeByRaceId,
        )
    }

    /** Resolved `gameWorldAuthentication` result: account id plus canonical character name. */
    data class WorldAuthentication(
        val accountId: Long,
        val characterName: String,
    )

    /**
     * `IOLoginData::gameWorldAuthentication` (src/io/iologindata.cpp:24-67).
     * Returns `null` on any failure, mirroring the C++ `false` return.
     */
    fun gameWorldAuthentication(
        accountDescriptor: String,
        sessionOrPassword: String,
        characterName: String,
        oldProtocol: Boolean,
        ip: Long,
    ): WorldAuthentication? {
        val account = Account(name = accountDescriptor, repository = accounts)
        account.setProtocolCompat(oldProtocol)

        if (account.load() != AccountErrors.Ok) {
            logger.error { "Couldn't load account [$accountDescriptor]." }
            return null
        }

        val authenticated =
            if (authType == AUTH_TYPE_SESSION) account.authenticate() else account.authenticate(sessionOrPassword)
        if (!authenticated) {
            return null
        }

        if (!accounts.getCharacterByAccountIdAndName(account.getID(), characterName)) {
            logger.warn { "IP [$ip] trying to connect into another account character" }
            return null
        }

        if (account.load() != AccountErrors.Ok) {
            logger.error { "Failed to load account [$accountDescriptor]" }
            return null
        }

        val (characterIds, result) = account.getAccountPlayers()
        if (result != AccountErrors.Ok) {
            logger.error { "Failed to load account [$accountDescriptor] players" }
            return null
        }

        if ((characterIds[characterName] ?: 0uL) != 0uL) {
            logger.error { "Account [$accountDescriptor] player [$characterName] not found or deleted." }
            return null
        }

        return WorldAuthentication(account.getID(), characterName)
    }

    /** `IOLoginData::getAccountType` (src/io/iologindata.cpp:69-78). */
    fun getAccountType(accountId: Long): Int = accounts.getAccountType(accountId)

    /** `IOLoginData::loadPlayerById` (src/io/iologindata.cpp:81-86). */
    fun loadPlayerById(player: Player, id: Long, disableIrrelevantInfo: Boolean = true): Boolean {
        val record = players.loadRecordById(id) ?: return false
        return loadPlayer(player, record, disableIrrelevantInfo)
    }

    /** `IOLoginData::loadPlayerByName` (src/io/iologindata.cpp:88-93). */
    fun loadPlayerByName(player: Player, name: String, disableIrrelevantInfo: Boolean = true): Boolean {
        val record = players.loadRecordByName(name) ?: return false
        return loadPlayer(player, record, disableIrrelevantInfo)
    }

    /**
     * `IOLoginData::loadPlayer` (src/io/iologindata.cpp:95-185). The C++
     * `DBResult` parameter becomes the already-mapped [PlayerRecord]; `null`
     * mirrors the missing-result guard.
     *
     * The ported sub-loads keep Canary's order for the systems the Kotlin state
     * models: basic info, experience, blessings, conditions, animus mastery,
     * default outfit, skull, skills, kills, guild, stash, bestiary charms,
     * inventory tree, store inbox, depot chests, reward chest, inbox, storage,
     * VIP, prey/task hunting, instant spell list and (when the login is not a
     * preload) the advanced feature state plus the exiva restrictions.
     */
    fun loadPlayer(player: Player, record: PlayerRecord?, disableIrrelevantInfo: Boolean = false): Boolean {
        if (record == null) {
            logger.warn { "[loadPlayer] - Result is nullptr" }
            return false
        }

        loadPlayerBasicInfo(player, record)
        loadPlayerExperience(player, record)
        loadPlayerBlessings(player, record)
        subloads?.loadPlayerConditions(player)
        subloads2.loadPlayerAnimusMastery(player)
        subloads?.loadPlayerDefaultOutfit(player)
        subloads?.loadPlayerSkullSystem(player)
        loadPlayerSkill(player, record)
        loadPlayerKills(player)
        subloads?.loadPlayerGuild(player)
        loadPlayerStashItems(player)
        subloads2.loadPlayerBestiaryCharms(player)
        loadPlayerInventoryItems(player)
        subloads2.loadPlayerStoreInbox(player)
        loadPlayerDepotItems(player)
        subloads2.loadRewardItems(player)
        loadPlayerInboxItems(player)
        subloads?.loadPlayerStorageMap(player)
        subloads?.loadPlayerVip(player)
        subloads2.loadPlayerPreyClass(player)
        subloads2.loadPlayerTaskHuntingClass(player)
        loadPlayerInstantSpellList(player)

        if (!disableIrrelevantInfo) {
            loadOnlyDataForOnlinePlayer(player, record)
        }

        return true
    }

    /**
     * `IOLoginData::loadOnlyDataForOnlinePlayer` (src/io/iologindata.cpp:187-193):
     * loads the advanced-feature state Canary keeps out of the login preload.
     * The forge/bosstiary/wheel systems already live in the feature repository.
     * `IOLoginDataLoad::loadPlayerUpdateSystem`
     * (iologindata_load_player.cpp:1031-1039) `updateInventoryWeight` is
     * re-applied once the whole inventory tree is attached, and the exiva
     * restrictions load last like the C++ `loadPlayerExivaRestrictions`
     * (iologindata.cpp:192).
     */
    fun loadOnlyDataForOnlinePlayer(player: Player, record: PlayerRecord?) {
        featureLoader?.loadInto(player.id, player.features)
        player.updateInventoryWeight()
        subloads?.loadPlayerExivaRestrictions(player)
    }

    /**
     * `IOLoginDataLoad::loadPlayerSkill` (iologindata_load_player.cpp:344-369):
     * the 13 persisted `skill_<skill>`/`skill_<skill>_tries` column pairs are
     * written into `Player::skills` and the percent is recomputed from the
     * vocation's required tries. Like the C++, a stored tries count above the
     * next requirement is clamped to 0 and the load is inert when no vocation
     * resolves for the player.
     */
    fun loadPlayerSkill(player: Player, record: PlayerRecord) {
        val vocation = player.vocations?.getVocation(player.vocationId)
            ?: vocations?.getVocation(player.vocationId)
            ?: return

        for (skill in skills_t.FIRST..skills_t.LAST) {
            val stored = record.skills.getOrNull(skill) ?: continue
            var skillTries = stored.tries
            val nextSkillTries = vocation.getReqSkillTries(skill, stored.level + 1)
            if (skillTries > nextSkillTries) {
                skillTries = 0L
            }

            player.skills[skill].level = stored.level
            player.skills[skill].tries = skillTries
            player.skills[skill].percent = Player.getPercentLevel(skillTries, nextSkillTries)
        }
    }

    /**
     * `IOLoginData::savePlayerFirst` (src/io/functions/iologindata_save_player.cpp:191-372):
     * the `players` record write plus the blob columns of the same statement,
     * the persistent `conditions` stream (:279-291) and the `animus_mastery`
     * blob (:293-299). A failed write of either blob fails the guard exactly
     * like the C++ `savePlayerFirst` returning `false`.
     */
    fun savePlayerFirst(player: Player): Boolean {
        savePlayerExivaRestrictions(player)

        if (!players.savePlayerRecord(player)) {
            return false
        }

        if (!savePlayerConditions(player)) {
            return false
        }

        return savePlayerAnimusMastery(player)
    }

    /**
     * The inline `conditions` block of `IOLoginDataSave::savePlayerFirst`
     * (iologindata_save_player.cpp:279-291) over the [PlayerConditionPersistence]
     * codec. An unwired [subloadRepository] keeps the write inert like the
     * other unwired seams.
     */
    private fun savePlayerConditions(player: Player): Boolean {
        val repository = subloadRepository ?: return true
        return repository.saveConditions(player.id, playerConditions.savePlayerConditions(player))
    }

    /**
     * The inline `animus_mastery` block of `IOLoginDataSave::savePlayerFirst`
     * (iologindata_save_player.cpp:293-299): `Player::animusMastery()`
     * (player.cpp:12812) is serialized through [IOBestiaryPersistence] into the
     * single Canary column. An unwired sink stays inert like the other unwired
     * seams.
     */
    private fun savePlayerAnimusMastery(player: Player): Boolean {
        val persistence = bestiaryPersistence ?: return true
        persistence.saveAnimusMastery(player.id, player.features.animusMastery().getAnimusMasteries())
        return true
    }

    /**
     * `IOLoginData::savePlayer` (src/io/iologindata.cpp:195-211). The
     * `DBTransaction::executeWithinTransaction` scope becomes the injected
     * [saveTransaction]: when wired the whole guard runs in one JDBC transaction
     * and a `false`/thrown save rolls every write back.
     */
    fun savePlayer(player: Player): Boolean = try {
        val transaction = saveTransaction
        val success = if (transaction != null) {
            transaction.executeWithinTransaction { savePlayerGuard(player) }
        } else {
            savePlayerGuard(player)
        }

        if (!success) {
            logger.error { "[savePlayer] Error occurred saving player ${player.name}" }
        }
        success
    } catch (ex: Exception) {
        logger.error(ex) { "[savePlayer] Error occurred saving player ${player.name}" }
        false
    }

    /** `IOLoginData::savePlayerGuard` (src/io/iologindata.cpp:213-267); private like the C++ helper. */
    private fun savePlayerGuard(player: Player): Boolean {
        if (!savePlayerFirst(player)) {
            logger.error { "[savePlayerGuard] - Failed to save player first: ${player.name}" }
            return false
        }

        if (!savePlayerStash(player)) {
            logger.error { "[savePlayerGuard] - Failed to save player stash: ${player.name}" }
            return false
        }

        if (!savePlayerSpells(player)) {
            logger.error { "[savePlayerGuard] - Failed to save player spells: ${player.name}" }
            return false
        }

        if (!savePlayerKills(player.id, player.pvpPlayer.getUnjustifiedKills())) {
            logger.error { "[savePlayerGuard] - Failed to save player kills: ${player.name}" }
            return false
        }

        if (!savePlayerBestiarySystem(player)) {
            logger.error { "[savePlayerGuard] - Failed to save player bestiary system: ${player.name}" }
            return false
        }

        if (!savePlayerItem(player)) {
            logger.error { "[savePlayerGuard] - Failed to save player items: ${player.name}" }
            return false
        }

        if (!savePlayerDepotItems(player)) {
            logger.error { "[savePlayerGuard] - Failed to save player depot items: ${player.name}" }
            return false
        }

        if (!saveRewardItems(player)) {
            logger.error { "[savePlayerGuard] - Failed to save player reward items: ${player.name}" }
            return false
        }

        if (!savePlayerInbox(player)) {
            logger.error { "[savePlayerGuard] - Failed to save player inbox: ${player.name}" }
            return false
        }

        if (!savePlayerPreyClass(player)) {
            logger.error { "[savePlayerGuard] - Failed to save player prey class: ${player.name}" }
            return false
        }

        if (!savePlayerTaskHuntingClass(player)) {
            logger.error { "[savePlayerGuard] - Failed to save player task hunting class: ${player.name}" }
            return false
        }

        return saveOnlyDataForOnlinePlayer(player)
    }

    /**
     * `IOLoginData::saveOnlyDataForOnlinePlayer` (src/io/iologindata.cpp:269-298):
     * the advanced feature state is flushed through [featureLoader] and the
     * `Player::storage()` delta through [savePlayerStorage] (iologindata.cpp:295).
     * The C++ `if (player->isOffline()) { return; }` guard (:270-272) is the
     * injected [isOffline] seam, so a loaded off-line player keeps its unloaded
     * feature/storage state instead of overwriting the persisted rows.
     */
    fun saveOnlyDataForOnlinePlayer(player: Player): Boolean {
        if (isOffline(player)) {
            return true
        }

        featureLoader?.save(player.id, player.features)
        return savePlayerStorage(player, player.features.storage)
    }

    /**
     * `IOLoginDataSave::savePlayerExivaRestrictions`
     * (src/io/functions/iologindata_save_player.cpp:878-907): the six flags of
     * `Player::getExivaRestrictions()` are written into the
     * `Player::kv()` `exiva-restrictions` scope as `ValueWrapper(bool)`
     * exactly like the C++ implicit `ValueWrapper(bool)` constructor, and both
     * whitelists become arrays of `ValueWrapper(static_cast<int>(guid))`
     * elements. The scope is the same store the login load reads
     * (`IOLoginDataLoad::loadPlayerExivaRestrictions`,
     * iologindata_load_player.cpp:1042-1086), so the values persist through the
     * KV write-behind flush. [savePlayerFirst] calls this before the `players`
     * record write exactly like the C++ (:201-203). The C++ `nullptr` player
     * guard is unrepresentable in Kotlin ([player] is non-null) like the other
     * ported save helpers.
     */
    fun savePlayerExivaRestrictions(player: Player) {
        val restrictions = player.getExivaRestrictions()
        val scope = player.kv.scoped("exiva-restrictions")

        scope.set(KEY_ALLOW_ALL, ValueWrapper(restrictions.allowAll))
        scope.set(KEY_ALLOW_OWN_GUILD, ValueWrapper(restrictions.allowOwnGuild))
        scope.set(KEY_ALLOW_OWN_PARTY, ValueWrapper(restrictions.allowOwnParty))
        scope.set(KEY_ALLOW_VIP_LIST, ValueWrapper(restrictions.allowVipList))
        scope.set(KEY_ALLOW_PLAYER_WHITELIST, ValueWrapper(restrictions.allowPlayerWhitelist))
        scope.set(KEY_ALLOW_GUILD_WHITELIST, ValueWrapper(restrictions.allowGuildWhitelist))

        scope.set(KEY_PLAYER_WHITELIST, ValueWrapper(restrictions.playerWhitelist.map { ValueWrapper(it.toInt()) }))
        scope.set(KEY_GUILD_WHITELIST, ValueWrapper(restrictions.guildWhitelist.map { ValueWrapper(it.toInt()) }))
    }

    /** `IOLoginData::getNameByGuid` (src/io/iologindata.cpp:300-308). */
    fun getNameByGuid(guid: Long): String = players.getNameByGuid(guid)

    /** `IOLoginData::getGuidByName` (src/io/iologindata.cpp:310-320). */
    fun getGuidByName(name: String): Long = players.getGuidByName(name)

    /** `IOLoginData::getGuidByNameEx` (src/io/iologindata.cpp:322-340). */
    fun getGuidByNameEx(name: String): GuidNameEx? = players.getGuidByNameEx(name)

    /** `IOLoginData::formatPlayerName` (src/io/iologindata.cpp:342-355). */
    fun formatPlayerName(name: String): String? = players.formatPlayerName(name)

    /** `IOLoginData::increaseBankBalance` (src/io/iologindata.cpp:357-361). */
    fun increaseBankBalance(guid: Long, bankBalance: Long) {
        players.increaseBankBalance(guid, bankBalance)
    }

    /** `IOLoginData::getVIPEntries` (src/io/iologindata.cpp:363-385). */
    fun getVipEntries(accountId: Long): List<VipEntry> = vip.getVipEntries(accountId)

    /** `IOLoginData::addVIPEntry` (src/io/iologindata.cpp:387-399). */
    fun addVipEntry(accountId: Long, guid: Long, description: String, icon: Int, notify: Boolean) {
        vip.addVipEntry(accountId, guid, description, icon, notify)
    }

    /** `IOLoginData::editVIPEntry` (src/io/iologindata.cpp:401-413). */
    fun editVipEntry(accountId: Long, guid: Long, description: String, icon: Int, notify: Boolean) {
        vip.editVipEntry(accountId, guid, description, icon, notify)
    }

    /** `IOLoginData::removeVIPEntry` (src/io/iologindata.cpp:415-422). */
    fun removeVipEntry(accountId: Long, guid: Long) {
        vip.removeVipEntry(accountId, guid)
    }

    /**
     * `IOLoginData::getVIPGroupEntries` (src/io/iologindata.cpp:424-444). The
     * C++ `guid` parameter is accepted for signature parity and ignored, exactly
     * like the C++ implementation.
     */
    fun getVipGroupEntries(accountId: Long, guid: Long = 0): List<VipGroup> = vip.getVipGroupEntries(accountId, guid)

    /** `IOLoginData::addVIPGroupEntry` (src/io/iologindata.cpp:446-457). */
    fun addVipGroupEntry(groupId: Int, accountId: Long, groupName: String, customizable: Boolean) {
        vip.addVipGroupEntry(groupId, accountId, groupName, customizable)
    }

    /** `IOLoginData::editVIPGroupEntry` (src/io/iologindata.cpp:459-470). */
    fun editVipGroupEntry(groupId: Int, accountId: Long, groupName: String, customizable: Boolean) {
        vip.editVipGroupEntry(groupId, accountId, groupName, customizable)
    }

    /** `IOLoginData::removeVIPGroupEntry` (src/io/iologindata.cpp:472-479). */
    fun removeVipGroupEntry(groupId: Int, accountId: Long) {
        vip.removeVipGroupEntry(groupId, accountId)
    }

    /** `IOLoginData::addGuidVIPGroupEntry` (src/io/iologindata.cpp:481-491). */
    fun addGuidVipGroupEntry(groupId: Int, accountId: Long, guid: Long) {
        vip.addGuidVipGroupEntry(groupId, accountId, guid)
    }

    /** `IOLoginData::removeGuidVIPGroupEntry` (src/io/iologindata.cpp:493-499). */
    fun removeGuidVipGroupEntry(accountId: Long, guid: Long) {
        vip.removeGuidVipGroupEntry(accountId, guid)
    }

    // --- IOLoginDataLoad / IOLoginDataSave helpers over the Kotlin state --------

    /** `IOLoginDataLoad::loadPlayerInstantSpellList` (iologindata_load_player.cpp:527-541). */
    fun loadPlayerInstantSpellList(player: Player) {
        for (spellName in spells.load(player.id)) {
            player.spellBook.learnInstantSpell(spellName)
        }
    }

    /** `IOLoginDataSave::savePlayerSpells` (iologindata_save_player.cpp:416-445). */
    fun savePlayerSpells(player: Player): Boolean {
        spells.save(player.id, player.learnedInstantSpellList)
        return true
    }

    /** `IOLoginDataLoad::loadPlayerInventoryItems` (iologindata_load_player.cpp:543-632) row read. */
    fun loadPlayerInventoryItems(playerId: Long): List<PlayerItemState> = items.load(playerId)

    /**
     * `IOLoginDataSave::savePlayerItem` / `saveItems`
     * (iologindata_save_player.cpp:34-188, 534-564) row write for callers that
     * already hold the `(pid, sid)` tree rows (the depot/reward/inbox helpers
     * without a live `Player` sink). The live inventory walk is
     * [savePlayerItem(player)]/[saveItems].
     */
    fun savePlayerItem(playerId: Long, itemList: List<PlayerItemState>): Boolean {
        items.save(playerId, itemList)
        return true
    }

    /**
     * `IOLoginDataSave::savePlayerItem` (iologindata_save_player.cpp:534-564):
     * the rows are built from `player->inventory[CONST_SLOT_FIRST..
     * CONST_SLOT_LAST]` (each direct slot item keyed by its slot id) through
     * [saveItems] and replace the persisted inventory in one transaction. The
     * delete+insert pair of the C++ `DELETE` query and `DBInsert` is owned by
     * [PlayerItemsRepository.save].
     */
    fun savePlayerItem(player: Player): Boolean {
        val itemList = mutableListOf<Pair<Int, Item>>()
        for (slot in Slots_t.FIRST..Slots_t.LAST) {
            val item = player.getInventoryItem(Slots_t.fromValue(slot) ?: continue) ?: continue
            itemList.add(slot to item)
        }

        items.save(player.id, saveItems(player, itemList))
        return true
    }

    /**
     * `IOLoginDataSave::saveItems` (iologindata_save_player.cpp:34-188): walks
     * the given `(pid, item)` blocks (ascending slot or chest order for the
     * direct children) and their containers in BFS order, assigning the running
     * `sid` from 101. Each container item re-arms its `OPENCONTAINER` attribute
     * from `Player::getOpenContainers` (item attribute first cleared when
     * positive, then the matching open container id plus one) before both the
     * live item mutation and the row attributes are captured, exactly like the
     * C++ helper serializes `item->serializeAttr` after the same update. The
     * C++ `query_insert`/`propWriteStream` out-parameters become the returned
     * row list and a local stream. The reward proxy is a real [Container], so
     * [childItems] queues its children through `item->getContainer()` exactly
     * like every other container.
     */
    fun saveItems(player: Player, itemList: List<Pair<Int, Item>>): List<PlayerItemState> {
        val rows = mutableListOf<PlayerItemState>()
        val queue = ArrayDeque<Pair<List<Item>, Int>>()
        var runningId = 100
        val openContainers = player.getOpenContainers()

        for ((pid, item) in itemList) {
            ++runningId

            val container = item.getContainer()
            if (container != null) {
                rearmOpenContainer(container, openContainers)
            }

            childItems(item)?.let { queue.addLast(it to runningId) }

            rows.add(itemRow(player.id, pid, runningId, item))
        }

        while (queue.isNotEmpty()) {
            val (children, parentId) = queue.removeFirst()

            for (item in children) {
                ++runningId

                val subContainer = item.getContainer()
                if (subContainer != null) {
                    rearmOpenContainer(subContainer, openContainers)
                }

                childItems(item)?.let { queue.addLast(it to runningId) }

                rows.add(itemRow(player.id, parentId, runningId, item))
            }
        }

        return rows
    }

    /**
     * The `item->getContainer()` child-list lookup the C++
     * `IOLoginDataSave::saveItems` (iologin_data_save_player.cpp:64, 128) uses
     * to queue an item's children. The reward proxy is a real [Container], so
     * it needs no special case.
     */
    private fun childItems(item: Item): List<Item>? = item.getContainer()?.getItemList()

    /**
     * The container attribute update block `IOLoginDataSave::saveItems`
     * (iologindata_save_player.cpp:69-83, 137-151) runs for every container
     * item before the row is serialized.
     */
    private fun rearmOpenContainer(container: Container, openContainers: Map<Int, OpenContainer>) {
        val current = (container.getAttribute(ItemAttribute.OPENCONTAINER) as? Number)?.toLong() ?: 0L
        if (current > 0L) {
            container.setAttribute(ItemAttribute.OPENCONTAINER, 0L)
        }

        for ((containerId, openContainer) in openContainers) {
            if (openContainer.container === container) {
                container.setAttribute(ItemAttribute.OPENCONTAINER, (containerId + 1).toLong())
                break
            }
        }
    }

    /**
     * One `(pid, sid, itemtype, count, attributes)` row of
     * iologindata_save_player.cpp:98-107: the id and subtype come from the live
     * item (`item->getID()`, `item->getSubType()`) and the attributes are
     * `item->serializeAttr`, decoded into the flat row shape the shared codec
     * re-encodes on write.
     */
    private fun itemRow(playerId: Long, pid: Int, sid: Int, item: Item): PlayerItemState {
        val stream = PropWriteStream()
        item.serializeAttr(stream)
        return PlayerItemState(
            pid = pid,
            sid = sid,
            itemId = item.id,
            count = item.getSubType(),
            attributes = deserializeItemAttributes(stream.getStream()),
        )
    }

    /** `IOLoginDataLoad::loadPlayerDepotItems` (iologindata_load_player.cpp:663-708) row read. */
    fun loadPlayerDepotItems(playerId: Long): List<PlayerContainerItemState> = depots.load(playerId)

    /** `IOLoginDataSave::savePlayerDepotItems` (iologindata_save_player.cpp:566-597). */
    fun savePlayerDepotItems(playerId: Long, depotList: List<PlayerContainerItemState>): Boolean {
        depots.save(playerId, depotList)
        return true
    }

    /**
     * `IOLoginDataSave::savePlayerDepotItems`
     * (iologindata_save_player.cpp:566-597): `player->depotChests` is walked in
     * its `std::map` order ([Map.toSortedMap], ascending `pid`), every chest's
     * item list becomes a `(pid, item)` block keyed by the chest id and
     * [saveItems] assigns the `sid`s. Canary skips the whole walk, including
     * the `DELETE`, while `lastDepotId` is `-1`, i.e. before the player ever
     * opened a depot (iologindata_save_player.cpp:576). The [depotChests] sink
     * defaults to `Player::getDepotChests`, so the walk and its `DELETE` cover
     * the live chests the loader materializes; an explicit `null` keeps the
     * walk inert instead of clearing persisted rows.
     */
    fun savePlayerDepotItems(player: Player): Boolean {
        val chests = depotChests?.invoke(player) ?: return true
        if (player.getLastDepotId() == -1) {
            return true
        }

        val depotList = mutableListOf<Pair<Int, Item>>()
        for ((pid, depotChest) in chests.toSortedMap()) {
            for (item in depotChest.getItemList()) {
                depotList.add(pid to item)
            }
        }

        depots.save(player.id, saveItems(player, depotList))
        return true
    }

    /** `IOLoginDataLoad::loadPlayerInboxItems` (iologindata_load_player.cpp:710-758) row read. */
    fun loadPlayerInboxItems(playerId: Long): List<PlayerContainerItemState> = depots.loadInbox(playerId)

    // --- IOLoginDataLoad item materialization (iologindata_load_player.cpp:34-758) ---

    /**
     * `IOLoginDataLoad::loadItems` (iologindata_load_player.cpp:34-65):
     * materializes one [Item] per stored row, applies its attribute map through
     * `Item::unserializeAttr` (src/items/item.cpp:1351-1366) and keys the
     * result by `sid` with its `pid`. Canary's guards are preserved: a row
     * whose item type is not registered is skipped like a null
     * `Item::CreateItem`, a row whose attributes fail to deserialize is
     * skipped, and an exception during creation or deserialization is logged
     * and the row skipped.
     */
    fun loadItems(
        itemsMap: ItemsMap,
        rows: List<PlayerContainerItemState>,
        player: Player? = null,
    ): ItemsMap {
        for (row in rows) {
            try {
                val item = materializeItem(row.itemId, row.count, row.attributes)
                if (item == null) {
                    logger.warn {
                        "[loadItems] - Failed to create item of type ${row.itemId} for player ${player?.name}"
                    }
                    continue
                }
                itemsMap[row.sid] = item to row.pid
            } catch (ex: Exception) {
                logger.warn(ex) { "[loadItems] - Exception during the creation or deserialization of the item" }
            }
        }
        return itemsMap
    }

    /**
     * `Item::CreateItem` plus `Item::unserializeAttr`
     * (src/items/item.cpp:393-472, 1351-1366): the decoded attribute map is
     * re-encoded with the shared persistence codec and replayed through
     * `readAttr`, so every attribute follows Canary's exact deserialization
     * semantics (subtype, decaying state, custom attributes/imbuements).
     * Returns `null` when the type is unknown or the attributes are rejected,
     * mirroring Canary's null-item and failed-`unserializeAttr` paths.
     */
    private fun materializeItem(itemId: Int, count: Int, attributes: Map<ItemAttribute, Any>): Item? {
        val registry = itemTypes
        if (registry != null && registry.get(itemId) == null) {
            return null
        }

        val item = Item.create(itemId, count, registry, imbuements)
        if (!item.unserializeAttr(PropStream(serializeItemAttributes(count, attributes)))) {
            logger.warn { "[loadItems] - Failed to deserialize item attributes ${item.id}" }
            return null
        }
        return item
    }

    /**
     * `IOLoginDataLoad::loadPlayerInventoryItems` live form
     * (iologindata_load_player.cpp:543-632): the repository rows are read and
     * materialized into the player through `Player::internalAddThing(pid, item)`
     * exactly like the C++ `internalAddThing(pid, item)` call.
     */
    fun loadPlayerInventoryItems(player: Player): ItemsMap =
        loadPlayerInventoryItems(player, loadPlayerInventoryItems(player.id)) { slot, item ->
            player.internalAddThing(slot, item)
        }

    /**
     * `IOLoginDataLoad::loadPlayerInventoryItems`
     * (iologindata_load_player.cpp:543-632): items are attached in descending
     * `sid` order. A `pid` in `CONST_SLOT_FIRST..CONST_SLOT_LAST` is a direct
     * slot item and starts decaying immediately; any other `pid` is the `sid`
     * of the parent container, whose child is attached and only starts decaying
     * after the whole parent chain is established. The packed imbuement store
     * arms `g_imbuementDecay().startImbuementDecay` after the loop. Containers
     * carrying `QUICKLOOTCONTAINER`/`OBTAINCONTAINER` bits register through
     * `Player::refreshManagedContainer(..., loading = true)`.
     *
     * `internalAddThing` replaces `Player::internalAddThing` as a caller seam.
     */
    fun loadPlayerInventoryItems(
        player: Player,
        rows: List<PlayerItemState>,
        internalAddThing: (slot: Int, item: Item) -> Unit = { _, _ -> },
    ): ItemsMap {
        val inventoryItems = sortedMapOf<Int, Pair<Item, Int>>()
        val itemsToStartDecaying = mutableListOf<Item>()
        val imbuedItemsToStartDecay = mutableListOf<Item>()

        try {
            loadItems(inventoryItems, rows, player)

            for ((_, pair) in inventoryItems.entries.reversed()) {
                val (item, pid) = pair
                if (pid >= Slots_t.FIRST && pid <= Slots_t.LAST) {
                    internalAddThing(pid, item)
                    startDecaying(item)
                    if (item.hasImbuements()) {
                        imbuedItemsToStartDecay.add(item)
                    }
                } else {
                    val parentItem = inventoryItems[pid]?.first ?: continue
                    val container = parentItem.getContainer() ?: continue
                    container.internalAddThing(item)
                    // The sub-containers do not have a parent yet, since the
                    // main backpack has not been added to the player, so the
                    // decay start must be postponed until the chain is built.
                    itemsToStartDecaying.add(item)
                    if (item.hasImbuements()) {
                        imbuedItemsToStartDecay.add(item)
                    }
                }

                val itemContainer = item.getContainer()
                if (itemContainer == null) {
                    continue
                }

                if (itemContainer !== item && itemContainer.hasImbuements()) {
                    imbuedItemsToStartDecay.add(itemContainer)
                }

                for (isLootContainer in listOf(true, false)) {
                    val checkAttribute =
                        if (isLootContainer) ItemAttribute.QUICKLOOTCONTAINER else ItemAttribute.OBTAINCONTAINER
                    if (!item.hasAttribute(checkAttribute)) {
                        continue
                    }

                    val flags = (item.getAttribute(checkAttribute) as? Number)?.toLong() ?: 0L
                    for (category in 1..31) {
                        if (flags and (1L shl category) == 0L) {
                            continue
                        }
                        val resolvedCategory =
                            ObjectCategory_t.entries.firstOrNull { it.value == category } ?: continue
                        player.refreshManagedContainer(resolvedCategory, itemContainer, isLootContainer, true)
                    }
                }
            }

            for (item in itemsToStartDecaying) {
                startDecaying(item)
            }

            for (item in imbuedItemsToStartDecay) {
                startImbuementDecay(ImbuementItem(item))
            }
        } catch (ex: Exception) {
            logger.error(ex) {
                "[IOLoginDataLoad::loadPlayerInventoryItems] - Exception during inventory loading"
            }
        }

        return inventoryItems
    }

    /**
     * `IOLoginDataLoad::loadPlayerDepotItems` live form
     * (iologindata_load_player.cpp:663-708): the repository rows are read and
     * materialized through `Player::getDepotChest(pid, true)`, the accessor
     * `Player::getDepotLocker` (player.cpp:2024-2058) populates for boxes
     * `1..DEPOT_BOXES`.
     */
    fun loadPlayerDepotItems(player: Player): ItemsMap =
        loadPlayerDepotItems(player, loadPlayerDepotItems(player.id)) { depotId ->
            player.getDepotChest(depotId, true)
        }

    /**
     * `IOLoginDataLoad::loadPlayerDepotItems`
     * (iologindata_load_player.cpp:663-708): `pid` below 100 targets the town
     * depot chest (`Player::getDepotChest(pid, true)`), any other `pid` is the
     * `sid` of the parent container. Direct chest children start decaying
     * immediately, nested items only after the whole parent chain is attached.
     */
    fun loadPlayerDepotItems(
        player: Player,
        rows: List<PlayerContainerItemState>,
        getDepotChest: (depotId: Int) -> Container?,
    ): ItemsMap {
        val depotItems = sortedMapOf<Int, Pair<Item, Int>>()
        loadItems(depotItems, rows, player)

        val itemsToStartDecaying = mutableListOf<Item>()
        for ((_, pair) in depotItems.entries.reversed()) {
            val (item, pid) = pair
            if (pid >= 0 && pid < 100) {
                val depotChest = getDepotChest(pid)
                if (depotChest != null) {
                    depotChest.internalAddThing(item)
                    startDecaying(item)
                }
            } else {
                val parentItem = depotItems[pid]?.first ?: continue
                val container = parentItem.getContainer() ?: continue
                container.internalAddThing(item)
                itemsToStartDecaying.add(item)
            }
        }

        for (item in itemsToStartDecaying) {
            startDecaying(item)
        }

        return depotItems
    }

    /**
     * `IOLoginDataLoad::loadPlayerInboxItems` live form
     * (iologindata_load_player.cpp:710-758): the repository rows are read and
     * materialized into `Player::inbox` (player.hpp:1760), the container the
     * depot locker and the store inbox delivery share.
     */
    fun loadPlayerInboxItems(player: Player): ItemsMap =
        loadPlayerInboxItems(player, loadPlayerInboxItems(player.id)) { player.inbox }

    /**
     * `IOLoginDataLoad::loadPlayerInboxItems`
     * (iologindata_load_player.cpp:710-758): `pid` below 100 targets the
     * player inbox, any other `pid` is the `sid` of the parent container. A
     * missing inbox warns and skips the whole attachment (and the deferred
     * decay loop), exactly like the C++ early return.
     */
    fun loadPlayerInboxItems(
        player: Player,
        rows: List<PlayerContainerItemState>,
        getInbox: () -> Container?,
    ): ItemsMap {
        val inboxItems = sortedMapOf<Int, Pair<Item, Int>>()
        loadItems(inboxItems, rows, player)

        val playerInbox = getInbox()
        if (playerInbox == null) {
            logger.warn { "[loadPlayerInboxItems] - Player inbox nullptr" }
            return inboxItems
        }

        val itemsToStartDecaying = mutableListOf<Item>()
        for ((_, pair) in inboxItems.entries.reversed()) {
            val (item, pid) = pair
            if (pid >= 0 && pid < 100) {
                playerInbox.internalAddThing(item)
                startDecaying(item)
            } else {
                val parentItem = inboxItems[pid]?.first ?: continue
                val container = parentItem.getContainer() ?: continue
                container.internalAddThing(item)
                itemsToStartDecaying.add(item)
            }
        }

        for (item in itemsToStartDecaying) {
            startDecaying(item)
        }

        return inboxItems
    }

    /** `IOLoginDataSave::savePlayerInbox` (iologindata_save_player.cpp:634-659). */
    fun savePlayerInbox(playerId: Long, inboxList: List<PlayerContainerItemState>): Boolean {
        depots.saveInbox(playerId, inboxList)
        return true
    }

    /**
     * `IOLoginDataSave::savePlayerInbox` (iologindata_save_player.cpp:634-659):
     * every direct item of `Player::getInbox()->getItemList()` (player.hpp:374)
     * becomes a `(pid = 0, item)` block and [saveItems] walks its containers in
     * BFS order.
     */
    fun savePlayerInbox(player: Player): Boolean {
        val inboxList = mutableListOf<Pair<Int, Item>>()
        for (item in player.inbox.getItemList()) {
            inboxList.add(0 to item)
        }

        depots.saveInbox(player.id, saveItems(player, inboxList))
        return true
    }

    /**
     * `IOLoginDataLoad::loadPlayerKills` (iologindata_load_player.cpp:371-392).
     * Rows outside the `FRAG_TIME` window are dropped like the C++ loop.
     */
    fun loadPlayerKills(playerId: Long, now: Long = getTimeNow(), fragTime: Long = FRAG_TIME_DEFAULT_SECONDS): List<Kill> {
        val result = mutableListOf<Kill>()
        for (kill in kills.load(playerId)) {
            if (now - kill.time <= fragTime) {
                result.add(kill)
            }
        }
        return result
    }

    /**
     * `IOLoginDataLoad::loadPlayerKills` live form
     * (iologindata_load_player.cpp:371-392): the in-window rows replace
     * `Player::unjustifiedKills` and `Player::updateLastKillTimeCache` receives
     * the newest in-window kill time. Like the C++, both mutations are skipped
     * when the repository holds no rows at all.
     */
    fun loadPlayerKills(player: Player, now: Long = getTimeNow(), fragTime: Long = FRAG_TIME_DEFAULT_SECONDS) {
        val rows = kills.load(player.id)
        if (rows.isEmpty()) {
            return
        }

        var lastKillTime = 0L
        val loadedKills = mutableListOf<Kill>()
        for (kill in rows) {
            if (now - kill.time <= fragTime) {
                loadedKills.add(kill)
                lastKillTime = maxOf(lastKillTime, kill.time)
            }
        }

        player.pvpPlayer.setUnjustifiedKills(loadedKills)
        player.pvpPlayer.updateLastKillTimeCache(lastKillTime)
    }

    /** `IOLoginDataSave::savePlayerKills` (iologindata_save_player.cpp:447-476). */
    fun savePlayerKills(playerId: Long, killList: List<Kill>): Boolean {
        kills.save(playerId, killList)
        return true
    }

    /** `IOLoginDataLoad::loadPlayerStashItems` (iologindata_load_player.cpp:444-470) row read. */
    fun loadPlayerStashItems(playerId: Long): Map<Int, Int> = stash.load(playerId)

    /**
     * `IOLoginDataLoad::loadPlayerStashItems` live form
     * (iologindata_load_player.cpp:444-470): every persisted counter is
     * re-added through `Player::addItemOnStash`, with Canary's decay and ware
     * filters applied through the injected [itemTypes] registry. An unwired
     * registry or unknown item id keeps the default `ItemType` behavior
     * (no filter), exactly like the loader's `Item::items[itemId]` default.
     */
    fun loadPlayerStashItems(player: Player) {
        for ((itemId, itemCount) in loadPlayerStashItems(player.id)) {
            val itemType = itemTypes?.get(itemId)
            if (itemType != null) {
                if (itemType.decayTo >= 0 && itemType.decayTime > 0) {
                    continue
                }
                if (itemType.wareId > 0 && itemType.wareId != itemType.id) {
                    logger.warn {
                        "[loadPlayerStashItems] - Item ID $itemId is a ware item, for player: ${player.name}, skipping."
                    }
                    continue
                }
            }
            player.stash.addItemOnStash(itemId, itemCount)
        }
    }

    /** `IOLoginDataSave::savePlayerStash` (iologindata_save_player.cpp:374-414). */
    fun savePlayerStash(playerId: Long, stashItems: Map<Int, Int>): Boolean {
        stash.save(playerId, stashItems)
        return true
    }

    /**
     * `IOLoginDataSave::savePlayerStash` (iologindata_save_player.cpp:374-414):
     * `Player::getStashItems` (player.cpp:5437) is iterated in its ascending
     * item-id order, skipping decaying items (`decayTo >= 0 && decayTime > 0`)
     * and market wares whose `wareId` differs from the item id, then the
     * remaining counters replace the persisted rows through
     * [PlayerStashRepository.save]. The C++ `Item::items` lookup becomes the
     * injected [itemTypes]; an unknown id keeps the default `ItemType`
     * behavior (no filter), and an unwired registry filters nothing.
     */
    fun savePlayerStash(player: Player): Boolean {
        val stashItems = mutableMapOf<Int, Int>()
        for ((itemId, itemCount) in player.stash.getStashItems()) {
            val itemType = itemTypes?.get(itemId)
            if (itemType != null) {
                if (itemType.decayTo >= 0 && itemType.decayTime > 0) {
                    continue
                }
                if (itemType.wareId > 0 && itemType.wareId != itemType.id) {
                    logger.warn {
                        "[savePlayerStash] - Item ID $itemId is a ware item, for player: ${player.name}, skipping."
                    }
                    continue
                }
            }
            stashItems[itemId] = itemCount
        }

        stash.save(player.id, stashItems)
        return true
    }

    /**
     * `IOLoginDataSave::savePlayerBestiarySystem`
     * (iologindata_save_player.cpp:478-532): the charm points/echoes/rune bits,
     * the `charms` blob (raceId + tier per rune) and the tracker list built from
     * `Player::getCyclopediaMonsterTrackerSet(false)` race ids
     * (player.cpp:941-943) are persisted through [IOBestiaryPersistence]. A
     * missing sink stays inert like the other unwired seams.
     */
    fun savePlayerBestiarySystem(player: Player): Boolean {
        val persistence = bestiaryPersistence ?: return true
        return try {
            persistence.savePlayerBestiarySystem(
                player.id,
                player.features.bestiary,
                player.getCyclopediaMonsterTrackerSet(false).map { it.info.raceid },
            )
            true
        } catch (ex: Exception) {
            logger.error(ex) { "[savePlayerBestiarySystem] - Failed to save player bestiary system: ${player.name}" }
            false
        }
    }

    /**
     * `IOLoginDataSave::savePlayerPreyClass`
     * (iologindata_save_player.cpp:661-718): every live prey slot of
     * `Player::features.prey` is upserted through [IOPreyPersistence], which
     * owns the `PREY_ENABLED` gate and the `raceIdList` blob encoding. A missing
     * sink stays inert like the other unwired seams.
     */
    fun savePlayerPreyClass(player: Player): Boolean {
        val persistence = preyPersistence ?: return true
        return persistence.savePlayerPreyClass(player.features.prey, player.id)
    }

    /**
     * `IOLoginDataSave::savePlayerTaskHuntingClass`
     * (iologindata_save_player.cpp:720-775): every live task-hunting slot of
     * `Player::features.taskHunting` is upserted through [IOPreyPersistence],
     * which owns the `TASK_HUNTING_ENABLED` gate and the `raceIdList` blob
     * encoding. A missing sink stays inert like the other unwired seams.
     */
    fun savePlayerTaskHuntingClass(player: Player): Boolean {
        val persistence = preyPersistence ?: return true
        return persistence.savePlayerTaskHuntingClass(player.features.taskHunting, player.id)
    }

    /** `IOLoginDataLoad::loadRewardItems` (iologindata_load_player.cpp:645-661) row read. */
    fun loadRewardItems(playerId: Long): List<PlayerContainerItemState> = rewards.load(playerId)

    /** `IOLoginDataSave::saveRewardItems` (iologindata_save_player.cpp:599-632). */
    fun saveRewardItems(playerId: Long, rewardList: List<PlayerContainerItemState>): Boolean {
        rewards.save(playerId, rewardList)
        return true
    }

    /**
     * `IOLoginDataSave::saveRewardItems` (iologindata_save_player.cpp:599-632):
     * every `Player::rewardMap` id is resolved through
     * `Player::getReward(rewardId, false)` in `std::map` order (ascending
     * `rewardId`) and kept only when the proxy is non-empty and at most seven
     * days old, then [saveItems] walks each kept reward with `pid` 0.
     * `Player::getReward` stores the id in the DATE attribute
     * (player.cpp:2067-2083) and `RewardChest.getReward` sets the same
     * attribute on the Kotlin proxy, so `serializeAttr` carries it without any
     * save-time patch. The age test mirrors the C++ `uint64_t` subtraction,
     * which skips a reward whose id is newer than [now].
     */
    fun saveRewardItems(player: Player, now: Long = Tools.getTimeMsNow()): Boolean {
        val rewardChest = player.getRewardChest()
        val rewardListItems = mutableListOf<Pair<Int, Item>>()

        for (rewardId in rewardChest.getRewardList().sorted()) {
            val reward = rewardChest.getReward(rewardId, autoCreate = false) ?: continue

            val age = now - rewardId
            if (!reward.empty() && age in 0..REWARD_EXPIRATION_MS) {
                rewardListItems.add(0 to reward)
            }
        }

        rewards.save(player.id, saveItems(player, rewardListItems))
        return true
    }

    /** `IOLoginDataLoad::loadPlayerStorageMap` (iologindata_load_player.cpp:760-768). */
    fun loadPlayerStorageMap(player: Player, storage: PlayerStorage) {
        storage.ingest(storageRepository.load(player.id))
    }

    /** `IOLoginDataSave::savePlayerStorage` (iologindata_save_player.cpp:837-859). */
    fun savePlayerStorage(player: Player, storage: PlayerStorage): Boolean {
        storage.prepareForPersist()
        val delta = storage.delta()

        if (delta.deletions.isNotEmpty() && !storageRepository.deleteKeys(player.id, delta.deletions)) {
            return false
        }

        if (delta.upserts.isNotEmpty() && !storageRepository.upsert(player.id, delta.upserts)) {
            return false
        }

        storage.clearDirty()
        return true
    }

    private fun loadPlayerBasicInfo(player: Player, record: PlayerRecord) {
        player.vocationId = record.vocation
        // `IOLoginDataLoad::loadPlayerBasicInfo` group load
        // (src/io/functions/iologindata_load_player.cpp:140-145): resolve the
        // persisted `group_id` through the groups registry and `setGroup` it.
        // A miss (unwired registry or unknown id) keeps the player's current
        // group — group 1 is `GROUP_TYPE_NORMAL` and keeps the default.
        groups(record.groupId)?.let { player.group = it }
        player.level = maxOf(1, record.level)
        player.health = record.health
        player.maxHealth = record.healthMax
        player.mana = record.mana
        player.maxMana = record.manaMax
        player.capacity = record.capacity
        player.speed = record.speed
        player.position = Position(record.posX, record.posY, record.posZ)
        player.setSex(PlayerSex_t.entries.firstOrNull { it.value == record.sex } ?: PlayerSex_t.FEMALE)
        player.setPronoun(
            PlayerPronoun_t.entries.firstOrNull { it.value == record.pronoun } ?: PlayerPronoun_t.PLAYERPRONOUN_UNSET,
        )
    }

    private fun loadPlayerExperience(player: Player, record: PlayerRecord) {
        val experience = record.experience
        val currExpCount = Player.getExpForLevel(player.level)
        val nextExpCount = Player.getExpForLevel(player.level + 1)

        player.experience = if (experience < currExpCount || experience > nextExpCount) currExpCount else experience
    }

    private fun loadPlayerBlessings(player: Player, record: PlayerRecord) {
        for (index in 1..8) {
            player.addBlessing(index, record.blessings[index - 1])
        }
    }

    companion object {
        /** `AUTH_TYPE` config value `"password"` (configmanager.cpp). */
        const val AUTH_TYPE_PASSWORD = "password"

        /** `AUTH_TYPE` config value `"session"` (configmanager.cpp). */
        const val AUTH_TYPE_SESSION = "session"

        /** `FRAG_TIME` config default in seconds (`configmanager.hpp`). */
        const val FRAG_TIME_DEFAULT_SECONDS = 86_400L

        /** `1000 * 60 * 60 * 24 * 7` reward age window (iologindata_save_player.cpp:620). */
        const val REWARD_EXPIRATION_MS = 604_800_000L

        /** `savePlayerExivaRestrictions` scope keys (iologindata_save_player.cpp:887-906). */
        private const val KEY_ALLOW_ALL = "allowAll"
        private const val KEY_ALLOW_OWN_GUILD = "allowOwnGuild"
        private const val KEY_ALLOW_OWN_PARTY = "allowOwnParty"
        private const val KEY_ALLOW_VIP_LIST = "allowVipList"
        private const val KEY_ALLOW_PLAYER_WHITELIST = "allowPlayerWhitelist"
        private const val KEY_ALLOW_GUILD_WHITELIST = "allowGuildWhitelist"
        private const val KEY_PLAYER_WHITELIST = "playerWhitelist"
        private const val KEY_GUILD_WHITELIST = "guildWhitelist"
    }
}
