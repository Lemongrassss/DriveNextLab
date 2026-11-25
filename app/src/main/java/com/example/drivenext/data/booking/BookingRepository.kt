package com.example.drivenext.data.booking

import com.example.drivenext.data.auth.AccountRepository
import com.example.drivenext.data.cloud.Supabase
import com.example.drivenext.domain.catalog.Car
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal class BookingRepository(private val account: AccountRepository) {
    data class Booking(val carId: Int, val name: String, val start: LocalDate, val end: LocalDate)

    suspend fun list(): List<Booking> {
        val rows = JSONArray(Supabase.json(
            "/rest/v1/drivenext_bookings?select=car_id,start_date,end_date,drivenext_cars(brand,model)&order=id.asc",
            account.accessToken()
        ))
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val car = row.getJSONObject("drivenext_cars")
            Booking(row.getInt("car_id"), "${car.getString("brand")} ${car.getString("model")}",
                LocalDate.parse(row.getString("start_date")), LocalDate.parse(row.getString("end_date")))
        }
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
