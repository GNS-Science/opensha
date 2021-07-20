package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.AnyWithinDistConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ConnectionPointsRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

public class SectBySectDetailPlots extends AbstractRupSetPlot {
	
	private double maxNeighborDistance;

	public SectBySectDetailPlots() {
		this(Double.NaN);
	}
	
	public SectBySectDetailPlots(double maxNeighborDistance) {
		this.maxNeighborDistance = maxNeighborDistance;
		
	}

	@Override
	public String getName() {
		return "Parent Section Detail Pages";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		SectionDistanceAzimuthCalculator distAzCalc = rupSet.getModule(SectionDistanceAzimuthCalculator.class);
		if (distAzCalc == null) {
			distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
			rupSet.addModule(distAzCalc);
		}
		
		if (Double.isNaN(maxNeighborDistance)) {
			PlausibilityConfiguration config = rupSet.getModule(PlausibilityConfiguration.class);
			if (config == null || config.getConnectionStrategy() == null) {
				System.out.println(getName()+": WARNING, no maximum jump distance specified & no connection strategy. "
						+ "Will include everything up to 20 km.");
				maxNeighborDistance = 20d;
			} else {
				maxNeighborDistance = config.getConnectionStrategy().getMaxJumpDist();
			}
		}
		
		if (!rupSet.hasModule(ClusterRuptures.class))
			rupSet.addModule(ClusterRuptures.singleStranged(rupSet));
		if (meta.comparison != null && !meta.comparison.rupSet.hasModule(ClusterRuptures.class))
			meta.comparison.rupSet.addModule(ClusterRuptures.singleStranged(meta.comparison.rupSet));
		
		Map<Integer, List<FaultSection>> sectsByParent = rupSet.getFaultSectionDataList().stream().collect(
				Collectors.groupingBy(S -> S.getParentSectionId()));
		
		File parentsDir = new File(resourcesDir.getParentFile(), "parent_sect_pages");
		Preconditions.checkState(parentsDir.exists() || parentsDir.mkdir());
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(rupSet, meta.region);
		Map<String, String> linksMap = new HashMap<>();
		for (int parentID : sectsByParent.keySet()) {
			String parentName = sectsByParent.get(parentID).get(0).getParentSectionName();
			String subDirName = buildSectionPage(meta, parentID, parentName, parentsDir, distAzCalc,
					sectsByParent, mapMaker);
			
			linksMap.put(parentName, relPathToResources+"/../"+parentsDir.getName()+"/"+subDirName);
		}
		
		List<String> sortedNames = new ArrayList<>(linksMap.keySet());
		Collections.sort(sortedNames);
		
		TableBuilder table = buildSectLinksTable(linksMap, sortedNames);

		return table.build();
	}

	public TableBuilder buildSectLinksTable(Map<String, String> linksMap, List<String> sortedNames) {
		return buildSectLinksTable(linksMap, sortedNames, null);
	}

	public TableBuilder buildSectLinksTable(Map<String, String> linksMap, List<String> sortedNames,
			Map<String, Boolean> highlights) {
		int cols;
		if (sortedNames.size() > 30)
			cols = 3;
		else if (sortedNames.size() > 15)
			cols = 2;
		else
			cols = 1;
		int rows = (int)Math.ceil((double)sortedNames.size()/(double)cols);
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		for (int c=0; c<cols; c++)
			table.addColumn("Fault Section");
		table.finalizeLine();
		for (int row=0; row<rows; row++) {
			table.initNewLine();
			for (int col=0; col<cols; col++) {
				int index = rows*col+row;
				if (index >= sortedNames.size()) {
					table.addColumn("");
				} else {
					String name = sortedNames.get(index);
					if (highlights != null && highlights.get(name))
						table.addColumn("[**"+name+"**]("+linksMap.get(name)+")");
					else
						table.addColumn("["+name+"]("+linksMap.get(name)+")");
				}
			}
			table.finalizeLine();
		}
		return table;
	}
	
