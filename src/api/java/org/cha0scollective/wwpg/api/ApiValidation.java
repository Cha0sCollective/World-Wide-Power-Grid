package org.cha0scollective.wwpg.api;

import java.util.Objects;

final class ApiValidation {
    private ApiValidation() { }

    static String key(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() > 128 || !value.matches("[a-z0-9_.:/-]+"))
            throw new IllegalArgumentException(field + " must be a stable lowercase key (1..128 characters): " + abbreviated(value));
        return value;
    }

    static String resourceId(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() > 256 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException(field + " must be a namespaced lowercase ID: " + abbreviated(value));
        return value;
    }

    static double finite(double value, String field) {
        if (!Double.isFinite(value) || Math.abs(value) > 1e15)
            throw new IllegalArgumentException(field + " must be finite and have magnitude at most 1e15: " + value);
        return value;
    }

    static double positive(double value, String field) {
        finite(value, field);
        if (value < 1e-12)
            throw new IllegalArgumentException(field + " must be at least 1e-12: " + value);
        return value;
    }

    static double nonnegative(double value, String field) {
        finite(value, field);
        if (value < 0) throw new IllegalArgumentException(field + " must be nonnegative: " + value);
        return value;
    }

    static String detail(String value) {
        Objects.requireNonNull(value, "detail");
        if (value.length() > 1024) throw new IllegalArgumentException("detail must contain at most 1024 characters");
        return value;
    }

    private static String abbreviated(String value) {
        return value.length() <= 160 ? value : value.substring(0, 160) + "...";
    }
}
