# Canary-Kotlin Release Flow

> Adapted from canary/docs/release.md for the Kotlin/JVM engine.
> Canary-Kotlin status: adapted to Gradle distribution artifacts and the Docker
> image. The upstream C++ release automation (`release.yml`, materialized
> metadata tags, `otservbr.otbm` asset copy, metadata sync PR) does not exist in
> this repository, so the corresponding steps are manual and described below.

Canary Kotlin releases are published from stable SemVer tags such as `v1.0.0`.
The tag is the source of truth for the release version; the project version in
`build.gradle.kts` (`version = "1.0.0-SNAPSHOT"`) is the development version
until a release commit sets it.

## Publish

Create and push the tag from the `main` commit you want to release. For a
normal release, update your local `main` first:

```bash
git switch main
git pull --ff-only origin main
git fetch --force --tags origin
git tag -a v1.0.0 -m v1.0.0
git push origin v1.0.0
```

Before tagging, run the release gate and produce the artifacts:

```bash
./gradlew clean test
./gradlew distZip distTar
docker build -t <registry>/canary-kotlin:v1.0.0 .
```

After the tag is pushed, publish a GitHub release from the tag and attach:

- `build/distributions/canary-kotlin-<version>.zip`
- `build/distributions/canary-kotlin-<version>.tar`
- the built Docker image tag (or a registry digest reference)
- release notes derived from the commit range

Unlike upstream, this repository does not rewrite release-only metadata or force
move the tag; GitHub's automatic source archives always match the tagged
commit.

## Choosing The Next Version

Use the smallest SemVer bump that accurately describes the user, operator and
developer impact since the previous release.

- Patch, for example `v1.0.0` to `v1.0.1`: bug fixes, crash fixes,
  non-breaking build/documentation fixes, world-data fixes, or small
  compatibility corrections that do not add a new public feature and do not
  require operators to change configuration, schema, clients or deployment
  steps.
- Minor, for example `v1.0.0` to `v1.1.0`: new backwards-compatible gameplay
  features, new systems, new optional configuration, new supported
  protocol/client functionality, compatible Flyway migrations, new
  Docker/operator convenience, or meaningful content additions.
- Major, for example `v1.0.0` to `v2.0.0`: breaking configuration or deployment
  changes, required manual operator migration, incompatible database/storage
  changes, removed Lua/Kotlin APIs, removed supported behavior, protocol changes
  that make existing clients or integrations incompatible, or any change where a
  server owner should expect a migration project instead of a routine update.

When a release contains both fixes and features, choose the highest required
bump. When unsure between patch and minor, prefer minor if users or operators
will notice new behavior. When unsure between minor and major, choose major only
when an existing supported setup can break without a deliberate migration.

## Tagging Tutorial

This is the normal maintainer flow for publishing a new release:

1. Inspect the changes since the latest stable release tag.
2. Choose the next SemVer tag.
3. Run `./gradlew clean test` and build the artifacts.
4. Push the new annotated tag to GitHub.
5. Publish the GitHub release and attach the artifacts.
6. Confirm the Docker image tag/digest matches the release commit.

First inspect the range since the latest stable release tag.

On Bash:

```bash
git fetch origin main --tags
previous_tag="$(git tag --sort=-version:refname --list 'v[0-9]*.[0-9]*.[0-9]*' | head -n 1)"
git log --oneline --decorate --first-parent "${previous_tag}..origin/main"
git diff --name-only "${previous_tag}..origin/main"
```

On PowerShell:

```powershell
git fetch origin main --tags
$previousTag = git tag --sort=-version:refname --list "v[0-9]*.[0-9]*.[0-9]*" | Select-Object -First 1
git log --oneline --decorate --first-parent "$previousTag..origin/main"
git diff --name-only "$previousTag..origin/main"
```

Then choose the next version:

- Patch: increment only the third number, for example `v1.0.0` to `v1.0.1`.
- Minor: increment the second number and reset patch to zero, for example
  `v1.0.0` to `v1.1.0`.
- Major: increment the first number and reset minor and patch to zero, for
  example `v1.0.0` to `v2.0.0`.

After choosing the version, create and push an annotated tag:

```bash
git switch main
git pull --ff-only origin main
git fetch --force --tags origin
git tag -a v1.1.0 -m v1.1.0
git push origin v1.1.0
```

PowerShell uses the same Git commands.

Pushing the tag does not start automation in this repository; the maintainer
completes the steps above. Do not reuse an already published tag for a different
release.

## Version References

The project version lives in `build.gradle.kts`. The port also contains a
map-download compatibility constant:

- `MapDownload.SERVER_RELEASE_VERSION` (`src/main/kotlin/.../map/cache/MapDownload.kt`)
  tracks the upstream Canary release tag used when validating `mapDownloadUrl`.
  Update it only when the upstream map release contract changes; it is not the
  Canary-Kotlin release version.

If the supported Tibia protocol version changes, update the client profile
definitions under `src/main/kotlin/org/opentibia/canary/network/protocol/` and
the related tests before tagging.

## AI Version Prompt

Use this prompt when a maintainer wants help choosing the next tag. Paste the
command output from the tagging tutorial into the placeholders.

```markdown
You are helping choose the next Canary-Kotlin release version.

Previous release tag: <previous tag>
Candidate release target: origin/main
Commit range: <previous tag>..origin/main

Use this SemVer policy:
- PATCH for bug fixes, crash fixes, non-breaking build/release fixes,
  documentation updates, world-data fixes, or compatibility corrections that do
  not require config, schema, client, deployment, or operator changes.
- MINOR for backwards-compatible gameplay features, new systems, optional
  config, new supported protocol/client functionality, compatible migrations,
  Docker/operator convenience, or meaningful content additions.
- MAJOR for breaking config/deployment changes, required manual migrations,
  incompatible database/storage changes, removed APIs, removed supported
  behavior, or protocol/client incompatibility.

Analyze these inputs:

Commit log:
<paste git log --oneline --decorate --first-parent output>

Changed files:
<paste git diff --name-only output>

Return:
1. Recommended next tag.
2. Whether the bump is patch, minor, or major.
3. The concrete commits or file areas that justify the bump.
4. Any release risks or manual notes maintainers should check.
5. The exact tag commands to run.
```

## Validation

For local checks, run:

```bash
./gradlew clean test
./gradlew installDist
docker compose build
```

Validation expectations:

- `./gradlew clean test` is green with zero warnings.
- The distribution starts and reaches the server-online log (H2 in-memory mode).
- The Docker image builds from the release commit.
- Flyway migrations apply cleanly against a fresh PostgreSQL 16 database.

## Assets

Attach to each GitHub release:

- `canary-kotlin-<version>.zip` and `.tar` from `build/distributions/`
- Docker image tag/digest
- Release notes

The upstream release assets (`canary-linux-release.zip`, Windows solution zips,
`canary-docker.zip`, `otservbr.otbm`) are C++ build outputs and are not produced
by this repository.
