package org.experiments;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.evaluator.Evaluator;
import org.evaluator.RetrievedResults;
import org.qpp.*;
import org.trec.TRECQuery;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QPPScoresFileWriter {
    static public QPPMethod[] qppMethods(IndexSearcher searcher) {
        QPPMethod[] qppMethods = {
                new WIGSpecificity(searcher),
                new UEFSpecificity(new WIGSpecificity(searcher)),
        };
        return qppMethods;
    }
    
    public static void main(String[] args) {

        if (args.length < 1) {
            args = new String[1];
            args[0] = "qpp.properties";
        }
        Settings.init(args[0]);

        final String queryFile = Settings.getQueryFile();
        final String resFile = Settings.RES_FILE;
        final String qrelsFile = Settings.getQrelsFile();

        try {

            QPPEvaluator qppEvaluator = new QPPEvaluator(Settings.getProp(),
                    Settings.getCorrelationMetric(), Settings.getSearcher(), Settings.getNumWanted());
            List<TRECQuery> queries = qppEvaluator.constructQueries(queryFile);

            QPPMethod[] qppMethods = qppEvaluator.qppMethods();
            
            Similarity sim = new LMDirichletSimilarity(1000);

            final int nwanted = Settings.getNumWanted();
            final int qppTopK = Settings.getQppTopK();

            Map<String, TopDocs> topDocsMap = new HashMap<>();
           
            Evaluator evaluator = qppEvaluator.executeQueries(queries, sim, nwanted, qrelsFile, resFile, topDocsMap);

            FileWriter fw = new FileWriter(Settings.RES_FILE);
            BufferedWriter bw = new BufferedWriter(fw);
            StringBuilder buff = new StringBuilder();
            buff.append("QID\t");
            for (QPPMethod qppMethod: qppMethods) {
                buff.append(qppMethod.name()).append("\t");
            }
            buff.deleteCharAt(buff.length()-1);
            bw.write(buff.toString());
            bw.newLine();

            for (TRECQuery query : queries) {
                buff.setLength(0);
                buff.append(query.id).append("\t");

                for (QPPMethod qppMethod: qppMethods) {
                    System.out.println(String.format("computing %s scores for qid %s", qppMethod.name(), query.id));
                    RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.id);
                    TopDocs topDocs = topDocsMap.get(query.id);
                    if (topDocs==null) {
                        System.err.println(String.format("No Topdocs found for query %s", query.id.trim()));
                        continue;
                    }
                    float qppEstimate = (float)qppMethod.computeSpecificity(query.getLuceneQueryObj(), rr, topDocs, qppTopK);
                    buff.append(qppEstimate).append("\t");
                }
                buff.deleteCharAt(buff.length()-1);
                bw.write(buff.toString());
                bw.newLine();
            }
            
            bw.close();
            fw.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
