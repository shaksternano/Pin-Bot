package io.github.shaksternano.pinbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.exceptions.HttpException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class PinnedMessageForwarder {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static void tryDeleteSystemPinMessage(MessageReceivedEvent event) {
        if (event.getMessage().getType().equals(MessageType.CHANNEL_PINNED_ADD)
                && PinBotSettings.getPinChannel(event.getChannel().getIdLong()).isPresent()) {
            event.getMessage().delete().queue();
        }
    }

    public static void tryForwardPinnedMessage(MessageUpdateEvent event) {
        Message message = event.getMessage();
        if (message.isPinned()) {
            Channel channel = event.getChannel();
            PinBotSettings.getPinChannel(channel.getIdLong())
                    .map(channelId -> event.getGuild().getChannelById(TextChannel.class, channelId))
                    .ifPresentOrElse(textChannel -> textChannel.retrieveWebhooks()
                                    .submit()
                                    .thenCompose(webhooks -> getOrCreateWebhook(webhooks, textChannel))
                                    .thenCompose(webhook -> forwardPinnedMessage(message, webhook))
                                    .whenComplete((unused, throwable) -> handleError(throwable, textChannel)),
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
        return webhook.getName().equals(Main.getJDA().getSelfUser().getName());
    }

    private static CompletableFuture<Webhook> createWebhook(TextChannel channel) {
        return retrieveSelfIcon().thenCompose(icon -> createWebhook(channel, icon));
    }

    private static CompletableFuture<Webhook> createWebhook(TextChannel channel, @Nullable Icon icon) {
        return channel.createWebhook(Main.getJDA().getSelfUser().getName())
                .setAvatar(icon)
                .submit();
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

    private static CompletableFuture<Void> forwardPinnedMessage(Message message, Webhook webhook) {
        String webhookToken = webhook.getToken();
        if (webhookToken == null) {
            throw new IllegalStateException("The webhook token is null.");
        }

        JsonElement webhookMessage = createWebhookMessage(message);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(webhook.getUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(webhookMessage.toString()))
                    .build();
            return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            return message.unpin().submit();
                        } else {
                            return CompletableFuture.failedFuture(new HttpException(
                                    "The webhook message request failed with status code " + response.statusCode() + ". Response body:\n" + response.body())
                            );
                        }
                    });
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static JsonElement createWebhookMessage(Message message) {
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

        JsonObject webhookMessage = new JsonObject();
        webhookMessage.addProperty("content", messageContent.toString());
        webhookMessage.addProperty("username", username);
        webhookMessage.addProperty("avatar_url", avatarUrl);

        JsonArray components = new JsonArray();

        JsonObject actionRow = new JsonObject();
        actionRow.addProperty("type", 1);
        JsonArray actionRowComponents = new JsonArray();

        JsonObject messageLinkButton = new JsonObject();
        messageLinkButton.addProperty("type", 2);
        messageLinkButton.addProperty("style", 5);
        messageLinkButton.addProperty("label", "Original Message");
        messageLinkButton.addProperty("url", message.getJumpUrl());

        actionRowComponents.add(messageLinkButton);
        actionRow.add("components", actionRowComponents);
        components.add(actionRow);
        webhookMessage.add("components", components);

        return webhookMessage;
    }

    private static void handleError(Throwable throwable, MessageChannel channel) {
        if (throwable != null) {
            channel.sendMessage("An error occurred while pinning this message.").queue();
            Main.getLogger().error("An error occurred while pinning a message.", throwable);
        }
    }
}
