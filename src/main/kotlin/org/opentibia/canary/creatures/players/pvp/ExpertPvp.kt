package org.opentibia.canary.creatures.players.pvp

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.players.getPvpMode
import org.opentibia.canary.spells.WorldType

/**
 * `PvpMode_t` (`src/creatures/creatures_definitions.hpp:818-822`).
 *
 * The C++ enum is `uint8_t`-backed and raw client bytes are cast into it
 * (`ExpertPvp::modeFromClientByte`, expert_pvp.cpp:398-400), so the port keeps
 * it as a value class over the raw byte instead of an enum: an unknown mode
 * such as `99` must survive into [ExpertPvp.normalizeMode] and be reported as
 * invalid, exactly like the C++ `static_cast<PvpMode_t>(rawMode)`.
 */
@JvmInline
value class PvpMode_t(val value: Int) {
    companion object {
        val PVP_MODE_DOVE = PvpMode_t(0)
        val PVP_MODE_WHITE_HAND = PvpMode_t(1)
        val PVP_MODE_YELLOW_HAND = PvpMode_t(2)
        val PVP_MODE_RED_FIST = PvpMode_t(3)
    }
}

/**
 * `ExpertPvpModeSource` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:19-25`).
 */
enum class ExpertPvpModeSource(val value: Int) {
    ClientByte(0),
    DefaultForClient(1),
    StoredPlayerState(2),
    CastTimeFieldContext(3),
    LiveOwnerState(4),
}

/**
 * `ExpertPvpDecisionReason` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:55-71`).
 */
enum class ExpertPvpDecisionReason(val value: Int) {
    FeatureDisabled(0),
    NotEvaluated(1),
    CurrentCanaryFallback(2),
    InvalidMode(3),
    MissingPlayer(4),
    MissingFieldContext(5),
    Self(6),
    AccessPlayer(7),
    Neutral(8),
    Ally(9),
    War(10),
    DirectCombat(11),
    SkulledTarget(12),
    Monster(13),
    Npc(14),
}

/**
 * `ExpertPvpSituationMark` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:80-85`).
 */
enum class ExpertPvpSituationMark(val value: Int) {
    None(0),
    Yellow(1),
    Orange(2),
    Brown(3),
}

/**
 * `CreatureMark_t` (`src/utils/utils_definitions.hpp:463-468`); the values are
 * the `TextColor_t` ids the client square packet carries.
 */
enum class CreatureMark_t(val value: Int) {
    CREATURE_MARK_BROWN(78),
    CREATURE_MARK_ORANGE(198),
    CREATURE_MARK_YELLOW(210),
    CREATURE_MARK_UNMARKED(255),
}

/**
 * `ExpertPvpModeResult` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:87-93`).
 */
data class ExpertPvpModeResult(
    val mode: PvpMode_t = PvpMode_t.PVP_MODE_DOVE,
    val source: ExpertPvpModeSource = ExpertPvpModeSource.DefaultForClient,
    val accepted: Boolean = true,
    val normalized: Boolean = false,
    val reason: ExpertPvpDecisionReason = ExpertPvpDecisionReason.CurrentCanaryFallback,
)

/**
 * `ExpertPvpRelation` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:27-42`).
 */
enum class ExpertPvpRelation(val value: Int) {
    Unknown(0),
    Self(1),
    AccessPlayer(2),
    PartyAlly(3),
    GuildAlly(4),
    WarEnemy(5),
    DirectAttacker(6),
    ProtectedAllyAttacker(7),
    DirectTarget(8),
    SkulledTarget(9),
    NeutralPlayer(10),
    Monster(11),
    PlayerSummon(12),
    Npc(13),
}

/**
 * `ExpertPvpRelationContext` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:95-113`).
 */
data class ExpertPvpRelationContext(
    val actorGuid: Long = 0,
    val subjectGuid: Long = 0,
    val actorMode: PvpMode_t = PvpMode_t.PVP_MODE_DOVE,
    val actorIsAccessPlayer: Boolean = false,
    val subjectIsAccessPlayer: Boolean = false,
    val isSelf: Boolean = false,
    val subjectIsPlayer: Boolean = false,
    val subjectIsMonster: Boolean = false,
    val subjectIsPlayerSummon: Boolean = false,
    val subjectIsNpc: Boolean = false,
    val partyAlly: Boolean = false,
    val guildAlly: Boolean = false,
    val warEnemy: Boolean = false,
    val directAttacker: Boolean = false,
    val protectedAllyAttacker: Boolean = false,
    val directTarget: Boolean = false,
    val skulledTarget: Boolean = false,
)

/**
 * `ExpertPvpRelationResult` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:115-118`).
 */
data class ExpertPvpRelationResult(
    val relation: ExpertPvpRelation = ExpertPvpRelation.Unknown,
    val facts: ExpertPvpRelationContext = ExpertPvpRelationContext(),
)

/**
 * `ExpertPvpActionKind` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:44-53`).
 */
enum class ExpertPvpActionKind(val value: Int) {
    DirectAttack(0),
    AreaSpell(1),
    RuneTarget(2),
    FieldDamage(3),
    FieldStep(4),
    PlayerWalkthrough(5),
    PathfindingProbe(6),
    VisualSerialization(7),
}

/**
 * `ExpertPvpSkullAction` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:73-78`).
 */
enum class ExpertPvpSkullAction(val value: Int) {
    None(0),
    Yellow(1),
    White(2),
    Red(3),
}

/**
 * `ExpertPvpDecision` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:131-143`).
 *
 * C++ fills the aggregate field by field; the port keeps the same mutable shape
 * so the decision flow transcribes one-to-one.
 */
data class ExpertPvpDecision(
    var handled: Boolean = false,
    var allowed: Boolean = true,
    var actionKind: ExpertPvpActionKind = ExpertPvpActionKind.DirectAttack,
    var relation: ExpertPvpRelation = ExpertPvpRelation.Unknown,
    var reason: ExpertPvpDecisionReason = ExpertPvpDecisionReason.FeatureDisabled,
    var sideEffectOwnerGuid: Long = 0,
    var skullAction: ExpertPvpSkullAction = ExpertPvpSkullAction.None,
    var appliesPzLock: Boolean = false,
    var startsFight: Boolean = false,
    var sendsSquare: Boolean = false,
    var countsUnjustified: Boolean = false,
)

