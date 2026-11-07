package org.cha0scollective.wwpg.bridge;
import org.junit.jupiter.api.Test;
import java.util.function.DoubleUnaryOperator;
import static org.junit.jupiter.api.Assertions.*;
class ExcitationSearchTest {
    private static ExcitationSearch response() {
        var search=new ExcitationSearch();search.reset(2);
        search.observe(0,100,100,new double[]{100,100}); // obsolete state
        search.observe(0,1,-.1,new double[]{.2,.4});
        search.observe(0,-100,100,new double[]{-100,-100}); // backtracking
        search.observe(1,2,-.2,new double[]{.4,.8});
        return search;
    }
    @Test void comparesOnlyFullElectricalTrialsAfterTheInitialHook(){
        var search=response();
        search.observe(1,900,-900,new double[]{900,900});
        assertEquals(-.3,search.currentAt(3),1e-12);
        assertEquals(.6,search.fieldCurrentAt(0,3),1e-12);
        assertEquals(1.2,search.fieldCurrentAt(1,3),1e-12);
    }
    @Test void aNewPhysicalStepDiscardsEarlierSearchPoints(){
        var search=response();search.reset(2);
        assertEquals(0,search.derivative(1,6,v->10));
    }
    @Test void findsTheCrossingBeyondPositiveFeedbackBuildUp(){
        var search=response();
        DoubleUnaryOperator emf=v->.1+20*Math.tanh(v/5);
        for(double start:new double[]{.01,.5,5,30}){
            double derivative=search.derivative(start,6,emf);
            double target=start-(1.6*start-emf.applyAsDouble(start))/(1.6-derivative);
            assertTrue(target>10);
            assertEquals(0,1.6*target-emf.applyAsDouble(target),1e-7);
        }
    }
    @Test void supportsNegativePolarityWithoutReplacingTheEquation(){
        var search=response();
        DoubleUnaryOperator emf=v->-.1+20*Math.tanh(v/5);
        double derivative=search.derivative(-.5,6,emf);
        double target=-.5-(1.6*-.5-emf.applyAsDouble(-.5))/(1.6-derivative);
        assertTrue(target< -10);
        assertEquals(0,1.6*target-emf.applyAsDouble(target),1e-7);
    }
    @Test void linearAndConstantSourcesKeepTheirExactSlope(){
        var search=response();
        assertEquals(.2,search.derivative(1,6,v->10+.2*v),1e-9);
        assertEquals(0,search.derivative(1,6,v->10),1e-9);
    }
    @Test void doesNotTreatDiscontinuitiesOrMissingRootsAsSolutions(){
        var search=response();
        assertEquals(0,search.derivative(1,6,v->1.6*v+(v<2?1:-1)));
        assertEquals(0,search.derivative(1,6,v->1.6*v+1));
        assertEquals(0,search.derivative(1,6,v->Double.NaN));
    }
}
