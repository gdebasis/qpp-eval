package org.retriever;

import java.io.*;
import java.util.*;

import org.qpp.*;
import org.evaluator.Evaluator;
import org.evaluator.Metric;
import org.evaluator.RetrievedResults;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.*;
import org.trec.TRECQuery;
import org.trec.TRECQueryParser;

public class QPPEvaluator {

    IndexReader reader;
    IndexSearcher searcher;
    int numWanted;
    Properties prop;
    String runName;
    Map<String, TopDocs> topDocsMap;
    QPPCorrelationMetric correlationMetric;

    public QPPEvaluator(Properties prop, QPPCorrelationMetric correlationMetric) {
        this.prop = prop;

        try {
            File indexDir = new File(prop.getProperty("index.dir"));
            System.out.println("Running queries against index: " + indexDir.getPath());

            reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
            searcher = new IndexSearcher(reader);
            numWanted = Integer.parseInt(prop.getProperty("retrieve.num_wanted", "1000"));
            runName = prop.getProperty("retrieve.runname", "lm");
            this.correlationMetric = correlationMetric;
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
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
        String queryFile = prop.getProperty("query.file");
        TRECQueryParser parser = new TRECQueryParser(this, queryFile, englishAnalyzerWithSmartStopwords());
        parser.parse();
        return parser.getQueries();
    }

    TopDocs retrieve(TRECQuery query, Similarity sim, int numWanted) throws IOException {
        searcher.setSimilarity(sim);
        return searcher.search(query.getLuceneQueryObj(), numWanted);
    }

    Similarity[] modelsToTest() {
        return new Similarity[]{
            new LMJelinekMercerSimilarity(0.6f),
            new LMDirichletSimilarity(1000),
            new BM25Similarity(1.5f, 0.75f),
            new BM25Similarity(0.5f, 1.0f)
        };
    }

    double averageIDF(Query q) throws IOException {
        long N = reader.numDocs();
        Set<Term> qterms = new HashSet<>();
        q.createWeight(searcher, ScoreMode.COMPLETE, 1).extractTerms(qterms);

        float aggregated_idf = 0;
        for (Term t: qterms) {
            int n = reader.docFreq(t);
            double idf = Math.log(N/(double)n);
            aggregated_idf += idf;
        }
        return aggregated_idf/(double)qterms.size();
    }

    double[] evaluate(List<TRECQuery> queries, Similarity sim, Metric m) throws Exception {
        return evaluate(queries, sim, m, numWanted);
    }

    // Evaluate a given metric (e.g. AP/P@5) for all queries. Return an array of these computed values
    double[] evaluate(List<TRECQuery> queries, Similarity sim, Metric m, int cutoff) throws Exception {
        topDocsMap = new HashMap<>();

        int numQueries = queries.size();
        double[] evaluatedMetricValues = new double[numQueries];

        final String resFile = "/tmp/res";
        FileWriter fw = new FileWriter(resFile);
        BufferedWriter bw = new BufferedWriter(fw);

        for (TRECQuery query : queries) {
            TopDocs topDocs = retrieve(query, sim, cutoff);
            topDocsMap.put(query.title, topDocs);
            saveRetrievedTuples(bw, query, topDocs, sim.toString());
        }
        bw.flush();
        bw.close();
        fw.close();

        String qrelsFile = prop.getProperty("qrels.file");
        Evaluator evaluator = new Evaluator(qrelsFile, resFile); // load ret and rel

        int i=0;
        for (TRECQuery query : queries) {
            evaluatedMetricValues[i++] = evaluator.compute(query.id, m);
        }
        return evaluatedMetricValues;
    }

    public double evaluateQPPOnModel(
            QPPMethod qppMethod,
           List<TRECQuery> queries,
           double[] evaluatedMetricValues,
           Metric m) throws Exception {
        final String resFile = "/tmp/res";
        double[] qppEstimates = new double[queries.size()];
        int i = 0;

        int qppTopK = Integer.parseInt(prop.getProperty("qpp.numtopdocs"));
        String qrelsFile = prop.getProperty("qrels.file");
        Evaluator evaluator = new Evaluator(qrelsFile, resFile); // load ret and rel

        for (TRECQuery query : queries) {
            RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.id);
            TopDocs topDocs = topDocsMap.get(query.title);
            if (topDocs==null) {
                System.err.println("No Topdocs found for query <" + query.title + ">");
                System.exit(1);
            }
            qppEstimates[i] = qppMethod.computeSpecificity(query.getLuceneQueryObj(), rr, topDocs, qppTopK);
            i++;
        }

        return correlationMetric.correlation(evaluatedMetricValues, qppEstimates);
    }

