package org.cha0scollective.wwpg.bridge;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.mixin.*;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.calculation.*;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.solver.IResidualAdder;
import org.patryk3211.powergrid.electricity.sim.special.*;
import org.patryk3211.powergrid.kinetics.generator.clutch.GeneratorClutchBlockEntity;
import org.patryk3211.powergrid.kinetics.generator.inductionrotor.InductionRotorBlockEntity;
import org.patryk3211.powergrid.kinetics.generator.inductionrotor.LargeInductionRotorBlockEntity;
import org.patryk3211.powergrid.kinetics.generator.rotor.RotorBehaviour;

import java.util.*;

/** Coupled numerical trials; the existing PG controller remains the only committed rotor. */
public final class PgGeneratorTiming {
    private PgGeneratorTiming() {}
    private static final ThreadLocal<Window> ACTIVE = new ThreadLocal<>();
    public interface Source {
        GeneratorCoupling wwpg$generator();
        IRotor wwpg$rotor();
        Precalculated<Float> wwpg$fieldProvider();
        void wwpg$prepareCopper();
        void wwpg$publishField(double field, double emf, double legacyHistory);
        Coupling wwpg$coupling();
    }
    /** Per-segment tick marker survives a controller handoff, without a retained world registry. */
    public interface Advanced {
        int wwpg$advancedTick();
        void wwpg$advancedTick(int tick);
    }

    public static Window begin(MinecraftServer server,List<List<ElectricalNetwork>> networks) {
        if(ACTIVE.get()!=null)throw new IllegalStateException("Nested generator schedule");
        var window=new Window(server);
        if(Boolean.parseBoolean(System.getProperty("wwpg.generatorCoupling","true"))) {
            var seen=new IdentityHashMap<RotorBehaviour,Source>();
            var duplicates=Collections.newSetFromMap(new IdentityHashMap<RotorBehaviour,Boolean>());
            for(var group:networks)for(var network:group)for(var hook:network.innerHooks)if(hook instanceof Source source) {
                if(!(source.wwpg$rotor() instanceof RotorBehaviour rotor))continue;
                var owner=rotor.getControllerOrThis();
                var prior=seen.putIfAbsent(owner,source);
                if(prior!=null&&prior!=source){duplicates.add(owner);continue;}
                var frame=Frame.create(server,source,owner);
                if(frame!=null)window.frames.put(source,frame);
            }
            window.frames.entrySet().removeIf(e->duplicates.contains(e.getValue().owner));
        }
        ACTIVE.set(window);return window;
    }

    public static boolean advanced(RotorBehaviour rotor) {
        return rotor.getWorld() instanceof ServerLevel level
                && ((Advanced)rotor).wwpg$advancedTick()==level.getServer().getTickCount();
    }

    public static final class Window implements AutoCloseable {
        private final MinecraftServer server;
        private final Map<Source,Frame> frames=new IdentityHashMap<>();
        private Window(MinecraftServer server){this.server=server;}
        public void beforeStep(){for(var frame:frames.values())frame.beforeStep();}
        public void afterStep(){for(var frame:frames.values())frame.afterStep(server.getTickCount());}
        public void close(){
            try{for(var frame:frames.values())frame.finish();}
            finally{for(var source:frames.keySet())source.wwpg$coupling().frame=null;ACTIVE.remove();}
        }
    }

