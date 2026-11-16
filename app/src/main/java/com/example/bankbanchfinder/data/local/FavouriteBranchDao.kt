package com.example.bankbanchfinder.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteBranchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavourite(branch: FavouriteBranch)

    @Delete
    suspend fun deleteFavourite(branch: FavouriteBranch)

    @Query("DELETE FROM favourite_branches WHERE ifsc = :ifsc")
    suspend fun deleteByIfsc(ifsc: String)

    @Query("SELECT * FROM favourite_branches ORDER BY timestamp DESC")
    fun getAllFavourites(): Flow<List<FavouriteBranch>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourite_branches WHERE ifsc = :ifsc)")
    fun isFavourite(ifsc: String): Flow<Boolean>

    @Query("SELECT * FROM favourite_branches WHERE ifsc = :ifsc LIMIT 1")
    suspend fun getByIfsc(ifsc: String): FavouriteBranch?
}
