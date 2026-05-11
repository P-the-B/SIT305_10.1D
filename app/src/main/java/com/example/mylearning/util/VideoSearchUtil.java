package com.example.mylearning.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.example.mylearning.BuildConfig;
import com.example.mylearning.network.GeminiClient;
import com.example.mylearning.network.GeminiRequest;
import com.example.mylearning.network.GeminiResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Generates a YouTube search query from quiz data and fires it as a browser intent.
// Uses Gemini to distill a focused 3-5 word query from the full question text,
// falling back to a static word-cap builder if the API call fails.
public class VideoSearchUtil {

    // Static fallback — used only if the Gemini distillation call fails.
    // Extracts topic + first 5 words of the question as a rough approximation.
    public static String buildFallbackQuery(String topicName, String questionText) {
        String[] words = questionText.trim().split("\\s+");
        int wordLimit = Math.min(words.length, 5);

        StringBuilder sb = new StringBuilder(topicName).append(" ");
        for (int i = 0; i < wordLimit; i++) {
            sb.append(words[i]);
            if (i < wordLimit - 1) sb.append(" ");
        }

        return sb.toString().trim();
    }

    // Launches YouTube search with the given query string
    public static void openYouTubeSearch(Context context, String query) {
        String encoded = Uri.encode(query);

        try {
            Uri youtubeUri = Uri.parse("https://www.youtube.com/results?search_query=" + encoded);
            Intent intent = new Intent(Intent.ACTION_VIEW, youtubeUri);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Uri fallbackUri = Uri.parse("https://www.google.com/search?tbm=vid&q=" + encoded);
            context.startActivity(new Intent(Intent.ACTION_VIEW, fallbackUri));
        }
    }

    // Primary method — asks Gemini to distill the question into a focused YouTube search query.
    // Gemini returns something like "principle of least privilege cybersecurity" instead of
    // dumping the full question text into the search bar.
    // Falls back to the static builder if the API call fails or times out.
    public static void searchForQuestion(Context context, String topicName, String questionText) {
        String prompt = "Generate a short YouTube search query (3-5 words maximum) that would "
                + "find an educational video explaining the concept behind this quiz question.\n\n"
                + "Topic: " + topicName + "\n"
                + "Question: " + questionText + "\n\n"
                + "Rules:\n"
                + "- Output ONLY the search query, nothing else\n"
                + "- No quotes, no explanation, no punctuation\n"
                + "- Focus on the core concept, not the question format\n"
                + "- Include the subject area for context\n"
                + "Example: For a question about which principle limits user access rights, "
                + "output: principle of least privilege cybersecurity";

        GeminiClient.getInstance().generateContent(

                new GeminiRequest(prompt)
        ).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                String query = null;

                if (response.isSuccessful() && response.body() != null) {
                    query = response.body().getResponseText();
                }

                // Validate — Gemini occasionally returns empty or overly long responses
                if (query == null || query.trim().isEmpty() || query.trim().split("\\s+").length > 10) {
                    query = buildFallbackQuery(topicName, questionText);
                } else {
                    // Strip any quotes or newlines Gemini might wrap the response in
                    query = query.trim()
                            .replaceAll("^\"|\"$", "")
                            .replaceAll("\\n", " ")
                            .trim();
                }

                openYouTubeSearch(context, query);
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                // Network or timeout failure — use the static fallback immediately
                openYouTubeSearch(context, buildFallbackQuery(topicName, questionText));
            }
        });
    }
}