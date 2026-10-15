package org.opentibia.canary.io.iologindata

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionAttributes
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionLight
import org.opentibia.canary.combat.condition.ConditionOutfit
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionSpeed
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.IntervalInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.players.PlayerSocialState
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.creatures.players.vip.PlayerVip
import org.opentibia.canary.database.SqlDialect
import org.opentibia.canary.database.VipLoginRepository
import org.opentibia.canary.io.IOGuild
import org.opentibia.canary.io.PlayerStorageRepository
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.kv.KV
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.social.guild.GuildRank
import org.opentibia.canary.spells.WorldType
import java.sql.SQLException
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * The `players` row subset `IOLoginDataLoad::loadPlayerConditions` and
 * `loadPlayerSkullSystem` read (iologindata_load_player.cpp:257-342).
 *
 * Canary reads both from the `DBResult` of the single `players` query
 * `IOLoginData::loadPlayer` already holds; the port's [PlayerRecord] does not
 * carry the columns, so [PlayerSubloadRepository] re-reads them by id
 * (PARITY NOTE: one extra query per sub-load).
 */
data class PlayerConditionSkullRecord(
    val conditions: ByteArray?,
    val skull: Int,
    val skullTime: Long,
)

/**
 * The `players` look columns `IOLoginDataLoad::loadPlayerDefaultOutfit`
 * (iologindata_load_player.cpp:291-317) reads from the same result row.
 *
 * Canary's `players` row also carries `lookmounthead`, `lookmountbody`,
 * `lookmountlegs`, `lookmountfeet` and `lookfamiliarstype` (schema.sql:144-148);
 * the port's V20 migration (`V20__player_lookmount_columns.sql`) adds those
 * columns on top of the V1 core look columns (V1__initial_schema.sql:48-53).
 */
data class PlayerOutfitRecord(
    val lookType: Int,
    val lookHead: Int,
    val lookBody: Int,
    val lookLegs: Int,
    val lookFeet: Int,
    val lookAddons: Int,
    val lookMountHead: Int,
    val lookMountBody: Int,
    val lookMountLegs: Int,
    val lookMountFeet: Int,
    val lookFamiliarsType: Int,
)

/**
 * Port of the `players` column reads of `IOLoginDataLoad::loadPlayerConditions`
 * / `loadPlayerSkullSystem` (iologindata_load_player.cpp:257-342) and
 * `loadPlayerDefaultOutfit` (:291-317), plus the `players.conditions` write of
 * `IOLoginDataSave::savePlayerFirst`
 * (iologindata_save_player.cpp:279-291).
 */
interface PlayerSubloadRepository {
    /** `null` when the player row is missing, mirroring the C++ null result. */
    fun loadConditionSkull(playerId: Long): PlayerConditionSkullRecord?

    /** `null` when the player row is missing, mirroring the C++ null result. */
    fun loadOutfit(playerId: Long): PlayerOutfitRecord?

    /**
     * `IOLoginDataSave::savePlayerFirst` `players`.`conditions` write
     * (iologindata_save_player.cpp:279-291): the serialized blob replaces the
     * stored stream. Returns `false` when the player row is missing or the
     * update fails, mirroring the guard's failure contract.
     */
    fun saveConditions(playerId: Long, blob: ByteArray): Boolean
}

/**
 * JDBC implementation of [PlayerSubloadRepository] over the V19 columns
 * (`conditions` BYTEA, `skull` SMALLINT, `skulltime` BIGINT), mirroring the
 * `players` row Canary already holds at load time.
 */
class DbPlayerSubloadRepository(private val dataSource: DataSource) : PlayerSubloadRepository {

    override fun loadConditionSkull(playerId: Long): PlayerConditionSkullRecord? = try {
        dataSource.connection.use { connection ->
            connection.prepareStatement(loadSql(SqlDialect.from(connection))).use { statement ->
                statement.setLong(1, playerId)
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        PlayerConditionSkullRecord(
                            conditions = result.getBytes(CONDITIONS_COLUMN),
                            skull = result.getShort(SKULL_COLUMN).toInt(),
                            skullTime = result.getLong(SKULL_TIME_COLUMN),
                        )
                    } else {
                        null
                    }
                }
            }
        }
    } catch (ex: SQLException) {
        logger.error(ex) { "player condition/skull load failed for player $playerId" }
        null
    }

    override fun loadOutfit(playerId: Long): PlayerOutfitRecord? = try {
        dataSource.connection.use { connection ->
            connection.prepareStatement(outfitSql(SqlDialect.from(connection))).use { statement ->
                statement.setLong(1, playerId)
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        PlayerOutfitRecord(
                            lookType = result.getInt(LOOK_TYPE_COLUMN),
                            lookHead = result.getInt(LOOK_HEAD_COLUMN),
                            lookBody = result.getInt(LOOK_BODY_COLUMN),
                            lookLegs = result.getInt(LOOK_LEGS_COLUMN),
                            lookFeet = result.getInt(LOOK_FEET_COLUMN),
                            lookAddons = result.getInt(LOOK_ADDONS_COLUMN),
                            lookMountHead = result.getInt(LOOK_MOUNT_HEAD_COLUMN),
                            lookMountBody = result.getInt(LOOK_MOUNT_BODY_COLUMN),
                            lookMountLegs = result.getInt(LOOK_MOUNT_LEGS_COLUMN),
                            lookMountFeet = result.getInt(LOOK_MOUNT_FEET_COLUMN),
                            lookFamiliarsType = result.getInt(LOOK_FAMILIARS_TYPE_COLUMN),
                        )
                    } else {
                        null
                    }
                }
            }
        }
    } catch (ex: SQLException) {
        logger.error(ex) { "player outfit load failed for player $playerId" }
        null
    }

    /**
     * `IOLoginDataSave::savePlayerFirst` `players`.`conditions` write
     * (iologindata_save_player.cpp:279-291). Like the record write of
     * `savePlayerFirst`, a missing player row leaves the guard failed.
     */
    override fun saveConditions(playerId: Long, blob: ByteArray): Boolean = try {
        dataSource.connection.use { connection ->
            connection.prepareStatement(saveConditionsSql(SqlDialect.from(connection))).use { statement ->
                statement.setBytes(1, blob)
                statement.setLong(2, playerId)
                statement.executeUpdate() > 0
            }
        }
    } catch (ex: SQLException) {
        logger.error(ex) { "player condition save failed for player $playerId" }
        false
    }

    private companion object {
        const val CONDITIONS_COLUMN = "conditions"
        const val SKULL_COLUMN = "skull"
        const val SKULL_TIME_COLUMN = "skulltime"
        const val LOOK_TYPE_COLUMN = "looktype"
        const val LOOK_HEAD_COLUMN = "lookhead"
        const val LOOK_BODY_COLUMN = "lookbody"
        const val LOOK_LEGS_COLUMN = "looklegs"
        const val LOOK_FEET_COLUMN = "lookfeet"
        const val LOOK_ADDONS_COLUMN = "lookaddons"
        const val LOOK_MOUNT_HEAD_COLUMN = "lookmounthead"
        const val LOOK_MOUNT_BODY_COLUMN = "lookmountbody"
        const val LOOK_MOUNT_LEGS_COLUMN = "lookmountlegs"
        const val LOOK_MOUNT_FEET_COLUMN = "lookmountfeet"
        const val LOOK_FAMILIARS_TYPE_COLUMN = "lookfamiliarstype"

        /** `SELECT` of `loadConditionSkull` with dialect-quoted column names. */
        fun loadSql(dialect: SqlDialect): String =
            "SELECT ${dialect.quoteIdentifier(CONDITIONS_COLUMN)}, ${dialect.quoteIdentifier(SKULL_COLUMN)}, " +
                "${dialect.quoteIdentifier(SKULL_TIME_COLUMN)} FROM players WHERE id = ?"

        /** `UPDATE` of `saveConditions` with the dialect-quoted `conditions` column. */
        fun saveConditionsSql(dialect: SqlDialect): String =
            "UPDATE players SET ${dialect.quoteIdentifier(CONDITIONS_COLUMN)} = ? WHERE id = ?"

        /** `SELECT` of `loadOutfit` with dialect-quoted look columns. */
        fun outfitSql(dialect: SqlDialect): String {
            val columns = listOf(
                LOOK_TYPE_COLUMN,
                LOOK_HEAD_COLUMN,
                LOOK_BODY_COLUMN,
                LOOK_LEGS_COLUMN,
                LOOK_FEET_COLUMN,
                LOOK_ADDONS_COLUMN,
                LOOK_MOUNT_HEAD_COLUMN,
                LOOK_MOUNT_BODY_COLUMN,
                LOOK_MOUNT_LEGS_COLUMN,
                LOOK_MOUNT_FEET_COLUMN,
                LOOK_FAMILIARS_TYPE_COLUMN,
            ).joinToString(", ") { dialect.quoteIdentifier(it) }
            return "SELECT $columns FROM players WHERE id = ?"
        }
    }
}

