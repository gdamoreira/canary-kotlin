package org.opentibia.canary.creatures.players.grouping

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.PlayerFlags_t
import java.nio.file.Files
import java.nio.file.Path

/**
 * Transcript tests for `Groups::load` / `Groups::reload`
 * (src/creatures/players/grouping/groups.cpp:78-169) and `parseGroupFlags`
 * (:44-66) over the datapack `data/XML/groups.xml`.
 *
 * Canary reads the file through `g_configManager().getString(CORE_DIRECTORY)`;
 * the port injects the XML content instead (AGENTS.md 1.1), so the production
 * boot and these tests feed the same document.
 */
class GroupsTest {

    private val coreXml: String = Files.readString(Path.of("data", "XML", "groups.xml"))

    @Test
    fun `loads the core groups xml with access flags and caps`() {
        val groups = Groups()

        assertThat(groups.load(coreXml)).isTrue()

        val player = groups.getGroup(1)
        assertThat(player).isNotNull
        assertThat(player!!.name).isEqualTo("player")
        assertThat(player.access).isFalse()
        assertThat(player.flags).isEmpty()
        assertThat(player.maxDepotItems).isEqualTo(0)
        assertThat(player.maxVipEntries).isEqualTo(0)

        val tutor = groups.getGroup(2)!!
        assertThat(tutor.name).isEqualTo("tutor")
        assertThat(tutor.access).isFalse()
        assertThat(tutor.maxVipEntries).isEqualTo(200)
        assertThat(tutor.flags).containsExactlyInAnyOrder(
            PlayerFlags_t.TalkOrangeHelpChannel,
            PlayerFlags_t.CannotBeMuted,
        )

        val seniorTutor = groups.getGroup(3)!!
        assertThat(seniorTutor.flags).contains(
            PlayerFlags_t.CanTalkRedPrivate,
            PlayerFlags_t.CanTalkRedChannel,
            PlayerFlags_t.TalkOrangeHelpChannel,
            PlayerFlags_t.CannotBeMuted,
        )

        val gamemaster = groups.getGroup(4)!!
        assertThat(gamemaster.name).isEqualTo("gamemaster")
        assertThat(gamemaster.access).isTrue()
        assertThat(gamemaster.flags).contains(
            PlayerFlags_t.CannotUseCombat,
            PlayerFlags_t.CannotAttackPlayer,
            PlayerFlags_t.CannotBeAttacked,
            PlayerFlags_t.IgnoredByMonsters,
            PlayerFlags_t.IgnoredByNpcs,
            PlayerFlags_t.NotGainInFight,
            PlayerFlags_t.HasNoExhaustion,
            PlayerFlags_t.CannotUseSpells,
            PlayerFlags_t.CannotPickupItem,
            PlayerFlags_t.CanAlwaysLogin,
            PlayerFlags_t.CanBroadcast,
            PlayerFlags_t.CannotBeBanned,
            PlayerFlags_t.CannotBePushed,
        )
        // `canconvinceall="0"` is skipped by the `as_bool` guard
        // (groups.cpp:53-55): the flag stays unset even though the node exists.
        assertThat(gamemaster.flags).doesNotContain(PlayerFlags_t.CanConvinceAll)
    }

    @Test
    fun `the flags integer attribute sets the low bitset bits`() {
        val groups = Groups()

        assertThat(
            groups.load(
                """
                <groups>
                    <group id="9" name="bits" flags="5" />
                </groups>
                """.trimIndent(),
            ),
        ).isTrue()

        // `std::bitset(5)` -> bits 0 and 2 (groups.cpp:86-91).
        assertThat(groups.getGroup(9)!!.flags).containsExactlyInAnyOrder(
            PlayerFlags_t.CannotUseCombat,
            PlayerFlags_t.CannotAttackMonster,
        )
    }

    @Test
    fun `unknown flag names and missing first attributes are ignored`() {
        val groups = Groups()

        assertThat(
            groups.load(
                """
                <groups>
                    <group id="1" name="odd">
                        <flags>
                            <flag notarealflag="1" />
                            <flag />
                            <flag cannotbemuted="1" />
                        </flags>
                    </group>
                </groups>
                """.trimIndent(),
            ),
        ).isTrue()

        assertThat(groups.getGroup(1)!!.flags).containsExactly(PlayerFlags_t.CannotBeMuted)
    }

    @Test
    fun `reload clears the previously loaded groups`() {
        val groups = Groups()
        assertThat(groups.load(coreXml)).isTrue()
        assertThat(groups.getGroups()).isNotEmpty()

        assertThat(groups.reload("""<groups><group id="1" name="only" /></groups>""")).isTrue()

        assertThat(groups.getGroups()).hasSize(1)
        assertThat(groups.getGroup(1)!!.name).isEqualTo("only")
        assertThat(groups.getGroup(4)).isNull()
    }

    @Test
    fun `malformed xml fails the load without throwing`() {
        val groups = Groups()

        assertThat(groups.load("<groups><group")).isFalse()
        assertThat(groups.getGroups()).isEmpty()
    }

    @Test
    fun `a missing groups root fails the load`() {
        val groups = Groups()

        assertThat(groups.load("<notgroups />")).isFalse()
        assertThat(groups.getGroups()).isEmpty()
    }

    @Test
    fun `getGroup returns null for an unknown id`() {
        val groups = Groups()
        assertThat(groups.load(coreXml)).isTrue()

        assertThat(groups.getGroup(99)).isNull()
        assertThat(groups.getGroup(0)).isNull()
    }

    @Test
    fun `getFlagNumber and getFlagFromNumber round trip every flag`() {
        for (flag in PlayerFlags_t.entries) {
            assertThat(Groups.getFlagNumber(flag)).isEqualTo(flag.value)
        }

        assertThat(Groups.getFlagFromNumber(PlayerFlags_t.CannotBeMuted.value))
            .isEqualTo(PlayerFlags_t.CannotBeMuted)
        assertThat(Groups.getFlagFromNumber(999)).isNull()
    }
}
