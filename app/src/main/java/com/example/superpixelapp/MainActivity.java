package com.example.superpixelapp;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.superpixelapp.MainFragment.ListFragment;
import com.example.superpixelapp.MainFragment.CompressionFragment;
import com.example.superpixelapp.MainFragment.CreationFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_view);

        bottomNav = findViewById(R.id.bottom_navigation);

        // Charger le fragment par défaut
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ListFragment())
                    .commit();
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment;

            int itemId = item.getItemId();
            if (itemId == R.id.nav_list) {
                selectedFragment = new ListFragment();
            } else if (itemId == R.id.nav_crea) {
                selectedFragment = new CreationFragment();
            } else if (itemId == R.id.nav_comp) {
                selectedFragment = new CompressionFragment();
            } else {
                selectedFragment = null;
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });

    }
}
