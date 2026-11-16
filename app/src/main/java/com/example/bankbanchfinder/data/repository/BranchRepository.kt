package com.example.bankbanchfinder.data.repository

import com.example.bankbanchfinder.data.local.FavouriteBranch
import com.example.bankbanchfinder.data.local.FavouriteBranchDao
import com.example.bankbanchfinder.data.model.BranchResponse
import com.example.bankbanchfinder.data.network.RazorpayApiService
import kotlinx.coroutines.flow.Flow
import retrofit2.Response

sealed class ResultState<out T> {
    object Loading : ResultState<Nothing>()
    data class Success<out T>(val data: T) : ResultState<T>()
    data class Error(val message: String, val isNotFound: Boolean = false) : ResultState<Nothing>()
}

class BranchRepository(
    private val apiService: RazorpayApiService,
    private val dao: FavouriteBranchDao
) {
    suspend fun getBranchDetails(ifsc: String): ResultState<BranchResponse> {
        return try {
            val response: Response<BranchResponse> = apiService.getBranchDetails(ifsc.uppercase())
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && !body.ifsc.isNullOrEmpty()) {
                    ResultState.Success(body)
                } else {
                    ResultState.Error("Branch information not found for IFSC $ifsc", isNotFound = true)
                }
            } else if (response.code() == 404) {
                ResultState.Error("IFSC Code not found (HTTP 404). Please check the code.", isNotFound = true)
            } else {
                ResultState.Error("Error: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            ResultState.Error(e.localizedMessage ?: "Failed to connect to network")
        }
    }

    val allFavourites: Flow<List<FavouriteBranch>> = dao.getAllFavourites()

    fun isFavourite(ifsc: String): Flow<Boolean> = dao.isFavourite(ifsc)

    suspend fun addFavourite(branch: FavouriteBranch) {
        dao.insertFavourite(branch)
    }

    suspend fun removeFavourite(ifsc: String) {
        dao.deleteByIfsc(ifsc)
    }
}
