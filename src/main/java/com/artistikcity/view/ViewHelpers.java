package com.artistikcity.view;

import com.artistikcity.support.Values;
import com.samskivert.mustache.Mustache;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Template helpers (Mustache lambdas) that stand in for the PHP expressions used by the Blade views.
 * The section body is rendered first, then interpreted:
 *
 * <pre>
 * {{#when}}{{status}}==1⟫Active⟪else⟫Inactive{{/when}}      @if / ternary; conditions: a==b, a!=b, a, !a,
 *                                                           "a in x,y,z", combined with &amp;&amp;
 * {{#money}}{{price}}{{/money}}                             number_format($price, 2)
 * {{#money0}}{{price}}{{/money0}}                           number_format($price)
 * {{#dateFmt}}F j, Y|{{created_at}}{{/dateFmt}}            date('F j, Y', strtotime($created_at))
 * {{#limit}}60|{{sub_title}}{{/limit}}                      Str::limit($sub_title, 60, ' ...')
 * </pre>
 */
public final class ViewHelpers {

    public static final String THEN = "⟫";   // ⟫
    public static final String ELSE = "⟪else⟫"; // ⟪else⟫

    private ViewHelpers() {
    }

    public static void register(Map<String, Object> ctx) {
        ctx.put("when", (Mustache.Lambda) (frag, out) -> out.write(when(frag.execute())));
        ctx.put("money", (Mustache.Lambda) (frag, out) -> out.write(money(frag.execute().trim(), 2)));
        ctx.put("money0", (Mustache.Lambda) (frag, out) -> out.write(money(frag.execute().trim(), 0)));
        ctx.put("dateFmt", (Mustache.Lambda) (frag, out) -> {
            String s = frag.execute();
            int bar = s.indexOf('|');
            out.write(bar < 0 ? s : phpDate(s.substring(0, bar), s.substring(bar + 1).trim()));
        });
        ctx.put("limit", (Mustache.Lambda) (frag, out) -> {
            String s = frag.execute();
            int bar = s.indexOf('|');
            if (bar < 0) {
                out.write(s);
                return;
            }
            int n = Integer.parseInt(s.substring(0, bar).trim());
            String v = s.substring(bar + 1).trim();
            out.write(v.length() <= n ? v : v.substring(0, n) + " ...");
        });
    }

    static String when(String rendered) {
        int t = rendered.indexOf(THEN);
        if (t < 0) {
            return rendered;
        }
        String cond = rendered.substring(0, t);
        String rest = rendered.substring(t + THEN.length());
        int e = rest.indexOf(ELSE);
        String yes = e < 0 ? rest : rest.substring(0, e);
        String no = e < 0 ? "" : rest.substring(e + ELSE.length());
        return test(cond) ? yes : no;
    }

    static boolean test(String cond) {
        for (String part : cond.split("&&")) {
            if (!single(part.trim())) {
                return false;
            }
        }
        return true;
    }

    private static boolean single(String c) {
        int i;
        if ((i = c.indexOf("!=")) >= 0) {
            return !Values.looseEquals(norm(c.substring(0, i)), norm(c.substring(i + 2)));
        }
        if ((i = c.indexOf("==")) >= 0) {
            return Values.looseEquals(norm(c.substring(0, i)), norm(c.substring(i + 2)));
        }
        if ((i = c.indexOf(" in ")) >= 0) {
            String needle = norm(c.substring(0, i));
            return Arrays.stream(c.substring(i + 4).split(",")).map(ViewHelpers::norm).anyMatch(needle::equals);
        }
        if (c.startsWith("!")) {
            return !Values.truthy(norm(c.substring(1)));
        }
        return Values.truthy(norm(c));
    }

    private static String norm(String s) {
        String t = s.trim();
        if (t.length() >= 2 && (t.startsWith("'") && t.endsWith("'") || t.startsWith("\"") && t.endsWith("\""))) {
            t = t.substring(1, t.length() - 1);
        }
        return t;
    }

    static String money(String v, int decimals) {
        Double d = Values.toDouble(v.replace(",", ""));
        if (d == null) {
            return v;
        }
        String pattern = decimals == 0 ? "#,##0" : "#,##0." + "0".repeat(decimals);
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US)).format(d);
    }

    /** Subset of PHP date() format characters: d j D l m n M F Y y H G i s A a. */
    static String phpDate(String format, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        LocalDateTime dt;
        try {
            dt = value.length() <= 10 ? LocalDate.parse(value.substring(0, 10)).atStartOfDay()
                    : LocalDateTime.parse(value.substring(0, 19).replace(' ', 'T'));
        } catch (RuntimeException e) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        for (char ch : format.toCharArray()) {
            String p = switch (ch) {
                case 'd' -> "dd";
                case 'j' -> "d";
                case 'D' -> "EEE";
                case 'l' -> "EEEE";
                case 'm' -> "MM";
                case 'n' -> "M";
                case 'M' -> "MMM";
                case 'F' -> "MMMM";
                case 'Y' -> "yyyy";
                case 'y' -> "yy";
                case 'H' -> "HH";
                case 'G' -> "H";
                case 'i' -> "mm";
                case 's' -> "ss";
                case 'A', 'a' -> "a";
                default -> null;
            };
            sb.append(p == null ? String.valueOf(ch) : dt.format(DateTimeFormatter.ofPattern(p, Locale.ENGLISH)));
        }
        return sb.toString();
    }

    /** Escape of the helper markers inside user supplied values is not needed: they are unusual code points. */
    public static final Pattern MARKERS = Pattern.compile(THEN + "|" + ELSE);
}