	private static PlotCurveCharacterstics highlightChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 5f, Color.BLACK);
	
	private String buildSectionPage(ReportMetadata meta, int parentSectIndex, String parentName, File parentsDir,
			SectionDistanceAzimuthCalculator distAzCalc, Map<Integer, List<FaultSection>> sectsByParent,
			RupSetMapMaker mapMaker) throws IOException {
		System.out.println("Building page for: "+parentName);
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		String dirName = getFileSafe(parentName);
		
		File outputDir = new File(parentsDir, dirName);
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		List<String> lines = new ArrayList<>();
		lines.add("# "+parentName+" Details");
		
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		
		List<FaultSection> parentSects = sectsByParent.get(parentSectIndex);
		
		if (meta.primary.sol != null) {
			lines.add("");
			lines.addAll(getMFDLines(meta, parentSectIndex, parentName, parentSects, outputDir, topLink));
			
			lines.add("");
			lines.addAll(getAlongStrikeLines(meta, parentSectIndex, parentName, parentSects, outputDir, topLink));
		}

		lines.add("");
		lines.addAll(getConnectivityLines(meta, parentSectIndex, parentName, distAzCalc, sectsByParent,
				mapMaker, rupSet, outputDir, topLink));
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 3));
		lines.add(tocIndex, "## Table Of Contents");
		
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
		
		return dirName;
	}

	public List<String> getConnectivityLines(ReportMetadata meta, int parentSectIndex, String parentName,
			SectionDistanceAzimuthCalculator distAzCalc, Map<Integer, List<FaultSection>> sectsByParent,
			RupSetMapMaker mapMaker, FaultSystemRupSet rupSet, File outputDir, String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		
		lines.add("## Connectivity");
		lines.add(topLink); lines.add("");
		
		List<FaultSection> mySects = sectsByParent.get(parentSectIndex);
		
		// include all nearby parents up to the cutoff distance
		List<Integer> parentsIDsToConsider = new ArrayList<>();
		Map<Integer, Double> matchMinDists = new HashMap<>();
		for (int parentID : sectsByParent.keySet()) {
			if (parentID == parentSectIndex)
				continue;
			double minDist = Double.POSITIVE_INFINITY;
			for (FaultSection s1 : sectsByParent.get(parentID))
				for (FaultSection s2 : mySects)
					minDist = Double.min(minDist, distAzCalc.getDistance(s1, s2));
			if (minDist <= maxNeighborDistance) {
				parentsIDsToConsider.add(parentID);
				matchMinDists.put(parentID, minDist);
			}
		}
		System.out.println("\t"+parentsIDsToConsider.size()+" parents are within "+(float)maxNeighborDistance+" km");
		
		ClusterRuptures clusterRups = rupSet.requireModule(ClusterRuptures.class);

		RupConnectionsData rupData = new RupConnectionsData(parentSectIndex, clusterRups, rupSet, meta.primary.sol);
		System.out.println("\t"+rupData.parentCoruptureCounts.size()+" parents ("+rupData.sectCoruptureCounts.size()
			+" sects) corupture with this parent");
		
		RupConnectionsData compRupData = null;
		if (meta.comparison != null)
			compRupData = new RupConnectionsData(parentSectIndex,
					meta.comparison.rupSet.requireModule(ClusterRuptures.class),
					meta.comparison.rupSet, meta.comparison.sol);
		
		HashSet<FaultSection> plotSectsSet = new HashSet<>();
		plotSectsSet.addAll(mySects);
		// add all sections within the cutoff
		for (int parentID : parentsIDsToConsider)
			plotSectsSet.addAll(sectsByParent.get(parentID));
		// add all sections that corupture
		for (int sectID : rupData.sectCoruptureCounts.keySet())
			plotSectsSet.add(rupSet.getFaultSectionData(sectID));
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (FaultSection plotSect : plotSectsSet) {
			for (Location loc : plotSect.getFaultTrace()) {
				latTrack.addValue(loc.lat);
				lonTrack.addValue(loc.lon);
			}
		}
		// also buffer around our trace
		for (FaultSection sect : mySects) {
			for (Location loc : new Region(sect.getFaultTrace(), maxNeighborDistance).getBorder()) {
				latTrack.addValue(loc.lat);
				lonTrack.addValue(loc.lon);
			}
		}
		Region plotRegion = new Region(new Location(latTrack.getMin()-0.1, lonTrack.getMin()-0.1),
				new Location(latTrack.getMax()+0.1, lonTrack.getMax()+0.1));
		
		mapMaker.setRegion(plotRegion);
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		List<String> jsonLinks = new ArrayList<>();
		for (boolean rate : new boolean[] {false, true}) {
			Map<Integer, ? extends Number> valsMap = rate ? rupData.sectCoruptureRates : rupData.sectCoruptureCounts;
			if (valsMap == null)
				continue;
			double[] scalars = new double[rupSet.getNumSections()];
			MinMaxAveTracker logValTrack = new MinMaxAveTracker();
			for (int s=0; s<scalars.length; s++) {
				if (valsMap.containsKey(s) && valsMap.get(s).doubleValue() > 0d) {
					double logVal = Math.log10(valsMap.get(s).doubleValue());
					logValTrack.addValue(logVal);
					scalars[s] = logVal;
				} else {
					scalars[s] = Double.NaN;
				}
			}
			double min, max;
			String label;
			String prefix;
			if (rate) {
				if (logValTrack.getNum() == 0) {
					min = -6;
					max = -1d;
				} else {
					min = Math.floor(logValTrack.getMin());
					max = Math.ceil(logValTrack.getMax());
				}
				label = "Log10 Co-rupture Rate";
				prefix = "corupture_rate";
			} else {
				if (logValTrack.getNum() == 0) {
					min = 0;
					max = 1;
				} else {
					min = Math.floor(logValTrack.getMin());
					max = Math.ceil(logValTrack.getMax());
				}
				label = "Log10 Co-rupture Count";
				prefix = "corupture_count";
			}
			if (min == max)
				max++;
			CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(min, max);
			cpt.setNanColor(Color.GRAY);
			mapMaker.clearSectScalars();
			mapMaker.plotSectScalars(scalars, cpt, label);
			mapMaker.highLightSections(mySects, highlightChar);
			mapMaker.setWriteGeoJSON(true);
			mapMaker.setWritePDFs(false);
			mapMaker.setSkipNaNs(true);
			mapMaker.plot(outputDir, prefix, parentName+" Connectivity");
			table.addColumn("![Map]("+prefix+".png)");
			
			jsonLinks.add(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", prefix+".geojson")
					+" [Download GeoJSON]("+prefix+".geojson)");
		}
		table.finalizeLine();
		table.addLine(jsonLinks);
		
		lines.addAll(table.build());
		lines.add("");
		
		lines.add("### Nearby Sections");
		lines.add(topLink); lines.add("");
		String nearbyLink = "[_(back to table)_](#"+MarkdownUtils.getAnchorName("Nearby Sections")+")";
		
		PlausibilityConfiguration config = rupSet.getModule(PlausibilityConfiguration.class);
		
		Map<String, String> linksMap = new HashMap<>();
		Map<String, List<String>> parentMarkdowns = new HashMap<>(); 
		Map<String, Boolean> parentConnecteds = new HashMap<>(); 
		Map<String, Double> parentDists = new HashMap<>();
		for (int parentID : parentsIDsToConsider) {
			List<FaultSection> parentSects = sectsByParent.get(parentID);
			String name = parentSects.get(0).getParentSectionName();
			double minDist = matchMinDists.get(parentID);
			
			name += ", "+optionalDigitDF.format(minDist)+" km away";
			
			List<String> connLines = new ArrayList<>();
			connLines.add("#### "+name);
			connLines.add(nearbyLink); connLines.add("");
			
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("");
			table.addColumn(meta.primary.name);
			if (meta.comparison != null)
				table.addColumn(meta.comparison.name);
			table.finalizeLine();
			
			parentDists.put(name, minDist);
			
			boolean connected = rupData.parentCoruptureCounts.containsKey(parentID);
			
			table.initNewLine();
			table.addColumn("**Connected?**");
			table.addColumn(connected);
			if (meta.comparison != null)
				table.addColumn(compRupData.parentCoruptureCounts.containsKey(parentID));
			table.finalizeLine();
			
			table.initNewLine();
			table.addColumn("**Directly Connected?**");
			table.addColumn(rupData.directlyConnectedParents.contains(parentID));
			if (meta.comparison != null)
				table.addColumn(compRupData.directlyConnectedParents.contains(parentID));
			table.finalizeLine();
			
			table.initNewLine();
			table.addColumn("**Co-rupture Count**");
			if (rupData.parentCoruptureCounts.containsKey(parentID))
				table.addColumn(countDF.format(rupData.parentCoruptureCounts.get(parentID)));
			else
				table.addColumn("0");
			if (meta.comparison != null) {
				if (compRupData.parentCoruptureCounts.containsKey(parentID))
					table.addColumn(countDF.format(compRupData.parentCoruptureCounts.get(parentID)));
				else
					table.addColumn("0");
			}
			table.finalizeLine();
			
			table.initNewLine();
			table.addColumn("**Co-rupture Rate**");
			if (rupData.parentCoruptureRates != null && rupData.parentCoruptureRates.containsKey(parentID))
				table.addColumn(rupData.parentCoruptureRates.get(parentID).floatValue());
			else
				table.addColumn("_N/A_");
			if (meta.comparison != null) {
				if (compRupData.parentCoruptureRates != null && compRupData.parentCoruptureRates.containsKey(parentID))
					table.addColumn(compRupData.parentCoruptureRates.get(parentID).floatValue());
				else
					table.addColumn("_N/A_");
			}
			table.finalizeLine();
			
			connLines.addAll(table.build());
			
			if (!connected && config != null && config.getFilters() != null && !config.getFilters().isEmpty()
					&& config.getConnectionStrategy() != null) {
				List<FaultSection> pairSects = new ArrayList<>();
				List<FaultSubsectionCluster> pairClusters = new ArrayList<>();
				pairSects.addAll(mySects);
				pairClusters.add(new FaultSubsectionCluster(mySects));
				pairSects.addAll(parentSects);
				pairClusters.add(new FaultSubsectionCluster(parentSects));
				ClusterConnectionStrategy pairStrat = new AnyWithinDistConnectionStrategy(
						pairSects, pairClusters, distAzCalc, maxNeighborDistance);
				
				PlausibilityConfiguration pairConfig = new PlausibilityConfiguration(
						List.of(new FullConnectionOneWayFilter(pairClusters.get(0), pairClusters.get(1))), 0, pairStrat, distAzCalc);
				ClusterRuptureBuilder builder = new ClusterRuptureBuilder(pairConfig);
				System.out.println("\tBuilding ruptures from "+parentName+" to "+name);
				List<ClusterRupture> possibleRups = builder.build(new ConnectionPointsRuptureGrowingStrategy());
				
				System.out.println("\tBuilt "+possibleRups.size()+" possible ruptures including "+name);
				
				List<Boolean> passes = new ArrayList<>();
				boolean hasPassAll = false;
				List<PlausibilityFilter> filters = config.getFilters();
				for (int p=0; p<filters.size(); p++)
					passes.add(false);
				
				for (ClusterRupture rup : possibleRups) {
					boolean passAll = true;
					for (int p=0; p<filters.size(); p++) {
						PlausibilityFilter filter = filters.get(p);
						PlausibilityResult result;
						try {
							result = filter.apply(rup, false);
						} catch (Exception e) {
							result = PlausibilityResult.FAIL_HARD_STOP;
						}
						if (result.isPass())
							passes.set(p, true);
						else
							passAll = false;
					}
					if (passAll)
						hasPassAll = true;
				}
				
				connLines.add("");
				connLines.add("### Plausibility Filters Comparisons");
				connLines.add("");
				connLines.add("Here, we try to figure out which plausibility filter(s) precluded connections "
						+ "between these two fault sections. This is not necessarily done using the exact same connection "
						+ "strategy (i.e., the algorithm that chooses where jumps occur), so results might not exactly "
						+ "match the original rupture building algorithm.");
				connLines.add("");
				connLines.add("Plausibility filters precluding direct connection:");
				connLines.add("");
				boolean hasNever = false;
				for (int p=0; p<filters.size(); p++) {
					PlausibilityFilter filter = filters.get(p);
					if (!passes.get(p)) {
						hasNever = true;
						connLines.add("* "+filter.getName());
					}
				}
				if (!hasNever) {
					if (hasPassAll)
						connLines.add("* _None found, and this test found a rupture that passed all filters. This may be "
								+ "due to the connection strategy: we try many connection points here and may have found "
								+ "one that works and was not included in the original rupture set, or no connections "
								+ "may have been allowed to this fault in the case of an adaptive strategy._");
					else
						connLines.add("* _Each filter passed for at least one possible rupture connecting these two "
								+ "sections, but no individual rupture between the two sections passed all filters "
								+ "simultaneously._");
				}
			}

			parentConnecteds.put(name, connected);
			linksMap.put(name, "#"+MarkdownUtils.getAnchorName(name));
			parentMarkdowns.put(name, connLines);
		}
		
		List<String> sortedNames = ComparablePairing.getSortedData(parentDists);
		
		lines.addAll(buildSectLinksTable(linksMap, sortedNames, parentConnecteds).build());
		lines.add("");
		for (String name : sortedNames)
			lines.addAll(parentMarkdowns.get(name));
		return lines;
	}
	
	private static class FullConnectionOneWayFilter implements PlausibilityFilter {

		private FaultSubsectionCluster from;
		private FaultSubsectionCluster to;
		
		private HashSet<FaultSection> validStarts;
		private HashSet<FaultSection> validEnds;

		public FullConnectionOneWayFilter(FaultSubsectionCluster from, FaultSubsectionCluster to) {
			this.from = from;
			this.to = to;
			validStarts = new HashSet<>();
			validStarts.add(from.startSect);
			validStarts.add(from.subSects.get(from.subSects.size()-1));
			validEnds = new HashSet<>();
			validEnds.add(to.startSect);
			validEnds.add(to.subSects.get(to.subSects.size()-1));
		}

		@Override
		public String getShortName() {
			return "One Way";
		}

		@Override
		public String getName() {
			return "One Way";
		}

		@Override
		public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
			if (rupture.clusters.length > 2)
				return PlausibilityResult.FAIL_HARD_STOP;
			FaultSubsectionCluster firstCluster = rupture.clusters[0];
			if (firstCluster.parentSectionID != from.parentSectionID)
				return PlausibilityResult.FAIL_HARD_STOP;
			// make sure it starts with an end
			if (!validStarts.contains(firstCluster.startSect))
				return PlausibilityResult.FAIL_HARD_STOP;
			if (rupture.clusters.length < 2)
				return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
			FaultSubsectionCluster secondCluster = rupture.clusters[1];
			if (secondCluster.parentSectionID != to.parentSectionID)
				return PlausibilityResult.FAIL_HARD_STOP;
			if (!validEnds.contains(secondCluster.subSects.get(secondCluster.subSects.size()-1)))
				return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
			return PlausibilityResult.PASS;
		}
		
	}
	
	private static class RupConnectionsData {
		
		private Map<Integer, Integer> parentCoruptureCounts;
		private HashSet<Integer> directlyConnectedParents;
		private Map<Integer, Double> parentCoruptureRates;
		private Map<Integer, Integer> sectCoruptureCounts;
		private Map<Integer, Double> sectCoruptureRates;
		
		public RupConnectionsData(int parentSectIndex, ClusterRuptures clusterRups,
				FaultSystemRupSet rupSet, FaultSystemSolution sol) {
			parentCoruptureCounts = new HashMap<>();
			directlyConnectedParents = new HashSet<>();
			parentCoruptureRates = sol == null ? null : new HashMap<>();
			sectCoruptureCounts = new HashMap<>();
			sectCoruptureRates = sol == null ? null : new HashMap<>();
			
			for (int rupIndex : rupSet.getRupturesForParentSection(parentSectIndex)) {
				ClusterRupture rup = clusterRups.get(rupIndex);
				double rate = sectCoruptureRates == null ? Double.NaN : sol.getRateForRup(rupIndex);
				RuptureTreeNavigator nav = rup.getTreeNavigator();
				for (FaultSubsectionCluster cluster : rup.getClustersIterable()) {
					if (cluster.parentSectionID == parentSectIndex) {
						FaultSubsectionCluster pred = nav.getPredecessor(cluster);
						if (pred != null)
							directlyConnectedParents.add(pred.parentSectionID);
						for (FaultSubsectionCluster desc : nav.getDescendants(cluster))
							directlyConnectedParents.add(desc.parentSectionID);
						continue;
					}
					if (parentCoruptureCounts.containsKey(cluster.parentSectionID)) {
						parentCoruptureCounts.put(cluster.parentSectionID, parentCoruptureCounts.get(cluster.parentSectionID)+1);
						if (parentCoruptureRates != null)
							parentCoruptureRates.put(cluster.parentSectionID, parentCoruptureRates.get(cluster.parentSectionID)+rate);
					} else {
						parentCoruptureCounts.put(cluster.parentSectionID, 1);
						if (parentCoruptureRates != null)
							parentCoruptureRates.put(cluster.parentSectionID, rate);
					}
					for (FaultSection sect : cluster.subSects) {
						Integer id = sect.getSectionId();
						if (sectCoruptureCounts.containsKey(id)) {
							sectCoruptureCounts.put(id, sectCoruptureCounts.get(id)+1);
							if (sectCoruptureRates != null)
								sectCoruptureRates.put(id, sectCoruptureRates.get(id)+rate);
						} else {
							sectCoruptureCounts.put(id, 1);
							if (sectCoruptureRates != null)
								sectCoruptureRates.put(id, rate);
						}
					}
				}
			}
		}
	}

	public List<String> getMFDLines(ReportMetadata meta, int parentSectIndex, String parentName,
			List<FaultSection> parentSects, File outputDir, String topLink) throws IOException {
		
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		FaultSystemSolution sol = meta.primary.sol;
		
		double minMag = Double.POSITIVE_INFINITY;
		double maxMag = Double.NEGATIVE_INFINITY;
		for (FaultSection sect : parentSects) {
			minMag = Math.min(minMag, rupSet.getMinMagForSection(sect.getSectionId()));
			maxMag = Math.max(maxMag, rupSet.getMaxMagForSection(sect.getSectionId()));
		}
		
		IncrementalMagFreqDist defaultMFD = SolMFDPlot.initDefaultMFD(minMag, maxMag);
		
		Range xRange = SolMFDPlot.xRange(defaultMFD);
		List<IncrementalMagFreqDist> incrFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		IncrementalMagFreqDist particMFD = sol.calcParticipationMFD_forParentSect(
				parentSectIndex, defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size());
		IncrementalMagFreqDist nuclMFD = sol.calcNucleationMFD_forParentSect(
				parentSectIndex, defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size());

		incrFuncs.add(particMFD);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, MAIN_COLOR));
		
		incrFuncs.add(nuclMFD);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 4f, MAIN_COLOR));
		
		if (meta.comparison != null && meta.comparison.sol != null) {
			IncrementalMagFreqDist compParticMFD = meta.comparison.sol.calcParticipationMFD_forParentSect(
					parentSectIndex, defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size());
			IncrementalMagFreqDist compNuclMFD = meta.comparison.sol.calcNucleationMFD_forParentSect(
					parentSectIndex, defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size());
			
			particMFD.setName("Primary Participation");
			nuclMFD.setName("Nucleation");
			compParticMFD.setName("Participation");
			compNuclMFD.setName("Nucleation");
			
			incrFuncs.add(compParticMFD);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, COMP_COLOR));
			
			incrFuncs.add(compNuclMFD);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 4f, COMP_COLOR));
		} else {
			particMFD.setName("Participation");
			nuclMFD.setName("Nucleation");
		}
		
		
		List<EvenlyDiscretizedFunc> cmlFuncs = new ArrayList<>();
		for (IncrementalMagFreqDist incrFunc : incrFuncs)
			cmlFuncs.add(incrFunc.getCumRateDistWithOffset());
		Range yRange = yRange(cmlFuncs, MFD_DEFAULT_Y_RANGE, MFD_MAX_Y_RANGE);
		if (yRange == null)
			return new ArrayList<>();
		
		PlotSpec incrSpec = new PlotSpec(incrFuncs, chars, parentName, "Magnitude", "Incremental Rate (per yr)");
		PlotSpec cmlSpec = new PlotSpec(cmlFuncs, chars, parentName, "Magnitude", "Cumulative Rate (per yr)");
		incrSpec.setLegendInset(true);
		cmlSpec.setLegendInset(true);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		gp.setTickLabelFontSize(20);
		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
		
		String prefix = "sect_mfd";
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		table.addLine("Incremental", "Cumulative");
		
		table.initNewLine();
		gp.drawGraphPanel(incrSpec, false, true, xRange, yRange);
		PlotUtils.writePlots(outputDir, prefix, gp, 1000, 850, true, true, true);
		table.addColumn("![Incremental Plot]("+prefix+".png)");
		
		prefix += "_cumulative";
		gp.drawGraphPanel(cmlSpec, false, true, xRange, yRange);
		PlotUtils.writePlots(outputDir, prefix, gp, 1000, 850, true, true, true);
		table.addColumn("![Cumulative Plot]("+prefix+".png)");
		table.finalizeLine();
		
		List<String> lines = new ArrayList<>();
		
		lines.add("## Magnitude-Frequency Distribution");
		lines.add(topLink); lines.add("");
		
		lines.addAll(table.build());
		
		return lines;
	}
	
	private static Range MFD_DEFAULT_Y_RANGE = new Range(1e-8, 1e-2);
	private static Range MFD_MAX_Y_RANGE = new Range(1e-10, 1e1);
	
	private static Range yRange(List<? extends XY_DataSet> funcs, Range defaultRange, Range maxRange) {
		double minNonZero = defaultRange.getLowerBound();
		double max = defaultRange.getUpperBound();
		int numNonZero = 0;
		for (XY_DataSet func : funcs) {
			for (Point2D pt : func) {
				if (pt.getY() > 0) {
					minNonZero = Double.min(minNonZero, pt.getY());
					numNonZero++;
				}
				max = Double.max(max, pt.getY());
			}
		}
		if (numNonZero == 0)
			return null;
		Preconditions.checkState(Double.isFinite(minNonZero));
		minNonZero = Math.pow(10, Math.floor(Math.log10(minNonZero)));
		max = Math.pow(10, Math.ceil(Math.log10(max)));
		if (maxRange != null) {
			minNonZero = Math.max(minNonZero, maxRange.getLowerBound());
			max = Math.min(max, maxRange.getUpperBound());
		}
		return new Range(minNonZero, max);
	}

	public List<String> getAlongStrikeLines(ReportMetadata meta, int parentSectIndex, String parentName,
			List<FaultSection> parentSects, File outputDir, String topLink) throws IOException {
		
		MinMaxAveTracker latRange = new MinMaxAveTracker();
		MinMaxAveTracker lonRange = new MinMaxAveTracker();
		
		for (FaultSection sect : parentSects) {
			for (Location loc : sect.getFaultTrace()) {
				latRange.addValue(loc.lat);
				lonRange.addValue(loc.lon);
			}
		}
		
		boolean latX = latRange.getLength() > 0.75*lonRange.getLength(); // prefer latitude
		String xLabel;
		Range xRange;
		if (latX) {
			xLabel = "Latitude (degrees)";
			xRange = new Range(latRange.getMin(), latRange.getMax());
		} else {
			xLabel = "Longitude (degrees)";
			xRange = new Range(lonRange.getMin(), lonRange.getMax());
		}
		
		List<XY_DataSet> emptySectFuncs = new ArrayList<>();
		double minMag = Double.POSITIVE_INFINITY;
		double maxMag = 0d;
		for (FaultSection sect : parentSects) {
			XY_DataSet func = new DefaultXY_DataSet();
			for (Location loc : sect.getFaultTrace()) {
				if (latX)
					func.set(loc.getLatitude(), 0d);
				else
					func.set(loc.getLongitude(), 0d);
			}
			emptySectFuncs.add(func);
			minMag = Math.min(minMag, meta.primary.rupSet.getMinMagForSection(sect.getSectionId()));
			maxMag = Math.max(maxMag, meta.primary.rupSet.getMaxMagForSection(sect.getSectionId()));
		}
		
		List<PlotSpec> specs = new ArrayList<>();
		List<Range> yRanges = new ArrayList<>();
		List<Boolean> yLogs = new ArrayList<>();
		
		// build event rate plot
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		List<PlotLineType> magLines = new ArrayList<>();
		List<Double> minMags = new ArrayList<>();
		minMags.add(0d);
		magLines.add(PlotLineType.SOLID);
		
		
		if (maxMag > 9) {
			if (minMag < 7 && maxMag > 7) {
				minMags.add(7d);
				magLines.add(PlotLineType.DASHED);
			}
			if (minMag < 8 && maxMag > 8) {
				minMags.add(8d);
				magLines.add(PlotLineType.DOTTED);
			}
			if (minMag < 9 && maxMag > 9) {
				minMags.add(9d);
				magLines.add(PlotLineType.DOTTED_AND_DASHED);
			}
		} else {
			if (minMag < 6 && maxMag > 6) {
				minMags.add(6d);
				magLines.add(PlotLineType.DASHED);
			}
			if (minMag < 7 && maxMag > 7) {
				minMags.add(7d);
				magLines.add(PlotLineType.DOTTED);
			}
			if (minMag < 8 && maxMag > 8) {
				minMags.add(8d);
				magLines.add(PlotLineType.DOTTED_AND_DASHED);
			}
		}
		List<String> magLabels = new ArrayList<>();
		for (double myMinMag : minMags) {
			if (myMinMag > 0d)
				magLabels.add("M≥"+optionalDigitDF.format(myMinMag));
			else
				magLabels.add("Supra-Seismogenic");
		}
		
		boolean comp = meta.comparison != null && meta.comparison.sol != null;
		boolean first = true;
		for (int s=0; s<parentSects.size(); s++) {
			FaultSection sect = parentSects.get(s);
			XY_DataSet emptyFunc = emptySectFuncs.get(s);
			for (int m=0; m<minMags.size(); m++) {
				double myMinMag = minMags.get(m);
				
				double rate = rateAbove(myMinMag, sect.getSectionId(), meta.primary.sol);
				XY_DataSet rateFunc = copyAtY(emptyFunc, rate);
				if (first) {
					if (comp && m == 0)
						rateFunc.setName("Primary "+magLabels.get(m));
					else
						rateFunc.setName(magLabels.get(m));
				}
				
				PlotLineType line = magLines.get(m);
				
				funcs.add(rateFunc);
				chars.add(new PlotCurveCharacterstics(line, 3f, MAIN_COLOR));
			}
			
			if (comp) {
				for (int m=0; m<minMags.size(); m++) {
					double myMinMag = minMags.get(m);
					
					double rate = rateAbove(myMinMag, sect.getSectionId(), meta.comparison.sol);
					XY_DataSet rateFunc = copyAtY(emptyFunc, rate);
					if (first) {
						if (comp && m == 0)
							rateFunc.setName("Comparison "+magLabels.get(m));
						else
							rateFunc.setName(magLabels.get(m));
					}
					
					PlotLineType line = magLines.get(m);
					
					funcs.add(rateFunc);
					chars.add(new PlotCurveCharacterstics(line, 3f, COMP_COLOR));
				}
			}
			
			first = false;
		}
		PlotSpec magRateSpec = new PlotSpec(funcs, chars, parentName, xLabel, "Participation Rate (per year)");
		// legend at bottom
		magRateSpec.setLegendInset(RectangleAnchor.BOTTOM_LEFT, 0.025, 0.025, 0.95, false);
		specs.add(magRateSpec);
		yRanges.add(yRange(funcs, new Range(1e-6, 1e-2), new Range(1e-8, 1e1)));
		yLogs.add(true);
		
		if (meta.primary.rupSet.hasModule(SlipAlongRuptureModel.class) && meta.primary.rupSet.hasModule(AveSlipModule.class)) {
			funcs = new ArrayList<>();
			chars = new ArrayList<>();
			
			SlipAlongRuptureModel slipAlongs = meta.primary.rupSet.getModule(SlipAlongRuptureModel.class);
			AveSlipModule aveSlips = meta.primary.rupSet.getModule(AveSlipModule.class);
			SectSlipRates slipRates = meta.primary.rupSet.getModule(SectSlipRates.class);
			
			double[] solSlipRates = new double[parentSects.size()];
			Map<Integer, Integer> sectIndMappings = new HashMap<>();
			for (int s=0; s<parentSects.size(); s++)
				sectIndMappings.put(parentSects.get(s).getSectionId(), s);
			for (int r : meta.primary.rupSet.getRupturesForParentSection(parentSectIndex)) {
				double rate = meta.primary.sol.getRateForRup(r);
				double[] slipOnSects = slipAlongs.calcSlipOnSectionsForRup(meta.primary.rupSet, aveSlips, r);
				List<Integer> sectsForRup = meta.primary.rupSet.getSectionsIndicesForRup(r);
				for (int i=0; i<sectsForRup.size(); i++) {
					int sectIndex = sectsForRup.get(i);
					Integer index = sectIndMappings.get(sectIndex);
					if (index != null)
						solSlipRates[index] += rate*slipOnSects[i];
				}
			}
			
			for (int s=0; s<parentSects.size(); s++) {
				XY_DataSet emptyFunc = emptySectFuncs.get(s);
				
				if (slipRates != null) {
					double targetSlip = slipRates.getSlipRate(s)*1e3;
					XY_DataSet targetFunc = copyAtY(emptyFunc, targetSlip);
					if (s == 0)
						targetFunc.setName("Target");
					
					funcs.add(targetFunc);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.CYAN.darker()));
				}
				
				XY_DataSet solFunc = copyAtY(emptyFunc, solSlipRates[s]*1e3);
				if (s == 0)
					solFunc.setName("Solution");
				
				funcs.add(solFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.MAGENTA.darker()));
			}
			PlotSpec slipRateSpec = new PlotSpec(funcs, chars, parentName, xLabel, "Slip Rate (mm/yr)");
			// legend at bottom
			if (slipRates != null)
				slipRateSpec.setLegendInset(RectangleAnchor.BOTTOM_LEFT, 0.025, 0.025, 0.95, false);
			specs.add(slipRateSpec);
			yRanges.add(yRange(funcs, new Range(1e-2, 1e2), new Range(1e-3, 1e3)));
			yLogs.add(true);
		}
		
		List<String> lines = new ArrayList<>();
		
		lines.add("## Along-Strike Values");
		lines.add(topLink); lines.add("");
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		gp.setTickLabelFontSize(20);
		
		List<Boolean> xLogs = List.of(false);
		List<Range> xRanges = List.of(xRange);
		
		gp.drawGraphPanel(specs, xLogs, yLogs, xRanges, yRanges);

		String prefix = "sect_along_strike";
		int height = 300 + 500*specs.size();
		PlotUtils.writePlots(outputDir, prefix, gp, 1000, height, true, true, false);
		lines.add("![Along-strike plot]("+prefix+".png)");
		
		return lines;
	}
	
	private static double rateAbove(double minMag, int sectIndex, FaultSystemSolution sol) {
		double rate = 0d;
		FaultSystemRupSet rupSet = sol.getRupSet();
		for (int rupIndex : rupSet.getRupturesForSection(sectIndex))
			if (rupSet.getMagForRup(rupIndex) >= minMag)
				rate += sol.getRateForRup(rupIndex);
		return rate;
	}
	
	private static XY_DataSet copyAtY(XY_DataSet func, double y) {
		DefaultXY_DataSet ret = new DefaultXY_DataSet();
		for (Point2D pt : func)
			ret.set(pt.getX(), y);
		return ret;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(ClusterRuptures.class);
	}
	
	public static void main(String[] args) throws IOException {
//		File inputFile = new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_ucerf3.zip");
		File inputFile = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/modular/"
				+ "FM3_1_ZENGBB_Shaw09Mod_DsrTap_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3.zip");
//		File inputFile = new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_plausibleMulti15km_adaptive6km_direct_"
//				+ "cmlRake360_jumpP0.001_slipP0.05incrCapDist_cff0.75IntsPos_comb2Paths_cffFavP0.01"
//				+ "_cffFavRatioN2P0.5_sectFractGrow0.1.zip");
		String name = "UCERF3";
		ZipFile zip = new ZipFile(inputFile);
		
		File outputDir = new File("/tmp/sect_by_sect");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		FaultSystemRupSet rupSet;
		FaultSystemSolution sol = null;
		if (FaultSystemSolution.isSolution(zip)) {
			sol = FaultSystemSolution.load(zip);
			rupSet = sol.getRupSet();
		} else {
			rupSet = FaultSystemRupSet.load(zip);
		}
		
		SectBySectDetailPlots plot = new SectBySectDetailPlots();
		
		plot.writePlot(rupSet, sol, name, outputDir);
	}

}
