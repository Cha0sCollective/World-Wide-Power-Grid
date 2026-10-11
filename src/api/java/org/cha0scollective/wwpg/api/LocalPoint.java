package org.cha0scollective.wwpg.api;

/** Terminal position in block-local coordinates, already transformed for block orientation. */
public record LocalPoint(double x, double y, double z) {
    public LocalPoint {
        ApiValidation.finite(x, "terminal x");
        ApiValidation.finite(y, "terminal y");
        ApiValidation.finite(z, "terminal z");
        if (Math.abs(x) > 64 || Math.abs(y) > 64 || Math.abs(z) > 64)
            throw new IllegalArgumentException("terminal position must be within 64 blocks of its owning block");
    }
}
