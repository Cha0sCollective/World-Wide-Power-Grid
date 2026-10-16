package org.cha0scollective.wwpg.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import org.cha0scollective.wwpg.gametest.NativeReferenceTimingGameTests;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only packaged in the bridge-free control jar, never the player mod. */
@Mixin(value = NativeReferenceTimingGameTests.class, remap = false)
abstract class ReferenceTeardownMixin {
    @Shadow @Final private static BlockPos SOURCE;
    @Shadow @Final private static BlockPos DEVICE;
    @Shadow @Final private static BlockPos LOAD;
    @Inject(method = "finish", at = @At("HEAD"))
    private static void removeControlSources(GameTestHelper helper, CallbackInfo ci) {
        // Measurements and their assertions have finished. GameTest success
        // discards wire entities but leaves device blocks ticking. Raw PG does
        // not repartition that disconnected control network: two voltage
        // sources would retain only one ground anchor and become singular.
        // Remove the test-owned PG devices first, without changing any sample,
        // equation, guard or the separate saved-world reference fixtures.
        for (var pos : new BlockPos[]{SOURCE, DEVICE, LOAD}) {
            if (helper.getBlockState(pos).getBlock().builtInRegistryHolder().key().location().getNamespace().equals("powergrid"))
                helper.setBlock(pos, Blocks.AIR);
        }
    }
}