/**
 * One `guild_membership` row (schema.sql:377-390) as
 * `IOLoginDataLoad::loadPlayerGuild` reads it
 * (iologindata_load_player.cpp:402-406).
 */
data class GuildMembershipRow(
    val guildId: Long,
    val rankId: Long,
    val nick: String,
)

/**
 * Port of the `guild_membership` / `guild_ranks` reads of
 * `IOLoginDataLoad::loadPlayerGuild` (iologindata_load_player.cpp:394-442).
 * The guild itself is resolved through [IOGuild] / the injected game registry.
 */
interface GuildMembershipRepository {
    /** `null` when the player is guildless, mirroring the missing query result. */
    fun loadMembership(playerId: Long): GuildMembershipRow?

    /** `SELECT id, name, level FROM guild_ranks WHERE id = {}` (:419). */
    fun loadRank(rankId: Long): GuildRank?

    /** `SELECT COUNT(*) AS members FROM guild_membership WHERE guild_id = {}` (:436). */
    fun countMembers(guildId: Long): Long
}

/** JDBC implementation of [GuildMembershipRepository] over the V9 guild tables. */
class DbGuildMembershipRepository(private val dataSource: DataSource) : GuildMembershipRepository {

    override fun loadMembership(playerId: Long): GuildMembershipRow? = try {
        dataSource.connection.use { connection ->
            connection.prepareStatement(membershipSql(SqlDialect.from(connection))).use { statement ->
                statement.setLong(1, playerId)
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        GuildMembershipRow(
                            guildId = result.getLong(GUILD_ID_COLUMN),
                            rankId = result.getLong(RANK_ID_COLUMN),
                            nick = result.getString(NICK_COLUMN) ?: "",
                        )
                    } else {
                        null
                    }
                }
            }
        }
    } catch (ex: SQLException) {
        logger.error(ex) { "guild membership load failed for player $playerId" }
        null
    }

    override fun loadRank(rankId: Long): GuildRank? = try {
        dataSource.connection.use { connection ->
            connection.prepareStatement(rankSql(SqlDialect.from(connection))).use { statement ->
                statement.setLong(1, rankId)
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        GuildRank(
                            id = result.getLong("id"),
                            name = result.getString("name"),
                            level = result.getInt("level"),
                        )
                    } else {
                        null
                    }
                }
            }
        }
    } catch (ex: SQLException) {
        logger.error(ex) { "guild rank load failed for rank $rankId" }
        null
    }

    override fun countMembers(guildId: Long): Long = try {
        dataSource.connection.use { connection ->
            connection.prepareStatement(memberCountSql(SqlDialect.from(connection))).use { statement ->
                statement.setLong(1, guildId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getLong(MEMBERS_COLUMN) else 0L
                }
            }
        }
    } catch (ex: SQLException) {
        logger.error(ex) { "guild member count failed for guild $guildId" }
        0L
    }

    private companion object {
        const val GUILD_ID_COLUMN = "guild_id"
        const val RANK_ID_COLUMN = "rank_id"
        const val NICK_COLUMN = "nick"
        const val MEMBERS_COLUMN = "members"

        /** `SELECT` of `loadMembership` with dialect-quoted column names. */
        fun membershipSql(dialect: SqlDialect): String =
            "SELECT ${dialect.quoteIdentifier(GUILD_ID_COLUMN)}, ${dialect.quoteIdentifier(RANK_ID_COLUMN)}, " +
                "${dialect.quoteIdentifier(NICK_COLUMN)} FROM guild_membership WHERE player_id = ?"

        /** `SELECT` of `loadRank` with dialect-quoted column names. */
        fun rankSql(dialect: SqlDialect): String =
            "SELECT ${dialect.quoteIdentifier("id")}, ${dialect.quoteIdentifier("name")}, " +
                "${dialect.quoteIdentifier("level")} FROM guild_ranks WHERE id = ?"

        /** `SELECT COUNT(*)` of `countMembers` with the dialect-quoted alias. */
        fun memberCountSql(dialect: SqlDialect): String =
            "SELECT COUNT(*) AS ${dialect.quoteIdentifier(MEMBERS_COLUMN)} " +
                "FROM guild_membership WHERE guild_id = ?"
    }
}

