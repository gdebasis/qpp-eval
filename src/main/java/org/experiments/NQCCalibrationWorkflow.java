package org.experiments;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.correlation.KendallsCorrelation;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.correlation.KendalCorrelation;
import org.evaluator.Evaluator;
import org.evaluator.Metric;
import org.evaluator.ResultTuple;
import org.evaluator.RetrievedResults;
import org.qpp.NQCSpecificityCalibrated;
import org.qpp.QPPMethod;
import org.trec.FieldConstants;
import org.trec.TRECQuery;

import java.io.File;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import org.correlation.PearsonCorrelation;

public class NQCCalibrationWorkflow {
    Similarity sim = new LMDirichletSimilarity(1000);
    protected Map<String, TopDocs> topDocsMap = new HashMap<>();
    protected QPPEvaluator qppEvaluator;
    protected Evaluator evaluator;
    protected QPPMethod qppMethod;
    protected List<TRECQuery> queries;

    public NQCCalibrationWorkflow() throws Exception {
        qppEvaluator = new QPPEvaluator(
                Settings.getProp(),
                Settings.getCorrelationMetric(), Settings.getSearcher(), Settings.getNumWanted());
        queries = qppEvaluator.constructQueries(Settings.getQueryFile(), Settings.tsvMode);
        evaluator = qppEvaluator.executeQueries(queries, sim, Settings.getNumWanted(),
                Settings.getQrelsFile(), Settings.RES_FILE, topDocsMap);
    }

    public NQCCalibrationWorkflow(String queryFile) throws Exception {
        qppEvaluator = new QPPEvaluator(
                Settings.getProp(),
                Settings.getCorrelationMetric(), Settings.getSearcher(), Settings.getNumWanted());
        queries = qppEvaluator.constructQueries(queryFile, Settings.tsvMode);
        evaluator = qppEvaluator.executeQueries(queries, sim, Settings.getNumWanted(), Settings.getQrelsFile(), Settings.RES_FILE, topDocsMap);
    }

    public NQCCalibrationWorkflow(String queryFile, String resFile) throws Exception {
        qppEvaluator = new QPPEvaluator(
                Settings.getProp(),
                Settings.getCorrelationMetric(), Settings.getSearcher(), Settings.getNumWanted());
        queries = qppEvaluator.constructQueries(queryFile, Settings.tsvMode);
        topDocsMap = loadResFile(new File(resFile));
        //System.out.println("#### : " + topDocsMap.size());
        evaluator = qppEvaluator.executeDummy(queries, sim,
                Settings.getNumWanted(), Settings.getQrelsFile(),
                Settings.RES_FILE, topDocsMap);
    }

