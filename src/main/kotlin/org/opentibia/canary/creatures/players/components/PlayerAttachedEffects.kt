package org.opentibia.canary.creatures.players.components

import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.appearance.effects.AttachedEffects
import org.opentibia.canary.creatures.appearance.effects.Aura
import org.opentibia.canary.creatures.appearance.effects.Effect
import org.opentibia.canary.creatures.appearance.effects.Shader
import org.opentibia.canary.creatures.appearance.effects.Wing
import org.opentibia.canary.creatures.appearance.outfit.OutfitPlayer
import org.opentibia.canary.creatures.appearance.outfit.Outfits
import org.opentibia.canary.kv.KV
import org.opentibia.canary.kv.ValueWrapper
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Global `uniform_random(min, max)` (`src/utils/tools.hpp`) seam; inclusive on
 * both ends like the C++ helper (`player_attached_effects.cpp:75, 264, 452,
 * 606`).
 */
fun interface AttachedEffectsRandom {
    fun uniformRandom(min: Int, max: Int): Int

    companion object {
        val DEFAULT: AttachedEffectsRandom = AttachedEffectsRandom { min, max ->
            if (max <= min) min else kotlin.random.Random.nextInt(min, max + 1)
        }
    }
}

/**
 * `std::variant<uint16_t, std::string>` argument of
 * `PlayerAttachedEffects::addCustomOutfit`/`removeCustomOutfit`
 * (player_attached_effects.cpp:747, 795).
 */
sealed interface AttachedEffectSelector {
    data class Id(val id: Int) : AttachedEffectSelector
    data class Name(val name: String) : AttachedEffectSelector
}

/**
 * `m_player.client` seam (`player_attached_effects.cpp:845, 853, 861, 869`).
 * The C++ member is null for players without a protocol game.
 */
interface AttachedEffectsClient {
    /** `ProtocolGame::sendAttachedEffect(creature, effectId)` (player_attached_effects.cpp:849). */
    fun sendAttachedEffect(creature: Creature, effectId: Int)

    /** `ProtocolGame::sendDetachEffect(creature, effectId)` (player_attached_effects.cpp:857). */
    fun sendDetachEffect(creature: Creature, effectId: Int)

    /** `ProtocolGame::sendShader(creature, shaderName)` (player_attached_effects.cpp:865). */
    fun sendShader(creature: Creature, shaderName: String)

    /** `ProtocolGame::sendMapShader(shaderName)` (player_attached_effects.cpp:873). */
    fun sendMapShader(shaderName: String)
}

/**
 * Injected `Player` view for the attached-effects component. Every member
 * transcribes the exact `Player` symbol `PlayerAttachedEffects` calls, with the
 * C++ line cited.
 */
interface AttachedEffectsPlayer : OutfitPlayer {
    /** `m_player.getDefaultOutfit()` (`player_attached_effects.cpp:20, 97, 284, 472, 629`). */
    fun getDefaultOutfit(): Outfit

    /** `m_player.setDefaultOutfit(defaultOutfit)` (`player_attached_effects.cpp:128, 214, 318, 505, 590, 659`). */
    fun setDefaultOutfit(outfit: Outfit)

    /** `m_player.getStorageValue(key)` (`player_attached_effects.cpp:27, 40, 154, 176, ...`). */
    fun getStorageValue(key: Int): Int

    /** `m_player.addStorageValue(key, value)` (`player_attached_effects.cpp:48, 161, 182, ...`). */
    fun addStorageValue(key: Int, value: Int)

    /** `m_player.kv()` (`player_attached_effects.cpp:31, 117, 130, 224, ...`). */
    fun kv(): KV

    /** `m_player.isAccessPlayer()` (`player_attached_effects.cpp:198, 386, 574, 715`). */
    fun isAccessPlayer(): Boolean

    /** `m_player.isRandomMounted()` (`player_attached_effects.cpp:106, 295, 483`). */
    fun isRandomMounted(): Boolean

    /** `m_player.hasCondition(CONDITION_OUTFIT)` (`player_attached_effects.cpp:122, 311, 499, 653`). */
    fun hasCondition(conditionType: ConditionType): Boolean

    /** `m_player.sendCancelMessage(returnValue)` (`player_attached_effects.cpp:85, 123, ...`). */
    fun sendCancelMessage(returnValue: ReturnValue)

