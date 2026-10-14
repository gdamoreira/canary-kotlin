package org.opentibia.canary.creatures.players.pvp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.players.setPvpMode
import org.opentibia.canary.map.Position
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.spells.WorldType

/**
 * Transcribes `ExpertPvp::classifyRelation(actor, subject)`
 * (src/creatures/players/components/pvp/expert_pvp.cpp:485-522) and its
 * file-local helpers `isAccessPlayer` (expert_pvp.cpp:52-59),
 * `getMasterPlayer` (expert_pvp.cpp:61-68),
 * `isSkulledClientTarget` (expert_pvp.cpp:240-255) and
 * `hasAttackedProtectedAlly` (expert_pvp.cpp:257-278).
 *
 * `g_game().getWorldType()` is an explicit [WorldType] parameter and the
 * `g_game()` player lookups are the port's own `PlayerPvp` component state,
 * per AGENTS.md 1.1.
 */
class ExpertPvpRelationContextTest {

    private fun newPlayer(id: Long, position: Position = Position(100, 100, 7)) = Player(
        id = id,
        name = "Hero$id",
        maxHealth = 150,
        health = 150,
        position = position,
    )

    @Test
    fun `a missing actor or subject resolves the unknown relation`() {
        // expert_pvp.cpp:486-489.
        val player = newPlayer(1)

        assertThat(ExpertPvp.classifyRelation(null, player, WorldType.PVP).relation)
            .isEqualTo(ExpertPvpRelation.Unknown)
        assertThat(ExpertPvp.classifyRelation(player, null, WorldType.PVP).relation)
            .isEqualTo(ExpertPvpRelation.Unknown)
        assertThat(ExpertPvp.classifyRelation(null, null, WorldType.PVP).facts.actorGuid).isZero()
    }

    @Test
    fun `the actor itself and its summons are self`() {
        // expert_pvp.cpp:506: isSelf = subjectOwnerPlayer == actor.
        val actor = newPlayer(1)
        val summon = Monster(id = 2, name = "Summon", maxHealth = 10, health = 10, position = Position(100, 100, 7))
        summon.setMaster(actor)

        assertThat(ExpertPvp.classifyRelation(actor, actor, WorldType.PVP).relation)
            .isEqualTo(ExpertPvpRelation.Self)
        assertThat(ExpertPvp.classifyRelation(actor, summon, WorldType.PVP).relation)
            .isEqualTo(ExpertPvpRelation.Self)
    }

    @Test
    fun `an access actor or subject resolves the access relation`() {
        // expert_pvp.cpp:494, 507: group->access for both sides.
        val actor = newPlayer(1)
        val subject = newPlayer(2)
        val accessActor = newPlayer(3)
        accessActor.group = Group(access = true)
        val accessSubject = newPlayer(4)
        accessSubject.group = Group(access = true)

        assertThat(ExpertPvp.classifyRelation(accessActor, subject, WorldType.PVP).relation)
            .isEqualTo(ExpertPvpRelation.AccessPlayer)
        assertThat(ExpertPvp.classifyRelation(actor, accessSubject, WorldType.PVP).relation)
            .isEqualTo(ExpertPvpRelation.AccessPlayer)
    }

