package com.artistikcity.http;

/** Thrown for missing records (Laravel's ModelNotFoundException / abort(404)). */
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Not Found");
    }

    public NotFoundException(String message) {
        super(message);
    }

    /** Returns the value or throws 404 (findOrFail). */
    public static <T> T orFail(T value) {
        if (value == null) {
            throw new NotFoundException();
        }
        return value;
    }
}
