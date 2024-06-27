package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;

import java.util.List;

public class DistanceBasedSubSectStiffnessCalculator extends SubSectStiffnessCalculator {

    protected final ScaleFunction scaleFn;
    protected final SectionDistanceAzimuthCalculator disAzCalc;
    protected final StiffnessType type;

    public interface ScaleFunction {
        double scale(int sourceID, int receiverID, double distance);
    }

    public DistanceBasedSubSectStiffnessCalculator(List<? extends FaultSection> subSects, double gridSpacing, double lameLambda, double lameMu, double coeffOfFriction, SectionDistanceAzimuthCalculator disAzCalc, ScaleFunction scaleFn, StiffnessType type) {
        super(subSects, gridSpacing, lameLambda, lameMu, coeffOfFriction);
        this.scaleFn = scaleFn;
        this.disAzCalc = disAzCalc;
        this.type = type;
    }

    public DistanceBasedSubSectStiffnessCalculator(List<? extends FaultSection> subSects, double gridSpacing, double lameLambda, double lameMu, double coeffOfFriction, PatchAlignment alignment, double selfStiffnessCap, SectionDistanceAzimuthCalculator disAzCalc, ScaleFunction scaleFn, StiffnessType type) {
        super(subSects, gridSpacing, lameLambda, lameMu, coeffOfFriction, alignment, selfStiffnessCap);
        this.scaleFn = scaleFn;
        this.disAzCalc = disAzCalc;
        this.type = type;
    }

    @Override
    public StiffnessDistribution calcStiffnessDistribution(int sourceID, int receiverID) {
        double distance = disAzCalc.getDistance(sourceID, receiverID);
        double scale = scaleFn.scale(sourceID, receiverID, distance);
        StiffnessDistribution stiffnessDistribution = super.calcStiffnessDistribution(sourceID, receiverID);

        // hard coded StiffnessType for efficiency
        double[][] values = stiffnessDistribution.get(type);
        for (double[] row : values) {
            for (int j = 0; j < row.length; j++) {
                row[j] = scale * row[j];
            }
        }

        return stiffnessDistribution;
    }
}
