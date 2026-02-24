package com.example.boardgames;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiceRollActivity extends AppCompatActivity {

    private static final Pattern DICE_PATTERN = Pattern.compile("^(\\d*)d(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final int ANIMATION_STEPS = 12;
    private static final long ANIMATION_STEP_MS = 60;

    private TextInputEditText editDiceInput;
    private MaterialButton btnRoll;
    private MaterialCardView cardResult;
    private TextView textResult;
    private TextView textDiceLabel;
    private TextView textError;
    private TextView textHistoryHeader;
    private RecyclerView recyclerHistory;

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isAnimating = false;

    private static final List<RollEntry> rollHistory = new ArrayList<>();
    private HistoryAdapter historyAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dice_roll);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editDiceInput = findViewById(R.id.edit_dice_input);
        btnRoll = findViewById(R.id.btn_roll);
        cardResult = findViewById(R.id.card_result);
        textResult = findViewById(R.id.text_result);
        textDiceLabel = findViewById(R.id.text_dice_label);
        textError = findViewById(R.id.text_error);
        textHistoryHeader = findViewById(R.id.text_history_header);
        recyclerHistory = findViewById(R.id.recycler_history);

        historyAdapter = new HistoryAdapter(rollHistory);
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerHistory.setAdapter(historyAdapter);

        if (!rollHistory.isEmpty()) {
            textHistoryHeader.setVisibility(View.VISIBLE);
            recyclerHistory.setVisibility(View.VISIBLE);
        }

        btnRoll.setOnClickListener(v -> onRollClicked());

        editDiceInput.setOnEditorActionListener((v, actionId, event) -> {
            onRollClicked();
            return true;
        });
    }

    private void onRollClicked() {
        if (isAnimating) return;

        String input = editDiceInput.getText() != null ? editDiceInput.getText().toString().trim() : "";
        textError.setVisibility(View.GONE);

        if (input.isEmpty()) {
            showError(getString(R.string.error_empty_input));
            return;
        }

        Matcher matcher = DICE_PATTERN.matcher(input);
        if (!matcher.matches()) {
            showError(getString(R.string.error_invalid_format));
            return;
        }

        String countStr = matcher.group(1);
        int count = (countStr == null || countStr.isEmpty()) ? 1 : Integer.parseInt(countStr);
        int sides = Integer.parseInt(matcher.group(2));

        if (sides < 2) {
            showError(getString(R.string.error_min_sides));
            return;
        }

        if (sides > 1000) {
            showError(getString(R.string.error_max_sides));
            return;
        }

        if (count < 1 || count > 100) {
            showError(getString(R.string.error_dice_count));
            return;
        }

        int finalResult = 0;
        for (int i = 0; i < count; i++) {
            finalResult += random.nextInt(sides) + 1;
        }

        String label = count + "d" + sides;
        animateRoll(label, finalResult, count, sides);
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    private void addToHistory(String label, int result) {
        rollHistory.add(0, new RollEntry(label, result));
        historyAdapter.notifyItemInserted(0);
        recyclerHistory.scrollToPosition(0);

        textHistoryHeader.setVisibility(View.VISIBLE);
        recyclerHistory.setVisibility(View.VISIBLE);
    }

    private void animateRoll(String label, int finalResult, int count, int sides) {
        isAnimating = true;
        btnRoll.setEnabled(false);

        textDiceLabel.setText(label);
        cardResult.setVisibility(View.VISIBLE);

        // Rapid number cycling animation
        for (int i = 0; i < ANIMATION_STEPS; i++) {
            final int step = i;
            handler.postDelayed(() -> {
                // Generate a random number in the valid range for this dice config
                int fakeResult = 0;
                for (int d = 0; d < count; d++) {
                    fakeResult += random.nextInt(sides) + 1;
                }
                textResult.setText(String.valueOf(fakeResult));

                // Subtle scale pulse on each tick
                textResult.setScaleX(1.1f);
                textResult.setScaleY(1.1f);
                textResult.animate().scaleX(1f).scaleY(1f).setDuration(ANIMATION_STEP_MS).start();

            }, step * ANIMATION_STEP_MS);
        }

        // Final result with bounce animation
        handler.postDelayed(() -> {
            textResult.setText(String.valueOf(finalResult));

            AnimatorSet bounceSet = new AnimatorSet();

            ObjectAnimator scaleX = ObjectAnimator.ofFloat(textResult, "scaleX", 0.5f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(textResult, "scaleY", 0.5f, 1f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(textResult, "alpha", 0.5f, 1f);

            ObjectAnimator cardScaleX = ObjectAnimator.ofFloat(cardResult, "scaleX", 0.95f, 1f);
            ObjectAnimator cardScaleY = ObjectAnimator.ofFloat(cardResult, "scaleY", 0.95f, 1f);

            bounceSet.playTogether(scaleX, scaleY, alpha, cardScaleX, cardScaleY);
            bounceSet.setDuration(400);
            bounceSet.setInterpolator(new OvershootInterpolator(2f));
            bounceSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    isAnimating = false;
                    btnRoll.setEnabled(true);
                    addToHistory(label, finalResult);
                }
            });
            bounceSet.start();

        }, ANIMATION_STEPS * ANIMATION_STEP_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // --- Data class ---

    private static class RollEntry {
        final String dice;
        final int result;

        RollEntry(String dice, int result) {
            this.dice = dice;
            this.result = result;
        }
    }

    // --- RecyclerView Adapter ---

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private final List<RollEntry> entries;

        HistoryAdapter(List<RollEntry> entries) {
            this.entries = entries;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dice_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RollEntry entry = entries.get(position);
            holder.indexText.setText(String.valueOf(entries.size() - position));
            holder.diceText.setText(entry.dice);
            holder.resultText.setText(String.valueOf(entry.result));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView indexText;
            final TextView diceText;
            final TextView resultText;

            ViewHolder(View itemView) {
                super(itemView);
                indexText = itemView.findViewById(R.id.text_history_index);
                diceText = itemView.findViewById(R.id.text_history_dice);
                resultText = itemView.findViewById(R.id.text_history_result);
            }
        }
    }
}
