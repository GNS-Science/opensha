package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import org.apache.commons.math3.stat.StatUtils;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.opensha.commons.gui.plot.GraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.report.MultiRupturePlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupCartoonGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

public class MultiRuptureStiffnessPlot extends AbstractRupSetPlot {

    // FIXME: get this from the rupset
    final static double STIFFNESS_THRESHOLD = 0.75;

    final static String PREFIX = "jointStiffness";
    DecimalFormat o3DF = new DecimalFormat("0.###");
    DecimalFormat o1DF = new DecimalFormat("0.#");

    AggregatedStiffnessCalculator stiffnessCalc;
    SectionDistanceAzimuthCalculator disAzCalc;
    FaultSystemRupSet rupSet;

    public void setStiffnessCalc(SubSectStiffnessCalculator stiffnessCalc) {
        this.stiffnessCalc = new AggregatedStiffnessCalculator(
                SubSectStiffnessCalculator.StiffnessType.CFF,
                stiffnessCalc,
                true,
                AggregatedStiffnessCalculator.AggregationMethod.FLATTEN,
                AggregatedStiffnessCalculator.AggregationMethod.SUM,
                AggregatedStiffnessCalculator.AggregationMethod.SUM,
                AggregatedStiffnessCalculator.AggregationMethod.SUM);
    }

    public static class RuptureProperties {
        public ClusterRupture rupture;
        public int index;
        public List<FaultSection> subduction;
        public List<FaultSection> crustal;
        public double subToCrustalStiffness;
        public double crustalToSubStiffness;
        public Map<String, String> plots = new HashMap<>();

        public RuptureProperties(
                ClusterRupture rupture,
                int index) {
            this.rupture = rupture;
            this.index = index;
        }
    }

    public RuptureProperties findBestMatch(List<RuptureProperties> properties, ToDoubleFunction<RuptureProperties> valueFn, double value) {
        RuptureProperties prop = properties.get(0);
        for (RuptureProperties candidate : properties) {
            double actualValue = valueFn.applyAsDouble(candidate);
            if (actualValue < value) {
                prop = candidate;
            } else if (actualValue == value) {
                return candidate;
            } else {
                if (Math.abs(actualValue - value) < Math.abs(valueFn.applyAsDouble(prop) - value)) {
                    return candidate;
                } else {
                    return prop;
                }
            }
        }
        return prop;
    }

