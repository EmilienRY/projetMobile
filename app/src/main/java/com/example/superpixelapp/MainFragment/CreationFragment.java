package com.example.superpixelapp.MainFragment;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
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

import com.example.superpixelapp.R;
import com.example.superpixelapp.utils.ImageSavingUtil;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CreationFragment extends Fragment {
    private ImageView imageView;
    private Button boutonPhoto, boutonChoisir, boutonValider;
    private EditText editTextNom, choixAlgo, param1, param2;
    private Bitmap bitmapSelectionne, bitmapTraite;
    private Uri photoUri;
    private File photoFichier;

    private TextView textViewInfosImage; // AJOUT
    private ActivityResultLauncher<Intent> launcherGalerie, launcherCamera;
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    static {
        System.loadLibrary("superpixel");
    }

    public native void traiterImageWatershed(int[] pixels, int width, int height, int minSize);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View vue = inflater.inflate(R.layout.fragment_creation, container, false);

        // Initialisation UI
        imageView = vue.findViewById(R.id.imageView);
        boutonPhoto = vue.findViewById(R.id.boutonPhoto);
        boutonChoisir = vue.findViewById(R.id.boutonChoisir);
        boutonValider = vue.findViewById(R.id.boutonValider);
        editTextNom = vue.findViewById(R.id.editTextNom);
        choixAlgo = vue.findViewById(R.id.choixAlgo);
        param1 = vue.findViewById(R.id.param1);
        param2 = vue.findViewById(R.id.param2);

        textViewInfosImage = vue.findViewById(R.id.textViewInfosImage); // AJOUT

        boutonValider.setEnabled(false);
        param2.setVisibility(View.GONE);

        // Adaptation des champs selon algo
        choixAlgo.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String algo = choixAlgo.getText().toString().trim().toLowerCase();
                if (algo.equals("slic")) {
                    param1.setHint(getString(R.string.nbClusters));
                    param2.setHint(getString(R.string.spatialRes));
                    param2.setVisibility(View.VISIBLE);
                } else {
                    param1.setHint(getString(R.string.minSize));
                    param2.setVisibility(View.GONE);
                }
            }
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
                            afficherInfosImage(photoUri, bitmapSelectionne); // AJOUT
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
                            // Copie l'image dans le dossier interne de l'app
                            String fileName = "import_" + System.currentTimeMillis() + ".jpg";
                            String appPath = ImageSavingUtil.copyToAppInternal(requireContext(), photoUri, fileName);
                            Bitmap bitmap = BitmapFactory.decodeFile(appPath);

                            bitmapSelectionne = adapterTaille(bitmap);
                            imageView.setImageBitmap(bitmapSelectionne);
                            afficherInfosImage(Uri.fromFile(new File(appPath)), bitmapSelectionne); // Utilise Uri du fichier copié
                            boutonValider.setEnabled(true);

                            // Met à jour photoUri pour pointer vers le fichier local (pour sauvegarder en BDD)
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

        String algo = choixAlgo.getText().toString().trim().toLowerCase();
        String nom = editTextNom.getText().toString().trim();
        String param1Text = param1.getText().toString().trim();
        String param2Text = param2.getText().toString().trim();

        if (algo.equals("watershed")) {
            int minSize = Integer.parseInt(param1Text);
            traiterImageWatershed(pixels, width, height, minSize);
            bitmapTraite = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
            imageView.setImageBitmap(bitmapTraite);
            sauvegarderImage(nom, "Watershed", "minSize=" + minSize);
        }
        else if (algo.equals("slic")) {
            // En attente d’implémentation
            Toast.makeText(getContext(), "SLIC non encore implémenté", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), getString(R.string.UnknownAlgo), Toast.LENGTH_SHORT).show();
        }

        bitmapTraite = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        imageView.setImageBitmap(bitmapTraite);
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
        // Taille en pixels
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Taille du fichier en Ko
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
