package com.example.superpixelapp.MainFragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.superpixelapp.DataBase.BitmapConverter;
import com.example.superpixelapp.DataBase.SuperPixelImage;
import com.example.superpixelapp.R;
import com.example.superpixelapp.Visu.VisuActivity;
import com.example.superpixelapp.utils.ImageSavingUtil;

import java.util.List;

public class SuperPixelAdapter extends RecyclerView.Adapter<SuperPixelAdapter.ViewHolder> {
    private List<SuperPixelImage> images;
    private Context context;

    public SuperPixelAdapter(List<SuperPixelImage> images, Context context) {
        this.images = images;
        this.context = context;
    }

    public void updateData(List<SuperPixelImage> newImages) {
        this.images = newImages;
        notifyDataSetChanged();
    }


    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageViewProcessed);
            textView = itemView.findViewById(R.id.textViewName);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_super_pixel_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        SuperPixelImage item = images.get(position);
        holder.textView.setText(item.name);

        ImageSavingUtil.loadProcessedImage(context, item.processedImagePath,
                new ImageSavingUtil.ImageLoadCallback() {
                    @Override
                    public void onImageLoaded(Bitmap bitmap) {
                        holder.imageView.setImageBitmap(bitmap);
                    }

                    @Override
                    public void onError(String message) {
                        holder.imageView.setImageResource(R.drawable.ic_error);
                    }
                });

        // Clic pour ouvrir l'activité de visualisation
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, VisuActivity.class);
            intent.putExtra("image_id", item.id);
            ((Activity) context).startActivityForResult(intent, 1);
        });
    }



    @Override
    public int getItemCount() {
        return images.size();
    }
}