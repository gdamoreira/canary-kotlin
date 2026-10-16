package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.containers.depot.DepotChest
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.bestiary.MonsterBestiaryInfo

/**
 * Transcript-style tests for the batch 3 Canary `Player` binding surface
 * (spells/runes, wheel of destiny, bestiary/charm/prey/task hunting,
 * health/combat identity, walk/position/map, training/depot/stash/containers and
 * blessings/achievements/vip/kv). Driven through real Lua chunks with the exact
 * method names from src/lua/functions/creatures/player/player_functions.cpp.
 */
class PlayerBatch3BindingsTest {

    private class FakePlayer

    private class FakeMonsterType

    private class FakeKv

    private class FakeBatch3Ops : PlayerBatch3Operations {
        val calls = linkedMapOf<String, List<Any?>>()

        var receiver: Any? = null

        fun player(): Any? = receiver

        fun positionArg(): Position = Position(100, 100, 7)

        private fun <T> rec(name: String, result: T, vararg args: Any?): T {
            calls[name] = args.toList()
            return result
        }

        val item = Item(2160, 1)
        val scroll = Item(36839, 1)
        val container: Container = DepotChest(2594)
        val monsterType = FakeMonsterType()
        val kv = FakeKv()

        override fun itemIdByName(name: String): Int = if (name == "magic sword") 2376 else 0

        override fun monsterRaceByName(name: String): Int = if (name == "rat") 21 else 0

        override fun achievementIdByName(name: String): Int = if (name == "First Steps") 1 else 0

        override fun spellExistsByName(name: String): Boolean = name == "executioners_throw"

        // --- spells / runes / wheel of destiny ---------------------------------

        override fun sendSpellCooldown(player: Any, spellId: Int, time: Int): Boolean? =
            rec("sendSpellCooldown", true, player, spellId, time)

        override fun sendSpellGroupCooldown(player: Any, groupId: Int, time: Int): Boolean? =
            rec("sendSpellGroupCooldown", true, player, groupId, time)

        override fun clearSpellCooldowns(player: Any, spenders: Boolean, builders: Boolean, ticks: Int): Boolean? =
            rec("clearSpellCooldowns", true, player, spenders, builders, ticks)

        override fun applyImbuementScroll(player: Any, item: Any, scrollItem: Any): Boolean? =
            rec("applyImbuementScroll", true, player, item, scrollItem)

        override fun openImbuementWindow(player: Any, action: Int, item: Any?): Boolean? =
            rec("openImbuementWindow", true, player, action, item)

        override fun closeImbuementWindow(player: Any): Boolean? =
            rec("closeImbuementWindow", true, player)

        override fun clearAllImbuements(player: Any, item: Any): Boolean? =
            rec("clearAllImbuements", true, player, item)

        override fun getWheelInstantSkill(player: Any, name: String): Boolean? =
            rec("getWheelInstantSkill", false, player, name)

        override fun setWheelInstantSkill(player: Any, name: String, value: Boolean): Boolean? =
            rec("setWheelInstantSkill", true, player, name, value)

        override fun resetWheelUpgradedSpells(player: Any): Boolean? =
            rec("resetWheelUpgradedSpells", true, player)

        override fun getWheelSpellUpgrade(player: Any, name: String): Int? =
            rec("getWheelSpellUpgrade", 2, player, name)

        override fun setWheelSpellUpgrade(player: Any, name: String, add: Boolean): Boolean? =
            rec("setWheelSpellUpgrade", true, player, name, add)

        override fun getWheelSpellStage(player: Any, name: String): Int? =
            rec("getWheelSpellStage", 1, player, name)

        override fun setWheelSpellStage(player: Any, name: String, value: Int): Boolean? =
            rec("setWheelSpellStage", true, player, name, value)

        override fun wheelUnlockScroll(player: Any, scrollName: String): Boolean? =
            rec("wheelUnlockScroll", true, player, scrollName)

        override fun onThinkWheelOfDestiny(player: Any, force: Boolean): Boolean? =
            rec("onThinkWheelOfDestiny", true, player, force)

        override fun getWheelAvatarTimer(player: Any): Long? =
            rec("getWheelAvatarTimer", 5000L, player)

        override fun setWheelAvatarTimer(player: Any, value: Long): Boolean? =
            rec("setWheelAvatarTimer", true, player, value)

        override fun getWheelSpellAdditionalArea(player: Any, spellName: String): Boolean? =
            rec("getWheelSpellAdditionalArea", true, player, spellName)

        override fun getWheelSpellAdditionalTarget(player: Any, spellName: String): Int? =
            rec("getWheelSpellAdditionalTarget", 3, player, spellName)

        override fun getWheelSpellAdditionalDuration(player: Any, spellName: String): Int? =
            rec("getWheelSpellAdditionalDuration", 4, player, spellName)

        // --- bestiary / charm / prey / task hunting ----------------------------

        override fun resetCharmsBestiary(player: Any): Boolean? =
            rec("resetCharmsBestiary", true, player)

        override fun unlockAllCharmRunes(player: Any): Boolean? =
            rec("unlockAllCharmRunes", true, player)

        override fun addCharmPoints(player: Any, points: Int): Boolean? =
            rec("addCharmPoints", true, player, points)

        override fun addMinorCharmEchoes(player: Any, points: Int): Boolean? =
            rec("addMinorCharmEchoes", true, player, points)

        override fun getCharmTier(player: Any, charmId: Int): Int? =
            rec("getCharmTier", 3, player, charmId)

