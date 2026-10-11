package org.cha0scollective.wwpg.api;

/** An electrical node without an externally selectable wiring terminal. */
public record InternalNode(String key) {
    public InternalNode { ApiValidation.key(key, "internal node key"); }
}
