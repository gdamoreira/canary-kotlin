package org.opentibia.canary.lua.bindings.core

/**
 * Test-only [PlayerOperations] base with inert defaults. Binding transcript tests
 * override only the seams they exercise, so adding a Canary method to the interface
 * never forces every test fake to grow another stub.
 */
open class StubPlayerOperations : PlayerOperations {
    override fun itemIdByName(name: String): Int = 0

    override fun addItem(
        player: Any,
        itemId: Int,
        count: Int,
        canDropOnMap: Boolean,
        subType: Int,
        slot: Int,
        tier: Int,
    ): List<Any> = emptyList()

    override fun removeItem(
        player: Any,
        itemId: Int,
        count: Int,
        subType: Int,
        ignoreEquipped: Boolean,
        ignoreStoreInbox: Boolean,
    ): Boolean = false

    override fun getItemCount(
        player: Any,
        itemId: Int,
        subType: Int,
        ignoreEquipped: Boolean,
        ignoreStoreInbox: Boolean,
    ): Long = 0L

    override fun addSkillTries(player: Any, skillType: Int, tries: Long): Boolean = false

    override fun sendTextMessage(player: Any, message: LuaTextMessage): Boolean = false

    override fun getStorageValue(player: Any, key: Int): Int = -1

    override fun setStorageValue(player: Any, key: Int, value: Int): Boolean = false

    override fun getLevel(player: Any): Int = 0

    override fun getExperience(player: Any): Long = 0L

    override fun addExperience(player: Any, experience: Long, sendText: Boolean): Boolean = false

    override fun getMana(player: Any): Int = 0

    override fun addMana(player: Any, manaChange: Int, animationOnLoss: Boolean): Boolean = false

    override fun getVocation(player: Any): Any = Unit

    override fun getGuid(player: Any): Long? = null

    override fun getSpeed(player: Any): Int? = null

    override fun setSpeed(player: Any, speed: Int): Boolean = false

    override fun getCapacity(player: Any): Long? = null

    override fun setCapacity(player: Any, capacity: Long): Boolean = false

    override fun getFreeCapacity(player: Any): Long? = null

    override fun getMaxMana(player: Any): Int? = null

    override fun setMaxMana(player: Any, maxMana: Int): Boolean = false

    override fun getSkillLevel(player: Any, skillType: Int): Int? = null

    override fun getSkillTries(player: Any, skillType: Int): Long? = null

    override fun getPremiumDays(player: Any): Int? = null

    override fun getFightMode(player: Any): Int? = null

    override fun hasChaseMode(player: Any): Boolean = false

    override fun hasSecureMode(player: Any): Boolean = false

    override fun getBaseMagicLevel(player: Any): Int? = null

    override fun getManaSpent(player: Any): Long? = null

    override fun addManaSpent(player: Any, amount: Long): Boolean? = null

    override fun getBaseMaxHealth(player: Any): Int? = null

    override fun getBaseMaxMana(player: Any): Int? = null

    override fun removeExperience(player: Any, experience: Long, sendText: Boolean): Boolean? = null

    override fun setLevel(player: Any, level: Int): Boolean? = null

    override fun setMagicLevel(player: Any, level: Int, manaSpent: Long): Boolean? = null

    override fun getEffectiveSkillLevel(player: Any, skillType: Int): Int? = null

    override fun getSkillPercent(player: Any, skillType: Int): Double? = null

    override fun setSkillLevel(player: Any, skillType: Int, level: Int, tries: Long): Boolean? = null

    override fun getItemById(player: Any, itemId: Int, deepSearch: Boolean, subType: Int): Any? = null

    override fun addItemEx(
        player: Any,
        item: Any,
        canDropOnMap: Boolean,
        indexOrSlot: Int,
        flags: Int,
    ): Int? = null

    override fun getSlotItem(player: Any, slot: Int): Any? = null

    override fun getBackpack(player: Any): Any? = null

    override fun getMoney(player: Any): Long? = null

    override fun addMoney(player: Any, money: Long, flags: Int): LuaMoneyResult? = null

    override fun removeMoney(player: Any, money: Long, flags: Int, useBank: Boolean): Boolean? = null

    override fun getBankBalance(player: Any): Long? = null

    override fun setBankBalance(player: Any, bankBalance: Long): Boolean? = null

    override fun getCondition(player: Any, conditionType: Int, conditionId: Int, subId: Int): Any? = null

    override fun addCondition(player: Any, condition: Any): Boolean? = null

    override fun removeCondition(
        player: Any,
        conditionType: Int,
        conditionId: Int,
        subId: Int,
        force: Boolean,
    ): Boolean? = null

    override fun hasCondition(player: Any, conditionType: Int, subId: Int): Boolean? = null

    override fun getStamina(player: Any): Int? = null

    override fun setStamina(player: Any, stamina: Int): Boolean? = null

    override fun getSoul(player: Any): Int? = null

    override fun addSoul(player: Any, soulChange: Int): Boolean? = null

    override fun getMaxSoul(player: Any): Int? = null
}
