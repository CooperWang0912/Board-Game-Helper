package com.example.boardgames;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.card_chess).setOnClickListener(v ->
                startActivity(new Intent(this, ChessActivity.class)));

        findViewById(R.id.card_checkers).setOnClickListener(v ->
                startActivity(new Intent(this, CheckersActivity.class)));

        findViewById(R.id.card_tictactoe).setOnClickListener(v ->
                startActivity(new Intent(this, TicTacToeActivity.class)));
    }
}
