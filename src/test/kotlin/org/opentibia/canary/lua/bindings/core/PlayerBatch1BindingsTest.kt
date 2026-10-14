package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldPlayerOperations
import org.opentibia.canary.map.Position

/**
 * Transcript-style tests for the batch 1 Canary `Player` binding surface
 * (vitals/attributes, skills/experience, inventory/equipment, money/bank,
 * conditions, stamina/soul), driven through real Lua chunks with the exact Canary
 * method names from src/lua/functions/creatures/player/player_functions.cpp and
 * src/lua/functions/creatures/creature_functions.cpp.
 */
class PlayerBatch1BindingsTest {

    private class FakePlayer

    private class FakeCondition

    private data class RemoveConditionCall(
        val conditionType: Int,
        val conditionId: Int,
        val subId: Int,
        val force: Boolean,
    )

    private class FakePlayerOps : StubPlayerOperations() {
        var baseMagicLevel = 7
        var manaSpent = 1234L
        var lastManaSpentAdded: Long? = null
        var baseMaxHealth = 150
        var baseMaxMana = 90
        var lastRemoveExperience: Pair<Long, Boolean>? = null
        var lastSetLevel: Int? = null
        var lastSetMagicLevel: Pair<Int, Long>? = null
        var effectiveSkillLevel = 33
        var skillPercent = 42.5
        var lastSetSkillLevel: Triple<Int, Int, Long>? = null
        var itemById: Any = Item(2160, 1)
        val itemNames = mapOf("magic sword" to 2376)
        var lastGetItemById: Triple<Int, Boolean, Int>? = null
        var addItemExResult = 0
        var lastAddItemExItem: Any? = null
        var lastAddItemEx: Triple<Boolean, Int, Int>? = null
        var slotItem: Any = Item(2376, 1)
        var lastSlotItemSlot: Int? = null
        var backpack: Any = Item(1988, 1)
        var money = 5000L
        var lastAddMoney: Pair<Long, Int>? = null
        var lastRemoveMoney: Triple<Long, Int, Boolean>? = null
        var bankBalance = 9000L
        var lastSetBankBalance: Long? = null
        var condition: Any = FakeCondition()
        var lastGetCondition: Triple<Int, Int, Int>? = null
        var lastAddedCondition: Any? = null
        var lastRemoveCondition: RemoveConditionCall? = null
        var lastHasCondition: Pair<Int, Int>? = null
        var stamina = 1500
        var soul = 55
        var maxSoul = 100
        var lastSetStamina: Int? = null
        var lastAddSoul: Int? = null

        override fun itemIdByName(name: String): Int = itemNames[name] ?: 0

        override fun getBaseMagicLevel(player: Any): Int? = baseMagicLevel

        override fun getManaSpent(player: Any): Long? = manaSpent

        override fun addManaSpent(player: Any, amount: Long): Boolean? {
            lastManaSpentAdded = amount
            return true
        }

        override fun getBaseMaxHealth(player: Any): Int? = baseMaxHealth

        override fun getBaseMaxMana(player: Any): Int? = baseMaxMana

        override fun removeExperience(player: Any, experience: Long, sendText: Boolean): Boolean? {
            lastRemoveExperience = experience to sendText
            return true
        }

        override fun setLevel(player: Any, level: Int): Boolean? {
            lastSetLevel = level
            return true
        }

        override fun setMagicLevel(player: Any, level: Int, manaSpent: Long): Boolean? {
            lastSetMagicLevel = level to manaSpent
            return true
        }

        override fun getEffectiveSkillLevel(player: Any, skillType: Int): Int? =
            if (skillType in 0..12) effectiveSkillLevel else null

        override fun getSkillPercent(player: Any, skillType: Int): Double? =
            if (skillType in 0..12) skillPercent else null

        override fun setSkillLevel(player: Any, skillType: Int, level: Int, tries: Long): Boolean? {
            lastSetSkillLevel = Triple(skillType, level, tries)
            return true
        }

        override fun getItemById(player: Any, itemId: Int, deepSearch: Boolean, subType: Int): Any? {
            lastGetItemById = Triple(itemId, deepSearch, subType)
            return itemById
        }

