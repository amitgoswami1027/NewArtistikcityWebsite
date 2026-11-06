package com.artistikcity.rooms;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Audit;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.UploadedFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin > Room preview: the room photos buyers see their artwork in on {@code /purchase/step-2}. Upload a photo, drag
 * the hanging area on it, say how wide that area is in real life (for true scale), order and hide rooms, or switch the
 * room step off. Capability {@code marketplace.publish} (super admins and gallery moderators; instructors only manage their own listings); every change is in the security log.
 */
@Controller
public class RoomAdminController {

    /** Below this the photo looks soft on a laptop screen. */
    static final int MIN_WIDTH = 1000;

    private final AdminKit kit;
    private final Json json;
    private final Storage storage;
    private final Audit audit;
    private final RoomScenes rooms;

    public RoomAdminController(AdminKit kit, Json json, Storage storage, Audit audit, RoomScenes rooms) {
        this.kit = kit;
        this.json = json;
        this.storage = storage;
        this.audit = audit;
        this.rooms = rooms;
    }

    @GetMapping({"/admin/marketplace/rooms", "/admin/rooms"})
    public ResponseEntity<String> page(HttpServletRequest request) {
        return kit.page(request, "marketplace.publish", "Admin/Rooms", () -> {
            Map<String, Object> p = new LinkedHashMap<>(kit.base(kit.admin(request)));
            p.put("rooms", rooms.all());
            p.put("enabled", rooms.enabled());
            p.put("maxActive", RoomScenes.MAX_ACTIVE);
            p.put("minWidth", MIN_WIDTH);
            return p;
        });
    }

