package org.cha0scollective.wwpg.bridge;

import org.cha0scollective.wwpg.mixin.PgNativeStateAccessor;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class NativeStateReadTest {
    private static final class State implements PgNativeStateAccessor {
        ByteBuffer buffer;
        int rows;
        @Override public ByteBuffer wwpg$stateBuffer() { return buffer; }
        @Override public int wwpg$stateSize() { return rows; }
    }

    @Test void missingUnallocatedAndShortBuffersRemainUnavailable() {
        var state = new State(); state.rows = 3;
        assertEquals(0, NativeStateRead.assignedRow(state, 0));
        state.buffer = ByteBuffer.allocateDirect(0);
        assertEquals(0, NativeStateRead.assignedRow(state, 0));
        state.buffer = ByteBuffer.allocateDirect(16);
        state.buffer.putDouble(0, 12); state.buffer.putDouble(8, 34);
        state.rows = 1;
        assertEquals(12, NativeStateRead.assignedRow(state, 0));
        assertEquals(0, NativeStateRead.assignedRow(state, 1), "Allocated bytes beyond matrix rows are not state");
        state.rows = 3;
        assertEquals(0, NativeStateRead.assignedRow(state, 2), "Rows beyond buffer capacity are unavailable");
    }

    @Test void everyReadUsesTheCurrentBufferAndCurrentValue() {
        var state = new State(); state.rows = 2;
        for (var order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
            state.buffer = ByteBuffer.allocateDirect(16).order(order);
            for (double value : new double[]{0.0, -0.0, 10, -10, Double.MIN_VALUE, Double.MAX_VALUE,
                    Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.longBitsToDouble(0x7ff8000000001234L)}) {
                state.buffer.putDouble(8, value);
                assertEquals(Double.doubleToRawLongBits(value), Double.doubleToRawLongBits(NativeStateRead.assignedRow(state, 1)));
            }
        }
        state.buffer = null;
        assertEquals(0, NativeStateRead.assignedRow(state, 0), "Cleanup cannot expose an earlier buffer");
        state.buffer = ByteBuffer.allocateDirect(8); state.rows = 1;
        state.buffer.putDouble(0, 99);
        assertEquals(99, NativeStateRead.assignedRow(state, 0), "Replacement buffer is read immediately");
    }

    @Test void byteBufferStillChecksLimitsAndInvalidOffsets() {
        var state = new State(); state.rows = 2; state.buffer = ByteBuffer.allocateDirect(16);
        assertThrows(IndexOutOfBoundsException.class, () -> NativeStateRead.assignedRow(state, -1));
        state.buffer.limit(9);
        assertThrows(IndexOutOfBoundsException.class, () -> NativeStateRead.assignedRow(state, 1));
        state.rows = Integer.MAX_VALUE;
        assertThrows(IndexOutOfBoundsException.class, () -> NativeStateRead.assignedRow(state, Integer.MAX_VALUE - 1));
    }
}
