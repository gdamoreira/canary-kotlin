package org.opentibia.canary.items

/**
 * Compatibility alias for the earlier Kotlin name of the Canary `Items` registry
 * (`src/items/items.hpp:389`). The registry now lives in `Items.kt` with the exact
 * Canary class name; existing call sites keep compiling through this alias.
 */
typealias ItemTypeRegistry = Items
