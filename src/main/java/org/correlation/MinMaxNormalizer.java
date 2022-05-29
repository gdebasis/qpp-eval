package org.correlation;
import java.util.Arrays;
import java.util.Map;

public class MinMaxNormalizer {
    public static double normalize(double x, double min, double max) {
        return (x-min)/(max-min);
    }

    public static double[] normalize(double[] x) {
        double[] z = new double[x.length];
        double min = Arrays.stream(x).min().getAsDouble();
        double max = Arrays.stream(x).max().getAsDouble();
        double diff = max - min;
        if (max - min == 0) {
            System.err.println("Values of max and min identical for maxmin normalization");
            System.exit(1);
        }

        for (int i=0; i < x.length; i++) {
            z[i] = (x[i]-min)/diff;
        }
        return z;
    }
}
