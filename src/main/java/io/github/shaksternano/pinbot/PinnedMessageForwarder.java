package io.github.shaksternano.pinbot;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.receive.ReadonlyMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class PinnedMessageForwarder {

    public static void tryDeleteSystemPinMessage(MessageReceivedEvent event) {
        if (event.getMessage().getType().equals(MessageType.CHANNEL_PINNED_ADD)
                && PinBotSettings.getPinChannel(event.getChannel().getIdLong()).isPresent()) {
            event.getMessage().delete().queue();
        }
    }

    public static void tryTransferPinnedMessage(MessageUpdateEvent event) {
        Message message = event.getMessage();
        if (message.isPinned()) {
            Channel channel = event.getChannel();
            PinBotSettings.getPinChannel(channel.getIdLong())
                    .map(channelId -> event.getGuild().getChannelById(TextChannel.class, channelId))
                    .ifPresentOrElse(textChannel -> textChannel.retrieveWebhooks()
                                    .submit()
                                    .thenCompose(webhooks -> getOrCreateWebhook(webhooks, textChannel))
                                    .thenCompose(webhook -> transferPinnedMessage(message, webhook))
                                    .handle((unused, throwable) -> handleError(unused, throwable, textChannel)),
                            () -> event.getChannel().sendMessage("No pin channel set!").queue()
                    );
        }
    }

    private static CompletableFuture<Webhook> getOrCreateWebhook(Collection<Webhook> webhooks, TextChannel channel) {
        return webhooks.stream()
                .filter(PinnedMessageForwarder::isOwnWebhook)
                .findAny()
                .map(CompletableFuture::completedFuture)
                .orElseGet(() -> createWebhook(channel));
    }

    private static boolean isOwnWebhook(Webhook webhook) {
        return PinBotSettings.getWebhook(webhook.getChannel().getIdLong())
                .map(webhookId -> webhookId == webhook.getIdLong())
                .orElse(false);
    }

    private static CompletableFuture<Webhook> createWebhook(TextChannel channel) {
        return retrieveSelfIcon().thenCompose(icon -> createWebhook(channel, icon));
    }

    private static CompletableFuture<Webhook> createWebhook(TextChannel channel, @Nullable Icon icon) {
        return channel.createWebhook(Main.getJDA().getSelfUser().getName())
                .setAvatar(icon)
                .submit()
                .thenApply(webhook -> {
                    PinBotSettings.setWebhook(channel.getIdLong(), webhook.getIdLong());
                    return webhook;
                });
    }

    /**
     * Retrieves the icon of the bot.
     * The icon will be null if the bot has no avatar.
     *
     * @return A {@code CompletableFuture} containing the icon of the bot.
     */
    private static CompletableFuture<Icon> retrieveSelfIcon() {
        return CompletableFuture.supplyAsync(() -> {
            String avatarUrl = Main.getJDA().getSelfUser().getAvatarUrl();
            if (avatarUrl == null) {
                return null;
            }
            try {
                return Icon.from(new URL(avatarUrl).openStream());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static CompletableFuture<ReadonlyMessage> transferPinnedMessage(Message message, Webhook webhook) {
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
            return client.send(messageBuilder.build()).thenApply(readonlyMessage -> {
                message.unpin().queue();
                return readonlyMessage;
            });
        }
    }

    private static <T> T handleError(T t, Throwable throwable, MessageChannel channel) {
        if (throwable != null) {
            channel.sendMessage("An error occurred while pinning this message.").queue();
            Main.getLogger().error("An error occurred while pinning a message.", throwable);
        }
        return t;
    }
}
