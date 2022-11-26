package io.github.shaksternano.pinbot.command;

import io.github.shaksternano.pinbot.PinBotSettings;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collection;
import java.util.List;

public class RemovePinChannelCommand extends PinSubCommand {

    private static final RemovePinChannelCommand INSTANCE = new RemovePinChannelCommand();

    private RemovePinChannelCommand() {
        super("remove", "Stops sending pinned messages from this channel to the pin channel.");
    }

    @Override
    public String execute(SlashCommandInteractionEvent event) {
        Channel sendPinFrom = event.getChannel();
        PinBotSettings.removePinChannel(sendPinFrom.getIdLong());
        return "Pins from " + sendPinFrom.getAsMention() + " will no longer be sent to another channel.";
    }

    @Override
    public Collection<OptionData> getOptions() {
        return List.of();
    }

    public static RemovePinChannelCommand getInstance() {
        return INSTANCE;
    }
}
