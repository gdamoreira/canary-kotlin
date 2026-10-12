package org.opentibia.canary.network.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import java.util.Base64

object Argon2Security {

    private const val DEFAULT_ITERATIONS = 4
    private const val DEFAULT_MEMORY_KB = 65536
    private const val DEFAULT_PARALLELISM = 1
    private const val HASH_LENGTH = 32

    fun hashPassword(password: String): String {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(DEFAULT_ITERATIONS)
            .withMemoryAsKB(DEFAULT_MEMORY_KB)
            .withParallelism(DEFAULT_PARALLELISM)
            .withSalt(salt)
            .build()

        val generator = Argon2BytesGenerator().apply { init(params) }
        val result = ByteArray(HASH_LENGTH)
        generator.generateBytes(password.toByteArray(Charsets.UTF_8), result)

        val b64Salt = Base64.getEncoder().withoutPadding().encodeToString(salt)
        val b64Hash = Base64.getEncoder().withoutPadding().encodeToString(result)

        return "\$argon2id\$v=19\$m=$DEFAULT_MEMORY_KB,t=$DEFAULT_ITERATIONS,p=$DEFAULT_PARALLELISM\$$b64Salt\$$b64Hash"
    }

    fun verifyPassword(passwordAttempt: String, storedHash: String): Boolean {
        if (!storedHash.startsWith("\$argon2id\$")) {
            // Legacy / plaintext fallback
            return passwordAttempt == storedHash
        }

        val parts = storedHash.split("\$")
        if (parts.size < 6) return false

        val costPart = parts[3] // m=65536,t=4,p=1
        val b64Salt = parts[4]
        val b64Hash = parts[5]

        var memory = DEFAULT_MEMORY_KB
        var iterations = DEFAULT_ITERATIONS
        var parallelism = DEFAULT_PARALLELISM

        for (token in costPart.split(",")) {
            val kv = token.split("=")
            if (kv.size == 2) {
                when (kv[0]) {
                    "m" -> memory = kv[1].toIntOrNull() ?: memory
                    "t" -> iterations = kv[1].toIntOrNull() ?: iterations
                    "p" -> parallelism = kv[1].toIntOrNull() ?: parallelism
                }
            }
        }

        val salt = Base64.getDecoder().decode(b64Salt)
        val expectedHash = Base64.getDecoder().decode(b64Hash)

        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memory)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()

        val generator = Argon2BytesGenerator().apply { init(params) }
        val generated = ByteArray(expectedHash.size)
        generator.generateBytes(passwordAttempt.toByteArray(Charsets.UTF_8), generated)

        return expectedHash.contentEquals(generated)
    }
}
