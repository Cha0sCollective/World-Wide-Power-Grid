package org.cha0scollective.wwpg.bridge;

import org.ejml.data.DMatrixSparseCSC;
import java.util.function.IntPredicate;

/** PG's scaling equation, visiting stored entries in ascending column order. */
public final class SparseMatrixSupport {
    private SparseMatrixSupport() {}

    public static void scales(DMatrixSparseCSC matrix, IntPredicate coupling,
                              double[] columns, double[] rows) {
        java.util.Arrays.fill(rows, 0);
        for (int col = 0; col < matrix.numCols; col++) {
            for (int index = matrix.col_idx[col]; index < matrix.col_idx[col + 1]; index++) {
                double value = Math.abs(matrix.nz_values[index]);
                rows[matrix.nz_rows[index]] += value * value;
            }
        }
        for (int row = 0; row < matrix.numRows; row++) {
            double squaredNorm = rows[row];
            columns[row] = rows[row] = coupling.test(row) || squaredNorm == 0 ? 1 :
                    Math.sqrt(Math.min(1.0 / Math.sqrt(squaredNorm), 2000));
        }
    }
}
