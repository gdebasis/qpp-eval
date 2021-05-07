package org.qpp;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.feedback.RelevanceModelConditional;
import org.feedback.RelevanceModelIId;
import org.evaluator.RetrievedResults;
import org.trec.TRECQuery;

import java.io.IOException;
import java.util.*;

public class UEFSpecificity implements QPPMethod {
    BaseIDFSpecificity qppMethod;
    RelevanceModelIId rlm;
    static final int SEED = 314159; // first six digits of pi - a beautiful seed!
    static Random rnd = new Random(SEED);
    static final int NUM_SAMPLES = 10;

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
    static public double computeRankDist(TopDocs listA, TopDocs listB) {
        int[] docIdsA = getTopDocNames(listA);
        int[] docIdsB = getTopDocNames(listB);

        Arrays.sort(docIdsB);
        int posInA = 0, posInB;
        double delRank;
        double avgShift = 0;

        for (int docId: docIdsA) {
            posInB = Arrays.binarySearch(docIdsB, docId);
            if (posInB >= 0) {
                delRank = (posInA - posInB)/(double)docIdsA.length;
                avgShift += delRank * delRank;
            }
            posInA++;
        }

        avgShift = avgShift/(double)docIdsA.length;
        avgShift = Math.sqrt(avgShift);
        return avgShift;
    }

    TopDocs sampleTopDocs(TopDocs topDocs, int M, int k) {
        ScoreDoc[] sampledScoreDocs = new ScoreDoc[k];
        List<ScoreDoc> sdList = new ArrayList(Arrays.asList(topDocs.scoreDocs));
        Collections.shuffle(sdList, rnd);
        sampledScoreDocs = sdList.subList(0, Math.min(topDocs.scoreDocs.length, k)).toArray(sampledScoreDocs);
        return new TopDocs(new TotalHits(k, TotalHits.Relation.EQUAL_TO), sampledScoreDocs);
    }

    @Override
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        TopDocs topDocs_rr = null;
        double avgRankDist = 0;
        RelevanceModelIId rlm = null;

        for (int i=0; i < NUM_SAMPLES; i++) {
            TopDocs sampledTopDocs = sampleTopDocs(topDocs, 3 * k, k);
            try {
                rlm = new RelevanceModelConditional(
                    qppMethod.searcher, new TRECQuery(q), sampledTopDocs, k);
                rlm.computeFdbkWeights();
            }
            catch (NullPointerException nex) { continue; /* next sample */ }
            catch (IOException ioex) { ioex.printStackTrace(); }

            topDocs_rr = rlm.rerankDocs();
            double rankDist = computeRankDist(topDocs, topDocs_rr);
            avgRankDist += rankDist;
        }
        return ((double)NUM_SAMPLES/avgRankDist) * qppMethod.computeSpecificity(q, retInfo, topDocs, k);
    }

    @Override
    public String name() {
        return String.format("uef_%s", this.qppMethod.name());
    }
}
