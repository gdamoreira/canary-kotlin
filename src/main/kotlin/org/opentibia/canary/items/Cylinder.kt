package org.opentibia.canary.items

/**
 * Port of the `Cylinder` insertion seam used by `IOMapSerialize::loadItem`
 * (src/io/iomapserialize.cpp:174). The full C++ `Cylinder` contract
 * (`queryAdd`/`queryRemove`/`queryDestination`, src/items/cylinder.hpp) is not
 * ported yet; only the unchecked `internalAddThing` the deserializer calls and
 * the `Thing::isRemoved` probe `Item::isRemoved`/`Item::canDecay` read are.
 */
interface Cylinder {
    /** `Cylinder::internalAddThing` — insert bypassing capacity/query checks. */
    fun internalAddThing(item: Item)

    /**
     * `Thing::isRemoved` (src/items/thing.hpp). Unmodeled cylinders default to
     * live, mirroring `VirtualCylinder::isRemoved` (src/items/cylinder.hpp:214).
     */
    fun isRemoved(): Boolean = false
}
