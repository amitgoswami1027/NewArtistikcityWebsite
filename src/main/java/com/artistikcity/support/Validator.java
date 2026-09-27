package com.artistikcity.support;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A small implementation of the Laravel validation rules used by this application:
 * required, email, numeric, min:n, max:n, string, confirmed, same:field, unique:table[,column].
 * Messages match Laravel's default English messages.
 */
@Component
public class Validator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final Db db;

    public Validator(Db db) {
        this.db = db;
    }

    /**
     * Validates and throws {@link ValidationException} on failure.
     *
     * @param rules pairs of field name and pipe separated rules, e.g. "email", "required|email"
     */
    public void validate(Input in, String... rules) {
        Map<String, String> errors = check(in, rules);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    public Map<String, String> check(Input in, String... rules) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (int i = 0; i + 1 < rules.length; i += 2) {
            String field = rules[i];
            String message = checkField(in, field, rules[i + 1].split("\\|"));
            if (message != null) {
                errors.put(field, message);
            }
        }
        return errors;
    }

    private String checkField(Input in, String field, String[] rules) {
        String attr = field.replace('_', ' ');
        Object raw = in.raw(field);
        String value = in.get(field);
        boolean present = in.filled(field) || in.hasFile(field);
        for (String rule : rules) {
            String name = rule;
            String arg = null;
            int colon = rule.indexOf(':');
            if (colon > 0) {
                name = rule.substring(0, colon);
                arg = rule.substring(colon + 1);
            }
            switch (name) {
                case "required":
                    if (!present) {
                        return "The " + attr + " field is required.";
                    }
                    break;
                case "email":
                    if (present && (value == null || !EMAIL.matcher(value).matches())) {
                        return "The " + attr + " must be a valid email address.";
                    }
                    break;
                case "numeric":
                    if (present && Values.toDouble(raw) == null) {
                        return "The " + attr + " must be a number.";
                    }
                    break;
                case "string":
                    if (present && !(raw instanceof String)) {
                        return "The " + attr + " must be a string.";
                    }
                    break;
                case "min":
                    if (present && value != null && value.length() < Integer.parseInt(arg)) {
                        return "The " + attr + " must be at least " + arg + " characters.";
                    }
                    break;
                case "max":
                    if (present && value != null && value.length() > Integer.parseInt(arg)) {
                        return "The " + attr + " must not be greater than " + arg + " characters.";
                    }
                    break;
                case "confirmed": {
                    String confirmation = in.get(field + "_confirmation");
                    if (present && !java.util.Objects.equals(value, confirmation)) {
                        return "The " + attr + " confirmation does not match.";
                    }
                    break;
                }
                case "same": {
                    String other = in.get(arg);
                    if (!java.util.Objects.equals(value, other)) {
                        return "The " + attr + " and " + arg.replace('_', ' ') + " must match.";
                    }
                    break;
                }
                case "unique": {
                    if (present) {
                        String[] parts = arg.split(",");
                        String table = parts[0];
                        String column = parts.length > 1 ? parts[1] : field;
                        if (!table.matches("[a-z_]+") || !column.matches("[a-z_]+")) {
                            throw new IllegalArgumentException("Bad unique rule " + arg);
                        }
                        long n = db.count("select count(*) as c from " + table + " where " + column + " = ?", value);
                        if (n > 0) {
                            return "The " + attr + " has already been taken.";
                        }
                    }
                    break;
                }
                default:
                    // "nullable", "sometimes" and unknown rules are ignored
                    break;
            }
        }
        return null;
    }
}
