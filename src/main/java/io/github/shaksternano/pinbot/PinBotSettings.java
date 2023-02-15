package io.github.shaksternano.pinbot;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class PinBotSettings {

    private static final DB DB = DBMaker.fileDB("database.mapdb")
        .transactionEnable()
        .make();

    /**
     * Maps the ID of a channel A to the ID of a channel B, where pins from channel A are forwarded to channel B.
     */
    private static final Map<Long, Long> PIN_CHANNELS = DB.hashMap("pinChannels")
        .keySerializer(Serializer.LONG)
        .valueSerializer(Serializer.LONG)
        .createOrOpen();

    /**
     * Maps the ID of a channel where pins are forwarded from to the ID of the guild that the channel belongs to.
     */
    private static final Map<Long, Long> GUILD_PIN_CHANNELS = DB.hashMap("guildPinChannels")
        .keySerializer(Serializer.LONG)
        .valueSerializer(Serializer.LONG)
        .createOrOpen();

    /**
     * A set of IDs of the guilds that use the guild profile for the author of pinned messages.
     */
    private static final Set<Long> USES_GUILD_PROFILE = DB.hashSet("usesGuildProfile")
        .serializer(Serializer.LONG)
        .createOrOpen();

    public static Optional<Long> getPinChannel(long sendPinFromChannelId) {
        return Optional.ofNullable(PIN_CHANNELS.get(sendPinFromChannelId));
    }

    public static Map<Long, Long> getPinChannels(long guildId) {
        return GUILD_PIN_CHANNELS.entrySet()
            .stream()
            .filter(entry -> entry.getValue().equals(guildId))
            .map(entry -> Map.entry(entry.getKey(), PIN_CHANNELS.get(entry.getKey())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static void setPinChannel(long sendPinFromChannelId, long sendPinToChannelId, long guildId) {
        PIN_CHANNELS.put(sendPinFromChannelId, sendPinToChannelId);
        GUILD_PIN_CHANNELS.put(sendPinFromChannelId, guildId);
        DB.commit();
    }

    public static void removeSendPinFromChannel(long sendPinFromChannelId) {
        PIN_CHANNELS.remove(sendPinFromChannelId);
        GUILD_PIN_CHANNELS.remove(sendPinFromChannelId);
        DB.commit();
    }

    public static boolean usesGuildProfile(long guildId) {
        return USES_GUILD_PROFILE.contains(guildId);
    }

    public static void setUsesGuildProfile(long guildId, boolean useGuildProfile) {
        if (useGuildProfile) {
            USES_GUILD_PROFILE.add(guildId);
        } else {
            USES_GUILD_PROFILE.remove(guildId);
        }
        DB.commit();
    }
}
