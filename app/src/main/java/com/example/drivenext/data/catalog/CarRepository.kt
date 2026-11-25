package com.example.drivenext.data.catalog

import com.example.drivenext.data.cloud.Supabase
import com.example.drivenext.domain.catalog.Car
import org.json.JSONArray
import org.json.JSONObject

class CarRepository {
    suspend fun load(query: String): List<Car> {
        val rows = JSONArray(Supabase.json("/rest/v1/rpc/drivenext_find_cars",
            body = JSONObject().put("query_text", query)))
        return List(rows.length()) { index -> rows.getJSONObject(index).toCar() }
    }

    suspend fun details(id: Int): Car = find("id=eq.$id").firstOrNull()
        ?: error("Автомобиль не найден.")

    suspend fun favorites(ids: Set<Int>): List<Car> =
        if (ids.isEmpty()) emptyList() else find("id=in.(${ids.joinToString(",")})&order=id.asc")

    private suspend fun find(filter: String): List<Car> {
        val rows = JSONArray(Supabase.json("/rest/v1/drivenext_cars?$filter"))
        return List(rows.length()) { rows.getJSONObject(it).toCar() }
    }
}

internal fun JSONObject.toCar() = Car(
    getInt("id"), getString("model"), getString("brand"),
    getInt("price"), getString("transmission"), getString("fuel")
)
