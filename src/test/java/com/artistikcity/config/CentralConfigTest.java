package com.artistikcity.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** config/artistikcity.yml is the one place for settings: defaults, the dev profile and overrides all go through it. */
class CentralConfigTest {

    @Configuration
    static class Empty {
    }

    private static ConfigurableApplicationContext boot(String... args) {
        return new SpringApplicationBuilder(Empty.class).web(WebApplicationType.NONE).logStartupInfo(false)
                .properties("spring.main.banner-mode=off").run(args);
    }

    @Test
    void defaultsComeFromTheCentralFile() {
        try (ConfigurableApplicationContext c = boot()) {
            Environment env = c.getEnvironment();
            assertEquals("ArtistikCity", env.getProperty("app.name"));
            assertEquals("local", env.getProperty("app.storage.driver"));
            assertEquals("false", env.getProperty("app.payment-test-mode"));
            assertEquals("30", env.getProperty("app.ledger.commission-percent"));
            assertEquals("15", env.getProperty("app.seats.hold-minutes"));
            assertEquals("https://api.razorpay.com", env.getProperty("app.payments.razorpay.api-base"));
        }
    }

    @Test
    void theDevProfileSectionApplies() {
        try (ConfigurableApplicationContext c = boot("--spring.profiles.active=dev")) {
            assertEquals("true", c.getEnvironment().getProperty("app.payment-test-mode"));
            assertEquals("false", c.getEnvironment().getProperty("app.template-cache"));
        }
    }

    @Test
    void aSettingCanBeOverriddenWithoutEditingTheFile() {
        // the same mechanism as an environment variable: the ${COMMISSION_PERCENT:30} placeholder picks it up
        try (ConfigurableApplicationContext c = boot("--COMMISSION_PERCENT=25", "--APP_NAME=Studio")) {
            assertEquals("25", c.getEnvironment().getProperty("app.ledger.commission-percent"));
            assertEquals("Studio", c.getEnvironment().getProperty("app.name"));
        }
    }
}
