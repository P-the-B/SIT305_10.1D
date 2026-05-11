package com.example.mylearning.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.mylearning.data.dao.QuizAttemptDao;
import com.example.mylearning.data.dao.QuizQuestionDao;
import com.example.mylearning.data.dao.TopicDao;
import com.example.mylearning.data.dao.UserDao;
import com.example.mylearning.data.entity.QuizAttempt;
import com.example.mylearning.data.entity.QuizQuestion;
import com.example.mylearning.data.entity.Topic;
import com.example.mylearning.data.entity.User;
import com.example.mylearning.data.entity.UserTopic;
import com.example.mylearning.util.SessionManager;
import com.example.mylearning.util.TopicSeeder;

import java.util.concurrent.Executors;

@Database(
        entities = {User.class, Topic.class, UserTopic.class, QuizAttempt.class, QuizQuestion.class},
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    // Migration 3→4: adds profileImage blob column to users table.
    // Non-destructive — preserves all existing data.
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE users ADD COLUMN profileImage BLOB");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    Context appContext = context.getApplicationContext();
                    instance = Room.databaseBuilder(
                                    appContext,
                                    AppDatabase.class,
                                    "mylearning.db"
                            )
                            .addMigrations(MIGRATION_3_4)
                            .fallbackToDestructiveMigration()
                            .addCallback(new SeedCallback(appContext))
                            .build();
                }
            }
        }
        return instance;
    }

    public abstract UserDao userDao();
    public abstract TopicDao topicDao();
    public abstract QuizAttemptDao quizAttemptDao();
    public abstract QuizQuestionDao quizQuestionDao();

    // Seeds topics and clears stale auth on fresh DB creation
    private static class SeedCallback extends Callback {
        private final Context context;

        SeedCallback(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            new SessionManager(context).clearSession();

            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase database = AppDatabase.getInstance(context);
                database.topicDao().insertAll(TopicSeeder.getTopics());
            });
        }
    }
}