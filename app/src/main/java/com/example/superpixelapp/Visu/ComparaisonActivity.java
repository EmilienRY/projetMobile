package com.example.superpixelapp.Visu;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.superpixelapp.R;
import com.google.android.material.slider.Slider;

import java.io.File;

public class ComparaisonActivity extends AppCompatActivity {

    private Slider slider;
    private ImageView imageOriginale, imageTraitee;
    private TextView textPsnr;
    ProgressBar progressPsnr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comparaison);

        imageOriginale = findViewById(R.id.imageOriginale);
        imageTraitee = findViewById(R.id.imageTraitee);
        slider = findViewById(R.id.slider);
        textPsnr = findViewById(R.id.textPsnr);
        progressPsnr = findViewById(R.id.progressPsnr);
        String pathOriginal = getIntent().getStringExtra("original_path");
        String pathProcessed = getIntent().getStringExtra("processed_path");


        Bitmap originalBitmap;
        Bitmap processedBitmap;


        // Image originale
        if (pathOriginal != null && new File(pathOriginal).exists()) {
            originalBitmap = BitmapFactory.decodeFile(pathOriginal);
            if (originalBitmap != null) {
                imageOriginale.setImageBitmap(originalBitmap);
            } else {
                imageOriginale.setImageResource(R.drawable.ic_error);
            }
        } else {
            originalBitmap = null;
            imageOriginale.setImageResource(R.drawable.ic_error);
        }

        // Image traitée
        if (pathProcessed != null && new File(pathProcessed).exists()) {
            processedBitmap = BitmapFactory.decodeFile(pathProcessed);
            if (processedBitmap != null) {
                imageTraitee.setImageBitmap(processedBitmap);
            } else {
                imageTraitee.setImageResource(R.drawable.ic_error);
            }
        } else {
            processedBitmap = null;
            imageTraitee.setImageResource(R.drawable.ic_error);
        }

        progressPsnr.setVisibility(View.VISIBLE);
        textPsnr.setText("Calcul...");
        Log.d("PSNR_CHECK", "originalBitmap=" + (originalBitmap != null ? originalBitmap.getWidth() + "x" + originalBitmap.getHeight() : "null")
                + ", processedBitmap=" + (processedBitmap != null ? processedBitmap.getWidth() + "x" + processedBitmap.getHeight() : "null"));

        if (originalBitmap != null && processedBitmap != null
                && originalBitmap.getWidth() == processedBitmap.getWidth()
                && originalBitmap.getHeight() == processedBitmap.getHeight()) {

            Log.d("PSNR_CHECK", "Bitmaps loaded and sizes match");
            new Thread(() -> {
                Log.d("PSNR_THREAD", "Thread started");
                try {
                    double psnr = computePSNR(originalBitmap, processedBitmap);
                    Log.d("PSNR_THREAD", "PSNR computed: " + psnr);
                    runOnUiThread(() -> {
                        textPsnr.setText("PSNR : " + (psnr < 0 ? "erreur" : String.format("%.2f dB", psnr)));
                        progressPsnr.setVisibility(View.GONE);
                    });
                } catch (Exception e) {
                    Log.e("PSNR_THREAD", "Erreur lors du calcul du PSNR", e);
                    runOnUiThread(() -> {
                        textPsnr.setText("PSNR : erreur");
                        progressPsnr.setVisibility(View.GONE);
                    });
                }
            }).start();

        } else {
            Log.d("PSNR_CHECK", "Bitmaps invalid or sizes don't match");
            textPsnr.setText("PSNR : -");
            progressPsnr.setVisibility(View.GONE);
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

    // Calcul du PSNR
    public static double computePSNR(Bitmap original, Bitmap processed) {
        if (original.getWidth() != processed.getWidth() ||
                original.getHeight() != processed.getHeight()) {
            return -1; // Erreur : tailles différentes
        }

        double mse = 0.0;
        int width = original.getWidth();
        int height = original.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int origPixel = original.getPixel(x, y);
                int procPixel = processed.getPixel(x, y);

                int rOrig = (origPixel >> 16) & 0xFF;
                int gOrig = (origPixel >> 8) & 0xFF;
                int bOrig = origPixel & 0xFF;

                int rProc = (procPixel >> 16) & 0xFF;
                int gProc = (procPixel >> 8) & 0xFF;
                int bProc = procPixel & 0xFF;

                mse += Math.pow(rOrig - rProc, 2);
                mse += Math.pow(gOrig - gProc, 2);
                mse += Math.pow(bOrig - bProc, 2);
            }
        }
        mse /= (width * height * 3.0);

        if (mse == 0) return 99.99; // images identiques (PSNR infini)
        double psnr = 10.0 * Math.log10((255 * 255) / mse);
        return psnr;
    }

    public void onBackPressed(View view) {
        finish();
    }
}