    /** Matrix linearization belongs to its generator, just as PG diode stamps belong to their diode. */
    public static final class Coupling {
        private final Source source;
        private Frame frame;
        private final Map<Integer,Double> jacobian=new HashMap<>();
        private final ExcitationSearch search=new ExcitationSearch();
        private double[] fieldCurrents=new double[0];
        public Coupling(Source source){this.source=source;}
        public void matrixReset(){jacobian.clear();}
        public boolean prepare(){
            var window=ACTIVE.get();frame=window==null?null:window.frames.get(source);
            if(frame==null){clearJacobian();return false;}
            var generator=source.wwpg$generator();
            // The rotor's implicit response is part of E(x). Retaining PG's
            // additional inertia/history resistor here would count it twice.
            source.wwpg$prepareCopper();
            generator.setVoltage(0);
            int size=frame.field.coils.size();
            search.reset(size);
            if(fieldCurrents.length!=size)fieldCurrents=new double[size];
            return true;
        }
        public void iterate(int iteration){
            if(frame==null)return;
            var generator=source.wwpg$generator();
            double voltage=generator.getPositive().getVoltage()-(generator.getNegative()==null?0:generator.getNegative().getVoltage());
            for(int n=0;n<fieldCurrents.length;n++)fieldCurrents[n]=frame.field.coils.get(n).wire.current();
            search.observe(iteration,voltage,generator.getCurrent(),fieldCurrents);
            double derivative=search.derivative(voltage,generator.getResistance(),this::emfAt);
            var next=new HashMap<Integer,Double>();
            add(next,generator.getPositive(),-derivative);
            add(next,generator.getNegative(),derivative);
            stamp(next);
        }
        private double emfAt(double voltage){
            double field=0;
            for(int n=0;n<fieldCurrents.length;n++){
                var coil=frame.field.coils.get(n);
                field+=coil.weight*coil.curve(search.fieldCurrentAt(n,voltage));
            }
            return frame.emf(field,search.currentAt(voltage));
        }
        public void residual(IResidualAdder adder){
            if(frame==null)return;
            var generator=source.wwpg$generator();
            // PG can fit a step without another hook callback. Always check
            // the complete equation at the state actually being evaluated,
            // rather than reusing the previous trial's linearized input.
            double residual=-frame.emf(frame.field.value(),generator.getCurrent());
            var network=generator.getNetwork();
            var state=((ElectricalNetworkAccessor)network).wwpg$solver().stateVector();
            for(var entry:jacobian.entrySet())residual-=entry.getValue()*state.get(entry.getKey(),0);
            if(!Double.isFinite(residual))throw new IllegalStateException("Nonfinite coupled generator residual");
            adder.add(generator.getIndex(),residual);
        }
        public boolean managed(){return frame!=null;}
        public void accepted(){
            if(frame==null||!source.wwpg$generator().getNetwork().isConverged())return;
            var generator=source.wwpg$generator();
            double b=frame.field.value(),i=generator.getCurrent();
            frame.accepted=frame.evaluate(b,i);
            // Keep the legacy save field meaningful if the assembly later
            // returns to PG's original path. It is not an extra coupled force.
            double history=-b*b*frame.dt/frame.inertia*i;
            source.wwpg$publishField(b,b*frame.accepted.speed()*Math.PI/30,history);
            source.wwpg$fieldProvider().get();
        }
        private void clearJacobian(){
            var generator=source.wwpg$generator();var network=generator.getNetwork();
            if(network!=null&&!network.isDirty()&&((ElectricalNetworkAccessor)network).wwpg$solver()!=null)stamp(Map.of());
            else jacobian.clear(); // A replacement matrix starts with no trial stamp.
        }
        private void stamp(Map<Integer,Double> next){
            var generator=source.wwpg$generator();var network=generator.getNetwork();
            var solver=((ElectricalNetworkAccessor)network).wwpg$solver();
            if(network.isDirty()||solver==null)throw new IllegalStateException("Generator trial has no prepared matrix");
            boolean exchange=solver.rowExchange();solver.rowExchange(false);
            try{
                // alterConductanceMatrix intentionally drops tiny changes.
                // A nonlinear stamp and its residual must instead describe the
                // very same coefficients: discarded deltas accumulate across
                // trials and can make a false physical residual look accepted.
                for(var column:union(jacobian.keySet(),next.keySet()))
                    solver.jacobianAdd(generator.getIndex(),column,next.getOrDefault(column,0d)-jacobian.getOrDefault(column,0d));
            }finally{solver.rowExchange(exchange);}
            jacobian.clear();jacobian.putAll(next);
        }
        private static Set<Integer> union(Set<Integer>a,Set<Integer>b){var result=new HashSet<>(a);result.addAll(b);return result;}
        private static void add(Map<Integer,Double> map,IElectricNode node,double value){if(node!=null)map.merge(node.getIndex(),value,Double::sum);}
    }

