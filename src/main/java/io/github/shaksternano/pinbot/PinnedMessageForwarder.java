package io.github.shaksternano.pinbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.exceptions.HttpException;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PinnedMessageForwarder {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static void deletePinMessage(MessageReceivedEvent event) {
        Message message = event.getMessage();
        if (message.getType().equals(MessageType.CHANNEL_PINNED_ADD)
            && PinBotSettings.getPinChannel(event.getChannel().getIdLong()).isPresent()) {
            message.delete().queue();
        }
    }

    private static MessageCreateData createPinConfirmationMessage(User pinner, Channel pinChannel, Message pinnedMessage) {
        return new MessageCreateBuilder()
            .addContent(pinner.getAsMention() + " pinned a message to " + pinChannel.getAsMention() + ".")
            .addActionRow(Button.link(pinnedMessage.getJumpUrl(), "Jump to message"))
            .build();
    }

    public static void forwardMessageIfPinned(MessageUpdateEvent event) {
        Message message = event.getMessage();
        if (message.isPinned()) {
            PinBotSettings.getPinChannel(event.getChannel().getIdLong())
                .ifPresent(pinChannelId -> forwardPinnedMessage(message, pinChannelId));
        }
    }

    private static void forwardPinnedMessage(Message message, long pinChannelId) {
        MessageChannel sentFrom = message.getChannel();
        Channel pinChannel = message.getGuild().getChannelById(Channel.class, pinChannelId);
        if (pinChannel instanceof IWebhookContainer webhookContainer) {
            webhookContainer.retrieveWebhooks()
                .submit()
                .thenCompose(webhooks -> getOrCreateWebhook(webhooks, webhookContainer))
                .thenCompose(webhook -> forwardMessageToWebhook(message, webhook))
                .whenComplete((unused, throwable) -> handleError(throwable, sentFrom));
        } else {
            if (pinChannel != null) {
                sentFrom.sendMessage(pinChannel.getAsMention() + " doesn't support webhooks!.").queue();
            }
            PinBotSettings.removeSendPinFromChannel(sentFrom.getIdLong());
        }
    }

    private static CompletableFuture<Webhook> getOrCreateWebhook(Collection<Webhook> webhooks, IWebhookContainer webhookContainer) {
        return getOwnWebhook(webhooks)
            .map(CompletableFuture::completedFuture)
            .orElseGet(() -> createWebhook(webhookContainer));
    }

    private static Optional<Webhook> getOwnWebhook(Collection<Webhook> webhooks) {
        return webhooks.stream()
            .filter(PinnedMessageForwarder::isOwnWebhook)
            .findAny();
    }

    private static boolean isOwnWebhook(Webhook webhook) {
        return webhook.getName().equals(Main.getJDA().getSelfUser().getName());
    }

    private static CompletableFuture<Webhook> createWebhook(IWebhookContainer webhookContainer) {
        return retrieveIcon(Main.getJDA().getSelfUser())
            .thenCompose(iconOptional -> createWebhook(webhookContainer, iconOptional.orElse(null)));
    }

    private static CompletableFuture<Optional<Icon>> retrieveIcon(User user) {
        return CompletableFuture.supplyAsync(() -> getIcon(user));
    }

    private static Optional<Icon> getIcon(User user) {
        String avatarUrl = user.getEffectiveAvatarUrl();
        try (InputStream iconStream = new URL(avatarUrl).openStream()) {
            return Optional.of(Icon.from(iconStream));
        } catch (IOException e) {
            Main.getLogger().error("Failed to create icon for " + user, e);
            return Optional.empty();
        }
    }

    private static CompletableFuture<Webhook> createWebhook(IWebhookContainer webhookContainer, @Nullable Icon icon) {
        return webhookContainer.createWebhook(Main.getJDA().getSelfUser().getName())
            .setAvatar(icon)
            .submit();
    }

    private static CompletableFuture<Void> forwardMessageToWebhook(Message message, Webhook webhook) {
        User author = message.getAuthor();
        Guild guild = message.getGuild();
        return retrieveUserDetails(author, guild)
            .thenApply(userDetails -> createWebhookMessage(message, userDetails.username(), userDetails.avatarUrl()))
            .thenCompose(webhookMessage -> sendWebhookMessage(webhookMessage, webhook))
            .thenAccept(PinnedMessageForwarder::handleResponse)
            .thenCompose(unused -> CompletableFuture.allOf(
                sendPinConfirmation(message),
                unpinOriginalMessage(message)
            ));
    }

    private static CompletableFuture<UserDetails> retrieveUserDetails(User author, Guild guild) {
        if (PinBotSettings.usesGuildProfile(guild.getIdLong())) {
            return guild.retrieveMember(author)
                .submit()
                .thenApply(UserDetails::new)
                .exceptionally(throwable -> new UserDetails(author));
        } else {
            return CompletableFuture.completedFuture(new UserDetails(author));
        }
    }

    private static String createWebhookMessage(Message message, String username, String avatarUrl) {
        String messageContent = getMessageContent(message);

        JsonObject messageLinkButton = new JsonObject();
        messageLinkButton.addProperty("type", 2);
        messageLinkButton.addProperty("style", 5);
        messageLinkButton.addProperty("label", "Original message");
        messageLinkButton.addProperty("url", message.getJumpUrl());

        JsonArray actionRowComponents = new JsonArray();
        actionRowComponents.add(messageLinkButton);

        JsonObject actionRow = new JsonObject();
        actionRow.addProperty("type", 1);
        actionRow.add("components", actionRowComponents);

        JsonArray components = new JsonArray();
        components.add(actionRow);

        JsonObject webhookMessage = new JsonObject();
        webhookMessage.addProperty("content", messageContent);
        webhookMessage.addProperty("username", username);
        webhookMessage.addProperty("avatar_url", avatarUrl);
        webhookMessage.add("components", components);

        return webhookMessage.toString();
    }

    private static String getMessageContent(Message message) {
        StringBuilder messageContentBuilder = new StringBuilder(message.getContentRaw());
        for (Message.Attachment attachment : message.getAttachments()) {
            messageContentBuilder.append("\n").append(attachment.getUrl());
        }
        for (StickerItem sticker : message.getStickers()) {
            messageContentBuilder.append("\n").append(sticker.getIconUrl());
        }
        return messageContentBuilder.toString();
    }

    private static CompletableFuture<HttpResponse<String>> sendWebhookMessage(String webhookMessage, Webhook webhook) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(webhook.getUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(webhookMessage))
                .build();
            return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static void handleResponse(HttpResponse<?> response) {
        // Check if status code is not 2xx
        if (response.statusCode() / 100 != 2) {
            throw new HttpException(
                "The webhook message request failed with status code "
                    + response.statusCode()
                    + ". Response body:\n"
                    + response.body()
            );
        }
    }

    private static CompletableFuture<Message> sendPinConfirmation(Message pinned) {
        return PinBotSettings.getPinChannel(pinned.getChannel().getIdLong())
            .map(pinChannelId -> pinned.getJDA().getChannelById(Channel.class, pinChannelId))
            .map(pinChannel -> createPinConfirmationMessage(pinned.getAuthor(), pinChannel, pinned))
            .map(messageCreateData -> pinned.getChannel().sendMessage(messageCreateData).submit())
            .orElseGet(() -> CompletableFuture.failedFuture(new IllegalArgumentException("No pin channel set for " + pinned.getChannel() + ".")));
    }

    private static CompletableFuture<Void> unpinOriginalMessage(Message pinned) {
        return pinned.unpin().submit();
    }

    private static void handleError(@Nullable Throwable throwable, MessageChannel channel) {
        if (throwable != null) {
            Main.getLogger().error("An error occurred while pinning a message.", throwable);
            channel.sendMessage("An error occurred while pinning this message.").queue();
        }
    }

    private record UserDetails(String username, String avatarUrl) {

        public UserDetails(User user) {
            this(user.getName(), user.getEffectiveAvatarUrl());
        }

        public UserDetails(Member member) {
            this(member.getEffectiveName(), member.getEffectiveAvatarUrl());
        }
    }
}
