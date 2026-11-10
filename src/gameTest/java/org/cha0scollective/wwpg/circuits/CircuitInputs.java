package org.cha0scollective.wwpg.circuits;

import java.io.*;
import net.minecraft.nbt.*;

/** The fixture jar carries immutable circuit inputs; player jars contain none. */
final class CircuitInputs {
    static InputStream open(String file) throws IOException {
        var input=CircuitInputs.class.getResourceAsStream("/data/wwpg_circuits/boards/"+file);
        if(input==null) throw new FileNotFoundException("Circuit fixture: "+file);
        return input;
    }
    static CompoundTag read(String file) throws IOException {
        try(var input=open(file)){return NbtIo.readCompressed(input,NbtAccounter.unlimitedHeap());}
    }
}
