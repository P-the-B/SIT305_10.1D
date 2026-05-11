package com.example.mylearning.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;

// Junction table linking a user to their selected topics.
// No UUID here — for cloud sync this maps to a topicIds array on the user document,
// not a standalone Firestore collection.
@Entity(
        tableName = "user_topics",
        primaryKeys = {"userId", "topicId"},
        foreignKeys = {
                @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "userId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Topic.class, parentColumns = "id", childColumns = "topicId", onDelete = ForeignKey.CASCADE)
        }
)
public class UserTopic {
    public int userId;
    public int topicId;
}
