package com.artistikcity.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** JSON encode / decode (PHP json_encode / json_decode equivalents). */
@Component
public class Json {

    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String encode(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON encode failed", e);
        }
    }

    /** json_decode($s, true): returns Map, List, String, Number, Boolean or null (null on invalid JSON). */
    public Object decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> decodeMap(String json) {
        Object o = decode(json);
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public List<Object> decodeList(String json) {
        Object o = decode(json);
        return o instanceof List ? (List<Object>) o : null;
    }
}
