package io.github.shaksternano.pinbot;

import io.github.shaksternano.pinbot.command.PinChannelCommand;
import io.github.shaksternano.pinbot.command.PinChannelListCommand;
import io.github.shaksternano.pinbot.command.RemovePinChannelCommand;
import io.github.shaksternano.pinbot.command.UsesServerProfileCommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger("Pin Bot");
    private static JDA jda;

    public static void main(String[] args) {
        System.setProperty("log4j2.contextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
        ProgramArguments arguments = new ProgramArguments(args);
        Optional<String> token = arguments.getArgumentOrEnvironmentVariable("DISCORD_BOT_TOKEN");
        if (token.isEmpty()) {
            LOGGER.error("No Discord bot token provided. Please provide a token via the program argument or environment variable \"DISCORD_BOT_TOKEN\".");
            return;
        }
        jda = JDABuilder.createDefault(token.orElseThrow())
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(PinBotEventListener.getInstance())
                .build();
        CommandClient.addCommands(
                jda,
                PinChannelCommand.getInstance(),
                PinChannelListCommand.getInstance(),
                RemovePinChannelCommand.getInstance(),
                UsesServerProfileCommand.getInstance()
        );
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public static JDA getJDA() {
        return jda;
    }
}