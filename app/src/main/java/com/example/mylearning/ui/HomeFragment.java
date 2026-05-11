package com.example.mylearning.ui;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mylearning.InterestsActivity;
import com.example.mylearning.QuizActivity;
import com.example.mylearning.LessonActivity;
import com.example.mylearning.R;
import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.Topic;
import com.example.mylearning.databinding.FragmentHomeBinding;
import com.example.mylearning.util.SessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private AppDatabase db;
    private SessionManager session;
    private List<Topic> userTopics = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        db = AppDatabase.getInstance(requireContext());
        session = new SessionManager(requireContext());

        binding.tvGreeting.setText("Hello, " + session.getUsername() + " 👋");

        loadTopics();

        binding.editSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                renderTopics(s.toString().trim().toLowerCase());
            }
        });

        binding.linkEditInterests.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), InterestsActivity.class);
            intent.putExtra("userId", session.getUserId());
            startActivity(intent);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload topics in case user just edited interests
        loadTopics();
    }

    private void loadTopics() {
        AsyncTask.execute(() -> {
            userTopics = db.topicDao().getTopicsForUser(session.getUserId());
            requireActivity().runOnUiThread(() -> renderTopics(""));
        });
    }

    private void renderTopics(String query) {
        binding.containerTopics.removeAllViews();

        for (Topic topic : userTopics) {
            if (!query.isEmpty() && !topic.name.toLowerCase().contains(query)) continue;

            View card = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_topic_card, binding.containerTopics, false);

            ((TextView) card.findViewById(R.id.tvTopicName)).setText(topic.name);
            ((TextView) card.findViewById(R.id.tvCategory)).setText(topic.category);

            card.setOnClickListener(v -> showTopicOptions(topic));
            binding.containerTopics.addView(card);
        }
    }

    // Bottom sheet with Start Quiz / Short Lesson options
    private void showTopicOptions(Topic topic) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_topic_options, null);

        ((TextView) sheetView.findViewById(R.id.tvTopicName)).setText(topic.name);
        ((TextView) sheetView.findViewById(R.id.tvTopicCategory)).setText(topic.category);

        sheetView.findViewById(R.id.btnStartQuiz).setOnClickListener(v -> {
            sheet.dismiss();
            Intent intent = new Intent(requireContext(), QuizActivity.class);
            intent.putExtra("topicId", topic.id);
            intent.putExtra("topicName", topic.name);
            startActivity(intent);
        });

        sheetView.findViewById(R.id.btnShortLesson).setOnClickListener(v -> {
            sheet.dismiss();
            Intent intent = new Intent(requireContext(), LessonActivity.class);
            intent.putExtra("topicId", topic.id);
            intent.putExtra("topicName", topic.name);
            startActivity(intent);
        });

        sheet.setContentView(sheetView);
        sheet.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}