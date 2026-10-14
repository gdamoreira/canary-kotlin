# Security Policy

## Supported versions

Only the latest `main` revision of Canary Kotlin Edition is supported with
security fixes.

## Reporting a vulnerability

Please do **not** open a public issue for security problems. Report them
privately to the maintainers through GitHub's private vulnerability reporting
(Security tab → "Report a vulnerability") on `gdamoreira/canary-kotlin`, or by
contacting a maintainer directly.

Include as much of the following as possible:

- Affected component (network protocol, cryptography, persistence, Lua bridge, …)
- Reproduction steps or a proof-of-concept
- Impact assessment (what an attacker can achieve)
- Suggested fix, if you have one

You can expect an acknowledgement within a few days. Please allow time for a
fix and coordinated release before public disclosure.

## Scope notes

- The server ships with **test credentials** (account `1` / password `1`) for
  local development only. Never expose a test instance to the public internet.
- RSA handshake key material can be overridden with `CANARY_RSA_P` /
  `CANARY_RSA_Q`; deployments should use their own key pair.
- The default configuration is intended for local development, not for
  untrusted networks.
