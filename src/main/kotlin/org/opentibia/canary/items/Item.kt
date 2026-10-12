package org.opentibia.canary.items

data class ItemType(
    val id: Int,
    val name: String,
    val isStackable: Boolean = false,
    val isContainer: Boolean = false,
    val capacity: Int = 0,
    val weight: Double = 0.0,
    val isPickupable: Boolean = true
)

class ItemTypeRegistry {
    private val types = mutableMapOf<Int, ItemType>()

    fun register(itemType: ItemType) {
        types[itemType.id] = itemType
    }

    fun get(id: Int): ItemType? = types[id]
}

open class Item(
    val id: Int,
    var count: Int = 1,
    val type: ItemType? = null
) {
    open val weight: Double
        get() = (type?.weight ?: 0.0) * if (type?.isStackable == true) count else 1

    companion object {
        fun create(id: Int, count: Int = 1, registry: ItemTypeRegistry? = null): Item {
            val type = registry?.get(id)
            return if (type?.isContainer == true) {
                Container(id, capacity = type.capacity, type = type)
            } else {
                Item(id, count = count, type = type)
            }
        }
    }
}

class Container(
    id: Int,
    val capacity: Int,
    type: ItemType? = null
) : Item(id, count = 1, type = type) {
    private val _items = mutableListOf<Item>()
    val items: List<Item> get() = _items

    val totalWeight: Double
        get() = (type?.weight ?: 0.0) + _items.sumOf { it.weight }

    fun addItem(item: Item): Boolean {
        if (_items.size >= capacity) return false
        _items.add(item)
        return true
    }

    companion object {
        fun create(id: Int, capacity: Int, registry: ItemTypeRegistry? = null): Container {
            val type = registry?.get(id)
            return Container(id, capacity = capacity, type = type)
        }
    }
}
