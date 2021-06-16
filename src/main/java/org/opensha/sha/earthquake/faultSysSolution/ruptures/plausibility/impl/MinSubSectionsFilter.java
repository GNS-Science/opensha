package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

//public class MinRuptureSectionsFilter {
//
//}

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * To enforce a minimum number of subsections per rupture.
 * 
 * @author chrisch
 *
 */
public class MinSubSectionsFilter implements PlausibilityFilter {
	
	private int minSubSections;
	private int MIN_SECTS_PER_PARENT = 2; // by convention we have a minimum of 2 sub-sections per fault section.

	public MinSubSectionsFilter(int minSubSections) {
		this.minSubSections = minSubSections;
	}	
	
	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		PlausibilityResult result = apply(rupture.clusters, verbose);
		if (result.canContinue()) {
			for (Jump jump : rupture.splays.keySet()) {
				ClusterRupture splay = rupture.splays.get(jump);
				FaultSubsectionCluster[] strand = splay.clusters;
				result = result.logicalAnd(apply(strand, verbose));
			}
		}
		return result;
	}	

	private PlausibilityResult apply(FaultSubsectionCluster[] clusters, boolean verbose) {
		int sectionCount = 0;

		for (int i=0; i<clusters.length; i++) {
			sectionCount += clusters[i].subSects.size();
			if (sectionCount >= minSubSections)
				return PlausibilityResult.PASS;
		}
		
		//We could succeed if we add more clusters
		if ((clusters.length * MIN_SECTS_PER_PARENT) < minSubSections) {
			return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		} else {
			return PlausibilityResult.FAIL_HARD_STOP;
		}	
	}	
	
	@Override
	public String getShortName() {
		return "minRuptureSections";
	}

	@Override
	public String getName() {
		return "Minimum Rupture Sections";
	}

	@Override
	public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
		return new Adapter();
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		// never directional
		return false;
	}
	
	public static class Adapter extends PlausibilityFilterTypeAdapter {

		@Override
		public void init(ClusterConnectionStrategy connStrategy,
				SectionDistanceAzimuthCalculator distAzCalc, Gson gson) {
				//Do nothing
		}

		@Override
		public void write(JsonWriter out, PlausibilityFilter value) throws IOException {
			MinSubSectionsFilter filter = (MinSubSectionsFilter)value;
			out.beginObject();
			out.name("minRuptureSections").value(filter.minSubSections);
			out.endObject();
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			in.beginObject();
			Integer minSubSections = null;
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "minRuptureSections":
					minSubSections = in.nextInt();
					break;
				default:
					break;
				}
			}
			
			in.endObject();
			return new MinSubSectionsFilter(minSubSections);
		}
		
	}	
	
}
