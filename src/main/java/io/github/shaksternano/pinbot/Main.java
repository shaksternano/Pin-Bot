package io.github.shaksternano.pinbot;

import io.github.shaksternano.pinbot.command.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger("Pin Bot");

    public static void main(String[] args) {
        System.setProperty("log4j2.contextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
        ProgramArguments arguments = new ProgramArguments(args);
        Optional<String> token = arguments.getArgumentOrEnvironmentVariable("DISCORD_BOT_TOKEN");
        if (token.isEmpty()) {
            LOGGER.error("No Discord bot token provided. Please provide a token via the program argument or environment variable \"DISCORD_BOT_TOKEN\".");
            return;
        }
        JDA jda = JDABuilder.createDefault(token.orElseThrow())
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(PinBotEventListener.getInstance())
                .build();
        addCommands(
                jda,
                PinChannelCommand.getInstance(),
                PinChannelListCommand.getInstance(),
                RemovePinChannelCommand.getInstance(),
                UsesServerProfileCommand.getInstance()
        );
    }

    private static void addCommands(JDA jda, Command command, Command... commands) {
        List<Command> commandList = new ArrayList<>(Arrays.asList(commands));
        commandList.add(command);

        List<? extends CommandData> ungroupedCommands = commandList.stream()
                .filter(command1 -> command1.getGroup().isEmpty())
                .map(command1 -> Commands.slash(command1.getName(), command1.getDescription())
                        .setDefaultPermissions(command1.getPermissions())
                        .setGuildOnly(command1.isGuildOnly())
                        .addOptions(command1.getOptions())
                )
                .toList();
        List<? extends CommandData> groupedCommands = commandList.stream()
                .filter(command1 -> command1.getGroup().isPresent())
                .collect(Collectors.groupingBy(command1 -> command1.getGroup().orElseThrow()))
                .entrySet()
                .stream()
                .map(entry -> {
                    String group = entry.getKey();
                    Collection<Command> commands1 = entry.getValue();
                    Command firstCommand = commands1.iterator().next();
                    DefaultMemberPermissions permissions = firstCommand.getPermissions();
                    boolean guildOnly = firstCommand.isGuildOnly();
                    List<SubcommandData> subCommands = commands1.stream()
                            .map(command1 -> new SubcommandData(command1.getName(), command1.getDescription())
                                    .addOptions(command1.getOptions())
                            ).toList();
                    return Commands.slash(group, group)
                            .setDefaultPermissions(permissions)
                            .setGuildOnly(guildOnly)
                            .addSubcommands(subCommands);
                })
                .toList();

        jda.updateCommands()
                .addCommands(ungroupedCommands)
                .addCommands(groupedCommands)
                .queue();
        CommandClient.addCommands(command, commands);
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
