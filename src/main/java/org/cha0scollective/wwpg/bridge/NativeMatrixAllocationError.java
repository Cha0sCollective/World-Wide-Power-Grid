package org.cha0scollective.wwpg.bridge;

import net.minecraft.resources.ResourceLocation;

/** A fatal native matrix allocation failure with the operation that failed. */
public final class NativeMatrixAllocationError extends OutOfMemoryError {
    private final String operation;
    private final int rows;
    private ResourceLocation dimension;

    public NativeMatrixAllocationError(String operation, int rows, OutOfMemoryError cause) {
        super(cause.getMessage());
        this.operation = operation;
        this.rows = rows;
        initCause(cause);
    }

    public void inDimension(ResourceLocation dimension) {
        this.dimension = dimension;
    }

    @Override
    public String getMessage() {
        return "Power Grid native matrix allocation failed (operation=" + operation
                + ", dimension=" + (dimension == null ? "unavailable" : dimension)
                + ", matrix=" + rows + "x" + rows + "): " + super.getMessage();
    }
}
