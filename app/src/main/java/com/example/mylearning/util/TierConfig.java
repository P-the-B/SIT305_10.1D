package com.example.mylearning.util;

// All tier limits live here — adjust values, nothing else needs touching.
// Billing product IDs must match exactly what's registered in Play Console / Billing Lab.
public class TierConfig {

    // ── Tier name constants ───────────────────────────────────────────────────
    // These strings are persisted in SharedPreferences — never change them post-release
    public static final String TIER_FREE     = "free";
    public static final String TIER_STARTER  = "starter";
    public static final String TIER_ADVANCED = "advanced";

    // ── Play Billing product IDs ──────────────────────────────────────────────
    // Register these exact strings as INAPP products in Play Console / Billing Lab
    public static final String PRODUCT_STARTER  = "tier_starter";
    public static final String PRODUCT_ADVANCED = "tier_advanced";

    // ── Topic selection caps ──────────────────────────────────────────────────
    // Soft cap only — existing selections are never trimmed, only new additions blocked
    public static final int TOPICS_FREE     = 2;
    public static final int TOPICS_STARTER  = 5;
    public static final int TOPICS_ADVANCED = 10;

    // ── Daily quiz limits (reset at midnight) ─────────────────────────────────
    public static final int DAILY_QUIZZES_FREE     = 2;
    public static final int DAILY_QUIZZES_STARTER  = 7;
    public static final int DAILY_QUIZZES_ADVANCED = Integer.MAX_VALUE;

    // ── Questions generated per Gemini quiz call ──────────────────────────────
    public static final int QUESTIONS_FREE     = 3;
    public static final int QUESTIONS_STARTER  = 5;
    public static final int QUESTIONS_ADVANCED = 10;

    // ── AI explanations per ResultsActivity session ───────────────────────────
    // Not persisted — counter resets every time Results screen opens, which is intentional.
    // One explanation is enough to demo the feature; more requires upgrading.
    public static final int EXPLANATIONS_FREE = 1;
    public static final int EXPLANATIONS_PAID = Integer.MAX_VALUE;

    // ── History expand limit ──────────────────────────────────────────────────
    // Free users see attempt headers (date, topic, score) but cannot expand into question detail.
    // Starter gets 15 full attempts; Advanced gets everything.
    public static final int HISTORY_LIMIT_STARTER  = 15;
    public static final int HISTORY_LIMIT_ADVANCED = Integer.MAX_VALUE;

    // ── Video suggestions per screen session ──────────────────────────────────
    // Starter gets 1 video search per question; Advanced is uncapped.
    // Counter is per-screen-instance, same pattern as explanations.
    public static final int VIDEO_SUGGESTIONS_FREE     = 0;
    public static final int VIDEO_SUGGESTIONS_STARTER  = 1;
    public static final int VIDEO_SUGGESTIONS_ADVANCED = Integer.MAX_VALUE;
}