/**
 * The seven `IOLoginDataLoad` sub-loads of `IOLoginData::loadPlayer`
 * (src/io/iologindata.cpp:102-193) that wave 55 reported missing:
 *
 *  - [loadPlayerConditions] (`load_player.cpp:257-275`): decodes the persisted
 *    `players.conditions` stream into `Player::storedConditionList`.
 *  - [loadPlayerDefaultOutfit] (`:291-317`): applies the persisted look
 *    columns to `Player::defaultOutfit` and copies them into
 *    `Player::currentOutfit`, with the `WARN_UNSAFE_SCRIPTS` unregistered
 *    look-type guard.
 *  - [loadPlayerSkullSystem] (`:322-342`): restores `Player::skullTicks` /
 *    `Player::skull` on non-`WORLD_TYPE_PVP_ENFORCED` worlds.
 *  - [loadPlayerGuild] (`:394-442`): membership, nick, guild + rank, war list
 *    and member count.
 *  - [loadPlayerStorageMap] (`:760-768`): the wave-36 storage repository into
 *    the player's [PlayerStorage].
 *  - [loadPlayerVip] (`:770-806`): the wave-36 VIP repository into the
 *    player's [PlayerVip].
 *  - [loadPlayerExivaRestrictions] (`:1042-1086`): the `exiva-restrictions`
 *    KV scope into `Player::getExivaRestrictions()`.
 *
 * The class lives in the io package and keeps Canary's method names and guards. The
 * globals (`g_game().getGuild`/`addGuild`, `g_game().getWorldType`) and the
 * component owners (`Player::storage()`, `Player::vip()`, `player->kv()`) are
 * constructor-injected seams per AGENTS.md 1.1; a `null` seam keeps that
 * sub-load inert exactly like the other unwired `IOLoginData` seams.
 *
 * COMPOSED OWNERS (wave 64): `Player::vip()` is `Player.features.vip`
 * (player.hpp:1965), `Player::kv()` is the composed `Player.kv` full
 * `ValueWrapper` store (player.cpp:1398-1400) and `Player::storage()` is
 * `Player.features.storage` (player.hpp:1968), so their seams default to the
 * live player and the sub-loads run at login out of the box; only an explicit
 * `null` keeps one inert.
 *
 * PARITY GAPS (see the individual methods for details):
 *  - `loadPlayerConditions` decodes the base attributes, the regeneration/
 *    mana-shield/soul/damage/speed/outfit/light/charm payloads (including the
 *    flat skill/stat/buff/absorb/increase records, `PERSISTENT` and the raw
 *    `INTERVALDATA` rounds) and skips a condition whose representation is
 *    impossible (matching the C++ failure semantics). The condition classes
 *    expose the C++ `unserializeProp` writes as module-visible members because
 *    Kotlin has no friend classes.
 *  - `Player::guildNick` is not composed into the Kotlin `Player` yet, so
 *    [loadPlayerGuild] takes its owner as a seam (`null` leaves the nick
 *    unset).
 */
