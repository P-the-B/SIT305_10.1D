package com.example.mylearning.data.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "topics", indices = {@Index(value = "name", unique = true)})
public class Topic {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // Stable ID for cloud sync — seeded topics get the same UUID across devices
    // because TopicSeeder sets them explicitly
    public String uuid = UUID.randomUUID().toString();

    public String name;
    public String category;
}
