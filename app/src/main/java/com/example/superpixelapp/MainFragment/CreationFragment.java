package com.example.superpixelapp.MainFragment;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.example.superpixelapp.R;
import com.example.superpixelapp.utils.ImageSavingUtil;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;



public class CreationFragment extends Fragment {
    private ImageView imageView;
    private Button boutonPhoto, boutonValider, boutonSauvegarder;

    private Bitmap bitmapSelectionne;
    private Bitmap bitmapTraite;
    private Uri photoUri;
    private File photoFichier;
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private ActivityResultLauncher<Intent> launcherCamera;

    static {
        System.loadLibrary("superpixel");
    }

    public native void traiterImageNative(int[] pixels, int width, int height);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View vue = inflater.inflate(R.layout.fragment_creation, container, false);

        boutonPhoto = vue.findViewById(R.id.boutonPhoto);
        boutonValider = vue.findViewById(R.id.boutonValider);
        boutonSauvegarder = vue.findViewById(R.id.boutonSauvegarder); // Assurez-vous d'ajouter ce bouton dans votre layout
        imageView = vue.findViewById(R.id.imageView);

        boutonValider.setEnabled(false);
        boutonSauvegarder.setEnabled(false);

        launcherCamera = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK) {
                        try {
                            Bitmap bitmap = BitmapFactory.decodeStream(requireContext().getContentResolver().openInputStream(photoUri));
                            bitmapSelectionne = bitmap;

                            if (bitmap.getWidth() > 2000 || bitmap.getHeight() > 2000) {
                                bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / 3, bitmap.getHeight() / 3, true);
                            } else if (bitmap.getWidth() > 1000 || bitmap.getHeight() > 1000) {
                                bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / 2, bitmap.getHeight() / 2, true);
                            }

                            imageView.setImageBitmap(bitmap);
                            boutonValider.setEnabled(true);
                            boutonSauvegarder.setEnabled(false);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

        boutonPhoto.setOnClickListener(view -> verifierEtPrendrePhoto());

        boutonValider.setOnClickListener(view -> {
            if (bitmapSelectionne != null) {
                bitmapTraite = lancerTraitement(bitmapSelectionne);
                boutonSauvegarder.setEnabled(true);
            }
        });

        boutonSauvegarder.setOnClickListener(view -> {
            if (bitmapTraite != null && photoUri != null) {
                afficherDialogueSauvegarde();
            }
        });

        return vue;
    }

    private void prendrePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            try {
                photoFichier = creerFichierImage();
                photoUri = FileProvider.getUriForFile(requireContext(),
                        "com.example.superpixelapp.fileprovider",
                        photoFichier);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                launcherCamera.launch(intent);
            } catch (IOException e) {
                Toast.makeText(getContext(), "Erreur lors de la création du fichier photo", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private File creerFichierImage() throws IOException {
        String nomFichier = "photo_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File repertoire = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(nomFichier, ".jpg", repertoire);
    }

    private Bitmap lancerTraitement(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        traiterImageNative(pixels, width, height);

        Bitmap bitmapTraite = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        imageView.setImageBitmap(bitmapTraite);
        return bitmapTraite;
    }

    private void verifierEtPrendrePhoto() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Demande la permission à l'utilisateur
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{android.Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            // Permission déjà accordée
            prendrePhoto();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                prendrePhoto();
            } else {
                Toast.makeText(getContext(), "Permission caméra refusée", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void afficherDialogueSauvegarde() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Sauvegarder l'image");

        View viewDialogue = getLayoutInflater().inflate(R.layout.dialogue_sauvegarde_image, null);
        EditText editTextNom = viewDialogue.findViewById(R.id.editTextNom);
        EditText editTextAlgorithme = viewDialogue.findViewById(R.id.editTextAlgorithme);
        EditText editTextParametres = viewDialogue.findViewById(R.id.editTextParametres);

        builder.setView(viewDialogue);

        builder.setPositiveButton("Sauvegarder", (dialog, which) -> {
            String nom = editTextNom.getText().toString();
            String algorithme = editTextAlgorithme.getText().toString();
            String parametres = editTextParametres.getText().toString();

            if (nom.isEmpty()) {
                nom = "Image_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            }

            ImageSavingUtil.saveProcessedImage(
                    requireContext(),
                    photoUri,
                    bitmapTraite,
                    nom,
                    algorithme,
                    parametres
            );
        });

        builder.setNegativeButton("Annuler", null);
        builder.show();
    }
}