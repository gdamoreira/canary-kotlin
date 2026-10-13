package org.opentibia.canary.creatures.players.vip

/**
 * `VipStatus_t` (`src/creatures/creatures_definitions.hpp:783-788`).
 */
enum class VipStatus(val value: Int) {
    Offline(0),
    Online(1),
    Pending(2),
    Training(3),
}

/**
 * One VIP list record. Mirrors `VIPEntry` (`creatures_definitions.hpp:1585-1601`)
 * plus the live status `ProtocolGame::sendVIP` resolves for the listed player
 * (protocolgame.cpp:8750-8784).
 */
data class VipEntry(
    val guid: Long,
    val name: String,
    var description: String = "",
    var icon: Int = 0,
    var notify: Boolean = false,
    var status: VipStatus = VipStatus.Offline,
)

/**
 * `VIPGroup` (`src/creatures/players/components/player_vip.hpp:16-26`).
 * [vipGroupGuids] is the `vipGroupGuids` membership set.
 */
data class VipGroup(
    val id: Int,
    var name: String,
    var customizable: Boolean = false,
    val vipGroupGuids: MutableSet<Long> = linkedSetOf(),
)

/**
 * Persistence seam for the `IOLoginData` VIP statics
 * (src/io/iologindata.cpp:363-497). `PlayerVIP` calls these only while
 * `m_player.account` is set (player_vip.cpp:69, 87, 112-123, 176, 200, 219).
 */
interface VipRepository {
    /** `IOLoginData::addVIPEntry` (iologindata.cpp:383-396). */
    fun addVipEntry(accountId: Long, guid: Long, description: String, icon: Int, notify: Boolean)

    /** `IOLoginData::editVIPEntry` (iologindata.cpp:398-411). */
    fun editVipEntry(accountId: Long, guid: Long, description: String, icon: Int, notify: Boolean)

    /** `IOLoginData::removeVIPEntry` (iologindata.cpp:413-422). */
    fun removeVipEntry(accountId: Long, guid: Long)

    /** `IOLoginData::addVIPGroupEntry` (iologindata.cpp:446-457). */
    fun addVipGroupEntry(groupId: Int, accountId: Long, groupName: String, customizable: Boolean)

    /** `IOLoginData::editVIPGroupEntry` (iologindata.cpp:459-470). */
    fun editVipGroupEntry(groupId: Int, accountId: Long, groupName: String, customizable: Boolean)

    /** `IOLoginData::removeVIPGroupEntry` (iologindata.cpp:472-479). */
    fun removeVipGroupEntry(groupId: Int, accountId: Long)

    /** `IOLoginData::addGuidVIPGroupEntry` (iologindata.cpp:481-491). */
    fun addGuidVipGroupEntry(groupId: Int, accountId: Long, guid: Long)

    /** `IOLoginData::removeGuidVIPGroupEntry` (iologindata.cpp:493-497); clears every group of the guid. */
    fun removeGuidVipGroupEntry(accountId: Long, guid: Long)
}

/**
 * In-memory stand-in for the `account_viplist`, `account_vipgroups` and
 * `account_vipgrouplist` tables, used for local runs and tests.
 */
class InMemoryVipRepository : VipRepository {
    data class EntryRecord(
        val accountId: Long,
        val guid: Long,
        val description: String,
        val icon: Int,
        val notify: Boolean,
    )

    data class GroupRecord(
        val groupId: Int,
        val accountId: Long,
        val name: String,
        val customizable: Boolean,
    )

    data class MemberRecord(
        val accountId: Long,
        val guid: Long,
        val groupId: Int,
    )

    val entries = mutableListOf<EntryRecord>()
    val groups = mutableListOf<GroupRecord>()
    val members = mutableListOf<MemberRecord>()

    override fun addVipEntry(accountId: Long, guid: Long, description: String, icon: Int, notify: Boolean) {
        entries.removeIf { it.accountId == accountId && it.guid == guid }
        entries.add(EntryRecord(accountId, guid, description, icon, notify))
    }

    override fun editVipEntry(accountId: Long, guid: Long, description: String, icon: Int, notify: Boolean) {
        val index = entries.indexOfFirst { it.accountId == accountId && it.guid == guid }
        if (index < 0) {
            return
        }
        entries[index] = EntryRecord(accountId, guid, description, icon, notify)
    }

    override fun removeVipEntry(accountId: Long, guid: Long) {
        entries.removeIf { it.accountId == accountId && it.guid == guid }
    }

    override fun addVipGroupEntry(groupId: Int, accountId: Long, groupName: String, customizable: Boolean) {
        groups.removeIf { it.accountId == accountId && it.groupId == groupId }
        groups.add(GroupRecord(groupId, accountId, groupName, customizable))
    }

