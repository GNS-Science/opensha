package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SupraSeisBValues;

import com.google.common.base.Preconditions;

public class PRVI25_LogicTreeBranch {
	
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsOnFault;
	
	/*
	 * Core FSS branch levels
	 */
	public static LogicTreeLevel<PRVI25_FaultModels> FM =
			LogicTreeLevel.forEnum(PRVI25_FaultModels.class, "Fault Model", "FM");
	public static LogicTreeLevel<PRVI25_DeformationModels> DM =
			LogicTreeLevel.forEnum(PRVI25_DeformationModels.class, "Deformation Model", "DM");
	public static LogicTreeLevel<NSHM23_ScalingRelationships> SCALE = NSHM23_LogicTreeBranch.SCALE;
	public static LogicTreeLevel<SupraSeisBValues> SUPRA_B = NSHM23_LogicTreeBranch.SUPRA_B;
	public static LogicTreeLevel<NSHM23_SegmentationModels> SEG = NSHM23_LogicTreeBranch.SEG;
	
	static {
		// exhaustive for now, can trim down later
		levelsOnFault = List.of(FM, DM, SCALE, SUPRA_B, SEG);
	}
	
	/**
	 * This is the default on-fault reference branch
	 */
	public static final LogicTreeBranch<LogicTreeNode> DEFAULT_ON_FAULT = fromValues(levelsOnFault,
			PRVI25_FaultModels.PRVI_FM_INITIAL,
			PRVI25_DeformationModels.GEOLOGIC,
			NSHM23_ScalingRelationships.LOGA_C4p2,
			SupraSeisBValues.B_0p5,
			NSHM23_SegmentationModels.MID);
	
	public static LogicTreeBranch<LogicTreeNode> fromValues(List<LogicTreeLevel<? extends LogicTreeNode>> levels, LogicTreeNode... vals) {
		Preconditions.checkState(levels.size() == vals.length);
		
		// initialize branch with null
		List<LogicTreeNode> values = new ArrayList<>();
		for (int i=0; i<levels.size(); i++)
			values.add(null);

		// now add each value
		for (LogicTreeNode val : vals) {
			if (val == null)
				continue;

			int ind = -1;
			for (int i=0; i<levelsOnFault.size(); i++) {
				LogicTreeLevel<?> level = levels.get(i);
				if (level.isMember(val)) {
					ind = i;
					break;
				}
			}
			Preconditions.checkArgument(ind >= 0, "Value of class '"+val.getClass()+"' does not match any known branch level");
			values.set(ind, val);
		}

		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels, values);

		return branch;
	}

}