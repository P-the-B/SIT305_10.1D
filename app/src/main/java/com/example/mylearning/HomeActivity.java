package com.example.mylearning;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.mylearning.databinding.ActivityHomeBinding;
import com.example.mylearning.ui.HomeFragment;
import com.example.mylearning.ui.ProgressFragment;
import com.example.mylearning.util.SessionManager;
import com.example.mylearning.util.TierGate;
import com.example.mylearning.util.ToolbarUtil;
import com.google.android.material.tabs.TabLayout;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private SessionManager session;
    private TierGate gate;
    private TextView tierBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        session = new SessionManager(this);
        gate = new TierGate(this);

        setSupportActionBar(binding.toolbar);
        ToolbarUtil.applyStatusBarInset(binding.toolbar); // added to fix UI header layout

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayShowHomeEnabled(false);
        }

        // Centered app title — sized to match the taller toolbar
        TextView titleView = new TextView(this);
        titleView.setText(getString(R.string.app_name));
        titleView.setTextSize(22f);
        titleView.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary));
        titleView.setTypeface(null, Typeface.BOLD);
        Toolbar.LayoutParams lp = new Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT,
                Toolbar.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        binding.toolbar.addView(titleView, lp);

        // Tier badge — left-aligned, tapping opens UpgradeActivity
        addTierBadge();

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_home));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_progress));

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadFragment(tab.getPosition() == 0 ? new HomeFragment() : new ProgressFragment());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        gate = new TierGate(this);
        if (tierBadge != null) {
            tierBadge.setText("  " + gate.getTierLabel() + "  ");
        }
    }

    // Tier badge chip — larger text and padding for the taller toolbar
    private void addTierBadge() {
        tierBadge = new TextView(this);
        tierBadge.setText("  " + gate.getTierLabel() + "  ");
        tierBadge.setTextSize(13f);
        tierBadge.setTextColor(ContextCompat.getColor(this, R.color.primary));
        tierBadge.setTypeface(null, Typeface.BOLD);
        tierBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_light));
        tierBadge.setPadding(20, 10, 20, 10);

        Toolbar.LayoutParams badgeLp = new Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT,
                Toolbar.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        badgeLp.setMarginStart(4);
        binding.toolbar.addView(tierBadge, badgeLp);

        tierBadge.setOnClickListener(v ->
                startActivity(new Intent(this, UpgradeActivity.class)));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            session.clearSession();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}