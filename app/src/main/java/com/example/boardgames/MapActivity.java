package com.example.boardgames;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
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
    private static final String KEY_MAP_DRAWINGS = "map_drawings";

    private static final int[] PALETTE_COLORS = {
            0xFFFF0000, // Red
            0xFF2196F3, // Blue
            0xFF4CAF50, // Green
            0xFFFFEB3B, // Yellow
            0xFFFF9800, // Orange
            0xFF9C27B0, // Purple
            0xFFFFFFFF, // White
            0xFF000000  // Black
    };

    private static final String[] PALETTE_NAMES = {
            "Red", "Blue", "Green", "Yellow", "Orange", "Purple", "White", "Black"
    };

    private MapImageView mapImageView;
    private LinearLayout emptyState;
    private LinearLayout zoomControls;
    private FloatingActionButton fabAddPoint;
    private FloatingActionButton fabImportMap;
    private FloatingActionButton fabDraw;
    private FloatingActionButton fabErase;
    private FloatingActionButton fabColorPicker;
    private ActivityResultLauncher<String[]> imagePickerLauncher;

    private Uri currentMapUri;
    private int currentDrawColor = 0xFFFF0000; // Default red

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
        fabDraw = findViewById(R.id.fab_draw);
        fabErase = findViewById(R.id.fab_erase);
        fabColorPicker = findViewById(R.id.fab_color_picker);

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

        fabDraw.setOnClickListener(v -> toggleDrawMode());
        fabErase.setOnClickListener(v -> toggleEraseMode());
        fabErase.setOnLongClickListener(v -> {
            showClearDrawingsDialog();
            return true;
        });
        fabColorPicker.setOnClickListener(v -> showColorPickerDialog());

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
        // Exit draw/erase if active
        if (mapImageView.getInteractionMode() != MapImageView.InteractionMode.NAVIGATE) {
            resetDrawEraseMode();
        }

        boolean entering = !mapImageView.isPlacementMode();
        mapImageView.setPlacementMode(entering);

        if (entering) {
            setFabActive(fabAddPoint, true);
            Toast.makeText(this, R.string.map_placement_mode, Toast.LENGTH_SHORT).show();
        } else {
            setFabActive(fabAddPoint, false);
        }
    }

    private void exitPlacementMode() {
        mapImageView.setPlacementMode(false);
        setFabActive(fabAddPoint, false);
    }

    private void toggleDrawMode() {
        // Exit placement mode if active
        if (mapImageView.isPlacementMode()) {
            exitPlacementMode();
        }

        if (mapImageView.getInteractionMode() == MapImageView.InteractionMode.DRAW) {
            // Already in draw mode — return to navigate
            mapImageView.setInteractionMode(MapImageView.InteractionMode.NAVIGATE);
            setFabActive(fabDraw, false);
        } else {
            // Enter draw mode (deactivate erase if active)
            setFabActive(fabErase, false);
            mapImageView.setInteractionMode(MapImageView.InteractionMode.DRAW);
            setFabActive(fabDraw, true);
            Toast.makeText(this, R.string.map_drawing_mode, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleEraseMode() {
        // Exit placement mode if active
        if (mapImageView.isPlacementMode()) {
            exitPlacementMode();
        }

        if (mapImageView.getInteractionMode() == MapImageView.InteractionMode.ERASE) {
            // Already in erase mode — return to navigate
            mapImageView.setInteractionMode(MapImageView.InteractionMode.NAVIGATE);
            setFabActive(fabErase, false);
        } else {
            // Enter erase mode (deactivate draw if active)
            setFabActive(fabDraw, false);
            mapImageView.setInteractionMode(MapImageView.InteractionMode.ERASE);
            setFabActive(fabErase, true);
            Toast.makeText(this, R.string.map_eraser_mode, Toast.LENGTH_SHORT).show();
        }
    }

    private void resetDrawEraseMode() {
        mapImageView.setInteractionMode(MapImageView.InteractionMode.NAVIGATE);
        setFabActive(fabDraw, false);
        setFabActive(fabErase, false);
    }

    private void setFabActive(FloatingActionButton fab, boolean active) {
        if (active) {
            fab.setBackgroundTintList(ColorStateList.valueOf(0xFFFF5722));
            fab.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        } else {
            fab.setBackgroundTintList(ColorStateList.valueOf(0xFFFFFFFF));
            fab.setImageTintList(ColorStateList.valueOf(0xFF000000));
        }
    }

    private void showColorPickerDialog() {
        int sizeDp = 48;
        int marginDp = 8;
        float density = getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density);
        int marginPx = (int) (marginDp * density);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setRowCount(2);
        int padding = (int) (16 * density);
        grid.setPadding(padding, padding, padding, padding);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.map_pick_color)
                .setView(grid)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        for (int i = 0; i < PALETTE_COLORS.length; i++) {
            final int color = PALETTE_COLORS[i];
            View swatch = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            bg.setStroke((int) (2 * density), color == 0xFFFFFFFF ? 0xFFAAAAAA : 0xFF444444);
            swatch.setBackground(bg);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = sizePx;
            params.height = sizePx;
            params.setMargins(marginPx, marginPx, marginPx, marginPx);
            swatch.setLayoutParams(params);

            swatch.setContentDescription(PALETTE_NAMES[i]);
            swatch.setOnClickListener(v -> {
                currentDrawColor = color;
                mapImageView.setDrawColor(color);
                fabColorPicker.setBackgroundTintList(ColorStateList.valueOf(color));
                // For white color, use dark icon tint for visibility
                if (color == 0xFFFFFFFF) {
                    fabColorPicker.setImageTintList(ColorStateList.valueOf(0xFF000000));
                } else {
                    fabColorPicker.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
                }
                dialog.dismiss();
            });

            grid.addView(swatch);
        }

        dialog.show();
    }

    private void showClearDrawingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.map_clear_drawings_title)
                .setMessage(R.string.map_clear_drawings_message)
                .setPositiveButton(R.string.cc_delete, (dialog, which) -> {
                    mapImageView.clearStrokes();
                    Toast.makeText(this, R.string.map_drawings_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
                    fabDraw.setVisibility(View.VISIBLE);
                    fabErase.setVisibility(View.VISIBLE);
                    fabColorPicker.setVisibility(View.VISIBLE);
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

    private void saveMapData() {
        try {
            // Save points
            JSONArray pointsArr = new JSONArray();
            for (MapImageView.MapPoint p : mapImageView.getPoints()) {
                JSONObject obj = new JSONObject();
                obj.put("x", p.imgX);
                obj.put("y", p.imgY);
                obj.put("label", p.label);
                pointsArr.put(obj);
            }

            // Save drawings
            JSONArray strokesArr = new JSONArray();
            for (MapImageView.DrawStroke s : mapImageView.getStrokes()) {
                JSONObject obj = new JSONObject();
                obj.put("color", s.color);
                obj.put("width", s.strokeWidth);
                JSONArray ptsArr = new JSONArray();
                for (float[] pt : s.points) {
                    JSONArray coord = new JSONArray();
                    coord.put(pt[0]);
                    coord.put(pt[1]);
                    ptsArr.put(coord);
                }
                obj.put("points", ptsArr);
                strokesArr.put(obj);
            }

            getPrefs().edit()
                    .putString(KEY_MAP_POINTS, pointsArr.toString())
                    .putString(KEY_MAP_DRAWINGS, strokesArr.toString())
                    .apply();
            mapImageView.markSaved();
            Toast.makeText(this, R.string.map_data_saved, Toast.LENGTH_SHORT).show();
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

    private List<MapImageView.DrawStroke> loadDrawings() {
        List<MapImageView.DrawStroke> result = new ArrayList<>();
        String json = getPrefs().getString(KEY_MAP_DRAWINGS, null);
        if (json == null) return result;

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                MapImageView.DrawStroke stroke = new MapImageView.DrawStroke(
                        obj.getInt("color"),
                        (float) obj.getDouble("width")
                );
                JSONArray ptsArr = obj.getJSONArray("points");
                for (int j = 0; j < ptsArr.length(); j++) {
                    JSONArray coord = ptsArr.getJSONArray(j);
                    stroke.points.add(new float[]{
                            (float) coord.getDouble(0),
                            (float) coord.getDouble(1)
                    });
                }
                result.add(stroke);
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
            List<MapImageView.MapPoint> savedPoints = loadPoints();
            if (!savedPoints.isEmpty()) {
                mapImageView.setPoints(savedPoints);
            }
            List<MapImageView.DrawStroke> savedStrokes = loadDrawings();
            if (!savedStrokes.isEmpty()) {
                mapImageView.setStrokes(savedStrokes);
            }
            mapImageView.markSaved();
        }
    }

    // Exit handling

    private void handleExit() {
        if (mapImageView.hasUnsavedChanges()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.map_save_prompt_title)
                    .setMessage(R.string.map_save_prompt_message)
                    .setPositiveButton(R.string.map_save, (dialog, which) -> {
                        saveMapData();
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
