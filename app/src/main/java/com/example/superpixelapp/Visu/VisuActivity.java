package com.example.superpixelapp.Visu;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.superpixelapp.DataBase.DataBase;
import com.example.superpixelapp.DataBase.SuperPixelImage;
import com.example.superpixelapp.MainActivity;
import com.example.superpixelapp.R;
import com.example.superpixelapp.utils.ImageSavingUtil;

import java.util.concurrent.Executors;

public class VisuActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView nameText, algoText, paramText;
    private Button btnCompare, btnCompress;
    private ImageButton btnDelete;
    private int imageId;
    private SuperPixelImage imageData;
    private ImageButton btnSaveToGallery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_visu);

        imageView = findViewById(R.id.imageView);
        nameText = findViewById(R.id.nameText);
        algoText = findViewById(R.id.algoText);
        paramText = findViewById(R.id.paramText);

        btnDelete = findViewById(R.id.btnDelete);
        btnCompare = findViewById(R.id.btnCompare);
        btnCompress = findViewById(R.id.btnCompress);
        btnSaveToGallery = findViewById(R.id.btnSaveToGallery);

        imageId = getIntent().getIntExtra("image_id", -1);

        if (imageId == -1) {
            finish(); // id invalide
            return;
        }

        loadImageData();

        btnDelete.setOnClickListener(v -> deleteImage());

        btnCompare.setOnClickListener(v -> {
            Intent intent = new Intent(this, ComparaisonActivity.class);
            intent.putExtra("original_path", imageData.originalImagePath);
            intent.putExtra("processed_path", imageData.processedImagePath);
            startActivity(intent);
        });

        btnCompress.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("goto_compression", true);
            intent.putExtra("image_path", imageData.processedImagePath); // ou originalImagePath selon ton choix
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // optionnel : évite d’empiler les MainActivity
            startActivity(intent);
            finish(); // optionnel, ferme VisuActivity
        });



        btnSaveToGallery.setOnClickListener(v -> {
            ImageSavingUtil.loadProcessedImage(this, imageData.processedImagePath,
                    new ImageSavingUtil.ImageLoadCallback() {
                        @Override
                        public void onImageLoaded(Bitmap bitmap) {
                            ImageSavingUtil.saveToGallery(VisuActivity.this, bitmap, imageData.name);
                        }

                        @Override
                        public void onError(String message) {
                            Toast.makeText(VisuActivity.this, getString(R.string.load_failed), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }




    private void loadImageData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            imageData = DataBase.getInstance(this)
                    .superPixelImageDao()
                    .getById(imageId);

            if (imageData == null) {
                runOnUiThread(this::finish);
                return;
            }

            runOnUiThread(() -> {
                nameText.setText(imageData.name);
                algoText.setText(imageData.algorithmName);
                paramText.setText(imageData.parameters);

                ImageSavingUtil.loadProcessedImage(this, imageData.processedImagePath,
                        new ImageSavingUtil.ImageLoadCallback() {
                            @Override
                            public void onImageLoaded(Bitmap bitmap) {
                                imageView.setImageBitmap(bitmap);
                            }

                            @Override
                            public void onError(String message) {
                                imageView.setImageResource(R.drawable.ic_error);
                            }
                        });
            });
        });
    }

    private void deleteImage() {
        Executors.newSingleThreadExecutor().execute(() -> {
            DataBase.getInstance(this)
                    .superPixelImageDao()
                    .delete(imageData);

            runOnUiThread(() -> {
                Toast.makeText(this, getString(R.string.delete_image_success), Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK); // 🔥
                finish();
            });
        });
    }

}
