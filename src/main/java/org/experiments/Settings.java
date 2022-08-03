package org.experiments;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.correlation.*;
import org.evaluator.Metric;
import org.qpp.*;
import org.trec.FieldConstants;


public class Settings {
    public static final int SEED = 314159; // first six digits of pi - a beautiful seed!
    static Map<String, QPPCorrelationMetric> corrMetrics;
    static Map<String, QPPMethod>            qppMethods;
    static Map<String, Metric>               retEvalMetrics;

    static int                               qppTopK;
    static Properties                        prop;
    static IndexReader                       reader;
    static IndexSearcher                     searcher;
    static int                               numWanted;
    static boolean initialized = false;
    static Map<String, Similarity> retModelMap = new HashMap<>(3);
    public static String RES_FILE = "/tmp/res";
    public static int EVAL_POOL_DEPTH = 100;

    public static String qppScoresFile;
    public static int minDepth;
    public static int maxDepth;
    public static boolean randomDepths;
    public static boolean tsvMode;
    public static HashMap<String, Integer> docId2OffsetMap = new HashMap<>();
    public static HashMap<Integer, String> offset2DocIdMap = new HashMap<>();

    static public String getQueryFile() {
        return prop.getProperty("query.file");
    }

    static public String getQrelsFile() {
        return prop.getProperty("qrels.file");
    }

    static public String getQppScoresFile() {
        return prop.getProperty("qpp.scores.file", "");
    }

    static public boolean applyLogTransform() {
        return Boolean.parseBoolean(prop.getProperty("qpp.logtramsform", "true"));
    }

