package org.opentibia.canary.items

/**
 * `ObjectCategory_t` (`src/enums/object_category.hpp:12-45`), the managed-loot
 * category byte `ProtocolGame` reads from the quick-loot/managed-container
 * opcodes (protocolgame.cpp:2628-2712) and `Game::getObjectCategory`
 * (game.hpp:248-249) assigns to items.
 *
 * Values 26, 28 and 29 are unassigned in Canary. `OBJECTCATEGORY_FIRST` and
 * `OBJECTCATEGORY_LAST` (object_category.hpp:43-44) are aliases of
 * `OBJECTCATEGORY_ARMORS` and `OBJECTCATEGORY_DEFAULT`; they are modelled as
 * aliases so equality matches Canary's numeric comparison and iteration stays
 * over distinct values.
 */
enum class ObjectCategory_t(val value: Int) {
    OBJECTCATEGORY_NONE(0),
    OBJECTCATEGORY_ARMORS(1),
    OBJECTCATEGORY_NECKLACES(2),
    OBJECTCATEGORY_BOOTS(3),
    OBJECTCATEGORY_CONTAINERS(4),
    OBJECTCATEGORY_DECORATION(5),
    OBJECTCATEGORY_FOOD(6),
    OBJECTCATEGORY_HELMETS(7),
    OBJECTCATEGORY_LEGS(8),
    OBJECTCATEGORY_OTHERS(9),
    OBJECTCATEGORY_POTIONS(10),
    OBJECTCATEGORY_RINGS(11),
    OBJECTCATEGORY_RUNES(12),
    OBJECTCATEGORY_SHIELDS(13),
    OBJECTCATEGORY_TOOLS(14),
    OBJECTCATEGORY_VALUABLES(15),
    OBJECTCATEGORY_AMMO(16),
    OBJECTCATEGORY_AXES(17),
    OBJECTCATEGORY_CLUBS(18),
    OBJECTCATEGORY_DISTANCEWEAPONS(19),
    OBJECTCATEGORY_SWORDS(20),
    OBJECTCATEGORY_WANDS(21),
    OBJECTCATEGORY_PREMIUMSCROLLS(22),
    OBJECTCATEGORY_TIBIACOINS(23),
    OBJECTCATEGORY_CREATUREPRODUCTS(24),
    OBJECTCATEGORY_QUIVERS(25),
    OBJECTCATEGORY_FISTWEAPONS(27),
    OBJECTCATEGORY_GOLD(30),
    OBJECTCATEGORY_DEFAULT(31),
    ;

    companion object {
        /** `OBJECTCATEGORY_FIRST = OBJECTCATEGORY_ARMORS` (object_category.hpp:43). */
        val OBJECTCATEGORY_FIRST: ObjectCategory_t = OBJECTCATEGORY_ARMORS

        /** `OBJECTCATEGORY_LAST = OBJECTCATEGORY_DEFAULT` (object_category.hpp:44). */
        val OBJECTCATEGORY_LAST: ObjectCategory_t = OBJECTCATEGORY_DEFAULT
    }
}
