# Wave 60 Plan — Repository Improvements (`docs/development/improvements.md`)

**Status:** Completed (2026-10-14) — all 8 improvements merged and pushed
**Decisions (user):** I2 keep upstream download URLs annotated · I7 copy datapack verbatim · I4 full 45-file port · I8 keep root compose + add docker/ guides · I5 **MIT license** (user-elected; upstream Canary is GPL-2.0 — provenance noted in README acknowledgments) · Q1-Q7 resolved as listed
**Source of requirements:** `docs/development/improvements.md` (user-authored)
**Baseline:** `main` @ `cf6496d`; 689 suites / 7620 tests green; zero Kotlin warnings
**Rules from improvements.md:** every improvement documented in `docs/`; **each improvement committed as a separate commit**

---

## 0. Summary of the 8 improvements

| # | Improvement | Scope | Commit type |
|---|---|---|---|
| I1 | Remove local absolute path references | 59 tracked files | docs/chore |
| I2 | Point project references at `gdamoreira/canary-kotlin` | 10 tracked files + 1 binary | chore |
| I3 | Move hardcoded RSA keys to configuration | 4 source files + tests | feat |
| I4 | Port and adapt `canary/docs` → `docs/` | 45 upstream files → curated port | docs |
| I5 | README parity sections (12 requested) | `README.md` | docs |
| I6 | GitHub CI/CD pipeline | new `.github/workflows/` | ci |
| I7 | Copy `data-otservbr-global` datapack | ~38 MB | data |
| I8 | Full `docker/` delivery + guides | compose, env, guides | build |

Execution order = above (I1/I2 first: they rewrite files I4/I5 add; I7/I8 late: heaviest artifacts).
All items are **sequential on `main`** (they overlap files; parallel worktrees would conflict).

---

## I1 — Remove local absolute path references

**Requirement:** "Remove all reference to local files `/Users/<name>` for example. Consider the root path as `../.projects/`."

**Evidence (measured):** 59 tracked files contain the literal home prefix; ~60 occurrences.

- `AGENTS.md` (§5.1 template: "Canary Repository Root: `/Users/<name>/.projects/canary`")
- `docs/development/TEMPLATE_FEATURE.md` (same template)
- `docs/development/done/050-…` – `docs/development/done/064-…` (15 feature docs)
- `docs/development/missing-features.md`, `docs/development/improvements.md`
- `src/main`: `systems/hireling/Hireling.kt`, `systems/hireling/HirelingRegistry.kt`, `systems/rewardchest/{ReturnValue,Reward,RewardChest}.kt` (KDoc "Source:" lines)
- ~35 test files (KDoc "Canary source of truth / Source of truth / Source" lines)

**Patterns seen:** bare root (`…/.projects/canary`), root + `src/...:line` refs, template field, prose sentences.

**Task:**
1. Mechanical sweep: `/Users/<name>/.projects/canary` → `../.projects/canary`, preserving subpaths and `:line` suffixes.
2. Update `AGENTS.md` §5.1 and `docs/development/TEMPLATE_FEATURE.md` so the **convention is relative going forward** (prevents recurrence).
3. Reword the `docs/development/improvements.md` directive line to use `/Users/<name>` placeholder (self-referential literal otherwise defeats the check).

**Validation:**
- `git grep -nE "Users/[A-Za-z0-9._-]+"` → **0 matches**.
- `./gradlew clean test` green (KDoc-only change, still gate it).

**Commit:** `chore(docs): use relative ../.projects paths for Canary source references`

---

## I2 — Point project references at `gdamoreira/canary-kotlin`

**Requirement:** "Change all `https://github.com/opentibiabr/canary` references to `https://github.com/gdamoreira/canary-kotlin`."

**Evidence (measured, `git grep -l opentibiabr`):**

| File | Occurrences | Kind | Decision needed |
|---|---|---|---|
| `README.md` | 2 | badge + link | Branding → change |
| `MapDownload.kt` | 3 | **functional download URLs** (`config.lua.dist` raw @ v3.6.1/main; map release base) | Q1 |
| `MapDownloadTest.kt` | 7 | test fixtures for same URLs | follows Q1 |
| `StatusHandler.kt` | 2 | XML `url=` + owner email | Branding → change |
| `Webhook.kt` | 1 | user-agent string | Branding → change |
| `V2__seed_test_accounts.sql` | 1 | `test@opentibiabr.com` seed email | Low impact → change |
| `OpcodeApplicationMarketRouterTest.kt` | 1 | test string | Check context → change |
| `data-canary/scripts/actions/objects/world_board.lua` | 2 | datapack Lua text (`github.com/opentibiabr` org URL, donation link) | **Gameplay content** → Q1 |
| `data-canary/world/canary.otbm` | 1 (binary) | string inside the map binary | Do not edit binary → leave |
| `docs/development/improvements.md` | 1 | the directive itself | Leave |

