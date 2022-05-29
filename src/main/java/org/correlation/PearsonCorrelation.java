package org.correlation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

public class PearsonCorrelation implements QPPCorrelationMetric {
    @Override
    public double correlation(double[] gt, double[] pred) {
        return new PearsonsCorrelation().correlation(gt, pred);
    }
    
    @Override
    public String name() {
        return "r";
    }
}
