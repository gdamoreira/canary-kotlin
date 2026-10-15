package org.opentibia.canary.creatures.players.vip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the Canary PlayerVIP component.
 *
 * Source of truth: ../.projects/canary
 *  - src/creatures/players/components/player_vip.hpp:16-77
 *  - src/creatures/players/components/player_vip.cpp:18-253
 *  - src/io/iologindata.cpp:363-497 (VIP persistence statics)
 *  - src/game/game.cpp:7070-7114 (playerRequestAddVip/playerRequestRemoveVip/playerRequestEditVip)
 *  - src/server/network/protocol/protocolgame.cpp:9355-9425 (0xD2/0xD3/0xD4 senders)
 */
class PlayerVipTest {

    private data class EntryCall(
        val accountId: Long,
        val guid: Long,
        val description: String,
        val icon: Int,
        val notify: Boolean,
    )

    private data class GroupCall(
        val groupId: Int,
        val accountId: Long,
        val name: String,
        val customizable: Boolean,
    )

    private data class MemberCall(
        val groupId: Int,
        val accountId: Long,
        val guid: Long,
    )

    private data class VipFrame(
        val guid: Long,
        val name: String,
        val description: String,
        val icon: Int,
        val notify: Boolean,
        val status: VipStatus,
    )

    /** Stand-in for `IOLoginData` VIP statics. */
    private class RecordingRepository : VipRepository {
        val addedEntries = mutableListOf<EntryCall>()
        val editedEntries = mutableListOf<EntryCall>()
        val removedEntries = mutableListOf<Pair<Long, Long>>()
        val addedGroups = mutableListOf<GroupCall>()
        val editedGroups = mutableListOf<GroupCall>()
        val removedGroups = mutableListOf<Pair<Long, Int>>()
        val addedMembers = mutableListOf<MemberCall>()
        val removedMembers = mutableListOf<Pair<Long, Long>>()

        override fun addVipEntry(accountId: Long, guid: Long, description: String, icon: Int, notify: Boolean) {
            addedEntries.add(EntryCall(accountId, guid, description, icon, notify))
        }

        override fun editVipEntry(accountId: Long, guid: Long, description: String, icon: Int, notify: Boolean) {
            editedEntries.add(EntryCall(accountId, guid, description, icon, notify))
        }

        override fun removeVipEntry(accountId: Long, guid: Long) {
            removedEntries.add(accountId to guid)
        }

        override fun addVipGroupEntry(groupId: Int, accountId: Long, groupName: String, customizable: Boolean) {
            addedGroups.add(GroupCall(groupId, accountId, groupName, customizable))
        }

        override fun editVipGroupEntry(groupId: Int, accountId: Long, groupName: String, customizable: Boolean) {
            editedGroups.add(GroupCall(groupId, accountId, groupName, customizable))
        }

        override fun removeVipGroupEntry(groupId: Int, accountId: Long) {
            removedGroups.add(accountId to groupId)
        }

        override fun addGuidVipGroupEntry(groupId: Int, accountId: Long, guid: Long) {
            addedMembers.add(MemberCall(groupId, accountId, guid))
        }

        override fun removeGuidVipGroupEntry(accountId: Long, guid: Long) {
            removedMembers.add(accountId to guid)
        }
    }

    /** Stand-in for the `ProtocolGame` VIP senders. */
    private class RecordingClient : VipClient {
        val vipFrames = mutableListOf<VipFrame>()
        var groupsFrames = 0
        val statusUpdates = mutableListOf<Pair<Long, VipStatus>>()

        override fun sendVip(
            guid: Long,
            name: String,
            description: String,
            icon: Int,
            notify: Boolean,
            status: VipStatus,
        ) {
            vipFrames.add(VipFrame(guid, name, description, icon, notify, status))
        }

        override fun sendVipGroups() {
            groupsFrames++
        }

        override fun sendUpdatedVipStatus(guid: Long, newStatus: VipStatus) {
            statusUpdates.add(guid to newStatus)
        }
    }

