/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.feedback;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.trec.FieldConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Debasis
 */
public class RetrievedDocsTermStats {
    TopDocs topDocs;
    IndexReader reader;
    private int sumTf;
    float sumDf;
    Map<String, RetrievedDocTermInfo> termStats;
    List<PerDocTermVector> docTermVecs;
    int numTopDocs;
    
    public RetrievedDocsTermStats(IndexReader reader,
            TopDocs topDocs, int numTopDocs) {
        this.topDocs = topDocs;
        this.reader = reader;
        sumDf = numTopDocs;
        termStats = new HashMap<>();
        docTermVecs = new ArrayList<>();
        this.numTopDocs = numTopDocs;
    }

    public PerDocTermVector getDocTermVecs(int i) {
        return docTermVecs.get(i);
    }

    public int sumTf() {
        if (sumTf==0)
            sumTf = termStats.values().stream().map(x->x.getTf()).reduce(0, (a,b)->a+b);
        return sumTf;
    }

    public IndexReader getReader() { return reader; }

    public float getSumDf() { return sumDf; }

    public Map<String, RetrievedDocTermInfo> getTermStats() {
        return termStats;
    }
    
    public void buildAllStats() throws Exception {
        int rank = 0;
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            int docId = scoreDoc.doc;
            docTermVecs.add(buildStatsForSingleDoc(docId, rank, scoreDoc.score));
            rank++;
        }
    }
    
    public RetrievedDocTermInfo getTermStats(String qTerm) {
        return this.termStats.get(qTerm);        
    }
    
    PerDocTermVector buildStatsForSingleDoc(int docId, int rank, float sim) throws Exception {
        String termText;
        BytesRef term;
        Terms tfvector;
        TermsEnum termsEnum;
        int tf;
        RetrievedDocTermInfo trmInfo;
        PerDocTermVector docTermVector = new PerDocTermVector(docId);
        docTermVector.sim = sim;  // sim value for document D_j
        
        tfvector = reader.getTermVector(docId, FieldConstants.FIELD_ANALYZED_CONTENT);
        if (tfvector == null || tfvector.size() == 0)
            return null;
        
        // Construct the normalized tf vector
        termsEnum = tfvector.iterator(); // access the terms for this field
        
    	while ((term = termsEnum.next()) != null) { // explore the terms for this field
            termText = term.utf8ToString();
            tf = (int)termsEnum.totalTermFreq();
            
            // per-doc
            docTermVector.perDocStats.put(termText, new RetrievedDocTermInfo(termText, tf));
            if (rank >= numTopDocs) {
                continue;
            }
            
            // collection stats for top k docs
            trmInfo = termStats.get(termText);
            if (trmInfo == null) {
                trmInfo = new RetrievedDocTermInfo(termText);
            }
            trmInfo.incrementTf(tf);
            trmInfo.incrementDF();
            termStats.put(termText, trmInfo);
        }
    	docTermVector.setSumTf();
        return docTermVector;
    }
}
