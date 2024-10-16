package scratch.UCERF3.griddedSeismicity;

import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider.Abstract;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.rupForecastImpl.PointSource13b;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis.Point2Vert_FaultPoisSource;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.FocalMech;

import com.google.common.collect.Maps;

import scratch.UCERF3.utils.GardnerKnopoffAftershockFilter;

/**
 * Grid source provider used in UCERF3, with UCERF3-specific declustering and point source representations.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public abstract class AbstractGridSourceProvider extends GridSourceProvider.Abstract implements ArchivableModule {

	private static final WC1994_MagLengthRelationship magLenRel = new WC1994_MagLengthRelationship();
	private static final double ptSrcCutoff = 6.0;
	
	public static double SOURCE_MIN_MAG_CUTOFF = 5.05;

	protected AbstractGridSourceProvider() {
		super(SOURCE_MIN_MAG_CUTOFF);
	}

	@Override
	public String getName() {
		return "UCERF3 Grid Source Provider";
	}

	private static final double[] DEPTHS = new double[] {5.0, 1.0};

	@Override
	protected ProbEqkSource buildSource(int gridIndex, IncrementalMagFreqDist mfd, double duration, BackgroundRupType bgRupType) {
		Location loc = getGriddedRegion().locationForIndex(gridIndex);
		
		double fracStrikeSlip = getFracStrikeSlip(gridIndex);
		double fracNormal = getFracNormal(gridIndex);
		double fracReverse = getFracReverse(gridIndex);

		return buildSource(mfd, duration, bgRupType, loc, fracStrikeSlip, fracNormal, fracReverse);
	}
	
	@Override
	public void applyAftershockFilter(IncrementalMagFreqDist mfd) {
		applyGK_AftershockFilter(mfd);
	}

	public static ProbEqkSource buildSource(IncrementalMagFreqDist mfd, double duration, BackgroundRupType bgRupType,
			Location loc, double fracStrikeSlip, double fracNormal, double fracReverse) {
		switch (bgRupType) {
		case CROSSHAIR:
			return new Point2Vert_FaultPoisSource(loc, mfd, magLenRel, duration,
					ptSrcCutoff, fracStrikeSlip, fracNormal,
					fracReverse, true);
		case FINITE:
			return new Point2Vert_FaultPoisSource(loc, mfd, magLenRel, duration,
					ptSrcCutoff, fracStrikeSlip, fracNormal,
					fracReverse, false);
		case POINT:
			Map<FocalMech, Double> mechMap = Maps.newHashMap();
			mechMap.put(FocalMech.STRIKE_SLIP, fracStrikeSlip);
			mechMap.put(FocalMech.REVERSE, fracReverse);
			mechMap.put(FocalMech.NORMAL, fracNormal);
			return new PointSource13b(loc, mfd, duration, DEPTHS, mechMap);

		default:
			throw new IllegalStateException("Unknown Background Rup Type: "+bgRupType);
		}
	}
	
	/*
	 * Applies gardner Knopoff aftershock filter scaling to MFD in place.
	 */
	public static void applyGK_AftershockFilter(IncrementalMagFreqDist mfd) {
		double scale;
		for (int i=0; i<mfd.size(); i++) {
			scale = GardnerKnopoffAftershockFilter.scaleForMagnitude(mfd.getX(i));
			mfd.set(i, mfd.getY(i) * scale);
		}
	}

	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		new Precomputed(this).writeToArchive(zout, entryPrefix);
	}

	@Override
	public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		throw new IllegalStateException("This should not be called (loaded as Precomputed instance)");
	}

	@Override
	public Class<? extends ArchivableModule> getLoadingClass() {
		return Precomputed.class;
	}
	
	@Override
	public void scaleAllMFDs(double[] valuesArray) {
		if(valuesArray.length != getGriddedRegion().getNodeCount())
			throw new RuntimeException("Error: valuesArray must have same length as getGriddedRegion().getNodeCount()");
		for(int i=0;i<valuesArray.length;i++) {
			if(valuesArray[i] != 1.0) {
				IncrementalMagFreqDist mfd = getMFD_Unassociated(i);
				if(mfd != null)
					mfd.scale(valuesArray[i]);;
				mfd = getMFD_SubSeisOnFault(i);				
				if(mfd != null)
					mfd.scale(valuesArray[i]);;
			}
		}
	}

	public static final String ARCHIVE_GRID_REGION_FILE_NAME = "grid_region.geojson";
	public static final String ARCHIVE_MECH_WEIGHT_FILE_NAME = "grid_mech_weights.csv";
	public static final String ARCHIVE_SUB_SEIS_FILE_NAME = "grid_sub_seis_mfds.csv";
	public static final String ARCHIVE_UNASSOCIATED_FILE_NAME = "grid_unassociated_mfds.csv";

	public static class Precomputed extends GridSourceProvider.AbstractPrecomputed {
		
		private Precomputed() {
			super(SOURCE_MIN_MAG_CUTOFF);
		}
		
		public Precomputed(GridSourceProvider prov) {
			super(prov, SOURCE_MIN_MAG_CUTOFF);
		}

		public Precomputed(GriddedRegion region, CSVFile<String> subSeisCSV, CSVFile<String> unassociatedCSV,
				CSVFile<String> mechCSV) {
			super(region, subSeisCSV, unassociatedCSV, mechCSV, SOURCE_MIN_MAG_CUTOFF);
		}

		public Precomputed(GriddedRegion region, Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
				double[] fracReverse) {
			super(region, nodeSubSeisMFDs, nodeUnassociatedMFDs, fracStrikeSlip, fracNormal, fracReverse, SOURCE_MIN_MAG_CUTOFF);
		}

		@Override
		public String getName() {
			return "Precomputed UCERF3 Grid Source Provider";
		}

		@Override
		public void applyAftershockFilter(IncrementalMagFreqDist mfd) {
			applyGK_AftershockFilter(mfd);
		}

		@Override
		protected ProbEqkSource buildSource(int gridIndex, IncrementalMagFreqDist mfd, double duration,
				BackgroundRupType bgRupType) {
			Location loc = getGriddedRegion().locationForIndex(gridIndex);
			
			double fracStrikeSlip = getFracStrikeSlip(gridIndex);
			double fracNormal = getFracNormal(gridIndex);
			double fracReverse = getFracReverse(gridIndex);

			return AbstractGridSourceProvider.buildSource(
					mfd, duration, bgRupType, loc, fracStrikeSlip, fracNormal, fracReverse);
		}

		@Override
		public GridSourceProvider newInstance(Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
				double[] fracReverse) {
			// TODO Auto-generated method stub
			return new Precomputed(getGriddedRegion(), nodeSubSeisMFDs, nodeUnassociatedMFDs,
					fracStrikeSlip, fracNormal, fracReverse);
		}
		
	}

}
