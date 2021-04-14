package org.qpp;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TopDocs;
import org.evaluator.RetrievedResults;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class BaseIDFSpecificity implements QPPMethod {
    IndexReader reader;
    IndexSearcher searcher;

    public BaseIDFSpecificity(IndexSearcher searcher) {
        this.searcher = searcher;
        this.reader = searcher.getIndexReader();
    }

    @Override
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        double specificity = 0;
        try {
            specificity = maxIDF(q);
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        return specificity;
    }

    double maxIDF(Query q) throws IOException {
        long N = reader.numDocs();
        Set<Term> qterms = new HashSet<>();
        q.createWeight(searcher, ScoreMode.COMPLETE, 1).extractTerms(qterms);

        double aggregated_idf = 0;
        for (Term t: qterms) {
            int n = reader.docFreq(t);
            double idf = Math.log(N/(double)n);
            if (idf > aggregated_idf)
                aggregated_idf = idf;
        }
        return aggregated_idf;
    }

    @Override
    public String name() {
        return "MaxIDF";
    }
}
