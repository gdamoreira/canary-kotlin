package org.opentibia.canary.creatures.players.pvp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the Canary `ExpertPvp` component.
 *
 * Source of truth: ../.projects/canary
 *  - src/creatures/players/components/pvp/expert_pvp.hpp:24-61
 *  - src/creatures/players/components/pvp/expert_pvp.cpp:361-1030
 *  - src/creatures/players/components/pvp/expert_pvp_definitions.hpp:19-195
 *  - tests/unit/players/components/expert_pvp_test.cpp (upstream unit suite)
 */
class ExpertPvpTest {

    // --- world type -----------------------------------------------------------------

    @Test
    fun `isExpertPvpWorldTypeName enables expert only for the expert-pvp world type`() {
        assertThat(ExpertPvp.isExpertPvpWorldTypeName("expert-pvp")).isTrue()
        assertThat(ExpertPvp.isExpertPvpWorldTypeName("EXPERT-PVP")).isTrue()
        assertThat(ExpertPvp.isExpertPvpWorldTypeName("retro-pvp")).isFalse()
        assertThat(ExpertPvp.isExpertPvpWorldTypeName("pvp")).isFalse()
        assertThat(ExpertPvp.isExpertPvpWorldTypeName("no-pvp")).isFalse()
        assertThat(ExpertPvp.isExpertPvpWorldTypeName("pvp-enforced")).isFalse()
    }

    @Test
    fun `isRetroPvpWorldTypeName treats the legacy pvp name as a retro alias`() {
        assertThat(ExpertPvp.isRetroPvpWorldTypeName("retro-pvp")).isTrue()
        assertThat(ExpertPvp.isRetroPvpWorldTypeName("RETRO-PVP")).isTrue()
        assertThat(ExpertPvp.isRetroPvpWorldTypeName("pvp")).isTrue()
        assertThat(ExpertPvp.isRetroPvpWorldTypeName("expert-pvp")).isFalse()
        assertThat(ExpertPvp.isRetroPvpWorldTypeName("no-pvp")).isFalse()
        assertThat(ExpertPvp.isRetroPvpWorldTypeName("pvp-enforced")).isFalse()
    }

    @Test
    fun `isEnabled follows the configured world type`() {
        assertThat(ExpertPvp.isEnabled("expert-pvp")).isTrue()
        assertThat(ExpertPvp.isEnabled("EXPERT-PVP")).isTrue()
        assertThat(ExpertPvp.isEnabled("retro-pvp")).isFalse()
        assertThat(ExpertPvp.isEnabled("pvp")).isFalse()
    }

    @Test
    fun `isExpertPvpWorldType and isRetroPvpWorldType read the configured world type`() {
        assertThat(ExpertPvp.isExpertPvpWorldType("expert-pvp")).isTrue()
        assertThat(ExpertPvp.isExpertPvpWorldType("retro-pvp")).isFalse()
        assertThat(ExpertPvp.isRetroPvpWorldType("retro-pvp")).isTrue()
        assertThat(ExpertPvp.isRetroPvpWorldType("pvp")).isTrue()
        assertThat(ExpertPvp.isRetroPvpWorldType("expert-pvp")).isFalse()
    }

    // --- mode normalization ---------------------------------------------------------

    @Test
    fun `isValidMode accepts exactly the four pvp modes`() {
        assertThat(ExpertPvp.isValidMode(PvpMode_t.PVP_MODE_DOVE)).isTrue()
        assertThat(ExpertPvp.isValidMode(PvpMode_t.PVP_MODE_WHITE_HAND)).isTrue()
        assertThat(ExpertPvp.isValidMode(PvpMode_t.PVP_MODE_YELLOW_HAND)).isTrue()
        assertThat(ExpertPvp.isValidMode(PvpMode_t.PVP_MODE_RED_FIST)).isTrue()
        assertThat(ExpertPvp.isValidMode(PvpMode_t(99))).isFalse()
    }

    @Test
    fun `accepts known client modes`() {
        val dove = ExpertPvp.modeFromClientByte(0)
        assertThat(dove.mode).isEqualTo(PvpMode_t.PVP_MODE_DOVE)
        assertThat(dove.source).isEqualTo(ExpertPvpModeSource.ClientByte)
        assertThat(dove.accepted).isTrue()
        assertThat(dove.normalized).isFalse()

        val redFist = ExpertPvp.modeFromClientByte(3)
        assertThat(redFist.mode).isEqualTo(PvpMode_t.PVP_MODE_RED_FIST)
        assertThat(redFist.source).isEqualTo(ExpertPvpModeSource.ClientByte)
        assertThat(redFist.accepted).isTrue()
        assertThat(redFist.normalized).isFalse()
    }

