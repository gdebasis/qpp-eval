package org.qpp;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.qppeval.evaluator.RetrievedResults;
import org.qppeval.trec.TRECQuery;

import java.util.Set;

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
            Set<Term> qterms = new TRECQuery(q).getQueryTerms(searcher);
            numQueryTerms = qterms.size();
            avgIDF = 1/averageIDF(q);
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
