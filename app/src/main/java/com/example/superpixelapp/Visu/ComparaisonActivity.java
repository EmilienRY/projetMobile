package com.example.superpixelapp.Visu;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.superpixelapp.R;
import com.google.android.material.slider.Slider;

import java.io.File;

public class ComparaisonActivity extends AppCompatActivity {

    private Slider slider;
    private ImageView imageOriginale, imageTraitee;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comparaison);

        imageOriginale = findViewById(R.id.imageOriginale);
        imageTraitee = findViewById(R.id.imageTraitee);
        slider = findViewById(R.id.slider);

        String pathOriginal = getIntent().getStringExtra("original_path");
        String pathProcessed = getIntent().getStringExtra("processed_path");

        Log.d("ComparaisonActivity", "Original: " + pathOriginal);
        Log.d("ComparaisonActivity", "Processed: " + pathProcessed);

        // Image originale
        if (pathOriginal != null && new File(pathOriginal).exists()) {
            Bitmap originalBitmap = BitmapFactory.decodeFile(pathOriginal);
            if (originalBitmap != null) {
                imageOriginale.setImageBitmap(originalBitmap);
            } else {
                imageOriginale.setImageResource(R.drawable.ic_error);
            }
        } else {
            imageOriginale.setImageResource(R.drawable.ic_error);
        }

        // Image traitée
        if (pathProcessed != null && new File(pathProcessed).exists()) {
            Bitmap processedBitmap = BitmapFactory.decodeFile(pathProcessed);
            if (processedBitmap != null) {
                imageTraitee.setImageBitmap(processedBitmap);
            } else {
                imageTraitee.setImageResource(R.drawable.ic_error);
            }
        } else {
            imageTraitee.setImageResource(R.drawable.ic_error);
        }

        // Slider : change la largeur du masque
        slider.addOnChangeListener((slider, value, fromUser) -> {
            imageTraitee.setClipBounds(new Rect(
                    0, 0,
                    (int) (imageTraitee.getWidth() * value / 100),
                    imageTraitee.getHeight()
            ));
        });
    }

    public void onBackPressed(View view) {
        finish();
    }

}