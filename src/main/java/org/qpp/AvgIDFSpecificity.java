package org.qpp;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.evaluator.RetrievedResults;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.search.ScoreMode;

public class AvgIDFSpecificity implements QPPMethod {
    IndexReader reader;
    IndexSearcher searcher;

    public AvgIDFSpecificity(IndexSearcher searcher) {
        this.searcher = searcher;
        this.reader = searcher.getIndexReader();
    }

    @Override
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        double specificity = 0;
        try {
            specificity = averageIDF(q);
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        return specificity;
    }

    double averageIDF(Query q) throws IOException {
        long N = reader.numDocs();
        Set<Term> qterms = new HashSet<>();
        //+++LUCENE_COMPATIBILITY: Sad there's no #ifdef like C!
        // 8.x CODE
        q.createWeight(searcher, ScoreMode.COMPLETE, 1).extractTerms(qterms);
        // 5.x code
        //q.createWeight(searcher, false).extractTerms(qterms);
        //---LUCENE_COMPATIBILITY

        float aggregated_idf = 0;
        for (Term t: qterms) {
            int n = reader.docFreq(t);
            if(n != 0){
                double idf = Math.log(N/(double)n);
                aggregated_idf += idf;
            }
        }
        return aggregated_idf/(double)qterms.size();
    }

    @Override
    public String name() {
        return "avgidf";
    }
}
