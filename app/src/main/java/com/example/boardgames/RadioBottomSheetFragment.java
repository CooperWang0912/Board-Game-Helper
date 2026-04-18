package com.example.boardgames;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class RadioBottomSheetFragment extends BottomSheetDialogFragment
        implements RadioService.RadioServiceListener {

    public interface RadioStateCallback {
        void onRadioStateChanged(boolean playing);
    }

    private static final List<RadioStation> STATIONS = new ArrayList<>();

    static {
        STATIONS.add(new RadioStation(
                "SomaFM: Drone Zone",
                "Atmospheric ambient space music",
                "https://ice2.somafm.com/dronezone-128-mp3",
                "Ambient"));
        STATIONS.add(new RadioStation(
                "SomaFM: Deep Space One",
                "Deep ambient electronic and space music",
                "https://ice2.somafm.com/deepspaceone-128-mp3",
                "Space Ambient"));
        STATIONS.add(new RadioStation(
                "SomaFM: Lush",
                "Sensuous and mellow vocals, mostly female",
                "https://ice2.somafm.com/lush-128-mp3",
                "Chill"));
        STATIONS.add(new RadioStation(
                "SomaFM: Groove Salad",
                "A nicely chilled plate of ambient and downtempo beats",
                "https://ice2.somafm.com/groovesalad-256-mp3",
                "Downtempo"));
        STATIONS.add(new RadioStation(
                "SomaFM: Metal Detector",
                "Heavy metal, thrash, and more",
                "https://ice2.somafm.com/metal-128-mp3",
                "Epic / Battle"));
    }

    private RadioService radioService;
    private boolean serviceBound;
    private RadioStationAdapter adapter;
    private TextView textStatus;
    private RadioStateCallback stateCallback;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            RadioService.RadioBinder radioBinder = (RadioService.RadioBinder) binder;
            radioService = radioBinder.getService();
            radioService.setListener(RadioBottomSheetFragment.this);
            serviceBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            radioService = null;
        }
    };

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof RadioStateCallback) {
            stateCallback = (RadioStateCallback) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_radio_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textStatus = view.findViewById(R.id.text_radio_status);
        RecyclerView recycler = view.findViewById(R.id.recycler_stations);

        adapter = new RadioStationAdapter(STATIONS, this::onStationClicked);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        textStatus.setText(R.string.radio_tap_to_play);
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(requireContext(), RadioService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (serviceBound) {
            if (radioService != null) {
                radioService.setListener(null);
            }
            requireContext().unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private void onStationClicked(RadioStation station) {
        if (!serviceBound || radioService == null) {
            return;
        }

        if (radioService.isPlaying()
                && radioService.getCurrentStation() != null
                && radioService.getCurrentStation().getStreamUrl().equals(station.getStreamUrl())) {
            radioService.stop();
        } else {
            Intent serviceIntent = new Intent(requireContext(), RadioService.class);
            requireContext().startService(serviceIntent);
            radioService.play(station);
        }
    }

    private void updateUI() {
        if (radioService == null || adapter == null) {
            return;
        }

        if (radioService.isPlaying() && radioService.getCurrentStation() != null) {
            adapter.setActiveStreamUrl(radioService.getCurrentStation().getStreamUrl());
            textStatus.setText(getString(R.string.radio_status_playing,
                    radioService.getCurrentStation().getName()));
        } else if (radioService.isPreparing()) {
            textStatus.setText(R.string.radio_buffering);
        } else {
            adapter.setActiveStreamUrl(null);
            textStatus.setText(R.string.radio_tap_to_play);
        }
    }

    @Override
    public void onPlaybackStarted(RadioStation station) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            adapter.setActiveStreamUrl(station.getStreamUrl());
            textStatus.setText(getString(R.string.radio_status_playing, station.getName()));
            if (stateCallback != null) {
                stateCallback.onRadioStateChanged(true);
            }
        });
    }

    @Override
    public void onPlaybackStopped() {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            adapter.setActiveStreamUrl(null);
            textStatus.setText(R.string.radio_tap_to_play);
            if (stateCallback != null) {
                stateCallback.onRadioStateChanged(false);
            }
        });
    }

    @Override
    public void onBuffering() {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() ->
                textStatus.setText(R.string.radio_buffering));
    }

    @Override
    public void onError(String message) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() ->
                textStatus.setText(getString(R.string.radio_error, message)));
    }
}
