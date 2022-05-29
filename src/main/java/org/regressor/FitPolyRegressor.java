package org.regressor;

import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.correlation.MinMaxNormalizer;

/**
 *
 * @author suchana
 */

public class FitPolyRegressor {
    WeightedObservedPoints obs;
    PolynomialCurveFitter  fitter;
    int                    degree;
    double[]               coeff;
    
    public FitPolyRegressor(int degree) {
        obs = new WeightedObservedPoints();
        this.degree = degree;
    }   
    
    public double[] fitCurve (double [] gt, double [] pred) {
        double[] n_gt = MinMaxNormalizer.normalize(gt); // in [0, 1]
        double[] n_pred = MinMaxNormalizer.normalize(pred); // in [0, 1]
        for(int i = 0; i < n_gt.length; i++) {
            obs.add(n_gt[i], n_pred[i]);
        }
        fitter = PolynomialCurveFitter.create(degree);
        coeff = fitter.fit(obs.toList());
        return coeff;
    }
}
