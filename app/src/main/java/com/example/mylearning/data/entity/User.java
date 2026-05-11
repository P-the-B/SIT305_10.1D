package com.example.mylearning.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "users")
public class User {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // Cloud-ready identifier
    public String uuid = UUID.randomUUID().toString();

    public String username;
    public String email;
    public String passwordHash;   // SHA-256 hash, never plain text
    public String phone;
    public String securityQuestion;
    public String securityAnswerHash;

    // Epoch millis — set once on signup
    public long createdAt = System.currentTimeMillis();

    // Profile avatar — stored as compressed JPEG bytes, max ~256x256.
    // Nullable so existing users without a photo don't break.
    public byte[] profileImage;
}