package io.github.shaksternano.pinbot.command;

import io.github.shaksternano.pinbot.PinBotSettings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collection;
import java.util.List;

public class PinChannelCommand extends PinSubCommand {

    private static final PinChannelCommand INSTANCE = new PinChannelCommand();
    private static final String CHANNEL_OPTION = "channel";

    private PinChannelCommand() {
        super("set", "Sets the channel where pins from this channel are sent to.");
    }

    @Override
    public String execute(SlashCommandInteractionEvent event) {
        Guild guild = getGuild(event);
        OptionMapping mapping = getRequiredOption(event, CHANNEL_OPTION);
        Channel sendPinFrom = event.getChannel();
        Channel sendPinTo = mapping.getAsChannel();
        if (sendPinTo.getType().equals(ChannelType.TEXT)) {
            PinBotSettings.setPinChannel(sendPinFrom.getIdLong(), sendPinTo.getIdLong(), guild.getIdLong());
            return "Pins from " + sendPinFrom.getAsMention() + " will now be sent to " + sendPinTo.getAsMention() + ".";
        } else {
            return sendPinTo.getAsMention() + " is not a text channel.";
        }
    }

    @Override
    public Collection<OptionData> getOptions() {
        return List.of(new OptionData(OptionType.CHANNEL, CHANNEL_OPTION, "The channel where pins from this channel are sent to.", true));
    }

    public static PinChannelCommand getInstance() {
        return INSTANCE;
    }
}
