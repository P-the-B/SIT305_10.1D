package com.example.mylearning.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mylearning.data.TopicSummary;
import com.example.mylearning.data.entity.QuizAttempt;

import java.util.List;

@Dao
public interface QuizAttemptDao {

    @Insert
    long insert(QuizAttempt attempt);

    @Query("SELECT * FROM quiz_attempts WHERE userId = :userId AND topicId = :topicId ORDER BY timestamp DESC")
    List<QuizAttempt> getAttemptsForTopic(int userId, int topicId);

    @Query("SELECT SUM(score) * 100 / SUM(totalQuestions) FROM quiz_attempts WHERE userId = :userId AND topicId = :topicId")
    int getAccuracyForTopic(int userId, int topicId);

    @Query("SELECT COUNT(*) FROM quiz_attempts WHERE userId = :userId AND topicId = :topicId")
    int getAttemptCount(int userId, int topicId);

    @Query("UPDATE quiz_attempts SET score = :score, totalQuestions = :total WHERE id = :attemptId")
    void updateScoreAndTotal(int attemptId, int score, int total);

    @Query("SELECT DISTINCT topicId FROM quiz_attempts WHERE userId = :userId")
    List<Integer> getAttemptedTopicIds(int userId);

    @Query("SELECT * FROM quiz_attempts WHERE userId = :userId ORDER BY timestamp DESC")
    List<QuizAttempt> getAllAttemptsForUser(int userId);

    @Query("SELECT COALESCE(SUM(totalQuestions), 0) FROM quiz_attempts WHERE userId = :userId")
    int getTotalQuestionsForUser(int userId);

    @Query("SELECT COALESCE(SUM(score), 0) FROM quiz_attempts WHERE userId = :userId")
    int getTotalCorrectForUser(int userId);

    @Query("SELECT COUNT(*) FROM quiz_attempts WHERE userId = :userId")
    int getTotalAttemptCount(int userId);

    @Query("SELECT MAX(timestamp) FROM quiz_attempts WHERE userId = :userId")
    Long getLastQuizTimestamp(int userId);

    // Single query replaces the N×4 per-topic loop in ProfileActivity.loadStats().
    // Subquery for starredCount is safe — SQLite evaluates it once per row.
    @Query("SELECT t.id AS topicId, t.name AS topicName, " +
            "SUM(qa.score) * 100 / SUM(qa.totalQuestions) AS accuracy, " +
            "COUNT(qa.id) AS attemptCount, " +
            "0 AS starredCount " +
            "FROM topics t " +
            "INNER JOIN quiz_attempts qa ON t.id = qa.topicId AND qa.userId = :userId " +
            "GROUP BY t.id " +
            "ORDER BY t.name ASC")
    List<TopicSummary> getTopicSummariesForUser(int userId);
}