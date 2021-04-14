package org.correlation;

import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;

public class SpearmanCorrelation implements QPPCorrelationMetric {
    @Override
    public double correlation(double[] a, double[] b) {
        return new SpearmansCorrelation().correlation(a, b);
    }

    @Override
    public String name() {
        return "rho";
    }
}
