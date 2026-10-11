package org.cha0scollective.wwpg.extension;

import org.cha0scollective.wwpg.api.DeviceResults;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.TickContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiDiagnosticsTest {
    @Test void largeProviderFailureRemainsAnUnavailableResultWithItsIdentity() {
        String identity="minecraft:overworld 1, 64, 2 provider=example:power: ";
        var detail=ApiDiagnostics.detail(identity+"NoClassDefFoundError: "+"x".repeat(4096));
        var result=assertDoesNotThrow(()->DeviceResults.unavailable(new TickContext(1,.05,16),MeasurementStatus.ISOLATED,detail));
        assertTrue(result.detail().startsWith(identity));
        assertEquals(1024,result.detail().length());
        assertFalse(result.detail().isBlank());
        assertTrue(result.nodes().isEmpty());
        assertTrue(result.elements().isEmpty());
    }

    @Test void ordinaryProviderDiagnosticsArePreserved() {
        String diagnostic="example:power: terminal declaration failed";
        assertEquals(diagnostic,ApiDiagnostics.detail(diagnostic));
    }
}
