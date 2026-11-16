package com.example.bankbanchfinder.data.model

import com.google.gson.annotations.SerializedName

data class BranchResponse(
    @SerializedName("BANK") val bank: String? = null,
    @SerializedName("IFSC") val ifsc: String? = null,
    @SerializedName("BRANCH") val branch: String? = null,
    @SerializedName("ADDRESS") val address: String? = null,
    @SerializedName("CONTACT") val contact: String? = null,
    @SerializedName("CITY") val city: String? = null,
    @SerializedName("DISTRICT") val district: String? = null,
    @SerializedName("STATE") val state: String? = null,
    @SerializedName("MICR") val micr: String? = null,
    @SerializedName("UPI") val upi: Boolean? = false,
    @SerializedName("NEFT") val neft: Boolean? = false,
    @SerializedName("RTGS") val rtgs: Boolean? = false,
    @SerializedName("IMPS") val imps: Boolean? = false,
    @SerializedName("SWIFT") val swift: String? = null,
    @SerializedName("BANKCODE") val bankCode: String? = null
)
