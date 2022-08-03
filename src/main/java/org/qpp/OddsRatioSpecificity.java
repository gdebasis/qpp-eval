package org.qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.evaluator.RetrievedResults;

import java.util.Arrays;
import java.util.stream.DoubleStream;

public class OddsRatioSpecificity extends BaseIDFSpecificity {
    int topK;
    int bottomK;

    OddsRatioSpecificity(IndexSearcher searcher, int topK, int bottomK) {
        super(searcher);
        this.topK = topK;
        this.bottomK = bottomK;
    }

    @Override
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        double[] rsvs = retInfo.getRSVs(k);
        double avgIDF = 0;
        double topAvg = Arrays.stream(rsvs).limit(topK).average().getAsDouble();
        double bottomAvg = Arrays.stream(rsvs).
    }

    @Override
    public String name() {
        return "nqc";
    }
}
