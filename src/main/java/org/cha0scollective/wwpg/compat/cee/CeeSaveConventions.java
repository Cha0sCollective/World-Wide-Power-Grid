package org.cha0scollective.wwpg.compat.cee;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import java.nio.file.*;
import java.util.*;
import static org.cha0scollective.wwpg.compat.cee.CeeSavedCurrent.Convention;

/** Keep the origin for unmarked histories after NeoForge updates its loaded-mod list. */
public final class CeeSaveConventions {
    private static final String ROOT="wwpgCEEHistory";
    private static final Map<Path,Convention> ORIGINS=new HashMap<>();
    private CeeSaveConventions() { }
    public static Convention current(){return CeeCompatibility.modern()?Convention.MODERN:Convention.LEGACY;}
    public static Convention rootSource(CompoundTag root){
        if(root.contains(ROOT,Tag.TAG_COMPOUND))return Convention.marker(root.getCompound(ROOT).getString("unmarkedConvention"));
        var mods=root.getCompound("fml").getList("LoadingModList",Tag.TAG_COMPOUND);
        for(int i=0;i<mods.size();i++)if(mods.getCompound(i).getString("ModId").equals("electroenergetics"))
            return Convention.version(mods.getCompound(i).getString("ModVersion"));
        return Convention.UNKNOWN;
    }
    public static void markRoot(CompoundTag root,Convention origin){
        var owned=root.getCompound(ROOT);owned.putString("unmarkedConvention",origin.marker);root.put(ROOT,owned);
    }
    private static Path key(Path root){return root.toAbsolutePath().normalize();}
    public static synchronized void loaded(CompoundTag root,Path directory){ORIGINS.put(key(directory),rootSource(root));}
    private static synchronized Convention source(Path directory){return ORIGINS.computeIfAbsent(key(directory),path->{
        var file=path.resolve("level.dat");
        if(!Files.exists(file))return current();
        try{return rootSource(NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap()));}
        catch(java.io.IOException error){WorldWidePowerGrid.LOGGER.warn("Could not determine saved CEE history convention in {}",file,error);return Convention.UNKNOWN;}
    });}
    public static Convention source(Level level){return level instanceof ServerLevel server?source(server.getServer().getWorldPath(LevelResource.ROOT)):Convention.UNKNOWN;}
    public static void saving(CompoundTag root){
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server!=null)markRoot(root,source(server.getWorldPath(LevelResource.ROOT)));
    }
    public static synchronized void clear(){ORIGINS.clear();}
}
