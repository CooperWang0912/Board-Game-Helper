package com.example.boardgames;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
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

public class CharacterCreationActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "character_prefs";
    private static final String KEY_CHARACTERS = "saved_characters";

    // Basic info
    private TextInputEditText editCharName;
    private Spinner spinnerRace;
    private Spinner spinnerClass;
    private TextInputEditText editLevel;

    // Ability scores and modifiers
    private EditText editStr, editDex, editCon, editInt, editWis, editCha;
    private TextView textStrMod, textDexMod, textConMod, textIntMod, textWisMod, textChaMod;

    // Combat stats
    private TextInputEditText editHp, editAc, editSpeed, editInitiative;

    // Details
    private Spinner spinnerBackground;
    private Spinner spinnerAlignment;
    private TextInputEditText editNotes;

    // Buttons
    private MaterialButton btnSave, btnLoad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_character_creation);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        bindViews();
        setupSpinners();
        setupAbilityScoreListeners();

        btnSave.setOnClickListener(v -> saveCharacter());
        btnLoad.setOnClickListener(v -> showLoadDialog());
    }

    private void bindViews() {
        editCharName = findViewById(R.id.edit_char_name);
        spinnerRace = findViewById(R.id.spinner_race);
        spinnerClass = findViewById(R.id.spinner_class);
        editLevel = findViewById(R.id.edit_level);

        editStr = findViewById(R.id.edit_str);
        editDex = findViewById(R.id.edit_dex);
        editCon = findViewById(R.id.edit_con);
        editInt = findViewById(R.id.edit_int);
        editWis = findViewById(R.id.edit_wis);
        editCha = findViewById(R.id.edit_cha);

        textStrMod = findViewById(R.id.text_str_mod);
        textDexMod = findViewById(R.id.text_dex_mod);
        textConMod = findViewById(R.id.text_con_mod);
        textIntMod = findViewById(R.id.text_int_mod);
        textWisMod = findViewById(R.id.text_wis_mod);
        textChaMod = findViewById(R.id.text_cha_mod);

        editHp = findViewById(R.id.edit_hp);
        editAc = findViewById(R.id.edit_ac);
        editSpeed = findViewById(R.id.edit_speed);
        editInitiative = findViewById(R.id.edit_initiative);

        spinnerBackground = findViewById(R.id.spinner_background);
        spinnerAlignment = findViewById(R.id.spinner_alignment);
        editNotes = findViewById(R.id.edit_notes);

        btnSave = findViewById(R.id.btn_save_character);
        btnLoad = findViewById(R.id.btn_load_character);
    }

    private void setupSpinners() {
        spinnerRace.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.cc_races)));

        spinnerClass.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.cc_classes)));

        spinnerBackground.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.cc_backgrounds)));

        spinnerAlignment.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.cc_alignments)));
    }

    private void setupAbilityScoreListeners() {
        addModifierWatcher(editStr, textStrMod);
        addModifierWatcher(editDex, textDexMod);
        addModifierWatcher(editCon, textConMod);
        addModifierWatcher(editInt, textIntMod);
        addModifierWatcher(editWis, textWisMod);
        addModifierWatcher(editCha, textChaMod);
    }

    private void addModifierWatcher(EditText scoreField, TextView modField) {
        scoreField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateModifier(scoreField, modField);
            }
        });
    }

    private void updateModifier(EditText scoreField, TextView modField) {
        String text = scoreField.getText().toString().trim();
        if (text.isEmpty()) {
            modField.setText("+0");
            return;
        }
        try {
            int score = Integer.parseInt(text);
            int mod = Math.floorDiv(score - 10, 2);
            String modStr = (mod >= 0) ? "+" + mod : String.valueOf(mod);
            modField.setText(modStr);
        } catch (NumberFormatException e) {
            modField.setText("+0");
        }
    }

    private int getEditTextInt(EditText field, int defaultValue) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getEditTextString(EditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    // ========== Save / Load ==========

    private void saveCharacter() {
        String name = getEditTextString(editCharName);
        if (name.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.cc_save_failed_title)
                    .setMessage(R.string.cc_name_required)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            editCharName.requestFocus();
            return;
        }

        try {
            JSONObject character = new JSONObject();
            character.put("name", name);
            character.put("race", spinnerRace.getSelectedItemPosition());
            character.put("class", spinnerClass.getSelectedItemPosition());
            character.put("level", getEditTextInt(editLevel, 1));
            character.put("str", getEditTextInt(editStr, 10));
            character.put("dex", getEditTextInt(editDex, 10));
            character.put("con", getEditTextInt(editCon, 10));
            character.put("int", getEditTextInt(editInt, 10));
            character.put("wis", getEditTextInt(editWis, 10));
            character.put("cha", getEditTextInt(editCha, 10));
            character.put("hp", getEditTextInt(editHp, 10));
            character.put("ac", getEditTextInt(editAc, 10));
            character.put("speed", getEditTextInt(editSpeed, 30));
            character.put("initiative", getEditTextInt(editInitiative, 0));
            character.put("background", spinnerBackground.getSelectedItemPosition());
            character.put("alignment", spinnerAlignment.getSelectedItemPosition());
            character.put("notes", getEditTextString(editNotes));

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String existing = prefs.getString(KEY_CHARACTERS, "[]");
            JSONArray characters = new JSONArray(existing);

            // Replace if a character with the same name exists, otherwise append
            int replaceIndex = -1;
            for (int i = 0; i < characters.length(); i++) {
                if (characters.getJSONObject(i).getString("name").equals(name)) {
                    replaceIndex = i;
                    break;
                }
            }

            if (replaceIndex >= 0) {
                characters.put(replaceIndex, character);
            } else {
                characters.put(character);
            }

            prefs.edit().putString(KEY_CHARACTERS, characters.toString()).apply();

            new AlertDialog.Builder(this)
                    .setTitle(R.string.cc_save_success_title)
                    .setMessage(getString(R.string.cc_save_success, name))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (JSONException e) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.cc_save_failed_title)
                    .setMessage(R.string.cc_save_failed)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void showLoadDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String existing = prefs.getString(KEY_CHARACTERS, "[]");

        try {
            JSONArray characters = new JSONArray(existing);
            if (characters.length() == 0) {
                Toast.makeText(this, R.string.cc_no_saved, Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> names = new ArrayList<>();
            for (int i = 0; i < characters.length(); i++) {
                JSONObject c = characters.getJSONObject(i);
                String label = c.getString("name") + " (Lvl " + c.getInt("level") + " "
                        + getResources().getStringArray(R.array.cc_races)[c.getInt("race")] + " "
                        + getResources().getStringArray(R.array.cc_classes)[c.getInt("class")] + ")";
                names.add(label);
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.cc_select_character)
                    .setItems(names.toArray(new String[0]), (dialog, which) -> {
                        try {
                            loadCharacter(characters.getJSONObject(which));
                        } catch (JSONException e) {
                            new AlertDialog.Builder(this)
                                    .setTitle(R.string.cc_save_failed_title)
                                    .setMessage(R.string.cc_load_failed)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.cc_delete, (dialog, which) -> showDeleteDialog(characters, names))
                    .show();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.cc_no_saved, Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteDialog(JSONArray characters, List<String> names) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cc_delete)
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    characters.remove(which);
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().putString(KEY_CHARACTERS, characters.toString()).apply();
                    Toast.makeText(this, "Character deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadCharacter(JSONObject character) throws JSONException {
        editCharName.setText(character.getString("name"));
        spinnerRace.setSelection(character.getInt("race"));
        spinnerClass.setSelection(character.getInt("class"));
        editLevel.setText(String.valueOf(character.getInt("level")));

        editStr.setText(String.valueOf(character.getInt("str")));
        editDex.setText(String.valueOf(character.getInt("dex")));
        editCon.setText(String.valueOf(character.getInt("con")));
        editInt.setText(String.valueOf(character.getInt("int")));
        editWis.setText(String.valueOf(character.getInt("wis")));
        editCha.setText(String.valueOf(character.getInt("cha")));

        editHp.setText(String.valueOf(character.getInt("hp")));
        editAc.setText(String.valueOf(character.getInt("ac")));
        editSpeed.setText(String.valueOf(character.getInt("speed")));
        editInitiative.setText(String.valueOf(character.getInt("initiative")));

        spinnerBackground.setSelection(character.getInt("background"));
        spinnerAlignment.setSelection(character.getInt("alignment"));
        editNotes.setText(character.optString("notes", ""));

        new AlertDialog.Builder(this)
                .setTitle(R.string.cc_load_success_title)
                .setMessage(getString(R.string.cc_load_success, character.getString("name")))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
