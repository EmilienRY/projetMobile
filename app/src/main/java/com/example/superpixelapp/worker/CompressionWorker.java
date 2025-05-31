package com.example.superpixelapp.worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.Data;

import com.example.superpixelapp.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CompressionWorker extends Worker {

    static {
        System.loadLibrary("superpixel");
    }
    public native int[] compression(int[] pixels, int width, int height, String path);

    public CompressionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String imageUriString = getInputData().getString("image_uri");
        if (imageUriString == null) return Result.failure();

        try {
            Context context = getApplicationContext();
            Uri imageUri = Uri.parse(imageUriString);
            InputStream stream = context.getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap == null) return Result.failure();

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            String jsonOutputPath = getInputData().getString("json_output_path");

            // Traitement C++ !

            int[] clusterMap = compression(pixels, width, height, jsonOutputPath);

            // Sauvegarde clusterMap dans un fichier temporaire PNG
            Bitmap outputBitmap = Bitmap.createBitmap(clusterMap, width, height, Bitmap.Config.ARGB_8888);

            File outputDir = context.getCacheDir();
            File outputFile = File.createTempFile("img_comp_", ".png", outputDir);
            FileOutputStream out = new FileOutputStream(outputFile);
            outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();

            Data outputData = new Data.Builder()
                    .putString("output_image_path", outputFile.getAbsolutePath())
                    .build();
            sendFinishNotification(outputFile.getAbsolutePath());

            return Result.success(outputData);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
    }


    private void sendFinishNotification(String filePath) {
        String CHANNEL_ID = "compression_result";
        Context context = getApplicationContext();

        // Crée le canal (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Compression";
            String description = "Résultats compression superpixel";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null)
                notificationManager.createNotificationChannel(channel);
        }

        // Intent pour ouvrir l'app quand on clique sur la notif
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE // obligatoire sur Android 12+
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle("Compression terminée")
                .setContentText("Votre image compressée est prête.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission non accordée, ne pas envoyer la notification
                return;
            }
        }

        notificationManager.notify(1234, builder.build());
    }

}
