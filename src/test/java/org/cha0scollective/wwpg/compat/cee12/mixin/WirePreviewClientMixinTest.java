package org.cha0scollective.wwpg.compat.cee12.mixin;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeHelper;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class WirePreviewClientMixinTest {
    // Reflect into the real handlers: widening their access for tests breaks Mixin application.
    private static Object invokeHandler(String name,Object... args) {
        var method=java.util.Arrays.stream(WirePreviewClientMixin.class.getDeclaredMethods())
                .filter(m->m.getName().equals(name)).findFirst().orElseThrow();
        try { method.setAccessible(true);return method.invoke(null,args); }
        catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static Vec3 hoveredPosition(InWorldNode node,Vec3 local,Level level,Operation<Vec3> original) {
        return (Vec3)invokeHandler("wwpg$hoveredTerminalPosition",node,local,level,original);
    }
    private static double range(SableCompanion companion,Level level,Position from,Position to,Operation<Double> original) {
        return (Double)invokeHandler("wwpg$skipUnavailablePosition",companion,level,from,to,original);
    }
    @Test void staticMixinHandlersMustBePrivate() {
        int handlers=0;
        for(var method:WirePreviewClientMixin.class.getDeclaredMethods()) if(method.isAnnotationPresent(WrapOperation.class)) {
            handlers++;
            assertTrue(Modifier.isStatic(method.getModifiers()));
            assertTrue(Modifier.isPrivate(method.getModifiers()),"Mixin rejects non-private static handlers: "+method.getName());
        }
        assertEquals(2,handlers);
    }
    @Test void extensionGeometryUsesExactlyOneBlockTranslationInWorldAndPlotCoordinates() {
        for(var pos:new BlockPos[]{new BlockPos(12,64,-8),new BlockPos(2_000_000,64,-2_000_000)}) {
            var local=new Vec3(.25,.75,.5);
            var lookups=new AtomicInteger();
            var node=new InWorldNode(3,pos) {
                @Override public Vec3 getLocalPosition(Level level) { lookups.incrementAndGet();return local; }
            };
            var conversions=new AtomicInteger();
            Operation<Vec3> original=args->{
                conversions.incrementAndGet();
                return ((InWorldNode)args[0]).toGlobalPosNoSable((Vec3)args[1],(Level)args[2]);
            };
            assertEquals(local.add(pos.getX(),pos.getY(),pos.getZ()),
                    hoveredPosition(node,null,null,original));
            assertEquals(1,lookups.get());
            assertEquals(1,conversions.get());
        }
    }

    @Test void nativeCeePositionIsPreserved() {
        var node=new InWorldNode(0,new BlockPos(10,20,30)) {
            @Override public Vec3 getLocalPosition(Level level) { throw new AssertionError("native geometry replaced"); }
        };
        var local=new Vec3(.1,.2,.3);
        assertEquals(local.add(10,20,30),hoveredPosition(node,local,null,
                args->((InWorldNode)args[0]).toGlobalPosNoSable((Vec3)args[1],(Level)args[2])));
    }

    @Test void missingGeometryStaysUnavailableAndNeverReachesSable() {
        var node=new InWorldNode(0,BlockPos.ZERO) {
            @Override public Vec3 getLocalPosition(Level level) { return null; }
        };
        Vec3 resolved=hoveredPosition(node,null,null,
                args->((InWorldNode)args[0]).toGlobalPosNoSable((Vec3)args[1],(Level)args[2]));
        assertNull(resolved);
        assertEquals(Double.POSITIVE_INFINITY,range(
                null,null,resolved,Vec3.ZERO,args->{ throw new AssertionError("Sable received null"); }));
        assertEquals(Double.POSITIVE_INFINITY,range(
                null,null,Vec3.ZERO,null,args->{ throw new AssertionError("Sable received null"); }));
    }

    @Test void detachedNodesKeepCeeCacheResolutionEvenWhenGeometryIsMissing() {
        var node=new InWorldNode(7,DetachedNodeHelper.getFromId(7).sourcePos()) {
            @Override public Vec3 getLocalPosition(Level level) { throw new AssertionError("detached fake origin"); }
        };
        var cached=new Vec3(4,5,6);
        for(Vec3 answer:new Vec3[]{null,cached}) {
            assertSame(answer,hoveredPosition(node,null,null,args->{
                assertSame(node,args[0]);assertNull(args[1]);return answer;
            }));
        }
    }

    @Test void validRangeChecksAreDelegatedWithoutTransformingPlotCoordinates() {
        var from=new Vec3(2_000_000.25,64.75,-2_000_000.5);
        var to=new Vec3(10,65,20);
        var calls=new AtomicInteger();
        assertEquals(2.5,range(null,null,from,to,args->{
            calls.incrementAndGet();assertSame(from,args[2]);assertSame(to,args[3]);return 2.5;
        }));
        assertEquals(1,calls.get());
    }

    @Test void frozenCee132HasTheExpectedPreviewInjectionSites() throws Exception {
        var runtime=System.getProperty("wwpg.test.ceeRuntimeJar");
        if(runtime!=null) try(var jar=new ZipFile(runtime);
                var loaded=InWorldNode.class.getResourceAsStream("InWorldNode.class")) {
            assertArrayEquals(jar.getInputStream(jar.getEntry(
                    "com/george_vi/electroenergetics/foundation/nodes/InWorldNode.class")).readAllBytes(),loaded.readAllBytes(),
                    "Preview checks must execute against the requested CEE runtime");
        }
        try(var jar=new ZipFile(System.getProperty("wwpg.test.cee12Contract"))) {
            var cls=new ClassNode();
            new ClassReader(jar.getInputStream(jar.getEntry(
                    "com/george_vi/electroenergetics/content/wire_spool/WireApplyingBehaviour.class"))).accept(cls,0);
            var tick=cls.methods.stream().filter(m->m.name.equals("tick")&&m.desc.equals("()V")).findFirst().orElseThrow();
            int conversions=0,ranges=0;
            for(var instruction:tick.instructions) if(instruction instanceof MethodInsnNode call) {
                if(call.owner.equals("com/george_vi/electroenergetics/foundation/nodes/InWorldNode")
                        &&call.name.equals("toGlobalPosNoSable")
                        &&call.desc.equals("(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/phys/Vec3;")) conversions++;
                if(call.owner.equals("dev/ryanhcode/sable/companion/SableCompanion")
                        &&call.name.equals("distanceSquaredWithSubLevels")
                        &&call.desc.equals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;Lnet/minecraft/core/Position;)D")) ranges++;
            }
            assertEquals(2,conversions,"ordinal 1 must be the hovered-node conversion");
            assertEquals(1,ranges);
        }
    }

    @Test void previewMixinIsRegisteredOnlyOnTheClient() throws Exception {
        try(var reader=new InputStreamReader(getClass().getResourceAsStream("/wwpg.cee12.mixins.json"),StandardCharsets.UTF_8)) {
            var config=JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(config.getAsJsonArray("client").asList().stream().anyMatch(e->e.getAsString().equals("WirePreviewClientMixin")));
            assertFalse(config.getAsJsonArray("mixins").asList().stream().anyMatch(e->e.getAsString().equals("WirePreviewClientMixin")));
            assertEquals("org.cha0scollective.wwpg.compat.cee.CeeMixinPlugin",config.get("plugin").getAsString());
        }
    }
}
