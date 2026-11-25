package dev.tempest.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Pack Editor's data: groups of categories (End Crystals, Obsidian, Crosshair...), each a set of game textures
 * that gets one "look": Vanilla, a colour style (a gradient map over the texture's brightness), a custom colour, or
 * the same textures taken from one of your installed packs. Applying writes the results into the "Tempest Custom"
 * pack (util/CustomPack) and reloads.
 *
 * <p>Looks are stored in config/tempest-packs.json as {"category": {"look": "Crimson", "color": 16711680}}.</p>
 */
public final class PackLooks {
    // ------------------------------------------------------------------ styles
    /** A colour style: three gradient stops for dark, mid and light pixels. */
    public record Style(String name, int dark, int mid, int light) { }

    public static final Style[] STYLES = {
            new Style("Crimson", 0x1A0003, 0xC0182A, 0xFF8A8A),
            new Style("Frost", 0x04121C, 0x3AB8E8, 0xD8F6FF),
            new Style("Toxic", 0x031A04, 0x3FD12E, 0xD6FFB0),
            new Style("Royal", 0x12041E, 0x8A3FE0, 0xE6CCFF),
            new Style("Gilded", 0x1C1002, 0xE0A820, 0xFFF0A8),
            new Style("Sakura", 0x1E0610, 0xF06AA8, 0xFFE0EE),
            new Style("Ocean", 0x020A1E, 0x2F6FE0, 0xBFD8FF),
            new Style("Midnight", 0x05050C, 0x3A3A60, 0x9A9AC0),
            new Style("Neon", 0x0A0024, 0x7A10FF, 0xFF5AF0),
            new Style("Pastel", 0x6A5C78, 0xC8B4E6, 0xFFF4FA),
            new Style("Monochrome", 0x000000, 0x808080, 0xFFFFFF),
            new Style("Inverted", 0, 0, 0),
    };

    public static Style style(String name) {
        for (Style s : STYLES) if (s.name.equals(name)) return s;
        return null;
    }

    /** Gradient-maps one ARGB pixel. Alpha is kept. */
    public static int recolor(int argb, Style s) {
        int a = argb >>> 24;
        if (a == 0 || s == null) return argb;
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        if (s.name.equals("Inverted")) return (a << 24) | ((255 - r) << 16) | ((255 - g) << 8) | (255 - b);
        float l = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
        final float mid = 0.45f;
        int rgb = l < mid ? lerp(s.dark, s.mid, l / mid) : lerp(s.mid, s.light, (l - mid) / (1 - mid));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Style made from one colour (the Custom look and screenshot colours). */
    public static Style custom(int rgb) {
        return new Style("Custom", scale(rgb, 0.12f), rgb & 0xFFFFFF, lerp(rgb, 0xFFFFFF, 0.72f));
    }

    private static int scale(int rgb, float f) {
        int r = Math.round(((rgb >> 16) & 255) * f), g = Math.round(((rgb >> 8) & 255) * f), b = Math.round((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static int lerp(int c0, int c1, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((c0 >> 16) & 255) + (((c1 >> 16) & 255) - ((c0 >> 16) & 255)) * t);
        int g = Math.round(((c0 >> 8) & 255) + (((c1 >> 8) & 255) - ((c0 >> 8) & 255)) * t);
        int b = Math.round((c0 & 255) + ((c1 & 255) - (c0 & 255)) * t);
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ categories
    /**
     * @param region part of the preview texture shown on cards {x, y, w, h} (null = all, first frame of animations)
     * @param paths  textures this look is applied to; "dir:textures/particle/glitter_" means every texture whose
     *               path starts with that
     * @param toggle extra switch shown on the page: "explosions", "glint", "invert" or null
     * @param events for the Sounds group: the sound events that get replaced (null for texture categories)
     */
    public record Category(String id, String group, String name, Item icon, String preview, int[] region, String[] paths,
                           String toggle, String[] events) {
        public Identifier previewId() { return Identifier.ofVanilla(preview); }
        public boolean isSound() { return events != null; }
    }

    private static Category tex(String id, String group, String name, Item icon, String preview, int[] region, String toggle, String... paths) {
        return new Category(id, group, name, icon, preview, region, paths, toggle, null);
    }

    private static Category sound(String id, String name, Item icon, String... events) {
        return new Category(id, "Sounds", name, icon, "", null, new String[0], null, events);
    }

    public static final String[] GROUPS = {"PvP", "Blocks", "Gear", "HUD", "Menus", "Misc", "Effects", "Sounds"};
    public static final Item[] GROUP_ICONS = {Items.END_CRYSTAL, Items.OBSIDIAN, Items.NETHERITE_CHESTPLATE, Items.TARGET, Items.CHEST,
            Items.ENCHANTED_BOOK, Items.FIREWORK_ROCKET, Items.NOTE_BLOCK};

    private static final String HEART = "textures/gui/sprites/hud/heart/";
    private static final String C = "textures/gui/container/";
    private static final String EQ = "textures/entity/equipment/";

    public static final Category[] CATEGORIES = {
            tex("end_crystal", "PvP", "End Crystals", Items.END_CRYSTAL, "textures/item/end_crystal.png", null, "explosions",
                    "textures/item/end_crystal.png", "textures/entity/end_crystal/end_crystal.png", "textures/entity/end_crystal/end_crystal_beam.png"),
            tex("respawn_anchor", "PvP", "Respawn Anchors", Items.RESPAWN_ANCHOR, "textures/block/respawn_anchor_top.png", null, null,
                    "textures/block/respawn_anchor_top.png", "textures/block/respawn_anchor_top_off.png", "textures/block/respawn_anchor_bottom.png",
                    "textures/block/respawn_anchor_side0.png", "textures/block/respawn_anchor_side1.png", "textures/block/respawn_anchor_side2.png",
                    "textures/block/respawn_anchor_side3.png", "textures/block/respawn_anchor_side4.png"),
            tex("totem", "PvP", "Totems", Items.TOTEM_OF_UNDYING, "textures/item/totem_of_undying.png", null, null, "textures/item/totem_of_undying.png"),
            tex("ender_pearl", "PvP", "Ender Pearls", Items.ENDER_PEARL, "textures/item/ender_pearl.png", null, null,
                    "textures/item/ender_pearl.png", "textures/item/ender_eye.png"),
            tex("golden_apple", "PvP", "Golden Apples", Items.GOLDEN_APPLE, "textures/item/golden_apple.png", null, null, "textures/item/golden_apple.png"),
            tex("xp_bottle", "PvP", "XP Bottles", Items.EXPERIENCE_BOTTLE, "textures/item/experience_bottle.png", null, null,
                    "textures/item/experience_bottle.png"),
            tex("shield", "PvP", "Shields", Items.SHIELD, "textures/entity/shield_base_nopattern.png", new int[]{1, 1, 12, 22}, null,
                    "textures/entity/shield_base_nopattern.png", "textures/entity/shield_base.png"),
            tex("bows", "PvP", "Bows", Items.BOW, "textures/item/bow.png", null, null,
                    "textures/item/bow.png", "textures/item/bow_pulling_0.png", "textures/item/bow_pulling_1.png", "textures/item/bow_pulling_2.png",
                    "textures/item/crossbow_standby.png", "textures/item/crossbow_pulling_0.png", "textures/item/crossbow_pulling_1.png",
                    "textures/item/crossbow_pulling_2.png", "textures/item/crossbow_arrow.png", "textures/item/crossbow_firework.png"),

            tex("obsidian", "Blocks", "Obsidian", Items.OBSIDIAN, "textures/block/obsidian.png", null, null,
                    "textures/block/obsidian.png", "textures/block/crying_obsidian.png"),
            tex("glowstone", "Blocks", "Glowstone", Items.GLOWSTONE, "textures/block/glowstone.png", null, null, "textures/block/glowstone.png"),
            tex("cobweb", "Blocks", "Cobwebs", Items.COBWEB, "textures/block/cobweb.png", null, null, "textures/block/cobweb.png"),
            tex("fire", "Blocks", "Fire", Items.FLINT_AND_STEEL, "textures/block/fire_0.png", null, null, "textures/block/fire_0.png", "textures/block/fire_1.png"),
            tex("bedrock", "Blocks", "Bedrock", Items.BEDROCK, "textures/block/bedrock.png", null, null, "textures/block/bedrock.png"),
            tex("ores", "Blocks", "Ores", Items.DIAMOND_ORE, "textures/block/diamond_ore.png", null, null,
                    "dir:textures/block/diamond_ore", "dir:textures/block/deepslate_diamond_ore", "textures/block/ancient_debris_side.png",
                    "textures/block/ancient_debris_top.png"),

            tex("netherite_armor", "Gear", "Netherite Armor", Items.NETHERITE_CHESTPLATE, "textures/item/netherite_chestplate.png", null, null,
                    "textures/item/netherite_helmet.png", "textures/item/netherite_chestplate.png", "textures/item/netherite_leggings.png",
                    "textures/item/netherite_boots.png", EQ + "humanoid/netherite.png", EQ + "humanoid_leggings/netherite.png"),
            tex("diamond_armor", "Gear", "Diamond Armor", Items.DIAMOND_CHESTPLATE, "textures/item/diamond_chestplate.png", null, null,
                    "textures/item/diamond_helmet.png", "textures/item/diamond_chestplate.png", "textures/item/diamond_leggings.png",
                    "textures/item/diamond_boots.png", EQ + "humanoid/diamond.png", EQ + "humanoid_leggings/diamond.png"),
            tex("elytra", "Gear", "Elytra", Items.ELYTRA, "textures/item/elytra.png", null, null,
                    "textures/item/elytra.png", "textures/item/broken_elytra.png", EQ + "wings/elytra.png"),
            tex("swords", "Gear", "Swords", Items.NETHERITE_SWORD, "textures/item/netherite_sword.png", null, null,
                    "textures/item/netherite_sword.png", "textures/item/diamond_sword.png", "textures/item/iron_sword.png"),
            tex("axes", "Gear", "Axes & Mace", Items.NETHERITE_AXE, "textures/item/netherite_axe.png", null, null,
                    "textures/item/netherite_axe.png", "textures/item/diamond_axe.png", "textures/item/mace.png"),
            // spears (1.21.11): an inventory texture and a separate held one for every material
            tex("spears", "Gear", "Spears", Items.NETHERITE_SPEAR, "textures/item/netherite_spear.png", null, null,
                    "textures/item/netherite_spear.png", "textures/item/netherite_spear_in_hand.png", "textures/item/diamond_spear.png", "textures/item/diamond_spear_in_hand.png", "textures/item/iron_spear.png", "textures/item/iron_spear_in_hand.png", "textures/item/golden_spear.png", "textures/item/golden_spear_in_hand.png", "textures/item/copper_spear.png", "textures/item/copper_spear_in_hand.png", "textures/item/stone_spear.png", "textures/item/stone_spear_in_hand.png", "textures/item/wooden_spear.png", "textures/item/wooden_spear_in_hand.png"),
            tex("pickaxes", "Gear", "Pickaxes", Items.NETHERITE_PICKAXE, "textures/item/netherite_pickaxe.png", null, null,
                    "textures/item/netherite_pickaxe.png", "textures/item/diamond_pickaxe.png", "textures/item/iron_pickaxe.png"),

            tex("crosshair", "HUD", "Crosshair", Items.TARGET, "textures/gui/sprites/hud/crosshair.png", null, "invert",
                    "textures/gui/sprites/hud/crosshair.png"),
            tex("gui", "Menus", "Inventory GUIs", Items.CHEST, C + "inventory.png", new int[]{0, 0, 176, 166}, "clear",
                    C + "inventory.png", C + "generic_54.png", C + "shulker_box.png", C + "crafting_table.png", C + "furnace.png",
                    C + "blast_furnace.png", C + "smoker.png", C + "anvil.png", C + "enchanting_table.png", C + "dispenser.png",
                    C + "hopper.png", C + "brewing_stand.png", C + "smithing.png", C + "grindstone.png", C + "stonecutter.png",
                    C + "beacon.png", C + "villager.png", C + "loom.png", C + "cartography_table.png", C + "horse.png",
                    C + "crafter.png", C + "nautilus.png", C + "gamemode_switcher.png", "textures/gui/recipe_book.png",
                    "dir:textures/gui/sprites/container/creative_inventory/tab_", "dir:textures/gui/sprites/recipe_book/tab",
                    C + "creative_inventory/tab_items.png",
                    C + "creative_inventory/tab_inventory.png", C + "creative_inventory/tab_item_search.png"),
            tex("buttons", "Menus", "Buttons & Sliders", Items.STONE_BUTTON, "textures/gui/sprites/widget/button.png", null, "clear",
                    "dir:textures/gui/sprites/widget/"),
            tex("tooltips", "Menus", "Tooltips", Items.PAPER, "textures/gui/sprites/tooltip/background.png", null, "clear",
                    "textures/gui/sprites/tooltip/background.png", "textures/gui/sprites/tooltip/frame.png"),
            tex("books", "Menus", "Books & Advancements", Items.WRITABLE_BOOK, "textures/gui/book.png", new int[]{20, 1, 146, 180}, "clear",
                    "textures/gui/book.png", "textures/gui/advancements/window.png", "dir:textures/gui/sprites/advancements/"),
            tex("toasts", "Menus", "Toasts", Items.BELL, "textures/gui/sprites/toast/advancement.png", null, "clear",
                    "dir:textures/gui/sprites/toast/"),
            tex("hearts", "HUD", "Hearts", Items.RED_DYE, HEART + "full.png", null, null,
                    HEART + "full.png", HEART + "half.png", HEART + "full_blinking.png", HEART + "half_blinking.png",
                    HEART + "hardcore_full.png", HEART + "hardcore_half.png", HEART + "hardcore_full_blinking.png", HEART + "hardcore_half_blinking.png"),
            tex("hunger", "HUD", "Hunger", Items.COOKED_BEEF, "textures/gui/sprites/hud/food_full.png", null, null,
                    "textures/gui/sprites/hud/food_full.png", "textures/gui/sprites/hud/food_half.png"),
            tex("hotbar", "HUD", "Hotbar", Items.ITEM_FRAME, "textures/gui/sprites/hud/hotbar.png", null, null,
                    "textures/gui/sprites/hud/hotbar.png", "textures/gui/sprites/hud/hotbar_selection.png",
                    "textures/gui/sprites/hud/hotbar_offhand_left.png", "textures/gui/sprites/hud/hotbar_offhand_right.png"),
            tex("xp_bar", "HUD", "XP Bar", Items.EXPERIENCE_BOTTLE, "textures/gui/sprites/hud/experience_bar_progress.png", null, null,
                    "textures/gui/sprites/hud/experience_bar_progress.png", "textures/gui/sprites/hud/experience_bar_background.png"),

            tex("glint", "Misc", "Enchant Glint", Items.ENCHANTED_BOOK, "textures/misc/enchanted_glint_item.png", new int[]{0, 0, 64, 64}, "glint",
                    "textures/misc/enchanted_glint_item.png", "textures/misc/enchanted_glint_armor.png"),
            tex("arrows", "Misc", "Arrows", Items.ARROW, "textures/item/arrow.png", null, null,
                    "textures/item/arrow.png", "textures/item/spectral_arrow.png", "textures/entity/projectiles/arrow.png"),
            tex("xp_orbs", "Misc", "XP Orbs", Items.EXPERIENCE_BOTTLE, "textures/entity/experience_orb.png", new int[]{0, 0, 16, 16}, null,
                    "textures/entity/experience_orb.png"),

            tex("crit", "Effects", "Crit Particles", Items.DIAMOND_SWORD, "textures/particle/critical_hit.png", null, null,
                    "textures/particle/critical_hit.png", "textures/particle/enchanted_hit.png", "textures/particle/damage.png"),
            tex("totem_particles", "Effects", "Totem Particles", Items.TOTEM_OF_UNDYING, "textures/particle/glitter_7.png", null, null,
                    "dir:textures/particle/glitter_"),
            tex("explosion", "Effects", "Explosions", Items.TNT, "textures/particle/explosion_4.png", null, null,
                    "dir:textures/particle/explosion_"),
            tex("sweep", "Effects", "Sweep Attack", Items.IRON_SWORD, "textures/particle/sweep_3.png", null, null,
                    "dir:textures/particle/sweep_"),
            tex("potion_icons", "Effects", "Effect Icons", Items.POTION, "textures/mob_effect/strength.png", null, null,
                    "dir:textures/mob_effect/"),

            sound("hit_sounds", "Hit Sounds", Items.IRON_SWORD, "entity.player.attack.strong", "entity.player.attack.weak",
                    "entity.player.attack.crit", "entity.player.attack.sweep", "entity.player.attack.knockback", "entity.player.attack.nodamage"),
            sound("hurt_sound", "Hurt Sound", Items.RED_DYE, "entity.player.hurt"),
            sound("explosion_sound", "Explosions", Items.TNT, "entity.generic.explode"),
            sound("totem_sound", "Totem Pop", Items.TOTEM_OF_UNDYING, "item.totem.use"),
            sound("bow_sound", "Bow Hit", Items.BOW, "entity.arrow.hit_player", "entity.arrow.hit"),
    };

    public static List<Category> inGroup(String group) {
        List<Category> out = new ArrayList<>();
        for (Category c : CATEGORIES) if (c.group.equals(group)) out.add(c);
        return out;
    }

    /** The real textures of a category ("dir:" entries expanded through the resource manager). */
    public static List<Identifier> ids(Category cat) {
        List<Identifier> out = new ArrayList<>();
        for (String p : cat.paths) {
            if (!p.startsWith("dir:")) { out.add(Identifier.ofVanilla(p)); continue; }
            String prefix = p.substring(4);
            String dir = prefix.substring(0, prefix.lastIndexOf('/'));
            try {
                for (Identifier id : MinecraftClient.getInstance().getResourceManager()
                        .findResources(dir, i -> i.getPath().startsWith(prefix) && i.getPath().endsWith(".png")).keySet()) {
                    if (id.getNamespace().equals("minecraft") && !out.contains(id)) out.add(id);
                }
            } catch (Exception ignored) { }
        }
        return out;
    }

    // ------------------------------------------------------------------ sounds
    /** A replacement sound: the name on the card and the vanilla sound file it plays (null = silent). */
    public record SoundOption(String name, String file) { }

    public static final SoundOption[] SOUNDS = {
            new SoundOption("Orb", "random/orb"), new SoundOption("Ding", "random/successful_hit"),
            new SoundOption("Pling", "note/pling"), new SoundOption("Bell", "note/bell"), new SoundOption("Hat", "note/hat"),
            new SoundOption("Pop", "random/pop"), new SoundOption("Click", "random/click"), new SoundOption("Level Up", "random/levelup"),
            new SoundOption("Muted", null),
    };

    public static SoundOption soundOption(String name) {
        for (SoundOption s : SOUNDS) if (s.name.equals(name)) return s;
        return null;
    }

    /** Writes assets/minecraft/sounds.json for every sound category that isn't Vanilla (or deletes it). */
    private static void writeSounds(Map<String, Look> looks) throws java.io.IOException {
        JsonObject root = new JsonObject();
        for (Category cat : CATEGORIES) {
            if (!cat.isSound()) continue;
            SoundOption opt = soundOption(looks.getOrDefault(cat.id, Look.VANILLA).look());
            if (opt == null) continue;
            for (String ev : cat.events) {
                JsonObject e = new JsonObject();
                e.addProperty("replace", true);
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                if (opt.file != null) arr.add(opt.file);
                e.add("sounds", arr);
                root.add(ev, e);
            }
        }
        Path f = CustomPack.fileFor(Identifier.ofVanilla("sounds.json"));
        if (root.size() == 0) { Files.deleteIfExists(f); return; }
        CustomPack.ensureMeta();
        Files.createDirectories(f.getParent());
        Files.writeString(f, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ looks (what you picked)
    /**
     * look: "Vanilla", a style name, "Custom", or "pack:&lt;profile id&gt;"; color is for Custom; glintOff hides the glint;
     * clear makes container GUIs see-through at {@code opacity} percent (5..100).
     */
    public record Look(String look, int color, boolean glintOff, boolean clear, int opacity) {
        public static final int DEFAULT_OPACITY = 60;
        public static final Look VANILLA = new Look("Vanilla", 0xE0486A, false);
        public Look(String look, int color, boolean glintOff) { this(look, color, glintOff, false, DEFAULT_OPACITY); }
        public Look withLook(String l) { return new Look(l, color, glintOff, clear, opacity); }
        public Look withColor(int c) { return new Look("Custom", c & 0xFFFFFF, glintOff, clear, opacity); }
        public Look withGlintOff(boolean off) { return new Look(look, color, off, clear, opacity); }
        public Look withClear(boolean on) { return new Look(look, color, glintOff, on, opacity); }
        public Look withOpacity(int pct) { return new Look(look, color, glintOff, clear, Math.max(5, Math.min(100, pct))); }
        /** Anything different from plain vanilla? */
        public boolean changed() { return !look.equals("Vanilla") || glintOff || clear; }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Look> applied;

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("tempest-packs.json"); }

    /** What's currently written into the pack. */
    public static Map<String, Look> applied() {
        if (applied == null) {
            applied = new LinkedHashMap<>();
            try {
                if (Files.exists(file())) {
                    JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
                    for (var e : o.entrySet()) {
                        JsonObject v = e.getValue().getAsJsonObject();
                        applied.put(e.getKey(), new Look(v.has("look") ? v.get("look").getAsString() : "Vanilla",
                                v.has("color") ? v.get("color").getAsInt() : Look.VANILLA.color,
                                v.has("glintOff") && v.get("glintOff").getAsBoolean(),
                                v.has("clear") && v.get("clear").getAsBoolean(),
                                v.has("opacity") ? v.get("opacity").getAsInt() : Look.DEFAULT_OPACITY));
                    }
                }
            } catch (Exception ignored) { }
        }
        return applied;
    }

    /** Same result in game (the colour only matters for Custom). */
    public static boolean same(Look a, Look b) {
        return a.look().equals(b.look()) && a.glintOff() == b.glintOff() && (!a.look().equals("Custom") || a.color() == b.color())
                && a.clear() == b.clear() && (!a.clear() || a.opacity() == b.opacity());
    }

    public static Look appliedLook(String cat) { return applied().getOrDefault(cat, Look.VANILLA); }

    private static void saveApplied() {
        JsonObject o = new JsonObject();
        for (var e : applied().entrySet()) {
            JsonObject v = new JsonObject();
            v.addProperty("look", e.getValue().look());
            v.addProperty("color", e.getValue().color());
            if (e.getValue().glintOff()) v.addProperty("glintOff", true);
            if (e.getValue().clear()) { v.addProperty("clear", true); v.addProperty("opacity", e.getValue().opacity()); }
            o.add(e.getKey(), v);
        }
        try { Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ images
    /** A texture as ARGB pixels. */
    public record Img(int w, int h, int[] px) {
        public Img copy() { return new Img(w, h, px.clone()); }

        /** First square frame of an animation strip, or a region. */
        public Img crop(int[] region) { return crop(region, 0); }

        /** refW: width of the texture the region was measured on (HD packs are scaled to match); 0 = same size. */
        public Img crop(int[] region, int refW) {
            int x = 0, y = 0, cw = w, ch = h;
            if (region != null) {
                float k = refW > 0 ? w / (float) refW : 1f;
                x = Math.round(region[0] * k); y = Math.round(region[1] * k);
                cw = Math.min(Math.round(region[2] * k), w - x); ch = Math.min(Math.round(region[3] * k), h - y);
            }
            else if (h > w && h % w == 0) ch = w;
            if (cw <= 0 || ch <= 0) return this;
            int[] out = new int[cw * ch];
            for (int yy = 0; yy < ch; yy++) System.arraycopy(px, (y + yy) * w + x, out, yy * cw, cw);
            return new Img(cw, ch, out);
        }
    }

    static Img fromNative(NativeImage n) {
        int w = n.getWidth(), h = n.getHeight();
        int[] p = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) p[y * w + x] = n.getColorArgb(x, y);
        return new Img(w, h, p);
    }

    /** The texture without Tempest's own edits (your other packs, or vanilla). */
    public static Img base(Identifier id) {
        NativeImage n = CustomPack.read(id, true);
        if (n == null) return null;
        try (n) { return fromNative(n); }
    }

    /** The texture from one installed pack (null if that pack doesn't change it). */
    public static Img fromPack(String profileId, Identifier id) {
        MinecraftClient c = MinecraftClient.getInstance();
        ResourcePackProfile p = c.getResourcePackManager().getProfile(profileId);
        if (p == null) return null;
        try (ResourcePack pack = p.createResourcePack()) {
            var sup = pack.open(ResourceType.CLIENT_RESOURCES, id);
            if (sup == null) return null;
            try (InputStream in = sup.get(); NativeImage n = NativeImage.read(in)) { return fromNative(n); }
        } catch (Exception e) {
            return null;
        }
    }

    /** Your own packs (folder / zip in resourcepacks) that change this category's main texture. */
    public static List<ResourcePackProfile> packsWith(Category cat) {
        List<ResourcePackProfile> out = new ArrayList<>();
        MinecraftClient c = MinecraftClient.getInstance();
        for (ResourcePackProfile p : c.getResourcePackManager().getProfiles()) {
            if (!p.getId().startsWith("file/") || p.getId().equals(CustomPack.PACK_ID)) continue;
            try (ResourcePack pack = p.createResourcePack()) {
                if (pack.open(ResourceType.CLIENT_RESOURCES, cat.previewId()) != null) out.add(p);
            } catch (Exception ignored) { }
        }
        return out;
    }

    /**
     * One texture of a category with a look applied (null = leave it to vanilla). Container GUIs set to Transparent
     * get every pixel's opacity scaled down afterwards (on top of whatever look they have).
     */
    public static Img render(Category cat, Identifier id, Look look) {
        Img img = renderLook(cat, id, look);
        if (!look.clear() || !"clear".equals(cat.toggle())) return img;
        if (img == null) img = base(id);
        return img == null ? null : fade(img, look.opacity());
    }

    /** Multiplies every pixel's alpha by pct / 100. */
    public static Img fade(Img img, int pct) {
        float k = Math.max(0, Math.min(100, pct)) / 100f;
        int[] p = img.px.clone();
        for (int i = 0; i < p.length; i++) {
            int a = p[i] >>> 24;
            if (a == 0) continue;
            p[i] = (Math.round(a * k) << 24) | (p[i] & 0xFFFFFF);
        }
        return new Img(img.w, img.h, p);
    }

    private static Img renderLook(Category cat, Identifier id, Look look) {
        if (cat.toggle != null && cat.toggle.equals("glint") && look.glintOff()) {
            Img b = base(id);
            return b == null ? null : new Img(b.w, b.h, new int[b.w * b.h]);   // fully transparent: no glint
        }
        String l = look.look();
        if (l.equals("Vanilla")) return null;
        if (l.startsWith("pack:")) return solidify(cat, id, fromPack(l.substring(5), id));
        Img b = base(id);
        if (b == null) return null;
        Style s = l.equals("Custom") ? custom(look.color()) : style(l);
        if (s == null) return null;
        int[] p = b.px.clone();
        for (int i = 0; i < p.length; i++) p[i] = recolor(p[i], s);
        return new Img(b.w, b.h, p);
    }

    /**
     * Inventory / container screens must stay solid. Some PvP packs ship "clear" GUIs (see-through panels) that only
     * look right with the rest of that pack, so taking just that texture left the inventory see-through. Wherever the
     * normal texture is solid, the pack's texture is laid over it, so the panel is never see-through (works for HD
     * packs too: the normal texture is sampled at the matching spot).
     */
    public static Img solidify(Category cat, Identifier id, Img img) {
        if (img == null || cat == null || !cat.id().equals("gui")) return img;
        Img b = base(id);
        if (b == null) return img;
        int[] out = img.px.clone();
        for (int y = 0; y < img.h; y++) {
            int by = Math.min(b.h - 1, y * b.h / img.h);
            for (int x = 0; x < img.w; x++) {
                int bx = Math.min(b.w - 1, x * b.w / img.w);
                int under = b.px[by * b.w + bx];
                if ((under >>> 24) < 200) continue;                 // outside the panel: leave as the pack has it
                int over = out[y * img.w + x];
                int a = over >>> 24;
                if (a == 255) continue;
                float t = a / 255f;
                int rgb = lerp(under & 0xFFFFFF, over & 0xFFFFFF, t);
                out[y * img.w + x] = 0xFF000000 | rgb;
            }
        }
        return new Img(img.w, img.h, out);
    }

    // ------------------------------------------------------------------ apply
    /**
     * Writes every category whose look changed, then reloads once.
     * @return a short message for the screen
     */
    public static String apply(Map<String, Look> pending) {
        int written = 0, cleared = 0;
        boolean soundsChanged = false;
        for (Category cat : CATEGORIES) {
            Look want = pending.getOrDefault(cat.id, Look.VANILLA);
            if (same(want, appliedLook(cat.id))) continue;
            if (cat.isSound()) {
                if (want.look().equals("Vanilla")) applied().remove(cat.id); else applied().put(cat.id, want);
                soundsChanged = true;
                written++;
                continue;
            }
            for (Identifier id : ids(cat)) {
                String path = id.getPath();
                try {
                    CustomPack.remove(id);           // so "base" reads your other packs / vanilla, not our old version
                    Img img = render(cat, id, want);
                    if (img != null) { CustomPack.save(id, img.px, img.w, img.h); written++; }
                    else cleared++;
                } catch (Exception e) {
                    return "Couldn't write " + path + ": " + e.getMessage();
                }
            }
            if (!want.changed()) applied().remove(cat.id); else applied().put(cat.id, want);
        }
        if (soundsChanged) {
            try { writeSounds(applied()); } catch (Exception e) { return "Couldn't write sounds: " + e.getMessage(); }
        }
        saveApplied();
        CustomPack.apply();
        return written > 0 ? "Applied - reloading textures…" : cleared > 0 ? "Back to vanilla - reloading…" : "Everything applied";
    }

    /** Removes everything Tempest wrote (styles and paintings) and forgets the looks. */
    public static void deleteAll() throws java.io.IOException {
        Path d = CustomPack.dir();
        if (Files.exists(d)) {
            try (var s = Files.walk(d)) {
                for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
        applied().clear();
        saveApplied();
        CustomPack.apply();
    }

    // ------------------------------------------------------------------ colours from a screenshot
    /** The main colour of a picture: the most common strong hue, averaged. */
    public static int dominantColor(java.awt.image.BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int step = Math.max(1, Math.max(w, h) / 96);
        double[] weight = new double[36];
        double[][] sum = new double[36][3];
        double tr = 0, tg = 0, tb = 0, tn = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int c = img.getRGB(x, y);
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                float[] hsv = java.awt.Color.RGBtoHSB(r, g, b, null);
                tr += r; tg += g; tb += b; tn++;
                if (hsv[2] < 0.15f || hsv[1] < 0.15f) continue;
                int bin = Math.min(35, (int) (hsv[0] * 36));
                double wgt = hsv[1] * hsv[2];
                weight[bin] += wgt;
                sum[bin][0] += r * wgt; sum[bin][1] += g * wgt; sum[bin][2] += b * wgt;
            }
        }
        int best = 0;
        for (int i = 1; i < 36; i++) if (weight[i] > weight[best]) best = i;
        if (weight[best] < 1e-3) {
            if (tn == 0) return 0x808080;
            return ((int) (tr / tn) << 16) | ((int) (tg / tn) << 8) | (int) (tb / tn);
        }
        int r = (int) (sum[best][0] / weight[best]), g = (int) (sum[best][1] / weight[best]), b = (int) (sum[best][2] / weight[best]);
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    public static int dominantColor(Path file) throws java.io.IOException {
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file.toFile());
        if (img == null) throw new java.io.IOException("not a picture");
        return dominantColor(img);
    }

    // ------------------------------------------------------------------ small options file
    private static Path optionsFile() { return FabricLoader.getInstance().getConfigDir().resolve("tempest-packs-options.json"); }

    private static JsonObject options() {
        try {
            if (Files.exists(optionsFile())) return JsonParser.parseString(Files.readString(optionsFile())).getAsJsonObject();
        } catch (Exception ignored) { }
        return new JsonObject();
    }

    private static void setOption(String k, boolean v) {
        JsonObject o = options();
        o.addProperty(k, v);
        try { Files.writeString(optionsFile(), GSON.toJson(o)); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ crosshair blending
    private static Boolean invertCrosshair;

    /** Vanilla draws the crosshair inverted against the world; with a coloured crosshair you may want it plain. */
    public static boolean invertCrosshair() {
        if (invertCrosshair == null) {
            JsonObject o = options();
            invertCrosshair = !o.has("invertCrosshair") || o.get("invertCrosshair").getAsBoolean();
        }
        return invertCrosshair;
    }

    public static void setInvertCrosshair(boolean v) {
        invertCrosshair = v;
        setOption("invertCrosshair", v);
    }

    // ------------------------------------------------------------------ one-time repair
    /**
     * Inventory looks taken from a pack used to keep that pack's see-through panels. Rebuilds an already applied one
     * once (solid now, see solidify). Returns true if it rebuilt something (the caller says so).
     */
    public static boolean repairGuiOnce() {
        JsonObject o = options();
        if (o.has("guiSolidV1")) return false;
        setOption("guiSolidV1", true);
        Look l = appliedLook("gui");
        if (!l.look().startsWith("pack:")) return false;
        Category cat = null;
        for (Category c : CATEGORIES) if (c.id().equals("gui")) cat = c;
        if (cat == null) return false;
        try {
            for (Identifier id : ids(cat)) {
                CustomPack.remove(id);
                Img img = render(cat, id, l);
                if (img != null) CustomPack.save(id, img.px, img.w, img.h);
            }
            CustomPack.apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private PackLooks() { }
}
