package com.example.boardgames;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DungeonMasterActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "character_prefs";
    private static final String KEY_CHARACTERS = "saved_characters";
    private static final String DM_CHAT_PREFS = "dm_chat_prefs";
    private static final String KEY_CHAT_MESSAGES = "chat_messages_";
    private static final String KEY_LAST_CHARACTER = "last_character";

    private MaterialCardView cardCharacterInfo;
    private TextView textCharacterInfo;
    private RecyclerView recyclerChat;
    private LinearLayout layoutLoading;
    private LinearLayout layoutInput;
    private TextInputEditText editMessage;
    private MaterialButton btnSend;
    private MaterialButton btnIllustrate;
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
    private String selectedCharacterName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dungeon_master);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        races = getResources().getStringArray(R.array.cc_races);
        classes = getResources().getStringArray(R.array.cc_classes);
        backgrounds = getResources().getStringArray(R.array.cc_backgrounds);
        alignments = getResources().getStringArray(R.array.cc_alignments);

        bindViews();
        setupRecyclerView();

        btnSend.setOnClickListener(v -> onSendClicked());
        btnIllustrate.setOnClickListener(v -> onIllustrateClicked());

        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your_key_here")) {
            Toast.makeText(this, R.string.dm_no_api_key, Toast.LENGTH_LONG).show();
            return;
        }

        geminiClient = Client.builder().apiKey(apiKey).build();
        showCharacterSelectionDialog();
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
        textLoading = findViewById(R.id.text_loading);
    }

    private void setupRecyclerView() {
        File imageDir = new File(getFilesDir(), "dm_images");
        chatAdapter = new ChatAdapter(messages, imageDir);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerChat.setLayoutManager(layoutManager);
        recyclerChat.setAdapter(chatAdapter);
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
                    .setCancelable(false)
                    .show();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.dm_no_characters, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void onCharacterSelected(JSONObject character) throws JSONException {
        String name = character.getString("name");
        int level = character.getInt("level");
        String race = races[character.getInt("race")];
        String charClass = classes[character.getInt("class")];
        selectedCharacterName = name;

        // Show character info bar
        String infoLabel = getString(R.string.dm_character_label, name, level, race, charClass);
        textCharacterInfo.setText(infoLabel);
        cardCharacterInfo.setVisibility(View.VISIBLE);
        layoutInput.setVisibility(View.VISIBLE);

        // Build system prompt and config
        String systemPrompt = buildSystemPrompt(character);
        geminiConfig = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                .build();

        // Try to restore saved chat
        if (restoreChat()) {
            return;
        }

        // No saved chat - generate opening scene
        String openingPrompt = "Begin a new D&D adventure. Introduce the setting and give " + name
                + " a compelling opening scene. End with a clear prompt for the player's first action.";
        sendToGemini(openingPrompt, false);
    }

    private String buildSystemPrompt(JSONObject character) throws JSONException {
        String name = character.getString("name");
        String race = races[character.getInt("race")];
        String charClass = classes[character.getInt("class")];
        int level = character.getInt("level");
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

        return "You are an experienced and creative Dungeons & Dragons 5th Edition Dungeon Master. "
                + "You are running a solo adventure for a player with the following character:\n\n"
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
                + "CHA: " + cha + " (" + modString(cha) + ")\n\n"
                + "COMBAT STATS:\n"
                + "HP: " + hp + "\n"
                + "AC: " + ac + "\n"
                + "Speed: " + speed + " ft\n"
                + "Initiative: " + modString(initiative) + "\n"
                + (notes.isEmpty() ? "" : "\nNOTES: " + notes + "\n")
                + "\nRULES FOR THE DM:\n"
                + "- Reference the character's actual stats when determining outcomes.\n"
                + "- When an action requires a check, roll dice using standard D&D 5e mechanics "
                + "(d20 + relevant modifier). Show the roll and modifier in parentheses.\n"
                + "- Present results in engaging narrative form with dice roll details.\n"
                + "- Drive an engaging plot with encounters, puzzles, NPCs, and story hooks.\n"
                + "- Keep responses concise (2-4 short paragraphs max).\n"
                + "- Always end with a clear prompt for the player's next action.\n"
                + "- Track the character's HP and mention it when damage is taken or healed.\n"
                + "- Use the character's class abilities, race traits, and background appropriately.\n";
    }

    private String modString(int score) {
        int mod = Math.floorDiv(score - 10, 2);
        return (mod >= 0) ? "+" + mod : String.valueOf(mod);
    }

    // ========== Messaging ==========

    private void onSendClicked() {
        String text = editMessage.getText() != null ? editMessage.getText().toString().trim() : "";
        if (text.isEmpty() || geminiClient == null) return;

        addMessage(text, ChatMessage.TYPE_USER);
        editMessage.setText("");

        sendToGemini(text, true);
    }

    private void sendToGemini(String userText, boolean showAsUserMessage) {
        setLoading(true);

        // Add user content to conversation history
        Content userContent = Content.builder()
                .role("user")
                .parts(Part.fromText(userText))
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
                    addMessage(reply != null ? reply : "(No response)", ChatMessage.TYPE_DM);
                    setLoading(false);
                    saveChat();
                });
            } catch (Exception e) {
                // Remove the failed user content from history
                conversationHistory.remove(conversationHistory.size() - 1);

                runOnUiThread(() -> {
                    addMessage(getString(R.string.dm_error) + "\n" + e.getMessage(), ChatMessage.TYPE_DM);
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
        String hfKey = BuildConfig.HF_API_KEY;
        if (hfKey == null || hfKey.isEmpty() || hfKey.equals("your_hf_token_here")) {
            Toast.makeText(this, "HuggingFace API key not configured. Add HF_API_KEY to .env",
                    Toast.LENGTH_LONG).show();
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
                    });
                }

            } catch (Exception e) {
                String exMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> {
                    addMessage(getString(R.string.dm_image_error) + "\n" + exMsg,
                            ChatMessage.TYPE_DM);
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

    // ========== Chat Persistence ==========

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
                chatArray.put(msgObj);
            }

            SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_CHAT_MESSAGES + selectedCharacterName, chatArray.toString())
                    .putString(KEY_LAST_CHARACTER, selectedCharacterName)
                    .apply();
        } catch (JSONException ignored) {
        }
    }

    private boolean restoreChat() {
        if (selectedCharacterName == null) return false;

        SharedPreferences prefs = getSharedPreferences(DM_CHAT_PREFS, MODE_PRIVATE);
        String saved = prefs.getString(KEY_CHAT_MESSAGES + selectedCharacterName, null);
        if (saved == null) return false;

        try {
            JSONArray chatArray = new JSONArray(saved);
            if (chatArray.length() == 0) return false;

            messages.clear();
            conversationHistory.clear();

            for (int i = 0; i < chatArray.length(); i++) {
                JSONObject msgObj = chatArray.getJSONObject(i);
                String text = msgObj.getString("text");
                int type = msgObj.getInt("type");
                String imageFileName = msgObj.optString("imageFileName", null);

                if (imageFileName != null) {
                    messages.add(new ChatMessage(text, type, imageFileName));
                } else {
                    messages.add(new ChatMessage(text, type));
                }

                // Rebuild Gemini conversation history (skip image messages)
                if (type == ChatMessage.TYPE_USER || type == ChatMessage.TYPE_DM) {
                    String role = (type == ChatMessage.TYPE_USER) ? "user" : "model";
                    Content content = Content.builder()
                            .role(role)
                            .parts(Part.fromText(text))
                            .build();
                    conversationHistory.add(content);
                }
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

        final String text;
        final int type;
        final String imageFileName;

        ChatMessage(String text, int type) {
            this.text = text;
            this.type = type;
            this.imageFileName = null;
        }

        ChatMessage(String text, int type, String imageFileName) {
            this.text = text;
            this.type = type;
            this.imageFileName = imageFileName;
        }

        boolean hasImage() {
            return imageFileName != null && !imageFileName.isEmpty();
        }
    }

    // ========== Chat Adapter ==========

    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

        private final List<ChatMessage> messages;
        private final File imageDir;

        ChatAdapter(List<ChatMessage> messages, File imageDir) {
            this.messages = messages;
            this.imageDir = imageDir;
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
            ChatMessage msg = messages.get(position);
            holder.bind(msg, imageDir);
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

            void bind(ChatMessage msg, File imageDir) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) cardMessage.getLayoutParams();

                if (msg.type == ChatMessage.TYPE_USER) {
                    textSender.setText("You");
                    params.gravity = Gravity.END;
                    cardMessage.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
                    textSender.setTextColor(Color.parseColor("#1565C0"));
                    textMessage.setTextColor(Color.parseColor("#212121"));
                    imageScene.setVisibility(View.GONE);
                } else {
                    textSender.setText("Dungeon Master");
                    params.gravity = Gravity.START;
                    cardMessage.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                    textSender.setTextColor(Color.parseColor("#E65100"));
                    textMessage.setTextColor(Color.parseColor("#212121"));

                    if (msg.hasImage() && imageDir != null) {
                        File imageFile = new File(imageDir, msg.imageFileName);
                        if (imageFile.exists()) {
                            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                            if (bitmap != null) {
                                imageScene.setImageBitmap(bitmap);
                                imageScene.setVisibility(View.VISIBLE);
                            } else {
                                imageScene.setVisibility(View.GONE);
                            }
                        } else {
                            imageScene.setVisibility(View.GONE);
                        }
                    } else {
                        imageScene.setVisibility(View.GONE);
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

    // ========== Navigation ==========

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
