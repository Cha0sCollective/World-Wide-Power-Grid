package org.cha0scollective.wwpg.circuits;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.circuits.components.*;
import com.feb.moregrid.component.*;
import static org.cha0scollective.wwpg.circuits.AdvancedLibrary.*;
/** Power Components code stays outside the native-only assemblies. */
final class FusedOutputChecks {
    static void power(GameTestHelper h){
        var rack=new Rack(h);var r=rack.board("output","fused-latch",0,0);BlockPos[] load={null};
        h.runAtTickTime(5,()->{r.source("VIN",24);r.source("RUN",15);r.source("TRIGGER",0);load[0]=r.device(ModdedBlocks.CREATIVE_RESISTOR.get());rack.equipment.getLast().put("name","output-load");rack.equipment.getLast().put("resistance",240d);((ResistorBlockEntity)h.getLevel().getBlockEntity(load[0])).setValue(240);r.wire(r.board,r.ports.get("OUT"),load[0],0);r.wire(load[0],1,r.board,r.ports.get("GND"));rack.saveManifest();});
        h.runAtTickTime(30,()->{r.sample("untriggered","OUT");r.check("OUT",false);r.volts("TRIGGER",15);});
        h.runAtTickTime(40,()->{r.sample("triggered","OUT");r.check("OUT",true);r.volts("TRIGGER",0);});
        h.runAtTickTime(60,()->{r.sample("latched","OUT");r.check("OUT",true);r.volts("RUN",0);});
        h.runAtTickTime(80,()->{r.sample("reset","OUT");r.check("OUT",false);r.volts("RUN",15);});
        h.runAtTickTime(100,()->{r.sample("reset-held-off","OUT");r.check("OUT",false);r.volts("TRIGGER",15);});
        h.runAtTickTime(110,()->r.volts("TRIGGER",0));
        h.runAtTickTime(125,()->{r.check("OUT",true);rack.rebuild();});
        h.runAtTickTime(145,()->{r.sample("rebuild-resets-output","OUT");r.check("OUT",false);r.volts("TRIGGER",15);});
        h.runAtTickTime(155,()->r.volts("TRIGGER",0));
        h.runAtTickTime(175,()->{r.check("OUT",true);r.volts("VIN",0);});
        h.runAtTickTime(195,()->{r.sample("supply-lost","OUT");r.check("OUT",false);r.volts("VIN",24);});
        h.runAtTickTime(215,()->{r.sample("supply-restored-off","OUT");r.check("OUT",false);r.volts("TRIGGER",15);});
        h.runAtTickTime(225,()->r.volts("TRIGGER",0));
        for(int i=0;i<8;i++)h.runAtTickTime(250+i*100,()->{r.sample("steady-latched-"+r.trace.size(),"OUT");r.check("OUT",true);});
        h.runAtTickTime(1000,()->((ResistorBlockEntity)h.getLevel().getBlockEntity(load[0])).setValue(24));
        h.runAtTickTime(1045,()->{
            r.sample("overload-fuse","OUT");r.check("OUT",false);
            var fuse=r.be.getComponentsStream().filter(p->p.component instanceof FuseComponent).findFirst().orElseThrow();h.assertTrue(fuse.get(FuseComponent.BLOWN),"Overload did not blow actual addon fuse");rack.rebuild();
        });
        h.runAtTickTime(1070,()->{r.sample("blown-after-rebuild","OUT");r.check("OUT",false);r.volts("RUN",0);((ResistorBlockEntity)h.getLevel().getBlockEntity(load[0])).setValue(240);});
        h.runAtTickTime(1085,()->{
            var fuse=r.be.getComponentsStream().filter(p->p.component instanceof FuseComponent).findFirst().orElseThrow();
            var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(com.feb.moregrid.registry.ModItems.FUSE.get()));
            h.assertTrue(((FuseComponent)fuse.component).use(r.be,fuse,player).consumesAction(),"Native fuse replacement refused");
            h.assertTrue(!fuse.get(FuseComponent.BLOWN)&&player.getMainHandItem().isEmpty(),"Replacement did not install and consume real fuse");
        });
        h.runAtTickTime(1105,()->{r.check("OUT",false);r.volts("RUN",15);});
        h.runAtTickTime(1125,()->{r.sample("repaired-awaits-trigger","OUT");r.check("OUT",false);r.volts("TRIGGER",15);});
        h.runAtTickTime(1135,()->r.volts("TRIGGER",0));
        h.runAtTickTime(1155,()->{r.sample("repaired-retriggered","OUT");r.check("OUT",true);r.volts("RUN",0);});
        h.runAtTickTime(1175,()->{r.sample("final-reset","OUT");r.check("OUT",false);r.save("power");rack.finish();});
    }
}
