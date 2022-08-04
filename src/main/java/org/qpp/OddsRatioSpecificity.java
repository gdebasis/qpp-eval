package org.qpp;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.evaluator.RetrievedResults;
import org.trec.TRECQuery;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.DoubleStream;

public class OddsRatioSpecificity extends BaseIDFSpecificity {
    float p;

    public OddsRatioSpecificity(IndexSearcher searcher, float p) {
        super(searcher);
        this.p = p;
    }

    @Override
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        k = Math.min(k,topDocs.scoreDocs.length);
        int topK = (int)(p*k);
        int bottomK = topK;

        double[] rsvs = retInfo.getRSVs(k);
        double avgIDF = 0;
        try {
            avgIDF = Arrays.stream(idfs(q)).max().getAsDouble();
        }
        catch (Exception ex) { ex.printStackTrace(); }

        double topAvg = Arrays.stream(rsvs).limit(topK).average().getAsDouble();
        double bottomAvg = Arrays.stream(rsvs).skip(k-bottomK).average().getAsDouble();
        return topAvg/bottomAvg * avgIDF;
    }

    @Override
    public String name() {
        return "odds-ratio";
    }
}
