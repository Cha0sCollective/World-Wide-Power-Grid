package org.cha0scollective.wwpg.gametest;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.kinetics.generator.inductionrotor.CommutatorBlockEntity;
import org.patryk3211.powergrid.kinetics.generator.rotor.RotorBehaviour;
import org.patryk3211.powergrid.kinetics.generator.clutch.GeneratorClutchBlockEntity;
import java.nio.file.*;

/** Reuse the existing real assemblies and assertions, observing all startup/load-change ticks at sixteen steps. */
@GameTestHolder("wwpg_audit_generator")
@PrefixGameTestTemplate(false)
public final class AuditGeneratorGameTests {
    @BeforeBatch(batch="audit_generator")
    public static void configure(ServerLevel level){DynamicGameTests.substeps(level);}
    @GameTest(template="empty",batch="audit_generator",timeoutTicks=2850)
    public static void carbonPileAtSixteenSubsteps(GameTestHelper h){trace(h,"carbon",2610);GeneratorTimingGameTests.carbonPilePreservesNativeClutchOperatingPoints(h);}
    @GameTest(template="empty",batch="audit_generator",timeoutTicks=2600)
    public static void transformerAtSixteenSubsteps(GameTestHelper h) throws Exception {trace(h,"transformer",2460);GeneratorTimingGameTests.transformerFedSelfExcitationSettlesAndRecovers(h);}
    @GameTest(template="empty",batch="audit_generator",timeoutTicks=1800)
    public static void shaftVariacAtSixteenSubsteps(GameTestHelper h){trace(h,"shaft-variac",1660);GeneratorTimingGameTests.shaftVariacRestoresExcitationAtFullRotorSpeed(h);}
    @GameTest(template="empty",batch="audit_generator",timeoutTicks=1800)
    public static void redstoneVariacAtSixteenSubsteps(GameTestHelper h){trace(h,"redstone-variac",1660);GeneratorTimingGameTests.redstoneVariacRestoresExcitationAtFullRotorSpeed(h);}
    private static void trace(GameTestHelper h,String name,int finish){
        var rows=new JsonArray();
        h.onEachTick(()->{
            var row=new JsonObject();row.addProperty("tick",h.getTick());
            var block=h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(8,3,4)));
            if(block instanceof CommutatorBlockEntity generator&&generator.getBehaviour(RotorBehaviour.TYPE)!=null){
                row.addProperty("rpm",generator.getBehaviour(RotorBehaviour.TYPE).getAngularVelocity());
                var source=generator.getAssemblySource();
                boolean accepted=source!=null&&source.getNetwork()!=null&&source.getNetwork().isConverged();
                row.addProperty("accepted",accepted);
                if(accepted){
                    double voltage=new BlockWireEndpoint(generator.getBlockPos(),0).getNode(h.getLevel()).getVoltage()
                            -new BlockWireEndpoint(generator.getBlockPos(),1).getNode(h.getLevel()).getVoltage();
                    if(Double.isFinite(voltage))row.addProperty("voltage",voltage);else row.add("voltage",JsonNull.INSTANCE);
                }
                for(int x:new int[]{1,9})if(h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(x,3,4))) instanceof GeneratorClutchBlockEntity clutch)
                    row.addProperty(x==1?"left_clutch":"right_clutch",clutch.load);
            }
            rows.add(row);
        });
        h.runAtTickTime(finish,()->{
            h.assertTrue(ModdedConfigs.server().electricity.solver.multiTicks.get()==16,"Generator audit changed electrical settings");
            var record=new JsonObject();record.addProperty("scenario",name);record.addProperty("substeps",16);
            record.addProperty("backend",System.getProperty("wwpg.test.backend"));record.add("samples",rows);
            try{Files.writeString(Path.of("generator-audit-"+name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(record));}
            catch(java.io.IOException error){throw new IllegalStateException(error);}
        });
    }
}
