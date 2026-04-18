package com.example.boardgames;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ScenarioCreationActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "scenario_prefs";
    private static final String KEY_SCENARIOS = "saved_scenarios";

    private TextInputEditText editScenarioName;
    private TextInputEditText editTimePeriod;
    private TextInputEditText editSetting;
    private TextInputEditText editCharacters;
    private TextInputEditText editPlotHook;
    private Spinner spinnerRunLength;
    private TextInputEditText editNpcCount;
    private TextInputEditText editLocationCount;
    private TextInputEditText editStaminaRecovery;
    private TextInputEditText editCustomRules;

    private MaterialButton btnSave;
    private LinearLayout listSavedScenarios;
    private TextView textNoScenarios;

    // Win/Lose Conditions
    private Spinner spinnerWinCondition;
    private Spinner spinnerLoseCondition;
    private TextInputLayout layoutCustomWinText;
    private TextInputLayout layoutCustomLoseText;
    private TextInputEditText editCustomWinText;
    private TextInputEditText editCustomLoseText;

    // Rulebook
    private TextView textRulebookName;
    private MaterialButton btnRulebookUpload;
    private MaterialButton btnRulebookClear;
    private String rulebookText = "";
    private String rulebookFileName = "";
    private static final int RULEBOOK_MAX_CHARS = 50000;

    private final ActivityResultLauncher<String> rulebookPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onRulebookPicked);

    // Encounters
    private LinearLayout listEncounters;
    private TextView textNoEncounters;
    private final List<JSONObject> encounters = new ArrayList<>();
    private String[] encounterTypes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scenario_creation);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        encounterTypes = getResources().getStringArray(R.array.sc_encounter_types);

        bindViews();

        btnSave.setOnClickListener(v -> saveScenario());

        refreshScenarioList();
        refreshEncounterList();

        setupTutorial();
    }

    private void setupTutorial() {
        TutorialManager mgr = TutorialManager.getInstance(this);
        if (!mgr.isActive()) return;

        mgr.registerSteps("ScenarioCreationActivity", Arrays.asList(
                // 1. Scenario name
                new TutorialManager.TutorialStep(R.id.edit_scenario_name,
                        R.string.tutorial_sc_name,
                        () -> editScenarioName.setText("The Lost Mine")),
                // 2. Time period
                new TutorialManager.TutorialStep(R.id.edit_time_period,
                        R.string.tutorial_sc_time,
                        () -> editTimePeriod.setText("Medieval era, Year 1400")),
                // 3. Setting
                new TutorialManager.TutorialStep(R.id.edit_setting,
                        R.string.tutorial_sc_setting,
                        () -> editSetting.setText("An abandoned dwarven mine beneath the Sword Mountains")),
                // 4. Characters & NPCs
                new TutorialManager.TutorialStep(R.id.edit_characters,
                        R.string.tutorial_sc_characters,
                        () -> editCharacters.setText("Gundren Rockseeker (dwarf ally), Black Spider (drow villain), Sister Garaele (elf healer)")),
                // 5. Plot hook
                new TutorialManager.TutorialStep(R.id.edit_plot_hook,
                        R.string.tutorial_sc_plot_hook,
                        () -> editPlotHook.setText("A dwarf merchant hires the party to escort a wagon of supplies to the mining town of Phandalin, but he has been kidnapped along the way.")),
                // 6. Run length
                new TutorialManager.TutorialStep(R.id.spinner_run_length,
                        R.string.tutorial_sc_run_length,
                        () -> spinnerRunLength.setSelection(1)), // Medium
                // 7. NPC & Location counts
                new TutorialManager.TutorialStep(R.id.edit_npc_count,
                        R.string.tutorial_sc_gm_counts,
                        () -> {
                            editNpcCount.setText("5");
                            editLocationCount.setText("3");
                        }),
                // 8. Custom rules & stamina recovery
                new TutorialManager.TutorialStep(R.id.edit_custom_rules,
                        R.string.tutorial_sc_rules,
                        () -> {
                            editCustomRules.setText("Encounters should include at least one puzzle per session.");
                            editStaminaRecovery.setText("5");
                        }),
                // 9. Save button
                new TutorialManager.TutorialStep(R.id.btn_save_scenario,
                        R.string.tutorial_sc_save),
                // 10. Saved scenarios list
                new TutorialManager.TutorialStep(R.id.card_saved_scenarios,
                        R.string.tutorial_sc_list)
        ));

        TutorialOverlayView.attach(this, "ScenarioCreationActivity");
    }

    private void bindViews() {
        editScenarioName = findViewById(R.id.edit_scenario_name);
        editTimePeriod = findViewById(R.id.edit_time_period);
        editSetting = findViewById(R.id.edit_setting);
        editCharacters = findViewById(R.id.edit_characters);
        editPlotHook = findViewById(R.id.edit_plot_hook);

        spinnerRunLength = findViewById(R.id.spinner_run_length);
        ArrayAdapter<CharSequence> runLengthAdapter = ArrayAdapter.createFromResource(
                this, R.array.sc_run_lengths, android.R.layout.simple_spinner_item);
        runLengthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRunLength.setAdapter(runLengthAdapter);

        editNpcCount = findViewById(R.id.edit_npc_count);
        editLocationCount = findViewById(R.id.edit_location_count);
        editStaminaRecovery = findViewById(R.id.edit_stamina_recovery);
        editCustomRules = findViewById(R.id.edit_custom_rules);

        btnSave = findViewById(R.id.btn_save_scenario);
        listSavedScenarios = findViewById(R.id.list_saved_scenarios);
        textNoScenarios = findViewById(R.id.text_no_scenarios);

        // Win/Lose Conditions
        spinnerWinCondition = findViewById(R.id.spinner_win_condition);
        ArrayAdapter<CharSequence> winCondAdapter = ArrayAdapter.createFromResource(
                this, R.array.sc_win_conditions, android.R.layout.simple_spinner_item);
        winCondAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerWinCondition.setAdapter(winCondAdapter);

        spinnerLoseCondition = findViewById(R.id.spinner_lose_condition);
        ArrayAdapter<CharSequence> loseCondAdapter = ArrayAdapter.createFromResource(
                this, R.array.sc_lose_conditions, android.R.layout.simple_spinner_item);
        loseCondAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLoseCondition.setAdapter(loseCondAdapter);

        layoutCustomWinText = findViewById(R.id.layout_custom_win_text);
        editCustomWinText = findViewById(R.id.edit_custom_win_text);
        layoutCustomLoseText = findViewById(R.id.layout_custom_lose_text);
        editCustomLoseText = findViewById(R.id.edit_custom_lose_text);

        spinnerWinCondition.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                layoutCustomWinText.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        spinnerLoseCondition.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                layoutCustomLoseText.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        // Rulebook
        textRulebookName = findViewById(R.id.text_rulebook_name);
        btnRulebookUpload = findViewById(R.id.btn_rulebook_upload);
        btnRulebookClear = findViewById(R.id.btn_rulebook_clear);
        btnRulebookUpload.setOnClickListener(v -> rulebookPickerLauncher.launch("text/plain"));
        btnRulebookClear.setOnClickListener(v -> clearRulebook());

        // Encounters
        listEncounters = findViewById(R.id.list_encounters);
        textNoEncounters = findViewById(R.id.text_no_encounters);
        findViewById(R.id.btn_add_encounter).setOnClickListener(v -> showEncounterDialog(-1));
    }

    private String getEditTextString(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private String getEditTextString(EditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    // ========== Rulebook Picker ==========

    private void onRulebookPicked(Uri uri) {
        if (uri == null) return;

        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int charsRead;
            while ((charsRead = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, charsRead);
                if (sb.length() > RULEBOOK_MAX_CHARS) break;
            }

            boolean truncated = sb.length() > RULEBOOK_MAX_CHARS;
            if (truncated) {
                sb.setLength(RULEBOOK_MAX_CHARS);
                Toast.makeText(this, R.string.sc_rulebook_too_large, Toast.LENGTH_LONG).show();
            }

            rulebookText = sb.toString();
            rulebookFileName = getDisplayName(uri);
            textRulebookName.setText(rulebookFileName);
            textRulebookName.setAlpha(1f);
            btnRulebookClear.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.sc_rulebook_read_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void clearRulebook() {
        rulebookText = "";
        rulebookFileName = "";
        textRulebookName.setText(R.string.sc_rulebook_default);
        textRulebookName.setAlpha(0.7f);
        btnRulebookClear.setVisibility(View.GONE);
    }

    private String getDisplayName(Uri uri) {
        String name = "rulebook.txt";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        }
        return name;
    }

    // ========== Encounter Editor ==========

    private void showEncounterDialog(int editIndex) {
        boolean isEdit = editIndex >= 0 && editIndex < encounters.size();
        JSONObject existing = isEdit ? encounters.get(editIndex) : null;

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_encounter_editor, null);

        TextInputEditText editName = dialogView.findViewById(R.id.edit_encounter_name);
        Spinner spinnerType = dialogView.findViewById(R.id.spinner_encounter_type);
        TextInputEditText editDescription = dialogView.findViewById(R.id.edit_encounter_description);
        TextInputEditText editEnemies = dialogView.findViewById(R.id.edit_encounter_enemies);
        TextInputEditText editObjective = dialogView.findViewById(R.id.edit_encounter_objective);
        TextInputEditText editNotes = dialogView.findViewById(R.id.edit_encounter_notes);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, encounterTypes);
        spinnerType.setAdapter(typeAdapter);

        if (isEdit) {
            editName.setText(existing.optString("name", ""));
            spinnerType.setSelection(existing.optInt("type", 0));
            editDescription.setText(existing.optString("description", ""));
            editEnemies.setText(existing.optString("enemies", ""));
            editObjective.setText(existing.optString("objective", ""));
            editNotes.setText(existing.optString("notes", ""));
        }

        new AlertDialog.Builder(this)
                .setTitle(isEdit ? R.string.sc_edit_encounter : R.string.sc_add_encounter)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = getEditTextString(editName);
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.sc_encounter_name_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        JSONObject encounter = new JSONObject();
                        encounter.put("name", name);
                        encounter.put("type", spinnerType.getSelectedItemPosition());
                        encounter.put("description", getEditTextString(editDescription));
                        encounter.put("enemies", getEditTextString(editEnemies));
                        encounter.put("objective", getEditTextString(editObjective));
                        encounter.put("notes", getEditTextString(editNotes));

                        if (isEdit) {
                            encounters.set(editIndex, encounter);
                        } else {
                            encounters.add(encounter);
                        }
                        refreshEncounterList();
                    } catch (JSONException ignored) {
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshEncounterList() {
        listEncounters.removeAllViews();

        if (encounters.isEmpty()) {
            textNoEncounters.setVisibility(View.VISIBLE);
            return;
        }

        textNoEncounters.setVisibility(View.GONE);

        for (int i = 0; i < encounters.size(); i++) {
            JSONObject enc = encounters.get(i);

            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_encounter, listEncounters, false);

            TextView numberText = row.findViewById(R.id.text_encounter_number);
            TextView nameText = row.findViewById(R.id.text_encounter_name);
            TextView typeText = row.findViewById(R.id.text_encounter_type);
            ImageButton btnUp = row.findViewById(R.id.btn_move_up);
            ImageButton btnDown = row.findViewById(R.id.btn_move_down);
            ImageButton btnDelete = row.findViewById(R.id.btn_delete_encounter);

            numberText.setText(String.valueOf(i + 1));
            nameText.setText(enc.optString("name", ""));

            int typeIndex = enc.optInt("type", 0);
            if (typeIndex >= 0 && typeIndex < encounterTypes.length) {
                typeText.setText(encounterTypes[typeIndex]);
            }

            // Hide up/down buttons at boundaries
            btnUp.setVisibility(i == 0 ? View.INVISIBLE : View.VISIBLE);
            btnDown.setVisibility(i == encounters.size() - 1 ? View.INVISIBLE : View.VISIBLE);

            final int index = i;

            // Tap row to edit
            row.setOnClickListener(v -> showEncounterDialog(index));

            btnUp.setOnClickListener(v -> {
                if (index > 0) {
                    Collections.swap(encounters, index, index - 1);
                    refreshEncounterList();
                }
            });

            btnDown.setOnClickListener(v -> {
                if (index < encounters.size() - 1) {
                    Collections.swap(encounters, index, index + 1);
                    refreshEncounterList();
                }
            });

            btnDelete.setOnClickListener(v -> {
                String encName = enc.optString("name", "Encounter");
                new AlertDialog.Builder(this)
                        .setTitle(R.string.sc_encounter_delete_title)
                        .setMessage(getString(R.string.sc_encounter_delete_message, encName))
                        .setPositiveButton(R.string.sc_delete, (dialog, which) -> {
                            encounters.remove(index);
                            refreshEncounterList();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });

            listEncounters.addView(row);
        }
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
            scenario.put("runLength", spinnerRunLength.getSelectedItemPosition());
            String npcStr = getEditTextString(editNpcCount);
            scenario.put("npcCount", npcStr.isEmpty() ? 0 : Integer.parseInt(npcStr));
            String locStr = getEditTextString(editLocationCount);
            scenario.put("locationCount", locStr.isEmpty() ? 0 : Integer.parseInt(locStr));
            String stamRecStr = getEditTextString(editStaminaRecovery);
            scenario.put("staminaRecoveryPerTurn",
                    stamRecStr.isEmpty() ? 0 : Integer.parseInt(stamRecStr));
            scenario.put("customRules", getEditTextString(editCustomRules));
            scenario.put("winCondition", spinnerWinCondition.getSelectedItemPosition());
            scenario.put("loseCondition", spinnerLoseCondition.getSelectedItemPosition());
            scenario.put("customWinText", getEditTextString(editCustomWinText));
            scenario.put("customLoseText", getEditTextString(editCustomLoseText));

            // Save rulebook
            scenario.put("rulebookText", rulebookText);
            scenario.put("rulebookFileName", rulebookFileName);

            // Save encounters
            JSONArray encountersArray = new JSONArray();
            for (JSONObject enc : encounters) {
                encountersArray.put(enc);
            }
            scenario.put("encounters", encountersArray);

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

            Toast.makeText(this, getString(R.string.sc_save_success, name), Toast.LENGTH_SHORT).show();
            refreshScenarioList();
        } catch (JSONException e) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.sc_save_failed_title)
                    .setMessage(R.string.sc_save_failed)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void refreshScenarioList() {
        listSavedScenarios.removeAllViews();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String existing = prefs.getString(KEY_SCENARIOS, "[]");

        try {
            JSONArray scenarios = new JSONArray(existing);

            if (scenarios.length() == 0) {
                textNoScenarios.setVisibility(View.VISIBLE);
                return;
            }

            textNoScenarios.setVisibility(View.GONE);

            for (int i = 0; i < scenarios.length(); i++) {
                JSONObject s = scenarios.getJSONObject(i);
                String name = s.getString("name");
                String timePeriod = s.optString("timePeriod", "");
                String details = timePeriod.isEmpty() ? "" : timePeriod;
                String setting = s.optString("setting", "");
                if (!setting.isEmpty()) {
                    details = details.isEmpty() ? setting : details + " \u2022 " + setting;
                }
                JSONArray enc = s.optJSONArray("encounters");
                if (enc != null && enc.length() > 0) {
                    String encLabel = enc.length() + " encounter" + (enc.length() > 1 ? "s" : "");
                    details = details.isEmpty() ? encLabel : details + " \u2022 " + encLabel;
                }

                View itemView = LayoutInflater.from(this)
                        .inflate(R.layout.item_saved_scenario, listSavedScenarios, false);

                TextView nameText = itemView.findViewById(R.id.text_scenario_name);
                TextView detailsText = itemView.findViewById(R.id.text_scenario_details);
                ImageButton deleteBtn = itemView.findViewById(R.id.btn_delete_scenario);

                nameText.setText(name);
                if (details.isEmpty()) {
                    detailsText.setVisibility(View.GONE);
                } else {
                    detailsText.setText(details);
                }

                final int index = i;
                itemView.setOnClickListener(v -> {
                    try {
                        loadScenario(scenarios.getJSONObject(index));
                    } catch (JSONException e) {
                        Toast.makeText(this, R.string.sc_load_failed, Toast.LENGTH_SHORT).show();
                    }
                });

                deleteBtn.setOnClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.sc_delete_confirm_title)
                            .setMessage(getString(R.string.sc_delete_confirm_message, name))
                            .setPositiveButton(R.string.sc_delete, (dialog, which) -> {
                                scenarios.remove(index);
                                prefs.edit().putString(KEY_SCENARIOS, scenarios.toString()).apply();
                                Toast.makeText(this, "Scenario deleted", Toast.LENGTH_SHORT).show();
                                refreshScenarioList();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });

                listSavedScenarios.addView(itemView);
            }
        } catch (JSONException e) {
            textNoScenarios.setVisibility(View.VISIBLE);
        }
    }

    private void loadScenario(JSONObject scenario) throws JSONException {
        editScenarioName.setText(scenario.getString("name"));
        editTimePeriod.setText(scenario.optString("timePeriod", ""));
        editSetting.setText(scenario.optString("setting", ""));
        editCharacters.setText(scenario.optString("characters", ""));
        editPlotHook.setText(scenario.optString("plotHook", ""));

        spinnerRunLength.setSelection(scenario.optInt("runLength", 0));
        int npcCount = scenario.optInt("npcCount", 0);
        editNpcCount.setText(npcCount > 0 ? String.valueOf(npcCount) : "");
        int locationCount = scenario.optInt("locationCount", 0);
        editLocationCount.setText(locationCount > 0 ? String.valueOf(locationCount) : "");
        int staminaRecovery = scenario.optInt("staminaRecoveryPerTurn", 0);
        editStaminaRecovery.setText(staminaRecovery > 0 ? String.valueOf(staminaRecovery) : "");
        editCustomRules.setText(scenario.optString("customRules", ""));
        spinnerWinCondition.setSelection(scenario.optInt("winCondition", 0));
        spinnerLoseCondition.setSelection(scenario.optInt("loseCondition", 0));
        editCustomWinText.setText(scenario.optString("customWinText", ""));
        editCustomLoseText.setText(scenario.optString("customLoseText", ""));

        // Load rulebook
        rulebookText = scenario.optString("rulebookText", "");
        rulebookFileName = scenario.optString("rulebookFileName", "");
        if (!rulebookText.isEmpty() && !rulebookFileName.isEmpty()) {
            textRulebookName.setText(rulebookFileName);
            textRulebookName.setAlpha(1f);
            btnRulebookClear.setVisibility(View.VISIBLE);
        } else {
            clearRulebook();
        }

        // Load encounters
        encounters.clear();
        JSONArray encountersArray = scenario.optJSONArray("encounters");
        if (encountersArray != null) {
            for (int i = 0; i < encountersArray.length(); i++) {
                encounters.add(encountersArray.getJSONObject(i));
            }
        }
        refreshEncounterList();

        Toast.makeText(this,
                getString(R.string.sc_load_success, scenario.getString("name")),
                Toast.LENGTH_SHORT).show();

        // Scroll to the top so the user can see the loaded data
        findViewById(android.R.id.content).scrollTo(0, 0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
