package org.opentibia.canary.lua.bindings.core

/**
 * Test-only [PlayerExtraOperations] base with inert defaults. Binding transcript tests
 * override only the seams they exercise, so adding a Canary method to the interface
 * never forces every test fake to grow another stub.
 */
open class StubPlayerExtraOperations : PlayerExtraOperations {
    override fun getIdleTime(player: Any): Long? = null

    override fun getFreeBackpackSlots(player: Any): Int? = null

    override fun isOffline(player: Any): Boolean = false

    override fun openMarket(player: Any): Boolean? = null

    override fun openForge(player: Any): Boolean? = null

    override fun closeForge(player: Any): Boolean? = null

    override fun addForgeDusts(player: Any, amount: Long): Boolean? = null

    override fun removeForgeDusts(player: Any, amount: Long): Boolean? = null

    override fun getForgeDusts(player: Any): Long? = null

    override fun setForgeDusts(player: Any, amount: Long): Boolean? = null

    override fun addForgeDustLevel(player: Any, amount: Long): Boolean? = null

    override fun removeForgeDustLevel(player: Any, amount: Long): Boolean? = null

    override fun getForgeDustLevel(player: Any): Long? = null

    override fun getForgeSlivers(player: Any): Long? = null

    override fun getForgeCores(player: Any): Long? = null

    override fun setFaction(player: Any, factionId: Int): Boolean? = null

    override fun getFaction(player: Any): Int? = null

    override fun isUIExhausted(player: Any, time: Int): Boolean? = null

    override fun updateUIExhausted(player: Any): Boolean? = null

    override fun sendBosstiaryCooldownTimer(player: Any): Boolean? = null

    override fun getBosstiaryLevel(player: Any, monsterName: String): Int? = null

    override fun getBosstiaryKills(player: Any, monsterName: String): Int? = null

    override fun addBosstiaryKill(player: Any, monsterName: String, amount: Long): Boolean? = null

    override fun setBossPoints(player: Any, points: Long): Boolean? = null

    override fun setRemoveBossTime(player: Any, time: Int): Boolean? = null

    override fun getSlotBossId(player: Any, slotId: Int): Long? = null

    override fun getBossBonus(player: Any, slotId: Int): Long? = null

    override fun sendSingleSoundEffect(player: Any, soundId: Int, actor: Boolean): Boolean? = null

    override fun sendDoubleSoundEffect(
        player: Any,
        mainSoundId: Int,
        secondarySoundId: Int,
        actor: Boolean,
    ): Boolean? = null

    override fun sendAmbientSoundEffect(player: Any, soundId: Int): Boolean? = null

    override fun sendMusicSoundEffect(player: Any, soundId: Int): Boolean? = null

    override fun changeName(player: Any, newName: String): Boolean? = null

    override fun hasGroupFlag(player: Any, flag: Int): Boolean? = null

    override fun setGroupFlag(player: Any, flag: Int): Boolean? = null

    override fun removeGroupFlag(player: Any, flag: Int): Boolean? = null

    override fun setHazardSystemPoints(player: Any, amount: Int): Boolean? = null

    override fun getHazardSystemPoints(player: Any): Int? = null

    override fun setLoyaltyBonus(player: Any, amount: Int): Boolean? = null

    override fun getLoyaltyBonus(player: Any): Int? = null

    override fun getLoyaltyPoints(player: Any): Int? = null

    override fun getLoyaltyTitle(player: Any): String? = null

    override fun setLoyaltyTitle(player: Any, name: String): Boolean? = null

    override fun instantSkillWOD(player: Any, name: String, value: Boolean?): Boolean? = null

    override fun upgradeSpellsWOD(player: Any, name: String?, add: Boolean?): Any? = null

    override fun revelationStageWOD(player: Any, name: String?, stage: Int?): Any? = null

    override fun reloadData(player: Any): Boolean? = null

    override fun onThinkWheelOfDestiny(player: Any, force: Boolean): Boolean? = null

    override fun avatarTimer(player: Any, value: Long?): Any? = null

    override fun getWheelSpellAdditionalArea(player: Any, spellName: String): Boolean? = null