    @Override
    public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources, String topLink) throws IOException {
        this.rupSet = rupSet;
        this.disAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
        RupCartoonGenerator.SectionCharacteristicsFunction rupToonFun = RupCartoonGenerator.sectCharFun;
        List<String> lines = new ArrayList<>();

        ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
        StiffnessCalcModule stiffness = rupSet.requireModule(StiffnessCalcModule.class);
        setStiffnessCalc(stiffness.getStiffnessCalculator());

        List<RuptureProperties> properties = new ArrayList<>();
        for (int i = 0; i < cRups.size(); i++) {
            properties.add(new RuptureProperties(cRups.get(i), i));
        }

        properties.parallelStream().forEach(property -> {
            property.subduction = property.rupture.clusters[0].subSects;
            property.crustal = property.rupture.splays.values().asList().get(0).buildOrderedSectionList();
            property.subToCrustalStiffness = stiffnessCalc.calc(property.subduction, property.crustal);
            property.crustalToSubStiffness = stiffnessCalc.calc(property.crustal, property.subduction);
        });

        lines.add("Stiffness measure in this section: " + stiffnessCalc.toString());
        lines.add("");

        lines.add("### Subduction to Crustal Stiffness");
        lines.add("");
        lines.addAll(plotForValue(properties, p -> p.subToCrustalStiffness, resourcesDir, relPathToResources, "subToCru"));

        lines.add("### Crustal to Subduction Stiffness");
        lines.add("");
        lines.addAll(plotForValue(properties, p -> p.crustalToSubStiffness, resourcesDir, relPathToResources, "cruToSub"));

        lines.add("### Difference in Stiffness based on source");
        lines.add("");
        lines.addAll(plotForValue(properties, p -> Math.abs(p.crustalToSubStiffness - p.subToCrustalStiffness), resourcesDir, relPathToResources, "subToCru"));

        lines.add("### Number of sections");
        lines.add("");
        lines.addAll(plotForValue(properties, p -> p.crustal.size() + p.subduction.size(), resourcesDir, relPathToResources, "subToCru"));

        List<RuptureProperties> subductionOnly = properties.stream().
                filter(p -> p.subToCrustalStiffness >= STIFFNESS_THRESHOLD && p.crustalToSubStiffness < STIFFNESS_THRESHOLD).
                collect(Collectors.toList());

        if (!subductionOnly.isEmpty()) {
            lines.add("### Only Subduction As Source Above Stiffness Threshold of " + o3DF.format(STIFFNESS_THRESHOLD));
            lines.add("Ruptures where subduction as source is above threshold and crustal as source is below threshold.");
            lines.add("");
            lines.addAll(plotForValue(subductionOnly, p -> p.crustal.size() + p.subduction.size(), resourcesDir, relPathToResources, "subToCru"));

        }

        List<RuptureProperties> crustalOnly = properties.stream().
                filter(p -> p.subToCrustalStiffness < STIFFNESS_THRESHOLD && p.crustalToSubStiffness >= STIFFNESS_THRESHOLD).
                collect(Collectors.toList());

        if (!crustalOnly.isEmpty()) {
            lines.add("### Only Crustal As Source Above Stiffness Threshold of " + o3DF.format(STIFFNESS_THRESHOLD));
            lines.add("Ruptures where crustal as source is above threshold and subduction as source is below threshold.");
            lines.add("");
            lines.addAll(plotForValue(crustalOnly, p -> p.crustal.size() + p.subduction.size(), resourcesDir, relPathToResources, "cruToSub"));
        }

        List<RuptureProperties> both = properties.stream().
                filter(p -> p.subToCrustalStiffness >= STIFFNESS_THRESHOLD && p.crustalToSubStiffness >= STIFFNESS_THRESHOLD).
                collect(Collectors.toList());

        if (!both.isEmpty()) {
            lines.add("### Both Crustal and Subduction As Source Are Above Stiffness Threshold of " + o3DF.format(STIFFNESS_THRESHOLD));
            lines.add("Ruptures where both crustal as source is above threshold and subduction as source is above threshold.");
            lines.add("");
            lines.addAll(plotForValue(both, p -> p.crustal.size() + p.subduction.size(), resourcesDir, relPathToResources, "subToCru"));
        }
        RupCartoonGenerator.sectCharFun = rupToonFun;
        return lines;
    }

    static String formatPercentile(double value) {
        if (value == 0) {
            return "Min: ";
        }
        if (value == 1) {
            return "Max: ";
        }
        return "P" + (int) value + ": ";
    }

    protected List<String> plotForValue(List<RuptureProperties> properties,
                                        ToDoubleFunction<RuptureProperties> valueFn,
                                        File resourcesDir,
                                        String relPathToResources,
                                        String thumbnail) throws IOException {

        double[] values = properties.stream().mapToDouble(valueFn).toArray();

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
        properties.sort(Comparator.comparing(valueFn::applyAsDouble));

        MarkdownUtils.TableBuilder table = MarkdownUtils.tableBuilder();

        table.initNewLine();
        for (int i = 0; i < percentiles.size(); i++) {
            table.addColumn(formatPercentile(percentiles.get(i)) + o3DF.format(percentileValues.get(i)));
        }
        table.finalizeLine();

        table.initNewLine();
        for (double value : percentileValues) {
            RuptureProperties prop = findBestMatch(properties, valueFn, value);
            String relOutputDir = PREFIX + prop.index;
            File outputDir = new File(resourcesDir, relOutputDir);
            outputDir.mkdirs();
            makeRupturePage(prop, outputDir);
            File relPageDir = new File(relPathToResources, relOutputDir);
            table.addColumn("[<img src=\"" + new File(relPageDir, prop.plots.get(thumbnail)) + "\" />](" + new File(relPageDir, "index.html") + ")");
        }
        table.finalizeLine();

        return table.wrap(5, 0).build();
    }

    protected List<String> getParentNames(ClusterRupture rupture) {
        List<String> results = new ArrayList<>();
        rupture.getClustersIterable().forEach(cluster -> results.add(cluster.parentSectionName));
        return results;
    }

    protected void makeRupturePage(RuptureProperties prop, File outputDir) throws IOException {
        String rupTitle = "Rupture " + prop.index + " ";

        // set up plots

        List<MultiRupturePlot> allPlots = new ArrayList<>();
        MultiRupturePlot subToCruPlot = new MultiRupturePlot("SubAsSource", prop.rupture, rupTitle + "(subduction as source)", MultiRupturePlot.subductionAsSource(stiffnessCalc, prop), stiffnessCalc.toString());
        MultiRupturePlot cruToSubPlot = new MultiRupturePlot("CruAsSource", prop.rupture, rupTitle + "(crustal as source)", MultiRupturePlot.crustalAsSource(stiffnessCalc, prop), stiffnessCalc.toString());
        List<Double> maxDistances = List.of(5.0, 15.0, 50.0, 100.0, 200.0);
        List<MultiRupturePlot> subDistancePlots = new ArrayList<>();
        for (Double maxDist : maxDistances) {
            subDistancePlots.add(new MultiRupturePlot("SubAsSource" + maxDist, prop.rupture, rupTitle + "(subduction as source) " + maxDist + "km", MultiRupturePlot.subductionAsSource(stiffnessCalc, prop, maxDist, disAzCalc), stiffnessCalc.toString()));
        }
        List<MultiRupturePlot> cruDistancePlots = new ArrayList<>();
        for (Double maxDist : maxDistances) {
            cruDistancePlots.add(new MultiRupturePlot("CruAsSource" + maxDist, prop.rupture, rupTitle + "(crustal as source) " + maxDist + "km", MultiRupturePlot.crustalAsSource(stiffnessCalc, prop, maxDist, disAzCalc), stiffnessCalc.toString()));
        }
        allPlots.add(subToCruPlot);
        allPlots.add(cruToSubPlot);
        allPlots.addAll(subDistancePlots);
        allPlots.addAll(cruDistancePlots);

        prop.plots.put("subToCru", subToCruPlot.getFileName());
        prop.plots.put("cruToSub", cruToSubPlot.getFileName());

        // work out CPT to be able to use the same one across all plots

        double[] values = allPlots.stream().flatMapToDouble(p -> DoubleStream.of(p.min, p.max)).toArray();

        double min = Arrays.stream(values).min().getAsDouble();
        double max = Arrays.stream(values).max().getAsDouble();
        if (min >= 0 && min <= 1 && max >= 0 && max <= 1) {
            min = 0;
            max = 1;
        }
        if (min < 0 && max > 0) {
            double dist = Math.max(-min, max);
            min = -dist;
            max = dist;
        }
        CPT cpt = min >= 0 ?
                GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(min, max) :
                GMT_CPT_Files.DIVERGENT_RYB.instance().reverse().rescale(min, max);

        // create all plots

        allPlots.forEach(p -> {
            try {
                p.plot(outputDir, cpt);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // create markdown

        List<String> lines = new ArrayList<>();

        lines.add("## Rupture " + prop.index);
        lines.add("");
        lines.add("Stiffness data: " + stiffnessCalc.toString());
        lines.add("");

        MarkdownUtils.TableBuilder table = MarkdownUtils.tableBuilder();
        table.addLine("Subduction to crustal stiffness", "Crustal to subduction stiffness");
        table.initNewLine();
        table.addColumn("Subduction sections in this column show the stiffness of the section as source and all crustal sections as receivers. Crustal sections show the stiffness of the section as sole receiver with all subduction sections as source.");
        table.addColumn("Subduction sections in this column show the stiffness of the section as receiver and all crustal sections as source. Crustal sections show the stiffness of the section as sole source with all subduction sections as receivers.");
        table.finalizeLine();
        table.addLine("![subToCruPlot](" + subToCruPlot.getFileName() + ")", "![cruToSubPlot](" + cruToSubPlot.getFileName() + ")");
        table.addLine(
                subToCruPlot.getStats() + "Total: " + o3DF.format(prop.subToCrustalStiffness),
                cruToSubPlot.getStats() + "Total: " + o3DF.format(prop.crustalToSubStiffness));
        lines.addAll(table.wrap(2, 1).build());

        lines.add("");
        lines.add("- Crustal sections: " + prop.crustal.size());
        lines.add("- Subduction sections: " + prop.subduction.size());
        lines.add("- Rake: " + o1DF.format(rupSet.getAveRakeForRup(prop.index)));
        lines.add("");
        lines.add("#### Parent Faults");
        for (String parent : getParentNames(prop.rupture)) {
            lines.add("- " + parent);
        }
        lines.add("");

        table = MarkdownUtils.tableBuilder();
        table.initNewLine();
        for (Double maxDist : maxDistances) {
            table.addColumn("MaxDist: " + maxDist);
        }
        table.finalizeLine();
        table.initNewLine();
        for (MultiRupturePlot plot : subDistancePlots) {
            table.addColumn("![maxdist](" + plot.getFileName() + ")");
        }
        table.finalizeLine();
        table.initNewLine();
        for (int i = 0; i < maxDistances.size(); i++) {
            table.addColumn(subDistancePlots.get(i).getStats() + "Total: " + o3DF.format(calc(prop.subduction, prop.crustal, maxDistances.get(i))));
        }
        table.finalizeLine();

        table.initNewLine();
        for (MultiRupturePlot plot : cruDistancePlots) {
            table.addColumn("![maxdist](" + plot.getFileName() + ")");
        }
        table.finalizeLine();
        table.initNewLine();
        for (int i = 0; i < maxDistances.size(); i++) {
            table.addColumn(cruDistancePlots.get(i).getStats() + "Total: " + o3DF.format(calc(prop.crustal, prop.subduction, maxDistances.get(i))));
        }
        table.finalizeLine();

        lines.addAll(table.wrap(5, 1).build());

        MarkdownUtils.writeHTML(lines, new File(outputDir, "index.html"));

    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return List.of();
    }

    @Override
    public String getName() {
        return "MultiRuptureStiffnessPlot";
    }

    public interface ValueForSection {
        double getValue(FaultSection section);
    }

    public String plot(File outputDir, String prefix, ClusterRupture rup, String title, ValueForSection valueForSection) throws IOException {

        double[] values = rup.buildOrderedSectionList().stream().mapToDouble(valueForSection::getValue).toArray();
        double min = Arrays.stream(values).min().getAsDouble();
        double max = Arrays.stream(values).max().getAsDouble();

        if (min >= 0 && min <= 1 && max >= 0 && max <= 1) {
            min = 0;
            max = 1;
        }
        if (min < 0 && max > 0) {
            double dist = Math.max(-min, max);
            min = -dist;
            max = dist;
        }

        CPT cpt = min >= 0 ?
                GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(min, max) :
                GMT_CPT_Files.DIVERGING_RAINBOW.instance().rescale(min, max);

        RupCartoonGenerator.sectCharFun = (section, traceChar, outlineChar) -> {
            List<PlotCurveCharacterstics> chars = new ArrayList<>();
            chars.add(traceChar);

            if (section.getSectionName().contains("row:")) {
                double stiffness = valueForSection.getValue(section);
                chars.add(null);
                if (stiffness != 0) {
                    chars.add(new PlotCurveCharacterstics(PlotLineType.POLYGON_SOLID, 1, cpt.getColor((float) stiffness)));
                } else {
                    chars.add(null);
                }
            } else {
                double stiffness = valueForSection.getValue(section);
                chars.add(outlineChar);
                chars.add(new PlotCurveCharacterstics(PlotLineType.POLYGON_SOLID, 1, cpt.getColor((float) stiffness)));
            }
            return chars;
        };
        PlotSpec spec = RupCartoonGenerator.buildRupturePlot(rup, title, false, true);

        double tickUnit = min == 0 && max == 1 ? 0.25 : (max - min) / 4;
        PaintScaleLegend cptLegend = GraphPanel.getLegendForCPT(cpt, "stiffness",
                22, 18, tickUnit, RectangleEdge.BOTTOM);

        spec.addSubtitle(cptLegend);

        RupCartoonGenerator.plotRupture(outputDir, prefix, spec, true);
        return prefix + ".png";
    }

    protected double calc(Collection<? extends FaultSection> sources, Collection<? extends FaultSection> receivers, double maxJumpDist) {
        if (maxJumpDist == 0) {
            return stiffnessCalc.calc(sources, receivers);
        }
        double sum = 0;
        double count = 0;
        for (FaultSection source : sources) {
            List<FaultSection> closeReceivers = receivers.stream().
                    filter(s -> disAzCalc.getDistance(source, s) <= maxJumpDist).
                    collect(Collectors.toList());
            if (!closeReceivers.isEmpty()) {
                sum += stiffnessCalc.calc(List.of(source), closeReceivers);
                count++;
            }
        }
        return sum / count;
    }

    public static void main(String[] args) throws IOException {
        File file = new File("C:\\tmp\\mergedRupset_5km_cffPatch2km_cff0.75IntsPos_cffNetPositiveBOTH.zip");
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(file);
        ReportMetadata meta = new ReportMetadata(new RupSetMetadata(file.getName(), rupSet));
        List<AbstractRupSetPlot> plots = List.of(new MultiRuptureStiffnessPlot());
        ReportPageGen report = new ReportPageGen(meta, new File("/tmp/reports/stiffness4-cetr3"), plots);
        report.generatePage();
    }
}
