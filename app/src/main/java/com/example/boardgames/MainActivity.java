package com.example.boardgames;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity
        implements RadioBottomSheetFragment.RadioStateCallback {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_TOS_ACCEPTED = "tos_accepted";
    private static final String KEY_SHOW_GUIDE_ON_STARTUP = "show_guide_on_startup";
    public static final String KEY_DEV_MODE = "dev_mode";
    public static final String API_KEY_PREFS = "api_keys";
    public static final String KEY_GEMINI_API = "gemini_api_key";
    public static final String KEY_IMAGE_API = "image_api_key";

    private LinearLayout layoutApiContent;
    private ImageView iconApiExpand;
    private TextInputEditText editGeminiKey;
    private TextInputEditText editImageKey;
    private TextView textGeminiStatus;
    private TextView textImageStatus;
    private boolean apiSectionExpanded;

    private TextView textRadioBar;
    private ImageButton btnRadioStop;
    private RadioService radioService;
    private boolean radioServiceBound;

    private final ServiceConnection radioServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            RadioService.RadioBinder radioBinder = (RadioService.RadioBinder) binder;
            radioService = radioBinder.getService();
            radioServiceBound = true;
            updateRadioBar();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            radioServiceBound = false;
            radioService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.contains(KEY_DARK_MODE)) {
            boolean darkMode = prefs.getBoolean(KEY_DARK_MODE, false);
            AppCompatDelegate.setDefaultNightMode(
                    darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setupApiKeyPanel();

        findViewById(R.id.card_dice_roll).setOnClickListener(v ->
                startActivity(new Intent(this, DiceRollActivity.class)));

        findViewById(R.id.card_character_creation).setOnClickListener(v ->
                startActivity(new Intent(this, CharacterCreationActivity.class)));

        findViewById(R.id.card_dungeon_master).setOnClickListener(v ->
                startActivity(new Intent(this, DungeonMasterActivity.class)));

        findViewById(R.id.card_scenario_creation).setOnClickListener(v ->
                startActivity(new Intent(this, ScenarioCreationActivity.class)));

        findViewById(R.id.card_map).setOnClickListener(v ->
                startActivity(new Intent(this, MapActivity.class)));

        // Radio bar
        textRadioBar = findViewById(R.id.text_radio_bar);
        btnRadioStop = findViewById(R.id.btn_radio_stop);
        findViewById(R.id.radio_bar).setOnClickListener(v -> {
            RadioBottomSheetFragment sheet = new RadioBottomSheetFragment();
            sheet.show(getSupportFragmentManager(), "radio");
        });
        btnRadioStop.setOnClickListener(v -> {
            if (radioServiceBound && radioService != null && radioService.isPlaying()) {
                radioService.stop();
                updateRadioBar();
            }
        });

        // Dark mode switch
        SwitchCompat switchDarkMode = findViewById(R.id.switch_dark_mode);
        TextView labelDarkMode = findViewById(R.id.label_dark_mode);
        boolean isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES;
        switchDarkMode.setChecked(isDark);
        labelDarkMode.setText(isDark ? R.string.light_mode : R.string.dark_mode);
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        // Settings gear button → opens dialog with developer mode
        findViewById(R.id.btn_settings).setOnClickListener(v -> showSettingsDialog());

        findViewById(R.id.link_how_to_use).setOnClickListener(v -> showHowToUseDialog());
        findViewById(R.id.link_privacy_terms).setOnClickListener(v -> showPrivacyTermsDialog());

        if (!prefs.getBoolean(KEY_TOS_ACCEPTED, false)) {
            showTosDialog();
        } else if (prefs.getBoolean(KEY_SHOW_GUIDE_ON_STARTUP, false)) {
            showHowToUseDialog();
        }
    }

    private void showHowToUseDialog() {
        String[] titles = getResources().getStringArray(R.array.guide_titles);
        String[] bodies = getResources().getStringArray(R.array.guide_bodies);
        TypedArray screenshots = getResources().obtainTypedArray(R.array.guide_screenshots);

        LinearLayout content = buildSectionedContent(titles, bodies, screenshots);
        screenshots.recycle();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean showOnStartup = prefs.getBoolean(KEY_SHOW_GUIDE_ON_STARTUP, false);

        int dp16 = dpToPx(16);

        View divider = new View(this);
        divider.setBackgroundColor(0x1F000000);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        dividerParams.topMargin = dp16;
        divider.setLayoutParams(dividerParams);
        content.addView(divider);

        SwitchCompat toggle = new SwitchCompat(this);
        toggle.setText(R.string.guide_show_on_startup);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        toggle.setChecked(showOnStartup);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleParams.topMargin = dp16;
        toggle.setLayoutParams(toggleParams);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(KEY_SHOW_GUIDE_ON_STARTUP, isChecked).apply());
        content.addView(toggle);

        // "Start Interactive Tutorial" button
        MaterialButton btnTutorial = new MaterialButton(this);
        btnTutorial.setText(R.string.tutorial_start);
        btnTutorial.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams tutorialBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tutorialBtnParams.topMargin = dp16;
        btnTutorial.setLayoutParams(tutorialBtnParams);
        content.addView(btnTutorial);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.how_to_use_title))
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        btnTutorial.setOnClickListener(v -> {
            dialog.dismiss();
            startTutorial();
        });

        dialog.show();
    }

    private void startTutorial() {
        TutorialManager mgr = TutorialManager.getInstance(this);
        mgr.start();
        configureTutorialAutofill(mgr);
        Toast.makeText(this, R.string.tutorial_started, Toast.LENGTH_SHORT).show();
        showTutorialOverlay();
    }

    private void configureTutorialAutofill(TutorialManager mgr) {
        // Set autofill action on the first home step to expand the API panel
        java.util.List<TutorialManager.TutorialStep> steps = mgr.getSteps("MainActivity");
        if (!steps.isEmpty()) {
            // Replace step 0 (API header) with a version that expands the panel
            steps.set(0, new TutorialManager.TutorialStep(
                    R.id.card_api_settings,
                    R.string.tutorial_home_api_header,
                    () -> {
                        if (!apiSectionExpanded) {
                            toggleApiSection();
                        }
                    }));
        }
    }

    private void showTutorialOverlay() {
        TutorialOverlayView.attach(this, "MainActivity");
    }

    private void showTosDialog() {
        String[] titles = getResources().getStringArray(R.array.privacy_titles);
        String[] bodies = getResources().getStringArray(R.array.privacy_bodies);

        int dp16 = dpToPx(16);
        int dp24 = dpToPx(24);

        LinearLayout content = buildSectionedContent(titles, bodies);

        // Scroll hint shown until user reaches the bottom
        TextView scrollHint = new TextView(this);
        scrollHint.setText(R.string.tos_scroll_hint);
        scrollHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        scrollHint.setTypeface(null, Typeface.ITALIC);
        scrollHint.setTextColor(0xFFF44336);
        scrollHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.setMargins(dp24, dp16 * 2, dp24, 0);
        scrollHint.setLayoutParams(hintParams);
        content.addView(scrollHint);

        // Certify button — disabled until user scrolls to bottom
        MaterialButton btnCertify = new MaterialButton(this);
        btnCertify.setText(R.string.tos_certify);
        btnCertify.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnCertify.setEnabled(false);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(dp24, dp16, dp24, dp16);
        btnCertify.setLayoutParams(btnParams);
        content.addView(btnCertify);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.tos_dialog_title)
                .setView(scrollView)
                .setCancelable(false)
                .create();

        // Enable button when user scrolls to the bottom
        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (!btnCertify.isEnabled()) {
                int scrollY = scrollView.getScrollY();
                int contentHeight = scrollView.getChildAt(0).getHeight();
                int viewHeight = scrollView.getHeight();
                if (scrollY + viewHeight >= contentHeight - dpToPx(10)) {
                    btnCertify.setEnabled(true);
                    scrollHint.setVisibility(View.GONE);
                }
            }
        });

        btnCertify.setOnClickListener(v -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_TOS_ACCEPTED, true)
                    .apply();
            dialog.dismiss();
        });

        dialog.show();

        // Check after dialog is shown and laid out in case content fits without scrolling
        scrollView.post(() -> {
            int contentHeight = scrollView.getChildAt(0).getHeight();
            int viewHeight = scrollView.getHeight();
            if (contentHeight <= viewHeight) {
                btnCertify.setEnabled(true);
                scrollHint.setVisibility(View.GONE);
            }
        });
    }

    private void showPrivacyTermsDialog() {
        String[] titles = getResources().getStringArray(R.array.privacy_titles);
        String[] bodies = getResources().getStringArray(R.array.privacy_bodies);

        boolean accepted = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_TOS_ACCEPTED, false);

        LinearLayout content = buildSectionedContent(titles, bodies);

        if (accepted) {
            int dp16 = dpToPx(16);
            int dp24 = dpToPx(24);

            TextView agreedNote = new TextView(this);
            agreedNote.setText(R.string.tos_agreed_note);
            agreedNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            agreedNote.setTypeface(null, Typeface.ITALIC);
            agreedNote.setTextColor(0xFF4CAF50);
            LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            noteParams.setMargins(dp24, 0, dp24, dp16);
            agreedNote.setLayoutParams(noteParams);
            content.addView(agreedNote, 0);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.privacy_terms_title))
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private LinearLayout buildSectionedContent(String[] titles, String[] bodies) {
        return buildSectionedContent(titles, bodies, null);
    }

    private LinearLayout buildSectionedContent(String[] titles, String[] bodies,
                                               TypedArray screenshots) {
        int dp24 = dpToPx(24);
        int dp16 = dpToPx(16);
        int dp8 = dpToPx(8);
        int dp4 = dpToPx(4);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int primaryColor = typedValue.data;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp24, dp16, dp24, dp16);

        for (int i = 0; i < titles.length; i++) {
            if (i > 0) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp16));
                container.addView(spacer);
            }

            TextView title = new TextView(this);
            title.setText(titles[i]);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            title.setTypeface(null, Typeface.BOLD);
            title.setTextColor(primaryColor);
            container.addView(title);

            TextView body = new TextView(this);
            body.setText(bodies[i]);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setLineSpacing(0, 1.2f);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            bodyParams.topMargin = dp4;
            body.setLayoutParams(bodyParams);
            container.addView(body);

            // Add screenshot if one is provided for this section
            if (screenshots != null && i < screenshots.length()) {
                int drawableId = screenshots.getResourceId(i, 0);
                if (drawableId != 0) {
                    MaterialCardView imageCard = new MaterialCardView(this);
                    imageCard.setRadius(dpToPx(12));
                    imageCard.setCardElevation(dpToPx(2));
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    cardParams.topMargin = dp8;
                    imageCard.setLayoutParams(cardParams);

                    ImageView screenshot = new ImageView(this);
                    screenshot.setImageResource(drawableId);
                    screenshot.setAdjustViewBounds(true);
                    screenshot.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    imageCard.addView(screenshot);

                    container.addView(imageCard);
                }
            }
        }

        return container;
    }

    private void showSectionedDialog(String dialogTitle, String[] titles, String[] bodies) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(buildSectionedContent(titles, bodies));

        new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void setupApiKeyPanel() {
        layoutApiContent = findViewById(R.id.layout_api_content);
        iconApiExpand = findViewById(R.id.icon_api_expand);
        editGeminiKey = findViewById(R.id.edit_gemini_key);
        editImageKey = findViewById(R.id.edit_image_key);
        textGeminiStatus = findViewById(R.id.text_gemini_status);
        textImageStatus = findViewById(R.id.text_image_status);

        MaterialButton btnSaveGemini = findViewById(R.id.btn_save_gemini);
        MaterialButton btnClearGemini = findViewById(R.id.btn_clear_gemini);
        MaterialButton btnSaveImage = findViewById(R.id.btn_save_image);
        MaterialButton btnClearImage = findViewById(R.id.btn_clear_image);

        // Toggle expand/collapse
        findViewById(R.id.layout_api_header).setOnClickListener(v -> toggleApiSection());

        btnSaveGemini.setOnClickListener(v -> saveApiKey(KEY_GEMINI_API, editGeminiKey));
        btnClearGemini.setOnClickListener(v -> clearApiKey(KEY_GEMINI_API, editGeminiKey));
        btnSaveImage.setOnClickListener(v -> saveApiKey(KEY_IMAGE_API, editImageKey));
        btnClearImage.setOnClickListener(v -> clearApiKey(KEY_IMAGE_API, editImageKey));

        updateApiStatusIndicators();
    }

    private void toggleApiSection() {
        apiSectionExpanded = !apiSectionExpanded;
        layoutApiContent.setVisibility(apiSectionExpanded ? View.VISIBLE : View.GONE);
        iconApiExpand.setRotation(apiSectionExpanded ? 180f : 0f);
    }

    private void saveApiKey(String key, TextInputEditText editText) {
        String value = editText.getText() != null ? editText.getText().toString().trim() : "";
        if (value.isEmpty()) {
            Toast.makeText(this, R.string.api_key_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        getSharedPreferences(API_KEY_PREFS, MODE_PRIVATE)
                .edit()
                .putString(key, value)
                .apply();

        editText.setText("");
        Toast.makeText(this, R.string.api_key_saved, Toast.LENGTH_SHORT).show();
        updateApiStatusIndicators();
    }

    private void clearApiKey(String key, TextInputEditText editText) {
        getSharedPreferences(API_KEY_PREFS, MODE_PRIVATE)
                .edit()
                .remove(key)
                .apply();

        editText.setText("");
        Toast.makeText(this, R.string.api_key_cleared, Toast.LENGTH_SHORT).show();
        updateApiStatusIndicators();
    }

    private void updateApiStatusIndicators() {
        SharedPreferences apiPrefs = getSharedPreferences(API_KEY_PREFS, MODE_PRIVATE);

        String geminiKey = apiPrefs.getString(KEY_GEMINI_API, "");
        boolean geminiConfigured = !geminiKey.isEmpty();
        // Also consider BuildConfig as a fallback
        if (!geminiConfigured) {
            String buildKey = BuildConfig.GEMINI_API_KEY;
            geminiConfigured = buildKey != null && !buildKey.isEmpty() && !buildKey.equals("your_key_here");
        }
        textGeminiStatus.setText(geminiConfigured ? R.string.api_status_configured : R.string.api_status_not_configured);
        textGeminiStatus.setTextColor(geminiConfigured ? 0xFF4CAF50 : 0xFFF44336);

        String imageKey = apiPrefs.getString(KEY_IMAGE_API, "");
        boolean imageConfigured = !imageKey.isEmpty();
        if (!imageConfigured) {
            String buildKey = BuildConfig.HF_API_KEY;
            imageConfigured = buildKey != null && !buildKey.isEmpty() && !buildKey.equals("your_hf_token_here");
        }
        textImageStatus.setText(imageConfigured ? R.string.api_status_configured : R.string.api_status_not_configured);
        textImageStatus.setTextColor(imageConfigured ? 0xFF4CAF50 : 0xFFF44336);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (textGeminiStatus != null) {
            updateApiStatusIndicators();
        }
        // Bind to RadioService to show current playback state
        Intent radioIntent = new Intent(this, RadioService.class);
        bindService(radioIntent, radioServiceConnection, 0);

        // Show tutorial overlay if active
        TutorialManager tutorialMgr = TutorialManager.getInstance(this);
        if (tutorialMgr.isActive()) {
            configureTutorialAutofill(tutorialMgr);
            // Post to allow layout to complete first
            findViewById(android.R.id.content).post(this::showTutorialOverlay);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (radioServiceBound) {
            unbindService(radioServiceConnection);
            radioServiceBound = false;
            radioService = null;
        }
    }

    @Override
    public void onRadioStateChanged(boolean playing) {
        updateRadioBar();
    }

    private void updateRadioBar() {
        if (textRadioBar == null) return;
        if (radioServiceBound && radioService != null
                && radioService.isPlaying() && radioService.getCurrentStation() != null) {
            textRadioBar.setText(getString(R.string.radio_status_playing,
                    radioService.getCurrentStation().getName()));
            btnRadioStop.setVisibility(View.VISIBLE);
        } else if (radioServiceBound && radioService != null && radioService.isPreparing()) {
            textRadioBar.setText(R.string.radio_buffering);
            btnRadioStop.setVisibility(View.GONE);
        } else {
            textRadioBar.setText(R.string.radio_tap_to_play);
            btnRadioStop.setVisibility(View.GONE);
        }
    }

    private void showSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), 0);

        // Developer Mode toggle
        LinearLayout devRow = new LinearLayout(this);
        devRow.setOrientation(LinearLayout.HORIZONTAL);
        devRow.setGravity(Gravity.CENTER_VERTICAL);
        devRow.setPadding(0, dpToPx(8), 0, dpToPx(8));

        LinearLayout devTextColumn = new LinearLayout(this);
        devTextColumn.setOrientation(LinearLayout.VERTICAL);
        devTextColumn.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView devTitle = new TextView(this);
        devTitle.setText(R.string.dev_mode);
        devTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        devTitle.setTypeface(null, Typeface.BOLD);
        devTextColumn.addView(devTitle);

        TextView devDesc = new TextView(this);
        devDesc.setText(R.string.dev_mode_desc);
        devDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        devDesc.setAlpha(0.7f);
        devTextColumn.addView(devDesc);

        devRow.addView(devTextColumn);

        SwitchCompat switchDev = new SwitchCompat(this);
        switchDev.setChecked(prefs.getBoolean(KEY_DEV_MODE, false));
        switchDev.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DEV_MODE, isChecked).apply();
            Toast.makeText(this,
                    isChecked ? R.string.dev_mode_enabled : R.string.dev_mode_disabled,
                    Toast.LENGTH_SHORT).show();
        });
        devRow.addView(switchDev);

        layout.addView(devRow);

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
