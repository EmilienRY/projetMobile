package com.example.superpixelapp.MainFragment;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.example.superpixelapp.DataBase.DataBase;
import com.example.superpixelapp.DataBase.SuperPixelImage;
import com.example.superpixelapp.R;
import com.example.superpixelapp.utils.ImageSavingUtil;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class CreationFragment extends Fragment {
    private ImageView imageView;
    private Button boutonPhoto, boutonChoisir, boutonValider, boutonCompression;
    private EditText editTextNom, param1, param2;
    private Spinner choixAlgo;
    private Bitmap bitmapSelectionne, bitmapTraite;
    private Uri photoUri;
    private File photoFichier;

    private SuperPixelImage imageData;
    private TextView textViewInfosImage;
    private ActivityResultLauncher<Intent> launcherGalerie, launcherCamera;
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    static {
        System.loadLibrary("superpixel");
    }

    public native void traiterImageWatershed(int[] pixels, int width, int height, int minSize);

    public native void traiterImageSLIC(int[] pixels, int width, int height, int nSuperpixels, float compactness);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View vue = inflater.inflate(R.layout.fragment_creation, container, false);

        // Initialisation UI
        imageView = vue.findViewById(R.id.imageView);
        boutonPhoto = vue.findViewById(R.id.boutonPhoto);
        boutonChoisir = vue.findViewById(R.id.boutonChoisir);
        boutonValider = vue.findViewById(R.id.boutonValider);
        boutonCompression = vue.findViewById(R.id.boutonCompression);
        editTextNom = vue.findViewById(R.id.editTextNom);
        param1 = vue.findViewById(R.id.param1);
        param2 = vue.findViewById(R.id.param2);
        choixAlgo = vue.findViewById(R.id.choixAlgo);

        textViewInfosImage = vue.findViewById(R.id.textViewInfosImage);

        boutonValider.setEnabled(false);
        boutonCompression.setEnabled(false);
        param2.setVisibility(View.GONE);

        // Spinner d'algo : affiche les paramètres adaptés à l'algo
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.algo_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        choixAlgo.setAdapter(adapter);

        choixAlgo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String algo = (String) parent.getItemAtPosition(position);
                if (algo.equalsIgnoreCase("SLIC")) {
                    param1.setHint(getString(R.string.nbClusters));
                    param2.setHint(getString(R.string.spatialRes));
                    param2.setVisibility(View.VISIBLE);
                } else { // Watershed par défaut
                    param1.setHint(getString(R.string.minSize));
                    param2.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Choix caméra
        launcherCamera = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK) {
                        try {
                            Bitmap bitmap = BitmapFactory.decodeStream(requireContext().getContentResolver().openInputStream(photoUri));
                            bitmapSelectionne = adapterTaille(bitmap);
                            imageView.setImageBitmap(bitmapSelectionne);
                            imageView.setBackgroundColor(Color.TRANSPARENT);
                            afficherInfosImage(photoUri, bitmapSelectionne);
                            boutonValider.setEnabled(true);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

        // Choix galerie
        launcherGalerie = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                        photoUri = result.getData().getData();
                        try {
                            String fileName = "import_" + System.currentTimeMillis() + ".jpg";
                            String appPath = ImageSavingUtil.copyToAppInternal(requireContext(), photoUri, fileName);
                            Bitmap bitmap = BitmapFactory.decodeFile(appPath);

                            bitmapSelectionne = adapterTaille(bitmap);
                            imageView.setImageBitmap(bitmapSelectionne);
                            imageView.setBackgroundColor(Color.TRANSPARENT);
                            afficherInfosImage(Uri.fromFile(new File(appPath)), bitmapSelectionne);
                            boutonValider.setEnabled(true);

                            photoUri = Uri.fromFile(new File(appPath));

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

        boutonPhoto.setOnClickListener(view -> verifierEtPrendrePhoto());
        boutonChoisir.setOnClickListener(view -> ouvrirGalerie());

        boutonValider.setOnClickListener(view -> {
            if (bitmapSelectionne != null) {
                bitmapTraite = lancerTraitement(bitmapSelectionne);
            }
        });

        boutonCompression.setOnClickListener(v -> {
            if (imageData != null && imageData.processedImagePath != null) {
                String imagePath = imageData.processedImagePath;

                Fragment compressionFragment = new CompressionFragment();
                Bundle args = new Bundle();
                args.putString("image_path", imagePath);
                compressionFragment.setArguments(args);

                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, compressionFragment)
                        .addToBackStack(null)
                        .commit();
            } else {
                Toast.makeText(getContext(), "Erreur : image non disponible", Toast.LENGTH_SHORT).show();
            }
        });

        return vue;
    }

    private Bitmap adapterTaille(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w > 2000 || h > 2000) {
            return Bitmap.createScaledBitmap(bitmap, w / 3, h / 3, true);
        } else if (w > 1000 || h > 1000) {
            return Bitmap.createScaledBitmap(bitmap, w / 2, h / 2, true);
        }
        return bitmap;
    }

    private Bitmap lancerTraitement(Bitmap bitmap) {
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888)
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        String algo = (String) choixAlgo.getSelectedItem();
        String nom = editTextNom.getText().toString().trim();
        String param1Text = param1.getText().toString().trim();
        String param2Text = param2.getText().toString().trim();

        if (algo.equalsIgnoreCase("Watershed")) {
            int minSize = Integer.parseInt(param1Text);
            traiterImageWatershed(pixels, width, height, minSize);
            bitmapTraite = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
            imageView.setImageBitmap(bitmapTraite);
            imageView.setBackgroundColor(Color.TRANSPARENT);
            sauvegarderImage(nom, "Watershed", "minSize=" + minSize);
        }
        else if (algo.equalsIgnoreCase("SLIC")) {
            int nClusters = Integer.parseInt(param1Text);
            float compactness = Float.parseFloat(param2Text);
            traiterImageSLIC(pixels, width, height, nClusters, compactness);
            bitmapTraite = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
            imageView.setImageBitmap(bitmapTraite);
            imageView.setBackgroundColor(Color.TRANSPARENT);
            sauvegarderImage(nom, "SLIC", "nClusters=" + nClusters + ", m=" + compactness);
        } else {
            Toast.makeText(getContext(), getString(R.string.UnknownAlgo), Toast.LENGTH_SHORT).show();
        }

        bitmapTraite = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        imageView.setImageBitmap(bitmapTraite);
        boutonCompression.setEnabled(true);
        return bitmapTraite;
    }

    private void sauvegarderImage(String nom, String algo, String parametres) {
        if (nom.isEmpty()) {
            nom = "Image_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        }

        String originalPath = null;
        if (photoUri != null && "file".equals(photoUri.getScheme())) {
            originalPath = new File(photoUri.getPath()).getAbsolutePath();
        } else if (photoFichier != null && photoFichier.exists()) {
            originalPath = photoFichier.getAbsolutePath();
        }

        ImageSavingUtil.saveProcessedImage(requireContext(), originalPath, bitmapTraite, nom, algo, parametres);

        boutonCompression.setEnabled(false);

        String finalNom = nom;
        Executors.newSingleThreadExecutor().execute(() -> {
            SuperPixelImage img = null;
            for (int i = 0; i < 10; i++) {
                img = DataBase.getInstance(requireContext())
                        .superPixelImageDao()
                        .getImageByNom(finalNom);
                if (img != null) break;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
            }

            SuperPixelImage finalImg = img;
            requireActivity().runOnUiThread(() -> {
                if (finalImg != null) {
                    imageData = finalImg;
                    boutonCompression.setEnabled(true);
                } else {
                    Toast.makeText(getContext(), "Erreur lors de l'enregistrement en base", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void verifierEtPrendrePhoto() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            prendrePhoto();
        }
    }

    private void prendrePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            try {
                photoFichier = creerFichierImage();
                photoUri = FileProvider.getUriForFile(requireContext(), "com.example.superpixelapp.fileprovider", photoFichier);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                launcherCamera.launch(intent);
            } catch (IOException e) {
                Toast.makeText(getContext(), getString(R.string.photoError), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private File creerFichierImage() throws IOException {
        String nomFichier = "photo_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File repertoire = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(nomFichier, ".jpg", repertoire);
    }

    private void ouvrirGalerie() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        launcherGalerie.launch(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                prendrePhoto();
            } else {
                Toast.makeText(getContext(), getString(R.string.camPermDenied), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void afficherInfosImage(Uri uri, Bitmap bitmap) {
        if (bitmap == null) {
            textViewInfosImage.setText("");
            return;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        long sizeKb = -1;
        String format = "?";
        if (uri != null) {
            try {
                String scheme = uri.getScheme();
                if ("content".equals(scheme)) {
                    try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                            if (sizeIndex != -1) sizeKb = cursor.getLong(sizeIndex) / 1024;
                            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (nameIndex != -1) {
                                String name = cursor.getString(nameIndex);
                                int dot = name.lastIndexOf(".");
                                if (dot >= 0) format = name.substring(dot + 1).toUpperCase();
                            }
                        }
                    }
                } else if ("file".equals(scheme)) {
                    File f = new File(uri.getPath());
                    sizeKb = f.length() / 1024;
                    String name = f.getName();
                    int dot = name.lastIndexOf(".");
                    if (dot >= 0) format = name.substring(dot + 1).toUpperCase();
                }
            } catch (Exception e) { /* ignore */ }
        }

        String infos = getString(
                R.string.image_info,
                format,
                width,
                height,
                (sizeKb >= 0 ? String.valueOf(sizeKb) : "?")
        );

        textViewInfosImage.setText(infos);
    }
}
