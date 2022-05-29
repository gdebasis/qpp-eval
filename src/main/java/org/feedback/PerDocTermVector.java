/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.feedback;

import java.util.HashMap;

/**
 *
 * @author Debasis
 */
public class PerDocTermVector {
    int docId;
    float sim;  // similarity with query
    HashMap<String, RetrievedDocTermInfo> perDocStats;
    float sum_tf;
    
    public PerDocTermVector(int docId) {
        this.docId = docId;
        perDocStats = new HashMap<>();
    }
    
    public float getNormalizedTf(String term) {
        RetrievedDocTermInfo tInfo = perDocStats.get(term);
        if (tInfo == null)
            return 0;
        return perDocStats.get(term).getTf()/sum_tf;
    }

    public void setSumTf() {
        sum_tf = (float)(perDocStats.values().stream().map(x->x.getTf()).reduce(0, (a,b)->a+b));
    }

    public int getTf(String term) {
        RetrievedDocTermInfo tInfo = perDocStats.get(term);
        if (tInfo == null)
            return 0;
        return perDocStats.get(term).getTf();
    }

    public HashMap<String, RetrievedDocTermInfo> getTermStats() {
        return this.perDocStats;
    }

    RetrievedDocTermInfo getTermStats(String qTerm) {
        return this.perDocStats.get(qTerm);
    }

    public void addTermWt(String term, int wt) {
        RetrievedDocTermInfo retrievedDocTermInfo = perDocStats.get(term);
        if (retrievedDocTermInfo == null) {
            retrievedDocTermInfo = new RetrievedDocTermInfo(term);
            perDocStats.put(term, retrievedDocTermInfo);
        }
        retrievedDocTermInfo.setTf(retrievedDocTermInfo.getTf() + wt);
    }
}

