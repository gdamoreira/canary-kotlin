package org.opentibia.canary.utils

import java.security.MessageDigest

object Tools {

    fun subtractElapsedTime(current: Long, elapsed: Long): Long {
        if (current <= 0 || elapsed < 0) return 0L
        return if (current >= elapsed) current - elapsed else 0L
    }

    fun replaceString(subject: String, search: String, replace: String): String {
        if (search.isEmpty()) return subject
        return subject.replace(search, replace)
    }

    fun transformToSHA1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun transformToSHA256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
