package com.example.mylearning;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.QuizAttempt;
import com.example.mylearning.data.entity.QuizQuestion;
import com.example.mylearning.data.entity.Topic;
import com.example.mylearning.data.entity.User;
import com.example.mylearning.databinding.ActivityProfileBinding;
import com.example.mylearning.network.GeminiClient;
import com.example.mylearning.network.GeminiRequest;
import com.example.mylearning.network.GeminiResponse;
import com.example.mylearning.util.SessionManager;
import com.example.mylearning.util.TierGate;
import com.example.mylearning.util.ToolbarUtil;
import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private AppDatabase db;
    private SessionManager session;
    private TierGate gate;

    // Cached for share intent
    private int totalQuestions;
    private int totalCorrect;
    private int totalIncorrect;
    private String username;
    private String bestTopicName;
    private String worstTopicName;
    private int bestTopicId = -1;
    private int worstTopicId = -1;

    // Richer data for AI insight prompt — built once in loadStats, used when tapped
    private String topicBreakdown;
    private String trendSummary;
    private String recentMistakes;

    // Image picker — no permissions needed on API 26+ for media store access
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) processAndSaveAvatar(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        ToolbarUtil.setup(this, binding.toolbar, "Profile", true);

        db = AppDatabase.getInstance(this);
        session = new SessionManager(this);
        gate = new TierGate(this);

        setupTierBadge();
        loadUserInfo();
        loadStats();
        setupCardLinks();

        binding.btnShare.setOnClickListener(v -> shareProfile());
        binding.cardAiInsight.setOnClickListener(v -> handleAiInsightTap());

        // Tap avatar to pick a new photo
        binding.cardAvatar.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        gate = new TierGate(this);
        setupTierBadge();
    }

    private void setupTierBadge() {
        binding.tvTierBadge.setText(gate.getTierLabel() + " Plan");
        binding.cardTierBadge.setOnClickListener(v ->
                startActivity(new Intent(this, UpgradeActivity.class)));

        binding.tvAiInsightStatus.setText(
                gate.canViewAiInsight() ? "Tap to generate" : "Advanced only");
    }

    private void loadUserInfo() {
        AsyncTask.execute(() -> {
            User user = db.userDao().findById(session.getUserId());
            if (user != null) {
                runOnUiThread(() -> {
                    username = user.username;
                    binding.tvUsername.setText(user.username);
                    binding.tvEmail.setText(user.email);

                    // Load stored avatar if present
                    if (user.profileImage != null && user.profileImage.length > 0) {
                        Bitmap bmp = BitmapFactory.decodeByteArray(
                                user.profileImage, 0, user.profileImage.length);
                        binding.ivAvatar.setPadding(0, 0, 0, 0);
                        binding.ivAvatar.setImageBitmap(bmp);
                    }
                });
            }
        });
    }

    private void loadStats() {
        AsyncTask.execute(() -> {
            int userId = session.getUserId();

            totalQuestions = db.quizAttemptDao().getTotalQuestionsForUser(userId);
            totalCorrect = db.quizAttemptDao().getTotalCorrectForUser(userId);
            totalIncorrect = totalQuestions - totalCorrect;
            int totalQuizzes = db.quizAttemptDao().getTotalAttemptCount(userId);
            int avgScore = totalQuestions > 0 ? (totalCorrect * 100 / totalQuestions) : 0;

            // Topic count
            List<Integer> attemptedIds = db.quizAttemptDao().getAttemptedTopicIds(userId);
            int topicCount = attemptedIds.size();

            // Per-topic breakdown + best/worst detection
            String bestName = "—";
            String worstName = "—";
            int bestAcc = -1;
            int worstAcc = Integer.MAX_VALUE;
            int bestId = -1;
            int worstId = -1;

            StringBuilder breakdown = new StringBuilder();

            for (int topicId : attemptedIds) {
                int acc = db.quizAttemptDao().getAccuracyForTopic(userId, topicId);
                int attempts = db.quizAttemptDao().getAttemptCount(userId, topicId);
                int starred = db.quizQuestionDao().getStarredCountForTopic(topicId);
                Topic t = db.topicDao().getTopicById(topicId);
                if (t == null) continue;

                // Build the per-topic line for the AI prompt
                breakdown.append("- ").append(t.name)
                        .append(": ").append(acc).append("% across ").append(attempts).append(" quizzes");
                if (starred > 0) {
                    breakdown.append(" (").append(starred).append(" starred for review)");
                }
                breakdown.append("\n");

                if (acc > bestAcc) {
                    bestAcc = acc;
                    bestName = t.name;
                    bestId = t.id;
                }
                if (acc < worstAcc) {
                    worstAcc = acc;
                    worstName = t.name;
                    worstId = t.id;
                }
            }

            topicBreakdown = breakdown.toString();

            // Trend — compare last 3 quizzes vs overall average
            List<QuizAttempt> allAttempts = db.quizAttemptDao().getAllAttemptsForUser(userId);
            if (allAttempts.size() >= 3) {
                int recentCorrect = 0;
                int recentTotal = 0;
                for (int i = 0; i < 3; i++) {
                    recentCorrect += allAttempts.get(i).score;
                    recentTotal += allAttempts.get(i).totalQuestions;
                }
                int recentAvg = recentTotal > 0 ? (recentCorrect * 100 / recentTotal) : 0;
                int diff = recentAvg - avgScore;
                if (diff > 5) {
                    trendSummary = "Improving — last 3 quizzes averaged " + recentAvg
                            + "% vs " + avgScore + "% overall";
                } else if (diff < -5) {
                    trendSummary = "Declining — last 3 quizzes averaged " + recentAvg
                            + "% vs " + avgScore + "% overall";
                } else {
                    trendSummary = "Stable — last 3 quizzes averaged " + recentAvg
                            + "% vs " + avgScore + "% overall";
                }
            } else {
                trendSummary = "Not enough quizzes yet to detect a trend";
            }

            // Recent wrong answers — gives Gemini concrete material for study advice
            List<QuizQuestion> wrongQs = db.quizQuestionDao()
                    .getRecentWrongQuestions(userId, 6);
            StringBuilder mistakes = new StringBuilder();
            for (QuizQuestion q : wrongQs) {
                Topic t = db.topicDao().getTopicById(q.topicId);
                String topicLabel = t != null ? t.name : "Unknown";
                mistakes.append("- [").append(topicLabel).append("] ")
                        .append(q.questionText).append("\n");
            }
            recentMistakes = mistakes.toString();

            // Days since last quiz
            Long lastTimestamp = db.quizAttemptDao().getLastQuizTimestamp(userId);
            long daysSince = -1;
            if (lastTimestamp != null) {
                long diffMs = System.currentTimeMillis() - lastTimestamp;
                daysSince = TimeUnit.MILLISECONDS.toDays(diffMs);
            }

            // Cache for share and card tap targets
            bestTopicName = bestName;
            worstTopicName = worstName;
            bestTopicId = bestId;
            worstTopicId = worstId;

            // Capture finals for UI thread
            final String fBestName = bestName;
            final String fWorstName = worstName;
            final int fBestAcc = bestAcc;
            final int fWorstAcc = worstAcc == Integer.MAX_VALUE ? 0 : worstAcc;
            final long fDaysSince = daysSince;
            final boolean singleTopic = topicCount == 1;

            runOnUiThread(() -> {
                binding.tvTotalQuestions.setText(String.valueOf(totalQuestions));
                binding.tvCorrectAnswers.setText(String.valueOf(totalCorrect));
                binding.tvIncorrectAnswers.setText(String.valueOf(totalIncorrect));
                binding.tvAvgScore.setText(avgScore + "%");
                binding.tvTopicCount.setText(String.valueOf(topicCount));
                binding.tvTotalQuizzes.setText(String.valueOf(totalQuizzes));

                // Best topic — always shown, colour-coded to match score
                binding.tvBestTopic.setText(fBestName);
                binding.tvBestTopicScore.setText(fBestAcc >= 0 ? fBestAcc + "%" : "");
                applyScoreColour(binding.cardBestTopic, binding.tvBestTopicScore, fBestAcc);

                if (singleTopic) {
                    // Only one topic attempted — hide worst card entirely
                    binding.cardWorstTopic.setVisibility(View.GONE);
                } else {
                    binding.cardWorstTopic.setVisibility(View.VISIBLE);
                    binding.tvWorstTopic.setText(fWorstName);
                    binding.tvWorstTopicScore.setText(topicCount > 0 ? fWorstAcc + "%" : "");

                    // Label and colour shift based on how bad the score actually is
                    if (fWorstAcc >= 70) {
                        binding.tvWorstLabel.setText("Lowest Score");
                    } else if (fWorstAcc >= 50) {
                        binding.tvWorstLabel.setText("Room to Grow");
                    } else {
                        binding.tvWorstLabel.setText("Needs Work");
                    }
                    applyScoreColour(binding.cardWorstTopic, binding.tvWorstTopicScore, fWorstAcc);
                }

                if (fDaysSince == 0) {
                    binding.tvDaysSinceQuiz.setText("Today");
                } else if (fDaysSince == 1) {
                    binding.tvDaysSinceQuiz.setText("1 day");
                } else if (fDaysSince > 1) {
                    binding.tvDaysSinceQuiz.setText(fDaysSince + " days");
                } else {
                    binding.tvDaysSinceQuiz.setText("—");
                }
            });
        });
    }

    /** Sets card background and score text colour based on accuracy thresholds */
    private void applyScoreColour(
            com.google.android.material.card.MaterialCardView card,
            android.widget.TextView scoreText,
            int accuracy) {
        if (accuracy >= 70) {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.score_good));
            scoreText.setTextColor(ContextCompat.getColor(this, R.color.score_good_text));
        } else if (accuracy >= 50) {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.score_amber));
            scoreText.setTextColor(ContextCompat.getColor(this, R.color.score_amber_text));
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.score_poor));
            scoreText.setTextColor(ContextCompat.getColor(this, R.color.score_poor_text));
        }
    }

    // Links tappable cards to their relevant screens
    private void setupCardLinks() {
        // Question/quiz cards → History
        View.OnClickListener historyLink = v ->
                startActivity(new Intent(this, HistoryActivity.class));
        binding.cardTotalQuestions.setOnClickListener(historyLink);
        binding.cardCorrect.setOnClickListener(historyLink);
        binding.cardIncorrect.setOnClickListener(historyLink);
        binding.cardTotalQuizzes.setOnClickListener(historyLink);

        // Topics card → Interests
        binding.cardTopics.setOnClickListener(v -> {
            Intent intent = new Intent(this, InterestsActivity.class);
            intent.putExtra("userId", session.getUserId());
            startActivity(intent);
        });

        // Best/Worst topic → TopicDetail (paid) or progress tab
        binding.cardBestTopic.setOnClickListener(v -> openTopicDetail(bestTopicId, bestTopicName));
        binding.cardWorstTopic.setOnClickListener(v -> openTopicDetail(worstTopicId, worstTopicName));
    }

    private void openTopicDetail(int topicId, String topicName) {
        if (topicId < 0 || topicName == null) return;

        if (gate.canViewTopicDetail()) {
            Intent intent = new Intent(this, TopicDetailActivity.class);
            intent.putExtra("topicId", topicId);
            intent.putExtra("topicName", topicName);
            startActivity(intent);
        } else {
            Snackbar.make(binding.getRoot(), "Upgrade to view topic details",
                            Snackbar.LENGTH_SHORT)
                    .setAction("Upgrade", v ->
                            startActivity(new Intent(this, UpgradeActivity.class)))
                    .show();
        }
    }

    // Reads the picked image, center-crops to 256x256, compresses, stores as blob
    private void processAndSaveAvatar(Uri uri) {
        AsyncTask.execute(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                Bitmap original = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
                if (original == null) return;

                // Center-crop to square
                int size = Math.min(original.getWidth(), original.getHeight());
                int x = (original.getWidth() - size) / 2;
                int y = (original.getHeight() - size) / 2;
                Bitmap cropped = Bitmap.createBitmap(original, x, y, size, size);

                // Scale down to 256x256 to keep blob small
                Bitmap scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true);

                // Compress to JPEG bytes
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                byte[] bytes = baos.toByteArray();

                // Persist to Room
                db.userDao().updateProfileImage(session.getUserId(), bytes);

                runOnUiThread(() -> {
                    binding.ivAvatar.setPadding(0, 0, 0, 0);
                    binding.ivAvatar.setImageBitmap(scaled);
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Snackbar.make(binding.getRoot(), "Could not load image",
                                Snackbar.LENGTH_SHORT).show());
            }
        });
    }

    private void handleAiInsightTap() {
        if (!gate.canViewAiInsight()) {
            Snackbar.make(binding.getRoot(),
                            "Upgrade to Advanced to unlock AI insights",
                            Snackbar.LENGTH_SHORT)
                    .setAction("Upgrade", v ->
                            startActivity(new Intent(this, UpgradeActivity.class)))
                    .show();
            return;
        }

        if (binding.cardAiInsightResult.getVisibility() == View.VISIBLE) return;

        if (totalQuestions == 0) {
            Snackbar.make(binding.getRoot(),
                    "Complete some quizzes first for AI insights",
                    Snackbar.LENGTH_SHORT).show();
            return;
        }

        binding.tvAiInsightStatus.setText("Loading...");
        fetchAiInsight();
    }

    private void fetchAiInsight() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a personal learning coach reviewing a student's quiz history. ");
        prompt.append("Your job is to give advice they CANNOT get just by reading their stats. ");
        prompt.append("Do not restate numbers they already know.\n\n");

        prompt.append("=== TOPIC BREAKDOWN ===\n");
        if (topicBreakdown != null && !topicBreakdown.isEmpty()) {
            prompt.append(topicBreakdown);
        } else {
            prompt.append("No topic data yet.\n");
        }

        prompt.append("\n=== PERFORMANCE TREND ===\n");
        prompt.append(trendSummary).append("\n");

        prompt.append("\n=== RECENT MISTAKES ===\n");
        if (recentMistakes != null && !recentMistakes.isEmpty()) {
            prompt.append(recentMistakes);
        } else {
            prompt.append("None recorded.\n");
        }

        prompt.append("\nBased on this data, write a short coaching note (60–90 words) covering:\n");
        prompt.append("1. A pattern or blind spot they might not have noticed\n");
        prompt.append("2. One specific study action tied to their recent mistakes\n");
        prompt.append("3. An encouraging observation about their trajectory\n");
        prompt.append("Write in second person (\"you\"), conversational tone, no bullet points. ");
        prompt.append("Do not list their stats back to them.");

        GeminiClient.getInstance().generateContent(
                new GeminiRequest(prompt.toString())
        ).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                String text = response.isSuccessful() && response.body() != null
                        ? response.body().getResponseText() : null;

                runOnUiThread(() -> {
                    if (text != null) {
                        binding.tvAiInsightText.setText(text);
                        binding.cardAiInsightResult.setVisibility(View.VISIBLE);
                        binding.tvAiInsightStatus.setText("Generated");
                    } else {
                        binding.tvAiInsightStatus.setText("Failed — tap to retry");
                    }
                });
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                runOnUiThread(() ->
                        binding.tvAiInsightStatus.setText("Failed — tap to retry"));
            }
        });
    }

    private void shareProfile() {
        int accuracy = totalQuestions > 0 ? (totalCorrect * 100 / totalQuestions) : 0;

        String shareText = "MyLearning Profile\n"
                + "Student: " + (username != null ? username : "User") + "\n"
                + "Plan: " + gate.getTierLabel() + "\n\n"
                + "Total Questions: " + totalQuestions + "\n"
                + "Correct: " + totalCorrect + "\n"
                + "Incorrect: " + totalIncorrect + "\n"
                + "Accuracy: " + accuracy + "%\n"
                + (bestTopicName != null && !"—".equals(bestTopicName)
                ? "Best Topic: " + bestTopicName + "\n" : "")
                + (worstTopicName != null && !"—".equals(worstTopicName) && worstTopicId != bestTopicId
                ? "Needs Work: " + worstTopicName + "\n" : "")
                + "\nShared from MyLearning app";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "My MyLearning Profile");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Share your profile"));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}