package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.ModuleManager;
import dev.tempest.util.TierLists;
import dev.tempest.util.TierLists.Rank;
import dev.tempest.util.TierLists.Source;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * Tier Tagger: a player's PvP tier in front of their name (nametag and tab list), like the TierTagger mod - from
 * MCTiers, NovaTiers, or both at once. Pick a game mode per list, or "Highest" for their best tier on that list.
 * Data comes from util/TierLists (cached, looked up in the background).
 */
public class TierTaggerModule extends Module {
    private static final String[] MC_MODES = {"Highest", "Sword", "Axe", "Mace", "Pot", "NethPot", "SMP", "UHC", "Vanilla", "Spear"};
    private static final String[] NOVA_MODES = {"Highest", "Spear Mace", "Modern SMP", "SMP", "Vanilla", "Spleef", "Axe", "Pufferfish",
            "UHC", "Diamond OP", "Diamond Cart", "Elytra", "Elytra Spear"};

    private final ModeSetting list = add(new ModeSetting("list", "Tier List", "MCTiers", "MCTiers", "NovaTiers", "Both"));
    private final ModeSetting mcMode = add(new ModeSetting("mc_mode", "MCTiers Game Mode", "Highest", MC_MODES));
    private final ModeSetting novaMode = add(new ModeSetting("nova_mode", "NovaTiers Game Mode", "Highest", NOVA_MODES));
    private final BoolSetting fallback = add(new BoolSetting("fallback", "Show Highest If Unranked In Mode", true));
    private final BoolSetting showMode = add(new BoolSetting("show_mode", "Show Game Mode Name", false));
    private final BoolSetting peak = add(new BoolSetting("peak", "Show Peak Tier", false));
    private final BoolSetting nametags = add(new BoolSetting("nametags", "On Nametags", true));
    private final BoolSetting tab = add(new BoolSetting("tab", "In Tab List", true));
    private final KeyBinding cycleKey = bindKey("tier_cycle", "Cycle Game Mode", -1);

    public TierTaggerModule() {
        super("tier_tagger", "Tier Tagger", "Shows players' PvP tiers (MCTiers and/or NovaTiers) in front of their names.", Category.PVP, false);
        keyHint = "With Both, the NovaTiers tier is the one after the purple ✦.";
    }

    @Override
    public void onTick(MinecraftClient c) {
        while (cycleKey.wasPressed()) {
            ModeSetting m = list.is("NovaTiers") ? novaMode : mcMode;
            m.cycle(1);
            dev.tempest.util.Chat.info((list.is("NovaTiers") ? "NovaTiers" : "MCTiers") + " mode: " + m.get());
        }
    }

    @Override
    public void onDisable() { TierLists.clear(); }

    private static TierTaggerModule on() {
        TierTaggerModule m = ModuleManager.get(TierTaggerModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    /** Tier prefix for a nametag ("HT3 | "), or null. */
    public static Text nametagPrefix(UUID id) {
        TierTaggerModule m = on();
        return m == null || !m.nametags.get() ? null : m.prefix(id);
    }

    /** Tier prefix for the tab list, or null. */
    public static Text tabPrefix(UUID id) {
        TierTaggerModule m = on();
        return m == null || !m.tab.get() ? null : m.prefix(id);
    }

    private Text prefix(UUID id) {
        boolean mc = !list.is("NovaTiers"), nova = !list.is("MCTiers");
        Text a = mc ? tag(Source.MCTIERS, id, mcModeId(), false) : null;
        Text b = nova ? tag(Source.NOVA, id, novaMode.get(), list.is("Both")) : null;
        if (a == null && b == null) return null;
        MutableText out = Text.empty();
        if (a != null) out.append(a);
        if (a != null && b != null) out.append(Text.literal(" "));
        if (b != null) out.append(b);
        return out.append(Text.literal(" | ").styled(s -> s.withColor(0x8A8F9E)));
    }

    /** MCTiers mode ids differ from the names shown ("NethPot" is "nethop"). */
    private String mcModeId() {
        String m = mcMode.get();
        return m.equals("NethPot") ? "nethop" : m;
    }

    private Text tag(Source src, UUID id, String mode, boolean star) {
        TierLists.Profile p = TierLists.get(src, id);
        if (p == null || p.ranks().isEmpty()) return null;
        boolean highest = mode.equals("Highest");
        Rank r = highest ? p.best() : p.in(mode);
        if (r == null && fallback.get()) { r = p.best(); highest = true; }
        if (r == null) return null;
        Rank rank = r;
        MutableText out = Text.empty();
        if (star) out.append(Text.literal("✦").styled(s -> s.withColor(0xB06CFF)));
        int col = rank.retired() ? 0xA2D6FF : color(rank.level(), rank.high());
        out.append(Text.literal((rank.retired() ? "R" : "") + rank.label()).styled(s -> s.withColor(col).withBold(rank.level() == 1)));
        if (peak.get() && rank.peakLevel() > 0 && rank.peakScore() < rank.score()) {
            out.append(Text.literal(" (" + rank.peakLabel() + ")").styled(s -> s.withColor(0x8A8F9E)));
        }
        if (showMode.get() || (highest && !mode.equals("Highest"))) {
            out.append(Text.literal(" " + pretty(rank.mode())).styled(s -> s.withColor(0xB8BCC8)));
        }
        return out;
    }

    private static String pretty(String mode) {
        return switch (mode.toLowerCase(java.util.Locale.ROOT)) {
            case "nethop", "neth_pot" -> "NethPot";
            case "smp" -> "SMP";
            case "uhc" -> "UHC";
            default -> {
                StringBuilder sb = new StringBuilder();
                for (String w : mode.replace('_', ' ').split(" ")) {
                    if (w.isEmpty()) continue;
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(w.length() <= 2 ? w.toUpperCase(java.util.Locale.ROOT) : Character.toUpperCase(w.charAt(0)) + w.substring(1));
                }
                yield sb.toString();
            }
        };
    }

    /** Same palette as TierTagger: gold for tier 1, silver 2, bronze 3, muted purple 4-5; HT brighter than LT. */
    private static int color(int level, boolean high) {
        return switch (level) {
            case 1 -> high ? 0xE8BA3A : 0xD5B355;
            case 2 -> high ? 0xC4D3E7 : 0xA0A7B2;
            case 3 -> high ? 0xF89F5A : 0xC67B42;
            case 4 -> high ? 0x81749A : 0x655B79;
            default -> high ? 0x8F82A8 : 0x655B79;
        };
    }
}
