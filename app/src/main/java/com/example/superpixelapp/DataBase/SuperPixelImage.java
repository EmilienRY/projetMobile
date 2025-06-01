package com.example.superpixelapp.DataBase;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
@Entity
public class SuperPixelImage {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public String originalImagePath;
    public String processedImagePath;
    public String algorithmName;

    public String parameters;
    public long dateCreated;
}