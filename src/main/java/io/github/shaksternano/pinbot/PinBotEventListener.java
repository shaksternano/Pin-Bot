package io.github.shaksternano.pinbot;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

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
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getMessage().getType().equals(MessageType.CHANNEL_PINNED_ADD)
                && PinBotSettings.getPinChannel(event.getChannel().getIdLong()).isPresent()) {
            event.getMessage().delete().queue();
        }
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        Message message = event.getMessage();
        if (message.isPinned()) {
            Channel channel = event.getChannel();
            PinBotSettings.getPinChannel(channel.getIdLong())
                    .map(channelId -> event.getGuild().getChannelById(TextChannel.class, channelId))
                    .ifPresentOrElse(textChannel -> textChannel.retrieveWebhooks()
                                    .submit()
                                    .thenApply(webhooks -> webhooks.stream()
                                            .filter(webhook -> PinBotSettings.getWebhook(message.getChannel().getIdLong())
                                                    .map(webhookId -> webhookId == webhook.getIdLong())
                                                    .orElse(false)
                                            ).findAny())
                                    .thenCompose(webhookOptional -> webhookOptional.map(webhook -> CompletableFuture.supplyAsync(() -> webhook))
                                            .orElseGet(() -> CompletableFuture.supplyAsync(PinBotEventListener::getSelfIcon)
                                                    .thenCompose(icon -> textChannel.createWebhook("Pin Bot")
                                                            .setAvatar(icon)
                                                            .submit()
                                                            .thenApply(webhook -> {
                                                                PinBotSettings.setWebhook(channel.getIdLong(), webhook.getIdLong());
                                                                return webhook;
                                                            })
                                                    )
                                            )
                                    ).thenAccept(webhook -> transferPinnedMessage(message, webhook))
                                    .handle((unused, throwable) -> {
                                        event.getChannel().sendMessage("An error occurred while pinning this message.").queue();
                                        Main.getLogger().error("An error occurred while pinning a message.", throwable);
                                        return null;
                                    }),
                            () -> event.getChannel().sendMessage("The pin channel for this channel doesn't exist anymore.").queue()
                    );
        }
    }

    private static void transferPinnedMessage(Message message, Webhook webhook) {
        String webhookToken = webhook.getToken();
        if (webhookToken == null) {
            throw new IllegalStateException("The webhook token is null.");
        }
        try (WebhookClient client = WebhookClient.withId(webhook.getIdLong(), webhookToken)) {
            StringBuilder messageContent = new StringBuilder(message.getContentRaw());
            for (Message.Attachment attachment : message.getAttachments()) {
                messageContent.append("\n").append(attachment.getUrl());
            }

            Guild guild = message.getGuild();
            User author = message.getAuthor();
            String username = author.getName();
            String avatarUrl = author.getEffectiveAvatarUrl();
            if (PinBotSettings.usesServerProfile(guild.getIdLong())) {
                Member member = guild.getMember(author);
                if (member != null) {
                    username = member.getEffectiveName();
                    avatarUrl = member.getEffectiveAvatarUrl();
                }
            }

            WebhookMessageBuilder messageBuilder = new WebhookMessageBuilder()
                    .setContent(messageContent.toString())
                    .setUsername(username)
                    .setAvatarUrl(avatarUrl);
            client.send(messageBuilder.build()).thenAccept(readonlyMessage -> message.unpin().queue());
        }
    }

    @Nullable
    private static Icon getSelfIcon() {
        String avatarUrl = Main.getJDA().getSelfUser().getAvatarUrl();
        if (avatarUrl == null) {
            return null;
        }
        try {
            return Icon.from(new URL(avatarUrl).openStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static PinBotEventListener getInstance() {
        return INSTANCE;
    }
}
