package io.github.shaksternano.pinbot;

import io.github.shaksternano.pinbot.command.Command;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CommandClient {

    public static final Map<String, Command> COMMANDS = new HashMap<>();

    public static void addCommands(Command command, Command... commands) {
        addCommand(command);
        for (Command c : commands) {
            addCommand(c);
        }
    }

    private static void addCommand(Command command) {
        COMMANDS.put(
                command.getGroup().orElse("") + " " + command.getName(),
                command
        );
    }

    public static Optional<Command> getCommand(String name) {
        return Optional.ofNullable(COMMANDS.get(name));
    }
}
