package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the batch 4 Canary `Player` binding surface. Each test
 * installs [PlayerFunctionsExtra] with a fake [PlayerExtraOperations] seam and drives it
 * through real Lua chunks using the exact Canary method names from
 * src/lua/functions/creatures/player/player_functions.cpp.
 */
class PlayerExtraBindingsTest {

    private class FakePlayer

    private class FakeExtraOps : StubPlayerExtraOperations() {
        var idleTime = 42L
        var freeBackpackSlots = 7
        var offline = false
        var forgeDusts = 0L
        var forgeDustLevel = 0L
        var forgeSlivers = 11L
        var forgeCores = 22L
        var forgeOpen = false
        var faction = 0
        var uiExhausted = false
        var bossPoints = 0L
        var bossBonus = 0L
        var bossLevel = 0
        var bossKills = 0
        var lastSoundId: Int? = null
        var lastActor: Boolean? = null
        var lastDoubleActor: Boolean? = null
        var lastMainSound: Int? = null
        var lastSecondarySound: Int? = null
        var lastFlag: Int? = null
        var flagSet = false
        var hazardPoints = 0
        var loyaltyBonus = 0
        var loyaltyPoints = 0
        var loyaltyTitle = "Squire"
        var lastLoyaltyTitle: String? = null
        var instantValue = false
        var instantLevel = 0
        var upgradeLevel = 0
        var stage = 0
        var avatarTicks = 0L
        var lastUpgradeAdd: Boolean? = null
        var lastReloadForce: Boolean? = null
        var lastConcoction: Pair<Int, Int>? = null
        var lastFood: Pair<Int, Long>? = null
        var lastCooldowns: Triple<Boolean, Boolean, Int>? = null
        var vip = true
        var vipDays: Int? = 30
        var vipTime = 12345L
        var storeInbox: Any? = null
        var achievementsUnlocked = 0
        var achievementPoints = 5
        var lastAchievementSendMessage: Boolean? = null
        var titles = listOf(LuaPlayerTitle(1, "Hero", "A brave soul"))
        var currentTitle = 0
        var lastTransaction: Triple<Int, Int, String>? = null
        var lastScreenshot: List<Any>? = null
        var lastIcon: Int? = null
        var lastRaceOverlay: Triple<Int, Int, Boolean>? = null
        var lastCreatureAppearLogin: Boolean? = null
        var animusMasteries = linkedSetOf<String>()
        var lastSerene: Pair<Boolean, Int>? = null
        var virtue = 0
        var harmony = 3
        var harmonyDamage = 10L to 20L
        var flatDamageHealing = 55L
        var lastWeaponExperience: Pair<Int, Int>? = null
        var livestreamViewerCount = 4
        var livestreamViewer = false
        var livestreamState = LuaLivestreamViewers(
            description = "watch me",
            broadcast = true,
            password = "secret",
            names = listOf("viewer-1"),
            mutes = listOf("mute-1"),
            bans = listOf("ban-1"),
            kick = listOf("kick-1"),
        )
        var lastLivestreamOverrides: LuaLivestreamOverrides? = null
        var mapShader = "default-shader"
        var lastShader: String? = null
        var lastCustomOutfit: Triple<String, Any, Boolean>? = null
        var lastName: String? = null

        override fun getIdleTime(player: Any): Long? = idleTime

        override fun getFreeBackpackSlots(player: Any): Int? = freeBackpackSlots

        override fun isOffline(player: Any): Boolean = offline

        override fun openMarket(player: Any): Boolean = true

        override fun openForge(player: Any): Boolean {
            forgeOpen = true
            return true
        }

        override fun closeForge(player: Any): Boolean {
            forgeOpen = false
            return true
        }

        override fun addForgeDusts(player: Any, amount: Long): Boolean {
            forgeDusts += amount
            return true
        }

        override fun removeForgeDusts(player: Any, amount: Long): Boolean {
            forgeDusts -= amount
            return true
        }

        override fun getForgeDusts(player: Any): Long = forgeDusts

        override fun setForgeDusts(player: Any, amount: Long): Boolean {
            forgeDusts = amount
            return true
        }

        override fun addForgeDustLevel(player: Any, amount: Long): Boolean {
            forgeDustLevel += amount
            return true
        }

        override fun removeForgeDustLevel(player: Any, amount: Long): Boolean {
            forgeDustLevel -= amount
            return true
        }