**Decision (Q1):** functional map/config download URLs in `MapDownload.kt` fetch artifacts that only exist on the upstream repo. Pointing them at the fork breaks downloads unless the fork hosts releases. Options: keep upstream for the *data source* with an explicit comment, or fork-all + publish artifacts.

**Task:** change branding/docs/tests per Q1; keep upstream only where it is a live artifact source and then annotate with a comment explaining why.

**Validation:** `git grep -n opentibiabr` equals the reviewed allow-list; `MapDownloadTest` + `StatusHandler` tests green.

**Commit:** `chore(repo): point project references at gdamoreira/canary-kotlin`

---

## I3 — Move hardcoded RSA keys to configuration

**Requirement:** "Hardcoded RSA encryption keys should be moved to a configuration file."

**Evidence (measured):**
- `network/crypto/RsaCipher.kt`: `companion.DEFAULT_P` / `DEFAULT_Q` (1024-bit decimal strings, the OpenTibia/Canary textbook-RSA key pair), `E = 65537`, `BLOCK_SIZE = 128`; constructor already accepts `pString`/`qString`.
- Default-constructed at: `network/protocol/GameHandler.kt:52`, `network/protocol/LoginHandler.kt:23`, `network/CanaryServer.kt:82`.
- 8 test call sites rely on defaults (`CryptoTest`, `LoginProtocolTest` ×2, `GameProtocolTest`, `GameHandlerLoginLoadTest`, `GameHandlerGameWiringTest`, `CanaryServerSharedWorldStateTest`).

**Task (TDD):**
1. New config keys `CANARY_RSA_P` / `CANARY_RSA_Q` (env + `--config` properties, same layering as `ConfigManager.load`: defaults < env < properties < CLI).
2. Defaults remain the current Canary key material, so parity is unchanged when unset.
3. Wire through composition: `CanaryServer` reads config and passes explicit `RsaCipher(p, q)` into `LoginHandler`/`GameHandler`.
4. Never log key material; invalid key config fails fast with a clear error.
5. Tests: RED first — overriding env changes `n` (decrypt of `encrypt()` still round-trips); defaults unchanged; invalid key rejected.

**Validation:** `./gradlew clean test` green; zero warnings.

**Commit:** `feat(config): make RSA key material configurable (CANARY_RSA_*)`

---

## I4 — Port and adapt `canary/docs` into `docs/`

**Requirement:** "Documentation in `canary/docs` repository should be copied to this repository and updated to reflect the changes made to the codebase."

**Evidence (measured):** upstream `../.projects/canary/docs` = **45 files / 1.2 MB**:

- `README.md` (index), `architecture.md`, `operations.md`, `hardware-sizing.md`, `development.md`, `release.md`, `crypto-backend-evaluation.md`
- `building/` (8 files — Debian/Ubuntu/WSL/Windows CMake + local-validation + recompile; all C++-specific)
- `development/shared-build-cache.md`, `maps/startup-loading-performance.md`
- `docker/quickstart-for-beginners.md`
- `lua-api/` (4 generated files: `.d.lua`, `.json`, `.md`, quality baseline)
- `python-scripts/` (4 C++ maintenance scripts), `Doxyfile.in`, `config.ld.in`, logos
- `systems/` (17 files, incl. `multiprotocol.md`, `lua-shared-userdata.md`, `performance-lifetime/*`, `taskboard-module.md`, `weapon-proficiency.md`, `content-reference-auditor.md`)

**Task (curated port — Q3):**
1. Add `docs/README.md` (documentation index) and adapt: `architecture.md` (JVM/C Kotlin architecture), `operations.md`, `hardware-sizing.md`, `development.md`, `release.md` (Gradle dist), `docker/quickstart-for-beginners.md`.
2. Port `docs/systems/*` engine docs already referenced by completed waves (keep prose, mark Kotlin status where behaviors are ported).
3. Replace C++ `building/*` with Kotlin/Gradle equivalents (`docs/building/kotlin-local-validation.md` etc.); skip `python-scripts/`, `Doxyfile.in`, `config.ld.in` (C++ tooling), or port only if useful for Lua API docs.
4. Every ported file carries an "Adapted from `canary/docs/<path>`" provenance line and uses the I1 relative-path convention.

