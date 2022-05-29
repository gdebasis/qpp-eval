package org.qpp;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.correlation.OverlapStats;
import org.experiments.Settings;
import org.feedback.RelevanceModelConditional;
import org.feedback.RelevanceModelIId;
import org.evaluator.RetrievedResults;
import org.trec.TRECQuery;

import java.util.Arrays;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.lucene.search.TotalHits;


public class UEFSpecificity implements QPPMethod {
    BaseIDFSpecificity qppMethod;

    static Random rnd = new Random(Settings.SEED);
    static final int NUM_SAMPLES = 10;

    public UEFSpecificity(BaseIDFSpecificity qppMethod) {
        this.qppMethod = qppMethod;
    }

    TopDocs sampleTopDocs(TopDocs topDocs, int M, int k) {
//        ScoreDoc[] sampledScoreDocs = new ScoreDoc[k];
        ScoreDoc[] sampledScoreDocs = new ScoreDoc[Math.min(topDocs.scoreDocs.length, k)];
        List<ScoreDoc> sdList = new ArrayList(Arrays.asList(topDocs.scoreDocs));
        Collections.shuffle(sdList, rnd);
        sampledScoreDocs = sdList.subList(0, Math.min(topDocs.scoreDocs.length, k)).toArray(sampledScoreDocs);
        //+++LUCENE_COMPATIBILITY: Sad there's no #ifdef like C!
        // 8.x CODE
        return new TopDocs(new TotalHits(k, TotalHits.Relation.EQUAL_TO), sampledScoreDocs);
        // 5.x code
        //return new TopDocs(Math.min(topDocs.scoreDocs.length, k), sampledScoreDocs, SEED);
        //---LUCENE_COMPATIBILITY
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
            catch (IOException ioex) { ioex.printStackTrace(); } catch (Exception ex) {
                Logger.getLogger(UEFSpecificity.class.getName()).log(Level.SEVERE, null, ex);
            }

            topDocs_rr = rlm.rerankDocs();
            double rankDist = OverlapStats.computeRankDist(topDocs, topDocs_rr);
            avgRankDist += rankDist;
        }

        double rankSim = OverlapStats.computeRankDist(topDocs, topDocs_rr);
        return ((double)NUM_SAMPLES/avgRankDist) * qppMethod.computeSpecificity(q, retInfo, topDocs, k);
    }

    @Override
    public String name() {
        return String.format("uef_%s", this.qppMethod.name());
    }
}
