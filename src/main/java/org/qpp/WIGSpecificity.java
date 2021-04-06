package org.qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.qppeval.evaluator.RetrievedResults;
import org.qppeval.trec.TRECQuery;

public class WIGSpecificity extends AvgIDFSpecificity {

    public WIGSpecificity(IndexSearcher searcher) {
        super(searcher);
    }

    @Override
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        double[] rsvs = retInfo.getRSVs(k);
        double avgIDF = 0;
        int numQueryTerms = 1;
        try {
            numQueryTerms = new TRECQuery(q).getQueryTerms(searcher).size();
            avgIDF = averageIDF(q);
        }
        catch (Exception ex) { ex.printStackTrace(); }

        double wig = 0;
        for (double rsv: rsvs) {
            wig += (rsv - avgIDF);
        }
        return wig/(double)(numQueryTerms * rsvs.length);
    }

    @Override
    public String name() {
        return "WIG";
    }
}