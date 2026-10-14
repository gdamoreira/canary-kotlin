# Improvements manually detected

- Remove all references to local absolute home paths (e.g. `/Users/<name>/.projects/...`). Consider the repository root as `../.projects/`.
- Change all `https://github.com/opentibiabr/canary` references to `https://github.com/gdamoreira/canary-kotlin`
- Hardcoded RSA encryption keys should be moved to a configuration file
- Documentation in `canary/docs` repository should be copied to this repository and updated to reflect the changes made to the codebase.
- README.md should be compatible with original `canary` repository and updated to reflect the changes made to the codebase.
  - should have a section for installation instructions
  - should have a section for contributing instructions
  - should have a section for license information
  - should have a section for troubleshooting instructions
  - should have a section for code of conduct
  - should have a section for security policy
  - should have a section for roadmap
  - should have a section for future improvements
  - should have a section for known issues
  - should have a section for frequently asked questions
  - should have a section for acknowledgments
  - should have a section for architecture explanation
- Github CI/CD pipeline should be set up to automate the test and build.
- `canary` repository contains `data-otserv-global` directory containing files for the game server and should be copied to this repository.
- A full `docker-compose.yml` should be created to automate the deployment of the application.
  - It has been created in the `docker` directory.
  - All necessary data, configuration files, and environment variables should be included.
  - All guides in `canary` repository should be copied to this repository and updated accordingly.

# Rules

- All improvements should be documented in the `docs` directory.
- Each improvement should be committed as separate commits.
---

## Status (wave 60 — completed 2026-10-14)

| # | Improvement | Status | Commit |
|---|---|---|---|
| I1 | Remove local absolute home-path references | Done — 57 files swept, convention enforced in AGENTS.md/template | `bd9015e` |
| I2 | Point references at `gdamoreira/canary-kotlin` | Done — branding changed; upstream map/config artifact URLs kept annotated; V2 migration and Lua datapack content untouched | `e64b190` |
| I3 | RSA keys to configuration | Done — `CANARY_RSA_P`/`CANARY_RSA_Q` with fast-fail validation (env layering) | `57beea9` |
| I4 | Copy/update `canary/docs` | Done — full 45-file port/adaptation + docs hub | `10d53b5` |
| I5 | README sections | Done — ToC + 12 requested sections; MIT `LICENSE`, CONTRIBUTING, CoC, SECURITY | `aa85506` |
| I6 | GitHub CI/CD | Done — `ci.yml` (build+test, JDK 25) and `docker.yml` (image build + compose validation) | `29b09f0` |
| I7 | Copy `data-otservbr-global` | Done — verbatim (4757 files / 38 MB); world map still fetched via MapDownload | `ca3e768` |
| I8 | Full Docker delivery | Done — root compose kept (decision), read-only data mounts, `.env.dist`, `docker/DOCKER.md`, guarded up scripts, `.dockerignore` | `637db6f3` |

**Decisions taken:** upstream download URLs stay (annotated) · datapack copied verbatim · docs ported in full · compose stays at the repository root · **MIT license** elected by the maintainer (upstream Canary is GPL-2.0; provenance noted in README/LICENSE).

**Known gaps:** no local Docker CLI on the build host, so `docker compose config` was validated as YAML only (CI `docker.yml` covers real validation); upstream building guides were adapted for Gradle without being executed on Windows/WSL.

