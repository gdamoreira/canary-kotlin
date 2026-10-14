# Kotlin local build validation

> Adapted from canary/docs/building/local-validation.md for the Kotlin/JVM
> engine.
> Canary-Kotlin status: adapted from the CMake/vcpkg workflow to the Gradle
> wrapper. The zero-warning `./gradlew clean test` gate replaces the CMake
> configure/build/test flow.

Use this workflow only after the active `AGENTS.md` build policy authorizes a
build. Documentation-only, script-only, and other clearly non-build-affecting
changes do not require compilation.

## Prerequisites

- A JDK 25 LTS installation.
- `JAVA_HOME` pointing at that JDK (the build declares
  `jvmToolchain(25)`).
- The repository's Gradle wrapper (`./gradlew`); no system Gradle is required.

Verify before building:

```bash
echo "$JAVA_HOME"
java -version          # must report a 25.x runtime
./gradlew -version     # Gradle 9.8.1 and the JVM used by the daemon
```

If Gradle reports a missing or wrong toolchain, fix `JAVA_HOME` first instead
of changing the build file.

## Maintained build entry points

Kotlin source files are auto-discovered by the Gradle source sets; adding,
removing or renaming `.kt` files needs no list update. The maintained build
entries are:

- `build.gradle.kts` — plugins, dependencies, toolchain and test configuration;
- `settings.gradle.kts` — project name and included builds;
- `gradle/wrapper/gradle-wrapper.properties` — Gradle version;
- `Dockerfile` — build/runtime stages (update when the toolchain changes).

When a change adds a dependency, a plugin or a new resource directory, update
the relevant entry in the same change.

## Standard validation workflow

From the repository root:

```bash
./gradlew clean test
```

The gate (AGENTS.md §4) is:

- zero test failures;
- zero compile warnings.

If warnings are buried in the output, rerun with the full console:

```bash
./gradlew clean test --console=plain
```

To run a single suite while iterating:

```bash
./gradlew test --tests 'org.opentibia.canary.<package>.<TestClass>'
```

For the runnable distribution:

```bash
./gradlew clean installDist
./build/install/canary-kotlin/bin/canary-kotlin
```

This starts the zero-configuration H2 in-memory server on ports 7171 (login)
and 7172 (game).

## Cache recovery

Gradle keeps several layers of state:

- `.gradle/` at the repository root — project-local configuration cache and
  file-system watching state;
- `build/` — task outputs, including the distribution;
- `~/.gradle/caches/` — global dependency and build-cache storage.

If a build fails after a build-script change or reports a corrupt task output:

1. Stop the daemon:

   ```bash
   ./gradlew --stop
   ```

2. Clean only the project outputs and rerun:

   ```bash
   ./gradlew clean test
   ```

3. If a build-cache entry is still suspected, bypass it once:

   ```bash
   ./gradlew clean test --no-build-cache
   ```

4. If the configuration cache is stale or incompatible after a Gradle upgrade:

   ```bash
   ./gradlew clean test --no-configuration-cache
   ```

Do not delete `~/.gradle` wholesale; it only needs attention when dependency
resolution itself is corrupted, in which case prefer:

```bash
./gradlew --refresh-dependencies installDist
```

## Interpreting failures

| Symptom | Likely cause | First action |
| --- | --- | --- |
| `No matching toolchains found ... languageVersion=25` | `JAVA_HOME` or installed JDK is not 25 | Install JDK 25 and repoint `JAVA_HOME` |
| Compile warning in build output | Zero-warning gate violated | Fix the warning; do not suppress it |
| Test failure in persistence tests | H2/PostgreSQL-mode SQL incompatibility | Fix the migration/SQL; never edit an applied Flyway migration |
| Random socket/timeout failures | Live network access attempted in a unit test | Use `EmbeddedChannel`/test seams instead |
| Configuration cache problem | Build script not configuration-cache compatible | Inspect the report; use `--no-configuration-cache` only while diagnosing |
