package org.cha0scollective.wwpg.api;

import java.util.Objects;

/**
 * Independently identified electrical element. id is a stable local identity;
 * two elements may have identical endpoints and models with different IDs.
 * Changing endpoint orientation is a structural change, not an ID migration.
 */
public record Element(String id, String positiveNode, String negativeNode, ElectricalModel model) {
    public Element {
        ApiValidation.key(id, "element id");
        ApiValidation.key(positiveNode, "element " + id + " positiveNode");
        ApiValidation.key(negativeNode, "element " + id + " negativeNode");
        Objects.requireNonNull(model, "element " + id + " model");
        if (positiveNode.equals(negativeNode))
            throw new IllegalArgumentException("element " + id + " endpoints must differ");
    }
}
