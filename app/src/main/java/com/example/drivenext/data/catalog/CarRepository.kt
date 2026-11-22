package com.example.drivenext.data.catalog

import android.net.Uri
import com.example.drivenext.BuildConfig
import com.example.drivenext.domain.catalog.Car
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class CarRepository {
    suspend fun load(query: String): List<Car> = withContext(Dispatchers.IO) {
        val url = BuildConfig.SERVER_URL.trimEnd('/')
        val endpoint = Uri.parse("$url/cars").buildUpon()
            .appendQueryParameter("q", query).build()
        val connection = URL(endpoint.toString()).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")
            val rows = connection.inputStream.bufferedReader().use { JSONArray(it.readText()) }
            List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                Car(row.getInt("id"), row.getString("model"), row.getString("brand"),
                    row.getInt("price"), row.getString("transmission"), row.getString("fuel"))
            }
        } finally {
            connection.disconnect()
        }
    }
}
