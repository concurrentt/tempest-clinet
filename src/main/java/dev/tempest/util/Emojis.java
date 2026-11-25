package dev.tempest.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Discord-style emoji in chat: ":sob:" (and a real 😭) shows the emoji picture. The pictures are Twemoji
 * (CC-BY 4.0) packed into a bitmap font, tempest:emoji, one private-use character per emoji
 * (made by tools/make_emoji_font.py; names from GitHub's gemoji plus Discord's own names).
 */
public final class Emojis {
    public static final StyleSpriteSource FONT = new StyleSpriteSource.Font(Identifier.of("tempest", "emoji"));
    private static final Map<String, String> NAMES = new HashMap<>();   // "sob" -> glyph
    private static final Map<String, String> RAW = new HashMap<>();     // "😭" (no FE0F) -> glyph
    private static int maxRaw = 1;
    private static boolean loaded;

    private Emojis() { }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = Emojis.class.getResourceAsStream("/assets/tempest/emoji/emoji.tsv")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length < 3) continue;
                String glyph = new String(Character.toChars(Integer.parseInt(p[0], 16)));
                String raw = p[1].replace("️", "");
                if (!raw.isEmpty()) { RAW.putIfAbsent(raw, glyph); maxRaw = Math.max(maxRaw, raw.length()); }
                for (String n : p[2].split(",")) if (!n.isEmpty()) NAMES.putIfAbsent(n.toLowerCase(Locale.ROOT), glyph);
            }
        } catch (Exception e) {
            System.out.println("[Tempest] Couldn't load emoji list: " + e);
        }
    }

    /** The emoji for a name like "sob", or null. */
    public static String glyph(String name) {
        load();
        return NAMES.get(name.toLowerCase(Locale.ROOT));
    }

    /** Returns the message with :names: and emoji characters drawn as emoji pictures (the same Text if none). */
    public static Text apply(Text message) {
        if (message == null) return null;
        String all = message.getString();
        if (!worthLooking(all)) return message;
        load();
        MutableText out = Text.empty();
        boolean[] changed = {false};
        message.visit((style, s) -> {
            append(out, style, s, changed);
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? out : message;
    }

    private static boolean worthLooking(String s) {
        if (s.indexOf(':') >= 0) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x203C && (c < 0xE000 || c > 0xF8FF)) return true;
        }
        return false;
    }

    private static void append(MutableText out, Style style, String s, boolean[] changed) {
        StringBuilder plain = new StringBuilder();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == ':') {
                int end = s.indexOf(':', i + 1);
                if (end > i + 1 && end - i <= 40) {
                    String g = NAMES.get(s.substring(i + 1, end).toLowerCase(Locale.ROOT));
                    if (g != null) {
                        flush(out, style, plain);
                        out.append(Text.literal(g).setStyle(emojiStyle(style)));
                        changed[0] = true;
                        i = end + 1;
                        continue;
                    }
                }
            } else if (c >= 0x203C && (c < 0xE000 || c > 0xF8FF)) {
                // longest emoji sequence starting here (FE0F variation selectors ignored)
                int best = 0;
                String bestGlyph = null;
                StringBuilder key = new StringBuilder();
                for (int j = i; j < n && j - i < maxRaw + 4; j++) {
                    char d = s.charAt(j);
                    if (d != '️') key.append(d);
                    String g = RAW.get(key.toString());
                    if (g != null) { best = j - i + 1; bestGlyph = g; }
                }
                if (bestGlyph != null) {
                    // swallow a trailing FE0F too
                    int stop = i + best;
                    if (stop < n && s.charAt(stop) == '️') stop++;
                    flush(out, style, plain);
                    out.append(Text.literal(bestGlyph).setStyle(emojiStyle(style)));
                    changed[0] = true;
                    i = stop;
                    continue;
                }
            }
            plain.append(c);
            i++;
        }
        flush(out, style, plain);
    }

    private static void flush(MutableText out, Style style, StringBuilder plain) {
        if (plain.length() == 0) return;
        out.append(Text.literal(plain.toString()).setStyle(style));
        plain.setLength(0);
    }

    /** Same hover / click as the text around it, but full colour, no shadow and no bold / italics. */
    private static Style emojiStyle(Style s) {
        return s.withFont(FONT).withColor(0xFFFFFF).withShadowColor(0).withBold(false).withItalic(false)
                .withUnderline(false).withStrikethrough(false).withObfuscated(false);
    }
}
