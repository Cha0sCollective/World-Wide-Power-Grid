package org.cha0scollective.wwpg.bridge;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.ejml.data.DMatrixSparseCSC;
import java.util.Arrays;

/** Full-build coefficients, accumulated in original order and emitted in CSC order. */
public final class SparseAssembly {
    private final Long2DoubleOpenHashMap values = new Long2DoubleOpenHashMap();
    private final Long2DoubleOpenHashMap first = new Long2DoubleOpenHashMap();
    public interface EntryConsumer { void accept(int row, int column, double value, double first); }
    public void add(int row, int column, double value) {
        if (row < 0 || column < 0) throw new IllegalArgumentException("Negative matrix coordinate");
        long key = ((long) column << 32) | row;
        if (!values.containsKey(key)) first.put(key, value);
        values.addTo(key, value);
    }
    public void seed(DMatrixSparseCSC matrix) {
        for (int col = 0; col < matrix.numCols; col++)
            for (int index = matrix.col_idx[col]; index < matrix.col_idx[col + 1]; index++)
                add(matrix.nz_rows[index], col, matrix.nz_values[index]);
    }
    public void ordered(EntryConsumer consumer) {
        long[] keys = values.keySet().toLongArray(); Arrays.sort(keys);
        for (long key : keys) consumer.accept((int) key, (int) (key >>> 32), values.get(key), first.get(key));
    }
    public DMatrixSparseCSC matrix(int rows, int columns) {
        var matrix = new DMatrixSparseCSC(rows, columns, values.size());
        ordered((row, col, value, ignored) -> {
            if (row >= rows || col >= columns) throw new IllegalArgumentException("Matrix coordinate outside dimensions");
            int index = matrix.nz_length++;
            matrix.nz_rows[index] = row; matrix.nz_values[index] = value;
            matrix.col_idx[col + 1]++;
        });
        for (int col = 1; col <= columns; col++) matrix.col_idx[col] += matrix.col_idx[col - 1];
        matrix.indicesSorted = true;
        return matrix;
    }
}
