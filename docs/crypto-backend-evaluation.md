# Crypto backend evaluation (JVM)

> Adapted from canary/docs/crypto-backend-evaluation.md for the Kotlin/JVM engine.
> Canary-Kotlin status: adapted to the JVM crypto stack. The upstream Mbed TLS
> vs OpenSSL backend question maps to the JDK `BigInteger` RSA path plus
> BouncyCastle for Argon2id; the byte-for-byte login contract is unchanged.

## Summary

Canary Kotlin performs the OpenTibia login handshake with the **JDK's
`java.math.BigInteger`** (raw modular exponentiation) and uses **BouncyCastle
`bcprov-jdk18on` 1.80** for **Argon2id** password hashing. There is no OpenSSL,
Mbed TLS or native crypto dependency in this repository; the upstream C++
backend-selection discussion does not apply to this stack.

The goal of the port is to keep the login protocol byte-for-byte identical while
expressing the crypto in JVM terms. The migration depends on byte-for-byte tests
for fixed-width raw RSA behavior and on parity tests for XTEA, Adler-32 and
password verification.

## Motivation

Upstream removed direct OpenSSL coupling from `RSAManager` because `libcrypto`
ended up in the final executable. On the JVM the equivalent concern is the
dependency surface and provider behavior:

- RSA login operations are simple enough to implement with `BigInteger`
  (`c^d mod n`) without a native provider.
- Password hashing uses a maintained, pure-Java implementation
  (BouncyCastle Argon2id) selected in the composition root, not a JCE provider
  registered globally.
- Keeping RSA independent from the password-hashing provider means a future
  provider change cannot silently alter login bytes.

Do not claim performance or footprint improvements without a fresh measurement
for the exact JVM and flags being reviewed.

## Current behavior that must be preserved

The RSA login path has a narrow compatibility contract:

- Input blocks are exactly 128 bytes.
- The operation is raw RSA private exponentiation, equivalent to `m = c^d mod n`.
- No implicit padding must be added or removed by the backend.
- Output blocks remain exactly 128 bytes.
- Leading zero bytes in the decrypted block must be preserved.
- The login flow validates the first decrypted byte after RSA decrypt.
- The built-in OpenTibia/CipSoft key pair remains the default key material.
- Key material can be overridden with `CANARY_RSA_P` / `CANARY_RSA_Q`
  (decimal strings, both required together).

Breaking any of these rules can make login incompatible even when the crypto
APIs appear to succeed.

## Current backends

### RSA (JDK `BigInteger`)

`network/crypto/RsaCipher.kt` implements:

- 1024-bit modulus from the configured `p`/`q` primes (`n = p*q`),
- public exponent `e = 65537`,
- private exponent derived as `d = e^-1 mod φ(n)`,
- in-place raw decryption of each 128-byte block with `modPow`.

`CANARY_RSA_P`/`CANARY_RSA_Q` override the default key pair; invalid or
mismatched values fail the boot fast with a clear configuration error. Do not
replace the raw operation with a padded scheme: the login block contract
requires no padding.

### Argon2id (BouncyCastle)

`network/crypto/Argon2Security.kt` uses
`org.bouncycastle.crypto.generators.Argon2BytesGenerator` with
`Argon2Parameters.ARGON2_id`, version 13, 16-byte random salt, 32-byte output,
and a PHC-style encoded hash (`$argon2id$v=19$m=...,t=...,p=...$salt$hash`).
Verification re-parses parameters from the stored hash so changing defaults does
not invalidate existing passwords.

BouncyCastle is declared as `bcprov-jdk18on:1.80` in `build.gradle.kts`; do not
add a JCE provider with global registration unless the change is reviewed
against this contract.

### XTEA and Adler-32

`network/crypto/XteaCipher.kt` and `network/crypto/Adler32Checksum.kt` are pure
Kotlin ports of the upstream algorithms:

- XTEA: 64 rounds (32 cycles), DELTA `0x9E3779B9` (written as `-0x61C88647` in
  signed 32-bit Kotlin), 8-byte little-endian blocks, unaligned payloads padded
  to multiples of 8.
- Adler-32: RFC 1950 checksum over client-first packets before opcode parsing.

## Benchmark and audit guidance

Treat performance and footprint as validation work, not as measured claims:

- Warm the JIT before timing; cold-start numbers are dominated by class loading.
- Use a focused harness or microbenchmark that repeatedly calls the RSA decrypt
  path with fixed 128-byte ciphertext samples after one-time key setup.
- Do not include key-material parsing unless the benchmark is explicitly about
  startup cost.
- Record JVM version, GC, flags, hardware and iteration count with any result.
- For password hashing, measure Argon2id cost on the target host and confirm it
  stays within the login timeout budget.
- RSA private operations are not constant-time; this matches the legacy login
  contract and is not a general-purpose decrypt wrapper. Do not reuse it for
  padded RSA workflows without a separate API and tests.

## How to build and test

Run commands from the repository root:

```bash
./gradlew test
```

The crypto tests include byte-for-byte vectors and transcript tests that mirror
the upstream C++ coverage.

## Validation

Validation should focus on behavior, not only successful compilation:

- Verify that the built-in key pair decrypts the standard 128-byte vectors.
- Verify that `CANARY_RSA_P`/`CANARY_RSA_Q` overrides produce deterministic
  output and fail fast when incomplete.
- Verify that decrypted output preserves leading zeros and remains 128 bytes.
- Keep XTEA encrypt/decrypt round-trip tests with the upstream vectors.
- Keep Adler-32 checksum tests.
- Keep Argon2id hash/verify tests, including PHC parameter parsing.
- Run login smoke tests with the supported clients and protocol profiles.

## Limitations

- `BigInteger.modPow` is not constant-time; the login protocol inherits the
  upstream limitation.
- The port does not ship multiple interchangeable RSA providers; the JDK path is
  the only one.
- Argon2id parameters are local constants; changing them changes only newly
  stored hashes because verification reads parameters from the stored string.

## Future improvements

Potential follow-up work:

- Evaluate JCE/BouncyCastle RSA only if the JDK path becomes a measured
  bottleneck.
- Add more RSA vectors if new key sizes or protocol variants are introduced.
- Revisit Argon2id parameters (memory/iterations/parallelism) with a host-cost
  measurement.
- Consider constant-time comparison for password hash verification as
  defense-in-depth.