    @Test
    fun `normalizes an unknown client mode to dove`() {
        val result = ExpertPvp.modeFromClientByte(99)

        assertThat(result.mode).isEqualTo(PvpMode_t.PVP_MODE_DOVE)
        assertThat(result.source).isEqualTo(ExpertPvpModeSource.ClientByte)
        assertThat(result.accepted).isFalse()
        assertThat(result.normalized).isTrue()
        assertThat(result.reason).isEqualTo(ExpertPvpDecisionReason.InvalidMode)
    }

    @Test
    fun `default client mode is dove`() {
        val result = ExpertPvp.defaultModeForClient()

        assertThat(result.mode).isEqualTo(PvpMode_t.PVP_MODE_DOVE)
        assertThat(result.source).isEqualTo(ExpertPvpModeSource.DefaultForClient)
        assertThat(result.accepted).isTrue()
        assertThat(result.normalized).isFalse()
    }

    @Test
    fun `normalizeMode keeps a valid requested mode and its source`() {
        val result = ExpertPvp.normalizeMode(PvpMode_t.PVP_MODE_YELLOW_HAND, ExpertPvpModeSource.StoredPlayerState)

        assertThat(result.mode).isEqualTo(PvpMode_t.PVP_MODE_YELLOW_HAND)
        assertThat(result.source).isEqualTo(ExpertPvpModeSource.StoredPlayerState)
        assertThat(result.accepted).isTrue()
        assertThat(result.normalized).isFalse()
    }

    @Test
    fun `normalizeMode falls back to dove and reports invalid`() {
        val result = ExpertPvp.normalizeMode(PvpMode_t(99), ExpertPvpModeSource.LiveOwnerState)

        assertThat(result.mode).isEqualTo(PvpMode_t.PVP_MODE_DOVE)
        assertThat(result.source).isEqualTo(ExpertPvpModeSource.LiveOwnerState)
        assertThat(result.accepted).isFalse()
        assertThat(result.normalized).isTrue()
        assertThat(result.reason).isEqualTo(ExpertPvpDecisionReason.InvalidMode)
    }

    // --- relation classification ----------------------------------------------------

