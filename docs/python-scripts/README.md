# Python maintenance scripts (upstream C++)

> Adapted from canary/docs/python-scripts/ (four scripts).
> Canary-Kotlin status: kept verbatim for parity maintenance. These scripts
> target the **upstream C++ sources** in `../.projects/canary/src` (and the
> upstream `data/` trees); they are not part of the Kotlin build and are **not**
> rewritten as Kotlin. Run them only against an upstream Canary checkout, never
> against this repository's Kotlin sources.

These scripts were used during the C++ engine's ongoing maintenance. They are
preserved here because the Kotlin port tracks upstream C++ changes and may need
to replay the same content transformations when synchronizing shared data.

| Script | Purpose |
| --- | --- |
| `convert_monsters_callbacks.py` | Cleans up monster Lua callback functions (removes empty callbacks, drops `onAppear`, normalizes blank lines) in upstream `data/` trees. |
| `modify_lua_script_interfaces.py` | Rewrites C++ Lua script interface classes (removes constructors/destructors and stale `LuaScriptInterface` includes). |
| `normalize_cpp_own_includes.py.py` | Normalizes a C++ file's own `#include` line to the correct relative path. |
| `remove_player_includes.py` | Removes obsolete player component includes and updates `->` member accesses after component extraction. |

## Usage

Each script documents its own usage in its header docstring. Run them from an
upstream Canary checkout (or pointed at upstream paths), with Python 3 and
read/write permissions on the target files. Always review the diff before
committing transformations to the upstream repository.

## Provenance

Each script carries a top-of-file provenance comment pointing at its upstream
source (`canary/docs/python-scripts/<name>`) and at the upstream C++ target.
