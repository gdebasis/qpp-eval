/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.experiments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.correlation.MinMaxNormalizer;
import org.correlation.QPPCorrelationMetric;
import org.evaluator.Metric;
import org.qpp.QPPMethod;
import org.regressor.FitPolyRegressor;
import org.regressor.RegressionLearner;
import org.trec.TRECQuery;

/**
 *
 * @author suchana
 */

public class QPPPolynomialRegressor {
    Properties                  prop;
    int                         partition;
    int                         degree;
    static List<TRECQuery>      trainQueries;
    static List<TRECQuery>      testQueries;
    static String               qrelsFile;
    static String               queryFile;
    static String               resFileTrain;
    static String               resFileTest;
    static List<RegressionLearner> regressionLearner;
    static QPPCorrelationMetric correlationMetric;
    
    static Random rnd = new Random(Settings.SEED);
    
    public QPPPolynomialRegressor(Properties prop, int partition) {
        this.prop = prop;
        this.partition = partition;
        trainQueries = new ArrayList<>();
        testQueries = new ArrayList<>();
        regressionLearner = new ArrayList<>();
        qrelsFile = prop.getProperty("qrels.file");
        queryFile = prop.getProperty("query.file");
        resFileTrain = prop.getProperty("res.train");
        resFileTest = prop.getProperty("res.test");
        degree = 2;
    }
    
    public void randomSplit(List<TRECQuery> queries) {
        int splitQuery = (int) Math.floor(queries.size() * partition/100);
//        System.out.println("##### : " + splitQuery);
        
        for (int i=0; i<splitQuery; i++) {
            trainQueries.add(queries.get(i));
        }
        for (int i=splitQuery; i<queries.size(); i++) {
            testQueries.add(queries.get(i));
        }
        System.out.println("train : " + trainQueries.size() + "\t" + trainQueries.get(0).id);
        System.out.println("test : " + testQueries.size() + "\t" + testQueries.get(0).id);
    }
    
    public void fitRegressorTrainSetIndividualSetting(QPPMethod [] qppMethods, QPPEvaluator qppEvaluator,
            Similarity sim, int nwanted) throws Exception {
        for (QPPMethod qppMethod: qppMethods) { 
            System.out.println("QPP method : " + qppMethod.name());
            for (Metric m : Metric.values()){
                System.out.println("METRIC : " + m.name());
                RegressionLearner lr = new RegressionLearner();
                lr.setQppMethod(qppMethod.name());
                lr.setMetric(m.name());

                double [] corrMeasure = qppEvaluator.evaluate(trainQueries, sim, m, nwanted);
                double [] qppEstimates = qppEvaluator.getQPPEstimates(qppEvaluator.topDocsMap, qppMethod, trainQueries, m);

                FitPolyRegressor fpr = new FitPolyRegressor(degree);
                double[] coeff = fpr.fitCurve(corrMeasure, qppEstimates);
                lr.setCoeff(coeff);
                regressionLearner.add(lr);
            }
        }
    }
    
    public void predictCorrelationTestSetIndividual(QPPMethod [] qppMethods, QPPEvaluator qppEvaluator,
            Similarity sim, int nwanted) throws Exception {

        for (QPPMethod qppMethod: qppMethods) { 
            System.out.println("QPP method : " + qppMethod.name());
            for (Metric m : Metric.values()){
                System.out.println("METRIC : " + m.name());
                
                double[] corrMeasure = qppEvaluator.evaluate(testQueries, sim, m, nwanted);
                System.out.println("CORR : " + corrMeasure.length);
                corrMeasure = MinMaxNormalizer.normalize(corrMeasure);
                
                double[] qppEstimates = qppEvaluator.getQPPEstimates(qppEvaluator.topDocsMap, qppMethod, testQueries, m);
                System.out.println("ESTIMATE : " + qppEstimates.length);
                double[] qppEstimateWithRegressor = new double[qppEstimates.length];
                
                for (RegressionLearner lr : regressionLearner) {
                    if (lr.getQppMethod().equalsIgnoreCase(qppMethod.name()) && 
                            lr.getMetric().equalsIgnoreCase(m.name())) {

                        int i = 0;
                        for (double x : qppEstimates) {
                            qppEstimateWithRegressor[i] = lr.getSlope() * x + lr.getyIntercept();
                            qppEstimateWithRegressor[i] = lr.getCoeff()[degree] +
                                    lr.getCoeff()[degree-1] * Math.pow(x, degree-1) +
                                    lr.getCoeff()[degree-2] * Math.pow(x, degree-2);
                            i++;
                        } 
                    }
                }
                
                qppEstimateWithRegressor = MinMaxNormalizer.normalize(qppEstimateWithRegressor);  
                double correlation = qppEvaluator.measureCorrelation(corrMeasure, qppEstimateWithRegressor);
                System.out.println("CORRELATION : " + correlation);
            }
        }  
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "qpp.properties";
        }

        try {
            Settings.init(args[0]);
            QPPEvaluator qppEvaluator = new QPPEvaluator(Settings.getProp(),
                    Settings.getCorrelationMetric(), Settings.getSearcher(),
                    Settings.getNumWanted());
            QPPPolynomialRegressor polreg = new QPPPolynomialRegressor(Settings.getProp(), Settings.getTrainPercentage());

            List<TRECQuery> queries = qppEvaluator.constructQueries();
            System.out.println("QUERIES : " + queries.size() + "\t" + queries.get(0).id);
            
            // create train:test splits
            Collections.shuffle(queries, rnd);
            System.out.println("SHUFFLED : " + queries.size() + "\t" + queries.get(0).id);
            polreg.randomSplit(queries);
            
            QPPMethod [] qppMethods = qppEvaluator.qppMethods();
            System.out.println("QPPMETHODS : " + qppMethods.length);
            
            Similarity sim = new LMDirichletSimilarity(1000);

            final int nwanted = Settings.getNumWanted();
            final int qppTopK = Settings.getQppTopK();
            
            // learn individual regressor learning parameters for individual qpp estimators
            polreg.fitRegressorTrainSetIndividualSetting(qppMethods, qppEvaluator, sim, nwanted);
            
            // predict test set values based on individual learning parameters 
            polreg.predictCorrelationTestSetIndividual(qppMethods, qppEvaluator, sim, nwanted);
                        
            for (RegressionLearner foo : regressionLearner) {
                System.out.println(foo.getQppMethod() +"\t" + foo.getMetric() + "\t"
                        + foo.getSlope() + "\t" + foo.getyIntercept());
            }            
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }   
}