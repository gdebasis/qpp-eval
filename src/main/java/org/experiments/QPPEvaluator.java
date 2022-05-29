package org.experiments;

import java.io.*;
import java.util.*;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.correlation.KendalCorrelation;
import org.correlation.MinMaxNormalizer;
import org.correlation.PearsonCorrelation;
import org.correlation.QPPCorrelationMetric;
import org.evaluator.Evaluator;
import org.evaluator.Metric;
import org.evaluator.RetrievedResults;
import org.qpp.*;
import org.trec.TRECQuery;
import org.trec.TRECQueryParser;

import javax.xml.bind.annotation.XmlInlineBinaryData;

class RegParameters {
    SimpleRegression reg;
    double min;
    double max;

    RegParameters(SimpleRegression reg, double min, double max) {
        this.reg = reg;
        this.min = min;
        this.max = max;
    }

    double predict(double x) {
        return MinMaxNormalizer.normalize(reg.predict(x), min, max);
    }
}

public class QPPEvaluator {

    IndexReader               reader;
    IndexSearcher             searcher;
    int                       numWanted;
    Properties                prop;
    Map<String, TopDocs>      topDocsMap;
    QPPCorrelationMetric      correlationMetric;
    TRECQueryParser           trecQueryParser;

    public QPPEvaluator(Properties prop, QPPCorrelationMetric correlationMetric, IndexSearcher searcher, int numWanted) {
        this.prop = prop;
        this.searcher = searcher;
        this.reader = searcher.getIndexReader();
        this.numWanted = numWanted;
        this.correlationMetric = correlationMetric;
    }

    public String getContentFieldName() {
        return prop.getProperty("content.field", "words");
    }

    public String getIdFieldName() {
        return prop.getProperty("id.field", "id");
    }

