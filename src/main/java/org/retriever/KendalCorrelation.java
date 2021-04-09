package org.retriever;

import org.apache.commons.math3.stat.correlation.KendallsCorrelation;

public class KendalCorrelation implements QPPCorrelationMetric {
    @Override
    public double correlation(double[] a, double[] b) {
        return new KendallsCorrelation().correlation(a, b);
    }

    @Override
    public String name() {
        return "tau";
    }
}
