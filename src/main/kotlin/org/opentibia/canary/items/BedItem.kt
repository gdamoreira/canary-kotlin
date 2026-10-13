package org.opentibia.canary.items

import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream

/**
 * Port of the serialization and sleeper state of `BedItem`
 * (src/items/bed.hpp:16-52, src/items/bed.cpp:26-95). `wakeUp`/`sleep`/
 * `trySleep` and `getNextBedItem` need the game engine and remain in
 * `IOMapSerialize`'s seams (PARITY GAP).
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

    /** `BedItem::forceClearSleeper` (src/items/bed.cpp:26-36); `g_game().removeBedSleeper` is a PARITY GAP. */
    fun forceClearSleeper() {
        sleeperGUID = 0
        sleepStart = 0
        setAttribute(ItemAttribute.DESCRIPTION, "Nobody is sleeping there.")
    }

    /** `BedItem::isBedComplete` (src/items/bed.cpp:135-149). */
    fun isBedComplete(nextBedItem: BedItem?): Boolean {
        if (nextBedItem == null) {
            return false
        }
        return (type?.bedPartOf ?: 0) == nextBedItem.id
    }
}
