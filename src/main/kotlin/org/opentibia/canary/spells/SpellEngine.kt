package org.opentibia.canary.spells

import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.combat.condition.ZoneType
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.utils.Tools
import kotlin.math.abs

/**
 * Cast pipeline of Canary `Spell`/`InstantSpell`/`RuneSpell`
 * (`src/creatures/combat/spells.cpp`).
 *
 * The registry data lives in [Spells]; this engine performs the player-state
 * checks, cooldown application, cost payment and the rune-use charge handling
 * over the injected [SpellCaster]/[SpellWorld]/[SpellScriptRunner] seams.
 *
 * PARITY GAPS (each inherited from an unported subsystem):
 *  - Wheel of Destiny, weapon proficiency and item-augment cooldown/mana
 *    reductions (`spells.cpp:822-885`, `952-977`);
 *  - the Monk harmony builder/spender effects (`spells.cpp:903-918`);
 *  - the `g_game().addMagicEffect(CONST_ME_POFF)` cancel feedback;
 *  - `ExpertPvp` gating of the legacy PZ lock (`spells.cpp:70-76`);
 *  - tile creation/lookup failures in the instant/rune tile checks.
 */
class SpellEngine(
    val registry: Spells,
    val world: SpellWorld,
    val scripts: SpellScriptRunner,
) {

    /**
     * `Player::hasLearnedInstantSpell` (`player.cpp:3132-3145`).
     *
     * The two flags short-circuit the spell-book lookup exactly like Canary.
     */
    fun hasLearnedInstantSpell(caster: SpellCaster, spellName: String): Boolean {
        if (caster.hasFlag(PlayerFlags_t.CannotUseSpells)) {
            return false
        }

        if (caster.hasFlag(PlayerFlags_t.IgnoreSpellCheck)) {
            return true
        }

        return caster.getSpellBook()?.hasLearned(spellName) == true
    }

    /** `Player::learnInstantSpell` (`player.cpp:3122-3126`). */
    fun learnInstantSpell(caster: SpellCaster, spellName: String) {
        if (!hasLearnedInstantSpell(caster, spellName)) {
            caster.getSpellBook()?.learnInstantSpell(spellName)
        }
    }

    /** `Player::forgetInstantSpell` (`player.cpp:3128-3130`). */
    fun forgetInstantSpell(caster: SpellCaster, spellName: String) {
        caster.getSpellBook()?.forgetInstantSpell(spellName)
    }

    /**
     * `InstantSpell::canCast` (`spells.cpp:1419-1443`).
     *
     * Pure castability query used by the Lua/spell-list surfaces: unlike
     * [playerSpellCheck] there is no group/gamemaster bypass, no cancel message
     * and no cooldown/level/mana checks. The `vocSpellMap.contains` check is a
     * key-presence test, so a `false` entry still allows the spell.
     */
    fun canCast(caster: SpellCaster, spell: InstantSpell): Boolean {
        if (caster.hasFlag(PlayerFlags_t.CannotUseSpells)) {
            return false
        }

        if (caster.hasFlag(PlayerFlags_t.IgnoreSpellCheck)) {
            return true
        }

        if (world.learnSpells) {
            return hasLearnedInstantSpell(caster, spell.name)
        }

        if (spell.isLearnable()) {
            if (hasLearnedInstantSpell(caster, spell.name)) {
                return true
            }
        } else if (spell.vocSpellMap.isEmpty() || spell.vocSpellMap.containsKey(caster.getVocationId())) {
            return true
        }

        return false
    }

    /**
     * `Spells::playerSaySpell` (`spells.cpp:82-162`).
     *
     * Resolves the spoken words into an instant spell, extracts the
     * (optionally quoted) parameter and runs [playerCastInstant]. The `words`
     * out-parameter of Canary is returned in [PlayerSaySpellResult].
     *
     * PARITY GAPS: the per-account/outside-PZ message and the feared/Exiva
     * messages are dispatched through the [SpellCaster] text seam; the
     * `g_game().addMagicEffect(CONST_ME_POFF)` feedback of the cast path stays
     * in [playerCastInstant].
     */
    fun playerSaySpell(caster: SpellCaster, words: String): PlayerSaySpellResult {
        val maxOnline = world.maxPlayersPerAccount
        val tile = caster.getTile()
        if (
            maxOnline > 1 &&
            caster.getAccountType().value < ACCOUNT_TYPE_GAMEMASTER &&
            tile != null &&
            !tile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE)
        ) {
            val maxOutsidePz = world.maxPlayersOutsidePzPerAccount
            var countOutsidePz = 0
            for (accountPlayer in world.getPlayersByAccount(caster.getAccount())) {
                if (accountPlayer === caster || accountPlayer.isOffline()) {
                    continue
                }

                val accountTile = accountPlayer.getTile()
                if (accountTile != null && !accountTile.hasFlag(TileFlags.TILESTATE_PROTECTIONZONE)) {
                    ++countOutsidePz
                }
            }

            if (countOutsidePz >= maxOutsidePz) {
                caster.sendTextMessage(
                    MessageClass.MESSAGE_FAILURE,
                    "You cannot cast spells while you have $maxOutsidePz character(s) outside of a protection zone.",
                )
                return PlayerSaySpellResult(TalkActionResult.TALKACTION_FAILED, words)
            }
        }

        if (caster.hasCondition(ConditionType.CONDITION_FEARED)) {
            caster.sendTextMessage(MessageClass.MESSAGE_FAILURE, "You are feared.")
            return PlayerSaySpellResult(TalkActionResult.TALKACTION_FAILED, words)
        }

        val strWords = trimSpaces(words)
        val instantSpell = registry.getInstantSpell(strWords)
            ?: return PlayerSaySpellResult(TalkActionResult.TALKACTION_CONTINUE, words)

        var param = ""
        if (instantSpell.hasParam) {
            val spellLen = instantSpell.words.length
            val paramText = strWords.substring(spellLen)
            if (paramText.isNotEmpty() && paramText[0] == ' ') {
                val loc1 = paramText.indexOf('"', 1)
                if (loc1 != -1) {
                    var loc2 = paramText.indexOf('"', loc1 + 1)
                    if (loc2 == -1) {
                        loc2 = paramText.length
                    } else if (paramText.indexOfLast { it != ' ' } != loc2) {
                        return PlayerSaySpellResult(TalkActionResult.TALKACTION_CONTINUE, words)
                    }

                    param = paramText.substring(loc1 + 1, loc2)
                } else {
                    val quotedText = trimSpaces(paramText)
                    if (quotedText.indexOf(' ') == -1) {
                        param = quotedText
                    } else {
                        return PlayerSaySpellResult(TalkActionResult.TALKACTION_CONTINUE, words)
                    }
                }
            }
        }

        if (instantSpell.name == "Find Person" && !caster.canExiva(param)) {
            caster.sendTextMessage(
                MessageClass.MESSAGE_TRADE,
                "The character you are trying to find with Exiva is currently protected from your spell.",
            )
            return PlayerSaySpellResult(TalkActionResult.TALKACTION_FAILED, words)
        }

        val cast = castInstant(caster, instantSpell, param)
        if (cast.cast) {
            var resultWords = instantSpell.words
            if (instantSpell.hasParam && cast.param.isNotEmpty()) {
                resultWords += " \"" + cast.param + "\""
            }
            return PlayerSaySpellResult(TalkActionResult.TALKACTION_BREAK, resultWords)
        }

        return PlayerSaySpellResult(TalkActionResult.TALKACTION_FAILED, words)
    }

    /**
     * `Spell::playerSpellCheck` (`spells.cpp:494-615`).
     *
     * PARITY GAP: the feared text message and every `CONST_ME_POFF` effect are
     * presentation-only and not dispatched through the seams; the checks
     * themselves are complete.
     */
    fun playerSpellCheck(caster: SpellCaster, spell: Spell): Boolean {
        if (caster.hasFlag(PlayerFlags_t.CannotUseSpells)) {
            return false
        }

        if (caster.hasFlag(PlayerFlags_t.IgnoreSpellCheck)) {
            return true
        }

        if (caster.hasCondition(ConditionType.CONDITION_FEARED)) {
            return false
        }

        if (!spell.enabled) {
            return false
        }

        if (
            spell.aggressive &&
            (spell.range < 1 || (spell.range > 0 && caster.getAttackedCreature() == null)) &&
            caster.getSkull() == Skulls_t.SKULL_BLACK
        ) {
            caster.sendCancelMessage(ReturnValue.NOTPOSSIBLE)
            return false
        }

        if (spell.aggressive && caster.hasCondition(ConditionType.CONDITION_PACIFIED)) {
            caster.sendCancelMessage(ReturnValue.YOUAREEXHAUSTED)
            return false
        }

        if (
            spell.aggressive &&
            !caster.hasFlag(PlayerFlags_t.IgnoreProtectionZone) &&
            caster.getZoneType() == ZoneType.ZONE_PROTECTION
        ) {
            caster.sendCancelMessage(ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE)
            return false
        }

        if (
            caster.hasCondition(ConditionType.CONDITION_SPELLGROUPCOOLDOWN, spell.group.value) ||
            caster.hasCondition(ConditionType.CONDITION_SPELLCOOLDOWN, spell.spellId) ||
            (
                spell.secondaryGroup != SpellGroup.SPELLGROUP_NONE &&
                    caster.hasCondition(
                        ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
                        spell.secondaryGroup.value,
                    )
                )
        ) {
            caster.sendCancelMessage(ReturnValue.YOUAREEXHAUSTED)
            return false
        }

        if (caster.getLevel() < spell.level) {
            caster.sendCancelMessage(ReturnValue.NOTENOUGHLEVEL)
            return false
        }

        if (caster.getMagicLevel() < spell.magicLevel) {
            caster.sendCancelMessage(ReturnValue.NOTENOUGHMAGICLEVEL)
            return false
        }

        if (caster.getMana() < spell.getManaCost(caster) && !caster.hasFlag(PlayerFlags_t.HasInfiniteMana)) {
            caster.sendCancelMessage(ReturnValue.NOTENOUGHMANA)
            return false
        }

        if (caster.getSoul() < spell.soul && !caster.hasFlag(PlayerFlags_t.HasInfiniteSoul)) {
            caster.sendCancelMessage(ReturnValue.NOTENOUGHSOUL)
            return false
        }

        if (world.learnSpells) {
            if (spell.isInstant() && !hasLearnedInstantSpell(caster, spell.name)) {
                caster.sendCancelMessage(ReturnValue.YOUNEEDTOLEARNTHISSPELL)
                return false
            }
        } else {
            if (spell.isInstant() && spell.isLearnable()) {
                if (!hasLearnedInstantSpell(caster, spell.name)) {
                    caster.sendCancelMessage(ReturnValue.YOUNEEDTOLEARNTHISSPELL)
                    return false
                }
            } else if (
                spell.vocSpellMap.isNotEmpty() &&
                !spell.vocSpellMap.containsKey(caster.getVocationId()) &&
                caster.getGroupId() < GROUP_TYPE_GAMEMASTER
            ) {
                caster.sendCancelMessage(ReturnValue.YOURVOCATIONCANNOTUSETHISSPELL)
                return false
            }
        }

        if (spell.needWeapon) {
            when (caster.getWeaponType()) {
                WeaponType_t.WEAPON_FIST,
                WeaponType_t.WEAPON_SWORD,
                WeaponType_t.WEAPON_CLUB,
                WeaponType_t.WEAPON_AXE,
                -> Unit

                else -> {
                    caster.sendCancelMessage(ReturnValue.YOUNEEDAWEAPONTOUSETHISSPELL)
                    return false
                }
            }
        }

        if (spell.premium && !caster.isPremium()) {
            caster.sendCancelMessage(ReturnValue.YOUNEEDPREMIUMACCOUNT)
            return false
        }

        if (world.worldType == WorldType.NO_PVP && !caster.isFirstOnStack()) {
            val instantSpell = registry.getInstantSpell(spell.name)
            if ((instantSpell != null && !instantSpell.casterTargetOrDirection) || !spell.needTarget) {
                caster.sendCancelMessage(ReturnValue.NOTPOSSIBLE)
                return false
            }
        }

        return true
    }

    /** `Spell::playerInstantSpellCheck` (`spells.cpp:617-654`). */
    fun playerInstantSpellCheck(caster: SpellCaster, spell: Spell, toPos: Position): Boolean {
        if (toPos.x == 0xFFFF) {
            return true
        }

        val playerPos = caster.getPosition()
        if (playerPos.z > toPos.z) {
            caster.sendCancelMessage(ReturnValue.FIRSTGOUPSTAIRS)
            return false
        } else if (playerPos.z < toPos.z) {
            caster.sendCancelMessage(ReturnValue.FIRSTGODOWNSTAIRS)
            return false
        }

        if (spell.blockingCreature && world.getBottomVisibleCreatureId(toPos) != null) {
            caster.sendCancelMessage(ReturnValue.NOTENOUGHROOM)
            return false
        }

        if (spell.blockingSolid && world.isTileBlockingSolid(toPos)) {
            caster.sendCancelMessage(ReturnValue.NOTENOUGHROOM)
            return false
        }

        return true
    }

    /**
     * `Spell::playerRuneSpellCheck` (`spells.cpp:656-728`).
     *
     * PARITY GAP: the secure-mode/expert-PvP target gates
     * (`spells.cpp:710-726`) belong to the PvP systems and are not modelled.
     */
    fun playerRuneSpellCheck(caster: SpellCaster, spell: Spell, toPos: Position): Boolean {
        if (!playerSpellCheck(caster, spell)) {
            return false
        }

        if (toPos.x == 0xFFFF) {
            return true
        }

        val playerPos = caster.getPosition()
        if (playerPos.z > toPos.z) {
            caster.sendCancelMessage(ReturnValue.FIRSTGOUPSTAIRS)
            return false
        } else if (playerPos.z < toPos.z) {
            caster.sendCancelMessage(ReturnValue.FIRSTGODOWNSTAIRS)
            return false
        }

        if (spell.range != -1 && !world.canThrowObjectTo(playerPos, toPos, true, spell.range)) {
            caster.sendCancelMessage(ReturnValue.DESTINATIONOUTOFREACH)
            return false
        }

        // spells.cpp:690-694: `Combat::canDoCombat(player, tile, aggressive)`.
        val tileCombat = world.canDoCombat(caster, toPos, spell.aggressive)
        if (tileCombat != ReturnValue.NOERROR) {
            caster.sendCancelMessage(tileCombat)
            return false
        }

        val topVisibleCreature = world.getBottomVisibleCreatureId(toPos)
        if (
            (spell.blockingCreature && topVisibleCreature != null) ||
            (spell.blockingSolid && world.isTileBlockingSolid(toPos) && topVisibleCreature == null)
        ) {
            caster.sendCancelMessage(ReturnValue.NOTENOUGHROOM)
            return false
        }

        if (spell.needTarget && topVisibleCreature == null) {
            caster.sendCancelMessage(ReturnValue.CANONLYUSETHISRUNEONCREATURES)
            return false
        }

        return true
    }

    /**
     * `Spell::applyCooldownConditions` (`spells.cpp:822-885`).
     *
     * The wheel/augment reductions are not modelled (PARITY GAP), so the base
     * cooldowns divided by `RATE_SPELL_COOLDOWN` are applied; the C++ float
     * arithmetic is preserved because the truncation to condition ticks is
     * observable.
     */
    fun applyCooldownConditions(caster: SpellCaster, spell: Spell) {
        var rateCooldown = world.rateSpellCooldown
        if (abs(rateCooldown) < FLOAT_EPSILON) {
            rateCooldown = 0.1
        }
        val rate = rateCooldown.toFloat()

        if (spell.cooldown > 0) {
            val ticks = (spell.cooldown / rate).toInt()
            if (ticks > 0) {
                Condition.createCondition(
                    ConditionId.CONDITIONID_DEFAULT,
                    ConditionType.CONDITION_SPELLCOOLDOWN,
                    ticks,
                    0,
                    false,
                    spell.spellId,
                )?.let { caster.addCondition(it) }
            }
        }

        if (spell.groupCooldown > 0) {
            val ticks = (spell.groupCooldown / rate).toInt()
            if (ticks > 0) {
                Condition.createCondition(
                    ConditionId.CONDITIONID_DEFAULT,
                    ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
                    ticks,
                    0,
                    false,
                    spell.group.value,
                )?.let { caster.addCondition(it) }
            }
        }

        if (spell.secondaryGroupCooldown > 0) {
            val ticks = (spell.secondaryGroupCooldown / rate).toInt()
            if (ticks > 0) {
                Condition.createCondition(
                    ConditionId.CONDITIONID_DEFAULT,
                    ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
                    ticks,
                    0,
                    false,
                    spell.secondaryGroup.value,
                )?.let { caster.addCondition(it) }
            }
        }
    }

    /** `Spell::postCastSpell(player, finishedCast, payCost)` (`spells.cpp:903-933`). */
    fun postCastSpell(
        caster: SpellCaster,
        spell: Spell,
        finishedCast: Boolean = true,
        payCost: Boolean = true,
    ) {
        if (finishedCast) {
            // Monk harmony spender/builder effects (spells.cpp:905-914) are PARITY GAPS.
            if (!caster.hasFlag(PlayerFlags_t.HasNoExhaustion)) {
                applyCooldownConditions(caster, spell)
            }

            if (spell.aggressive) {
                caster.addInFightTicks()
                caster.updateLastAggressiveAction()
            }

            if (spell.soundCastEffect != SoundEffect.SILENCE) {
                world.sendDoubleSoundEffect(
                    caster.getPosition(),
                    spell.soundCastEffect,
                    spell.soundImpactEffect,
                    caster,
                )
            }
        }

        if (payCost) {
            postCastSpell(caster, spell.getManaCost(caster), spell.getSoulCost())
        }
    }

    /** `Spell::postCastSpell(player, manaCost, soulCost)` (`spells.cpp:935-946`). */
    fun postCastSpell(caster: SpellCaster, manaCost: Int, soulCost: Int) {
        if (manaCost > 0) {
            caster.addManaSpent(manaCost)
            caster.changeMana(-manaCost)
        }

        if (!caster.hasFlag(PlayerFlags_t.HasInfiniteSoul) && soulCost > 0) {
            caster.changeSoul(-soulCost)
        }
    }

    /**
     * `InstantSpell::playerCastInstant` (`spells.cpp:1171-1297`).
     *
     * Canary mutates the by-reference `param` when a target player name is
     * resolved; [castInstant] carries that value back for
     * [playerSaySpell]'s words echo.
     *
     * PARITY GAP: a failed wildcard lookup cancels with `NOTPOSSIBLE` instead
     * of the exact `getPlayerByNameWildcard` return value.
     */
    fun playerCastInstant(caster: SpellCaster, spell: InstantSpell, param: String = ""): Boolean =
        castInstant(caster, spell, param).cast

    /**
     * Port of `InstantSpell::playerCastInstant(player, std::string &param)`
     * exposing the resolved `param` out-parameter (spells.cpp:1192-1213).
     */
    private data class InstantCastResult(val cast: Boolean, val param: String)

    private fun castInstant(caster: SpellCaster, spell: InstantSpell, param: String): InstantCastResult {
        if (!playerSpellCheck(caster, spell)) {
            return InstantCastResult(false, param)
        }

        if (caster.hasCondition(ConditionType.CONDITION_POWERLESS) && spell.group == SpellGroup.SPELLGROUP_ATTACK) {
            return InstantCastResult(false, param)
        }

        val variant = LuaVariant()
        variant.instantName = spell.name
        var playerTarget: SpellTarget? = null
        var pzLockTarget: SpellTarget? = null
        var resolvedParam = param

        if (spell.selfTarget) {
            variant.type = LuaVariantType.VARIANT_NUMBER
            variant.number = caster.getID()
        } else if (spell.needTarget || spell.casterTargetOrDirection) {
            var target: SpellTarget? = null
            var useDirection = false

            if (spell.hasParam) {
                playerTarget = world.getPlayerByNameWildcard(resolvedParam)
                if (playerTarget != null && playerTarget.isAccessPlayer() && !caster.isAccessPlayer()) {
                    playerTarget = null
                }

                target = playerTarget
                if (target == null || target.isRemoved() || target.getHealth() <= 0) {
                    if (!spell.casterTargetOrDirection) {
                        applyCooldownConditions(caster, spell)
                        caster.sendCancelMessage(ReturnValue.NOTPOSSIBLE)
                        return InstantCastResult(false, resolvedParam)
                    }
                    useDirection = true
                }

                if (playerTarget != null) {
                    resolvedParam = playerTarget.getName()
                }
            } else {
                target = caster.getAttackedCreature()
                if (target == null || target.isRemoved() || target.getHealth() <= 0) {
                    if (!spell.casterTargetOrDirection) {
                        caster.sendCancelMessage(ReturnValue.YOUCANONLYUSEITONCREATURES)
                        return InstantCastResult(false, resolvedParam)
                    }
                    useDirection = true
                }
            }

            if (!useDirection) {
                if (!canThrowSpell(caster, spell, target!!)) {
                    caster.sendCancelMessage(ReturnValue.CREATUREISNOTREACHABLE)
                    return InstantCastResult(false, resolvedParam)
                }

                variant.type = LuaVariantType.VARIANT_NUMBER
                variant.number = target.getID()
                pzLockTarget = target
            } else {
                variant.type = LuaVariantType.VARIANT_POSITION
                variant.pos = Spells.getCasterPosition(caster, caster.getDirection())

                if (!playerInstantSpellCheck(caster, spell, variant.pos)) {
                    return InstantCastResult(false, resolvedParam)
                }
            }
        } else if (spell.hasParam) {
            variant.type = LuaVariantType.VARIANT_STRING

            if (spell.hasPlayerNameParam) {
                playerTarget = world.getPlayerByNameWildcard(resolvedParam)
                if (playerTarget == null) {
                    applyCooldownConditions(caster, spell)
                    caster.sendCancelMessage(ReturnValue.NOTPOSSIBLE)
                    return InstantCastResult(false, resolvedParam)
                }

                if (!playerTarget.isAccessPlayer() || caster.isAccessPlayer()) {
                    resolvedParam = playerTarget.getName()
                }
            }

            variant.text = resolvedParam
        } else {
            variant.type = LuaVariantType.VARIANT_POSITION
            variant.pos = caster.getPosition()

            if (spell.needDirection) {
                applyInstantSpellDirection(caster, variant, variant.pos, spell.spellId)
            }

            if (!playerInstantSpellCheck(caster, spell, variant.pos)) {
                return InstantCastResult(false, resolvedParam)
            }
        }

        if (!spell.allowOnSelf && playerTarget != null && playerTarget.getName() == caster.getName()) {
            caster.sendCancelMessage(ReturnValue.NOTPOSSIBLE)
            return InstantCastResult(false, resolvedParam)
        }

        if (
            spell.pzLocked &&
            (world.worldType == WorldType.PVP || world.worldType == WorldType.PVP_ENFORCED)
        ) {
            caster.addInFightTicks()
            caster.updateLastAggressiveAction()
        }

        val result = scripts.executeCastSpell(caster, spell, variant)
        if (result) {
            postCastSpell(caster, spell)
        }

        return InstantCastResult(result, resolvedParam)
    }

    /**
     * `RuneSpell::executeUse` (`spells.cpp:1501-1559`).
     *
     * PARITY GAPS: `player->updateSupplyTracker(item)` is protocol-side;
     * `Game::transformItem(item, id, 0)` removal semantics stay with the item
     * owner, so the port clamps `item.count` at zero.
     */
    fun executeUse(
        caster: SpellCaster,
        rune: RuneSpell,
        item: Item?,
        target: SpellTarget?,
        toPosition: Position,
        isHotkey: Boolean,
    ): Boolean {
        if (!playerRuneSpellCheck(caster, rune, toPosition)) {
            return false
        }

        if (rune.scriptId == 0) {
            return false
        }

        if (caster.hasCondition(ConditionType.CONDITION_POWERLESS) && rune.group == SpellGroup.SPELLGROUP_ATTACK) {
            return false
        }

        val variant = LuaVariant()
        variant.runeName = rune.name
        var pzLockTarget: SpellTarget? = null

        if (rune.needTarget) {
            variant.type = LuaVariantType.VARIANT_NUMBER

            if (target == null) {
                val visibleCreature = world.getBottomVisibleCreatureId(toPosition)
                if (visibleCreature != null) {
                    variant.number = visibleCreature
                }
            } else {
                variant.number = target.getID()
                pzLockTarget = target
            }
        } else {
            variant.type = LuaVariantType.VARIANT_POSITION
            variant.pos = toPosition
        }

        if (!scripts.executeCastSpell(caster, rune, variant, isHotkey)) {
            return false
        }

        postCastSpell(caster, rune)

        if (rune.hasCharges && item != null && world.removeRuneCharges) {
            item.count = maxOf(0, item.count - 1)
        }

        if (
            rune.pzLocked &&
            (world.worldType == WorldType.PVP || world.worldType == WorldType.PVP_ENFORCED)
        ) {
            caster.addInFightTicks()
            caster.updateLastAggressiveAction()
        }

        return true
    }

    /**
     * `InstantSpell::canThrowSpell` (`spells.cpp:1299-1310`). The range-less and
     * ranged `Game::canThrowObjectTo` overloads are folded into one seam call
     * with `-1` meaning "no range limit".
     */
    private fun canThrowSpell(caster: SpellCaster, spell: InstantSpell, target: SpellTarget): Boolean {
        val fromPos = caster.getPosition()
        val toPos = target.getPosition()
        if (fromPos.z != toPos.z) {
            return false
        }

        return world.canThrowObjectTo(fromPos, toPos, spell.checkLineOfSight, spell.range)
    }

    /**
     * `applyInstantSpellDirection` (`spells.cpp:47-59`): an attacked creature
     * switches the variant to `VARIANT_TARGETPOSITION` and, when the spell is
     * registered to aim at the target, the facing becomes the primary axis
     * toward it (`getPrimaryDirection`, tools.cpp:2317-2326).
     *
     * PARITY GAP: the wheel state that populates `Player::getAimAtTargetSpells`
     * (`player.cpp:13355-13367`) is not modelled; the [SpellCaster] seam
     * supplies the set.
     */
    private fun applyInstantSpellDirection(
        caster: SpellCaster,
        variant: LuaVariant,
        casterPos: Position,
        spellId: Int,
    ) {
        var direction = caster.getDirection()
        val target = caster.getAttackedCreature()
        if (target != null) {
            variant.type = LuaVariantType.VARIANT_TARGETPOSITION
            if (spellId in caster.getAimAtTargetSpells()) {
                direction = Tools.getPrimaryDirection(casterPos, target.getPosition())
            }
        }

        variant.pos = Spells.getCasterPosition(caster, direction)
    }

    /**
     * `trimString` (`src/utils/tools.cpp:482-485`) trims only the space
     * character from both ends.
     */
    private fun trimSpaces(value: String): String = value.trimEnd(' ').trimStart(' ')

    private companion object {
        /** `GROUP_TYPE_GAMEMASTER` (`src/enums/account_group_type.hpp:21`). */
        const val GROUP_TYPE_GAMEMASTER = 4

        /** `ACCOUNT_TYPE_GAMEMASTER` (`src/enums/account_type.hpp:21`). */
        const val ACCOUNT_TYPE_GAMEMASTER = 4

        /** `std::numeric_limits<float>::epsilon()` (`spells.cpp:827`). */
        const val FLOAT_EPSILON = 1.1920929E-7
    }
}
