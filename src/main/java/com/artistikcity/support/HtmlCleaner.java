package com.artistikcity.support;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allow-list HTML cleaner for rich text written in the admin console (blog posts, lessons, newsletters).
 * Every tag is rebuilt from its parsed name and allowed attributes, so anything not on the list
 * (scripts, styles, event handlers, javascript: links, forms, iframes) never reaches the page.
 * Text is passed through; a stray "&lt;" that is not a tag is escaped.
 */
public final class HtmlCleaner {

    private HtmlCleaner() {
    }

    private static final Set<String> TAGS = Set.of("p", "br", "hr", "h2", "h3", "h4", "strong", "b", "em", "i", "u", "s", "strike", "del",
            "sub", "sup", "ul", "ol", "li", "blockquote", "pre", "code", "a", "img", "figure", "figcaption", "span", "div",
            "table", "thead", "tbody", "tr", "th", "td", "small", "mark");
    private static final Set<String> VOID = Set.of("br", "hr", "img");
    /** Tags removed together with everything inside them. */
    private static final Pattern DROP_BLOCKS = Pattern.compile("(?is)<(script|style|iframe|object|embed|template|noscript|svg|math|textarea|select|button|form)\\b.*?(</\\1\\s*>|$)");
    private static final Pattern COMMENTS = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:\\s+[^\\s/>=]+(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?)*)\\s*/?>");
    private static final Pattern ATTR = Pattern.compile("([^\\s/>=]+)(?:\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+))?");
    private static final Map<String, Set<String>> ATTRS = Map.of(
            "a", Set.of("href", "title", "target"),
            "img", Set.of("src", "alt", "title", "width", "height"),
            "td", Set.of("colspan", "rowspan"),
            "th", Set.of("colspan", "rowspan", "scope"),
            "ol", Set.of("start"));
    private static final Pattern SAFE_URL = Pattern.compile("(?i)^(https?://|mailto:|tel:|/(?!/)|#)[^\\s\"'<>`]*$");
    private static final Pattern NUMBER = Pattern.compile("^\\d{1,4}%?$");

    public static String clean(String html) {
        if (html == null || html.isBlank()) {
            return html == null ? null : "";
        }
        String s = COMMENTS.matcher(html).replaceAll("");
        s = DROP_BLOCKS.matcher(s).replaceAll("");
        StringBuilder out = new StringBuilder(s.length());
        Matcher m = TAG.matcher(s);
        int last = 0;
        while (m.find()) {
            out.append(text(s.substring(last, m.start())));
            last = m.end();
            String name = m.group(2).toLowerCase(Locale.ROOT);
            if (!TAGS.contains(name)) {
                continue;
            }
            boolean closing = !m.group(1).isEmpty();
            if (closing) {
                if (!VOID.contains(name)) {
                    out.append("</").append(name).append('>');
                }
                continue;
            }
            out.append('<').append(name).append(attributes(name, m.group(3))).append('>');
        }
        out.append(text(s.substring(last)));
        return out.toString();
    }

    private static String attributes(String tag, String raw) {
        Set<String> allowed = ATTRS.get(tag);
        if (allowed == null || raw == null || raw.isBlank()) {
            return "img".equals(tag) ? " alt=\"\"" : "";
        }
        StringBuilder sb = new StringBuilder();
        boolean external = false;
        Matcher a = ATTR.matcher(raw);
        while (a.find()) {
            String name = a.group(1).toLowerCase(Locale.ROOT);
            if (!allowed.contains(name)) {
                continue;
            }
            String v = a.group(2) == null ? "" : a.group(2);
            if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
                v = v.substring(1, v.length() - 1);
            }
            v = decodeBasic(v).trim();
            if (("href".equals(name) || "src".equals(name)) && !SAFE_URL.matcher(v).matches()) {
                continue;
            }
            if (("width".equals(name) || "height".equals(name) || "colspan".equals(name) || "rowspan".equals(name) || "start".equals(name))
                    && !NUMBER.matcher(v).matches()) {
                continue;
            }
            if ("target".equals(name)) {
                if (!"_blank".equals(v)) {
                    continue;
                }
                external = true;
            }
            if ("scope".equals(name) && !Set.of("row", "col").contains(v)) {
                continue;
            }
            sb.append(' ').append(name).append("=\"").append(escapeAttr(v)).append('"');
        }
        if (external) {
            sb.append(" rel=\"noopener noreferrer\"");
        }
        if ("img".equals(tag) && sb.indexOf(" alt=") < 0) {
            sb.append(" alt=\"\"");
        }
        return sb.toString();
    }

    private static String text(String t) {
        return t.replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String decodeBasic(String v) {
        // entities could hide "javascript:" from the URL check, so decode the common forms first
        String d = v.replace("&colon;", ":").replace("&#58;", ":").replace("&#x3a;", ":").replace("&#x3A;", ":")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
        return d.replaceAll("[\\x00-\\x1F]", "");
    }

    private static String escapeAttr(String v) {
        return v.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Plain text from HTML (for excerpts and meta descriptions). */
    public static String text(String html, int max) {
        if (html == null) {
            return "";
        }
        String t = DROP_BLOCKS.matcher(html).replaceAll(" ").replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, Math.max(0, max - 1)).trim() + "…" : t;
    }
}
