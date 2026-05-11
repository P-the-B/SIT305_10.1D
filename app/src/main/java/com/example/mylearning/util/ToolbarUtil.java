package com.example.mylearning.util;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mylearning.R;

public class ToolbarUtil {

    /**
     * Call from every activity after setSupportActionBar().
     * Handles centered title, back arrow tint, and status bar inset.
     */
    public static void setup(AppCompatActivity activity, Toolbar toolbar, String title, boolean showBack) {
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(showBack);
        }

        // White back arrow
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setTint(
                    ContextCompat.getColor(activity, R.color.text_on_primary));
        }

        // Centered title
        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(20f);
        titleView.setTextColor(ContextCompat.getColor(activity, R.color.text_on_primary));
        titleView.setTypeface(null, Typeface.BOLD);

        Toolbar.LayoutParams lp = new Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT,
                Toolbar.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        toolbar.addView(titleView, lp);

        // Apply status bar padding so content isn't hidden behind system bar
        applyStatusBarInset(toolbar);
    }

    /**
     * Adds top padding equal to the status bar height (minus a small trim)
     * so toolbar content never sits behind the system bar. Safe to call
     * on any toolbar, including HomeActivity's custom one.
     */
    public static void applyStatusBarInset(Toolbar toolbar) {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            int statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            float density = v.getResources().getDisplayMetrics().density;

            // Trim 4dp off the raw inset to tighten the gap slightly
            int topPad = Math.max(0, statusBar - (int)(4 * density));
            v.setPadding(v.getPaddingLeft(), topPad, v.getPaddingRight(), v.getPaddingBottom());

            // If height is fixed, grow it to absorb the extra padding.
            // wrap_content toolbars expand naturally via the padding alone.
            ViewGroup.LayoutParams params = v.getLayoutParams();
            if (params.height > 0) {
                int baseHeight = Math.round(56 * density);
                params.height = baseHeight + topPad;
                v.setLayoutParams(params);
            }

            return insets;
        });
    }
}