package com.example.drivenext.data.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import com.example.drivenext.data.cloud.Supabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom

internal class AccountRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("supabase_account", Context.MODE_PRIVATE)
    private val user get() = JSONObject(prefs.getString("user", "{}") ?: "{}")
    private val metadata get() = user.optJSONObject("user_metadata") ?: JSONObject()
    val email get() = user.optString("email")
    val name get() = metadata.optString("full_name").ifBlank { email.substringBefore('@') }
    val gender get() = metadata.optString("gender", "—")
    val joinedAt get() = runCatching { java.time.Instant.parse(user.getString("created_at")).toEpochMilli() }
        .getOrDefault(0)

    fun hasSession() = prefs.contains("refresh_token") && prefs.getString("project", "") == Supabase.url

    private fun password(email: String, password: String) =
        JSONObject().put("email", email.trim()).put("password", password)

    private suspend fun passwordSession(email: String, password: String) =
        auth("/token?grant_type=password", password(email, password))

    suspend fun signIn(email: String, password: String): Boolean {
        val session = passwordSession(email, password)
        if (!ready(session)) return false
        save(session)
        return true
    }

    suspend fun register(values: Map<String, String>) {
        val email = values.getValue("email").trim()
        val password = values.getValue("password")
        val fullName = "${values.getValue("first_name")} ${values.getValue("surname")}"
        val data = JSONObject().put("full_name", fullName).put("gender", values["gender"])
        val session = try {
            val result = auth("/signup", password(email, password).put("data", data))
            if (result.has("access_token")) result else passwordSession(email, password)
        } catch (error: Supabase.Error) {
            if (error.code !in setOf("user_already_exists", "email_exists")) throw error
            passwordSession(email, password)
        }
        val token = session.getString("access_token")
        val id = session.getJSONObject("user").getString("id")
        check(!profileExists(token, id)) { "Аккаунт уже создан. Войди с этой почтой и паролем." }

        upload(id, "licence", Uri.parse(values.getValue("licence_photo")), token)
        upload(id, "passport", Uri.parse(values.getValue("passport_photo")), token)
        values["avatar"]?.let { upload(id, "avatar", Uri.parse(it), token) }
        val profile = JSONObject().put("user_id", id)
        for (field in listOf("surname", "first_name", "patronymic", "birth", "gender", "licence", "issued")) {
            profile.put(field, values[field].orEmpty().trim())
        }
        Supabase.json("/rest/v1/drivenext_profiles", token, profile)
        save(session)
    }

    private suspend fun ready(session: JSONObject): Boolean {
        val account = session.getJSONObject("user")
        if (account.optJSONObject("app_metadata")?.optString("provider") == "google") return true
        return profileExists(session.getString("access_token"), account.getString("id"))
    }

    private suspend fun profileExists(token: String, id: String): Boolean =
        JSONArray(Supabase.json("/rest/v1/drivenext_profiles?select=user_id&user_id=eq.$id", token)).length() > 0

    fun googleUrl(): Uri {
        return challenge().let { hash ->
            Uri.parse("${Supabase.url}/auth/v1/authorize").buildUpon()
                .appendQueryParameter("provider", "google").appendQueryParameter("redirect_to", redirect)
                .appendQueryParameter("code_challenge", hash).appendQueryParameter("code_challenge_method", "s256").build()
        }
    }

    suspend fun finishGoogle(code: String) {
        val verifier = prefs.getString("verifier", null) ?: error("Начни вход через Google заново.")
        val session = auth("/token?grant_type=pkce",
            JSONObject().put("auth_code", code).put("code_verifier", verifier))
        save(session)
        prefs.edit().remove("verifier").apply()
    }

    suspend fun sendRecovery(email: String) {
        auth("/recover", JSONObject().put("email", email))
    }

    suspend fun resetPassword(email: String, code: String, password: String) {
        val session = auth("/verify", JSONObject().put("type", "recovery").put("email", email).put("token", code))
        Supabase.json("/auth/v1/user", session.getString("access_token"),
            JSONObject().put("password", password), method = "PUT")
    }

    suspend fun accessToken(): String = refreshLock.withLock { validToken() }

    private suspend fun validToken(): String {
        check(hasSession()) { "Войди в аккаунт заново." }
        val token = prefs.getString("access_token", "").orEmpty()
        if (token.isNotBlank() && prefs.getLong("expires_at", 0) > System.currentTimeMillis() / 1000 + 60) {
            return token
        }
        val updated = auth("/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", prefs.getString("refresh_token", "")))
        save(updated)
        return updated.getString("access_token")
    }

    suspend fun signOut() {
        val token = refreshLock.withLock {
            try {
                if (hasSession()) validToken() else null
            } finally {
                prefs.edit().clear().commit()
            }
        }
        if (token != null) Supabase.json("/auth/v1/logout?scope=local", token, method = "POST")
    }

    suspend fun saveAvatar(uri: Uri) {
        val token = accessToken()
        upload(user.getString("id"), "avatar", uri, token)
    }

    suspend fun avatar(): Bitmap? {
        val token = accessToken()
        val id = user.getString("id")
        val bytes = try {
            Supabase.request("/storage/v1/object/authenticated/drivenext-private/$id/avatar.jpg", "GET", token)
        } catch (error: Supabase.Error) {
            if (error.status in listOf(400, 404)) return null
            throw error
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private suspend fun upload(id: String, name: String, uri: Uri, token: String) {
        val photo = withContext(Dispatchers.IO) {
            val maxSize = if (name == "avatar") 512 else 1800
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val sample = ((maxOf(info.size.width, info.size.height) + maxSize - 1) / maxSize).coerceAtLeast(1)
                decoder.setTargetSampleSize(sample)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 87, output)) { "Не удалось прочитать фото." }
                output.toByteArray()
            }
        }
        Supabase.request("/storage/v1/object/drivenext-private/$id/$name.jpg", "POST", token,
            photo, "image/jpeg", mapOf("x-upsert" to "true"))
    }

    private suspend fun auth(path: String, body: JSONObject): JSONObject =
        Supabase.json("/auth/v1$path", body = body).let { if (it.isBlank()) JSONObject() else JSONObject(it) }

    private fun save(session: JSONObject) {
        val account = session.optJSONObject("user") ?: user
        val expires = session.optLong("expires_at", System.currentTimeMillis() / 1000 + session.optLong("expires_in", 3600))
        check(prefs.edit().putString("project", Supabase.url)
            .putString("access_token", session.getString("access_token"))
            .putString("refresh_token", session.getString("refresh_token"))
            .putString("user", account.toString()).putLong("expires_at", expires).commit()) {
            "Не удалось сохранить сессию."
        }
    }

    private fun challenge(): String {
        check(Supabase.url.startsWith("https://")) { "Укажи адрес Supabase в gradle.properties." }
        val verifier = encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
        prefs.edit().putString("verifier", verifier).commit()
        return encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
    }

    private fun encode(bytes: ByteArray) =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private companion object {
        const val redirect = "com.example.drivenext://auth/callback"
        val refreshLock = Mutex()
    }
}
