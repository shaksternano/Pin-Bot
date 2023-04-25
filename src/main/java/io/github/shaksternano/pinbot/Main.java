package io.github.shaksternano.pinbot;

import io.github.shaksternano.pinbot.command.PinChannelListCommand;
import io.github.shaksternano.pinbot.command.PinChannelRemoveCommand;
import io.github.shaksternano.pinbot.command.PinChannelSetCommand;
import io.github.shaksternano.pinbot.command.UsesGuildProfileCommand;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger("Pin Bot");

    public static void main(String[] args) {
        System.setProperty("log4j2.contextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
        BotConfig config;
        try {
            config = BotConfig.parse(new File(BotConfig.FILE_NAME));
        } catch (Exception e) {
            getLogger().error("Error reading " + BotConfig.FILE_NAME + " file.", e);
            return;
        }
        init(config);
    }

    private static void init(BotConfig config) {
        var jda = JDABuilder.createLight(config.token())
            .enableIntents(GatewayIntent.MESSAGE_CONTENT)
            .addEventListeners(PinBotEventListener.INSTANCE)
            .build();
        CommandClient.addCommands(
            jda,
            PinChannelSetCommand.INSTANCE,
            PinChannelListCommand.INSTANCE,
            PinChannelRemoveCommand.INSTANCE,
            UsesGuildProfileCommand.INSTANCE
        );
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
