package com.example.boardgames;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
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

    // Ability point tracking
    private TextInputEditText editMaxAbilityPoints;
    private TextView textAbilityTotal;
    private LinearLayout containerCustomAbilities;
    private final List<View> customAbilityRows = new ArrayList<>();

    // Combat stats
    private TextInputEditText editHp, editStamina, editAc, editSpeed, editInitiative;
    private TextInputEditText editMaxCombatPoints;
    private TextView textCombatTotal;

    // Details
    private Spinner spinnerBackground;
    private Spinner spinnerAlignment;
    private TextInputEditText editNotes;

    // Starting items
    private TextInputEditText editCarryCapacity;
    private LinearLayout containerStartingItems;
    private final List<View> startingItemRows = new ArrayList<>();

    // Buttons and list
    private MaterialButton btnSave;
    private LinearLayout listSavedCharacters;
    private TextView textNoCharacters;

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

        findViewById(R.id.btn_add_custom_ability).setOnClickListener(v -> addCustomAbilityRow("", 10));
        findViewById(R.id.btn_add_starting_item).setOnClickListener(v -> addStartingItemRow("", "", 100));
        btnSave.setOnClickListener(v -> saveCharacter());

        setupCombatStatListeners();
        updateAbilityTotal();
        updateCombatTotal();
        refreshCharacterList();

        setupTutorial();
    }

    private void setupTutorial() {
        TutorialManager mgr = TutorialManager.getInstance(this);
        if (!mgr.isActive()) return;

        mgr.registerSteps("CharacterCreationActivity", Arrays.asList(
                // 1. Name
                new TutorialManager.TutorialStep(R.id.edit_char_name,
                        R.string.tutorial_cc_name,
                        () -> editCharName.setText("Thorin Oakenshield")),
                // 2. Race
                new TutorialManager.TutorialStep(R.id.spinner_race,
                        R.string.tutorial_cc_race,
                        () -> spinnerRace.setSelection(2)), // Dwarf
                // 3. Class
                new TutorialManager.TutorialStep(R.id.spinner_class,
                        R.string.tutorial_cc_class,
                        () -> spinnerClass.setSelection(4)), // Fighter
                // 4. Level
                new TutorialManager.TutorialStep(R.id.edit_level,
                        R.string.tutorial_cc_level,
                        () -> editLevel.setText("5")),
                // 5. Ability Scores (whole card)
                new TutorialManager.TutorialStep(R.id.card_ability_scores,
                        R.string.tutorial_cc_abilities,
                        () -> {
                            editStr.setText("16");
                            editDex.setText("12");
                            editCon.setText("14");
                            editInt.setText("10");
                            editWis.setText("13");
                            editCha.setText("8");
                        }),
                // 6. Combat Stats (whole card)
                new TutorialManager.TutorialStep(R.id.card_combat_stats,
                        R.string.tutorial_cc_combat,
                        () -> {
                            editHp.setText("44");
                            editAc.setText("16");
                            editStamina.setText("100");
                            editSpeed.setText("25");
                            editInitiative.setText("1");
                        }),
                // 7. Details (whole card)
                new TutorialManager.TutorialStep(R.id.card_details,
                        R.string.tutorial_cc_details,
                        () -> {
                            spinnerBackground.setSelection(11); // Soldier
                            spinnerAlignment.setSelection(0);   // Lawful Good
                            editNotes.setText("A proud dwarf warrior from the Lonely Mountain.");
                        }),
                // 8. Starting Items section
                new TutorialManager.TutorialStep(R.id.btn_add_starting_item,
                        R.string.tutorial_cc_items,
                        () -> {
                            editCarryCapacity.setText("10");
                            if (startingItemRows.isEmpty()) {
                                addStartingItemRow("Battleaxe", "A sturdy dwarven battleaxe", 100);
                                addStartingItemRow("Chain Mail", "Standard fighter armor", 100);
                            }
                        }),
                // 9. Save button
                new TutorialManager.TutorialStep(R.id.btn_save_character,
                        R.string.tutorial_cc_save),
                // 10. Saved characters list
                new TutorialManager.TutorialStep(R.id.card_saved_characters,
                        R.string.tutorial_cc_list)
        ));

        TutorialOverlayView.attach(this, "CharacterCreationActivity");
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

        editMaxAbilityPoints = findViewById(R.id.edit_max_ability_points);
        textAbilityTotal = findViewById(R.id.text_ability_total);
        containerCustomAbilities = findViewById(R.id.container_custom_abilities);

        editHp = findViewById(R.id.edit_hp);
        editStamina = findViewById(R.id.edit_stamina);
        editAc = findViewById(R.id.edit_ac);
        editSpeed = findViewById(R.id.edit_speed);
        editInitiative = findViewById(R.id.edit_initiative);
        editMaxCombatPoints = findViewById(R.id.edit_max_combat_points);
        textCombatTotal = findViewById(R.id.text_combat_total);

        spinnerBackground = findViewById(R.id.spinner_background);
        spinnerAlignment = findViewById(R.id.spinner_alignment);
        editNotes = findViewById(R.id.edit_notes);

        editCarryCapacity = findViewById(R.id.edit_carry_capacity);
        containerStartingItems = findViewById(R.id.container_starting_items);

        btnSave = findViewById(R.id.btn_save_character);
        listSavedCharacters = findViewById(R.id.list_saved_characters);
        textNoCharacters = findViewById(R.id.text_no_characters);
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

        // Also update the total when max ability points field changes
        editMaxAbilityPoints.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateAbilityTotal();
            }
        });
    }

    private void addModifierWatcher(EditText scoreField, TextView modField) {
        scoreField.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateModifier(scoreField, modField);
                updateAbilityTotal();
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

    // ========== Custom Abilities ==========

    private void addCustomAbilityRow(String name, int score) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_custom_ability, containerCustomAbilities, false);

        EditText editName = row.findViewById(R.id.edit_custom_ability_name);
        EditText editScore = row.findViewById(R.id.edit_custom_ability_score);
        TextView textMod = row.findViewById(R.id.text_custom_ability_mod);
        ImageButton btnDelete = row.findViewById(R.id.btn_delete_custom_ability);

        editName.setText(name);
        editScore.setText(String.valueOf(score));
        updateModifier(editScore, textMod);

        // Wire up modifier + total tracking
        editScore.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateModifier(editScore, textMod);
                updateAbilityTotal();
            }
        });

        btnDelete.setOnClickListener(v -> {
            containerCustomAbilities.removeView(row);
            customAbilityRows.remove(row);
            updateAbilityTotal();
        });

        customAbilityRows.add(row);
        containerCustomAbilities.addView(row);
        updateAbilityTotal();
    }

    // ========== Starting Items ==========

    private void addStartingItemRow(String name, String description, int durability) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_starting_item, containerStartingItems, false);

        EditText editName = row.findViewById(R.id.edit_item_name);
        EditText editDesc = row.findViewById(R.id.edit_item_description);
        EditText editDurability = row.findViewById(R.id.edit_item_durability);
        ImageButton btnDelete = row.findViewById(R.id.btn_delete_item);

        editName.setText(name);
        editDesc.setText(description);
        if (durability > 0) {
            editDurability.setText(String.valueOf(durability));
        }

        btnDelete.setOnClickListener(v -> {
            containerStartingItems.removeView(row);
            startingItemRows.remove(row);
        });

        startingItemRows.add(row);
        containerStartingItems.addView(row);
    }

    // ========== Ability Point Total ==========

    private int calculateAbilityTotal() {
        int total = 0;
        total += getEditTextInt(editStr, 10);
        total += getEditTextInt(editDex, 10);
        total += getEditTextInt(editCon, 10);
        total += getEditTextInt(editInt, 10);
        total += getEditTextInt(editWis, 10);
        total += getEditTextInt(editCha, 10);

        for (View row : customAbilityRows) {
            EditText editScore = row.findViewById(R.id.edit_custom_ability_score);
            total += getEditTextInt(editScore, 10);
        }

        return total;
    }

    private void updateAbilityTotal() {
        int total = calculateAbilityTotal();
        int max = getEditTextInt(editMaxAbilityPoints, 0);

        if (max > 0) {
            if (total > max) {
                textAbilityTotal.setText(getString(R.string.cc_ability_total_exceeded, total, max));
                textAbilityTotal.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            } else {
                textAbilityTotal.setText(getString(R.string.cc_ability_total, total, max));
                textAbilityTotal.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            }
        } else {
            textAbilityTotal.setText(getString(R.string.cc_ability_total_no_max, total));
            textAbilityTotal.setTextColor(textStrMod.getCurrentTextColor());
        }
    }

    // ========== Combat Point Total ==========

    private void setupCombatStatListeners() {
        SimpleTextWatcher combatWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateCombatTotal();
            }
        };
        editHp.addTextChangedListener(combatWatcher);
        editStamina.addTextChangedListener(combatWatcher);
        editAc.addTextChangedListener(combatWatcher);
        editSpeed.addTextChangedListener(combatWatcher);
        editInitiative.addTextChangedListener(combatWatcher);
        editMaxCombatPoints.addTextChangedListener(combatWatcher);
    }

    private int calculateCombatTotal() {
        return getEditTextInt(editHp, 10)
                + getEditTextInt(editStamina, 100)
                + getEditTextInt(editAc, 10)
                + getEditTextInt(editSpeed, 30)
                + Math.abs(getEditTextInt(editInitiative, 0));
    }

    private void updateCombatTotal() {
        int total = calculateCombatTotal();
        int max = getEditTextInt(editMaxCombatPoints, 0);

        if (max > 0) {
            if (total > max) {
                textCombatTotal.setText(getString(R.string.cc_combat_total_exceeded, total, max));
                textCombatTotal.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            } else {
                textCombatTotal.setText(getString(R.string.cc_combat_total, total, max));
                textCombatTotal.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            }
        } else {
            textCombatTotal.setText(getString(R.string.cc_combat_total_no_max, total));
            textCombatTotal.setTextColor(textStrMod.getCurrentTextColor());
        }
    }

    // ========== Helpers ==========

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

        // Check ability point constraint
        int maxPoints = getEditTextInt(editMaxAbilityPoints, 0);
        if (maxPoints > 0) {
            int total = calculateAbilityTotal();
            if (total > maxPoints) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.cc_save_failed_title)
                        .setMessage(getString(R.string.cc_ability_points_exceeded, total, maxPoints))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
        }

        // Check combat point constraint
        int maxCombatPoints = getEditTextInt(editMaxCombatPoints, 0);
        if (maxCombatPoints > 0) {
            int combatTotal = calculateCombatTotal();
            if (combatTotal > maxCombatPoints) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.cc_save_failed_title)
                        .setMessage(getString(R.string.cc_combat_points_exceeded, combatTotal, maxCombatPoints))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
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
            character.put("stamina", getEditTextInt(editStamina, 100));
            character.put("ac", getEditTextInt(editAc, 10));
            character.put("speed", getEditTextInt(editSpeed, 30));
            character.put("initiative", getEditTextInt(editInitiative, 0));
            character.put("background", spinnerBackground.getSelectedItemPosition());
            character.put("alignment", spinnerAlignment.getSelectedItemPosition());
            character.put("notes", getEditTextString(editNotes));

            // Save max ability points and max combat points
            character.put("maxAbilityPoints", maxPoints);
            character.put("maxCombatPoints", maxCombatPoints);

            // Save custom abilities
            JSONArray customAbilities = new JSONArray();
            for (View row : customAbilityRows) {
                EditText editAbilityName = row.findViewById(R.id.edit_custom_ability_name);
                EditText editScore = row.findViewById(R.id.edit_custom_ability_score);
                String abilityName = getEditTextString(editAbilityName);
                if (!abilityName.isEmpty()) {
                    JSONObject ability = new JSONObject();
                    ability.put("name", abilityName);
                    ability.put("score", getEditTextInt(editScore, 10));
                    customAbilities.put(ability);
                }
            }
            character.put("customAbilities", customAbilities);

            // Save starting items and carry capacity
            character.put("carryCapacity", getEditTextInt(editCarryCapacity, 10));
            JSONArray startingItems = new JSONArray();
            for (View row : startingItemRows) {
                EditText editItemName = row.findViewById(R.id.edit_item_name);
                EditText editItemDesc = row.findViewById(R.id.edit_item_description);
                EditText editItemDur = row.findViewById(R.id.edit_item_durability);
                String itemName = getEditTextString(editItemName);
                if (!itemName.isEmpty()) {
                    JSONObject item = new JSONObject();
                    item.put("name", itemName);
                    item.put("description", getEditTextString(editItemDesc));
                    item.put("durability", getEditTextInt(editItemDur, 100));
                    startingItems.put(item);
                }
            }
            character.put("startingItems", startingItems);

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

            Toast.makeText(this, getString(R.string.cc_save_success, name), Toast.LENGTH_SHORT).show();
            refreshCharacterList();
        } catch (JSONException e) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.cc_save_failed_title)
                    .setMessage(R.string.cc_save_failed)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void refreshCharacterList() {
        listSavedCharacters.removeAllViews();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String existing = prefs.getString(KEY_CHARACTERS, "[]");

        try {
            JSONArray characters = new JSONArray(existing);

            if (characters.length() == 0) {
                textNoCharacters.setVisibility(View.VISIBLE);
                return;
            }

            textNoCharacters.setVisibility(View.GONE);

            String[] races = getResources().getStringArray(R.array.cc_races);
            String[] classes = getResources().getStringArray(R.array.cc_classes);

            for (int i = 0; i < characters.length(); i++) {
                JSONObject c = characters.getJSONObject(i);
                String name = c.getString("name");
                String details = "Lvl " + c.getInt("level") + " "
                        + races[c.getInt("race")] + " "
                        + classes[c.getInt("class")];

                View itemView = LayoutInflater.from(this)
                        .inflate(R.layout.item_saved_character, listSavedCharacters, false);

                TextView nameText = itemView.findViewById(R.id.text_character_name);
                TextView detailsText = itemView.findViewById(R.id.text_character_details);
                ImageButton deleteBtn = itemView.findViewById(R.id.btn_delete_character);

                nameText.setText(name);
                detailsText.setText(details);

                final int index = i;
                itemView.setOnClickListener(v -> {
                    try {
                        loadCharacter(characters.getJSONObject(index));
                    } catch (JSONException e) {
                        Toast.makeText(this, R.string.cc_load_failed, Toast.LENGTH_SHORT).show();
                    }
                });

                deleteBtn.setOnClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.cc_delete_confirm_title)
                            .setMessage(getString(R.string.cc_delete_confirm_message, name))
                            .setPositiveButton(R.string.cc_delete, (dialog, which) -> {
                                characters.remove(index);
                                prefs.edit().putString(KEY_CHARACTERS, characters.toString()).apply();
                                Toast.makeText(this, "Character deleted", Toast.LENGTH_SHORT).show();
                                refreshCharacterList();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });

                listSavedCharacters.addView(itemView);
            }
        } catch (JSONException e) {
            textNoCharacters.setVisibility(View.VISIBLE);
        }
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
        editStamina.setText(String.valueOf(character.optInt("stamina", 100)));
        editAc.setText(String.valueOf(character.getInt("ac")));
        editSpeed.setText(String.valueOf(character.getInt("speed")));
        editInitiative.setText(String.valueOf(character.getInt("initiative")));

        spinnerBackground.setSelection(character.getInt("background"));
        spinnerAlignment.setSelection(character.getInt("alignment"));
        editNotes.setText(character.optString("notes", ""));

        // Load max ability points
        int maxPoints = character.optInt("maxAbilityPoints", 0);
        editMaxAbilityPoints.setText(maxPoints > 0 ? String.valueOf(maxPoints) : "");

        // Load max combat points
        int maxCombat = character.optInt("maxCombatPoints", 0);
        editMaxCombatPoints.setText(maxCombat > 0 ? String.valueOf(maxCombat) : "");

        // Clear existing custom abilities and load saved ones
        containerCustomAbilities.removeAllViews();
        customAbilityRows.clear();

        JSONArray customAbilities = character.optJSONArray("customAbilities");
        if (customAbilities != null) {
            for (int i = 0; i < customAbilities.length(); i++) {
                JSONObject ability = customAbilities.getJSONObject(i);
                addCustomAbilityRow(
                        ability.getString("name"),
                        ability.getInt("score")
                );
            }
        }

        // Load carry capacity and starting items
        int capacity = character.optInt("carryCapacity", 10);
        editCarryCapacity.setText(String.valueOf(capacity));

        containerStartingItems.removeAllViews();
        startingItemRows.clear();
        JSONArray startingItems = character.optJSONArray("startingItems");
        if (startingItems != null) {
            for (int i = 0; i < startingItems.length(); i++) {
                JSONObject item = startingItems.getJSONObject(i);
                addStartingItemRow(
                        item.getString("name"),
                        item.optString("description", ""),
                        item.optInt("durability", 100)
                );
            }
        }

        Toast.makeText(this,
                getString(R.string.cc_load_success, character.getString("name")),
                Toast.LENGTH_SHORT).show();

        // Scroll to the top so the user can see the loaded data
        findViewById(android.R.id.content).scrollTo(0, 0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /**
     * Simplified TextWatcher that only requires afterTextChanged.
     */
    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
