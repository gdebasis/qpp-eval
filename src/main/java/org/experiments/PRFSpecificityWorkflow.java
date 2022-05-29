package org.experiments;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.correlation.KendalCorrelation;
import org.evaluator.Evaluator;
import org.evaluator.Metric;
import org.evaluator.RetrievedResults;
import org.feedback.RelevanceModelConditional;
import org.feedback.RelevanceModelIId;
import org.qpp.PRFSpeciticity;
import org.qpp.UEFSpecificity;
import org.trec.TRECQuery;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.correlation.PearsonCorrelation;

public class PRFSpecificityWorkflow extends NQCCalibrationWorkflow {
    PRFSpeciticity qppMethod;
    float alpha;
    int num_fdbk;
    int num_expansion;

    PRFSpecificityWorkflow(String queryFile, float alpha, int num_fdbk, int num_expansion) throws Exception {
        super(queryFile);
        this.alpha = alpha;
        this.num_fdbk = num_fdbk;
        this.num_expansion = num_expansion;
        qppMethod = new PRFSpeciticity(Settings.getSearcher(), alpha);
    }
    
    PRFSpecificityWorkflow(String queryFile, String resFile, float alpha, int num_fdbk, int num_expansion) throws Exception {
        super(queryFile, resFile);
        this.alpha = alpha;
        this.num_fdbk = num_fdbk;
        this.num_expansion = num_expansion;
        qppMethod = new PRFSpeciticity(Settings.getSearcher(), alpha);
    }

    TRECQuery expandQuery(TRECQuery q, TopDocs topDocs, int k) throws Exception {
        ScoreDoc[] sd = new ScoreDoc[k];
        System.arraycopy(topDocs.scoreDocs, 0, sd, 0, k);
        TopDocs subset = new TopDocs(topDocs.totalHits, sd);

        RelevanceModelIId rlm = null;
        try {
            rlm = new RelevanceModelConditional(
                    Settings.getSearcher(), q, subset, subset.scoreDocs.length);
        }
        catch (IOException ioex) { ioex.printStackTrace(); } catch (Exception ex) {
            Logger.getLogger(UEFSpecificity.class.getName()).log(Level.SEVERE, null, ex);
        }

        // Post retrieval query expansion
        TRECQuery expandedQuery = rlm.expandQuery(q, num_expansion);
        System.out.println("Expanded qry: " + expandedQuery.getLuceneQueryObj());

        return expandedQuery;
    }

    static public TopDocs rerankDocs(TRECQuery q, TopDocs topDocs, int k) throws Exception {
        RelevanceModelIId rlm = null;
        try {
            rlm = new RelevanceModelConditional(
                    Settings.getSearcher(), q, topDocs, Settings.getQppTopK());
            rlm.computeFdbkWeights();
        }
        catch (IOException ioex) { ioex.printStackTrace(); } catch (Exception ex) {
            Logger.getLogger(UEFSpecificity.class.getName()).log(Level.SEVERE, null, ex);
        }
        return rlm.rerankDocs();
    }


    public double computeCorrelationWithExpandedQueries(List<TRECQuery> queries) throws Exception {
        final int qppTopK = Settings.getQppTopK();

        int numQueries = queries.size();
        double[] qppEstimates = new double[numQueries];
        double[] evaluatedMetricValues = new double[numQueries];

        List<TRECQuery> expandedQueries = new ArrayList<>(queries.size());

        for (TRECQuery query : queries) {
            TopDocs topDocs = topDocsMap.get(query.id);
            TRECQuery expandQuery = expandQuery(query, topDocs, qppTopK);
            System.out.println("Query: " + query + ", Expanded query: " + expandQuery);
            expandedQueries.add(expandQuery);
        }

        Map<String, TopDocs> postFdbkTopDocsMap = new HashMap<>();
        Evaluator postfdbk_evaluator = qppEvaluator.executeQueries(expandedQueries, sim,
                Settings.getNumWanted(), Settings.getQrelsFile(), Settings.RES_FILE, postFdbkTopDocsMap);

        //evaluation proceeds with the post-fdbk retrieval AP and specificity
        int i = 0;
        for (TRECQuery query: expandedQueries) {
            evaluatedMetricValues[i] = postfdbk_evaluator.compute(query.id, Metric.AP);

            TopDocs postRLMTopDocs = postFdbkTopDocsMap.get(query.id);
            TopDocs topDocs = topDocsMap.get(query.id); // top-docs before fdbk

            qppEstimates[i] = (float)qppMethod.computeSpecificity(
                    query.getLuceneQueryObj(), postRLMTopDocs, topDocs, qppTopK);
            i++;
        }

        double corr = new KendalCorrelation().correlation(evaluatedMetricValues, qppEstimates);
        System.out.println(String.format("Kendall's = %.4f", corr));
        return corr;
    }

