package uk.nhs.prm.deduction.e2e.performance;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Integer.parseInt;

public class LoadSpecParser {
    public static List<LoadPhase> parsePhases(String loadSpecString) {
        var phases = new ArrayList<LoadPhase>();
        String[] phaseSpecs = loadSpecString.split(",");
        for (var phaseSpec : phaseSpecs) {
            var countAndRate = phaseSpec.split("@");
            phases.add(LoadPhase.atFlatRate(countAndRate[1], parseInt(countAndRate[0])));
        }
        return phases;
    }
}
