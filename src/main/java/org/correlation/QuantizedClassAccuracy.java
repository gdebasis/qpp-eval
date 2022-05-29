package org.correlation;

import java.util.HashMap;
import java.util.Map;

public class QuantizedClassAccuracy implements QPPCorrelationMetric {
    
    int numIntervals;
    double delta;
    
    public QuantizedClassAccuracy(int numIntervals) {
        this.numIntervals = numIntervals;
        delta = 1/(double)numIntervals;
    }
    
    @Override
    public double correlation(double[] gt, double[] pred) {
        int[] q_a = quantizeInUnitInterval(MinMaxNormalizer.normalize(gt)); // in [0, 1]
        int[] q_b = quantizeInUnitInterval(MinMaxNormalizer.normalize(pred));
        return quantizedAccuracy(q_a, q_b);
    }
    
    int[] quantizeInUnitInterval(double[] x) {
        int[] quantizedValues = new int[x.length];
        int i = 0;
        for (double x_i: x) {
            quantizedValues[i++] = (int)(x_i/delta);
        }
        return quantizedValues;
    }
    
    public double quantizedAccuracy(int[] truth, int[] pred) {
        double acc = 0;
        for (int i=0; i < truth.length; i++) {
           int pred_label = pred[i];
           int ref_label = truth[i];
           acc += pred_label == ref_label? 1:0;
        }
        return acc/(double)truth.length;
    }
    
    @Override
    public String name() {
        return "classacc";
    }
}
