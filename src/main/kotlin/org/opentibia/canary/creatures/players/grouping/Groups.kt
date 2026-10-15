package org.opentibia.canary.creatures.players.grouping

import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.io.xml.GroupsFlagNumbers
import org.opentibia.canary.io.xml.GroupsXmlLoader

/**
 * Mirror of Canary's `class Groups`
 * (src/creatures/players/grouping/groups.hpp:23-36, groups.cpp:37-113).
 *
 * Canary's `load()` reads `{CORE_DIRECTORY}/XML/groups.xml` through the global
 * config manager and the class is a member of the `g_game()` singleton
 * (`Game::groups`, game.hpp:625). Following AGENTS.md 1.1 (no global singletons,
 * dependency injection), the XML source is injected: [load] takes the document
 * content directly, so the production boot and tests feed the same
 * `data/XML/groups.xml`.
 *
 * The registry is the `IOLoginDataLoad::loadPlayerBasicInfo` group source
 * (iologindata_load_player.cpp:140-145): the login resolves the persisted
 * `players.group_id` through [getGroup] before `Player::setGroup`
 * (player.hpp:519-521), exactly like the C++ `g_game().groups.getGroup(id)`.
 */
class Groups {

    private val groupsVector = mutableListOf<Group>()

    /**
     * `Groups::load` (groups.cpp:69-99) over the injected XML content; the
     * `g_configManager().getString(CORE_DIRECTORY) + "/XML/groups.xml"` file
     * read stays with the composition root. Returns `false` and leaves the
     * registry untouched when the document cannot be parsed, mirroring the
     * C++ `printXMLError` path (groups.cpp:73-76).
     */
    fun load(xmlContent: String): Boolean {
        val loaded = GroupsXmlLoader.load(xmlContent) ?: return false
        groupsVector.addAll(loaded)
        return true
    }

    /**
     * `Groups::reload` (groups.cpp:45-49): clear the registry, then load
     * again. The C++ clears through `g_game().groups.getGroups().clear()`
     * (a mutable reference); the port owns the vector, so the clear is
     * internal.
     */
    fun reload(xmlContent: String): Boolean {
        groupsVector.clear()
        return load(xmlContent)
    }

    /**
     * `Groups::getGroup` (groups.cpp:101-109): the first group with [id], or
     * `null` like the C++ `nullptr` return.
     */
    fun getGroup(id: Int): Group? = groupsVector.firstOrNull { it.id == id }

    /** `Groups::getGroups` (groups.cpp:111-113): the live registry list. */
    fun getGroups(): List<Group> = groupsVector

    companion object {
        /** `Groups::getFlagNumber` (groups.cpp:37-39). */
        fun getFlagNumber(playerFlags: PlayerFlags_t): Int = playerFlags.value

        /** `Groups::getFlagFromNumber` (groups.cpp:41-43); `null` mirrors the C++ undefined value. */
        fun getFlagFromNumber(value: Int): PlayerFlags_t? = GroupsFlagNumbers.fromNumber(value)
    }
}
