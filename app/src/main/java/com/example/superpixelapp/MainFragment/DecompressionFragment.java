package com.example.superpixelapp.MainFragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.superpixelapp.R;
import com.example.superpixelapp.utils.ImageSavingUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class DecompressionFragment extends Fragment {

    private ImageView imageView;
    private Button btnImportJson, btnValider, btmImportImg, btnDownload;
    private Bitmap bitmapImgDecomp, bitmapSelectionne;
    private Uri photoUri;
    private ActivityResultLauncher<Intent> jsonFileLauncher;
    private ActivityResultLauncher<Intent> launcherGalerie;
    private boolean json = false;
    private boolean img = false;
    List<int[]> palette;

    static {
        System.loadLibrary("superpixel");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View vue = inflater.inflate(R.layout.fragment_decompression, container, false);

        // Initialisation UI
        imageView = vue.findViewById(R.id.imageView);
        btnImportJson = vue.findViewById(R.id.btnImportJson);
        btnValider = vue.findViewById(R.id.boutonValider);
        btmImportImg = vue.findViewById(R.id.btnImportMap);
        btnDownload = vue.findViewById(R.id.boutonDownload);

        btnValider.setEnabled(false);

        jsonFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            palette = lireFichierJsonDepuisUri(uri);
                        }
                    }
                }
        );

        launcherGalerie = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imageUri);
                            bitmapSelectionne = bitmap;

                            imageView.setImageBitmap(bitmap);
                            imageView.setBackgroundColor(Color.TRANSPARENT);
                            img=true;
                            if (img && json){
                                btnValider.setEnabled(true);
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        btnImportJson.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/json");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            jsonFileLauncher.launch(intent);
        });

        btmImportImg.setOnClickListener(view -> ouvrirGalerie());

        btnValider.setOnClickListener(view -> {
            if (bitmapSelectionne != null) {
                bitmapImgDecomp = appliquerPaletteSurImage(bitmapSelectionne,palette);
                imageView.setImageBitmap(bitmapImgDecomp);
            }
        });

        btnDownload.setOnClickListener(view -> saveBitmapToFile(bitmapImgDecomp,requireContext()));

        return vue;
    }

    private List<int[]> lireFichierJsonDepuisUri(Uri uri) {
        List<int[]> palette = new ArrayList<>();
        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder builder = new StringBuilder();
            String ligne;
            while ((ligne = reader.readLine()) != null) {
                builder.append(ligne);
            }

            String contenuJson = builder.toString();

            JSONArray jsonArray = new JSONArray(contenuJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject colorObject = jsonArray.getJSONObject(i);
                int id = colorObject.getInt("id");

                JSONArray rgbArray = colorObject.getJSONArray("rgb");
                int[] rgb = new int[] {
                        rgbArray.getInt(0),
                        rgbArray.getInt(1),
                        rgbArray.getInt(2)
                };
                palette.add(rgb);
            }
            json=true;
            Toast.makeText(getContext(), "JSON Chargé", Toast.LENGTH_SHORT).show();
            if (img && json){
                btnValider.setEnabled(true);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Erreur de lecture du fichier", Toast.LENGTH_SHORT).show();
        }

        return palette;
    }

    private Bitmap appliquerPaletteSurImage(Bitmap imageIndexee, List<int[]> palette) {
        int width = imageIndexee.getWidth();
        int height = imageIndexee.getHeight();
        Bitmap imageCouleur = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = imageIndexee.getPixel(x, y);

                // Extraire l'index depuis le niveau de gris
                int index = Color.red(pixel); // Comme R=G=B dans du grayscale

                // Protéger contre un index hors limites
                if (index >= 0 && index < palette.size()) {
                    int[] rgb = palette.get(index);
                    int couleur = Color.rgb(rgb[0], rgb[1], rgb[2]);
                    imageCouleur.setPixel(x, y, couleur);
                } else {
                    // Couleur par défaut (noir)
                    imageCouleur.setPixel(x, y, Color.BLACK);
                }
            }
        }

        return imageCouleur;
    }

    private void ouvrirGalerie() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        launcherGalerie.launch(intent);
    }

    public void saveBitmapToFile(Bitmap bitmap, Context context) {
        String filename = "image_traitee_" + System.currentTimeMillis() + ".png";
        File directory = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "SuperPixelApp");

        if (!directory.exists()) {
            directory.mkdirs();
        }

        File file = new File(directory, filename);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();

            // Rendre visible dans la galerie
            MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);

            Toast.makeText(context, "Image sauvegardée dans la galerie", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Erreur de sauvegarde", Toast.LENGTH_SHORT).show();
        }
    }
}
