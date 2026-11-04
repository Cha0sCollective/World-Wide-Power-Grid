package org.cha0scollective.wwpg.wiring;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerminalPickingRayTest {
    private final Vec3 from=new Vec3(.5,64.5,.5),to=from.add(6,0,0);

    @Test void translatedBlockHitCannotStartAnyWorldLookup() {
        var location=new Vec3(2000005.5,64.5,.5);
        var hit=new BlockHitResult(location,Direction.WEST,BlockPos.containing(location),false);
        // A null player makes any world/terminal lookup fail rather than masking an expanded scan.
        assertSame(hit,CeeTerminalPicking.pick(null,from,to,hit));
    }
    @Test void offRayOrBeyondReachHitsCannotChangeTheRay() {
        for(var location:new Vec3[]{from.add(3,.01,0),from.add(6.1,0,0),from.add(-.1,0,0)}) {
            var hit=new BlockHitResult(location,Direction.WEST,BlockPos.containing(location),false);
            assertSame(hit,CeeTerminalPicking.pick(null,from,to,hit));
        }
    }
    @Test void validPointsAndDistantSameFrameCoordinatesRemainSupported() {
        for(double shift:new double[]{0,2000000,-2000000}) {
            var a=from.add(shift,0,-shift);var b=to.add(shift,0,-shift);
            assertTrue(CeeTerminalPicking.withinRay(a,b,a));
            assertTrue(CeeTerminalPicking.withinRay(a,b,b));
            assertTrue(CeeTerminalPicking.withinRay(a,b,a.lerp(b,.5)));
        }
    }
    @Test void transformRoundingIsAllowedWithoutAllowingAnotherPin() {
        assertTrue(CeeTerminalPicking.withinRay(from,to,from.add(3,1e-8,0)));
        assertFalse(CeeTerminalPicking.withinRay(from,to,from.add(3,.001,0)));
    }
    @Test void nonfiniteAndZeroLengthRaysAreRejected() {
        assertFalse(CeeTerminalPicking.withinRay(from,from,from));
        assertFalse(CeeTerminalPicking.withinRay(from,new Vec3(Double.POSITIVE_INFINITY,0,0),from));
        assertFalse(CeeTerminalPicking.withinRay(from,to,new Vec3(Double.NaN,0,0)));
    }
}
