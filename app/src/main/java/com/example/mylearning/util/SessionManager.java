package com.example.mylearning.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SessionManager {

    private static final String PREF_NAME    = "mylearning_session";
    private static final String KEY_USER_ID  = "userId";
    private static final String KEY_USERNAME = "username";

    // ── Tier keys ─────────────────────────────────────────────────────────────
    // KEY_TIER stores the string set by BillingManager on a successful purchase.
    // Swapping to real billing only touches BillingManager.grantTier() — not here.
    private static final String KEY_TIER       = "userTier";

    // ── Daily quiz throttle keys ──────────────────────────────────────────────
    // Date stored as "yyyy-MM-dd" so the count resets automatically on a new calendar day.
    private static final String KEY_QUIZ_DATE  = "quizDate";
    private static final String KEY_QUIZ_COUNT = "quizCount";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ── Auth session ──────────────────────────────────────────────────────────

    // Called on successful login
    public void saveSession(int userId, String username) {
        prefs.edit()
                .putInt(KEY_USER_ID, userId)
                .putString(KEY_USERNAME, username)
                .apply();
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    // -1 means no active session
    public boolean isLoggedIn() {
        return getUserId() != -1;
    }

    // Called on logout — clears auth AND tier so a new account starts fresh
    public void clearSession() {
        prefs.edit().clear().apply();
    }

    // ── Tier management ───────────────────────────────────────────────────────

    // Written by BillingManager after a confirmed, acknowledged purchase
    public void saveTier(String tier) {
        prefs.edit().putString(KEY_TIER, tier).apply();
    }

    // Defaults to free — safest fallback if nothing has been purchased
    public String getTier() {
        return prefs.getString(KEY_TIER, TierConfig.TIER_FREE);
    }

    // ── Daily quiz throttle ───────────────────────────────────────────────────

    // Always call this before reading getTodayQuizCount() — resets on a new calendar day
    public void refreshDailyCountIfNeeded() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        if (!today.equals(prefs.getString(KEY_QUIZ_DATE, ""))) {
            prefs.edit()
                    .putString(KEY_QUIZ_DATE, today)
                    .putInt(KEY_QUIZ_COUNT, 0)
                    .apply();
        }
    }

    public int getTodayQuizCount() {
        return prefs.getInt(KEY_QUIZ_COUNT, 0);
    }

    // Called by QuizActivity once an attempt row is committed to Room
    public void incrementQuizCount() {
        prefs.edit().putInt(KEY_QUIZ_COUNT, getTodayQuizCount() + 1).apply();
    }
}