        override fun getForgeDustLevel(player: Any): Long = forgeDustLevel

        override fun getForgeSlivers(player: Any): Long = forgeSlivers

        override fun getForgeCores(player: Any): Long = forgeCores

        override fun setFaction(player: Any, factionId: Int): Boolean {
            faction = factionId
            return true
        }

        override fun getFaction(player: Any): Int = faction

        override fun isUIExhausted(player: Any, time: Int): Boolean = uiExhausted

        override fun updateUIExhausted(player: Any): Boolean {
            uiExhausted = true
            return true
        }

        override fun sendBosstiaryCooldownTimer(player: Any): Boolean = true

        override fun getBosstiaryLevel(player: Any, monsterName: String): Int? =
            if (monsterName == "Demon") bossLevel else null

        override fun getBosstiaryKills(player: Any, monsterName: String): Int? =
            if (monsterName == "Demon") bossKills else null

        override fun addBosstiaryKill(player: Any, monsterName: String, amount: Long): Boolean {
            bossKills += amount.toInt()
            return true
        }

        override fun setBossPoints(player: Any, points: Long): Boolean {
            bossPoints = points
            return true
        }

        override fun setRemoveBossTime(player: Any, time: Int): Boolean = true

        override fun getSlotBossId(player: Any, slotId: Int): Long = slotId.toLong()

        override fun getBossBonus(player: Any, slotId: Int): Long = bossBonus

        override fun sendSingleSoundEffect(player: Any, soundId: Int, actor: Boolean): Boolean {
            lastSoundId = soundId
            lastActor = actor
            return true
        }

        override fun sendDoubleSoundEffect(
            player: Any,
            mainSoundId: Int,
            secondarySoundId: Int,
            actor: Boolean,
        ): Boolean {
            lastMainSound = mainSoundId
            lastSecondarySound = secondarySoundId
            lastDoubleActor = actor
            return true
        }

        override fun sendAmbientSoundEffect(player: Any, soundId: Int): Boolean {
            lastSoundId = soundId
            return true
        }

        override fun sendMusicSoundEffect(player: Any, soundId: Int): Boolean {
            lastSoundId = soundId
            return true
        }

        override fun changeName(player: Any, newName: String): Boolean {
            lastName = newName
            return true
        }

        override fun hasGroupFlag(player: Any, flag: Int): Boolean {
            lastFlag = flag
            return flagSet
        }

        override fun setGroupFlag(player: Any, flag: Int): Boolean {
            lastFlag = flag
            flagSet = true
            return true
        }

        override fun removeGroupFlag(player: Any, flag: Int): Boolean {
            lastFlag = flag
            flagSet = false
            return true
        }

        override fun setHazardSystemPoints(player: Any, amount: Int): Boolean {
            hazardPoints = amount
            return true
        }

        override fun getHazardSystemPoints(player: Any): Int = hazardPoints

        override fun setLoyaltyBonus(player: Any, amount: Int): Boolean {
            loyaltyBonus = amount
            return true
        }

        override fun getLoyaltyBonus(player: Any): Int = loyaltyBonus

        override fun getLoyaltyPoints(player: Any): Int = loyaltyPoints

        override fun getLoyaltyTitle(player: Any): String = loyaltyTitle

        override fun setLoyaltyTitle(player: Any, name: String): Boolean {
            lastLoyaltyTitle = name
            loyaltyTitle = name
            return true
        }

        override fun instantSkillWOD(player: Any, name: String, value: Boolean?): Boolean {
            if (value == null) {
                return instantValue
            }
            instantValue = value
            return true
        }

        override fun upgradeSpellsWOD(player: Any, name: String?, add: Boolean?): Any {
            if (name == null) {
                upgradeLevel = 0
                return true
            }
            if (add == null) {
                return upgradeLevel
            }
            lastUpgradeAdd = add
            upgradeLevel += if (add) 1 else -1
            return true
        }

        override fun revelationStageWOD(player: Any, name: String?, stage: Int?): Any {
            if (name == null) {
                this.stage = 0
                return true
            }
            if (stage == null) {
                return this.stage
            }
            this.stage = stage
            return true
        }

        override fun reloadData(player: Any): Boolean = true

        override fun onThinkWheelOfDestiny(player: Any, force: Boolean): Boolean {
            lastReloadForce = force
            return true
        }

        override fun avatarTimer(player: Any, value: Long?): Any {
            if (value == null) {
                return avatarTicks
            }
            avatarTicks = value
            return true
        }

