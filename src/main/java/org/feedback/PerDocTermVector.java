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
    int sum_tf;
    float sim;  // similarity with query
    HashMap<String, RetrievedDocTermInfo> perDocStats;
    
    public PerDocTermVector(int docId) {
        this.docId = docId;
        perDocStats = new HashMap<>();
        sum_tf = 0;
    }
    
    public float getNormalizedTf(String term) {
        RetrievedDocTermInfo tInfo = perDocStats.get(term);
        if (tInfo == null)
            return 0;
        return perDocStats.get(term).getTf()/(float)sum_tf;
    }
    
    RetrievedDocTermInfo getTermStats(String qTerm) {
        return this.perDocStats.get(qTerm);
    }    
}

