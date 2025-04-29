package com.example.superpixelapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.example.superpixelapp.DataBase.BitmapConverter;
import com.example.superpixelapp.DataBase.DataBase;
import com.example.superpixelapp.DataBase.SuperPixelImage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageSavingUtil {
    public static void saveProcessedImage(Context context, Uri originalImageUri, Bitmap processedImage,
                                          String name, String algorithmName, String parameters) {

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    // 1. Sauvegarder l'image traitée dans le stockage
                    String processedImagePath = saveBitmapToInternalStorage(context, processedImage, name);

                    // 2. Créer l'entité à sauvegarder
                    SuperPixelImage image = new SuperPixelImage();
                    image.name = name;
                    image.originalImagePath = originalImageUri.toString();
                    image.processedImagePath = processedImagePath;
                    image.algorithmName = algorithmName;
                    image.parameters = parameters;
                    image.dateCreated = System.currentTimeMillis();

                    // 3. Insérer dans la base de données
                    DataBase.getInstance(context).superPixelImageDao().insert(image);
                    return true;
                } catch (Exception e) {
                    Log.e("ImageSaving", "Erreur de sauvegarde", e);
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    Toast.makeText(context, "Image sauvegardée", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Échec de la sauvegarde", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private static String saveBitmapToInternalStorage(Context context, Bitmap bitmap, String name) throws IOException {
        // Créer un nom de fichier unique
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "PROCESSED_" + timeStamp + "_" + name + ".jpg";

        // Créer le fichier dans le répertoire Pictures de l'application
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = new File(storageDir, fileName);

        // Compresser et sauvegarder le bitmap
        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out); // 85% de qualité
        }

        return imageFile.getAbsolutePath();
    }

    public static void loadProcessedImage(Context context, String imagePath, ImageLoadCallback callback) {
        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... voids) {
                try {
                    return BitmapFactory.decodeFile(imagePath);
                } catch (Exception e) {
                    Log.e("ImageLoading", "Erreur de chargement", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null) {
                    callback.onImageLoaded(bitmap);
                } else {
                    callback.onError("Échec du chargement");
                }
            }
        }.execute();
    }

    public interface ImageLoadCallback {
        void onImageLoaded(Bitmap bitmap);
        void onError(String message);
    }
}