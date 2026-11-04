package org.cha0scollective.wwpg.compat.cio;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Optional targets are absent in ordinary installations; private history is version scoped. */
public final class CioMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        var mods = LoadingModList.get();
        var file = mods == null ? null : mods.getModFileById(CioCompatibility.MOD_ID);
        if (file == null) return false;
        if (!targetClassName.equals(CioCompatibility.PHASE_TYPE)) return true;
        if (file.getMods().stream().noneMatch(mod -> mod.getModId().equals(CioCompatibility.MOD_ID)
                && mod.getVersion().toString().equals(CioCompatibility.INVERTER_VERSION))) return false;
        // The published jar and tag source differ. Private-state assumptions must
        // match the inspected numerical class, even if a version label is reused.
        try {
            byte[] bytes = Files.readAllBytes(file.getFile().findResource(
                    CioCompatibility.PHASE_TYPE.replace('.', '/') + ".class"));
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return sha.equals("e48034d26daf891727101db22e52bef1dd0ffb4a74fc557866f621bff431ac08");
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
            return false;
        }
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
