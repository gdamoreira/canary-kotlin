package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.combat.condition.CombatType
import org.opentibia.canary.combat.condition.ConditionAttributes
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionSpeed
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.FakeConditionTarget
import org.opentibia.canary.combat.condition.IntervalInfo
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.appearance.outfit.OutfitEntry
import org.opentibia.canary.creatures.players.PlayerSocialState
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.creatures.players.components.StoragePlayer
import org.opentibia.canary.creatures.players.vip.PlayerVip
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcAccountRepository
import org.opentibia.canary.database.JdbcFeatureStateRepository
import org.opentibia.canary.database.JdbcPlayerDepotRepository
import org.opentibia.canary.database.JdbcPlayerItemsRepository
import org.opentibia.canary.database.JdbcPlayerKillsRepository
import org.opentibia.canary.database.JdbcPlayerRepository
import org.opentibia.canary.database.JdbcPlayerRewardsRepository
import org.opentibia.canary.database.JdbcPlayerSpellsRepository
import org.opentibia.canary.database.JdbcPlayerStashRepository
import org.opentibia.canary.database.JdbcPlayerStorageRepository
import org.opentibia.canary.database.JdbcVipRepository
import org.opentibia.canary.io.DbPlayerStorageRepository
import org.opentibia.canary.io.IOGuild
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.kv.KVSQL
import org.opentibia.canary.kv.ValueWrapper
import org.opentibia.canary.map.Position
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.social.guild.InMemoryGuildRepository
import org.opentibia.canary.social.guild.JdbcGuildRepository
import org.opentibia.canary.spells.WorldType
import org.opentibia.canary.systems.familiar.FamiliarEntry

/**
 * Transcript tests for the `IOLoginData` sub-loads ported into
 * [IOLoginDataSubloads] (src/io/functions/iologindata_load_player.cpp):
 *  - `loadPlayerConditions` (:257-275)
 *  - `loadPlayerSkullSystem` (:322-342)
 *  - `loadPlayerGuild` (:394-442)
 *  - `loadPlayerStorageMap` (:760-768)
 *  - `loadPlayerVip` (:770-806)
 *  - `loadPlayerExivaRestrictions` (:1042-1086)
 *
 * The fixture seeds the real H2 (Flyway V1-V19) schema; the condition blob is
 * written with the exact byte layout of the Canary `Condition::serialize`
 * (condition.cpp:277-301, 1830-1848, 2510-2535).
 */
class IOLoginDataSubloadsTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var subloads: IOLoginDataSubloads
    private lateinit var storage: PlayerStorage
    private lateinit var vipComponent: PlayerVip
    private lateinit var social: PlayerSocialState
    private lateinit var kv: KVSQL
    private val addedGuilds = mutableListOf<Guild>()
    private var registryGuild: Guild? = null
    private var worldType: WorldType = WorldType.PVP
    private var ioGuild: IOGuild? = null

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_subloads_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Subload Hero")
        insertPlayer(102L, "Subload Mate")

        storage = newStorage()
        vipComponent = PlayerVip(accountId = { 1L })
        social = PlayerSocialState()
        kv = KVSQL(dataSource)
        addedGuilds.clear()
        registryGuild = null
        worldType = WorldType.PVP
        ioGuild = IOGuild(JdbcGuildRepository(dataSource))

        subloads = newSubloads()
    }

    private fun newSubloads(): IOLoginDataSubloads = IOLoginDataSubloads(
        subloadRepository = DbPlayerSubloadRepository(dataSource),
        storageRepository = DbPlayerStorageRepository(dataSource),
        vip = JdbcVipRepository(dataSource),
        guildMemberships = DbGuildMembershipRepository(dataSource),
        ioGuild = ioGuild,
        playerStorage = { storage },
        playerVip = { vipComponent },
        playerSocialState = { social },
        // `Player::kv()` is the `g_kv().scoped("player").scoped(guid)` root
        // (player.cpp:1398-1400); the loader scopes `exiva-restrictions` from it.
        playerKv = { player -> kv.scoped("player").scoped(player.id.toString()) },
        getGuild = { registryGuild },
        addGuild = { addedGuilds.add(it) },
        now = { NOW },
    )

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    // --- loadPlayerConditions -------------------------------------------------

    @Test
    fun `loadPlayerConditions decodes the persisted condition stream`() {
        seedConditions(
            genericCondition(
                type = ConditionType.CONDITION_INFIGHT,
                id = ConditionId.CONDITIONID_DEFAULT,
                ticks = 5_000,
            ),
        )
        val player = newPlayer()

        subloads.loadPlayerConditions(player)

        val condition = player.storedConditionList.single()
        assertThat(condition.type).isEqualTo(ConditionType.CONDITION_INFIGHT)
        assertThat(condition.id).isEqualTo(ConditionId.CONDITIONID_DEFAULT)
        assertThat(condition.ticks).isEqualTo(5_000)
    }

    @Test
    fun `loadPlayerConditions applies the speed attributes`() {
        seedConditions(speedCondition(speedDelta = 120, mina = 0.5f, minb = 1.0f, maxa = 2.0f, maxb = 3.0f))
        val player = newPlayer()

        subloads.loadPlayerConditions(player)

        val condition = player.storedConditionList.single() as ConditionSpeed
        assertThat(condition.type).isEqualTo(ConditionType.CONDITION_HASTE)
        assertThat(condition.speedDelta).isEqualTo(120)
        assertThat(condition.mina).isEqualTo(0.5f)
        assertThat(condition.minb).isEqualTo(1.0f)
        assertThat(condition.maxa).isEqualTo(2.0f)
        assertThat(condition.maxb).isEqualTo(3.0f)
    }

    @Test
    fun `loadPlayerConditions skips a condition with an unknown attribute`() {
        seedConditions(
            genericCondition(type = ConditionType.CONDITION_INFIGHT, ticks = 5_000, trailingAttrs = { stream ->
                stream.writeU8(200)
                stream.writeI32(1)
            }),
        )
        val player = newPlayer()

        subloads.loadPlayerConditions(player)

        assertThat(player.storedConditionList).isEmpty()
    }

    @Test
    fun `loadPlayerConditions skips an unknown condition type`() {
        seedConditions(byteArrayOf(1, 99, 2, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        val player = newPlayer()

        subloads.loadPlayerConditions(player)

        assertThat(player.storedConditionList).isEmpty()
    }

    @Test
    fun `loadPlayerConditions applies the flat skill stat buff absorb and increase records`() {
        seedConditions(attributesCondition())
        val player = newPlayer()

        subloads.loadPlayerConditions(player)

        val condition = player.storedConditionList.single() as ConditionAttributes
        val target = FakeConditionTarget()
        condition.startCondition(target)

        assertThat(target.varSkills[Skill.SKILL_FIST]).isEqualTo(7)
        assertThat(target.varStats[Stat.STAT_MAXHITPOINTS]).isEqualTo(100)
        assertThat(target.varStats[Stat.STAT_MAXMANAPOINTS]).isEqualTo(50)
        assertThat(target.varStats[Stat.STAT_SOULPOINTS]).isEqualTo(33)
        assertThat(target.varStats[Stat.STAT_MAGICPOINTS]).isEqualTo(9)
        assertThat(target.varStats[Stat.STAT_CAPACITY]).isEqualTo(200)
        assertThat(target.buffValues[Buff.BUFF_AUTOATTACKDEALT]).isEqualTo(15)
        // The serialized absorb/increase index selects the combat type.
        assertThat(target.absorbValues[CombatType.COMBAT_LIFEDRAIN]).isEqualTo(8)
        assertThat(target.increaseValues[CombatType.COMBAT_FIREDAMAGE]).isEqualTo(25)
    }

    @Test
    fun `loadPlayerConditions restores the persistent flag`() {
        seedConditions(
            genericCondition(
                type = ConditionType.CONDITION_INFIGHT,
                id = ConditionId.CONDITIONID_HEAD,
                ticks = 5_000,
                persistent = true,
            ),
        )
        val player = newPlayer()

        subloads.loadPlayerConditions(player)

        // `Condition::unserializeProp` CONDITIONATTR_PERSISTENT sets
        // `m_isPersistent` (condition.cpp:259-267); a HEAD-id condition would
        // otherwise derive false (condition.cpp:475-489).
        assertThat(player.storedConditionList.single().isPersistent()).isTrue()
    }

    @Test
    fun `loadPlayerConditions preserves the interval damage timeLeft and interval`() {
        seedConditions(intervalCondition())
        val player = newPlayer()

        subloads.loadPlayerConditions(player)

        val condition = player.storedConditionList.single() as ConditionDamage
        // `ConditionDamage::unserializeProp` appends the raw IntervalInfo and
        // re-stamps `ticks = ticks + damageInfo.interval` per record
        // (condition.cpp:1817-1824); the damage condition starts at ticks 0
        // (condition.cpp:343-345).
        assertThat(condition.ticks).isEqualTo(4_500)
        assertThat(condition.scheduledDamage).containsExactly(
            IntervalInfo(timeLeft = 500, value = -5, interval = 1_500),
            IntervalInfo(timeLeft = 50, value = -7, interval = 3_000),
        )
    }

    // --- loadPlayerSkullSystem ------------------------------------------------

    @Test
    fun `loadPlayerSkullSystem loads the red skull ticks from the persisted time`() {
        seedSkull(skull = Skulls_t.SKULL_RED.value, skullTime = NOW + 100)
        val player = newPlayer()

        subloads.loadPlayerSkullSystem(player)

        assertThat(player.getSkull()).isEqualTo(Skulls_t.SKULL_RED)
        assertThat(player.pvpPlayer.getSkullTicks()).isEqualTo(102L)
    }

    @Test
    fun `loadPlayerSkullSystem skips an expired skull and the enforced pvp world`() {
        seedSkull(skull = Skulls_t.SKULL_BLACK.value, skullTime = NOW - 1)
        val expired = newPlayer()
        subloads.loadPlayerSkullSystem(expired)
        assertThat(expired.getSkull()).isEqualTo(Skulls_t.SKULL_NONE)
        assertThat(expired.pvpPlayer.getSkullTicks()).isZero()

        seedSkull(skull = Skulls_t.SKULL_BLACK.value, skullTime = NOW + 100)
        worldType = WorldType.PVP_ENFORCED
        val enforced = newPlayer()
        subloads.loadPlayerSkullSystem(enforced)
        assertThat(enforced.getSkull()).isEqualTo(Skulls_t.SKULL_NONE)
        assertThat(enforced.pvpPlayer.getSkullTicks()).isZero()
    }

    // --- loadPlayerGuild ------------------------------------------------------

    @Test
    fun `loadPlayerGuild attaches the guild rank nick wars and member count`() {
        seedGuild(guildId = 7, name = "Wolves", ranks = listOf(Triple(10L, "Leader", 3), Triple(11L, "Member", 1)))
        seedMembership(playerId = 101L, guildId = 7, rankId = 11, nick = "Wolfie")
        seedWar(warId = 1, guild1 = 7, guild2 = 8, status = 1)
        seedMembership(playerId = 102L, guildId = 7, rankId = 10, nick = "")
        val player = newPlayer()

        subloads.loadPlayerGuild(player)

        val guild = player.getGuild()
        assertThat(guild).isNotNull
        assertThat(guild!!.id).isEqualTo(7L)
        assertThat(guild.getMemberCount()).isEqualTo(2L)
        assertThat(player.getGuildRank()!!.id).isEqualTo(11L)
        assertThat(social.getGuildNick()).isEqualTo("Wolfie")
        assertThat(player.pvpPlayer.guildWarVector).containsExactly(8L)
        assertThat(addedGuilds).hasSize(1)
        assertThat(addedGuilds.single().id).isEqualTo(7L)
    }

    @Test
    fun `loadPlayerGuild reloads a missing rank from the guild ranks table`() {
        seedGuild(
            guildId = 7,
            name = "Wolves",
            ranks = listOf(Triple(11L, "Member", 1), Triple(12L, "Vice-Leader", 2)),
        )
        seedMembership(playerId = 101L, guildId = 7, rankId = 12, nick = "")
        registryGuild = Guild(7L, "Wolves")
        val player = newPlayer()

        subloads.loadPlayerGuild(player)

        assertThat(registryGuild!!.getRankById(12L)!!.name).isEqualTo("Vice-Leader")
        assertThat(player.getGuildRank()!!.id).isEqualTo(12L)
        assertThat(addedGuilds).isEmpty()
    }

    @Test
    fun `loadPlayerGuild without a membership row stays inert`() {
        val player = newPlayer()

        subloads.loadPlayerGuild(player)

        assertThat(player.getGuild()).isNull()
        assertThat(social.getGuildNick()).isEmpty()
    }

    @Test
    fun `loadPlayerGuild with an unknown guild row keeps only the nick`() {
        seedGuild(guildId = 7, name = "Wolves", ranks = listOf(Triple(11L, "Member", 1)))
        seedMembership(playerId = 101L, guildId = 7, rankId = 11, nick = "Wolfie")
        ioGuild = IOGuild(InMemoryGuildRepository())
        val player = newPlayer()

        newSubloads().loadPlayerGuild(player)

        assertThat(player.getGuild()).isNull()
        assertThat(player.getGuildRank()).isNull()
        assertThat(social.getGuildNick()).isEqualTo("Wolfie")
        assertThat(addedGuilds).isEmpty()
    }

    // --- loadPlayerStorageMap -------------------------------------------------

    @Test
    fun `loadPlayerStorageMap ingests the persisted rows`() {
        DbPlayerStorageRepository(dataSource).upsert(101L, mapOf(100 to 7, 200 to -3))
        val player = newPlayer()

        subloads.loadPlayerStorageMap(player)

        assertThat(storage.get(100)).isEqualTo(7)
        assertThat(storage.get(200)).isEqualTo(-3)
        assertThat(storage.getModifiedKeys()).isEmpty()
    }

    // --- loadPlayerVip --------------------------------------------------------

    @Test
    fun `loadPlayerVip loads the entries groups and memberships`() {
        val repository = JdbcVipRepository(dataSource)
        repository.addVipEntry(1L, 102L, "buddy", 1, true)
        repository.addVipGroupEntry(1, 1L, "Enemies", false)
        repository.addGuidVipGroupEntry(1, 1L, 102L)
        val player = newPlayer()
        player.account = Account(id = 1L)

        subloads.loadPlayerVip(player)

        assertThat(vipComponent.exists(102L)).isTrue()
        assertThat(vipComponent.getGroupByID(1)!!.name).isEqualTo("Enemies")
        assertThat(vipComponent.getGroupByID(1)!!.vipGroupGuids).containsExactly(102L)
    }

    @Test
    fun `loadPlayerVip without an account stays inert`() {
        JdbcVipRepository(dataSource).addVipEntry(1L, 102L, "", 0, false)
        val player = newPlayer()

        subloads.loadPlayerVip(player)

        assertThat(vipComponent.getVipEntries()).isEmpty()
    }

    // --- loadPlayerExivaRestrictions ------------------------------------------

    @Test
    fun `loadPlayerExivaRestrictions reads the flags and whitelists from the player kv`() {
        val scope = kv.scoped("player").scoped("101").scoped("exiva-restrictions")
        scope.set("allowAll", ValueWrapper(1))
        scope.set("allowOwnGuild", ValueWrapper(0))
        scope.set("allowOwnParty", ValueWrapper(1))
        scope.set("allowVipList", ValueWrapper(0))
        scope.set("allowPlayerWhitelist", ValueWrapper(1))
        scope.set("allowGuildWhitelist", ValueWrapper(0))
        scope.set(
            "playerWhitelist",
            ValueWrapper(listOf(ValueWrapper(42), ValueWrapper(43))),
        )
        scope.set(
            "guildWhitelist",
            ValueWrapper(listOf(ValueWrapper(7))),
        )
        val player = newPlayer()

        subloads.loadPlayerExivaRestrictions(player)

        val restrictions = player.getExivaRestrictions()
        assertThat(restrictions.allowAll).isTrue()
        assertThat(restrictions.allowOwnGuild).isFalse()
        assertThat(restrictions.allowOwnParty).isTrue()
        assertThat(restrictions.allowVipList).isFalse()
        assertThat(restrictions.allowPlayerWhitelist).isTrue()
        assertThat(restrictions.allowGuildWhitelist).isFalse()
        assertThat(restrictions.playerWhitelist).containsExactly(42L, 43L)
        assertThat(restrictions.guildWhitelist).containsExactly(7L)
    }

    @Test
    fun `loadPlayerExivaRestrictions mirrors the Canary getNumber boolean behavior`() {
        val scope = kv.scoped("player").scoped("101").scoped("exiva-restrictions")
        scope.set("allowAll", ValueWrapper(true))
        val player = newPlayer()

        subloads.loadPlayerExivaRestrictions(player)

        // `ValueWrapper::getNumber()` (value_wrapper.hpp:51-58) returns 0.0 for
        // the boolean variant Canary's saver writes, so a persisted `true`
        // loads as `false` exactly like the C++ loader.
        assertThat(player.getExivaRestrictions().allowAll).isFalse()
    }

    // --- loadPlayer integration -----------------------------------------------

    @Test
    fun `loadPlayerById runs the sub-loads through IOLoginData`() {
        DbPlayerStorageRepository(dataSource).upsert(101L, mapOf(100 to 7))
        JdbcVipRepository(dataSource).addVipEntry(1L, 102L, "", 0, false)
        seedGuild(guildId = 7, name = "Wolves", ranks = listOf(Triple(11L, "Member", 1)))
        seedMembership(playerId = 101L, guildId = 7, rankId = 11, nick = "Wolfie")
        seedSkull(skull = Skulls_t.SKULL_RED.value, skullTime = NOW + 100)
        seedConditions(genericCondition(type = ConditionType.CONDITION_INFIGHT, ticks = 5_000))
        val scope = kv.scoped("player").scoped("101").scoped("exiva-restrictions")
        scope.set("allowAll", ValueWrapper(1))

        val loginData = IOLoginData(
            players = JdbcPlayerRepository(dataSource),
            accounts = JdbcAccountRepository(dataSource),
            vip = JdbcVipRepository(dataSource),
            storageRepository = JdbcPlayerStorageRepository(dataSource),
            spells = JdbcPlayerSpellsRepository(dataSource),
            kills = JdbcPlayerKillsRepository(dataSource),
            stash = JdbcPlayerStashRepository(dataSource),
            rewards = JdbcPlayerRewardsRepository(dataSource),
            features = JdbcFeatureStateRepository(dataSource),
            items = JdbcPlayerItemsRepository(dataSource),
            depots = JdbcPlayerDepotRepository(dataSource),
            subloads = subloads,
        )
        val player = newPlayer()
        player.account = Account(id = 1L)

        assertThat(loginData.loadPlayerById(player, 101L, disableIrrelevantInfo = false)).isTrue()

        assertThat(player.getSkull()).isEqualTo(Skulls_t.SKULL_RED)
        assertThat(player.storedConditionList).hasSize(1)
        assertThat(player.getGuild()!!.id).isEqualTo(7L)
        assertThat(social.getGuildNick()).isEqualTo("Wolfie")
        assertThat(storage.get(100)).isEqualTo(7)
        assertThat(vipComponent.exists(102L)).isTrue()
        assertThat(player.getExivaRestrictions().allowAll).isTrue()
    }

    @Test
    fun `loadPlayerById without an injected subloads keeps the pre-load state`() {
        seedSkull(skull = Skulls_t.SKULL_RED.value, skullTime = NOW + 100)
        seedConditions(genericCondition(type = ConditionType.CONDITION_INFIGHT, ticks = 5_000))
        val loginData = IOLoginData(
            players = JdbcPlayerRepository(dataSource),
            accounts = JdbcAccountRepository(dataSource),
            vip = JdbcVipRepository(dataSource),
            storageRepository = JdbcPlayerStorageRepository(dataSource),
            spells = JdbcPlayerSpellsRepository(dataSource),
            kills = JdbcPlayerKillsRepository(dataSource),
            stash = JdbcPlayerStashRepository(dataSource),
            rewards = JdbcPlayerRewardsRepository(dataSource),
            features = JdbcFeatureStateRepository(dataSource),
            items = JdbcPlayerItemsRepository(dataSource),
            depots = JdbcPlayerDepotRepository(dataSource),
        )
        val player = newPlayer()

        assertThat(loginData.loadPlayerById(player, 101L)).isTrue()

        assertThat(player.getSkull()).isEqualTo(Skulls_t.SKULL_NONE)
        assertThat(player.storedConditionList).isEmpty()
        assertThat(player.getGuild()).isNull()
    }

    // --- fixtures -------------------------------------------------------------

    private fun newPlayer(): Player = Player(
        id = 101L,
        name = "Subload Hero",
        maxHealth = 1,
        health = 1,
        position = Position(1, 1, 7),
    ).also { it.worldTypeProvider = { worldType } }

    private fun newStorage(): PlayerStorage {
        val owner = object : StoragePlayer {
            override fun getName(): String = "Subload Hero"

            override fun getOutfits(): MutableList<OutfitEntry> = mutableListOf()

            override fun getFamiliars(): MutableList<FamiliarEntry> = mutableListOf()
        }
        return PlayerStorage(owner)
    }

    private fun insertPlayer(id: Long, name: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO players (
                    id, name, account_id, level, vocation, health, healthmax, mana, manamax,
                    experience, posx, posy, posz, cap, sex, pronoun, town_id
                ) VALUES (?, ?, 1, 10, 4, 100, 100, 50, 50, 1, 1000, 1000, 7, 350, 1, 3, 1)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
        }
    }

    private fun seedConditions(blob: ByteArray) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE players SET conditions = ? WHERE id = 101").use { stmt ->
                stmt.setBytes(1, blob)
                stmt.executeUpdate()
            }
        }
    }

    private fun seedSkull(skull: Int, skullTime: Long) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE players SET skull = ?, skulltime = ? WHERE id = 101").use { stmt ->
                stmt.setInt(1, skull)
                stmt.setLong(2, skullTime)
                stmt.executeUpdate()
            }
        }
    }

    private fun seedGuild(guildId: Long, name: String, ranks: List<Triple<Long, String, Int>>) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO guilds (id, name) VALUES (?, ?)").use { stmt ->
                stmt.setLong(1, guildId)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
            for ((rankId, rankName, level) in ranks) {
                conn.prepareStatement("INSERT INTO guild_ranks (id, guild_id, name, level) VALUES (?, ?, ?, ?)")
                    .use { stmt ->
                        stmt.setLong(1, rankId)
                        stmt.setLong(2, guildId)
                        stmt.setString(3, rankName)
                        stmt.setInt(4, level)
                        stmt.executeUpdate()
                    }
            }
        }
    }

    private fun seedMembership(playerId: Long, guildId: Long, rankId: Long, nick: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO guild_membership (player_id, guild_id, rank_id, nick) VALUES (?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setLong(2, guildId)
                stmt.setLong(3, rankId)
                stmt.setString(4, nick)
                stmt.executeUpdate()
            }
        }
    }

    private fun seedWar(warId: Long, guild1: Long, guild2: Long, status: Int) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO guild_wars (id, guild1, guild2, status) VALUES (?, ?, ?, ?)").use { stmt ->
                stmt.setLong(1, warId)
                stmt.setLong(2, guild1)
                stmt.setLong(3, guild2)
                stmt.setInt(4, status)
                stmt.executeUpdate()
            }
        }
    }

    private fun genericCondition(
        type: ConditionType,
        ticks: Int,
        id: ConditionId = ConditionId.CONDITIONID_COMBAT,
        persistent: Boolean = false,
        trailingAttrs: ((PropWriteStream) -> Unit)? = null,
    ): ByteArray {
        val stream = PropWriteStream()
        conditionHeader(stream, type, id, ticks)
        stream.writeU8(CONDITION_ATTR_TICKSOUND)
        stream.writeU16(0)
        stream.writeU8(CONDITION_ATTR_ADDSOUND)
        stream.writeU16(0)
        stream.writeU8(CONDITION_ATTR_PERSISTENT)
        stream.writeU8(if (persistent) 1 else 0)
        trailingAttrs?.invoke(stream)
        stream.writeU8(CONDITION_ATTR_END)
        return stream.getStream()
    }

    /**
     * One `ConditionAttributes::serialize` stream
     * (condition.cpp:789-820): one flat skill, all five flat stats, one flat
     * buff and the COMBAT_COUNT absorb/increase pairs.
     */
    private fun attributesCondition(): ByteArray {
        val stream = PropWriteStream()
        conditionHeader(
            stream,
            ConditionType.CONDITION_ATTRIBUTES,
            ConditionId.CONDITIONID_DEFAULT,
            ticks = 10_000,
        )
        stream.writeU8(CONDITION_ATTR_SKILLS)
        stream.writeI32(7)
        for (value in listOf(100, 50, 33, 9, 200)) {
            stream.writeU8(CONDITION_ATTR_STATS)
            stream.writeI32(value)
        }
        stream.writeU8(CONDITION_ATTR_BUFFS)
        stream.writeI32(15)
        stream.writeU8(CONDITION_ATTR_ABSORBS)
        for (index in 0 until COMBAT_COUNT) {
            stream.writeU8(index)
            stream.writeI32(if (index == CombatType.COMBAT_LIFEDRAIN.value) 8 else 0)
        }
        stream.writeU8(CONDITION_ATTR_INCREASES)
        for (index in 0 until COMBAT_COUNT) {
            stream.writeU8(index)
            stream.writeI32(if (index == CombatType.COMBAT_FIREDAMAGE.value) 25 else 0)
        }
        stream.writeU8(CONDITION_ATTR_END)
        return stream.getStream()
    }

    /**
     * One `ConditionDamage::serialize` stream
     * (condition.cpp:1830-1848): two raw `IntervalInfo` rounds with a
     * `timeLeft` that differs from their interval.
     */
    private fun intervalCondition(): ByteArray {
        val stream = PropWriteStream()
        conditionHeader(
            stream,
            ConditionType.CONDITION_POISON,
            ConditionId.CONDITIONID_DEFAULT,
            ticks = 9_000,
        )
        stream.writeU8(CONDITION_ATTR_INTERVALDATA)
        stream.writeI32(500)
        stream.writeI32(-5)
        stream.writeI32(1_500)
        stream.writeU8(CONDITION_ATTR_INTERVALDATA)
        stream.writeI32(50)
        stream.writeI32(-7)
        stream.writeI32(3_000)
        stream.writeU8(CONDITION_ATTR_END)
        return stream.getStream()
    }

    private fun speedCondition(speedDelta: Int, mina: Float, minb: Float, maxa: Float, maxb: Float): ByteArray {
        val stream = PropWriteStream()
        conditionHeader(stream, ConditionType.CONDITION_HASTE, ConditionId.CONDITIONID_COMBAT, 10_000)
        stream.writeU8(CONDITION_ATTR_TICKSOUND)
        stream.writeU16(0)
        stream.writeU8(CONDITION_ATTR_ADDSOUND)
        stream.writeU16(0)
        stream.writeU8(CONDITION_ATTR_PERSISTENT)
        stream.writeU8(0)
        stream.writeU8(CONDITION_ATTR_SPEEDDELTA)
        stream.writeI32(speedDelta)
        stream.writeU8(CONDITION_ATTR_FORMULA_MINA)
        stream.writeU32(mina.toRawBits().toLong() and 0xFFFFFFFFL)
        stream.writeU8(CONDITION_ATTR_FORMULA_MINB)
        stream.writeU32(minb.toRawBits().toLong() and 0xFFFFFFFFL)
        stream.writeU8(CONDITION_ATTR_FORMULA_MAXA)
        stream.writeU32(maxa.toRawBits().toLong() and 0xFFFFFFFFL)
        stream.writeU8(CONDITION_ATTR_FORMULA_MAXB)
        stream.writeU32(maxb.toRawBits().toLong() and 0xFFFFFFFFL)
        stream.writeU8(CONDITION_ATTR_END)
        return stream.getStream()
    }

    private fun conditionHeader(stream: PropWriteStream, type: ConditionType, id: ConditionId, ticks: Int) {
        stream.writeU8(CONDITION_ATTR_TYPE)
        stream.writeU8(type.value)
        stream.writeU8(CONDITION_ATTR_ID)
        stream.writeU32(id.value.toLong() and 0xFFFFFFFFL)
        stream.writeU8(CONDITION_ATTR_TICKS)
        stream.writeI32(ticks)
        stream.writeU8(CONDITION_ATTR_ISBUFF)
        stream.writeU8(0)
        stream.writeU8(CONDITION_ATTR_SUBID)
        stream.writeU32(0)
    }

    private companion object {
        const val NOW = 1_700_000_000L

        const val COMBAT_COUNT = 14

        const val CONDITION_ATTR_TYPE = 1
        const val CONDITION_ATTR_ID = 2
        const val CONDITION_ATTR_TICKS = 3
        const val CONDITION_ATTR_INTERVALDATA = 10
        const val CONDITION_ATTR_SPEEDDELTA = 11
        const val CONDITION_ATTR_FORMULA_MINA = 12
        const val CONDITION_ATTR_FORMULA_MINB = 13
        const val CONDITION_ATTR_FORMULA_MAXA = 14
        const val CONDITION_ATTR_FORMULA_MAXB = 15
        const val CONDITION_ATTR_SKILLS = 22
        const val CONDITION_ATTR_STATS = 23
        const val CONDITION_ATTR_BUFFS = 24
        const val CONDITION_ATTR_ISBUFF = 27
        const val CONDITION_ATTR_SUBID = 28
        const val CONDITION_ATTR_ADDSOUND = 30
        const val CONDITION_ATTR_TICKSOUND = 31
        const val CONDITION_ATTR_ABSORBS = 32
        const val CONDITION_ATTR_INCREASES = 33
        const val CONDITION_ATTR_PERSISTENT = 35
        const val CONDITION_ATTR_END = 254
    }
}
