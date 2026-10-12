package org.cha0scollective.wwpg.api;

import java.util.Objects;

/**
 * Independently identified electrical element. id is a stable local identity;
 * two elements may have identical endpoints and models with different IDs.
 * Changing endpoint orientation is a structural change, not an ID migration.
 */
public record Element(String id, String positiveNode, String negativeNode, ElectricalModel model) {
    /**
     * Constructs Element with validated captured values.
     * @param id stable local key, 1..128 characters matching [a-z0-9_.:/-]+
     * @param positiveNode declared local key; different from negativeNode
     * @param negativeNode declared local key; different from positiveNode
     * @param model non-null captured electrical model
     * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
     * @throws NullPointerException if a required reference or collection entry is null
     */
    public Element {
        ApiValidation.key(id, "element id");
        ApiValidation.key(positiveNode, "element " + id + " positiveNode");
        ApiValidation.key(negativeNode, "element " + id + " negativeNode");
        Objects.requireNonNull(model, "element " + id + " model");
        if (positiveNode.equals(negativeNode))
            throw new IllegalArgumentException("element " + id + " endpoints must differ");
    }
}
