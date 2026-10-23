package com.artistikcity.web.user;

import com.artistikcity.http.Auth;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.web.AuthApiController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/** Student wish list: the page under My studio and the heart buttons on course and marketplace pages. */
@Controller
public class WishlistController {

    private final Inertia inertia;
    private final Auth auth;
    private final Json json;
    private final WishlistService wishlist;

    public WishlistController(Inertia inertia, Auth auth, Json json, WishlistService wishlist) {
        this.inertia = inertia;
        this.auth = auth;
        this.json = json;
        this.wishlist = wishlist;
    }

    @GetMapping("/user/wishlist")
    public ResponseEntity<String> page(HttpServletRequest request) {
        return inertia.renderUser(request, "Students/Wishlist", Map.of("items", wishlist.items(auth.userId(request))));
    }

    /** {type: course|painting, id, saved: true|false} -> {saved, count}. 401 for guests (the page sends them to log in). */
    @PostMapping("/wishlist")
    public ResponseEntity<String> set(HttpServletRequest request) {
        Long userId = auth.userId(request);
        if (userId == null) {
            throw new AuthApiController.UnauthorizedException();
        }
        Input in = Input.of(request, json);
        String type = in.get("type");
        long id;
        try {
            id = Long.parseLong(String.valueOf(in.get("id")));
        } catch (NumberFormatException e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown item.");
        }
        boolean saved = in.bool("saved");
        if (saved && !wishlist.exists(type, id)) {
            return error(HttpStatus.NOT_FOUND, "This item is no longer available.");
        }
        if (!WishlistService.COURSE.equals(type) && !WishlistService.PAINTING.equals(type)) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown item.");
        }
        wishlist.set(userId, type, id, saved);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("saved", saved);
        body.put("count", wishlist.count(userId));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of("message", message)));
    }
}
