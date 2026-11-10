package org.cha0scollective.wwpg.circuits;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import java.util.concurrent.ConcurrentHashMap;
/** Keep GameTest/Player class initialization out of parallel mod construction. */

public final class QuietSoundObserver {
    static final ConcurrentHashMap<String,Integer> sounds=new ConcurrentHashMap<>();
    public QuietSoundObserver(){NeoForge.EVENT_BUS.addListener(this::sound);}
    private void sound(PlayLevelSoundEvent.AtPosition event){
        if(event.getLevel().isClientSide)return;
        var id=net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(event.getSound().value());
        sounds.merge(String.valueOf(id),1,Integer::sum);
    }
}
