package com.example.securekeysbos2

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

class UserManager(context: Context) {
    private val prefs = context.getSharedPreferences("users", Context.MODE_PRIVATE)
    var currentUser: String = prefs.getString("current_user", "student") ?: "student"
        private set

    fun users(): List<String> = prefs.getStringSet("names", setOf("student"))!!.sorted()

    fun selectUser(name: String) {
        currentUser = name.ifBlank { "student" }
        prefs.edit().putString("current_user", currentUser).apply()
    }

    fun hasPassword(user: String = currentUser): Boolean = prefs.contains("hash_$user")

    fun setPassword(user: String = currentUser, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(password, salt)
        prefs.edit()
            .putStringSet("names", (users() + user).toSet())
            .putString("salt_$user", salt.toB64())
            .putString("hash_$user", hash.toB64())
            .apply()
    }

    fun verify(password: String, user: String = currentUser): Boolean {
        val salt = prefs.getString("salt_$user", null)?.fromB64() ?: return false
        val expected = prefs.getString("hash_$user", null)?.fromB64() ?: return false
        return MessageDigest.isEqual(expected, hash(password, salt))
    }

    fun addUser(name: String, password: String) {
        val clean = name.trim().ifBlank { return }
        selectUser(clean)
        setPassword(clean, password)
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        var data = salt + password.toByteArray(Charsets.UTF_8)
        repeat(120_000) { data = MessageDigest.getInstance("SHA-256").digest(data) }
        return data
    }
}
