package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.bridge.PgGeneratorTiming;
import org.patryk3211.powergrid.electricity.sim.calculation.Precalculated;
import org.patryk3211.powergrid.electricity.sim.node.*;
import org.patryk3211.powergrid.electricity.sim.solver.*;
import org.patryk3211.powergrid.electricity.sim.special.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=GeneratorCoupling.class,remap=false)
public abstract class PgGeneratorCouplingMixin extends VoltageSourceCoupling implements ISolverHook,PgGeneratorTiming.Source {
    @Shadow @Final private IRotor rotor;
    @Shadow private Precalculated<Float> fieldStrength;
    @Shadow private float baseResistance;
    @Shadow private float field;
    @Shadow private double backEmf;
    @Shadow private double emfValue;
    @Unique private PgGeneratorTiming.Coupling wwpg$timing;
    protected PgGeneratorCouplingMixin(IElectricNode p,IElectricNode n,Number r){super(p,n,r);}
    public GeneratorCoupling wwpg$generator(){return (GeneratorCoupling)(Object)this;}
    public IRotor wwpg$rotor(){return rotor;}
    public Precalculated<Float> wwpg$fieldProvider(){return fieldStrength;}
    public void wwpg$prepareCopper(){backEmf=0;super.setResistance(baseResistance);}
    public void wwpg$publishField(double value,double emf,double history){field=(float)value;setVoltage(emf);emfValue=history;}
    public PgGeneratorTiming.Coupling wwpg$coupling(){
        if(wwpg$timing==null)wwpg$timing=new PgGeneratorTiming.Coupling(this);
        return wwpg$timing;
    }
    @Inject(method="preSolve",at=@At("HEAD"),cancellable=true)
    private void wwpg$prepare(CallbackInfo ci){if(wwpg$coupling().prepare())ci.cancel();}
    @Inject(method="addStaticResidual",at=@At("HEAD"),cancellable=true)
    private void wwpg$coupledInput(IResidualAdder residual,CallbackInfo ci){if(wwpg$coupling().managed())ci.cancel();}
    @Inject(method="postUpperSolve",at=@At("HEAD"),cancellable=true)
    private void wwpg$commit(CallbackInfo ci){if(wwpg$coupling().managed()){wwpg$coupling().accepted();ci.cancel();}}
    public void startIteration(int iteration){wwpg$coupling().iterate(iteration);}
    public void addResidual(IResidualAdder residual){wwpg$coupling().residual(residual);}
}
