package org.correlation;

import java.util.Map;

public class QuantizedSimCorrelation implements QPPCorrelationMetric {
    int numIntervals;
    double delta;

    public QuantizedSimCorrelation(int numIntervals) {
        this.numIntervals = numIntervals;
        delta = 1/(double)numIntervals;
    }

    int[] quantizeInUnitInterval(double[] x) {
        int[] x_quantized = new int[x.length];
        int i = 0;
        for (double x_i: x) {
            x_quantized[i++] = (int) (x_i/delta);
        }
        return x_quantized;
    }

    @Override
    public double correlation(double[] a, double[] b) {
        int[] q_a = quantizeInUnitInterval(MinMaxNormalizer.normalize(a)); // in [0, 1]
        int[] q_b = quantizeInUnitInterval(MinMaxNormalizer.normalize(b));

        return 1 - l2Dist(q_a, q_b);
    }

    public double l2Dist(int[] x, int[] y) {
        int dist = 0;
        System.out.println("Num intervals : " + numIntervals);
        System.out.println("Delta : " + delta);

        int maxDist = numIntervals * x.length; // sqrt(dim) is the length between {0}^dim and {1}^dim

        for (int i=0; i < x.length; i++) {
            dist += Math.abs(x[i]-y[i]);
        }
        if (dist > maxDist) {
            System.err.println("Unexpected problem in normalizing distance.");
            System.exit(1);
        }
        return dist/(double)maxDist; // normalized dist
    }
    
    @Override
    public String name() {
        return "qsim";
    }
}
