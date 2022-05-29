package org.correlation;

import java.util.Map;

public interface QPPCorrelationMetric {
    public double correlation(double[] a, double[] b);
    public String name();
}
