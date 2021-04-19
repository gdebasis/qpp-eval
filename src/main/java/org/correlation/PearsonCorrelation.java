package org.correlation;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

public class PearsonCorrelation implements QPPCorrelationMetric {
    @Override
    public double correlation(double[] a, double[] b) {
        return new PearsonsCorrelation().correlation(a, b);
    }

    @Override
    public String name() {
        return "r";
    }
}
