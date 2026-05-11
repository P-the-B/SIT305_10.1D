package com.example.mylearning.data;

import androidx.room.ColumnInfo;

// Result POJO for the consolidated per-topic query in QuizAttemptDao.
// Room maps column aliases to fields by name — no @Entity annotation needed.
public class TopicSummary {

    @ColumnInfo(name = "topicId")
    public int topicId;

    @ColumnInfo(name = "topicName")
    public String topicName;

    // 0–100 integer percentage
    @ColumnInfo(name = "accuracy")
    public int accuracy;

    @ColumnInfo(name = "attemptCount")
    public int attemptCount;

    @ColumnInfo(name = "starredCount")
    public int starredCount;
}
