/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.regressor;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.correlation.MinMaxNormalizer;

/**
 *
 * @author suchana
 */

public class FitLinearRegressor {
    SimpleRegression re;
    
    public FitLinearRegressor() {
        re = new SimpleRegression();
    }   
    
    public void fit(double[] pred, double[] gt) {
        double[] n_pred = MinMaxNormalizer.normalize(pred); // in [0, 1]
        for (int i=0; i<gt.length; i++) {
            re.addData(n_pred[i], gt[i]);
        }
    }
    
    public double getSlope() {
        return re.getSlope();
    }
    public double getIntercept() {
        return re.getIntercept();
    }
}
