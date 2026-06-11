package com.example.spetify

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Streaming

interface VocalRemoverApi {
    @Multipart
    @Streaming
    @POST("/separate")
    suspend fun separateVocals(
        @Part file: MultipartBody.Part,
        @Part("mode") mode: okhttp3.RequestBody = "two-stems".toRequestBody("text/plain".toMediaTypeOrNull())
    ): Response<ResponseBody>
}
