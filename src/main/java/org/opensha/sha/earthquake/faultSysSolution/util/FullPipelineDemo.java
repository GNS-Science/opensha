package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.swing.text.DateFormatter;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.CoulombRupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.RupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.WaterLevelRates;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen.PlotLevel;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.inversion.UCERF3InversionInputGenerator;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.simulatedAnnealing.SerialSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.SimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.ProgressTrackingCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.params.GenerationFunctionType;
import scratch.UCERF3.simulatedAnnealing.params.NonnegativityConstraintType;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;

class FullPipelineDemo {

	public static void main(String[] args) throws Exception {
//		System.out.println("Yawn...");
//		long minute = 1000l*60l;
//		long hour = minute*60l;
//		Thread.sleep(8l*hour + 0l*minute);
//		System.out.println("Im awake! "+new Date());
		
		File markdownDirDir = new File("/home/kevin/markdown/inversions");
		Preconditions.checkState(markdownDirDir.exists() || markdownDirDir.mkdir());
		int threads = 32;
		
		U3LogicTreeBranch branch = U3LogicTreeBranch.DEFAULT;
		
		FaultModels fm = branch.getValue(FaultModels.class);
		ScalingRelationships scale = branch.getValue(ScalingRelationships.class);
		
		String dirName = new SimpleDateFormat("yyyy_MM_dd").format(new Date());
		
		String newName = "Coulomb Rups, U3 Ref Branch";
//		dirName += "-coulomb-u3_ref-perturb_uniform_1e-4-avg_anneal-wl1e-4";
		SerialSimulatedAnnealing.exp_orders_of_mag = 10;
		String minScaleStr = new DecimalFormat("0E0").format(
				Math.pow(10, SerialSimulatedAnnealing.max_exp-SerialSimulatedAnnealing.exp_orders_of_mag)).toLowerCase();
		String scaleStr = "perturb_exp_scale_1e-2_to_"+minScaleStr;
		dirName += "-coulomb-u3_ref-"+scaleStr+"-avg_anneal_20m-noWL-tryZeroRates-24hr";
		System.out.println(dirName);
		CoulombRupSetConfig rsConfig = new RuptureSets.CoulombRupSetConfig(fm, scale);
//		rsConfig.setAdaptiveSectFract(0.3f);
//		String newName = "U3 Reproduction";
//		dirName += "-u3_ref-quick-test";
//		RupSetConfig rsConfig = new RuptureSets.U3RupSetConfig(fm , scale);
//		FaultSystemSolution compSol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_ucerf3.zip"));
		FaultSystemSolution compSol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/UCERF3/rup_sets/modular/"
				+ "FM3_1_ZENGBB_Shaw09Mod_DsrTap_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3.zip"));
		String compName =  "UCERF3";
		
//		double wlFract = 1e-3;
		double wlFract = 0d;
		boolean wlAsStarting = false;
//		double wlFract = 1e-2;
//		boolean wlAsStarting = true;
		
		GenerationFunctionType perturb = GenerationFunctionType.EXPONENTIAL_SCALE;
//		GenerationFunctionType perturb = GenerationFunctionType.UNIFORM_NO_TEMP_DEPENDENCE;
//		NonnegativityConstraintType nonNeg = NonnegativityConstraintType.LIMIT_ZERO_RATES; // default
		NonnegativityConstraintType nonNeg = NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN;

		CompletionCriteria completion = TimeCompletionCriteria.getInHours(24);
//		CompletionCriteria completion = TimeCompletionCriteria.getInMinutes(30);
		CompletionCriteria avgSubCompletion = TimeCompletionCriteria.getInMinutes(20);
