package com.example.superpixelapp.MainFragment;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.superpixelapp.R;

import java.io.IOException;

public class CreationFragment extends Fragment {
    private ImageView imageView;
    private Button boutonChoisir, boutonPhoto, boutonValider;
    private ActivityResultLauncher<Intent> launcherGalerie;

    private Bitmap bitmapSelectionne;

    private ActivityResultLauncher<Intent> launcherCamera;

    static {
        System.loadLibrary("superpixel");
    }

    public native void traiterImageNative(int[] pixels, int width, int height);


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View vue = inflater.inflate(R.layout.fragment_creation, container, false);

        boutonChoisir = vue.findViewById(R.id.boutonChoisir);
        boutonValider = vue.findViewById(R.id.boutonValider);
        boutonPhoto = vue.findViewById(R.id.boutonPhoto);
        imageView = vue.findViewById(R.id.imageView);

        boutonValider.setEnabled(false);

        // Préparer le launcher pour ouvrir la galerie
        launcherGalerie = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imageUri);
                            bitmapSelectionne=bitmap;
                            if(bitmap.getWidth()>2000 || bitmap.getHeight()>2000){
                                int newWidth = bitmap.getWidth() / 3;
                                int newHeight = bitmap.getHeight() / 3;
                                Bitmap bitmapReduit = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                                //bitmapSelectionne=bitmapReduit;
                                imageView.setImageBitmap(bitmapReduit);
                                boutonValider.setEnabled(true);
                            }
                            else if((bitmap.getWidth()>1000 || bitmap.getHeight()>1000) && bitmap.getWidth()<2000 && bitmap.getHeight()<2000){
                                int newWidth = bitmap.getWidth() / 2;
                                int newHeight = bitmap.getHeight() / 2;

                                Bitmap bitmapReduit = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                                //bitmapSelectionne=bitmapReduit;
                                imageView.setImageBitmap(bitmapReduit);
                                boutonValider.setEnabled(true);

                            }
                            else{
                                //bitmapSelectionne=bitmap;
                                imageView.setImageBitmap(bitmap);
                                boutonValider.setEnabled(true);
                            }
                            // Ici tu peux appeler ton traitement d'image C++ si besoin

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        boutonChoisir.setOnClickListener(view -> ouvrirGalerie());

        boutonValider.setOnClickListener(view -> {
            if (bitmapSelectionne != null) {
                lancerTraitement(bitmapSelectionne);
            }
        });

        launcherCamera = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        Bitmap photo = (Bitmap) extras.get("data"); // image basse résolution

                        if (photo != null) {
                            bitmapSelectionne = photo;
                            imageView.setImageBitmap(photo);
                            boutonValider.setEnabled(true);
                        }
                    }
                }
        );

        boutonPhoto.setOnClickListener(view -> verifierEtPrendrePhoto());

        return vue;
    }

    private void ouvrirGalerie() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        launcherGalerie.launch(intent);
    }

    private static final int REQUEST_CAMERA_PERMISSION = 100;

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

    private void prendrePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        launcherCamera.launch(intent);
    }

    private void lancerTraitement(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        // Appel de la fonction C++ native
        traiterImageNative(pixels, width, height);

        // Tu pourrais ensuite, par exemple, mettre à jour l'affichage de l'image traitée
        imageView.setImageBitmap(Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888));
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

}
