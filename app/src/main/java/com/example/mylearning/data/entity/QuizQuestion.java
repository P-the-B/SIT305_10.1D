package com.example.mylearning.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(
        tableName = "quiz_questions",
        foreignKeys = @ForeignKey(entity = QuizAttempt.class, parentColumns = "id", childColumns = "attemptId", onDelete = ForeignKey.CASCADE)
)
public class QuizQuestion {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // Cloud-ready — individual questions can be referenced in Firestore subcollections
    public String uuid = UUID.randomUUID().toString();

    public int attemptId;       // links back to the quiz attempt
    public int topicId;         // kept for starred question lookups by topic

    public String questionText;
    public String optionA;
    public String optionB;
    public String optionC;
    public String optionD;
    public String correctAnswer; // "A", "B", "C", or "D"
    public String userAnswer;    // what the user selected (null if skipped)

    public boolean isStarred;    // flagged for lesson screen review
    public boolean isCorrect;
}
