package com.example.mylearning.network;

import com.example.mylearning.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/";

    // volatile ensures double-checked locking works correctly across threads
    private static volatile GeminiApi instance;

    public static GeminiApi getInstance() {
        if (instance == null) {
            synchronized (GeminiClient.class) {
                if (instance == null) {
                    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS);

                    // Inject API key as header so it never appears in logged URLs
                    clientBuilder.addInterceptor(chain -> chain.proceed(
                            chain.request().newBuilder()
                                    .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                                    .build()
                    ));

                    // BASIC only — BODY would log full request/response including question text
                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
                        clientBuilder.addInterceptor(logging);
                    }

                    instance = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(clientBuilder.build())
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                            .create(GeminiApi.class);
                }
            }
        }
        return instance;
    }
}