package org.cha0scollective.wwpg.bridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class NativeBootstrapTest {
    @TempDir Path root;
    final byte[] bytes = {1, 2, 3, 4};
    String hash() throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }

    @Test void oldAdministratorLibraryIsPreservedAndNeverSelected() throws Exception {
        Path old = root.resolve("libpowergridNative7.dll"); Files.writeString(old, "old administrator binary");
        Path installed = NativeBootstrap.install(new ByteArrayInputStream(bytes), hash(), root.resolve("wwpg-safety-1"), old.getFileName().toString());
        assertArrayEquals(bytes, Files.readAllBytes(installed));
        assertNotEquals(old, installed);
        assertEquals("old administrator binary", Files.readString(old));
        assertEquals(installed, NativeBootstrap.install(new ByteArrayInputStream(bytes), hash(), root.resolve("wwpg-safety-1"), old.getFileName().toString()));
    }

    @Test void corruptedExistingExtractionIsRejectedAndPreserved() throws Exception {
        Path installed = NativeBootstrap.install(new ByteArrayInputStream(bytes), hash(), root, "test.dll");
        Files.writeString(installed, "corrupt");
        assertThrows(java.io.IOException.class, () -> NativeBootstrap.install(new ByteArrayInputStream(bytes), hash(), root, "test.dll"));
        assertEquals("corrupt", Files.readString(installed));
    }

    @Test void missingOrMismatchedResourcesCannotSelectOldBinary() throws Exception {
        assertThrows(java.io.IOException.class, () -> NativeBootstrap.install(null, hash(), root, "test.dll"));
        assertThrows(java.io.IOException.class, () -> NativeBootstrap.install(new ByteArrayInputStream(bytes), "0".repeat(64), root, "test.dll"));
        assertThrows(java.io.IOException.class, () -> NativeBootstrap.install(new ByteArrayInputStream(bytes), "../bad", root, "test.dll"));
        assertFalse(Files.exists(root.resolve("test.dll")));
    }
}
