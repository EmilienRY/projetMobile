package com.example.superpixelapp.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.example.superpixelapp.DataBase.DataBase;
import com.example.superpixelapp.DataBase.SuperPixelImage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageSavingUtil { // fonctions utilitaires pour les images
    public static void saveProcessedImage(Context context, String originalImageUri, Bitmap processedImage,
                                          String name, String algorithmName, String parameters) {

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    String processedImagePath = saveBitmapToInternalStorage(context, processedImage, name);

                    SuperPixelImage image = new SuperPixelImage();
                    image.name = name;
                    image.originalImagePath = originalImageUri.toString();
                    image.processedImagePath = processedImagePath;
                    image.algorithmName = algorithmName;
                    image.parameters = parameters;
                    image.dateCreated = System.currentTimeMillis();

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

    public static String getRealPathFromUri(Context context, Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }


    public static void saveToGallery(Context context, Bitmap bitmap, String name) {
        String filename = name + "_" + System.currentTimeMillis() + ".jpg";
        OutputStream fos;

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SuperPixelApp");

                ContentResolver resolver = context.getContentResolver();
                Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    if (fos != null) fos.close();
                    Toast.makeText(context, "Image enregistrée dans la galerie", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Erreur lors de l'enregistrement", Toast.LENGTH_SHORT).show();
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/SuperPixelApp");
                if (!dir.exists()) dir.mkdirs();
                File image = new File(dir, filename);
                fos = new FileOutputStream(image);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();


                MediaStore.Images.Media.insertImage(context.getContentResolver(), image.getAbsolutePath(), image.getName(), null);
                Toast.makeText(context, "Image enregistrée dans la galerie", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Erreur d'enregistrement", Toast.LENGTH_SHORT).show();
        }
    }


    public static String copyToAppInternal(Context context, Uri sourceUri, String fileName) throws IOException {
        File destDir = context.getExternalFilesDir("SuperPixelImages");
        if (!destDir.exists()) destDir.mkdirs();
        File destFile = new File(destDir, fileName);

        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
        return destFile.getAbsolutePath();
    }


    private static String saveBitmapToInternalStorage(Context context, Bitmap bitmap, String name) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "PROCESSED_" + timeStamp + "_" + name + ".jpg";

        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = new File(storageDir, fileName);

        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
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