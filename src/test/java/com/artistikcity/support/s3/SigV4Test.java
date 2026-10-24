package com.artistikcity.support.s3;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The worked examples from the Amazon S3 guide "Signature calculations for the Authorization header (single chunk)". */
class SigV4Test {

    private static final AwsCredentials EXAMPLE = new AwsCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", null, null);
    private static final Instant WHEN = Instant.parse("2013-05-24T00:00:00Z");

    private static String signature(Map<String, String> signed) {
        String auth = signed.get("authorization");
        return auth.substring(auth.indexOf("Signature=") + 10);
    }

    @Test
    void getObjectWithRange() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Host", "examplebucket.s3.amazonaws.com");
        h.put("Range", "bytes=0-9");
        Map<String, String> s = SigV4.sign("GET", "/test.txt", null, h, SigV4.EMPTY_SHA256, EXAMPLE, "us-east-1", "s3", WHEN);
        assertEquals("f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41", signature(s));
        assertTrue(s.get("authorization").contains("Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request"));
        assertTrue(s.get("authorization").contains("SignedHeaders=host;range;x-amz-content-sha256;x-amz-date"));
    }

    @Test
    void putObjectWithEncodedKey() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Host", "examplebucket.s3.amazonaws.com");
        h.put("Date", "Fri, 24 May 2013 00:00:00 GMT");
        h.put("x-amz-storage-class", "REDUCED_REDUNDANCY");
        String path = "/" + SigV4.uriEncode("test$file.text", false);
        assertEquals("/test%24file.text", path);
        Map<String, String> s = SigV4.sign("PUT", path, null, h, "44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072",
                EXAMPLE, "us-east-1", "s3", WHEN);
        assertEquals("98ad721746da40c64f1a55b78f14c238d841ea1380cd77a1b5971af0ece108bd", signature(s));
    }

    @Test
    void getBucketLifecycleWithEmptyQueryValue() {
        Map<String, String> h = Map.of("Host", "examplebucket.s3.amazonaws.com");
        Map<String, String> s = SigV4.sign("GET", "/", "lifecycle", h, SigV4.EMPTY_SHA256, EXAMPLE, "us-east-1", "s3", WHEN);
        assertEquals("fea454ca298b7da1c68078a5d1bdbfbbe0d65c699e0f91ac7a200a0136783543", signature(s));
    }

    @Test
    void listObjectsWithSortedQuery() {
        Map<String, String> h = Map.of("Host", "examplebucket.s3.amazonaws.com");
        Map<String, String> s = SigV4.sign("GET", "/", "prefix=J&max-keys=2", h, SigV4.EMPTY_SHA256, EXAMPLE, "us-east-1", "s3", WHEN);
        assertEquals("34b48302e7b5fa45bde8084f4b7868a86f0a534bc59db6670ed5711ef69dc6f7", signature(s));
    }

    @Test
    void sessionTokenIsSigned() {
        AwsCredentials role = new AwsCredentials("ASIAEXAMPLE", "secret", "token/with+chars=", null);
        Map<String, String> s = SigV4.sign("GET", "/a.jpg", null, Map.of("host", "b.s3.ap-south-1.amazonaws.com"), SigV4.UNSIGNED_PAYLOAD,
                role, "ap-south-1", "s3", WHEN);
        assertEquals("token/with+chars=", s.get("x-amz-security-token"));
        assertTrue(s.get("authorization").contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date;x-amz-security-token"));
        assertEquals("UNSIGNED-PAYLOAD", s.get("x-amz-content-sha256"));
    }

    @Test
    void keysAreEncodedLikeAws() {
        assertEquals("storage/uploads/1700000000_My%20Photo%20%281%29.jpg", SigV4.uriEncode("storage/uploads/1700000000_My Photo (1).jpg", false));
        assertEquals("a%2Fb", SigV4.uriEncode("a/b", true));
        assertEquals("caf%C3%A9~-_.", SigV4.uriEncode("café~-_.", false));
    }
}
