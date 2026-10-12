# Feature: Argon2id Password Hashing & Security Engine

## 1. Explanation
Implements **Argon2id** password hashing and verification (`src/security/argon.cpp`). Canary and modern web AACs (MyAAC, Znote AAC) generate database password hashes conforming to PHC format strings:
`$argon2id$v=19$m=65536,t=4,p=1$<salt>$<hash>`

## 2. Impact
Security compliance and real web AAC compatibility. Prevents plaintext password compromises and enables players registered on web portals to log into the Kotlin game server directly.

## 3. Functionality Affected
- **Account Authentication**: `AccountRepository.authenticate(name, password)` passes the plaintext login attempt through `Argon2Security.verify(attempt, hash)`.
- **Legacy Fallback**: If the stored hash is legacy SHA1 (40 characters) or MD5, verifies using fallback and flags account for upgrade on next successful authentication.

## 4. Agent Implementation Details
- Add `de.mkammerer:argon2-jvm:2.11` or BouncyCastle Argon2 to `build.gradle.kts`.
- Implement `Argon2Security.kt` with configurable memory cost (`m_cost = 65536`), time cost (`t_cost = 4`), and parallelism (`parallelism = 1`).
- Connect `Argon2Security` to `JdbcAccountRepository`.

## 5. Test Reference
- `org.opentibia.canary.security.Argon2SecurityTest`: Generates an Argon2id hash for a test password, verifies matching password succeeds, and verifies mismatching password fails.
