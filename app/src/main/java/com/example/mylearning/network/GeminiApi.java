package com.example.mylearning.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface GeminiApi {

    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    Call<GeminiResponse> generateContent(@Body GeminiRequest request);
}