package retriever;

import java.io.*;
import java.util.*;

import evaluator.Evaluator;
import evaluator.Metrics;
import evaluator.RetrievedResults;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.*;
import trec.TRECQuery;
import trec.TRECQueryParser;

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

    public List<trec.TRECQuery> constructQueries() throws Exception {
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
        q.extractTerms(qterms);

        float aggregated_idf = 0;
        for (Term t: qterms) {
            int n = reader.docFreq(t);
            double idf = Math.log(N/(double)n);
            aggregated_idf += idf;
        }
        return aggregated_idf/(double)qterms.size();
    }

    double computeNQC(Query q, RetrievedResults topDocs) {
        double[] rsvs = topDocs.getRSVs();
        double sd = new StandardDeviation().evaluate(rsvs);
        double avgIDF = 0;
        try {
            avgIDF = averageIDF(q);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return sd * avgIDF; // high variance, high avgIDF -- more specificity
    }

    public double evaluateQPPWithModel(List<TRECQuery> queries, Similarity sim, Metrics m) throws Exception {
        int numQueries = queries.size();
        double[] qppEstimates = new double[numQueries];
        double[] evaluatedMetricValues = new double[numQueries];
        String resFile = prop.getProperty("res.file") + "_" + sim.toString();

        FileWriter fw = new FileWriter(resFile);
        BufferedWriter bw = new BufferedWriter(fw);

        for (TRECQuery query : queries) {
            TopDocs topDocs = retrieve(query, sim);
            saveRetrievedTuples(bw, query, topDocs, sim.toString());
        }
        bw.flush();
        bw.close();
        fw.close();

        int i = 0;
        String qrelsFile = prop.getProperty("qrels.file");
        Evaluator evaluator = new Evaluator(qrelsFile, resFile); // load ret and rel

        for (TRECQuery query : queries) {
            RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.id);
            qppEstimates[i] = computeNQC(query.getLuceneQueryObj(), rr);
            evaluatedMetricValues[i] = evaluator.compute(query.id, m);
            i++;
        }

        System.out.println(String.format("Average %s: %.4f",
                m.toString(),
                StatUtils.mean(evaluatedMetricValues)));

        return new SpearmansCorrelation().correlation(evaluatedMetricValues, qppEstimates);
    }

    public void evaluateQPPAll() throws Exception {
        List<TRECQuery> queries = constructQueries();
        Metrics[] metricsForEval = Metrics.values();

        for (Similarity sim: modelsToTest()) {
            for (Metrics m: metricsForEval) {
                double spearmans = evaluateQPPWithModel(queries, sim, m);
                System.out.printf("Model: %s, Metric %s: rho = %.4f%n",
                        sim.toString(), m.toString(), spearmans);
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
