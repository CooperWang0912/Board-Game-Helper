package com.example.boardgames;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class GameOverActivity extends AppCompatActivity {

    static final String EXTRA_RESULT = "result";
    static final String EXTRA_SUBTITLE = "subtitle";
    static final String EXTRA_ENEMIES_DEFEATED = "enemiesDefeated";
    static final String EXTRA_ITEMS_USED = "itemsUsed";
    static final String EXTRA_LOOT_COLLECTED = "lootCollected";
    static final String EXTRA_TURNS_TAKEN = "turnsTaken";
    static final String EXTRA_DAMAGE_TAKEN = "damageTaken";
    static final String EXTRA_ENCOUNTERS_DONE = "encountersDone";
    static final String EXTRA_ENCOUNTERS_TOTAL = "encountersTotal";
    static final String EXTRA_SURVIVED = "survived";
    static final String EXTRA_FALLEN = "fallen";

    static final int RESULT_WIN = 1;
    static final int RESULT_LOSE = 2;

    static final int RETURN_NEW_RUN = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        Intent intent = getIntent();
        int result = intent.getIntExtra(EXTRA_RESULT, RESULT_LOSE);
        String subtitle = intent.getStringExtra(EXTRA_SUBTITLE);
        boolean isWin = result == RESULT_WIN;

        // Title
        TextView titleText = findViewById(R.id.text_result_title);
        titleText.setText(isWin ? R.string.game_over_victory : R.string.game_over_defeat);
        titleText.setTextColor(isWin ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));

        // Subtitle
        TextView subtitleText = findViewById(R.id.text_result_subtitle);
        if (subtitle != null && !subtitle.isEmpty()) {
            subtitleText.setText(subtitle);
        } else {
            subtitleText.setText(isWin ? R.string.game_over_subtitle_win : R.string.game_over_subtitle_lose);
        }

        // Adventure Stats
        int enemies = intent.getIntExtra(EXTRA_ENEMIES_DEFEATED, 0);
        int itemsUsed = intent.getIntExtra(EXTRA_ITEMS_USED, 0);
        int loot = intent.getIntExtra(EXTRA_LOOT_COLLECTED, 0);
        int turns = intent.getIntExtra(EXTRA_TURNS_TAKEN, 0);
        int damage = intent.getIntExtra(EXTRA_DAMAGE_TAKEN, 0);
        int encDone = intent.getIntExtra(EXTRA_ENCOUNTERS_DONE, 0);
        int encTotal = intent.getIntExtra(EXTRA_ENCOUNTERS_TOTAL, 0);

        ((TextView) findViewById(R.id.text_stat_enemies))
                .setText(getString(R.string.dm_stats_enemies_defeated, enemies));
        ((TextView) findViewById(R.id.text_stat_items_used))
                .setText(getString(R.string.dm_stats_items_used, itemsUsed));
        ((TextView) findViewById(R.id.text_stat_loot))
                .setText(getString(R.string.dm_stats_loot_collected, loot));
        ((TextView) findViewById(R.id.text_stat_turns))
                .setText(getString(R.string.dm_stats_turns_taken, turns));
        ((TextView) findViewById(R.id.text_stat_damage))
                .setText(getString(R.string.dm_stats_damage_taken, damage));

        TextView encText = findViewById(R.id.text_stat_encounters);
        if (encTotal > 0) {
            encText.setText(getString(R.string.dm_stats_encounters_progress, encDone, encTotal));
        } else {
            encText.setVisibility(View.GONE);
        }

        // Party Status
        String[] survived = intent.getStringArrayExtra(EXTRA_SURVIVED);
        String[] fallen = intent.getStringArrayExtra(EXTRA_FALLEN);
        LinearLayout partyLayout = findViewById(R.id.layout_party_members);

        boolean hasPartyData = (survived != null && survived.length > 0)
                || (fallen != null && fallen.length > 0);

        if (!hasPartyData) {
            findViewById(R.id.card_party_status).setVisibility(View.GONE);
        } else {
            if (survived != null) {
                for (String name : survived) {
                    addPartyMemberRow(partyLayout, name, true);
                }
            }
            if (fallen != null) {
                for (String name : fallen) {
                    addPartyMemberRow(partyLayout, name, false);
                }
            }
        }

        // Buttons
        MaterialButton btnNewRun = findViewById(R.id.btn_new_run);
        btnNewRun.setOnClickListener(v -> {
            setResult(RETURN_NEW_RUN);
            finish();
        });

        MaterialButton btnReturnHome = findViewById(R.id.btn_return_home);
        btnReturnHome.setOnClickListener(v -> {
            Intent homeIntent = new Intent(this, MainActivity.class);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
            finish();
        });
    }

    private void addPartyMemberRow(LinearLayout parent, String name, boolean alive) {
        TextView row = new TextView(this);
        String status = alive
                ? getString(R.string.game_over_survived)
                : getString(R.string.game_over_fallen);
        row.setText(name + " — " + status);
        row.setTextSize(15);
        row.setPadding(0, 6, 0, 6);
        row.setTextColor(alive ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        parent.addView(row);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
