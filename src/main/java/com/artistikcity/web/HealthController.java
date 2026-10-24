package com.artistikcity.web;

import com.artistikcity.support.Db;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check for the deploy script, CloudFront tests and monitoring: 200 {@code {"status":"UP"}} when the app and
 * its database answer, 503 otherwise. No session, no secrets, nothing else.
 */
@RestController
public class HealthController {

    private final Db db;

    public HealthController(Db db) {
        this.db = db;
    }

    @GetMapping(value = "/healthz", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> health() {
        boolean dbUp;
        try {
            dbUp = db.count("select 1") == 1;
        } catch (RuntimeException e) {
            dbUp = false;
        }
        return ResponseEntity.status(dbUp ? 200 : 503).cacheControl(CacheControl.noStore())
                .body(dbUp ? "{\"status\":\"UP\",\"db\":\"UP\"}" : "{\"status\":\"DOWN\",\"db\":\"DOWN\"}");
    }
}
