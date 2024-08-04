package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.report;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.Arrays;
import java.util.List;

public class RuptureData {

    public interface ValueForRupture {
        double getValue(ClusterRupture rupture);
    }

    public interface ValueForSection {
        double getValue(FaultSection section);
    }

    final ClusterRupture rupture;
    final int ruptureIndex;

    double[] sectionValues;
    public double ruptureValue;
    public double minSectionValue;
    public double maxSectionValue;

    public List<RuptureData> children;

    public RuptureData(ClusterRupture rupture, int ruptureIndex) {
        this.rupture = rupture;
        this.ruptureIndex = ruptureIndex;
    }

    public double calculateRuptureValues(ValueForRupture valueForRupture) {
        ruptureValue = valueForRupture.getValue(rupture);
        return ruptureValue;
    }

    public void calculateSectionValues(ValueForSection valueForSection) {
        sectionValues = rupture.buildOrderedSectionList().stream().mapToDouble(valueForSection::getValue).toArray();
        minSectionValue = Arrays.stream(sectionValues).min().getAsDouble();
        maxSectionValue = Arrays.stream(sectionValues).max().getAsDouble();
    }


}
