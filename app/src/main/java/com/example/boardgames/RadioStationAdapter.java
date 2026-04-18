package com.example.boardgames;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RadioStationAdapter extends RecyclerView.Adapter<RadioStationAdapter.ViewHolder> {

    public interface OnStationClickListener {
        void onStationClick(RadioStation station);
    }

    private final List<RadioStation> stations;
    private final OnStationClickListener clickListener;
    private String activeStreamUrl;

    public RadioStationAdapter(List<RadioStation> stations, OnStationClickListener listener) {
        this.stations = stations;
        this.clickListener = listener;
    }

    public void setActiveStreamUrl(String url) {
        this.activeStreamUrl = url;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_radio_station, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RadioStation station = stations.get(position);
        holder.name.setText(station.getName());
        holder.description.setText(station.getDescription());
        holder.genre.setText(station.getGenre());

        boolean isActive = station.getStreamUrl().equals(activeStreamUrl);
        holder.nowPlaying.setVisibility(isActive ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> clickListener.onStationClick(station));
    }

    @Override
    public int getItemCount() {
        return stations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView description;
        final TextView genre;
        final ImageView nowPlaying;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_station_name);
            description = itemView.findViewById(R.id.text_station_description);
            genre = itemView.findViewById(R.id.text_station_genre);
            nowPlaying = itemView.findViewById(R.id.icon_now_playing);
        }
    }
}