**Validation:** index links resolve; `git grep -nE "Users/[A-Za-z0-9._-]+"` still 0; no stale C++ build claims.

**Commit:** `docs: port and adapt canary documentation for the Kotlin engine`

---

## I5 — README parity and requested sections

**Requirement:** README compatible with the original `canary` README, updated for this codebase, with sections: installation, contributing, license, troubleshooting, code of conduct, security policy, roadmap, future improvements, known issues, FAQ, acknowledgments, architecture explanation.

**Evidence:** current `README.md` (10.3 KB) has: intro, architectural adaptations table, Getting Started, Docker Infrastructure, Project Structure, Parity & Verification, Lua Policy, Feature Coverage, Implementation Coverage. **None of the 12 requested sections exist**; badge/link point at `opentibiabr/canary`.

**Task:** add a table of contents and all 12 sections; keep existing current content and truthful metrics; link architecture to `docs/architecture.md` (I4); license section reflects the repo `LICENSE` (I5 dependency: add `LICENSE` — see Q5).

**Validation:** section checklist (12/12); relative links resolve.

**Commit:** `docs(readme): add installation, governance and operational sections`

---

## I6 — GitHub CI/CD pipeline

**Requirement:** "Github CI/CD pipeline should be set up to automate the test and build."

**Evidence:** **no `.github/` directory** in this repo. Upstream has 23 workflows; only the build/test pattern is relevant (Lua smoke tests, vcpkg and MySQL schema checks are C++-only).

**Task:**
1. `.github/workflows/ci.yml`: on push `main` + PRs — `actions/setup-java` Temurin **25**, Gradle cache (`gradle/actions/setup-gradle`), `./gradlew clean test`, upload JUnit XML + HTML reports on failure.
2. Optional `.github/workflows/docker.yml`: build `Dockerfile` on PR touching `Dockerfile|docker/**` (no push, no registry auth).
3. No invented jobs (no Sonar, no release automation) — add later if required.

**Validation:** YAML lint; first CI run green on push; suite mirrors local counts.

**Commit:** `ci: add GitHub Actions build and test pipeline`

---

## I7 — Add the `data-otservbr-global` datapack

**Requirement:** "`canary` repository contains `data-otserv-global` directory containing files for the game server and should be copied to this repository."

**Evidence (measured):**
- Upstream directory is actually named **`data-otservbr-global`** (38 MB) — the playable global datapack.
- This repo already ships `data/` (9.3 MB shared engine Lua/XML/items) and `data-canary/` (19 MB: lib, monster, npc, raids, scripts, world `canary.otbm`, migrations).
- The global datapack (its own world `.otbm`, scripts, NPCs, spawns) is absent.

**Task (Q2):** copy `../.projects/canary/data-otservbr-global/` **verbatim** (Lua stays Lua — zero content edits), verify boot can select it as datapack, document it in `docs/` and README project structure.

**Validation:** boot smoke with datapack selected; `./gradlew clean test` green; repo size note documented.

**Risk:** +38 MB git history; confirm whether world `.otbm` files should instead be fetched on boot via `MapDownload` (existing capability) — Q2.

**Commit:** `data: add data-otservbr-global playable datapack`

---

## I8 — Full Docker delivery and guides

**Requirement:** "A full `docker-compose.yml` should be created to automate the deployment … It has been created in the `docker` directory. All necessary data, configuration files, and environment variables should be included. All guides in `canary` repository should be copied to this repository and updated accordingly."

**Evidence (measured):**
- Root `docker-compose.yml` exists (Postgres 16 + server build-from-Dockerfile, `CANARY_*` env, healthcheck for db only, named volume) and root `Dockerfile` (gradle:9.8.1-jdk25 multi-stage → temurin 25 JRE).
- **No `docker/` directory**, no `.env.dist`, no `docker/DOCKER.md`, no quickstart guide — so the "created in the `docker` directory" statement describes the upstream layout, not this repo's.
- Upstream `canary/docker/`: `docker-compose.yml`, `DOCKER.md`, `.env.dist`(style), `config.sh`, `up.sh`/`up.ps1` (guarded start scripts), `Dockerfile.x86/arm/dev`, `quickstart/myaac/`, `data/start.sh`; guides at `canary/docs/docker/quickstart-for-beginners.md`.

