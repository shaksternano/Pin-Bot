package io.github.shaksternano.pinbot;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public record BotConfig(String token) {

    public static final String FILE_NAME = "config.json";

    public static BotConfig parse(File configJson) throws IOException {
        var gson = new Gson();
        try (var reader = new FileReader(configJson)) {
            var config = gson.fromJson(reader, BotConfig.class);
            if (config == null) {
                throw new IOException(FILE_NAME + " file is empty.");
            } else {
                config.validate();
                return config;
            }
        }
    }

    private void validate() {
        if (token() == null || token().isBlank()) {
            throw new RuntimeException("Bot token is missing from " + FILE_NAME + " file.");
        }
    }
}
