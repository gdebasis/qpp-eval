package org.correlation;

public interface QPPCorrelationMetric {
    public double correlation(double[] a, double[] b);
    public String name();
}