    override fun editVipGroupEntry(groupId: Int, accountId: Long, groupName: String, customizable: Boolean) {
        val index = groups.indexOfFirst { it.accountId == accountId && it.groupId == groupId }
        if (index < 0) {
            return
        }
        groups[index] = GroupRecord(groupId, accountId, groupName, customizable)
    }

    override fun removeVipGroupEntry(groupId: Int, accountId: Long) {
        groups.removeIf { it.accountId == accountId && it.groupId == groupId }
    }

    override fun addGuidVipGroupEntry(groupId: Int, accountId: Long, guid: Long) {
        if (members.none { it.accountId == accountId && it.guid == guid && it.groupId == groupId }) {
            members.add(MemberRecord(accountId, guid, groupId))
        }
    }

    override fun removeGuidVipGroupEntry(accountId: Long, guid: Long) {
        members.removeIf { it.accountId == accountId && it.guid == guid }
    }
}

/**
 * `ProtocolGame` VIP senders used by `PlayerVIP`
 * (protocolgame.hpp:451-453). A `null` client models `m_player.client == nullptr`.
 */
interface VipClient {
    /** `ProtocolGame::sendVIP` (protocolgame.cpp:9374-9405). */
    fun sendVip(guid: Long, name: String, description: String, icon: Int, notify: Boolean, status: VipStatus)

    /** `ProtocolGame::sendVIPGroups` (protocolgame.cpp:9407-9425). */
    fun sendVipGroups()

    /** `ProtocolGame::sendUpdatedVIPStatus` (protocolgame.cpp:9355-9372). */
    fun sendUpdatedVipStatus(guid: Long, newStatus: VipStatus)
}

/**
 * Mirror of Canary `PlayerVIP`
 * (src/creatures/players/components/player_vip.hpp:28-77,
 * player_vip.cpp:18-253).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Global
 * and Player dependencies are constructor-injected seams (AGENTS.md 1.1):
 * [accountId] replaces `m_player.account->getID()`, [groupMaxVipEntries]
 * replaces `m_player.group->maxVipEntries` (the port's `Group` does not model
 * the field yet), [isPremium] replaces `m_player.isPremium()`, [client]
 * replaces `m_player.client`, and the message/sound/log sinks replace the
 * `Player`/`g_logger()` calls.
 *
 * C++ keeps only the listed guids in memory and reads names/descriptions/icons
 * from `account_viplist` through `IOLoginData::getVIPEntries` at login
 * (protocolgame.cpp:8750). This port keeps the complete [VipEntry] records so
 * the component is self-contained and [getVipEntries] can drive `sendVIP`.
 */
