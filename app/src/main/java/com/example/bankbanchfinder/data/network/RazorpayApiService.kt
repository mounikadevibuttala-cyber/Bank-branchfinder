package com.example.bankbanchfinder.data.network

import com.example.bankbanchfinder.data.model.BranchResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface RazorpayApiService {

    @GET("{ifsc}")
    suspend fun getBranchDetails(@Path("ifsc") ifsc: String): Response<BranchResponse>

    companion object {
        private const val BASE_URL = "https://ifsc.razorpay.com/"

        fun create(): RazorpayApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(RazorpayApiService::class.java)
        }
    }
}
