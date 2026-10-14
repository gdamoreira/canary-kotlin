package org.opentibia.canary.creatures.players.components

/**
 * Mutable [BadgeRegistry] owned by `PlayerFeatures.badge`.
 *
 * `Game::m_badges` (game.cpp:570-597) is a `std::unordered_set<Badge>` seeded before
 * the first player logs in; `PlayerFeatures` cannot receive that global registry by
 * injection yet, so `WorldPlayerExtraOperations` adopts the composition root's
 * entries through [register] on first touch (`AchievementRegistryAdopter` pattern,
 * game.cpp:12536-12564). Iteration keeps the registration order so
 * [PlayerBadge.checkAndUpdateNewBadges] stays deterministic.
 */
class MutableBadgeRegistry(initial: List<Badge> = emptyList()) : BadgeRegistry {

    private val badges = initial.toMutableList()

    /** `Game::m_badges` insertion (game.cpp:570-597); id 0 is the invalid sentinel. */
    fun register(badge: Badge) {
        if (badge.id == 0 || badges.any { it.id == badge.id }) {
            return
        }
        badges.add(badge)
    }

    override fun getBadgeById(id: Int): Badge {
        if (id == 0) {
            return Badge()
        }
        return badges.firstOrNull { it.id == id } ?: Badge()
    }

    override fun getBadgeByName(name: String): Badge {
        if (name.isEmpty()) {
            return Badge()
        }
        return badges.firstOrNull { it.name == name } ?: Badge()
    }

    override fun getBadges(): List<Badge> = badges
}

/**
 * Mutable [TitleRegistry] owned by `PlayerFeatures.title`.
 *
 * Mirror of [MutableBadgeRegistry] over `Game::m_titles` (game.cpp:599-703,
 * 12566-12594): the composition root's registry is adopted into this catalog the
 * first time a player is reached.
 */
class MutableTitleRegistry(initial: List<Title> = emptyList()) : TitleRegistry {

    private val titles = initial.toMutableList()

    /** `Game::m_titles` insertion (game.cpp:599-703); id 0 is the invalid sentinel. */
    fun register(title: Title) {
        if (title.id == 0 || titles.any { it.id == title.id }) {
            return
        }
        titles.add(title)
    }

    override fun getTitleById(id: Int): Title {
        if (id == 0) {
            return Title()
        }
        return titles.firstOrNull { it.id == id } ?: Title()
    }

    override fun getTitleByName(name: String): Title {
        if (name.isEmpty()) {
            return Title()
        }
        return titles.firstOrNull { it.maleName == name } ?: Title()
    }

    override fun getTitles(): List<Title> = titles
}