    private class Fixture(
        accountId: Long? = 42L,
        isPremium: Boolean = false,
        groupMaxVipEntries: Long = 0L,
        clientEnabled: Boolean = true,
    ) {
        val repository = RecordingRepository()
        val client: RecordingClient? = if (clientEnabled) RecordingClient() else null
        val textMessages = mutableListOf<String>()
        val cancelMessages = mutableListOf<String>()
        val sounds = mutableListOf<String>()
        val debugLogs = mutableListOf<String>()
        val warnLogs = mutableListOf<String>()

        val vip = PlayerVip(
            repository = repository,
            accountId = { accountId },
            groupMaxVipEntries = { groupMaxVipEntries },
            isPremium = { isPremium },
            client = { client },
            sendTextMessage = { textMessages.add(it) },
            sendCancelMessage = { cancelMessages.add(it) },
            sendVipLoginSound = { sounds.add("login") },
            sendVipLogoutSound = { sounds.add("logout") },
            logDebug = { debugLogs.add(it) },
            logWarn = { warnLogs.add(it) },
        )
    }

    @Test
    fun `add stores the entry with persisted defaults and sends the VIP frame`() {
        val f = Fixture()

        assertThat(f.vip.add(1000L, "Alice", VipStatus.Online)).isTrue()

        assertThat(f.vip.exists(1000L)).isTrue()
        assertThat(f.vip.getVipEntries()).containsExactly(VipEntry(1000L, "Alice", "", 0, false, VipStatus.Online))
        assertThat(f.repository.addedEntries).containsExactly(EntryCall(42L, 1000L, "", 0, false))
        assertThat(f.client!!.vipFrames).containsExactly(VipFrame(1000L, "Alice", "", 0, false, VipStatus.Online))
    }

    @Test
    fun `add refuses a duplicate guid with the Canary failure message`() {
        val f = Fixture()
        f.vip.add(1000L, "Alice", VipStatus.Online)

        assertThat(f.vip.add(1000L, "Alice", VipStatus.Online)).isFalse()

        assertThat(f.textMessages).containsExactly("This player is already in your list.")
        assertThat(f.repository.addedEntries).hasSize(1)
        assertThat(f.client!!.vipFrames).hasSize(1)
    }

    @Test
    fun `add enforces the premium cap of 100 entries`() {
        val f = Fixture(isPremium = true)

        for (guid in 1L..100L) {
            assertThat(f.vip.add(guid, "Player$guid", VipStatus.Offline)).isTrue()
        }
        assertThat(f.vip.add(101L, "Player101", VipStatus.Offline)).isFalse()

        assertThat(f.textMessages).containsExactly("You cannot add more buddies.")
        assertThat(f.vip.getVipEntries()).hasSize(100)
    }

    @Test
    fun `add enforces the free account cap of 20 entries`() {
        val f = Fixture()

        for (guid in 1L..20L) {
            assertThat(f.vip.add(guid, "Player$guid", VipStatus.Offline)).isTrue()
        }
        assertThat(f.vip.add(21L, "Player21", VipStatus.Offline)).isFalse()

        assertThat(f.textMessages).containsExactly("You cannot add more buddies.")
        assertThat(f.vip.getVipEntries()).hasSize(20)
    }

    @Test
    fun `add prefers the group max entries over the premium cap`() {
        val f = Fixture(isPremium = true, groupMaxVipEntries = 5L)

        for (guid in 1L..5L) {
            assertThat(f.vip.add(guid, "Player$guid", VipStatus.Offline)).isTrue()
        }
        assertThat(f.vip.add(6L, "Player6", VipStatus.Offline)).isFalse()

        assertThat(f.textMessages).containsExactly("You cannot add more buddies.")
    }

    @Test
    fun `add enforces the 200 buddy client limit even with a higher group cap`() {
        val f = Fixture(groupMaxVipEntries = 300L)

        for (guid in 1L..200L) {
            assertThat(f.vip.add(guid, "Player$guid", VipStatus.Offline)).isTrue()
        }
        assertThat(f.vip.add(201L, "Player201", VipStatus.Offline)).isFalse()

        assertThat(f.textMessages).containsExactly("You cannot add more buddies.")
        assertThat(f.vip.getVipEntries()).hasSize(200)
    }

    @Test
    fun `addInternal inserts without sending or persisting and refuses duplicates`() {
        val f = Fixture()

        assertThat(f.vip.addInternal(77L)).isTrue()
        assertThat(f.vip.addInternal(77L)).isFalse()

        assertThat(f.vip.getVipEntries()).containsExactly(VipEntry(77L, "", "", 0, false, VipStatus.Offline))
        assertThat(f.repository.addedEntries).isEmpty()
        assertThat(f.client!!.vipFrames).isEmpty()
        assertThat(f.textMessages).isEmpty()
    }

    @Test
    fun `addInternal respects the max entries cap`() {
        val f = Fixture()

        for (guid in 1L..20L) {
            assertThat(f.vip.addInternal(guid)).isTrue()
        }
        assertThat(f.vip.addInternal(21L)).isFalse()
        assertThat(f.vip.getVipEntries()).hasSize(20)
    }

