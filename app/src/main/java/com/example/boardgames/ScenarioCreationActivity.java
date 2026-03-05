package com.example.boardgames;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ScenarioCreationActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "scenario_prefs";
    private static final String KEY_SCENARIOS = "saved_scenarios";

    private TextInputEditText editScenarioName;
    private TextInputEditText editTimePeriod;
    private TextInputEditText editSetting;
    private TextInputEditText editCharacters;
    private TextInputEditText editPlotHook;

    private MaterialButton btnSave, btnLoad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scenario_creation);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        bindViews();

        btnSave.setOnClickListener(v -> saveScenario());
        btnLoad.setOnClickListener(v -> showLoadDialog());
    }

    private void bindViews() {
        editScenarioName = findViewById(R.id.edit_scenario_name);
        editTimePeriod = findViewById(R.id.edit_time_period);
        editSetting = findViewById(R.id.edit_setting);
        editCharacters = findViewById(R.id.edit_characters);
        editPlotHook = findViewById(R.id.edit_plot_hook);

        btnSave = findViewById(R.id.btn_save_scenario);
        btnLoad = findViewById(R.id.btn_load_scenario);
    }

    private String getEditTextString(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    // ========== Save / Load ==========

    private void saveScenario() {
        String name = getEditTextString(editScenarioName);
        if (name.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.sc_save_failed_title)
                    .setMessage(R.string.sc_name_required)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            editScenarioName.requestFocus();
            return;
        }

        try {
            JSONObject scenario = new JSONObject();
            scenario.put("name", name);
            scenario.put("timePeriod", getEditTextString(editTimePeriod));
            scenario.put("setting", getEditTextString(editSetting));
            scenario.put("characters", getEditTextString(editCharacters));
            scenario.put("plotHook", getEditTextString(editPlotHook));

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String existing = prefs.getString(KEY_SCENARIOS, "[]");
            JSONArray scenarios = new JSONArray(existing);

            // Replace if a scenario with the same name exists, otherwise append
            int replaceIndex = -1;
            for (int i = 0; i < scenarios.length(); i++) {
                if (scenarios.getJSONObject(i).getString("name").equals(name)) {
                    replaceIndex = i;
                    break;
                }
            }

            if (replaceIndex >= 0) {
                scenarios.put(replaceIndex, scenario);
            } else {
                scenarios.put(scenario);
            }

            prefs.edit().putString(KEY_SCENARIOS, scenarios.toString()).apply();

            new AlertDialog.Builder(this)
                    .setTitle(R.string.sc_save_success_title)
                    .setMessage(getString(R.string.sc_save_success, name))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (JSONException e) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.sc_save_failed_title)
                    .setMessage(R.string.sc_save_failed)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void showLoadDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String existing = prefs.getString(KEY_SCENARIOS, "[]");

        try {
            JSONArray scenarios = new JSONArray(existing);
            if (scenarios.length() == 0) {
                Toast.makeText(this, R.string.sc_no_saved, Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> names = new ArrayList<>();
            for (int i = 0; i < scenarios.length(); i++) {
                JSONObject s = scenarios.getJSONObject(i);
                String label = s.getString("name");
                String timePeriod = s.optString("timePeriod", "");
                if (!timePeriod.isEmpty()) {
                    label += " (" + timePeriod + ")";
                }
                names.add(label);
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.sc_select_scenario)
                    .setItems(names.toArray(new String[0]), (dialog, which) -> {
                        try {
                            loadScenario(scenarios.getJSONObject(which));
                        } catch (JSONException e) {
                            new AlertDialog.Builder(this)
                                    .setTitle(R.string.sc_save_failed_title)
                                    .setMessage(R.string.sc_load_failed)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.sc_delete, (dialog, which) -> showDeleteDialog(scenarios, names))
                    .show();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.sc_no_saved, Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteDialog(JSONArray scenarios, List<String> names) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sc_delete)
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    scenarios.remove(which);
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().putString(KEY_SCENARIOS, scenarios.toString()).apply();
                    Toast.makeText(this, "Scenario deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadScenario(JSONObject scenario) throws JSONException {
        editScenarioName.setText(scenario.getString("name"));
        editTimePeriod.setText(scenario.optString("timePeriod", ""));
        editSetting.setText(scenario.optString("setting", ""));
        editCharacters.setText(scenario.optString("characters", ""));
        editPlotHook.setText(scenario.optString("plotHook", ""));

        new AlertDialog.Builder(this)
                .setTitle(R.string.sc_load_success_title)
                .setMessage(getString(R.string.sc_load_success, scenario.getString("name")))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
