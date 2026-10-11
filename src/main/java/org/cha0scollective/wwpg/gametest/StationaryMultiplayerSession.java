package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Additional opt-in TCP acceptance in a fresh extraction of the exact yard ZIP. */
final class StationaryMultiplayerSession {
    static final BlockPos TABLE=new BlockPos(159,64,224), BOARD=TABLE.east(4);
    private final net.minecraft.gametest.framework.GameTestHelper h;
    private final List<ServerPlayer> players;
    private final boolean pinout;
    private final CompoundTag expectedDesign;
    private final Set<UUID> boardIds;
    private final int expectedSchematicCount;
    private Set<UUID> loadedDesignIds;
    private final long started;
    private int stage;
    private String phase="";

    StationaryMultiplayerSession(net.minecraft.gametest.framework.GameTestHelper h,List<ServerPlayer> players) {
        this.h=h;this.players=List.copyOf(players);this.started=h.getLevel().getGameTime();
        h.assertTrue(Files.isRegularFile(Path.of("world/WWPG_MANIFEST.json")),"Stationary multiplayer requires a fresh exported ZIP extraction");
        pinout=net.neoforged.fml.ModList.get().isLoaded("pinout");
        for(int x=0;x<=17;x++)for(int z=0;z<=20;z++){h.getLevel().setChunkForced(x,z,true);h.getLevel().getChunk(x,z);}
        var table=(CircuitDesignTableBlockEntity)h.getLevel().getBlockEntity(TABLE);
        var design=CircuitSchematic.fromStack(h.getLevel().registryAccess(),table.getInventory().getItem(2));
        h.assertTrue(design.components().size()==5,"Saved multiplayer design item is missing");
        // Native item copies intentionally omit UUIDs and allocate fresh identities
        // on load. Compare their safe description, then retain the loaded IDs.
        expectedDesign=design.serializeSafeNbt(h.getLevel().registryAccess());expectedDesign.remove("Name");
        int count=0;for(int slot=0;slot<table.getInventory().getContainerSize();slot++) {
            var stack=table.getInventory().getItem(slot);
            if(stack.is(org.patryk3211.powergrid.collections.ModdedItems.CIRCUIT_SCHEMATIC.get()))count+=stack.getCount();
        }
        expectedSchematicCount=count;
        boardIds=((CircuitBoardBlockEntity)h.getLevel().getBlockEntity(BOARD)).getComponentsStream().map(c->c.uuid).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    boolean tick() {
        var level=h.getLevel();
        h.assertTrue(level.getGameTime()-started<4000,"Stationary multiplayer client interactions timed out in "+phase);
        if(stage==1&&loadedDesignIds==null) {
            var design=((CircuitDesignTableBlockEntity)level.getBlockEntity(TABLE)).getSchematic();
            if(design.components().size()==5)loadedDesignIds=design.components().stream().map(c->c.uuid).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        if(stage==0) {
            for(var pos:List.of(TABLE,BOARD,pins(true),pins(false)))
                if(!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4))||!level.isPositionEntityTicking(pos))return false;
            for(var player:players){player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.inventoryMenu.broadcastChanges();
                player.teleportTo(level,160.5,64,227.5,180,25);}
            announce("stationary-design-a");stage=1;
        } else if(stage==1&&MultiplayerGameTests.acked(phase,players)) {
            verifyDesign("WWPG multiplayer A");announce("stationary-design-b");stage=2;
        } else if(stage==2&&MultiplayerGameTests.acked(phase,players)) {
            verifyDesign("WWPG multiplayer B");
            if(!pinout){passed();return true;}
            for(var player:players){var computer=pins(player.getGameProfile().getName().endsWith("A")).west();
                player.teleportTo(level,computer.getX()+.5,64,computer.getZ()+3,180,25);}
            announce("stationary-pinout-stop");stage=3;
        } else if(stage==3&&MultiplayerGameTests.acked(phase,players)) {
            verifyPins(0);announce("stationary-pinout-pattern");stage=4;
        } else if(stage==4&&MultiplayerGameTests.acked(phase,players)) {
            verifyPins(85);announce("stationary-pinout-volts");stage=5;
        } else if(stage==5&&MultiplayerGameTests.acked(phase,players)) {
            verifyPins(85);announce("stationary-pinout-start");stage=6;
        } else if(stage==6&&MultiplayerGameTests.acked(phase,players)) {
            verifyPins(255);
            passed();
            return true;
        }
        return false;
    }
    private void announce(String phase){this.phase=phase;MultiplayerGameTests.announce(players,phase);}
    private void passed(){org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("STATIONARY_TWO_REAL_CLIENTS_PASSED: players={}; profile={}; backend={}; native design load/editor/save; optional Lua={}",players.stream().map(p->p.getGameProfile().getName()).toList(),pinout?"PINOUT":"BASE",System.getProperty("wwpg.test.backend"),pinout);}
    private void verifyDesign(String name) {
        var level=h.getLevel();var table=(CircuitDesignTableBlockEntity)level.getBlockEntity(TABLE);
        var actual=table.getSchematic().serializeSafeNbt(level.registryAccess());actual.remove("Name");
        h.assertTrue(actual.equals(expectedDesign)&&table.getSchematicName().equals(name),"Real-client native editor/save altered components, settings, traces, vias or labels");
        h.assertTrue(loadedDesignIds!=null&&table.getSchematic().components().stream().map(c->c.uuid).collect(java.util.stream.Collectors.toSet()).equals(loadedDesignIds),"Native real-client editing replaced loaded design identities");
        int schematics=0,saved=0;
        for(var player:players)for(var item:player.getInventory().items)if(item.is(org.patryk3211.powergrid.collections.ModdedItems.CIRCUIT_SCHEMATIC.get())) {
            schematics+=item.getCount();
            if(item.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
                var design=CircuitSchematic.fromStack(level.registryAccess(),item).serializeSafeNbt(level.registryAccess());design.remove("Name");
                if(design.equals(expectedDesign))saved+=item.getCount();
            }
        }
        h.assertTrue(schematics==expectedSchematicCount&&saved==1,"Native menu closure lost or duplicated the blank/saved items: total="+schematics+", expected="+expectedSchematicCount+", saved="+saved);
        h.assertTrue(((CircuitBoardBlockEntity)level.getBlockEntity(BOARD)).getComponentsStream().map(c->c.uuid).collect(java.util.stream.Collectors.toSet()).equals(boardIds),"Real-client editing replaced powered board identities");
        BoardComponentGameTests.near(h,ShowroomTools.volts(h,BOARD,0,1),10,.01,"Real-client saved board remains powered");
        org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("STATIONARY_REAL_CLIENT_DESIGN_SAVED: {}",name);
    }
    private void verifyPins(int pattern) {
        var level=h.getLevel();
        for(boolean cee:List.of(true,false)) {
            var pos=pins(cee);var tag=level.getBlockEntity(pos).saveWithoutMetadata(level.registryAccess());
            for(int pin=1;pin<=8;pin++) {
                boolean on=(pattern&(1<<(pin-1)))!=0;
                h.assertTrue(tag.getBoolean("Pin"+pin)==on,"Native Lua pin state mismatch: "+cee+" pin "+pin);
                var lamp=load(pos,pin);float light=level.getBlockEntity(lamp).saveWithoutMetadata(level.registryAccess()).getFloat("FirstLight");
                h.assertTrue(on?light>.95:light<.01,"Native Lua lamp did not respond: "+cee+" pin "+pin);
                BoardComponentGameTests.near(h,ShowroomTools.volts(h,lamp,0,1),on?70:0,.2,"Actual Lua controlled load");
            }
        }
        org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("STATIONARY_REAL_CLIENT_LUA_PATTERN: {} independently verified on both supplies",pattern);
    }
    static BlockPos pins(boolean cee){return cee?new BlockPos(263,64,278):new BlockPos(159,64,296);}
    static BlockPos load(BlockPos pins,int pin){return pins.offset(5+(pin-1)%2*4,0,(pin-1)/2*2);}
}