        override fun getCharmChance(player: Any, charmId: Int): Double? =
            rec("getCharmChance", 25.5, player, charmId)

        override fun resetOldCharms(player: Any): Boolean? =
            rec("resetOldCharms", true, player)

        override fun isMonsterBestiaryUnlocked(player: Any, raceId: Int): Boolean? =
            rec("isMonsterBestiaryUnlocked", if (raceId == 21) true else null, player, raceId)

        override fun addBestiaryKill(player: Any, raceId: Int, amount: Int): Boolean? =
            rec("addBestiaryKill", true, player, raceId, amount)

        override fun charmExpansion(player: Any): Boolean? =
            rec("charmExpansion", true, player)

        override fun setCharmExpansion(player: Any, value: Boolean): Boolean? =
            rec("setCharmExpansion", true, player, value)

        override fun getCharmMonsterType(player: Any, charmId: Int): Any? =
            rec("getCharmMonsterType", monsterType, player, charmId)

        override fun isMonsterPrey(player: Any, raceId: Int): Boolean? =
            rec("isMonsterPrey", true, player, raceId)

        override fun getPreyCards(player: Any): Long? = rec("getPreyCards", 100L, player)

        override fun getPreyLootPercentage(player: Any, raceId: Int): Int? =
            rec("getPreyLootPercentage", 30, player, raceId)

        override fun getPreyExperiencePercentage(player: Any, raceId: Int): Int? =
            rec("getPreyExperiencePercentage", 150, player, raceId)

        override fun preyThirdSlot(player: Any): Boolean? = rec("preyThirdSlot", true, player)

        override fun setPreyThirdSlot(player: Any, value: Boolean): Boolean? =
            rec("setPreyThirdSlot", true, player, value)

        override fun taskHuntingThirdSlot(player: Any): Boolean? = rec("taskHuntingThirdSlot", false, player)

        override fun setTaskHuntingThirdSlot(player: Any, value: Boolean): Boolean? =
            rec("setTaskHuntingThirdSlot", true, player, value)

        override fun removePreyStamina(player: Any, amount: Int): Boolean? =
            rec("removePreyStamina", true, player, amount)

        override fun addPreyCards(player: Any, amount: Long): Boolean? =
            rec("addPreyCards", true, player, amount)

        override fun removeTaskHuntingPoints(player: Any, amount: Long): Boolean? =
            rec("removeTaskHuntingPoints", true, player, amount)

        override fun getTaskHuntingPoints(player: Any): Long? = rec("getTaskHuntingPoints", 777L, player)

        override fun addTaskHuntingPoints(player: Any, amount: Long): Long? =
            rec("addTaskHuntingPoints", amount, player, amount)

        // --- health / combat / identity -----------------------------------------

        override fun getMagicShieldCapacityFlat(player: Any, useCharges: Boolean): Int? =
            rec("getMagicShieldCapacityFlat", 120, player, useCharges)

        override fun getMagicShieldCapacityPercent(player: Any, useCharges: Boolean): Int? =
            rec("getMagicShieldCapacityPercent", 15, player, useCharges)

        override fun isPzLocked(player: Any): Boolean? = rec("isPzLocked", true, player)

        override fun getFaction(player: Any): Int? = rec("getFaction", 1, player)

        override fun setFaction(player: Any, factionId: Int): Boolean? =
            rec("setFaction", true, player, factionId)

        override fun getName(player: Any): String? = rec("getName", "Batch Hero", player)

        override fun changeName(player: Any, newName: String): Boolean? =
            rec("changeName", true, player, newName)

        override fun isPlayer(player: Any?): Boolean = rec("isPlayer", player is FakePlayer, player)

        override fun canReceiveLoot(player: Any): Boolean? = rec("canReceiveLoot", true, player)

        override fun isOffline(player: Any): Boolean? = rec("isOffline", false, player)

        override fun getIdleTime(player: Any): Long? = rec("getIdleTime", 3600L, player)

        // --- walk / position / map ----------------------------------------------

        override fun addMapMark(player: Any, position: Position, type: Int, description: String): Boolean? =
            rec("addMapMark", true, player, position, type, description)

        override fun setGhostMode(player: Any, enabled: Boolean): Boolean? =
            rec("setGhostMode", true, player, enabled)

        override fun getMapShader(player: Any): String? = rec("getMapShader", "shader", player)

        override fun setMapShader(player: Any, shaderName: String): Boolean? =
            rec("setMapShader", true, player, shaderName)

        // --- training / offline / depot / stash / containers ---------------------

        override fun isTraining(player: Any): Boolean? = rec("isTraining", false, player)

        override fun setTraining(player: Any, value: Boolean): Boolean? =
            rec("setTraining", true, player, value)

        override fun getDepotLocker(player: Any, depotId: Int): Any? =
            rec("getDepotLocker", item, player, depotId)

        override fun getDepotChest(player: Any, depotId: Int, autoCreate: Boolean): Any? =
            rec("getDepotChest", item, player, depotId, autoCreate)

        override fun getInbox(player: Any): Any? = rec("getInbox", item, player)

        override fun addOfflineTrainingTime(player: Any, time: Int): Boolean? =
            rec("addOfflineTrainingTime", true, player, time)

        override fun getOfflineTrainingTime(player: Any): Int? = rec("getOfflineTrainingTime", 7200000, player)

        override fun removeOfflineTrainingTime(player: Any, time: Int): Boolean? =
            rec("removeOfflineTrainingTime", true, player, time)

