package com.artistikcity.support.s3;

import java.time.Instant;

/** AWS access key, secret and (for temporary role credentials) session token with its expiry. */
public record AwsCredentials(String accessKey, String secretKey, String sessionToken, Instant expiration) {

    @Override
    public String toString() {
        return "AwsCredentials[" + (accessKey == null || accessKey.length() < 4 ? "?" : accessKey.substring(0, 4) + "…") + "]";
    }
}
