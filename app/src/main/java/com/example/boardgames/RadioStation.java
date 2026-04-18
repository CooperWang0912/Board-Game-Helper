package com.example.boardgames;

public class RadioStation {
    private final String name;
    private final String description;
    private final String streamUrl;
    private final String genre;

    public RadioStation(String name, String description, String streamUrl, String genre) {
        this.name = name;
        this.description = description;
        this.streamUrl = streamUrl;
        this.genre = genre;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public String getGenre() {
        return genre;
    }
}
