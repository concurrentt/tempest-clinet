package dev.tempest.util;

import net.minecraft.util.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;

/**
 * Swaps the cape (and elytra) texture inside the local player's skin.
 *
 * Minecraft keeps a player's textures in one immutable record (body, cape, elytra, model, secure),
 * where cape and elytra are "texture asset" objects. This class rebuilds that record with Tempest's
 * texture in the cape and elytra slots.
 *
 * It is written against the record's <i>shape</i> (a 5-part record whose first three parts share
 * one type) instead of Minecraft's class names, the same way PlayerEntityRendererMixin avoids
 * naming the renderer's types. If the shape is not what is expected on some Minecraft version, it
 * logs once and leaves the skin untouched, so the worst case is "no cape", never a crash.
 */
public final class CapeSkins {
    private static Object lastOriginal, lastResult;
    private static String lastKey;
    private static boolean warned;

    private CapeSkins() { }

    /** @return the skin with the Tempest cape applied, or null to keep the original. */
    public static Object apply(Object original, String key) {
        if (original == null || key == null) return null;
        if (original == lastOriginal && key.equals(lastKey)) return lastResult;
        try {
            Object result = rebuild(original, key);
            lastOriginal = original;
            lastKey = key;
            lastResult = result;
            return result;
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                System.err.println("[Tempest] Could not apply cape (" + t + "). Capes are disabled for this session.");
            }
            lastOriginal = original;
            lastKey = key;
            lastResult = null;
            return null;
        }
    }

    private static Object rebuild(Object skin, String key) throws Exception {
        Class<?> type = skin.getClass();
        RecordComponent[] rc = type.getRecordComponents();
        if (rc == null || rc.length < 3) throw new IllegalStateException("unexpected skin type " + type.getName());

        // body, cape, elytra share one asset type
        Class<?> assetType = rc[0].getType();
        if (rc[1].getType() != assetType || rc[2].getType() != assetType || !assetType.isInterface())
            throw new IllegalStateException("unexpected skin layout in " + type.getName());

        Object asset = capeAsset(assetType, key);

        Class<?>[] types = new Class<?>[rc.length];
        Object[] values = new Object[rc.length];
        for (int i = 0; i < rc.length; i++) {
            types[i] = rc[i].getType();
            Method accessor = rc[i].getAccessor();
            accessor.setAccessible(true);
            values[i] = accessor.invoke(skin);
        }
        values[1] = asset;   // cape
        values[2] = asset;   // elytra uses the same design
        Constructor<?> ctor = type.getDeclaredConstructor(types);
        ctor.setAccessible(true);
        return ctor.newInstance(values);
    }

    /**
     * A texture-asset object pointing at assets/tempest/textures/cape/&lt;key&gt;.png.
     * The asset type is an interface with an id and a texture path (both Identifiers). The id
     * is answered by whichever super-interface declares it; the path by the type itself.
     */
    private static Object capeAsset(Class<?> assetType, String key) {
        final Identifier id = Identifier.of("tempest", "cape/" + key);
        final Identifier path = Identifier.of("tempest", "textures/cape/" + key + ".png");
        InvocationHandler h = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "TempestCape[" + key + "]";
                };
            }
            Class<?> ret = method.getReturnType();
            if (ret == Identifier.class) return method.getDeclaringClass() == assetType ? path : id;
            if (ret == boolean.class) return false;
            if (ret.isPrimitive()) return 0;
            return null;
        };
        return Proxy.newProxyInstance(assetType.getClassLoader(), new Class<?>[]{assetType}, h);
    }
}
