package org.cha0scollective.wwpg.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;

/** Applies the diagnostic only when explicitly requested on the modern contract. */
public final class CeeReferenceControlPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage){}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){
        if(!mixin.endsWith(".CeeReferencePrecisionMixin"))return true;
        if(!Boolean.getBoolean("wwpg.test.tightCeeResidual"))return false;
        if(!org.cha0scollective.wwpg.compat.cee.CeeCompatibility.modern())throw new IllegalStateException("CEE residual diagnostic requires the modern contract");
        System.getLogger("WWPG reference diagnostic").log(System.Logger.Level.INFO,"CEE_REFERENCE_RESIDUAL threshold=1.0E-16");
        return true;
    }
    public void acceptTargets(Set<String> mine,Set<String> others){}
    public List<String> getMixins(){return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