    private TopDocs postProcess(TopDocs topDocs) { // change KLdivs to exp(-KLDiv)
        ScoreDoc[] new_sd = new ScoreDoc[topDocs.scoreDocs.length];
        System.arraycopy(topDocs.scoreDocs, 0, new_sd, 0, topDocs.scoreDocs.length);
        float max_score = topDocs.scoreDocs[topDocs.scoreDocs.length-1].score + 1;
        int i=0;
        for (ScoreDoc sd: topDocs.scoreDocs) {
            new_sd[i].doc = sd.doc;
            new_sd[i].score = max_score - sd.score;
            i++;
        }
        TopDocs new_topdocs = new TopDocs(topDocs.totalHits, new_sd);
        return new_topdocs;
    }

    public double computeCorrelationWithoutExpandedQueries(List<TRECQuery> queries) throws Exception {
        final int qppTopK = Settings.getQppTopK();

        int numQueries = queries.size();
        double[] qppEstimates = new double[numQueries];
        double[] preFdbkEvaluatedMetricValues = new double[numQueries];
        double[] evaluatedMetricValues = new double[numQueries];

        Map<String, TopDocs> postFdbkTopDocsMap = new HashMap<>();

        for (TRECQuery query : queries) {
            TopDocs topDocs = topDocsMap.get(query.id);
            /* TODO: This is only for reranking with RLM. For neural models,
                this 'rerankedTop' has to be *loaded* from Resfile.
                See the loadRes function (invoked from the constructor of RLSWorkFlow).
             */
            TopDocs rerankedTop = rerankDocs(query, topDocs, topDocs.scoreDocs.length);
            TopDocs rerankedTopPostProcessed = postProcess(rerankedTop);
            postFdbkTopDocsMap.put(query.id, rerankedTopPostProcessed);
        }

        Evaluator postfdbk_evaluator = qppEvaluator.executeDummy(queries, sim,
                Settings.getNumWanted(), Settings.getQrelsFile(), Settings.RES_FILE, postFdbkTopDocsMap);

        //evaluation proceeds with the post-fdbk retrieval AP and specificity
        int i = 0;
        for (TRECQuery query: queries) {
            preFdbkEvaluatedMetricValues[i] = evaluator.compute(query.id, Metric.AP); // compute AP on reranked!
            evaluatedMetricValues[i] = postfdbk_evaluator.compute(query.id, Metric.AP); // compute AP on reranked!

            TopDocs postRLMTopDocs = postFdbkTopDocsMap.get(query.id);
            TopDocs topDocs = topDocsMap.get(query.id); // top-docs before fdbk

            qppEstimates[i] = (float)qppMethod.computeSpecificity(
                    query.getLuceneQueryObj(), postRLMTopDocs, topDocs, qppTopK);
            i++;
        }

        System.out.println("Pre-fdbk MAP = " + Arrays.stream(preFdbkEvaluatedMetricValues).average().getAsDouble());
        System.out.println("Post-fdbk MAP = " + Arrays.stream(evaluatedMetricValues).average().getAsDouble());

        double corr = new KendalCorrelation().correlation(evaluatedMetricValues, qppEstimates);
        System.out.println(String.format("Kendall's = %.4f", corr));
        
//        double corr = new PearsonCorrelation().correlation(evaluatedMetricValues, qppEstimates);
//        System.out.println(String.format("Pearson's = %.4f", corr));
        
        return corr;
    }

    public static void main(String[] args) {
        final String queryFile = "/home/suchana/NetBeansProjects/qpp-variation/data/topics.401-450.xml";
        final float ALPHA = 0.3f;
        final int NUM_FDBK_TOP_DOCS = 10;
        final int NUM_EXPANSION = 5; 
        Settings.init("qpp.properties");

        try {
            PRFSpecificityWorkflow prfSpecificityWorkflow = new PRFSpecificityWorkflow(queryFile, ALPHA, NUM_FDBK_TOP_DOCS, NUM_EXPANSION);
            System.out.println(String.format("Evaluating on %d queries", prfSpecificityWorkflow.queries.size()));
            //prfSpecificityWorkflow.computeCorrelationWithExpandedQueries(prfSpecificityWorkflow.queries);
            prfSpecificityWorkflow.computeCorrelationWithoutExpandedQueries(prfSpecificityWorkflow.queries);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}