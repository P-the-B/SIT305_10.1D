package com.example.mylearning;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.User;
import com.example.mylearning.databinding.ActivitySignupBinding;
import com.example.mylearning.util.HashUtil;
import com.example.mylearning.util.ToolbarUtil;

public class SignupActivity extends AppCompatActivity {

    private ActivitySignupBinding binding;
    private AppDatabase db;

    private static final String[] SECURITY_QUESTIONS = {
            "What was the name of your first pet?",
            "What city were you born in?",
            "What is your mother's maiden name?"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getInstance(this);

        // Back arrow in toolbar
        setSupportActionBar(binding.toolbar);
        ToolbarUtil.setup(this, binding.toolbar, "Create Account", true);

        setupSecurityQuestionDropdown();
        setupValidationWatchers();

        binding.btnCreateAccount.setOnClickListener(v -> attemptSignup());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void setupSecurityQuestionDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                SECURITY_QUESTIONS
        );
        binding.spinnerSecurityQuestion.setAdapter(adapter);
        binding.spinnerSecurityQuestion.setOnItemClickListener((parent, view, position, id) -> validateForm());
    }

    private void setupValidationWatchers() {
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { validateForm(); }
        };

        binding.editUsername.addTextChangedListener(watcher);
        binding.editEmail.addTextChangedListener(watcher);
        binding.editConfirmEmail.addTextChangedListener(watcher);
        binding.editPassword.addTextChangedListener(watcher);
        binding.editConfirmPassword.addTextChangedListener(watcher);
        binding.editPhone.addTextChangedListener(watcher);
        binding.editSecurityAnswer.addTextChangedListener(watcher);
        binding.editConfirmSecurityAnswer.addTextChangedListener(watcher);
    }

    private void validateForm() {
        String username = binding.editUsername.getText().toString().trim();
        String email = binding.editEmail.getText().toString().trim();
        String confirmEmail = binding.editConfirmEmail.getText().toString().trim();
        String password = binding.editPassword.getText().toString();
        String confirmPassword = binding.editConfirmPassword.getText().toString();
        String phone = binding.editPhone.getText().toString().trim();
        String answer = binding.editSecurityAnswer.getText().toString().trim();
        String confirmAnswer = binding.editConfirmSecurityAnswer.getText().toString().trim();
        String question = binding.spinnerSecurityQuestion.getText().toString().trim();

        // setErrorEnabled(false) after clearing so TextInputLayout collapses the reserved error space
        if (username.length() > 0 && username.length() < 3) {
            binding.layoutUsername.setError("At least 3 characters");
        } else {
            binding.layoutUsername.setError(null);
            binding.layoutUsername.setErrorEnabled(false);
        }

        if (!email.isEmpty() && !confirmEmail.isEmpty() && !email.equals(confirmEmail)) {
            binding.layoutConfirmEmail.setError("Emails do not match");
        } else {
            binding.layoutConfirmEmail.setError(null);
            binding.layoutConfirmEmail.setErrorEnabled(false);
        }

        if (password.length() > 0 && password.length() < 3) {
            binding.layoutPassword.setError("At least 3 characters");
        } else {
            binding.layoutPassword.setError(null);
            binding.layoutPassword.setErrorEnabled(false);
        }

        if (!password.isEmpty() && !confirmPassword.isEmpty() && !password.equals(confirmPassword)) {
            binding.layoutConfirmPassword.setError("Passwords do not match");
        } else {
            binding.layoutConfirmPassword.setError(null);
            binding.layoutConfirmPassword.setErrorEnabled(false);
        }

        if (phone.length() > 0 && phone.length() < 6) {
            binding.layoutPhone.setError("At least 6 digits");
        } else {
            binding.layoutPhone.setError(null);
            binding.layoutPhone.setErrorEnabled(false);
        }

        if (!answer.isEmpty() && !confirmAnswer.isEmpty() && !answer.equals(confirmAnswer)) {
            binding.layoutConfirmSecurityAnswer.setError("Answers do not match");
        } else {
            binding.layoutConfirmSecurityAnswer.setError(null);
            binding.layoutConfirmSecurityAnswer.setErrorEnabled(false);
        }

        boolean valid = username.length() >= 3
                && email.contains("@")
                && email.equals(confirmEmail)
                && password.length() >= 3
                && password.equals(confirmPassword)
                && phone.length() >= 6
                && !question.isEmpty()
                && answer.length() >= 1
                && answer.equals(confirmAnswer);

        binding.btnCreateAccount.setEnabled(valid);
    }

    private void attemptSignup() {
        clearErrors();

        String username = binding.editUsername.getText().toString().trim();
        String email = binding.editEmail.getText().toString().trim();
        String password = binding.editPassword.getText().toString();
        String phone = binding.editPhone.getText().toString().trim();
        String question = binding.spinnerSecurityQuestion.getText().toString();
        String answer = binding.editSecurityAnswer.getText().toString().trim();

        setLoading(true);

        AsyncTask.execute(() -> {
            User existing = db.userDao().findByEmail(email);
            if (existing != null) {
                runOnUiThread(() -> {
                    setLoading(false);
                    binding.layoutEmail.setError("Email already registered");
                });
                return;
            }

            User user = new User();
            user.username = username;
            user.email = email;
            user.passwordHash = HashUtil.sha256(email.toLowerCase(), password);
            user.phone = phone;
            user.securityQuestion = question;
            user.securityAnswerHash = HashUtil.sha256(answer.toLowerCase());

            long newUserId = db.userDao().insert(user);

            runOnUiThread(() -> {
                setLoading(false);
                Intent intent = new Intent(this, InterestsActivity.class);
                intent.putExtra("userId", (int) newUserId);
                startActivity(intent);
                finish();
            });
        });
    }

    private void clearErrors() {
        binding.layoutUsername.setError(null);
        binding.layoutUsername.setErrorEnabled(false);
        binding.layoutEmail.setError(null);
        binding.layoutEmail.setErrorEnabled(false);
        binding.layoutConfirmEmail.setError(null);
        binding.layoutConfirmEmail.setErrorEnabled(false);
        binding.layoutPassword.setError(null);
        binding.layoutPassword.setErrorEnabled(false);
        binding.layoutConfirmPassword.setError(null);
        binding.layoutConfirmPassword.setErrorEnabled(false);
        binding.layoutSecurityAnswer.setError(null);
        binding.layoutSecurityAnswer.setErrorEnabled(false);
        binding.layoutConfirmSecurityAnswer.setError(null);
        binding.layoutConfirmSecurityAnswer.setErrorEnabled(false);
    }

    private void setLoading(boolean loading) {
        binding.btnCreateAccount.setEnabled(!loading);
        binding.btnCreateAccount.setText(loading ? "Creating account…" : getString(R.string.btn_create_account));
    }
}