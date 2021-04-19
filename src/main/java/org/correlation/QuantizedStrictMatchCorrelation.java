package org.correlation;

public class QuantizedStrictMatchCorrelation extends QuantizedSimCorrelation {

    public QuantizedStrictMatchCorrelation(int numIntervals) {
        super(numIntervals);
    }

    @Override
    public double correlation(double[] a, double[] b) {
        int[] q_a = quantizeInUnitInterval(MinMaxNormalizer.normalize(a)); // in [0, 1]
        int[] q_b = quantizeInUnitInterval(MinMaxNormalizer.normalize(b));

        int nMatches = 0;
        for (int i=0; i < q_a.length; i++) {
            if (q_a[i] == q_b[i])
                nMatches++;
        }
        return nMatches/(double)q_a.length;
    }

    @Override
    public String name() {
        return "qsim_strict";
    }
}