    @Test
    fun `remove deletes the entry persists it and reports the prior existence`() {
        val f = Fixture()
        f.vip.add(1000L, "Alice", VipStatus.Online)

        assertThat(f.vip.remove(1000L)).isTrue()

        assertThat(f.vip.exists(1000L)).isFalse()
        assertThat(f.vip.getVipEntries()).isEmpty()
        assertThat(f.repository.removedEntries).containsExactly(42L to 1000L)
        assertThat(f.vip.remove(1000L)).isFalse()
        assertThat(f.repository.removedEntries).hasSize(1)
    }

    @Test
    fun `remove without an account skips persistence`() {
        val f = Fixture(accountId = null)
        f.vip.add(1000L, "Alice", VipStatus.Online)

        assertThat(f.vip.remove(1000L)).isTrue()

        assertThat(f.repository.removedEntries).isEmpty()
    }

    @Test
    fun `edit updates description icon and notification for a listed guid`() {
        val f = Fixture()
        f.vip.add(1000L, "Alice", VipStatus.Online)

        // The inbound parser caps the icon at 10; the component stores what it is given.
        assertThat(f.vip.edit(1000L, "best friend", 12, true, emptyList())).isTrue()

        assertThat(f.vip.getVipEntries())
            .containsExactly(VipEntry(1000L, "Alice", "best friend", 12, true, VipStatus.Online))
        assertThat(f.repository.editedEntries).containsExactly(EntryCall(42L, 1000L, "best friend", 12, true))
    }

    @Test
    fun `edit returns false for an unlisted guid and persists nothing`() {
        val f = Fixture()

        assertThat(f.vip.edit(999L, "x", 1, true, listOf(1))).isFalse()

        assertThat(f.repository.editedEntries).isEmpty()
        assertThat(f.repository.removedMembers).isEmpty()
        assertThat(f.repository.addedMembers).isEmpty()
    }

    @Test
    fun `edit replaces the group memberships with the supplied groups`() {
        val f = Fixture()
        f.vip.addGroup("Friends")
        f.vip.addGroup("Party")
        f.vip.add(1000L, "Alice", VipStatus.Online)

        f.vip.edit(1000L, "", 0, false, listOf(1, 2))
        assertThat(f.vip.getGroupsIdGuidBelongs(1000L)).containsExactly(1, 2)

        f.vip.edit(1000L, "", 0, false, listOf(2))
        assertThat(f.vip.getGroupsIdGuidBelongs(1000L)).containsExactly(2)
        assertThat(f.vip.getGroupByID(1)!!.vipGroupGuids).doesNotContain(1000L)

        f.vip.edit(1000L, "", 0, false, emptyList())
        assertThat(f.vip.getGroupsIdGuidBelongs(1000L)).isEmpty()

        assertThat(f.repository.addedMembers.map { it.groupId }).containsExactly(1, 2, 2)
        assertThat(f.repository.removedMembers).containsExactly(42L to 1000L, 42L to 1000L, 42L to 1000L)
    }

    @Test
    fun `edit ignores unknown group ids`() {
        val f = Fixture()
        f.vip.addGroup("Friends")
        f.vip.add(1000L, "Alice", VipStatus.Online)

        assertThat(f.vip.edit(1000L, "", 0, false, listOf(9))).isTrue()

        assertThat(f.vip.getGroupsIdGuidBelongs(1000L)).isEmpty()
        assertThat(f.repository.addedMembers).isEmpty()
    }

    @Test
    fun `edit without an account updates memory only`() {
        val f = Fixture(accountId = null)
        f.vip.add(1000L, "Alice", VipStatus.Online)

        assertThat(f.vip.edit(1000L, "note", 3, true, emptyList())).isTrue()

        assertThat(f.vip.getVipEntries().single().description).isEqualTo("note")
        assertThat(f.repository.editedEntries).isEmpty()
    }

    @Test
    fun `addGroup assigns the first free id persists and notifies the client`() {
        val f = Fixture()

        f.vip.addGroup("Friends")
        f.vip.addGroup("Enemies", customizable = false)

        val friends = f.vip.getGroupByID(1)!!
        assertThat(friends.name).isEqualTo("Friends")
        assertThat(friends.customizable).isTrue()
        val enemies = f.vip.getGroupByID(2)!!
        assertThat(enemies.name).isEqualTo("Enemies")
        assertThat(enemies.customizable).isFalse()
        assertThat(f.vip.getGroups().map { it.id }).containsExactly(1, 2)
        assertThat(f.repository.addedGroups)
            .containsExactly(GroupCall(1, 42L, "Friends", true), GroupCall(2, 42L, "Enemies", false))
        assertThat(f.client!!.groupsFrames).isEqualTo(2)
    }

