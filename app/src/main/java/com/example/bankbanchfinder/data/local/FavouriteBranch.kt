package com.example.bankbanchfinder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favourite_branches")
data class FavouriteBranch(
    @PrimaryKey val ifsc: String,
    val bank: String,
    val branch: String,
    val address: String,
    val city: String,
    val state: String,
    val contact: String,
    val micr: String,
    val upi: Boolean,
    val neft: Boolean,
    val rtgs: Boolean,
    val imps: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
