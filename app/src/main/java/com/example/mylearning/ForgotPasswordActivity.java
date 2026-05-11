package com.example.mylearning;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.User;
import com.example.mylearning.databinding.ActivityForgotPasswordBinding;
import com.example.mylearning.util.HashUtil;
import com.example.mylearning.util.ToolbarUtil;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;
    private AppDatabase db;

    private enum Step { FIND_ACCOUNT, VERIFY_ANSWER, RESET_PASSWORD }
    private Step currentStep = Step.FIND_ACCOUNT;

    // Null when the email lookup found nothing — verifyAnswer always fails in that case,
    // preventing an attacker from learning which emails are registered.
    private User foundUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getInstance(this);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAction.setOnClickListener(v -> handleAction());

        setSupportActionBar(binding.toolbar);
        ToolbarUtil.setup(this, binding.toolbar, "Reset Password", true);
    }

    private void handleAction() {
        switch (currentStep) {
            case FIND_ACCOUNT:   findAccount();   break;
            case VERIFY_ANSWER:  verifyAnswer();  break;
            case RESET_PASSWORD: resetPassword(); break;
        }
    }

    // Step 1 — look up account by email.
    // Always advances to the security question regardless of outcome so we don't
    // reveal whether a given email address is registered in the app.
    private void findAccount() {
        binding.layoutEmail.setError(null);
        String email = binding.editEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            binding.layoutEmail.setError("Enter your email");
            return;
        }

        setLoading(true);
        AsyncTask.execute(() -> {
            User user = db.userDao().findByEmail(email);
            runOnUiThread(() -> {
                setLoading(false);
                foundUser = user; // null is intentional — verifyAnswer handles it
                // Show real question if found, generic placeholder otherwise
                String question = user != null
                        ? user.securityQuestion
                        : "What was the name of your first pet?";
                showSecurityQuestion(question);
            });
        });
    }

    private void showSecurityQuestion(String question) {
        binding.labelSecurityQuestion.setText(question);
        binding.labelSecurityQuestion.setVisibility(View.VISIBLE);
        binding.layoutSecurityAnswer.setVisibility(View.VISIBLE);
        binding.editEmail.setEnabled(false);
        binding.btnAction.setText("Verify Answer");
        currentStep = Step.VERIFY_ANSWER;
    }

    // Step 2 — check security answer hash.
    // If foundUser is null the answer is always wrong — same error either way.
    private void verifyAnswer() {
        binding.layoutSecurityAnswer.setError(null);
        String answer = binding.editSecurityAnswer.getText().toString().trim();

        if (TextUtils.isEmpty(answer)) {
            binding.layoutSecurityAnswer.setError("Enter your answer");
            return;
        }

        if (foundUser == null) {
            binding.layoutSecurityAnswer.setError("Incorrect answer");
            return;
        }

        String hash = HashUtil.sha256(answer.toLowerCase());
        if (!hash.equals(foundUser.securityAnswerHash)) {
            binding.layoutSecurityAnswer.setError("Incorrect answer");
            return;
        }

        binding.layoutNewPassword.setVisibility(View.VISIBLE);
        binding.layoutConfirmNewPassword.setVisibility(View.VISIBLE);
        binding.layoutSecurityAnswer.setEnabled(false);
        binding.btnAction.setText("Reset Password");
        currentStep = Step.RESET_PASSWORD;
    }

    // Step 3 — save new password hash, salted with the user's email.
    private void resetPassword() {
        binding.layoutNewPassword.setError(null);
        binding.layoutConfirmNewPassword.setError(null);

        String newPass = binding.editNewPassword.getText().toString();
        String confirm = binding.editConfirmNewPassword.getText().toString();

        if (newPass.length() < 6) {
            binding.layoutNewPassword.setError("At least 6 characters");
            return;
        }
        if (!newPass.equals(confirm)) {
            binding.layoutConfirmNewPassword.setError("Passwords do not match");
            return;
        }

        // Defensive — verifyAnswer() should have blocked this path if foundUser is null
        if (foundUser == null) { finish(); return; }

        setLoading(true);
        String newHash = HashUtil.sha256(foundUser.email.toLowerCase(), newPass);

        AsyncTask.execute(() -> {
            db.userDao().updatePassword(foundUser.id, newHash);
            runOnUiThread(() -> {
                setLoading(false);
                finish();
            });
        });
    }

    private void setLoading(boolean loading) {
        binding.btnAction.setEnabled(!loading);
        if (loading) binding.btnAction.setText("Please wait…");
    }
}