        override fun addOfflineTrainingTries(player: Any, skillType: Int, tries: Long): Boolean? =
            rec("addOfflineTrainingTries", true, player, skillType, tries)

        override fun getOfflineTrainingSkill(player: Any): Int? = rec("getOfflineTrainingSkill", 2, player)

        override fun setOfflineTrainingSkill(player: Any, skillId: Int): Boolean? =
            rec("setOfflineTrainingSkill", true, player, skillId)

        override fun getStashItemCount(player: Any, itemId: Int): Long? =
            rec("getStashItemCount", 500L, player, itemId)

        override fun setSpecialContainersAvailable(
            player: Any,
            stashMenu: Boolean,
            marketMenu: Boolean,
            depotSearchMenu: Boolean,
        ): Boolean? = rec("setSpecialContainersAvailable", true, player, stashMenu, marketMenu, depotSearchMenu)

        override fun getStashCount(player: Any): Int? = rec("getStashCount", 3, player)

        override fun openStash(player: Any, isNpc: Boolean): Boolean? = rec("openStash", true, player, isNpc)

        override fun addItemStash(player: Any, itemId: Int, count: Int): Boolean? =
            rec("addItemStash", true, player, itemId, count)

        override fun removeStashItem(player: Any, itemId: Int, count: Int): Boolean? =
            rec("removeStashItem", true, player, itemId, count)

        override fun addItemBatchToPaginedContainer(
            player: Any,
            container: Any,
            itemId: Int,
            count: Int,
            tier: Int,
            flags: Int,
        ): Int? = rec("addItemBatchToPaginedContainer", 5, player, container, itemId, count, tier, flags)

        override fun getStoreInbox(player: Any): Any? = rec("getStoreInbox", item, player)

        override fun sendContainer(player: Any, container: Any): Boolean? =
            rec("sendContainer", true, player, container)

        override fun sendUpdateContainer(player: Any, container: Any): Boolean? =
            rec("sendUpdateContainer", true, player, container)

        override fun getContainerId(player: Any, container: Any): Int? =
            rec("getContainerId", 3, player, container)

        override fun getContainerById(player: Any, id: Int): Any? =
            rec("getContainerById", container, player, id)

        override fun getContainerIndex(player: Any, id: Int): Int? =
            rec("getContainerIndex", 0, player, id)

        override fun getLootPouch(player: Any): Any? = rec("getLootPouch", item, player)

        override fun getFreeBackpackSlots(player: Any): Int? = rec("getFreeBackpackSlots", 17, player)

        // --- blessings / achievements / vip / kv --------------------------------

        override fun sendBlessStatus(player: Any): Boolean? = rec("sendBlessStatus", true, player)

        override fun hasBlessing(player: Any, blessing: Int): Boolean? =
            rec("hasBlessing", blessing == 1, player, blessing)

        override fun addBlessing(player: Any, blessing: Int, count: Int): Boolean? =
            rec("addBlessing", true, player, blessing, count)

        override fun removeBlessing(player: Any, blessing: Int, count: Int): Boolean? =
            rec("removeBlessing", blessing == 2, player, blessing, count)

        override fun getBlessingCount(player: Any, index: Int, storeCount: Boolean): Int? =
            rec("getBlessingCount", 4, player, index, storeCount)

        override fun isVip(player: Any): Boolean? = rec("isVip", true, player)

        override fun getVipDays(player: Any): Int? = rec("getVipDays", 30, player)

        override fun getVipTime(player: Any): Long? = rec("getVipTime", 123456789L, player)

        override fun getAchievementPoints(player: Any): Int? = rec("getAchievementPoints", 250, player)

        override fun addAchievementPoints(player: Any, amount: Int): Boolean? =
            rec("addAchievementPoints", true, player, amount)

        override fun removeAchievementPoints(player: Any, amount: Int): Boolean? =
            rec("removeAchievementPoints", true, player, amount)

        override fun hasAchievement(player: Any, achievementId: Int): Boolean? =
            rec("hasAchievement", achievementId == 1, player, achievementId)

        override fun addAchievement(player: Any, achievementId: Int, sendMessage: Boolean): Boolean? =
            rec("addAchievement", true, player, achievementId, sendMessage)

        override fun removeAchievement(player: Any, achievementId: Int): Boolean? =
            rec("removeAchievement", true, player, achievementId)

        override fun kv(player: Any): Any? = rec("kv", kv, player)

        override fun save(player: Any): Boolean? = rec("save", true, player)
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(
        fake: FakeBatch3Ops = FakeBatch3Ops(),
    ): Pair<LuaEnvironment, FakeBatch3Ops> {
        val environment = env()
        PlayerFunctions.install(environment, StubPlayerOperations(), fake)
        val player = FakePlayer()
        fake.receiver = player
        environment.globals.set("p", environment.userdata.wrap("Player", player))
        environment.globals.set("item", environment.userdata.wrap("Item", fake.item))
        environment.globals.set("scroll", environment.userdata.wrap("Item", fake.scroll))
        return environment to fake
    }

