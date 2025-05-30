package com.example.superpixelapp.MainFragment;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;
import android.widget.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import android.os.Build;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.superpixelapp.R;
import com.example.superpixelapp.worker.CompressionWorker;

public class CompressionFragment extends Fragment {
    private ImageView imageViewCarte, imageViewComp;
    private Button boutonChoisir;
    private Button boutonValider;
    private ActivityResultLauncher<Intent> launcherGalerie;

    private Bitmap bitmapSelectionne;
    private ProgressBar progressBar;

    private ActivityResultLauncher<String> permissionLauncher;

    static {
        System.loadLibrary("superpixel");
    }


    public static CompressionFragment newInstance(String imagePath) {
        CompressionFragment fragment = new CompressionFragment();
        Bundle args = new Bundle();
        args.putString("image_path", imagePath);
        fragment.setArguments(args);
        return fragment;
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View vue = inflater.inflate(R.layout.fragment_compression, container, false);

        boutonChoisir = vue.findViewById(R.id.boutonChoisir);
        boutonValider = vue.findViewById(R.id.boutonValider);
        imageViewCarte = vue.findViewById(R.id.imageViewCarte);
        imageViewComp = vue.findViewById(R.id.imageViewComp);
        progressBar = vue.findViewById(R.id.progressBar);

        boutonValider.setEnabled(false);

        // SI on reçoit une image via arguments
        boolean imageArgLoaded = false;
        if (getArguments() != null && getArguments().containsKey("image_path")) {
            String imagePath = getArguments().getString("image_path");
            if (imagePath != null) {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                if (bitmap != null) {
                    imageViewComp.setImageBitmap(bitmap);
                    bitmapSelectionne = bitmap;
                    boutonValider.setEnabled(true);
                    boutonChoisir.setEnabled(false); // On désactive choisir si image pré-sélectionnée
                    imageArgLoaded = true;
                }
            }
        }

        // Préparer le launcher pour ouvrir la galerie
        launcherGalerie = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imageUri);
                            bitmapSelectionne = bitmap;
                            if (bitmap.getWidth() > 2000 || bitmap.getHeight() > 2000) {
                                int newWidth = bitmap.getWidth() / 3;
                                int newHeight = bitmap.getHeight() / 3;
                                Bitmap bitmapReduit = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                                imageViewComp.setImageBitmap(bitmapReduit);
                                //bitmapSelectionne = bitmapReduit;
                                boutonValider.setEnabled(true);
                            } else if ((bitmap.getWidth() > 1000 || bitmap.getHeight() > 1000) && bitmap.getWidth() < 2000 && bitmap.getHeight() < 2000) {
                                int newWidth = bitmap.getWidth() / 2;
                                int newHeight = bitmap.getHeight() / 2;
                                Bitmap bitmapReduit = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                                imageViewComp.setImageBitmap(bitmapReduit);
                                //bitmapSelectionne = bitmapReduit;
                                boutonValider.setEnabled(true);
                            } else {
                                imageViewComp.setImageBitmap(bitmap);
                                boutonValider.setEnabled(true);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        boutonChoisir.setOnClickListener(view -> ouvrirGalerie());

        boutonValider.setOnClickListener(view -> {
            if (bitmapSelectionne != null) {
                lancerTraitementAvecWorker(bitmapSelectionne);
            }
        });

        // Préparer le launcher de permission notification
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        Toast.makeText(getContext(), "Impossible d'envoyer des notifications sans permission", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // Demander la permission si besoin (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        return vue;
    }


    private void ouvrirGalerie() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        launcherGalerie.launch(intent);
    }
    private void lancerTraitementAvecWorker(Bitmap bitmap) {
        // 1. Sauvegarde temporaire du bitmap pour le Worker
        File file = new File(requireContext().getCacheDir(), "to_compress.png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Erreur lors de la sauvegarde temporaire", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri fileUri = Uri.fromFile(file);

        Data inputData = new Data.Builder()
                .putString("image_uri", fileUri.toString())
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(CompressionWorker.class)
                .setInputData(inputData)
                .build();

        // ===> Montre la roue et désactive les boutons tout de suite
        progressBar.setVisibility(View.VISIBLE);
        boutonValider.setEnabled(false);
        boutonChoisir.setEnabled(false);

        WorkManager.getInstance(requireContext()).enqueue(workRequest);

        // Observe l'état du Worker pour afficher l'image une fois terminée
        WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(workRequest.getId())
                .observe(getViewLifecycleOwner(), new Observer<WorkInfo>() {
                    @Override
                    public void onChanged(WorkInfo workInfo) {
                        if (workInfo != null) {
                            if (workInfo.getState() == WorkInfo.State.RUNNING) {
                                // (optionnel, déjà fait au lancement)
                                progressBar.setVisibility(View.VISIBLE);
                                boutonValider.setEnabled(false);
                                boutonChoisir.setEnabled(false);
                            }
                            if (workInfo.getState().isFinished()) {
                                // ===> Cache la roue et réactive les boutons
                                progressBar.setVisibility(View.GONE);
                                boutonValider.setEnabled(true);
                                boutonChoisir.setEnabled(true);

                                if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                                    String outputPath = workInfo.getOutputData().getString("output_image_path");
                                    if (outputPath != null) {
                                        Bitmap imgComp = BitmapFactory.decodeFile(outputPath);
                                        imageViewCarte.setImageBitmap(imgComp);
                                        // Remet l'original dans imageViewComp
                                        imageViewComp.setImageBitmap(bitmap);
                                        Toast.makeText(getContext(), "Compression terminée !", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(getContext(), "Erreur: chemin du fichier résultat absent", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Toast.makeText(getContext(), "Erreur de compression", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                });
    }




}