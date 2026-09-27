package com.artistikcity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ArtistikCity - online art school.
 *
 * Java / Spring Boot port of the original Laravel 8 + Inertia.js + React application.
 * The React front-end (frontend/) is unchanged: this backend speaks the Inertia protocol
 * and publishes the Ziggy route list, so every React page renders exactly as before.
 */
@SpringBootApplication
public class ArtistikCityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArtistikCityApplication.class, args);
    }
}