    private static final class Frame {
        final Source source;final RotorBehaviour owner;final List<RotorBehaviour> segments;
        final List<RotorBehaviour.IForceSource> suppliers;final RotorStep.Drive[] drives;
        final Field field;final double inertia,friction,kp,kd,dt,oldSpeed,controlSpeed;final int steps;
        final double[] loadSum;
        double entrySpeed,netSum,powerSum;int completedSteps;RotorStep.Result accepted;
        private Frame(Source source,RotorBehaviour owner,List<RotorBehaviour> segments,List<RotorBehaviour.IForceSource> suppliers,Field field){
            this.source=source;this.owner=owner;this.segments=segments;this.suppliers=suppliers;this.field=field;
            inertia=owner.getInertia();oldSpeed=owner.getOldAngVel();controlSpeed=owner.getAngularVelocity();
            friction=segments.size()*ModdedConfigs.server().kinetics.generatorControls.rotorSegmentFriction.getF();
            kp=RotorBehaviour.getRotorKp();kd=RotorBehaviour.getRotorKd();
            steps=source.wwpg$generator().getNetwork().getMultiTick();dt=source.wwpg$generator().getNetwork().getDeltaTime();
            drives=suppliers.stream().map(d->new RotorStep.Drive(d.forceSpeed(),d.sourceForce(owner.getAngularVelocity()))).toArray(RotorStep.Drive[]::new);
            loadSum=new double[drives.length];
        }
        static Frame create(MinecraftServer server,Source source,RotorBehaviour owner){
            var network=source.wwpg$generator().getNetwork();
            if(source.wwpg$generator().getClass()!=GeneratorCoupling.class||owner.getClass()!=RotorBehaviour.class
                    ||network==null||!network.isConverged()||RestorationCoordinator.paused(network)||!(owner.getWorld() instanceof ServerLevel level)
                    ||!owner.isController()||!(owner.getInertia()>0)||((PgRotorAccess)owner).wwpg$force()!=0
                    ||!Field.assigned(source.wwpg$generator().getPositive(),network)
                    ||!Field.assigned(source.wwpg$generator().getNegative(),network)
                    ||!level.getChunkSource().isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(owner.getPos())))return null;
            if(network.innerHooks.stream().anyMatch(hook->hook!=source))return null;
            var segments=new ArrayList<RotorBehaviour>();owner.forEachSegment(segments::add);
            var suppliers=new ArrayList<RotorBehaviour.IForceSource>();
            for(var part:segments){
                if(part.getClass()!=RotorBehaviour.class||advanced(part))return null;
                var drive=((PgRotorAccess)part).wwpg$drive();
                if(drive==null)continue;
                // Motor-mode and third-party force suppliers retain their native
                // scheduling; their once-per-tick torque is not a microstep force.
                if(drive.getClass()!=GeneratorClutchBlockEntity.class)return null;
                var clutch=(GeneratorClutchBlockEntity)drive;
                var mode=clutch.getBehaviour(ScrollOptionBehaviour.TYPE);
                if(!(mode instanceof ScrollOptionBehaviour<?> option)||option.get()!=GeneratorClutchBlockEntity.ClutchMode.GENERATOR)return null;
                suppliers.add(drive);
            }
            var field=Field.create(source,owner);
            return field==null?null:new Frame(source,owner,segments,suppliers,field);
        }
        void beforeStep(){entrySpeed=owner.getAngularVelocity();accepted=null;field.snapshot();}
        RotorStep.Result evaluate(double b,double i){
            double force=b*i;
            if(Math.abs(force/steps)<=.001)force=0; // Retain PG's native force threshold.
            return RotorStep.evaluate(entrySpeed,controlSpeed,oldSpeed,inertia,friction,kp,kd,dt,force,drives,completedSteps==0);
        }
        double emf(double b,double i){return b*evaluate(b,i).speed()*Math.PI/30;}
        void afterStep(int tick){
            var result=accepted==null?evaluate(0,0):accepted;
            for(var part:segments){((PgRotorAccess)part).wwpg$speed((float)result.speed());((Advanced)part).wwpg$advancedTick(tick);}
            netSum+=result.netForce()/steps;powerSum+=result.lastPower()/steps;
            for(int n=0;n<loadSum.length;n++)loadSum[n]+=result.loads()[n]/steps;
            completedSteps++;
        }
        void finish(){
            if(completedSteps==0)return;
            owner.prevForce=(float)netSum;owner.power=(float)powerSum;
            for(int n=0;n<suppliers.size();n++)suppliers.get(n).receiveUsedForce((float)loadSum[n]);
        }
    }

    /** Geometry and field ownership come from PG's existing calculation graph. */
    private static final class Field {
        final List<Coil> coils;
        private Field(List<Coil> coils){this.coils=coils;}
        static Field create(Source source,RotorBehaviour owner){
            if(!(source.wwpg$fieldProvider() instanceof PrecalculatedN<?,?> root))return null;
            var dependencies=((PgCalculationManyAccessor)root).wwpg$dependencies();
            if(dependencies==null)return null;
            var expected=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
            var contributions=new IdentityHashMap<LRSeriesWire,Coil>();
            boolean[] supported={true};
            owner.forEachSegment(part->{
                var entity=part.getWorld().getBlockEntity(part.getPos());
                if(!(entity instanceof InductionRotorBlockEntity rotor))return;
                if(entity.getClass()!=InductionRotorBlockEntity.class&&entity.getClass()!=LargeInductionRotorBlockEntity.class){supported[0]=false;return;}
                expected.add(rotor.totalField);
                var values=((PgCalculationManyAccessor)rotor.totalField).wwpg$dependencies();
                if(values==null||values.length==0){supported[0]=false;return;}
                double weight=((PgRotorFieldAccessor)rotor).wwpg$multiplier()/(double)values.length;
                for(var value:values){
                    if(value==null)continue;
                    if(!(value instanceof StampedSupplier<?> supplier)){supported[0]=false;return;}
                    var calculation=supplier.get();if(calculation==null)continue;
                    if(!(calculation instanceof Precalculated1<?,?> calc)){supported[0]=false;return;}
                    var dependency=((PgCalculationOneAccessor)calc).wwpg$dependency();
                    if(!(dependency instanceof StampedSupplier<?> wireSupplier)||!(wireSupplier.get() instanceof LRSeriesWire wire)) {supported[0]=false;return;}
                    if(wire.getClass()!=LRSeriesWire.class||wire.getNetwork()!=source.wwpg$generator().getNetwork()
                            ||!assigned(wire.getNode1(),wire.getNetwork())||!assigned(wire.getNode2(),wire.getNetwork())){supported[0]=false;return;}
                    var coil=contributions.computeIfAbsent(wire,w->new Coil(w,calc));
                    if(coil.calculation!=calc){supported[0]=false;return;}
                    coil.weight+=weight;
                }
            });
            if(!supported[0]||contributions.isEmpty()||expected.size()!=dependencies.length)return null;
            for(var dependency:dependencies)if(!expected.contains(dependency))return null;
            return new Field(new ArrayList<>(contributions.values()));
        }
        static boolean assigned(IElectricNode node,ElectricalNetwork network){return node==null||node.getNetwork()==network&&node.getIndex()>=0&&!network.isLeaf(node);}
        void snapshot(){for(var coil:coils)coil.previous=((Number)((PgCalculationAccessor)coil.calculation).wwpg$value()).doubleValue();}
        double value(){double sum=0;for(var coil:coils)sum+=coil.weight*coil.curve(coil.wire.current());return sum;}
    }
    private static final class Coil {
        final LRSeriesWire wire;final Precalculated1<?,?> calculation;double weight,previous;
        Coil(LRSeriesWire wire,Precalculated1<?,?> calculation){this.wire=wire;this.calculation=calculation;}
        double curve(double current){
            double value=PgWindingFieldInvoker.wwpg$field(current);
            if(previous*value<0)value=.001*Math.signum(value);
            if(!Double.isFinite(value))throw new IllegalStateException("Nonfinite native winding field");
            return value;
        }
    }
}
