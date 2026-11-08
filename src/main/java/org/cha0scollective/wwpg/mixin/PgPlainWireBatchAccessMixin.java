package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.experimental.PlainWireBatch;
import org.cha0scollective.wwpg.experimental.PackedResults;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractElectricWire.class, remap = false)
public abstract class PgPlainWireBatchAccessMixin implements PlainWireBatch.WireAccess {
    @Shadow protected double aggregatePower;
    @Shadow protected int tickCount;
    @Shadow protected ElectricalNetwork network;

    @Inject(method = {"flipNodes", "setNetwork"}, at = @At("HEAD"))
    private void wwpg$invalidateOldOwner(CallbackInfo ci) { PlainWireBatch.invalidate(network); }

    @Inject(method = "setNetwork", at = @At("RETURN"))
    private void wwpg$invalidateNewOwner(CallbackInfo ci) { PlainWireBatch.invalidate(network); }

    @Override public boolean wwpg$accumulateResistivePower(double voltage) {
        var wire = (AbstractElectricWire) (Object) this;
        double conductance = wire.conductance();
        if (!Double.isFinite(conductance) || conductance <= 0) {
            wire.postMicroTick();
            return false;
        }
        double power = (voltage * conductance) * voltage;
        double next = aggregatePower + power / tickCount;
        if (PlainWireBatch.VERIFY) {
            double scalar = wire.internalPower();
            double expected = aggregatePower + scalar / tickCount;
            if (Double.doubleToRawLongBits(power) != Double.doubleToRawLongBits(scalar)
                    || Double.doubleToRawLongBits(next) != Double.doubleToRawLongBits(expected))
                throw new IllegalStateException("Experimental plain-wire batch differs from scalar PG power");
        }
        aggregatePower = next;
        return true;
    }

    @Override public boolean wwpg$capturePower(PackedResults.Buffer buffer, int index, double voltage) {
        double g = ((AbstractElectricWire) (Object) this).conductance();
        if (!Double.isFinite(g) || g <= 0) return false;
        buffer.a[index] = voltage; buffer.c[index] = g;
        buffer.old[index] = aggregatePower; buffer.d[index] = tickCount;
        return true;
    }
    @Override public void wwpg$verifyPower(double power, double next) {
        double scalar = ((AbstractElectricWire) (Object) this).internalPower();
        PackedResults.same(scalar, power);
        PackedResults.same(aggregatePower + scalar / tickCount, next);
    }
    @Override public void wwpg$publishPower(double next) { aggregatePower = next; }
}
