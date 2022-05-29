package org.qpp;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.correlation.OverlapStats;
import org.evaluator.RetrievedResults;
import org.feedback.*;
import org.trec.FieldConstants;
import org.trec.TRECQuery;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PRFSpeciticity {
    IndexSearcher searcher;
    NQCSpecificity qppMethod;
    float alpha;

    public PRFSpeciticity(IndexSearcher searcher,
                   float alpha /* the linear combination parameter (to be tuned) */) {
        this.searcher = searcher;
        this.qppMethod = new NQCSpecificity(searcher);
        this.alpha = alpha;
    }

    private float crossEntropy(TopDocs topDocs, RelevanceModelIId rlm, int qppTopK) {
        float ce = 0;

        try {
            for (int i=0; i < qppTopK; i++) {
                ce += crossEntropyForOneDoc(topDocs, rlm, i);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        float avg_ce = ce/(float)topDocs.scoreDocs.length;
        return avg_ce;
    }

    private float entropy_of_mean_doc_lm(TopDocs topDocs, RelevanceModelIId rlm, int qppTopK) {
        RetrievedDocsTermStats retrievedDocsTermStats = rlm.getRetrievedDocsTermStats();
        PerDocTermVector mu = new PerDocTermVector(0); // docid is unused here

        for (RetrievedDocTermInfo retDocInfo : retrievedDocsTermStats.getTermStats().values()) { // for vocab of top
            String w = retDocInfo.getTerm();

            for (int i=0; i < qppTopK; i++) {
                PerDocTermVector docVector = retrievedDocsTermStats.getDocTermVecs(i);
                int tf = docVector.getTf(w);  /// tf(w)
                mu.addTermWt(w, tf);
            }
        }

        // normalize
        HashMap<String, RetrievedDocTermInfo> termStats = mu.getTermStats();
        int sum_tf = termStats.values().stream().map(x->x.getTf()).reduce(0, (a, b) -> a + b);

        float entropy = 0;
        for (RetrievedDocTermInfo tinfo: termStats.values()) {
            float p_x_i = tinfo.getTf()/(float)sum_tf;
            float log_p_x_i = (float)Math.log(p_x_i);
            entropy += p_x_i*log_p_x_i;
        }

        // compute entropy
        return -1*entropy;
    }

    // how close is the document LM to the TopDocs LM and how distict is the topDocs LM from the collection
    private float crossEntropyForOneDoc(TopDocs topDocs, RelevanceModelIId rlm, int i) throws IOException {
        float klDiv = 0;
        float p_w_D;    // P(w|D) for this doc D
        float p_w_R;    // P(w|D) for top-docs
        String term;
        double N = (double)searcher.getIndexReader().numDocs();

        RetrievedDocsTermStats retrievedDocsTermStats = rlm.getRetrievedDocsTermStats(); // topdocs stats
        float Z = retrievedDocsTermStats.sumTf();
        PerDocTermVector docVector = retrievedDocsTermStats.getDocTermVecs(i); // per-doc stats

        // For each v \in V (vocab of top ranked documents)
        for (RetrievedDocTermInfo w: docVector.getTermStats().values()) {
            term = w.getTerm();
            p_w_D = docVector.getNormalizedTf(term);
            RetrievedDocTermInfo w_g = retrievedDocsTermStats.getTermStats(term);
            if (w_g==null) {
                w_g = w_g;
                continue;
            }
            else p_w_R = w_g.getTf()/Z;
            float p_w_C = (float)(searcher.getIndexReader().docFreq(new Term(FieldConstants.FIELD_ANALYZED_CONTENT, term))/N);
            klDiv += p_w_R * (Math.log(p_w_R/p_w_D) - Math.log(p_w_D/p_w_C));
        }
        return klDiv;
    }

    public double computeSpecificity(Query q,
                         TopDocs topDocs,
                         TopDocs firstStepTopDocs,
                         int k) {
        // prob_d_second
        double prob_D_second = qppMethod.computeNQC(q, topDocs, k);

        try {
            // Now estimate a relevance model from the top-docs of D_init
            RelevanceModelIId rlm = new RelevanceModelConditional(searcher, new TRECQuery(q), firstStepTopDocs, k);
            rlm.computeFdbkWeights();

            float sim_D_second_Dinit = (float)OverlapStats.computeRBO(firstStepTopDocs, topDocs);
            float p_d_r_Dinit = crossEntropy(firstStepTopDocs, rlm, k);
            float r_Dinit = entropy_of_mean_doc_lm(firstStepTopDocs, rlm, k);

            float p_Dsecond_Dinit = 0;
            for (int i=0; i < k; i++) {
                ScoreDoc sd = topDocs.scoreDocs[i];
                p_Dsecond_Dinit += sd.score * p_d_r_Dinit/r_Dinit;
            }

            return alpha * prob_D_second + (1-alpha) * sim_D_second_Dinit/p_Dsecond_Dinit;
            //return alpha * prob_D_second + (1-alpha) * sim_D_second_Dinit;
            //return (1-alpha)*prob_D_second*sim_D_second_Dinit;
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }
}