-- Canary-Kotlin LuaJ compatibility shim.
-- Provides LuaJIT-only globals that the Canary Lua libraries rely on.
-- Lua logic stays Lua (AGENTS.md 1.4); this file is infrastructure only.

__metatables__ = __metatables__ or {}

-- LuaJIT extension used by data/libs/functions/revscriptsys.lua. Auto-vivifies so
-- that rawgetmetatable(name) is never nil for a type the libraries mutate, matching
-- Canary where every class metatable is pre-registered in C++.
function rawgetmetatable(name)
    local metatable = __metatables__[name]
    if metatable == nil then
        metatable = {}
        __metatables__[name] = metatable
    end
    return metatable
end
