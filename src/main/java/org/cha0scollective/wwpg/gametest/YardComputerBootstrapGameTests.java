package org.cha0scollective.wwpg.gametest;

import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity;
import dan200.computercraft.shared.computer.core.ServerComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/** Causal native turn-on control plus a delayed, state-preserving fresh boot. */
@GameTestHolder("wwpg_pinout")
@PrefixGameTestTemplate(false)
public final class YardComputerBootstrapGameTests {
    @GameTest(template="empty",timeoutTicks=1800)
    public static void completedComputerNeedsRebootAndDelayedLuaCompletion(GameTestHelper h) {
        var local=new BlockPos(2,2,2);var pc=h.absolutePos(local);var pin=pc.east();
        h.setBlock(local.below(),Blocks.STONE);h.setBlock(local.east().below(),Blocks.STONE);
        h.setBlock(local,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("computercraft:computer_normal")));
        h.setBlock(local.east(),BuiltInRegistries.BLOCK.get(ResourceLocation.parse("pinout:pinout")));
        var computer=new ServerComputer[1];var mount=new WritableMount[1];var restarted=new long[1];
        h.startSequence().thenWaitUntil(()->h.assertTrue(h.getLevel().isPositionEntityTicking(pc)&&h.getLevel().areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(pc.getX()>>4,pc.getZ()>>4)),"Waiting for native computer readiness"))
          .thenExecute(()->{
            computer[0]=((AbstractComputerBlockEntity)h.getLevel().getBlockEntity(pc)).createServerComputer();mount[0]=computer[0].createRootMount();
            String program="""
                local p=assert(peripheral.find("pinout"))
                local count=0
                if fs.exists("boots.txt") then local f=fs.open("boots.txt","r");count=tonumber(f.readAll());f.close() end
                count=count+1
                if count==1 then p.setByte(170) end
                local f=assert(fs.open("boots.txt","w"));f.write(tostring(count));f.close()
                if count>1 then sleep(20) end
                f=assert(fs.open("yard-ready.pending.json","w"));f.write('{"ok":true}');f.close()
                if fs.exists("yard-ready.json") then fs.delete("yard-ready.json") end
                fs.move("yard-ready.pending.json","yard-ready.json")
                """;
            io(()->{try(var channel=mount[0].openFile("startup.lua",Set.of(StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING))){var data=ByteBuffer.wrap(program.getBytes(StandardCharsets.UTF_8));while(data.hasRemaining())channel.write(data);}});
            io(()->YardComputerBootstrap.start(computer[0],mount[0],"warm-control-first",h.getLevel().getGameTime()));
          }).thenWaitUntil(()->h.assertTrue(exists(mount[0],"yard-ready.json"),"Waiting for first completed native startup"))
          .thenExecute(()->{
            h.assertTrue(computer[0].isOn()&&read(mount[0],"boots.txt").equals("1"),"First native startup did not finish");pins(h,pin);
            io(()->mount[0].delete("yard-ready.json"));computer[0].turnOn();
          }).thenIdle(80).thenExecute(()->{
            h.assertTrue(!exists(mount[0],"yard-ready.json")&&read(mount[0],"boots.txt").equals("1"),"Native turnOn unexpectedly restarted the completed shell");
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("YARD_LEGACY_TURN_ON_CONTROL: completed startup -> delete marker -> turnOn; boot count=1 and no new completion");
            restarted[0]=h.getLevel().getGameTime();io(()->YardComputerBootstrap.start(computer[0],mount[0],"warm-control-restart",restarted[0]));
          }).thenWaitUntil(()->h.assertTrue(exists(mount[0],"yard-ready.json"),"Waiting for delayed native Lua completion"))
          .thenExecute(()->{
            h.assertTrue(read(mount[0],"boots.txt").equals("2"),"Corrected bootstrap did not make one fresh native boot");
            h.assertTrue(h.getLevel().getGameTime()-restarted[0]>=400,"Delayed completion was accepted early");pins(h,pin);
            h.assertTrue(read(mount[0],"yard-ready.json").equals("{\"ok\":true}"),"Atomic native completion report is incomplete");
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("YARD_DELAYED_NATIVE_BOOT_PASSED: fresh boot=2; elapsedTicks={}; all eight saved pins unchanged",h.getLevel().getGameTime()-restarted[0]);
          }).thenSucceed();
    }
    private static void pins(GameTestHelper h,BlockPos pos){var tag=h.getLevel().getBlockEntity(pos).saveWithoutMetadata(h.getLevel().registryAccess());for(int pin=1;pin<=8;pin++)h.assertTrue(tag.getBoolean("Pin"+pin)==((170&(1<<(pin-1)))!=0),"Native bootstrap changed pin "+pin);}
    private interface Action {void run()throws java.io.IOException;}
    private static void io(Action action){try{action.run();}catch(java.io.IOException error){throw new IllegalStateException(error);}}
    private static boolean exists(WritableMount mount,String name){try{return mount.exists(name);}catch(java.io.IOException error){throw new IllegalStateException(error);}}
    private static String read(WritableMount mount,String name){try(var channel=mount.openForRead(name)){var data=ByteBuffer.allocate((int)mount.getSize(name));while(data.hasRemaining()&&channel.read(data)!=-1){}return new String(data.array(),StandardCharsets.UTF_8);}catch(java.io.IOException error){throw new IllegalStateException(error);}}
}
