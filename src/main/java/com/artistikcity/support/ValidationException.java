package com.artistikcity.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** Thrown when request validation fails; handled globally like Laravel's ValidationException. */
public class ValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super("The given data was invalid.");
        this.errors = new LinkedHashMap<>(errors);
    }

    public static ValidationException withMessage(String field, String message) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(field, message);
        return new ValidationException(m);
    }

    /** field -> first error message. */
    public Map<String, String> getErrors() {
        return errors;
    }
}
