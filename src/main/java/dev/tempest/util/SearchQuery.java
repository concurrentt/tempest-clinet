package dev.tempest.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Small search language used by Tempest's searchable screens (mod menu, key binds), in the spirit of the
 * Searchables library: free words plus {@code filter:value} tokens, with auto-complete.
 *
 * <pre>
 *   "sprint"                 name contains "sprint"
 *   "category:movement"      only that category
 *   "key:r is:conflict"      bound to R and conflicting
 *   "-hud"                   exclude anything matching "hud"
 * </pre>
 * Every word must match (AND). Values may be partial ("cat:mov").
 */
public final class SearchQuery {
    public record Token(String key, String value, boolean negate) { }

    private final List<Token> tokens = new ArrayList<>();
    private final Map<String, Supplier<List<String>>> filters;   // filter name -> possible values (for completion)

    private SearchQuery(String text, Map<String, Supplier<List<String>>> filters) {
        this.filters = filters;
        for (String raw : text.trim().split("\\s+")) {
            if (raw.isEmpty()) continue;
            boolean neg = raw.startsWith("-") && raw.length() > 1;
            String w = neg ? raw.substring(1) : raw;
            int c = w.indexOf(':');
            if (c > 0) tokens.add(new Token(resolve(w.substring(0, c).toLowerCase(Locale.ROOT)), w.substring(c + 1).toLowerCase(Locale.ROOT), neg));
            else tokens.add(new Token(null, w.toLowerCase(Locale.ROOT), neg));
        }
    }

    public static SearchQuery parse(String text, Map<String, Supplier<List<String>>> filters) {
        return new SearchQuery(text == null ? "" : text, filters);
    }

    /** "cat" -> "category" when it is the only filter starting with it. */
    private String resolve(String k) {
        if (filters.containsKey(k)) return k;
        String hit = null;
        for (String f : filters.keySet()) {
            if (f.startsWith(k)) {
                if (hit != null) return k;
                hit = f;
            }
        }
        return hit != null ? hit : k;
    }

    public boolean isEmpty() { return tokens.isEmpty(); }

    public List<Token> tokens() { return tokens; }

    /**
     * Matches an item. {@code text} is what free words are matched against; {@code values} gives the item's
     * value(s) for each filter (several values joined by '\n').
     */
    public boolean matches(String text, Map<String, String> values) {
        String t = text.toLowerCase(Locale.ROOT);
        for (Token tok : tokens) {
            boolean hit;
            if (tok.key() == null) hit = t.contains(tok.value());
            else {
                String v = values.get(tok.key());
                if (v == null) hit = false;
                else {
                    hit = false;
                    for (String part : v.toLowerCase(Locale.ROOT).split("\n")) {
                        if (part.startsWith(tok.value()) || part.replace(' ', '_').startsWith(tok.value())
                                || part.replace(" ", "").startsWith(tok.value()) || part.contains(" " + tok.value())) {
                            hit = true;
                            break;
                        }
                    }
                }
            }
            if (hit == tok.negate()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ auto-complete
    /** Suggestions for the last word being typed: filter names, then values for "filter:". */
    public static List<String> suggest(String text, Map<String, Supplier<List<String>>> filters, int max) {
        List<String> out = new ArrayList<>();
        String s = text == null ? "" : text;
        int start = s.lastIndexOf(' ') + 1;
        String word = s.substring(start);
        boolean neg = word.startsWith("-");
        if (neg) word = word.substring(1);
        String lw = word.toLowerCase(Locale.ROOT);
        int c = lw.indexOf(':');
        if (c < 0) {
            if (lw.isEmpty()) return out;
            for (String f : filters.keySet()) if (f.startsWith(lw) && !f.equals(lw)) out.add(f + ":");
        } else {
            String key = lw.substring(0, c), val = lw.substring(c + 1);
            Supplier<List<String>> vs = filters.get(key);
            if (vs == null) for (String f : filters.keySet()) if (f.startsWith(key)) { vs = filters.get(f); key = f; break; }
            if (vs != null) {
                for (String v : vs.get()) {
                    String lv = v.toLowerCase(Locale.ROOT).replace(' ', '_');
                    if (lv.startsWith(val) && !lv.equals(val)) out.add(key + ":" + lv);
                    if (out.size() >= max) break;
                }
            }
        }
        if (out.size() > max) return out.subList(0, max);
        return out;
    }

    /** Replaces the last word of {@code text} with {@code completion} (keeps a leading '-'). */
    public static String complete(String text, String completion) {
        int start = text.lastIndexOf(' ') + 1;
        boolean neg = text.startsWith("-", start);
        String prefix = text.substring(0, start) + (neg ? "-" : "");
        return prefix + completion + (completion.endsWith(":") ? "" : " ");
    }

    /** Helper to build an ordered filter map. */
    public static Map<String, Supplier<List<String>>> filters() { return new LinkedHashMap<>(); }
}