/**
 * `ExpertPvpWalkthroughDecision` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:145-150`).
 */
data class ExpertPvpWalkthroughDecision(
    var handled: Boolean = false,
    var canWalkThrough: Boolean = false,
    var relation: ExpertPvpRelation = ExpertPvpRelation.Unknown,
    var reason: ExpertPvpDecisionReason = ExpertPvpDecisionReason.FeatureDisabled,
)

/**
 * `ExpertFieldContext` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:152-166`).
 *
 * The C++ `explicit operator bool()` reports `canonicalItemId != 0`; the port
 * keeps [canonicalItemId] as the presence marker and evaluates it explicitly.
 */
data class ExpertFieldContext(
    var ownerGuid: Long = 0,
    var ownerModeSource: ExpertPvpModeSource = ExpertPvpModeSource.CastTimeFieldContext,
    var ownerMode: PvpMode_t = PvpMode_t.PVP_MODE_DOVE,
    var canonicalItemId: Int = 0,
    var safeVisualItemId: Int = 0,
    var blockingVisualItemId: Int = 0,
    var ownerTargetsAtCast: MutableList<Long> = mutableListOf(),
    var ownerAttackersAtCast: MutableList<Long> = mutableListOf(),
    var ownerWasPlayerOrSummon: Boolean = false,
)

/**
 * `ExpertPvpFieldStepDecision` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:168-179`).
 */
data class ExpertPvpFieldStepDecision(
    var handled: Boolean = false,
    var canStep: Boolean = true,
    var removeField: Boolean = false,
    var relation: ExpertPvpRelation = ExpertPvpRelation.Unknown,
    var reason: ExpertPvpDecisionReason = ExpertPvpDecisionReason.FeatureDisabled,
    var sideEffectOwnerGuid: Long = 0,
    var skullAction: ExpertPvpSkullAction = ExpertPvpSkullAction.None,
    var appliesPzLock: Boolean = false,
    var startsFight: Boolean = false,
    var sendsSquare: Boolean = false,
)

/**
 * `ExpertPvpFieldDamageDecision` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:181-188`).
 */
data class ExpertPvpFieldDamageDecision(
    var handled: Boolean = false,
    var applyDamage: Boolean = true,
    var setConditionOwner: Boolean = false,
    var conditionOwnerGuid: Long = 0,
    var relation: ExpertPvpRelation = ExpertPvpRelation.Unknown,
    var reason: ExpertPvpDecisionReason = ExpertPvpDecisionReason.FeatureDisabled,
)

/**
 * `ExpertPvpFieldVisualDecision` (`src/creatures/players/components/pvp/expert_pvp_definitions.hpp:190-195`).
 */
data class ExpertPvpFieldVisualDecision(
    var handled: Boolean = false,
    var clientItemId: Int = 0,
    var relation: ExpertPvpRelation = ExpertPvpRelation.Unknown,
    var reason: ExpertPvpDecisionReason = ExpertPvpDecisionReason.FeatureDisabled,
)

/**
 * Port of the static `ExpertPvp` component
 * (`src/creatures/players/components/pvp/expert_pvp.hpp:24-61`,
 * expert_pvp.cpp:361-1030).
 *
 * The C++ statics read `g_configManager()` and `g_game()`; per AGENTS.md 1.1 the
 * configured world type is an explicit parameter and the registry lookups are
 * injected by the caller, with every method citing the exact C++ symbol it
 * transcribes.
 */
object ExpertPvp {

    /** `expertPvpWorldType` (expert_pvp.cpp:44). */
    const val EXPERT_PVP_WORLD_TYPE = "expert-pvp"

    /** `retroPvpWorldType` (expert_pvp.cpp:46). */
    const val RETRO_PVP_WORLD_TYPE = "retro-pvp"

    /** `legacyRetroPvpWorldType` (expert_pvp.cpp:45). */
    const val LEGACY_RETRO_PVP_WORLD_TYPE = "pvp"

    // Field item ids (`src/utils/utils_definitions.hpp:580-602`).

    const val ITEM_FIREFIELD_PVP_FULL = 2118
    const val ITEM_FIREFIELD_PVP_MEDIUM = 2119
    const val ITEM_FIREFIELD_PVP_SMALL = 2120
    const val ITEM_FIREFIELD_PERSISTENT_FULL = 2123
    const val ITEM_FIREFIELD_PERSISTENT_MEDIUM = 2124
    const val ITEM_FIREFIELD_PERSISTENT_SMALL = 2125
    const val ITEM_FIREFIELD_NOPVP = 21465
    const val ITEM_POISONFIELD_PVP = 105
    const val ITEM_POISONFIELD_PERSISTENT = 2121
    const val ITEM_POISONFIELD_NOPVP = 2134
    const val ITEM_ENERGYFIELD_PVP = 2122
    const val ITEM_ENERGYFIELD_PERSISTENT = 2126
    const val ITEM_ENERGYFIELD_NOPVP = 2135
    const val ITEM_MAGICWALL = 2128
    const val ITEM_MAGICWALL_PERSISTENT = 2129
    const val ITEM_MAGICWALL_SAFE = 10181
    const val ITEM_WILDGROWTH = 2130
    const val ITEM_WILDGROWTH_PERSISTENT = 3635
    const val ITEM_WILDGROWTH_SAFE = 10182
    const val ITEM_GOLD_COIN = 3031

    /**
     * `ExpertPvp::isEnabled()` (expert_pvp.cpp:361-363): true only for the
     * `expert-pvp` configured world type. C++ reads
     * `g_configManager().getString(WORLD_TYPE)`; the port takes the configured
     * world type as a parameter (AGENTS.md 1.1).
     */
    fun isEnabled(worldType: String): Boolean = isExpertPvpWorldType(worldType)

    /** `ExpertPvp::isExpertPvpWorldType()` (expert_pvp.cpp:365-367). */
    fun isExpertPvpWorldType(worldType: String): Boolean = isExpertPvpWorldTypeName(worldType)

    /** `ExpertPvp::isRetroPvpWorldType()` (expert_pvp.cpp:369-371). */
    fun isRetroPvpWorldType(worldType: String): Boolean = isRetroPvpWorldTypeName(worldType)

