package io.github.shaksternano.pinbot;

import io.github.shaksternano.pinbot.command.Command;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.HashMap;
import java.util.Map;

public class CommandClient {

    public static final Map<String, Command> COMMANDS = new HashMap<>();

    public static void handleCommand(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        if (event.getSubcommandName() != null) {
            commandName += " " + event.getSubcommandName();
        }
        Command command = COMMANDS.get(commandName);
        if (command != null) {
            String response;
            try {
                response = command.execute(event);
            } catch (Throwable t) {
                response = "An error occurred while executing the command.";
                Main.getLogger().error("An error occurred while executing the command " + command, t);
            }
            event.reply(response).queue();
        }
    }

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
}
