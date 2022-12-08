package io.github.shaksternano.pinbot;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class PinBotEventListener extends ListenerAdapter {

    private static final PinBotEventListener INSTANCE = new PinBotEventListener();

    private PinBotEventListener() {
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        CommandClient.handleCommand(event);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        PinnedMessageForwarder.sendPinConfirmationAndDeleteSystemMessage(event);
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        PinnedMessageForwarder.forwardPinnedMessage(event);
    }

    public static PinBotEventListener getInstance() {
        return INSTANCE;
    }
}
