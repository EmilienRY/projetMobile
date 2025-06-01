package com.example.superpixelapp.MainFragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.os.Environment;
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
import androidx.annotation.DrawableRes;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.superpixelapp.R;
import com.example.superpixelapp.worker.CompressionWorker;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class CompressionFragment extends Fragment {
    private ImageView imageViewCarte, imageViewComp;
    private Button boutonChoisir, boutonValider, boutonTelecharger;
    private ActivityResultLauncher<Intent> launcherGalerie;
    private Bitmap bitmapSelectionne, imgComp;
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
        boutonTelecharger = vue.findViewById(R.id.boutonDownload);
        imageViewCarte = vue.findViewById(R.id.imageViewCarte);
        imageViewComp = vue.findViewById(R.id.imageViewComp);
        progressBar = vue.findViewById(R.id.progressBar);

        boutonValider.setEnabled(false);
        boutonTelecharger.setEnabled(false);

        // si on reçoit une image via arguments
        boolean imageArgLoaded = false;
        if (getArguments() != null && getArguments().containsKey("image_path")) {
            String imagePath = getArguments().getString("image_path");
            if (imagePath != null) {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                if (bitmap != null) {
                    imageViewComp.setImageBitmap(bitmap);
                    imageViewComp.setBackgroundColor(Color.TRANSPARENT);
                    bitmapSelectionne = bitmap;
                    boutonValider.setEnabled(true);
                    boutonChoisir.setEnabled(false); // On désactive choisir si image pré-sélectionnée
                    imageArgLoaded = true;
                }
            }
        }

        // pour galerie
        launcherGalerie = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imageUri);
                            bitmapSelectionne = bitmap;

                                imageViewComp.setImageBitmap(bitmap);
                                imageViewComp.setBackgroundColor(Color.TRANSPARENT);
                                boutonValider.setEnabled(true);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        boutonChoisir.setOnClickListener(view -> ouvrirGalerie());

        boutonValider.setOnClickListener(view -> {
            if (bitmapSelectionne != null) {
                lancerTraitementAvecWorker(bitmapSelectionne,imgComp);
                boutonTelecharger.setEnabled(true);
            }
        });

        boutonTelecharger.setOnClickListener(view -> saveBitmapToFile(imgComp,requireContext()));

        // permissions
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        Toast.makeText(getContext(), "Impossible d'envoyer des notifications sans permission", Toast.LENGTH_SHORT).show();
                    }
                }
        );


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
    private void lancerTraitementAvecWorker(Bitmap bitmap, Bitmap bitmapImgComp) {

        File file = new File(requireContext().getCacheDir(), "to_compress.png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Erreur lors de la sauvegarde temporaire", Toast.LENGTH_SHORT).show();
            return;
        }

        String jsonPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath() + "/palette" + getTimestampForFilename() + ".json";
        Uri fileUri = Uri.fromFile(file);

        Data inputData = new Data.Builder()
                .putString("image_uri", fileUri.toString())
                .putString("json_output_path", jsonPath)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(CompressionWorker.class)
                .setInputData(inputData)
                .build();


        progressBar.setVisibility(View.VISIBLE);
        boutonValider.setEnabled(false);
        boutonChoisir.setEnabled(false);

        WorkManager.getInstance(requireContext()).enqueue(workRequest);
        setCompressionEnCours(true);

        // lance le worker
        WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(workRequest.getId())
                .observe(getViewLifecycleOwner(), new Observer<WorkInfo>() {
                    @Override
                    public void onChanged(WorkInfo workInfo) {
                        if (workInfo != null) {
                            if (workInfo.getState() == WorkInfo.State.RUNNING) {
                                // affiche une roue pendant le calcul
                                progressBar.setVisibility(View.VISIBLE);
                                boutonValider.setEnabled(false);
                                boutonChoisir.setEnabled(false);
                            }
                            if (workInfo.getState().isFinished()) {

                                progressBar.setVisibility(View.GONE);
                                boutonValider.setEnabled(true);
                                boutonChoisir.setEnabled(true);
                                setCompressionEnCours(false);

                                if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                                    String outputPath = workInfo.getOutputData().getString("output_image_path");
                                    if (outputPath != null) {
                                        imgComp = BitmapFactory.decodeFile(outputPath);
                                        imageViewCarte.setImageBitmap(imgComp);
                                        imageViewCarte.setBackgroundColor(Color.TRANSPARENT);

                                        imageViewComp.setImageBitmap(bitmap);
                                        Toast.makeText(getContext(), "Compression terminée!", Toast.LENGTH_SHORT).show();
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


            MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);

            Toast.makeText(context, "Image sauvegardée dans la galerie", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Erreur de sauvegarde", Toast.LENGTH_SHORT).show();
        }
    }

    public String getTimestampForFilename() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        return dateFormat.format(new Date());
    }
    private boolean compressionEnCours = false;

    public boolean isCompressionEnCours() {
        return compressionEnCours;
    }

    private void setCompressionEnCours(boolean enCours) {
        this.compressionEnCours = enCours;
    }


}