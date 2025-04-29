package com.example.superpixelapp.DataBase;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.room.TypeConverter;

import java.io.ByteArrayOutputStream;

public class BitmapConverter {
    @TypeConverter
    public static byte[] fromBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        return outputStream.toByteArray();
    }

    @TypeConverter
    public static Bitmap toBitmap(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}