    /** multipart: photo, label, note, x, y, w, h (percent of the photo), widthIn (optional), active. */
    @PostMapping("/admin/api/rooms")
    public ResponseEntity<String> create(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "marketplace.publish")) return kit.forbidden();
        Input in = Input.of(request, json);
        RoomScenes.Fields f = fields(in);
        Map<String, String> errors = RoomScenes.validate(f);
        UploadedFile photo = in.file("photo");
        if (photo == null || photo.getContent() == null || photo.getContent().length == 0) errors.put("photo", "Upload a photo of the room.");
        Photo stored = errors.isEmpty() ? store(photo, errors) : null;
        if (f.active() && rooms.activeCount() >= RoomScenes.MAX_ACTIVE) errors.put("active", "Up to " + RoomScenes.MAX_ACTIVE + " rooms can be shown. Hide one first.");
        if (!errors.isEmpty()) return kit.invalid(errors);
        long id = rooms.create(f, stored.url(), stored.width(), stored.height());
        audit.change(admin, "room_scene", id, "created", f.label(), null, audit.snapshot("room_scenes", id));
        return kit.ok(Map.of("room", RoomScenes.view(rooms.find(id)), "rooms", rooms.all()));
    }

    /** multipart or JSON: the same fields; a new photo is optional. */
    @PostMapping("/admin/api/rooms/{id}")
    public ResponseEntity<String> update(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "marketplace.publish")) return kit.forbidden();
        Row before = rooms.find(id);
        if (before == null) return kit.fail(HttpStatus.NOT_FOUND, "That room was deleted.");
        Input in = Input.of(request, json);
        RoomScenes.Fields f = fields(in);
        Map<String, String> errors = RoomScenes.validate(f);
        UploadedFile photo = in.file("photo");
        Photo stored = errors.isEmpty() && photo != null && photo.getContent() != null && photo.getContent().length > 0 ? store(photo, errors) : null;
        boolean wasActive = "ACTIVE".equals(before.str("status"));
        if (f.active() && !wasActive && rooms.activeCount() >= RoomScenes.MAX_ACTIVE) errors.put("active", "Up to " + RoomScenes.MAX_ACTIVE + " rooms can be shown. Hide one first.");
        if (!errors.isEmpty()) return kit.invalid(errors);
        rooms.update(id, f, stored == null ? null : stored.url(), stored == null ? null : stored.width(), stored == null ? null : stored.height());
        audit.change(admin, "room_scene", id, "updated", f.label(), before, audit.snapshot("room_scenes", id));
        return kit.ok(Map.of("room", RoomScenes.view(rooms.find(id)), "rooms", rooms.all()));
    }

    @PostMapping("/admin/api/rooms/{id}/delete")
    public ResponseEntity<String> delete(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "marketplace.publish")) return kit.forbidden();
        Row before = rooms.find(id);
        if (before == null) return kit.ok(Map.of("rooms", rooms.all()));
        rooms.delete(id);
        audit.change(admin, "room_scene", id, "deleted", before.str("label"), before, null);
        return kit.ok(Map.of("rooms", rooms.all()));
    }

    /** JSON {ids: [3, 1, 2]}. */
    @PostMapping("/admin/api/rooms/order")
    public ResponseEntity<String> order(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "marketplace.publish")) return kit.forbidden();
        List<Long> ids = new ArrayList<>();
        for (String s : Input.of(request, json).array("ids")) {
            if (s != null && s.matches("\\d{1,18}")) ids.add(Long.parseLong(s));
        }
        rooms.reorder(ids);
        audit.log(admin, "room_scene", 0, "reordered", String.valueOf(ids));
        return kit.ok(Map.of("rooms", rooms.all()));
    }

    /** JSON {enabled: true|false}: the room step on the purchase page. */
    @PostMapping("/admin/api/rooms/settings")
    public ResponseEntity<String> settings(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "marketplace.publish")) return kit.forbidden();
        boolean on = Input.of(request, json).bool("enabled");
        boolean was = rooms.enabled();
        rooms.setEnabled(on);
        audit.change(admin, "setting", 0, "room preview " + (on ? "on" : "off"), null, Map.of("enabled", was), Map.of("enabled", on));
        return kit.ok(Map.of("enabled", rooms.enabled()));
    }

    // ------------------------------------------------------------------ helpers

    private static RoomScenes.Fields fields(Input in) {
        String w = in.get("widthIn");
        BigDecimal widthIn = null;
        if (w != null && !w.isBlank()) {
            try {
                widthIn = new BigDecimal(w.trim()).setScale(2, java.math.RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                widthIn = BigDecimal.valueOf(-1);
            }
        }
        return new RoomScenes.Fields(AdminKit.trim(in.get("label"), 200), AdminKit.trim(in.get("note"), 300),
                d(in.get("x")), d(in.get("y")), d(in.get("w")), d(in.get("h")), widthIn, !"false".equals(in.get("active")) && !"0".equals(in.get("active")));
    }

    private static double d(String s) {
        try {
            return s == null || s.isBlank() ? -1 : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private record Photo(String url, int width, int height) {
    }

    private Photo store(UploadedFile photo, Map<String, String> errors) {
        String bad = AdminKit.checkImage(photo);
        if (bad != null) {
            errors.put("photo", bad);
            return null;
        }
        int[] size = RoomScenes.dimensions(photo.getContent());
        if (size == null) {
            errors.put("photo", "We couldn't read that photo. Upload a JPG, PNG or WebP.");
            return null;
        }
        if (size[0] < MIN_WIDTH) {
            errors.put("photo", "Use a photo at least " + MIN_WIDTH + " pixels wide (this one is " + size[0] + "), so the room looks sharp.");
            return null;
        }
        String ext = String.valueOf(photo.getClientOriginalName()).replaceAll("^.*\\.", "").toLowerCase(java.util.Locale.ROOT);
        if (ext.equals("jpeg")) ext = "jpg";
        LocalDate today = LocalDate.now();
        String rel = "uploads/rooms/" + today.getYear() + "/" + String.format("%02d", today.getMonthValue()) + "/room-" + UUID.randomUUID().toString().substring(0, 12) + "." + ext;
        storage.put(rel, photo.getContent());
        return new Photo(Storage.publicUrl(rel), size[0], size[1]);
    }
}
