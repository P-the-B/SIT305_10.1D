package com.example.mylearning.billing;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.mylearning.R;
import com.example.mylearning.util.SessionManager;
import com.example.mylearning.util.TierConfig;
import com.google.android.material.bottomsheet.BottomSheetDialog;

// Simulated purchase flow for assignment scope — no paid developer account needed.
// Interface matches BillingManager exactly: swap this class for the real one when
// going to production. UpgradeActivity never knows the difference.
public class SimulatedBillingManager {

    // Same callback interface as BillingManager — Activities implement this
    public interface PurchaseResultListener {
        void onPurchaseSuccess(String tier);
        void onPurchaseCancelled();
        void onPurchaseError(String message);
    }

    private final SessionManager session;
    private PurchaseResultListener resultListener;

    public SimulatedBillingManager(Activity activity) {
        session = new SessionManager(activity);
    }

    public void setResultListener(PurchaseResultListener listener) {
        this.resultListener = listener;
    }

    // No-op — real BillingManager connects to Play here
    public void startConnection() {}

    // No-op — real BillingManager disconnects here
    public void endConnection() {}

    // Shows a styled bottom sheet with tier features and a confirm button.
    // On confirm, writes tier to SessionManager and fires the callback.
    public void purchase(Activity activity, String productId) {
        String tierName;
        String price;
        String[] features;

        // Map product ID to display details — keeps pricing data out of TierConfig
        // so the config stays pure-logic and this class owns presentation
        if (TierConfig.PRODUCT_STARTER.equals(productId)) {
            tierName = "Starter";
            price = "$4.99 / month";
            features = new String[]{
                    "Up to 5 topics",
                    "7 quizzes per day",
                    "5 questions per quiz",
                    "Full AI explanations",
                    "AI lesson summaries",
                    "History with full detail",
                    "1 video suggestion per question",
                    "Topic detail insights"
            };
        } else if (TierConfig.PRODUCT_ADVANCED.equals(productId)) {
            tierName = "Advanced";
            price = "$9.99 / month";
            features = new String[]{
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
        } else {
            if (resultListener != null) {
                resultListener.onPurchaseError("Unknown product: " + productId);
            }
            return;
        }

        showPurchaseSheet(activity, productId, tierName, price, features);
    }

    private void showPurchaseSheet(Activity activity, String productId,
                                   String tierName, String price, String[] features) {

        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        View sheetView = LayoutInflater.from(activity)
                .inflate(R.layout.bottom_sheet_purchase, null);

        TextView tvTierName = sheetView.findViewById(R.id.tvTierName);
        TextView tvPrice = sheetView.findViewById(R.id.tvPrice);
        LinearLayout containerFeatures = sheetView.findViewById(R.id.containerFeatures);

        tvTierName.setText(tierName);
        tvPrice.setText(price);

        // Build feature list programmatically — each line gets a check mark prefix
        for (String feature : features) {
            TextView tv = new TextView(activity);
            tv.setText("\u2713  " + feature);
            tv.setTextSize(15f);
            tv.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
            tv.setPadding(0, 8, 0, 8);
            containerFeatures.addView(tv);
        }

        // Confirm purchase — write tier to SharedPreferences and dismiss
        sheetView.findViewById(R.id.btnPurchase).setOnClickListener(v -> {
            sheet.dismiss();
            grantTier(productId);
        });

        // Cancel — dismiss and fire callback
        sheetView.findViewById(R.id.tvCancel).setOnClickListener(v -> {
            sheet.dismiss();
            if (resultListener != null) resultListener.onPurchaseCancelled();
        });

        // Swiping the sheet down also counts as cancel
        sheet.setOnCancelListener(d -> {
            if (resultListener != null) resultListener.onPurchaseCancelled();
        });

        sheet.setContentView(sheetView);
        sheet.show();
    }

    // Maps product ID to tier string and persists it — same logic as BillingManager.grantTier()
    private void grantTier(String productId) {
        String tier;
        if (TierConfig.PRODUCT_STARTER.equals(productId)) {
            tier = TierConfig.TIER_STARTER;
        } else if (TierConfig.PRODUCT_ADVANCED.equals(productId)) {
            tier = TierConfig.TIER_ADVANCED;
        } else {
            if (resultListener != null) resultListener.onPurchaseError("Unknown product.");
            return;
        }

        session.saveTier(tier);
        if (resultListener != null) resultListener.onPurchaseSuccess(tier);
    }
}
