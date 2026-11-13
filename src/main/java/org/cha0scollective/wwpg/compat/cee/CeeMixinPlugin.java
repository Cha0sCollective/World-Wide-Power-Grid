package org.cha0scollective.wwpg.compat.cee;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;

public final class CeeMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) { }
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin) {
        boolean modern=CeeCompatibility.modern();
        if(mixin.endsWith(".TriPolarMeterResultsMixin"))return !modern;
        if(mixin.contains(".cee12."))return modern;
        if(mixin.endsWith(".CircuitBuilderValidationMixin"))return !modern;
        return true;
    }
    public void acceptTargets(Set<String> mine,Set<String> others) { }
    public List<String> getMixins(){return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) { }
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) { }
}
