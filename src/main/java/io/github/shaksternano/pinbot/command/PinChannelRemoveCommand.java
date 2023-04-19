package io.github.shaksternano.pinbot.command;

import io.github.shaksternano.pinbot.Database;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collection;
import java.util.List;

public class PinChannelRemoveCommand extends PinChannelSubCommand {

    private static final PinChannelRemoveCommand INSTANCE = new PinChannelRemoveCommand();

    private PinChannelRemoveCommand() {
        super("remove", "Stops sending pinned messages from this channel to the pin channel.");
    }

    @Override
    public String execute(SlashCommandInteractionEvent event) {
        var sendPinFrom = event.getChannel();
        Database.removeSendPinFromChannel(sendPinFrom.getIdLong());
        return "Pins from " + sendPinFrom.getAsMention() + " will no longer be sent to another channel.";
    }

    @Override
    public Collection<OptionData> getOptions() {
        return List.of();
    }

    public static PinChannelRemoveCommand getInstance() {
        return INSTANCE;
    }
}
