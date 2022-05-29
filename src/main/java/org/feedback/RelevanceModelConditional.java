/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.feedback;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.trec.TRECQuery;

import java.util.Map;
import java.io.IOException;

/**
 *
 * @author Debasis
 */
public class RelevanceModelConditional extends RelevanceModelIId {

    public RelevanceModelConditional(IndexSearcher searcher, TRECQuery trecQuery, TopDocs topDocs, int numTopDocs) throws Exception {
        super(searcher, trecQuery, topDocs, numTopDocs);
    }
    
    @Override
    public void computeFdbkWeights() throws IOException, Exception {
        float p_w;
        float this_wt; // phi(q,w)
        
        buildTermStats();
        
        int docsSeen = 0;
        float sumSim = this.retrievedDocsTermStats.docTermVecs
                        .stream()
                        .map(x->x.sim)
                        .reduce(0.0f, (a, b) -> a + b)
        ;

        // For each doc in top ranked
        for (PerDocTermVector docvec : this.retrievedDocsTermStats.docTermVecs) {            
            // For each word in this document
            for (RetrievedDocTermInfo w : docvec.perDocStats.values()) {
                RetrievedDocTermInfo wGlobal = retrievedDocsTermStats.getTermStats(w.getTerm());
                p_w = mixTfIdf(w, docvec);
                this_wt = p_w * docvec.sim/sumSim;
                
                // Take the average
                wGlobal.setWeight(wGlobal.getWeight() + this_wt);
            }
            docsSeen++;
            if (docsSeen >= numTopDocs)
                break;
        }  
    }
}
