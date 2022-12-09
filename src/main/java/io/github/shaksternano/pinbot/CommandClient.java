package io.github.shaksternano.pinbot;

import io.github.shaksternano.pinbot.command.Command;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.*;
import java.util.stream.Collectors;

public class CommandClient {

    public static final Map<String, Command> COMMANDS = new HashMap<>();

    public static void addCommands(JDA jda, Command command, Command... commands) {
        List<Command> commandList = new ArrayList<>(Arrays.asList(commands));
        commandList.add(command);

        List<? extends CommandData> ungroupedCommands = commandList.stream()
                .filter(command1 -> command1.getGroup().isEmpty())
                .map(CommandClient::createUngroupedCommand)
                .toList();
        List<? extends CommandData> groupedCommands = commandList.stream()
                .filter(command1 -> command1.getGroup().isPresent())
                .collect(Collectors.groupingBy(command1 -> command1.getGroup().orElseThrow()))
                .entrySet()
                .stream()
                .map(CommandClient::createGroupedCommand)
                .toList();

        jda.updateCommands()
                .addCommands(ungroupedCommands)
                .addCommands(groupedCommands)
                .queue();
        CommandClient.addCommands(command, commands);
    }

    private static SlashCommandData createUngroupedCommand(Command command) {
        return Commands.slash(command.getName(), command.getDescription())
                .setDefaultPermissions(command.getPermissions())
                .setGuildOnly(command.isGuildOnly())
                .addOptions(command.getOptions());
    }

    private static SlashCommandData createGroupedCommand(Map.Entry<String, List<Command>> commandGroup) {
        String group = commandGroup.getKey();
        List<Command> commands = commandGroup.getValue();
        Command firstCommand = commands.get(0);
        DefaultMemberPermissions permissions = firstCommand.getPermissions();
        boolean guildOnly = firstCommand.isGuildOnly();
        List<SubcommandData> subCommands = commands.stream()
                .map(command -> new SubcommandData(command.getName(), command.getDescription())
                        .addOptions(command.getOptions())
                ).toList();
        return Commands.slash(group, group)
                .setDefaultPermissions(permissions)
                .setGuildOnly(guildOnly)
                .addSubcommands(subCommands);
    }

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
            event.reply(response).setEphemeral(true).queue();
        }
    }

    private static void addCommands(Command command, Command... commands) {
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