        override fun getWheelSpellAdditionalArea(player: Any, spellName: String): Boolean =
            spellName == "Death Strike"

        override fun getWheelSpellAdditionalTarget(player: Any, spellName: String): Long =
            if (spellName == "Death Strike") 2L else 0L

        override fun getWheelSpellAdditionalDuration(player: Any, spellName: String): Long =
            if (spellName == "Death Strike") 4L else 0L

        override fun wheelUnlockScroll(player: Any, scrollName: String): Boolean = scrollName == "Scroll of Ascension"

        override fun updateConcoction(player: Any, itemId: Int, timeLeft: Int): Boolean {
            lastConcoction = itemId to timeLeft
            return true
        }

        override fun updateFood(player: Any, itemId: Int, timeLeft: Long): Boolean {
            lastFood = itemId to timeLeft
            return true
        }

        override fun clearSpellCooldowns(player: Any, spenders: Boolean, builders: Boolean, ticks: Int): Boolean {
            lastCooldowns = Triple(spenders, builders, ticks)
            return true
        }

        override fun isVip(player: Any): Boolean = vip

        override fun getVipDays(player: Any): Int? = vipDays

        override fun getVipTime(player: Any): Long = vipTime

        override fun getKv(player: Any): Any = Unit

        override fun getStoreInbox(player: Any): Any? = storeInbox

        override fun achievementIdByName(name: String): Int = if (name == "Pitfighter") 7 else 0

        override fun hasAchievement(player: Any, achievementId: Int): Boolean = achievementsUnlocked == achievementId

        override fun addAchievement(player: Any, achievementId: Int, sendMessage: Boolean): Boolean {
            lastAchievementSendMessage = sendMessage
            achievementsUnlocked = achievementId
            return true
        }

        override fun removeAchievement(player: Any, achievementId: Int): Boolean {
            achievementsUnlocked = 0
            return true
        }

        override fun getAchievementPoints(player: Any): Int = achievementPoints

        override fun addAchievementPoints(player: Any, amount: Int): Boolean {
            achievementPoints += amount
            return true
        }

        override fun removeAchievementPoints(player: Any, amount: Int): Boolean {
            achievementPoints -= amount
            return true
        }

        override fun addBadge(player: Any, badgeId: Int): Boolean = true

        override fun addTitle(player: Any, titleId: Int): Boolean = true

        override fun getTitles(player: Any): List<LuaPlayerTitle> = titles

        override fun setCurrentTitle(player: Any, titleId: Int): Boolean {
            if (titleId == 0) {
                return false
            }
            currentTitle = titleId
            return true
        }

        override fun createTransactionSummary(player: Any, type: Int, amount: Int, id: String): Boolean {
            if (type == 0) {
                return false
            }
            lastTransaction = Triple(type, amount, id)
            return true
        }

        override fun takeScreenshot(
            player: Any,
            type: Int,
            skillId: Int,
            skillLevel: Int,
            achievementName: String,
            raceId: Int,
            bestiaryStep: Int,
        ): Boolean {
            lastScreenshot = listOf(type, skillId, skillLevel, achievementName, raceId, bestiaryStep)
            return true
        }

        override fun sendIconBakragore(player: Any, iconType: Int): Boolean {
            lastIcon = iconType
            return true
        }

        override fun removeIconBakragore(player: Any, iconType: Int): Boolean {
            lastIcon = iconType
            return true
        }

        override fun sendCreatureAppear(player: Any, isLogin: Boolean): Boolean {
            lastCreatureAppearLogin = isLogin
            return true
        }

        override fun setRaceIconOverlay(player: Any, raceId: Int, icon: Int, enabled: Boolean): Boolean {
            lastRaceOverlay = Triple(raceId, icon, enabled)
            return true
        }

        override fun clearRaceIconOverlays(player: Any, icon: Int): Boolean {
            lastIcon = icon
            return true
        }

        override fun refreshVisibleCreatureIcons(player: Any): Boolean = true

        override fun addAnimusMastery(player: Any, monsterType: String): Boolean {
            animusMasteries += monsterType
            return true
        }

        override fun removeAnimusMastery(player: Any, monsterType: String): Boolean {
            animusMasteries -= monsterType
            return true
        }

        override fun hasAnimusMastery(player: Any, monsterType: String): Boolean = monsterType in animusMasteries

