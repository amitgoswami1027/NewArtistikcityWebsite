package com.artistikcity.marketplace;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled so the marketplace janitor can release expired 15-minute holds every minute. */
@Configuration
@EnableScheduling
public class MarketplaceScheduling {
}
