package org.opentibia.canary.creatures.appearance.effects

/**
 * Common classification for the four attached-effect entry kinds loaded from
 * `data/XML/attachedeffects.xml`.
 *
 * Mirrors the four structs declared in Canary
 * `src/creatures/appearance/attached_effects/attached_effects.hpp`: `Aura`,
 * `Shader`, `Effect` and `Wing`. Each struct exposes the same `id`/`name`
 * fields, so the Kotlin shape keeps that shared contract as a sealed hierarchy
 * while preserving the exact XML tag that produced every entry.
 */
sealed interface AttachedEffect {
    val id: Int
    val name: String
}

/** Mirrors `struct Aura` (attached_effects.hpp:12): `uint16_t id`, `std::string name`. */
data class Aura(override val id: Int, override val name: String) : AttachedEffect

/** Mirrors `struct Shader` (attached_effects.hpp:19): `uint8_t id`, `std::string name`. */
data class Shader(override val id: Int, override val name: String) : AttachedEffect

/** Mirrors `struct Effect` (attached_effects.hpp:26): `uint16_t id`, `std::string name`. */
data class Effect(override val id: Int, override val name: String) : AttachedEffect

/** Mirrors `struct Wing` (attached_effects.hpp:33): `uint16_t id`, `std::string name`. */
data class Wing(override val id: Int, override val name: String) : AttachedEffect
