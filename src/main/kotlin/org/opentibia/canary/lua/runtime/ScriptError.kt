package org.opentibia.canary.lua.runtime

class ScriptError(
    message: String,
    val chunkName: String,
    val line: Int
) : RuntimeException(message)
