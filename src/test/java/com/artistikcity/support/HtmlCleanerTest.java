package com.artistikcity.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlCleanerTest {

    @Test
    void keepsFormattingFromTheEditor() {
        String html = "<h2>Washes</h2><p>Use <strong>clean</strong> water and <em>soft</em> brushes.</p><ul><li>Flat</li><li>Graded</li></ul>"
                + "<blockquote>Less is more</blockquote>";
        assertEquals(html, HtmlCleaner.clean(html));
    }

    @Test
    void removesScriptsStylesAndHandlers() {
        String out = HtmlCleaner.clean("<p onclick=\"steal()\">Hi</p><script>alert(1)</script><style>p{}</style><img src=x onerror=alert(1)>");
        assertFalse(out.contains("script"));
        assertFalse(out.contains("onclick"));
        assertFalse(out.contains("onerror"));
        assertFalse(out.contains("style"));
        assertTrue(out.startsWith("<p>Hi</p>"));
    }

    @Test
    void dropsDangerousLinks() {
        assertEquals("<a>x</a>", HtmlCleaner.clean("<a href=\"javascript:alert(1)\">x</a>"));
        assertEquals("<a>x</a>", HtmlCleaner.clean("<a href=\"java&#x3A;script:alert(1)\">x</a>"));
        assertEquals("<a>x</a>", HtmlCleaner.clean("<a href=\"data:text/html,hi\">x</a>"));
        assertEquals("<a href=\"https://example.com/a?b=1&amp;c=2\">x</a>", HtmlCleaner.clean("<a href=\"https://example.com/a?b=1&c=2\">x</a>"));
        assertEquals("<a href=\"/courses\" target=\"_blank\" rel=\"noopener noreferrer\">x</a>",
                HtmlCleaner.clean("<a href=\"/courses\" target=\"_blank\">x</a>"));
    }

    @Test
    void escapesUnknownTagsAndStrayBrackets() {
        assertEquals("<p>a &lt; b</p>", HtmlCleaner.clean("<p>a < b</p>"));
        assertEquals("<p>hi</p>", HtmlCleaner.clean("<p><form action=/x><input name=a></form>hi</p>"));
        assertEquals("&lt;img src=x onerror=alert(1)", HtmlCleaner.clean("<img src=x onerror=alert(1)"));
        assertEquals("<p></p>", HtmlCleaner.clean("<p><iframe src=\"https://evil\"></iframe></p>"));
    }

    @Test
    void imagesKeepSafeSourcesOnly() {
        assertEquals("<img src=\"/storage/uploads/editor/a.png\" alt=\"Brush\">", HtmlCleaner.clean("<img src=\"/storage/uploads/editor/a.png\" alt=\"Brush\" style=\"x\">"));
        assertEquals("<img alt=\"\">", HtmlCleaner.clean("<img src=\"//evil.example/x.png\">"));
    }

    @Test
    void plainText() {
        assertEquals("Hello world", HtmlCleaner.text("<p>Hello <b>world</b></p>", 50));
        assertEquals("Hello…", HtmlCleaner.text("<p>Hello world</p>", 6));
    }
}
