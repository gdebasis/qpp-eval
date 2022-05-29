package org.experiments;

import org.apache.commons.io.FileUtils;
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

class TrainTestInfo {
    List<TRECQuery> train;
    List<TRECQuery> test;
    static final int SEED = 31415;
    static Random r = new Random(SEED);

    TrainTestInfo(List<TRECQuery> parent, float trainRatio) {
        List<TRECQuery> listToShuffle = new ArrayList<>(parent);
        Collections.shuffle(listToShuffle); // shuffle the copy!

        int splitPoint = (int)(trainRatio * listToShuffle.size());
        train = listToShuffle.subList(0, splitPoint);
        test = listToShuffle.subList(splitPoint, listToShuffle.size());
    }

    List<TRECQuery> getTrain() { return train; }
    List<TRECQuery> getTest() { return test; }
}

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
        queries = qppEvaluator.constructQueries(Settings.getQueryFile());
        evaluator = qppEvaluator.executeQueries(queries, sim, Settings.getNumWanted(),
                Settings.getQrelsFile(), Settings.RES_FILE, topDocsMap);
    }

    public NQCCalibrationWorkflow(String queryFile) throws Exception {
        qppEvaluator = new QPPEvaluator(
                Settings.getProp(),
                Settings.getCorrelationMetric(), Settings.getSearcher(), Settings.getNumWanted());
        queries = qppEvaluator.constructQueries(queryFile);
        evaluator = qppEvaluator.executeQueries(queries, sim, Settings.getNumWanted(), Settings.getQrelsFile(), Settings.RES_FILE, topDocsMap);
    }

    public NQCCalibrationWorkflow(String queryFile, String resFile) throws Exception {
        qppEvaluator = new QPPEvaluator(
                Settings.getProp(),
                Settings.getCorrelationMetric(), Settings.getSearcher(), Settings.getNumWanted());
        queries = qppEvaluator.constructQueries(queryFile);
        topDocsMap = loadResFile(new File(resFile));
        System.out.println("#### : " + topDocsMap.size());
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

                int offset = getDocOffsetFromId(tokens[2]);
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

    private int getDocOffsetFromId(String docId) throws Exception {
        Query query = new TermQuery(new Term(FieldConstants.FIELD_ID, docId));
        TopDocs topDocs = qppEvaluator.searcher.search(query, 1);
        return topDocs.scoreDocs[0].doc;
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

    public double computeCorrelation(List<TRECQuery> queries, QPPMethod qppMethod) {
        final int qppTopK = Settings.getQppTopK();
        int numQueries = queries.size();
        double[] qppEstimates = new double[numQueries]; // stores qpp estimates for the list of input queries
        double[] evaluatedMetricValues = new double[numQueries]; // stores GTs (AP/nDCG etc.) for the list of input queries
        int i = 0;

        for (TRECQuery query : queries) {
//            RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.id);
            RetrievedResults rr = null;
            TopDocs topDocs = topDocsMap.get(query.id);
            evaluatedMetricValues[i] = evaluator.compute(query.id, Metric.AP);
            qppEstimates[i] = (float)qppMethod.computeSpecificity(
                    query.getLuceneQueryObj(), rr, topDocs, qppTopK);
            i++;
        }

//        double corr = new KendalCorrelation().correlation(evaluatedMetricValues, qppEstimates);
//        System.out.println(String.format("Kendall's = %.4f", corr));
//        
        double corr = new PearsonCorrelation().correlation(evaluatedMetricValues, qppEstimates);
        System.out.println(String.format("Pearson's = %.4f", corr));
        
        return corr;
    }
    
    public float[] calibrateParams(List<TRECQuery> trainQueries) {
        final int qppTopK = Settings.getQppTopK();
        final float[] alpha_choices = {/*0.25f, 0.5f, 1.0f,*/ 1.5f, /*2.0f*/};
        final float[] beta_choices = {0.25f, /*0.5f, 1.0f, 1.5f, 2.0f*/};
        final float[] gamma_choices = {/*0.25f,*/ 0.5f /*, 1.0f, 1.5f, 2.0f*/};
        float[] best_choice = new float[3]; // best (alpha, beta, gamma)
        double max_corr = 0;

        for (float alpha: alpha_choices) {
            for (float beta: beta_choices) {
                for (float gamma: gamma_choices) {
                    qppMethod = new NQCSpecificityCalibrated(Settings.getSearcher(), alpha, beta, gamma);
                    System.out.println(String.format("Executing NQC (%.2f, %.2f, %.2f)", alpha, beta, gamma));
                    double corr = computeCorrelation(trainQueries, qppMethod);
                    if (corr > max_corr) {
                        max_corr = corr;
                        best_choice[0] = alpha;
                        best_choice[1] = beta;
                        best_choice[2] = gamma;
                    }
                }
            }
        }
        return best_choice;
    }

    public double epoch() {
        final float TRAIN_RATIO = 0.5f;
        TrainTestInfo trainTestInfo = new TrainTestInfo(queries, TRAIN_RATIO);
        float[] tuned_params = calibrateParams(trainTestInfo.getTrain());
        QPPMethod qppMethod = new NQCSpecificityCalibrated(
                            Settings.getSearcher(),
                            tuned_params[0], tuned_params[1], tuned_params[2]);

        return computeCorrelation(queries, qppMethod);
    }

    public void averageAcrossEpochs() {
        final int NUM_EPOCHS = 2; // change it to 30!
        double avg = 0;
        for (int i=1; i <= NUM_EPOCHS; i++) {
            System.out.println("Random split: " + i);
            avg += epoch();
        }
        System.out.println(String.format("Result over %d runs of tuned 50:50 splits = %.4f", NUM_EPOCHS, avg/NUM_EPOCHS));
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
