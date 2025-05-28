package com.example.superpixelapp.DataBase;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SuperPixelImageDao {
    @Insert
    long insert(SuperPixelImage image);

    @Query("SELECT * FROM SuperPixelImage ORDER BY dateCreated DESC")
    List<SuperPixelImage> getAll();

    @Query("SELECT * FROM SuperPixelImage WHERE id = :id")
    SuperPixelImage getById(int id);

    @Delete
    void delete(SuperPixelImage image);

    @Query("DELETE FROM SuperPixelImage WHERE id = :id")
    void deleteById(int id);


}