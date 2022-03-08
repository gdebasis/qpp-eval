package org.qpp;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.evaluator.RetrievedResults;

import java.io.IOException;
import java.util.Arrays;

public class NQCCalibratedSpecificity extends BaseIDFSpecificity {
    float alpha, beta, gamma;

    public NQCCalibratedSpecificity(IndexSearcher searcher) {
        super(searcher);
    }

    public void setParameters(float alpha, float beta, float gamma) {
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
    }

    @Override
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        return computeNQC(q, retInfo, k);
    }

    private double computeNQC(Query q, RetrievedResults topDocs, int k) {
        double[] rsvs = topDocs.getRSVs(k);
        double mean = Arrays.stream(rsvs).average().getAsDouble();

        double avgIDF = 0;
        try {
            avgIDF = maxIDF(q);
        } catch (IOException e) {
            e.printStackTrace();
        }


        double nqc = 0;
        for (double rsv: rsvs) {
            double factor_1 = avgIDF;
            double factor_2 = (rsv - mean)/Math.sqrt(rsv) ;

            double prod = Math.pow(factor_1, alpha) * Math.pow(factor_2, beta);
            prod = Math.pow(prod, gamma);

            nqc += prod;
        }
        nqc /= (double)rsvs.length;

        return nqc * avgIDF; // high variance, high avgIDF -- more specificity
    }

    @Override
    public String name() {
        return "nqc_generic";
    }
}
