package com.example.superpixelapp.MainFragment;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.io.IOException;

import com.example.superpixelapp.R;

public class CompressionFragment extends Fragment {
    private ImageView imageView;
    private Button boutonChoisir;
    private Button boutonValider;
    private ActivityResultLauncher<Intent> launcherGalerie;

    private Bitmap bitmapSelectionne;


    static {
       System.loadLibrary("superpixelapp");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View vue = inflater.inflate(R.layout.fragment_compression, container, false);

        boutonChoisir = vue.findViewById(R.id.boutonChoisir);
        boutonValider = vue.findViewById(R.id.boutonValider);
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
                            if(bitmap.getWidth()>2000 || bitmap.getHeight()>2000){
                                int newWidth = bitmap.getWidth() / 3;
                                int newHeight = bitmap.getHeight() / 3;
                                Bitmap bitmapReduit = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                                imageView.setImageBitmap(bitmapReduit);
                            }
                            else if(bitmap.getWidth()>1000 || bitmap.getHeight()>1000){
                                int newWidth = bitmap.getWidth() / 2;
                                int newHeight = bitmap.getHeight() / 2;

                                Bitmap bitmapReduit = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                                bitmapSelectionne=bitmapReduit;
                                imageView.setImageBitmap(bitmapReduit);
                            }
                            else{
                                bitmapSelectionne=bitmap;
                                imageView.setImageBitmap(bitmap);
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

        return vue;
    }

    private void ouvrirGalerie() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        launcherGalerie.launch(intent);
    }

    private void lancerTraitement(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        // Appel de la fonction C++ native
        traiterImageNative(pixels, width, height);

        // Tu pourrais ensuite, par exemple, mettre à jour l'affichage de l'image traitée
        // imageView.setImageBitmap(Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888));
    }
}