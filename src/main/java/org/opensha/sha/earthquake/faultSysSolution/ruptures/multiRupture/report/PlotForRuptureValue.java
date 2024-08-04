package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.report;

import org.apache.commons.math3.stat.StatUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PlotForRuptureValue {

    final List<RuptureData> data;
    final List<Double> percentiles;

    public PlotForRuptureValue(RuptureData.ValueForRupture valueForRupture, List<RuptureData> data) {
        double[] values = data.stream().mapToDouble(d -> d.calculateRuptureValues(valueForRupture)).toArray();
        data.sort(Comparator.comparing(d -> d.ruptureValue));
        this.percentiles = percentiles(values);
        this.data = this.percentiles.stream().map(this::findBestMatch).collect(Collectors.toList());
    }

    /**
     * Calculates interesting percentiles of the valueForRupture results.
     * These numbers are then used to pick the ruptures we want to draw.
     * @param values
     * @return
     */
    public List<Double> percentiles(double[] values) {
        List<Double> percentiles = List.of(0.0, 10.0, 25.0, 50.0, 75.0, 90.0, 95.0, 97.5, 99.0, 1.0);
        List<Double> percentileValues = percentiles.stream().map(p -> {
            if (p == 0) {
                return StatUtils.min(values);
            }
            if (p == 1) {
                return StatUtils.max(values);
            }
            return StatUtils.percentile(values, p);

        }).collect(Collectors.toList());

        return percentileValues;
    }

    /**
     * Find the best rupture match for a ruptureValue. This is used to select a rupture to represent a percentile.
     * @param ruptureValue
     * @return
     */
    public RuptureData findBestMatch(double ruptureValue) {
        RuptureData result = data.get(0);
        for (RuptureData candidate : data) {
            if (candidate.ruptureValue < ruptureValue) {
                result = candidate;
            } else if (candidate.ruptureValue == ruptureValue) {
                return candidate;
            } else {
                if (Math.abs(candidate.ruptureValue - ruptureValue) < Math.abs(result.ruptureValue - ruptureValue)) {
                    return candidate;
                } else {
                    return result;
                }
            }
        }
        return result;
    }
}