        override fun setSerene(player: Any, set: Boolean, ticks: Int): Boolean {
            lastSerene = set to ticks
            return true
        }

        override fun getVirtue(player: Any): Int = virtue

        override fun setVirtue(player: Any, virtue: Int): Boolean {
            this.virtue = virtue
            return true
        }

        override fun fillHarmony(player: Any): Boolean {
            harmony += 1
            return true
        }

        override fun getHarmony(player: Any): Int = harmony

        override fun getHarmonyDamage(player: Any, baseMin: Int, baseMax: Int): Pair<Long, Long> = harmonyDamage

        override fun calculateFlatDamageHealing(player: Any): Long = flatDamageHealing

        override fun addWeaponExperience(player: Any, experience: Int, itemId: Int): Boolean {
            lastWeaponExperience = experience to itemId
            return true
        }

        override fun getLivestreamViewersCount(player: Any): Int = livestreamViewerCount

        override fun getLivestreamViewers(player: Any): LuaLivestreamViewers = livestreamState

        override fun setLivestreamViewers(player: Any, viewers: LuaLivestreamOverrides): Boolean {
            lastLivestreamOverrides = viewers
            return true
        }

        override fun isLivestreamViewer(player: Any): Boolean = livestreamViewer

        override fun getMapShader(player: Any): String = mapShader

        override fun setMapShader(player: Any, shaderName: String): Boolean {
            lastShader = shaderName
            mapShader = shaderName
            return true
        }

        override fun addCustomOutfit(player: Any, type: String, idOrName: Any): Boolean {
            lastCustomOutfit = Triple(type, idOrName, true)
            return true
        }

        override fun removeCustomOutfit(player: Any, type: String, idOrName: Any): Boolean {
            lastCustomOutfit = Triple(type, idOrName, false)
            return true
        }
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(fake: FakeExtraOps = FakeExtraOps()): Pair<LuaEnvironment, FakeExtraOps> {
        val environment = env()
        PlayerFunctionsExtra.install(environment, fake)
        environment.globals.set("p", environment.userdata.wrap("Player", FakePlayer()))
        return environment to fake
    }

