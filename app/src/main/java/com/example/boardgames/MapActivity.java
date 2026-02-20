package com.example.boardgames;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;

public class MapActivity extends AppCompatActivity {

    private MapImageView mapImageView;
    private LinearLayout emptyState;
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.map);
        }

        mapImageView = findViewById(R.id.map_image_view);
        emptyState = findViewById(R.id.empty_state);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::onImagePicked
        );

        findViewById(R.id.fab_import_map).setOnClickListener(v ->
                imagePickerLauncher.launch("image/*")
        );
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;

        try {
            // Take persistable read permission so the URI survives restarts
            getContentResolver().takePersistableUriPermission(uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Not all providers support persistable permissions; that's fine
        }

        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                android.graphics.drawable.Drawable drawable =
                        android.graphics.drawable.Drawable.createFromStream(inputStream, uri.toString());
                inputStream.close();

                if (drawable != null) {
                    mapImageView.setImageDrawable(drawable);
                    mapImageView.setVisibility(View.VISIBLE);
                    emptyState.setVisibility(View.GONE);
                } else {
                    Toast.makeText(this, R.string.map_load_failed, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.map_load_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