    @Test
    fun `addGroup rejects a duplicate name with the Canary cancel message`() {
        val f = Fixture()
        f.vip.addGroup("Friends")

        f.vip.addGroup("Friends")

        assertThat(f.cancelMessages)
            .containsExactly("A group with this name already exists. Please choose another name.")
        assertThat(f.vip.getGroups()).hasSize(1)
        assertThat(f.client!!.groupsFrames).isEqualTo(1)
    }

    @Test
    fun `addGroup refuses to create a ninth group`() {
        val f = Fixture()
        for (id in 1..8) {
            f.vip.addGroup("Group$id")
        }

        assertThat(f.vip.getFreeId()).isEqualTo(0)
        f.vip.addGroup("Group9")

        assertThat(f.vip.getGroups()).hasSize(8)
        assertThat(f.warnLogs).containsExactly("addGroup - No id available.")
        assertThat(f.repository.addedGroups).hasSize(8)
        assertThat(f.client!!.groupsFrames).isEqualTo(8)
    }

    @Test
    fun `addGroupInternal ignores the passed group id and assigns the first free id`() {
        val f = Fixture()
        f.vip.addGroup("Friends")

        // Canary consumes the first free id and drops the passed groupId (player_vip.cpp:156-162).
        f.vip.addGroupInternal(7, "Team", false)

        assertThat(f.vip.getGroupByID(1)!!.name).isEqualTo("Friends")
        val team = f.vip.getGroupByID(2)!!
        assertThat(team.name).isEqualTo("Team")
        assertThat(team.customizable).isFalse()
        assertThat(f.vip.getGroupByID(7)).isNull()
        assertThat(f.repository.addedGroups).hasSize(1)
        assertThat(f.client!!.groupsFrames).isEqualTo(1)
    }

    @Test
    fun `addGroupInternal skips duplicate names and logs`() {
        val f = Fixture()
        f.vip.addGroup("Friends")

        f.vip.addGroupInternal(2, "Friends", true)

        assertThat(f.vip.getGroups()).hasSize(1)
        assertThat(f.debugLogs).containsExactly("addGroupInternal - Group name already exists.")
    }

    @Test
    fun `addGroupInternal refuses when all group ids are taken`() {
        val f = Fixture()
        for (id in 1..8) {
            f.vip.addGroupInternal(0, "Group$id", true)
        }

        f.vip.addGroupInternal(0, "Group9", true)

        assertThat(f.vip.getGroups()).hasSize(8)
        assertThat(f.debugLogs).containsExactly("addGroupInternal - No id available.")
    }

    @Test
    fun `getGroupByID and getGroupByName resolve stored groups`() {
        val f = Fixture()
        f.vip.addGroup("Friends")

        assertThat(f.vip.getGroupByID(1)!!.name).isEqualTo("Friends")
        assertThat(f.vip.getGroupByName("Friends")!!.id).isEqualTo(1)
        assertThat(f.vip.getGroupByID(2)).isNull()
        assertThat(f.vip.getGroupByName("friends")).isNull()
    }

    @Test
    fun `removeGroup deletes the group persists it and notifies the client`() {
        val f = Fixture()
        f.vip.addGroup("Friends")

        f.vip.removeGroup(1)

        assertThat(f.vip.getGroups()).isEmpty()
        assertThat(f.repository.removedGroups).containsExactly(42L to 1)
        assertThat(f.client!!.groupsFrames).isEqualTo(2)
    }

    @Test
    fun `removeGroup ignores an unknown group id`() {
        val f = Fixture()
        f.vip.addGroup("Friends")

        f.vip.removeGroup(9)

        assertThat(f.vip.getGroups()).hasSize(1)
        assertThat(f.repository.removedGroups).isEmpty()
        assertThat(f.client!!.groupsFrames).isEqualTo(1)
    }

    @Test
    fun `editGroup renames persists and notifies the client`() {
        val f = Fixture()
        f.vip.addGroup("Friends")

        f.vip.editGroup(1, "Besties", customizable = false)

        val group = f.vip.getGroupByID(1)!!
        assertThat(group.name).isEqualTo("Besties")
        assertThat(group.customizable).isFalse()
        assertThat(f.repository.editedGroups).containsExactly(GroupCall(1, 42L, "Besties", false))
        assertThat(f.client!!.groupsFrames).isEqualTo(2)
    }