    @Test
    fun `batch 3 spell cooldown imbuement and wheel bindings use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            cooldown = p:sendSpellCooldown(7, 3000)
            groupCooldown = p:sendSpellGroupCooldown(2, 4000)
            cleared = p:clearSpellCooldowns(true, false, 900)
            applied = p:applyImbuementScroll(item, scroll)
            opened = p:openImbuementWindow(1, item)
            closed = p:closeImbuementWindow()
            clearedAll = p:clearAllImbuements(item)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("cooldown").toboolean()).isTrue()
        assertThat(environment.getGlobal("groupCooldown").toboolean()).isTrue()
        assertThat(environment.getGlobal("cleared").toboolean()).isTrue()
        assertThat(environment.getGlobal("applied").toboolean()).isTrue()
        assertThat(environment.getGlobal("opened").toboolean()).isTrue()
        assertThat(environment.getGlobal("closed").toboolean()).isTrue()
        assertThat(environment.getGlobal("clearedAll").toboolean()).isTrue()
        assertThat(fake.calls["sendSpellCooldown"]).isEqualTo(listOf(fake.player(), 7, 3000))
        assertThat(fake.calls["sendSpellGroupCooldown"]).isEqualTo(listOf(fake.player(), 2, 4000))
        assertThat(fake.calls["clearSpellCooldowns"]).isEqualTo(listOf(fake.player(), true, false, 900))
        assertThat(fake.calls["openImbuementWindow"]).isEqualTo(listOf(fake.player(), 1, fake.item))
        assertThat(fake.calls["applyImbuementScroll"]).isEqualTo(listOf(fake.player(), fake.item, fake.scroll))
    }

    @Test
    fun `batch 3 wheel of destiny bindings use the Canary names and overloads`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            instant = p:instantSkillWOD("executioners_throw")
            instantSet = p:instantSkillWOD("executioners_throw", true)
            upgradeGet = p:upgradeSpellsWOD("executioners_throw")
            upgradeSet = p:upgradeSpellsWOD("executioners_throw", true)
            stageGet = p:revelationStageWOD("executioners_throw")
            stageSet = p:revelationStageWOD("executioners_throw", 2)
            scroll = p:wheelUnlockScroll("scroll")
            think = p:onThinkWheelOfDestiny()
            timer = p:avatarTimer()
            timerSet = p:avatarTimer(3000)
            area = p:getWheelSpellAdditionalArea("executioners_throw")
            target = p:getWheelSpellAdditionalTarget("executioners_throw")
            duration = p:getWheelSpellAdditionalDuration("executioners_throw")
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("instant").toboolean()).isFalse()
        assertThat(environment.getGlobal("instantSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("upgradeGet").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("upgradeSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("stageGet").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("stageSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("scroll").toboolean()).isTrue()
        assertThat(environment.getGlobal("think").toboolean()).isTrue()
        assertThat(environment.getGlobal("timer").toint()).isEqualTo(5000)
        assertThat(environment.getGlobal("timerSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("area").toboolean()).isTrue()
        assertThat(environment.getGlobal("target").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("duration").toint()).isEqualTo(4)
        assertThat(fake.calls["setWheelInstantSkill"]).isEqualTo(listOf(player, "executioners_throw", true))
        assertThat(fake.calls["setWheelSpellUpgrade"]).isEqualTo(listOf(player, "executioners_throw", true))
        assertThat(fake.calls["setWheelSpellStage"]).isEqualTo(listOf(player, "executioners_throw", 2))
        assertThat(fake.calls["onThinkWheelOfDestiny"]).isEqualTo(listOf(player, false))
        assertThat(fake.calls["setWheelAvatarTimer"]).isEqualTo(listOf(player, 3000L))
    }

    @Test
    fun `batch 3 bestiary and charm bindings use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            reset = p:resetCharmsBestiary()
            unlocked = p:unlockAllCharmRunes()
            points = p:addCharmPoints(5)
            negativePoints = p:addCharmPoints(-5)
            echoes = p:addMinorCharmEchoes(2)
            tier = p:getCharmTier(1)
            chance = p:getCharmChance(1)
            old = p:resetOldCharms()
            finite = p:isMonsterBestiaryUnlocked(21)
            kill = p:addBestiaryKill("rat", 2)
            expansion = p:charmExpansion()
            expansionSet = p:charmExpansion(false)
            monsterType = p:getCharmMonsterType(1)
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("reset").toboolean()).isTrue()
        assertThat(environment.getGlobal("unlocked").toboolean()).isTrue()
        assertThat(environment.getGlobal("points").toboolean()).isTrue()
        assertThat(environment.getGlobal("negativePoints").toboolean()).isTrue()
        assertThat(environment.getGlobal("echoes").toboolean()).isTrue()
        assertThat(environment.getGlobal("tier").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("chance").todouble()).isEqualTo(25.5)
        assertThat(environment.getGlobal("old").toboolean()).isTrue()
        assertThat(environment.getGlobal("finite").toboolean()).isTrue()
        assertThat(environment.getGlobal("kill").toboolean()).isTrue()
        assertThat(environment.getGlobal("expansion").toboolean()).isTrue()
        assertThat(environment.getGlobal("expansionSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("monsterType").touserdata()).isSameAs(fake.monsterType)
        assertThat(fake.calls["addCharmPoints"]).isEqualTo(listOf(player, -5))
        assertThat(fake.calls["addBestiaryKill"]).isEqualTo(listOf(player, 21, 2))
        assertThat(fake.calls["setCharmExpansion"]).isEqualTo(listOf(player, false))
    }

    @Test
    fun `batch 3 prey and task hunting bindings use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            prey = p:isMonsterPrey(21)
            cards = p:getPreyCards()
            loot = p:getPreyLootPercentage(21)
            experience = p:getPreyExperiencePercentage(21)
            third = p:preyThirdSlot()
            thirdSet = p:preyThirdSlot(false)
            taskThird = p:taskHuntingThirdSlot()
            taskThirdSet = p:taskHuntingThirdSlot(true)
            stamina = p:removePreyStamina(15)
            cardsAdded = p:addPreyCards(10)
            pointsRemoved = p:removeTaskHuntingPoints(50)
            points = p:getTaskHuntingPoints()
            pointsAdded = p:addTaskHuntingPoints(20)
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("prey").toboolean()).isTrue()
        assertThat(environment.getGlobal("cards").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("loot").toint()).isEqualTo(30)
        assertThat(environment.getGlobal("experience").toint()).isEqualTo(150)
        assertThat(environment.getGlobal("third").toboolean()).isTrue()
        assertThat(environment.getGlobal("thirdSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("taskThird").toboolean()).isFalse()
        assertThat(environment.getGlobal("taskThirdSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("stamina").toboolean()).isTrue()
        assertThat(environment.getGlobal("cardsAdded").toboolean()).isTrue()
        assertThat(environment.getGlobal("pointsRemoved").toboolean()).isTrue()
        assertThat(environment.getGlobal("points").toint()).isEqualTo(777)
        assertThat(environment.getGlobal("pointsAdded").toint()).isEqualTo(20)
        assertThat(fake.calls["setPreyThirdSlot"]).isEqualTo(listOf(player, false))
        assertThat(fake.calls["setTaskHuntingThirdSlot"]).isEqualTo(listOf(player, true))
        assertThat(fake.calls["addPreyCards"]).isEqualTo(listOf(player, 10L))
        assertThat(fake.calls["removeTaskHuntingPoints"]).isEqualTo(listOf(player, 50L))
        assertThat(fake.calls["addTaskHuntingPoints"]).isEqualTo(listOf(player, 20L))
    }

    @Test
    fun `batch 3 health combat and identity bindings use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            flat = p:getMagicShieldCapacityFlat(true)
            percent = p:getMagicShieldCapacityPercent()
            locked = p:isPzLocked()
            faction = p:getFaction()
            factionSet = p:setFaction(2)
            name = p:getName()
            renamed = p:changeName("New Hero")
            player = p:isPlayer()
            loot = p:canReceiveLoot()
            offline = p:isOffline()
            idle = p:getIdleTime()
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("flat").toint()).isEqualTo(120)
        assertThat(environment.getGlobal("percent").toint()).isEqualTo(15)
        assertThat(environment.getGlobal("locked").toboolean()).isTrue()
        assertThat(environment.getGlobal("faction").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("factionSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Batch Hero")
        assertThat(environment.getGlobal("renamed").toboolean()).isTrue()
        assertThat(environment.getGlobal("player").toboolean()).isTrue()
        assertThat(environment.getGlobal("loot").toboolean()).isTrue()
        assertThat(environment.getGlobal("offline").toboolean()).isFalse()
        assertThat(environment.getGlobal("idle").toint()).isEqualTo(3600)
        assertThat(fake.calls["changeName"]).isEqualTo(listOf(player, "New Hero"))
        assertThat(fake.calls["getMagicShieldCapacityPercent"]).isEqualTo(listOf(player, false))
        assertThat(fake.calls["setFaction"]).isEqualTo(listOf(player, 2))
    }

