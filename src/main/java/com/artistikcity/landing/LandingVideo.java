package com.artistikcity.landing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a video link into something safe to embed: YouTube / Vimeo (privacy-friendly players) or an uploaded mp4. */
public final class LandingVideo {

    private static final Pattern YT = Pattern.compile("^https://(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})(?:[?&#].*)?$");
    private static final Pattern VIMEO = Pattern.compile("^https://(?:www\\.|player\\.)?vimeo\\.com/(?:video/)?([0-9]{6,12})(?:[?#/].*)?$");
    private static final Pattern FILE = Pattern.compile("^(?:/storage/|https://)[^\\s\"'<>]+\\.(?:mp4|webm)$", Pattern.CASE_INSENSITIVE);

    private LandingVideo() {
    }

    /** kind = youtube | vimeo | file; src = the embed / file address; poster = a thumbnail when the host offers one. */
    public record Embed(String kind, String src, String poster) {
    }

    public static Embed embed(String url) {
        if (url == null || url.isBlank() || url.length() > 1000) {
            return null;
        }
        String u = url.trim();
        Matcher m = YT.matcher(u);
        if (m.matches()) {
            String id = m.group(1);
            return new Embed("youtube", "https://www.youtube-nocookie.com/embed/" + id + "?autoplay=1&rel=0", "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg");
        }
        m = VIMEO.matcher(u);
        if (m.matches()) {
            return new Embed("vimeo", "https://player.vimeo.com/video/" + m.group(1) + "?autoplay=1&dnt=1", null);
        }
        if (FILE.matcher(u).matches() && !u.contains("..")) {
            return new Embed("file", u, null);
        }
        return null;
    }
}
