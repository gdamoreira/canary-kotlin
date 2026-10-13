package org.opentibia.canary.items

/**
 * `ContainerSpecial_t` (`src/enums/container_type.hpp:16-25`), the container
 * "special category" byte `ProtocolGame::AddItem` serializes
 * (protocolgame.cpp:816-836) and `Container::getSpecialCategory` produces
 * (container.cpp:1330-1345).
 *
 * Canary names the header `container_type.hpp`; the only enum it declares is
 * `ContainerSpecial_t`. Values 3, 5-7 and 10 are unassigned in Canary.
 */
enum class ContainerSpecial_t(val value: Int) {
    None(0),
    LootContainer(1),
    ContentCounter(2),
    LootHighlight(4),
    Obtain(8),
    Manager(9),
    QuiverLoot(11),
}
