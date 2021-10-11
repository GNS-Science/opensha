/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;
import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import static org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.Stirling_2021_SimplifiedNZ_FaultRegime.*;


/**
 * Implements the simplified relations as provided by Mark Stirling for the 2022 New Zealand NSHM
 * 
 * @version 0.0
 */


public class Stirling_2021_SimplifiedNZ_MagAreaRel extends MagAreaRelationship {
	public final static String NAME = "SimplifiedScalingNZNSHM_2021";
	/**
	 * Regime is either CRUSTAL or INTERFACE  
	 */
	protected Stirling_2021_SimplifiedNZ_FaultRegime faultRegime = CRUSTAL;
	protected Stirling_2021_SimplifiedNZ_FaultRegime faultType = NONE;
	protected Stirling_2021_SimplifiedNZ_FaultRegime epistemicBound = MEAN;

	public Stirling_2021_SimplifiedNZ_MagAreaRel(){
		super();
	}

	public Stirling_2021_SimplifiedNZ_MagAreaRel(double initalRake, String initialEpistemicBound){
		super();
		setRake(initalRake);
		setEpistemicBound(initialEpistemicBound);
	}

	public Stirling_2021_SimplifiedNZ_MagAreaRel(String initialRegime, String initialEpistemicBound){
		super();
		setRegime(initialRegime);
		setEpistemicBound(initialEpistemicBound);
	}

	public Stirling_2021_SimplifiedNZ_MagAreaRel(double inititalRake, String initialRegime, String initialEpistemicBound){
		super();
		setRake(inititalRake);
		setRegime(initialRegime);
		setEpistemicBound(initialEpistemicBound);
	}

	/* *
	 * @param rake
	 */
	public void setRake(double rake) {
		super.setRake(rake);
		this.faultType = Stirling_2021_SimplifiedNZ_FaultRegime.fromRake(rake);
	}

	/* *
	 * @param regime
	 */
	public void setRegime(String regime) {
		this.faultRegime = Stirling_2021_SimplifiedNZ_FaultRegime.fromRegime(regime);
	}

	public String getRegime(){
		return faultRegime.name();
	}

	/* *
	 * @param epistemic Bound
	 */
	public void setEpistemicBound(String epistemicBound) {
		this.epistemicBound = Stirling_2021_SimplifiedNZ_FaultRegime.fromEpistemicBound(epistemicBound);
	}

	public String getEpistemicBound(){
		return epistemicBound.name();
	}

	/**
	 * Computes the median magnitude from rupture area 
	 *
	 * @param area in km^2
	 * @return median magnitude MW
	 */
	public double getMedianMag(double area) {
		return getC4log10A2Mw() + Math.log(area) * lnToLog;
	}

	/**
	 * Gives the standard deviation for the magnitude as a function of area for
	 * previously-set rake values
	 *
	 * @return standard deviation
	 */
	public double getMagStdDev() {
		return Double.NaN;
	}

	/**
	 * Computes the median rupture area from magnitude 
	 *
	 * @param mag - moment magnitude
	 * @return median area in km^2
	 */
	public double getMedianArea(double mag) {
		return Math.pow(10.0, -getC4log10A2Mw() + mag);
	}

	/**
	 * Computes the standard deviation of log(area) (base-10) 
	 * @return standard deviation
	 */
	public double getAreaStdDev() {
		return Double.NaN;
	}

	/**
	 * Mw = log10A + C
	 * @return C
	 */

	private double getC4log10A2Mw() {

		//rhat AKA cValue => perhaps, we refactor rhat as cvalue
		Double rhat = Double.NaN;
		if (faultRegime == CRUSTAL || faultRegime == NONE) {
			if (faultType == NONE || epistemicBound == NONE) {
				return Double.NaN;
			} else if (faultType == STRIKE_SLIP && epistemicBound == MEAN) {
				rhat = 4.0;
			} else if (faultType == STRIKE_SLIP && epistemicBound == LOWER) {
				rhat = 3.65;
			} else if (faultType == STRIKE_SLIP && epistemicBound == UPPER) {
				rhat= 4.30;
			} else if (faultType == REVERSE_FAULTING && epistemicBound == MEAN) {
				rhat = 4.13;
			}	else if (faultType == REVERSE_FAULTING && epistemicBound == LOWER) {
				rhat= 3.95;
			} else if (faultType ==  REVERSE_FAULTING && epistemicBound == UPPER) {
				rhat= 4.30;
			} else if (faultType ==  NORMAL_FAULTING && epistemicBound == MEAN) {
				rhat = 4.13;
			} else if (faultType ==  NORMAL_FAULTING && epistemicBound == LOWER) {
				rhat = 3.95;
			} else if (faultType == NORMAL_FAULTING && epistemicBound == UPPER) {
				rhat = 4.30;
			}
		}
		else if (faultRegime == SUBDUCTION_INTERFACE){
			if (epistemicBound == MEAN) {
				rhat = 3.85;
			} else if (epistemicBound == LOWER) {
				rhat = 3.60;
			} else if (epistemicBound == UPPER) {
				rhat= 4.10;
			} else if (epistemicBound == NONE) {
				return Double.NaN;
			}
		}
		return rhat;
	}

	/**
	 * Returns the name of the object
	 */
	public String getName() {

		return NAME + " " + faultType.toString() + " " + faultRegime.toString() + " " + epistemicBound.toString();
	}
}
