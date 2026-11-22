package com.example.drivenext.domain.catalog

data class Car(
    val id: Int,
    val model: String,
    val brand: String,
    val price: Int,
    val transmission: String,
    val fuel: String
)
