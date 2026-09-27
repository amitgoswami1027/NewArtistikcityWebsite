package com.artistikcity.marketplace;

import java.util.LinkedHashMap;
import java.util.Map;

/** A business-rule refusal with an HTTP status, a machine-readable code and a message a collector can act on. */
public class MarketplaceException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, Object> extra;

    public MarketplaceException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public MarketplaceException(int status, String code, String message, Map<String, Object> extra) {
        super(message);
        this.status = status;
        this.code = code;
        this.extra = extra;
    }

    public int status() {
        return status;
    }

    public Map<String, Object> body() {
        Map<String, Object> m = new LinkedHashMap<>(extra);
        m.put("code", code);
        m.put("message", getMessage());
        return m;
    }
}
