package com.example.drivenext.data.booking

import com.example.drivenext.data.auth.AccountRepository
import com.example.drivenext.data.catalog.toCar
import com.example.drivenext.data.cloud.Supabase
import com.example.drivenext.domain.catalog.Car
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal class BookingRepository(private val account: AccountRepository) {
    data class Booking(val id: Long, val car: Car, val start: LocalDate, val end: LocalDate)
    data class Driver(val name: String, val licence: String)

    suspend fun list(): List<Booking> = load("&order=id.desc")

    suspend fun details(id: Long): Booking = load("&id=eq.$id").firstOrNull()
        ?: error("Бронирование не найдено.")

    private suspend fun load(filter: String): List<Booking> {
        val fields = "id,start_date,end_date,daily_price,drivenext_cars(id,model,brand,price,transmission,fuel)"
        val rows = JSONArray(Supabase.json(
            "/rest/v1/drivenext_bookings?select=$fields$filter",
            account.accessToken()
        ))
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val car = row.getJSONObject("drivenext_cars").toCar().copy(price = row.getInt("daily_price"))
            Booking(row.getLong("id"), car,
                LocalDate.parse(row.getString("start_date")), LocalDate.parse(row.getString("end_date")))
        }
    }

    suspend fun driver(): Driver {
        val fields = "surname,first_name,patronymic,licence"
        val row = JSONArray(Supabase.json(
            "/rest/v1/drivenext_profiles?select=$fields&user_id=eq.${account.id}",
            account.accessToken()
        )).optJSONObject(0) ?: return Driver(account.name, "—")
        val name = listOf("surname", "first_name", "patronymic").map { row.optString(it) }
            .filter { it.isNotBlank() }.joinToString(" ")
        return Driver(name.ifBlank { account.name }, row.optString("licence").ifBlank { "—" })
    }

    suspend fun cancel(id: Long): Boolean {
        val deleted = Supabase.json("/rest/v1/drivenext_bookings?id=eq.$id&select=id",
            account.accessToken(), method = "DELETE", headers = mapOf("Prefer" to "return=representation"))
        return JSONArray(deleted).length() > 0
    }

    suspend fun book(car: Car, start: LocalDate, end: LocalDate) {
        val booking = JSONObject().put("car_id", car.id)
            .put("start_date", start.toString()).put("end_date", end.toString())
        try {
            Supabase.json("/rest/v1/drivenext_bookings", account.accessToken(), booking)
        } catch (error: Supabase.Error) {
            if (error.code == "23P01") error("Автомобиль уже забронирован на эти даты.")
            throw error
        }
    }
}
