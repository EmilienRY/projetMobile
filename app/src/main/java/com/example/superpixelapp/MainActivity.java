package com.example.superpixelapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.superpixelapp.MainFragment.DecompressionFragment;
import com.example.superpixelapp.MainFragment.ListFragment;
import com.example.superpixelapp.MainFragment.CompressionFragment;
import com.example.superpixelapp.MainFragment.CreationFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNav;
    private boolean isInitialIntentHandled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_view);

        bottomNav = findViewById(R.id.bottom_navigation);

        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("goto_compression", false)) {
            String imagePath = intent.getStringExtra("image_path");
            if (imagePath != null) {
                // Charge CompressionFragment AVEC image
                CompressionFragment fragment = CompressionFragment.newInstance(imagePath);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit();
                bottomNav.setSelectedItemId(R.id.nav_comp);
                isInitialIntentHandled = true;
            }
        } else if (savedInstanceState == null) {
            // Fragment par défaut (liste)
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ListFragment())
                    .commit();
        }



        bottomNav.setOnItemSelectedListener(item -> {
            // Si on vient d'un intent spécial → ignore la première sélection
            if (isInitialIntentHandled) {
                isInitialIntentHandled = false;
                return true;
            }

            Fragment selectedFragment;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_list) {
                selectedFragment = new ListFragment();
            } else if (itemId == R.id.nav_crea) {
                selectedFragment = new CreationFragment();
            } else if (itemId == R.id.nav_comp) {
                selectedFragment = new CompressionFragment();
            } else if (itemId == R.id.nav_decomp) {
                selectedFragment = new DecompressionFragment();
            } else {
                selectedFragment = null;
            }

            if (selectedFragment != null) {
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

                if (currentFragment instanceof CompressionFragment) {
                    CompressionFragment cf = (CompressionFragment) currentFragment;
                    if (cf.isCompressionEnCours()) {
                        Toast.makeText(this, "Veuillez attendre la fin de la compression", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }

                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }

            return false;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK) {
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (current instanceof ListFragment) {
                ((ListFragment) current).reload(); // 🔁 fonction à ajouter dans ListFragment
            }
        }
    }
}
