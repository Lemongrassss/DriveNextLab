package com.example.drivenext.data.auth

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

class LocalAccountRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("local_account", Context.MODE_PRIVATE)

    fun hasSession(): Boolean = preferences.getBoolean("session", false)

    val email get() = preferences.getString("email", "").orEmpty()
    val name get() = preferences.getString("name", "").orEmpty().ifBlank { email.substringBefore('@') }
    val gender get() = preferences.getString("gender", "—").orEmpty()
    val joinedAt get() = preferences.getLong("joined_at", 0)
    private val avatarFile get() = File(context.filesDir, "profile_avatar")
    val avatar get() = avatarFile.takeIf(File::isFile)

    fun register(email: String, password: String, name: String, gender: String) {
        val random = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = random.joinToString("") { "%02x".format(it) }
        if (!email.trim().equals(this.email, ignoreCase = true)) AtomicFile(avatarFile).delete()
        preferences.edit()
            .putString("email", email.trim())
            .putString("name", name)
            .putString("gender", gender)
            .putLong("joined_at", System.currentTimeMillis())
            .putString("salt", salt)
            .putString("password_hash", hash(salt + password))
            .putBoolean("session", true)
            .apply()
    }

    fun signIn(email: String, password: String): Boolean {
        val salt = preferences.getString("salt", null) ?: return false
        val matches = email.trim() == preferences.getString("email", null) &&
            hash(salt + password) == preferences.getString("password_hash", null)
        if (matches) preferences.edit().putBoolean("session", true).apply()
        return matches
    }

    fun signOut() { preferences.edit().remove("session").apply() }

    fun saveAvatar(uri: Uri): Boolean {
        val file = AtomicFile(avatarFile)
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use {
                val output = file.startWrite()
                try {
                    it.copyTo(output)
                    file.finishWrite(output)
                } catch (error: IOException) {
                    file.failWrite(output)
                    throw error
                }
            }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
