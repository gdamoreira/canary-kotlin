# Rebuild on the JVM with distribution history

> Adapted from canary/docs/building/recompile.md for the Kotlin/JVM engine.
> Canary-Kotlin status: the executable-history workflow is adapted to Gradle
> distributions. Builds are incremental and daemon-based; there is no CMake,
> Ninja or vcpkg step.

Gradle rebuilds the server incrementally, installs a runnable distribution at
`build/install/canary-kotlin`, and can package archives under
`build/distributions`. Keeping a copy of each installed distribution (or its
archive) lets an operator match a crash log, heap dump or thread dump to the
exact build that produced it, even after another version has been installed.

The default rebuild happens in the working tree. It does **not** restart a
running server: a running JVM keeps using the classes loaded at startup until
the process is restarted.

- [Requirements](#requirements)
- [Usage and options](#usage-and-options)
- [Build and installation](#build-and-installation)
- [Distribution history](#distribution-history)
- [Investigating a crash](#investigating-a-crash)
- [Windows and WSL](#windows-and-wsl)
- [Validation without running the server](#validation-without-running-the-server)
- [Troubleshooting](#troubleshooting)

## Requirements

- JDK 25 LTS (`JAVA_HOME` configured) on Linux, macOS or Windows.
- The repository's Gradle wrapper (`./gradlew` / `.\gradlew.bat`).
- Enough disk space for `build/` plus any retained distributions.

Unlike the upstream C++ workflow, no compiler, CMake, Ninja or vcpkg
installation is required.

## Usage and options

Run these examples from the repository root:

```bash
# Incremental rebuild of the distribution.
./gradlew installDist

# Clean rebuild after a dependency or toolchain change.
./gradlew clean installDist

# Re-run all tasks even when Gradle considers them up to date.
./gradlew installDist --rerun-tasks

# Build the archives for release handling.
./gradlew distZip distTar

# Full gate before installing anything.
./gradlew clean test installDist
```

Useful flags:

| Flag | Meaning |
| --- | --- |
| `--no-daemon` | One-shot build; useful in CI or when a stale daemon is suspected |
| `--rerun-tasks` | Ignore up-to-date checks for this invocation |
| `--no-build-cache` | Bypass the build cache once |
| `--offline` | Resolve dependencies from the local cache only |
| `--stop` | Stop Gradle daemons for this Gradle version |

The Gradle daemon keeps Kotlin compilation warm between invocations. Restart it
after upgrading Gradle or JDK, after memory-pressure incidents, or when builds
behave inconsistently (`./gradlew --stop`).

## Build and installation

Each rebuild follows this sequence:

1. Gradle configures the project and checks task inputs (configuration cache
   when enabled).
2. Kotlin compiles incrementally; unchanged files are not recompiled.
3. Tests run if requested (`test`) or are skipped for a distribution-only build.
4. `installDist` copies the launcher and JARs to
   `build/install/canary-kotlin/`.

Layout:

```text
build/
├── install/canary-kotlin/
│   ├── bin/canary-kotlin          # launcher
│   └── lib/*.jar                  # application + dependencies
├── distributions/
│   ├── canary-kotlin-<version>.zip
│   └── canary-kotlin-<version>.tar
└── reports/tests/                  # HTML test report when tests ran
```

Run the server from the installed distribution:

```bash
./build/install/canary-kotlin/bin/canary-kotlin
```

If the server is managed by a process supervisor, point it at the launcher
script; replacing the distribution while the JVM runs has no effect until the
process restarts.

## Distribution history

The upstream `recompile.sh` kept copies of previous executables for crash
matching. The JVM equivalent is to retain the installed distribution or its
archive with build metadata.

Recommended practice:

1. Copy `build/install/canary-kotlin` (or the `distZip` archive) to a
   deployment-controlled directory, named with the version and Git commit:

   ```text
   releases/
     canary-kotlin-1.0.0-<short-sha>/
       bin/, lib/
       build.txt        # commit, JDK version, Gradle version, build timestamp
   ```

2. Record the SHA-256 of the archive and keep it with the release notes.
3. Keep at least the previous one or two releases for rollback.
4. Do not overwrite a retained release in place; create a new directory and
   switch the launcher/symlink atomically.

Retention is an operator policy, not something the build system enforces.

## Investigating a crash

A useful JVM crash investigation needs:

- `hs_err_pid<pid>.log` for a fatal JVM crash (native fault, OOM with
  `-XX:+ExitOnOutOfMemoryError`, and similar);
- a heap dump (`-XX:+HeapDumpOnOutOfMemoryError`) when memory growth is
  involved;
- thread dumps for a live but stalled process;
- the **matching distribution or archive** and the exact JVM version.

Useful commands:

```bash
jcmd <pid> Thread.print
jcmd <pid> GC.heap_info
jcmd <pid> VM.info
jstack <pid>
```

If core dumps are enabled (`ulimit -c unlimited`, `kernel.core_pattern`), a
native crash handler can attach a debugger to the core and the `hs_err` log
references the exact shared libraries. Java-level exceptions do not need core
dumps; they are captured in the server log and a thread dump is enough.

Rebuilding the same commit does not guarantee byte-identical JARs (timestamps,
dependency resolution order and JVM/toolchain differences). Prefer the
originally retained artifact.

## Windows and WSL

On Windows PowerShell, use the batch wrapper:

```powershell
.\gradlew.bat clean test installDist
.\build\install\canary-kotlin\bin\canary-kotlin.bat
```

Under WSL, use the repository from the WSL filesystem where possible:

```bash
./gradlew clean test installDist
```

Cross-filesystem builds (`/mnt/c/...`) work but are significantly slower and
can break file watching; keep the checkout inside the Linux filesystem for
regular work. See [wsl-debian.md](wsl-debian.md) and [wsl-ubuntu.md](wsl-ubuntu.md).

## Validation without running the server

Run from the repository root:

```bash
./gradlew clean test
./gradlew installDist
test -x build/install/canary-kotlin/bin/canary-kotlin
```

The test suite uses H2 in-memory and Netty `EmbeddedChannel`; it does not need
a live database or external sockets. The launcher check verifies the
distribution layout without starting listeners; starting the server with
`--config`/`CANARY_*` against H2 is the full smoke test.

## Troubleshooting

| Symptom | Meaning and next step |
| --- | --- |
| `No matching toolchains found ... languageVersion=25` | JDK 25 is missing or `JAVA_HOME` is wrong |
| Build hangs at startup | A previous daemon may be stale or under memory pressure: `./gradlew --stop`, then retry |
| Up-to-date checks hide a change | Use `--rerun-tasks` once; investigate why the input was not declared |
| Dependency resolution fails | `./gradlew --refresh-dependencies`; check network/proxy settings |
| Configuration cache failure | Run once with `--no-configuration-cache`, read the report, then fix the build script |
| Distribution starts but cannot connect to PostgreSQL | Check `CANARY_DB_*`; the default mode expects H2 unless configured |
| OOM during tests | The test task caps heap at 3g in `build.gradle.kts`; reduce parallel forks or raise the limit deliberately |
