package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.frequency_meter.FrequencyMeterDevice;
import com.george_vi.electroenergetics.content.synchroscope.SynchroscopeDevice;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** A saved sample remainder must not prevent the native instrument from syncing. */
@Mixin(value = {FrequencyMeterDevice.class, SynchroscopeDevice.class}, remap = false)
abstract class MeterDisplayTimingMixin {
    @Unique private int wwpg$lastDisplaySample;

    @ModifyExpressionValue(method = "postTick", at = {
            @At(value = "FIELD", target = "Lcom/george_vi/electroenergetics/content/frequency_meter/FrequencyMeterDevice;ticks:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lcom/george_vi/electroenergetics/content/synchroscope/SynchroscopeDevice;ticks:I", opcode = Opcodes.GETFIELD)
    })
    private int wwpg$elapsedSamples(int samples) {
        // The upstream condition uses ticks % 10 == 0. At 16 substeps, an
        // odd saved count never meets it. Retain the ten-sample throttle and
        // native value/change checks, triggering whenever that interval elapses.
        if (samples < wwpg$lastDisplaySample || (long) samples - wwpg$lastDisplaySample >= 10) {
            wwpg$lastDisplaySample = samples;
            return 0;
        }
        return 1;
    }
}
