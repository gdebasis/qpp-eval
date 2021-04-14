package org.qpp;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.feedback.RelevanceModelConditional;
import org.feedback.RelevanceModelIId;
import org.evaluator.RetrievedResults;
import org.trec.TRECQuery;

import java.util.Arrays;

public class UEFSpecificity implements QPPMethod {
    BaseIDFSpecificity qppMethod;
    RelevanceModelIId rlm;

    public UEFSpecificity(BaseIDFSpecificity qppMethod) {
        this.qppMethod = qppMethod;
    }

    static private int[] getTopDocNames(TopDocs topDocs) {
        int[] docIds = new int[topDocs.scoreDocs.length];
        int i=0;
        for (ScoreDoc sd: topDocs.scoreDocs) {
            docIds[i++] = sd.doc;
        }
        return docIds;
    }

    // instead of using a rank correlation metric (where we need to supply two lists of float),
    // we compute the average shift in the ranks of items.
    // Assumption: |listA \cap listB| = |listA| = |listB|
    static public double computeRankSimilarity(TopDocs listA, TopDocs listB) {
        int[] docIdsA = getTopDocNames(listA);
        int[] docIdsB = getTopDocNames(listB);

        Arrays.sort(docIdsB);
        int posInA = 0, posInB, delRank;
        double avgShift = 0;
        for (int docId: docIdsA) {
            posInB = Arrays.binarySearch(docIdsB, docId);
            if (posInB >= 0) {
                delRank = posInA - posInB;
                avgShift += delRank * delRank;
            }
            posInA++;
        }

        avgShift = avgShift/(double)docIdsA.length;
        avgShift = Math.sqrt(avgShift);
        return avgShift;
    }

    @Override
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        TopDocs topDocs_rr = null;
        try {
            RelevanceModelIId rlm = new RelevanceModelConditional(qppMethod.searcher, new TRECQuery(q), topDocs, k);
            rlm.computeFdbkWeights();
            topDocs_rr = rlm.rerankDocs();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        double rankSim = computeRankSimilarity(topDocs, topDocs_rr);
        return rankSim * qppMethod.computeSpecificity(q, retInfo, topDocs, k);
    }

    @Override
    public String name() {
        return String.format("uef_%s", this.qppMethod.name());
    }
}
