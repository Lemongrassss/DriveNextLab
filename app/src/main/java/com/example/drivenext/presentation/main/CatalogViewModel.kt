package com.example.drivenext.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivenext.data.catalog.CarRepository
import com.example.drivenext.domain.catalog.Car
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CatalogViewModel : ViewModel() {
    data class State(
        val query: String = "",
        val loading: Boolean = true,
        val cars: List<Car> = emptyList(),
        val error: String? = null
    )

    private val repository = CarRepository()
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    fun load(query: String) {
        request?.cancel()
        mutableState.value = State(query)
        request = viewModelScope.launch {
            try {
                mutableState.value = State(query, loading = false, cars = repository.load(query))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val error = if (query.isBlank()) "Не удалось загрузить данные. Попробуйте снова."
                    else "Не удалось выполнить поиск. Попробуйте снова."
                mutableState.value = State(query, loading = false, error = error)
            }
        }
    }
}
