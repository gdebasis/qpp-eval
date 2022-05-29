package org.qpp;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.evaluator.RetrievedResults;

public interface QPPMethod {
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k);
    public String name();
}


