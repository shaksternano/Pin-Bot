package io.github.shaksternano.pinbot.command;

import io.github.shaksternano.pinbot.PinBotSettings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collection;
import java.util.List;

public class UsesServerProfileCommand extends PinSubCommand {

    private static final UsesServerProfileCommand INSTANCE = new UsesServerProfileCommand();

    private static final String BOOLEAN_OPTION = "boolean";

    private UsesServerProfileCommand() {
        super("server-profile", "Checks if the server profile of a user is used. If a boolean is provided, it will be set.");
    }

    @Override
    public String execute(SlashCommandInteractionEvent event) {
        Guild guild = getGuild(event);
        return getOptionalOption(event, BOOLEAN_OPTION)
                .map(OptionMapping::getAsBoolean)
                .map(usesServerProfile -> {
                    PinBotSettings.setUsesServerProfile(guild.getIdLong(), usesServerProfile);
                    if (usesServerProfile) {
                        return "The server profile of a user is now used.";
                    } else {
                        return "The server profile of a user is no longer used.";
                    }
                }).orElseGet(() -> {
                    if (PinBotSettings.usesServerProfile(guild.getIdLong())) {
                        return "The server profile of a user is used.";
                    } else {
                        return "The server profile of a user is not used.";
                    }
                });
    }

    @Override
    public Collection<OptionData> getOptions() {
        return List.of(new OptionData(OptionType.BOOLEAN, BOOLEAN_OPTION, "Sets if the server profile of a user is used.", false));
    }

    public static UsesServerProfileCommand getInstance() {
        return INSTANCE;
    }
}