    /** `ExpertPvp::isExpertPvpWorldTypeName` (expert_pvp.cpp:373-375). */
    fun isExpertPvpWorldTypeName(worldType: String): Boolean =
        worldType.lowercase() == EXPERT_PVP_WORLD_TYPE

    /** `ExpertPvp::isRetroPvpWorldTypeName` (expert_pvp.cpp:377-380). */
    fun isRetroPvpWorldTypeName(worldType: String): Boolean {
        val normalized = worldType.lowercase()
        return normalized == RETRO_PVP_WORLD_TYPE || normalized == LEGACY_RETRO_PVP_WORLD_TYPE
    }

    /** `ExpertPvp::isValidMode` (expert_pvp.cpp:382-392). */
    fun isValidMode(mode: PvpMode_t): Boolean = when (mode) {
        PvpMode_t.PVP_MODE_DOVE,
        PvpMode_t.PVP_MODE_WHITE_HAND,
        PvpMode_t.PVP_MODE_YELLOW_HAND,
        PvpMode_t.PVP_MODE_RED_FIST,
        -> true

        else -> false
    }

    /** `ExpertPvp::defaultModeForClient` (expert_pvp.cpp:394-396). */
    fun defaultModeForClient(): ExpertPvpModeResult =
        makeModeResult(PvpMode_t.PVP_MODE_DOVE, ExpertPvpModeSource.DefaultForClient)

    /** `ExpertPvp::modeFromClientByte` (expert_pvp.cpp:398-400). */
    fun modeFromClientByte(rawMode: Int): ExpertPvpModeResult =
        normalizeMode(PvpMode_t(rawMode), ExpertPvpModeSource.ClientByte)

    /** `ExpertPvp::normalizeMode` (expert_pvp.cpp:402-412). */
    fun normalizeMode(
        requestedMode: PvpMode_t,
        source: ExpertPvpModeSource = ExpertPvpModeSource.StoredPlayerState,
    ): ExpertPvpModeResult {
        if (isValidMode(requestedMode)) {
            return makeModeResult(requestedMode, source)
        }

        return makeModeResult(PvpMode_t.PVP_MODE_DOVE, source).copy(
            accepted = false,
            normalized = true,
            reason = ExpertPvpDecisionReason.InvalidMode,
        )
    }

    /**
     * `ExpertPvp::classifyRelation(context)` (expert_pvp.cpp:414-483). The
     * relation precedence is evaluated exactly in the C++ order.
     */
    fun classifyRelation(context: ExpertPvpRelationContext): ExpertPvpRelationResult {
        val relation = when {
            context.isSelf -> ExpertPvpRelation.Self
            context.actorIsAccessPlayer || context.subjectIsAccessPlayer -> ExpertPvpRelation.AccessPlayer
            context.subjectIsNpc -> ExpertPvpRelation.Npc
            context.partyAlly -> ExpertPvpRelation.PartyAlly
            context.guildAlly -> ExpertPvpRelation.GuildAlly
            context.directAttacker -> ExpertPvpRelation.DirectAttacker
            context.warEnemy -> ExpertPvpRelation.WarEnemy
            context.protectedAllyAttacker -> ExpertPvpRelation.ProtectedAllyAttacker
            context.directTarget -> ExpertPvpRelation.DirectTarget
            context.skulledTarget -> ExpertPvpRelation.SkulledTarget
            context.subjectIsPlayerSummon -> ExpertPvpRelation.PlayerSummon
            context.subjectIsMonster -> ExpertPvpRelation.Monster
            context.subjectIsPlayer -> ExpertPvpRelation.NeutralPlayer
            else -> ExpertPvpRelation.Unknown
        }

        return ExpertPvpRelationResult(relation = relation, facts = context)
    }

    /**
     * `ExpertPvp::classifyRelation(actor, subject)` (expert_pvp.cpp:485-522):
     * builds the relation facts from the live players and re-runs the context
     * classifier. `g_game().getWorldType()` of `isSkulledClientTarget`
     * (expert_pvp.cpp:240-255) is an explicit [worldType] parameter per
     * AGENTS.md 1.1.
     */
    fun classifyRelation(actor: Player?, subject: Creature?, worldType: WorldType): ExpertPvpRelationResult {
        if (actor == null || subject == null) {
            return classifyRelation(ExpertPvpRelationContext())
        }

        val subjectPlayer = subject as? Player
        val subjectMonster = subject as? Monster
        val subjectOwnerPlayer = subjectPlayer ?: getMasterPlayer(subject)

        var partyAlly = false
        var guildAlly = false
        var warEnemy = false
        var directAttacker = false
        var protectedAllyAttacker = false
        var directTarget = false
        var skulledTarget = false
        if (subjectOwnerPlayer != null && subjectOwnerPlayer !== actor) {
            val actorParty = actor.getParty()
            val actorGuild = actor.getGuild()
            val subjectGuild = subjectOwnerPlayer.getGuild()
            partyAlly = actorParty != null && actorParty === subjectOwnerPlayer.getParty()
            guildAlly = actorGuild != null && actorGuild === subjectGuild
            warEnemy = actorGuild != null && subjectGuild != null &&
                actor.pvpPlayer.isInWarList(subjectGuild.id) &&
                subjectOwnerPlayer.pvpPlayer.isInWarList(actorGuild.id)
            directAttacker = subjectOwnerPlayer.pvpPlayer.hasAttacked(actor.pvpPlayer)
            protectedAllyAttacker = hasAttackedProtectedAlly(actor, subjectOwnerPlayer)
            directTarget = !actor.hasFlag(PlayerFlags_t.NotGainInFight) &&
                actor.pvpPlayer.getAttackedPlayerGuids().contains(subjectOwnerPlayer.getGUID())
            skulledTarget = isSkulledClientTarget(actor, subjectOwnerPlayer, worldType)
        }

        val context = ExpertPvpRelationContext(
            actorGuid = actor.getGUID(),
            subjectGuid = subjectOwnerPlayer?.getGUID() ?: 0L,
            actorMode = actor.getPvpMode(),
            actorIsAccessPlayer = isAccessPlayer(actor),
            subjectIsAccessPlayer = isAccessPlayer(subjectOwnerPlayer),
            isSelf = subjectOwnerPlayer === actor,
            subjectIsPlayer = subjectPlayer != null,
            subjectIsMonster = subjectMonster != null,
            subjectIsPlayerSummon = subjectPlayer == null && subject.isSummon() && subjectOwnerPlayer != null,
            subjectIsNpc = subject is Npc,
            partyAlly = partyAlly,
            guildAlly = guildAlly,
            warEnemy = warEnemy,
            directAttacker = directAttacker,
            protectedAllyAttacker = protectedAllyAttacker,
            directTarget = directTarget,
            skulledTarget = skulledTarget,
        )

        return classifyRelation(context)
    }

