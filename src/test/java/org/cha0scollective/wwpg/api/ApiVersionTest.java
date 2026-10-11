package org.cha0scollective.wwpg.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiVersionTest {
    @Test void declaredArtifactVersionMatchesTheRuntimeContract() {
        String expected = System.getProperty("wwpg.test.apiVersion");
        assertNotNull(expected, "Build must supply its independent API artifact version");
        assertEquals(expected, WWPGApi.version());
        assertEquals(expected, WWPGApi.API_VERSION);
    }
}