//		CompletionCriteria avgSubCompletion = TimeCompletionCriteria.getInSeconds(5);
//		CompletionCriteria avgSubCompletion = null;
		int threadsPerAvg = 4;
		
		int numRuns = 1;
		
		boolean rebuildRupSet = false;
		boolean rerunInversion = true;
		boolean doRupSetReport = false;
		
		FaultSystemRupSet rupSet = null;
		
		for (int run=0; run<numRuns; run++) {
			String myDirName = dirName;
			if (numRuns > 1)
				myDirName += "-run"+run;
			File outputDir = new File(markdownDirDir, myDirName);
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
			
			File rupSetFile = new File(outputDir, "rupture_set.zip");
			
			if (rupSet == null) {
				if (!rebuildRupSet && rupSetFile.exists()) {
					rupSet = FaultSystemRupSet.load(rupSetFile);
				} else {
					rupSet = rsConfig.build(threads);
					// configure as UCERF3
					rupSet = FaultSystemRupSet.buildFromExisting(rupSet).forU3Branch(branch).build();
				}
			}
			// write it out
			if (rebuildRupSet || !rupSetFile.exists())
				rupSet.write(rupSetFile);
			
			Thread reportThread = null;
			if (doRupSetReport) {
				// write a full rupture set report in the background
				ReportMetadata rupMeta = new ReportMetadata(new RupSetMetadata(newName, rupSet), new RupSetMetadata("UCERF3", compSol));
				ReportPageGen rupSetReport = new ReportPageGen(rupMeta, new File(outputDir, "rup_set_report"),
						ReportPageGen.getDefaultRupSetPlots(PlotLevel.DEFAULT));
				reportThread = new Thread(new Runnable() {
					
					@Override
					public void run() {
						try {
							rupSetReport.generatePage();
							System.out.println("Done with rupture set report");
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});
				reportThread.start();
			}
			
			File solFile = new File(outputDir, "solution.zip");
			
			FaultSystemSolution sol;
			if (rerunInversion || !solFile.exists()) {
				// now build inputs
				InversionTargetMFDs targetMFDs = rupSet.requireModule(InversionTargetMFDs.class);
				UCERF3InversionConfiguration config = UCERF3InversionConfiguration.forModel(
						branch.getValue(InversionModels.class), rupSet, fm, targetMFDs);
				config.setMinimumRuptureRateFraction(wlFract);
				if (wlAsStarting && wlFract > 0) {
					double[] initial = Arrays.copyOf(config.getMinimumRuptureRateBasis(), rupSet.getNumRuptures());
					for (int i=0; i<initial.length; i++)
						initial[i] *= wlFract;
					config.setInitialRupModel(initial);
					config.setMinimumRuptureRateFraction(0d);
				}
				
				// get the paleo rate constraints
				List<PaleoRateConstraint> paleoRateConstraints = CommandLineInversionRunner.getPaleoConstraints(
						fm, rupSet);

				// get the improbability constraints
				double[] improbabilityConstraint = null; // null for now

				// paleo probability model
				PaleoProbabilityModel paleoProbabilityModel = UCERF3InversionInputGenerator.loadDefaultPaleoProbabilityModel();

				List<AveSlipConstraint> aveSlipConstraints = AveSlipConstraint.load(rupSet.getFaultSectionDataList());

				UCERF3InversionInputGenerator inputGen = new UCERF3InversionInputGenerator(
						rupSet, config, paleoRateConstraints, aveSlipConstraints, improbabilityConstraint, paleoProbabilityModel);
				
				System.out.println("Generating inputs");
				inputGen.generateInputs();
				inputGen.columnCompress();
				
				ProgressTrackingCompletionCriteria progress = new ProgressTrackingCompletionCriteria(completion);
				TimeCompletionCriteria subCompletion = TimeCompletionCriteria.getInSeconds(1);
				
				ThreadedSimulatedAnnealing tsa;
				if (avgSubCompletion != null) {
					Preconditions.checkState(threadsPerAvg < threads);
					
					int threadsLeft = threads;
					
					// arrange lower-level (actual worker) SAs
					List<SimulatedAnnealing> tsas = new ArrayList<>();
					while (threadsLeft > 0) {
						int myThreads = Integer.min(threadsLeft, threadsPerAvg);
						if (myThreads > 1)
							tsas.add(new ThreadedSimulatedAnnealing(inputGen.getA(), inputGen.getD(),
									inputGen.getInitialSolution(), 0d, inputGen.getA_ineq(), inputGen.getD_ineq(), myThreads, subCompletion));
						else
							tsas.add(new SerialSimulatedAnnealing(inputGen.getA(), inputGen.getD(),
									inputGen.getInitialSolution(), 0d, inputGen.getA_ineq(), inputGen.getD_ineq()));
						threadsLeft -= myThreads;
					}
					tsa = new ThreadedSimulatedAnnealing(tsas, avgSubCompletion);
					tsa.setAverage(true);
				} else {
					tsa = new ThreadedSimulatedAnnealing(inputGen.getA(), inputGen.getD(),
							inputGen.getInitialSolution(), 0d, inputGen.getA_ineq(), inputGen.getD_ineq(), threads, subCompletion);
				}
				
				progress.setConstraintRanges(inputGen.getConstraintRowRanges());
				tsa.setConstraintRanges(inputGen.getConstraintRowRanges());
				tsa.setPerturbationFunc(perturb);
				tsa.setNonnegativeityConstraintAlgorithm(nonNeg);
				tsa.iterate(progress);
				
				double[] rawSol = tsa.getBestSolution();
				double[] rates = inputGen.adjustSolutionForWaterLevel(rawSol);
				
				sol = new FaultSystemSolution(rupSet, rates);
				// add sub-seismo MFDs
				sol.addModule(targetMFDs.getOnFaultSubSeisMFDs());
				// add grid source provider
				sol.setGridSourceProvider(new UCERF3_GridSourceGenerator(sol, branch.getValue(SpatialSeisPDF.class),
						branch.getValue(MomentRateFixes.class), targetMFDs,
						sol.requireModule(SubSeismoOnFaultMFDs.class),
						branch.getValue(MaxMagOffFault.class).getMaxMagOffFault(),
						rupSet.requireModule(FaultGridAssociations.class)));
				// add inversion progress
				sol.addModule(progress.getProgress());
				// add water level rates
				if (inputGen.getWaterLevelRates() != null)
					sol.addModule(new WaterLevelRates(inputGen.getWaterLevelRates()));
				if (inputGen.hasInitialSolution())
					sol.addModule(new InitialSolution(inputGen.getInitialSolution()));
				
				// write solution
				sol.write(solFile);
			} else {
				sol = FaultSystemSolution.load(solFile);
			}
			
			if (reportThread != null) {
				try {
					reportThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			// write solution report
			String myName = newName;
			if (run > 0)
				myName += " Run #"+(run+1);
			ReportMetadata solMeta = new ReportMetadata(new RupSetMetadata(myName, sol), new RupSetMetadata(compName, compSol));
			ReportPageGen solReport = new ReportPageGen(solMeta, new File(outputDir, "sol_report"),
					ReportPageGen.getDefaultSolutionPlots(PlotLevel.FULL));
			solReport.generatePage();
			
			if (run == 0) {
				compSol = sol;
				compName = "Run #1";
			}
		}
		System.out.println("DONE");
		System.exit(0);
	}
	
	
}