package dev.tempest.util;

import dev.tempest.TempestConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local friend list. Adding a name makes that player glow on your screen; nothing is sent to anyone. */
public final class FriendManager {
    private static final Set<String> CACHE = new HashSet<>();

    private FriendManager() { }

    private static void rebuild() {
        CACHE.clear();
        for (String f : TempestConfig.get().friends) CACHE.add(f.toLowerCase(Locale.ROOT));
    }

    public static boolean isFriend(String name) {
        if (name == null) return false;
        if (CACHE.isEmpty() && !TempestConfig.get().friends.isEmpty()) rebuild();
        return CACHE.contains(name.toLowerCase(Locale.ROOT));
    }

    public static void addFriend(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return;
        if (!TempestConfig.get().friends.contains(n)) TempestConfig.get().friends.add(n);
        rebuild();
        TempestConfig.save();
    }

    public static void removeFriend(String name) {
        TempestConfig.get().friends.remove(name.trim().toLowerCase(Locale.ROOT));
        rebuild();
        TempestConfig.save();
    }

    public static List<String> friends() { return TempestConfig.get().friends; }
}
