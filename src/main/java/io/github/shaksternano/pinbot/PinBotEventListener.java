package io.github.shaksternano.pinbot;

import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class PinBotEventListener extends ListenerAdapter {

    public static final PinBotEventListener INSTANCE = new PinBotEventListener();

    private PinBotEventListener() {
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        CompletableFuture.runAsync(() -> CommandClient.handleCommand(event));
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        CompletableFuture.runAsync(() -> PinnedMessageForwarder.sendCustomPinConfirmationIfPinChannelSet(event));
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        CompletableFuture.runAsync(() -> PinnedMessageForwarder.forwardMessageIfPinned(event));
    }

    @Override
    public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        CompletableFuture.runAsync(() -> {
            var channelId = event.getChannel().getIdLong();
            Database.removeSendPinFromChannel(channelId);
        });
    }
}
