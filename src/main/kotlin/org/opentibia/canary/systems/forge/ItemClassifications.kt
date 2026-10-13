package org.opentibia.canary.systems.forge

/**
 * Registry of [ItemClassification]s mirroring the Exaltation Forge classification
 * store owned by Canary's `Game`:
 *
 *  - `Game::addItemsClassification` (src/game/game.hpp:208-210)
 *  - `Game::getItemsClassification(id, create)` (src/game/game.cpp:9463-9477)
 *  - `Game::getItemsClassifications` (src/game/game.hpp:591-593)
 *
 * The price tables themselves are Lua-authored
 * (`data/scripts/systems/item_tiers.lua`) and reach the store through
 * `Game.createItemClassification` plus `ItemClassification:addTier`; Kotlin only
 * owns the engine store, per AGENTS.md 1.4.
 *
 * Parity note: Canary's store starts empty and classification 0 exists only as the
 * `ItemType::upgradeClassification` default (src/items/items.hpp:329). The port
 * pre-registers it as [defaultClassification] so the forge adapter's default item
 * classification always resolves to an empty (non-forgeable) table instead of an
 * unknown id.
 */
class ItemClassifications {
    private val classifications = linkedMapOf<Int, ItemClassification>()

    /** Classification 0, the `ItemType::upgradeClassification` default. */
    val defaultClassification: ItemClassification = createClassification(DEFAULT_CLASSIFICATION_ID)

    /** Mirrors `Game::addItemsClassification`. */
    fun addClassification(classification: ItemClassification) {
        classifications[classification.id] = classification
    }

    /** Mirrors `Game::getItemsClassification(id, create = true)`. */
    fun createClassification(id: Int): ItemClassification =
        getClassification(id) ?: ItemClassification(id).also { addClassification(it) }

    /** Mirrors `Game::getItemsClassification(id, create = false)`. */
    fun getClassification(id: Int): ItemClassification? = classifications[id]

    /** Mirrors `Game::getItemsClassifications`. */
    fun getAll(): List<ItemClassification> = classifications.values.toList()

    companion object {
        const val DEFAULT_CLASSIFICATION_ID = 0
    }
}
