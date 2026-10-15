# Development Documents

This folder holds every development document for the Kotlin port: the parity
inventory, gameplay matrix, improvement tracker, audits, templates, the
aggregated implementation history — and any new document an agent creates.

## Conventions

- **Documents are created directly in this folder.** There is no
  `plan/`/`implementing/`/`done/` lifecycle anymore: a wave document is written
  as `wave-<N>-<topic>.md` and stays here (updated in place after the wave), and
  a feature document uses its topic name.
- Every document follows `TEMPLATE_FEATURE.md` and the documentation gates in
  `AGENTS.md` §5/§6 (exact Canary C++ source references are mandatory).
- Each wave updates `missing-features.md` / `game-play-features.md` in the same
  documentation commit and records the real test-suite metrics.

## Contents

| Document | Purpose |
| --- | --- |
| `architecture-and-roadmap.md` | Architectural mapping, parity validation strategy and sequential roadmap (aggregated from the former `plan/000-*` documents) |
| `feature-history.md` | All 64 feature specifications (001–064), aggregated from the former `done/` folder |
| `wave-history.md` | All wave plans (20–60), aggregated from the former `done/` folder |
| `missing-features.md` | Per-subsystem parity inventory and ranked remaining work |
| `game-play-features.md` | 56-feature gameplay matrix and residual parity gaps |
| `improvements.md` | Repository improvement tracker |
| `canary-codebase-implementation-audit.md` | Per-subsystem C++ to Kotlin port audit |
| `shared-build-cache.md` | Gradle build-cache notes (adapted from `canary/docs`) |
| `TEMPLATE_FEATURE.md` | Mandatory feature-document template |
