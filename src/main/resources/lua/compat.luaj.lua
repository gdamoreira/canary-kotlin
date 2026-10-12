-- Canary-Kotlin LuaJ compatibility shim.
-- Provides LuaJIT-only globals that the Canary Lua libraries rely on.
-- Lua logic stays Lua (AGENTS.md 1.4); this file is infrastructure only.

__metatables__ = __metatables__ or {}

-- LuaJIT extension used by data/libs/functions/revscriptsys.lua.
function rawgetmetatable(name)
    return __metatables__[name]
end
