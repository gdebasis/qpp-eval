/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.experiments;

import java.util.*;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.correlation.MinMaxNormalizer;
import org.evaluator.Metric;
import org.qpp.QPPMethod;
import org.regressor.FitLinearRegressor;
import org.regressor.RegressionLearner;
import org.trec.TRECQuery;

/**
 *
 * @author suchana
 */

class CorrelationPair {
    float withoutTransformation;
    float withTransformation;

    CorrelationPair(float x, float y) {
        withoutTransformation = x;
        withTransformation = y;
    }
}

public class QPPLinearRegressor {
    Properties                  prop;
    int                         partition;
    static List<TRECQuery>      trainQueries;
    static List<TRECQuery>      testQueries;
    static String               qrelsFile;
    static String               queryFile;
    static String               resFileTrain;
    static String               resFileTest;

    static Random rnd = new Random(Settings.SEED);
    
    public QPPLinearRegressor(Properties prop, int partition) {
        this.prop = prop;
        this.partition = partition;
        trainQueries = new ArrayList<>();
        testQueries = new ArrayList<>();
        qrelsFile = prop.getProperty("qrels.file");
        queryFile = prop.getProperty("query.file");
        resFileTrain = prop.getProperty("res.train");
        resFileTest = prop.getProperty("res.test");
    }
    
    public void randomSplit(List<TRECQuery> queries) {
        int splitQuery = (int) Math.floor(queries.size() * partition/100);

        for (int i=0; i<splitQuery; i++) {
            trainQueries.add(queries.get(i));
        }
        for (int i=splitQuery; i<queries.size(); i++) {
            testQueries.add(queries.get(i));
        }
    }

    public RegParameters fit(QPPEvaluator qppEvaluator,
                    QPPMethod qppMethod, Metric m,
                    Similarity sim, int nwanted) throws Exception {

        double[] retEvalMeasure = qppEvaluator.evaluate(trainQueries, sim, m, nwanted);
        double[] qppEstimates = qppEvaluator.getQPPEstimates(qppEvaluator.topDocsMap, qppMethod, trainQueries, m);

        SimpleRegression reg = new SimpleRegression();
        double[] n_pred = MinMaxNormalizer.normalize(qppEstimates); // in [0, 1]
        for (int i=0; i<retEvalMeasure.length; i++) {
            reg.addData(n_pred[i], retEvalMeasure[i]);
        }
        double min = Arrays.stream(qppEstimates).min().getAsDouble();
        double max =Arrays.stream(qppEstimates).max().getAsDouble();
        return new RegParameters(reg, min, max);
    }
    
    public CorrelationPair predict(RegParameters regParameters,
            QPPEvaluator qppEvaluator, QPPMethod qppMethod, Metric m,
            Similarity sim, int nwanted) throws Exception {

        double[] perQueryRetEvalMeasure = qppEvaluator.evaluate(testQueries, sim, m, nwanted);
        double[] qppEstimates = qppEvaluator.getQPPEstimates(qppEvaluator.topDocsMap, qppMethod, testQueries, m);
        float x = (float)qppEvaluator.measureCorrelation(perQueryRetEvalMeasure, qppEstimates);

        // Transform with LR
        double[] qppEstimatesTransformed = new double[qppEstimates.length];
        for (int i=0; i < qppEstimates.length; i++) {
            double transformed = regParameters.predict(qppEstimates[i]);
            System.out.println(String.format("%.4f --> %.4f", qppEstimates[i], transformed));
            qppEstimatesTransformed[i] = transformed;
        }

        float y = (float)qppEvaluator.measureCorrelation(perQueryRetEvalMeasure, qppEstimatesTransformed);
        return new CorrelationPair(x, y);
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "qpp.properties";
        }

        Settings.init(args[0]);
        QPPEvaluator qppEvaluator = new QPPEvaluator(Settings.getProp(),
                Settings.getCorrelationMetric(), Settings.getSearcher(),
                Settings.getNumWanted());
        QPPLinearRegressor qppreg = new QPPLinearRegressor(Settings.getProp(), Settings.getTrainPercentage());

        List<TRECQuery> queries = qppEvaluator.constructQueries();
        // create train:test splits
        Collections.shuffle(queries, rnd);
        qppreg.randomSplit(queries);

        QPPMethod [] qppMethods = qppEvaluator.qppMethods();
        Similarity sim = new LMDirichletSimilarity(1000);

        /* Experiment configurations */
        final int nwanted = Settings.getNumWanted();
        final int qppTopK = Settings.getQppTopK();
        QPPMethod method = Settings.getQPPMethod();

        // learn individual regressor learning parameters for individual qpp estimators
        System.out.println(String.format("Fitting linear regression on %s scores", Settings.getRetEvalMetric()));
        RegParameters regParams = qppreg.fit(qppEvaluator, method, Settings.getRetEvalMetric(), sim, nwanted);

        // predict test set values based on individual learning parameters
        CorrelationPair cp = qppreg.predict(regParams,
                qppEvaluator, method, Settings.getRetEvalMetric(), sim, nwanted);
        System.out.println(String.format("CORRELATION (%s): %.4f %.4f",
                Settings.getCorrelationMetric().name(), cp.withoutTransformation, cp.withTransformation));
    }
}