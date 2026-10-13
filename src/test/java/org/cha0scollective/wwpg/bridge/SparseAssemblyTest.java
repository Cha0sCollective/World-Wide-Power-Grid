package org.cha0scollective.wwpg.bridge;

import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SparseAssemblyTest {
    @Test void coefficientsMatchOriginalAdditionIncludingCancellation() {
        var random = new Random(0x15_000); int n = 61;
        var original = new DMatrixRMaj(n, n); var assembly = new SparseAssembly();
        for (int i = 0; i < 12000; i++) {
            int row = random.nextInt(n), col = random.nextInt(n); double value = random.nextGaussian();
            original.add(row, col, value); assembly.add(row, col, value);
        }
        assembly.add(0, 0, 1e20); original.add(0, 0, 1e20);
        assembly.add(0, 0, -1e20); original.add(0, 0, -1e20);
        var sparse = assembly.matrix(n, n);
        for (int row = 0; row < n; row++) for (int col = 0; col < n; col++)
            assertEquals(original.get(row, col), sparse.get(row, col), 0, row + "," + col);
        assertTrue(sparse.indicesSorted);
    }
    @Test void untouchedCoefficientsAndEmptyColumnsSurviveSeededBuild() {
        var first = new SparseAssembly(); first.add(3, 1, 7); first.add(1, 5, -2);
        var next = new SparseAssembly(); next.seed(first.matrix(7, 7)); next.add(3, 1, -7);
        var actual = next.matrix(7, 7);
        assertEquals(0, actual.get(3, 1)); assertEquals(-2, actual.get(1, 5));
        assertEquals(2, actual.nz_length); assertEquals(0, actual.col_idx[0]);
        assertEquals(2, actual.col_idx[7]);
    }
}