    @Test
    fun `market idle and backpack helpers read through the seam`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            idle = p:getIdleTime()
            free = p:getFreeBackpackSlots()
            off = p:isOffline()
            market = p:openMarket()
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("idle").toint()).isEqualTo(42)
        assertThat(environment.getGlobal("free").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("off").toboolean()).isFalse()
        assertThat(environment.getGlobal("market").toboolean()).isTrue()
        assertThat(fake.forgeOpen).isFalse()
    }

    @Test
    fun `forge dusts and levels round trip`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            p:setForgeDusts(10)
            p:addForgeDusts(5)
            p:removeForgeDusts(3)
            dusts = p:getForgeDusts()
            p:addForgeDustLevel(2)
            p:removeForgeDustLevel(1)
            level = p:getForgeDustLevel()
            slivers = p:getForgeSlivers()
            cores = p:getForgeCores()
            opened = p:openForge()
            closed = p:closeForge()
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("dusts").toint()).isEqualTo(12)
        assertThat(environment.getGlobal("level").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("slivers").toint()).isEqualTo(11)
        assertThat(environment.getGlobal("cores").toint()).isEqualTo(22)
        assertThat(environment.getGlobal("opened").toboolean()).isTrue()
        assertThat(environment.getGlobal("closed").toboolean()).isTrue()
    }

    @Test
    fun `faction and UI exhaustion follow the C defaults`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            p:setFaction(2)
            faction = p:getFaction()
            before = p:isUIExhausted(500)
            p:updateUIExhausted()
            after = p:isUIExhausted(500)
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("faction").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("before").toboolean()).isFalse()
        assertThat(environment.getGlobal("after").toboolean()).isTrue()
    }

    @Test
    fun `bosstiary queries resolve monster names and points`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            level = p:getBosstiaryLevel("Demon")
            missing = p:getBosstiaryLevel("Rat")
            kills = p:getBosstiaryKills("Demon")
            p:addBosstiaryKill("Demon", 3)
            killsAfter = p:getBosstiaryKills("Demon")
            p:setBossPoints(2500)
            p:setRemoveBossTime(1)
            slot = p:getSlotBossId(4)
            bonus = p:getBossBonus(4)
            timer = p:sendBosstiaryCooldownTimer()
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("level").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(environment.getGlobal("killsAfter").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("slot").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("bonus").toint()).isZero()
        assertThat(environment.getGlobal("timer").toboolean()).isTrue()
    }

    @Test
    fun `sound effects default the actor flag to true`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            single = p:sendSingleSoundEffect(31)
            singleActor = p:sendSingleSoundEffect(32, false)
            double = p:sendDoubleSoundEffect(41, 42)
            ambient = p:sendAmbientSoundEffect(51)
            music = p:sendMusicSoundEffect(61)
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("single").toboolean()).isTrue()
        assertThat(fake.lastSoundId).isEqualTo(61)
        assertThat(environment.getGlobal("double").toboolean()).isTrue()
        assertThat(fake.lastMainSound).isEqualTo(41)
        assertThat(fake.lastSecondarySound).isEqualTo(42)
        assertThat(environment.getGlobal("ambient").toboolean()).isTrue()
        assertThat(environment.getGlobal("music").toboolean()).isTrue()
        assertThat(fake.lastActor).isFalse()
        assertThat(fake.lastDoubleActor).isTrue()
    }

    @Test
    fun `group flags hazard and loyalty state`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            hasBefore = p:hasGroupFlag(8)
            p:setGroupFlag(8)
            hasAfter = p:hasGroupFlag(8)
            p:removeGroupFlag(8)
            hasRemoved = p:hasGroupFlag(8)
            p:setHazardSystemPoints(15)
            hazard = p:getHazardSystemPoints()
            p:setLoyaltyBonus(25)
            bonus = p:getLoyaltyBonus()
            points = p:getLoyaltyPoints()
            title = p:getLoyaltyTitle()
            p:setLoyaltyTitle("Knight")
            titleAfter = p:getLoyaltyTitle()
            renamed = p:changeName("New Name")
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("hasAfter").toboolean()).isTrue()
        assertThat(environment.getGlobal("hasRemoved").toboolean()).isFalse()
        assertThat(environment.getGlobal("hazard").toint()).isEqualTo(15)
        assertThat(environment.getGlobal("bonus").toint()).isEqualTo(25)
        assertThat(environment.getGlobal("title").tojstring()).isEqualTo("Squire")
        assertThat(environment.getGlobal("titleAfter").tojstring()).isEqualTo("Knight")
        assertThat(environment.getGlobal("renamed").toboolean()).isTrue()
        assertThat(fake.lastName).isEqualTo("New Name")
    }

    @Test
    fun `wheel instant upgrades and revelation stages match argument counts`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            instant = p:instantSkillWOD("Avatar")
            set = p:instantSkillWOD("Avatar", true)
            instantAfter = p:instantSkillWOD("Avatar")
            upgrade = p:upgradeSpellsWOD("Heal")
            upgraded = p:upgradeSpellsWOD("Heal", true)
            upgradeAfter = p:upgradeSpellsWOD("Heal")
            reset = p:upgradeSpellsWOD()
            stage = p:revelationStageWOD("Beam")
            stageSet = p:revelationStageWOD("Beam", 2)
            stageAfter = p:revelationStageWOD("Beam")
            stageReset = p:revelationStageWOD()
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("instant").toboolean()).isFalse()
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("instantAfter").toboolean()).isTrue()
        assertThat(environment.getGlobal("upgrade").isnumber()).isTrue()
        assertThat(environment.getGlobal("upgraded").toboolean()).isTrue()
        assertThat(environment.getGlobal("upgradeAfter").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("reset").toboolean()).isTrue()
        assertThat(environment.getGlobal("stage").toint()).isZero()
        assertThat(environment.getGlobal("stageSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("stageAfter").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("stageReset").toboolean()).isTrue()
    }

    @Test
    fun `wheel additional spell data scrolls and timers`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            area = p:getWheelSpellAdditionalArea("Death Strike")
            target = p:getWheelSpellAdditionalTarget("Death Strike")
            duration = p:getWheelSpellAdditionalDuration("Death Strike")
            empty = p:getWheelSpellAdditionalArea("")
            scroll = p:wheelUnlockScroll("Scroll of Ascension")
            timer = p:avatarTimer()
            timerSet = p:avatarTimer(900)
            timerAfter = p:avatarTimer()
            think = p:onThinkWheelOfDestiny()
            thinkForced = p:onThinkWheelOfDestiny(true)
            reloaded = p:reloadData()
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("area").toboolean()).isTrue()
        assertThat(environment.getGlobal("target").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("duration").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("empty").toboolean()).isFalse()
        assertThat(environment.getGlobal("scroll").toboolean()).isTrue()
        assertThat(environment.getGlobal("timerSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("timerAfter").toint()).isEqualTo(900)
        assertThat(environment.getGlobal("think").toboolean()).isTrue()
        assertThat(fake.lastReloadForce).isTrue()
        assertThat(environment.getGlobal("reloaded").toboolean()).isTrue()
    }

    @Test
    fun `concoction food and cooldown resets forward their arguments`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            concoction = p:updateConcoction(100, 300)
            food = p:updateFood(200, 400)
            cooldowns = p:clearSpellCooldowns(true, false, 250)
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("concoction").toboolean()).isTrue()
        assertThat(fake.lastConcoction).isEqualTo(100 to 300)
        assertThat(fake.lastFood).isEqualTo(200 to 400L)
        assertThat(fake.lastCooldowns).isEqualTo(Triple(true, false, 250))
    }

    @Test
    fun `vip kv and store inbox follow the C fallbacks`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            vip = p:isVip()
            days = p:getVipDays()
            time = p:getVipTime()
            kv = p:kv()
            inbox = p:getStoreInbox()
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("vip").toboolean()).isTrue()
        assertThat(environment.getGlobal("days").toint()).isEqualTo(30)
        assertThat(environment.getGlobal("time").toint()).isEqualTo(12345)
        assertThat(environment.getGlobal("kv").isnil()).isFalse()
        assertThat(environment.getGlobal("inbox").toboolean()).isFalse()
    }

    @Test
    fun `achievements resolve by id or name`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            unlocked = p:hasAchievement(7)
            byName = p:hasAchievement("Pitfighter")
            added = p:addAchievement("Pitfighter")
            addedSilent = p:addAchievement(9, false)
            unlockedAfter = p:hasAchievement(9)
            removed = p:removeAchievement(9)
            points = p:getAchievementPoints()
            p:addAchievementPoints(3)
            pointsAdded = p:getAchievementPoints()
            p:removeAchievementPoints(2)
            pointsRemoved = p:getAchievementPoints()
            bad = p:addBadge(1)
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("unlocked").toboolean()).isFalse()
        assertThat(environment.getGlobal("byName").toboolean()).isFalse()
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("addedSilent").toboolean()).isTrue()
        assertThat(fake.lastAchievementSendMessage).isFalse()
        assertThat(environment.getGlobal("unlockedAfter").toboolean()).isTrue()
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("pointsAdded").toint()).isEqualTo(8)
        assertThat(environment.getGlobal("pointsRemoved").toint()).isEqualTo(6)
        assertThat(environment.getGlobal("bad").toboolean()).isTrue()
    }

    @Test
    fun `titles expose id name and description tables`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            titles = p:getTitles()
            firstId = titles[1].id
            firstName = titles[1].name
            firstDescription = titles[1].description
            count = #titles
            added = p:addTitle(2)
            current = p:setCurrentTitle(2)
            invalid = p:setCurrentTitle(0)
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("firstId").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("firstName").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("firstDescription").tojstring()).isEqualTo("A brave soul")
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("current").toboolean()).isTrue()
        assertThat(environment.getGlobal("invalid").toboolean()).isFalse()
    }

    @Test
    fun `store summary screenshots and creature icons`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            summary = p:createTransactionSummary(1, 2, "id-1")
            invalidSummary = p:createTransactionSummary(0, 2)
            screenshot = p:takeScreenshot(3)
            empty = p:takeScreenshot(3, 0, 0, "", 0, 0)
            icon = p:sendIconBakragore(1)
            removed = p:removeIconBakragore()
            overlay = p:setRaceIconOverlay(10, 2)
            cleared = p:clearRaceIconOverlays(2)
            refresh = p:refreshVisibleCreatureIcons()
            appear = p:sendCreatureAppear(true)
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("summary").toboolean()).isTrue()
        assertThat(fake.lastTransaction).isEqualTo(Triple(1, 2, "id-1"))
        assertThat(environment.getGlobal("invalidSummary").toboolean()).isFalse()
        assertThat(environment.getGlobal("screenshot").toboolean()).isTrue()
        assertThat(fake.lastScreenshot).isEqualTo(listOf(3, 0, 0, "", 0, 0))
        assertThat(environment.getGlobal("icon").toboolean()).isTrue()
        assertThat(fake.lastIcon).isEqualTo(2)
        assertThat(environment.getGlobal("overlay").toboolean()).isTrue()
        assertThat(fake.lastRaceOverlay).isEqualTo(Triple(10, 2, true))
        assertThat(environment.getGlobal("refresh").toboolean()).isTrue()
        assertThat(environment.getGlobal("appear").toboolean()).isTrue()
        assertThat(fake.lastCreatureAppearLogin).isTrue()
    }

    @Test
    fun `animus serenity virtue and harmony`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            hasBefore = p:hasAnimusMastery("Dragon")
            p:addAnimusMastery("Dragon")
            hasAfter = p:hasAnimusMastery("Dragon")
            p:removeAnimusMastery("Dragon")
            hasRemoved = p:hasAnimusMastery("Dragon")
            serene = p:setSerene(true, 500)
            virtueBefore = p:getVirtue()
            virtueSet = p:setVirtue(3)
            virtueAfter = p:getVirtue()
            p:fillHarmony()
            harmony = p:getHarmony()
            min, max = p:getHarmonyDamage(5, 10)
            flat = p:calculateFlatDamageHealing()
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("hasAfter").toboolean()).isTrue()
        assertThat(environment.getGlobal("hasRemoved").toboolean()).isFalse()
        assertThat(environment.getGlobal("serene").toboolean()).isTrue()
        assertThat(environment.getGlobal("virtueBefore").toint()).isZero()
        assertThat(environment.getGlobal("virtueAfter").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("harmony").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("min").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("max").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("flat").toint()).isEqualTo(55)
    }

    @Test
    fun `weapon experience and livestream payloads`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            weapon = p:addWeaponExperience(120, 3)
            viewers = p:getLivestreamViewersCount()
            state = p:getLivestreamViewers()
            description = state.description
            broadcast = state.broadcast
            names = state.names[1]
            p:setLivestreamViewers({ description = "d", broadcast = false, password = "p" })
            invalid = p:setLivestreamViewers({ description = 5 })
            isViewer = p:isLivestreamViewer()
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("weapon").toboolean()).isTrue()
        assertThat(fake.lastWeaponExperience).isEqualTo(120 to 3)
        assertThat(environment.getGlobal("viewers").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("description").tojstring()).isEqualTo("watch me")
        assertThat(environment.getGlobal("broadcast").toboolean()).isTrue()
        assertThat(environment.getGlobal("names").tojstring()).isEqualTo("viewer-1")
        assertThat(environment.getGlobal("invalid").toboolean()).isFalse()
        assertThat(fake.lastLivestreamOverrides?.description).isEqualTo("d")
        assertThat(fake.lastLivestreamOverrides?.broadcast).isFalse()
        assertThat(environment.getGlobal("isViewer").toboolean()).isFalse()
    }

    @Test
    fun `map shader and custom outfits pass numeric and string selectors`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            shader = p:getMapShader()
            set = p:setMapShader("winter")
            addId = p:addCustomOutfit("outfit", 5)
            addName = p:addCustomOutfit("outfit", "winter")
            removeId = p:removeCustomOutfit("outfit", 5)
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("shader").tojstring()).isEqualTo("default-shader")
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(fake.lastShader).isEqualTo("winter")
        assertThat(environment.getGlobal("addId").toboolean()).isTrue()
        assertThat(fake.lastCustomOutfit).isEqualTo(Triple("outfit", 5, false))
        assertThat(environment.getGlobal("addName").toboolean()).isTrue()
        assertThat(environment.getGlobal("removeId").toboolean()).isTrue()
    }

    @Test
    fun `store inbox returns the wrapped item when present`() {
        val fake = FakeExtraOps()
        val item = Item(id = 100, count = 1)
        fake.storeInbox = item
        val (environment, _) = installed(fake)
        environment.loadChunk("inbox = p:getStoreInbox()")
        assertThat(environment.getGlobal("inbox").isuserdata()).isTrue()
    }

    @Test
    fun `non-player userdata results bubble up as nil`() {
        val environment = env()
        PlayerFunctionsExtra.install(environment, StubPlayerExtraOperations())
        environment.globals.set("p", environment.userdata.wrap("Player", FakePlayer()))
        environment.loadChunk("dusts = p:getForgeDusts()")
        assertThat(environment.getGlobal("dusts").isnil()).isTrue()
    }
}
