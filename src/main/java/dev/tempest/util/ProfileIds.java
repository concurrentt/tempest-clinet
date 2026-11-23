package dev.tempest.util;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reads the UUID from a Mojang GameProfile. The accessor is id() in newer authlib and getId() in older ones,
 * so it's looked up once by name and cached (the tab list asks every frame).
 */
public final class ProfileIds {
    private static volatile Method accessor;
    private static volatile boolean failed;

    private ProfileIds() { }

    public static UUID of(Object profile) {
        if (profile == null || failed) return null;
        try {
            Method m = accessor;
            if (m == null) {
                for (String name : new String[]{"id", "getId"}) {
                    try {
                        Method c = profile.getClass().getMethod(name);
                        if (c.getReturnType() == UUID.class) { m = c; break; }
                    } catch (NoSuchMethodException ignored) { }
                }
                if (m == null) { failed = true; return null; }
                accessor = m;
            }
            return (UUID) m.invoke(profile);
        } catch (Exception e) {
            return null;
        }
    }
}
