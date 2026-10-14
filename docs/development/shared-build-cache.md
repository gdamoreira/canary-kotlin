# Shared build cache for worktrees and forks (Gradle)

> Adapted from canary/docs/development/shared-build-cache.md for the Kotlin/JVM
> engine.
> Canary-Kotlin status: the vcpkg/CMake/sccache pool is replaced by Gradle's
> dependency cache, build cache and configuration cache. Gradle handles most
> cache isolation automatically; the rules below describe safe sharing across
> worktrees and forks.

Related Gradle projects can share expensive reusable artifacts across Git
worktrees and independent forks without sharing mutable build directories. The
shared pool must live on a local filesystem outside every source and build
hierarchy, so removing one checkout cannot remove another checkout's caches.

## Cache ownership

| Layer | Ownership | Reason |
| --- | --- | --- |
| `build/` outputs | One per worktree | Task outputs, Kotlin incremental caches, test reports and the installed distribution contain consumer-specific paths and state. |
| `.gradle/` project state | One per worktree | Configuration cache and file-system watching state are project-local and path-sensitive. |
| Dependency cache (`~/.gradle/caches/modules-2`) | Global | Dependencies are content-addressed by module coordinates and checksums; identical versions are reused safely. |
| Wrapper distribution (`~/.gradle/wrapper/dists`) | Global | The Gradle distribution is version-addressed and immutable in use. |
| Build cache (`~/.gradle/caches/build-cache-1` or a configured directory) | Global per user or machine | Task outputs are keyed by task implementation, inputs, classpath and output properties; identical work is reusable. |
| Kotlin compiler daemon | Per user | Keeps compilation warm; restart after JDK/Gradle upgrades. |
| Gradle daemon | One per Gradle/JVM version and user | Reuses the JVM and configuration; restart with `./gradlew --stop`. |

Never junction, symlink or otherwise share an entire `build/` or `.gradle/`
directory manually. Do not place a shared cache root inside a primary worktree.

## Enabling caches

The build cache and configuration cache are opt-in for this repository. Enable
them per user in `~/.gradle/gradle.properties`, or per invocation:

```bash
./gradlew clean test --build-cache --configuration-cache
```

Recommended local settings:

```properties
# ~/.gradle/gradle.properties
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.parallel=true

# Optional: keep the build cache outside every checkout
org.gradle.caching.local.directory=/path/to/local/gradle-build-cache
```

Notes:

- Keep the cache directory on a local filesystem with working file locks; do
  not place it on a network share.
- The configuration cache is invalidated when build scripts or plugin versions
  change; that is expected and safe.
- `kotlin.incremental=true` is the default. Do not disable it to force cache
  hits; use `--rerun-tasks` only when diagnosing.

## Worktrees

Each worktree keeps its own `build/` and `.gradle/` directories. Dependency
resolution, wrapper distributions and the build cache are shared through the
Gradle user home, which is safe because entries are keyed by coordinates and
content.

Rules:

- Never run two builds that write to the same `build/` directory at once; use
  separate worktrees for parallel work.
- When worktrees live under different absolute paths, task outputs are still
  cache-reusable as long as paths are not part of the task inputs. Tasks that
  embed absolute paths (for example generated manifests) will simply miss the
  cache; that is not a regression.
- Prefer `--project-cache-dir` only when a tool requires isolation; do not point
  multiple worktrees at one project cache directory.

## Independent forks

Forks share the same Gradle user home and build cache when they build on the
same machine. Sharing is safe because cache entries include the task
implementation hash, classpath and inputs. A fork with a different Kotlin or
Gradle version will reuse only compatible entries.

A remote build cache (Develocity or another Gradle-compatible backend) is not
configured in this repository. If one is added, keep credentials out of the
repository, use read-only or scoped tokens for CI consumers, and document the
trust boundary. Never push cache entries produced from untrusted code to a
shared remote cache.

## Normal configure and build

Continue using the wrapper:

```bash
./gradlew clean test
./gradlew installDist
```

With caches enabled, repeated builds should show `FROM-CACHE` or `UP-TO-DATE`
for unchanged tasks. Verify with:

```bash
./gradlew test --info | grep -E "FROM-CACHE|UP-TO-DATE"
```

## Concurrency and cleanup

- Do not prune caches while Gradle, the Kotlin daemon or a running server is
  using them.
- Different projects writing to the same local build cache are safe; Gradle
  handles locking.
- To reclaim space, delete the oldest entries under
  `~/.gradle/caches/build-cache-1` when no build is running, or point
  `org.gradle.caching.local.directory` at a dedicated cache volume and manage it
  as such.
- To recover from a corrupt cache entry:

  ```bash
  ./gradlew --stop
  ./gradlew clean test --no-build-cache
  ```

  Then re-enable the cache. Do not delete dependency caches unless resolution
  itself fails; prefer `./gradlew --refresh-dependencies`.

## CI and production boundaries

- CI should use `--build-cache` (and a remote cache only when explicitly
  configured) and `--no-daemon` for deterministic cleanup.
- Cache setup must not change deployment directories, runtime data, production
  services or release publication behavior.
- The installed distribution and any distribution archives are build outputs,
  not cache entries; retain them as release artifacts (see
  [../release.md](../release.md)).

## Regression checklist

Before changing build or cache configuration, verify:

- `build/` and `.gradle/` remain local to each worktree;
- no machine-local path appears in committed build scripts or documentation;
- the build cache directory is on a local filesystem outside every checkout;
- configuration-cache failures are fixed rather than permanently disabled;
- `./gradlew clean test` still passes with caches disabled
  (`--no-build-cache --no-configuration-cache`).
