package org.cha0scollective.wwpg.bridge;

import org.ejml.data.DMatrixSparseCSC;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SparseMatrixSupportTest {
    @Test void matchesPinnedPgEquationIncludingCouplingsZeroRowsAndScaleLimit() {
        var random = new Random(102026);
        for (int size : new int[]{7, 19, 128}) {
            var matrix = new DMatrixSparseCSC(size, size);
            for (int row = 2; row < size; row++) for (int col = 0; col < size; col++) {
                if (random.nextInt(8) == 0) matrix.set(row, col, (random.nextDouble() - .5) * 100);
            }
            matrix.set(1, 1, 1e-12);
            var columns = new double[size]; var rows = new double[size];
            SparseMatrixSupport.scales(matrix, index -> index % 11 == 0, columns, rows);
            for (int row = 0; row < size; row++) {
                double squared = 0;
                for (int col = 0; col < size; col++) { double v = Math.abs(matrix.unsafe_get(row, col)); squared += v * v; }
                double expected = row % 11 == 0 || squared == 0 ? 1 : Math.sqrt(Math.min(1 / Math.sqrt(squared), 2000));
                assertEquals(expected, rows[row]); assertEquals(expected, columns[row]);
            }
        }
    }

    @Test void handlesTenThousandRowsAndParameterChangesWithoutDenseStorage() {
        var matrix = new DMatrixSparseCSC(10_000, 10_000);
        for (int i = 0; i < 10_000; i++) matrix.set(i, i, 4);
        var columns = new double[10_000]; var rows = new double[10_000];
        SparseMatrixSupport.scales(matrix, index -> false, columns, rows);
        assertEquals(.5, rows[9999]);
        matrix.set(9999, 9999, 16);
        SparseMatrixSupport.scales(matrix, index -> false, columns, rows);
        assertEquals(.25, rows[9999]); assertEquals(10_000, matrix.nz_length);
        matrix.zero();
        SparseMatrixSupport.scales(matrix, index -> false, columns, rows);
        assertEquals(1, rows[9999]); assertEquals(0, matrix.nz_length);
    }
}
