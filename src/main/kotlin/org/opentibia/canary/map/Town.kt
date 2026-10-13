package org.opentibia.canary.map

/**
 * Faithful port of Canary `class Town` (src/map/town.hpp:15-41): the id fixed at
 * construction, the mutable name and the mutable temple position.
 *
 * Canary defaults `templePosition` to the zero `Position` and `name` to an empty
 * `std::string`; [Position] is immutable in Kotlin, so [getTemplePosition]
 * returns the stored instance and [setTemplePos] replaces it, matching the C++
 * by-value assignment.
 */
class Town(initId: Int) {
    private val id: Int = initId
    private var name: String = ""
    private var templePosition: Position = Position(0, 0, 0)

    /** `Town::getTemplePosition` (town.hpp:20-22). */
    fun getTemplePosition(): Position = templePosition

    /** `Town::getName` (town.hpp:23-25). */
    fun getName(): String = name

    /** `Town::setTemplePos` (town.hpp:27-29). */
    fun setTemplePos(pos: Position) {
        templePosition = pos
    }

    /** `Town::setName` (town.hpp:30-32). */
    fun setName(newName: String) {
        name = newName
    }

    /** `Town::getID` (town.hpp:33-35). */
    fun getID(): Int = id
}

/** `using TownMap` (town.hpp:43): `std::map<uint32_t, std::shared_ptr<Town>>`. */
typealias TownMap = Map<Int, Town>

/**
 * Faithful port of Canary `class Towns` (src/map/town.hpp:45-93): the id-keyed
 * registry with insertion that never replaces, the case-insensitive name lookup
 * that skips the reserved id 0 and the create-on-miss [getOrCreateTown].
 *
 * The C++ `TownMap` is a `std::map`, so name lookups scan ids in ascending
 * order; [townMap] is a `sortedMapOf` (a `TreeMap`) to preserve that order.
 * `caseInsensitiveCompare` (src/utils/tools.cpp:2206) compares the ASCII
 * lowercased characters and requires equal lengths; Kotlin's
 * `String.equals(ignoreCase = true)` additionally folds non-ASCII characters,
 * which is the only observable difference.
 *
 * C++ `Towns` is non-copyable and holds `std::shared_ptr<Town>`; Kotlin
 * references are already shared, so both properties hold by construction.
 */
class Towns {
    /** `Towns::townMap` (town.hpp:92). */
    private val townMap = sortedMapOf<Int, Town>()

    /** `Towns::addTown` (town.hpp:54-56): `emplace` keeps the first town for an id. */
    fun addTown(townId: Int, town: Town): Boolean {
        if (townMap.containsKey(townId)) {
            return false
        }
        townMap[townId] = town
        return true
    }

    /** `Towns::getTown(name)` (town.hpp:58-69), skipping the reserved id 0. */
    fun getTown(townName: String): Town? {
        for ((townId, town) in townMap) {
            if (townId == 0) {
                continue
            }

            if (townName.equals(town.getName(), ignoreCase = true)) {
                return town
            }
        }
        return null
    }

    /** `Towns::getTown(id)` (town.hpp:71-77). */
    fun getTown(townId: Int): Town? = townMap[townId]

    /** `Towns::getOrCreateTown` (town.hpp:79-85). */
    fun getOrCreateTown(townId: Int): Town {
        var town = getTown(townId)
        if (town == null) {
            town = Town(townId)
            addTown(townId, town)
        }
        return town
    }

    /** `Towns::getTowns` (town.hpp:87-89). */
    fun getTowns(): TownMap = townMap
}
