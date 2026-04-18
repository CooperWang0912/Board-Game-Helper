package com.example.boardgames;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TutorialManager {

    private static final String PREFS_NAME = "tutorial_prefs";
    private static final String KEY_ACTIVE = "tutorial_active";
    private static final String KEY_STEP_PREFIX = "tutorial_step_";

    private static TutorialManager instance;

    private final SharedPreferences prefs;
    private final Map<String, List<TutorialStep>> stepsByActivity = new HashMap<>();

    public static final class TutorialStep {
        public final int targetViewId;
        public final int messageResId;
        public final Runnable autofillAction;

        public TutorialStep(int targetViewId, int messageResId, Runnable autofillAction) {
            this.targetViewId = targetViewId;
            this.messageResId = messageResId;
            this.autofillAction = autofillAction;
        }

        public TutorialStep(int targetViewId, int messageResId) {
            this(targetViewId, messageResId, null);
        }
    }

    private TutorialManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        buildSteps();
    }

    public static synchronized TutorialManager getInstance(Context context) {
        if (instance == null) {
            instance = new TutorialManager(context);
        }
        return instance;
    }

    public boolean isActive() {
        return prefs.getBoolean(KEY_ACTIVE, false);
    }

    public void start() {
        prefs.edit().putBoolean(KEY_ACTIVE, true).apply();
        // Reset all step counters
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : stepsByActivity.keySet()) {
            editor.putInt(KEY_STEP_PREFIX + key, 0);
        }
        editor.apply();
    }

    public void stop() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_ACTIVE, false);
        for (String key : stepsByActivity.keySet()) {
            editor.remove(KEY_STEP_PREFIX + key);
        }
        editor.apply();
    }

    public List<TutorialStep> getSteps(String activityName) {
        List<TutorialStep> steps = stepsByActivity.get(activityName);
        return steps != null ? steps : new ArrayList<>();
    }

    public int getCurrentStepIndex(String activityName) {
        return prefs.getInt(KEY_STEP_PREFIX + activityName, 0);
    }

    public TutorialStep getCurrentStep(String activityName) {
        List<TutorialStep> steps = getSteps(activityName);
        int index = getCurrentStepIndex(activityName);
        if (index >= 0 && index < steps.size()) {
            return steps.get(index);
        }
        return null;
    }

    public boolean advanceStep(String activityName) {
        List<TutorialStep> steps = getSteps(activityName);
        int next = getCurrentStepIndex(activityName) + 1;
        if (next < steps.size()) {
            prefs.edit().putInt(KEY_STEP_PREFIX + activityName, next).apply();
            return true;
        }
        // Store out-of-bounds index so getCurrentStep() returns null
        // instead of resetting to 0 and replaying the tutorial
        prefs.edit().putInt(KEY_STEP_PREFIX + activityName, next).apply();
        return false;
    }

    public void registerSteps(String activityName, List<TutorialStep> steps) {
        stepsByActivity.put(activityName, steps);
    }

    private void buildSteps() {
        // Home screen steps — autofill actions for API expansion are set by MainActivity
        List<TutorialStep> homeSteps = new ArrayList<>();
        homeSteps.add(new TutorialStep(R.id.card_api_settings, R.string.tutorial_home_api_header));
        homeSteps.add(new TutorialStep(R.id.edit_gemini_key, R.string.tutorial_home_api_gemini));
        homeSteps.add(new TutorialStep(R.id.edit_image_key, R.string.tutorial_home_api_image));
        homeSteps.add(new TutorialStep(R.id.card_character_creation, R.string.tutorial_home_character));
        homeSteps.add(new TutorialStep(R.id.card_scenario_creation, R.string.tutorial_home_scenario));
        homeSteps.add(new TutorialStep(R.id.card_dice_roll, R.string.tutorial_home_dice));
        homeSteps.add(new TutorialStep(R.id.card_map, R.string.tutorial_home_map));
        homeSteps.add(new TutorialStep(R.id.card_dungeon_master, R.string.tutorial_home_dm));
        homeSteps.add(new TutorialStep(R.id.radio_bar, R.string.tutorial_home_radio));
        homeSteps.add(new TutorialStep(R.id.switch_dark_mode, R.string.tutorial_home_settings));
        stepsByActivity.put("MainActivity", homeSteps);

        // Character creation, Dice roll, Scenario creation steps are registered by each activity
        // because they need references to views for autofill actions.
    }
}
