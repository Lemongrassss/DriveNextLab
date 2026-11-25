package com.example.drivenext.data.catalog

import com.example.drivenext.data.cloud.Supabase
import com.example.drivenext.domain.catalog.Car
import org.json.JSONArray
import org.json.JSONObject

class CarRepository {
    suspend fun load(query: String): List<Car> {
        val rows = JSONArray(Supabase.json("/rest/v1/rpc/drivenext_find_cars",
            body = JSONObject().put("query_text", query)))
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            Car(row.getInt("id"), row.getString("model"), row.getString("brand"),
                row.getInt("price"), row.getString("transmission"), row.getString("fuel"))
        }
    }
}
