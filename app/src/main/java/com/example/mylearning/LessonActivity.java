package com.example.mylearning;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.QuizQuestion;
import com.example.mylearning.databinding.ActivityLessonBinding;
import com.example.mylearning.network.GeminiClient;
import com.example.mylearning.network.GeminiRequest;
import com.example.mylearning.network.GeminiResponse;
import com.example.mylearning.util.TierGate;
import com.example.mylearning.util.ToolbarUtil;
import com.example.mylearning.util.VideoSearchUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LessonActivity extends AppCompatActivity {

    private ActivityLessonBinding binding;
    private AppDatabase db;
    private TierGate gate;
    private int topicId;
    private String topicName;

    private int videosUsed = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLessonBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        ToolbarUtil.setup(this, binding.toolbar, "Short Topic Lesson", true);

        db = AppDatabase.getInstance(this);
        gate = new TierGate(this);
        topicId = getIntent().getIntExtra("topicId", -1);
        topicName = getIntent().getStringExtra("topicName");

        binding.tvTopicName.setText(topicName);

        binding.btnStartQuiz.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuizActivity.class);
            intent.putExtra("topicId", topicId);
            intent.putExtra("topicName", topicName);
            startActivity(intent);
            finish();
        });

        loadLesson();
    }

    private void loadLesson() {
        AsyncTask.execute(() -> {
            List<QuizQuestion> starred = db.quizQuestionDao().getStarredForTopic(topicId);
            runOnUiThread(() -> {
                if (!starred.isEmpty()) {
                    showStarredMode(starred);
                } else if (gate.canViewLessonSummary()) {
                    showSummaryMode();
                } else {
                    showLockedState();
                }
            });
        });
    }

    // Starred questions — always free, user earned them by completing quizzes
    private void showStarredMode(List<QuizQuestion> starred) {
        binding.tvModeLabel.setText("Based on your saved questions. Tap a question for its lesson summary.");
        binding.containerStarred.setVisibility(View.VISIBLE);

        int videoLimit = gate.getVideoSuggestionLimit();

        for (QuizQuestion q : starred) {
            View item = LayoutInflater.from(this)
                    .inflate(R.layout.item_starred_question, binding.containerStarred, false);

            TextView tvQuestion = item.findViewById(R.id.tvQuestion);
            LinearLayout rowQuestion = item.findViewById(R.id.rowQuestion);
            LinearLayout containerExplanation = item.findViewById(R.id.containerExplanation);
            MaterialButton btnVideo = item.findViewById(R.id.btnVideo);

            tvQuestion.setText(q.questionText);

            // Tap to expand and load explanation
            rowQuestion.setOnClickListener(v -> {
                if (containerExplanation.getVisibility() == View.VISIBLE) {
                    containerExplanation.setVisibility(View.GONE);
                } else {
                    containerExplanation.setVisibility(View.VISIBLE);
                    TextView tvExp = item.findViewById(R.id.tvExplanation);
                    if (tvExp.getVisibility() != View.VISIBLE) {
                        fetchStarredExplanation(item, q);
                    }
                }
            });

            // Video button — inside the card, shown if tier allows
            if (videoLimit > 0) {
                btnVideo.setVisibility(View.VISIBLE);
                btnVideo.setOnClickListener(v -> {
                    if (videosUsed >= videoLimit) {
                        Snackbar.make(binding.getRoot(),
                                        "Video limit reached. Upgrade for more.",
                                        Snackbar.LENGTH_SHORT)
                                .setAction("Upgrade", u ->
                                        startActivity(new Intent(this, UpgradeActivity.class)))
                                .show();
                        return;
                    }
                    videosUsed++;
                    VideoSearchUtil.searchForQuestion(this, topicName, q.questionText);
                });
            }

            binding.containerStarred.addView(item);
        }
    }

    // Free tier with no starred questions — show upgrade prompt
    private void showLockedState() {
        binding.tvModeLabel.setText("AI lesson summaries");

        TextView tvLocked = new TextView(this);
        tvLocked.setText("Upgrade to Starter or above to unlock AI-generated lesson summaries.\n\n"
                + "Tip: star questions during a quiz to build your own lesson for free!");
        tvLocked.setTextSize(14f);
        tvLocked.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvLocked.setPadding(0, 24, 0, 16);
        tvLocked.setLineSpacing(0, 1.4f);

        MaterialButton btnUpgrade = new MaterialButton(this);
        btnUpgrade.setText("Upgrade");
        btnUpgrade.setBackgroundTintList(
                ContextCompat.getColorStateList(this, R.color.primary));
        btnUpgrade.setOnClickListener(v ->
                startActivity(new Intent(this, UpgradeActivity.class)));

        LinearLayout parent = (LinearLayout) binding.tvModeLabel.getParent();
        parent.addView(tvLocked);
        parent.addView(btnUpgrade);
    }

    // Paid tier, no starred questions — Gemini topic summary
    private void showSummaryMode() {
        binding.tvModeLabel.setText("General topic summary");
        binding.tvLoading.setVisibility(View.VISIBLE);

        String prompt = "Give me a concise 2-minute study summary of: " + topicName + ".\n"
                + "Format as exactly 5 bullet points starting with \u2022\n"
                + "Each bullet should cover a key concept. Keep language clear and educational.\n"
                + "No introduction, no conclusion — just the 5 bullets.";

        GeminiClient.getInstance().generateContent(

                new GeminiRequest(prompt)
        ).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                String text = response.isSuccessful() && response.body() != null
                        ? response.body().getResponseText() : null;

                runOnUiThread(() -> {
                    binding.tvLoading.setVisibility(View.GONE);
                    if (text != null) {
                        binding.tvSummary.setText(text);
                        binding.tvSummary.setVisibility(View.VISIBLE);
                        addTopicVideoButton();
                    } else {
                        showSummaryError();
                    }
                });
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                runOnUiThread(() -> {
                    binding.tvLoading.setVisibility(View.GONE);
                    showSummaryError();
                });
            }
        });
    }

    // Single topic-level video button after the summary
    private void addTopicVideoButton() {
        int videoLimit = gate.getVideoSuggestionLimit();
        if (videoLimit <= 0) return;

        MaterialButton btnVideo = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnVideo.setText("Search videos for " + topicName);
        btnVideo.setIconResource(R.drawable.ic_video);
        btnVideo.setTextSize(13f);
        btnVideo.setTextColor(ContextCompat.getColor(this, R.color.primary));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 16, 0, 0);
        btnVideo.setLayoutParams(lp);

        btnVideo.setOnClickListener(v -> {
            if (videosUsed >= videoLimit) {
                Snackbar.make(binding.getRoot(),
                                "Video limit reached. Upgrade for more.",
                                Snackbar.LENGTH_SHORT)
                        .setAction("Upgrade", u ->
                                startActivity(new Intent(this, UpgradeActivity.class)))
                        .show();
                return;
            }
            videosUsed++;
            VideoSearchUtil.openYouTubeSearch(this, topicName + " explained");
        });

        LinearLayout parent = (LinearLayout) binding.tvSummary.getParent();
        parent.addView(btnVideo);
    }

    private void fetchStarredExplanation(View item, QuizQuestion q) {
        TextView tvLoading = item.findViewById(R.id.tvLoading);
        TextView tvExplanation = item.findViewById(R.id.tvExplanation);
        TextView tvError = item.findViewById(R.id.tvError);

        tvLoading.setVisibility(View.VISIBLE);
        tvExplanation.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);

        String prompt = "In 3 concise bullet points starting with \u2022, explain the key concept behind this question:\n"
                + q.questionText + "\n"
                + "The correct answer is: " + q.correctAnswer + "\n"
                + "Keep it simple, clear, and educational.";

        GeminiClient.getInstance().generateContent(

                new GeminiRequest(prompt)
        ).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                String text = response.isSuccessful() && response.body() != null
                        ? response.body().getResponseText() : null;

                runOnUiThread(() -> {
                    tvLoading.setVisibility(View.GONE);
                    if (text != null) {
                        tvExplanation.setText(text);
                        tvExplanation.setVisibility(View.VISIBLE);
                    } else {
                        tvError.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                runOnUiThread(() -> {
                    tvLoading.setVisibility(View.GONE);
                    tvError.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void showSummaryError() {
        binding.containerError.setVisibility(View.VISIBLE);
        binding.btnRetry.setOnClickListener(v -> {
            binding.containerError.setVisibility(View.GONE);
            showSummaryMode();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}