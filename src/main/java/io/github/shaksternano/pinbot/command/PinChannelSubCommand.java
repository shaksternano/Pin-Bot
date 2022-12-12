package io.github.shaksternano.pinbot.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public abstract class PinChannelSubCommand extends BaseCommand {

    public PinChannelSubCommand(String name, String description) {
        super(name, description);
    }

    @Override
    public Optional<String> getGroup() {
        return Optional.of("pin-channel");
    }

    @Override
    public DefaultMemberPermissions getPermissions() {
        return DefaultMemberPermissions.DISABLED;
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    protected Guild getGuild(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            throw new IllegalArgumentException("Command used in a private channel (direct message).");
        } else {
            return guild;
        }
    }

    protected OptionMapping getRequiredOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        if (option == null) {
            throw new IllegalArgumentException("No option with name " + name + " provided.");
        } else {
            return option;
        }
    }

    protected Optional<OptionMapping> getOptionalOption(SlashCommandInteractionEvent event, String name) {
        return Optional.ofNullable(event.getOption(name));
    }

    protected Channel getChannel(Guild guild, long id) {
        Channel channel = guild.getChannelById(Channel.class, id);
        if (channel == null) {
            throw new IllegalArgumentException("Channel with id " + id + " not found.");
        } else {
            return channel;
        }
    }
}