    @Test
    fun `batch 3 walk position and map bindings use the Canary names`() {
        val (environment, fake) = installed()
        environment.globals.set("pos", pushPosition(environment, Position(100, 100, 7)))
        environment.loadChunk(
            """
            mark = p:addMapMark(pos, 5, "Hunt here")
            ghost = p:setGhostMode(true)
            shader = p:getMapShader()
            shaderSet = p:setMapShader("new_shader")
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("mark").toboolean()).isTrue()
        assertThat(environment.getGlobal("ghost").toboolean()).isTrue()
        assertThat(environment.getGlobal("shader").tojstring()).isEqualTo("shader")
        assertThat(environment.getGlobal("shaderSet").toboolean()).isTrue()
        assertThat(fake.calls["addMapMark"]).isEqualTo(listOf(player, fake.positionArg(), 5, "Hunt here"))
        assertThat(fake.calls["setGhostMode"]).isEqualTo(listOf(player, true))
        assertThat(fake.calls["setMapShader"]).isEqualTo(listOf(player, "new_shader"))
    }

    @Test
    fun `batch 3 training depot and offline training bindings use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            training = p:isTraining()
            trainingSet = p:setTraining(true)
            locker = p:getDepotLocker(2)
            chest = p:getDepotChest(2, true)
            inbox = p:getInbox()
            addedTime = p:addOfflineTrainingTime(60000)
            time = p:getOfflineTrainingTime()
            removedTime = p:removeOfflineTrainingTime(1000)
            tries = p:addOfflineTrainingTries(2, 500)
            skill = p:getOfflineTrainingSkill()
            skillSet = p:setOfflineTrainingSkill(2)
            slots = p:getFreeBackpackSlots()
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("training").toint()).isZero()
        assertThat(environment.getGlobal("trainingSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("locker").touserdata()).isSameAs(fake.item)
        assertThat(environment.getGlobal("chest").touserdata()).isSameAs(fake.item)
        assertThat(environment.getGlobal("inbox").touserdata()).isSameAs(fake.item)
        assertThat(environment.getGlobal("addedTime").toboolean()).isTrue()
        assertThat(environment.getGlobal("time").toint()).isEqualTo(7200000)
        assertThat(environment.getGlobal("removedTime").toboolean()).isTrue()
        assertThat(environment.getGlobal("tries").toboolean()).isTrue()
        assertThat(environment.getGlobal("skill").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("skillSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("slots").toint()).isEqualTo(17)
        assertThat(fake.calls["addOfflineTrainingTries"]).isEqualTo(listOf(player, 2, 500L))
        assertThat(fake.calls["setOfflineTrainingSkill"]).isEqualTo(listOf(player, 2))
    }

    @Test
    fun `batch 3 stash and container bindings use the Canary names`() {
        val (environment, fake) = installed()
        environment.globals.set("bag", environment.userdata.wrap("Container", fake.container))
        environment.globals.set("none", environment.userdata.wrap("Player", FakePlayer()))
        environment.loadChunk(
            """
            count = p:getStashItemCount(2160)
            countByName = p:getStashItemCount("magic sword")
            containers = p:setSpecialContainersAvailable(true, false, true)
            stashCount = p:getStashCount()
            opened = p:openStash(true)
            stashed = p:addItemStash(2160, 5)
            withdrawn = p:removeStashItem(2160, 2)
            batch = p:addItemBatchToPaginedContainer(bag, 2160, 5, 1, 0)
            store = p:getStoreInbox()
            sent = p:sendContainer(bag)
            updated = p:sendUpdateContainer(bag)
            containerId = p:getContainerId(bag)
            container = p:getContainerById(3)
            containerIndex = p:getContainerIndex(3)
            pouch = p:getLootPouch()
            missingContainer = none:sendContainer(nil)
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("count").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("countByName").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("containers").toboolean()).isTrue()
        assertThat(environment.getGlobal("stashCount").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("opened").toboolean()).isTrue()
        assertThat(environment.getGlobal("stashed").toboolean()).isTrue()
        assertThat(environment.getGlobal("withdrawn").toboolean()).isTrue()
        assertThat(environment.getGlobal("batch").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("store").touserdata()).isSameAs(fake.item)
        assertThat(environment.getGlobal("sent").toboolean()).isTrue()
        assertThat(environment.getGlobal("updated").toboolean()).isTrue()
        assertThat(environment.getGlobal("containerId").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("container").touserdata()).isSameAs(fake.container)
        assertThat(environment.getGlobal("containerIndex").toint()).isZero()
        assertThat(environment.getGlobal("pouch").touserdata()).isSameAs(fake.item)
        assertThat(environment.getGlobal("missingContainer").isnil()).isTrue()
        assertThat(fake.calls["getStashItemCount"]).isEqualTo(listOf(player, 2376))
        assertThat(fake.calls["addItemBatchToPaginedContainer"]).isEqualTo(
            listOf(player, fake.container, 2160, 5, 1, 0),
        )
        assertThat(fake.calls["setSpecialContainersAvailable"]).isEqualTo(listOf(player, true, false, true))
        assertThat(fake.calls["addItemStash"]).isEqualTo(listOf(player, 2160, 5))
    }

