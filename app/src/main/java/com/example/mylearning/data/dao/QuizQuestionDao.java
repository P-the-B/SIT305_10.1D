package com.example.mylearning.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mylearning.data.entity.QuizQuestion;

import java.util.List;

@Dao
public interface QuizQuestionDao {

    @Insert
    void insertAll(List<QuizQuestion> questions);

    // Results screen — all questions for a completed attempt
    @Query("SELECT * FROM quiz_questions WHERE attemptId = :attemptId")
    List<QuizQuestion> getQuestionsForAttempt(int attemptId);

    // Lesson screen — starred questions for a topic across all attempts
    @Query("SELECT * FROM quiz_questions WHERE topicId = :topicId AND isStarred = 1")
    List<QuizQuestion> getStarredForTopic(int topicId);

    // Star / unstar toggle from results or lesson screen
    @Query("UPDATE quiz_questions SET isStarred = :starred WHERE id = :questionId")
    void setStarred(int questionId, boolean starred);

    // How many questions the student flagged in a given topic
    @Query("SELECT COUNT(*) FROM quiz_questions WHERE topicId = :topicId AND isStarred = 1")
    int getStarredCountForTopic(int topicId);

    // Recent wrong answers across all topics — ordered newest first so Gemini sees current gaps
    @Query("SELECT qq.* FROM quiz_questions qq " +
            "INNER JOIN quiz_attempts qa ON qq.attemptId = qa.id " +
            "WHERE qa.userId = :userId AND qq.isCorrect = 0 " +
            "ORDER BY qa.timestamp DESC LIMIT :limit")
    List<QuizQuestion> getRecentWrongQuestions(int userId, int limit);
}