    @Test
    fun `editGroup rejects a duplicate name even when it is the group's own name`() {
        val f = Fixture()
        f.vip.addGroup("Friends")

        f.vip.editGroup(1, "Friends", customizable = true)

        assertThat(f.cancelMessages)
            .containsExactly("A group with this name already exists. Please choose another name.")
        assertThat(f.repository.editedGroups).isEmpty()
        assertThat(f.client!!.groupsFrames).isEqualTo(1)
    }

    @Test
    fun `editGroup ignores an unknown group id`() {
        val f = Fixture()
        f.vip.addGroup("Friends")

        f.vip.editGroup(9, "Ghost", customizable = true)

        assertThat(f.vip.getGroupByID(1)!!.name).isEqualTo("Friends")
        assertThat(f.repository.editedGroups).isEmpty()
        assertThat(f.client!!.groupsFrames).isEqualTo(1)
    }

    @Test
    fun `addGuidToGroupInternal adds membership only for existing groups`() {
        val f = Fixture()
        f.vip.addGroup("Friends")
        f.vip.addGroup("Party")

        f.vip.addGuidToGroupInternal(1, 500L)
        f.vip.addGuidToGroupInternal(9, 500L)

        assertThat(f.vip.getGroupByID(1)!!.vipGroupGuids).containsExactly(500L)
        assertThat(f.vip.getGroupsIdGuidBelongs(500L)).containsExactly(1)
    }

    @Test
    fun `getMaxEntries resolves group premium and free defaults`() {
        assertThat(Fixture().vip.getMaxEntries()).isEqualTo(20L)
        assertThat(Fixture(isPremium = true).vip.getMaxEntries()).isEqualTo(100L)
        assertThat(Fixture(isPremium = true, groupMaxVipEntries = 50L).vip.getMaxEntries()).isEqualTo(50L)
    }

    @Test
    fun `getMaxGroupEntries is eight for premium accounts and zero otherwise`() {
        assertThat(Fixture(isPremium = true).vip.getMaxGroupEntries()).isEqualTo(8)
        assertThat(Fixture().vip.getMaxGroupEntries()).isEqualTo(0)
    }

    @Test
    fun `status defaults to online and setStatus stores the owner status`() {
        val f = Fixture()

        assertThat(f.vip.getStatus()).isEqualTo(VipStatus.Online)
        f.vip.setStatus(VipStatus.Training)
        assertThat(f.vip.getStatus()).isEqualTo(VipStatus.Training)
    }

    @Test
    fun `notifyStatusChange sends the status text and sound for a listed guid`() {
        val f = Fixture()
        f.vip.add(1000L, "Alice", VipStatus.Offline)

        f.vip.notifyStatusChange(1000L, "Alice", VipStatus.Online)
        f.vip.notifyStatusChange(1000L, "Alice", VipStatus.Offline)

        assertThat(f.client!!.statusUpdates)
            .containsExactly(1000L to VipStatus.Online, 1000L to VipStatus.Offline)
        assertThat(f.sounds).containsExactly("login", "logout")
        assertThat(f.textMessages).containsExactly("Alice has logged in.", "Alice has logged out.")
    }

    @Test
    fun `notifyStatusChange respects the message flag`() {
        val f = Fixture()
        f.vip.add(1000L, "Alice", VipStatus.Offline)

        f.vip.notifyStatusChange(1000L, "Alice", VipStatus.Online, message = false)

        assertThat(f.client!!.statusUpdates).containsExactly(1000L to VipStatus.Online)
        assertThat(f.sounds).isEmpty()
        assertThat(f.textMessages).isEmpty()
    }

    @Test
    fun `notifyStatusChange ignores guids absent from the list`() {
        val f = Fixture()

        f.vip.notifyStatusChange(999L, "Ghost", VipStatus.Online)

        assertThat(f.client!!.statusUpdates).isEmpty()
        assertThat(f.sounds).isEmpty()
        assertThat(f.textMessages).isEmpty()
    }

    @Test
    fun `notifyStatusChange is a no-op without a protocol client`() {
        val f = Fixture(clientEnabled = false)
        f.vip.add(1000L, "Alice", VipStatus.Offline)

        f.vip.notifyStatusChange(1000L, "Alice", VipStatus.Online)

        assertThat(f.client).isNull()
        assertThat(f.sounds).isEmpty()
        assertThat(f.textMessages).isEmpty()
    }
}
