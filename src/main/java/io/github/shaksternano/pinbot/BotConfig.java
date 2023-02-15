package io.github.shaksternano.pinbot;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;

public record BotConfig(String token) {

    public static final String FILE_NAME = "config.json";

    public static BotConfig parse(File configJson) {
        Gson gson = new Gson();
        try {
            BotConfig config = gson.fromJson(new FileReader(configJson), BotConfig.class);
            if (config == null) {
                throw new RuntimeException(FILE_NAME + " file is empty.");
            } else {
                config.validate();
                return config;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void validate() {
        if (token() == null || token().isBlank()) {
            throw new RuntimeException("Bot token is missing from " + FILE_NAME + " file.");
        }
    }
}
