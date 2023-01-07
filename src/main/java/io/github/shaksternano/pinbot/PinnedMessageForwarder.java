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

    public static void sendPinConfirmationAndDeleteSystemMessage(MessageReceivedEvent event) {
        if (event.getMessage().getType().equals(MessageType.CHANNEL_PINNED_ADD)
            && PinBotSettings.getPinChannel(event.getChannel().getIdLong()).isPresent()) {
            MessageReference pinnedMessageReference = event.getMessage().getMessageReference();
            if (pinnedMessageReference == null) {
                Main.getLogger().error("System pin confirmation message has no message reference.");
            } else {
                event.getMessage().delete().queue();
                event.getChannel()
                    .retrieveMessageById(pinnedMessageReference.getMessageId())
                    .submit()
                    .thenCompose(pinnedMessage -> sendPinConfirmation(event, pinnedMessage));
            }
        }
    }

    private static CompletableFuture<Message> sendPinConfirmation(MessageReceivedEvent event, Message pinnedMessage) {
        return PinBotSettings.getPinChannel(event.getChannel().getIdLong())
            .map(pinChannelId -> event.getJDA().getChannelById(Channel.class, pinChannelId))
            .map(pinChannel -> createPinConfirmationMessage(event.getAuthor(), pinChannel, pinnedMessage))
            .map(messageCreateData -> event.getChannel().sendMessage(messageCreateData).submit())
            .orElseGet(() -> CompletableFuture.failedFuture(new IllegalArgumentException("No pin channel set for " + event.getChannel() + ".")));
    }

    private static MessageCreateData createPinConfirmationMessage(User pinner, Channel pinChannel, Message pinnedMessage) {
        return new MessageCreateBuilder()
            .addContent(pinner.getAsMention() + " pinned a message to " + pinChannel.getAsMention() + ".")
            .addActionRow(Button.link(pinnedMessage.getJumpUrl(), "Jump to message"))
            .build();
    }

    public static void forwardPinnedMessage(MessageUpdateEvent event) {
        Message message = event.getMessage();
        if (message.isPinned()) {
            MessageChannel channel = event.getChannel();
            PinBotSettings.getPinChannel(channel.getIdLong()).ifPresent(channelId -> {
                Channel pinChannel = event.getGuild().getChannelById(Channel.class, channelId);
                if (pinChannel instanceof IWebhookContainer webhookContainer) {
                    webhookContainer.retrieveWebhooks()
                        .submit()
                        .thenCompose(webhooks -> getOrCreateWebhook(webhooks, webhookContainer))
                        .thenCompose(webhook -> forwardPinnedMessage(message, webhook))
                        .exceptionallyCompose(throwable -> handleError(throwable, channel));
                } else {
                    if (pinChannel != null) {
                        event.getChannel().sendMessage(pinChannel.getAsMention() + " doesn't support webhooks!.").queue();
                    }
                    PinBotSettings.removeSendPinFromChannel(channel.getIdLong());
                }
            });
        }
    }

    private static CompletableFuture<Webhook> getOrCreateWebhook(Collection<Webhook> webhooks, IWebhookContainer webhookContainer) {
        return getWebhook(webhooks)
            .map(CompletableFuture::completedFuture)
            .orElseGet(() -> createWebhook(webhookContainer));
    }

    private static Optional<Webhook> getWebhook(Collection<Webhook> webhooks) {
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

    private static CompletableFuture<Webhook> createWebhook(IWebhookContainer webhookContainer, @Nullable Icon icon) {
        return webhookContainer.createWebhook(Main.getJDA().getSelfUser().getName())
            .setAvatar(icon)
            .submit();
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

    private static CompletableFuture<Void> forwardPinnedMessage(Message message, Webhook webhook) {
        User author = message.getAuthor();
        Guild guild = message.getGuild();
        return retrieveUserDetails(author, guild)
            .thenApply(userDetails -> createWebhookMessage(message, userDetails.username(), userDetails.avatarUrl()))
            .thenCompose(webhookMessage -> sendWebhookMessage(webhookMessage, webhook))
            .thenCompose(response -> unpinOriginalMessage(message, response));
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

    private static CompletableFuture<Void> unpinOriginalMessage(Message message, HttpResponse<?> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return message.unpin().submit();
        } else {
            return CompletableFuture.failedFuture(new HttpException(
                "The webhook message request failed with status code "
                    + response.statusCode()
                    + ". Response body:\n"
                    + response.body()
            ));
        }
    }

    private static CompletableFuture<Void> handleError(Throwable throwable, MessageChannel channel) {
        Main.getLogger().error("An error occurred while pinning a message.", throwable);
        return channel.sendMessage("An error occurred while pinning this message.")
            .submit()
            .thenApply(message -> null);
    }

    private record UserDetails(String username, String avatarUrl) {

        public UserDetails(User user) {
            this(user.getName(), user.getAvatarUrl());
        }

        public UserDetails(Member member) {
            this(member.getEffectiveName(), member.getEffectiveAvatarUrl());
        }
    }
}
