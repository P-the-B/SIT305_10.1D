package com.example.mylearning;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.Topic;
import com.example.mylearning.data.entity.User;
import com.example.mylearning.data.entity.UserTopic;
import com.example.mylearning.databinding.ActivityInterestsBinding;
import com.example.mylearning.network.GeminiClient;
import com.example.mylearning.network.GeminiRequest;
import com.example.mylearning.network.GeminiResponse;
import com.example.mylearning.util.SessionManager;
import com.example.mylearning.util.TierGate;
import com.example.mylearning.util.TopicSeeder;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InterestsActivity extends AppCompatActivity {

    private ActivityInterestsBinding binding;
    private AppDatabase db;
    private SessionManager session;
    private TierGate gate;

    private List<Topic> allTopics = new ArrayList<>();
    private final List<Topic> selectedTopics = new ArrayList<>();
    private int userId;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInterestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getInstance(this);
        session = new SessionManager(this);
        gate = new TierGate(this);

        userId = getIntent().getIntExtra("userId", session.getUserId());

        // Set subtitle to reflect tier-aware limit
        binding.tvSubtitle.setText("Select up to " + getMaxTopics() + " topics");

        // Enable clickable spans in the feedback text
        binding.tvAiFeedback.setMovementMethod(LinkMovementMethod.getInstance());

        loadTopics();

        searchRunnable = () -> renderTopics(
                binding.editSearch.getText().toString().trim().toLowerCase());

        binding.editSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, 300);
                hideFeedback();
            }
            public void afterTextChanged(Editable s) {}
        });

        binding.editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String query = binding.editSearch.getText().toString().trim();
                if (!query.isEmpty()) {
                    handleDonePressed(query);
                }
                return true;
            }
            return false;
        });

        binding.btnContinue.setOnClickListener(v -> saveAndContinue());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh gate in case user upgraded and came back
        gate = new TierGate(this);
        binding.tvSubtitle.setText("Select up to " + getMaxTopics() + " topics");
        updateCounter();
        // Clear stale limit feedback if upgrade expanded the cap
        if (selectedTopics.size() < getMaxTopics()) {
            hideFeedback();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHandler.removeCallbacks(searchRunnable);
    }

    private int getMaxTopics() {
        return gate.getMaxTopics();
    }

    private void loadTopics() {
        AsyncTask.execute(() -> {
            allTopics = db.topicDao().getAllTopics();

            // Re-seed if empty after a destructive migration
            if (allTopics.isEmpty()) {
                db.topicDao().insertAll(TopicSeeder.getTopics());
                allTopics = db.topicDao().getAllTopics();
            }

            selectedTopics.clear();
            List<Topic> existing = db.topicDao().getTopicsForUser(userId);
            selectedTopics.addAll(existing);

            runOnUiThread(() -> {
                renderTopics("");
                updateSelectedChips();
                updateCounter();
                binding.btnContinue.setEnabled(!selectedTopics.isEmpty());
            });
        });
    }

    private void handleDonePressed(String query) {
        boolean hasMatch = allTopics.stream()
                .anyMatch(t -> t.name.toLowerCase().contains(query.toLowerCase())
                        || t.category.toLowerCase().contains(query.toLowerCase()));

        if (hasMatch) return;

        if (selectedTopics.size() >= getMaxTopics()) {
            showLimitReachedFeedback();
            return;
        }

        validateNewTopic(query);
    }

    // Shows the limit message with a tappable "upgrade your plan" link
    private void showLimitReachedFeedback() {
        String base = "Topic limit reached (" + getMaxTopics() + "). Remove a topic or ";
        String link = "upgrade your plan";
        String full = base + link + ".";

        SpannableString spannable = new SpannableString(full);

        int linkStart = base.length();
        int linkEnd = linkStart + link.length();

        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                startActivity(new Intent(InterestsActivity.this, UpgradeActivity.class));
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(ContextCompat.getColor(InterestsActivity.this, R.color.primary));
                ds.setUnderlineText(true);
            }
        }, linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        binding.tvAiFeedback.setText(spannable);
        binding.tvAiFeedback.setVisibility(View.VISIBLE);
    }

    private void validateNewTopic(String topicName) {
        showFeedback("Checking '" + topicName + "'…", true);

        String prompt = "Is '" + topicName + "' a SPECIFIC formal academic or professional subject "
                + "that would appear as a standalone course title on a university transcript?\n\n"
                + "CRITICAL RULES:\n"
                + "- The term must be specific enough to have its own dedicated course.\n"
                + "- Generic or vague terms like 'cyber', 'tech', 'science', 'health', 'business' "
                + "are INVALID because they are categories, not subjects.\n"
                + "- 'Cybersecurity' is VALID. 'Cyber' is INVALID.\n"
                + "- 'Organic Chemistry' is VALID. 'Chemistry stuff' is INVALID.\n"
                + "- 'Machine Learning' is VALID. 'AI' alone is INVALID (too broad).\n"
                + "- 'Welding Technology' is VALID. 'Building' alone is INVALID.\n\n"
                + "Respond ONLY with valid JSON, no markdown:\n"
                + "{\"valid\": true or false, \"category\": \"category name if valid, else null\", "
                + "\"reason\": \"one-sentence explanation\"}\n"
                + "Use broad academic categories like: Science, Mathematics, Law, Medicine, "
                + "Engineering, Business, History, Literature, Technology, Arts, Social Sciences, "
                + "Computer Science.";

        GeminiClient.getInstance().generateContent(

                new GeminiRequest(prompt)
        ).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                String text = response.isSuccessful() && response.body() != null
                        ? response.body().getResponseText() : null;

                if (text == null) {
                    showFeedback("Could not validate topic. Please try again.", false);
                    return;
                }

                parseValidationResponse(text, topicName);
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                showFeedback("Could not validate topic. Please check your connection.", false);
            }
        });
    }

    private void parseValidationResponse(String json, String topicName) {
        try {
            json = json.replaceAll("```json", "").replaceAll("```", "").trim();
            JSONObject obj = new JSONObject(json);
            boolean valid = obj.getBoolean("valid");
            String category = obj.isNull("category") ? "General" : obj.getString("category");

            if (!valid) {
                String reason = obj.optString("reason", "");
                String message = "'" + topicName + "' doesn't appear to be a specific educational subject.";
                if (!reason.isEmpty()) {
                    message += "\n" + reason;
                }
                showFeedback(message, false);
                return;
            }

            addNewTopicGlobally(topicName, category);

        } catch (Exception e) {
            showFeedback("Could not validate topic. Please try again.", false);
        }
    }

    private void addNewTopicGlobally(String topicName, String category) {
        AsyncTask.execute(() -> {
            String name = topicName.substring(0, 1).toUpperCase() + topicName.substring(1);

            Topic newTopic = new Topic();
            newTopic.name = name;
            newTopic.category = category;

            List<Topic> toInsert = new ArrayList<>();
            toInsert.add(newTopic);
            db.topicDao().insertAll(toInsert);

            allTopics = db.topicDao().getAllTopics();

            Topic inserted = allTopics.stream()
                    .filter(t -> t.name.equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);

            if (inserted != null && selectedTopics.stream().noneMatch(t -> t.id == inserted.id)) {
                selectedTopics.add(inserted);

                UserTopic ut = new UserTopic();
                ut.userId = userId;
                ut.topicId = inserted.id;
                db.topicDao().insertUserTopic(ut);
            }

            runOnUiThread(() -> {
                binding.editSearch.setText("");
                renderTopics("");
                updateSelectedChips();
                updateCounter();
                binding.btnContinue.setEnabled(!selectedTopics.isEmpty());
                showFeedback("'" + name + "' added to " + category + " and selected!", true);
            });
        });
    }

    private void showFeedback(String message, boolean isPositive) {
        runOnUiThread(() -> {
            binding.tvAiFeedback.setText(message);
            binding.tvAiFeedback.setTextColor(ContextCompat.getColor(this,
                    isPositive ? R.color.accent_green : R.color.accent_red));
            binding.tvAiFeedback.setVisibility(View.VISIBLE);
        });
    }

    private void hideFeedback() {
        binding.tvAiFeedback.setVisibility(View.GONE);
    }

    private void renderTopics(String query) {
        binding.containerTopics.removeAllViews();

        Map<String, List<Topic>> grouped = new LinkedHashMap<>();
        for (Topic t : allTopics) {
            if (query.isEmpty() || t.name.toLowerCase().contains(query) || t.category.toLowerCase().contains(query)) {
                grouped.computeIfAbsent(t.category, k -> new ArrayList<>()).add(t);
            }
        }

        for (Map.Entry<String, List<Topic>> entry : grouped.entrySet()) {
            TextView header = new TextView(this);
            header.setText(entry.getKey());
            header.setTextSize(13f);
            header.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            header.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            headerParams.setMargins(0, 24, 0, 8);
            header.setLayoutParams(headerParams);
            binding.containerTopics.addView(header);

            ChipGroup group = new ChipGroup(this);
            group.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            for (Topic topic : entry.getValue()) {
                group.addView(buildChip(topic));
            }
            binding.containerTopics.addView(group);
        }
    }

    private Chip buildChip(Topic topic) {
        boolean isSelected = selectedTopics.stream().anyMatch(t -> t.id == topic.id);

        Chip chip = new Chip(this);
        chip.setText(topic.name);
        chip.setCheckable(true);
        chip.setChecked(isSelected);
        applyChipStyle(chip, isSelected);

        chip.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && selectedTopics.size() >= getMaxTopics()) {
                chip.setChecked(false);
                showLimitReachedFeedback();
                return;
            }
            if (checked) {
                selectedTopics.add(topic);
            } else {
                selectedTopics.removeIf(t -> t.id == topic.id);
            }
            applyChipStyle(chip, checked);
            updateSelectedChips();
            updateCounter();
            binding.btnContinue.setEnabled(!selectedTopics.isEmpty());

            // Clear the limit feedback once user is back under the cap
            if (selectedTopics.size() < getMaxTopics()) {
                hideFeedback();
            }
        });

        return chip;
    }

    private void applyChipStyle(Chip chip, boolean selected) {
        chip.setChipBackgroundColorResource(selected ? R.color.chip_selected : R.color.chip_unselected);
        chip.setTextColor(ContextCompat.getColor(this, selected ? R.color.text_on_primary : R.color.text_primary));
        chip.setChipStrokeColorResource(selected ? R.color.chip_selected : R.color.divider);
        chip.setChipStrokeWidth(2f);
    }

    private void updateSelectedChips() {
        binding.chipGroupSelected.removeAllViews();

        if (selectedTopics.isEmpty()) {
            binding.labelYourPicks.setVisibility(View.GONE);
            return;
        }

        binding.labelYourPicks.setVisibility(View.VISIBLE);
        for (Topic topic : selectedTopics) {
            Chip chip = new Chip(this);
            chip.setText(topic.name);
            chip.setCloseIconVisible(true);
            chip.setChipBackgroundColorResource(R.color.primary_light);
            chip.setTextColor(ContextCompat.getColor(this, R.color.primary));

            chip.setOnCloseIconClickListener(v -> {
                selectedTopics.removeIf(t -> t.id == topic.id);
                updateSelectedChips();
                updateCounter();
                renderTopics(binding.editSearch.getText().toString().trim().toLowerCase());
                binding.btnContinue.setEnabled(!selectedTopics.isEmpty());

                // Clear limit feedback when removing a topic frees up a slot
                if (selectedTopics.size() < getMaxTopics()) {
                    hideFeedback();
                }
            });
            binding.chipGroupSelected.addView(chip);
        }
    }

    private void updateCounter() {
        binding.tvCounter.setText(selectedTopics.size() + " / " + getMaxTopics() + " selected");
    }

    private void saveAndContinue() {
        binding.btnContinue.setEnabled(false);

        AsyncTask.execute(() -> {
            // Guard against stale session after destructive migration
            User user = db.userDao().findById(userId);
            if (user == null) {
                session.clearSession();
                runOnUiThread(() -> {
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                });
                return;
            }

            db.topicDao().clearUserTopics(userId);
            for (Topic t : selectedTopics) {
                UserTopic ut = new UserTopic();
                ut.userId = userId;
                ut.topicId = t.id;
                db.topicDao().insertUserTopic(ut);
            }

            if (!session.isLoggedIn()) {
                session.saveSession(user.id, user.username);
            }

            runOnUiThread(() -> {
                Intent intent = new Intent(this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        });
    }
}