    /** `m_player.sendOutfitWindow()` (`player_attached_effects.cpp:102, 118, 291, ...`). */
    fun sendOutfitWindow()

    /** `g_game().internalCreatureChangeOutfit(thisPlayer, defaultOutfit)` (`player_attached_effects.cpp:140, 329, ...`). */
    fun internalCreatureChangeOutfit(outfit: Outfit)
}

/**
 * Mirror of Canary `PlayerAttachedEffects`
 * (`src/creatures/players/components/player_attached_effects.hpp:19-136`,
 * player_attached_effects.cpp:18-874).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). The
 * `m_player` reference becomes [player], the `g_game().getAttachedEffects()`
 * singleton becomes the injected [attachedEffects] registry, the
 * `Outfits::getInstance()` singleton the injected [outfits], `m_player.client`
 * the nullable [client], `OTSYS_TIME()` the [now] seam and the global
 * `uniform_random` the [random] seam (AGENTS.md 1.1).
 *
 * PARITY NOTES:
 *  - `getDefaultOutfit()` is copied into [defaultOutfit] at construction,
 *    matching the C++ by-value copy (`player_attached_effects.cpp:20`).
 *  - The C++ `uint8_t` wing/aura/effect buffer indices wrap around 255; the
 *    port masks with `0xFF` at the same casts.
 *  - The C++ `currentWing`/`currentAura`/`currentEffect` fields are never read
 *    (the getters read storage); only `currentShader` is kept here, defaulting
 *    to 0 instead of the C++ uninitialised value.
 *  - `getRandomShader` bounds-checks the sampled index; the C++ indexes the
 *    vector unchecked (player_attached_effects.cpp:606).
 */
