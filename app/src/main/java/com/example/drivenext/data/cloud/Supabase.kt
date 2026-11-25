package com.example.drivenext.data.cloud

import com.example.drivenext.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal object Supabase {
    val url = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val key = BuildConfig.SUPABASE_KEY.trim()

    class Error(val status: Int, val code: String, message: String) : Exception(message)

    suspend fun json(
        path: String, token: String? = null, body: JSONObject? = null,
        method: String = if (body == null) "GET" else "POST", headers: Map<String, String> = emptyMap()
    ): String = request(path, method, token, body?.toString()?.toByteArray(), "application/json", headers)
        .toString(Charsets.UTF_8)

    suspend fun request(
        path: String, method: String, token: String? = null, body: ByteArray? = null,
        contentType: String = "application/json", headers: Map<String, String> = emptyMap()
    ): ByteArray = withContext(Dispatchers.IO) {
        check(url.startsWith("https://") && key.isNotBlank()) {
            "Укажи URL и публичный ключ Supabase в gradle.properties."
        }
        val connection = URL(url + path).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("apikey", key)
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val error = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                throw Error(status, error.optString("error_code", error.optString("code")),
                    error.optString("message", error.optString("msg", "Ошибка Supabase ($status).")))
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
