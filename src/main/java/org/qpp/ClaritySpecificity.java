package org.qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.feedback.RelevanceModelConditional;
import org.feedback.RelevanceModelIId;
import org.evaluator.RetrievedResults;
import org.trec.TRECQuery;

public class ClaritySpecificity extends BaseIDFSpecificity {
    public ClaritySpecificity(IndexSearcher searcher) {
        super(searcher);
    }

    @Override
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k) { // retInfo is unused
        try {
            RelevanceModelIId rlm = new RelevanceModelConditional(searcher, new TRECQuery(q), topDocs, k);
            rlm.computeFdbkWeights();
            return rlm.getQueryClarity() * maxIDF(q);
        }
        catch (Exception ex) { ex.printStackTrace(); }
        return 0;
    }

    @Override
    public String name() {
        return "clarity";
    }

}