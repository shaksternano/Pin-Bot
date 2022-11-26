package io.github.shaksternano.pinbot;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class PinBotEventListener extends ListenerAdapter {

    private static final PinBotEventListener INSTANCE = new PinBotEventListener();

    private PinBotEventListener() {
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        if (event.getSubcommandName() != null) {
            commandName += " " + event.getSubcommandName();
        }
        CommandClient.getCommand(commandName).ifPresent(command -> {
            String response;
            try {
                response = command.execute(event);
            } catch (Throwable t) {
                response = "An error occurred while executing the command.";
                Main.getLogger().error("An error occurred while executing the command " + command, t);
            }
            event.reply(response).queue();
        });
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (event.getMessage().isPinned()) {
            System.out.println("Test");
        }
    }

    public static PinBotEventListener getInstance() {
        return INSTANCE;
    }
}
