package dev.tempest.util;

import com.google.gson.JsonObject;
import dev.tempest.module.ModuleManager;
import dev.tempest.modules.CapesModule;
import dev.tempest.modules.CosmeticsModule;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosmetics of other Tempest players (cape, wings, head piece), as the Tempest service reports them: filled from
 * the nearby-player lookup and kept fresh by live "cos" messages. Also builds what you share about yourself.
 *
 * <p>Everything received is checked against the cosmetics this version knows; anything unknown is dropped,
 * so a newer client's items simply don't show instead of breaking anything.</p>
 */
public final class RemoteCosmetics {
    /** One player's look. {@code cape} is the lower-case texture key or null; wings/head use option names. */
    public record Look(String cape, String wings, String head, String halo, float wingSize, float flapSpeed) {
        public boolean hasWings() { return wings != null && !wings.equals("None"); }
        public boolean hasHead() { return head != null && !head.equals("None"); }

        public int haloRgb() { return CosmeticsModule.haloRgb(halo); }
    }

    private static final Map<UUID, Look> LOOKS = new ConcurrentHashMap<>();

    private RemoteCosmetics() { }

    /** Look of another player, or null if they share nothing (or "see others" is off). */
    public static Look of(UUID id) {
        if (id == null || !seeOthers()) return null;
        return LOOKS.get(id);
    }

    public static void forget(UUID id) { LOOKS.remove(id); }

    public static void clear() { LOOKS.clear(); }

    /** From the service: {"cape":"Galaxy","wings":"Angel","head":"Halo","halo":"Gold","wingSize":1.2}. */
    public static void put(UUID id, JsonObject c) {
        if (id == null) return;
        Look l = parse(c);
        if (l == null) LOOKS.remove(id); else LOOKS.put(id, l);
    }

    static Look parse(JsonObject c) {
        if (c == null) return null;
        String cape = pick(str(c, "cape"), CapesModule.CAPES);
        String wings = pick(str(c, "wings"), CosmeticsModule.WINGS);
        String head = pick(str(c, "head"), CosmeticsModule.HEADS);
        String halo = pick(str(c, "halo"), CosmeticsModule.HALO_COLORS);
        float ws = 1f;
        try { if (c.has("wingSize")) ws = Math.max(0.6f, Math.min(1.6f, c.get("wingSize").getAsFloat())); } catch (Exception ignored) { }
        if ("None".equals(cape)) cape = null;
        boolean anything = cape != null || (wings != null && !wings.equals("None")) || (head != null && !head.equals("None"));
        if (!anything) return null;
        return new Look(cape == null ? null : cape.toLowerCase(Locale.ROOT), wings == null ? "None" : wings,
                head == null ? "None" : head, halo == null ? "Gold" : halo, ws, 1f);
    }

    private static String str(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null; } catch (Exception e) { return null; }
    }

    private static String pick(String v, String[] known) {
        if (v == null) return null;
        for (String k : known) if (k.equalsIgnoreCase(v)) return k;
        return null;
    }

    // ------------------------------------------------------------------ settings
    public static boolean seeOthers() {
        CosmeticsModule m = ModuleManager.get(CosmeticsModule.class);
        return m != null && m.seeOthers.get();
    }

    public static boolean share() {
        CosmeticsModule m = ModuleManager.get(CosmeticsModule.class);
        return m != null && m.share.get();
    }

    /** What you show other Tempest players: your saved choices (never a try-on preview). Empty when sharing is off. */
    public static JsonObject mine() {
        JsonObject o = new JsonObject();
        if (!share()) return o;
        CapesModule cp = ModuleManager.get(CapesModule.class);
        if (cp != null && cp.isEnabled() && !cp.cape.is("None")) o.addProperty("cape", cp.cape.get());
        CosmeticsModule m = ModuleManager.get(CosmeticsModule.class);
        if (m != null && m.isEnabled()) {
            if (!m.wings.is("None")) {
                o.addProperty("wings", m.wings.get());
                o.addProperty("wingSize", Math.round(m.wingSize.getFloat() * 100) / 100f);
            }
            if (!m.head.is("None")) o.addProperty("head", m.head.get());
            if (m.head.is("Halo")) o.addProperty("halo", m.haloColor.is("Theme") ? "Gold" : m.haloColor.get());
        }
        return o;
    }
}
