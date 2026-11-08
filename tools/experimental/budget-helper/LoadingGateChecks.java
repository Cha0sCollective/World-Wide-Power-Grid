package org.cha0scollective.wwpg.benchmark;

public final class LoadingGateChecks {
    private static void require(boolean condition) { if (!condition) throw new AssertionError(); }
    private static void fails(Runnable operation, String text) {
        try { operation.run(); throw new AssertionError("Expected "+text); }
        catch (IllegalStateException expected) { require(expected.getMessage().contains(text)); }
    }
    public static void main(String[] args) {
        var normal=new LoadingGate(953,0,0);
        for(int i=1;i<=100;i++) require(normal.observe(i,i,1,true,true,true,0)==(i==100));
        var work=new LoadingGate(953,0,0);
        for(int i=1;i<=500;i++) require(!work.observe(i,i,1,true,true,true,1000-i));
        require(work.stage()==LoadingGate.Stage.WORK&&work.healthyTicks()==0);
        for(int i=501;i<=600;i++) require(work.observe(i,i,1,true,true,true,0)==(i==600));
        var stalled=new LoadingGate(953,0,0); stalled.observe(1,0,1,true,false,false,10);
        fails(()->stalled.observe(2,30_000_000_001L,2,true,false,false,9),"no readiness progress");
        var total=new LoadingGate(1,0,0);
        for(int i=1;i<=600;i++) total.observe(i,i,1,true,false,false,1000-i);
        fails(()->total.observe(601,601,1,true,false,false,399),"total 600 ticks");
        var again=new LoadingGate(953,0,0);
        for(int i=1;i<=50;i++) again.observe(i,i,1,true,true,true,0);
        require(!again.observe(51,51,1,true,true,true,1)&&again.healthyTicks()==0);
        for(int i=52;i<=151;i++) require(again.observe(i,i,1,true,true,true,0)==(i==151));
        var graph=new LoadingGate(953,0,0); graph.observe(1,0,1,true,false,false,0);
        fails(()->graph.observe(302,302,1,true,false,false,0),"300 ticks");
        var lost=new LoadingGate(953,0,0); lost.observe(1,0,1,true,false,false,10);
        fails(()->lost.observe(2,1,1,false,false,false,9),"Chunk readiness lost");
        System.out.println("BUDGET_GATE_CHECKS passed=7");
    }
}
