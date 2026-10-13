package org.cha0scollective.wwpg.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InitializationWindowTest {
    @Test void initializationCannotConcealPersistentNonconvergence() {
        var window=new InitializationWindow();
        for(int step=0;step<16;step++)assertTrue(window.hold());
        for(int step=0;step<32;step++)assertFalse(window.hold());
    }
}
