package com.example.boardgames;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.Intent;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.LruCache;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DungeonMasterActivity extends AppCompatActivity
        implements RadioBottomSheetFragment.RadioStateCallback {

    private static final String PREFS_NAME = "character_prefs";
    private static final String KEY_CHARACTERS = "saved_characters";
    private static final String DM_CHAT_PREFS = "dm_chat_prefs";
    private static final String KEY_CHAT_MESSAGES = "chat_messages_";
    private static final String KEY_LAST_CHARACTER = "last_character";
    private static final String KEY_RUN_LIST = "run_list_";
    private static final String KEY_ACTIVE_RUN = "active_run_";

    private MaterialCardView cardCharacterInfo;
    private TextView textCharacterInfo;
    private RecyclerView recyclerChat;
    private LinearLayout layoutLoading;
    private LinearLayout layoutInput;
    private TextInputEditText editMessage;
    private MaterialButton btnSend;
    private MaterialButton btnIllustrate;
    private MaterialButton btnNewRun;
    private TextView textLoading;

    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter chatAdapter;

    private Client geminiClient;
    private final List<Content> conversationHistory = new ArrayList<>();
    private GenerateContentConfig geminiConfig;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private String[] races;
    private String[] classes;
    private String[] backgrounds;
    private String[] alignments;
    private String[] runLengths;
    private static final String SCENARIO_PREFS = "scenario_prefs";
    private static final String KEY_SCENARIOS = "saved_scenarios";
    private static final String MAP_PREFS = "map_prefs";
    private static final String KEY_MAP_POINTS = "map_points";

    private static final Pattern MAP_COMMAND_PATTERN = Pattern.compile(
            "\\[MAP:(CREATE_LOCATION|CREATE_CHARACTER|MOVE|REMOVE)"
                    + "\\s+name=\"([^\"]+)\"(?:\\s+x=(\\d+)\\s+y=(\\d+))?\\]");

    private static final Pattern PARTY_RESPONSE_PATTERN = Pattern.compile(
            "\\[([^\\]]+)\\]:\\s*(.+?)(?=\\n\\[|\\z)", Pattern.DOTALL);

    private static final Pattern NPC_DIALOGUE_PATTERN = Pattern.compile(
            "\\[NPC:([^\\]]+)\\]:\\s*(.+?)(?=\\n\\n|\\n\\[NPC:|\\z)", Pattern.DOTALL);

    private static final Pattern LEVEL_UP_PATTERN = Pattern.compile(
            "\\[LEVEL_UP:([^\\]]+)\\]");

    private static final Pattern LOOT_DROP_PATTERN = Pattern.compile(
            "\\[LOOT:(?:([^:\\]|]+):)?([^|\\]]+)(?:\\|(\\d+))?\\]");

    private static final Pattern HP_CHANGE_PATTERN = Pattern.compile(
            "\\[HP:(?:([^:\\]]+):)?([+-]?\\d+)\\]");

    private static final Pattern STAMINA_CHANGE_PATTERN = Pattern.compile(
            "\\[STAMINA:(?:([^:\\]]+):)?([+-]?\\d+)\\]");

    private static final Pattern ENCOUNTER_COMPLETE_PATTERN = Pattern.compile(
            "\\[ENCOUNTER_COMPLETE\\]");

    private static final Pattern ENEMY_DEFEATED_PATTERN = Pattern.compile(
            "\\[ENEMY_DEFEATED:(\\d+)\\]");

    private static final Pattern GAME_WIN_PATTERN = Pattern.compile("\\[GAME_WIN\\]");
    private static final Pattern GAME_LOSE_PATTERN = Pattern.compile("\\[GAME_LOSE\\]");

    private static final int REQUEST_GAME_OVER = 9001;
    private boolean gameOver;

    private boolean mapAccessEnabled;
    private MaterialButton btnMapToggle;
    private MaterialButton btnStatus;

    private SoundEffectManager sfx;

    private String selectedCharacterName;
    private JSONObject selectedCharacter;
    private JSONObject selectedScenario;
    private final List<JSONObject> aiPartyMembers = new ArrayList<>();
    private final List<JSONObject> humanPlayers = new ArrayList<>();
    private int currentTurnIndex;
    private final List<String> turnActions = new ArrayList<>();
    private boolean awaitingHumanTurns;
    private boolean pvpMode;
    private boolean pvpHiddenVisibility;
    private int teamCount;
    private final Map<String, Integer> playerTeams = new HashMap<>();
    private TextView textTurnIndicator;
    private int activeRunId;
    private int runLevel;
    private final List<JSONObject> inventory = new ArrayList<>();
    private int carryCapacity = 10;
    private int currentHP;
    private int maxHP;
    private int currentStamina;
    private int maxStamina;

    // Per-member stats: name → [currentHP, maxHP, currentStamina, maxStamina]
    private final Map<String, int[]> memberStats = new HashMap<>();
    // Per-member inventories: name → inventory list
    private final Map<String, List<JSONObject>> memberInventories = new HashMap<>();
    // Per-member carry capacity: name → capacity
    private final Map<String, Integer> memberCarryCapacity = new HashMap<>();
    // Loot that no one has picked up yet (shared pool)
    private final List<JSONObject> unclaimedLoot = new ArrayList<>();
    // Dead characters: names of characters who have died (HP reached 0)
    private final Set<String> deadCharacters = new HashSet<>();
    private boolean mainCharacterDead;
    // Exhausted characters: names of characters with 0 stamina (can speak but not act)
    private final Set<String> exhaustedCharacters = new HashSet<>();
    private boolean mainCharacterExhausted;
    // Stamina recovery per turn (from scenario settings)
    private int staminaRecoveryPerTurn;

    // Rulebook
    private String cachedDefaultRulebook;

    // Encounter sequence tracking
    private int currentEncounterIndex;
    private String[] encounterTypes;

    // Adventure stats
    private int enemiesDefeated;
    private int itemsUsed;
    private int lootCollected;
    private int turnsTaken;
    private int totalDamageTaken;

    // Developer mode
    private boolean devMode;

    // Rules Advisor
    private DrawerLayout drawerLayout;
    private RecyclerView recyclerRulesChat;
    private TextInputEditText editRulesMessage;
    private MaterialButton btnRulesSend;
    private MaterialButton btnRulesAdvisor;
    private LinearLayout layoutRulesLoading;
    private final List<ChatMessage> rulesMessages = new ArrayList<>();
    private ChatAdapter rulesAdapter;
    private final List<Content> rulesConversationHistory = new ArrayList<>();
    private GenerateContentConfig rulesGeminiConfig;

    // Radio
    private RadioService radioService;
    private boolean radioServiceBound;
    private final ServiceConnection radioServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            RadioService.RadioBinder radioBinder = (RadioService.RadioBinder) binder;
            radioService = radioBinder.getService();
            radioServiceBound = true;
            updateRadioButtonTint();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            radioServiceBound = false;
            radioService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dungeon_master);

        sfx = new SoundEffectManager(this);

        // Enable Firebase offline persistence so writes queue when offline
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception ignored) {
            // Already enabled from a previous Activity instance
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        races = getResources().getStringArray(R.array.cc_races);
        classes = getResources().getStringArray(R.array.cc_classes);
        backgrounds = getResources().getStringArray(R.array.cc_backgrounds);
        alignments = getResources().getStringArray(R.array.cc_alignments);
        runLengths = getResources().getStringArray(R.array.sc_run_lengths);
        encounterTypes = getResources().getStringArray(R.array.sc_encounter_types);

        devMode = getSharedPreferences("app_settings", MODE_PRIVATE)
                .getBoolean(MainActivity.KEY_DEV_MODE, false);

        bindViews();
        setupRecyclerView();

        btnMapToggle = findViewById(R.id.btn_map_toggle);
        btnMapToggle.setOnClickListener(v -> toggleMapAccess());

        btnStatus = findViewById(R.id.btn_inventory);
        btnStatus.setOnClickListener(v -> showStatusDialog());

        btnSend.setOnClickListener(v -> onSendClicked());
        btnIllustrate.setOnClickListener(v -> onIllustrateClicked());
        btnNewRun.setOnClickListener(v -> onNewRunClicked());

        setupRulesRecyclerView();
        btnRulesAdvisor.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));
        findViewById(R.id.btn_close_rules).setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.END));
        btnRulesSend.setOnClickListener(v -> onRulesSendClicked());

        String apiKey = getGeminiApiKey();
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your_key_here")) {
            addMessage(getString(R.string.dm_no_api_key), ChatMessage.TYPE_DM);
            btnSend.setEnabled(false);
            btnIllustrate.setEnabled(false);
            layoutInput.setVisibility(View.VISIBLE);
            return;
        }

        if (!isNetworkAvailable()) {
            addMessage(getString(R.string.dm_no_network), ChatMessage.TYPE_DM);
            btnSend.setEnabled(false);
            btnIllustrate.setEnabled(false);
            layoutInput.setVisibility(View.VISIBLE);
            return;
        }

        // Initialize the Gemini client off the main thread to avoid ANR
        setLoading(true);
        executor.execute(() -> {
            Client client = Client.builder().apiKey(apiKey).build();
            runOnUiThread(() -> {
                geminiClient = client;
                rulesGeminiConfig = buildRulesAdvisorConfig();
                rulesMessages.add(new ChatMessage(
                        getString(R.string.rules_advisor_welcome), ChatMessage.TYPE_DM));
                rulesAdapter.notifyItemInserted(rulesMessages.size() - 1);
                setLoading(false);
                showCharacterSelectionDialog();
            });
        });
    }

    private void bindViews() {
        cardCharacterInfo = findViewById(R.id.card_character_info);
        textCharacterInfo = findViewById(R.id.text_character_info);
        recyclerChat = findViewById(R.id.recycler_chat);
        layoutLoading = findViewById(R.id.layout_loading);
        layoutInput = findViewById(R.id.layout_input);
        editMessage = findViewById(R.id.edit_message);
        btnSend = findViewById(R.id.btn_send);
        btnIllustrate = findViewById(R.id.btn_illustrate);
        btnNewRun = findViewById(R.id.btn_new_run);
        textLoading = findViewById(R.id.text_loading);
        textTurnIndicator = findViewById(R.id.text_turn_indicator);

        // Rules Advisor views
        drawerLayout = findViewById(R.id.drawer_layout);
        recyclerRulesChat = findViewById(R.id.recycler_rules_chat);
        editRulesMessage = findViewById(R.id.edit_rules_message);
        btnRulesSend = findViewById(R.id.btn_rules_send);
        btnRulesAdvisor = findViewById(R.id.btn_rules_advisor);
        layoutRulesLoading = findViewById(R.id.layout_rules_loading);
    }

    private void setupRecyclerView() {
        File imageDir = new File(getFilesDir(), "dm_images");
        chatAdapter = new ChatAdapter(messages, imageDir, this::showFullscreenImage);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerChat.setLayoutManager(layoutManager);
        recyclerChat.setAdapter(chatAdapter);
    }

    // ========== API Key Resolution ==========

    private String getGeminiApiKey() {
        SharedPreferences apiPrefs = getSharedPreferences(MainActivity.API_KEY_PREFS, MODE_PRIVATE);
        String savedKey = apiPrefs.getString(MainActivity.KEY_GEMINI_API, "");
        if (!savedKey.isEmpty()) return savedKey;
        return BuildConfig.GEMINI_API_KEY;
    }

    private String getImageApiKey() {
        SharedPreferences apiPrefs = getSharedPreferences(MainActivity.API_KEY_PREFS, MODE_PRIVATE);
        String savedKey = apiPrefs.getString(MainActivity.KEY_IMAGE_API, "");
        if (!savedKey.isEmpty()) return savedKey;
        return BuildConfig.HF_API_KEY;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private boolean isMapLoaded() {
        SharedPreferences mapPrefs = getSharedPreferences(MAP_PREFS, MODE_PRIVATE);
        return mapPrefs.getString("map_uri", null) != null;
    }

    // ========== Character Selection ==========

    private void showCharacterSelectionDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String existing = prefs.getString(KEY_CHARACTERS, "[]");

        try {
            JSONArray characters = new JSONArray(existing);
            if (characters.length() == 0) {
                Toast.makeText(this, R.string.dm_no_characters, Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            List<String> labels = new ArrayList<>();
            for (int i = 0; i < characters.length(); i++) {
                JSONObject c = characters.getJSONObject(i);
                String label = c.getString("name") + " (Lvl " + c.getInt("level") + " "
                        + races[c.getInt("race")] + " "
                        + classes[c.getInt("class")] + ")";
                labels.add(label);
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.dm_select_character)
                    .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                        try {
                            onCharacterSelected(characters.getJSONObject(which));
                        } catch (JSONException e) {
                            Toast.makeText(this, R.string.dm_error, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .show();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.dm_no_characters, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void onCharacterSelected(JSONObject character) throws JSONException {
        String name = character.getString("name");
        selectedCharacterName = name;
        selectedCharacter = character;

        // After character is selected, show scenario selection
        showScenarioSelectionDialog();
    }

    private void showScenarioSelectionDialog() {
        SharedPreferences prefs = getSharedPreferences(SCENARIO_PREFS, MODE_PRIVATE);
        String existing = prefs.getString(KEY_SCENARIOS, "[]");

        try {
            JSONArray scenarios = new JSONArray(existing);

            // Build list with "No Scenario" as the first option
            List<String> labels = new ArrayList<>();
            labels.add(getString(R.string.dm_no_scenario));
            for (int i = 0; i < scenarios.length(); i++) {
                JSONObject s = scenarios.getJSONObject(i);
                String label = s.getString("name");
                String timePeriod = s.optString("timePeriod", "");
                if (!timePeriod.isEmpty()) {
                    label += " (" + timePeriod + ")";
                }
                labels.add(label);
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.dm_select_scenario)
                    .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                        try {
                            if (which == 0) {
                                // No scenario selected
                                selectedScenario = null;
                            } else {
                                selectedScenario = scenarios.getJSONObject(which - 1);
                            }
                            onScenarioSelected();
                        } catch (JSONException e) {
                            Toast.makeText(this, R.string.dm_error, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .show();
        } catch (JSONException e) {
            // If scenarios can't be loaded, proceed without one
            selectedScenario = null;
            onScenarioSelected();
        }
    }

    private void onScenarioSelected() {
        showPartyMemberSelectionDialog();
    }

    private void showPartyMemberSelectionDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String existing = prefs.getString(KEY_CHARACTERS, "[]");

        try {
            JSONArray characters = new JSONArray(existing);
            List<JSONObject> available = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            for (int i = 0; i < characters.length(); i++) {
                JSONObject c = characters.getJSONObject(i);
                if (!c.getString("name").equals(selectedCharacterName)) {
                    available.add(c);
                    String label = c.getString("name") + " (Lvl " + c.getInt("level") + " "
                            + races[c.getInt("race")] + " "
                            + classes[c.getInt("class")] + ")";
                    labels.add(label);
                }
            }

            if (available.isEmpty()) {
                aiPartyMembers.clear();
                humanPlayers.clear();
                try {
                    onSetupComplete();
                } catch (JSONException e) {
                    Toast.makeText(this, R.string.dm_error, Toast.LENGTH_SHORT).show();
                }
                return;
            }

            boolean[] checked = new boolean[available.size()];

            new AlertDialog.Builder(this)
                    .setTitle(R.string.dm_select_party)
                    .setMultiChoiceItems(labels.toArray(new String[0]), checked,
                            (dialog, which, isChecked) -> checked[which] = isChecked)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        aiPartyMembers.clear();
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) {
                                aiPartyMembers.add(available.get(i));
                            }
                        }
                        showHumanPlayerSelectionDialog();
                    })
                    .setNeutralButton(R.string.dm_no_party, (dialog, which) -> {
                        aiPartyMembers.clear();
                        humanPlayers.clear();
                        try {
                            onSetupComplete();
                        } catch (JSONException e) {
                            Toast.makeText(this, R.string.dm_error, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .show();
        } catch (JSONException e) {
            aiPartyMembers.clear();
            humanPlayers.clear();
            try {
                onSetupComplete();
            } catch (JSONException ex) {
                Toast.makeText(this, R.string.dm_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showHumanPlayerSelectionDialog() {
        if (aiPartyMembers.isEmpty()) {
            humanPlayers.clear();
            showGameModeSelectionDialog();
            return;
        }

        List<String> labels = new ArrayList<>();
        for (JSONObject member : aiPartyMembers) {
            try {
                String label = member.getString("name") + " ("
                        + races[member.getInt("race")] + " "
                        + classes[member.getInt("class")] + ")";
                labels.add(label);
            } catch (JSONException ignored) {
            }
        }

        boolean[] checked = new boolean[aiPartyMembers.size()];

        new AlertDialog.Builder(this)
                .setTitle(R.string.dm_select_human_players)
                .setMultiChoiceItems(labels.toArray(new String[0]), checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    humanPlayers.clear();
                    List<JSONObject> stillAi = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            humanPlayers.add(aiPartyMembers.get(i));
                        } else {
                            stillAi.add(aiPartyMembers.get(i));
                        }
                    }
                    aiPartyMembers.clear();
                    aiPartyMembers.addAll(stillAi);
                    showGameModeSelectionDialog();
                })
                .setNeutralButton(R.string.dm_all_ai, (dialog, which) -> {
                    humanPlayers.clear();
                    showGameModeSelectionDialog();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showGameModeSelectionDialog() {
        if (humanPlayers.isEmpty()) {
            pvpMode = false;
            pvpHiddenVisibility = false;
            teamCount = 0;
            playerTeams.clear();
            try {
                onSetupComplete();
            } catch (JSONException e) {
                Toast.makeText(this, R.string.dm_error, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String[] options = {
                getString(R.string.dm_pvp_cooperative),
                getString(R.string.dm_pvp_competitive)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.dm_pvp_select_mode)
                .setItems(options, (dialog, which) -> {
                    if (which == 1) {
                        pvpMode = true;
                        showVisibilitySelectionDialog();
                    } else {
                        pvpMode = false;
                        pvpHiddenVisibility = false;
                        teamCount = 0;
                        playerTeams.clear();
                        try {
                            onSetupComplete();
                        } catch (JSONException e) {
                            Toast.makeText(this, R.string.dm_error, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showVisibilitySelectionDialog() {
        String[] options = {
                getString(R.string.dm_pvp_visibility_open),
                getString(R.string.dm_pvp_visibility_hidden)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.dm_pvp_select_visibility)
                .setItems(options, (dialog, which) -> {
                    pvpHiddenVisibility = (which == 1);
                    showTeamSetupDialog();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showTeamSetupDialog() {
        // Need at least 2 human players (including main) for teams
        int totalHumans = 1 + humanPlayers.size();
        if (totalHumans < 2) {
            teamCount = 0;
            playerTeams.clear();
            try {
                onSetupComplete();
            } catch (JSONException e) {
                Toast.makeText(this, R.string.dm_error, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String[] options = {
                getString(R.string.dm_pvp_free_for_all),
                getString(R.string.dm_pvp_teams)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.dm_pvp_team_setup)
                .setItems(options, (dialog, which) -> {
                    if (which == 1) {
                        showTeamCountDialog();
                    } else {
                        teamCount = 0;
                        playerTeams.clear();
                        try {
                            onSetupComplete();
                        } catch (JSONException e) {
                            Toast.makeText(this, R.string.dm_error, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showTeamCountDialog() {
        int totalHumans = 1 + humanPlayers.size();
        int maxTeams = Math.min(4, totalHumans);

        String[] options = new String[maxTeams - 1];
        for (int i = 0; i < options.length; i++) {
            options[i] = getString(R.string.dm_pvp_team_label, i + 2);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dm_pvp_team_count)
                .setItems(options, (dialog, which) -> {
                    teamCount = which + 2;
                    showTeamAssignmentDialog();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showTeamAssignmentDialog() {
        // Collect all human player names
        List<String> playerNames = new ArrayList<>();
        playerNames.add(selectedCharacterName);
        for (JSONObject member : humanPlayers) {
            try {
                playerNames.add(member.getString("name"));
            } catch (JSONException ignored) {
            }
        }

        // Build team labels for spinner
        String[] teamLabels = new String[teamCount];
        for (int i = 0; i < teamCount; i++) {
            teamLabels[i] = getString(R.string.dm_pvp_team_label, i + 1);
        }

        // Build custom view with ScrollView > LinearLayout > rows
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);
        scrollView.addView(container);

        List<Spinner> spinners = new ArrayList<>();

        // Add Randomize button at the top
        MaterialButton randomizeButton = new MaterialButton(this);
        randomizeButton.setText(R.string.dm_pvp_randomize_teams);
        randomizeButton.setIconResource(android.R.drawable.ic_menu_sort_by_size);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.bottomMargin = pad;
        btnParams.gravity = Gravity.CENTER_HORIZONTAL;
        randomizeButton.setLayoutParams(btnParams);
        randomizeButton.setOnClickListener(v -> randomizeTeamSpinners(spinners, playerNames.size()));
        container.addView(randomizeButton);

        for (int i = 0; i < playerNames.size(); i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
            row.setLayoutParams(rowParams);

            TextView nameLabel = new TextView(this);
            nameLabel.setText(playerNames.get(i));
            nameLabel.setTextSize(16);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nameLabel.setLayoutParams(nameParams);
            row.addView(nameLabel);

            Spinner spinner = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, teamLabels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            // Default: round-robin assignment
            spinner.setSelection(i % teamCount);
            row.addView(spinner);
            spinners.add(spinner);

            container.addView(row);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dm_pvp_assign_teams)
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    playerTeams.clear();
                    for (int i = 0; i < playerNames.size(); i++) {
                        playerTeams.put(playerNames.get(i),
                                spinners.get(i).getSelectedItemPosition() + 1);
                    }
                    try {
                        onSetupComplete();
                    } catch (JSONException e) {
                        Toast.makeText(this, R.string.dm_error, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void randomizeTeamSpinners(List<Spinner> spinners, int playerCount) {
        // Build a list of team indices guaranteeing each team has at least one player
        List<Integer> assignments = new ArrayList<>();
        for (int t = 0; t < teamCount; t++) {
            assignments.add(t);
        }
        // Fill remaining slots randomly
        for (int i = teamCount; i < playerCount; i++) {
            assignments.add((int) (Math.random() * teamCount));
        }
        Collections.shuffle(assignments);
        for (int i = 0; i < spinners.size(); i++) {
            spinners.get(i).setSelection(assignments.get(i));
        }
    }

    private int getTeamBgColor(int team) {
        switch (team) {
            case 1: return R.color.team_1_bg;
            case 2: return R.color.team_2_bg;
            case 3: return R.color.team_3_bg;
            case 4: return R.color.team_4_bg;
            default: return R.color.pvp_turn_bg;
        }
    }

    private int getTeamTextColor(int team) {
        switch (team) {
            case 1: return R.color.team_1_text;
            case 2: return R.color.team_2_text;
            case 3: return R.color.team_3_text;
            case 4: return R.color.team_4_text;
            default: return R.color.pvp_turn_text;
        }
    }

    private void onSetupComplete() throws JSONException {
        // Read stamina recovery per turn from scenario
        staminaRecoveryPerTurn = selectedScenario != null
                ? selectedScenario.optInt("staminaRecoveryPerTurn", 0) : 0;

        // Build system prompt and config
        String systemPrompt = buildSystemPrompt(selectedCharacter);
        geminiConfig = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                .build();

        // Rebuild rules advisor config with character context
        rulesGeminiConfig = buildRulesAdvisorConfig();

        // Migrate legacy chat data (pre-run system) into run 1
        migrateLegacyChat();

        // Check for existing runs
        JSONArray runs = getRunList();
        if (runs.length() == 0) {
            // No runs yet — start the first one
            setupCharacterUI();
            createAndLoadNewRun();
        } else {
            // Load the last active run
            SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
            activeRunId = prefs.getInt(KEY_ACTIVE_RUN + selectedCharacterName, runs.optInt(0, 1));
            setupCharacterUI();
            loadRun(activeRunId);
        }
    }

    private void setupCharacterUI() {
        try {
            String name = selectedCharacter.getString("name");
            int level = selectedCharacter.getInt("level");
            String race = races[selectedCharacter.getInt("race")];
            String charClass = classes[selectedCharacter.getInt("class")];

            updateCharacterInfoLabel(name, level, race, charClass);
            cardCharacterInfo.setVisibility(View.VISIBLE);
            cardCharacterInfo.setOnClickListener(v -> showRunSelectionDialog());
            layoutInput.setVisibility(View.VISIBLE);

            // Enable map access by default if a map is loaded
            enableMapByDefault();
        } catch (JSONException ignored) {
        }
    }

    private void enableMapByDefault() {
        SharedPreferences mapPrefs = getSharedPreferences(MAP_PREFS, MODE_PRIVATE);
        String mapUri = mapPrefs.getString("map_uri", null);
        if (mapUri != null && !mapAccessEnabled) {
            mapAccessEnabled = true;
            btnMapToggle.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF4CAF50));
            btnMapToggle.setIconTint(
                    android.content.res.ColorStateList.valueOf(0xFFFFFFFF));

            // Rebuild system prompt with map section included
            try {
                String systemPrompt = buildSystemPrompt(selectedCharacter);
                geminiConfig = GenerateContentConfig.builder()
                        .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                        .build();
            } catch (JSONException ignored) {
            }
        }
    }

    private void updateCharacterInfoLabel(String name, int level, String race, String charClass) {
        int displayLevel = runLevel > 0 ? runLevel : level;
        String infoLabel = getString(R.string.dm_character_label, name, displayLevel, race, charClass)
                + " \u2022 Run " + activeRunId;
        if (mainCharacterDead) {
            infoLabel += " \u2022 DEAD";
        } else if (mainCharacterExhausted) {
            infoLabel += " \u2022 EXHAUSTED";
        }
        if (!humanPlayers.isEmpty()) {
            infoLabel += " \u2022 +" + humanPlayers.size() + " human";
        }
        if (!aiPartyMembers.isEmpty()) {
            infoLabel += " \u2022 +" + aiPartyMembers.size() + " AI";
        }
        if (pvpMode) {
            if (teamCount > 0) {
                infoLabel += " \u2022 PvP (" + teamCount + " teams)";
            } else {
                infoLabel += " \u2022 PvP FFA";
            }
            if (pvpHiddenVisibility) {
                infoLabel += " (Hidden)";
            }
        }
        textCharacterInfo.setText(infoLabel);
    }

    private String buildSystemPrompt(JSONObject character) throws JSONException {
        String name = character.getString("name");
        String race = races[character.getInt("race")];
        String charClass = classes[character.getInt("class")];
        int level = runLevel > 0 ? runLevel : character.getInt("level");
        String background = backgrounds[character.getInt("background")];
        String alignment = alignments[character.getInt("alignment")];

        int str = character.getInt("str");
        int dex = character.getInt("dex");
        int con = character.getInt("con");
        int intStat = character.getInt("int");
        int wis = character.getInt("wis");
        int cha = character.getInt("cha");

        int hp = character.getInt("hp");
        int ac = character.getInt("ac");
        int speed = character.getInt("speed");
        int initiative = character.getInt("initiative");

        String notes = character.optString("notes", "");

        String adventureType;
        if (pvpMode && teamCount > 0) {
            adventureType = "a competitive PvP team-based multiplayer adventure where human players compete in "
                    + teamCount + " teams";
        } else if (pvpMode) {
            adventureType = "a competitive PvP multiplayer adventure where human players compete against each other";
        } else if (!humanPlayers.isEmpty()) {
            adventureType = "a multiplayer adventure with multiple human players sharing the device";
        } else if (!aiPartyMembers.isEmpty()) {
            adventureType = "an adventure with a party";
        } else {
            adventureType = "a solo adventure";
        }
        return "You are an experienced and creative Dungeons & Dragons 5th Edition Dungeon Master. "
                + "You are running " + adventureType + " for a player with the following character:\n\n"
                + "CHARACTER SHEET:\n"
                + "Name: " + name + "\n"
                + "Race: " + race + "\n"
                + "Class: " + charClass + "\n"
                + "Level: " + level + "\n"
                + "Background: " + background + "\n"
                + "Alignment: " + alignment + "\n\n"
                + "ABILITY SCORES:\n"
                + "STR: " + str + " (" + modString(str) + ")\n"
                + "DEX: " + dex + " (" + modString(dex) + ")\n"
                + "CON: " + con + " (" + modString(con) + ")\n"
                + "INT: " + intStat + " (" + modString(intStat) + ")\n"
                + "WIS: " + wis + " (" + modString(wis) + ")\n"
                + "CHA: " + cha + " (" + modString(cha) + ")\n"
                + buildCustomAbilitiesSection(character)
                + "\nCOMBAT STATS:\n"
                + "HP: " + hp + "\n"
                + "AC: " + ac + "\n"
                + "Speed: " + speed + " ft\n"
                + "Initiative: " + modString(initiative) + "\n"
                + (notes.isEmpty() ? "" : "\nNOTES: " + notes + "\n")
                + "\nRULES FOR THE DM:\n"
                + "- Reference the character's actual stats when determining outcomes.\n"
                + "- Consult the RULEBOOK REFERENCE section below for detailed game mechanics.\n"
                + "- When an action requires a check, roll dice using the rulebook's mechanics "
                + "(d20 + relevant modifier). Show the roll and modifier in parentheses.\n"
                + "- Present results in engaging narrative form with dice roll details.\n"
                + "- Drive an engaging plot with encounters, puzzles, NPCs, and story hooks.\n"
                + "- Keep responses concise (2-4 short paragraphs max).\n"
                + "- Always end with a clear prompt for the player's next action.\n"
                + "- HP and stamina are tracked automatically via tags (see HP & STAMINA TRACKING section below).\n"
                + "- Use the character's class abilities, race traits, and background appropriately.\n"
                + "- When any NPC or enemy speaks, format their dialogue as [NPC:Name]: followed by their speech.\n"
                + "  Example: [NPC:Guard Captain]: \"Halt! Who goes there?\"\n"
                + "  Put NPC dialogue on its own line with a blank line before and after it.\n"
                + "  This displays their speech in a separate dialogue box. Keep narration separate from NPC lines.\n"
                + "- LEVEL UP SYSTEM: When the player completes a major milestone (defeating a boss, "
                + "finishing a quest, surviving a dangerous encounter, or making a significant story breakthrough), "
                + "include [LEVEL_UP:" + name + "] in your response to level up the character. "
                + "Narrate the level-up moment dramatically. Only award level-ups for meaningful achievements, "
                + "not every minor encounter. The character is currently level " + level + ".\n"
                + buildPartyMembersSection()
                + buildHumanPlayersSection()
                + buildPvpRulesSection()
                + buildRulebookSection()
                + buildScenarioSection()
                + buildStatsSection()
                + buildInventorySection()
                + buildMapSection();
    }

    private String buildStatsSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nHP & STAMINA TRACKING:\n");
        if (mainCharacterDead) {
            sb.append(selectedCharacterName).append(" — DEAD\n");
        } else if (mainCharacterExhausted) {
            sb.append(selectedCharacterName).append(" — HP: ").append(currentHP).append("/").append(maxHP)
                    .append(", Stamina: 0/").append(maxStamina).append(" [EXHAUSTED]\n");
        } else {
            sb.append(selectedCharacterName).append(" — HP: ").append(currentHP).append("/").append(maxHP)
                    .append(", Stamina: ").append(currentStamina).append("/").append(maxStamina).append("\n");
        }
        for (Map.Entry<String, int[]> entry : memberStats.entrySet()) {
            if (deadCharacters.contains(entry.getKey())) {
                sb.append(entry.getKey()).append(" — DEAD\n");
                continue;
            }
            int[] s = entry.getValue();
            sb.append(entry.getKey()).append(" — HP: ").append(s[0]).append("/").append(s[1])
                    .append(", Stamina: ").append(s[2]).append("/").append(s[3]);
            if (exhaustedCharacters.contains(entry.getKey())) {
                sb.append(" [EXHAUSTED]");
            }
            sb.append("\n");
        }
        sb.append("- To apply HP changes to a specific character, use [HP:CharName:±N] (e.g. [HP:Gandalf:-10]).\n");
        sb.append("- To apply HP changes to the main character (").append(selectedCharacterName).append("), use [HP:±N] (no name needed).\n");
        sb.append("- Same format for stamina: [STAMINA:CharName:±N] for party members, [STAMINA:±N] for the main character.\n");
        sb.append("- Scale damage/healing to the character's max HP. A minor scratch might be -2, a critical hit -15.\n");
        sb.append("- Scale stamina costs to the action's intensity. A short sprint might be -5, an exhausting battle -20.\n");
        sb.append("- You may include multiple tags in one response if multiple events happen.\n");
        sb.append("- If HP reaches 0, the character DIES. Narrate the death dramatically. Dead characters cannot act, speak, or be targeted.\n");
        sb.append("- Do NOT apply HP or stamina changes to dead characters. They are permanently removed from play.\n");
        sb.append("- If stamina reaches 0, the character is EXHAUSTED. Exhausted characters can only speak — ");
        sb.append("they cannot perform physical actions, cast spells, attack, or move. ");
        sb.append("If an exhausted character attempts a physical action, refuse it and remind them they can only talk.\n");
        if (staminaRecoveryPerTurn > 0) {
            sb.append("- Each turn, all living non-dead characters recover ").append(staminaRecoveryPerTurn)
                    .append(" stamina automatically.\n");
        }
        return sb.toString();
    }

    private String buildInventorySection() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nINVENTORY SYSTEM:\n");
        // Main character inventory
        sb.append(selectedCharacterName).append("'s inventory (capacity ").append(carryCapacity).append("):\n");
        if (inventory.isEmpty()) {
            sb.append("  empty\n");
        } else {
            sb.append("  ").append(inventory.size()).append("/").append(carryCapacity).append(":\n");
            for (JSONObject item : inventory) {
                String name = getItemName(item);
                String desc = getItemDescription(item);
                int dur = getItemDurability(item);
                int maxDur = getItemMaxDurability(item);
                sb.append("  - ").append(name).append(" [").append(dur).append("/").append(maxDur).append(" durability]");
                if (!desc.isEmpty()) sb.append(" — ").append(desc);
                sb.append("\n");
            }
        }
        // Party member inventories
        for (Map.Entry<String, List<JSONObject>> entry : memberInventories.entrySet()) {
            String mName = entry.getKey();
            List<JSONObject> mInv = entry.getValue();
            int mCap = memberCarryCapacity.getOrDefault(mName, 10);
            sb.append(mName).append("'s inventory (capacity ").append(mCap).append("):\n");
            if (mInv.isEmpty()) {
                sb.append("  empty\n");
            } else {
                sb.append("  ").append(mInv.size()).append("/").append(mCap).append(":\n");
                for (JSONObject item : mInv) {
                    int dur = getItemDurability(item);
                    int maxDur = getItemMaxDurability(item);
                    sb.append("  - ").append(getItemName(item)).append(" [").append(dur).append("/").append(maxDur).append(" durability]");
                    String desc = getItemDescription(item);
                    if (!desc.isEmpty()) sb.append(" — ").append(desc);
                    sb.append("\n");
                }
            }
        }
        sb.append("- Each item has a DURABILITY value. Assign durability when dropping loot using a pipe: [LOOT:ItemName|durability].\n");
        sb.append("  Examples: [LOOT:Iron Sword|80], [LOOT:Gandalf:Magic Staff|95].\n");
        sb.append("  Choose durability based on the item's condition: new/pristine items 90-100, used/worn 50-80, damaged/old 10-40.\n");
        sb.append("- To give loot to a specific character, use [LOOT:CharName:ItemName|durability].\n");
        sb.append("- To give loot to the main character (").append(selectedCharacterName).append("), use [LOOT:ItemName|durability].\n");
        sb.append("- You may drop multiple loot items in one response using multiple [LOOT:...] tags.\n");
        sb.append("- Only drop items for tangible objects the character actually acquires, not abstract things.\n");
        sb.append("- Award loot naturally as part of the adventure (defeating enemies, exploring, purchasing, etc.).\n");
        sb.append("\nENEMY TRACKING:\n");
        sb.append("- When the party defeats enemies, include [ENEMY_DEFEATED:count] in your response ");
        sb.append("(e.g. [ENEMY_DEFEATED:3] if 3 enemies were slain).\n");
        sb.append("- Only count enemies that are actually killed or permanently neutralized, not fled or incapacitated.\n");
        return sb.toString();
    }

    private void loadDefaultRulebook() {
        if (cachedDefaultRulebook != null) return;
        try (InputStream is = getAssets().open("dnd_5e_srd_summary.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            cachedDefaultRulebook = sb.toString();
        } catch (IOException e) {
            cachedDefaultRulebook = "";
        }
    }

    private String buildRulebookSection() {
        String content;
        if (selectedScenario != null) {
            String custom = selectedScenario.optString("rulebookText", "");
            if (!custom.isEmpty()) {
                content = custom;
            } else {
                loadDefaultRulebook();
                content = cachedDefaultRulebook;
            }
        } else {
            loadDefaultRulebook();
            content = cachedDefaultRulebook;
        }
        if (content == null || content.isEmpty()) return "";
        return "\nRULEBOOK REFERENCE (use these rules for all game mechanics):\n"
                + content + "\n";
    }

    private String buildScenarioSection() {
        if (selectedScenario == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\nSCENARIO SETTING:\n");

        String timePeriod = selectedScenario.optString("timePeriod", "");
        if (!timePeriod.isEmpty()) {
            sb.append("Time Period: ").append(timePeriod).append("\n");
        }

        String setting = selectedScenario.optString("setting", "");
        if (!setting.isEmpty()) {
            sb.append("Setting: ").append(setting).append("\n");
        }

        String characters = selectedScenario.optString("characters", "");
        if (!characters.isEmpty()) {
            sb.append("Key Characters/NPCs: ").append(characters).append("\n");
        }

        String plotHook = selectedScenario.optString("plotHook", "");
        if (!plotHook.isEmpty()) {
            sb.append("Plot Hook: ").append(plotHook).append("\n");
        }

        int runLengthIndex = selectedScenario.optInt("runLength", 0);
        if (runLengthIndex >= 0 && runLengthIndex < runLengths.length) {
            sb.append("Run Length: ").append(runLengths[runLengthIndex]).append("\n");
        }

        int npcCount = selectedScenario.optInt("npcCount", 0);
        if (npcCount > 0) {
            sb.append("Target NPC Count: ").append(npcCount).append("\n");
        }

        int locationCount = selectedScenario.optInt("locationCount", 0);
        if (locationCount > 0) {
            sb.append("Target Location Count: ").append(locationCount).append("\n");
        }

        sb.append("- Incorporate the scenario setting above into the adventure.\n");
        sb.append("- Use the specified time period, setting, and characters as the foundation for the story.\n");

        if (npcCount > 0) {
            sb.append("- Introduce approximately ").append(npcCount)
                    .append(" NPCs throughout the adventure. Space them out over the course of the run.\n");
        }
        if (locationCount > 0) {
            sb.append("- Feature approximately ").append(locationCount)
                    .append(" distinct locations throughout the adventure. Introduce new locations as the story progresses.\n");
        }
        sb.append("- Pace the adventure according to the run length: ").append(runLengths[runLengthIndex])
                .append(". Don't rush to the climax; build tension gradually and match the story arc to this length.\n");

        String customRules = selectedScenario.optString("customRules", "");
        if (!customRules.isEmpty()) {
            sb.append("\nCUSTOM RULES (you MUST follow these rules throughout the adventure):\n");
            sb.append(customRules).append("\n");
        }

        // Encounter sequence
        JSONArray encounters = selectedScenario.optJSONArray("encounters");
        if (encounters != null && encounters.length() > 0) {
            sb.append("\nENCOUNTER SEQUENCE (you MUST follow this order):\n");
            sb.append("The adventure has ").append(encounters.length())
                    .append(" pre-designed encounters. Run them in strict order.\n");
            for (int i = 0; i < encounters.length(); i++) {
                try {
                    JSONObject enc = encounters.getJSONObject(i);
                    String marker = (i == currentEncounterIndex) ? " <-- CURRENT" :
                            (i < currentEncounterIndex) ? " [DONE]" : "";
                    int typeIdx = enc.optInt("type", 0);
                    String typeName = (typeIdx >= 0 && typeIdx < encounterTypes.length)
                            ? encounterTypes[typeIdx] : "Unknown";
                    sb.append(i + 1).append(". ").append(enc.optString("name", "Unnamed"))
                            .append(" (").append(typeName).append(")").append(marker).append("\n");
                    String desc = enc.optString("description", "");
                    if (!desc.isEmpty()) sb.append("   Description: ").append(desc).append("\n");
                    String enemies = enc.optString("enemies", "");
                    if (!enemies.isEmpty()) sb.append("   Enemies: ").append(enemies).append("\n");
                    String objective = enc.optString("objective", "");
                    if (!objective.isEmpty()) sb.append("   Objective: ").append(objective).append("\n");
                    String notes = enc.optString("notes", "");
                    if (!notes.isEmpty()) sb.append("   DM Notes: ").append(notes).append("\n");
                } catch (JSONException ignored) {
                }
            }
            if (currentEncounterIndex < encounters.length()) {
                sb.append("\nYou are currently on encounter #").append(currentEncounterIndex + 1)
                        .append(". Run it fully before moving on.\n");
                sb.append("When the current encounter is resolved (objective met or players move past it), ")
                        .append("include the tag [ENCOUNTER_COMPLETE] in your response. ")
                        .append("This advances to the next encounter. Only use this tag once per encounter.\n");
                sb.append("After marking an encounter complete, naturally transition to the next one.\n");
            } else {
                sb.append("\nAll encounters have been completed. Wrap up the adventure with a fitting conclusion.\n");
            }
        }

        // Win/Lose condition instructions for the AI
        int winCond = selectedScenario.optInt("winCondition", 0);
        int loseCond = selectedScenario.optInt("loseCondition", 0);

        if (winCond == 1) {
            String customWinText = selectedScenario.optString("customWinText", "");
            sb.append("\nCUSTOM WIN CONDITION:\n");
            sb.append("The scenario uses a custom win condition. ");
            if (!customWinText.isEmpty()) {
                sb.append("Win when: ").append(customWinText).append("\n");
            }
            sb.append("When the win condition is met, include [GAME_WIN] in your response. ");
            sb.append("Only use this tag once when the condition is clearly satisfied.\n");
        }

        if (loseCond == 1) {
            String customLoseText = selectedScenario.optString("customLoseText", "");
            sb.append("\nCUSTOM LOSE CONDITION:\n");
            sb.append("The scenario uses a custom lose condition. ");
            if (!customLoseText.isEmpty()) {
                sb.append("Lose when: ").append(customLoseText).append("\n");
            }
            sb.append("When the lose condition is met, include [GAME_LOSE] in your response. ");
            sb.append("Only use this tag once when the condition is clearly satisfied.\n");
        }

        return sb.toString();
    }

    private String buildOpeningPrompt() {
        StringBuilder sb = new StringBuilder();

        if (selectedScenario == null) {
            sb.append("Begin a new D&D adventure. Introduce the setting and give ")
                    .append(selectedCharacterName)
                    .append(" a compelling opening scene.");
        } else {
            sb.append("Begin a new D&D adventure for ").append(selectedCharacterName).append(".");

            String timePeriod = selectedScenario.optString("timePeriod", "");
            if (!timePeriod.isEmpty()) {
                sb.append(" The adventure takes place during ").append(timePeriod).append(".");
            }

            String setting = selectedScenario.optString("setting", "");
            if (!setting.isEmpty()) {
                sb.append(" The setting is ").append(setting).append(".");
            }

            String characters = selectedScenario.optString("characters", "");
            if (!characters.isEmpty()) {
                sb.append(" Key characters include: ").append(characters).append(".");
            }

            String plotHook = selectedScenario.optString("plotHook", "");
            if (!plotHook.isEmpty()) {
                sb.append(" The story begins with: ").append(plotHook).append(".");
            }

            int runLengthIndex = selectedScenario.optInt("runLength", 0);
            if (runLengthIndex >= 0 && runLengthIndex < runLengths.length) {
                sb.append(" This is a ").append(runLengths[runLengthIndex]).append(" adventure.");
            }

            int npcCount = selectedScenario.optInt("npcCount", 0);
            if (npcCount > 0) {
                sb.append(" The adventure should feature around ").append(npcCount).append(" NPCs total.");
            }

            int locationCount = selectedScenario.optInt("locationCount", 0);
            if (locationCount > 0) {
                sb.append(" The adventure should span around ").append(locationCount).append(" distinct locations.");
            }

            String customRules = selectedScenario.optString("customRules", "");
            if (!customRules.isEmpty()) {
                sb.append(" The following custom rules MUST be followed: ").append(customRules);
            }

            sb.append(" Introduce the setting vividly and give the player a compelling opening scene.");

            // Reference first encounter if encounters exist
            JSONArray encounters = selectedScenario.optJSONArray("encounters");
            if (encounters != null && encounters.length() > 0) {
                try {
                    JSONObject first = encounters.getJSONObject(0);
                    sb.append(" The adventure has ").append(encounters.length())
                            .append(" planned encounters. Begin with the first encounter: \"")
                            .append(first.optString("name", "")).append("\".");
                    String desc = first.optString("description", "");
                    if (!desc.isEmpty()) {
                        sb.append(" ").append(desc);
                    }
                } catch (JSONException ignored) {
                }
            }
        }

        // Include human players in opening
        if (!humanPlayers.isEmpty()) {
            sb.append(" This is a multiplayer session. The human players are: ")
                    .append(selectedCharacterName);
            for (JSONObject member : humanPlayers) {
                try {
                    sb.append(", ").append(member.getString("name")).append(" the ")
                            .append(races[member.getInt("race")]).append(" ")
                            .append(classes[member.getInt("class")]);
                } catch (JSONException ignored) {
                }
            }
            sb.append(". Address all players and give each a chance to act.");

            if (pvpMode && teamCount > 0) {
                sb.append(" This is a team-based PvP session with ").append(teamCount)
                        .append(" teams. Assign each TEAM a competing objective — teammates share the same objective.");
                for (int t = 1; t <= teamCount; t++) {
                    sb.append(" Team ").append(t).append(": ");
                    boolean first = true;
                    for (Map.Entry<String, Integer> entry : playerTeams.entrySet()) {
                        if (entry.getValue() == t) {
                            if (!first) sb.append(", ");
                            sb.append(entry.getKey());
                            first = false;
                        }
                    }
                    sb.append(".");
                }
                if (pvpHiddenVisibility) {
                    sb.append(" HIDDEN MODE: Address each team's objective privately. Teammates may share info, but do not reveal one team's objective to another.");
                } else {
                    sb.append(" OPEN MODE: Objectives can be publicly stated. All actions will be visible to all players.");
                }
            } else if (pvpMode) {
                sb.append(" This is a PvP (Player vs Player) session. Assign each player a different, competing objective.");
                if (pvpHiddenVisibility) {
                    sb.append(" HIDDEN MODE: Address each player's objective privately. Do not reveal one player's objective to another.");
                } else {
                    sb.append(" OPEN MODE: Objectives can be publicly stated. All actions will be visible to all players.");
                }
            }
        }

        // Include AI party members in opening
        if (!aiPartyMembers.isEmpty()) {
            sb.append(humanPlayers.isEmpty()
                    ? " The player's party includes: "
                    : " The party also includes AI-controlled members: ");
            for (int i = 0; i < aiPartyMembers.size(); i++) {
                try {
                    JSONObject member = aiPartyMembers.get(i);
                    if (i > 0) sb.append(", ");
                    sb.append(member.getString("name")).append(" the ")
                            .append(races[member.getInt("race")]).append(" ")
                            .append(classes[member.getInt("class")]);
                } catch (JSONException ignored) {
                }
            }
            sb.append(". Introduce them naturally in the opening scene.");
            sb.append(" Remember: do NOT write their dialogue or specific actions.");
        }

        // When map access is enabled, instruct the AI to populate the map
        if (mapAccessEnabled) {
            int locationTarget = 0;
            int npcTarget = 0;
            if (selectedScenario != null) {
                locationTarget = selectedScenario.optInt("locationCount", 0);
                npcTarget = selectedScenario.optInt("npcCount", 0);
            }

            sb.append(" IMPORTANT: Since map access is enabled, you MUST include MAP commands")
                    .append(" in your opening response to populate the map.");

            if (locationTarget > 0) {
                sb.append(" Create exactly ").append(locationTarget)
                        .append(" locations using [MAP:CREATE_LOCATION name=\"...\" x=N y=N].");
            } else {
                sb.append(" Create several key locations (3-5) using")
                        .append(" [MAP:CREATE_LOCATION name=\"...\" x=N y=N].");
            }

            sb.append(" Place the player's character using")
                    .append(" [MAP:CREATE_CHARACTER name=\"").append(selectedCharacterName)
                    .append("\" x=N y=N].");

            if (!aiPartyMembers.isEmpty()) {
                sb.append(" Place every AI party member on the map too:");
                for (JSONObject member : aiPartyMembers) {
                    try {
                        sb.append(" [MAP:CREATE_CHARACTER name=\"")
                                .append(member.getString("name")).append("\" x=N y=N]");
                    } catch (JSONException ignored) {
                    }
                }
                sb.append(".");
            }

            if (!humanPlayers.isEmpty()) {
                sb.append(" Place every human player on the map too:");
                for (JSONObject member : humanPlayers) {
                    try {
                        sb.append(" [MAP:CREATE_CHARACTER name=\"")
                                .append(member.getString("name")).append("\" x=N y=N]");
                    } catch (JSONException ignored) {
                    }
                }
                sb.append(".");
            }

            if (npcTarget > 0) {
                sb.append(" Also create exactly ").append(npcTarget)
                        .append(" NPCs as characters using [MAP:CREATE_CHARACTER name=\"...\" x=N y=N].");
            } else {
                sb.append(" Also create any relevant NPCs or enemies as characters.");
            }

            sb.append(" Spread all points across the map (use coordinates from 10 to 90).");
        }

        sb.append(" End with a clear prompt for the player's first action.");

        return sb.toString();
    }

    private String modString(int score) {
        int mod = Math.floorDiv(score - 10, 2);
        return (mod >= 0) ? "+" + mod : String.valueOf(mod);
    }

    private String buildCustomAbilitiesSection(JSONObject character) {
        JSONArray customAbilities = character.optJSONArray("customAbilities");
        if (customAbilities == null || customAbilities.length() == 0) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\nCUSTOM ABILITIES:\n");
        for (int i = 0; i < customAbilities.length(); i++) {
            try {
                JSONObject ability = customAbilities.getJSONObject(i);
                String name = ability.getString("name");
                int score = ability.getInt("score");
                sb.append(name.toUpperCase()).append(": ").append(score)
                        .append(" (").append(modString(score)).append(")\n");
            } catch (JSONException ignored) {
            }
        }
        return sb.toString();
    }

    private String buildPartyMembersSection() {
        if (aiPartyMembers.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\nAI PARTY MEMBERS:\n");
        sb.append("The player is accompanied by the following AI-controlled characters. ");
        sb.append("Do NOT write dialogue or specific actions for these characters in your narration. ");
        sb.append("They will respond separately after your narration, speaking in first person ");
        sb.append("and using their own names. You may reference the party generally ");
        sb.append("(e.g., \"your companions follow\") but never put words in their mouths.\n\n");

        for (JSONObject member : aiPartyMembers) {
            try {
                String mName = member.getString("name");
                if (deadCharacters.contains(mName)) {
                    sb.append("- ").append(mName).append(": DEAD (cannot act, speak, or be interacted with)\n");
                    continue;
                }
                String mRace = races[member.getInt("race")];
                String mClass = classes[member.getInt("class")];
                int mLevel = member.getInt("level");
                String mAlignment = alignments[member.getInt("alignment")];
                String mNotes = member.optString("notes", "");

                sb.append("- ").append(mName).append(": Level ").append(mLevel).append(" ")
                        .append(mRace).append(" ").append(mClass)
                        .append(", ").append(mAlignment);
                if (!mNotes.isEmpty()) {
                    sb.append(" \u2014 ").append(mNotes);
                }
                sb.append("\n");
                sb.append("  STR:").append(member.getInt("str"))
                        .append(" DEX:").append(member.getInt("dex"))
                        .append(" CON:").append(member.getInt("con"))
                        .append(" INT:").append(member.getInt("int"))
                        .append(" WIS:").append(member.getInt("wis"))
                        .append(" CHA:").append(member.getInt("cha"))
                        .append(" AC:").append(member.getInt("ac"));
                int[] stats = memberStats.get(mName);
                if (stats != null) {
                    sb.append(" | HP:").append(stats[0]).append("/").append(stats[1])
                            .append(" Stamina:").append(stats[2]).append("/").append(stats[3]);
                } else {
                    sb.append(" | HP:").append(member.getInt("hp"));
                }
                if (exhaustedCharacters.contains(mName)) {
                    sb.append(" [EXHAUSTED - can only speak]");
                }
                sb.append("\n");
            } catch (JSONException ignored) {
            }
        }

        return sb.toString();
    }

    private String buildHumanPlayersSection() {
        if (humanPlayers.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\nHUMAN PLAYERS:\n");
        if (pvpMode && teamCount > 0) {
            sb.append("The following characters are organized into ").append(teamCount)
                    .append(" competing teams (PvP hot-seat). ")
                    .append("Same-team players cooperate; different teams compete. ")
                    .append("Each human player will submit their own action. ")
                    .append("Referee all PvP interactions fairly using dice rolls and character stats.\n\n");

            // Group players by team
            for (int t = 1; t <= teamCount; t++) {
                sb.append("Team ").append(t).append(":\n");
                // Check main character
                Integer mainTeam = playerTeams.get(selectedCharacterName);
                if (mainTeam != null && mainTeam == t) {
                    sb.append("  - ").append(selectedCharacterName);
                    if (mainCharacterDead) {
                        sb.append(": DEAD (cannot act, speak, or be interacted with)");
                    }
                    sb.append("\n");
                }
                for (JSONObject member : humanPlayers) {
                    try {
                        String mName = member.getString("name");
                        Integer mTeam = playerTeams.get(mName);
                        if (mTeam == null || mTeam != t) continue;
                        if (deadCharacters.contains(mName)) {
                            sb.append("  - ").append(mName).append(": DEAD (cannot act, speak, or be interacted with)\n");
                            continue;
                        }
                        String mRace = races[member.getInt("race")];
                        String mClass = classes[member.getInt("class")];
                        int mLevel = member.getInt("level");
                        sb.append("  - ").append(mName).append(": Level ").append(mLevel).append(" ")
                                .append(mRace).append(" ").append(mClass);
                        int[] stats = memberStats.get(mName);
                        if (stats != null) {
                            sb.append(" | HP:").append(stats[0]).append("/").append(stats[1])
                                    .append(" Stamina:").append(stats[2]).append("/").append(stats[3]);
                        }
                        if (exhaustedCharacters.contains(mName)) {
                            sb.append(" [EXHAUSTED - can only speak]");
                        }
                        sb.append("\n");
                    } catch (JSONException ignored) {
                    }
                }
            }
        } else if (pvpMode) {
            sb.append("The following characters are controlled by competing human players (PvP hot-seat). ");
            sb.append("These players are rivals who may fight each other, sabotage each other, or compete for objectives. ");
            sb.append("Each human player will submit their own action. ");
            sb.append("Referee all PvP interactions fairly using dice rolls and character stats.\n\n");
            appendFlatPlayerList(sb);
        } else {
            sb.append("In addition to the primary player, the following characters are controlled by ");
            sb.append("other human players sharing the same device (hot-seat multiplayer). ");
            sb.append("Each human player will submit their own action. ");
            sb.append("Address ALL players in your narration and give each a chance to act.\n\n");
            appendFlatPlayerList(sb);
        }

        return sb.toString();
    }

    private void appendFlatPlayerList(StringBuilder sb) {
        for (JSONObject member : humanPlayers) {
            try {
                String mName = member.getString("name");
                if (deadCharacters.contains(mName)) {
                    sb.append("- ").append(mName).append(": DEAD (cannot act, speak, or be interacted with)\n");
                    continue;
                }
                String mRace = races[member.getInt("race")];
                String mClass = classes[member.getInt("class")];
                int mLevel = member.getInt("level");

                sb.append("- ").append(mName).append(": Level ").append(mLevel).append(" ")
                        .append(mRace).append(" ").append(mClass);
                int[] stats = memberStats.get(mName);
                if (stats != null) {
                    sb.append(" | HP:").append(stats[0]).append("/").append(stats[1])
                            .append(" Stamina:").append(stats[2]).append("/").append(stats[3]);
                }
                if (exhaustedCharacters.contains(mName)) {
                    sb.append(" [EXHAUSTED - can only speak]");
                }
                sb.append("\n");
            } catch (JSONException ignored) {
            }
        }
    }

    private String buildPvpRulesSection() {
        if (!pvpMode) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\nPVP RULES:\n");

        if (teamCount > 0) {
            sb.append("This is a team-based PvP session with ").append(teamCount).append(" teams.\n");
            sb.append("- Same-team players share cooperative objectives; different teams get competing objectives.\n");
            sb.append("- PvP combat is only allowed between different teams. No friendly fire within a team.\n");
            sb.append("- Use [HP:TargetName:-N] to apply damage when players from different teams fight.\n");
            sb.append("- Track each TEAM's progress toward their team objective.\n");
            sb.append("- Be a fair and impartial referee for inter-team conflicts. Use dice rolls and stats to resolve combat.\n");

            // List team compositions
            sb.append("- Team compositions:\n");
            for (int t = 1; t <= teamCount; t++) {
                sb.append("  Team ").append(t).append(": ");
                boolean first = true;
                for (Map.Entry<String, Integer> entry : playerTeams.entrySet()) {
                    if (entry.getValue() == t) {
                        if (!first) sb.append(", ");
                        sb.append(entry.getKey());
                        first = false;
                    }
                }
                sb.append("\n");
            }
        } else {
            sb.append("This is a Player vs Player (PvP) session. The human players are COMPETING against each other.\n");
            sb.append("- Each player has their own individual objective. Assign different, competing objectives.\n");
            sb.append("- PvP combat between players is allowed. Use [HP:TargetName:-N] to apply damage when players fight each other.\n");
            sb.append("- Track individual progress toward each player's objective separately.\n");
            sb.append("- Players may form temporary alliances, betray each other, or compete directly.\n");
            sb.append("- Be a fair and impartial referee for PvP conflicts. Use dice rolls and stats to resolve combat.\n");
        }

        if (pvpHiddenVisibility) {
            sb.append("- HIDDEN VISIBILITY MODE: Each player's actions are secret from the others.\n");
            sb.append("  Narrate outcomes per-player without revealing other players' private actions or strategies.\n");
            sb.append("  Do not reveal what one player did to another unless it directly affects them.\n");
            if (teamCount > 0) {
                sb.append("  Teammates may share information with each other, but not with other teams.\n");
            }
        } else {
            sb.append("- OPEN VISIBILITY MODE: All actions are visible to all players.\n");
            sb.append("  Individual objectives may still be secret, but actions and their outcomes are public.\n");
        }

        return sb.toString();
    }

    // ========== Run Management ==========

    private JSONArray getRunList() {
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String json = prefs.getString(KEY_RUN_LIST + selectedCharacterName, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void saveRunList(JSONArray runs) {
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        prefs.edit().putString(KEY_RUN_LIST + selectedCharacterName, runs.toString()).apply();
    }

    private int getNextRunId() {
        JSONArray runs = getRunList();
        int maxId = 0;
        for (int i = 0; i < runs.length(); i++) {
            maxId = Math.max(maxId, runs.optInt(i, 0));
        }
        return maxId + 1;
    }

    private void migrateLegacyChat() {
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String legacyKey = KEY_CHAT_MESSAGES + selectedCharacterName;
        String legacyData = prefs.getString(legacyKey, null);

        // Only migrate if legacy data exists and no runs exist yet
        if (legacyData != null && !prefs.contains(KEY_RUN_LIST + selectedCharacterName)) {
            String runKey = KEY_CHAT_MESSAGES + selectedCharacterName + "_run_1";
            JSONArray runs = new JSONArray();
            runs.put(1);
            prefs.edit()
                    .putString(runKey, legacyData)
                    .putString(KEY_RUN_LIST + selectedCharacterName, runs.toString())
                    .putInt(KEY_ACTIVE_RUN + selectedCharacterName, 1)
                    .remove(legacyKey)
                    .apply();
        }
    }

    private void createAndLoadNewRun() {
        int newId = getNextRunId();
        JSONArray runs = getRunList();
        runs.put(newId);
        saveRunList(runs);

        activeRunId = newId;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        prefs.edit().putInt(KEY_ACTIVE_RUN + selectedCharacterName, activeRunId).apply();

        // Reset run level to base character level for a new run
        try {
            runLevel = selectedCharacter.getInt("level");
        } catch (JSONException e) {
            runLevel = 1;
        }
        saveRunLevel();

        // Load starting items and carry capacity from the character card
        inventory.clear();
        carryCapacity = selectedCharacter.optInt("carryCapacity", 10);
        JSONArray startingItems = selectedCharacter.optJSONArray("startingItems");
        if (startingItems != null) {
            for (int i = 0; i < startingItems.length() && inventory.size() < carryCapacity; i++) {
                try {
                    JSONObject srcItem = startingItems.getJSONObject(i);
                    inventory.add(createInventoryItem(
                            srcItem.getString("name"),
                            srcItem.optString("description", ""),
                            srcItem.optInt("durability", 100)));
                } catch (JSONException ignored) {
                }
            }
        }
        saveInventory();

        // Clear unclaimed loot for new run
        unclaimedLoot.clear();
        saveUnclaimedLoot();

        // Initialize HP and stamina from character sheet
        maxHP = selectedCharacter.optInt("hp", 20);
        currentHP = maxHP;
        maxStamina = selectedCharacter.optInt("stamina", 100);
        currentStamina = maxStamina;
        saveRunStats();

        // Initialize stats for all party members
        memberStats.clear();
        memberInventories.clear();
        memberCarryCapacity.clear();
        deadCharacters.clear();
        mainCharacterDead = false;
        exhaustedCharacters.clear();
        mainCharacterExhausted = false;
        currentEncounterIndex = 0;
        enemiesDefeated = 0;
        itemsUsed = 0;
        lootCollected = 0;
        turnsTaken = 0;
        totalDamageTaken = 0;
        gameOver = false;
        initMemberStats(aiPartyMembers);
        initMemberStats(humanPlayers);
        saveMemberStats();
        saveMemberInventories();
        saveDeadCharacters();
        saveExhaustedCharacters();

        // Update UI label with new run number
        refreshCharacterInfoLabel();

        // Clear map markers when starting a new run so the DM can populate fresh ones
        if (mapAccessEnabled) {
            SharedPreferences mapPrefs = getSharedPreferences(MAP_PREFS, MODE_PRIVATE);
            mapPrefs.edit().putString(KEY_MAP_POINTS, "[]").apply();
        }

        // Clear display and start fresh
        messages.clear();
        conversationHistory.clear();
        chatAdapter.notifyDataSetChanged();
        resetTurnState();

        sendToGemini(buildOpeningPrompt(), false);
    }

    private void loadRun(int runId) {
        activeRunId = runId;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        prefs.edit().putInt(KEY_ACTIVE_RUN + selectedCharacterName, activeRunId).apply();

        // Try loading from JSON file first, fall back to SharedPreferences
        if (!loadRunFromFile()) {
            loadRunLevel();
            loadInventory();
            loadUnclaimedLoot();
            loadRunStats();
            loadMemberStats();
            loadMemberInventories();
            loadDeadCharacters();
            loadExhaustedCharacters();
        }

        // Update UI label
        refreshCharacterInfoLabel();

        messages.clear();
        conversationHistory.clear();
        chatAdapter.notifyDataSetChanged();
        resetTurnState();

        if (!restoreChat()) {
            // Run exists in list but has no chat data — generate opening
            sendToGemini(buildOpeningPrompt(), false);
        }
    }

    private void onNewRunClicked() {
        // Save current chat before switching
        saveChat();
        saveRunToFile();
        createAndLoadNewRun();
    }

    private void showRunSelectionDialog() {
        JSONArray runs = getRunList();
        if (runs.length() <= 1) return;

        String[] labels = new String[runs.length()];
        for (int i = 0; i < runs.length(); i++) {
            int runId = runs.optInt(i, 0);
            labels[i] = "Run " + runId;
            if (runId == activeRunId) {
                labels[i] += " (current)";
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dm_select_run)
                .setItems(labels, (dialog, which) -> {
                    int selectedRunId = runs.optInt(which, 1);
                    if (selectedRunId != activeRunId) {
                        saveChat();
                        saveRunToFile();
                        loadRun(selectedRunId);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ========== Messaging ==========

    private boolean isHotSeatActive() {
        return !humanPlayers.isEmpty();
    }

    private String getCurrentTurnPlayerName() {
        if (currentTurnIndex == 0) {
            return selectedCharacterName;
        }
        try {
            return humanPlayers.get(currentTurnIndex - 1).getString("name");
        } catch (JSONException e) {
            return "Player " + (currentTurnIndex + 1);
        }
    }

    private void showTurnIndicator() {
        String playerName = getCurrentTurnPlayerName();
        if (pvpMode && teamCount > 0) {
            Integer team = playerTeams.get(playerName);
            int t = team != null ? team : 1;
            textTurnIndicator.setText(getString(R.string.dm_pvp_turn_indicator_team, playerName, t));
            textTurnIndicator.setBackgroundColor(getColor(getTeamBgColor(t)));
            textTurnIndicator.setTextColor(getColor(getTeamTextColor(t)));
            editMessage.setHint(getString(R.string.dm_pvp_turn_indicator_team, playerName, t));
        } else if (pvpMode) {
            textTurnIndicator.setText(getString(R.string.dm_pvp_turn_indicator, playerName));
            textTurnIndicator.setBackgroundColor(getColor(R.color.pvp_turn_bg));
            textTurnIndicator.setTextColor(getColor(R.color.pvp_turn_text));
            editMessage.setHint(getString(R.string.dm_pvp_turn_indicator, playerName));
        } else {
            textTurnIndicator.setText(getString(R.string.dm_turn_indicator, playerName));
            textTurnIndicator.setBackgroundColor(getColor(R.color.human_player_bg));
            textTurnIndicator.setTextColor(getColor(R.color.human_player_text));
            editMessage.setHint(getString(R.string.dm_turn_indicator, playerName));
        }
        textTurnIndicator.setVisibility(View.VISIBLE);
    }

    private void hideTurnIndicator() {
        textTurnIndicator.setVisibility(View.GONE);
        editMessage.setHint(R.string.dm_message_hint);
    }

    private void startHotSeatTurns() {
        awaitingHumanTurns = true;
        currentTurnIndex = 0;
        turnActions.clear();
        btnNewRun.setEnabled(false);

        // Reset message visibility so the primary player sees the full DM narration
        chatAdapter.setHiddenBeforeIndex(-1);
        chatAdapter.notifyDataSetChanged();

        // If the main character is dead, skip their turn and advance
        if (mainCharacterDead) {
            turnActions.add("[DEAD - no action]");
            advanceToNextHumanTurn();
        } else {
            showTurnIndicator();
        }
    }

    private void showPassPhoneDialog(String nextPlayerName) {
        if (pvpMode && pvpHiddenVisibility) {
            chatAdapter.setHiddenBeforeIndex(messages.size());
            chatAdapter.notifyDataSetChanged();
        }

        String message = (pvpMode && pvpHiddenVisibility)
                ? getString(R.string.dm_pvp_pass_phone_message, nextPlayerName)
                : getString(R.string.dm_pass_phone_message, nextPlayerName);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dm_pass_phone_title)
                .setMessage(message)
                .setPositiveButton(R.string.dm_pass_phone_ready, (dialog, which) -> {
                    showTurnIndicator();
                    if (pvpMode && pvpHiddenVisibility) {
                        recyclerChat.scrollToPosition(messages.size() - 1);
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void advanceToNextHumanTurn() {
        currentTurnIndex++;
        // Skip dead players automatically
        while (currentTurnIndex <= humanPlayers.size()) {
            String nextPlayer = getCurrentTurnPlayerName();
            boolean isDead = (currentTurnIndex == 0 && mainCharacterDead)
                    || (currentTurnIndex > 0 && deadCharacters.contains(nextPlayer));
            if (!isDead) break;
            turnActions.add("[DEAD - no action]");
            currentTurnIndex++;
        }
        if (currentTurnIndex > humanPlayers.size()) {
            onAllHumanTurnsComplete();
        } else {
            String nextPlayerName = getCurrentTurnPlayerName();
            hideTurnIndicator();
            showPassPhoneDialog(nextPlayerName);
        }
    }

    private void onAllHumanTurnsComplete() {
        awaitingHumanTurns = false;
        hideTurnIndicator();
        btnIllustrate.setEnabled(true);
        btnNewRun.setEnabled(true);

        // Compile all actions into one message for Gemini
        StringBuilder compiled = new StringBuilder();
        compiled.append("[PLAYER ACTIONS]\n");

        if (pvpMode && teamCount > 0) {
            Integer mainTeam = playerTeams.get(selectedCharacterName);
            String teamLabel = mainTeam != null ? " (Team " + mainTeam + ")" : "";
            compiled.append(selectedCharacterName).append(teamLabel).append(": ")
                    .append(turnActions.get(0)).append("\n");
            for (int i = 0; i < humanPlayers.size(); i++) {
                try {
                    String name = humanPlayers.get(i).getString("name");
                    Integer t = playerTeams.get(name);
                    String tLabel = t != null ? " (Team " + t + ")" : "";
                    compiled.append(name).append(tLabel).append(": ")
                            .append(turnActions.get(i + 1)).append("\n");
                } catch (JSONException ignored) {
                }
            }
        } else {
            compiled.append(selectedCharacterName).append(": ").append(turnActions.get(0)).append("\n");
            for (int i = 0; i < humanPlayers.size(); i++) {
                try {
                    String name = humanPlayers.get(i).getString("name");
                    compiled.append(name).append(": ").append(turnActions.get(i + 1)).append("\n");
                } catch (JSONException ignored) {
                }
            }
        }

        if (pvpMode && pvpHiddenVisibility) {
            compiled.append("\n[PvP HIDDEN MODE: Narrate outcomes for each player separately. ")
                    .append("Do not reveal one player's secret actions or strategies to another. ")
                    .append("Only reveal information that the other player would naturally observe.]");
        }

        sendToGemini(compiled.toString(), true);
    }

    private void resetTurnState() {
        awaitingHumanTurns = false;
        currentTurnIndex = 0;
        turnActions.clear();
        hideTurnIndicator();
        btnIllustrate.setEnabled(true);
        btnNewRun.setEnabled(true);
    }

    private void onSendClicked() {
        String text = editMessage.getText() != null ? editMessage.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        if (geminiClient == null) {
            Toast.makeText(this, R.string.dm_no_api_key, Toast.LENGTH_LONG).show();
            return;
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(this, R.string.dm_no_network, Toast.LENGTH_LONG).show();
            return;
        }

        // If map access is enabled, verify the map is still loaded
        if (mapAccessEnabled && !isMapLoaded()) {
            mapAccessEnabled = false;
            btnMapToggle.setBackgroundTintList(null);
            btnMapToggle.setIconTint(null);
            Toast.makeText(this, R.string.dm_map_no_longer_loaded, Toast.LENGTH_LONG).show();

            try {
                String systemPrompt = buildSystemPrompt(selectedCharacter);
                geminiConfig = GenerateContentConfig.builder()
                        .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                        .build();
            } catch (JSONException ignored) {
            }
        }

        editMessage.setText("");

        if (isHotSeatActive() && awaitingHumanTurns) {
            // Hot-seat multiplayer flow
            if (currentTurnIndex == 0) {
                if (mainCharacterDead) {
                    Toast.makeText(this, R.string.dm_dead_cannot_act, Toast.LENGTH_SHORT).show();
                    turnActions.add("[DEAD - no action]");
                    advanceToNextHumanTurn();
                    return;
                }
                if (mainCharacterExhausted) {
                    Toast.makeText(this, R.string.dm_exhausted_cannot_act,
                            Toast.LENGTH_SHORT).show();
                }
                // Primary player's action
                turnsTaken++;
                addMessage(text, ChatMessage.TYPE_USER);
                String action = mainCharacterExhausted
                        ? "[EXHAUSTED - can only speak] " + text : text;
                turnActions.add(action);
                advanceToNextHumanTurn();
            } else {
                String playerName = getCurrentTurnPlayerName();
                if (deadCharacters.contains(playerName)) {
                    Toast.makeText(this, getString(R.string.dm_dead_skip_turn, playerName),
                            Toast.LENGTH_SHORT).show();
                    turnActions.add("[DEAD - no action]");
                    advanceToNextHumanTurn();
                    return;
                }
                if (exhaustedCharacters.contains(playerName)) {
                    Toast.makeText(this,
                            getString(R.string.dm_exhausted_skip_action, playerName),
                            Toast.LENGTH_SHORT).show();
                }
                // Subsequent human player action
                messages.add(ChatMessage.humanPlayerMessage(text, playerName));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerChat.scrollToPosition(messages.size() - 1);
                String action = exhaustedCharacters.contains(playerName)
                        ? "[EXHAUSTED - can only speak] " + text : text;
                turnActions.add(action);
                advanceToNextHumanTurn();
            }
        } else {
            // Standard single-player flow
            if (mainCharacterDead) {
                Toast.makeText(this, R.string.dm_dead_cannot_act, Toast.LENGTH_SHORT).show();
                return;
            }
            if (mainCharacterExhausted) {
                Toast.makeText(this, R.string.dm_exhausted_cannot_act, Toast.LENGTH_SHORT).show();
            }
            turnsTaken++;
            addMessage(text, ChatMessage.TYPE_USER);
            String apiText = mainCharacterExhausted
                    ? "[EXHAUSTED - can only speak] " + text : text;
            sendToGemini(apiText, true);
        }
    }

    private void sendToGemini(String userText, boolean showAsUserMessage) {
        setLoading(true);

        // Apply stamina recovery per turn for all living characters
        if (staminaRecoveryPerTurn > 0) {
            applyStaminaRecovery();
        }

        // When map access is enabled, prepend current map state to the API text
        String apiText = userText;
        if (mapAccessEnabled) {
            String mapContext = buildMapContext();
            if (!mapContext.isEmpty()) {
                apiText = mapContext + "\n\n" + userText;
            }
        }

        // Add user content to conversation history
        Content userContent = Content.builder()
                .role("user")
                .parts(Part.fromText(apiText))
                .build();
        conversationHistory.add(userContent);

        // Copy current history for the API call
        List<Content> requestContents = new ArrayList<>(conversationHistory);

        executor.execute(() -> {
            try {
                GenerateContentResponse response = geminiClient.models.generateContent(
                        "gemini-2.5-flash", requestContents, geminiConfig);
                String reply = response.text();

                // Add model response to conversation history
                Content modelContent = Content.builder()
                        .role("model")
                        .parts(Part.fromText(reply != null ? reply : ""))
                        .build();
                conversationHistory.add(modelContent);

                runOnUiThread(() -> {
                    String displayText = reply != null ? reply : "(No response)";
                    if (mapAccessEnabled && reply != null) {
                        displayText = parseAndExecuteMapCommands(reply);
                    }
                    displayText = parseAndExecuteLevelUps(displayText);
                    displayText = parseAndExecuteHPChanges(displayText);
                    displayText = parseAndExecuteStaminaChanges(displayText);
                    displayText = parseAndExecuteLootDrops(displayText);
                    displayText = parseAndExecuteEncounterComplete(displayText);
                    displayText = parseAndExecuteEnemyDefeated(displayText);
                    displayText = parseAndExecuteGameOver(displayText);
                    rebuildSystemPrompt();
                    displayDmResponse(displayText);
                    if (checkGameOverConditions()) {
                        setLoading(false);
                        saveChat();
                        saveRunToFile();
                        return;
                    }
                    if (!aiPartyMembers.isEmpty() && hasLivingAIMembers()) {
                        textLoading.setText(R.string.dm_party_loading);
                        requestPartyResponses();
                    } else {
                        setLoading(false);
                        saveChat();
                        saveRunToFile();
                        if (isHotSeatActive()) {
                            startHotSeatTurns();
                        }
                    }
                });
            } catch (Exception e) {
                // Remove the failed user content from history
                conversationHistory.remove(conversationHistory.size() - 1);

                runOnUiThread(() -> {
                    String errorMsg;
                    if (e instanceof java.net.UnknownHostException
                            || e instanceof java.net.ConnectException
                            || e instanceof java.net.SocketTimeoutException) {
                        errorMsg = getString(R.string.dm_no_network);
                    } else {
                        errorMsg = getString(R.string.dm_error) + "\n" + e.getMessage();
                    }
                    addMessage(errorMsg, ChatMessage.TYPE_DM);
                    setLoading(false);
                });
            }
        });
    }

    private void addMessage(String text, int type) {
        messages.add(new ChatMessage(text, type));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        recyclerChat.scrollToPosition(messages.size() - 1);
    }

    private void setLoading(boolean loading) {
        setLoading(loading, false);
    }

    private void setLoading(boolean loading, boolean isImageGeneration) {
        layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(!loading);
        btnIllustrate.setEnabled(!loading);
        if (loading) {
            textLoading.setText(isImageGeneration
                    ? R.string.dm_illustrate_loading
                    : R.string.dm_loading);
            recyclerChat.scrollToPosition(messages.size() - 1);
        }
    }

    // ========== Image Generation ==========

    private static final String HF_API_URL =
            "https://router.huggingface.co/hf-inference/models/black-forest-labs/FLUX.1-schnell";

    private void onIllustrateClicked() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, R.string.dm_no_network, Toast.LENGTH_LONG).show();
            return;
        }

        String hfKey = getImageApiKey();
        if (hfKey == null || hfKey.isEmpty() || hfKey.equals("your_hf_token_here")) {
            Toast.makeText(this, R.string.dm_no_image_api_key, Toast.LENGTH_LONG).show();
            return;
        }

        // Find the last DM text message to use as scene description
        String lastDmText = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg.type == ChatMessage.TYPE_DM && !msg.hasImage()) {
                lastDmText = msg.text;
                break;
            }
        }

        if (lastDmText == null) {
            Toast.makeText(this, R.string.dm_no_scene, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true, true);

        String imagePrompt = "detailed fantasy illustration, D&D scene, "
                + "dramatic lighting, rich colors, fantasy art style. " + lastDmText;

        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put("inputs", imagePrompt);
        } catch (JSONException ignored) {
            return;
        }

        executor.execute(() -> {
            try {
                RequestBody body = RequestBody.create(
                        requestJson.toString(),
                        MediaType.get("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(HF_API_URL)
                        .addHeader("Authorization", "Bearer " + hfKey)
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        String errBody = response.body() != null
                                ? response.body().string() : "No response body";
                        String exMsg = "HTTP " + response.code() + ": " + errBody;
                        runOnUiThread(() -> {
                            addMessage(getString(R.string.dm_image_error) + "\n" + exMsg,
                                    ChatMessage.TYPE_DM);
                            setLoading(false, false);
                        });
                        return;
                    }

                    byte[] imageBytes = response.body().bytes();
                    String fileName = "dm_scene_" + UUID.randomUUID().toString() + ".png";
                    String savedFileName = saveImageToInternal(imageBytes, fileName);

                    if (savedFileName == null) {
                        runOnUiThread(() -> {
                            addMessage(getString(R.string.dm_image_error)
                                    + "\nFailed to save image.", ChatMessage.TYPE_DM);
                            setLoading(false, false);
                        });
                        return;
                    }

                    runOnUiThread(() -> {
                        addImageMessage("", savedFileName);
                        setLoading(false, false);
                        saveChat();
                        saveRunToFile();
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    String errorMsg;
                    if (e instanceof java.net.UnknownHostException
                            || e instanceof java.net.ConnectException
                            || e instanceof java.net.SocketTimeoutException) {
                        errorMsg = getString(R.string.dm_no_network);
                    } else {
                        String exMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                        errorMsg = getString(R.string.dm_image_error) + "\n" + exMsg;
                    }
                    addMessage(errorMsg, ChatMessage.TYPE_DM);
                    setLoading(false, false);
                });
            }
        });
    }

    private String saveImageToInternal(byte[] imageBytes, String fileName) {
        File imageDir = new File(getFilesDir(), "dm_images");
        if (!imageDir.exists()) {
            imageDir.mkdirs();
        }
        File imageFile = new File(imageDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            fos.write(imageBytes);
            fos.flush();
            return fileName;
        } catch (IOException e) {
            return null;
        }
    }

    private void addImageMessage(String caption, String imageFileName) {
        String displayText = (caption != null && !caption.isEmpty()) ? caption : "";
        messages.add(new ChatMessage(displayText, ChatMessage.TYPE_IMAGE, imageFileName));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        recyclerChat.scrollToPosition(messages.size() - 1);
    }

    // ========== NPC Dialogue Parsing ==========

    private void displayDmResponse(String response) {
        Matcher matcher = NPC_DIALOGUE_PATTERN.matcher(response);
        int lastEnd = 0;
        boolean hasNpcDialogue = false;

        while (matcher.find()) {
            hasNpcDialogue = true;

            // Add narration before this NPC block
            String narration = response.substring(lastEnd, matcher.start()).trim();
            if (!narration.isEmpty()) {
                addMessage(narration, ChatMessage.TYPE_DM);
            }

            // Add NPC dialogue as separate bubble
            String npcName = matcher.group(1).trim();
            String npcText = matcher.group(2).trim();
            messages.add(new ChatMessage(npcText, ChatMessage.TYPE_NPC, null, npcName));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerChat.scrollToPosition(messages.size() - 1);

            lastEnd = matcher.end();
        }

        if (hasNpcDialogue) {
            // Add any remaining narration after the last NPC block
            String remaining = response.substring(lastEnd).trim();
            if (!remaining.isEmpty()) {
                addMessage(remaining, ChatMessage.TYPE_DM);
            }
        } else {
            // No NPC dialogue found, display as regular DM message
            addMessage(response, ChatMessage.TYPE_DM);
        }
    }

    // ========== Level Up System ==========

    private String parseAndExecuteLevelUps(String response) {
        Matcher matcher = LEVEL_UP_PATTERN.matcher(response);
        List<String> leveledUpNames = new ArrayList<>();

        while (matcher.find()) {
            String charName = matcher.group(1).trim();
            leveledUpNames.add(charName);
        }

        // Strip level-up tags from display text
        String cleaned = LEVEL_UP_PATTERN.matcher(response).replaceAll("").trim();

        for (String charName : leveledUpNames) {
            if (charName.equalsIgnoreCase(selectedCharacterName)) {
                runLevel++;
                saveRunLevel();
                refreshCharacterInfoLabel();
                sfx.playLevelUp();
                messages.add(new ChatMessage(
                        selectedCharacterName + " reached Level " + runLevel + "!",
                        ChatMessage.TYPE_LEVEL_UP));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerChat.scrollToPosition(messages.size() - 1);
            }
        }

        return cleaned;
    }

    private String parseAndExecuteLootDrops(String response) {
        Matcher matcher = LOOT_DROP_PATTERN.matcher(response);
        List<String> targetNames = new ArrayList<>();
        List<String> lootNames = new ArrayList<>();
        List<Integer> lootDurabilities = new ArrayList<>();

        while (matcher.find()) {
            targetNames.add(matcher.group(1));
            lootNames.add(matcher.group(2).trim());
            String durStr = matcher.group(3);
            lootDurabilities.add(durStr != null ? Integer.parseInt(durStr) : 100);
        }

        // Strip loot tags from display text
        String cleaned = LOOT_DROP_PATTERN.matcher(response).replaceAll("").trim();

        for (int i = 0; i < lootNames.size(); i++) {
            String itemName = lootNames.get(i);
            String targetName = getMemberName(targetNames.get(i));
            int durability = lootDurabilities.get(i);
            lootCollected++;
            sfx.playLoot();

            if (targetName != null && memberInventories.containsKey(targetName)) {
                List<JSONObject> mInv = memberInventories.get(targetName);
                int mCap = memberCarryCapacity.getOrDefault(targetName, 10);
                if (mInv.size() < mCap) {
                    mInv.add(createInventoryItem(itemName, "", durability));
                    messages.add(new ChatMessage(
                            getString(R.string.dm_member_loot_found, targetName, itemName),
                            ChatMessage.TYPE_LOOT_DROP));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerChat.scrollToPosition(messages.size() - 1);
                } else {
                    unclaimedLoot.add(createInventoryItem(itemName, "", durability));
                    messages.add(new ChatMessage(
                            getString(R.string.dm_loot_unclaimed, targetName, itemName),
                            ChatMessage.TYPE_LOOT_DROP));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerChat.scrollToPosition(messages.size() - 1);
                }
            } else {
                if (inventory.size() < carryCapacity) {
                    inventory.add(createInventoryItem(itemName, "", durability));
                    messages.add(new ChatMessage(
                            getString(R.string.dm_inventory_loot_found, itemName),
                            ChatMessage.TYPE_LOOT_DROP));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerChat.scrollToPosition(messages.size() - 1);
                } else {
                    unclaimedLoot.add(createInventoryItem(itemName, "", durability));
                    messages.add(new ChatMessage(
                            getString(R.string.dm_loot_unclaimed_main, itemName),
                            ChatMessage.TYPE_LOOT_DROP));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerChat.scrollToPosition(messages.size() - 1);
                }
            }
        }

        return cleaned;
    }

    private String parseAndExecuteEncounterComplete(String response) {
        if (selectedScenario == null) return response;
        JSONArray encounters = selectedScenario.optJSONArray("encounters");
        if (encounters == null || encounters.length() == 0) return response;

        Matcher matcher = ENCOUNTER_COMPLETE_PATTERN.matcher(response);
        String cleaned = matcher.replaceAll("").trim();

        if (matcher.reset(response).find() && currentEncounterIndex < encounters.length()) {
            try {
                JSONObject completed = encounters.getJSONObject(currentEncounterIndex);
                String encName = completed.optString("name", "Encounter");
                int total = encounters.length();
                currentEncounterIndex++;
                sfx.playEncounterComplete();

                if (currentEncounterIndex >= total) {
                    messages.add(new ChatMessage(
                            getString(R.string.dm_encounters_finished),
                            ChatMessage.TYPE_ENCOUNTER_COMPLETE));
                } else {
                    messages.add(new ChatMessage(
                            getString(R.string.dm_encounter_complete, encName,
                                    currentEncounterIndex, total),
                            ChatMessage.TYPE_ENCOUNTER_COMPLETE));
                }
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerChat.scrollToPosition(messages.size() - 1);
            } catch (JSONException ignored) {
            }
        }

        return cleaned;
    }

    private String parseAndExecuteEnemyDefeated(String response) {
        Matcher matcher = ENEMY_DEFEATED_PATTERN.matcher(response);
        String cleaned = ENEMY_DEFEATED_PATTERN.matcher(response).replaceAll("").trim();

        while (matcher.find()) {
            try {
                int count = Integer.parseInt(matcher.group(1));
                enemiesDefeated += count;
                sfx.playEnemyDefeated();
                messages.add(new ChatMessage(
                        getString(R.string.dm_enemy_defeated, count),
                        ChatMessage.TYPE_ENEMY_DEFEATED));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerChat.scrollToPosition(messages.size() - 1);
            } catch (NumberFormatException ignored) {
            }
        }

        return cleaned;
    }

    // ========== Game Over ==========

    private String parseAndExecuteGameOver(String response) {
        String cleaned = GAME_WIN_PATTERN.matcher(response).replaceAll("").trim();
        cleaned = GAME_LOSE_PATTERN.matcher(cleaned).replaceAll("").trim();

        if (GAME_WIN_PATTERN.matcher(response).find()) {
            gameOver = true;
            launchGameOverScreen(GameOverActivity.RESULT_WIN, null);
        } else if (GAME_LOSE_PATTERN.matcher(response).find()) {
            gameOver = true;
            launchGameOverScreen(GameOverActivity.RESULT_LOSE, null);
        }

        return cleaned;
    }

    @SuppressWarnings("deprecation")
    private boolean checkGameOverConditions() {
        if (gameOver) return true;

        // --- Default lose: all party members dead ---
        boolean allDead = mainCharacterDead;
        if (allDead) {
            for (String name : memberStats.keySet()) {
                if (!deadCharacters.contains(name)) {
                    allDead = false;
                    break;
                }
            }
        }

        // In PvP mode, handle eliminations differently
        if (pvpMode) {
            return checkPvpGameOver();
        }

        if (allDead) {
            // Check if lose condition is custom (index 1) — if so, let AI decide via tags
            if (selectedScenario != null && selectedScenario.optInt("loseCondition", 0) == 1) {
                return false;
            }
            gameOver = true;
            launchGameOverScreen(GameOverActivity.RESULT_LOSE, null);
            return true;
        }

        // --- Default win: all encounters complete ---
        if (selectedScenario != null) {
            JSONArray encounters = selectedScenario.optJSONArray("encounters");
            if (encounters != null && encounters.length() > 0
                    && currentEncounterIndex >= encounters.length()) {
                // Check if win condition is custom (index 1) — if so, let AI decide via tags
                if (selectedScenario.optInt("winCondition", 0) == 1) {
                    return false;
                }
                gameOver = true;
                launchGameOverScreen(GameOverActivity.RESULT_WIN, null);
                return true;
            }
        }

        return false;
    }

    private boolean checkPvpGameOver() {
        if (teamCount > 0) {
            // Team-based PvP: check which teams still have living members
            Set<Integer> aliveTeams = new HashSet<>();
            // Check main character
            if (!mainCharacterDead) {
                Integer mainTeam = playerTeams.get(selectedCharacterName);
                if (mainTeam != null) aliveTeams.add(mainTeam);
            }
            for (Map.Entry<String, int[]> entry : memberStats.entrySet()) {
                if (!deadCharacters.contains(entry.getKey())) {
                    Integer team = playerTeams.get(entry.getKey());
                    if (team != null) aliveTeams.add(team);
                }
            }
            if (aliveTeams.size() <= 1 && !aliveTeams.isEmpty()) {
                int winningTeam = aliveTeams.iterator().next();
                gameOver = true;
                String subtitle = getString(R.string.game_over_subtitle_pvp_team_win, winningTeam);
                launchGameOverScreen(GameOverActivity.RESULT_WIN, subtitle);
                return true;
            }
        } else {
            // Free-for-all: check how many humans/players are alive
            List<String> alivePlayers = new ArrayList<>();
            if (!mainCharacterDead) alivePlayers.add(selectedCharacterName);
            for (JSONObject human : humanPlayers) {
                try {
                    String name = human.getString("name");
                    if (!deadCharacters.contains(name)) {
                        alivePlayers.add(name);
                    }
                } catch (JSONException ignored) {
                }
            }
            if (alivePlayers.size() <= 1) {
                gameOver = true;
                String subtitle;
                if (alivePlayers.size() == 1) {
                    subtitle = getString(R.string.game_over_subtitle_pvp_win, alivePlayers.get(0));
                } else {
                    subtitle = getString(R.string.game_over_subtitle_lose);
                }
                launchGameOverScreen(
                        alivePlayers.isEmpty() ? GameOverActivity.RESULT_LOSE : GameOverActivity.RESULT_WIN,
                        subtitle);
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private void launchGameOverScreen(int result, String subtitle) {
        addMessage(getString(R.string.dm_game_over_pending), ChatMessage.TYPE_DM);
        btnSend.setEnabled(false);

        // Gather party status
        List<String> survived = new ArrayList<>();
        List<String> fallen = new ArrayList<>();
        if (!mainCharacterDead) {
            survived.add(selectedCharacterName);
        } else {
            fallen.add(selectedCharacterName);
        }
        for (String name : memberStats.keySet()) {
            if (deadCharacters.contains(name)) {
                fallen.add(name);
            } else {
                survived.add(name);
            }
        }

        int encTotal = 0;
        if (selectedScenario != null) {
            JSONArray enc = selectedScenario.optJSONArray("encounters");
            if (enc != null) encTotal = enc.length();
        }

        Intent intent = new Intent(this, GameOverActivity.class);
        intent.putExtra(GameOverActivity.EXTRA_RESULT, result);
        if (subtitle != null) {
            intent.putExtra(GameOverActivity.EXTRA_SUBTITLE, subtitle);
        }
        intent.putExtra(GameOverActivity.EXTRA_ENEMIES_DEFEATED, enemiesDefeated);
        intent.putExtra(GameOverActivity.EXTRA_ITEMS_USED, itemsUsed);
        intent.putExtra(GameOverActivity.EXTRA_LOOT_COLLECTED, lootCollected);
        intent.putExtra(GameOverActivity.EXTRA_TURNS_TAKEN, turnsTaken);
        intent.putExtra(GameOverActivity.EXTRA_DAMAGE_TAKEN, totalDamageTaken);
        intent.putExtra(GameOverActivity.EXTRA_ENCOUNTERS_DONE, currentEncounterIndex);
        intent.putExtra(GameOverActivity.EXTRA_ENCOUNTERS_TOTAL, encTotal);
        intent.putExtra(GameOverActivity.EXTRA_SURVIVED, survived.toArray(new String[0]));
        intent.putExtra(GameOverActivity.EXTRA_FALLEN, fallen.toArray(new String[0]));

        // Delay briefly so the player can read the final DM response
        recyclerChat.postDelayed(() ->
                startActivityForResult(intent, REQUEST_GAME_OVER), 2000);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_GAME_OVER && resultCode == GameOverActivity.RETURN_NEW_RUN) {
            gameOver = false;
            btnSend.setEnabled(true);
            saveChat();
            saveRunToFile();
            createAndLoadNewRun();
        }
    }

    private void saveRunLevel() {
        if (selectedCharacterName == null) return;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        prefs.edit().putInt("run_level_" + selectedCharacterName + "_run_" + activeRunId, runLevel).apply();
    }

    private void loadRunLevel() {
        if (selectedCharacterName == null) return;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        int baseLevel = 1;
        try {
            baseLevel = selectedCharacter.getInt("level");
        } catch (JSONException ignored) {
        }
        runLevel = prefs.getInt("run_level_" + selectedCharacterName + "_run_" + activeRunId, baseLevel);
    }

    // ========== HP & Stamina Tracking ==========

    private void saveRunStats() {
        if (selectedCharacterName == null) return;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String key = selectedCharacterName + "_run_" + activeRunId;
        prefs.edit()
                .putInt("currentHP_" + key, currentHP)
                .putInt("maxHP_" + key, maxHP)
                .putInt("currentStamina_" + key, currentStamina)
                .putInt("maxStamina_" + key, maxStamina)
                .apply();
    }

    private void loadRunStats() {
        if (selectedCharacterName == null) return;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String key = selectedCharacterName + "_run_" + activeRunId;
        int baseHP = selectedCharacter.optInt("hp", 20);
        int baseStamina = selectedCharacter.optInt("stamina", 100);
        maxHP = prefs.getInt("maxHP_" + key, baseHP);
        currentHP = prefs.getInt("currentHP_" + key, maxHP);
        maxStamina = prefs.getInt("maxStamina_" + key, baseStamina);
        currentStamina = prefs.getInt("currentStamina_" + key, maxStamina);
    }

    private void initMemberStats(List<JSONObject> members) {
        for (JSONObject member : members) {
            try {
                String name = member.getString("name");
                int hp = member.optInt("hp", 20);
                int stamina = member.optInt("stamina", 100);
                memberStats.put(name, new int[]{hp, hp, stamina, stamina});
                memberInventories.put(name, new ArrayList<>());
                memberCarryCapacity.put(name, 10);
            } catch (JSONException ignored) {
            }
        }
    }

    private void saveMemberStats() {
        if (selectedCharacterName == null) return;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String key = "memberStats_" + selectedCharacterName + "_run_" + activeRunId;
        JSONObject json = new JSONObject();
        try {
            for (Map.Entry<String, int[]> entry : memberStats.entrySet()) {
                JSONArray arr = new JSONArray();
                for (int v : entry.getValue()) arr.put(v);
                json.put(entry.getKey(), arr);
            }
        } catch (JSONException ignored) {
        }
        prefs.edit().putString(key, json.toString()).apply();
    }

    private void loadMemberStats() {
        if (selectedCharacterName == null) return;
        memberStats.clear();
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String key = "memberStats_" + selectedCharacterName + "_run_" + activeRunId;
        String json = prefs.getString(key, null);
        if (json != null) {
            try {
                JSONObject obj = new JSONObject(json);
                java.util.Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    JSONArray arr = obj.getJSONArray(name);
                    memberStats.put(name, new int[]{
                            arr.getInt(0), arr.getInt(1), arr.getInt(2), arr.getInt(3)});
                }
            } catch (JSONException ignored) {
            }
        }
    }

    private void saveDeadCharacters() {
        if (selectedCharacterName == null) return;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String key = "deadChars_" + selectedCharacterName + "_run_" + activeRunId;
        JSONArray arr = new JSONArray();
        for (String name : deadCharacters) {
            arr.put(name);
        }
        prefs.edit()
                .putString(key, arr.toString())
                .putBoolean("mainDead_" + selectedCharacterName + "_run_" + activeRunId, mainCharacterDead)
                .apply();
    }

    private void loadDeadCharacters() {
        if (selectedCharacterName == null) return;
        deadCharacters.clear();
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String key = "deadChars_" + selectedCharacterName + "_run_" + activeRunId;
        String json = prefs.getString(key, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    deadCharacters.add(arr.getString(i));
                }
            } catch (JSONException ignored) {
            }
        }
        mainCharacterDead = prefs.getBoolean(
                "mainDead_" + selectedCharacterName + "_run_" + activeRunId, false);
    }

    private void saveExhaustedCharacters() {
        if (selectedCharacterName == null) return;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String key = "exhaustedChars_" + selectedCharacterName + "_run_" + activeRunId;
        JSONArray arr = new JSONArray();
        for (String name : exhaustedCharacters) {
            arr.put(name);
        }
        prefs.edit()
                .putString(key, arr.toString())
                .putBoolean("mainExhausted_" + selectedCharacterName + "_run_" + activeRunId,
                        mainCharacterExhausted)
                .apply();
    }

    private void loadExhaustedCharacters() {
        if (selectedCharacterName == null) return;
        exhaustedCharacters.clear();
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String key = "exhaustedChars_" + selectedCharacterName + "_run_" + activeRunId;
        String json = prefs.getString(key, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    exhaustedCharacters.add(arr.getString(i));
                }
            } catch (JSONException ignored) {
            }
        }
        mainCharacterExhausted = prefs.getBoolean(
                "mainExhausted_" + selectedCharacterName + "_run_" + activeRunId, false);
    }

    private void saveMemberInventories() {
        if (selectedCharacterName == null) return;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String key = "memberInv_" + selectedCharacterName + "_run_" + activeRunId;
        String capKey = "memberCap_" + selectedCharacterName + "_run_" + activeRunId;
        JSONObject invJson = new JSONObject();
        JSONObject capJson = new JSONObject();
        try {
            for (Map.Entry<String, List<JSONObject>> entry : memberInventories.entrySet()) {
                JSONArray arr = new JSONArray();
                for (JSONObject item : entry.getValue()) arr.put(item);
                invJson.put(entry.getKey(), arr);
            }
            for (Map.Entry<String, Integer> entry : memberCarryCapacity.entrySet()) {
                capJson.put(entry.getKey(), entry.getValue());
            }
        } catch (JSONException ignored) {
        }
        prefs.edit()
                .putString(key, invJson.toString())
                .putString(capKey, capJson.toString())
                .apply();
    }

    private void loadMemberInventories() {
        if (selectedCharacterName == null) return;
        memberInventories.clear();
        memberCarryCapacity.clear();
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String key = "memberInv_" + selectedCharacterName + "_run_" + activeRunId;
        String capKey = "memberCap_" + selectedCharacterName + "_run_" + activeRunId;
        String invJson = prefs.getString(key, null);
        if (invJson != null) {
            try {
                JSONObject obj = new JSONObject(invJson);
                java.util.Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    JSONArray arr = obj.getJSONArray(name);
                    List<JSONObject> items = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        items.add(arr.getJSONObject(i));
                    }
                    memberInventories.put(name, items);
                }
            } catch (JSONException ignored) {
            }
        }
        String capJsonStr = prefs.getString(capKey, null);
        if (capJsonStr != null) {
            try {
                JSONObject obj = new JSONObject(capJsonStr);
                java.util.Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    memberCarryCapacity.put(name, obj.getInt(name));
                }
            } catch (JSONException ignored) {
            }
        }
    }

    private String getMemberName(String rawName) {
        if (rawName == null) return null;
        String trimmed = rawName.trim();
        if (trimmed.equalsIgnoreCase(selectedCharacterName)) return null;
        if (memberStats.containsKey(trimmed)) return trimmed;
        for (String key : memberStats.keySet()) {
            if (key.equalsIgnoreCase(trimmed)) return key;
        }
        return null;
    }

    private String parseAndExecuteHPChanges(String response) {
        Matcher matcher = HP_CHANGE_PATTERN.matcher(response);
        List<String> names = new ArrayList<>();
        List<Integer> deltas = new ArrayList<>();

        while (matcher.find()) {
            try {
                names.add(matcher.group(1));
                deltas.add(Integer.parseInt(matcher.group(2)));
            } catch (NumberFormatException ignored) {
            }
        }

        String cleaned = HP_CHANGE_PATTERN.matcher(response).replaceAll("").trim();

        for (int i = 0; i < deltas.size(); i++) {
            int delta = deltas.get(i);
            String targetName = getMemberName(names.get(i));

            if (targetName != null && memberStats.containsKey(targetName)) {
                int[] stats = memberStats.get(targetName);
                int oldHP = stats[0];
                stats[0] = Math.max(0, Math.min(stats[1], stats[0] + delta));
                int actualDelta = stats[0] - oldHP;

                String msg;
                if (stats[0] == 0) {
                    msg = getString(R.string.dm_hp_zero, targetName);
                } else if (actualDelta < 0) {
                    msg = targetName + ": " + getString(R.string.dm_hp_damage, Math.abs(actualDelta), stats[0], stats[1]);
                    sfx.playAttack();
                } else {
                    msg = targetName + ": " + getString(R.string.dm_hp_heal, actualDelta, stats[0], stats[1]);
                    sfx.playHeal();
                }
                messages.add(new ChatMessage(msg, ChatMessage.TYPE_HP_CHANGE));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerChat.scrollToPosition(messages.size() - 1);

                // Declare death if HP reached 0
                if (stats[0] == 0 && !deadCharacters.contains(targetName)) {
                    deadCharacters.add(targetName);
                    sfx.playDeath();
                    String deathMsg = getString(R.string.dm_death, targetName);
                    messages.add(new ChatMessage(deathMsg, ChatMessage.TYPE_DEATH));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerChat.scrollToPosition(messages.size() - 1);
                }
            } else {
                int oldHP = currentHP;
                currentHP = Math.max(0, Math.min(maxHP, currentHP + delta));
                int actualDelta = currentHP - oldHP;
                if (actualDelta < 0) {
                    totalDamageTaken += Math.abs(actualDelta);
                }
                refreshCharacterInfoLabel();

                String msg;
                if (currentHP == 0) {
                    msg = getString(R.string.dm_hp_zero, selectedCharacterName);
                } else if (actualDelta < 0) {
                    msg = getString(R.string.dm_hp_damage, Math.abs(actualDelta), currentHP, maxHP);
                    sfx.playAttack();
                } else {
                    msg = getString(R.string.dm_hp_heal, actualDelta, currentHP, maxHP);
                    sfx.playHeal();
                }
                messages.add(new ChatMessage(msg, ChatMessage.TYPE_HP_CHANGE));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerChat.scrollToPosition(messages.size() - 1);

                // Declare death if main character HP reached 0
                if (currentHP == 0 && !mainCharacterDead) {
                    mainCharacterDead = true;
                    sfx.playDeath();
                    refreshCharacterInfoLabel();
                    String deathMsg = getString(R.string.dm_death, selectedCharacterName);
                    messages.add(new ChatMessage(deathMsg, ChatMessage.TYPE_DEATH));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerChat.scrollToPosition(messages.size() - 1);
                }
            }
        }

        return cleaned;
    }

    private String parseAndExecuteStaminaChanges(String response) {
        Matcher matcher = STAMINA_CHANGE_PATTERN.matcher(response);
        List<String> names = new ArrayList<>();
        List<Integer> deltas = new ArrayList<>();

        while (matcher.find()) {
            try {
                names.add(matcher.group(1));
                deltas.add(Integer.parseInt(matcher.group(2)));
            } catch (NumberFormatException ignored) {
            }
        }

        String cleaned = STAMINA_CHANGE_PATTERN.matcher(response).replaceAll("").trim();

        for (int i = 0; i < deltas.size(); i++) {
            int delta = deltas.get(i);
            String targetName = getMemberName(names.get(i));

            if (targetName != null && memberStats.containsKey(targetName)) {
                int[] stats = memberStats.get(targetName);
                int oldStamina = stats[2];
                stats[2] = Math.max(0, Math.min(stats[3], stats[2] + delta));
                int actualDelta = stats[2] - oldStamina;

                String msg;
                if (stats[2] == 0) {
                    msg = getString(R.string.dm_stamina_zero, targetName);
                } else if (actualDelta < 0) {
                    msg = targetName + ": " + getString(R.string.dm_stamina_drain, Math.abs(actualDelta), stats[2], stats[3]);
                    sfx.playStaminaDrain();
                } else {
                    msg = targetName + ": " + getString(R.string.dm_stamina_recover, actualDelta, stats[2], stats[3]);
                }
                messages.add(new ChatMessage(msg, ChatMessage.TYPE_STAMINA_CHANGE));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerChat.scrollToPosition(messages.size() - 1);

                // Track exhaustion state
                if (stats[2] == 0 && !exhaustedCharacters.contains(targetName)) {
                    exhaustedCharacters.add(targetName);
                    String exhaustMsg = getString(R.string.dm_exhausted, targetName);
                    messages.add(new ChatMessage(exhaustMsg, ChatMessage.TYPE_EXHAUSTED));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerChat.scrollToPosition(messages.size() - 1);
                } else if (stats[2] > 0 && exhaustedCharacters.contains(targetName)) {
                    exhaustedCharacters.remove(targetName);
                    String recoverMsg = getString(R.string.dm_exhausted_recovered, targetName);
                    messages.add(new ChatMessage(recoverMsg, ChatMessage.TYPE_EXHAUSTED));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerChat.scrollToPosition(messages.size() - 1);
                }
            } else {
                int oldStamina = currentStamina;
                currentStamina = Math.max(0, Math.min(maxStamina, currentStamina + delta));
                int actualDelta = currentStamina - oldStamina;
                refreshCharacterInfoLabel();

                String msg;
                if (currentStamina == 0) {
                    msg = getString(R.string.dm_stamina_zero, selectedCharacterName);
                } else if (actualDelta < 0) {
                    msg = getString(R.string.dm_stamina_drain, Math.abs(actualDelta), currentStamina, maxStamina);
                    sfx.playStaminaDrain();
                } else {
                    msg = getString(R.string.dm_stamina_recover, actualDelta, currentStamina, maxStamina);
                }
                messages.add(new ChatMessage(msg, ChatMessage.TYPE_STAMINA_CHANGE));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerChat.scrollToPosition(messages.size() - 1);

                // Track exhaustion for main character
                if (currentStamina == 0 && !mainCharacterExhausted) {
                    mainCharacterExhausted = true;
                    refreshCharacterInfoLabel();
                    String exhaustMsg = getString(R.string.dm_exhausted, selectedCharacterName);
                    messages.add(new ChatMessage(exhaustMsg, ChatMessage.TYPE_EXHAUSTED));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerChat.scrollToPosition(messages.size() - 1);
                } else if (currentStamina > 0 && mainCharacterExhausted) {
                    mainCharacterExhausted = false;
                    refreshCharacterInfoLabel();
                    String recoverMsg = getString(R.string.dm_exhausted_recovered, selectedCharacterName);
                    messages.add(new ChatMessage(recoverMsg, ChatMessage.TYPE_EXHAUSTED));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerChat.scrollToPosition(messages.size() - 1);
                }
            }
        }

        return cleaned;
    }

    private void applyStaminaRecovery() {
        boolean anyRecovered = false;

        // Recover main character stamina (if alive and not at max)
        if (!mainCharacterDead && currentStamina < maxStamina) {
            boolean wasExhausted = mainCharacterExhausted;
            currentStamina = Math.min(maxStamina, currentStamina + staminaRecoveryPerTurn);
            saveRunStats();
            if (currentStamina > 0 && wasExhausted) {
                mainCharacterExhausted = false;
                saveExhaustedCharacters();
                String recoverMsg = getString(R.string.dm_exhausted_recovered, selectedCharacterName);
                messages.add(new ChatMessage(recoverMsg, ChatMessage.TYPE_EXHAUSTED));
                chatAdapter.notifyItemInserted(messages.size() - 1);
            }
            anyRecovered = true;
        }

        // Recover party member stamina (if alive and not at max)
        for (Map.Entry<String, int[]> entry : memberStats.entrySet()) {
            String name = entry.getKey();
            if (deadCharacters.contains(name)) continue;
            int[] stats = entry.getValue();
            if (stats[2] < stats[3]) {
                boolean wasExhausted = exhaustedCharacters.contains(name);
                stats[2] = Math.min(stats[3], stats[2] + staminaRecoveryPerTurn);
                if (stats[2] > 0 && wasExhausted) {
                    exhaustedCharacters.remove(name);
                    saveExhaustedCharacters();
                    String recoverMsg = getString(R.string.dm_exhausted_recovered, name);
                    messages.add(new ChatMessage(recoverMsg, ChatMessage.TYPE_EXHAUSTED));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                }
                anyRecovered = true;
            }
        }

        if (anyRecovered) {
            saveMemberStats();
            rebuildSystemPrompt();
            refreshCharacterInfoLabel();
            String recoveryMsg = getString(R.string.dm_stamina_recovery_turn,
                    staminaRecoveryPerTurn);
            messages.add(new ChatMessage(recoveryMsg, ChatMessage.TYPE_STAMINA_CHANGE));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerChat.scrollToPosition(messages.size() - 1);
        }
    }

    // ========== Inventory System ==========

    private void saveInventory() {
        if (selectedCharacterName == null) return;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        for (JSONObject item : inventory) {
            arr.put(item);
        }
        prefs.edit()
                .putString("inventory_" + selectedCharacterName + "_run_" + activeRunId, arr.toString())
                .putInt("carry_capacity_" + selectedCharacterName + "_run_" + activeRunId, carryCapacity)
                .apply();
    }

    private void loadInventory() {
        if (selectedCharacterName == null) return;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        carryCapacity = prefs.getInt("carry_capacity_" + selectedCharacterName + "_run_" + activeRunId, 10);
        inventory.clear();
        String json = prefs.getString("inventory_" + selectedCharacterName + "_run_" + activeRunId, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    inventory.add(arr.getJSONObject(i));
                }
            } catch (JSONException ignored) {
            }
        }
    }

    private void saveUnclaimedLoot() {
        if (selectedCharacterName == null) return;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        for (JSONObject item : unclaimedLoot) {
            arr.put(item);
        }
        prefs.edit()
                .putString("unclaimed_loot_" + selectedCharacterName + "_run_" + activeRunId, arr.toString())
                .apply();
    }

    private void loadUnclaimedLoot() {
        if (selectedCharacterName == null) return;
        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        unclaimedLoot.clear();
        String json = prefs.getString("unclaimed_loot_" + selectedCharacterName + "_run_" + activeRunId, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    unclaimedLoot.add(arr.getJSONObject(i));
                }
            } catch (JSONException ignored) {
            }
        }
    }

    private String getItemName(JSONObject item) {
        return item.optString("name", "Unknown Item");
    }

    private String getItemDescription(JSONObject item) {
        return item.optString("description", "");
    }

    private int getItemDurability(JSONObject item) {
        return item.optInt("durability", 100);
    }

    private int getItemMaxDurability(JSONObject item) {
        return item.optInt("maxDurability", 100);
    }

    private int getDurabilityColor(int durability, int maxDurability) {
        if (maxDurability <= 0) return Color.parseColor("#4CAF50");
        int percent = durability * 100 / maxDurability;
        if (percent > 50) {
            return Color.parseColor("#4CAF50"); // green
        } else if (percent > 25) {
            return Color.parseColor("#FFC107"); // yellow
        } else {
            return Color.parseColor("#F44336"); // red
        }
    }

    private JSONObject createInventoryItem(String name, String description) {
        return createInventoryItem(name, description, 100);
    }

    private JSONObject createInventoryItem(String name, String description, int durability) {
        JSONObject item = new JSONObject();
        try {
            item.put("name", name);
            if (description != null && !description.isEmpty()) {
                item.put("description", description);
            }
            item.put("durability", durability);
            item.put("maxDurability", durability);
        } catch (JSONException ignored) {
        }
        return item;
    }

    private void showStatusDialog() {
        ScrollView outerScroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);
        outerScroll.addView(layout);

        // ---- Adventure Stats Section ----
        addMemberHeaderToLayout(layout, getString(R.string.dm_stats_header));
        addStatRow(layout, getString(R.string.dm_stats_enemies_defeated, enemiesDefeated));
        addStatRow(layout, getString(R.string.dm_stats_items_used, itemsUsed));
        addStatRow(layout, getString(R.string.dm_stats_loot_collected, lootCollected));
        addStatRow(layout, getString(R.string.dm_stats_turns_taken, turnsTaken));
        addStatRow(layout, getString(R.string.dm_stats_damage_taken, totalDamageTaken));
        if (selectedScenario != null) {
            JSONArray encounters = selectedScenario.optJSONArray("encounters");
            if (encounters != null && encounters.length() > 0) {
                addStatRow(layout, getString(R.string.dm_stats_encounters_progress,
                        currentEncounterIndex, encounters.length()));
            }
        }

        // Add spacing between stats and character sections
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24));
        layout.addView(spacer);

        // ---- Main Character Section ----
        String mainRole;
        if (mainCharacterDead) {
            mainRole = getString(R.string.dm_status_dead);
        } else if (mainCharacterExhausted) {
            mainRole = getString(R.string.dm_status_exhausted);
        } else {
            mainRole = "Main";
        }
        addMemberHeaderToLayout(layout,
                getString(R.string.dm_status_member_header, selectedCharacterName, mainRole));

        addHPBarToLayout(layout, currentHP, maxHP);
        if (devMode) {
            addDevEditButton(layout, getString(R.string.dev_edit_hp), () -> showDevEditHPDialog(null));
        }
        addStaminaBarToLayout(layout, currentStamina, maxStamina);
        if (devMode) {
            addDevEditButton(layout, getString(R.string.dev_edit_stamina), () -> showDevEditStaminaDialog(null));
        }

        // Main character inventory with Use/Drop buttons
        TextView capacityText = new TextView(this);
        capacityText.setText(getString(R.string.dm_inventory_capacity, inventory.size(), carryCapacity));
        capacityText.setTextSize(14);
        capacityText.setTypeface(capacityText.getTypeface(), android.graphics.Typeface.BOLD);
        layout.addView(capacityText);

        LinearLayout itemList = new LinearLayout(this);
        itemList.setOrientation(LinearLayout.VERTICAL);

        if (inventory.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText(R.string.dm_inventory_empty);
            emptyText.setPadding(0, 12, 0, 12);
            itemList.addView(emptyText);
        } else {
            for (int i = 0; i < inventory.size(); i++) {
                JSONObject item = inventory.get(i);
                String name = getItemName(item);
                String desc = getItemDescription(item);
                int dur = getItemDurability(item);
                int maxDur = getItemMaxDurability(item);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, 8, 0, 8);

                LinearLayout textColumn = new LinearLayout(this);
                textColumn.setOrientation(LinearLayout.VERTICAL);
                textColumn.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView nameText = new TextView(this);
                nameText.setText(name);
                nameText.setTextSize(14);
                nameText.setTypeface(nameText.getTypeface(), android.graphics.Typeface.BOLD);
                textColumn.addView(nameText);

                if (!desc.isEmpty()) {
                    TextView descText = new TextView(this);
                    descText.setText(desc);
                    descText.setTextSize(12);
                    descText.setAlpha(0.7f);
                    textColumn.addView(descText);
                }

                TextView durText = new TextView(this);
                durText.setText(getString(R.string.dm_item_durability, dur, maxDur));
                durText.setTextSize(11);
                durText.setTextColor(getDurabilityColor(dur, maxDur));
                textColumn.addView(durText);

                row.addView(textColumn);

                MaterialButton useBtn = new MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                useBtn.setText(R.string.dm_inventory_use);
                useBtn.setTextSize(11);
                useBtn.setMinimumWidth(0);
                useBtn.setMinWidth(0);
                useBtn.setMinimumHeight(0);
                useBtn.setMinHeight(0);
                useBtn.setPadding(16, 0, 16, 0);
                row.addView(useBtn);

                MaterialButton dropBtn = new MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                dropBtn.setText(R.string.dm_inventory_drop);
                dropBtn.setTextSize(11);
                dropBtn.setMinimumWidth(0);
                dropBtn.setMinWidth(0);
                dropBtn.setMinimumHeight(0);
                dropBtn.setMinHeight(0);
                dropBtn.setPadding(16, 0, 16, 0);
                row.addView(dropBtn);

                if (!memberInventories.isEmpty()) {
                    MaterialButton tradeBtn = new MaterialButton(this, null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle);
                    tradeBtn.setText(R.string.dm_inventory_trade);
                    tradeBtn.setTextSize(11);
                    tradeBtn.setMinimumWidth(0);
                    tradeBtn.setMinWidth(0);
                    tradeBtn.setMinimumHeight(0);
                    tradeBtn.setMinHeight(0);
                    tradeBtn.setPadding(16, 0, 16, 0);
                    tradeBtn.setTag(i);
                    row.addView(tradeBtn);
                }

                itemList.addView(row);

                useBtn.setTag(i);
                dropBtn.setTag(i);
            }
        }

        layout.addView(itemList);

        if (devMode) {
            addDevEditButton(layout, getString(R.string.dev_add_item), () -> {
                showDevAddItemDialog(null);
            });
        }

        // ---- AI Party Member Sections ----
        for (JSONObject member : aiPartyMembers) {
            try {
                String mName = member.getString("name");
                addMemberSection(layout, mName, "AI");
            } catch (JSONException ignored) {
            }
        }

        // ---- Human Player Sections ----
        for (JSONObject member : humanPlayers) {
            try {
                String mName = member.getString("name");
                addMemberSection(layout, mName, "Human");
            } catch (JSONException ignored) {
            }
        }

        // ---- Unclaimed Loot Section ----
        LinearLayout unclaimedList = new LinearLayout(this);
        unclaimedList.setOrientation(LinearLayout.VERTICAL);
        if (!unclaimedLoot.isEmpty()) {
            addMemberHeaderToLayout(layout,
                    getString(R.string.dm_unclaimed_loot_header, unclaimedLoot.size()));
            for (int i = 0; i < unclaimedLoot.size(); i++) {
                JSONObject lootItem = unclaimedLoot.get(i);
                String name = getItemName(lootItem);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, 8, 0, 8);

                TextView nameText = new TextView(this);
                nameText.setText(name);
                nameText.setTextSize(14);
                nameText.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(nameText);

                MaterialButton pickUpBtn = new MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                pickUpBtn.setText(R.string.dm_unclaimed_pick_up);
                pickUpBtn.setTextSize(11);
                pickUpBtn.setMinimumWidth(0);
                pickUpBtn.setMinWidth(0);
                pickUpBtn.setMinimumHeight(0);
                pickUpBtn.setMinHeight(0);
                pickUpBtn.setPadding(16, 0, 16, 0);
                pickUpBtn.setTag(i);
                row.addView(pickUpBtn);

                unclaimedList.addView(row);
            }
            layout.addView(unclaimedList);
        }

        // ---- Developer Tools Section ----
        if (devMode) {
            View devSpacer = new View(this);
            devSpacer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 24));
            layout.addView(devSpacer);
            addMemberHeaderToLayout(layout, getString(R.string.dev_tools_title));

            addDevEditButton(layout, getString(R.string.dev_trigger_win), () ->
                    launchGameOverScreen(GameOverActivity.RESULT_WIN, null));
            addDevEditButton(layout, getString(R.string.dev_trigger_lose), () ->
                    launchGameOverScreen(GameOverActivity.RESULT_LOSE, null));
            addDevEditButton(layout, getString(R.string.dev_complete_encounter), () ->
                    devCompleteEncounter());
            addDevEditButton(layout, getString(R.string.dev_set_encounter), () ->
                    showDevSetEncounterDialog());
            addDevEditButton(layout, getString(R.string.dev_kill_character), () ->
                    showDevKillCharacterDialog());
            addDevEditButton(layout, getString(R.string.dev_revive_character), () ->
                    showDevReviveCharacterDialog());
            addDevEditButton(layout, getString(R.string.dev_level_up), () ->
                    devLevelUp());
            addDevEditButton(layout, getString(R.string.dev_edit_stats), () ->
                    showDevEditAdventureStatsDialog());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dm_status_title)
                .setView(outerScroll)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.dm_inventory_set_capacity, null)
                .create();

        dialog.show();

        // Wire Pick Up buttons for unclaimed loot
        for (int i = 0; i < unclaimedList.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) unclaimedList.getChildAt(i);
            MaterialButton pickUpBtn = (MaterialButton) row.getChildAt(1);
            final int lootIndex = (int) pickUpBtn.getTag();

            pickUpBtn.setOnClickListener(v -> {
                dialog.dismiss();
                if (lootIndex < unclaimedLoot.size()) {
                    showPickUpCharacterDialog(lootIndex);
                }
            });
        }

        // Wire Use/Drop buttons for main character inventory
        if (!inventory.isEmpty()) {
            for (int i = 0; i < itemList.getChildCount(); i++) {
                LinearLayout row = (LinearLayout) itemList.getChildAt(i);
                MaterialButton useBtn = (MaterialButton) row.getChildAt(1);
                MaterialButton dropBtn = (MaterialButton) row.getChildAt(2);
                final int useIndex = (int) useBtn.getTag();
                final int dropIndex = (int) dropBtn.getTag();

                useBtn.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (useIndex < inventory.size()) {
                        String itemName = getItemName(inventory.remove(useIndex));
                        itemsUsed++;
                        saveInventory();
                        rebuildSystemPrompt();
                        String useMessage = "I use my " + itemName;
                        addMessage(useMessage, ChatMessage.TYPE_USER);
                        sendToGemini(useMessage, true);
                    }
                });

                dropBtn.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (dropIndex < inventory.size()) {
                        String itemName = getItemName(inventory.remove(dropIndex));
                        saveInventory();
                        rebuildSystemPrompt();
                        Toast.makeText(this,
                                getString(R.string.dm_inventory_dropped, itemName),
                                Toast.LENGTH_SHORT).show();
                    }
                });

                // Wire Trade button if it exists (child index 3)
                if (row.getChildCount() > 3) {
                    MaterialButton tradeBtn = (MaterialButton) row.getChildAt(3);
                    final int tradeIndex = (int) tradeBtn.getTag();
                    tradeBtn.setOnClickListener(v -> {
                        dialog.dismiss();
                        if (tradeIndex < inventory.size()) {
                            showTradeDialog(tradeIndex);
                        }
                    });
                }
            }
        }

        // Wire Set Capacity button
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            dialog.dismiss();
            showSetCapacityDialog();
        });
    }

    private void showPickUpCharacterDialog(int lootIndex) {
        if (lootIndex >= unclaimedLoot.size()) return;
        String itemName = getItemName(unclaimedLoot.get(lootIndex));

        // Build list of characters who have inventory space
        List<String> names = new ArrayList<>();
        if (inventory.size() < carryCapacity) {
            names.add(selectedCharacterName);
        }
        for (Map.Entry<String, List<JSONObject>> entry : memberInventories.entrySet()) {
            int mCap = memberCarryCapacity.getOrDefault(entry.getKey(), 10);
            if (entry.getValue().size() < mCap) {
                names.add(entry.getKey());
            }
        }

        if (names.isEmpty()) {
            Toast.makeText(this, R.string.dm_unclaimed_no_space, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] nameArray = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dm_unclaimed_pick_up_title, itemName))
                .setItems(nameArray, (dialog, which) -> {
                    if (lootIndex >= unclaimedLoot.size()) return;
                    JSONObject item = unclaimedLoot.remove(lootIndex);
                    saveUnclaimedLoot();

                    String chosen = nameArray[which];
                    if (chosen.equals(selectedCharacterName)) {
                        inventory.add(item);
                        saveInventory();
                    } else {
                        List<JSONObject> mInv = memberInventories.get(chosen);
                        if (mInv != null) {
                            mInv.add(item);
                            saveMemberInventories();
                        }
                    }
                    rebuildSystemPrompt();
                    Toast.makeText(this,
                            getString(R.string.dm_unclaimed_picked_up, chosen, itemName),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showTradeDialog(int myItemIndex) {
        if (myItemIndex >= inventory.size()) return;
        String myItemName = getItemName(inventory.get(myItemIndex));

        // Step 1: Pick a teammate who has at least one item
        List<String> eligibleMembers = new ArrayList<>();
        for (Map.Entry<String, List<JSONObject>> entry : memberInventories.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                eligibleMembers.add(entry.getKey());
            }
        }

        if (eligibleMembers.isEmpty()) {
            Toast.makeText(this, R.string.dm_trade_no_members, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] memberArray = eligibleMembers.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dm_trade_select_member, myItemName))
                .setItems(memberArray, (d1, which) -> {
                    String memberName = memberArray[which];
                    showTradeItemPickerDialog(myItemIndex, memberName);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showTradeItemPickerDialog(int myItemIndex, String memberName) {
        if (myItemIndex >= inventory.size()) return;
        List<JSONObject> mInv = memberInventories.get(memberName);
        if (mInv == null || mInv.isEmpty()) return;

        String myItemName = getItemName(inventory.get(myItemIndex));

        // Build display names for the member's items
        String[] itemNames = new String[mInv.size()];
        for (int i = 0; i < mInv.size(); i++) {
            itemNames[i] = getItemName(mInv.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dm_trade_select_item, memberName))
                .setItems(itemNames, (d2, theirIndex) -> {
                    if (myItemIndex >= inventory.size()) return;
                    if (theirIndex >= mInv.size()) return;

                    // Swap items
                    JSONObject myItem = inventory.get(myItemIndex);
                    JSONObject theirItem = mInv.get(theirIndex);
                    inventory.set(myItemIndex, theirItem);
                    mInv.set(theirIndex, myItem);

                    saveInventory();
                    saveMemberInventories();
                    rebuildSystemPrompt();

                    String theirItemName = getItemName(theirItem);
                    Toast.makeText(this,
                            getString(R.string.dm_trade_complete, myItemName, theirItemName, memberName),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addMemberHeaderToLayout(LinearLayout layout, String headerText) {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundColor(Color.parseColor("#BDBDBD"));

        // Only add divider if this isn't the first child
        if (layout.getChildCount() > 0) {
            View spacer = new View(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 16));
            layout.addView(spacer);
            layout.addView(divider);
        }

        TextView header = new TextView(this);
        header.setText(headerText);
        header.setTextSize(18);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        header.setPadding(0, 12, 0, 8);
        layout.addView(header);
    }

    private void addStatRow(LinearLayout layout, String text) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextSize(14);
        row.setPadding(0, 4, 0, 4);
        layout.addView(row);
    }

    private void addHPBarToLayout(LinearLayout layout, int hp, int hpMax) {
        if (hpMax <= 0) return;

        TextView hpLabel = new TextView(this);
        hpLabel.setText(getString(R.string.dm_status_hp, hp, hpMax));
        hpLabel.setTextSize(14);
        layout.addView(hpLabel);

        ProgressBar hpBar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        hpBar.setMax(hpMax);
        hpBar.setProgress(hp);
        hpBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 20));
        int hpPercent = hp * 100 / hpMax;
        int hpColor;
        if (hpPercent > 50) {
            hpColor = Color.parseColor("#4CAF50");
        } else if (hpPercent > 25) {
            hpColor = Color.parseColor("#FFC107");
        } else {
            hpColor = Color.parseColor("#F44336");
        }
        hpBar.getProgressDrawable().setColorFilter(hpColor, android.graphics.PorterDuff.Mode.SRC_IN);
        layout.addView(hpBar);
    }

    private void addStaminaBarToLayout(LinearLayout layout, int sta, int staMax) {
        if (staMax <= 0) return;

        TextView staLabel = new TextView(this);
        staLabel.setText(getString(R.string.dm_status_stamina, sta, staMax));
        staLabel.setTextSize(14);
        layout.addView(staLabel);

        ProgressBar staBar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        staBar.setMax(staMax);
        staBar.setProgress(sta);
        staBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 20));
        int staPercent = sta * 100 / staMax;
        int staColor;
        if (staPercent > 50) {
            staColor = Color.parseColor("#009688");
        } else if (staPercent > 25) {
            staColor = Color.parseColor("#FFC107");
        } else {
            staColor = Color.parseColor("#F44336");
        }
        staBar.getProgressDrawable().setColorFilter(staColor, android.graphics.PorterDuff.Mode.SRC_IN);
        layout.addView(staBar);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 8));
        layout.addView(spacer);
    }

    private void addMemberSection(LinearLayout layout, String memberName, String role) {
        String displayRole;
        if (deadCharacters.contains(memberName)) {
            displayRole = getString(R.string.dm_status_dead);
        } else if (exhaustedCharacters.contains(memberName)) {
            displayRole = getString(R.string.dm_status_exhausted);
        } else {
            displayRole = role;
        }
        addMemberHeaderToLayout(layout,
                getString(R.string.dm_status_member_header, memberName, displayRole));

        int[] stats = memberStats.get(memberName);
        if (stats != null) {
            addHPBarToLayout(layout, stats[0], stats[1]);
            if (devMode) {
                addDevEditButton(layout, getString(R.string.dev_edit_hp),
                        () -> showDevEditHPDialog(memberName));
            }
            addStaminaBarToLayout(layout, stats[2], stats[3]);
            if (devMode) {
                addDevEditButton(layout, getString(R.string.dev_edit_stamina),
                        () -> showDevEditStaminaDialog(memberName));
            }
        }

        List<JSONObject> mInv = memberInventories.get(memberName);
        int mCap = memberCarryCapacity.getOrDefault(memberName, 10);
        int mSize = mInv != null ? mInv.size() : 0;

        TextView capText = new TextView(this);
        capText.setText(getString(R.string.dm_inventory_capacity, mSize, mCap));
        capText.setTextSize(14);
        capText.setTypeface(capText.getTypeface(), android.graphics.Typeface.BOLD);
        layout.addView(capText);

        if (mInv == null || mInv.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText(R.string.dm_inventory_empty);
            emptyText.setPadding(0, 8, 0, 8);
            layout.addView(emptyText);
        } else {
            for (JSONObject item : mInv) {
                LinearLayout itemRow = new LinearLayout(this);
                itemRow.setOrientation(LinearLayout.VERTICAL);
                itemRow.setPadding(16, 4, 0, 4);

                TextView itemText = new TextView(this);
                String name = getItemName(item);
                String desc = getItemDescription(item);
                itemText.setText(desc.isEmpty() ? name : name + " — " + desc);
                itemText.setTextSize(14);
                itemRow.addView(itemText);

                int dur = getItemDurability(item);
                int maxDur = getItemMaxDurability(item);
                TextView durText = new TextView(this);
                durText.setText(getString(R.string.dm_item_durability, dur, maxDur));
                durText.setTextSize(11);
                durText.setTextColor(getDurabilityColor(dur, maxDur));
                itemRow.addView(durText);

                layout.addView(itemRow);
            }
        }

        if (devMode) {
            addDevEditButton(layout, getString(R.string.dev_add_item),
                    () -> showDevAddItemDialog(memberName));
        }
    }

    private void showSetCapacityDialog() {
        TextInputEditText input = new TextInputEditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.dm_inventory_capacity_hint);
        input.setText(String.valueOf(carryCapacity));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(48, 24, 48, 0);
        wrapper.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dm_inventory_set_capacity)
                .setView(wrapper)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!text.isEmpty()) {
                        try {
                            int newCapacity = Integer.parseInt(text);
                            if (newCapacity > 0) {
                                carryCapacity = newCapacity;
                                saveInventory();
                                rebuildSystemPrompt();
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ========== Developer Mode Helpers ==========

    private void addDevEditButton(LinearLayout layout, String label, Runnable onClick) {
        MaterialButton btn = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(label);
        btn.setTextSize(11);
        btn.setMinimumWidth(0);
        btn.setMinWidth(0);
        btn.setMinimumHeight(0);
        btn.setMinHeight(0);
        btn.setPadding(24, 0, 24, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 8;
        btn.setLayoutParams(params);
        btn.setOnClickListener(v -> onClick.run());
        layout.addView(btn);
    }

    private void showDevEditHPDialog(String memberName) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(48, 24, 48, 0);

        int curHP, curMaxHP;
        if (memberName == null) {
            curHP = currentHP;
            curMaxHP = maxHP;
        } else {
            int[] stats = memberStats.get(memberName);
            if (stats == null) return;
            curHP = stats[0];
            curMaxHP = stats[1];
        }

        TextInputEditText inputCurrent = new TextInputEditText(this);
        inputCurrent.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        inputCurrent.setHint(R.string.dev_current_value_hint);
        inputCurrent.setText(String.valueOf(curHP));
        wrapper.addView(inputCurrent);

        TextInputEditText inputMax = new TextInputEditText(this);
        inputMax.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputMax.setHint(R.string.dev_max_value_hint);
        inputMax.setText(String.valueOf(curMaxHP));
        LinearLayout.LayoutParams maxParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        maxParams.topMargin = 16;
        inputMax.setLayoutParams(maxParams);
        wrapper.addView(inputMax);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dev_edit_hp_title)
                .setView(wrapper)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String curText = inputCurrent.getText() != null
                            ? inputCurrent.getText().toString().trim() : "";
                    String maxText = inputMax.getText() != null
                            ? inputMax.getText().toString().trim() : "";
                    if (curText.isEmpty() || maxText.isEmpty()) return;
                    try {
                        int newHP = Integer.parseInt(curText);
                        int newMax = Integer.parseInt(maxText);
                        if (newMax < 1) newMax = 1;
                        if (newHP < 0) newHP = 0;
                        if (newHP > newMax) newHP = newMax;

                        if (memberName == null) {
                            currentHP = newHP;
                            maxHP = newMax;
                            mainCharacterDead = currentHP <= 0;
                            saveRunStats();
                            saveDeadCharacters();
                        } else {
                            int[] stats = memberStats.get(memberName);
                            if (stats != null) {
                                stats[0] = newHP;
                                stats[1] = newMax;
                                if (newHP <= 0) {
                                    deadCharacters.add(memberName);
                                } else {
                                    deadCharacters.remove(memberName);
                                }
                                saveMemberStats();
                                saveDeadCharacters();
                            }
                        }
                        rebuildSystemPrompt();
                    } catch (NumberFormatException ignored) {
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDevEditStaminaDialog(String memberName) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(48, 24, 48, 0);

        int curSta, curMaxSta;
        if (memberName == null) {
            curSta = currentStamina;
            curMaxSta = maxStamina;
        } else {
            int[] stats = memberStats.get(memberName);
            if (stats == null) return;
            curSta = stats[2];
            curMaxSta = stats[3];
        }

        TextInputEditText inputCurrent = new TextInputEditText(this);
        inputCurrent.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        inputCurrent.setHint(R.string.dev_current_value_hint);
        inputCurrent.setText(String.valueOf(curSta));
        wrapper.addView(inputCurrent);

        TextInputEditText inputMax = new TextInputEditText(this);
        inputMax.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputMax.setHint(R.string.dev_max_value_hint);
        inputMax.setText(String.valueOf(curMaxSta));
        LinearLayout.LayoutParams maxParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        maxParams.topMargin = 16;
        inputMax.setLayoutParams(maxParams);
        wrapper.addView(inputMax);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dev_edit_stamina_title)
                .setView(wrapper)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String curText = inputCurrent.getText() != null
                            ? inputCurrent.getText().toString().trim() : "";
                    String maxText = inputMax.getText() != null
                            ? inputMax.getText().toString().trim() : "";
                    if (curText.isEmpty() || maxText.isEmpty()) return;
                    try {
                        int newSta = Integer.parseInt(curText);
                        int newMax = Integer.parseInt(maxText);
                        if (newMax < 1) newMax = 1;
                        if (newSta < 0) newSta = 0;
                        if (newSta > newMax) newSta = newMax;

                        if (memberName == null) {
                            currentStamina = newSta;
                            maxStamina = newMax;
                            mainCharacterExhausted = currentStamina <= 0;
                            saveRunStats();
                            saveExhaustedCharacters();
                        } else {
                            int[] stats = memberStats.get(memberName);
                            if (stats != null) {
                                stats[2] = newSta;
                                stats[3] = newMax;
                                if (newSta <= 0) {
                                    exhaustedCharacters.add(memberName);
                                } else {
                                    exhaustedCharacters.remove(memberName);
                                }
                                saveMemberStats();
                                saveExhaustedCharacters();
                            }
                        }
                        rebuildSystemPrompt();
                    } catch (NumberFormatException ignored) {
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDevAddItemDialog(String memberName) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(48, 24, 48, 0);

        TextInputEditText inputName = new TextInputEditText(this);
        inputName.setHint(R.string.dev_item_name_hint);
        wrapper.addView(inputName);

        TextInputEditText inputDesc = new TextInputEditText(this);
        inputDesc.setHint(R.string.dev_item_desc_hint);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = 16;
        inputDesc.setLayoutParams(descParams);
        wrapper.addView(inputDesc);

        TextInputEditText inputDur = new TextInputEditText(this);
        inputDur.setHint(R.string.dev_item_durability_hint);
        inputDur.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams durParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        durParams.topMargin = 16;
        inputDur.setLayoutParams(durParams);
        wrapper.addView(inputDur);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dev_add_item_title)
                .setView(wrapper)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = inputName.getText() != null
                            ? inputName.getText().toString().trim() : "";
                    if (name.isEmpty()) return;
                    String desc = inputDesc.getText() != null
                            ? inputDesc.getText().toString().trim() : "";
                    int durability = 100;
                    if (inputDur.getText() != null && !inputDur.getText().toString().trim().isEmpty()) {
                        try {
                            durability = Integer.parseInt(inputDur.getText().toString().trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    JSONObject item = createInventoryItem(name, desc, durability);

                    if (memberName == null) {
                        if (inventory.size() >= carryCapacity) {
                            Toast.makeText(this,
                                    getString(R.string.dm_inventory_full, name),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        inventory.add(item);
                        saveInventory();
                    } else {
                        List<JSONObject> mInv = memberInventories.get(memberName);
                        if (mInv == null) {
                            mInv = new ArrayList<>();
                            memberInventories.put(memberName, mInv);
                        }
                        int mCap = memberCarryCapacity.getOrDefault(memberName, 10);
                        if (mInv.size() >= mCap) {
                            Toast.makeText(this,
                                    getString(R.string.dm_member_inventory_full, memberName, name),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        mInv.add(item);
                        saveMemberInventories();
                    }
                    rebuildSystemPrompt();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @SuppressWarnings("deprecation")
    private void devCompleteEncounter() {
        if (selectedScenario == null) {
            Toast.makeText(this, R.string.dev_no_encounters, Toast.LENGTH_SHORT).show();
            return;
        }
        JSONArray encounters = selectedScenario.optJSONArray("encounters");
        if (encounters == null || encounters.length() == 0) {
            Toast.makeText(this, R.string.dev_no_encounters, Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentEncounterIndex >= encounters.length()) {
            Toast.makeText(this, R.string.dev_all_encounters_done, Toast.LENGTH_SHORT).show();
            return;
        }
        currentEncounterIndex++;
        int total = encounters.length();
        sfx.playEncounterComplete();
        Toast.makeText(this,
                getString(R.string.dev_encounter_completed_dev, currentEncounterIndex, total),
                Toast.LENGTH_SHORT).show();
        if (currentEncounterIndex >= total) {
            messages.add(new ChatMessage(
                    getString(R.string.dm_encounters_finished),
                    ChatMessage.TYPE_ENCOUNTER_COMPLETE));
        } else {
            try {
                String encName = encounters.getJSONObject(currentEncounterIndex - 1)
                        .optString("name", "Encounter");
                messages.add(new ChatMessage(
                        getString(R.string.dm_encounter_complete, encName,
                                currentEncounterIndex, total),
                        ChatMessage.TYPE_ENCOUNTER_COMPLETE));
            } catch (JSONException ignored) {
            }
        }
        chatAdapter.notifyItemInserted(messages.size() - 1);
        recyclerChat.scrollToPosition(messages.size() - 1);
        rebuildSystemPrompt();
        saveRunToFile();
    }

    private void showDevSetEncounterDialog() {
        if (selectedScenario == null) {
            Toast.makeText(this, R.string.dev_no_encounters, Toast.LENGTH_SHORT).show();
            return;
        }
        JSONArray encounters = selectedScenario.optJSONArray("encounters");
        if (encounters == null || encounters.length() == 0) {
            Toast.makeText(this, R.string.dev_no_encounters, Toast.LENGTH_SHORT).show();
            return;
        }

        TextInputEditText input = new TextInputEditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.dev_encounter_index_hint);
        input.setText(String.valueOf(currentEncounterIndex));
        input.setPadding(48, 24, 48, 0);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dev_set_encounter_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String text = input.getText() != null
                            ? input.getText().toString().trim() : "";
                    if (text.isEmpty()) return;
                    try {
                        int newIndex = Integer.parseInt(text);
                        int total = encounters.length();
                        if (newIndex < 0) newIndex = 0;
                        if (newIndex > total) newIndex = total;
                        currentEncounterIndex = newIndex;
                        Toast.makeText(this,
                                getString(R.string.dev_encounter_updated, currentEncounterIndex),
                                Toast.LENGTH_SHORT).show();
                        rebuildSystemPrompt();
                        saveRunToFile();
                    } catch (NumberFormatException ignored) {
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDevKillCharacterDialog() {
        List<String> alive = new ArrayList<>();
        if (!mainCharacterDead) alive.add(selectedCharacterName);
        for (String name : memberStats.keySet()) {
            if (!deadCharacters.contains(name)) alive.add(name);
        }
        if (alive.isEmpty()) {
            Toast.makeText(this, R.string.dev_no_alive, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dev_select_kill)
                .setItems(alive.toArray(new String[0]), (dialog, which) -> {
                    String target = alive.get(which);
                    if (target.equals(selectedCharacterName)) {
                        currentHP = 0;
                        mainCharacterDead = true;
                        saveRunStats();
                    } else {
                        int[] stats = memberStats.get(target);
                        if (stats != null) stats[0] = 0;
                        deadCharacters.add(target);
                        saveMemberStats();
                    }
                    saveDeadCharacters();
                    refreshCharacterInfoLabel();
                    rebuildSystemPrompt();
                    sfx.playDeath();
                    String deathMsg = getString(R.string.dm_death, target);
                    messages.add(new ChatMessage(deathMsg, ChatMessage.TYPE_DEATH));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerChat.scrollToPosition(messages.size() - 1);
                    Toast.makeText(this,
                            getString(R.string.dev_killed, target),
                            Toast.LENGTH_SHORT).show();
                    saveRunToFile();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDevReviveCharacterDialog() {
        List<String> dead = new ArrayList<>();
        if (mainCharacterDead) dead.add(selectedCharacterName);
        for (String name : deadCharacters) {
            if (!name.equals(selectedCharacterName)) dead.add(name);
        }
        if (dead.isEmpty()) {
            Toast.makeText(this, R.string.dev_no_dead, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dev_select_revive)
                .setItems(dead.toArray(new String[0]), (dialog, which) -> {
                    String target = dead.get(which);
                    if (target.equals(selectedCharacterName)) {
                        currentHP = maxHP / 2;
                        if (currentHP < 1) currentHP = 1;
                        mainCharacterDead = false;
                        saveRunStats();
                    } else {
                        int[] stats = memberStats.get(target);
                        if (stats != null) {
                            stats[0] = stats[1] / 2;
                            if (stats[0] < 1) stats[0] = 1;
                        }
                        saveMemberStats();
                    }
                    deadCharacters.remove(target);
                    saveDeadCharacters();
                    refreshCharacterInfoLabel();
                    rebuildSystemPrompt();
                    Toast.makeText(this,
                            getString(R.string.dev_revived, target),
                            Toast.LENGTH_SHORT).show();
                    saveRunToFile();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void devLevelUp() {
        runLevel++;
        saveRunLevel();
        refreshCharacterInfoLabel();
        sfx.playLevelUp();
        messages.add(new ChatMessage(
                selectedCharacterName + " reached Level " + runLevel + "!",
                ChatMessage.TYPE_LEVEL_UP));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        recyclerChat.scrollToPosition(messages.size() - 1);
        Toast.makeText(this,
                getString(R.string.dev_leveled_up, selectedCharacterName, runLevel),
                Toast.LENGTH_SHORT).show();
        rebuildSystemPrompt();
        saveRunToFile();
    }

    private void showDevEditAdventureStatsDialog() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(48, 24, 48, 0);

        TextInputEditText inputEnemies = new TextInputEditText(this);
        inputEnemies.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputEnemies.setHint(R.string.dev_enemies_hint);
        inputEnemies.setText(String.valueOf(enemiesDefeated));
        wrapper.addView(inputEnemies);

        TextInputEditText inputItemsUsed = new TextInputEditText(this);
        inputItemsUsed.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputItemsUsed.setHint(R.string.dev_items_used_hint);
        inputItemsUsed.setText(String.valueOf(itemsUsed));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p1.topMargin = 16;
        inputItemsUsed.setLayoutParams(p1);
        wrapper.addView(inputItemsUsed);

        TextInputEditText inputLoot = new TextInputEditText(this);
        inputLoot.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputLoot.setHint(R.string.dev_loot_hint);
        inputLoot.setText(String.valueOf(lootCollected));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p2.topMargin = 16;
        inputLoot.setLayoutParams(p2);
        wrapper.addView(inputLoot);

        TextInputEditText inputTurns = new TextInputEditText(this);
        inputTurns.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputTurns.setHint(R.string.dev_turns_hint);
        inputTurns.setText(String.valueOf(turnsTaken));
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p3.topMargin = 16;
        inputTurns.setLayoutParams(p3);
        wrapper.addView(inputTurns);

        TextInputEditText inputDamage = new TextInputEditText(this);
        inputDamage.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputDamage.setHint(R.string.dev_damage_hint);
        inputDamage.setText(String.valueOf(totalDamageTaken));
        LinearLayout.LayoutParams p4 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p4.topMargin = 16;
        inputDamage.setLayoutParams(p4);
        wrapper.addView(inputDamage);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dev_edit_stats_title)
                .setView(wrapper)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        String e = inputEnemies.getText() != null
                                ? inputEnemies.getText().toString().trim() : "";
                        if (!e.isEmpty()) enemiesDefeated = Integer.parseInt(e);

                        String iu = inputItemsUsed.getText() != null
                                ? inputItemsUsed.getText().toString().trim() : "";
                        if (!iu.isEmpty()) itemsUsed = Integer.parseInt(iu);

                        String l = inputLoot.getText() != null
                                ? inputLoot.getText().toString().trim() : "";
                        if (!l.isEmpty()) lootCollected = Integer.parseInt(l);

                        String t = inputTurns.getText() != null
                                ? inputTurns.getText().toString().trim() : "";
                        if (!t.isEmpty()) turnsTaken = Integer.parseInt(t);

                        String d = inputDamage.getText() != null
                                ? inputDamage.getText().toString().trim() : "";
                        if (!d.isEmpty()) totalDamageTaken = Integer.parseInt(d);

                        Toast.makeText(this, R.string.dev_stats_updated,
                                Toast.LENGTH_SHORT).show();
                        saveRunToFile();
                    } catch (NumberFormatException ignored) {
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshCharacterInfoLabel() {
        try {
            String name = selectedCharacter.getString("name");
            int level = selectedCharacter.getInt("level");
            String race = races[selectedCharacter.getInt("race")];
            String charClass = classes[selectedCharacter.getInt("class")];
            updateCharacterInfoLabel(name, level, race, charClass);
        } catch (JSONException ignored) {
        }
    }

    private void rebuildSystemPrompt() {
        try {
            String systemPrompt = buildSystemPrompt(selectedCharacter);
            geminiConfig = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                    .build();
        } catch (JSONException ignored) {
        }
    }

    // ========== AI Party Member Responses ==========

    private boolean hasLivingAIMembers() {
        for (JSONObject member : aiPartyMembers) {
            try {
                if (!deadCharacters.contains(member.getString("name"))) {
                    return true;
                }
            } catch (JSONException ignored) {
            }
        }
        return false;
    }

    private void requestPartyResponses() {
        // Skip if all AI party members are dead
        if (!hasLivingAIMembers()) {
            setLoading(false);
            saveChat();
            saveRunToFile();
            if (isHotSeatActive()) {
                startHotSeatTurns();
            }
            return;
        }

        String prompt = buildPartyResponsePrompt();

        Content userContent = Content.builder()
                .role("user")
                .parts(Part.fromText(prompt))
                .build();
        conversationHistory.add(userContent);

        List<Content> requestContents = new ArrayList<>(conversationHistory);

        executor.execute(() -> {
            try {
                GenerateContentResponse response = geminiClient.models.generateContent(
                        "gemini-2.5-flash", requestContents, geminiConfig);
                String reply = response.text();

                Content modelContent = Content.builder()
                        .role("model")
                        .parts(Part.fromText(reply != null ? reply : ""))
                        .build();
                conversationHistory.add(modelContent);

                runOnUiThread(() -> {
                    if (reply != null) {
                        parseAndDisplayPartyResponses(reply);
                    }
                    setLoading(false);
                    saveChat();
                    saveRunToFile();
                    if (isHotSeatActive()) {
                        startHotSeatTurns();
                    }
                });
            } catch (Exception e) {
                conversationHistory.remove(conversationHistory.size() - 1);
                runOnUiThread(() -> {
                    setLoading(false);
                    saveChat();
                    saveRunToFile();
                    if (isHotSeatActive()) {
                        startHotSeatTurns();
                    }
                });
            }
        });
    }

    private String buildPartyResponsePrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("[PARTY TURN] Now respond as each AI party member reacting to the DM's ");
        sb.append("most recent narration (the last Dungeon Master message above) and the ");
        sb.append("player's action. Each character MUST:\n");
        sb.append("- Speak in FIRST PERSON (\"I draw my sword\", not \"Thorin draws his sword\")\n");
        sb.append("- Refer to themselves by their own name when appropriate ");
        sb.append("(\"I, Thorin, will hold the line\" or introduce themselves naturally)\n");
        sb.append("- Stay in character based on their personality, class, and abilities\n");
        sb.append("- React to what the DM just described, NOT repeat or summarize it\n\n");
        sb.append("Format each response with the character's name in brackets:\n\n");

        if (aiPartyMembers.isEmpty()) {
            sb.append("[Character Name]: (their first-person response)\n");
        } else {
            for (JSONObject member : aiPartyMembers) {
                try {
                    String mName = member.getString("name");
                    if (deadCharacters.contains(mName)) continue;
                    if (exhaustedCharacters.contains(mName)) {
                        sb.append("[").append(mName).append("]: (EXHAUSTED — can only speak, ")
                                .append("no physical actions, spells, or attacks)\n");
                    } else {
                        sb.append("[").append(mName).append("]: (their first-person response)\n");
                    }
                } catch (JSONException ignored) {
                }
            }
        }

        sb.append("\nKeep each response to 1-3 sentences. Show personality through actions and dialogue.");
        return sb.toString();
    }

    private void parseAndDisplayPartyResponses(String response) {
        Matcher matcher = PARTY_RESPONSE_PATTERN.matcher(response);
        boolean found = false;

        while (matcher.find()) {
            String name = matcher.group(1);
            String text = matcher.group(2);
            if (name != null && text != null) {
                messages.add(ChatMessage.aiCharacterMessage(text.trim(), name.trim()));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerChat.scrollToPosition(messages.size() - 1);
                found = true;
            }
        }

        if (!found && !response.trim().isEmpty()) {
            messages.add(ChatMessage.aiCharacterMessage(response.trim(), "Party"));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerChat.scrollToPosition(messages.size() - 1);
        }
    }

    // ========== Chat Persistence ==========

    private String chatKey() {
        return KEY_CHAT_MESSAGES + selectedCharacterName + "_run_" + activeRunId;
    }

    private void saveChat() {
        if (selectedCharacterName == null) return;

        try {
            JSONArray chatArray = new JSONArray();
            for (ChatMessage msg : messages) {
                JSONObject msgObj = new JSONObject();
                msgObj.put("text", msg.text);
                msgObj.put("type", msg.type);
                if (msg.imageFileName != null) {
                    msgObj.put("imageFileName", msg.imageFileName);
                }
                if (msg.senderName != null) {
                    msgObj.put("senderName", msg.senderName);
                }
                chatArray.put(msgObj);
            }

            SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
            prefs.edit()
                    .putString(chatKey(), chatArray.toString())
                    .putString(KEY_LAST_CHARACTER, selectedCharacterName)
                    .apply();
        } catch (JSONException ignored) {
        }
    }

    private void saveRunToFile() {
        if (selectedCharacterName == null) return;
        try {
            JSONObject root = new JSONObject();
            root.put("characterName", selectedCharacterName);
            root.put("runId", activeRunId);
            root.put("runLevel", runLevel);
            root.put("currentHP", currentHP);
            root.put("maxHP", maxHP);
            root.put("currentStamina", currentStamina);
            root.put("maxStamina", maxStamina);
            root.put("mainCharacterDead", mainCharacterDead);
            root.put("mainCharacterExhausted", mainCharacterExhausted);
            root.put("currentEncounterIndex", currentEncounterIndex);
            root.put("enemiesDefeated", enemiesDefeated);
            root.put("itemsUsed", itemsUsed);
            root.put("lootCollected", lootCollected);
            root.put("turnsTaken", turnsTaken);
            root.put("totalDamageTaken", totalDamageTaken);
            root.put("carryCapacity", carryCapacity);
            root.put("pvpMode", pvpMode);
            root.put("pvpHiddenVisibility", pvpHiddenVisibility);
            root.put("teamCount", teamCount);
            JSONObject playerTeamsJson = new JSONObject();
            for (Map.Entry<String, Integer> entry : playerTeams.entrySet()) {
                playerTeamsJson.put(entry.getKey(), entry.getValue());
            }
            root.put("playerTeams", playerTeamsJson);

            // Inventory
            JSONArray invArr = new JSONArray();
            for (JSONObject item : inventory) {
                invArr.put(item);
            }
            root.put("inventory", invArr);

            // Unclaimed loot
            JSONArray lootArr = new JSONArray();
            for (JSONObject item : unclaimedLoot) {
                lootArr.put(item);
            }
            root.put("unclaimedLoot", lootArr);

            // Member stats
            JSONObject memberStatsJson = new JSONObject();
            for (Map.Entry<String, int[]> entry : memberStats.entrySet()) {
                JSONArray arr = new JSONArray();
                for (int v : entry.getValue()) {
                    arr.put(v);
                }
                memberStatsJson.put(entry.getKey(), arr);
            }
            root.put("memberStats", memberStatsJson);

            // Member inventories
            JSONObject memberInvJson = new JSONObject();
            for (Map.Entry<String, List<JSONObject>> entry : memberInventories.entrySet()) {
                JSONArray arr = new JSONArray();
                for (JSONObject item : entry.getValue()) {
                    arr.put(item);
                }
                memberInvJson.put(entry.getKey(), arr);
            }
            root.put("memberInventories", memberInvJson);

            // Member carry capacity
            JSONObject memberCapJson = new JSONObject();
            for (Map.Entry<String, Integer> entry : memberCarryCapacity.entrySet()) {
                memberCapJson.put(entry.getKey(), entry.getValue());
            }
            root.put("memberCarryCapacity", memberCapJson);

            // Dead characters
            JSONArray deadArr = new JSONArray();
            for (String name : deadCharacters) {
                deadArr.put(name);
            }
            root.put("deadCharacters", deadArr);

            // Exhausted characters
            JSONArray exhaustedArr = new JSONArray();
            for (String name : exhaustedCharacters) {
                exhaustedArr.put(name);
            }
            root.put("exhaustedCharacters", exhaustedArr);

            // Chat messages
            JSONArray chatArray = new JSONArray();
            for (ChatMessage msg : messages) {
                JSONObject msgObj = new JSONObject();
                msgObj.put("text", msg.text);
                msgObj.put("type", msg.type);
                if (msg.imageFileName != null) {
                    msgObj.put("imageFileName", msg.imageFileName);
                }
                if (msg.senderName != null) {
                    msgObj.put("senderName", msg.senderName);
                }
                chatArray.put(msgObj);
            }
            root.put("chatMessages", chatArray);

            // Write to file and upload to Firebase on background thread
            String jsonStr = root.toString(2);
            File runsDir = new File(getFilesDir(), "runs");
            String fileName = selectedCharacterName + "_run_" + activeRunId + ".json";
            executor.execute(() -> {
                try {
                    if (!runsDir.exists()) {
                        runsDir.mkdirs();
                    }
                    File runFile = new File(runsDir, fileName);
                    try (FileWriter writer = new FileWriter(runFile)) {
                        writer.write(jsonStr);
                    }
                } catch (IOException e) {
                    android.util.Log.e("DungeonMaster", "Failed to write run file", e);
                }
                saveRunToFirebase(root);
            });
        } catch (JSONException e) {
            android.util.Log.e("DungeonMaster", "Failed to serialize run data", e);
        }
    }

    private void saveRunToFirebase(JSONObject root) {
        if (selectedCharacterName == null) return;

        Map<String, Object> data;
        try {
            data = jsonToMap(root);
        } catch (JSONException e) {
            android.util.Log.e("DungeonMaster", "Failed to convert run data for Firebase", e);
            return;
        }

        try {
            FirebaseDatabase db = FirebaseDatabase.getInstance();
            String key = selectedCharacterName + "_run_" + activeRunId;
            DatabaseReference ref = db.getReference("runs").child(key);

            if (!isNetworkAvailable()) {
                android.util.Log.w("DungeonMaster",
                        "No network — Firebase write queued for sync when online");
            }

            ref.setValue(data)
                    .addOnSuccessListener(unused ->
                            android.util.Log.d("DungeonMaster",
                                    "Firebase save succeeded for " + key))
                    .addOnFailureListener(e ->
                            android.util.Log.e("DungeonMaster",
                                    "Firebase save failed for " + key, e));
        } catch (Exception e) {
            android.util.Log.e("DungeonMaster", "Firebase save error", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonToMap(JSONObject json) throws JSONException {
        Map<String, Object> map = new HashMap<>();
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);
            if (value instanceof JSONObject) {
                map.put(key, jsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.put(key, jsonArrayToList((JSONArray) value));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private static List<Object> jsonArrayToList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONObject) {
                list.add(jsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                list.add(jsonArrayToList((JSONArray) value));
            } else {
                list.add(value);
            }
        }
        return list;
    }

    private boolean loadRunFromFile() {
        if (selectedCharacterName == null) return false;
        File runsDir = new File(getFilesDir(), "runs");
        String fileName = selectedCharacterName + "_run_" + activeRunId + ".json";
        File runFile = new File(runsDir, fileName);
        if (!runFile.exists()) return false;

        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(runFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject root = new JSONObject(sb.toString());

            // Run level
            int baseLevel = 1;
            try {
                baseLevel = selectedCharacter.getInt("level");
            } catch (JSONException ignored) {
            }
            runLevel = root.optInt("runLevel", baseLevel);

            // HP & Stamina
            int baseHP = selectedCharacter.optInt("hp", 20);
            int baseStamina = selectedCharacter.optInt("stamina", 100);
            maxHP = root.optInt("maxHP", baseHP);
            currentHP = root.optInt("currentHP", maxHP);
            maxStamina = root.optInt("maxStamina", baseStamina);
            currentStamina = root.optInt("currentStamina", maxStamina);
            mainCharacterDead = root.optBoolean("mainCharacterDead", false);
            mainCharacterExhausted = root.optBoolean("mainCharacterExhausted", false);
            currentEncounterIndex = root.optInt("currentEncounterIndex", 0);
            enemiesDefeated = root.optInt("enemiesDefeated", 0);
            itemsUsed = root.optInt("itemsUsed", 0);
            lootCollected = root.optInt("lootCollected", 0);
            turnsTaken = root.optInt("turnsTaken", 0);
            totalDamageTaken = root.optInt("totalDamageTaken", 0);
            carryCapacity = root.optInt("carryCapacity", 10);
            pvpMode = root.optBoolean("pvpMode", false);
            pvpHiddenVisibility = root.optBoolean("pvpHiddenVisibility", false);
            teamCount = root.optInt("teamCount", 0);
            playerTeams.clear();
            JSONObject playerTeamsJson = root.optJSONObject("playerTeams");
            if (playerTeamsJson != null) {
                java.util.Iterator<String> ptKeys = playerTeamsJson.keys();
                while (ptKeys.hasNext()) {
                    String ptName = ptKeys.next();
                    playerTeams.put(ptName, playerTeamsJson.getInt(ptName));
                }
            }

            // Inventory
            inventory.clear();
            JSONArray invArr = root.optJSONArray("inventory");
            if (invArr != null) {
                for (int i = 0; i < invArr.length(); i++) {
                    inventory.add(invArr.getJSONObject(i));
                }
            }

            // Unclaimed loot
            unclaimedLoot.clear();
            JSONArray lootArr = root.optJSONArray("unclaimedLoot");
            if (lootArr != null) {
                for (int i = 0; i < lootArr.length(); i++) {
                    unclaimedLoot.add(lootArr.getJSONObject(i));
                }
            }

            // Member stats
            memberStats.clear();
            JSONObject memberStatsJson = root.optJSONObject("memberStats");
            if (memberStatsJson != null) {
                java.util.Iterator<String> keys = memberStatsJson.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    JSONArray arr = memberStatsJson.getJSONArray(name);
                    memberStats.put(name, new int[]{
                            arr.getInt(0), arr.getInt(1), arr.getInt(2), arr.getInt(3)});
                }
            }

            // Member inventories
            memberInventories.clear();
            JSONObject memberInvJson = root.optJSONObject("memberInventories");
            if (memberInvJson != null) {
                java.util.Iterator<String> keys = memberInvJson.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    JSONArray arr = memberInvJson.getJSONArray(name);
                    List<JSONObject> items = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        items.add(arr.getJSONObject(i));
                    }
                    memberInventories.put(name, items);
                }
            }

            // Member carry capacity
            memberCarryCapacity.clear();
            JSONObject memberCapJson = root.optJSONObject("memberCarryCapacity");
            if (memberCapJson != null) {
                java.util.Iterator<String> keys = memberCapJson.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    memberCarryCapacity.put(name, memberCapJson.getInt(name));
                }
            }

            // Dead characters
            deadCharacters.clear();
            JSONArray deadArr = root.optJSONArray("deadCharacters");
            if (deadArr != null) {
                for (int i = 0; i < deadArr.length(); i++) {
                    deadCharacters.add(deadArr.getString(i));
                }
            }

            // Exhausted characters
            exhaustedCharacters.clear();
            JSONArray exhaustedArr = root.optJSONArray("exhaustedCharacters");
            if (exhaustedArr != null) {
                for (int i = 0; i < exhaustedArr.length(); i++) {
                    exhaustedCharacters.add(exhaustedArr.getString(i));
                }
            }

            // Chat messages — restore via restoreChat() which also rebuilds conversationHistory
            // The file's chatMessages are saved alongside SharedPreferences chat data,
            // so restoreChat() handles chat restoration from SharedPreferences.
            return true;
        } catch (JSONException | IOException e) {
            android.util.Log.e("DungeonMaster", "Failed to load run file", e);
            return false;
        }
    }

    private boolean restoreChat() {
        if (selectedCharacterName == null) return false;

        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String saved = prefs.getString(chatKey(), null);
        if (saved == null) return false;

        try {
            JSONArray chatArray = new JSONArray(saved);
            if (chatArray.length() == 0) return false;

            messages.clear();
            conversationHistory.clear();
            StringBuilder dmNpcBuffer = new StringBuilder();
            StringBuilder aiCharacterBuffer = new StringBuilder();

            for (int i = 0; i < chatArray.length(); i++) {
                JSONObject msgObj = chatArray.getJSONObject(i);
                String text = msgObj.getString("text");
                int type = msgObj.getInt("type");
                String imageFileName = msgObj.optString("imageFileName", null);
                String senderName = msgObj.optString("senderName", null);

                // Add to display messages
                if (type == ChatMessage.TYPE_AI_CHARACTER && senderName != null) {
                    messages.add(ChatMessage.aiCharacterMessage(text, senderName));
                } else if (type == ChatMessage.TYPE_HUMAN_PLAYER && senderName != null) {
                    messages.add(ChatMessage.humanPlayerMessage(text, senderName));
                } else if (type == ChatMessage.TYPE_NPC && senderName != null) {
                    messages.add(new ChatMessage(text, type, null, senderName));
                } else if (imageFileName != null) {
                    messages.add(new ChatMessage(text, type, imageFileName));
                } else {
                    messages.add(new ChatMessage(text, type));
                }

                // Rebuild Gemini conversation history
                if (type == ChatMessage.TYPE_DM || type == ChatMessage.TYPE_NPC) {
                    // Flush AI character buffer first if needed
                    if (aiCharacterBuffer.length() > 0) {
                        conversationHistory.add(Content.builder()
                                .role("user")
                                .parts(Part.fromText(buildPartyResponsePrompt()))
                                .build());
                        conversationHistory.add(Content.builder()
                                .role("model")
                                .parts(Part.fromText(aiCharacterBuffer.toString()))
                                .build());
                        aiCharacterBuffer.setLength(0);
                    }

                    // Buffer DM narration and NPC dialogue into a single model response
                    if (dmNpcBuffer.length() > 0) dmNpcBuffer.append("\n\n");
                    if (type == ChatMessage.TYPE_NPC && senderName != null) {
                        dmNpcBuffer.append("[NPC:").append(senderName).append("]: ").append(text);
                    } else {
                        dmNpcBuffer.append(text);
                    }
                } else if (type == ChatMessage.TYPE_AI_CHARACTER) {
                    // Flush DM/NPC buffer first
                    if (dmNpcBuffer.length() > 0) {
                        conversationHistory.add(Content.builder()
                                .role("model")
                                .parts(Part.fromText(dmNpcBuffer.toString()))
                                .build());
                        dmNpcBuffer.setLength(0);
                    }

                    // Buffer AI character responses
                    if (aiCharacterBuffer.length() > 0) aiCharacterBuffer.append("\n");
                    aiCharacterBuffer.append("[")
                            .append(senderName != null ? senderName : "Party")
                            .append("]: ").append(text);
                } else if (type == ChatMessage.TYPE_HUMAN_PLAYER) {
                    // Human player actions are buffered with the preceding TYPE_USER;
                    // look-ahead: check if there are more human player messages following
                    // and compile them all when the sequence ends.
                    // The preceding TYPE_USER already started a compiled message buffer,
                    // so append this human player action to it.
                    if (conversationHistory.isEmpty()) continue;
                    Content lastContent = conversationHistory.get(
                            conversationHistory.size() - 1);
                    if ("user".equals(lastContent.role().orElse(""))) {
                        String existing = lastContent.parts().get().get(0).text().orElse("");
                        String compiled = existing + "\n"
                                + (senderName != null ? senderName : "Player") + ": " + text;
                        conversationHistory.set(conversationHistory.size() - 1,
                                Content.builder()
                                        .role("user")
                                        .parts(Part.fromText(compiled))
                                        .build());
                    }
                } else if (type == ChatMessage.TYPE_USER) {
                    // Flush both buffers before adding user message
                    if (dmNpcBuffer.length() > 0) {
                        conversationHistory.add(Content.builder()
                                .role("model")
                                .parts(Part.fromText(dmNpcBuffer.toString()))
                                .build());
                        dmNpcBuffer.setLength(0);
                    }
                    if (aiCharacterBuffer.length() > 0) {
                        conversationHistory.add(Content.builder()
                                .role("user")
                                .parts(Part.fromText(buildPartyResponsePrompt()))
                                .build());
                        conversationHistory.add(Content.builder()
                                .role("model")
                                .parts(Part.fromText(aiCharacterBuffer.toString()))
                                .build());
                        aiCharacterBuffer.setLength(0);
                    }

                    // For hot-seat: look ahead to see if human player messages follow
                    boolean hasHumanFollowing = false;
                    if (isHotSeatActive() && i + 1 < chatArray.length()) {
                        int nextType = chatArray.getJSONObject(i + 1).optInt("type", -1);
                        hasHumanFollowing = (nextType == ChatMessage.TYPE_HUMAN_PLAYER);
                    }

                    if (hasHumanFollowing) {
                        // Start a compiled [PLAYER ACTIONS] message
                        String compiled = "[PLAYER ACTIONS]\n"
                                + selectedCharacterName + ": " + text;
                        conversationHistory.add(Content.builder()
                                .role("user")
                                .parts(Part.fromText(compiled))
                                .build());
                    } else {
                        conversationHistory.add(Content.builder()
                                .role("user")
                                .parts(Part.fromText(text))
                                .build());
                    }
                }
                // TYPE_IMAGE is skipped for conversation history
            }

            // Flush remaining buffers
            if (dmNpcBuffer.length() > 0) {
                conversationHistory.add(Content.builder()
                        .role("model")
                        .parts(Part.fromText(dmNpcBuffer.toString()))
                        .build());
            }
            if (aiCharacterBuffer.length() > 0) {
                conversationHistory.add(Content.builder()
                        .role("user")
                        .parts(Part.fromText(buildPartyResponsePrompt()))
                        .build());
                conversationHistory.add(Content.builder()
                        .role("model")
                        .parts(Part.fromText(aiCharacterBuffer.toString()))
                        .build());
            }

            chatAdapter.notifyDataSetChanged();
            recyclerChat.scrollToPosition(messages.size() - 1);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    // ========== Chat Message Model ==========

    static class ChatMessage {
        static final int TYPE_USER = 0;
        static final int TYPE_DM = 1;
        static final int TYPE_IMAGE = 2;
        static final int TYPE_AI_CHARACTER = 3;
        static final int TYPE_NPC = 4;
        static final int TYPE_HUMAN_PLAYER = 5;
        static final int TYPE_LEVEL_UP = 6;
        static final int TYPE_LOOT_DROP = 7;
        static final int TYPE_HP_CHANGE = 8;
        static final int TYPE_STAMINA_CHANGE = 9;
        static final int TYPE_DEATH = 10;
        static final int TYPE_EXHAUSTED = 11;
        static final int TYPE_ENCOUNTER_COMPLETE = 12;
        static final int TYPE_ENEMY_DEFEATED = 13;

        final String text;
        final int type;
        final String imageFileName;
        final String senderName;

        ChatMessage(String text, int type) {
            this(text, type, null, null);
        }

        ChatMessage(String text, int type, String imageFileName) {
            this(text, type, imageFileName, null);
        }

        ChatMessage(String text, int type, String imageFileName, String senderName) {
            this.text = text;
            this.type = type;
            this.imageFileName = imageFileName;
            this.senderName = senderName;
        }

        static ChatMessage aiCharacterMessage(String text, String characterName) {
            return new ChatMessage(text, TYPE_AI_CHARACTER, null, characterName);
        }

        static ChatMessage humanPlayerMessage(String text, String playerName) {
            return new ChatMessage(text, TYPE_HUMAN_PLAYER, null, playerName);
        }

        boolean hasImage() {
            return imageFileName != null && !imageFileName.isEmpty();
        }
    }

    // ========== Chat Adapter ==========

    interface OnImageClickListener {
        void onImageClick(String imagePath);
    }

    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

        private static final int CACHE_SIZE_KB = 8 * 1024; // 8 MB
        private static final LruCache<String, Bitmap> bitmapCache = new LruCache<String, Bitmap>(CACHE_SIZE_KB) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        // Pre-computed color constants to avoid Color.parseColor() on every bind
        private static final int COLOR_USER_BG = Color.parseColor("#E3F2FD");
        private static final int COLOR_USER_SENDER = Color.parseColor("#1565C0");
        private static final int COLOR_AI_CHAR_BG = Color.parseColor("#E8F5E9");
        private static final int COLOR_AI_CHAR_SENDER = Color.parseColor("#2E7D32");
        private static final int COLOR_HUMAN_PLAYER_BG = Color.parseColor("#E0F2F1");
        private static final int COLOR_HUMAN_PLAYER_SENDER = Color.parseColor("#00695C");
        private static final int COLOR_NPC_BG = Color.parseColor("#F3E5F5");
        private static final int COLOR_NPC_SENDER = Color.parseColor("#7B1FA2");
        private static final int COLOR_LEVEL_UP_BG = Color.parseColor("#FFF9C4");
        private static final int COLOR_LEVEL_UP_TEXT = Color.parseColor("#F57F17");
        private static final int COLOR_LOOT_BG = Color.parseColor("#E8F5E9");
        private static final int COLOR_LOOT_TEXT = Color.parseColor("#2E7D32");
        private static final int COLOR_DAMAGE_BG = Color.parseColor("#FFEBEE");
        private static final int COLOR_DAMAGE_TEXT = Color.parseColor("#C62828");
        private static final int COLOR_HEAL_BG = Color.parseColor("#E8F5E9");
        private static final int COLOR_HEAL_TEXT = Color.parseColor("#2E7D32");
        private static final int COLOR_STAMINA_DRAIN_BG = Color.parseColor("#E0F7FA");
        private static final int COLOR_STAMINA_DRAIN_TEXT = Color.parseColor("#00838F");
        private static final int COLOR_STAMINA_RECOVER_BG = Color.parseColor("#E0F2F1");
        private static final int COLOR_STAMINA_RECOVER_TEXT = Color.parseColor("#00695C");
        private static final int COLOR_DEATH_BG = Color.parseColor("#212121");
        private static final int COLOR_DEATH_TEXT = Color.parseColor("#F44336");
        private static final int COLOR_DEATH_MSG = Color.parseColor("#FFFFFF");
        private static final int COLOR_EXHAUSTED_BG = Color.parseColor("#FFF3E0");
        private static final int COLOR_EXHAUSTED_TEXT = Color.parseColor("#E65100");
        private static final int COLOR_RECOVERED_BG = Color.parseColor("#E0F2F1");
        private static final int COLOR_RECOVERED_TEXT = Color.parseColor("#00695C");
        private static final int COLOR_ENCOUNTER_BG = Color.parseColor("#E8EAF6");
        private static final int COLOR_ENCOUNTER_TEXT = Color.parseColor("#283593");
        private static final int COLOR_ENEMY_DEFEATED_BG = Color.parseColor("#FFEBEE");
        private static final int COLOR_ENEMY_DEFEATED_TEXT = Color.parseColor("#B71C1C");
        private static final int COLOR_DM_BG = Color.parseColor("#FFF3E0");
        private static final int COLOR_DM_SENDER = Color.parseColor("#E65100");
        private static final int COLOR_BODY_TEXT = Color.parseColor("#212121");

        private final List<ChatMessage> messages;
        private final File imageDir;
        private final OnImageClickListener imageClickListener;
        private int hiddenBeforeIndex = -1;

        ChatAdapter(List<ChatMessage> messages, File imageDir, OnImageClickListener listener) {
            this.messages = messages;
            this.imageDir = imageDir;
            this.imageClickListener = listener;
        }

        void setHiddenBeforeIndex(int index) {
            this.hiddenBeforeIndex = index;
        }

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message, parent, false);
            return new MessageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            if (hiddenBeforeIndex >= 0 && position < hiddenBeforeIndex) {
                holder.cardMessage.setVisibility(View.GONE);
                holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(0, 0));
                return;
            }
            holder.cardMessage.setVisibility(View.VISIBLE);
            holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ChatMessage msg = messages.get(position);
            holder.bind(msg, imageDir, imageClickListener);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class MessageViewHolder extends RecyclerView.ViewHolder {
            private final MaterialCardView cardMessage;
            private final TextView textSender;
            private final TextView textMessage;
            private final ImageView imageScene;

            MessageViewHolder(@NonNull View itemView) {
                super(itemView);
                cardMessage = itemView.findViewById(R.id.card_message);
                textSender = itemView.findViewById(R.id.text_sender);
                textMessage = itemView.findViewById(R.id.text_message);
                imageScene = itemView.findViewById(R.id.image_scene);
            }

            void bind(ChatMessage msg, File imageDir, OnImageClickListener imageClickListener) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) cardMessage.getLayoutParams();

                if (msg.type == ChatMessage.TYPE_USER) {
                    textSender.setText("You");
                    params.gravity = Gravity.END;
                    cardMessage.setCardBackgroundColor(COLOR_USER_BG);
                    textSender.setTextColor(COLOR_USER_SENDER);
                    textMessage.setTextColor(COLOR_BODY_TEXT);
                    imageScene.setVisibility(View.GONE);
                    imageScene.setOnClickListener(null);
                } else if (msg.type == ChatMessage.TYPE_AI_CHARACTER) {
                    textSender.setText(msg.senderName != null ? msg.senderName : "Party Member");
                    params.gravity = Gravity.START;
                    cardMessage.setCardBackgroundColor(COLOR_AI_CHAR_BG);
                    textSender.setTextColor(COLOR_AI_CHAR_SENDER);
                    textMessage.setTextColor(COLOR_BODY_TEXT);
                    imageScene.setVisibility(View.GONE);
                    imageScene.setOnClickListener(null);
                } else if (msg.type == ChatMessage.TYPE_HUMAN_PLAYER) {
                    textSender.setText(msg.senderName != null ? msg.senderName : "Player");
                    params.gravity = Gravity.END;
                    cardMessage.setCardBackgroundColor(COLOR_HUMAN_PLAYER_BG);
                    textSender.setTextColor(COLOR_HUMAN_PLAYER_SENDER);
                    textMessage.setTextColor(COLOR_BODY_TEXT);
                    imageScene.setVisibility(View.GONE);
                    imageScene.setOnClickListener(null);
                } else if (msg.type == ChatMessage.TYPE_NPC) {
                    textSender.setText(msg.senderName != null ? msg.senderName : "NPC");
                    params.gravity = Gravity.START;
                    cardMessage.setCardBackgroundColor(COLOR_NPC_BG);
                    textSender.setTextColor(COLOR_NPC_SENDER);
                    textMessage.setTextColor(COLOR_BODY_TEXT);
                    imageScene.setVisibility(View.GONE);
                    imageScene.setOnClickListener(null);
                } else if (msg.type == ChatMessage.TYPE_LEVEL_UP) {
                    textSender.setText("LEVEL UP!");
                    params.gravity = Gravity.CENTER_HORIZONTAL;
                    cardMessage.setCardBackgroundColor(COLOR_LEVEL_UP_BG);
                    textSender.setTextColor(COLOR_LEVEL_UP_TEXT);
                    textMessage.setTextColor(COLOR_LEVEL_UP_TEXT);
                    imageScene.setVisibility(View.GONE);
                    imageScene.setOnClickListener(null);
                } else if (msg.type == ChatMessage.TYPE_LOOT_DROP) {
                    textSender.setText("LOOT FOUND!");
                    params.gravity = Gravity.CENTER_HORIZONTAL;
                    cardMessage.setCardBackgroundColor(COLOR_LOOT_BG);
                    textSender.setTextColor(COLOR_LOOT_TEXT);
                    textMessage.setTextColor(COLOR_LOOT_TEXT);
                    imageScene.setVisibility(View.GONE);
                    imageScene.setOnClickListener(null);
                } else if (msg.type == ChatMessage.TYPE_HP_CHANGE) {
                    boolean isDamage = msg.text.contains("-") || msg.text.toLowerCase().contains("damage")
                            || msg.text.toLowerCase().contains("unconscious");
                    textSender.setText(isDamage ? "DAMAGE" : "HEALED");
                    params.gravity = Gravity.CENTER_HORIZONTAL;
                    cardMessage.setCardBackgroundColor(isDamage ? COLOR_DAMAGE_BG : COLOR_HEAL_BG);
                    textSender.setTextColor(isDamage ? COLOR_DAMAGE_TEXT : COLOR_HEAL_TEXT);
                    textMessage.setTextColor(isDamage ? COLOR_DAMAGE_TEXT : COLOR_HEAL_TEXT);
                    imageScene.setVisibility(View.GONE);
                    imageScene.setOnClickListener(null);
                } else if (msg.type == ChatMessage.TYPE_STAMINA_CHANGE) {
                    boolean isDrain = msg.text.contains("-") || msg.text.toLowerCase().contains("drain")
                            || msg.text.toLowerCase().contains("exhausted");
                    textSender.setText(isDrain ? "STAMINA DRAIN" : "STAMINA RECOVERED");
                    params.gravity = Gravity.CENTER_HORIZONTAL;
                    cardMessage.setCardBackgroundColor(isDrain ? COLOR_STAMINA_DRAIN_BG : COLOR_STAMINA_RECOVER_BG);
                    textSender.setTextColor(isDrain ? COLOR_STAMINA_DRAIN_TEXT : COLOR_STAMINA_RECOVER_TEXT);
                    textMessage.setTextColor(isDrain ? COLOR_STAMINA_DRAIN_TEXT : COLOR_STAMINA_RECOVER_TEXT);
                    imageScene.setVisibility(View.GONE);
                    imageScene.setOnClickListener(null);
                } else if (msg.type == ChatMessage.TYPE_DEATH) {
                    textSender.setText("DEATH");
                    params.gravity = Gravity.CENTER_HORIZONTAL;
                    cardMessage.setCardBackgroundColor(COLOR_DEATH_BG);
                    textSender.setTextColor(COLOR_DEATH_TEXT);
                    textMessage.setTextColor(COLOR_DEATH_MSG);
                    imageScene.setVisibility(View.GONE);
                    imageScene.setOnClickListener(null);
                } else if (msg.type == ChatMessage.TYPE_EXHAUSTED) {
                    boolean isRecovery = msg.text.toLowerCase().contains("recovered");
                    textSender.setText(isRecovery ? "RECOVERED" : "EXHAUSTED");
                    params.gravity = Gravity.CENTER_HORIZONTAL;
                    cardMessage.setCardBackgroundColor(
                            isRecovery ? COLOR_RECOVERED_BG : COLOR_EXHAUSTED_BG);
                    textSender.setTextColor(
                            isRecovery ? COLOR_RECOVERED_TEXT : COLOR_EXHAUSTED_TEXT);
                    textMessage.setTextColor(
                            isRecovery ? COLOR_RECOVERED_TEXT : COLOR_EXHAUSTED_TEXT);
                    imageScene.setVisibility(View.GONE);
                    imageScene.setOnClickListener(null);
                } else if (msg.type == ChatMessage.TYPE_ENCOUNTER_COMPLETE) {
                    textSender.setText("ENCOUNTER COMPLETE");
                    params.gravity = Gravity.CENTER_HORIZONTAL;
                    cardMessage.setCardBackgroundColor(COLOR_ENCOUNTER_BG);
                    textSender.setTextColor(COLOR_ENCOUNTER_TEXT);
                    textMessage.setTextColor(COLOR_ENCOUNTER_TEXT);
                    imageScene.setVisibility(View.GONE);
                    imageScene.setOnClickListener(null);
                } else if (msg.type == ChatMessage.TYPE_ENEMY_DEFEATED) {
                    textSender.setText("ENEMY DEFEATED");
                    params.gravity = Gravity.CENTER_HORIZONTAL;
                    cardMessage.setCardBackgroundColor(COLOR_ENEMY_DEFEATED_BG);
                    textSender.setTextColor(COLOR_ENEMY_DEFEATED_TEXT);
                    textMessage.setTextColor(COLOR_ENEMY_DEFEATED_TEXT);
                    imageScene.setVisibility(View.GONE);
                    imageScene.setOnClickListener(null);
                } else {
                    textSender.setText("Dungeon Master");
                    params.gravity = Gravity.START;
                    cardMessage.setCardBackgroundColor(COLOR_DM_BG);
                    textSender.setTextColor(COLOR_DM_SENDER);
                    textMessage.setTextColor(COLOR_BODY_TEXT);

                    if (msg.hasImage() && imageDir != null) {
                        File imageFile = new File(imageDir, msg.imageFileName);
                        if (imageFile.exists()) {
                            String path = imageFile.getAbsolutePath();
                            Bitmap bitmap = bitmapCache.get(path);
                            if (bitmap == null) {
                                bitmap = BitmapFactory.decodeFile(path);
                                if (bitmap != null) {
                                    bitmapCache.put(path, bitmap);
                                }
                            }
                            if (bitmap != null) {
                                imageScene.setImageBitmap(bitmap);
                                imageScene.setVisibility(View.VISIBLE);
                                imageScene.setOnClickListener(v -> {
                                    if (imageClickListener != null) {
                                        imageClickListener.onImageClick(imageFile.getAbsolutePath());
                                    }
                                });
                            } else {
                                imageScene.setVisibility(View.GONE);
                                imageScene.setOnClickListener(null);
                            }
                        } else {
                            imageScene.setVisibility(View.GONE);
                            imageScene.setOnClickListener(null);
                        }
                    } else {
                        imageScene.setVisibility(View.GONE);
                        imageScene.setOnClickListener(null);
                    }
                }

                if (msg.text != null && !msg.text.isEmpty()) {
                    textMessage.setText(msg.text);
                    textMessage.setVisibility(View.VISIBLE);
                } else {
                    textMessage.setVisibility(View.GONE);
                }

                cardMessage.setLayoutParams(params);
            }
        }
    }

    // ========== Fullscreen Image ==========

    private void showFullscreenImage(String imagePath) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_fullscreen_image);

        ImageView imageFullscreen = dialog.findViewById(R.id.image_fullscreen);
        ImageButton btnClose = dialog.findViewById(R.id.btn_close);

        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap != null) {
            imageFullscreen.setImageBitmap(bitmap);
        }

        // Tap image to dismiss
        imageFullscreen.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // ========== Map Integration ==========

    private void toggleMapAccess() {
        if (!mapAccessEnabled) {
            // Check if a map is actually loaded
            SharedPreferences mapPrefs = getSharedPreferences(MAP_PREFS, MODE_PRIVATE);
            String mapUri = mapPrefs.getString("map_uri", null);
            if (mapUri == null) {
                Toast.makeText(this, R.string.dm_no_map, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        mapAccessEnabled = !mapAccessEnabled;

        // Visual state: filled when on, outlined when off
        if (mapAccessEnabled) {
            btnMapToggle.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF4CAF50));
            btnMapToggle.setIconTint(
                    android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            Toast.makeText(this, R.string.dm_map_enabled, Toast.LENGTH_SHORT).show();
        } else {
            btnMapToggle.setBackgroundTintList(null);
            btnMapToggle.setIconTint(null);
            Toast.makeText(this, R.string.dm_map_disabled, Toast.LENGTH_SHORT).show();
        }

        // Rebuild system prompt with or without map section
        try {
            String systemPrompt = buildSystemPrompt(selectedCharacter);
            geminiConfig = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                    .build();
        } catch (JSONException ignored) {
        }
    }

    private String buildMapSection() {
        if (!mapAccessEnabled) return "";

        SharedPreferences mapPrefs = getSharedPreferences(MAP_PREFS, MODE_PRIVATE);
        int imgWidth = mapPrefs.getInt("map_image_width", 0);
        int imgHeight = mapPrefs.getInt("map_image_height", 0);
        if (imgWidth == 0 || imgHeight == 0) return "";

        String pointsJson = mapPrefs.getString(KEY_MAP_POINTS, null);
        StringBuilder locations = new StringBuilder();
        StringBuilder characters = new StringBuilder();

        if (pointsJson != null) {
            try {
                JSONArray points = new JSONArray(pointsJson);
                for (int i = 0; i < points.length(); i++) {
                    JSONObject p = points.getJSONObject(i);
                    String label = p.getString("label");
                    int xPct = Math.round((float) p.getDouble("x") / imgWidth * 100);
                    int yPct = Math.round((float) p.getDouble("y") / imgHeight * 100);
                    boolean isChar = p.optBoolean("isCharacter", false);

                    if (isChar) {
                        if (characters.length() > 0) characters.append(", ");
                        characters.append(label).append(" (").append(xPct).append("%, ").append(yPct).append("%)");
                    } else {
                        if (locations.length() > 0) locations.append(", ");
                        locations.append(label).append(" (").append(xPct).append("%, ").append(yPct).append("%)");
                    }
                }
            } catch (JSONException ignored) {
            }
        }

        StringBuilder mapSection = new StringBuilder();
        mapSection.append("\n\nMAP ACCESS:\n")
                .append("You have access to the game map. Current state:\n")
                .append("- Locations: ").append(locations.length() > 0 ? locations : "none").append("\n")
                .append("- Characters: ").append(characters.length() > 0 ? characters : "none").append("\n")
                .append("\nTo modify the map, include these commands in your response:\n")
                .append("[MAP:CREATE_LOCATION name=\"Name\" x=N y=N]  — Add a new location\n")
                .append("[MAP:CREATE_CHARACTER name=\"Name\" x=N y=N]  — Add a character token\n")
                .append("[MAP:MOVE name=\"Name\" x=N y=N]             — Move an existing point\n")
                .append("[MAP:REMOVE name=\"Name\"]                    — Remove a point\n")
                .append("\nCoordinates are percentages (0-100) where (0,0) is top-left and (100,100) is bottom-right.\n")
                .append("Use these commands naturally as the story unfolds. The player will see the changes on their map.\n")
                .append("IMPORTANT: When characters travel or move to a new location, you MUST use [MAP:MOVE] to update their position. ")
                .append("Move ALL characters who are traveling together, not just one. ")
                .append("Every player character, AI party member, and relevant NPC should be moved when they change location.\n");

        if (selectedScenario != null) {
            int npcTarget = selectedScenario.optInt("npcCount", 0);
            int locationTarget = selectedScenario.optInt("locationCount", 0);
            if (locationTarget > 0) {
                mapSection.append("The scenario calls for exactly ").append(locationTarget)
                        .append(" locations total on the map. Create all of them in the opening.\n");
            }
            if (npcTarget > 0) {
                mapSection.append("The scenario calls for exactly ").append(npcTarget)
                        .append(" NPCs total on the map. Create all of them as characters in the opening.\n");
            }
        }

        return mapSection.toString();
    }

    private String buildMapContext() {
        SharedPreferences mapPrefs = getSharedPreferences(MAP_PREFS, MODE_PRIVATE);
        int imgWidth = mapPrefs.getInt("map_image_width", 0);
        int imgHeight = mapPrefs.getInt("map_image_height", 0);
        if (imgWidth == 0 || imgHeight == 0) return "";

        String pointsJson = mapPrefs.getString(KEY_MAP_POINTS, null);
        if (pointsJson == null) return "[Current map: empty]";

        try {
            JSONArray points = new JSONArray(pointsJson);
            if (points.length() == 0) return "[Current map: empty]";

            StringBuilder sb = new StringBuilder("[Current map state: ");
            for (int i = 0; i < points.length(); i++) {
                JSONObject p = points.getJSONObject(i);
                if (i > 0) sb.append(", ");
                String label = p.getString("label");
                int xPct = Math.round((float) p.getDouble("x") / imgWidth * 100);
                int yPct = Math.round((float) p.getDouble("y") / imgHeight * 100);
                boolean isChar = p.optBoolean("isCharacter", false);
                sb.append(label);
                if (isChar) sb.append(" (character)");
                sb.append(" at ").append(xPct).append("%,").append(yPct).append("%");
            }
            sb.append("]");
            return sb.toString();
        } catch (JSONException e) {
            return "[Current map: empty]";
        }
    }

    private String parseAndExecuteMapCommands(String response) {
        SharedPreferences mapPrefs = getSharedPreferences(MAP_PREFS, MODE_PRIVATE);
        int imgWidth = mapPrefs.getInt("map_image_width", 0);
        int imgHeight = mapPrefs.getInt("map_image_height", 0);
        if (imgWidth == 0 || imgHeight == 0) {
            // Strip tags even if we can't execute
            return MAP_COMMAND_PATTERN.matcher(response).replaceAll("").trim();
        }

        // Load current points
        String pointsJson = mapPrefs.getString(KEY_MAP_POINTS, "[]");
        JSONArray points;
        try {
            points = new JSONArray(pointsJson);
        } catch (JSONException e) {
            points = new JSONArray();
        }

        List<String> actions = new ArrayList<>();
        Matcher matcher = MAP_COMMAND_PATTERN.matcher(response);

        while (matcher.find()) {
            String action = matcher.group(1);
            String name = matcher.group(2);
            String xStr = matcher.group(3);
            String yStr = matcher.group(4);

            try {
                switch (action) {
                    case "CREATE_LOCATION": {
                        if (xStr == null || yStr == null) break;
                        int xPct = Integer.parseInt(xStr);
                        int yPct = Integer.parseInt(yStr);
                        float imgX = xPct / 100f * imgWidth;
                        float imgY = yPct / 100f * imgHeight;
                        JSONObject newPoint = new JSONObject();
                        newPoint.put("x", imgX);
                        newPoint.put("y", imgY);
                        newPoint.put("label", name);
                        newPoint.put("isCharacter", false);
                        points.put(newPoint);
                        actions.add("Created " + name);
                        break;
                    }
                    case "CREATE_CHARACTER": {
                        if (xStr == null || yStr == null) break;
                        int xPct = Integer.parseInt(xStr);
                        int yPct = Integer.parseInt(yStr);
                        float imgX = xPct / 100f * imgWidth;
                        float imgY = yPct / 100f * imgHeight;
                        JSONObject newPoint = new JSONObject();
                        newPoint.put("x", imgX);
                        newPoint.put("y", imgY);
                        newPoint.put("label", name);
                        newPoint.put("isCharacter", true);
                        points.put(newPoint);
                        actions.add("Created " + name);
                        break;
                    }
                    case "MOVE": {
                        if (xStr == null || yStr == null) break;
                        int xPct = Integer.parseInt(xStr);
                        int yPct = Integer.parseInt(yStr);
                        float imgX = xPct / 100f * imgWidth;
                        float imgY = yPct / 100f * imgHeight;
                        for (int i = 0; i < points.length(); i++) {
                            JSONObject p = points.getJSONObject(i);
                            if (name.equalsIgnoreCase(p.getString("label"))) {
                                p.put("x", imgX);
                                p.put("y", imgY);
                                actions.add("Moved " + name);
                                break;
                            }
                        }
                        break;
                    }
                    case "REMOVE": {
                        for (int i = 0; i < points.length(); i++) {
                            JSONObject p = points.getJSONObject(i);
                            if (name.equalsIgnoreCase(p.getString("label"))) {
                                points.remove(i);
                                actions.add("Removed " + name);
                                break;
                            }
                        }
                        break;
                    }
                }
            } catch (JSONException | NumberFormatException ignored) {
            }
        }

        // Save updated points back to SharedPreferences
        if (!actions.isEmpty()) {
            separateOverlappingPoints(points, imgWidth, imgHeight);
            mapPrefs.edit().putString(KEY_MAP_POINTS, points.toString()).apply();
            String summary = String.join(", ", actions);
            Toast.makeText(this, getString(R.string.dm_map_action, summary), Toast.LENGTH_LONG).show();
        }

        // Strip all [MAP:...] tags from displayed text
        return matcher.reset().replaceAll("").trim();
    }

    private void separateOverlappingPoints(JSONArray points, int imgWidth, int imgHeight) {
        int count = points.length();
        if (count < 2) return;

        double minDist = 0.07 * Math.min(imgWidth, imgHeight);
        double minX = 0.02 * imgWidth;
        double maxX = 0.98 * imgWidth;
        double minY = 0.02 * imgHeight;
        double maxY = 0.98 * imgHeight;

        for (int iter = 0; iter < 10; iter++) {
            boolean moved = false;
            try {
                for (int i = 0; i < count; i++) {
                    for (int j = i + 1; j < count; j++) {
                        JSONObject pi = points.getJSONObject(i);
                        JSONObject pj = points.getJSONObject(j);
                        double xi = pi.getDouble("x");
                        double yi = pi.getDouble("y");
                        double xj = pj.getDouble("x");
                        double yj = pj.getDouble("y");

                        double dx = xj - xi;
                        double dy = yj - yi;
                        double dist = Math.sqrt(dx * dx + dy * dy);

                        if (dist < minDist) {
                            moved = true;
                            if (dist < 0.001) {
                                // Nearly identical positions — nudge diagonally
                                dx = 1.0;
                                dy = 1.0;
                                dist = Math.sqrt(2.0);
                            }
                            double overlap = minDist - dist;
                            double pushX = (dx / dist) * (overlap / 2);
                            double pushY = (dy / dist) * (overlap / 2);

                            pi.put("x", Math.max(minX, Math.min(maxX, xi - pushX)));
                            pi.put("y", Math.max(minY, Math.min(maxY, yi - pushY)));
                            pj.put("x", Math.max(minX, Math.min(maxX, xj + pushX)));
                            pj.put("y", Math.max(minY, Math.min(maxY, yj + pushY)));
                        }
                    }
                }
            } catch (JSONException ignored) {
            }
            if (!moved) break;
        }
    }

    // ========== Rules Advisor ==========

    private void setupRulesRecyclerView() {
        rulesAdapter = new ChatAdapter(rulesMessages, null, null);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerRulesChat.setLayoutManager(layoutManager);
        recyclerRulesChat.setAdapter(rulesAdapter);
    }

    private GenerateContentConfig buildRulesAdvisorConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a D&D 5th Edition rules reference assistant. ")
                .append("Your ONLY purpose is to answer questions about D&D 5e rules, mechanics, ")
                .append("spells, abilities, combat, classes, races, items, and conditions.\n\n")
                .append("RULES:\n")
                .append("- Answer rules questions accurately based on official D&D 5e rules (RAW) by default.\n")
                .append("- Cite the relevant rulebook or section when possible (e.g., PHB p.195).\n")
                .append("- Use bullet points for clarity when listing multiple rules or steps.\n")
                .append("- Do NOT narrate, roleplay, or act as a Dungeon Master.\n")
                .append("- Do NOT generate story content, encounters, or NPC dialogue.\n")
                .append("- Do NOT modify or affect any ongoing game or adventure.\n")
                .append("- Keep answers concise and focused on the rules question asked.\n")
                .append("- If a question is ambiguous, explain both RAW and common rulings.\n")
                .append("- When the player's character context is provided below, tailor your answers ")
                .append("to their specific class, race, level, and abilities where relevant.\n");

        // Include character context if available
        if (selectedCharacter != null) {
            try {
                String name = selectedCharacter.getString("name");
                String race = races[selectedCharacter.getInt("race")];
                String charClass = classes[selectedCharacter.getInt("class")];
                int level = runLevel > 0 ? runLevel : selectedCharacter.getInt("level");
                String background = backgrounds[selectedCharacter.getInt("background")];
                String alignment = alignments[selectedCharacter.getInt("alignment")];

                sb.append("\nPLAYER CHARACTER (for context only — do NOT alter game state):\n")
                        .append("Name: ").append(name).append("\n")
                        .append("Race: ").append(race).append("\n")
                        .append("Class: ").append(charClass).append("\n")
                        .append("Level: ").append(level).append("\n")
                        .append("Background: ").append(background).append("\n")
                        .append("Alignment: ").append(alignment).append("\n")
                        .append("STR: ").append(selectedCharacter.getInt("str"))
                        .append(" DEX: ").append(selectedCharacter.getInt("dex"))
                        .append(" CON: ").append(selectedCharacter.getInt("con"))
                        .append(" INT: ").append(selectedCharacter.getInt("int"))
                        .append(" WIS: ").append(selectedCharacter.getInt("wis"))
                        .append(" CHA: ").append(selectedCharacter.getInt("cha")).append("\n")
                        .append("HP: ").append(currentHP).append("/").append(maxHP)
                        .append("  Stamina: ").append(currentStamina).append("/").append(maxStamina).append("\n")
                        .append("AC: ").append(selectedCharacter.getInt("ac"))
                        .append("  Speed: ").append(selectedCharacter.getInt("speed")).append(" ft\n");

                String notes = selectedCharacter.optString("notes", "");
                if (!notes.isEmpty()) {
                    sb.append("Notes: ").append(notes).append("\n");
                }

                sb.append(buildCustomAbilitiesSection(selectedCharacter));
            } catch (JSONException ignored) {
            }

            // Include party members
            if (!aiPartyMembers.isEmpty() || !humanPlayers.isEmpty()) {
                sb.append("\nPARTY MEMBERS:\n");
                for (JSONObject member : aiPartyMembers) {
                    appendMemberSummary(sb, member, "AI");
                }
                for (JSONObject member : humanPlayers) {
                    appendMemberSummary(sb, member, "Human");
                }
            }
        }

        // Include rulebook so the advisor can reference the active rule system
        String rulebookContent = null;
        if (selectedScenario != null) {
            String custom = selectedScenario.optString("rulebookText", "");
            if (!custom.isEmpty()) {
                rulebookContent = custom;
            }
        }
        if (rulebookContent == null) {
            loadDefaultRulebook();
            rulebookContent = cachedDefaultRulebook;
        }
        if (rulebookContent != null && !rulebookContent.isEmpty()) {
            sb.append("\nRULEBOOK REFERENCE (use these rules when answering questions):\n")
                    .append(rulebookContent).append("\n");
        }

        return GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(sb.toString())))
                .build();
    }

    private void appendMemberSummary(StringBuilder sb, JSONObject member, String controlType) {
        try {
            sb.append("- ").append(member.getString("name"))
                    .append(" (").append(controlType).append("): Level ")
                    .append(member.getInt("level")).append(" ")
                    .append(races[member.getInt("race")]).append(" ")
                    .append(classes[member.getInt("class")]);
            int[] stats = memberStats.get(member.getString("name"));
            if (stats != null) {
                sb.append(" | HP: ").append(stats[0]).append("/").append(stats[1])
                        .append(" Stamina: ").append(stats[2]).append("/").append(stats[3]);
            }
            sb.append("\n");
        } catch (JSONException ignored) {
        }
    }

    private void onRulesSendClicked() {
        String text = editRulesMessage.getText() != null
                ? editRulesMessage.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        if (geminiClient == null) {
            Toast.makeText(this, R.string.dm_no_api_key, Toast.LENGTH_LONG).show();
            return;
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(this, R.string.dm_no_network, Toast.LENGTH_LONG).show();
            return;
        }

        editRulesMessage.setText("");

        // Add user message to rules chat
        rulesMessages.add(new ChatMessage(text, ChatMessage.TYPE_USER));
        rulesAdapter.notifyItemInserted(rulesMessages.size() - 1);
        recyclerRulesChat.scrollToPosition(rulesMessages.size() - 1);

        setRulesLoading(true);

        // Add to rules conversation history
        Content userContent = Content.builder()
                .role("user")
                .parts(Part.fromText(text))
                .build();
        rulesConversationHistory.add(userContent);

        List<Content> requestContents = new ArrayList<>(rulesConversationHistory);

        executor.execute(() -> {
            try {
                GenerateContentResponse response = geminiClient.models.generateContent(
                        "gemini-2.5-flash", requestContents, rulesGeminiConfig);
                String reply = response.text();

                Content modelContent = Content.builder()
                        .role("model")
                        .parts(Part.fromText(reply != null ? reply : ""))
                        .build();
                rulesConversationHistory.add(modelContent);

                runOnUiThread(() -> {
                    rulesMessages.add(new ChatMessage(
                            reply != null ? reply : "(No response)", ChatMessage.TYPE_DM));
                    rulesAdapter.notifyItemInserted(rulesMessages.size() - 1);
                    recyclerRulesChat.scrollToPosition(rulesMessages.size() - 1);
                    setRulesLoading(false);
                });
            } catch (Exception e) {
                rulesConversationHistory.remove(rulesConversationHistory.size() - 1);

                runOnUiThread(() -> {
                    String errorMsg;
                    if (e instanceof java.net.UnknownHostException
                            || e instanceof java.net.ConnectException
                            || e instanceof java.net.SocketTimeoutException) {
                        errorMsg = getString(R.string.dm_no_network);
                    } else {
                        errorMsg = getString(R.string.dm_error) + "\n" + e.getMessage();
                    }
                    rulesMessages.add(new ChatMessage(errorMsg, ChatMessage.TYPE_DM));
                    rulesAdapter.notifyItemInserted(rulesMessages.size() - 1);
                    recyclerRulesChat.scrollToPosition(rulesMessages.size() - 1);
                    setRulesLoading(false);
                });
            }
        });
    }

    private void setRulesLoading(boolean loading) {
        layoutRulesLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRulesSend.setEnabled(!loading);
        if (loading) {
            recyclerRulesChat.scrollToPosition(rulesMessages.size() - 1);
        }
    }

    // ========== Navigation ==========

    @Override
    public boolean onSupportNavigateUp() {
        if (awaitingHumanTurns) {
            showBackDuringTurnsWarning();
            return true;
        }
        finish();
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END);
            return;
        }
        if (awaitingHumanTurns) {
            showBackDuringTurnsWarning();
            return;
        }
        super.onBackPressed();
    }

    private void showBackDuringTurnsWarning() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.dm_back_during_turns)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    resetTurnState();
                    finish();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (awaitingHumanTurns) {
            saveChat();
            saveRunToFile();
        }
        if (radioServiceBound) {
            unbindService(radioServiceConnection);
            radioServiceBound = false;
        }
        if (sfx != null) {
            sfx.release();
        }
        super.onDestroy();
        executor.shutdownNow();
    }

    @Override
    public void onRadioStateChanged(boolean playing) {
        updateRadioButtonTint();
    }

    private void ensureRadioServiceBound() {
        if (!radioServiceBound) {
            Intent radioIntent = new Intent(this, RadioService.class);
            bindService(radioIntent, radioServiceConnection, BIND_AUTO_CREATE);
        }
    }

    private void updateRadioButtonTint() {
        // Radio button moved to options menu; no tinting needed
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dungeon_master, menu);
        MenuItem sfxItem = menu.findItem(R.id.action_sfx_toggle);
        sfxItem.setChecked(sfx.isEnabled());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_radio) {
            ensureRadioServiceBound();
            RadioBottomSheetFragment sheet = new RadioBottomSheetFragment();
            sheet.show(getSupportFragmentManager(), "radio");
            return true;
        } else if (id == R.id.action_sfx_toggle) {
            sfx.setEnabled(this, !sfx.isEnabled());
            item.setChecked(sfx.isEnabled());
            Toast.makeText(this,
                    sfx.isEnabled() ? R.string.sfx_enabled : R.string.sfx_disabled,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
