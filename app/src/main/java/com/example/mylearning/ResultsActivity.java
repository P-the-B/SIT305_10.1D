package com.example.mylearning;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.QuizQuestion;
import com.example.mylearning.databinding.ActivityResultsBinding;
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

public class ResultsActivity extends AppCompatActivity {

    private ActivityResultsBinding binding;
    private AppDatabase db;
    private TierGate gate;
    private int attemptId;
    private int topicId;
    private String topicName;

    private int explanationsUsed = 0;
    private int videosUsed = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResultsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        ToolbarUtil.setup(this, binding.toolbar, "Your Results", false);

        db = AppDatabase.getInstance(this);
        gate = new TierGate(this);
        attemptId = getIntent().getIntExtra("attemptId", -1);
        topicId = getIntent().getIntExtra("topicId", -1);
        topicName = getIntent().getStringExtra("topicName");

        binding.btnFinish.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        binding.btnAnotherQuiz.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuizActivity.class);
            intent.putExtra("topicId", topicId);
            intent.putExtra("topicName", topicName);
            startActivity(intent);
            finish();
        });

        loadResults();
    }

    private void loadResults() {
        AsyncTask.execute(() -> {
            List<QuizQuestion> questions = db.quizQuestionDao().getQuestionsForAttempt(attemptId);
            int correct = (int) questions.stream().filter(q -> q.isCorrect).count();
            int total = questions.size();
            int accuracy = total > 0 ? (correct * 100 / total) : 0;

            runOnUiThread(() -> {
                binding.tvScore.setText(correct + " / " + total);
                binding.tvAccuracy.setText(accuracy + "% accuracy");
                renderQuestions(questions);
            });
        });
    }

    private void renderQuestions(List<QuizQuestion> questions) {
        int explanationLimit = gate.getExplanationLimit();
        int videoLimit = gate.getVideoSuggestionLimit();

        for (QuizQuestion q : questions) {
            View item = LayoutInflater.from(this)
                    .inflate(R.layout.item_result_question, binding.containerQuestions, false);

            TextView tvLabel = item.findViewById(R.id.tvResultLabel);
            TextView tvQuestion = item.findViewById(R.id.tvQuestion);
            TextView tvYourAnswer = item.findViewById(R.id.tvYourAnswer);
            TextView tvCorrectAnswer = item.findViewById(R.id.tvCorrectAnswer);
            ImageButton btnStar = item.findViewById(R.id.btnStar);
            View btnExplain = item.findViewById(R.id.btnExplain);
            MaterialButton btnVideo = item.findViewById(R.id.btnVideo);

            tvQuestion.setText(q.questionText);
            tvYourAnswer.setText("Your answer: " + (q.userAnswer != null ? q.userAnswer : "Not answered"));
            tvCorrectAnswer.setText("Correct answer: " + q.correctAnswer);

            if (q.isCorrect) {
                tvLabel.setText(getString(R.string.label_correct));
                tvLabel.setTextColor(ContextCompat.getColor(this, R.color.score_good_text));
                tvLabel.setBackgroundResource(R.drawable.bg_badge_green);
            } else {
                tvLabel.setText(getString(R.string.label_incorrect));
                tvLabel.setTextColor(ContextCompat.getColor(this, R.color.score_poor_text));
                tvLabel.setBackgroundResource(R.drawable.bg_badge_red);
            }

            // Star toggle
            btnStar.setImageResource(q.isStarred ? R.drawable.ic_star : R.drawable.ic_star_outline);
            btnStar.setOnClickListener(v -> {
                boolean newState = !q.isStarred;
                q.isStarred = newState;
                btnStar.setImageResource(newState ? R.drawable.ic_star : R.drawable.ic_star_outline);
                AsyncTask.execute(() -> db.quizQuestionDao().setStarred(q.id, newState));
            });

            // Explain — gated by per-session cap
            btnExplain.setOnClickListener(v -> {
                if (explanationsUsed >= explanationLimit) {
                    Snackbar.make(binding.getRoot(),
                                    "Explanation limit reached. Upgrade for more.",
                                    Snackbar.LENGTH_SHORT)
                            .setAction("Upgrade", u ->
                                    startActivity(new Intent(this, UpgradeActivity.class)))
                            .show();
                    return;
                }
                explanationsUsed++;
                requestExplanation(item, q);
            });

            // Video — show button if tier allows, gated by per-session cap
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

            binding.containerQuestions.addView(item);
        }
    }

    private void requestExplanation(View item, QuizQuestion q) {
        LinearLayout container = item.findViewById(R.id.containerExplanation);
        TextView tvLoading = item.findViewById(R.id.tvExplainLoading);
        TextView tvExplanation = item.findViewById(R.id.tvExplanation);
        LinearLayout containerError = item.findViewById(R.id.containerError);
        View btnExplain = item.findViewById(R.id.btnExplain);
        View btnRetry = item.findViewById(R.id.btnRetry);

        container.setVisibility(View.VISIBLE);
        tvLoading.setVisibility(View.VISIBLE);
        tvExplanation.setVisibility(View.GONE);
        containerError.setVisibility(View.GONE);
        btnExplain.setEnabled(false);

        String prompt = "In 3 concise bullet points starting with \u2022, explain why the answer to this question is "
                + q.correctAnswer + ".\n"
                + "Question: " + q.questionText + "\n"
                + "Options: A) " + q.optionA + " B) " + q.optionB
                + " C) " + q.optionC + " D) " + q.optionD + "\n"
                + "Keep it simple and educational.";

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
                        showExplanationError(containerError, btnRetry, item, q);
                    }
                });
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                runOnUiThread(() -> {
                    tvLoading.setVisibility(View.GONE);
                    showExplanationError(containerError, btnRetry, item, q);
                });
            }
        });
    }

    private void showExplanationError(LinearLayout containerError, View btnRetry, View item, QuizQuestion q) {
        containerError.setVisibility(View.VISIBLE);
        btnRetry.setOnClickListener(v -> {
            containerError.setVisibility(View.GONE);
            requestExplanation(item, q);
        });
    }
}