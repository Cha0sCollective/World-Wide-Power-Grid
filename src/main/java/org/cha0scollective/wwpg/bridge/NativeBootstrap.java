package org.cha0scollective.wwpg.bridge;

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Restores the pinned v7 resources omitted from PG 0.6.2 before PG loads JNI. */
public final class NativeBootstrap {
    private NativeBootstrap() {}
    public static void installIfMissing() {
        String arch=System.getProperty("os.arch","").toLowerCase(Locale.ROOT);
        if(!arch.equals("amd64")&&!arch.equals("x86_64"))return;
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        String platform= os.startsWith("windows") ? "windows" : os.equals("linux") ? "linux" : null;
        if(platform==null)return;
        String name=platform.equals("windows") ? "libpowergridNative7.dll" : "libpowergridNative7.so";
        String expected=platform.equals("windows") ? "3150ba018b68ef173332a31b041b25cd8c6501394c4e9222aaa4c3ac9eeb16f4" : "c90e98869ed32ed8bff68aa5a856f4507ca09025a6152d9df718115637c8e837";
        var directory=Path.of(".pg-native").toAbsolutePath().normalize();var target=directory.resolve(name);
        if(Files.exists(target))return;
        try(var stream=NativeBootstrap.class.getResourceAsStream("/native/wwpg/"+platform+"/"+name)){
            if(stream==null)throw new IOException("Pinned native resource is missing from WWPG");
            byte[] bytes=stream.readAllBytes();
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!expected.equals(actual))throw new IOException("Pinned native resource checksum mismatch");
            Files.createDirectories(directory);
            // Publish a complete file and preserve administrator-installed binaries.
            var temporary=Files.createTempFile(directory,".wwpg-native-",".tmp");
            try{
                Files.write(temporary,bytes);
                try{Files.move(temporary,target);}catch(FileAlreadyExistsException ignored){return;}
            }finally{Files.deleteIfExists(temporary);}
            LoggerFactory.getLogger("WWPG/natives").info("Installed verified PG native v7 from WWPG: {}",name);
        }catch(IOException|NoSuchAlgorithmException e){LoggerFactory.getLogger("WWPG/natives").error("Cannot install pinned PG native v7; PG will try its normal loader",e);}
    }
}