        override fun addItemEx(
            player: Any,
            item: Any,
            canDropOnMap: Boolean,
            indexOrSlot: Int,
            flags: Int,
        ): Int? {
            lastAddItemExItem = item
            lastAddItemEx = Triple(canDropOnMap, indexOrSlot, flags)
            return addItemExResult
        }

        override fun getSlotItem(player: Any, slot: Int): Any? {
            lastSlotItemSlot = slot
            return slotItem
        }

        override fun getBackpack(player: Any): Any = backpack

        override fun getMoney(player: Any): Long? = money

        override fun addMoney(player: Any, money: Long, flags: Int): LuaMoneyResult? {
            lastAddMoney = money to flags
            return LuaMoneyResult(success = true, addedMoney = money, returnValue = 0)
        }

        override fun removeMoney(player: Any, money: Long, flags: Int, useBank: Boolean): Boolean? {
            lastRemoveMoney = Triple(money, flags, useBank)
            return true
        }

        override fun getBankBalance(player: Any): Long? = bankBalance

        override fun setBankBalance(player: Any, bankBalance: Long): Boolean? {
            lastSetBankBalance = bankBalance
            return true
        }

        override fun getCondition(player: Any, conditionType: Int, conditionId: Int, subId: Int): Any? {
            lastGetCondition = Triple(conditionType, conditionId, subId)
            return condition
        }

        override fun addCondition(player: Any, condition: Any): Boolean? {
            lastAddedCondition = condition
            return true
        }

        override fun removeCondition(
            player: Any,
            conditionType: Int,
            conditionId: Int,
            subId: Int,
            force: Boolean,
        ): Boolean? {
            lastRemoveCondition = RemoveConditionCall(conditionType, conditionId, subId, force)
            return true
        }

        override fun hasCondition(player: Any, conditionType: Int, subId: Int): Boolean? {
            lastHasCondition = conditionType to subId
            return true
        }

        override fun getStamina(player: Any): Int? = stamina

        override fun setStamina(player: Any, stamina: Int): Boolean? {
            lastSetStamina = stamina
            return true
        }

        override fun getSoul(player: Any): Int? = soul

        override fun addSoul(player: Any, soulChange: Int): Boolean? {
            lastAddSoul = soulChange
            return true
        }

        override fun getMaxSoul(player: Any): Int? = maxSoul
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(fake: FakePlayerOps = FakePlayerOps()): Pair<LuaEnvironment, FakePlayerOps> {
        val environment = env()
        PlayerFunctions.install(environment, fake)
        environment.globals.set("p", environment.userdata.wrap("Player", FakePlayer()))
        return environment to fake
    }

