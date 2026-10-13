package org.opentibia.canary.io.iologindata

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountErrors
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerPronoun_t
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.creatures.players.pvp.Kill
import org.opentibia.canary.creatures.players.vip.VipEntry
import org.opentibia.canary.creatures.players.vip.VipGroup
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
import org.opentibia.canary.map.Position
import org.opentibia.canary.persistence.PlayerFeatureStateLoader

private val logger = KotlinLogging.logger {}

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
 * PARITY GAPS (reported by the wave): `savePlayer` cannot wrap every
 * repository in one shared transaction because each JDBC repository owns its
 * connection; the Canary offline guard (`Player::isOffline`) has no Kotlin
 * equivalent yet, so [saveOnlyDataForOnlinePlayer] always flushes; and the
 * `PlayerPvp` kill list, stash and reward chest have no live `Player` sink in
 * this port, so those helpers take the persisted values directly.
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
) {

    private val featureLoader: PlayerFeatureStateLoader? by lazy(LazyThreadSafetyMode.NONE) {
        features?.let { PlayerFeatureStateLoader(it) }
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
     */
    fun loadPlayer(player: Player, record: PlayerRecord?, disableIrrelevantInfo: Boolean = false): Boolean {
        if (record == null) {
            logger.warn { "[loadPlayer] - Result is nullptr" }
            return false
        }

        loadPlayerBasicInfo(player, record)
        loadPlayerExperience(player, record)
        loadPlayerBlessings(player, record)
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
     */
    fun loadOnlyDataForOnlinePlayer(player: Player, record: PlayerRecord?) {
        featureLoader?.loadInto(player.id, player.features)
    }

    /** `IOLoginData::savePlayerFirst` (src/io/functions/iologindata_save_player.cpp:191-372). */
    fun savePlayerFirst(player: Player): Boolean = players.savePlayerRecord(player)

    /** `IOLoginData::savePlayer` (src/io/iologindata.cpp:195-211). */
    fun savePlayer(player: Player): Boolean = try {
        savePlayerGuard(player)
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

        if (!savePlayerSpells(player)) {
            logger.error { "[savePlayerGuard] - Failed to save player spells: ${player.name}" }
            return false
        }

        if (!savePlayerKills(player.id, player.pvpPlayer.getUnjustifiedKills())) {
            logger.error { "[savePlayerGuard] - Failed to save player kills: ${player.name}" }
            return false
        }

        return saveOnlyDataForOnlinePlayer(player)
    }

    /** `IOLoginData::saveOnlyDataForOnlinePlayer` (src/io/iologindata.cpp:269-298). */
    fun saveOnlyDataForOnlinePlayer(player: Player): Boolean {
        val loader = featureLoader ?: return true
        loader.save(player.id, player.features)
        return true
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

    /** `IOLoginDataLoad::loadPlayerInventoryItems` (iologindata_load_player.cpp:543-561) row read. */
    fun loadPlayerInventoryItems(playerId: Long): List<PlayerItemState> = items.load(playerId)

    /** `IOLoginDataSave::savePlayerItem` (iologindata_save_player.cpp:534-564). */
    fun savePlayerItem(playerId: Long, itemList: List<PlayerItemState>): Boolean {
        items.save(playerId, itemList)
        return true
    }

    /** `IOLoginDataLoad::loadPlayerDepotItems` (iologindata_load_player.cpp:663-708) row read. */
    fun loadPlayerDepotItems(playerId: Long): List<PlayerContainerItemState> = depots.load(playerId)

    /** `IOLoginDataSave::savePlayerDepotItems` (iologindata_save_player.cpp:566-597). */
    fun savePlayerDepotItems(playerId: Long, depotList: List<PlayerContainerItemState>): Boolean {
        depots.save(playerId, depotList)
        return true
    }

    /** `IOLoginDataLoad::loadPlayerInboxItems` (iologindata_load_player.cpp:710-758) row read. */
    fun loadPlayerInboxItems(playerId: Long): List<PlayerContainerItemState> = depots.loadInbox(playerId)

    /** `IOLoginDataSave::savePlayerInbox` (iologindata_save_player.cpp:634-659). */
    fun savePlayerInbox(playerId: Long, inboxList: List<PlayerContainerItemState>): Boolean {
        depots.saveInbox(playerId, inboxList)
        return true
    }

    /**
     * `IOLoginDataLoad::loadPlayerKills` (iologindata_load_player.cpp:371-392).
     * Rows outside the `FRAG_TIME` window are dropped like the C++ loop; the
     * `Player::updateLastKillTimeCache` side effect has no Kotlin sink yet
     * (PARITY GAP).
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

    /** `IOLoginDataSave::savePlayerKills` (iologindata_save_player.cpp:447-476). */
    fun savePlayerKills(playerId: Long, killList: List<Kill>): Boolean {
        kills.save(playerId, killList)
        return true
    }

    /** `IOLoginDataLoad::loadPlayerStashItems` (iologindata_load_player.cpp:444-470) row read. */
    fun loadPlayerStashItems(playerId: Long): Map<Int, Int> = stash.load(playerId)

    /** `IOLoginDataSave::savePlayerStash` (iologindata_save_player.cpp:374-414). */
    fun savePlayerStash(playerId: Long, stashItems: Map<Int, Int>): Boolean {
        stash.save(playerId, stashItems)
        return true
    }

    /** `IOLoginDataLoad::loadRewardItems` (iologindata_load_player.cpp:645-661) row read. */
    fun loadRewardItems(playerId: Long): List<PlayerContainerItemState> = rewards.load(playerId)

    /** `IOLoginDataSave::saveRewardItems` (iologindata_save_player.cpp:599-632). */
    fun saveRewardItems(playerId: Long, rewardList: List<PlayerContainerItemState>): Boolean {
        rewards.save(playerId, rewardList)
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
    }
}
