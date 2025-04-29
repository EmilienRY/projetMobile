package com.example.superpixelapp.MainFragment;

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
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.example.superpixelapp.R;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CreationFragment extends Fragment {
    private ImageView imageView;
    private Button boutonPhoto, boutonValider;

    private Bitmap bitmapSelectionne;
    private Uri photoUri;
    private File photoFichier;

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
        imageView = vue.findViewById(R.id.imageView);

        boutonValider.setEnabled(false);

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
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

        boutonPhoto.setOnClickListener(view -> prendrePhoto());

        boutonValider.setOnClickListener(view -> {
            if (bitmapSelectionne != null) {
                lancerTraitement(bitmapSelectionne);
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

    private void lancerTraitement(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        traiterImageNative(pixels, width, height);

        imageView.setImageBitmap(Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888));
    }
}
