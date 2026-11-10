package org.cha0scollective.wwpg.circuits;
import java.nio.file.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;

/** Only loads the already-published exact board bytes. Generates no new circuits. */
final class LibraryBoards {
    static CircuitSchematic load(GameTestHelper h,String name){
        try(var input=CircuitInputs.open(name+".nbt")){
            var tag=NbtIo.readCompressed(input,NbtAccounter.unlimitedHeap());
            var board=CircuitSchematic.fromNbt(h.getLevel().registryAccess(),tag);
            h.assertTrue(board.components().size()==tag.getList("Components",10).size(),"Dropped schematic components: "+name);
            return board;
        }catch(Exception e){throw new RuntimeException(e);}
    }
}
