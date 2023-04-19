package io.github.shaksternano.pinbot.command;

import io.github.shaksternano.pinbot.Database;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collection;
import java.util.List;

public class PinChannelSetCommand extends PinChannelSubCommand {

    private static final PinChannelSetCommand INSTANCE = new PinChannelSetCommand();
    private static final String CHANNEL_OPTION = "channel";

    private PinChannelSetCommand() {
        super("set", "Sets the channel where pins from this channel are sent to.");
    }

    @Override
    public String execute(SlashCommandInteractionEvent event) {
        var guild = getGuild(event);
        var option = getRequiredOption(event, CHANNEL_OPTION);
        var sendPinFrom = event.getChannel();
        var sendPinTo = option.getAsChannel();
        if (isValidChannel(sendPinTo)) {
            Database.setPinChannel(sendPinFrom.getIdLong(), sendPinTo.getIdLong(), guild.getIdLong());
            return "Pins from " + sendPinFrom.getAsMention() + " will now be sent to " + sendPinTo.getAsMention() + ".";
        } else {
            return sendPinTo.getAsMention() + " is not a message channel that supports webhooks!";
        }
    }

    private static boolean isValidChannel(Channel channel) {
        return channel instanceof IWebhookContainer
            || channel instanceof ThreadChannel threadChannel && threadChannel.getParentChannel() instanceof IWebhookContainer;
    }

    @Override
    public Collection<OptionData> getOptions() {
        return List.of(new OptionData(OptionType.CHANNEL, CHANNEL_OPTION, "The channel where pins from this channel are sent to.", true));
    }

    public static PinChannelSetCommand getInstance() {
        return INSTANCE;
    }
}
