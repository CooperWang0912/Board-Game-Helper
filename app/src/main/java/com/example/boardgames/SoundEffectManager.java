package com.example.boardgames;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * Manages short sound effects for combat and game events using SoundPool.
 * Designed to coexist with the RadioService background music.
 */
public class SoundEffectManager {

    private static final String PREFS_NAME = "sound_effect_prefs";
    private static final String KEY_SFX_ENABLED = "sfx_enabled";
    private static final String KEY_SFX_VOLUME = "sfx_volume";

    private SoundPool soundPool;
    private boolean loaded;
    private boolean enabled;
    private float volume;

    // Sound IDs returned by SoundPool.load()
    private int soundAttack;
    private int soundHeal;
    private int soundLoot;
    private int soundDeath;
    private int soundLevelUp;
    private int soundEncounterComplete;
    private int soundEnemyDefeated;
    private int soundStaminaDrain;
    private int soundDiceRoll;

    public SoundEffectManager(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        enabled = prefs.getBoolean(KEY_SFX_ENABLED, true);
        volume = prefs.getFloat(KEY_SFX_VOLUME, 0.7f);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build();

        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            // All sounds loaded when the last one completes
            if (status == 0) {
                loaded = true;
            }
        });

        soundAttack = soundPool.load(context, R.raw.sfx_attack, 1);
        soundHeal = soundPool.load(context, R.raw.sfx_heal, 1);
        soundLoot = soundPool.load(context, R.raw.sfx_loot, 1);
        soundDeath = soundPool.load(context, R.raw.sfx_death, 1);
        soundLevelUp = soundPool.load(context, R.raw.sfx_level_up, 1);
        soundEncounterComplete = soundPool.load(context, R.raw.sfx_encounter_complete, 1);
        soundEnemyDefeated = soundPool.load(context, R.raw.sfx_enemy_defeated, 1);
        soundStaminaDrain = soundPool.load(context, R.raw.sfx_stamina_drain, 1);
        soundDiceRoll = soundPool.load(context, R.raw.sfx_dice_roll, 1);
    }

    private void play(int soundId) {
        if (enabled && loaded && soundPool != null) {
            soundPool.play(soundId, volume, volume, 1, 0, 1.0f);
        }
    }

    public void playAttack() {
        play(soundAttack);
    }

    public void playHeal() {
        play(soundHeal);
    }

    public void playLoot() {
        play(soundLoot);
    }

    public void playDeath() {
        play(soundDeath);
    }

    public void playLevelUp() {
        play(soundLevelUp);
    }

    public void playEncounterComplete() {
        play(soundEncounterComplete);
    }

    public void playEnemyDefeated() {
        play(soundEnemyDefeated);
    }

    public void playStaminaDrain() {
        play(soundStaminaDrain);
    }

    public void playDiceRoll() {
        play(soundDiceRoll);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(Context context, boolean enabled) {
        this.enabled = enabled;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SFX_ENABLED, enabled).apply();
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(Context context, float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_SFX_VOLUME, this.volume).apply();
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
            loaded = false;
        }
    }
}
