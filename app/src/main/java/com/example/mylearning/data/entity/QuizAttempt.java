package com.example.mylearning.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(
        tableName = "quiz_attempts",
        foreignKeys = @ForeignKey(entity = Topic.class, parentColumns = "id", childColumns = "topicId", onDelete = ForeignKey.CASCADE)
)
public class QuizAttempt {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // Cloud-ready — each attempt is globally unique for sync
    public String uuid = UUID.randomUUID().toString();

    public int userId;
    public int topicId;
    public int score;           // questions correct
    public int totalQuestions;  // questions attempted (handles quit mid-quiz)
    public long timestamp;      // System.currentTimeMillis()
}
