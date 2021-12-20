package org.opensha.sha.imr.attenRelImpl.ngaw2;

import static org.junit.Assert.*;

import com.google.common.io.Resources;
import org.junit.Test;
import org.opensha.commons.data.CSVFile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.function.ToDoubleFunction;

/**
 * Test CSVs are copied from OpenQuake Engine test folder
 * https://github.com/gem/oq-engine/tree/master/openquake/hazardlib/tests/gsim/data/BSSA2014
 * See also
 * https://github.com/gem/oq-engine/blob/master/openquake/hazardlib/tests/gsim/boore_2014_test.py
 */

public class BSSA_2014Test {

    public final static String D_DIR = "data/";
    public final static String MEAN = "BSSA_2014_MEAN.csv";
    public final static String CA_MEAN = "BSSA_2014_CALIFORNIA_MEAN.csv"; // because it sets z1p0
    public final static String STD = "BSSA_2014_TOTAL_STD.csv";
    public final static String CA_STD = "BSSA_2014_CALIFORNIA_TOTAL_STD.csv"; // because it sets z1p0

    public CSVFile<String> loadCsv(String fileName) throws URISyntaxException, IOException {
        URL url = Resources.getResource(BSSA_2014Test.class, D_DIR + fileName);
        return CSVFile.readURL(url, false);
    }

    public static double get(CSVFile<String> csv, int row, int col) {
        return Double.parseDouble(csv.get(row, col));
    }

    public void testRow(CSVFile<String> csv, int row, ToDoubleFunction<BSSA_2014> function) {

        int firstDataCol = 6;

        //set up parameters from the first few columns
        BSSA_2014 bssa = new BSSA_2014();
        bssa.set_Mw(get(csv, row, 0));
        bssa.set_fault(NGAW2_Tests.getFaultStyleForRake(get(csv, row, 1)));
        bssa.set_rJB(get(csv, row, 2));
        bssa.set_vs30(get(csv, row, 3));

        if(csv.get(0, 4).equals("site_z1pt0")){
            firstDataCol++;
            bssa.set_z1p0(get(csv, row, 4));
        }

        // iterate over columns with expected values.
        // row 0 contains the IMT
        // the current row contains the expected result
        for (int col = firstDataCol; col < csv.getNumCols(); col++) {
            bssa.set_IMT(IMT.parseIMT(csv.get(0, col)));
            double expected = get(csv, row, col);
            assertEquals(expected, function.applyAsDouble(bssa), 0.0001);
        }
    }

    public void testFile(String fileName, ToDoubleFunction<BSSA_2014> function) throws URISyntaxException, IOException {
        CSVFile<String> csv = loadCsv(fileName);
        for (int row = 1; row < csv.getNumRows(); row++) {
            testRow(csv, row, function);
        }
    }

    @Test
    public void meanTest() throws URISyntaxException, IOException {
        testFile(MEAN, bssa -> Math.exp(bssa.calc().mean()));
    }

    @Test
    public void caMeanTest() throws URISyntaxException, IOException {
        testFile(CA_MEAN, bssa -> Math.exp(bssa.calc().mean()));
    }

    @Test
    public void stdvTest() throws URISyntaxException, IOException {
        testFile(STD, bssa -> bssa.calc().stdDev());
    }

    @Test
    public void caStdvTest() throws URISyntaxException, IOException {
        testFile(CA_STD, bssa -> bssa.calc().stdDev());
    }

}