    static void constructDocId2OffsetMap() {
        try {
            // we just need the mapping for the rel docs... just get all docs that are relevant for some query
            // we don't even need to keep track of the queries
            List<String> relRcdLines = FileUtils.readLines(new File(getQrelsFile()), Charset.defaultCharset());
            for (String relRcdLine: relRcdLines) {
                String[] tokens = relRcdLine.split("\\s+");
                String docId = tokens[2];
                int rel = Integer.parseInt(tokens[3]);
                if (rel>0) {
                    int offset = getDocOffsetFromId(docId);
                    offset2DocIdMap.put(offset, docId);
                    docId2OffsetMap.put(docId, offset);
                    //System.out.print(String.format("Added (%d, %s) pair...\r", offset, docId));
                }
            }
            System.out.println();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    static public void init(String propFile) {
        init(propFile, false);
    }

    static public void init(String propFile, boolean initMap) {
        if (initialized)
            return;

        try {
            prop = new Properties();
            prop.load(new FileReader(propFile));

            File indexDir = new File(prop.getProperty("index.dir"));
            System.out.println("Index: " + indexDir.getPath());

            reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
            searcher = new IndexSearcher(reader);
            numWanted = Integer.parseInt(prop.getProperty("retrieve.num_wanted", "100"));

            if (initMap) {
                System.out.println("Loading the map of Doc-Id strings and Lucene integer offsets in memory...");
                constructDocId2OffsetMap();
                System.out.println("Loaded the map of Doc-Id strings and Lucene integer offsets in memory...");
            }

            minDepth = Integer.parseInt(prop.getProperty("pool.mindepth", "20"));
            maxDepth = Integer.parseInt(prop.getProperty("pool.maxdepth", "50"));
            randomDepths = Boolean.parseBoolean(prop.getProperty("random_depth", "false"));
            tsvMode = prop.getProperty("query.readmode", "xml").equals("tsv");

            corrMetrics = new HashMap<>();
            corrMetrics.put("r", new PearsonCorrelation());
            corrMetrics.put("rho", new SpearmanCorrelation());
            corrMetrics.put("tau", new KendalCorrelation());
            corrMetrics.put("qsim", new QuantizedSimCorrelation(Integer.parseInt(prop.getProperty("qsim.numintervals", "5"))));
            corrMetrics.put("qsim_strict", new QuantizedStrictMatchCorrelation(Integer.parseInt(prop.getProperty("qsim.numintervals", "5"))));
            corrMetrics.put("pairacc", new PairwiseAccuracyMetric());
            corrMetrics.put("classacc", new QuantizedClassAccuracy(Integer.parseInt(prop.getProperty("qsim.numintervals", "5"))));
            corrMetrics.put("rmse", new RmseCorrelation());

            retEvalMetrics = new HashMap<>();
            retEvalMetrics.put("ap", Metric.AP);
            retEvalMetrics.put("p_10", Metric.P_10);
            retEvalMetrics.put("recall", Metric.Recall);
            retEvalMetrics.put("ndcg", Metric.nDCG);

            qppMethods = new HashMap<>();
            qppMethods.put("avgidf", new AvgIDFSpecificity(searcher));
            qppMethods.put("nqc", new NQCSpecificity(searcher));
            qppMethods.put("nqc_sc", new NQCSpecificityCalibrated(searcher, 2, 2, 0.5f));
            qppMethods.put("wig", new WIGSpecificity(searcher));
            qppMethods.put("clarity", new ClaritySpecificity(searcher));
            qppMethods.put("uef_nqc", new UEFSpecificity(new NQCSpecificity(searcher)));
            qppMethods.put("uef_wig", new UEFSpecificity(new WIGSpecificity(searcher)));
            qppMethods.put("uef_clarity", new UEFSpecificity(new ClaritySpecificity(searcher)));

            qppTopK = Integer.parseInt(prop.getProperty("qpp.numtopdocs"));

            retModelMap.put("lmjm", new LMJelinekMercerSimilarity(0.6f));
            retModelMap.put("lmdir", new LMDirichletSimilarity(1000));
            retModelMap.put("bm25", new BM25Similarity(0.7f, 0.3f));

            initialized = true;
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static String getContentFieldName() {
        return prop.getProperty("content.field", FieldConstants.FIELD_ANALYZED_CONTENT);
    }

    public static String getIdFieldName() {
        return prop.getProperty("id.field", FieldConstants.FIELD_ID);
    }


    static public Similarity getRetModel() {
        return retModelMap.get(prop.getProperty("ret.model"));
    }

    public static Properties getProp() { return prop; }

    public static int getNumWanted() { return numWanted; }
    public static int getQppTopK() { return qppTopK; }

    public static QPPCorrelationMetric getCorrelationMetric() {
        String key = prop.getProperty("qpp.metric");
        return corrMetrics.get(key);
    }

    public static IndexSearcher getSearcher() { return searcher; }

    public static QPPMethod getQPPMethod() {
        String key = prop.getProperty("qpp.method");
        return qppMethods.get(key);
    }
    
    static public int getTrainPercentage() {
        int splits = Integer.parseInt(prop.getProperty("qpp.splits"));
        return splits;
    }

    public static Metric getRetEvalMetric() {
        return retEvalMetrics.get(prop.getProperty("reteval.metric"));
    }

    public static String getDocIdFromOffset(int docOffset) {
        try {
            return reader.document(docOffset).get(FieldConstants.FIELD_ID);
        }
        catch (Exception ex) { ex.printStackTrace(); }
        return null;
    }

    public static String getDocIdFromOffset_Mem(int docOffset) {
        if (offset2DocIdMap.size()==0)
            return getDocIdFromOffset(docOffset);

        return offset2DocIdMap.get(docOffset);
    }

    public static int getDocOffsetFromId(String docId) {
        try {
            Query query = new TermQuery(new Term(FieldConstants.FIELD_ID, docId));
            TopDocs topDocs = searcher.search(query, 1);
            return topDocs.scoreDocs[0].doc;
        }
        catch (Exception ex) { ex.printStackTrace(); }
        return -1;
    }

    public static int getDocOffsetFromId_Mem(String docId) {
        if (docId2OffsetMap.size()==0)
            return getDocOffsetFromId(docId);

        Integer offset = docId2OffsetMap.get(docId);
        return offset==null? -1 : offset;
    }

    public static String analyze(Analyzer analyzer, String query) {
        StringBuffer buff = new StringBuffer();
        try {
            TokenStream stream = analyzer.tokenStream("dummy", new StringReader(query));
            CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                String term = termAtt.toString();
                buff.append(term).append(" ");
            }
            stream.end();
            stream.close();

            if (buff.length()>0)
                buff.deleteCharAt(buff.length()-1);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

        return buff.toString();
    }
}
