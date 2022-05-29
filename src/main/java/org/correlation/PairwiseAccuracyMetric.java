package org.correlation;

import java.util.Map;

public class PairwiseAccuracyMetric implements QPPCorrelationMetric {

    boolean[][] computePairComparisons(double[] x) {
        boolean[][] cmp = new boolean[x.length][x.length];
        for (int i=0; i < x.length-1; i++) {
            for (int j=i+1; j < x.length; j++) {
                cmp[i][j] = x[i] <= x[j];
            }
        }
        return cmp;
    }

    @Override
    public double correlation(double[] pred, double[] ref) {
        int i, j;
        boolean[][] cmp_predicted = computePairComparisons(pred);
        boolean[][] cmp_ref = computePairComparisons(ref);

        int n = 0, c = 0;
        for (i=0; i < pred.length-1; i++) {
            for (j=i+1; j < pred.length; j++) {
                if (cmp_predicted[i][j] == cmp_ref[i][j])
                    c++;
                n++;
            }
        }
        return c/(double)n; // normalize by n(n-1)/2
    }
    
    @Override
    public String name() {
        return "pairacc";
    }
}
