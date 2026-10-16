package org.cha0scollective.wwpg.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;

/** Installs the guarded PG build without replacing an administrator's v7 library. */
public final class NativeBootstrap {
    private NativeBootstrap() {}

    public static Path installGuarded() throws IOException {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!arch.equals("amd64") && !arch.equals("x86_64")) return null;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String platform = os.startsWith("windows") ? "windows" : os.equals("linux") ? "linux" : null;
        if (platform == null) return null;
        String name = platform.equals("windows") ? "libpowergridNative7.dll" : "libpowergridNative7.so";
        var manifest = new Properties();
        try (var stream = NativeBootstrap.class.getResourceAsStream("/native/wwpg/safe/guarded.properties")) {
            if (stream == null) throw new IOException("Guarded native manifest is missing");
            manifest.load(stream);
        }
        String expected = manifest.getProperty(platform + ".sha256", "");
        try (var stream = NativeBootstrap.class.getResourceAsStream("/native/wwpg/safe/" + platform + "/" + name)) {
            return install(stream, expected, Path.of(".pg-native", "wwpg-safety-1"), name);
        }
    }

    static Path install(InputStream stream, String expected, Path root, String name) throws IOException {
        if (!expected.matches("[0-9a-f]{64}")) throw new IOException("Invalid guarded native checksum");
        if (stream == null) throw new IOException("Guarded native resource is missing");
        byte[] bytes = stream.readAllBytes();
        if (!expected.equals(checksum(bytes))) throw new IOException("Guarded native resource checksum mismatch");
        var directory = root.toAbsolutePath().normalize().resolve(expected);
        Files.createDirectories(directory);
        var target = directory.resolve(name);
        if (!Files.exists(target)) {
            var temporary = Files.createTempFile(directory, ".wwpg-native-", ".tmp");
            try {
                Files.write(temporary, bytes);
                try { Files.move(temporary, target); }
                catch (FileAlreadyExistsException ignored) { /* Verify the other writer's result below. */ }
            } finally { Files.deleteIfExists(temporary); }
        }
        if (!expected.equals(checksum(Files.readAllBytes(target))))
            throw new IOException("Installed guarded native checksum mismatch: " + target);
        return target;
    }

    private static String checksum(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
