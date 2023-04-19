package io.github.shaksternano.pinbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.exceptions.HttpException;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.SplitUtil;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PinnedMessageForwarder {

    private static final int MESSAGE_LENGTH_LIMIT = 2000;

    public static void sendCustomPinConfirmationIfPinChannelSet(MessageReceivedEvent event) {
        var pinConfirmation = event.getMessage();
        var sentFrom = event.getChannel();
        if (pinConfirmation.getType().equals(MessageType.CHANNEL_PINNED_ADD)) {
            Database.getPinChannel(sentFrom.getIdLong())
                .ifPresent(pinChannelId -> sendCustomPinConfirmation(pinConfirmation, pinChannelId));
        }
    }

    private static void sendCustomPinConfirmation(Message pinConfirmation, long pinChannelId) {
        var pinnedMessageReference = pinConfirmation.getMessageReference();
        if (pinnedMessageReference == null) {
            Main.getLogger().error("System pin confirmation message has no message reference.");
        } else {
            var sentFrom = pinConfirmation.getChannel();
            var pinner = pinConfirmation.getAuthor();
            pinConfirmation.delete().queue();
            sentFrom.retrieveMessageById(pinnedMessageReference.getMessageId())
                .submit()
                .thenCompose(pinnedMessage -> sendPinConfirmation(sentFrom, pinChannelId, pinner, pinnedMessage))
                .whenComplete((unused, throwable) -> handleError(throwable, sentFrom));
        }
    }

    private static CompletableFuture<Message> sendPinConfirmation(MessageChannel sendTo, long pinChannelId, User pinner, Message pinnedMessage) {
        var pinChannel = sendTo.getJDA().getChannelById(Channel.class, pinChannelId);
        if (pinChannel == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No pin channel set for " + sendTo + "."));
        } else {
            try (var customPinConfirmation = createPinConfirmationMessage(pinner, pinChannel, pinnedMessage)) {
                return sendTo.sendMessage(customPinConfirmation).submit();
            }
        }
    }

    private static MessageCreateData createPinConfirmationMessage(User pinner, Channel pinChannel, Message pinnedMessage) {
        return new MessageCreateBuilder()
            .addContent(pinner.getAsMention() + " pinned a message to " + pinChannel.getAsMention() + ".")
            .addActionRow(Button.link(pinnedMessage.getJumpUrl(), "Jump to message"))
            .build();
    }

    public static void forwardMessageIfPinned(MessageUpdateEvent event) {
        var message = event.getMessage();
        if (message.isPinned()) {
            Database.getPinChannel(event.getChannel().getIdLong())
                .ifPresent(pinChannelId -> forwardPinnedMessage(message, pinChannelId));
        }
    }

    private static void forwardPinnedMessage(Message message, long pinChannelId) {
        var sentFrom = message.getChannel();
        var pinChannel = message.getGuild().getChannelById(Channel.class, pinChannelId);
        getWebhookContainer(pinChannel).ifPresentOrElse(
            webhookContainer -> webhookContainer.retrieveWebhooks()
                .submit()
                .thenCompose(webhooks -> getOrCreateWebhook(webhooks, webhookContainer))
                .thenApply(webhook -> getWebhookUrl(webhook, pinChannel))
                .thenCompose(webhook -> forwardMessageToWebhook(message, webhook))
                .whenComplete((unused, throwable) -> handleError(throwable, sentFrom)),
            () -> handleNoWebhookSupport(sentFrom, pinChannel)
        );
    }

    private static Optional<IWebhookContainer> getWebhookContainer(Channel channel) {
        if (channel instanceof ThreadChannel threadChannel) {
            channel = threadChannel.getParentChannel();
        }
        if (channel instanceof IWebhookContainer webhookContainer) {
            return Optional.of(webhookContainer);
        } else {
            return Optional.empty();
        }
    }

    private static CompletableFuture<Webhook> getOrCreateWebhook(Collection<Webhook> webhooks, IWebhookContainer webhookContainer) {
        return getOwnWebhook(webhooks)
            .map(CompletableFuture::completedFuture)
            .orElseGet(() -> createWebhook(webhookContainer));
    }

    private static String getWebhookUrl(Webhook webhook, Channel sendTo) {
        var webhookUrl = webhook.getUrl();
        if (sendTo instanceof ThreadChannel) {
            webhookUrl += "?thread_id=" + sendTo.getId();
        }
        return webhookUrl;
    }

    private static Optional<Webhook> getOwnWebhook(Collection<Webhook> webhooks) {
        return webhooks.stream()
            .filter(PinnedMessageForwarder::isOwnWebhook)
            .findAny();
    }

    private static boolean isOwnWebhook(Webhook webhook) {
        return webhook.getJDA().getSelfUser().equals(webhook.getOwnerAsUser());
    }

    private static CompletableFuture<Webhook> createWebhook(IWebhookContainer webhookContainer) {
        return retrieveIcon(webhookContainer.getJDA().getSelfUser())
            .thenCompose(iconOptional -> createWebhook(webhookContainer, iconOptional.orElse(null)));
    }

    private static CompletableFuture<Optional<Icon>> retrieveIcon(User user) {
        return CompletableFuture.supplyAsync(() -> getIcon(user));
    }

    private static Optional<Icon> getIcon(User user) {
        var avatarUrl = user.getEffectiveAvatarUrl();
        try (var iconStream = new URL(avatarUrl).openStream()) {
            return Optional.of(Icon.from(iconStream));
        } catch (IOException e) {
            Main.getLogger().error("Failed to create icon for " + user + ".", e);
            return Optional.empty();
        }
    }

    private static CompletableFuture<Webhook> createWebhook(IWebhookContainer webhookContainer, @Nullable Icon icon) {
        return webhookContainer.createWebhook(webhookContainer.getJDA().getSelfUser().getName())
            .setAvatar(icon)
            .submit();
    }

    private static CompletableFuture<Void> forwardMessageToWebhook(Message message, String webhookUrl) {
        var author = message.getAuthor();
        var guild = message.getGuild();
        return retrieveUserDetails(author, guild)
            .thenApply(userDetails -> createWebhookRequests(message, userDetails.username(), userDetails.avatarUrl()))
            .thenCompose(requestBodies -> sendWebhookMessages(requestBodies, webhookUrl))
            .thenCompose(unused -> unpinOriginalMessage(message));
    }

    private static CompletableFuture<UserDetails> retrieveUserDetails(User author, Guild guild) {
        if (Database.usesGuildProfile(guild.getIdLong())) {
            return guild.retrieveMember(author)
                .submit()
                .thenApply(UserDetails::new)
                .exceptionally(throwable -> new UserDetails(author));
        } else {
            return CompletableFuture.completedFuture(new UserDetails(author));
        }
    }

    private static List<String> createWebhookRequests(Message message, String username, String avatarUrl) {
        var messageContents = getMessageContent(message);
        List<String> requestBodies = new ArrayList<>();
        for (int i = 0; i < messageContents.size(); i++) {
            String messageContent = messageContents.get(i);
            boolean lastElement = i == messageContents.size() - 1;
            String requestBody = createWebhookRequest(messageContent, username, avatarUrl, message.getJumpUrl(), lastElement);
            requestBodies.add(requestBody);
        }
        return requestBodies;
    }

    private static List<String> getMessageContent(Message message) {
        var messageContentBuilder = new StringBuilder(message.getContentRaw());
        for (var attachment : message.getAttachments()) {
            messageContentBuilder.append("\n").append(attachment.getUrl());
        }
        for (var sticker : message.getStickers()) {
            messageContentBuilder.append("\n").append(sticker.getIconUrl());
        }
        return SplitUtil.split(
            messageContentBuilder.toString(),
            MESSAGE_LENGTH_LIMIT,
            true,
            SplitUtil.Strategy.NEWLINE,
            SplitUtil.Strategy.WHITESPACE,
            SplitUtil.Strategy.ANYWHERE
        );
    }

    private static String createWebhookRequest(String messageContent, String username, String avatarUrl, String originalMessageUrl, boolean originalMessageButton) {
        var requestBody = new JsonObject();
        requestBody.addProperty("content", messageContent);
        requestBody.addProperty("username", username);
        requestBody.addProperty("avatar_url", avatarUrl);

        if (originalMessageButton) {
            var messageLinkButton = new JsonObject();
            messageLinkButton.addProperty("type", 2);
            messageLinkButton.addProperty("style", 5);
            messageLinkButton.addProperty("label", "Original message");
            messageLinkButton.addProperty("url", originalMessageUrl);

            var actionRowComponents = new JsonArray();
            actionRowComponents.add(messageLinkButton);

            var actionRow = new JsonObject();
            actionRow.addProperty("type", 1);
            actionRow.add("components", actionRowComponents);

            var components = new JsonArray();
            components.add(actionRow);
            requestBody.add("components", components);
        }

        return requestBody.toString();
    }

    private static CompletableFuture<Void> sendWebhookMessages(Iterable<String> requestBodies, String webhookUrl) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (var requestBody : requestBodies) {
            future = future.thenCompose(unused -> sendWebhookMessage(requestBody, webhookUrl))
                .thenApply(unused -> null);
        }
        return future;
    }

    private static CompletableFuture<HttpResponse<String>> sendWebhookMessage(String requestBody, String webhookUrl) {
        try {
            var request = HttpRequest.newBuilder()
                .uri(new URI(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            return HttpClient.newHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete(PinnedMessageForwarder::handleResponse);
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static void handleResponse(HttpResponse<?> response, Throwable throwable) {
        if (throwable == null) {
            // Check if status code is not 2xx
            if (response.statusCode() / 100 != 2) {
                throw new HttpException(
                    "The webhook message request failed with status code "
                        + response.statusCode()
                        + ". Response body:\n"
                        + response.body()
                );
            }
        } else {
            throw new HttpException("Failed to send webhook message.", throwable);
        }
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

    private static void handleNoWebhookSupport(MessageChannel sentFrom, @Nullable Channel pinChannel) {
        if (pinChannel != null) {
            sentFrom.sendMessage(pinChannel.getAsMention() + " doesn't support webhooks.").queue();
        }
        Database.removeSendPinFromChannel(sentFrom.getIdLong());
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
