package com.artistikcity.shop;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prices for originals, prints and downloads, print sizes, frames and coupons. */
class ShopPricingTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private static final List<ShopPricing.Option> OPTIONS = List.of(
            new ShopPricing.Option("CANVAS", "S", "Small", d("12"), d("2499"), d("600"), 1, true),
            new ShopPricing.Option("CANVAS", "M", "Medium", d("24"), d("4999"), d("1100"), 2, true),
            new ShopPricing.Option("CANVAS", "XL", "Statement", d("48"), d("13999"), d("2200"), 4, false),
            new ShopPricing.Option("POSTER", "M", "Medium", d("18"), d("1199"), d("0"), 2, true),
            new ShopPricing.Option("DIGITAL", "PERSONAL", "High-resolution file, personal-use licence", null, d("999"), d("0"), 1, true),
            new ShopPricing.Option("FRAME", "BLACK_WOOD", "Black wood frame", null, d("22"), d("0"), 1, true),
            new ShopPricing.Option("FRAME", "OAK", "Natural oak frame", null, d("28"), d("0"), 2, true),
            new ShopPricing.Option("FRAME", "WHITE_FLOAT", "White canvas float frame", null, d("30"), d("0"), 3, true),
            new ShopPricing.Option("SHIPPING", "PRINTS_INDIA", "India", null, d("149"), d("0"), 1, true));

    /** 16 in tall, 12 in wide graphite drawing on paper, unframed. */
    private static ShopPricing.Art paper(String status, Set<String> formats, boolean hires, int editionSize, int sold) {
        return new ShopPricing.Art(1, "Macaw", "Vaishali J Goswami", "Archival 300gsm acid-free paper", status, d("15300.00"), d("16"), d("12"),
                false, formats, hires, editionSize, sold);
    }

    private static ShopPricing.Selection sel(String f, String size, String finish, String frame, int qty) {
        return new ShopPricing.Selection(f, size, finish, frame, qty);
    }

    @Test
    void printsKeepTheArtworksProportions() {
        BigDecimal[] wh = ShopPricing.printSize(paper("AVAILABLE", Set.of(), false, 50, 0), d("24"));
        assertEquals("18", wh[0].toPlainString());     // 24 * 12/16
        assertEquals("24", wh[1].toPlainString());
        ShopPricing.Art wide = new ShopPricing.Art(2, "Mill", "A", "Canvas", "AVAILABLE", d("100"), d("20"), d("30"), false, Set.of("CANVAS"), false, 50, 0);
        BigDecimal[] w2 = ShopPricing.printSize(wide, d("12"));
        assertEquals("12", w2[0].toPlainString());
        assertEquals("8", w2[1].toPlainString());
        ShopPricing.Art odd = new ShopPricing.Art(3, "Odd", "A", "Canvas", "AVAILABLE", d("100"), d("17"), d("10"), false, Set.of("CANVAS"), false, 50, 0);
        assertEquals("7", ShopPricing.printSize(odd, d("12"))[0].toPlainString());   // 7.06 -> 7.0
        assertEquals("14", ShopPricing.printSize(odd, d("24"))[0].toPlainString());  // 14.1 -> 14.0
        assertEquals("28", ShopPricing.printSize(odd, d("48"))[0].toPlainString());  // 28.2 -> 28.0
        assertEquals("10.5", ShopPricing.printSize(odd, d("18"))[0].toPlainString()); // 10.6 -> 10.5
    }

    @Test
    void originalIsTheListingPriceAndCanBeFramed() {
        ShopPricing.Art art = paper("AVAILABLE", Set.of(), false, 50, 0);
        ShopPricing.Quote q = ShopPricing.quote(art, OPTIONS, sel("ORIGINAL", "", "", "NONE", 3));
        assertTrue(q.ok());
        assertEquals(0, d("15300").compareTo(q.unitPrice()));
        assertEquals(1, q.selection().qty());            // one of one
        ShopPricing.Quote framed = ShopPricing.quote(art, OPTIONS, sel("ORIGINAL", "", "", "OAK", 1));
        assertEquals(0, d("16868").compareTo(framed.unitPrice()));   // 28 * (16+12)*2 = 1568, + 15300
        assertTrue(framed.label().contains("natural oak frame"));
        // a float frame is for canvas, not paper
        assertFalse(ShopPricing.quote(art, OPTIONS, sel("ORIGINAL", "", "", "WHITE_FLOAT", 1)).ok());
        assertFalse(ShopPricing.quote(paper("SOLD", Set.of(), false, 50, 0), OPTIONS, sel("ORIGINAL", "", "", "NONE", 1)).ok());
    }

    @Test
    void canvasAddsStretchingAndFrameAndRespectsTheEdition() {
        ShopPricing.Art art = paper("SOLD", Set.of("CANVAS", "POSTER"), false, 50, 48);
        ShopPricing.Quote q = ShopPricing.quote(art, OPTIONS, sel("CANVAS", "M", "STRETCHED", "BLACK_WOOD", 5));
        assertTrue(q.ok(), q.error());
        // 4999 + 1100 stretched + 22 * (18 + 24) * 2 = 1848
        assertEquals(0, d("7947").compareTo(q.unitPrice()));
        assertEquals(2, q.selection().qty());            // only 2 editions left
        assertEquals(0, d("15894").compareTo(q.lineTotal()));
        // a framed canvas is always stretched
        ShopPricing.Quote rolled = ShopPricing.quote(art, OPTIONS, sel("CANVAS", "M", "ROLLED", "OAK", 1));
        assertEquals("STRETCHED", rolled.selection().finish());
        ShopPricing.Quote plain = ShopPricing.quote(art, OPTIONS, sel("CANVAS", "M", "ROLLED", "NONE", 1));
        assertEquals("ROLLED", plain.selection().finish());
        assertEquals(0, d("4999").compareTo(plain.unitPrice()));
        // inactive size
        assertFalse(ShopPricing.quote(art, OPTIONS, sel("CANVAS", "XL", "", "NONE", 1)).ok());
        assertFalse(ShopPricing.quote(paper("AVAILABLE", Set.of("CANVAS"), false, 50, 50), OPTIONS, sel("CANVAS", "M", "", "NONE", 1)).ok());
    }

    @Test
    void postersAndDigital() {
        ShopPricing.Art art = paper("AVAILABLE", Set.of("POSTER", "DIGITAL"), true, 50, 0);
        ShopPricing.Quote poster = ShopPricing.quote(art, OPTIONS, sel("POSTER", "M", "glossy", "NONE", 4));
        assertTrue(poster.ok());
        assertEquals("MATTE", poster.selection().finish());   // codes are upper case; unknown -> matte
        assertEquals(0, d("4796").compareTo(poster.lineTotal()));
        assertFalse(ShopPricing.quote(art, OPTIONS, sel("POSTER", "M", "", "WHITE_FLOAT", 1)).ok());
        ShopPricing.Quote digital = ShopPricing.quote(art, OPTIONS, sel("DIGITAL", "", "", "NONE", 3));
        assertTrue(digital.ok());
        assertEquals(1, digital.selection().qty());
        assertNull(digital.widthIn());
        assertFalse(ShopPricing.quote(paper("AVAILABLE", Set.of("DIGITAL"), false, 50, 0), OPTIONS, sel("DIGITAL", "", "", "NONE", 1)).ok());
        assertFalse(ShopPricing.quote(art, OPTIONS, sel("CANVAS", "M", "", "NONE", 1)).ok());   // canvas not enabled
    }

    @Test
    void selectionParsingIsDefensive() {
        ShopPricing.Selection s = ShopPricing.Selection.of("canvas", "m'; drop", null, "", "3");
        assertEquals("CANVAS", s.format());
        assertEquals("MDROP", s.size());
        assertEquals("NONE", s.frame());
        assertEquals(3, s.qty());
    }

    @Test
    void offerListsFormatsWithPrintSizes() {
        Map<String, Object> offer = ShopPricing.offer(paper("AVAILABLE", Set.of("CANVAS"), false, 50, 0), OPTIONS, true, null);
        @SuppressWarnings("unchecked") List<Map<String, Object>> formats = (List<Map<String, Object>>) offer.get("formats");
        assertEquals(4, formats.size());
        assertEquals(true, formats.get(0).get("available"));    // original
        assertEquals(true, formats.get(1).get("available"));    // canvas
        assertEquals(false, formats.get(2).get("available"));   // poster not enabled
        assertEquals(false, formats.get(3).get("available"));   // digital: no file
        @SuppressWarnings("unchecked") List<Map<String, Object>> sizes = (List<Map<String, Object>>) formats.get(1).get("sizes");
        assertEquals(2, sizes.size());                           // XL is switched off
        assertEquals("9", sizes.get(0).get("width").toString());
    }

    @Test
    void couponsApplyToEligibleLinesOnly() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 25, 12, 0);
        List<ShopPricing.Line> lines = List.of(new ShopPricing.Line(1, "Vaishali J Goswami", d("10000")), new ShopPricing.Line(2, "Kabir", d("5000")));
        ShopPricing.Coupon pct = new ShopPricing.Coupon(1, "WELCOME10", null, "PERCENT", d("10"), null, null, null, null, 0, null, null, true);
        ShopPricing.Discount all = ShopPricing.discount(pct, lines, now);
        assertNull(all.error());
        assertEquals(0, d("1500").compareTo(all.total()));
        assertEquals(0, d("1000").compareTo(all.perLine().get(0)));
        assertEquals(0, d("500").compareTo(all.perLine().get(1)));

        ShopPricing.Coupon artist = new ShopPricing.Coupon(2, "V500", null, "FLAT", d("500"), null, "vaishali j goswami", d("3000"), 10, 0, null, null, true);
        ShopPricing.Discount a = ShopPricing.discount(artist, lines, now);
        assertEquals(0, d("500").compareTo(a.total()));
        assertEquals(0, BigDecimal.ZERO.compareTo(a.perLine().get(1)));

        ShopPricing.Coupon expired = new ShopPricing.Coupon(3, "OLD", null, "PERCENT", d("10"), null, null, null, null, 0, null, now.minusDays(1), true);
        assertNotNull(ShopPricing.discount(expired, lines, now).error());
        ShopPricing.Coupon usedUp = new ShopPricing.Coupon(4, "GONE", null, "PERCENT", d("10"), null, null, null, 5, 5, null, null, true);
        assertNotNull(ShopPricing.discount(usedUp, lines, now).error());
        ShopPricing.Coupon other = new ShopPricing.Coupon(5, "ART9", null, "PERCENT", d("10"), 9L, null, null, null, 0, null, null, true);
        assertEquals("This code doesn't apply to the artworks in your cart.", ShopPricing.discount(other, lines, now).error());
        ShopPricing.Coupon big = new ShopPricing.Coupon(6, "BIG", null, "FLAT", d("99999"), null, null, null, null, 0, null, null, true);
        assertEquals(0, d("15000").compareTo(ShopPricing.discount(big, lines, now).total()));   // never more than the lines
        ShopPricing.Coupon min = new ShopPricing.Coupon(7, "MIN", null, "FLAT", d("100"), null, null, d("20000"), null, 0, null, null, true);
        assertNotNull(ShopPricing.discount(min, lines, now).error());
    }

    @Test
    void artistContactHelpers() {
        assertEquals("919876543201", ArtistHubService.whatsappNumber("98765 43201"));
        assertEquals("919876543201", ArtistHubService.whatsappNumber("+91 98765-43201"));
        assertNull(ArtistHubService.whatsappNumber("12345"));
        assertEquals("artistikcity", ArtistHubService.handle("https://www.instagram.com/artistikcity/"));
        assertEquals("some.artist", ArtistHubService.handle("@some.artist"));
        assertNull(ArtistHubService.handle("not a handle!"));
    }
}
