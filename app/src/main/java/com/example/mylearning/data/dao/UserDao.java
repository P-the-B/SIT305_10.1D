package com.example.mylearning.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mylearning.data.entity.User;

@Dao
public interface UserDao {

    @Insert
    long insert(User user);

    @Query("SELECT * FROM users WHERE email = :email AND passwordHash = :hash LIMIT 1")
    User findByEmailAndPassword(String email, String hash);

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User findByEmail(String email);

    @Query("UPDATE users SET passwordHash = :newHash WHERE id = :userId")
    void updatePassword(int userId, String newHash);

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    User findById(int id);

    // Writes the cropped avatar bytes — called after image picker + compression
    @Query("UPDATE users SET profileImage = :imageBytes WHERE id = :userId")
    void updateProfileImage(int userId, byte[] imageBytes);
}