class IOLoginDataSubloads(
    /** The `players.conditions`/`skull`/`skulltime` reads and the `conditions` write. */
    private val subloadRepository: PlayerSubloadRepository? = null,
    /** `g_playerStorageRepository()` (wave-36). */
    private val storageRepository: PlayerStorageRepository? = null,
    /** `IOLoginData`'s VIP repository (wave-36). */
    private val vip: VipLoginRepository? = null,
    /** The `guild_membership`/`guild_ranks` reads. */
    private val guildMemberships: GuildMembershipRepository? = null,
    /** `IOGuild::loadGuild` / `getWarList` (src/io/ioguild.cpp:15-77). */
    private val ioGuild: IOGuild? = null,
    /**
     * `Player::storage()` (player.hpp:1541-1542). The composed component is
     * reached through `Player.features.storage`, so the default binds the live
     * accessor; an explicit `null` keeps the sub-load inert for callers that
     * need the pre-wiring behavior.
     */
    private val playerStorage: ((Player) -> PlayerStorage?)? = { player -> player.features.storage },
    /** `Player::vip()` (player.hpp:1529-1530); defaults to the composed `PlayerVIP`. */
    private val playerVip: ((Player) -> PlayerVip?)? = { player -> player.features.vip },
    /** `Player::guildNick` (`PlayerSocialState`); `null` leaves the nick unset. */
    private val playerSocialState: ((Player) -> PlayerSocialState?)? = null,
    /**
     * `Player::kv()` (player.cpp:1398-1400), the full
     * `g_kv().scoped("player").scoped(guid)` `ValueWrapper` store; defaults to
     * the composed `Player.kv`, so the exiva scope loads at login. An explicit
     * `null` keeps the sub-load inert for callers that need the pre-wiring
     * behavior.
     */
    private val playerKv: ((Player) -> KV?)? = { player -> player.kv },
    /** `g_game().getGuild(guildId)` registry read. */
    private val getGuild: (Long) -> Guild? = { null },
    /** `g_game().addGuild(guild)` registry write. */
    private val addGuild: (Guild) -> Unit = {},
    /** `time(nullptr)` for the skull window; seconds. */
    private val now: () -> Long = { getTimeNow() },
    /**
     * `g_configManager().getBoolean(WARN_UNSAFE_SCRIPTS)`
     * (configmanager.cpp:169, default true) for the default-outfit look-type
     * guard. `false` keeps the guard inert like [ConditionOutfit]'s seam.
     */
    private val warnUnsafeScripts: Boolean = false,
    /**
     * `g_game().isLookTypeRegistered` (game.hpp:667) over the appearance
     * registry for the default-outfit look-type guard. `null`-style default
     * `false` mirrors [ConditionOutfit]'s seam and only matters while
     * [warnUnsafeScripts] is set.
     */
    private val isLookTypeRegistered: (Int) -> Boolean = { false },
) {

    /**
     * `IOLoginDataLoad::loadPlayerConditions`
     * (iologindata_load_player.cpp:257-275): the stored stream is decoded with
     * the [PropStream] reader exactly like `Condition::createCondition` +
     * `Condition::unserialize` (condition.cpp:189-275, 414-462) and each
     * successful condition is appended to the friend-parity
     * [Player.storedConditionList] for the [Player.onCreatureAppear] replay.
     */
    fun loadPlayerConditions(player: Player) {
        val blob = subloadRepository?.loadConditionSkull(player.id)?.conditions ?: return
        for (condition in decodeConditions(blob)) {
            player.storedConditionList.add(condition)
        }
    }

    /**
     * `IOLoginDataLoad::loadPlayerDefaultOutfit`
     * (iologindata_load_player.cpp:291-317): the persisted look columns are
     * applied to `Player::defaultOutfit` and then copied into
     * `Player::currentOutfit`. `g_configManager().getBoolean(WARN_UNSAFE_SCRIPTS)`
     * becomes [warnUnsafeScripts] and `g_game().isLookTypeRegistered` becomes
     * [isLookTypeRegistered]: an unregistered non-zero look type (or familiar
     * type, the second guard at :312-315) logs the warning and returns before
     * the current-outfit copy, exactly like the two C++ `return`s after the
     * `defaultOutfit` fields were already assigned in place.
     *
     * ACCESSOR NEED (`Creature.kt`): Canary mutates the live
     * `player->defaultOutfit` fields and ends with
     * `player->currentOutfit = player->defaultOutfit`. The Kotlin `Creature`
     * only exposes value-copying accessors (`getDefaultOutfit()` returns a
     * `copy()`, `setDefaultOutfit`/`setCurrentOutfit` copy again), so the
     * loader read-modify-writes through them and commits the copy before each
     * guard return; this reproduces the in-place C++ mutation without a new
     * `Creature.kt` accessor.
     *
     * The `lookmounthead`/`lookmountbody`/`lookmountlegs`/`lookmountfeet` and
     * `lookfamiliarstype` columns (schema.sql:144-148, added by V20) are
     * applied here together with the second unregistered-familiar-type guard
     * (iologindata_load_player.cpp:291-317).
     */
    fun loadPlayerDefaultOutfit(player: Player) {
        val row = subloadRepository?.loadOutfit(player.id) ?: return

        val defaultOutfit = player.getDefaultOutfit()
        defaultOutfit.lookType = row.lookType
        // Commit before the guard: the C++ field is mutated in place and stays
        // assigned when the guard returns early.
        player.setDefaultOutfit(defaultOutfit)

        if (warnUnsafeScripts && defaultOutfit.lookType != 0 && !isLookTypeRegistered(defaultOutfit.lookType)) {
            logger.warn {
                "[loadPlayerDefaultOutfit] An unregistered creature looktype type with id " +
                    "'${defaultOutfit.lookType}' was blocked to prevent client crash."
            }
            return
        }

        // `static_cast<uint8_t>(result->getNumber<uint16_t>(...))` for the byte fields.
        defaultOutfit.lookHead = row.lookHead and 0xFF
        defaultOutfit.lookBody = row.lookBody and 0xFF
        defaultOutfit.lookLegs = row.lookLegs and 0xFF
        defaultOutfit.lookFeet = row.lookFeet and 0xFF
        defaultOutfit.lookAddons = row.lookAddons and 0xFF
        defaultOutfit.lookMountHead = row.lookMountHead and 0xFF
        defaultOutfit.lookMountBody = row.lookMountBody and 0xFF
        defaultOutfit.lookMountLegs = row.lookMountLegs and 0xFF
        defaultOutfit.lookMountFeet = row.lookMountFeet and 0xFF
        // `result->getNumber<uint16_t>("lookfamiliarstype")`.
        defaultOutfit.lookFamiliarsType = row.lookFamiliarsType and 0xFFFF
        player.setDefaultOutfit(defaultOutfit)

        if (warnUnsafeScripts &&
            defaultOutfit.lookFamiliarsType != 0 &&
            !isLookTypeRegistered(defaultOutfit.lookFamiliarsType)
        ) {
            logger.warn {
                "[loadPlayerDefaultOutfit] An unregistered creature looktype type with id " +
                    "'${defaultOutfit.lookFamiliarsType}' was blocked to prevent client crash."
            }
            return
        }

        player.setCurrentOutfit(player.getDefaultOutfit())
    }

    /**
     * `IOLoginDataLoad::loadPlayerSkullSystem`
     * (iologindata_load_player.cpp:322-342): on non-`PVP_ENFORCED` worlds a
     * still-running skull restores `skullTicks = skullSeconds + 2` and the
     * red/black skull id.
     */
    fun loadPlayerSkullSystem(player: Player) {
        val row = subloadRepository?.loadConditionSkull(player.id) ?: return

        if (player.worldTypeProvider() != WorldType.PVP_ENFORCED) {
            val skullSeconds = row.skullTime - now()
            if (skullSeconds > 0) {
                player.pvpPlayer.setSkullTicks(skullSeconds + 2)

                when (row.skull) {
                    Skulls_t.SKULL_RED.value -> player.setSkull(Skulls_t.SKULL_RED)
                    Skulls_t.SKULL_BLACK.value -> player.setSkull(Skulls_t.SKULL_BLACK)
                }
            }
        }
    }

    /**
     * `IOLoginDataLoad::loadPlayerGuild`
     * (iologindata_load_player.cpp:394-442): the membership row resolves the
     * nick, the guild (through the injected registry, loading and registering
     * it through [ioGuild] on a miss), its rank (reloading a missing rank row),
     * the active war list and the member count.
     */
    fun loadPlayerGuild(player: Player) {
        val membership = guildMemberships?.loadMembership(player.id) ?: return

        playerSocialState?.invoke(player)?.setGuildNick(membership.nick)

        var guild = getGuild(membership.guildId)
        if (guild == null) {
            guild = ioGuild?.loadGuild(membership.guildId)
            if (guild != null) {
                addGuild(guild)
            }
        }

        if (guild == null) {
            return
        }

        player.setGuild(guild)

        var rank = guild.getRankById(membership.rankId)
        if (rank == null) {
            val loadedRank = guildMemberships.loadRank(membership.rankId)
            if (loadedRank != null) {
                guild.addRank(loadedRank.id, loadedRank.name, loadedRank.level)
            }

            rank = guild.getRankById(membership.rankId)
            if (rank == null) {
                player.setGuild(null)
            }
        }

        player.setGuildRank(rank)

        // `IOGuild::getWarList(guid, player->guildWarVector)` appends
        // (ioguild.cpp:60-77); the load always starts from a fresh player.
        player.pvpPlayer.guildWarVector.addAll(ioGuild?.getWarList(membership.guildId) ?: emptyList())

        guild.setMemberCount(guildMemberships.countMembers(membership.guildId))
    }

    /**
     * `IOLoginDataLoad::loadPlayerStorageMap`
     * (iologindata_load_player.cpp:760-768): `g_playerStorageRepository().load`
     * rows are ingested into the player's [PlayerStorage].
     */
    fun loadPlayerStorageMap(player: Player) {
        val storage = playerStorage?.invoke(player) ?: return
        storage.ingest(storageRepository?.load(player.id) ?: return)
    }

    /**
     * `IOLoginDataLoad::loadPlayerVip`
     * (iologindata_load_player.cpp:770-806): the account's VIP list and groups
     * are read through the wave-36 [VipLoginRepository] (which folds in the
     * `account_vipgrouplist` membership rows) and added silently through
     * `PlayerVIP::addInternal` / `addGroupInternal` / `addGuidToGroupInternal`.
     */
    fun loadPlayerVip(player: Player) {
        val vipComponent = playerVip?.invoke(player) ?: return
        val repository = vip ?: return
        val accountId = player.account?.id ?: return

        for (entry in repository.getVipEntries(accountId)) {
            vipComponent.addInternal(entry.guid)
        }

        for (group in repository.getVipGroupEntries(accountId)) {
            vipComponent.addGroupInternal(group.id, group.name, group.customizable)
            for (guid in group.vipGroupGuids) {
                vipComponent.addGuidToGroupInternal(group.id, guid)
            }
        }
    }

    /**
     * `IOLoginDataLoad::loadPlayerExivaRestrictions`
     * (iologindata_load_player.cpp:1042-1086): every stored flag and both
     * whitelists of the `exiva-restrictions` scope under `Player::kv()`
     * (`player.<guid>.exiva-restrictions`) are copied into
     * `Player::getExivaRestrictions()`.
     *
     * PARITY NOTE: Canary saves the six flags as `ValueWrapper(bool)` and the
     * loader reads them through `ValueWrapper::getNumber()`, which returns
     * `0.0` for the boolean variant (value_wrapper.hpp:51-58); a persisted
     * `true` therefore loads as `false` here exactly like the C++ port.
     *
     * The whitelist elements are the `static_cast<int>(guid)` values the saver
     * stores, pushed into the C++ `std::vector<uint32_t>`
     * (`player.hpp:190-191`) with the modulo-2^32 conversion, so a guid above
     * `Int.MAX_VALUE` wraps back to its `uint32_t` value instead of
     * sign-extending.
     */
    fun loadPlayerExivaRestrictions(player: Player) {
        val playerScopedKv = playerKv?.invoke(player) ?: return
        val scope = playerScopedKv.scoped("exiva-restrictions")

        val restrictions = player.getExivaRestrictions()

        scope.get(KEY_ALLOW_ALL)?.let { restrictions.allowAll = it.getNumber() != 0.0 }
        scope.get(KEY_ALLOW_OWN_GUILD)?.let { restrictions.allowOwnGuild = it.getNumber() != 0.0 }
        scope.get(KEY_ALLOW_OWN_PARTY)?.let { restrictions.allowOwnParty = it.getNumber() != 0.0 }
        scope.get(KEY_ALLOW_VIP_LIST)?.let { restrictions.allowVipList = it.getNumber() != 0.0 }
        scope.get(KEY_ALLOW_PLAYER_WHITELIST)?.let { restrictions.allowPlayerWhitelist = it.getNumber() != 0.0 }
        scope.get(KEY_ALLOW_GUILD_WHITELIST)?.let { restrictions.allowGuildWhitelist = it.getNumber() != 0.0 }

        scope.get(KEY_PLAYER_WHITELIST)?.let { wrapper ->
            for (guid in wrapper.getArray()) {
                restrictions.playerWhitelist.add(guid.getInt().toLong() and UINT32_MASK)
            }
        }

        scope.get(KEY_GUILD_WHITELIST)?.let { wrapper ->
            for (guildId in wrapper.getArray()) {
                restrictions.guildWhitelist.add(guildId.getInt().toLong() and UINT32_MASK)
            }
        }
    }

    // --- condition stream codec (condition.cpp:189-462) ------------------------

    /**
     * `IOLoginDataLoad::loadPlayerConditions` decode loop: each iteration
     * mirrors `Condition::createCondition(PropStream&)`
     * (condition.cpp:414-462) followed by `Condition::unserialize`
     * (condition.cpp:189-197). A failed header, an unsupported attribute or an
     * unknown condition type ends the scan exactly like the C++
     * `while (condition)` loop.
     */
    private fun decodeConditions(blob: ByteArray): List<Condition> {
        val stream = PropStream(blob)
        val conditions = mutableListOf<Condition>()

        while (true) {
            val condition = createConditionFromStream(stream) ?: break
            if (!readConditionAttributes(condition, stream)) {
                break
            }
            conditions.add(condition)
        }

        return conditions
    }

    /** `Condition::createCondition(PropStream &)` (condition.cpp:414-462). */
    private fun createConditionFromStream(stream: PropStream): Condition? {
        if (stream.readU8() != CONDITION_ATTR_TYPE) {
            return null
        }
        val typeValue = stream.readU8() ?: return null

        if (stream.readU8() != CONDITION_ATTR_ID) {
            return null
        }
        val idValue = stream.readU32() ?: return null

        if (stream.readU8() != CONDITION_ATTR_TICKS) {
            return null
        }
        val ticks = stream.readU32()?.toInt() ?: return null

        if (stream.readU8() != CONDITION_ATTR_ISBUFF) {
            return null
        }
        val buff = stream.readU8() ?: return null

        if (stream.readU8() != CONDITION_ATTR_SUBID) {
            return null
        }
        val subId = stream.readU32() ?: return null

        val type = ConditionType.entries.firstOrNull { it.value == typeValue } ?: return null
        val id = ConditionId.entries
            .firstOrNull { (it.value.toLong() and UINT32_MASK) == idValue }
            ?: return null

        return Condition.createCondition(
            id = id,
            type = type,
            ticks = ticks,
            param = 0,
            buff = buff != 0,
            subId = subId.toInt(),
        )
    }

    /**
     * `Condition::unserialize` (condition.cpp:189-197) over the per-class
     * `unserializeProp` overrides: reads attribute records until
     * `CONDITIONATTR_END`, a short read (the C++ loop exit that keeps the
     * condition) or an attribute that fails to apply.
     *
     * Attribute support:
     *  - `TYPE`/`ID`/`ISBUFF`/`SUBID` are already carried by the constructor
     *    (the header consumed them); a repeated record is consumed.
     *  - The base attributes (`TICKS`, `TICKSOUND`, `ADDSOUND`) always apply
     *    through [Condition.setParam]; a subclass override rejecting them does
     *    not drop the condition (see [readBaseParam]).
     *  - `OWNER` applies only for `ConditionDamage`/`ConditionSpeed`, the two
     *    C++ classes that handle it in `unserializeProp`.
     *  - The subclass attributes (regeneration, mana-shield, soul, damage,
     *    speed, light and charm modifier) require [Condition.setParam] to
     *    accept the value, exactly like the C++ override dispatch.
     *  - `OUTFIT` maps to `ConditionOutfit::setOutfit`.
     *  - `INTERVALDATA` maps to `ConditionDamage::unserializeProp`
     *    (condition.cpp:1817-1824): the raw `IntervalInfo` (preserving
     *    `timeLeft`) is appended and `ticks` re-stamped by the raw interval.
     *  - `LIGHTTICKS`/`LIGHTINTERVAL` are consumed and ignored: Canary stores
     *    them but `ConditionLight::startCondition` recomputes both.
     *  - `PERSISTENT` writes `Condition::m_isPersistent` exactly like
     *    `Condition::unserializeProp` (condition.cpp:259-267).
     *  - `SKILLS`/`STATS`/`BUFFS` write the flat arrays at their serialized
     *    sequential index and `ABSORBS`/`INCREASES` call the C++
     *    `setAbsorb`/`setIncrease` by serialized index
     *    (condition.cpp:749-783); the charm modifier maps to `setParam`.
     */
    private fun readConditionAttributes(condition: Condition, stream: PropStream): Boolean {
        var skillIndex = 0
        var statIndex = 0
        var buffIndex = 0
        var formulaMina: Float? = null
        var formulaMinb: Float? = null
        var formulaMaxa: Float? = null
        var formulaMaxb: Float? = null

        while (true) {
            val attr = stream.readU8() ?: break
            if (attr == CONDITION_ATTR_END) {
                break
            }

            val applied = when (attr) {
                // The header already applied TYPE/ID/ISBUFF/SUBID through the
                // condition constructor (condition.cpp:414-462); a repeated
                // record is consumed exactly like the C++ prefix reader.
                CONDITION_ATTR_TYPE -> stream.readI32() != null
                CONDITION_ATTR_ID -> stream.readU32() != null
                CONDITION_ATTR_ISBUFF -> stream.readU8() != null
                CONDITION_ATTR_SUBID -> stream.readU32() != null

                // Base `Condition::unserializeProp` attributes always apply
                // (condition.cpp:189-275); the subclass `setParam` overrides
                // may reject them with `false` after the base already applied.
                CONDITION_ATTR_TICKS ->
                    readBaseParam(stream, condition, ConditionParam.CONDITION_PARAM_TICKS) { it.readI32() }

                CONDITION_ATTR_TICKSOUND ->
                    readBaseParam(stream, condition, ConditionParam.CONDITION_PARAM_SOUND_TICK) { it.readU16() }

                CONDITION_ATTR_ADDSOUND ->
                    readBaseParam(stream, condition, ConditionParam.CONDITION_PARAM_SOUND_ADD) { it.readU16() }

                CONDITION_ATTR_HEALTHTICKS ->
                    readParam(stream, condition, ConditionParam.CONDITION_PARAM_HEALTHTICKS) { it.readU32()?.toInt() }

                CONDITION_ATTR_HEALTHGAIN ->
                    readParam(stream, condition, ConditionParam.CONDITION_PARAM_HEALTHGAIN) { it.readU32()?.toInt() }

                CONDITION_ATTR_MANATICKS ->
                    readParam(stream, condition, ConditionParam.CONDITION_PARAM_MANATICKS) { it.readU32()?.toInt() }

                CONDITION_ATTR_MANAGAIN ->
                    readParam(stream, condition, ConditionParam.CONDITION_PARAM_MANAGAIN) { it.readU32()?.toInt() }

                CONDITION_ATTR_DELAYED -> stream.readU8()?.let { value ->
                    condition.setParam(ConditionParam.CONDITION_PARAM_DELAYED, if (value != 0) 1 else 0)
                } ?: false

                // Only `ConditionDamage`/`ConditionSpeed::unserializeProp`
                // handle `CONDITIONATTR_OWNER` (condition.cpp:1818, 2496).
                CONDITION_ATTR_OWNER -> if (condition is ConditionDamage || condition is ConditionSpeed) {
                    readParam(stream, condition, ConditionParam.CONDITION_PARAM_OWNER) { it.readU32()?.toInt() }
                } else {
                    false
                }

                CONDITION_ATTR_INTERVALDATA -> readIntervalData(condition, stream)

                CONDITION_ATTR_SPEEDDELTA ->
                    readParam(stream, condition, ConditionParam.CONDITION_PARAM_SPEED) { it.readI32() }

                CONDITION_ATTR_FORMULA_MINA -> if (condition is ConditionSpeed) {
                    val value = stream.readI32()
                    if (value != null) {
                        formulaMina = Float.fromBits(value)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }

                CONDITION_ATTR_FORMULA_MINB -> if (condition is ConditionSpeed) {
                    val value = stream.readI32()
                    if (value != null) {
                        formulaMinb = Float.fromBits(value)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }

                CONDITION_ATTR_FORMULA_MAXA -> if (condition is ConditionSpeed) {
                    val value = stream.readI32()
                    if (value != null) {
                        formulaMaxa = Float.fromBits(value)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }

                CONDITION_ATTR_FORMULA_MAXB -> if (condition is ConditionSpeed) {
                    val value = stream.readI32()
                    if (value != null) {
                        formulaMaxb = Float.fromBits(value)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }

                CONDITION_ATTR_LIGHTCOLOR ->
                    readParam(stream, condition, ConditionParam.CONDITION_PARAM_LIGHT_COLOR) { it.readU32()?.toInt() }

                CONDITION_ATTR_LIGHTLEVEL ->
                    readParam(stream, condition, ConditionParam.CONDITION_PARAM_LIGHT_LEVEL) { it.readU32()?.toInt() }

                // `ConditionLight::unserializeProp` (condition.cpp:2850-2857)
                // stores both, but `ConditionLight::startCondition`
                // (condition.cpp:2761-2771) always recomputes them.
                CONDITION_ATTR_LIGHTTICKS -> condition is ConditionLight && stream.readU32() != null
                CONDITION_ATTR_LIGHTINTERVAL -> condition is ConditionLight && stream.readU32() != null

                CONDITION_ATTR_SOULTICKS ->
                    readParam(stream, condition, ConditionParam.CONDITION_PARAM_SOULTICKS) { it.readU32()?.toInt() }

                CONDITION_ATTR_SOULGAIN ->
                    readParam(stream, condition, ConditionParam.CONDITION_PARAM_SOULGAIN) { it.readU32()?.toInt() }

                // `ConditionAttributes::unserializeProp` flat records
                // (condition.cpp:750-757): each record writes the next slot of
                // the flat array. C++ does the writes inside the class; the
                // Kotlin arrays are module-visible for this codec.
                CONDITION_ATTR_SKILLS -> {
                    val value = stream.readI32()
                    if (value == null || condition !is ConditionAttributes || skillIndex > Skill.LAST) {
                        false
                    } else {
                        condition.skills[skillIndex++] = value
                        true
                    }
                }

                CONDITION_ATTR_STATS -> {
                    val value = stream.readI32()
                    if (value == null || condition !is ConditionAttributes || statIndex > Stat.LAST) {
                        false
                    } else {
                        condition.stats[statIndex++] = value
                        true
                    }
                }

                CONDITION_ATTR_BUFFS -> {
                    val value = stream.readI32()
                    if (value == null || condition !is ConditionAttributes || buffIndex > Buff.LAST) {
                        false
                    } else {
                        condition.buffs[buffIndex++] = value
                        true
                    }
                }

                CONDITION_ATTR_OUTFIT -> readOutfit(stream)?.let { outfit ->
                    (condition as? ConditionOutfit)?.setOutfit(outfit) != null
                } ?: false

                CONDITION_ATTR_PERIODDAMAGE -> if (condition is ConditionDamage) {
                    // `ConditionDamage::unserializeProp` reads the field directly
                    // and returns the read result (condition.cpp:1810-1812); the
                    // Kotlin `ConditionDamage::setParam` returns the base result
                    // for `CONDITION_PARAM_PERIODICDAMAGE` (condition.cpp:1783-1785),
                    // so the value applies through `readBaseParam` exactly like
                    // the C++ override.
                    readBaseParam(stream, condition, ConditionParam.CONDITION_PARAM_PERIODICDAMAGE) { it.readI32() }
                } else {
                    false
                }

                CONDITION_ATTR_MANASHIELD ->
                    readParam(stream, condition, ConditionParam.CONDITION_PARAM_MANASHIELD) { it.readU32()?.toInt() }

                CONDITION_ATTR_ABSORBS -> readCombatAttribute(stream, condition) { attributes, index, value ->
                    attributes.setAbsorb(index, value)
                }

                CONDITION_ATTR_INCREASES -> readCombatAttribute(stream, condition) { attributes, index, value ->
                    attributes.setIncrease(index, value)
                }

                CONDITION_ATTR_CHARM_CHANCE_MODIFIER -> stream.readU8()?.let { value ->
                    condition.setParam(ConditionParam.CONDITION_PARAM_CHARM_CHANCE_MODIFIER, value.toByte().toInt())
                } ?: false

                // `Condition::unserializeProp` (condition.cpp:259-267) sets
                // `m_isPersistent`; the Kotlin property setter is
                // module-visible for this codec.
                CONDITION_ATTR_PERSISTENT -> {
                    val value = stream.readU8()
                    if (value == null) {
                        false
                    } else {
                        condition.isPersistentFlag = value != 0
                        true
                    }
                }

                else -> false
            }

            if (!applied) {
                return false
            }
        }

        if (condition is ConditionSpeed && (formulaMina != null || formulaMinb != null || formulaMaxa != null || formulaMaxb != null)) {
            condition.setFormulaVars(formulaMina ?: 0f, formulaMinb ?: 0f, formulaMaxa ?: 0f, formulaMaxb ?: 0f)
        }

        return true
    }

    /** One `CONDITIONATTR_*` numeric value through the condition's `setParam`. */
    private inline fun readParam(
        stream: PropStream,
        condition: Condition,
        param: ConditionParam,
        read: (PropStream) -> Int?,
    ): Boolean {
        val value = read(stream) ?: return false
        return condition.setParam(param, value)
    }

    /**
     * A base `Condition::unserializeProp` attribute
     * (condition.cpp:199-275). The port's subclass `setParam` overrides may
     * return `false` for them after the base implementation already applied
     * the value (e.g. `ConditionSpeed::setParam`, condition.cpp:2474-2491), so
     * the return value is deliberately ignored exactly like the C++ base
     * parser that never routes these through the virtual `setParam`.
     */
    private inline fun readBaseParam(
        stream: PropStream,
        condition: Condition,
        param: ConditionParam,
        read: (PropStream) -> Int?,
    ): Boolean {
        val value = read(stream) ?: return false
        condition.setParam(param, value)
        return true
    }

    /**
     * `ConditionDamage::unserializeProp` `CONDITIONATTR_INTERVALDATA`
     * (condition.cpp:1816-1824): the raw `IntervalInfo` is appended to the
     * damage list (preserving `timeLeft` and the unclamped interval) and
     * `ticks` is re-stamped by the record's interval unless the condition is
     * immortal, exactly like the C++ `damageList.emplace_back` +
     * `setTicks(ticks + damageInfo.interval)`.
     */
    private fun readIntervalData(condition: Condition, stream: PropStream): Boolean {
        val damage = condition as? ConditionDamage ?: return false
        val timeLeft = stream.readI32() ?: return false
        val value = stream.readI32() ?: return false
        val interval = stream.readI32() ?: return false

        damage.damageList.add(IntervalInfo(timeLeft = timeLeft, value = value, interval = interval))
        if (damage.ticks != -1) {
            damage.setTicks(damage.ticks + interval)
        }
        return true
    }

    /**
     * `ConditionAttributes::unserializeProp` absorbs/increases block
     * (condition.cpp:757-780): `COMBAT_COUNT` serialized `(uint8 index,
     * int32 value)` pairs routed through the C++ `setAbsorb`/`setIncrease`
     * by the record's own index.
     */
    private inline fun readCombatAttribute(
        stream: PropStream,
        condition: Condition,
        onValue: (attributes: ConditionAttributes, index: Int, value: Int) -> Unit,
    ): Boolean {
        val attributes = condition as? ConditionAttributes ?: return false

        for (index in 0 until COMBAT_COUNT) {
            stream.readU8() ?: return false
            val value = stream.readI32() ?: return false
            onValue(attributes, index, value)
        }
        return true
    }

    /**
     * `PropStream::read<Outfit_t>` (creatures_definitions.hpp:1832-1850,
     * `read<T>` bit-casts the 24 little-endian bytes): 3 × `uint16_t`,
     * 8 × `uint8_t`, 5 × `uint16_t`.
     */
    private fun readOutfit(stream: PropStream): Outfit? {
        val lookType = stream.readU16() ?: return null
        val lookTypeEx = stream.readU16() ?: return null
        val lookMount = stream.readU16() ?: return null
        val lookHead = stream.readU8() ?: return null
        val lookBody = stream.readU8() ?: return null
        val lookLegs = stream.readU8() ?: return null
        val lookFeet = stream.readU8() ?: return null
        val lookAddons = stream.readU8() ?: return null
        val lookMountHead = stream.readU8() ?: return null
        val lookMountBody = stream.readU8() ?: return null
        val lookMountLegs = stream.readU8() ?: return null
        val lookMountFeet = stream.readU8() ?: return null
        val lookFamiliarsType = stream.readU16() ?: return null
        val lookWing = stream.readU16() ?: return null
        val lookAura = stream.readU16() ?: return null
        val lookEffect = stream.readU16() ?: return null
        val lookShader = stream.readU16() ?: return null

        return Outfit(
            lookType = lookType,
            lookTypeEx = lookTypeEx,
            lookMount = lookMount,
            lookHead = lookHead,
            lookBody = lookBody,
            lookLegs = lookLegs,
            lookFeet = lookFeet,
            lookAddons = lookAddons,
            lookMountHead = lookMountHead,
            lookMountBody = lookMountBody,
            lookMountLegs = lookMountLegs,
            lookMountFeet = lookMountFeet,
            lookFamiliarsType = lookFamiliarsType,
            lookWing = lookWing,
            lookAura = lookAura,
            lookEffect = lookEffect,
            lookShader = lookShader,
        )
    }

    private companion object {
        /** `uint32_t` mask for the `ConditionId_t` conversion. */
        const val UINT32_MASK = 0xFFFFFFFFL

        /** `COMBAT_COUNT` (creatures_definitions.hpp:849). */
        const val COMBAT_COUNT = 14

        const val KEY_ALLOW_ALL = "allowAll"
        const val KEY_ALLOW_OWN_GUILD = "allowOwnGuild"
        const val KEY_ALLOW_OWN_PARTY = "allowOwnParty"
        const val KEY_ALLOW_VIP_LIST = "allowVipList"
        const val KEY_ALLOW_PLAYER_WHITELIST = "allowPlayerWhitelist"
        const val KEY_ALLOW_GUILD_WHITELIST = "allowGuildWhitelist"
        const val KEY_PLAYER_WHITELIST = "playerWhitelist"
        const val KEY_GUILD_WHITELIST = "guildWhitelist"

        /** `ConditionAttr_t` (creatures_definitions.hpp:71-107). */
        const val CONDITION_ATTR_TYPE = 1
        const val CONDITION_ATTR_ID = 2
        const val CONDITION_ATTR_TICKS = 3
        const val CONDITION_ATTR_HEALTHTICKS = 4
        const val CONDITION_ATTR_HEALTHGAIN = 5
        const val CONDITION_ATTR_MANATICKS = 6
        const val CONDITION_ATTR_MANAGAIN = 7
        const val CONDITION_ATTR_DELAYED = 8
        const val CONDITION_ATTR_OWNER = 9
        const val CONDITION_ATTR_INTERVALDATA = 10
        const val CONDITION_ATTR_SPEEDDELTA = 11
        const val CONDITION_ATTR_FORMULA_MINA = 12
        const val CONDITION_ATTR_FORMULA_MINB = 13
        const val CONDITION_ATTR_FORMULA_MAXA = 14
        const val CONDITION_ATTR_FORMULA_MAXB = 15
        const val CONDITION_ATTR_LIGHTCOLOR = 16
        const val CONDITION_ATTR_LIGHTLEVEL = 17
        const val CONDITION_ATTR_LIGHTTICKS = 18
        const val CONDITION_ATTR_LIGHTINTERVAL = 19
        const val CONDITION_ATTR_SOULTICKS = 20
        const val CONDITION_ATTR_SOULGAIN = 21
        const val CONDITION_ATTR_SKILLS = 22
        const val CONDITION_ATTR_STATS = 23
        const val CONDITION_ATTR_BUFFS = 24
        const val CONDITION_ATTR_OUTFIT = 25
        const val CONDITION_ATTR_PERIODDAMAGE = 26
        const val CONDITION_ATTR_ISBUFF = 27
        const val CONDITION_ATTR_SUBID = 28
        const val CONDITION_ATTR_MANASHIELD = 29
        const val CONDITION_ATTR_ADDSOUND = 30
        const val CONDITION_ATTR_TICKSOUND = 31
        const val CONDITION_ATTR_ABSORBS = 32
        const val CONDITION_ATTR_INCREASES = 33
        const val CONDITION_ATTR_CHARM_CHANCE_MODIFIER = 34
        const val CONDITION_ATTR_PERSISTENT = 35
        const val CONDITION_ATTR_END = 254
    }
}
