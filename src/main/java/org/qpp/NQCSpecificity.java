package org.qpp;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.evaluator.RetrievedResults;

import java.io.IOException;

public class NQCSpecificity extends BaseIDFSpecificity {

    public NQCSpecificity(IndexSearcher searcher) {
        super(searcher);
    }

    @Override
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        return computeNQC(q, retInfo, k);
    }

    private double computeNQC(Query q, RetrievedResults topDocs, int k) {
        double[] rsvs = topDocs.getRSVs(k);
        double sd = new StandardDeviation().evaluate(rsvs);
        double avgIDF = 0;
        double nqc = 0;
        double del;
        for (double rsv: rsvs) {
            del = rsv - sd;
            nqc += del*del;
        }
        nqc /= (double)rsvs.length;

        try {
            avgIDF = maxIDF(q);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return nqc * avgIDF; // high variance, high avgIDF -- more specificity
    }

    @Override
    public String name() {
        return "nqc";
    }
}
