package com.artistikcity.config;

import com.samskivert.mustache.Mustache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

/**
 * Mustache (JMustache) compiler used for the server-rendered pages that were Blade views
 * in the Laravel app (checkout, static pages, admin panel, e-mails, certificates).
 *
 * Settings mimic PHP/Blade truthiness: missing values render as "", and empty strings / 0
 * are "false" in {{#section}} blocks.
 */
@Configuration
public class MustacheConfig {

    @Bean
    public Mustache.Compiler mustacheCompiler() {
        return Mustache.compiler()
                .defaultValue("")
                .emptyStringIsFalse(true)
                .zeroIsFalse(true)
                // print 4999.0 as 4999 (PHP style) for FLOAT columns such as course prices
                .withFormatter(value -> value instanceof Double d && d == Math.rint(d) && !d.isInfinite()
                        ? String.valueOf(d.longValue()) : String.valueOf(value))
                .withLoader(name -> {
                    InputStream in = MustacheConfig.class.getResourceAsStream("/templates/" + name + ".mustache");
                    if (in == null) {
                        throw new FileNotFoundException("Template not found: " + name);
                    }
                    return new InputStreamReader(in, StandardCharsets.UTF_8);
                });
    }
}
