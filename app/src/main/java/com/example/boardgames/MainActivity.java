package com.example.boardgames;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.card_dice_roll).setOnClickListener(v ->
                startActivity(new Intent(this, DiceRollActivity.class)));

        findViewById(R.id.card_character_creation).setOnClickListener(v ->
                startActivity(new Intent(this, CharacterCreationActivity.class)));

        findViewById(R.id.card_dungeon_master).setOnClickListener(v ->
                startActivity(new Intent(this, DungeonMasterActivity.class)));
    }
}