    /** File-local `isAccessPlayer` (expert_pvp.cpp:52-59). */
    private fun isAccessPlayer(player: Player?): Boolean = player != null && player.group?.access == true

    /** File-local `getMasterPlayer` (expert_pvp.cpp:61-68). */
    private fun getMasterPlayer(creature: Creature): Player? = creature.getMaster() as? Player

    /**
     * File-local `isSkulledClientTarget` (expert_pvp.cpp:240-255): only the PvP
     * world type classifies a skulled/killed/attacked subject.
     */
    private fun isSkulledClientTarget(actor: Player, subjectPlayer: Player, worldType: WorldType): Boolean {
        if (worldType != WorldType.PVP || actor === subjectPlayer) {
            return false
        }

        val skull = subjectPlayer.getSkull()
        if (skull != Skulls_t.SKULL_NONE) {
            return skull != Skulls_t.SKULL_GREEN
        }

        return subjectPlayer.pvpPlayer.hasKilled(actor.pvpPlayer) ||
            subjectPlayer.pvpPlayer.hasAttacked(actor.pvpPlayer)
    }

    /** File-local `hasAttackedProtectedAlly` (expert_pvp.cpp:257-278). */
    private fun hasAttackedProtectedAlly(actor: Player, subjectPlayer: Player): Boolean {
        if (actor === subjectPlayer) {
            return false
        }

        val party = actor.getParty()
        if (party != null) {
            for (partyPlayer in party.getPlayers()) {
                if (partyPlayer !== actor && partyPlayer !== subjectPlayer &&
                    subjectPlayer.pvpPlayer.hasAttacked(partyPlayer.pvpPlayer)
                ) {
                    return true
                }
            }
        }

        val guild = actor.getGuild()
        if (guild != null) {
            for (guildPlayer in guild.getMembersOnline()) {
                if (guildPlayer !== actor && guildPlayer !== subjectPlayer &&
                    subjectPlayer.pvpPlayer.hasAttacked(guildPlayer.pvpPlayer)
                ) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * `ExpertPvp::evaluateCombatAction(actionKind, context)`
     * (expert_pvp.cpp:560-572). C++ gates on `ExpertPvp::isEnabled()` (the
     * configured `expert-pvp` world type); the port takes that gate as an
     * explicit parameter (AGENTS.md 1.1).
     */
    fun evaluateCombatAction(
        actionKind: ExpertPvpActionKind,
        relationContext: ExpertPvpRelationContext,
        enabled: Boolean,
    ): ExpertPvpDecision {
        if (!enabled) {
            val relation = classifyRelation(relationContext)
            return ExpertPvpDecision(
                actionKind = actionKind,
                relation = relation.relation,
                reason = ExpertPvpDecisionReason.FeatureDisabled,
            )
        }

        return evaluateCombatAction(relationContext.actorMode, actionKind, relationContext)
    }

    /**
     * `ExpertPvp::evaluateCombatAction(actorMode, actionKind, context)`
     * (expert_pvp.cpp:574-657). The decision is `handled` for every evaluated
     * relation and additionally `allowed` for war enemies, direct attackers,
     * the mode-dependent protected/direct/skulled targets and red-fist neutral
     * players.
     */
    fun evaluateCombatAction(
        actorMode: PvpMode_t,
        actionKind: ExpertPvpActionKind,
        relationContext: ExpertPvpRelationContext,
    ): ExpertPvpDecision {
        val mode = normalizeMode(actorMode, ExpertPvpModeSource.StoredPlayerState)
        val relation = classifyRelation(relationContext)

        val decision = ExpertPvpDecision(
            handled = true,
            allowed = false,
            actionKind = actionKind,
            relation = relation.relation,
        )

        if (!mode.accepted) {
            decision.reason = ExpertPvpDecisionReason.InvalidMode
            return decision
        }

        if (relation.relation == ExpertPvpRelation.Unknown) {
            decision.reason = ExpertPvpDecisionReason.MissingPlayer
            return decision
        }

        if (isAlwaysAllowedRelation(relation.relation)) {
            decision.allowed = true
            decision.reason = reasonForAllowedRelation(relation.relation)
            return decision
        }

        if (relation.relation == ExpertPvpRelation.PartyAlly || relation.relation == ExpertPvpRelation.GuildAlly) {
            decision.reason = ExpertPvpDecisionReason.Ally
            return decision
        }

        if (relation.relation == ExpertPvpRelation.WarEnemy) {
            decision.allowed = true
            decision.reason = ExpertPvpDecisionReason.War
            describePvpPressure(decision, relationContext)
            decision.appliesPzLock = false
            return decision
        }

        if (relation.relation == ExpertPvpRelation.DirectAttacker) {
            decision.allowed = true
            decision.reason = ExpertPvpDecisionReason.DirectCombat
            describePvpPressure(decision, relationContext)
            decision.appliesPzLock = false
            return decision
        }

        if (relation.relation == ExpertPvpRelation.ProtectedAllyAttacker) {
            decision.reason = ExpertPvpDecisionReason.DirectCombat
            if (mode.mode == PvpMode_t.PVP_MODE_WHITE_HAND ||
                mode.mode == PvpMode_t.PVP_MODE_RED_FIST ||
                (mode.mode == PvpMode_t.PVP_MODE_YELLOW_HAND && relationContext.skulledTarget)
            ) {
                decision.allowed = true
                describePvpPressure(decision, relationContext)
                decision.appliesPzLock = false
            }
            return decision
        }

        if (relation.relation == ExpertPvpRelation.DirectTarget) {
            decision.reason = ExpertPvpDecisionReason.DirectCombat
            if (mode.mode == PvpMode_t.PVP_MODE_YELLOW_HAND || mode.mode == PvpMode_t.PVP_MODE_RED_FIST) {
                decision.allowed = true
                describePvpPressure(decision, relationContext)
            }
            return decision
        }

        if (relation.relation == ExpertPvpRelation.SkulledTarget) {
            decision.reason = ExpertPvpDecisionReason.SkulledTarget
            if (mode.mode == PvpMode_t.PVP_MODE_YELLOW_HAND || mode.mode == PvpMode_t.PVP_MODE_RED_FIST) {
                decision.allowed = true
                describePvpPressure(decision, relationContext)
            }
            return decision
        }

        decision.reason = ExpertPvpDecisionReason.Neutral
        if (mode.mode == PvpMode_t.PVP_MODE_RED_FIST) {
            decision.allowed = true
            decision.skullAction = ExpertPvpSkullAction.White
            decision.countsUnjustified = true
            describePvpPressure(decision, relationContext)
        }
        return decision
    }

    /** `isAlwaysAllowedRelation` (expert_pvp.cpp:280-290). */
    private fun isAlwaysAllowedRelation(relation: ExpertPvpRelation): Boolean = when (relation) {
        ExpertPvpRelation.Self,
        ExpertPvpRelation.AccessPlayer,
        ExpertPvpRelation.Monster,
        ExpertPvpRelation.Npc,
        -> true

        else -> false
    }

    /** `reasonForAllowedRelation` (expert_pvp.cpp:292-305). */
    private fun reasonForAllowedRelation(relation: ExpertPvpRelation): ExpertPvpDecisionReason = when (relation) {
        ExpertPvpRelation.Self -> ExpertPvpDecisionReason.Self
        ExpertPvpRelation.AccessPlayer -> ExpertPvpDecisionReason.AccessPlayer
        ExpertPvpRelation.Monster -> ExpertPvpDecisionReason.Monster
        ExpertPvpRelation.Npc -> ExpertPvpDecisionReason.Npc
        else -> ExpertPvpDecisionReason.Neutral
    }

    /** `describePvpPressure` (expert_pvp.cpp:307-311). */
    private fun describePvpPressure(decision: ExpertPvpDecision, relationContext: ExpertPvpRelationContext) {
        decision.sideEffectOwnerGuid = relationContext.actorGuid
        decision.startsFight = true
        decision.appliesPzLock = true
    }

    /**
     * `ExpertPvp::canWalkThrough(context)` (expert_pvp.cpp:659-670). C++ gates
     * on `ExpertPvp::isEnabled()`; the port takes that gate as an explicit
     * parameter (AGENTS.md 1.1).
     */
    fun canWalkThrough(
        relationContext: ExpertPvpRelationContext,
        enabled: Boolean,
    ): ExpertPvpWalkthroughDecision {
        if (!enabled) {
            val relation = classifyRelation(relationContext)
            return ExpertPvpWalkthroughDecision(
                relation = relation.relation,
                reason = ExpertPvpDecisionReason.FeatureDisabled,
            )
        }

        return evaluateWalkthrough(relationContext)
    }

    /**
     * `ExpertPvp::evaluateWalkthrough` (expert_pvp.cpp:672-727). Self, access
     * players and allies always pass; every other relation is blocked per mode
     * with war enemies blocking in every mode.
     */
    fun evaluateWalkthrough(relationContext: ExpertPvpRelationContext): ExpertPvpWalkthroughDecision {
        val relation = classifyRelation(relationContext)

        val decision = ExpertPvpWalkthroughDecision(
            handled = true,
            relation = relation.relation,
            reason = ExpertPvpDecisionReason.Neutral,
        )

        if (relation.relation == ExpertPvpRelation.Self || relation.relation == ExpertPvpRelation.AccessPlayer) {
            decision.canWalkThrough = true
            decision.reason = reasonForAllowedRelation(relation.relation)
            return decision
        }

        if (relation.relation == ExpertPvpRelation.PartyAlly || relation.relation == ExpertPvpRelation.GuildAlly) {
            decision.canWalkThrough = true
            decision.reason = ExpertPvpDecisionReason.Ally
            return decision
        }

        val mode = normalizeMode(relationContext.actorMode, ExpertPvpModeSource.StoredPlayerState)
        if (!mode.accepted) {
            decision.reason = ExpertPvpDecisionReason.InvalidMode
            return decision
        }

        var blocks = relationContext.warEnemy
        when (mode.mode) {
            PvpMode_t.PVP_MODE_DOVE -> blocks = blocks || relationContext.directAttacker
            PvpMode_t.PVP_MODE_WHITE_HAND -> blocks = blocks || relationContext.directAttacker || relationContext.protectedAllyAttacker
            PvpMode_t.PVP_MODE_YELLOW_HAND -> blocks = blocks || relationContext.directAttacker || relationContext.skulledTarget
            PvpMode_t.PVP_MODE_RED_FIST -> blocks = blocks || relationContext.subjectIsPlayer || relationContext.subjectIsPlayerSummon
            else -> {
                decision.reason = ExpertPvpDecisionReason.InvalidMode
                return decision
            }
        }

        decision.canWalkThrough = !blocks
        when {
            relationContext.warEnemy -> decision.reason = ExpertPvpDecisionReason.War
            relationContext.directAttacker || relationContext.protectedAllyAttacker -> decision.reason = ExpertPvpDecisionReason.DirectCombat
            relationContext.skulledTarget -> decision.reason = ExpertPvpDecisionReason.SkulledTarget
        }

        return decision
    }

    /** `makeModeResult` helper (expert_pvp.cpp:313-319). */
    private fun makeModeResult(mode: PvpMode_t, source: ExpertPvpModeSource): ExpertPvpModeResult =
        ExpertPvpModeResult(
            mode = mode,
            source = source,
            reason = ExpertPvpDecisionReason.CurrentCanaryFallback,
        )

    /**
     * `ExpertPvp::evaluateFieldStep` (expert_pvp.cpp:729-807). Only magic wall
     * and wild growth carry the step semantics; the owner and their direct
     * targets keep the right of way while aggressors are blocked by mode.
     */
    fun evaluateFieldStep(
        fieldContext: ExpertFieldContext,
        relationContext: ExpertPvpRelationContext,
    ): ExpertPvpFieldStepDecision {
        val relation = classifyRelation(relationContext)

        val decision = ExpertPvpFieldStepDecision(relation = relation.relation)
        if (fieldContext.canonicalItemId == 0 ||
            fieldContext.ownerGuid == 0L ||
            !fieldContext.ownerWasPlayerOrSummon ||
            !isMagicWallOrWildGrowthField(fieldContext)
        ) {
            decision.reason = ExpertPvpDecisionReason.MissingFieldContext
            return decision
        }

        decision.handled = true
        decision.reason = ExpertPvpDecisionReason.Neutral

        val mode = normalizeMode(fieldContext.ownerMode, fieldContext.ownerModeSource)
        if (!mode.accepted) {
            decision.canStep = false
            decision.reason = ExpertPvpDecisionReason.InvalidMode
            return decision
        }

        if (relation.relation == ExpertPvpRelation.Self) {
            decision.canStep = false
            decision.reason = ExpertPvpDecisionReason.Self
            return decision
        }

        if (relation.relation == ExpertPvpRelation.AccessPlayer) {
            decision.reason = ExpertPvpDecisionReason.AccessPlayer
            return decision
        }

        if (relation.relation == ExpertPvpRelation.PartyAlly || relation.relation == ExpertPvpRelation.GuildAlly) {
            decision.reason = ExpertPvpDecisionReason.Ally
            return decision
        }

        if (relationContext.directTarget && !relationContext.directAttacker) {
            decision.reason = ExpertPvpDecisionReason.DirectCombat
            return decision
        }

        var blocks = relationContext.warEnemy
        when (mode.mode) {
            PvpMode_t.PVP_MODE_DOVE -> blocks = blocks || relationContext.directAttacker
            PvpMode_t.PVP_MODE_WHITE_HAND -> blocks = blocks || relationContext.directAttacker || relationContext.protectedAllyAttacker
            PvpMode_t.PVP_MODE_YELLOW_HAND -> blocks = blocks || relationContext.directAttacker || relationContext.skulledTarget
            PvpMode_t.PVP_MODE_RED_FIST -> blocks = blocks ||
                relationContext.directAttacker ||
                relationContext.protectedAllyAttacker ||
                relationContext.skulledTarget ||
                relationContext.subjectIsPlayer ||
                relationContext.subjectIsPlayerSummon
            else -> {
                decision.canStep = false
                decision.reason = ExpertPvpDecisionReason.InvalidMode
                return decision
            }
        }

        decision.canStep = !blocks
        when {
            relationContext.warEnemy -> decision.reason = ExpertPvpDecisionReason.War
            relationContext.directAttacker || relationContext.protectedAllyAttacker || relationContext.directTarget ->
                decision.reason = ExpertPvpDecisionReason.DirectCombat

            relationContext.skulledTarget -> decision.reason = ExpertPvpDecisionReason.SkulledTarget
        }

        val unjustifiedRedBlock = blocks &&
            mode.mode == PvpMode_t.PVP_MODE_RED_FIST &&
            !relationContext.directAttacker &&
            !relationContext.protectedAllyAttacker &&
            !relationContext.warEnemy &&
            !relationContext.skulledTarget
        if (unjustifiedRedBlock) {
            decision.sideEffectOwnerGuid = fieldContext.ownerGuid
            decision.skullAction = ExpertPvpSkullAction.White
            decision.appliesPzLock = true
        }

        return decision
    }

    /**
     * `ExpertPvp::evaluateFieldDamage` (expert_pvp.cpp:809-828). The damage is
     * applied exactly when the owner-mode combat decision allows the field
     * action, and then the condition keeps the owner as its source.
     */
    fun evaluateFieldDamage(
        fieldContext: ExpertFieldContext,
        relationContext: ExpertPvpRelationContext,
    ): ExpertPvpFieldDamageDecision {
        val decision = ExpertPvpFieldDamageDecision()
        if (fieldContext.canonicalItemId == 0 ||
            fieldContext.ownerGuid == 0L ||
            !fieldContext.ownerWasPlayerOrSummon
        ) {
            decision.reason = ExpertPvpDecisionReason.MissingFieldContext
            return decision
        }

        val combatDecision = evaluateCombatAction(
            fieldContext.ownerMode,
            ExpertPvpActionKind.FieldDamage,
            relationContext,
        )
        decision.handled = true
        decision.applyDamage = combatDecision.allowed
        decision.relation = combatDecision.relation
        decision.reason = combatDecision.reason

        if (decision.applyDamage) {
            decision.setConditionOwner = true
            decision.conditionOwnerGuid = fieldContext.ownerGuid
        }

        return decision
    }

    /**
     * `ExpertPvp::getFieldClientId` (expert_pvp.cpp:830-848). Magic wall and
     * wild growth serialize the safe or blocking visual depending on the step
     * decision; every other field keeps its canonical id.
     */
    fun getFieldClientId(
        fieldContext: ExpertFieldContext,
        relationContext: ExpertPvpRelationContext,
    ): ExpertPvpFieldVisualDecision {
        val decision = ExpertPvpFieldVisualDecision(clientItemId = fieldContext.canonicalItemId)
        if (fieldContext.canonicalItemId == 0 || !isMagicWallOrWildGrowthField(fieldContext)) {
            decision.reason = ExpertPvpDecisionReason.MissingFieldContext
            return decision
        }

        val stepDecision = evaluateFieldStep(fieldContext, relationContext)
        decision.handled = stepDecision.handled
        decision.relation = stepDecision.relation
        decision.reason = stepDecision.reason

        if (stepDecision.handled) {
            decision.clientItemId = if (stepDecision.canStep) {
                fieldContext.safeVisualItemId
            } else {
                fieldContext.blockingVisualItemId
            }
        }

        return decision
    }

    /** `isMagicWallOrWildGrowthField` (expert_pvp.cpp:356-358). */
    private fun isMagicWallOrWildGrowthField(fieldContext: ExpertFieldContext): Boolean =
        fieldContext.canonicalItemId == ITEM_MAGICWALL || fieldContext.canonicalItemId == ITEM_WILDGROWTH

    /** `ExpertPvp::isExpertFieldItem` (expert_pvp.cpp:930-955). */
    fun isExpertFieldItem(itemId: Int): Boolean = when (itemId) {
        ITEM_FIREFIELD_PVP_FULL,
        ITEM_FIREFIELD_PVP_MEDIUM,
        ITEM_FIREFIELD_PVP_SMALL,
        ITEM_FIREFIELD_PERSISTENT_FULL,
        ITEM_FIREFIELD_PERSISTENT_MEDIUM,
        ITEM_FIREFIELD_PERSISTENT_SMALL,
        ITEM_FIREFIELD_NOPVP,
        ITEM_POISONFIELD_PVP,
        ITEM_POISONFIELD_PERSISTENT,
        ITEM_POISONFIELD_NOPVP,
        ITEM_ENERGYFIELD_PVP,
        ITEM_ENERGYFIELD_PERSISTENT,
        ITEM_ENERGYFIELD_NOPVP,
        ITEM_MAGICWALL,
        ITEM_MAGICWALL_PERSISTENT,
        ITEM_MAGICWALL_SAFE,
        ITEM_WILDGROWTH,
        ITEM_WILDGROWTH_PERSISTENT,
        ITEM_WILDGROWTH_SAFE,
        -> true

        else -> false
    }

    /**
     * `ExpertPvp::makeFieldContext` (expert_pvp.cpp:957-1001). The owner mode is
     * normalized through the `CastTimeFieldContext` source before the item id
     * resolves the canonical and visual ids.
     */
    fun makeFieldContext(
        ownerGuid: Long,
        ownerMode: PvpMode_t,
        itemId: Int,
        ownerWasPlayerOrSummon: Boolean,
    ): ExpertFieldContext {
        val context = ExpertFieldContext(
            ownerGuid = ownerGuid,
            ownerModeSource = ExpertPvpModeSource.CastTimeFieldContext,
            ownerMode = normalizeMode(ownerMode, ExpertPvpModeSource.CastTimeFieldContext).mode,
            ownerWasPlayerOrSummon = ownerWasPlayerOrSummon,
        )

        when (itemId) {
            ITEM_FIREFIELD_PVP_FULL,
            ITEM_FIREFIELD_PVP_MEDIUM,
            ITEM_FIREFIELD_PVP_SMALL,
            ITEM_FIREFIELD_PERSISTENT_FULL,
            ITEM_FIREFIELD_PERSISTENT_MEDIUM,
            ITEM_FIREFIELD_PERSISTENT_SMALL,
            ITEM_FIREFIELD_NOPVP,
            ITEM_POISONFIELD_PVP,
            ITEM_POISONFIELD_PERSISTENT,
            ITEM_POISONFIELD_NOPVP,
            ITEM_ENERGYFIELD_PVP,
            ITEM_ENERGYFIELD_PERSISTENT,
            ITEM_ENERGYFIELD_NOPVP,
            -> {
                context.canonicalItemId = itemId
                context.safeVisualItemId = itemId
                context.blockingVisualItemId = itemId
            }

            ITEM_MAGICWALL, ITEM_MAGICWALL_PERSISTENT, ITEM_MAGICWALL_SAFE -> {
                context.canonicalItemId = ITEM_MAGICWALL
                context.safeVisualItemId = ITEM_MAGICWALL_SAFE
                context.blockingVisualItemId = ITEM_MAGICWALL
            }

            ITEM_WILDGROWTH, ITEM_WILDGROWTH_PERSISTENT, ITEM_WILDGROWTH_SAFE -> {
                context.canonicalItemId = ITEM_WILDGROWTH
                context.safeVisualItemId = ITEM_WILDGROWTH_SAFE
                context.blockingVisualItemId = ITEM_WILDGROWTH
            }

            else -> Unit
        }

        return context
    }

    // --- situation marks ------------------------------------------------------------

    /**
     * `ExpertPvp::getSituationMark` (expert_pvp.cpp:850-868). C++ gates on
     * `ExpertPvp::isEnabled()`; the port takes that gate as an explicit
     * parameter (AGENTS.md 1.1).
     */
    fun getSituationMark(
        subject: PlayerPvp,
        viewer: PlayerPvp,
        enabled: Boolean,
    ): ExpertPvpSituationMark {
        if (!enabled || subject.player === viewer.player) {
            return ExpertPvpSituationMark.None
        }

        if (hasPvpSituationBetween(subject, viewer)) {
            return ExpertPvpSituationMark.Yellow
        }

        if (hasAllyInSituationWithSubject(viewer, subject)) {
            return ExpertPvpSituationMark.Orange
        }

        if (hasAnyPvpSituation(subject)) {
            return ExpertPvpSituationMark.Brown
        }

        return ExpertPvpSituationMark.None
    }

    /** `ExpertPvp::getSituationCreatureMark` (expert_pvp.cpp:870-872). */
    fun getSituationCreatureMark(
        subject: PlayerPvp,
        viewer: PlayerPvp,
        enabled: Boolean,
    ): CreatureMark_t = toCreatureMark(getSituationMark(subject, viewer, enabled))

    /** `ExpertPvp::refreshCreatureMarkForViewer` (expert_pvp.cpp:874-880). */
    fun refreshCreatureMarkForViewer(viewer: PlayerPvp, subject: PlayerPvp, enabled: Boolean) {
        viewer.sendCreatureMark(subject.player, getSituationCreatureMark(subject, viewer, enabled))
    }

    /**
     * `ExpertPvp::refreshVisibleSituationMarks` (expert_pvp.cpp:882-895).
     * [visiblePlayers] replaces `Spectators().find<Player>(center->getPosition(), true)`
     * (expert_pvp.cpp:192-203), which the map layer does not port here.
     */
    fun refreshVisibleSituationMarks(
        first: PlayerPvp,
        second: PlayerPvp,
        enabled: Boolean,
        visiblePlayers: (PlayerPvp) -> List<PlayerPvp> = { emptyList() },
    ) {
        if (!enabled) {
            return
        }

        val viewers = linkedSetOf<PlayerPvp>()
        addVisiblePlayers(viewers, first, visiblePlayers)
        addVisiblePlayers(viewers, second, visiblePlayers)

        for (viewer in viewers) {
            refreshCreatureMarkForViewer(viewer, first, enabled)
            refreshCreatureMarkForViewer(viewer, second, enabled)
        }
    }

    /**
     * `ExpertPvp::refreshAllVisibleSituationMarks` (expert_pvp.cpp:897-912).
     * [allPlayers] replaces `g_game().getPlayers()` and [visiblePlayers] the
     * per-viewer spectator query.
     */
    fun refreshAllVisibleSituationMarks(
        enabled: Boolean,
        allPlayers: () -> Collection<PlayerPvp> = { emptyList() },
        visiblePlayers: (PlayerPvp) -> List<PlayerPvp> = { emptyList() },
    ) {
        if (!enabled) {
            return
        }

        for (viewer in allPlayers()) {
            for (subject in visiblePlayers(viewer)) {
                refreshCreatureMarkForViewer(viewer, subject, enabled)
            }
        }
    }

    // --- side effects ---------------------------------------------------------------

    /**
     * `ExpertPvp::applyCombatSideEffects` (expert_pvp.cpp:914-920).
     * [playerByGuid] replaces `g_game().getPlayerByGUID` (expert_pvp.cpp:330-331)
     * and [visiblePlayers] the spectator query used by the refreshed marks.
     */
    fun applyCombatSideEffects(
        decision: ExpertPvpDecision,
        relationContext: ExpertPvpRelationContext,
        enabled: Boolean,
        playerByGuid: (Long) -> PlayerPvp?,
        visiblePlayers: (PlayerPvp) -> List<PlayerPvp> = { emptyList() },
    ) {
        if (!enabled || !decision.handled || !decision.allowed || !decision.startsFight || decision.sideEffectOwnerGuid == 0L) {
            return
        }

        applyPlayerSituationSideEffects(
            decision.sideEffectOwnerGuid,
            relationContext.subjectGuid,
            decision.skullAction,
            decision.appliesPzLock,
            enabled,
            playerByGuid,
            visiblePlayers,
        )
    }

    /**
     * `ExpertPvp::applyFieldStepSideEffects` (expert_pvp.cpp:922-928).
     */
    fun applyFieldStepSideEffects(
        decision: ExpertPvpFieldStepDecision,
        relationContext: ExpertPvpRelationContext,
        enabled: Boolean,
        playerByGuid: (Long) -> PlayerPvp?,
        visiblePlayers: (PlayerPvp) -> List<PlayerPvp> = { emptyList() },
    ) {
        if (!enabled || !decision.handled || decision.canStep || decision.sideEffectOwnerGuid == 0L) {
            return
        }

        applyPlayerSituationSideEffects(
            decision.sideEffectOwnerGuid,
            relationContext.subjectGuid,
            decision.skullAction,
            decision.appliesPzLock,
            enabled,
            playerByGuid,
            visiblePlayers,
        )
    }

    /** `applyPlayerSituationSideEffects` (expert_pvp.cpp:325-354). */
    private fun applyPlayerSituationSideEffects(
        ownerGuid: Long,
        subjectGuid: Long,
        skullAction: ExpertPvpSkullAction,
        appliesPzLock: Boolean,
        enabled: Boolean,
        playerByGuid: (Long) -> PlayerPvp?,
        visiblePlayers: (PlayerPvp) -> List<PlayerPvp>,
    ) {
        if (ownerGuid == 0L || subjectGuid == 0L || ownerGuid == subjectGuid) {
            return
        }

        val owner = playerByGuid(ownerGuid) ?: return
        val subject = playerByGuid(subjectGuid) ?: return

        val ownerAlreadyAttacked = owner.hasAttacked(subject)
        if (!owner.isInWar(subject)) {
            owner.addAttacked(subject)
        }

        if (appliesPzLock) {
            owner.addPzLockTicks()
        }

        if (skullAction == ExpertPvpSkullAction.White &&
            subject.player.getSkull() == Skulls_t.SKULL_NONE &&
            owner.player.getSkull() == Skulls_t.SKULL_NONE &&
            !subject.hasKilled(owner)
        ) {
            owner.player.setSkull(Skulls_t.SKULL_WHITE)
        }

        owner.sendOpenPvpSituations()
        subject.sendCreatureSkull(owner.player)
        if (!ownerAlreadyAttacked || skullAction != ExpertPvpSkullAction.None) {
            refreshVisibleSituationMarks(owner, subject, enabled, visiblePlayers)
        }
    }

    /** `hasPvpSituationBetween` (expert_pvp.cpp:146-148). */
    private fun hasPvpSituationBetween(left: PlayerPvp, right: PlayerPvp): Boolean =
        left.player !== right.player && (left.hasAttacked(right) || right.hasAttacked(left))

    /** `isViewerAlly` (expert_pvp.cpp:150-152). */
    private fun isViewerAlly(viewer: PlayerPvp, player: PlayerPvp): Boolean =
        viewer.player !== player.player && (viewer.isPartner(player) || viewer.isGuildMate(player))

    /** `hasPvpOpponentMatching` (expert_pvp.cpp:154-165). */
    private fun hasPvpOpponentMatching(subject: PlayerPvp, predicate: (PlayerPvp) -> Boolean): Boolean {
        val matches = { guid: Long ->
            val opponent = subject.playerByGuid(guid)
            opponent != null && opponent.player !== subject.player && predicate(opponent)
        }
        return subject.getAttackedPlayerGuids().any(matches) || subject.getAttackerPlayerGuids().any(matches)
    }

    /** `hasAllyInSituationWithSubject` (expert_pvp.cpp:167-171). */
    private fun hasAllyInSituationWithSubject(viewer: PlayerPvp, subject: PlayerPvp): Boolean =
        hasPvpOpponentMatching(subject) { opponent -> isViewerAlly(viewer, opponent) }

    /** `hasAnyPvpSituation` (expert_pvp.cpp:173-177). */
    private fun hasAnyPvpSituation(subject: PlayerPvp): Boolean =
        hasPvpOpponentMatching(subject) { true }

    /** `toCreatureMark` (expert_pvp.cpp:179-190). */
    private fun toCreatureMark(mark: ExpertPvpSituationMark): CreatureMark_t = when (mark) {
        ExpertPvpSituationMark.Yellow -> CreatureMark_t.CREATURE_MARK_YELLOW
        ExpertPvpSituationMark.Orange -> CreatureMark_t.CREATURE_MARK_ORANGE
        ExpertPvpSituationMark.Brown -> CreatureMark_t.CREATURE_MARK_BROWN
        ExpertPvpSituationMark.None -> CreatureMark_t.CREATURE_MARK_UNMARKED
    }

    /** `addVisiblePlayers` (expert_pvp.cpp:192-203). */
    private fun addVisiblePlayers(
        players: MutableSet<PlayerPvp>,
        center: PlayerPvp,
        visiblePlayers: (PlayerPvp) -> List<PlayerPvp>,
    ) {
        for (spectator in visiblePlayers(center)) {
            players.add(spectator)
        }
    }
}