    @Test
    fun `classifies self before other relations`() {
        val context = ExpertPvpRelationContext(isSelf = true, directAttacker = true, skulledTarget = true)

        val result = ExpertPvp.classifyRelation(context)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.Self)
        assertThat(result.facts.isSelf).isTrue()
    }

    @Test
    fun `classifies access player before other relations`() {
        val context = ExpertPvpRelationContext(
            subjectIsAccessPlayer = true,
            subjectIsMonster = true,
            subjectIsPlayerSummon = true,
        )

        val result = ExpertPvp.classifyRelation(context)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.AccessPlayer)
    }

    @Test
    fun `classifies npc before party and aggression`() {
        val context = ExpertPvpRelationContext(
            subjectIsNpc = true,
            partyAlly = true,
            directAttacker = true,
        )

        val result = ExpertPvp.classifyRelation(context)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.Npc)
    }

    @Test
    fun `classifies party ally before aggression`() {
        val context = ExpertPvpRelationContext(
            subjectIsPlayer = true,
            partyAlly = true,
            directAttacker = true,
        )

        val result = ExpertPvp.classifyRelation(context)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.PartyAlly)
    }

    @Test
    fun `classifies guild ally after party and before aggression`() {
        val context = ExpertPvpRelationContext(
            subjectIsPlayer = true,
            guildAlly = true,
            directAttacker = true,
        )

        val result = ExpertPvp.classifyRelation(context)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.GuildAlly)
    }

    @Test
    fun `classifies direct attacker before war`() {
        val context = ExpertPvpRelationContext(
            subjectIsPlayer = true,
            directAttacker = true,
            warEnemy = true,
        )

        val result = ExpertPvp.classifyRelation(context)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.DirectAttacker)
    }

    @Test
    fun `keeps war ahead of stale direct target`() {
        val context = ExpertPvpRelationContext(
            subjectIsPlayer = true,
            warEnemy = true,
            directTarget = true,
        )

        val result = ExpertPvp.classifyRelation(context)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.WarEnemy)
    }

    @Test
    fun `classifies protected ally attacker before direct target`() {
        val context = ExpertPvpRelationContext(
            subjectIsPlayer = true,
            protectedAllyAttacker = true,
            directTarget = true,
        )

        val result = ExpertPvp.classifyRelation(context)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.ProtectedAllyAttacker)
    }

    @Test
    fun `classifies skulled target`() {
        val context = ExpertPvpRelationContext(
            subjectIsPlayer = true,
            skulledTarget = true,
        )

        val result = ExpertPvp.classifyRelation(context)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.SkulledTarget)
    }

    @Test
    fun `classifies player summon before monster`() {
        val context = ExpertPvpRelationContext(
            subjectIsMonster = true,
            subjectIsPlayerSummon = true,
        )

        val result = ExpertPvp.classifyRelation(context)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.PlayerSummon)
    }

    @Test
    fun `classifies player summon owner combat before summon fallback`() {
        val context = ExpertPvpRelationContext(
            subjectIsMonster = true,
            subjectIsPlayerSummon = true,
            directAttacker = true,
        )

        val result = ExpertPvp.classifyRelation(context)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.DirectAttacker)
    }

    @Test
    fun `classifies monster`() {
        val result = ExpertPvp.classifyRelation(ExpertPvpRelationContext(subjectIsMonster = true))

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.Monster)
    }

    @Test
    fun `classifies neutral player as fallback`() {
        val result = ExpertPvp.classifyRelation(ExpertPvpRelationContext(subjectIsPlayer = true))

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
    }

    @Test
    fun `classifies an empty context as unknown`() {
        val result = ExpertPvp.classifyRelation(ExpertPvpRelationContext())

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.Unknown)
    }

    @Test
    fun `classifyRelation returns the evaluated facts`() {
        val context = ExpertPvpRelationContext(actorGuid = 100, subjectGuid = 200, subjectIsPlayer = true)

        val result = ExpertPvp.classifyRelation(context)

        assertThat(result.facts).isEqualTo(context)
    }

    // --- combat action decisions ----------------------------------------------------

    @Test
    fun `dove denies neutral player combat`() {
        val context = ExpertPvpRelationContext(actorGuid = 100, subjectGuid = 200, subjectIsPlayer = true)

        val decision = ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_DOVE, ExpertPvpActionKind.DirectAttack, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.allowed).isFalse()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.Neutral)
    }

    @Test
    fun `dove allows direct defense without pz lock`() {
        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            subjectGuid = 200,
            subjectIsPlayer = true,
            directAttacker = true,
        )

        val decision = ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_DOVE, ExpertPvpActionKind.DirectAttack, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.allowed).isTrue()
        assertThat(decision.startsFight).isTrue()
        assertThat(decision.appliesPzLock).isFalse()
        assertThat(decision.sideEffectOwnerGuid).isEqualTo(100)
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.DirectAttacker)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.DirectCombat)
    }

    @Test
    fun `white protects party and guild members`() {
        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            subjectGuid = 200,
            subjectIsPlayer = true,
            protectedAllyAttacker = true,
        )

        val doveDecision = ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_DOVE, ExpertPvpActionKind.DirectAttack, context)
        val whiteDecision = ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_WHITE_HAND, ExpertPvpActionKind.DirectAttack, context)

        assertThat(doveDecision.allowed).isFalse()
        assertThat(whiteDecision.allowed).isTrue()
        assertThat(whiteDecision.appliesPzLock).isFalse()
        assertThat(whiteDecision.relation).isEqualTo(ExpertPvpRelation.ProtectedAllyAttacker)
    }

    @Test
    fun `yellow requires a skull for a protected ally attacker`() {
        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            subjectGuid = 200,
            subjectIsPlayer = true,
            protectedAllyAttacker = true,
        )

        val unskulledDecision = ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_YELLOW_HAND, ExpertPvpActionKind.DirectAttack, context)
        val skulledDecision = ExpertPvp.evaluateCombatAction(
            PvpMode_t.PVP_MODE_YELLOW_HAND,
            ExpertPvpActionKind.DirectAttack,
            context.copy(skulledTarget = true),
        )

        assertThat(unskulledDecision.allowed).isFalse()
        assertThat(skulledDecision.allowed).isTrue()
        assertThat(skulledDecision.appliesPzLock).isFalse()
    }

    @Test
    fun `red fist allows a protected ally attacker without a skull`() {
        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            subjectGuid = 200,
            subjectIsPlayer = true,
            protectedAllyAttacker = true,
        )

        val decision = ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_RED_FIST, ExpertPvpActionKind.DirectAttack, context)

        assertThat(decision.allowed).isTrue()
        assertThat(decision.appliesPzLock).isFalse()
        assertThat(decision.startsFight).isTrue()
    }

    @Test
    fun `dove denies neutral player summon combat`() {
        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            subjectGuid = 200,
            subjectIsMonster = true,
            subjectIsPlayerSummon = true,
        )

        val decision = ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_DOVE, ExpertPvpActionKind.DirectAttack, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.allowed).isFalse()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.PlayerSummon)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.Neutral)
    }

    @Test
    fun `yellow allows skulled targets`() {
        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            subjectGuid = 200,
            subjectIsPlayer = true,
            skulledTarget = true,
        )

        val decision = ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_YELLOW_HAND, ExpertPvpActionKind.DirectAttack, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.allowed).isTrue()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.SkulledTarget)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.SkulledTarget)
    }

    @Test
    fun `dove and white deny a neutral skulled target`() {
        val context = ExpertPvpRelationContext(subjectIsPlayer = true, skulledTarget = true)

        assertThat(ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_DOVE, ExpertPvpActionKind.DirectAttack, context).allowed).isFalse()
        assertThat(ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_WHITE_HAND, ExpertPvpActionKind.DirectAttack, context).allowed).isFalse()
    }

    @Test
    fun `yellow allows a direct target with pz lock`() {
        val context = ExpertPvpRelationContext(subjectIsPlayer = true, directTarget = true)

        val decision = ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_YELLOW_HAND, ExpertPvpActionKind.DirectAttack, context)

        assertThat(decision.allowed).isTrue()
        assertThat(decision.appliesPzLock).isTrue()
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.DirectCombat)
    }

    @Test
    fun `red allows neutral player with side effect description`() {
        val context = ExpertPvpRelationContext(actorGuid = 100, subjectGuid = 200, subjectIsPlayer = true)

        val decision = ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_RED_FIST, ExpertPvpActionKind.DirectAttack, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.allowed).isTrue()
        assertThat(decision.startsFight).isTrue()
        assertThat(decision.appliesPzLock).isTrue()
        assertThat(decision.countsUnjustified).isTrue()
        assertThat(decision.sideEffectOwnerGuid).isEqualTo(100)
        assertThat(decision.skullAction).isEqualTo(ExpertPvpSkullAction.White)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.Neutral)
    }

    @Test
    fun `war enemies are always allowed without pz lock`() {
        val context = ExpertPvpRelationContext(subjectIsPlayer = true, warEnemy = true)

        for (mode in listOf(PvpMode_t.PVP_MODE_DOVE, PvpMode_t.PVP_MODE_WHITE_HAND, PvpMode_t.PVP_MODE_YELLOW_HAND, PvpMode_t.PVP_MODE_RED_FIST)) {
            val decision = ExpertPvp.evaluateCombatAction(mode, ExpertPvpActionKind.DirectAttack, context)
            assertThat(decision.allowed).describedAs("mode $mode").isTrue()
            assertThat(decision.appliesPzLock).describedAs("mode $mode").isFalse()
            assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.War)
        }
    }

    @Test
    fun `self access player monster and npc are always allowed`() {
        val relations = listOf(
            ExpertPvpRelationContext(isSelf = true) to ExpertPvpDecisionReason.Self,
            ExpertPvpRelationContext(actorIsAccessPlayer = true) to ExpertPvpDecisionReason.AccessPlayer,
            ExpertPvpRelationContext(subjectIsMonster = true) to ExpertPvpDecisionReason.Monster,
            ExpertPvpRelationContext(subjectIsNpc = true) to ExpertPvpDecisionReason.Npc,
        )

        for ((context, expectedReason) in relations) {
            val decision = ExpertPvp.evaluateCombatAction(PvpMode_t.PVP_MODE_DOVE, ExpertPvpActionKind.DirectAttack, context)
            assertThat(decision.allowed).isTrue()
            assertThat(decision.reason).isEqualTo(expectedReason)
        }
    }

    @Test
    fun `party and guild allies are denied`() {
        val partyAlly = ExpertPvp.evaluateCombatAction(
            PvpMode_t.PVP_MODE_RED_FIST,
            ExpertPvpActionKind.DirectAttack,
            ExpertPvpRelationContext(subjectIsPlayer = true, partyAlly = true),
        )
        val guildAlly = ExpertPvp.evaluateCombatAction(
            PvpMode_t.PVP_MODE_RED_FIST,
            ExpertPvpActionKind.DirectAttack,
            ExpertPvpRelationContext(subjectIsPlayer = true, guildAlly = true),
        )

        assertThat(partyAlly.allowed).isFalse()
        assertThat(partyAlly.reason).isEqualTo(ExpertPvpDecisionReason.Ally)
        assertThat(guildAlly.allowed).isFalse()
        assertThat(guildAlly.reason).isEqualTo(ExpertPvpDecisionReason.Ally)
    }

    @Test
    fun `an unknown relation fails with missing player`() {
        val decision = ExpertPvp.evaluateCombatAction(
            PvpMode_t.PVP_MODE_RED_FIST,
            ExpertPvpActionKind.DirectAttack,
            ExpertPvpRelationContext(actorGuid = 100),
        )

        assertThat(decision.handled).isTrue()
        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.MissingPlayer)
    }

    @Test
    fun `an invalid mode fails closed`() {
        val context = ExpertPvpRelationContext(actorGuid = 100, subjectGuid = 200, subjectIsPlayer = true)

        val decision = ExpertPvp.evaluateCombatAction(PvpMode_t(99), ExpertPvpActionKind.DirectAttack, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.InvalidMode)
    }

    @Test
    fun `the enabled-gated overload reports FeatureDisabled when expert pvp is off`() {
        val context = ExpertPvpRelationContext(actorGuid = 100, subjectGuid = 200, subjectIsPlayer = true)

        val disabled = ExpertPvp.evaluateCombatAction(ExpertPvpActionKind.DirectAttack, context, enabled = false)
        val enabled = ExpertPvp.evaluateCombatAction(ExpertPvpActionKind.DirectAttack, context, enabled = true)

        assertThat(disabled.handled).isFalse()
        assertThat(disabled.reason).isEqualTo(ExpertPvpDecisionReason.FeatureDisabled)
        assertThat(disabled.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
        assertThat(enabled.handled).isTrue()
        assertThat(enabled.reason).isEqualTo(ExpertPvpDecisionReason.Neutral)
    }

    // --- walkthrough decisions ------------------------------------------------------

    @Test
    fun `neutral players can walk through`() {
        val context = ExpertPvpRelationContext(actorGuid = 100, subjectGuid = 200, subjectIsPlayer = true)

        val decision = ExpertPvp.evaluateWalkthrough(context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.canWalkThrough).isTrue()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.Neutral)
    }

    @Test
    fun `active combat blocks walkthrough in dove`() {
        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            subjectGuid = 200,
            subjectIsPlayer = true,
            directAttacker = true,
        )

        val decision = ExpertPvp.evaluateWalkthrough(context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.canWalkThrough).isFalse()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.DirectAttacker)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.DirectCombat)
    }

    @Test
    fun `self and access players always walk through`() {
        val self = ExpertPvp.evaluateWalkthrough(ExpertPvpRelationContext(isSelf = true, directAttacker = true))
        val access = ExpertPvp.evaluateWalkthrough(ExpertPvpRelationContext(actorIsAccessPlayer = true, directAttacker = true))

        assertThat(self.canWalkThrough).isTrue()
        assertThat(self.reason).isEqualTo(ExpertPvpDecisionReason.Self)
        assertThat(access.canWalkThrough).isTrue()
        assertThat(access.reason).isEqualTo(ExpertPvpDecisionReason.AccessPlayer)
    }

    @Test
    fun `party and guild allies always walk through`() {
        val partyAlly = ExpertPvp.evaluateWalkthrough(ExpertPvpRelationContext(subjectIsPlayer = true, partyAlly = true))
        val guildAlly = ExpertPvp.evaluateWalkthrough(ExpertPvpRelationContext(subjectIsPlayer = true, guildAlly = true))

        assertThat(partyAlly.canWalkThrough).isTrue()
        assertThat(partyAlly.reason).isEqualTo(ExpertPvpDecisionReason.Ally)
        assertThat(guildAlly.canWalkThrough).isTrue()
    }

    @Test
    fun `dove does not block a protected ally attacker`() {
        val context = ExpertPvpRelationContext(
            actorMode = PvpMode_t.PVP_MODE_DOVE,
            subjectIsPlayer = true,
            protectedAllyAttacker = true,
        )

        val decision = ExpertPvp.evaluateWalkthrough(context)

        assertThat(decision.canWalkThrough).isTrue()
    }

    @Test
    fun `white blocks a protected ally attacker`() {
        val context = ExpertPvpRelationContext(
            actorMode = PvpMode_t.PVP_MODE_WHITE_HAND,
            subjectIsPlayer = true,
            protectedAllyAttacker = true,
        )

        val decision = ExpertPvp.evaluateWalkthrough(context)

        assertThat(decision.canWalkThrough).isFalse()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.ProtectedAllyAttacker)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.DirectCombat)
    }

    @Test
    fun `yellow blocks a skulled non-ally`() {
        val context = ExpertPvpRelationContext(
            actorMode = PvpMode_t.PVP_MODE_YELLOW_HAND,
            subjectIsPlayer = true,
            skulledTarget = true,
        )

        val decision = ExpertPvp.evaluateWalkthrough(context)

        assertThat(decision.canWalkThrough).isFalse()
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.SkulledTarget)
    }

    @Test
    fun `red blocks a neutral non-ally`() {
        val context = ExpertPvpRelationContext(
            actorMode = PvpMode_t.PVP_MODE_RED_FIST,
            subjectIsPlayer = true,
        )

        val decision = ExpertPvp.evaluateWalkthrough(context)

        assertThat(decision.canWalkThrough).isFalse()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.Neutral)
    }

    @Test
    fun `red blocks neutral player summons`() {
        val context = ExpertPvpRelationContext(
            actorMode = PvpMode_t.PVP_MODE_RED_FIST,
            subjectIsMonster = true,
            subjectIsPlayerSummon = true,
        )

        val decision = ExpertPvp.evaluateWalkthrough(context)

        assertThat(decision.canWalkThrough).isFalse()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.PlayerSummon)
    }

    @Test
    fun `red lets a party ally pass`() {
        val context = ExpertPvpRelationContext(
            actorMode = PvpMode_t.PVP_MODE_RED_FIST,
            subjectIsPlayer = true,
            partyAlly = true,
        )

        val decision = ExpertPvp.evaluateWalkthrough(context)

        assertThat(decision.canWalkThrough).isTrue()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.PartyAlly)
    }

    @Test
    fun `war enemies block walkthrough in every mode`() {
        val context = ExpertPvpRelationContext(subjectIsPlayer = true, warEnemy = true)

        for (mode in listOf(PvpMode_t.PVP_MODE_DOVE, PvpMode_t.PVP_MODE_WHITE_HAND, PvpMode_t.PVP_MODE_YELLOW_HAND, PvpMode_t.PVP_MODE_RED_FIST)) {
            val decision = ExpertPvp.evaluateWalkthrough(context.copy(actorMode = mode))
            assertThat(decision.canWalkThrough).describedAs("mode $mode").isFalse()
            assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.War)
        }
    }

    @Test
    fun `an invalid walkthrough mode fails closed`() {
        val context = ExpertPvpRelationContext(actorMode = PvpMode_t(99), subjectIsPlayer = true)

        val decision = ExpertPvp.evaluateWalkthrough(context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.canWalkThrough).isFalse()
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.InvalidMode)
    }

    @Test
    fun `the enabled-gated walkthrough overload reports FeatureDisabled when expert pvp is off`() {
        val context = ExpertPvpRelationContext(actorMode = PvpMode_t.PVP_MODE_DOVE, subjectIsPlayer = true)

        val disabled = ExpertPvp.canWalkThrough(context, enabled = false)
        val enabled = ExpertPvp.canWalkThrough(context, enabled = true)

        assertThat(disabled.handled).isFalse()
        assertThat(disabled.reason).isEqualTo(ExpertPvpDecisionReason.FeatureDisabled)
        assertThat(disabled.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
        assertThat(enabled.handled).isTrue()
        assertThat(enabled.canWalkThrough).isTrue()
    }

    // --- field contexts -------------------------------------------------------------

    @Test
    fun `builds magic wall visual ids`() {
        val context = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_RED_FIST, ExpertPvp.ITEM_MAGICWALL_SAFE, true)

        assertThat(context.ownerGuid).isEqualTo(100)
        assertThat(context.ownerMode).isEqualTo(PvpMode_t.PVP_MODE_RED_FIST)
        assertThat(context.canonicalItemId).isEqualTo(ExpertPvp.ITEM_MAGICWALL)
        assertThat(context.safeVisualItemId).isEqualTo(ExpertPvp.ITEM_MAGICWALL_SAFE)
        assertThat(context.blockingVisualItemId).isEqualTo(ExpertPvp.ITEM_MAGICWALL)
        assertThat(context.ownerWasPlayerOrSummon).isTrue()
    }

    @Test
    fun `builds wild growth visual ids`() {
        val context = ExpertPvp.makeFieldContext(
            100,
            PvpMode_t.PVP_MODE_WHITE_HAND,
            ExpertPvp.ITEM_WILDGROWTH_PERSISTENT,
            true,
        )

        assertThat(context.ownerMode).isEqualTo(PvpMode_t.PVP_MODE_WHITE_HAND)
        assertThat(context.canonicalItemId).isEqualTo(ExpertPvp.ITEM_WILDGROWTH)
        assertThat(context.safeVisualItemId).isEqualTo(ExpertPvp.ITEM_WILDGROWTH_SAFE)
        assertThat(context.blockingVisualItemId).isEqualTo(ExpertPvp.ITEM_WILDGROWTH)
    }

    @Test
    fun `ignores unsupported field items`() {
        assertThat(ExpertPvp.isExpertFieldItem(ExpertPvp.ITEM_GOLD_COIN)).isFalse()

        val context = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_RED_FIST, ExpertPvp.ITEM_GOLD_COIN, true)
        assertThat(context.canonicalItemId).isZero()
    }

    @Test
    fun `recognizes every expert field item`() {
        val expertFieldItems = listOf(
            ExpertPvp.ITEM_FIREFIELD_PVP_FULL,
            ExpertPvp.ITEM_FIREFIELD_PVP_MEDIUM,
            ExpertPvp.ITEM_FIREFIELD_PVP_SMALL,
            ExpertPvp.ITEM_FIREFIELD_PERSISTENT_FULL,
            ExpertPvp.ITEM_FIREFIELD_PERSISTENT_MEDIUM,
            ExpertPvp.ITEM_FIREFIELD_PERSISTENT_SMALL,
            ExpertPvp.ITEM_FIREFIELD_NOPVP,
            ExpertPvp.ITEM_POISONFIELD_PVP,
            ExpertPvp.ITEM_POISONFIELD_PERSISTENT,
            ExpertPvp.ITEM_POISONFIELD_NOPVP,
            ExpertPvp.ITEM_ENERGYFIELD_PVP,
            ExpertPvp.ITEM_ENERGYFIELD_PERSISTENT,
            ExpertPvp.ITEM_ENERGYFIELD_NOPVP,
            ExpertPvp.ITEM_MAGICWALL,
            ExpertPvp.ITEM_MAGICWALL_PERSISTENT,
            ExpertPvp.ITEM_MAGICWALL_SAFE,
            ExpertPvp.ITEM_WILDGROWTH,
            ExpertPvp.ITEM_WILDGROWTH_PERSISTENT,
            ExpertPvp.ITEM_WILDGROWTH_SAFE,
        )

        assertThat(expertFieldItems).allMatch { ExpertPvp.isExpertFieldItem(it) }
    }

    @Test
    fun `builds damaging field ownership context`() {
        val context = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_DOVE, ExpertPvp.ITEM_FIREFIELD_PVP_FULL, true)

        assertThat(ExpertPvp.isExpertFieldItem(ExpertPvp.ITEM_FIREFIELD_PVP_FULL)).isTrue()
        assertThat(context.ownerGuid).isEqualTo(100)
        assertThat(context.ownerMode).isEqualTo(PvpMode_t.PVP_MODE_DOVE)
        assertThat(context.canonicalItemId).isEqualTo(ExpertPvp.ITEM_FIREFIELD_PVP_FULL)
        assertThat(context.safeVisualItemId).isEqualTo(ExpertPvp.ITEM_FIREFIELD_PVP_FULL)
        assertThat(context.blockingVisualItemId).isEqualTo(ExpertPvp.ITEM_FIREFIELD_PVP_FULL)
    }

    @Test
    fun `makeFieldContext normalizes an invalid owner mode to dove`() {
        val context = ExpertPvp.makeFieldContext(100, PvpMode_t(99), ExpertPvp.ITEM_MAGICWALL, true)

        assertThat(context.ownerMode).isEqualTo(PvpMode_t.PVP_MODE_DOVE)
        assertThat(context.ownerModeSource).isEqualTo(ExpertPvpModeSource.CastTimeFieldContext)
    }

    // --- field damage decisions -----------------------------------------------------

    private fun fireField(mode: PvpMode_t) =
        ExpertPvp.makeFieldContext(100, mode, ExpertPvp.ITEM_FIREFIELD_PVP_FULL, true)

    @Test
    fun `dove field does not damage neutral players`() {
        val field = fireField(PvpMode_t.PVP_MODE_DOVE)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 200,
            subjectIsPlayer = true,
        )

        val decision = ExpertPvp.evaluateFieldDamage(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.applyDamage).isFalse()
        assertThat(decision.setConditionOwner).isFalse()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.Neutral)
    }

    @Test
    fun `red field damages neutral players with the owner guid`() {
        val field = fireField(PvpMode_t.PVP_MODE_RED_FIST)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 200,
            subjectIsPlayer = true,
        )

        val decision = ExpertPvp.evaluateFieldDamage(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.applyDamage).isTrue()
        assertThat(decision.setConditionOwner).isTrue()
        assertThat(decision.conditionOwnerGuid).isEqualTo(100)
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.Neutral)
    }

    @Test
    fun `a field without owner context is not handled`() {
        val decision = ExpertPvp.evaluateFieldDamage(ExpertFieldContext(), ExpertPvpRelationContext())

        assertThat(decision.handled).isFalse()
        assertThat(decision.applyDamage).isTrue()
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.MissingFieldContext)
    }

    // --- field step decisions -------------------------------------------------------

    @Test
    fun `dove field lets neutral players step`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_DOVE, ExpertPvp.ITEM_MAGICWALL, true)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 200,
            subjectIsPlayer = true,
        )

        val decision = ExpertPvp.evaluateFieldStep(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.canStep).isTrue()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.Neutral)
    }

    @Test
    fun `the field owner is blocked by their own wall`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_DOVE, ExpertPvp.ITEM_MAGICWALL, true)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 100,
            subjectIsPlayer = true,
            isSelf = true,
        )

        val decision = ExpertPvp.evaluateFieldStep(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.canStep).isFalse()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.Self)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.Self)
    }

    @Test
    fun `active combat players are blocked by the wall`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_DOVE, ExpertPvp.ITEM_MAGICWALL, true)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 200,
            subjectIsPlayer = true,
            directAttacker = true,
        )

        val decision = ExpertPvp.evaluateFieldStep(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.canStep).isFalse()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.DirectAttacker)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.DirectCombat)
    }

    @Test
    fun `white field blocks a protected ally attacker`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_WHITE_HAND, ExpertPvp.ITEM_MAGICWALL, true)
        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 200,
            subjectIsPlayer = true,
            protectedAllyAttacker = true,
        )

        val decision = ExpertPvp.evaluateFieldStep(field, context)

        assertThat(decision.canStep).isFalse()
        assertThat(decision.appliesPzLock).isFalse()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.ProtectedAllyAttacker)
    }

    @Test
    fun `yellow field blocks a skulled non-ally`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_YELLOW_HAND, ExpertPvp.ITEM_WILDGROWTH, true)
        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 200,
            subjectIsPlayer = true,
            skulledTarget = true,
        )

        val decision = ExpertPvp.evaluateFieldStep(field, context)

        assertThat(decision.canStep).isFalse()
        assertThat(decision.appliesPzLock).isFalse()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.SkulledTarget)
    }

    @Test
    fun `red field victim who has not retaliated can step`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_RED_FIST, ExpertPvp.ITEM_MAGICWALL, true)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 200,
            subjectIsPlayer = true,
            directTarget = true,
            skulledTarget = true,
        )

        val decision = ExpertPvp.evaluateFieldStep(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.canStep).isTrue()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.DirectTarget)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.DirectCombat)
    }

    @Test
    fun `red field blocks neutral players with a side effect description`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_RED_FIST, ExpertPvp.ITEM_MAGICWALL, true)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 200,
            subjectIsPlayer = true,
        )

        val decision = ExpertPvp.evaluateFieldStep(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.canStep).isFalse()
        assertThat(decision.sideEffectOwnerGuid).isEqualTo(100)
        assertThat(decision.skullAction).isEqualTo(ExpertPvpSkullAction.White)
        assertThat(decision.appliesPzLock).isTrue()
        assertThat(decision.startsFight).isFalse()
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.Neutral)
    }

    @Test
    fun `a field step without a valid magic wall context is not handled`() {
        val fireField = fireField(PvpMode_t.PVP_MODE_RED_FIST)

        val decision = ExpertPvp.evaluateFieldStep(fireField, ExpertPvpRelationContext())

        assertThat(decision.handled).isFalse()
        assertThat(decision.canStep).isTrue()
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.MissingFieldContext)
    }

    @Test
    fun `a field step without a summon or player owner is not handled`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_RED_FIST, ExpertPvp.ITEM_MAGICWALL, false)

        val decision = ExpertPvp.evaluateFieldStep(field, ExpertPvpRelationContext())

        assertThat(decision.handled).isFalse()
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.MissingFieldContext)
    }

    // --- field visual decisions -----------------------------------------------------

    @Test
    fun `neutral players see the safe wall visual`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_DOVE, ExpertPvp.ITEM_MAGICWALL, true)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 200,
            subjectIsPlayer = true,
        )

        val decision = ExpertPvp.getFieldClientId(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.clientItemId).isEqualTo(ExpertPvp.ITEM_MAGICWALL_SAFE)
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
    }

    @Test
    fun `the owner sees the blocking wall visual`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_DOVE, ExpertPvp.ITEM_MAGICWALL, true)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 100,
            subjectIsPlayer = true,
            isSelf = true,
        )

        val decision = ExpertPvp.getFieldClientId(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.clientItemId).isEqualTo(ExpertPvp.ITEM_MAGICWALL)
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.Self)
    }

    @Test
    fun `the owner sees the blocking wall visual from a safe-backed field`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_DOVE, ExpertPvp.ITEM_MAGICWALL_SAFE, true)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 100,
            subjectIsPlayer = true,
            isSelf = true,
        )

        val decision = ExpertPvp.getFieldClientId(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.clientItemId).isEqualTo(ExpertPvp.ITEM_MAGICWALL)
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.Self)
    }

    @Test
    fun `active combat players see the blocking wall visual`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_DOVE, ExpertPvp.ITEM_MAGICWALL, true)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 200,
            subjectIsPlayer = true,
            directAttacker = true,
        )

        val decision = ExpertPvp.getFieldClientId(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.clientItemId).isEqualTo(ExpertPvp.ITEM_MAGICWALL)
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.DirectAttacker)
    }

    @Test
    fun `red field victim who has not retaliated sees the safe wall visual`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_RED_FIST, ExpertPvp.ITEM_MAGICWALL_SAFE, true)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 200,
            subjectIsPlayer = true,
            directTarget = true,
            skulledTarget = true,
        )

        val decision = ExpertPvp.getFieldClientId(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.clientItemId).isEqualTo(ExpertPvp.ITEM_MAGICWALL_SAFE)
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.DirectTarget)
    }

    @Test
    fun `active combat players see the blocking wall visual from a safe-backed field`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_DOVE, ExpertPvp.ITEM_MAGICWALL_SAFE, true)

        val context = ExpertPvpRelationContext(
            actorGuid = 100,
            actorMode = field.ownerMode,
            subjectGuid = 200,
            subjectIsPlayer = true,
            directAttacker = true,
        )

        val decision = ExpertPvp.getFieldClientId(field, context)

        assertThat(decision.handled).isTrue()
        assertThat(decision.clientItemId).isEqualTo(ExpertPvp.ITEM_MAGICWALL)
        assertThat(decision.relation).isEqualTo(ExpertPvpRelation.DirectAttacker)
    }

    @Test
    fun `an unsupported field visual keeps the canonical item id`() {
        val field = ExpertPvp.makeFieldContext(100, PvpMode_t.PVP_MODE_DOVE, ExpertPvp.ITEM_FIREFIELD_PVP_FULL, true)

        val decision = ExpertPvp.getFieldClientId(field, ExpertPvpRelationContext(subjectIsPlayer = true))

        assertThat(decision.handled).isFalse()
        assertThat(decision.clientItemId).isEqualTo(ExpertPvp.ITEM_FIREFIELD_PVP_FULL)
        assertThat(decision.reason).isEqualTo(ExpertPvpDecisionReason.MissingFieldContext)
    }
}
