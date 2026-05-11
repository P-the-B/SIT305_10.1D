package com.example.mylearning;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.QuizAttempt;
import com.example.mylearning.data.entity.QuizQuestion;
import com.example.mylearning.data.entity.Topic;
import com.example.mylearning.databinding.ActivityHistoryBinding;
import com.example.mylearning.network.GeminiClient;
import com.example.mylearning.network.GeminiRequest;
import com.example.mylearning.network.GeminiResponse;
import com.example.mylearning.util.SessionManager;
import com.example.mylearning.util.TierGate;
import com.example.mylearning.util.ToolbarUtil;
import com.example.mylearning.util.VideoSearchUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding binding;
    private AppDatabase db;
    private SessionManager session;
    private TierGate gate;

    private final Map<Integer, String> topicNameCache = new HashMap<>();
    private final Map<Integer, List<QuizQuestion>> questionCache = new HashMap<>();

    private int explanationsUsed = 0;
    private int videosUsed = 0;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        ToolbarUtil.setup(this, binding.toolbar, "History", true);

        db = AppDatabase.getInstance(this);
        session = new SessionManager(this);
        gate = new TierGate(this);

        loadHistory();
    }

    private void loadHistory() {
        AsyncTask.execute(() -> {
            List<QuizAttempt> attempts = db.quizAttemptDao()
                    .getAllAttemptsForUser(session.getUserId());

            for (QuizAttempt a : attempts) {
                if (!topicNameCache.containsKey(a.topicId)) {
                    Topic t = db.topicDao().getTopicById(a.topicId);
                    topicNameCache.put(a.topicId, t != null ? t.name : "Unknown");
                }
            }

            runOnUiThread(() -> {
                if (attempts.isEmpty()) {
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                    binding.containerAttempts.setVisibility(View.GONE);
                } else {
                    renderAttempts(attempts);
                }
            });
        });
    }

    private void renderAttempts(List<QuizAttempt> attempts) {
        binding.containerAttempts.removeAllViews();

        for (QuizAttempt attempt : attempts) {
            View card = LayoutInflater.from(this)
                    .inflate(R.layout.item_history_attempt, binding.containerAttempts, false);

            TextView tvTopicName = card.findViewById(R.id.tvTopicName);
            TextView tvDate = card.findViewById(R.id.tvDate);
            TextView tvScore = card.findViewById(R.id.tvScore);
            ImageView ivExpandIcon = card.findViewById(R.id.ivExpandIcon);
            LinearLayout containerQuestions = card.findViewById(R.id.containerQuestions);

            tvTopicName.setText(topicNameCache.get(attempt.topicId));
            tvDate.setText(dateFormat.format(new Date(attempt.timestamp)));
            tvScore.setText(attempt.score + " / " + attempt.totalQuestions);

            int accuracy = attempt.totalQuestions > 0
                    ? (attempt.score * 100 / attempt.totalQuestions) : 0;
            if (accuracy >= 70) {
                tvScore.setTextColor(ContextCompat.getColor(this, R.color.score_good_text));
            } else if (accuracy >= 50) {
                tvScore.setTextColor(ContextCompat.getColor(this, R.color.score_amber_text));
            } else {
                tvScore.setTextColor(ContextCompat.getColor(this, R.color.score_poor_text));
            }

            if (!gate.canExpandHistory()) {
                ivExpandIcon.setImageResource(R.drawable.ic_lock);
            }

            card.findViewById(R.id.rowHeader).setOnClickListener(v ->
                    handleAttemptTap(attempt, containerQuestions, ivExpandIcon));

            binding.containerAttempts.addView(card);
        }
    }

    private void handleAttemptTap(QuizAttempt attempt, LinearLayout containerQuestions,
                                  ImageView ivExpandIcon) {
        if (!gate.canExpandHistory()) {
            Snackbar.make(binding.getRoot(),
                            "Upgrade to see question details",
                            Snackbar.LENGTH_SHORT)
                    .setAction("Upgrade", v ->
                            startActivity(new Intent(this, UpgradeActivity.class)))
                    .show();
            return;
        }

        if (containerQuestions.getVisibility() == View.VISIBLE) {
            containerQuestions.setVisibility(View.GONE);
            ivExpandIcon.setRotation(0f);
            return;
        }

        if (questionCache.containsKey(attempt.id)) {
            containerQuestions.setVisibility(View.VISIBLE);
            ivExpandIcon.setRotation(180f);
            return;
        }

        ivExpandIcon.setRotation(180f);
        AsyncTask.execute(() -> {
            List<QuizQuestion> questions = db.quizQuestionDao()
                    .getQuestionsForAttempt(attempt.id);
            questionCache.put(attempt.id, questions);

            runOnUiThread(() -> {
                inflateQuestions(containerQuestions, questions, attempt.topicId);
                containerQuestions.setVisibility(View.VISIBLE);
            });
        });
    }

    private void inflateQuestions(LinearLayout container, List<QuizQuestion> questions,
                                  int topicId) {
        container.removeAllViews();

        int explanationLimit = gate.getExplanationLimit();
        int videoLimit = gate.getVideoSuggestionLimit();
        String topicName = topicNameCache.getOrDefault(topicId, "Unknown");

        for (QuizQuestion q : questions) {
            View item = LayoutInflater.from(this)
                    .inflate(R.layout.item_result_question, container, false);

            TextView tvLabel = item.findViewById(R.id.tvResultLabel);
            TextView tvQuestion = item.findViewById(R.id.tvQuestion);
            TextView tvYourAnswer = item.findViewById(R.id.tvYourAnswer);
            TextView tvCorrectAnswer = item.findViewById(R.id.tvCorrectAnswer);
            ImageButton btnStar = item.findViewById(R.id.btnStar);
            View btnExplain = item.findViewById(R.id.btnExplain);
            MaterialButton btnVideo = item.findViewById(R.id.btnVideo);

            tvQuestion.setText(q.questionText);
            tvYourAnswer.setText("Your answer: "
                    + (q.userAnswer != null ? q.userAnswer : "Not answered"));
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

            // Explain — gated
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

            // Video — show if tier allows
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

            container.addView(item);
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

    private void showExplanationError(LinearLayout containerError, View btnRetry,
                                      View item, QuizQuestion q) {
        containerError.setVisibility(View.VISIBLE);
        btnRetry.setOnClickListener(v -> {
            containerError.setVisibility(View.GONE);
            requestExplanation(item, q);
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}