    @Test
    fun `Player vitals accessors use the Canary names`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            baseMagic = p:getBaseMagicLevel()
            spent = p:getManaSpent()
            baseHp = p:getBaseMaxHealth()
            baseMp = p:getBaseMaxMana()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("baseMagic").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("spent").toint()).isEqualTo(1234)
        assertThat(environment.getGlobal("baseHp").toint()).isEqualTo(150)
        assertThat(environment.getGlobal("baseMp").toint()).isEqualTo(90)
    }

    @Test
    fun `Player vitals mutators use the Canary names and optional defaults`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            added = p:addManaSpent(250)
            level = p:setLevel(8)
            magic = p:setMagicLevel(12)
            lost = p:removeExperience(100)
            lostText = p:removeExperience(50, true)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("level").toboolean()).isTrue()
        assertThat(environment.getGlobal("magic").toboolean()).isTrue()
        assertThat(environment.getGlobal("lost").toboolean()).isTrue()
        assertThat(environment.getGlobal("lostText").toboolean()).isTrue()
        assertThat(fake.lastManaSpentAdded).isEqualTo(250L)
        assertThat(fake.lastSetLevel).isEqualTo(8)
        assertThat(fake.lastSetMagicLevel).isEqualTo(12 to 0L)
        assertThat(fake.lastRemoveExperience).isEqualTo(50L to true)
    }

    @Test
    fun `Player skill accessors use the Canary names and skill guards`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            effective = p:getEffectiveSkillLevel(2)
            percent = p:getSkillPercent(2)
            badEffective = p:getEffectiveSkillLevel(99)
            badPercent = p:getSkillPercent(-1)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("effective").toint()).isEqualTo(33)
        assertThat(environment.getGlobal("percent").todouble()).isEqualTo(42.5)
        assertThat(environment.getGlobal("badEffective").isnil()).isTrue()
        assertThat(environment.getGlobal("badPercent").isnil()).isTrue()
    }

    @Test
    fun `Player setSkillLevel forwards level and tries with the Canary default`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            plain = p:setSkillLevel(2, 55)
            withTries = p:setSkillLevel(2, 60, 900)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("plain").toboolean()).isTrue()
        assertThat(environment.getGlobal("withTries").toboolean()).isTrue()
        assertThat(fake.lastSetSkillLevel).isEqualTo(Triple(2, 60, 900L))
    }

    @Test
    fun `Player getItemById accepts ids names and the deepSearch subType defaults`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            item = p:getItemById(2160)
            byName = p:getItemById("magic sword", true, 3)
            unknown = p:getItemById("no such item")
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("item").touserdata()).isSameAs(fake.itemById)
        assertThat(environment.getGlobal("byName").touserdata()).isSameAs(fake.itemById)
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
        assertThat(fake.lastGetItemById).isEqualTo(Triple(2376, true, 3))
    }

    @Test
    fun `Player addItemEx forwards item canDropOnMap index and flags`() {
        val (environment, fake) = installed()
        val item = Item(2160, 1)
        environment.globals.set("item", environment.userdata.wrap("Item", item))
        environment.loadChunk(
            """
            plain = p:addItemEx(item)
            onMap = p:addItemEx(item, true, 3)
            indexed = p:addItemEx(item, false, 5, 8)
            missing = p:addItemEx(nil)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("plain").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("onMap").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("indexed").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("missing").toboolean()).isFalse()
        assertThat(fake.lastAddItemExItem).isSameAs(item)
        assertThat(fake.lastAddItemEx).isEqualTo(Triple(false, 5, 8))
    }

    @Test
    fun `Player getSlotItem and getBackpack use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            slot = p:getSlotItem(5)
            bag = p:getBackpack()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("slot").touserdata()).isSameAs(fake.slotItem)
        assertThat(environment.getGlobal("bag").touserdata()).isSameAs(fake.backpack)
        assertThat(fake.lastSlotItemSlot).isEqualTo(5)
    }

    @Test
    fun `Player money accessors use the Canary triple and optional defaults`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            current = p:getMoney()
            ok, added, rv = p:addMoney(500)
            removed = p:removeMoney(300)
            removedNoBank = p:removeMoney(300, 4, false)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("current").toint()).isEqualTo(5000)
        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(environment.getGlobal("added").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("rv").toint()).isZero()
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("removedNoBank").toboolean()).isTrue()
        assertThat(fake.lastAddMoney).isEqualTo(500L to 0)
        assertThat(fake.lastRemoveMoney).isEqualTo(Triple(300L, 4, false))
    }

    @Test
    fun `Player bank accessors use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            balance = p:getBankBalance()
            saved = p:setBankBalance(12345)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("balance").toint()).isEqualTo(9000)
        assertThat(environment.getGlobal("saved").toboolean()).isTrue()
        assertThat(fake.lastSetBankBalance).isEqualTo(12345L)
    }

    @Test
    fun `Player condition methods use the Canary names and optional defaults`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            condition = p:getCondition(5)
            explicit = p:getCondition(5, 1, 2)
            added = p:addCondition(condition)
            has = p:hasCondition(5)
            hasSub = p:hasCondition(5, 9)
            removed = p:removeCondition(5)
            forced = p:removeCondition(5, 1, 2, true)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("condition").touserdata()).isSameAs(fake.condition)
        assertThat(environment.getGlobal("explicit").touserdata()).isSameAs(fake.condition)
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("has").toboolean()).isTrue()
        assertThat(environment.getGlobal("hasSub").toboolean()).isTrue()
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("forced").toboolean()).isTrue()
        assertThat(fake.lastAddedCondition).isSameAs(fake.condition)
        assertThat(fake.lastGetCondition).isEqualTo(Triple(5, 1, 2))
        assertThat(fake.lastHasCondition).isEqualTo(5 to 9)
        assertThat(fake.lastRemoveCondition).isEqualTo(RemoveConditionCall(5, 1, 2, true))
    }

    @Test
    fun `Player stamina and soul accessors use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            stamina = p:getStamina()
            soul = p:getSoul()
            maxSoul = p:getMaxSoul()
            staminaSet = p:setStamina(1600)
            soulAdded = p:addSoul(10)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("stamina").toint()).isEqualTo(1500)
        assertThat(environment.getGlobal("soul").toint()).isEqualTo(55)
        assertThat(environment.getGlobal("maxSoul").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("staminaSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("soulAdded").toboolean()).isTrue()
        assertThat(fake.lastSetStamina).isEqualTo(1600)
        assertThat(fake.lastAddSoul).isEqualTo(10)
    }

    @Test
    fun `batch 1 Player getters return nil for a non-player receiver`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            local m = rawgetmetatable('Player').__metatable
            nilChecks =
                (m.getBaseMagicLevel(nil) == nil) and
                (m.getManaSpent(nil) == nil) and
                (m.getBaseMaxHealth(nil) == nil) and
                (m.getBaseMaxMana(nil) == nil) and
                (m.getMoney(nil) == nil) and
                (m.getBankBalance(nil) == nil) and
                (m.getStamina(nil) == nil) and
                (m.getSoul(nil) == nil) and
                (m.getMaxSoul(nil) == nil) and
                (m.getSlotItem(nil, 5) == nil) and
                (m.getBackpack(nil) == nil) and
                (m.getItemById(nil, 2160) == nil) and
                (m.getCondition(nil, 5) == nil)
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("nilChecks").toboolean()).isTrue()
    }

    @Test
    fun `batch 1 Player methods register on the Player class table`() {
        val (environment, _) = installed()
        val names = listOf(
            "getBaseMagicLevel", "getManaSpent", "addManaSpent", "getBaseMaxHealth", "getBaseMaxMana",
            "removeExperience", "setLevel", "setMagicLevel", "getEffectiveSkillLevel", "getSkillPercent",
            "setSkillLevel", "getItemById", "addItemEx", "getSlotItem", "getBackpack", "getMoney",
            "addMoney", "removeMoney", "getBankBalance", "setBankBalance", "getCondition", "addCondition",
            "removeCondition", "hasCondition", "getStamina", "setStamina", "getSoul", "addSoul", "getMaxSoul",
        )
        val checks = names.joinToString(" and ") { name ->
            "(rawgetmetatable('Player').__metatable.$name ~= nil)"
        }
        environment.loadChunk("checks = $checks")
        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }

    @Test
    fun `real Player domain answers the batch 1 surface through WorldPlayerOperations`() {
        val environment = env()
        PlayerFunctions.install(environment, WorldPlayerOperations())
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
        val sword = Item(2376, 1)
        backpack.addItem(sword)
        player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)
        val condition = Condition.createCondition(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_INFIGHT,
            ticks = -1,
            now = { 0L },
        )
        requireNotNull(condition)
        val fresh = Item(2160, 1)
        environment.globals.set("p", environment.userdata.wrap("Player", player))
        environment.globals.set("cond", environment.userdata.wrap("Condition", condition))
        environment.globals.set("fresh", environment.userdata.wrap("Item", fresh))

        environment.loadChunk(
            """
            baseMagic = p:getBaseMagicLevel()
            spent = p:getManaSpent()
            addSpent = p:addManaSpent(100)
            spentAfter = p:getManaSpent()
            baseHp = p:getBaseMaxHealth()
            baseMp = p:getBaseMaxMana()
            setLevel = p:setLevel(8)
            newLevel = p:getLevel()
            newExp = p:getExperience()
            setMagic = p:setMagicLevel(12, 50)
            baseMagicAfter = p:getBaseMagicLevel()
            effective = p:getEffectiveSkillLevel(2)
            percent = p:getSkillPercent(2)
            stamina = p:getStamina()
            clampedSet = p:setStamina(3000)
            clampedValue = p:getStamina()
            normalSet = p:setStamina(1500)
            staminaAfter = p:getStamina()
            soul = p:getSoul()
            soulAdded = p:addSoul(10)
            soulAfter = p:getSoul()
            maxSoul = p:getMaxSoul()
            ok, added, rv = p:addMoney(500)
            moneyAfter = p:getMoney()
            removed = p:removeMoney(200)
            moneyRemaining = p:getMoney()
            setBank = p:setBankBalance(777)
            bank = p:getBankBalance()
            slot = p:getSlotItem(3)
            bag = p:getBackpack()
            deepItem = p:getItemById(2376, true)
            shallowItem = p:getItemById(2376, false)
            itemEx = p:addItemEx(fresh, true, 0)
            addedCond = p:addCondition(cond)
            hasCond = p:hasCondition(11)
            conditionOut = p:getCondition(11)
            removedCond = p:removeCondition(11)
            hasCondAfter = p:hasCondition(11)
            lost = p:removeExperience(4200)
            levelAfterLoss = p:getLevel()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("baseMagic").toint()).isZero()
        assertThat(environment.getGlobal("spent").toint()).isZero()
        assertThat(environment.getGlobal("addSpent").toboolean()).isTrue()
        assertThat(environment.getGlobal("spentAfter").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("baseHp").toint()).isEqualTo(150)
        assertThat(environment.getGlobal("baseMp").toint()).isEqualTo(90)
        assertThat(environment.getGlobal("setLevel").toboolean()).isTrue()
        assertThat(environment.getGlobal("newLevel").toint()).isEqualTo(8)
        assertThat(environment.getGlobal("newExp").toint()).isEqualTo(Player.getExpForLevel(8).toInt())
        assertThat(environment.getGlobal("setMagic").toboolean()).isTrue()
        assertThat(environment.getGlobal("baseMagicAfter").toint()).isEqualTo(12)
        assertThat(environment.getGlobal("effective").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("percent").todouble()).isZero()
        assertThat(environment.getGlobal("stamina").toint()).isEqualTo(2520)
        assertThat(environment.getGlobal("clampedSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("clampedValue").toint()).isEqualTo(2520)
        assertThat(environment.getGlobal("normalSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("staminaAfter").toint()).isEqualTo(1500)
        assertThat(environment.getGlobal("soul").toint()).isZero()
        assertThat(environment.getGlobal("soulAdded").toboolean()).isTrue()
        assertThat(environment.getGlobal("soulAfter").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("maxSoul").isnil()).isTrue()
        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(environment.getGlobal("added").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("rv").toint()).isZero()
        assertThat(environment.getGlobal("moneyAfter").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("moneyRemaining").toint()).isEqualTo(300)
        assertThat(environment.getGlobal("setBank").toboolean()).isTrue()
        assertThat(environment.getGlobal("bank").toint()).isEqualTo(777)
        assertThat(environment.getGlobal("slot").touserdata()).isSameAs(backpack)
        assertThat(environment.getGlobal("bag").touserdata()).isSameAs(backpack)
        assertThat(environment.getGlobal("deepItem").touserdata()).isSameAs(sword)
        assertThat(environment.getGlobal("shallowItem").isnil()).isTrue()
        assertThat(environment.getGlobal("itemEx").toint()).isZero()
        assertThat(environment.getGlobal("addedCond").toboolean()).isTrue()
        assertThat(environment.getGlobal("hasCond").toboolean()).isTrue()
        assertThat(environment.getGlobal("conditionOut").isnil()).isFalse()
        assertThat(environment.getGlobal("removedCond").toboolean()).isTrue()
        assertThat(environment.getGlobal("hasCondAfter").toboolean()).isFalse()
        assertThat(environment.getGlobal("lost").toboolean()).isTrue()
        assertThat(environment.getGlobal("levelAfterLoss").toint()).isEqualTo(1)
    }
}
