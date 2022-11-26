package io.github.shaksternano.pinbot;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class ProgramArguments {

    private final Map<String, String> arguments;

    public ProgramArguments(String[] args) {
        arguments = parseArguments(args);
    }

    private static Map<String, String> parseArguments(String[] args) {
        Map<String, String> arguments = new HashMap<>();

        for (String arg : args) {
            String[] parts = arg.split(Pattern.quote("="), 2);

            if (parts.length >= 2) {
                arguments.put(parts[0], parts[1]);
            }
        }

        return arguments;
    }

    public Optional<String> getArgumentOrEnvironmentVariable(String key) {
        String value = arguments.get(key);

        if (value == null) {
            value = System.getenv(key);
        }

        return Optional.ofNullable(value);
    }
}
