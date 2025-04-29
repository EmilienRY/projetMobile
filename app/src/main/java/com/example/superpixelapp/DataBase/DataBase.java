package com.example.superpixelapp.DataBase;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {SuperPixelImage.class}, version = 2,exportSchema = false)
@TypeConverters({BitmapConverter.class})
public abstract class DataBase extends RoomDatabase {
    private static DataBase INSTANCE;

    public abstract SuperPixelImageDao superPixelImageDao();

    public static synchronized DataBase getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            DataBase.class, "super_pixel_db")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return INSTANCE;
    }
}