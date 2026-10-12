import org.cha0scollective.wwpg.api.*;
import org.cha0scollective.wwpg.api.cee.*;
import java.util.*;

/** Execute against the actual published API jars, not source stubs. */
public final class ApiContractChecks {
    static int checks;
    static void check(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
    static void invalid(Runnable work){++checks;try{work.run();throw new AssertionError("Expected IllegalArgumentException");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        check(WWPGApi.version().equals("1.0.0"),"wrong contract");
        for(double v:new double[]{1e-12,1e15}){new ElectricalModel.Resistor(v);new ElectricalModel.Capacitor(v,0);new ElectricalModel.Inductor(v,0);checks+=3;}
        for(double v:new double[]{Math.nextDown(1e-12),Math.nextUp(1e15),Double.NaN,Double.POSITIVE_INFINITY}){
            invalid(()->new ElectricalModel.Resistor(v));invalid(()->new ElectricalModel.Capacitor(v,0));invalid(()->new ElectricalModel.Inductor(v,0));
        }
        for(double v:new double[]{-1e12,-1e-12,1e-12,1e12}){new ElectricalModel.Transformer("a","b",v);++checks;}
        invalid(()->new ElectricalModel.Transformer("a","b",0));invalid(()->new ElectricalModel.Transformer("a","b",Math.nextUp(1e12)));
        new Terminal("a",4095,new LocalPoint(-64,64,0),2);++checks;
        invalid(()->new Terminal("a",4096,new LocalPoint(0,0,0),.12));
        invalid(()->new Terminal("a",0,new LocalPoint(0,0,0),Math.nextUp(2)));
        invalid(()->new LocalPoint(Math.nextUp(64),0,0));
        new InternalNode("a".repeat(128));++checks;invalid(()->new InternalNode("a".repeat(129)));invalid(()->new InternalNode("Upper"));
        invalid(()->new ElectricalModel.Diode(1e-9,1,-273.15,1));
        invalid(()->new CeeElectricalParameters(1,0,1,false));
        new CeeSubstepContext(0,0,1,Double.MIN_VALUE);++checks;
        invalid(()->new TickContext(0,Double.MIN_VALUE,1));
        var a=new Terminal("a",0,new LocalPoint(.25,.85,0),.12);var b=new Terminal("b",1,new LocalPoint(.75,.85,0),.12);
        check(new HashSet<>(List.of(a,b)).equals(new HashSet<>(List.of(b,a))),"ordering must not affect terminal equality");
        check(!new HashSet<>(List.of(a,b)).equals(new HashSet<>(List.of(new Terminal("a",0,a.position(),.13),b))),"radius must affect equality");
        var off=new CircuitDescription(List.of(a,b),List.of(new InternalNode("in")),List.of(new Element("enable","a","in",new ElectricalModel.Switch(false,.01)),new Element("load","in","b",new ElectricalModel.Resistor(100))),Set.of());
        check(off.terminals().size()==2&&off.elements().size()==2,"off state retains identities");
        invalid(()->new CircuitDescription(List.of(a,b),List.of(),List.of(new Element("x","a","missing",new ElectricalModel.Resistor(100))),Set.of()));
        System.out.println("PUBLISHED_API_DOCUMENTATION_CHECKS_PASSED: "+checks);
    }
}
