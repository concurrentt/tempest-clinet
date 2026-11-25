package dev.tempest.gui;

import dev.tempest.module.Category;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.Map;

public final class ModuleIcons {
    private static final Map<String, Item> MODULES = new HashMap<>();
    private static final Map<Item, ItemStack> CACHE = new HashMap<>();

    static {
        MODULES.put("fps", Items.CLOCK);
        MODULES.put("cps", Items.STONE_BUTTON);
        MODULES.put("keystrokes", Items.OAK_PRESSURE_PLATE);
        MODULES.put("reach", Items.STICK);
        MODULES.put("combo", Items.BLAZE_POWDER);
        MODULES.put("target_health", Items.GOLDEN_APPLE);
        MODULES.put("potions", Items.POTION);
        MODULES.put("item_counter", Items.CHEST);
        MODULES.put("appleskin", Items.APPLE);
        MODULES.put("armor_hud", Items.DIAMOND_CHESTPLATE);
        MODULES.put("cooldowns", Items.ENDER_PEARL);
        MODULES.put("coordinates", Items.COMPASS);
        MODULES.put("ping", Items.REPEATER);
        MODULES.put("inventory_hud", Items.BARREL);
        MODULES.put("totem_pops", Items.TOTEM_OF_UNDYING);
        MODULES.put("tier_tagger", Items.NETHERITE_SWORD);
        MODULES.put("discord", Items.NOTE_BLOCK);
        MODULES.put("auto_gg", Items.PAPER);
        MODULES.put("chat_macros", Items.WRITABLE_BOOK);
        MODULES.put("zoom", Items.SPYGLASS);
        MODULES.put("fullbright", Items.GLOWSTONE);
        MODULES.put("freelook", Items.ENDER_EYE);
        MODULES.put("cinematic", Items.PAINTING);
        MODULES.put("waypoints", Items.RECOVERY_COMPASS);
        MODULES.put("view_model", Items.IRON_SWORD);
        MODULES.put("low_fire", Items.FLINT_AND_STEEL);
        MODULES.put("particle_filter", Items.FIREWORK_STAR);
        MODULES.put("custom_fov", Items.SPYGLASS);
        MODULES.put("totem_tweaks", Items.TOTEM_OF_UNDYING);
        MODULES.put("environment", Items.WATER_BUCKET);
        MODULES.put("anti_leak", Items.BARRIER);
        MODULES.put("fly_speed", Items.FEATHER);
        MODULES.put("fps_saver", Items.REDSTONE);
        MODULES.put("combat_timer", Items.CLOCK);
        MODULES.put("saturation", Items.RED_DYE);
        MODULES.put("armor_alert", Items.IRON_CHESTPLATE);
        MODULES.put("server_setups", Items.WRITABLE_BOOK);
        MODULES.put("light_levels", Items.TORCH);
        MODULES.put("mouse_tweaks", Items.HOPPER);
        MODULES.put("shulker_preview", Items.SHULKER_BOX);
        MODULES.put("toggle_sprint", Items.LEATHER_BOOTS);
        MODULES.put("death_location", Items.SKELETON_SKULL);
        MODULES.put("reconnect", Items.LEAD);
        MODULES.put("armor_swap", Items.ELYTRA);
        MODULES.put("tooltip_info", Items.NAME_TAG);
        MODULES.put("screenshot_tools", Items.ITEM_FRAME);
        MODULES.put("streamer_mode", Items.NOTE_BLOCK);
        MODULES.put("chat_mentions", Items.BELL);
        MODULES.put("message_stacking", Items.BOOK);
        MODULES.put("session", Items.CLOCK);
        MODULES.put("info_bar", Items.NAME_TAG);
        MODULES.put("perf_graph", Items.REDSTONE);
        MODULES.put("alerts", Items.BELL);
        MODULES.put("hitboxes", Items.IRON_BARS);
        MODULES.put("friends", Items.PLAYER_HEAD);
        MODULES.put("item_physics", Items.DROPPER);
        MODULES.put("time_changer", Items.DAYLIGHT_DETECTOR);
        MODULES.put("quick_servers", Items.NETHER_STAR);
        MODULES.put("chat_upgrades", Items.WRITABLE_BOOK);
        MODULES.put("client", Items.COMPARATOR);
        MODULES.put("block_outline", Items.GLASS);
        MODULES.put("capes", Items.WHITE_BANNER);
        MODULES.put("client_crystals", Items.END_CRYSTAL);
        MODULES.put("clumps", Items.EXPERIENCE_BOTTLE);
        MODULES.put("fast_screens", Items.SUGAR);
        MODULES.put("middle_click_friend", Items.LIME_DYE);
        MODULES.put("motion_blur", Items.COBWEB);
        MODULES.put("scoreboard", Items.OAK_SIGN);
        MODULES.put("shield_fixes", Items.SHIELD);
        MODULES.put("shield_status", Items.SHIELD);
        MODULES.put("smooth_hotbar", Items.STICKY_PISTON);
        MODULES.put("who_am_i", Items.ZOMBIE_HEAD);
        MODULES.put("xray", Items.DIAMOND_ORE);
        MODULES.put("custom_sky", Items.BLACK_STAINED_GLASS);
        MODULES.put("fireflies", Items.GLOW_BERRIES);
        MODULES.put("cinematic_grade", Items.TINTED_GLASS);
    }

    private ModuleIcons() { }

    public static ItemStack stack(Item i) { return CACHE.computeIfAbsent(i, k -> new ItemStack(k)); }

    public static ItemStack forModule(String id) { return stack(MODULES.getOrDefault(id, Items.NETHER_STAR)); }

    public static ItemStack forCategory(Category c) {
        if (c == null) return stack(Items.NETHER_STAR);
        return stack(switch (c) {
            case HUD -> Items.COMPASS;
            case PVP -> Items.DIAMOND_SWORD;
            case RENDER -> Items.ENDER_EYE;
            case VISUALS -> Items.AMETHYST_SHARD;
            case QOL -> Items.EMERALD;
            case UTILITY -> Items.CRAFTING_TABLE;
            case CHAT -> Items.WRITABLE_BOOK;
            case CLIENT -> Items.COMPARATOR;
        });
    }
}
