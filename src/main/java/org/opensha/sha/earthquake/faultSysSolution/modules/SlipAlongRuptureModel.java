package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

public abstract class SlipAlongRuptureModel implements ArchivableModule {

	public static SlipAlongRuptureModel forModel(SlipAlongRuptureModels slipAlong) {
		return slipAlong.getModel();
	}
	
	/**
	 * This gives the slip (SI untis: m) on each section for the rth rupture
	 * @return slip (SI untis: m) on each section for the rth rupture
	 */
	public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, AveSlipModule aveSlips, int rthRup) {
		return calcSlipOnSectionsForRup(rupSet, rthRup, aveSlips.getAveSlip(rthRup));
	}
	
	/**
	 * This gives the slip (SI untis: m) on each section for the rth rupture
	 * @return slip (SI untis: m) on each section for the rth rupture
	 */
	public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup, double aveSlip) {
		List<Integer> sectionIndices = rupSet.getSectionsIndicesForRup(rthRup);
		int numSects = sectionIndices.size();

		// compute rupture area
		double[] sectArea = new double[numSects];
		double[] sectMoRate = new double[numSects];
		int index=0;
		for(Integer sectID: sectionIndices) {	
			//				FaultSectionPrefData sectData = getFaultSectionData(sectID);
			//				sectArea[index] = sectData.getTraceLength()*sectData.getReducedDownDipWidth()*1e6;	// aseismicity reduces area; 1e6 for sq-km --> sq-m
			sectArea[index] = rupSet.getAreaForSection(sectID);
			sectMoRate[index] = FaultMomentCalc.getMoment(sectArea[index], rupSet.getSlipRateForSection(sectID));
			index += 1;
		}
		
		return calcSlipOnSectionsForRup(rupSet, rthRup, sectArea, sectMoRate, aveSlip);
	}
	
	/**
	 * This gives the slip (SI untis: m) on each section for the rth rupture where the participating section areas,
	 * moment rates, and rupture average slip are all known.
	 * 
	 * @param rupSet
	 * @param rthRup
	 * @param sectArea
	 * @param sectMoRate
	 * @param aveSlip
	 * @return slip (SI untis: m) on each section for the rth rupture
	 */
	public abstract double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet,
			int rthRup, double[] sectArea, double[] sectMoRate, double aveSlip);
	
	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		// do nothing (no serialization required, just must be listed)
	}

	@Override
	public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		// do nothing (no deserialization required, just must be listed)
	}

	public static class Uniform extends SlipAlongRuptureModel {
		
		public Uniform() {}

		@Override
		public String getName() {
			return "Uniform Slip Along Rupture";
		}

		@Override
		public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup,
				double[] sectArea, double[] sectMoRate, double aveSlip) {
			double[] slipsForRup = new double[sectArea.length];
			
			for(int s=0; s<slipsForRup.length; s++)
				slipsForRup[s] = aveSlip;
			
			return slipsForRup;
		}
		
	}
	
	private static EvenlyDiscretizedFunc taperedSlipPDF, taperedSlipCDF;
	
	public static class Tapered extends SlipAlongRuptureModel {
		
		public Tapered() {}

		@Override
		public String getName() {
			return "Tapered Slip Along Rupture";
		}

		@Override
		public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup,
				double[] sectArea, double[] sectMoRate, double aveSlip) {
			double[] slipsForRup = new double[sectArea.length];
			
			// note that the ave slip is partitioned by area, not length; this is so the final model is moment balanced.

			// make the taper function if hasn't been done yet
			if(taperedSlipCDF == null) {
				synchronized (SlipAlongRuptureModel.class) {
					if (taperedSlipCDF == null) {
						EvenlyDiscretizedFunc taperedSlipCDF = new EvenlyDiscretizedFunc(0, 5001, 0.0002);
						EvenlyDiscretizedFunc taperedSlipPDF = new EvenlyDiscretizedFunc(0, 5001, 0.0002);
						double x,y, sum=0;
						int num = taperedSlipPDF.size();
						for(int i=0; i<num;i++) {
							x = taperedSlipPDF.getX(i);
							y = Math.pow(Math.sin(x*Math.PI), 0.5);
							taperedSlipPDF.set(i,y);
							sum += y;
						}
						// now make final PDF & CDF
						y=0;
						for(int i=0; i<num;i++) {
							y += taperedSlipPDF.getY(i);
							taperedSlipCDF.set(i,y/sum);
							taperedSlipPDF.set(i,taperedSlipPDF.getY(i)/sum);
//							System.out.println(taperedSlipCDF.getX(i)+"\t"+taperedSlipPDF.getY(i)+"\t"+taperedSlipCDF.getY(i));
						}
						SlipAlongRuptureModel.taperedSlipCDF = taperedSlipCDF;
						SlipAlongRuptureModel.taperedSlipPDF = taperedSlipPDF;
					}
				}
			}
			double normBegin=0, normEnd, scaleFactor;
			for(int s=0; s<slipsForRup.length; s++) {
				normEnd = normBegin + sectArea[s]/rupSet.getAreaForRup(rthRup);
				// fix normEnd values that are just past 1.0
				//					if(normEnd > 1 && normEnd < 1.00001) normEnd = 1.0;
				if(normEnd > 1 && normEnd < 1.01) normEnd = 1.0;
				scaleFactor = taperedSlipCDF.getInterpolatedY(normEnd)-taperedSlipCDF.getInterpolatedY(normBegin);
				scaleFactor /= (normEnd-normBegin);
				Preconditions.checkState(normEnd>=normBegin, "End is before beginning!");
				Preconditions.checkState(aveSlip >= 0, "Negative ave slip: "+aveSlip);
				slipsForRup[s] = aveSlip*scaleFactor;
				normBegin = normEnd;
			}
			
			return slipsForRup;
		}
		
	}
	
	public static class WG02 extends SlipAlongRuptureModel {
		
		public WG02() {}

		@Override
		public String getName() {
			return "WG02 Slip Along Rupture";
		}

		@Override
		public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup,
				double[] sectArea, double[] sectMoRate, double aveSlip) {
			double[] slipsForRup = new double[sectArea.length];
			
			List<Integer> sectsInRup = rupSet.getSectionsIndicesForRup(rthRup);
			double totMoRateForRup = 0;
			for(Integer sectID:sectsInRup) {
				double area = rupSet.getAreaForSection(sectID);
				totMoRateForRup += FaultMomentCalc.getMoment(area, rupSet.getSlipRateForSection(sectID));
			}
			for(int s=0; s<slipsForRup.length; s++) {
				slipsForRup[s] = aveSlip*sectMoRate[s]*rupSet.getAreaForRup(rthRup)/(totMoRateForRup*sectArea[s]);
			}
			
			return slipsForRup;
		}
		
	}
	
	public static class AVG_UCERF3 extends SlipAlongRuptureModel {
		
		public AVG_UCERF3() {}

		@Override
		public String getName() {
			return "Mean UCERF3 Slip Along Rupture";
		}

		@Override
		public double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup,
				double[] sectArea, double[] sectMoRate, double aveSlip) {
			double[] slipsForRup = new double[sectArea.length];
			
			// get mean weights
			List<Double> meanWeights = new ArrayList<>();
			List<SlipAlongRuptureModels> meanSALs = new ArrayList<>();

			double sum = 0;
			for (SlipAlongRuptureModels sal : SlipAlongRuptureModels.values()) {
				double weight = sal.getRelativeWeight(null);
				if (weight > 0) {
					meanWeights.add(weight);
					meanSALs.add(sal);
					sum += weight;
				}
			}
			if (sum != 0)
				for (int i=0; i<meanWeights.size(); i++)
					meanWeights.set(i, meanWeights.get(i)/sum);

			// calculate mean

			for (int i=0; i<meanSALs.size(); i++) {
				double weight = meanWeights.get(i);
				double[] subSlips = meanSALs.get(i).getModel().calcSlipOnSectionsForRup(
						rupSet, rthRup, sectArea, sectMoRate, aveSlip);

				for (int j=0; j<slipsForRup.length; j++)
					slipsForRup[j] += weight*subSlips[j];
			}
			
			return slipsForRup;
		}
		
	}

}
