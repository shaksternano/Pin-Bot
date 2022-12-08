package io.github.shaksternano.pinbot;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class PinBotSettings {

    private static final DB DB = DBMaker.fileDB("pin-bot-settings.mapdb")
            .transactionEnable()
            .make();

    private static final Map<Long, Long> PIN_CHANNELS = DB.hashMap("pinChannels")
            .keySerializer(Serializer.LONG)
            .valueSerializer(Serializer.LONG)
            .createOrOpen();

    private static final Map<Long, Long> SERVER_PIN_CHANNELS = DB.hashMap("serverPinChannels")
            .keySerializer(Serializer.LONG)
            .valueSerializer(Serializer.LONG)
            .createOrOpen();

    private static final Set<Long> USES_SERVER_PROFILE = DB.hashSet("usesServerProfile")
            .serializer(Serializer.LONG)
            .createOrOpen();

    public static Optional<Long> getPinChannel(long sendPinFromChannelId) {
        return Optional.ofNullable(PIN_CHANNELS.get(sendPinFromChannelId));
    }

    public static Map<Long, Long> getPinChannels(long serverId) {
        return SERVER_PIN_CHANNELS
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(serverId))
                .map(entry -> Map.entry(entry.getKey(), PIN_CHANNELS.get(entry.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static void setPinChannel(long sendPinFromChannelId, long sendPinToChannelId, long serverId) {
        PIN_CHANNELS.put(sendPinFromChannelId, sendPinToChannelId);
        SERVER_PIN_CHANNELS.put(sendPinFromChannelId, serverId);
        DB.commit();
    }

    public static void removePinChannel(long sendPinFromChannelId) {
        PIN_CHANNELS.remove(sendPinFromChannelId);
        SERVER_PIN_CHANNELS.remove(sendPinFromChannelId);
        DB.commit();
    }

    public static boolean usesServerProfile(long serverId) {
        return USES_SERVER_PROFILE.contains(serverId);
    }

    public static void setUsesServerProfile(long guildId, boolean useServerProfile) {
        if (useServerProfile) {
            USES_SERVER_PROFILE.add(guildId);
        } else {
            USES_SERVER_PROFILE.remove(guildId);
        }
        DB.commit();
    }
}
