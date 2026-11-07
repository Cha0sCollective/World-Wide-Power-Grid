package org.cha0scollective.wwpg.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RotorStepTest {
    @Test void shortStepsRetainNativeUnsaturatedResponseAndSequentialDriveLoads(){
        for(double sign:new double[]{-1,1})for(double start:new double[]{32,100,200,249,254,260})for(double torque:new double[]{-2500,-500,0,500}) {
            double speed=sign*start,old=speed-sign*3;
            var drives=new RotorStep.Drive[]{new RotorStep.Drive(sign*255,1319.4689),new RotorStep.Drive(sign*250,1200)};
            var nativeStep=RotorStep.evaluate(speed,old,12.3,2.25,.85,.002,.05,torque,drives);
            double current=speed;
            for(int step=0;step<16;step++) {
                var result=RotorStep.evaluate(current,speed,old,12.3,2.25,.85,.002,.05/16,torque,drives,step==0);
                assertArrayEquals(nativeStep.loads(),result.loads(),1e-12);
                current=result.speed();
            }
            assertEquals(nativeStep.speed(),current,1e-10);
        }
    }
    @Test void changingElectricalTorqueStillChangesTheClutchTrial(){
        var drives=new RotorStep.Drive[]{new RotorStep.Drive(255,5000)};
        var light=RotorStep.evaluate(249,249,249,12.3,2.25,.85,.002,.05/16,-500,drives,true);
        var heavy=RotorStep.evaluate(249,249,249,12.3,2.25,.85,.002,.05/16,-1500,drives,true);
        assertTrue(heavy.loads()[0]>light.loads()[0]);
        assertTrue(heavy.speed()<light.speed());
    }
    @Test void oneStepPreservesNativeSequentialClutchLaw(){
        for(double sign:new double[]{1,-1})for(double speed:new double[]{0,32,200,254,260})for(double force:new double[]{0,-500,500}){
            double start=sign*speed,expected=start+(force-Math.signum(start)*Math.min(Math.abs(start)*20*12.3,2.25))/20/12.3;
            var drives=new RotorStep.Drive[]{new RotorStep.Drive(sign*255,1319.4689),new RotorStep.Drive(sign*200,1200)};
            if(Math.abs(expected)<.01)expected=0;
            for(var d:drives){double error=(d.target()-expected)*(d.target()<0?-1:1);double f=(.85*Math.max(0,error)+.002*(start-expected))*20*12.3;expected+=Math.min(Math.abs(f),d.maximumForce())*Math.signum(d.target())/20/12.3;}
            assertEquals(expected,RotorStep.evaluate(start,start,12.3,2.25,.85,.002,.05,force,drives).speed(),1e-10);
        }
    }
    @Test void maximumDriveIsNotMultipliedBySubsteps(){
        for(int n:new int[]{1,2,16}){
            double speed=0;
            for(int k=0;k<n;k++)speed=RotorStep.evaluate(speed,0,12.3,0,.85,.002,.05/n,0,new RotorStep.Drive[]{new RotorStep.Drive(255,100)}).speed();
            assertEquals(100*.05/12.3,speed,1e-10);
        }
    }
    @Test void weakDriveSurvivesTheOncePerTickCutoff(){
        for(int n:new int[]{1,2,16}) {
            double speed=0;
            for(int step=0;step<n;step++)speed=RotorStep.evaluate(speed,0,10,0,.85,.002,.05/n,0,
                    new RotorStep.Drive[]{new RotorStep.Drive(255,1)},step==0).speed();
            assertEquals(.005,speed,1e-12);
        }
    }
    @Test void zeroTargetSuppliesNoMechanicalDrive(){assertEquals(50,RotorStep.evaluate(50,50,10,0,.85,.002,.05,0,new RotorStep.Drive[]{new RotorStep.Drive(0,1000)}).speed());}
    @Test void frictionStopsWithoutReversing(){assertEquals(0,RotorStep.evaluate(.01,.01,10,100,.85,.002,.05,0,new RotorStep.Drive[0]).speed());}
    @Test void rejectsInvalidPhysicalInputs(){assertThrows(IllegalArgumentException.class,()->RotorStep.evaluate(0,0,0,0,.85,.002,.05,0,new RotorStep.Drive[0]));assertThrows(IllegalStateException.class,()->RotorStep.evaluate(Double.NaN,0,10,0,.85,.002,.05,0,new RotorStep.Drive[0]));}
}