    private static List<String> buildStopwordList() {
        List<String> stopwords = new ArrayList<>();
        String line;

        try (FileReader fr = new FileReader("stop.txt");
             BufferedReader br = new BufferedReader(fr)) {
            while ( (line = br.readLine()) != null ) {
                stopwords.add(line.trim());
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return stopwords;
    }

    public static Analyzer englishAnalyzerWithSmartStopwords() {
        return new EnglishAnalyzer(
                StopFilter.makeStopSet(buildStopwordList())); // default analyzer
    }

    public Properties getProperties() { return prop; }
    public IndexReader getReader() { return reader; }

    public List<TRECQuery> constructQueries() throws Exception {
        return constructQueries(prop.getProperty("query.file"));
    }

    public List<TRECQuery> constructQueries(String queryFile) throws Exception {
        trecQueryParser = new TRECQueryParser(this, queryFile, englishAnalyzerWithSmartStopwords());
        trecQueryParser.parse();
        return trecQueryParser.getQueries();
    }

    public TopDocs retrieve(TRECQuery query, Similarity sim, int numWanted) throws IOException {
        searcher.setSimilarity(sim);
        return searcher.search(query.getLuceneQueryObj(), numWanted);
    }

    public static Similarity[] modelsToTest() {
        return new Similarity[]{
            new LMJelinekMercerSimilarity(0.6f),
            new LMDirichletSimilarity(1000),
            new BM25Similarity(0.7f, 0.3f),
        };
    }
    
    public static Similarity[] corrAcrossModels() {
        return new Similarity[] {
            new LMJelinekMercerSimilarity(0.3f),
            new LMJelinekMercerSimilarity(0.6f),
            new BM25Similarity(0.7f, 0.3f),
            new BM25Similarity(1.0f, 1.0f),
            new BM25Similarity(0.3f, 0.7f),
            new LMDirichletSimilarity(100),
            new LMDirichletSimilarity(500),
            new LMDirichletSimilarity(1000),
        };
    }

    double averageIDF(Query q) throws IOException {
        long N = reader.numDocs();
        Set<Term> qterms = new HashSet<>();
        //+++LUCENE_COMPATIBILITY: Sad there's no #ifdef like C!
        // 8.x CODE
        q.createWeight(searcher, ScoreMode.COMPLETE, 1).extractTerms(qterms);
        // 5.x CODE
        //q.createWeight(searcher, false).extractTerms(qterms);
        //---LUCENE_COMPATIBILITY

        float aggregated_idf = 0;
        for (Term t: qterms) {
            int n = reader.docFreq(t);
            double idf = Math.log(N/(double)n);
            aggregated_idf += idf;
        }
        return aggregated_idf/(double)qterms.size();
    }

    public Evaluator executeDummy(List<TRECQuery> queries, Similarity sim,
                                    int cutoff, String qrelsFile, String resFile,
                                    Map<String, TopDocs> topDocsMap) throws Exception {

        int numQueries = queries.size();
        double[] evaluatedMetricValues = new double[numQueries];

        FileWriter fw = new FileWriter(resFile);
        BufferedWriter bw = new BufferedWriter(fw);

        for (TRECQuery query : queries) {
            TopDocs topDocs = topDocsMap.get(query.id);
            saveRetrievedTuples(bw, query, topDocs, sim.toString());
        }
        bw.flush();
        bw.close();
        fw.close();

        Evaluator evaluator = new Evaluator(qrelsFile, resFile); // load ret and rel
        return evaluator;
    }

    public Evaluator executeQueries(List<TRECQuery> queries, Similarity sim,
                                    int cutoff, String qrelsFile, String resFile,
                                    Map<String, TopDocs> topDocsMap,
                                    Map<String, Integer> maxDepths) throws Exception {
        int numQueries = queries.size();
        double[] evaluatedMetricValues = new double[numQueries];

        FileWriter fw = new FileWriter(resFile);
        BufferedWriter bw = new BufferedWriter(fw);

        for (TRECQuery query : queries) {
            TopDocs topDocs = retrieve(query, sim, cutoff);
            if (topDocsMap != null)
                topDocsMap.put(query.id, topDocs);
            saveRetrievedTuples(bw, query, topDocs, sim.toString());
        }
        bw.flush();
        bw.close();
        fw.close();

        Evaluator evaluator = new Evaluator(qrelsFile, resFile); // load ret and rel
        return evaluator;
    }

    public Evaluator executeQueries(List<TRECQuery> queries, Similarity sim,
                                    int cutoff, String qrelsFile, String resFile, 
                                    Map<String, TopDocs> topDocsMap) throws Exception {

        int numQueries = queries.size();
        double[] evaluatedMetricValues = new double[numQueries];

        FileWriter fw = new FileWriter(resFile);
        BufferedWriter bw = new BufferedWriter(fw);

        for (TRECQuery query : queries) {
            TopDocs topDocs = retrieve(query, sim, cutoff);
            if (topDocsMap != null)
                topDocsMap.put(query.id, topDocs);
            saveRetrievedTuples(bw, query, topDocs, sim.toString());
        }
        bw.flush();
        bw.close();
        fw.close();

        Evaluator evaluator = new Evaluator(qrelsFile, resFile); // load ret and rel
        return evaluator;
    }

    double[] evaluate(List<TRECQuery> queries, Similarity sim, Metric m, int cutoff) throws Exception {
        topDocsMap = new HashMap<>();

        int numQueries = queries.size();
        double[] evaluatedMetricValues = new double[numQueries];

        FileWriter fw = new FileWriter(Settings.RES_FILE);
        BufferedWriter bw = new BufferedWriter(fw);

        for (TRECQuery query : queries) {
            TopDocs topDocs = retrieve(query, sim, cutoff);
            topDocsMap.put(query.id, topDocs);
            saveRetrievedTuples(bw, query, topDocs, sim.toString());
        }
        bw.flush();
        bw.close();
        fw.close();

        String qrelsFile = prop.getProperty("qrels.file");
        Evaluator evaluator = new Evaluator(qrelsFile, Settings.RES_FILE); // load ret and rel

        int i=0;
        for (TRECQuery query : queries) {
            evaluatedMetricValues[i++] = evaluator.compute(query.id, m);
        }
        return evaluatedMetricValues;
    }

    public double[] getQPPEstimates(
            Map<String, TopDocs> topDocsMap,
            QPPMethod qppMethod,
            List<TRECQuery> queries,
            Metric m) throws Exception {
        return getQPPEstimates(topDocsMap, qppMethod, queries, m, null);
    }

    public double[] getQPPEstimates(
            Map<String, TopDocs> topDocsMap,
            QPPMethod qppMethod,
            List<TRECQuery> queries,
            Metric m, RegParameters reg) throws Exception {
        double estimatedScore;
        double[] qppEstimates = new double[queries.size()];

        int qppTopK = Settings.getQppTopK();
        String qrelsFile = Settings.getQrelsFile();
        Evaluator evaluator = new Evaluator(qrelsFile, Settings.RES_FILE); // load ret and rel

        int i = 0;
        for (TRECQuery query : queries) {
            RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.id);
            TopDocs topDocs = topDocsMap.get(query.id);
            if (topDocs==null) {
                System.err.println(String.format("No Topdocs found for query %s", query.id));
                estimatedScore = 0;
            }
            else
                estimatedScore = qppMethod.computeSpecificity(query.getLuceneQueryObj(), rr, topDocs, qppTopK);

            if (reg != null) {
                estimatedScore = reg.predict(estimatedScore); // transform the score
            }
            qppEstimates[i++] = estimatedScore;
        }
        return qppEstimates;
    }

    public double evaluateQPPOnModel(
            Map<String, TopDocs> topDocsMap,
            QPPMethod qppMethod,
            List<TRECQuery> queries,
            double[] evaluatedMetricValues,
            Metric m) throws Exception {
        double[] estimates = getQPPEstimates(topDocsMap, qppMethod, queries, m);
        return correlationMetric.correlation(evaluatedMetricValues, estimates);
    }

    /* Returns a map of qid :-> ret_eval_value (e.g. AP value) --- works with regression */
    public RegParameters evaluateQPPOnModel(
            Map<String, TopDocs> topDocsMap,
            QPPMethod qppMethod,
            List<TRECQuery> queries,
            double[] evaluatedMetricValues,
            Metric m, boolean transform) throws Exception {

        double[] estimates = getQPPEstimates(topDocsMap, qppMethod, queries, m);

        SimpleRegression reg = new SimpleRegression();
        double[] n_pred = MinMaxNormalizer.normalize(estimates); // in [0, 1]
        for (int i=0; i<evaluatedMetricValues.length; i++) {
            reg.addData(n_pred[i], evaluatedMetricValues[i]);
        }
        return new RegParameters(reg,
                Arrays.stream(estimates).min().getAsDouble(),
                Arrays.stream(estimates).max().getAsDouble());
    }

    /* Returns a map of qid :-> ret_eval_value (e.g. AP value) --- works with regression */
    public RegParameters evaluateQPPOnModel(
            QPPMethod qppMethod,
            List<TRECQuery> queries,
            double[] evaluatedMetricValues,
            Metric m, boolean transform) throws Exception {
        return evaluateQPPOnModel(this.topDocsMap, qppMethod, queries, evaluatedMetricValues, m, transform);
    }

    public double measureCorrelation(double[] evaluatedMetricValues, double[] qppEstimates) {
        return correlationMetric.correlation(evaluatedMetricValues, qppEstimates);
    }
    
    public QPPMethod[] qppMethods() {
        QPPMethod[] qppMethods = {
                new AvgIDFSpecificity(searcher),
                new NQCSpecificity(searcher),
                new ClaritySpecificity(searcher),
                new WIGSpecificity(searcher),
                new UEFSpecificity(new NQCSpecificity(searcher)),
                new UEFSpecificity(new ClaritySpecificity(searcher)),
                new UEFSpecificity(new WIGSpecificity(searcher)),
        };
        return qppMethods;
    }

    public void relativeSystemRanksAcrossMetrics(List<TRECQuery> queries) throws Exception {
        Similarity[] sims = modelsToTest();
        for (Similarity sim: sims) {
            relativeSystemRanksAcrossMetrics(sim, queries, numWanted);
        }
    }

    public void relativeSystemRanksAcrossMetrics(List<TRECQuery> queries, int cutoff) throws Exception {
        Similarity[] sims = modelsToTest();
        for (Similarity sim: sims) {
            relativeSystemRanksAcrossMetrics(sim, queries, cutoff);
        }
    }

    /*
    Compute how much system rankings change with different settings for different IR models (BM25, LM etc.).
    For each model compute the average rank shift over a range of
    different metrics. For stability, these numbers should be high.
     */
    public void relativeSystemRanksAcrossMetrics(Similarity sim, List<TRECQuery> queries, int cutoff) throws Exception {
        int i, j, k;

        Metric[] metricForEval = Metric.values();
        QPPMethod[] qppMethods = qppMethods();

        // Rho and tau scores across the QPP methods.
        double[][] corr_scores = new double[metricForEval.length][qppMethods.length];
        double rankcorr;

        Map<Integer, double[]> preEvaluated = new HashMap<>();
        Map<String, TopDocs> topDocsMap[] = new Map[metricForEval.length];

        for (i=0; i< metricForEval.length; i++) { // pre-evaluate for each metric
            Metric m = metricForEval[i];
            double[] evaluatedMetricValues = evaluate(queries, sim, m, cutoff);
            topDocsMap[i] = this.topDocsMap; // store this map

            preEvaluated.put(i, evaluatedMetricValues);
            System.out.println(String.format("Average %s (IR-model: %s, Metric: %s): %.4f",
                    m.toString(), sim.toString(), m.toString(),
                    StatUtils.mean(evaluatedMetricValues)));
        }

        k = 0;
        for (QPPMethod qppMethod: qppMethods) {
            System.out.println(qppMethod.name());
            for (i = 0; i < metricForEval.length-1; i++) {
                rankcorr = evaluateQPPOnModel(topDocsMap[i], qppMethod, queries, preEvaluated.get(i), metricForEval[i]);
                System.out.println(rankcorr);
                corr_scores[i][k] = rankcorr;

                for (j = i+1; j < metricForEval.length; j++) {
                    rankcorr = evaluateQPPOnModel(topDocsMap[j], qppMethod, queries, preEvaluated.get(j), metricForEval[j]);
                    System.out.println(rankcorr);
                    corr_scores[j][k] = rankcorr;
                }
            }
            k++;
        }

        System.out.println("Contingency for IR model: " + sim.toString());
        for (i = 0; i < metricForEval.length-1; i++) {
            System.out.println("Rho values using " + sim.toString() + ", " + metricForEval[i].name() + " as GT");
            for (j = i + 1; j < metricForEval.length; j++) {
                double inter_corr = rankCorrAcrossCutOffs(corr_scores, i, j, new PearsonCorrelation());
                System.out.println("Rho values using " + sim.toString() + ", " + metricForEval[j].name() + " as GT");
                System.out.printf("%s %s/%s: %s = %.4f \n",
                        sim.toString(), metricForEval[i].name(), metricForEval[j].name(), correlationMetric.name(), inter_corr);
            }
        }
    }

    private RegParameters fit(
            List<TRECQuery> trainQueries,
            QPPMethod qppMethod, Similarity sim, Metric m) throws Exception {

        double[] retEvalMeasure = evaluate(trainQueries, sim, m, Settings.getNumWanted());
        return evaluateQPPOnModel(qppMethod, trainQueries, retEvalMeasure, m, true);
    }

    private RegParameters fit(
            Map<String, TopDocs> topDocsMap,
            List<TRECQuery> trainQueries,
            QPPMethod qppMethod, Similarity sim, Metric m) throws Exception {

        double[] retEvalMeasure = evaluate(trainQueries, sim, m, Settings.getNumWanted());
        return evaluateQPPOnModel(topDocsMap, qppMethod, trainQueries, retEvalMeasure, m, true);
    }

    public void relativeSystemRanksAcrossMetrics(Similarity sim,
             List<TRECQuery> trainQueries,
             List<TRECQuery> testQueries, int cutoff) throws Exception {
        int i, j, k;

        Metric[] metricForEval = Metric.values();
        QPPMethod[] qppMethods = qppMethods();

        // Rho and tau scores across the QPP methods.
        double[][] corr_scores = new double[metricForEval.length][qppMethods.length];
        double rankcorr;

        int numQueries = testQueries.size();
        Map<String, double[]> preEvaluated = new HashMap<>();
        Map<String, TopDocs> topDocsMap[] = new Map[metricForEval.length];

        for (i=0; i< metricForEval.length; i++) { // pre-evaluate for each metric
            Metric m = metricForEval[i];
            double[] evaluatedMetricValues = evaluate(trainQueries, sim, m, cutoff);
            topDocsMap[i] = this.topDocsMap;
            preEvaluated.put(metricForEval[i].name(), evaluatedMetricValues);
            System.out.println(String.format("Average %s (IR-model: %s, Metric: %s): %.4f",
                    m.toString(), sim.toString(), m.toString(),
                    StatUtils.mean(evaluatedMetricValues)));
        }

        // Learn the map for each ret-eval-metric : qpp estimate pair to be used later during prediction
        Map<String, RegParameters> transformerMaps = new HashMap<>();
        for (QPPMethod qppMethod: qppMethods) {
            for (i=0; i< metricForEval.length; i++) {
                Metric m = metricForEval[i];
                System.out.println(String.format("Fitting linear regression on %s scores and %s estimates",
                        m.name(), qppMethod.name()));
                RegParameters scoreTransformerModel = fit(topDocsMap[i], trainQueries, qppMethod, sim, m);
                transformerMaps.put(String.format("%s:%s", qppMethod.name(), m.name()), scoreTransformerModel);
            }
        }

        // Now use these score transformers on the test set...
        RegParameters scoreTransformerModel = null;
        double[] evaluatedMetricValues = null;
        double[] qppEstimates = null;

        preEvaluated.clear();
        for (i=0; i< metricForEval.length; i++) { // pre-evaluate for each metric on the test set now
            Metric m = metricForEval[i];
            evaluatedMetricValues = evaluate(testQueries, sim, m, cutoff);
            topDocsMap[i] = this.topDocsMap; // topdocs for each query for each metric
            preEvaluated.put(metricForEval[i].name(), evaluatedMetricValues);
            System.out.println(String.format("Average %s (IR-model: %s, Metric: %s): %.4f",
                    m.toString(), sim.toString(), m.toString(),
                    StatUtils.mean(evaluatedMetricValues)));
        }

        k = 0;  // k is the index for a qpp method
        for (QPPMethod qppMethod: qppMethods) {
            for (i = 0; i < metricForEval.length-1; i++) {
                // get the transformer for the correct pair
                scoreTransformerModel =
                        transformerMaps.get(String.format("%s:%s", qppMethod.name(), metricForEval[i].name()));

                // Get the ret-eval scores
                evaluatedMetricValues = preEvaluated.get(metricForEval[i].name());
                qppEstimates = getQPPEstimates(topDocsMap[i], qppMethod,
                        testQueries, metricForEval[i], scoreTransformerModel);

                // Now compute the correlation after a minmax transform
                corr_scores[i][k] = correlationMetric.correlation(evaluatedMetricValues, qppEstimates);;

                for (j = i+1; j < metricForEval.length; j++) {
                    // And the same to be done here for the inner loop... make sure u have the index 'j'
                    // First, get the transformer for the correct pair
                    scoreTransformerModel =
                        transformerMaps.get(String.format("%s:%s", qppMethod.name(), metricForEval[j].name()));

                    // Get the ret-eval scores
                    evaluatedMetricValues = preEvaluated.get(metricForEval[j].name());
                    qppEstimates = getQPPEstimates(topDocsMap[j], qppMethod, testQueries,
                            metricForEval[j], scoreTransformerModel);

                    // Now compute the correlation after a minmax transform
                    corr_scores[j][k] = correlationMetric.correlation(evaluatedMetricValues, qppEstimates);;
                }
            }
            k++;
        }

        System.out.println("Contingency for IR model: " + sim.toString());
        for (i = 0; i < metricForEval.length-1; i++) {
            System.out.println("Rho values using " + sim.toString() + ", " + metricForEval[i].name() + " as GT");
            for (j = i + 1; j < metricForEval.length; j++) {
                double inter_corr = rankCorrAcrossCutOffs(corr_scores, i, j, new PearsonCorrelation());
                System.out.println("Rho values using " + sim.toString() + ", " + metricForEval[j].name() + " as GT");
                System.out.printf("%s %s/%s: %s = %.4f \n",
                        sim.toString(), metricForEval[i].name(), metricForEval[j].name(), correlationMetric.name(), inter_corr);
            }
        }
    }

    /*
        Compute how much system rankings change with different settings for metric (AP, P@5)
        or retrieval model used. For each metric compute the average rank correlation over a range of
        different retrieval models. For stability, these numbers should be high.
     */
    public void relativeSystemRanksAcrossSims(Metric m,
                                              List<TRECQuery> trainQueries,
                                              List<TRECQuery> testQueries
                                              ) throws Exception {
        int i, j, k;
        int cutoff = Settings.getNumWanted();

        Similarity[] sims = corrAcrossModels();
        QPPMethod[] qppMethods = qppMethods();

        // Rho and tau scores across the QPP methods.
        double[][] corr_scores = new double[sims.length][qppMethods.length];
        double rankcorr;

        Map<String, TopDocs> topDocsMap[] = new Map[sims.length];
        double[] evaluatedMetricValues = null;

        for (i=0; i<sims.length; i++) {
            evaluatedMetricValues = evaluate(trainQueries, sims[i], m, cutoff);
            topDocsMap[i] = this.topDocsMap;
            System.out.println(String.format("Average %s (IR-model: %s, Metric: %s): %.4f",
                    m.toString(), sims[i].toString(), m.toString(),
                    StatUtils.mean(evaluatedMetricValues)));
        }

        // Learn the map for each ret-eval-metric : qpp estimate pair to be used later during prediction
        Map<String, RegParameters> transformerMaps = new HashMap<>();
        for (QPPMethod qppMethod: qppMethods) {
            for (i=0; i<sims.length; i++) {
                Similarity sim = sims[i];
                System.out.println(String.format("Fitting linear regression on %s scores and %s estimates",
                        m.name(), qppMethod.name()));
                RegParameters scoreTransformerModel = fit(topDocsMap[i], trainQueries, qppMethod, sim, m);
                transformerMaps.put(String.format("%s:%d", qppMethod.name(), i), scoreTransformerModel);
            }
        }

        k = 0;
        // Now use these score transformers on the test set...
        RegParameters scoreTransformerModel = null;
        double[] qppEstimates = null;
        Map<Integer, double[]> evaluatedMetricValuesSims = new HashMap<>();
        for (i = 0; i < sims.length; i++) {
            evaluatedMetricValues = evaluate(testQueries, sims[i], m, cutoff);
            topDocsMap[i] = this.topDocsMap;
            evaluatedMetricValuesSims.put(i, evaluatedMetricValues);
        }

        for (QPPMethod qppMethod: qppMethods) {
            for (i = 0; i < sims.length-1; i++) {
                // get the transformer for the correct pair
                scoreTransformerModel =
                        transformerMaps.get(String.format("%s:%d", qppMethod.name(), i));

                // Get the ret-eval scores
                evaluatedMetricValues = evaluatedMetricValuesSims.get(i);
                qppEstimates = getQPPEstimates(topDocsMap[i], qppMethod,
                        testQueries, m, scoreTransformerModel);

                // Now compute the correlation after a minmax transform
                System.out.println(evaluatedMetricValues.length + ", " + qppEstimates.length);
                rankcorr = correlationMetric.correlation(evaluatedMetricValues, qppEstimates);

                corr_scores[i][k] = rankcorr;

                for (j = i+1; j < sims.length; j++) {
                    scoreTransformerModel =
                            transformerMaps.get(String.format("%s:%d", qppMethod.name(), j));

                    // Get the ret-eval scores
                    evaluatedMetricValues = evaluatedMetricValuesSims.get(j);
                    qppEstimates = getQPPEstimates(topDocsMap[j], qppMethod,
                            testQueries, m, scoreTransformerModel);

                    // Now compute the correlation after a minmax transform
                    rankcorr = correlationMetric.correlation(evaluatedMetricValues, qppEstimates);

                    corr_scores[j][k] = rankcorr;
                }
            }
            k++;
        }

        System.out.println("Contingency for metric: " + m.toString());
        for (i = 0; i < sims.length-1; i++) {
            //System.out.println("Rho values using " + sims[i].toString() + ", " + m.name() + " as GT: " + getRowVector_Str(rho_scores, i));
            for (j = i + 1; j < sims.length; j++) {
                //System.out.println("Rho values using " + sims[i].toString() + ", " + m.name() + " as GT: " + getRowVector_Str(rho_scores, i));
                double inter_corr = rankCorrAcrossCutOffs(corr_scores, i, j, new KendalCorrelation());
                System.out.printf("%s %s/%s: %s = %.4f \n",
                        m.name(), sims[i].toString(), sims[j].toString(), correlationMetric.name(), inter_corr);
            }
        }
    }

    /*
    Compute how much system rankings change with different settings for metric (AP, P@5)
    or retrieval model used. For each metric compute the average rank correlation over a range of
    different retrieval models. For stability, these numbers should be high.
     */
    public void relativeSystemRanksAcrossSims(Metric m, List<TRECQuery> queries) throws Exception {
        int i, j, k;
        int cutoff = Settings.getNumWanted();
        Similarity[] sims = corrAcrossModels();
        QPPMethod[] qppMethods = qppMethods();

        // Rho and tau scores across the QPP methods.
        double[][] corr_scores = new double[sims.length][qppMethods.length];
        double rankcorr;

        int numQueries = queries.size();
        Map<Integer, double[]> evaluatedMetricValuesSims = new HashMap<>();
        Map<String, TopDocs> topDocsMap[] = new Map[sims.length];

        for (i=0; i<sims.length; i++) {
            double[] evaluatedMetricValues = evaluate(queries, sims[i], m, cutoff);
            topDocsMap[i] = this.topDocsMap;
            evaluatedMetricValuesSims.put(i, evaluatedMetricValues);
            System.out.println(String.format("Average %s (IR-model: %s, Metric: %s): %.4f",
                    m.toString(), sims[i].toString(), m.toString(),
                    StatUtils.mean(evaluatedMetricValues)));
        }

        k = 0;
        for (QPPMethod qppMethod: qppMethods) {
            for (i = 0; i < sims.length-1; i++) {
                rankcorr = evaluateQPPOnModel(topDocsMap[i], qppMethod, queries, evaluatedMetricValuesSims.get(i), m);
                System.out.println(rankcorr);
                corr_scores[i][k] = rankcorr;

                for (j = i+1; j < sims.length; j++) {
                    rankcorr = evaluateQPPOnModel(topDocsMap[j], qppMethod, queries, evaluatedMetricValuesSims.get(j), m);
                    System.out.println(rankcorr);
                    corr_scores[j][k] = rankcorr;
                }
            }
            k++;
        }

        System.out.println("Contingency for metric: " + m.toString());
        for (i = 0; i < sims.length-1; i++) {
            //System.out.println("Rho values using " + sims[i].toString() + ", " + m.name() + " as GT: " + getRowVector_Str(rho_scores, i));
            for (j = i + 1; j < sims.length; j++) {
                //System.out.println("Rho values using " + sims[i].toString() + ", " + m.name() + " as GT: " + getRowVector_Str(rho_scores, i));
                double inter_corr = rankCorrAcrossCutOffs(corr_scores, i, j, new KendalCorrelation());
                System.out.printf("%s %s/%s: %s = %.4f \n",
                        m.name(), sims[i].toString(), sims[j].toString(), correlationMetric.name(), inter_corr);
            }
        }
    }

    public void evaluateQPPAllWithCutoffs(List<TRECQuery> queries) throws Exception {
        QPPMethod[] qppMethods = qppMethods();
        for (QPPMethod qppMethod: qppMethods) {
            System.out.println("Results with " + qppMethod.name());
            evaluateQPPAllWithCutoffs(qppMethod, queries);
        }
    }

    public void evaluateQPPAllWithCutoffs(QPPMethod qppMethod, List<TRECQuery> queries) throws Exception {
        // Measure the relative stability of the rank of different systems
        // with varying number of top-docs and metrics
        Metric[] metricForEval = Metric.values();
        Similarity[] sims = modelsToTest();
        int i, j;
        final int numCutOffs = 10;
        final int cutOffStep = 10;

        for (Metric m : metricForEval) {
            for (i=0; i<numCutOffs; i++) {
                int cutoff = (i+1)*cutOffStep;
                for (Similarity sim : sims) {
                    double[] evaluatedMetricValues = evaluate(queries, sim, m, cutoff);
                    double rankcorr = evaluateQPPOnModel(this.topDocsMap, qppMethod, queries, evaluatedMetricValues, m);
                    System.out.printf("Model: %s, Metric %s: QPP-corr (%s) = %.4f%n",
                            sim.toString(), m.toString(), rankcorr);
                }
            }
        }
    }

    public void evaluateQPPAtCutoff(QPPMethod qppMethod,
                                    List<TRECQuery> trainQueries,
                                    List<TRECQuery> testQueries,
                                    int cutoff) throws Exception {

        Map<String, RegParameters> transformerMaps = new HashMap<>();
        Metric m = Settings.getRetEvalMetric();
        Similarity sim = Settings.getRetModel();

        // Train regressors on all combinations of 'm' and 'sim'
        System.out.println(String.format(
                "Training regression model for (Similarity: %s, Metric: %s)...", sim.toString(), m.name())
        );

        RegParameters regModel = fit(trainQueries, qppMethod, sim, m);
        transformerMaps.put(String.format("%s:%s", m.name(), sim.toString()), regModel);

        // Now, testing time begins! yay!!
        evaluateQPPAtCutoff(qppMethod, testQueries, cutoff, transformerMaps);
    }

    public void evaluateQPPAtCutoff(QPPMethod qppMethod,
                                    List<TRECQuery> queries,
                                    int cutoff,
                                    Map<String, RegParameters> transformerMaps) throws Exception {
        double[] estimates = null;
        Metric m = Settings.getRetEvalMetric();
        Similarity sim = Settings.getRetModel();

        double[] evaluatedMetricValues = evaluate(queries, sim, m, cutoff);
        System.out.println(String.format("Average %s (IR-model: %s, Metric: %s): %.4f",
                m.toString(), sim.toString(), m.toString(),
                StatUtils.mean(evaluatedMetricValues)));

        if (transformerMaps != null) {
            RegParameters reg = transformerMaps.get(
                    String.format("%s:%s", m.name(), sim.toString()));
            estimates = getQPPEstimates(topDocsMap, qppMethod, queries, m, reg);
        }
        else {
            estimates = getQPPEstimates(topDocsMap, qppMethod, queries, m);
        }
        double rankcorr = correlationMetric.correlation(evaluatedMetricValues, estimates);

        System.out.printf("QPP-method: %s Model: %s, Metric %s: %s = %.4f%n", qppMethod.name(),
                sim.toString(), m.toString(), correlationMetric.name(), rankcorr);
    }

    public void evaluateQPPAtCutoff(QPPMethod qppMethod, List<TRECQuery> queries, int cutoff) throws Exception {
        evaluateQPPAtCutoff(qppMethod, queries, cutoff, null);
    }

    double rankCorrAcrossCutOffs(double[][] rankCorrMatrix, int row_a, int row_b,
                                 QPPCorrelationMetric correlationMetric) {
        double[] rc_a = getRowVector(rankCorrMatrix, row_a);
        double[] rc_b = getRowVector(rankCorrMatrix, row_b);
        return correlationMetric.correlation(rc_a, rc_b);
    }

    double[] getRowVector(double[][] rankCorrMatrix, int row) {
        double[] values = new double[rankCorrMatrix[row].length];
        for (int j=0; j < values.length; j++) {
            values[j] = rankCorrMatrix[row][j];
        }
        return values;
    }

    String getRowVector_Str(double[][] rankCorrMatrix, int row) {
        double[] rowvec = getRowVector(rankCorrMatrix, row);
        StringBuilder buff = new StringBuilder("{");
        for (double x: rowvec)
            buff.append(x).append(" ");

        return buff.append("}").toString();
    }

    public void saveRetrievedTuples(BufferedWriter bw, TRECQuery query,
                                    TopDocs topDocs, String runName) throws Exception {
        StringBuilder buff = new StringBuilder();
        ScoreDoc[] hits = topDocs.scoreDocs;
        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            buff.append(query.id.trim()).append("\tQ0\t").
                    append(d.get(getIdFieldName())).append("\t").
                    append((i+1)).append("\t").
                    append(hits[i].score).append("\t").
                    append(runName).append("\n");
        }
        bw.write(buff.toString());
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "qpp.properties";
        }