    override fun getWheelSpellAdditionalTarget(player: Any, spellName: String): Long? = null

    override fun getWheelSpellAdditionalDuration(player: Any, spellName: String): Long? = null

    override fun wheelUnlockScroll(player: Any, scrollName: String): Boolean? = null

    override fun updateConcoction(player: Any, itemId: Int, timeLeft: Int): Boolean? = null

    override fun updateFood(player: Any, itemId: Int, timeLeft: Long): Boolean? = null

    override fun clearSpellCooldowns(
        player: Any,
        spenders: Boolean,
        builders: Boolean,
        ticks: Int,
    ): Boolean? = null

    override fun isVip(player: Any): Boolean? = null

    override fun getVipDays(player: Any): Int? = null

    override fun getVipTime(player: Any): Long? = null

    override fun getKv(player: Any): Any? = null

    override fun getStoreInbox(player: Any): Any? = null

    override fun achievementIdByName(name: String): Int = 0

    override fun hasAchievement(player: Any, achievementId: Int): Boolean? = null

    override fun addAchievement(player: Any, achievementId: Int, sendMessage: Boolean): Boolean? = null

    override fun removeAchievement(player: Any, achievementId: Int): Boolean? = null

    override fun getAchievementPoints(player: Any): Int? = null

    override fun addAchievementPoints(player: Any, amount: Int): Boolean? = null

    override fun removeAchievementPoints(player: Any, amount: Int): Boolean? = null

    override fun addBadge(player: Any, badgeId: Int): Boolean? = null

    override fun addTitle(player: Any, titleId: Int): Boolean? = null

    override fun getTitles(player: Any): List<LuaPlayerTitle>? = null

    override fun setCurrentTitle(player: Any, titleId: Int): Boolean? = null

    override fun createTransactionSummary(player: Any, type: Int, amount: Int, id: String): Boolean? = null

    override fun takeScreenshot(
        player: Any,
        type: Int,
        skillId: Int,
        skillLevel: Int,
        achievementName: String,
        raceId: Int,
        bestiaryStep: Int,
    ): Boolean? = null

    override fun sendIconBakragore(player: Any, iconType: Int): Boolean? = null

    override fun removeIconBakragore(player: Any, iconType: Int): Boolean? = null

    override fun sendCreatureAppear(player: Any, isLogin: Boolean): Boolean? = null

    override fun setRaceIconOverlay(player: Any, raceId: Int, icon: Int, enabled: Boolean): Boolean? = null

    override fun clearRaceIconOverlays(player: Any, icon: Int): Boolean? = null

    override fun refreshVisibleCreatureIcons(player: Any): Boolean? = null

    override fun addAnimusMastery(player: Any, monsterType: String): Boolean? = null

    override fun removeAnimusMastery(player: Any, monsterType: String): Boolean? = null

    override fun hasAnimusMastery(player: Any, monsterType: String): Boolean? = null

    override fun setSerene(player: Any, set: Boolean, ticks: Int): Boolean? = null

    override fun getVirtue(player: Any): Int? = null

    override fun setVirtue(player: Any, virtue: Int): Boolean? = null

    override fun fillHarmony(player: Any): Boolean? = null

    override fun getHarmony(player: Any): Int? = null

    override fun getHarmonyDamage(player: Any, baseMin: Int, baseMax: Int): Pair<Long, Long>? = null

    override fun calculateFlatDamageHealing(player: Any): Long? = null

    override fun addWeaponExperience(player: Any, experience: Int, itemId: Int): Boolean? = null

    override fun getLivestreamViewersCount(player: Any): Int? = null

    override fun getLivestreamViewers(player: Any): LuaLivestreamViewers? = null

    override fun setLivestreamViewers(player: Any, viewers: LuaLivestreamOverrides): Boolean? = null

    override fun isLivestreamViewer(player: Any): Boolean? = null

    override fun getMapShader(player: Any): String? = null

    override fun setMapShader(player: Any, shaderName: String): Boolean? = null

    override fun addCustomOutfit(player: Any, type: String, idOrName: Any): Boolean? = null

    override fun removeCustomOutfit(player: Any, type: String, idOrName: Any): Boolean? = null
}
