package org.qppeval.retriever;

import java.io.*;
import java.util.*;

import org.qppeval.evaluator.Evaluator;
import org.qppeval.evaluator.Metrics;
import org.qppeval.evaluator.RetrievedResults;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.correlation.KendallsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.util.Pair;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.*;
import org.qppeval.trec.TRECQuery;
import org.qppeval.trec.TRECQueryParser;

public class QPPEvaluator {

    IndexReader reader;
    IndexSearcher searcher;
    int numWanted;
    Properties prop;
    String runName;
    Similarity model;

    public QPPEvaluator(String propFile, Similarity sim) {

        try {
            prop = new Properties();
            prop.load(new FileReader(propFile));

            File indexDir = new File(prop.getProperty("index.dir"));
            System.out.println("Running queries against index: " + indexDir.getPath());

            reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
            searcher = new IndexSearcher(reader);

            this.model = sim;
            searcher.setSimilarity(sim);

            numWanted = Integer.parseInt(prop.getProperty("retrieve.num_wanted", "1000"));
            runName = prop.getProperty("retrieve.runname", "lm");
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

    TopDocs retrieve(TRECQuery query, Similarity sim) throws IOException {
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

    // k docs tp compute NQC
    double computeNQC(Query q, RetrievedResults topDocs, int k) {
        double[] rsvs = topDocs.getRSVs(k);
        double sd = new StandardDeviation().evaluate(rsvs);
        double avgIDF = 0;
        try {
            avgIDF = averageIDF(q);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return sd * avgIDF; // high variance, high avgIDF -- more specificity
    }

    double[] evaluate(List<TRECQuery> queries, Similarity sim, Metrics m) throws Exception {

        int numQueries = queries.size();
        double[] evaluatedMetricValues = new double[numQueries];

        final String resFile = "/tmp/res";
        FileWriter fw = new FileWriter(resFile);
        BufferedWriter bw = new BufferedWriter(fw);

        for (TRECQuery query : queries) {
            TopDocs topDocs = retrieve(query, sim);
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

    public Pair<Double, Double> compareModels(
            List<TRECQuery> queries, Similarity sima,
            Similarity simb, Metrics m) throws Exception {
        int numQueries = queries.size();
        double[] evaluatedMetricValues_sima = evaluate(queries, sima, m);
        double[] evaluatedMetricValues_simb = evaluate(queries, simb, m);

        double spearmans = new SpearmansCorrelation()
                .correlation(evaluatedMetricValues_sima, evaluatedMetricValues_simb);
        double kendals = new KendallsCorrelation()
                .correlation(evaluatedMetricValues_sima, evaluatedMetricValues_simb);

        return Pair.create(spearmans, kendals);
    }

    public Pair<Double, Double> evaluateQPPWithModel(List<TRECQuery> queries, Similarity sim, Metrics m) throws Exception {
        final String resFile = "/tmp/res";
        double[] qppEstimates = new double[queries.size()];
        int i = 0;

        double[] evaluatedMetricValues = evaluate(queries, sim, m);

        int qppTopK = Integer.parseInt(prop.getProperty("qpp.numtopdocs"));
        String qrelsFile = prop.getProperty("qrels.file");
        Evaluator evaluator = new Evaluator(qrelsFile, resFile); // load ret and rel

        for (TRECQuery query : queries) {
            RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.id);
            qppEstimates[i] = computeNQC(query.getLuceneQueryObj(), rr, qppTopK);
            i++;
        }

        System.out.println(String.format("Average %s: %.4f",
                m.toString(),
                StatUtils.mean(evaluatedMetricValues)));

        double spearmans = new SpearmansCorrelation().correlation(evaluatedMetricValues, qppEstimates);
        double kendals = new KendallsCorrelation().correlation(evaluatedMetricValues, qppEstimates);

        return Pair.create(spearmans, kendals);
    }

    public void evaluateQPPAll() throws Exception {
        List<TRECQuery> queries = constructQueries();
        Metrics[] metricsForEval = Metrics.values();
        int i, j;
        Similarity[] sims = modelsToTest();

        // Measure the inter- rank correlation values across retrieval models
        // e.g. AP(LM-Dir) and AP(BM25)
        for (Metrics m: metricsForEval) {
            for (i=0; i < sims.length-1; i++) {
                for (j=i+1; j < sims.length; j++) {
                    Pair<Double, Double> rankcorrs = compareModels(queries, sims[i], sims[j], m);
                    System.out.printf("(%s, %s, %s): rho = %.4f tau = %.4f%n",
                            sims[i].toString(), sims[j].toString(),
                            m.toString(), rankcorrs.getFirst(), rankcorrs.getSecond());
                }
            }
        }

        // Measure the QPP effectiveness with different induced ground-truths
        // (depending on retrieval model and the metric used, e.g. AP or P@5)
        for (Metrics m: metricsForEval) {
            for (Similarity sim: sims) {
                Pair<Double, Double> rankcorrs = evaluateQPPWithModel(queries, sim, m);
                System.out.printf("Model: %s, Metric %s: rho = %.4f tau = %.4f%n",
                        sim.toString(), m.toString(), rankcorrs.getFirst(), rankcorrs.getSecond());
            }
        }
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
            QPPEvaluator searcher = new QPPEvaluator(args[0], new LMJelinekMercerSimilarity(0.4f));
            searcher.evaluateQPPAll();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
