package com.example.mylearning.util;

import android.content.Context;

// All feature gate checks go through here — nothing in the app reads getTier() directly.
// This makes it trivial to add/remove gates and keeps business logic out of Activities.
public class TierGate {

    private final SessionManager session;

    public TierGate(Context context) {
        this.session = new SessionManager(context);
    }

    // ── Quiz gates ────────────────────────────────────────────────────────────

    // Checks both the daily cap and the tier — Advanced is always allowed.
    // Must call session.refreshDailyCountIfNeeded() first so the count is accurate.
    public boolean canStartQuiz() {
        if (TierConfig.TIER_ADVANCED.equals(session.getTier())) return true;
        session.refreshDailyCountIfNeeded();
        int limit = TierConfig.TIER_STARTER.equals(session.getTier())
                ? TierConfig.DAILY_QUIZZES_STARTER
                : TierConfig.DAILY_QUIZZES_FREE;
        return session.getTodayQuizCount() < limit;
    }

    // How many questions Gemini should generate for this user's tier
    public int getQuestionCount() {
        switch (session.getTier()) {
            case TierConfig.TIER_STARTER:  return TierConfig.QUESTIONS_STARTER;
            case TierConfig.TIER_ADVANCED: return TierConfig.QUESTIONS_ADVANCED;
            default:                       return TierConfig.QUESTIONS_FREE;
        }
    }

    // ── Topic cap ─────────────────────────────────────────────────────────────

    // Used by InterestsActivity and ProgressFragment to enforce selection limits.
    // Existing over-limit selections are never trimmed — only new additions are blocked.
    public int getMaxTopics() {
        switch (session.getTier()) {
            case TierConfig.TIER_STARTER:  return TierConfig.TOPICS_STARTER;
            case TierConfig.TIER_ADVANCED: return TierConfig.TOPICS_ADVANCED;
            default:                       return TierConfig.TOPICS_FREE;
        }
    }

    // ── Results screen ────────────────────────────────────────────────────────

    // Free users get 1 explanation per ResultsActivity session.
    // The counter is held in the Activity, not persisted — intentional, resets per screen open.
    public int getExplanationLimit() {
        return TierConfig.TIER_FREE.equals(session.getTier())
                ? TierConfig.EXPLANATIONS_FREE
                : TierConfig.EXPLANATIONS_PAID;
    }

    // ── Lesson screen ─────────────────────────────────────────────────────────

    // Free users see a locked state card instead of the Gemini summary.
    // Starred question mode stays free — they earned those by completing quizzes.
    public boolean canViewLessonSummary() {
        return !TierConfig.TIER_FREE.equals(session.getTier());
    }

    // ── History screen ────────────────────────────────────────────────────────

    // All tiers can enter History — free users see headers only (date, topic, score).
    // canExpandHistory() gates the question-level detail inside each attempt card.
    public boolean canExpandHistory() {
        return !TierConfig.TIER_FREE.equals(session.getTier());
    }

    // How many attempts to load with full expand capability
    public int getHistoryLimit() {
        switch (session.getTier()) {
            case TierConfig.TIER_STARTER:  return TierConfig.HISTORY_LIMIT_STARTER;
            case TierConfig.TIER_ADVANCED: return TierConfig.HISTORY_LIMIT_ADVANCED;
            default:                       return Integer.MAX_VALUE; // free sees all headers
        }
    }

    // ── Topic detail (Progress tab card tap) ──────────────────────────────────

    // Free users get the upgrade pitch bottom sheet instead of the full detail screen.
    public boolean canViewTopicDetail() {
        return !TierConfig.TIER_FREE.equals(session.getTier());
    }

    // ── Profile AI insight ─────────────────────────────────────────────────────

    // Advanced-only Gemini call on the Profile screen summarising the user's weak areas
    public boolean canViewAiInsight() {
        return TierConfig.TIER_ADVANCED.equals(session.getTier());
    }

    // ── Video suggestions ─────────────────────────────────────────────────────

    // Per-screen-session cap, same pattern as explanations.
    // Counter held in the Activity/Fragment, not persisted.
    public int getVideoSuggestionLimit() {
        switch (session.getTier()) {
            case TierConfig.TIER_STARTER:  return TierConfig.VIDEO_SUGGESTIONS_STARTER;
            case TierConfig.TIER_ADVANCED: return TierConfig.VIDEO_SUGGESTIONS_ADVANCED;
            default:                       return TierConfig.VIDEO_SUGGESTIONS_FREE;
        }
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    // Readable tier label for UI display (tier badge chip, profile screen)
    public String getTierLabel() {
        switch (session.getTier()) {
            case TierConfig.TIER_STARTER:  return "Starter";
            case TierConfig.TIER_ADVANCED: return "Advanced";
            default:                       return "Free";
        }
    }

    public boolean isFree() {
        return TierConfig.TIER_FREE.equals(session.getTier());
    }
}
