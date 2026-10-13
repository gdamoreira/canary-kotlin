package org.opentibia.canary.items

import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.map.house.House

/**
 * Port of the serialization and sleeper state of `BedItem`
 * (src/items/bed.hpp:16-52, src/items/bed.cpp:26-95). The lifecycle methods
 * (`trySleep`/`sleep`/`wakeUp`/`regeneratePlayer`/`updateAppearance`) live in
 * `Bed.kt` and take the injected [BedWorld] seam.
 */
class BedItem(
    id: Int,
    type: ItemType? = null,
) : Item(id, type = type) {

    /** `BedItem::sleeperGUID` (src/items/bed.hpp:50), a uint32 player GUID. */
    var sleeperGUID: Long = 0
        set(value) {
            field = value and 0xFFFFFFFFL
        }

    /** `BedItem::sleepStart` (src/items/bed.hpp:49), truncated to 32 bits on the wire. */
    var sleepStart: Long = 0
        set(value) {
            field = value and 0xFFFFFFFFL
        }

    override fun getBed(): BedItem = this

    /** `BedItem::house` (src/items/bed.hpp:60). */
    var house: House? = null
        private set

    /** `BedItem::setHouse` (src/items/bed.hpp:36-38). */
    fun setHouse(h: House) {
        house = h
    }

    /** `BedItem::getSleeper` (src/items/bed.hpp:34). */
    fun getSleeper(): Long = sleeperGUID

    /** `BedItem::readAttr` (src/items/bed.cpp:38-65). */
    override fun readAttr(attr: Int, propStream: PropStream): AttrReadValue {
        when (attr) {
            ATTR_SLEEPERGUID -> {
                val guid = propStream.readU32() ?: return AttrReadValue.ATTR_READ_ERROR
                if (guid != 0L) {
                    // C++ resolves the name through IOLoginData to set the
                    // "X is sleeping there." description and calls
                    // g_game().setBedSleeper; both are PARITY GAPS.
                    sleeperGUID = guid
                }
                return AttrReadValue.ATTR_READ_CONTINUE
            }

            ATTR_SLEEPSTART -> {
                val sleepStart = propStream.readU32() ?: return AttrReadValue.ATTR_READ_ERROR
                this.sleepStart = sleepStart
                return AttrReadValue.ATTR_READ_CONTINUE
            }
        }
        return super.readAttr(attr, propStream)
    }

    /** `BedItem::serializeAttr` (src/items/bed.cpp:69-81). */
    override fun serializeAttr(propWriteStream: PropWriteStream) {
        if (sleeperGUID != 0L) {
            propWriteStream.writeU8(ATTR_SLEEPERGUID)
            propWriteStream.writeU32(sleeperGUID)
        }

        if (sleepStart != 0L) {
            propWriteStream.writeU8(ATTR_SLEEPSTART)
            propWriteStream.writeU32(sleepStart)
        }
    }

    /**
     * `BedItem::forceClearSleeper` (src/items/bed.cpp:26-36). The
     * `g_game().removeBedSleeper`/`updateAppearance` half is the
     * `Bed.kt` extension taking a `BedWorld`; this state-only overload remains
     * for the `IOMapSerialize` bed-fix path.
     */
    fun forceClearSleeper() {
        internalRemoveSleeper()
    }

    /** `BedItem::isBedComplete` (src/items/bed.cpp:135-149). */
    fun isBedComplete(nextBedItem: BedItem?): Boolean {
        if (nextBedItem == null) {
            return false
        }
        return (type?.bedPartOf ?: 0) == nextBedItem.id
    }
}