    @Test
    fun `an npc subject resolves the npc relation`() {
        // expert_pvp.cpp:505.
        val actor = newPlayer(1)
        val npc = Npc(id = 2, name = "Sam", position = Position(101, 100, 7))

        val result = ExpertPvp.classifyRelation(actor, npc, WorldType.PVP)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.Npc)
        assertThat(result.facts.subjectIsNpc).isTrue()
    }

    @Test
    fun `party members resolve the party ally relation`() {
        // expert_pvp.cpp:511: both parties non-null and identical.
        val actor = newPlayer(1)
        val subject = newPlayer(2)
        val party = Party(leader = actor)
        party.invitePlayer(subject)
        assertThat(party.joinParty(subject)).isTrue()

        val result = ExpertPvp.classifyRelation(actor, subject, WorldType.PVP)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.PartyAlly)
        assertThat(result.facts.partyAlly).isTrue()
    }

    @Test
    fun `guild mates resolve the guild ally relation`() {
        // expert_pvp.cpp:513.
        val actor = newPlayer(1)
        val subject = newPlayer(2)
        val guild = Guild(42, "Red Rose")
        actor.setGuild(guild)
        subject.setGuild(guild)

        val result = ExpertPvp.classifyRelation(actor, subject, WorldType.PVP)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.GuildAlly)
        assertThat(result.facts.guildAlly).isTrue()
    }

    @Test
    fun `a subject that attacked the actor resolves the direct attacker relation`() {
        // expert_pvp.cpp:516: subjectOwnerPlayer->hasAttacked(actor).
        val actor = newPlayer(1)
        val subject = newPlayer(2)
        subject.pvpPlayer.addAttacked(actor.pvpPlayer)

        val result = ExpertPvp.classifyRelation(actor, subject, WorldType.PVP)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.DirectAttacker)
        assertThat(result.facts.directAttacker).isTrue()
    }

    @Test
    fun `mutual guild war flags resolve the war enemy relation`() {
        // expert_pvp.cpp:514-515: both war vectors contain the other guild id.
        val actor = newPlayer(1)
        val subject = newPlayer(2)
        val actorGuild = Guild(1, "One")
        val subjectGuild = Guild(2, "Two")
        actor.setGuild(actorGuild)
        subject.setGuild(subjectGuild)
        actor.pvpPlayer.guildWarVector.add(subjectGuild.id)
        subject.pvpPlayer.guildWarVector.add(actorGuild.id)

        val result = ExpertPvp.classifyRelation(actor, subject, WorldType.PVP)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.WarEnemy)
        assertThat(result.facts.warEnemy).isTrue()
    }

    @Test
    fun `the actors attacked guids resolve the direct target relation`() {
        // expert_pvp.cpp:518: NotGainInFight gate plus getAttackedPlayerGuids.
        val actor = newPlayer(1)
        val subject = newPlayer(2)
        actor.pvpPlayer.addAttacked(subject.pvpPlayer)

        val result = ExpertPvp.classifyRelation(actor, subject, WorldType.PVP)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.DirectTarget)
        assertThat(result.facts.directTarget).isTrue()
    }

    @Test
    fun `a skulled subject on a pvp world resolves the skulled target relation`() {
        // expert_pvp.cpp:240-255: world PVP, skull not NONE/GREEN.
        val actor = newPlayer(1)
        val subject = newPlayer(2)
        subject.setSkull(org.opentibia.canary.creatures.Skulls_t.SKULL_RED)

        val result = ExpertPvp.classifyRelation(actor, subject, WorldType.PVP)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.SkulledTarget)
        assertThat(result.facts.skulledTarget).isTrue()
    }

    @Test
    fun `skulled target detection is disabled outside the pvp world type`() {
        // expert_pvp.cpp:245-247.
        val actor = newPlayer(1)
        val subject = newPlayer(2)
        subject.setSkull(org.opentibia.canary.creatures.Skulls_t.SKULL_RED)

        val result = ExpertPvp.classifyRelation(actor, subject, WorldType.NO_PVP)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
        assertThat(result.facts.skulledTarget).isFalse()
    }

    @Test
    fun `a monster summon owned by a third player resolves the player summon relation`() {
        // expert_pvp.cpp:504: subjectGuid is the master player, not the monster.
        val actor = newPlayer(1)
        val owner = newPlayer(2)
        val summon = Monster(id = 3, name = "Summon", maxHealth = 10, health = 10, position = Position(102, 100, 7))
        summon.setMaster(owner)

        val result = ExpertPvp.classifyRelation(actor, summon, WorldType.PVP)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.PlayerSummon)
        assertThat(result.facts.subjectIsPlayerSummon).isTrue()
        assertThat(result.facts.subjectGuid).isEqualTo(owner.getGUID())
    }

    @Test
    fun `a plain monster resolves the monster relation`() {
        // expert_pvp.cpp:503, 520-521.
        val actor = newPlayer(1)
        val monster = Monster(id = 3, name = "Rat", maxHealth = 10, health = 10, position = Position(102, 100, 7))

        val result = ExpertPvp.classifyRelation(actor, monster, WorldType.PVP)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.Monster)
        assertThat(result.facts.subjectIsMonster).isTrue()
    }

    @Test
    fun `a neutral player resolves the neutral player relation`() {
        // expert_pvp.cpp:521-522.
        val actor = newPlayer(1)
        val subject = newPlayer(2)

        val result = ExpertPvp.classifyRelation(actor, subject, WorldType.PVP)

        assertThat(result.relation).isEqualTo(ExpertPvpRelation.NeutralPlayer)
        assertThat(result.facts.subjectIsPlayer).isTrue()
        assertThat(result.facts.actorGuid).isEqualTo(1L)
        assertThat(result.facts.subjectGuid).isEqualTo(2L)
    }

    @Test
    fun `the actor mode is carried into the facts`() {
        // expert_pvp.cpp:493: actorMode = actor->getPvpMode().
        val actor = newPlayer(1)
        val subject = newPlayer(2)
        actor.setPvpMode(PvpMode_t.PVP_MODE_WHITE_HAND)

        val result = ExpertPvp.classifyRelation(actor, subject, WorldType.PVP)

        assertThat(result.facts.actorMode).isEqualTo(PvpMode_t.PVP_MODE_WHITE_HAND)
    }
}
