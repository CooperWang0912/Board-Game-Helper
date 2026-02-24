package com.example.boardgames;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "map_prefs";
    private static final String KEY_MAP_URI = "map_uri";
    private static final String KEY_MAP_POINTS = "map_points";

    private MapImageView mapImageView;
    private LinearLayout emptyState;
    private LinearLayout zoomControls;
    private FloatingActionButton fabAddPoint;
    private FloatingActionButton fabImportMap;
    private ActivityResultLauncher<String[]> imagePickerLauncher;

    private Uri currentMapUri;

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
        zoomControls = findViewById(R.id.zoom_controls);
        fabAddPoint = findViewById(R.id.fab_add_point);
        fabImportMap = findViewById(R.id.fab_import_map);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onImagePicked
        );

        fabImportMap.setOnClickListener(v ->
                imagePickerLauncher.launch(new String[]{"image/*"})
        );

        findViewById(R.id.fab_zoom_in).setOnClickListener(v -> mapImageView.zoomIn());
        findViewById(R.id.fab_zoom_out).setOnClickListener(v -> mapImageView.zoomOut());

        fabAddPoint.setOnClickListener(v -> togglePlacementMode());

        mapImageView.setOnPointPlacedListener((imgX, imgY) -> showLabelDialog(imgX, imgY));

        mapImageView.setOnPointTappedListener(point -> showDeleteDialog(point));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleExit();
            }
        });

        loadSavedMap();
    }

    private void togglePlacementMode() {
        boolean entering = !mapImageView.isPlacementMode();
        mapImageView.setPlacementMode(entering);

        if (entering) {
            fabAddPoint.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFFF5722));
            fabAddPoint.setImageTintList(
                    android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            Toast.makeText(this, R.string.map_placement_mode, Toast.LENGTH_SHORT).show();
        } else {
            fabAddPoint.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            fabAddPoint.setImageTintList(
                    android.content.res.ColorStateList.valueOf(0xFF000000));
        }
    }

    private void exitPlacementMode() {
        mapImageView.setPlacementMode(false);
        fabAddPoint.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        fabAddPoint.setImageTintList(
                android.content.res.ColorStateList.valueOf(0xFF000000));
    }

    private void showLabelDialog(float imgX, float imgY) {
        EditText input = new EditText(this);
        input.setHint(R.string.map_label_hint);

        new AlertDialog.Builder(this)
                .setTitle(R.string.map_enter_label)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String label = input.getText().toString().trim();
                    if (label.isEmpty()) label = "Point";
                    mapImageView.addPoint(imgX, imgY, label);
                    exitPlacementMode();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> exitPlacementMode())
                .setOnCancelListener(dialog -> exitPlacementMode())
                .show();
    }

    private void showDeleteDialog(MapImageView.MapPoint point) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.map_delete_point_title)
                .setMessage(getString(R.string.map_delete_point_message, point.label))
                .setPositiveButton(R.string.cc_delete, (dialog, which) ->
                        mapImageView.removePoint(point))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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

        if (loadImageFromUri(uri)) {
            currentMapUri = uri;
            saveMapUri(uri);
        }
    }

    private boolean loadImageFromUri(Uri uri) {
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
                    zoomControls.setVisibility(View.VISIBLE);
                    fabAddPoint.setVisibility(View.VISIBLE);
                    return true;
                } else {
                    Toast.makeText(this, R.string.map_load_failed, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.map_load_failed, Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    // Save / Load

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void saveMapUri(Uri uri) {
        getPrefs().edit().putString(KEY_MAP_URI, uri.toString()).apply();
    }

    private void savePoints() {
        try {
            JSONArray arr = new JSONArray();
            for (MapImageView.MapPoint p : mapImageView.getPoints()) {
                JSONObject obj = new JSONObject();
                obj.put("x", p.imgX);
                obj.put("y", p.imgY);
                obj.put("label", p.label);
                arr.put(obj);
            }
            getPrefs().edit().putString(KEY_MAP_POINTS, arr.toString()).apply();
            mapImageView.markSaved();
            Toast.makeText(this, R.string.map_points_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private List<MapImageView.MapPoint> loadPoints() {
        List<MapImageView.MapPoint> result = new ArrayList<>();
        String json = getPrefs().getString(KEY_MAP_POINTS, null);
        if (json == null) return result;

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                result.add(new MapImageView.MapPoint(
                        (float) obj.getDouble("x"),
                        (float) obj.getDouble("y"),
                        obj.getString("label")
                ));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void loadSavedMap() {
        String uriStr = getPrefs().getString(KEY_MAP_URI, null);
        if (uriStr == null) return;

        Uri uri = Uri.parse(uriStr);
        if (loadImageFromUri(uri)) {
            currentMapUri = uri;
            List<MapImageView.MapPoint> saved = loadPoints();
            if (!saved.isEmpty()) {
                mapImageView.setPoints(saved);
            }
        }
    }

    // Exit handling

    private void handleExit() {
        if (mapImageView.hasUnsavedChanges()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.map_save_prompt_title)
                    .setMessage(R.string.map_save_prompt_message)
                    .setPositiveButton(R.string.map_save, (dialog, which) -> {
                        savePoints();
                        finish();
                    })
                    .setNegativeButton(R.string.map_discard, (dialog, which) -> finish())
                    .setNeutralButton(android.R.string.cancel, null)
                    .show();
        } else {
            finish();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        handleExit();
        return true;
    }
}
