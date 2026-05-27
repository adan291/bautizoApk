package com.example.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface CloudinaryApi {
    @Multipart
    @POST("image/upload")
    suspend fun uploadImage(
        @Part("upload_preset") uploadPreset: RequestBody,
        @Part("folder") folder: RequestBody,
        @Part file: MultipartBody.Part
    ): CloudinaryResponse
}

data class CloudinaryResponse(
    val secure_url: String,
    val public_id: String
)
