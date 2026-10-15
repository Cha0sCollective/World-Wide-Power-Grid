package org.cha0scollective.wwpg.bridge;

import org.cha0scollective.wwpg.mixin.PgNativeStateAccessor;

/** Checked column-zero reads; no retained buffer or value cache. */
public final class NativeStateRead {
    private NativeStateRead() { }

    public static double assignedRow(PgNativeStateAccessor state, int row) {
        var buffer = state.wwpg$stateBuffer();
        if (buffer == null) return 0;
        int offset = row * Double.BYTES;
        // Preserve StateAccess.safe_get's capacity and row checks, including
        // ByteBuffer's own limit/offset exceptions. This caller always uses col0.
        if (offset >= buffer.capacity() || row >= state.wwpg$stateSize()) return 0;
        return buffer.getDouble(offset);
    }
}