    public Map<String, TopDocs> loadResFile(File resFile) {
        Map<String, TopDocs> topDocsMap = new HashMap<>();
        try {
            List<String> lines = FileUtils.readLines(resFile, UTF_8);

            String prev_qid = null, qid = null;
            RetrievedResults rr = null;

            for (String line: lines) {
                String[] tokens = line.split("\\s+");
                qid = tokens[0];

                if (prev_qid!=null && !prev_qid.equals(qid)) {
                    topDocsMap.put(prev_qid, convert(rr));
                    rr = new RetrievedResults(qid);
                }
                else if (prev_qid == null) {
                    rr = new RetrievedResults(qid);
                }

                int offset = Settings.getDocOffsetFromId(tokens[2]);
                int rank = Integer.parseInt(tokens[3]);
                double score = Float.parseFloat(tokens[4]);

                rr.addTuple(String.valueOf(offset), rank, score);
                prev_qid = qid;
            }
            if (qid!=null)
                topDocsMap.put(qid, convert(rr));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return topDocsMap;
    }

    private TopDocs convert(RetrievedResults rr) {
        int nret = rr.getNumRet();
        ScoreDoc[] sd = new ScoreDoc[nret];

        int i = 0;
        for (ResultTuple resultTuple: rr.getTuples()) {
            sd[i++] = new ScoreDoc(
                Integer.parseInt(resultTuple.getDocName()),
                (float)(resultTuple.getScore())
            );
        }
        return new TopDocs(new TotalHits(nret, TotalHits.Relation.EQUAL_TO), sd);
    }

    public double computeCorrelation(List<TRECQuery> queries, QPPMethod qppMethod, int qppTopK) {
        int numQueries = queries.size();
        double[] qppEstimates = new double[numQueries]; // stores qpp estimates for the list of input queries
        double[] evaluatedMetricValues = new double[numQueries]; // stores GTs (AP/nDCG etc.) for the list of input queries
        int i = 0;

        for (TRECQuery query : queries) {
            RetrievedResults rr = null;
            TopDocs topDocs = topDocsMap.get(query.id);
            evaluatedMetricValues[i] = evaluator.compute(query.id, Metric.AP);
            rr = new RetrievedResults(query.id, topDocs); // this has to be set with the topdocs
            qppEstimates[i] = (float)qppMethod.computeSpecificity(
                    query.getLuceneQueryObj(), rr, topDocs, qppTopK);
            i++;
        }

        double p_corr = new PearsonCorrelation().correlation(evaluatedMetricValues, qppEstimates);
        System.out.println(String.format("P-rho = %.4f", p_corr));
        return p_corr;
    }

    public Pair<Double, Double> computeCorrelationPairs(List<TRECQuery> queries, QPPMethod qppMethod, int qppTopK) {
        int numQueries = queries.size();
        double[] qppEstimates = new double[numQueries]; // stores qpp estimates for the list of input queries
        double[] evaluatedMetricValues = new double[numQueries]; // stores GTs (AP/nDCG etc.) for the list of input queries
        int i = 0;

        for (TRECQuery query : queries) {
            RetrievedResults rr = null;
            TopDocs topDocs = topDocsMap.get(query.id);
            evaluatedMetricValues[i] = evaluator.compute(query.id, Metric.AP);
            rr = new RetrievedResults(query.id, topDocs); // this has to be set with the topdocs
            qppEstimates[i] = (float)qppMethod.computeSpecificity(
                    query.getLuceneQueryObj(), rr, topDocs, qppTopK);
            i++;
        }

        double p_corr = new PearsonCorrelation().correlation(evaluatedMetricValues, qppEstimates);
        double k_corr = new KendallsCorrelation().correlation(evaluatedMetricValues, qppEstimates);
        System.out.println(String.format("P-rho = %.4f, K-tau = %.4f", p_corr, k_corr));
        return Pair.of(p_corr, k_corr);
    }

    public Pair<Double, Double> computeCorrelation(List<TRECQuery> queries, QPPMethod qppMethod) {
        return computeCorrelationPairs(queries, qppMethod, Settings.getQppTopK());
    }
    
    public Pair<Double, Double> calibrateParams(List<TRECQuery> trainQueries) {
        final int qppTopK = Settings.getQppTopK();
        final float[] alpha_choices = {/*0.25f, 0.5f, 1.0f,*/ 1.5f, /*2.0f*/};
        final float[] beta_choices = {0.25f, /*0.5f, 1.0f, 1.5f, 2.0f*/};
        final float[] gamma_choices = {/*0.25f,*/ 0.5f /*, 1.0f, 1.5f, 2.0f*/};
        float[] best_choice = new float[3]; // best (alpha, beta, gamma)
        Pair<Double, Double> max_corr = Pair.of(0.0, 0.0);

        for (float alpha: alpha_choices) {
            for (float beta: beta_choices) {
                for (float gamma: gamma_choices) {
                    qppMethod = new NQCSpecificityCalibrated(Settings.getSearcher(), alpha, beta, gamma);
                    System.out.println(String.format("Executing NQC (%.2f, %.2f, %.2f)", alpha, beta, gamma));
                    Pair<Double, Double> corrs = computeCorrelation(trainQueries, qppMethod);
                    if (corrs.getLeft().doubleValue() > max_corr.getLeft().doubleValue()) {
                        max_corr = corrs;
                        best_choice[0] = alpha;
                        best_choice[1] = beta;
                        best_choice[2] = gamma;
                    }
                }
            }
        }
        return max_corr;
    }

    public double epoch() {
        final float TRAIN_RATIO = 0.5f;
        TrainTestInfo trainTestInfo = new TrainTestInfo(queries, TRAIN_RATIO);
        Pair<Double, Double> correlations = calibrateParams(trainTestInfo.getTrain());

        /*
        QPPMethod qppMethod = new NQCSpecificityCalibrated(
                            Settings.getSearcher(),
                            tuned_params[0], tuned_params[1], tuned_params[2]); */
        // computeCorrelation(queries, qppMethod);

        return correlations.getLeft();
    }

    public Pair<Double, Double> epochWithPairs() {
        final float TRAIN_RATIO = 0.5f;
        TrainTestInfo trainTestInfo = new TrainTestInfo(queries, TRAIN_RATIO);
        Pair<Double, Double> correlations = calibrateParams(trainTestInfo.getTrain());

        /*
        QPPMethod qppMethod = new NQCSpecificityCalibrated(
                            Settings.getSearcher(),
                            tuned_params[0], tuned_params[1], tuned_params[2]); */
        // computeCorrelation(queries, qppMethod);

        return correlations;
    }

    public void averageAcrossEpochs() {
        final int NUM_EPOCHS = 3; // change it to 30!
        double p_avg = 0, k_avg = 0;
        for (int i=1; i <= NUM_EPOCHS; i++) {
            System.out.println("Random split: " + i);
            Pair<Double, Double> corrs = epochWithPairs();
            p_avg += corrs.getLeft();
            k_avg += corrs.getRight();
        }
        System.out.println(String.format("Result over %d runs of tuned 50:50 splits = (%.4f, %.4f)", NUM_EPOCHS, p_avg/NUM_EPOCHS, k_avg/NUM_EPOCHS));
    }

    public static void main(String[] args) {
        final String queryFile = "data/topics.robust.all";
        Settings.init("qpp.properties");

        try {
            NQCCalibrationWorkflow nqcCalibrationWorkflow = new NQCCalibrationWorkflow(queryFile);
            nqcCalibrationWorkflow.averageAcrossEpochs();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
