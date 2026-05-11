package com.example.mylearning.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.mylearning.data.entity.Topic;
import com.example.mylearning.data.entity.UserTopic;

import java.util.List;

@Dao
public interface TopicDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<Topic> topics);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertUserTopic(UserTopic userTopic);

    @Query("DELETE FROM user_topics WHERE userId = :userId")
    void clearUserTopics(int userId);

    @Query("SELECT t.* FROM topics t INNER JOIN user_topics ut ON t.id = ut.topicId WHERE ut.userId = :userId")
    List<Topic> getTopicsForUser(int userId);

    @Query("SELECT * FROM topics")
    List<Topic> getAllTopics();

    @Query("SELECT * FROM topics WHERE id = :topicId")
    Topic getTopicById(int topicId);

    // Count of topics currently selected by user — used to enforce 10 topic limit
    @Query("SELECT COUNT(*) FROM user_topics WHERE userId = :userId")
    int getUserTopicCount(int userId);

    // Check if a specific topic is in the user's current interests
    @Query("SELECT COUNT(*) FROM user_topics WHERE userId = :userId AND topicId = :topicId")
    int isTopicSelected(int userId, int topicId);
}