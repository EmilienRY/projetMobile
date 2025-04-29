package com.example.superpixelapp.MainFragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.superpixelapp.DataBase.DataBase;
import com.example.superpixelapp.DataBase.SuperPixelImage;
import com.example.superpixelapp.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
public class ListFragment extends Fragment {
    private RecyclerView recyclerView;
    private SuperPixelAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_list, container, false);
        recyclerView = rootView.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initialiser avec une liste vide
        adapter = new SuperPixelAdapter(new ArrayList<>(), requireContext());
        recyclerView.setAdapter(adapter);

        loadImages();

        return rootView;
    }

    private void loadImages() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<SuperPixelImage> images = DataBase.getInstance(requireContext())
                    .superPixelImageDao()
                    .getAll();

            requireActivity().runOnUiThread(() -> {
                adapter.updateData(images);
            });
        });
    }
}