class PlayerAttachedEffects(
    private val player: AttachedEffectsPlayer,
    /** `g_game().getAttachedEffects()` (`player_attached_effects.cpp:56, 63, 110, ...`). */
    private val attachedEffects: AttachedEffects,
    /** `Outfits::getInstance()` (`player_attached_effects.cpp:95, 284, 472, 629`). */
    private val outfits: Outfits,
    /** `m_player.client` (`player_attached_effects.cpp:845, 853, 861, 869`). */
    private val client: AttachedEffectsClient? = null,
    /** Global `uniform_random` (`player_attached_effects.cpp:75, 264, 452, 606`). */
    private val random: AttachedEffectsRandom = AttachedEffectsRandom.DEFAULT,
    /** `OTSYS_TIME()` (`player_attached_effects.cpp:84, 141, 273, 330, ...`). */
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /** `PlayerAttachedEffects::shaders` (`player_attached_effects.hpp:111`). */
    private val shaders = mutableSetOf<Int>()

    /** `PlayerAttachedEffects::mapShader` (`player_attached_effects.hpp:112`). */
    private var mapShader: String = ""

    private var lastToggleWing = 0L
    private var lastToggleEffect = 0L
    private var lastToggleAura = 0L
    private var lastToggleShader = 0L

    private var currentShader = 0

    private var wasWinged = false
    private var wasAuraed = false
    private var wasEffected = false
    private var wasShadered = false

    /** `PlayerAttachedEffects::defaultOutfit` (`player_attached_effects.hpp:133`). */
    private val defaultOutfit: Outfit = player.getDefaultOutfit().copy()

    // ---- wings ---------------------------------------------------------------

    /** `PlayerAttachedEffects::getLastWing` (player_attached_effects.cpp:26-37). */
    fun getLastWing(): Int {
        val value = player.getStorageValue(PlayerStorage.PSTRG_WING_CURRENTWING)
        if (value > 0) {
            return value and 0xFF
        }
        val lastWing = player.kv().get("last-wing")?.getNumber() ?: return 0
        return lastWing.toInt() and 0xFF
    }

    /** `PlayerAttachedEffects::getCurrentWing` (player_attached_effects.cpp:39-45). */
    fun getCurrentWing(): Int {
        val value = player.getStorageValue(PlayerStorage.PSTRG_WING_CURRENTWING)
        if (value > 0) {
            return value and 0xFF
        }
        return 0
    }

    /** `PlayerAttachedEffects::setCurrentWing` (player_attached_effects.cpp:47-49). */
    fun setCurrentWing(wing: Int) {
        player.addStorageValue(PlayerStorage.PSTRG_WING_CURRENTWING, wing)
    }

    /** `PlayerAttachedEffects::isWinged` (player_attached_effects.cpp:51-53). */
    fun isWinged(): Boolean = defaultOutfit.lookWing != 0

    /** `PlayerAttachedEffects::hasAnyWing` (player_attached_effects.cpp:55-60). */
    fun hasAnyWing(): Boolean = attachedEffects.getWings().any { hasWing(it) }

    /** `PlayerAttachedEffects::getRandomWingId` (player_attached_effects.cpp:62-81). */
    fun getRandomWingId(): Int {
        val availableWings = mutableListOf<Int>()
        for (wing in attachedEffects.getWings()) {
            if (hasWing(wing)) {
                availableWings.add(wing.id and 0xFF)
            }
        }

        if (availableWings.isEmpty()) {
            return 0
        }

        val randomIndex = random.uniformRandom(0, availableWings.size - 1)
        if (randomIndex in availableWings.indices) {
            return availableWings[randomIndex]
        }
        return 0
    }

    /** `PlayerAttachedEffects::toggleWing` (player_attached_effects.cpp:83-143). */
    fun toggleWing(wing: Boolean): Boolean {
        if ((now() - lastToggleWing) < 3000 && !wasWinged) {
            player.sendCancelMessage(ReturnValue.YOUAREEXHAUSTED)
            return false
        }

        if (wing) {
            if (isWinged()) {
                return false
            }

            val playerOutfit = outfits.getOutfitByLookType(player, defaultOutfit.lookType)
            if (playerOutfit == null) {
                return false
            }

            var currentWingId = getLastWing()
            if (currentWingId == 0) {
                player.sendOutfitWindow()
                return false
            }

            if (player.isRandomMounted()) {
                currentWingId = getRandomWingId()
            }

            val currentWing = attachedEffects.getWingByID(currentWingId) ?: return false
            if (!hasWing(currentWing)) {
                setCurrentWing(0)
                player.kv().set("last-wing", ValueWrapper(0))
                player.sendOutfitWindow()
                return false
            }

            if (player.hasCondition(ConditionType.CONDITION_OUTFIT)) {
                player.sendCancelMessage(ReturnValue.NOTPOSSIBLE)
                return false
            }

            defaultOutfit.lookWing = currentWing.id
            player.setDefaultOutfit(defaultOutfit)
            setCurrentWing(currentWing.id)
            player.kv().set("last-wing", ValueWrapper(currentWing.id))
        } else {
            if (!isWinged()) {
                return false
            }

            diswing()
        }

        player.internalCreatureChangeOutfit(defaultOutfit)
        lastToggleWing = now()
        return true
    }

    /** `PlayerAttachedEffects::tameWing` (player_attached_effects.cpp:145-163). */
    fun tameWing(wingId: Int): Boolean {
        attachedEffects.getWingByID(wingId) ?: return false

        val tmpWingId = (wingId - 1) and 0xFF
        val key = PlayerStorage.PSTRG_WING_RANGE_START + (tmpWingId / 31)

        val value = player.getStorageValue(key)
        val newValue = if (value != -1) {
            value or (1 shl (tmpWingId % 31))
        } else {
            1 shl (tmpWingId % 31)
        }

        player.addStorageValue(key, newValue)
        return true
    }

    /** `PlayerAttachedEffects::untameWing` (player_attached_effects.cpp:165-195). */
    fun untameWing(wingId: Int): Boolean {
        attachedEffects.getWingByID(wingId) ?: return false

        val tmpWingId = (wingId - 1) and 0xFF
        val key = PlayerStorage.PSTRG_WING_RANGE_START + (tmpWingId / 31)

        val value = player.getStorageValue(key)
        if (value == -1) {
            return true
        }

        player.addStorageValue(key, value and (1 shl (tmpWingId % 31)).inv())

        if (getCurrentWing() == wingId) {
            if (isWinged()) {
                diswing()
                player.internalCreatureChangeOutfit(defaultOutfit)
            }

            setCurrentWing(0)
            player.kv().set("last-wing", ValueWrapper(0))
        }

        return true
    }

    /** `PlayerAttachedEffects::hasWing` (player_attached_effects.cpp:197-210). */
    fun hasWing(wing: Wing): Boolean {
        if (player.isAccessPlayer()) {
            return true
        }

        val tmpWingId = (wing.id - 1) and 0xFF
        val value = player.getStorageValue(PlayerStorage.PSTRG_WING_RANGE_START + (tmpWingId / 31))
        if (value == -1) {
            return false
        }

        return ((1 shl (tmpWingId % 31)) and value) != 0
    }

    /** `PlayerAttachedEffects::diswing` (player_attached_effects.cpp:212-215). */
    fun diswing() {
        defaultOutfit.lookWing = 0
        player.setDefaultOutfit(defaultOutfit)
    }

    // ---- auras ---------------------------------------------------------------

    /** `PlayerAttachedEffects::getLastAura` (player_attached_effects.cpp:219-230). */
    fun getLastAura(): Int {
        val value = player.getStorageValue(PlayerStorage.PSTRG_AURA_CURRENTAURA)
        if (value > 0) {
            return value and 0xFF
        }
        val lastAura = player.kv().get("last-aura")?.getNumber() ?: return 0
        return lastAura.toInt() and 0xFF
    }

    /** `PlayerAttachedEffects::getCurrentAura` (player_attached_effects.cpp:232-238). */
    fun getCurrentAura(): Int {
        val value = player.getStorageValue(PlayerStorage.PSTRG_AURA_CURRENTAURA)
        if (value > 0) {
            return value and 0xFF
        }
        return 0
    }

    /** `PlayerAttachedEffects::setCurrentAura` (player_attached_effects.cpp:240-242). */
    fun setCurrentAura(aura: Int) {
        player.addStorageValue(PlayerStorage.PSTRG_AURA_CURRENTAURA, aura)
    }

    /** `PlayerAttachedEffects::isAuraed` (player_attached_effects.hpp:40-42). */
    fun isAuraed(): Boolean = defaultOutfit.lookAura != 0

    /** `PlayerAttachedEffects::hasAnyAura` (player_attached_effects.cpp:244-249). */
    fun hasAnyAura(): Boolean = attachedEffects.getAuras().any { hasAura(it) }

    /** `PlayerAttachedEffects::getRandomAuraId` (player_attached_effects.cpp:251-270). */
    fun getRandomAuraId(): Int {
        val playerAuras = mutableListOf<Int>()
        for (aura in attachedEffects.getAuras()) {
            if (hasAura(aura)) {
                playerAuras.add(aura.id and 0xFF)
            }
        }

        if (playerAuras.isEmpty()) {
            return 0
        }

        val randomIndex = random.uniformRandom(0, playerAuras.size - 1)
        if (randomIndex in playerAuras.indices) {
            return playerAuras[randomIndex]
        }
        return 0
    }

    /** `PlayerAttachedEffects::toggleAura` (player_attached_effects.cpp:272-332). */
    fun toggleAura(aura: Boolean): Boolean {
        if ((now() - lastToggleAura) < 3000 && !wasAuraed) {
            player.sendCancelMessage(ReturnValue.YOUAREEXHAUSTED)
            return false
        }

        if (aura) {
            if (isAuraed()) {
                return false
            }

            val playerOutfit = outfits.getOutfitByLookType(player, defaultOutfit.lookType)
            if (playerOutfit == null) {
                return false
            }

            var currentAuraId = getLastAura()
            if (currentAuraId == 0) {
                player.sendOutfitWindow()
                return false
            }

            if (player.isRandomMounted()) {
                currentAuraId = getRandomAuraId()
            }

            val currentAura = attachedEffects.getAuraByID(currentAuraId) ?: return false
            if (!hasAura(currentAura)) {
                setCurrentAura(0)
                player.kv().set("last-aura", ValueWrapper(0))
                player.sendOutfitWindow()
                return false
            }

            if (player.hasCondition(ConditionType.CONDITION_OUTFIT)) {
                player.sendCancelMessage(ReturnValue.NOTPOSSIBLE)
                return false
            }

            defaultOutfit.lookAura = currentAura.id
            player.setDefaultOutfit(defaultOutfit)
            setCurrentAura(currentAura.id)
            player.kv().set("last-aura", ValueWrapper(currentAura.id))
        } else {
            if (!isAuraed()) {
                return false
            }

            disaura()
        }

        player.internalCreatureChangeOutfit(defaultOutfit)
        lastToggleAura = now()
        return true
    }

    /** `PlayerAttachedEffects::tameAura` (player_attached_effects.cpp:334-352). */
    fun tameAura(auraId: Int): Boolean {
        attachedEffects.getAuraByID(auraId) ?: return false

        val tmpAuraId = (auraId - 1) and 0xFF
        val key = PlayerStorage.PSTRG_AURA_RANGE_START + (tmpAuraId / 31)

        val value = player.getStorageValue(key)
        val newValue = if (value != -1) {
            value or (1 shl (tmpAuraId % 31))
        } else {
            1 shl (tmpAuraId % 31)
        }

        player.addStorageValue(key, newValue)
        return true
    }

    /** `PlayerAttachedEffects::untameAura` (player_attached_effects.cpp:354-383). */
    fun untameAura(auraId: Int): Boolean {
        attachedEffects.getAuraByID(auraId) ?: return false

        val tmpAuraId = (auraId - 1) and 0xFF
        val key = PlayerStorage.PSTRG_AURA_RANGE_START + (tmpAuraId / 31)

        val value = player.getStorageValue(key)
        if (value == -1) {
            return true
        }

        player.addStorageValue(key, value and (1 shl (tmpAuraId % 31)).inv())

        if (getCurrentAura() == auraId) {
            if (isAuraed()) {
                disaura()
                player.internalCreatureChangeOutfit(defaultOutfit)
            }

            setCurrentAura(0)
            player.kv().set("last-aura", ValueWrapper(0))
        }

        return true
    }

    /** `PlayerAttachedEffects::hasAura` (player_attached_effects.cpp:385-397). */
    fun hasAura(aura: Aura): Boolean {
        if (player.isAccessPlayer()) {
            return true
        }
        val tmpAuraId = (aura.id - 1) and 0xFF
        val value = player.getStorageValue(PlayerStorage.PSTRG_AURA_RANGE_START + (tmpAuraId / 31))
        if (value == -1) {
            return false
        }

        return ((1 shl (tmpAuraId % 31)) and value) != 0
    }

    /** `PlayerAttachedEffects::disaura` (player_attached_effects.cpp:399-402). */
    fun disaura() {
        defaultOutfit.lookAura = 0
        player.setDefaultOutfit(defaultOutfit)
    }

    // ---- effects -------------------------------------------------------------

    /** `PlayerAttachedEffects::getLastEffect` (player_attached_effects.cpp:406-418). */
    fun getLastEffect(): Int {
        val value = player.getStorageValue(PlayerStorage.PSTRG_EFFECT_CURRENTEFFECT)
        if (value > 0) {
            return value and 0xFF
        }
        val lastEffect = player.kv().get("last-effect")?.getNumber() ?: return 0
        return lastEffect.toInt() and 0xFF
    }

    /** `PlayerAttachedEffects::getCurrentEffect` (player_attached_effects.cpp:420-426). */
    fun getCurrentEffect(): Int {
        val value = player.getStorageValue(PlayerStorage.PSTRG_EFFECT_CURRENTEFFECT)
        if (value > 0) {
            return value and 0xFF
        }
        return 0
    }

    /** `PlayerAttachedEffects::setCurrentEffect` (player_attached_effects.cpp:428-430). */
    fun setCurrentEffect(effect: Int) {
        player.addStorageValue(PlayerStorage.PSTRG_EFFECT_CURRENTEFFECT, effect)
    }

    /** `PlayerAttachedEffects::isEffected` (player_attached_effects.hpp:55-57). */
    fun isEffected(): Boolean = defaultOutfit.lookEffect != 0

    /** `PlayerAttachedEffects::hasAnyEffect` (player_attached_effects.cpp:432-437). */
    fun hasAnyEffect(): Boolean = attachedEffects.getEffects().any { hasEffect(it) }

    /** `PlayerAttachedEffects::getRandomEffectId` (player_attached_effects.cpp:439-458). */
    fun getRandomEffectId(): Int {
        val playerEffects = mutableListOf<Int>()
        for (effect in attachedEffects.getEffects()) {
            if (hasEffect(effect)) {
                playerEffects.add(effect.id and 0xFF)
            }
        }

        if (playerEffects.isEmpty()) {
            return 0
        }

        val randomIndex = random.uniformRandom(0, playerEffects.size - 1)
        if (randomIndex in playerEffects.indices) {
            return playerEffects[randomIndex]
        }
        return 0
    }

    /** `PlayerAttachedEffects::toggleEffect` (player_attached_effects.cpp:460-520). */
    fun toggleEffect(effect: Boolean): Boolean {
        if ((now() - lastToggleEffect) < 3000 && !wasEffected) {
            player.sendCancelMessage(ReturnValue.YOUAREEXHAUSTED)
            return false
        }

        if (effect) {
            if (isEffected()) {
                return false
            }

            val playerOutfit = outfits.getOutfitByLookType(player, defaultOutfit.lookType)
            if (playerOutfit == null) {
                return false
            }

            var currentEffectId = getLastEffect()
            if (currentEffectId == 0) {
                player.sendOutfitWindow()
                return false
            }

            if (player.isRandomMounted()) {
                currentEffectId = getRandomEffectId()
            }

            val currentEffect = attachedEffects.getEffectByID(currentEffectId) ?: return false
            if (!hasEffect(currentEffect)) {
                setCurrentEffect(0)
                player.kv().set("last-effect", ValueWrapper(0))
                player.sendOutfitWindow()
                return false
            }

            if (player.hasCondition(ConditionType.CONDITION_OUTFIT)) {
                player.sendCancelMessage(ReturnValue.NOTPOSSIBLE)
                return false
            }

            defaultOutfit.lookEffect = currentEffect.id
            player.setDefaultOutfit(defaultOutfit)
            setCurrentEffect(currentEffect.id)
            player.kv().set("last-effect", ValueWrapper(currentEffect.id))
        } else {
            if (!isEffected()) {
                return false
            }

            diseffect()
        }

        player.internalCreatureChangeOutfit(defaultOutfit)
        lastToggleEffect = now()
        return true
    }

    /** `PlayerAttachedEffects::tameEffect` (player_attached_effects.cpp:522-540). */
    fun tameEffect(effectId: Int): Boolean {
        attachedEffects.getEffectByID(effectId) ?: return false

        val tmpEffectId = (effectId - 1) and 0xFF
        val key = PlayerStorage.PSTRG_EFFECT_RANGE_START + (tmpEffectId / 31)

        val value = player.getStorageValue(key)
        val newValue = if (value != -1) {
            value or (1 shl (tmpEffectId % 31))
        } else {
            1 shl (tmpEffectId % 31)
        }

        player.addStorageValue(key, newValue)
        return true
    }

    /** `PlayerAttachedEffects::untameEffect` (player_attached_effects.cpp:542-571). */
    fun untameEffect(effectId: Int): Boolean {
        attachedEffects.getEffectByID(effectId) ?: return false

        val tmpEffectId = (effectId - 1) and 0xFF
        val key = PlayerStorage.PSTRG_EFFECT_RANGE_START + (tmpEffectId / 31)

        val value = player.getStorageValue(key)
        if (value == -1) {
            return true
        }

        player.addStorageValue(key, value and (1 shl (tmpEffectId % 31)).inv())

        if (getCurrentEffect() == effectId) {
            if (isEffected()) {
                diseffect()
                player.internalCreatureChangeOutfit(defaultOutfit)
            }

            setCurrentEffect(0)
            player.kv().set("last-effect", ValueWrapper(0))
        }

        return true
    }

    /** `PlayerAttachedEffects::hasEffect` (player_attached_effects.cpp:573-586). */
    fun hasEffect(effect: Effect): Boolean {
        if (player.isAccessPlayer()) {
            return true
        }

        val tmpEffectId = (effect.id - 1) and 0xFF
        val value = player.getStorageValue(PlayerStorage.PSTRG_EFFECT_RANGE_START + (tmpEffectId / 31))
        if (value == -1) {
            return false
        }

        return ((1 shl (tmpEffectId % 31)) and value) != 0
    }

    /** `PlayerAttachedEffects::diseffect` (player_attached_effects.cpp:588-591). */
    fun diseffect() {
        defaultOutfit.lookEffect = 0
        player.setDefaultOutfit(defaultOutfit)
    }

    // ---- shaders -------------------------------------------------------------

    /** `PlayerAttachedEffects::getRandomShader` (player_attached_effects.cpp:594-607). */
    fun getRandomShader(): Int {
        val shadersId = mutableListOf<Int>()
        for (shader in attachedEffects.getShaders()) {
            if (hasShader(shader)) {
                shadersId.add(shader.id)
            }
        }

        if (shadersId.isEmpty()) {
            return 0
        }

        val randomIndex = random.uniformRandom(0, shadersId.size - 1)
        return if (randomIndex in shadersId.indices) shadersId[randomIndex] else 0
    }

    /** `PlayerAttachedEffects::getCurrentShader` (player_attached_effects.cpp:609-611). */
    fun getCurrentShader(): Int = currentShader

    /** `PlayerAttachedEffects::setCurrentShader` (player_attached_effects.cpp:613-615). */
    fun setCurrentShader(shaderId: Int) {
        currentShader = shaderId
    }

    /** `PlayerAttachedEffects::isShadered` (player_attached_effects.hpp:70-72). */
    fun isShadered(): Boolean = defaultOutfit.lookShader != 0

    /** `PlayerAttachedEffects::toggleShader` (player_attached_effects.cpp:617-672). */
    fun toggleShader(shader: Boolean): Boolean {
        if ((now() - lastToggleShader) < 3000 && !wasShadered) {
            player.sendCancelMessage(ReturnValue.YOUAREEXHAUSTED)
            return false
        }

        if (shader) {
            if (isShadered()) {
                return false
            }

            val playerOutfit = outfits.getOutfitByLookType(player, defaultOutfit.lookType)
            if (playerOutfit == null) {
                return false
            }

            val currentShaderId = getCurrentShader()
            if (currentShaderId == 0) {
                player.sendOutfitWindow()
                return false
            }

            val currentShaderPtr = attachedEffects.getShaderByID(currentShaderId) ?: return false
            if (!hasShader(currentShaderPtr)) {
                setCurrentShader(0)
                player.sendOutfitWindow()
                return false
            }

            if (player.hasCondition(ConditionType.CONDITION_OUTFIT)) {
                player.sendCancelMessage(ReturnValue.NOTPOSSIBLE)
                return false
            }

            defaultOutfit.lookShader = currentShaderPtr.id
            player.setDefaultOutfit(defaultOutfit)
        } else {
            if (!isShadered()) {
                return false
            }

            disshader()
        }

        player.internalCreatureChangeOutfit(defaultOutfit)
        lastToggleShader = now()
        return true
    }

    /** `PlayerAttachedEffects::tameShader` (player_attached_effects.cpp:674-686). */
    fun tameShader(shaderId: Int): Boolean {
        val shader = attachedEffects.getShaderByID(shaderId) ?: return false
        if (hasShader(shader)) {
            return false
        }

        shaders.add(shaderId)
        return true
    }

    /** `PlayerAttachedEffects::untameShader` (player_attached_effects.cpp:688-712). */
    fun untameShader(shaderId: Int): Boolean {
        val shader = attachedEffects.getShaderByID(shaderId) ?: return false
        if (!hasShader(shader)) {
            return false
        }

        shaders.remove(shaderId)

        if (getCurrentShader() == shaderId) {
            if (isShadered()) {
                disshader()
                player.internalCreatureChangeOutfit(defaultOutfit)
            }

            setCurrentShader(0)
        }

        return true
    }

    /** `PlayerAttachedEffects::hasShader` (player_attached_effects.cpp:714-720). */
    fun hasShader(shader: Shader): Boolean {
        if (player.isAccessPlayer()) {
            return true
        }

        return shaders.contains(shader.id)
    }

    /** `PlayerAttachedEffects::hasShaders` (player_attached_effects.cpp:722-729). */
    fun hasShaders(): Boolean = attachedEffects.getShaders().any { hasShader(it) }

    /** `PlayerAttachedEffects::disshader` (player_attached_effects.cpp:731-734). */
    fun disshader() {
        defaultOutfit.lookShader = 0
        player.setDefaultOutfit(defaultOutfit)
    }

    /** `PlayerAttachedEffects::getCurrentShader_NAME` (player_attached_effects.cpp:736-745). */
    fun getCurrentShader_NAME(): String {
        val currentShader = attachedEffects.getShaderByID(getCurrentShader())
        return currentShader?.name ?: "Outfit - Default"
    }

    /** `PlayerAttachedEffects::addCustomOutfit` (player_attached_effects.cpp:747-793). */
    fun addCustomOutfit(type: String, idOrName: AttachedEffectSelector): Boolean {
        val elementId = resolveElementId(type, idOrName) ?: return false

        return when (type) {
            "wing" -> tameWing(elementId)
            "aura" -> tameAura(elementId)
            "effect" -> tameEffect(elementId)
            "shader" -> tameShader(elementId)
            else -> false
        }
    }

    /**
     * `PlayerAttachedEffects::removeCustomOutfit` (player_attached_effects.cpp:795-841).
     *
     * PARITY NOTE: the C++ name branch checks `type == "wings"` (plural) while
     * the final dispatch checks `type == "wing"` (singular), so a name-based
     * wing removal always falls through to `false`; the port keeps that.
     */
    fun removeCustomOutfit(type: String, idOrName: AttachedEffectSelector): Boolean {
        val elementId = resolveRemovalElementId(type, idOrName) ?: return false

        return when (type) {
            "wing" -> untameWing(elementId)
            "aura" -> untameAura(elementId)
            "effect" -> untameEffect(elementId)
            "shader" -> untameShader(elementId)
            else -> false
        }
    }

    // ---- OTCR features --------------------------------------------------------

    /** `PlayerAttachedEffects::sendAttachedEffect` (player_attached_effects.cpp:844-850). */
    fun sendAttachedEffect(creature: Creature?, effectId: Int) {
        val client = client ?: return
        val target = creature ?: return
        client.sendAttachedEffect(target, effectId)
    }

    /** `PlayerAttachedEffects::sendDetachEffect` (player_attached_effects.cpp:852-858). */
    fun sendDetachEffect(creature: Creature?, effectId: Int) {
        val client = client ?: return
        val target = creature ?: return
        client.sendDetachEffect(target, effectId)
    }

    /** `PlayerAttachedEffects::sendShader` (player_attached_effects.cpp:860-866). */
    fun sendShader(creature: Creature?, shaderName: String) {
        val client = client ?: return
        val target = creature ?: return
        client.sendShader(target, shaderName)
    }

    /** `PlayerAttachedEffects::sendMapShader` (player_attached_effects.cpp:868-874). */
    fun sendMapShader(shaderName: String) {
        val client = client ?: return
        client.sendMapShader(shaderName)
    }

    /** `PlayerAttachedEffects::getMapShader` (player_attached_effects.hpp:87-89). */
    fun getMapShader(): String = mapShader

    /** `PlayerAttachedEffects::setMapShader` (player_attached_effects.hpp:90-92). */
    fun setMapShader(shaderName: String) {
        mapShader = shaderName
    }

    /** `PlayerAttachedEffects::setWasWinged` (player_attached_effects.hpp:94-96). */
    fun setWasWinged(wasWinged: Boolean) {
        this.wasWinged = wasWinged
    }

    /** `PlayerAttachedEffects::setWasAuraed` (player_attached_effects.hpp:98-100). */
    fun setWasAuraed(wasAuraed: Boolean) {
        this.wasAuraed = wasAuraed
    }

    /** `PlayerAttachedEffects::setWasEffected` (player_attached_effects.hpp:102-104). */
    fun setWasEffected(wasEffected: Boolean) {
        this.wasEffected = wasEffected
    }

    /** `PlayerAttachedEffects::setWasShadered` (player_attached_effects.hpp:106-108). */
    fun setWasShadered(wasShadered: Boolean) {
        this.wasShadered = wasShadered
    }

    /** The `idOrName` branch shared by `addCustomOutfit` (player_attached_effects.cpp:748-781). */
    private fun resolveElementId(type: String, idOrName: AttachedEffectSelector): Int? = when (idOrName) {
        is AttachedEffectSelector.Id -> idOrName.id
        is AttachedEffectSelector.Name -> when (type) {
            "wing" -> attachedEffects.getWingByName(idOrName.name)?.id
            "aura" -> attachedEffects.getAuraByName(idOrName.name)?.id
            "effect" -> attachedEffects.getEffectByName(idOrName.name)?.id
            "shader" -> attachedEffects.getShaderByName(idOrName.name)?.id
            else -> null
        }
    }

    /** The `idOrName` branch of `removeCustomOutfit` (player_attached_effects.cpp:796-829). */
    private fun resolveRemovalElementId(type: String, idOrName: AttachedEffectSelector): Int? = when (idOrName) {
        is AttachedEffectSelector.Id -> idOrName.id
        is AttachedEffectSelector.Name -> when (type) {
            "wings" -> attachedEffects.getWingByName(idOrName.name)?.id
            "aura" -> attachedEffects.getAuraByName(idOrName.name)?.id
            "effect" -> attachedEffects.getEffectByName(idOrName.name)?.id
            "shader" -> attachedEffects.getShaderByName(idOrName.name)?.id
            else -> null
        }
    }
}
