package io.github.shaksternano.pinbot;

import io.github.shaksternano.pinbot.command.PinChannelListCommand;
import io.github.shaksternano.pinbot.command.PinChannelRemoveCommand;
import io.github.shaksternano.pinbot.command.PinChannelSetCommand;
import io.github.shaksternano.pinbot.command.UsesGuildProfileCommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger("Pin Bot");
    private static JDA jda;

    public static void main(String[] args) {
        System.setProperty("log4j2.contextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
        ProgramArguments arguments = new ProgramArguments(args);
        arguments.getArgumentOrEnvironmentVariable("DISCORD_BOT_TOKEN")
            .ifPresentOrElse(Main::init, Main::handleNoToken);
    }

    private static void init(String token) {
        jda = JDABuilder.createDefault(token)
            .enableIntents(GatewayIntent.MESSAGE_CONTENT)
            .addEventListeners(PinBotEventListener.getInstance())
            .build();
        CommandClient.addCommands(
            jda,
            PinChannelSetCommand.getInstance(),
            PinChannelListCommand.getInstance(),
            PinChannelRemoveCommand.getInstance(),
            UsesGuildProfileCommand.getInstance()
        );
    }

    private static void handleNoToken() {
        getLogger().error(
            "No Discord bot token provided." +
                "Please provide a token via the" +
                "program argument or environment" +
                "variable \"DISCORD_BOT_TOKEN\"."
        );
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public static JDA getJDA() {
        return jda;
    }
}