    @Test
    fun `batch 3 blessing achievement vip kv bindings use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            status = p:sendBlessStatus()
            has = p:hasBlessing(1)
            missing = p:hasBlessing(9)
            added = p:addBlessing(2, 3)
            removed = p:removeBlessing(2, 1)
            notRemoved = p:removeBlessing(9, 1)
            zeroIndex = p:getBlessingCount(0)
            stored = p:getBlessingCount(1, true)
            vip = p:isVip()
            days = p:getVipDays()
            time = p:getVipTime()
            points = p:getAchievementPoints()
            pointsAdded = p:addAchievementPoints(10)
            pointsRemoved = p:removeAchievementPoints(5)
            hasAchievement = p:hasAchievement(1)
            hasByName = p:hasAchievement("First Steps")
            achievementAdded = p:addAchievement(1, false)
            achievementRemoved = p:removeAchievement(1)
            kv = p:kv()
            saved = p:save()
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("status").toboolean()).isTrue()
        assertThat(environment.getGlobal("has").toboolean()).isTrue()
        assertThat(environment.getGlobal("missing").toboolean()).isFalse()
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("notRemoved").toboolean()).isFalse()
        assertThat(environment.getGlobal("zeroIndex").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("stored").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("vip").toboolean()).isTrue()
        assertThat(environment.getGlobal("days").toint()).isEqualTo(30)
        assertThat(environment.getGlobal("time").toint()).isEqualTo(123456789)
        assertThat(environment.getGlobal("points").toint()).isEqualTo(250)
        assertThat(environment.getGlobal("pointsAdded").toboolean()).isTrue()
        assertThat(environment.getGlobal("pointsRemoved").toboolean()).isTrue()
        assertThat(environment.getGlobal("hasAchievement").toboolean()).isTrue()
        assertThat(environment.getGlobal("hasByName").toboolean()).isTrue()
        assertThat(environment.getGlobal("achievementAdded").toboolean()).isTrue()
        assertThat(environment.getGlobal("achievementRemoved").toboolean()).isTrue()
        assertThat(environment.getGlobal("kv").touserdata()).isSameAs(fake.kv)
        assertThat(environment.getGlobal("saved").toboolean()).isTrue()
        assertThat(fake.calls["getBlessingCount"]).isEqualTo(listOf(player, 1, true))
        assertThat(fake.calls["addBlessing"]).isEqualTo(listOf(player, 2, 3))
        assertThat(fake.calls["addAchievement"]).isEqualTo(listOf(player, 1, false))
        assertThat(fake.calls["hasAchievement"]).isEqualTo(listOf(player, 1))
        assertThat(fake.calls["addAchievementPoints"]).isEqualTo(listOf(player, 10))
    }

    @Test
    fun `batch 3 optional arguments default like Canary`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            cooldown = p:sendSpellCooldown()
            groupCooldown = p:sendSpellGroupCooldown()
            cleared = p:clearSpellCooldowns()
            opened = p:openImbuementWindow()
            kill = p:addBestiaryKill("rat")
            blessing = p:addBlessing(2)
            achievement = p:addAchievement(1)
            counter = p:getBlessingCount(2)
            count = p:addItemStash(2160)
            mark = p:addMapMark({x = 1, y = 2, z = 3}, 4, "label")
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("cooldown").toboolean()).isTrue()
        assertThat(environment.getGlobal("groupCooldown").toboolean()).isTrue()
        assertThat(fake.calls["sendSpellCooldown"]).isEqualTo(listOf(player, 1, 0))
        assertThat(fake.calls["sendSpellGroupCooldown"]).isEqualTo(listOf(player, 1, 0))
        assertThat(fake.calls["clearSpellCooldowns"]).isEqualTo(listOf(player, false, false, 0))
        assertThat(fake.calls["openImbuementWindow"]).isEqualTo(listOf(player, 0, null))
        assertThat(fake.calls["addBestiaryKill"]).isEqualTo(listOf(player, 21, 1))
        assertThat(fake.calls["addBlessing"]).isEqualTo(listOf(player, 2, 0))
        assertThat(fake.calls["addAchievement"]).isEqualTo(listOf(player, 1, true))
        assertThat(fake.calls["getBlessingCount"]).isEqualTo(listOf(player, 2, false))
        assertThat(fake.calls["addItemStash"]).isEqualTo(listOf(player, 2160, 1))
        assertThat(fake.calls["addMapMark"]).isEqualTo(listOf(player, Position(1, 2, 3), 4, "label"))
    }

    @Test
    fun `batch 3 bindings return nil or false for a non-player receiver`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            local m = rawgetmetatable('Player').__metatable
            nilChecks =
                (m.sendSpellCooldown(nil, 7, 3000) == nil) and
                (m.avatarTimer(nil) == nil) and
                (m.getCharmTier(nil, 1) == nil) and
                (m.getPreyCards(nil) == nil) and
                (m.getMagicShieldCapacityFlat(nil) == nil) and
                (m.getFaction(nil) == nil) and
                (m.getName(nil) == nil) and
                (m.getIdleTime(nil) == nil) and
                (m.setGhostMode(nil, true) == nil) and
                (m.getMapShader(nil) == nil) and
                (m.getDepotLocker(nil, 2) == nil) and
                (m.getStashCount(nil) == nil) and
                (m.sendContainer(nil, nil) == nil) and
                (m.hasBlessing(nil, 1) == nil) and
                (m.getAchievementPoints(nil) == nil) and
                (m.kv(nil) == false) and
                (m.save(nil) == nil) and
                (m.isVip(nil) == false) and
                (m.isTraining(nil) == false) and
                (m.isOffline(nil) == true) and
                (m.getTaskHuntingPoints(nil) == false) and
                (m.getVipDays(nil) == false) and
                (m.isPlayer(nil) == false)
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("nilChecks").toboolean()).isTrue()
    }

    @Test
    fun `batch 3 bindings guard unknown names spells and scrolls like Canary`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            unknownStash = p:getStashItemCount("no such item")
            unknownKill = p:addBestiaryKill("kobold")
            unknownSpell = p:getWheelSpellAdditionalArea("no such spell")
            emptyScroll = p:wheelUnlockScroll("")
            unknownAchievement = p:hasAchievement("no such achievement")
            unknownUnlock = p:isMonsterBestiaryUnlocked(99)
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("unknownStash").isnil()).isTrue()
        assertThat(environment.getGlobal("unknownKill").isnil()).isTrue()
        assertThat(environment.getGlobal("unknownSpell").isnil()).isTrue()
        assertThat(environment.getGlobal("emptyScroll").isnil()).isTrue()
        assertThat(environment.getGlobal("unknownAchievement").toboolean()).isFalse()
        assertThat(environment.getGlobal("unknownUnlock").isnil()).isTrue()
    }

    @Test
    fun `batch 3 Player methods register on the Player class table`() {
        val (environment, _) = installed()
        val names = listOf(
            "sendSpellCooldown", "sendSpellGroupCooldown", "clearSpellCooldowns", "applyImbuementScroll",
            "openImbuementWindow", "closeImbuementWindow", "clearAllImbuements", "instantSkillWOD",
            "upgradeSpellsWOD", "revelationStageWOD", "wheelUnlockScroll", "onThinkWheelOfDestiny",
            "avatarTimer", "getWheelSpellAdditionalArea", "getWheelSpellAdditionalTarget",
            "getWheelSpellAdditionalDuration", "resetCharmsBestiary", "unlockAllCharmRunes", "addCharmPoints",
            "addMinorCharmEchoes", "getCharmTier", "getCharmChance", "resetOldCharms",
            "isMonsterBestiaryUnlocked", "addBestiaryKill", "charmExpansion", "getCharmMonsterType",
            "isMonsterPrey", "getPreyCards", "getPreyLootPercentage", "getPreyExperiencePercentage",
            "preyThirdSlot", "taskHuntingThirdSlot", "removePreyStamina", "addPreyCards",
            "removeTaskHuntingPoints", "getTaskHuntingPoints", "addTaskHuntingPoints",
            "getMagicShieldCapacityFlat", "getMagicShieldCapacityPercent", "isPzLocked", "getFaction",
            "setFaction", "getName", "changeName", "isPlayer", "canReceiveLoot", "isOffline", "getIdleTime",
            "addMapMark", "setGhostMode", "getMapShader", "setMapShader", "isTraining", "setTraining",
            "getDepotLocker", "getDepotChest", "getInbox", "addOfflineTrainingTime", "getOfflineTrainingTime",
            "removeOfflineTrainingTime", "addOfflineTrainingTries", "getOfflineTrainingSkill",
            "setOfflineTrainingSkill", "getStashItemCount", "setSpecialContainersAvailable", "getStashCount",
            "openStash", "addItemStash", "removeStashItem", "addItemBatchToPaginedContainer", "getStoreInbox",
            "sendContainer", "sendUpdateContainer", "getContainerId", "getContainerById", "getContainerIndex",
            "getLootPouch", "getFreeBackpackSlots", "sendBlessStatus", "hasBlessing", "addBlessing",
            "removeBlessing", "getBlessingCount", "isVip", "getVipDays", "getVipTime", "getAchievementPoints",
            "addAchievementPoints", "removeAchievementPoints", "hasAchievement", "addAchievement",
            "removeAchievement", "kv", "save",
        )
        val checks = names.joinToString(" and ") { name ->
            "(rawgetmetatable('Player').__metatable.$name ~= nil)"
        }
        environment.loadChunk("checks = $checks")
        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }

    @Test
    fun `real Player domain answers the batch 3 surface through WorldPlayerBatch3Operations`() {
        val environment = env()
        val bestiaryInfos = listOf(
            MonsterBestiaryInfo(raceid = 21, name = "Rat", bestiaryToUnlock = 1),
        )
        PlayerFunctions.install(
            environment,
            StubPlayerOperations(),
            WorldPlayerBatch3Operations(
                itemIdsByName = mapOf("magic sword" to 2376),
                monsterBestiaryInfos = bestiaryInfos,
            ),
        )
        val player = Player(
            id = 42L,
            name = "Batch Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 90,
            mana = 90,
            capacity = 500.0,
            position = Position(10, 10, 7),
            level = 1,
        )
        val backpack = Container(1988, 20)
        backpack.addItem(Item(2160, 1))
        backpack.addItem(Item(2376, 1))
        player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)
        player.addContainer(3, backpack)
        environment.globals.set("p", environment.userdata.wrap("Player", player))

        environment.loadChunk(
            """
            name = p:getName()
            player = p:isPlayer()
            locked = p:isPzLocked()
            faction = p:getFaction()
            shader = p:getMapShader()
            addedBlessing = p:addBlessing(1, 2)
            count = p:getBlessingCount(1)
            expansionSet = p:charmExpansion(true)
            expansion = p:charmExpansion()
            kill = p:addBestiaryKill("rat", 2)
            unlocked = p:isMonsterBestiaryUnlocked(21)
            tier = p:getCharmTier(1)
            cards = p:getPreyCards()
            cardsAdded = p:addPreyCards(10)
            cardsAfter = p:getPreyCards()
            taskPointsAdded = p:addTaskHuntingPoints(20)
            taskPoints = p:getTaskHuntingPoints()
            stashAdded = p:addItemStash(2160, 5)
            stashCount = p:getStashItemCount(2160)
            stashWithdraw = p:removeStashItem(2160, 2)
            stashAfter = p:getStashItemCount(2160)
            totalStash = p:getStashCount()
            container = p:getContainerById(3)
            containerId = p:getContainerId(container)
            pendingTime = p:getOfflineTrainingTime()
            timeAdded = p:addOfflineTrainingTime(60000)
            timeAfter = p:getOfflineTrainingTime()
            slots = p:getFreeBackpackSlots()
            loot = p:canReceiveLoot()
            offline = p:isOffline()
            saved = p:save()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Batch Hero")
        assertThat(environment.getGlobal("player").toboolean()).isTrue()
        assertThat(environment.getGlobal("locked").toboolean()).isFalse()
        assertThat(environment.getGlobal("faction").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("shader").tojstring()).isEmpty()
        assertThat(environment.getGlobal("addedBlessing").toboolean()).isTrue()
        assertThat(environment.getGlobal("count").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("expansionSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("expansion").toboolean()).isTrue()
        assertThat(environment.getGlobal("kill").toboolean()).isTrue()
        assertThat(environment.getGlobal("unlocked").toboolean()).isTrue()
        assertThat(environment.getGlobal("tier").toint()).isZero()
        assertThat(environment.getGlobal("cards").toint()).isZero()
        assertThat(environment.getGlobal("cardsAdded").toboolean()).isTrue()
        assertThat(environment.getGlobal("cardsAfter").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("taskPointsAdded").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("taskPoints").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("stashAdded").toboolean()).isTrue()
        assertThat(environment.getGlobal("stashCount").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("stashWithdraw").toboolean()).isTrue()
        assertThat(environment.getGlobal("stashAfter").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("totalStash").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("container").touserdata()).isSameAs(backpack)
        assertThat(environment.getGlobal("containerId").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("pendingTime").toint()).isZero()
        assertThat(environment.getGlobal("timeAdded").toboolean()).isTrue()
        assertThat(environment.getGlobal("timeAfter").toint()).isEqualTo(60000)
        assertThat(environment.getGlobal("slots").toint()).isEqualTo(18)
        assertThat(environment.getGlobal("loot").toboolean()).isTrue()
        assertThat(environment.getGlobal("offline").toboolean()).isFalse()
        assertThat(environment.getGlobal("saved").toboolean()).isTrue()
    }
}
