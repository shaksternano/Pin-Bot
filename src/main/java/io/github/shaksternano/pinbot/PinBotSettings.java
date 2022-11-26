package io.github.shaksternano.pinbot;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class PinBotSettings {

    private static final DB db = DBMaker.fileDB("pin_bot_settings.mapdb")
            .transactionEnable()
            .make();

    private static final Map<Long, Long> pinChannels = db.hashMap("pinChannels")
            .keySerializer(Serializer.LONG)
            .valueSerializer(Serializer.LONG)
            .createOrOpen();

    private static final Map<Long, Long> serverPinChannels = db.hashMap("serverPinChannels")
            .keySerializer(Serializer.LONG)
            .valueSerializer(Serializer.LONG)
            .createOrOpen();

    private static final Set<Long> usesServerProfile = db.hashSet("usesServerProfile")
            .serializer(Serializer.LONG)
            .createOrOpen();

    public static Optional<Long> getPinChannel(long sendPinFromChannelId) {
        return Optional.ofNullable(pinChannels.get(sendPinFromChannelId));
    }

    public static Map<Long, Long> getPinChannels(long serverId) {
        return serverPinChannels
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(serverId))
                .map(entry -> Map.entry(entry.getKey(), pinChannels.get(entry.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static void setPinChannel(long sendPinFromChannelId, long sendPinToChannelId, long serverId) {
        pinChannels.put(sendPinFromChannelId, sendPinToChannelId);
        serverPinChannels.put(sendPinFromChannelId, serverId);
        db.commit();
    }

    public static void removePinChannel(long sendPinFromChannelId) {
        pinChannels.remove(sendPinFromChannelId);
        serverPinChannels.remove(sendPinFromChannelId);
        db.commit();
    }

    public static boolean usesServerProfile(long serverId) {
        return usesServerProfile.contains(serverId);
    }

    public static void setUsesServerProfile(long guildId, boolean useServerProfile) {
        if (useServerProfile) {
            usesServerProfile.add(guildId);
        } else {
            usesServerProfile.remove(guildId);
        }
        db.commit();
    }
}
