package com.artistikcity.web;

import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.StudioStoriesService;
import com.artistikcity.support.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;

/** "Studio Stories" - the art journal fed from the Studio Vaishali Arts blog. */
@Controller
public class StudioStoriesController {

    private final Inertia inertia;
    private final StudioStoriesService stories;
    private final Json json;

    public StudioStoriesController(Inertia inertia, StudioStoriesService stories, Json json) {
        this.inertia = inertia;
        this.stories = stories;
        this.json = json;
    }

    @GetMapping("/studio-stories")
    public ResponseEntity<String> index(HttpServletRequest request) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("stories", stories.all());
        props.put("blogUrl", stories.blogUrl());
        return inertia.render(request, "StudioStories", props);
    }

    @GetMapping("/api/studio-stories")
    public ResponseEntity<String> api(@RequestParam(value = "limit", defaultValue = "6") int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stories", stories.latest(Math.max(1, Math.min(limit, 30))));
        body.put("blogUrl", stories.blogUrl());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }
}
