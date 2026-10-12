package org.cha0scollective.wwpg.api;

/** An electrical node without an externally selectable wiring terminal. */
public record InternalNode(String key) {
    /**
     * Constructs InternalNode with validated captured values.
     * @param key stable local key, 1..128 characters matching [a-z0-9_.:/-]+
     * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
     * @throws NullPointerException if a required reference or collection entry is null
     */
    public InternalNode { ApiValidation.key(key, "internal node key"); }
}