    QPPMethod[] qppMethods() {
        QPPMethod[] qppMethods = {
                new AvgIDFSpecificity(searcher),
                new NQCSpecificity(searcher),
                new ClaritySpecificity(searcher),
                new WIGSpecificity(searcher),
                new UEFSpecificity(new AvgIDFSpecificity(searcher)),
                new UEFSpecificity(new NQCSpecificity(searcher)),
                new UEFSpecificity(new ClaritySpecificity(searcher)),
                new UEFSpecificity(new WIGSpecificity(searcher)),
        };
        return qppMethods;
    }

    public void relativeSystemRanksAcrossSims(List<TRECQuery> queries) throws Exception {
        relativeSystemRanksAcrossSims(queries, numWanted);
    }

    public void relativeSystemRanksAcrossSims(List<TRECQuery> queries, int cutoff) throws Exception {
        Metric[] metricForEval = Metric.values();
        for (Metric m: metricForEval) {
            relativeSystemRanksAcrossSims(m, queries, cutoff);
        }
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

        int numQueries = queries.size();
        Map<Integer, double[]> preEvaluated = new HashMap<>();

        for (i=0; i< metricForEval.length; i++) { // pre-evaluate for each metric
            Metric m = metricForEval[i];
            double[] evaluatedMetricValues = evaluate(queries, sim, m, cutoff);
            preEvaluated.put(i, evaluatedMetricValues);
            System.out.println(String.format("Average %s (IR-model: %s, Metric: %s): %.4f",
                    m.toString(), sim.toString(), m.toString(),
                    StatUtils.mean(evaluatedMetricValues)));
        }

        k = 0;
        for (QPPMethod qppMethod: qppMethods) {
            for (i = 0; i < metricForEval.length-1; i++) {
                rankcorr = evaluateQPPOnModel(qppMethod, queries, preEvaluated.get(i), metricForEval[i]);
                corr_scores[i][k] = rankcorr;

                for (j = i+1; j < metricForEval.length; j++) {
                    rankcorr = evaluateQPPOnModel(qppMethod, queries, preEvaluated.get(j), metricForEval[j]);
                    corr_scores[j][k] = rankcorr;
                }
            }
            k++;
        }

        System.out.println("Contingency for IR model: " + sim.toString());
        for (i = 0; i < metricForEval.length-1; i++) {
            //System.out.println("Rho values using " + sim.toString() + ", " + metricForEval[i].name() + " as GT: " + getRowVector_Str(corr_scores, i));
            for (j = i + 1; j < metricForEval.length; j++) {
                double inter_corr = rankCorrAcrossCutOffs(corr_scores, i, j);
                //System.out.println("Rho values using " + sim.toString() + ", " + metricForEval[j].name() + " as GT: " + getRowVector_Str(corr_scores, j));
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
    public void relativeSystemRanksAcrossSims(Metric m, List<TRECQuery> queries, int cutoff) throws Exception {
        int i, j, k;

        Similarity[] sims = modelsToTest();
        QPPMethod[] qppMethods = qppMethods();

        // Rho and tau scores across the QPP methods.
        double[][] corr_scores = new double[sims.length][qppMethods.length];
        double rankcorr;

        int numQueries = queries.size();
        Map<Integer, double[]> evaluatedMetricValuesSims = new HashMap<>();

        for (i=0; i<sims.length; i++) {
            double[] evaluatedMetricValues = evaluate(queries, sims[i], m, cutoff);
            evaluatedMetricValuesSims.put(i, evaluatedMetricValues);
            System.out.println(String.format("Average %s (IR-model: %s, Metric: %s): %.4f",
                    m.toString(), sims[i].toString(), m.toString(),
                    StatUtils.mean(evaluatedMetricValues)));
        }

        k = 0;
        for (QPPMethod qppMethod: qppMethods) {
            for (i = 0; i < sims.length-1; i++) {
                rankcorr = evaluateQPPOnModel(qppMethod, queries, evaluatedMetricValuesSims.get(i), m);
                corr_scores[i][k] = rankcorr;

                for (j = i+1; j < sims.length; j++) {
                    rankcorr = evaluateQPPOnModel(qppMethod, queries, evaluatedMetricValuesSims.get(j), m);
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
                double inter_corr = rankCorrAcrossCutOffs(corr_scores, i, j);
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
                    double rankcorr = evaluateQPPOnModel(qppMethod, queries, evaluatedMetricValues, m);
                    System.out.printf("Model: %s, Metric %s: QPP-corr (%s) = %.4f%n",
                            sim.toString(), m.toString(), rankcorr);
                }
            }
        }
    }

    public void evaluateQPPAtCutoff(List<TRECQuery> queries) throws Exception {
        QPPMethod[] qppMethods = qppMethods();
        for (QPPMethod qppMethod: qppMethods) {
            evaluateQPPAtCutoff(qppMethod, queries, numWanted);
        }
    }

    public void evaluateQPPAtCutoff(QPPMethod qppMethod, List<TRECQuery> queries, int cutoff) throws Exception {
        Metric[] metricForEval = Metric.values();
        Similarity[] sims = modelsToTest();

        for (Metric m : metricForEval) {
            for (Similarity sim : sims) {
                double[] evaluatedMetricValues = evaluate(queries, sim, m, cutoff);
                System.out.println(String.format("Average %s (IR-model: %s, Metric: %s): %.4f",
                        m.toString(), sim.toString(), m.toString(),
                        StatUtils.mean(evaluatedMetricValues)));

                double rankcorr = evaluateQPPOnModel(qppMethod, queries, evaluatedMetricValues, m);
                System.out.printf("QPP-method: %s Model: %s, Metric %s: %s = %.4f%n", qppMethod.name(),
                        sim.toString(), m.toString(), correlationMetric.name(), rankcorr);
            }
        }
    }

    double rankCorrAcrossCutOffs(double[][] rankCorrMatrix, int row_a, int row_b) {
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
            args[0] = "init.properties";
        }

        try {
            Properties prop = new Properties();
            prop.load(new FileReader(args[0]));

            QPPCorrelationMetric[] qppCorrelationMetrics = {
                    //new SpearmanCorrelation(),
                    //new KendalCorrelation(),
                    new QuantizedSimCorrelation(Integer.parseInt(prop.getProperty("qsim.numintervals", "5")))
                    //new QuantizedStrictMatchCorrelation(Integer.parseInt(prop.getProperty("qsim.numintervals", "5")))
            };

            for (QPPCorrelationMetric correlationMetric: qppCorrelationMetrics) {
                QPPEvaluator qppEvaluator = new QPPEvaluator(prop, correlationMetric);
                List<TRECQuery> queries = qppEvaluator.constructQueries();
                qppEvaluator.evaluateQPPAtCutoff(queries);

                //No need to test with all cutoffs
                //qppEvaluator.evaluateQPPAllWithCutoffs(queries);

                //qppEvaluator.relativeSystemRanksAcrossSims(queries);
                //qppEvaluator.relativeSystemRanksAcrossMetrics(queries);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