**Task (Q4):**
1. Create `docker/` with: `docker-compose.yml` (Postgres 16 + server; healthchecks both; volumes; `CANARY_*` contract), `.env.dist` (all env vars documented), `DOCKER.md` (setup, env reference, troubleshooting), `quickstart-for-beginners.md` (adapted from upstream), guarded `up.sh`/`up.ps1`.
2. Reconcile duplicates: move root compose/Dockerfile into `docker/` (or keep root paths but avoid two sources of truth) — Q4.
3. Decide the fate of upstream quickstart extras (`MyAAC` website + `opentibiabr/login-server`) — they are C++-ecosystem components; this port's login is native — document the substitute instead of copying blindly.
4. All game data/config volume considerations documented (datapack I7, map, DB volume).

**Validation:** `docker compose -f docker/docker-compose.yml config` parses; optional local `docker compose up` smoke; guides reviewed against actual env.

**Commit:** `build(docker): add compose stack, environment template and operation guides`

---

## Execution strategy

1. **Sequential commits on `main`**, one per improvement, in order I1 → I8.
2. Docs for each improvement ride in that improvement's commit (satisfies "each improvement as separate commits" + "all improvements documented in `docs/`"). This plan is committed before starting (§6).
3. Full `./gradlew clean test` after I1, I3, I7 and a final run; zero warnings policy unchanged.
4. `CHANGELOG.md` gains one "Repository Improvements" phase entry; `docs/development/improvements.md` gets a status/log table as items complete (V-style record), or move to `docs/development/done/` style only if requested.
5. No parallel worktrees (file overlap), except I7 could be isolated if desired (pure add).

## Verification matrix

| Check | Command | Pass |
|---|---|---|
| Home paths gone | `git grep -nE "Users/[A-Za-z0-9._-]+"` | 0 matches |
| Fork refs | `git grep -n opentibiabr` | reviewed allow-list only |
| RSA override | new tests + `CryptoTest` | green |
| Docs links | relative-link audit | 0 broken |
| README sections | manual checklist | 12/12 |
| CI | YAML validation + first run | green |
| Datapack boot | boot smoke with datapack | green |
| Docker | `docker compose config` | valid |
| Suite | `./gradlew clean test` | 689+ suites / 0 failures / 0 warnings |

## Open questions (need user decision before execution)

- **Q1 (I2):** functional download URLs in `MapDownload.kt` — keep upstream `opentibiabr/canary` as the artifact source (annotated) or point at the fork and host the releases there?
- **Q2 (I7):** copy `data-otservbr-global` verbatim into git (~38 MB) or rely on `MapDownload` fetch-on-boot and skip committing the world files?
- **Q3 (I4):** full 45-file port (rewritten) or curated subset (architecture/operations/development/docker/systems + new Kotlin building guide), skipping C++-only tooling?
- **Q4 (I8):** move compose/Dockerfile into `docker/` (upstream layout) or keep root compose and add `docker/` guides only? Replicate MyAAC/login-server quickstart extras or document the native login substitute?
- **Q5 (I5):** this repo has **no `LICENSE`**; upstream is GPL-2.0. Adopt GPL-2.0 with attribution?
- **Q6 (I6):** CI scope OK as proposed (build+test, optional Docker build), GitHub Actions only?
- **Q7:** execution mode — sequential commits on `main` as planned (recommended), or parallel worktrees per item?

## Canary Original Code Reference

- **Upstream docs:** `../.projects/canary/docs/**` (45 files; index `README.md`, `architecture.md`, `operations.md`, `hardware-sizing.md`, `development.md`, `release.md`, `docker/quickstart-for-beginners.md`, `systems/**`)
- **Upstream docker:** `../.projects/canary/docker/{docker-compose.yml,DOCKER.md,up.sh,up.ps1,config.sh,quickstart/}`
- **Upstream CI:** `../.projects/canary/.github/workflows/{ci.yml,reusable-build-*.yml,reusable-docker-quickstart-smoke.yml}`
- **Upstream datapack:** `../.projects/canary/data-otservbr-global/`
- **Upstream governance:** `../.projects/canary/{LICENSE,CONTRIBUTING.md,CODE_OF_CONDUCT.md}` (no `SECURITY.md` upstream — content must be authored)
- **RSA key origin:** `../.projects/canary/key.pem` + the OpenTibia default decimal key pair mirrored in `RsaCipher.kt`

---

## Completion (2026-10-14)

All eight improvements landed as separate commits on `main` (see the status
table in `docs/development/improvements.md`), ending with **690 suites / 7627 tests green**
and zero Kotlin warnings. This plan moves to `docs/development/done/`.