class PlayerVip(
    private val repository: VipRepository = InMemoryVipRepository(),
    /** `m_player.account->getID()`; `null` models `!m_player.account`. */
    private val accountId: () -> Long? = { null },
    /** `m_player.group->maxVipEntries`; 0 models an absent/disabled group cap. */
    private val groupMaxVipEntries: () -> Long = { 0L },
    /** `Player::isPremium()` (player.cpp:7532). */
    private val isPremium: () -> Boolean = { false },
    /** `m_player.client`; a `null` client skips every protocol send. */
    private val client: VipClient? = null,
    /** `Player::sendTextMessage(MESSAGE_FAILURE, ...)`. */
    private val sendTextMessage: (message: String) -> Unit = {},
    /** `Player::sendCancelMessage(...)`. */
    private val sendCancelMessage: (message: String) -> Unit = {},
    /** `Player::sendSingleSoundEffect(ACTION_VIP_LOGIN, OWN)` (creatures_definitions.hpp:1470). */
    private val sendVipLoginSound: () -> Unit = {},
    /** `Player::sendSingleSoundEffect(ACTION_VIP_LOGOUT, OWN)` (creatures_definitions.hpp:1469). */
    private val sendVipLogoutSound: () -> Unit = {},
    /** `g_logger().debug` (player_vip.cpp:152, 158). */
    private val logDebug: (message: String) -> Unit = {},
    /** `g_logger().warn` (player_vip.cpp:193). */
    private val logWarn: (message: String) -> Unit = {},
) {

    /** `PlayerVIP::status` (player_vip.hpp:74), default `VipStatus_t::Online`. */
    private var status: VipStatus = VipStatus.Online

    private val vipEntries = linkedMapOf<Long, VipEntry>()
    private val vipGroups = mutableListOf<VipGroup>()

    /** `PlayerVIP::getStatus` (player_vip.hpp:38-40). */
    fun getStatus(): VipStatus = status

    /** `PlayerVIP::setStatus` (player_vip.hpp:41-43). */
    fun setStatus(newStatus: VipStatus) {
        status = newStatus
    }

    /** `PlayerVIP::getMaxEntries` (player_vip.cpp:24-32). */
    fun getMaxEntries(): Long {
        val groupMax = groupMaxVipEntries()
        if (groupMax != 0L) {
            return groupMax
        }
        return if (isPremium()) 100L else 20L
    }

    /** `PlayerVIP::getMaxGroupEntries` (player_vip.cpp:34-39). */
    fun getMaxGroupEntries(): Int = if (isPremium()) 8 else 0

    /**
     * `PlayerVIP::notifyStatusChange` (player_vip.cpp:41-61). C++ takes the
     * `loginPlayer` object for its guid/name; the port takes those values so the
     * component stays decoupled from `Player`.
     */
    fun notifyStatusChange(
        loginPlayerGuid: Long,
        loginPlayerName: String,
        vipStatus: VipStatus,
        message: Boolean = true,
    ) {
        val vipClient = client ?: return
        if (!exists(loginPlayerGuid)) {
            return
        }

        vipClient.sendUpdatedVipStatus(loginPlayerGuid, vipStatus)

        if (message) {
            when (vipStatus) {
                VipStatus.Online -> {
                    sendVipLoginSound()
                    sendTextMessage("$loginPlayerName has logged in.")
                }
                VipStatus.Offline -> {
                    sendVipLogoutSound()
                    sendTextMessage("$loginPlayerName has logged out.")
                }
                else -> {
                    // `Pending`/`Training` notify without a message (player_vip.cpp:52-59).
                }
            }
        }
    }

    /** `PlayerVIP::remove` (player_vip.cpp:63-74). */
    fun remove(vipGuid: Long): Boolean {
        if (vipEntries.remove(vipGuid) == null) {
            return false
        }

        accountId()?.let { repository.removeVipEntry(it, vipGuid) }
        return true
    }

    /** `PlayerVIP::add` (player_vip.cpp:76-96). */
    fun add(vipGuid: Long, vipName: String, vipStatus: VipStatus): Boolean {
        if (isAtEntryCap()) {
            sendTextMessage("You cannot add more buddies.")
            return false
        }

        if (vipEntries.containsKey(vipGuid)) {
            sendTextMessage("This player is already in your list.")
            return false
        }

        vipEntries[vipGuid] = VipEntry(vipGuid, vipName, status = vipStatus)

        accountId()?.let { repository.addVipEntry(it, vipGuid, "", 0, false) }
        client?.sendVip(vipGuid, vipName, "", 0, false, vipStatus)
        return true
    }

    /** `PlayerVIP::addInternal` (player_vip.cpp:98-104): silent list load. */
    fun addInternal(vipGuid: Long): Boolean {
        if (isAtEntryCap() || vipEntries.containsKey(vipGuid)) {
            return false
        }

        vipEntries[vipGuid] = VipEntry(vipGuid, "")
        return true
    }

    /**
     * `PlayerVIP::edit` (player_vip.cpp:106-127). C++ clears only the persisted
     * `account_vipgrouplist` rows before inserting the supplied groups
     * (player_vip.cpp:116-124), leaving deselected groups stale in memory until
     * the next relog. This port clears both memberships so the in-memory and
     * persisted group assignments always agree (PARITY GAP: hardening).
     */
    fun edit(
        vipGuid: Long,
        description: String,
        icon: Int,
        notify: Boolean,
        groupsId: List<Int>,
    ): Boolean {
        val entry = vipEntries[vipGuid] ?: return false

        accountId()?.let {
            repository.editVipEntry(it, vipGuid, description, icon, notify)
            repository.removeGuidVipGroupEntry(it, vipGuid)
        }

        entry.description = description
        entry.icon = icon
        entry.notify = notify

        for (group in vipGroups) {
            group.vipGroupGuids.remove(vipGuid)
        }

        for (groupId in groupsId) {
            val group = getGroupByID(groupId) ?: continue
            group.vipGroupGuids.add(vipGuid)
            accountId()?.let { repository.addGuidVipGroupEntry(group.id, it, vipGuid) }
        }

        return true
    }

    /** `PlayerVIP::exists` (player_vip.cpp:129-131). */
    fun exists(vipGuid: Long): Boolean = vipEntries.containsKey(vipGuid)

    /**
     * Port seam for `IOLoginData::getVIPEntries` (iologindata.cpp:363-381), which
     * C++ resolves from `account_viplist` during login (protocolgame.cpp:8750).
     * Entries keep their insertion order.
     */
    fun getVipEntries(): List<VipEntry> = vipEntries.values.toList()

    /** `PlayerVIP::getGroupByID` (player_vip.cpp:133-139). */
    fun getGroupByID(groupId: Int): VipGroup? = vipGroups.firstOrNull { it.id == groupId }

    /** `PlayerVIP::getGroupByName` (player_vip.cpp:141-148), case-sensitive `strcmp`. */
    fun getGroupByName(name: String): VipGroup? = vipGroups.firstOrNull { it.name == name }

    /**
     * `PlayerVIP::addGroupInternal` (player_vip.cpp:150-163). Canary ignores the
     * passed `groupId` and stores the group under [getFreeId]; the port keeps
     * that behavior for load-path parity. No persistence or client send.
     */
    fun addGroupInternal(groupId: Int, name: String, customizable: Boolean) {
        if (getGroupByName(name) != null) {
            logDebug("addGroupInternal - Group name already exists.")
            return
        }

        val freeId = getFreeId()
        if (freeId == 0) {
            logDebug("addGroupInternal - No id available.")
            return
        }

        vipGroups.add(VipGroup(freeId, name, customizable))
    }

    /** `PlayerVIP::removeGroup` (player_vip.cpp:165-183). */
    fun removeGroup(groupId: Int) {
        val iterator = vipGroups.iterator()
        var removed = false
        while (iterator.hasNext()) {
            if (iterator.next().id == groupId) {
                iterator.remove()
                removed = true
                break
            }
        }

        if (!removed) {
            return
        }

        accountId()?.let { repository.removeVipGroupEntry(groupId, it) }
        client?.sendVipGroups()
    }

    /** `PlayerVIP::addGroup` (player_vip.cpp:185-207). */
    fun addGroup(name: String, customizable: Boolean = true) {
        if (getGroupByName(name) != null) {
            sendCancelMessage("A group with this name already exists. Please choose another name.")
            return
        }

        val freeId = getFreeId()
        if (freeId == 0) {
            logWarn("addGroup - No id available.")
            return
        }

        val vipGroup = VipGroup(freeId, name, customizable)
        vipGroups.add(vipGroup)

        accountId()?.let { repository.addVipGroupEntry(vipGroup.id, it, vipGroup.name, vipGroup.customizable) }
        client?.sendVipGroups()
    }

    /**
     * `PlayerVIP::editGroup` (player_vip.cpp:209-226). Canary dereferences the
     * looked-up group without a null check (player_vip.cpp:215-216); the port
     * ignores an unknown [groupId] instead of crashing (PARITY GAP: hardening).
     */
    fun editGroup(groupId: Int, newName: String, customizable: Boolean = true) {
        if (getGroupByName(newName) != null) {
            sendCancelMessage("A group with this name already exists. Please choose another name.")
            return
        }

        val vipGroup = getGroupByID(groupId) ?: return
        vipGroup.name = newName
        vipGroup.customizable = customizable

        accountId()?.let { repository.editVipGroupEntry(vipGroup.id, it, vipGroup.name, vipGroup.customizable) }
        client?.sendVipGroups()
    }

    /** `PlayerVIP::addGuidToGroupInternal` (player_vip.cpp:248-253). */
    fun addGuidToGroupInternal(groupId: Int, guid: Long) {
        getGroupByID(groupId)?.vipGroupGuids?.add(guid)
    }

    /** `PlayerVIP::getFreeId` (player_vip.cpp:228-236): first free id in 1..8, else 0. */
    fun getFreeId(): Int {
        for (id in FIRST_ID..LAST_ID) {
            if (getGroupByID(id) == null) {
                return id
            }
        }
        return 0
    }

    /** `PlayerVIP::getGroupsIdGuidBelongs` (player_vip.cpp:238-246), in group insertion order. */
    fun getGroupsIdGuidBelongs(guid: Long): List<Int> =
        vipGroups.filter { it.vipGroupGuids.contains(guid) }.map { it.id }

    /** `PlayerVIP::getGroups` (player_vip.hpp:67-69). */
    fun getGroups(): List<VipGroup> = vipGroups

    private fun isAtEntryCap(): Boolean =
        vipEntries.size.toLong() >= getMaxEntries() || vipEntries.size == HARD_MAX_ENTRIES

    companion object {
        /** `PlayerVIP::firstID` (player_vip.cpp:18). */
        const val FIRST_ID = 1

        /** `PlayerVIP::lastID` (player_vip.cpp:19). */
        const val LAST_ID = 8

        /** The 9.53 client buddy cap (player_vip.cpp:77). */
        private const val HARD_MAX_ENTRIES = 200
    }
}
