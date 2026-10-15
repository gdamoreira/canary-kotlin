package org.opentibia.canary.config

/**
 * Which protocol listeners a server process binds (wave-75 scaling phase 1).
 *
 * This is a Kotlin bootstrap extension with no Canary C++ counterpart: upstream
 * Canary always runs the login protocol and the game protocol in one process
 * (`CanaryServer::run`, src/canary_server.cpp). The port keeps that as
 * [ALL] and adds the [LOGIN]/[GAME] deployment roles so the DB-mediated session
 * handoff can scale login instances separately.
 */
enum class ServerRole {
    /** Canary's single-process behavior: login/status + game listeners. */
    ALL,

    /** Login instances: binds the login/status listener only, no world boot. */
    LOGIN,

    /** Game instances: boots the world, binds the game listener only. */
    GAME
}
