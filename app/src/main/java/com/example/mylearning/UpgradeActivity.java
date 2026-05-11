package com.example.mylearning;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mylearning.billing.SimulatedBillingManager;
import com.example.mylearning.databinding.ActivityUpgradeBinding;
import com.example.mylearning.util.SessionManager;
import com.example.mylearning.util.TierConfig;
import com.example.mylearning.util.TierGate;
import com.example.mylearning.util.ToolbarUtil;
import com.google.android.material.snackbar.Snackbar;

public class UpgradeActivity extends AppCompatActivity
        implements SimulatedBillingManager.PurchaseResultListener {

    private ActivityUpgradeBinding binding;
    private SimulatedBillingManager billing;
    private TierGate gate;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUpgradeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        ToolbarUtil.setup(this, binding.toolbar, "Upgrade", true);

        gate = new TierGate(this);
        session = new SessionManager(this);
        billing = new SimulatedBillingManager(this);
        billing.setResultListener(this);
        billing.startConnection();

        populateFeatureLists();
        refreshTierState();

        binding.btnPurchaseStarter.setOnClickListener(v ->
                billing.purchase(this, TierConfig.PRODUCT_STARTER));

        binding.btnPurchaseAdvanced.setOnClickListener(v ->
                billing.purchase(this, TierConfig.PRODUCT_ADVANCED));
    }

    @Override
    protected void onStop() {
        super.onStop();
        billing.endConnection();
    }

    private void populateFeatureLists() {
        String[] freeFeatures = {
                "2 topics",
                "2 quizzes per day",
                "3 questions per quiz",
                "1 AI explanation per result",
                "Basic progress tracking"
        };

        String[] starterFeatures = {
                "Up to 5 topics",
                "7 quizzes per day",
                "5 questions per quiz",
                "Full AI explanations",
                "AI lesson summaries",
                "History with full detail",
                "1 video suggestion per question",
                "Topic detail insights"
        };

        String[] advancedFeatures = {
                "Up to 10 topics",
                "Unlimited quizzes per day",
                "10 questions per quiz",
                "Full AI explanations",
                "AI lesson summaries",
                "Unlimited history",
                "Unlimited video suggestions",
                "Topic detail insights",
                "AI-powered profile insights"
        };

        addFeatureItems(binding.containerFreeTierFeatures, freeFeatures);
        addFeatureItems(binding.containerStarterFeatures, starterFeatures);
        addFeatureItems(binding.containerAdvancedFeatures, advancedFeatures);
    }

    private void addFeatureItems(LinearLayout container, String[] features) {
        for (String feature : features) {
            TextView tv = new TextView(this);
            tv.setText("\u2713  " + feature);
            tv.setTextSize(14f);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tv.setPadding(0, 6, 0, 6);
            container.addView(tv);
        }
    }

    // Reflects current tier state — highlights active plan, hides/shows buttons
    private void refreshTierState() {
        gate = new TierGate(this);
        String tierLabel = gate.getTierLabel();
        binding.tvCurrentTier.setText("Current plan: " + tierLabel);

        boolean isFree     = gate.isFree();
        boolean isStarter  = TierConfig.TIER_STARTER.equals(session.getTier());
        boolean isAdvanced = TierConfig.TIER_ADVANCED.equals(session.getTier());

        // Free card
        binding.tvFreeCurrentPlan.setVisibility(isFree ? View.VISIBLE : View.GONE);

        // If user is on a paid tier, show downgrade option on the Free card
        if (!isFree) {
            addDowngradeButton();
        }

        // If user is on Advanced, show downgrade option on the Starter card
        if (isAdvanced) {
            addDowngradeToStarterButton();
        }

        // Starter card
        binding.btnPurchaseStarter.setVisibility(
                (isStarter || isAdvanced) ? View.GONE : View.VISIBLE);
        binding.tvStarterCurrentPlan.setVisibility(isStarter ? View.VISIBLE : View.GONE);

        // Advanced card
        binding.btnPurchaseAdvanced.setVisibility(isAdvanced ? View.GONE : View.VISIBLE);
        binding.tvAdvancedCurrentPlan.setVisibility(isAdvanced ? View.VISIBLE : View.GONE);

        // Highlight active tier with a coloured stroke
        binding.cardFree.setStrokeWidth(isFree ? 3 : 0);
        binding.cardFree.setStrokeColor(ContextCompat.getColor(this, R.color.primary));

        binding.cardStarter.setStrokeWidth(isStarter ? 3 : 0);
        binding.cardStarter.setStrokeColor(ContextCompat.getColor(this, R.color.primary));

        binding.cardAdvanced.setStrokeWidth(isAdvanced ? 3 : 0);
        binding.cardAdvanced.setStrokeColor(ContextCompat.getColor(this, R.color.primary));
    }

    // Adds a "Downgrade to Free" link on the Free card for paid users
    private void addDowngradeButton() {
        LinearLayout freeCardLayout = (LinearLayout) binding.containerFreeTierFeatures.getParent();

        if (freeCardLayout.findViewWithTag("downgrade_btn") != null) return;

        TextView tvDowngrade = new TextView(this);
        tvDowngrade.setTag("downgrade_btn");
        tvDowngrade.setText("Downgrade to Free");
        tvDowngrade.setTextSize(14f);
        tvDowngrade.setTextColor(ContextCompat.getColor(this, R.color.accent_red));
        tvDowngrade.setPadding(0, 24, 0, 8);
        tvDowngrade.setOnClickListener(v -> confirmDowngrade());
        freeCardLayout.addView(tvDowngrade);
    }

    // Adds a "Downgrade to Starter" link on the Starter card for Advanced users
    private void addDowngradeToStarterButton() {
        LinearLayout starterCardLayout = (LinearLayout) binding.containerStarterFeatures.getParent();

        if (starterCardLayout.findViewWithTag("downgrade_starter_btn") != null) return;

        TextView tvDowngrade = new TextView(this);
        tvDowngrade.setTag("downgrade_starter_btn");
        tvDowngrade.setText("Downgrade to Starter");
        tvDowngrade.setTextSize(14f);
        tvDowngrade.setTextColor(ContextCompat.getColor(this, R.color.accent_red));
        tvDowngrade.setPadding(0, 24, 0, 8);
        tvDowngrade.setOnClickListener(v -> confirmDowngradeToStarter());
        starterCardLayout.addView(tvDowngrade);
    }

    private void confirmDowngradeToStarter() {
        new AlertDialog.Builder(this)
                .setTitle("Downgrade to Starter?")
                .setMessage("You'll lose access to Advanced features like AI profile insights "
                        + "and unlimited video suggestions. Your data will be kept.")
                .setPositiveButton("Downgrade", (d, w) -> {
                    session.saveTier(TierConfig.TIER_STARTER);
                    refreshTierState();
                    Snackbar.make(binding.getRoot(), "Downgraded to Starter plan.",
                            Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Confirmation dialog before downgrading to Free
    private void confirmDowngrade() {
        new AlertDialog.Builder(this)
                .setTitle("Downgrade to Free?")
                .setMessage("You'll lose access to paid features like full history, "
                        + "AI summaries, and video suggestions. Your data will be kept.")
                .setPositiveButton("Downgrade", (d, w) -> {
                    session.saveTier(TierConfig.TIER_FREE);
                    refreshTierState();
                    Snackbar.make(binding.getRoot(), "Downgraded to Free plan.",
                            Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onPurchaseSuccess(String tier) {
        refreshTierState();
        Snackbar.make(binding.getRoot(), "Upgraded to " + gate.getTierLabel() + "!",
                Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onPurchaseCancelled() {
        // Nothing to do
    }

    @Override
    public void onPurchaseError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}