        try {
            Settings.init(args[0]);

            QPPEvaluator qppEvaluator = new QPPEvaluator(
                    Settings.getProp(), Settings.getCorrelationMetric(),
                    Settings.getSearcher(), Settings.getNumWanted());
            List<TRECQuery> queries = qppEvaluator.constructQueries();

            boolean toTransform = Settings.getCorrelationMetric().name().equals("rmse") &&
                    Boolean.parseBoolean(Settings.getProp().getProperty("transform_scores", "false"));

            Collections.shuffle(queries, new Random(Settings.SEED));
            int splitIndex = (int) (queries.size() * Settings.getTrainPercentage() / 100);
            List<TRECQuery> trainQueries = queries.subList(0, splitIndex);
            List<TRECQuery> testQueries = queries.subList(splitIndex, queries.size());

            System.out.println(String.format(
                    "#Train Queries : %d, #Test queries: %d", trainQueries.size(), testQueries.size()));
            System.out.println("QPP method loaded : " + Settings.getQPPMethod().name() +
                    "\tMeasure specificity on docs :" + Settings.getNumWanted());

            if (!toTransform) {
                qppEvaluator.evaluateQPPAtCutoff(Settings.getQPPMethod(), testQueries, Settings.getNumWanted());
            }
            else {
                qppEvaluator.evaluateQPPAtCutoff(Settings.getQPPMethod(), trainQueries, testQueries, Settings.getNumWanted());
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
