package io.github.shaksternano.pinbot.command;

import io.github.shaksternano.pinbot.PinBotSettings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PinChannelListCommand extends PinSubCommand {

    private static final PinChannelListCommand INSTANCE = new PinChannelListCommand();

    private PinChannelListCommand() {
        super("list", "Lists all channels that are currently sending pins to another channel.");
    }

    @Override
    public String execute(SlashCommandInteractionEvent event) {
        Guild guild = getGuild(event);
        Map<Long, Long> serverPinChannels = PinBotSettings.getPinChannels(guild.getIdLong());
        if (serverPinChannels.isEmpty()) {
            return "There are no pin channels currently.";
        } else {
            return "Pin channels:\n" + serverPinChannels
                    .entrySet()
                    .stream()
                    .map(entry -> {
                        Channel sendPinFrom = getChannel(guild, entry.getKey());
                        Channel sendPinTo = getChannel(guild, entry.getValue());
                        return sendPinFrom.getAsMention() + " -> " + sendPinTo.getAsMention();
                    })
                    .collect(Collectors.joining("\n"));
        }
    }

    @Override
    public Collection<OptionData> getOptions() {
        return List.of();
    }

    public static PinChannelListCommand getInstance() {
        return INSTANCE;
    }
}
