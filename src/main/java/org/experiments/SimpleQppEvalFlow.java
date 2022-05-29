package org.experiments;

import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.correlation.KendalCorrelation;
import org.evaluator.Evaluator;
import org.evaluator.Metric;
import org.evaluator.RetrievedResults;
import org.qpp.NQCCalibratedSpecificity;
import org.qpp.NQCSpecificity;
import org.qpp.QPPMethod;
import org.trec.TRECQuery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleQppEvalFlow {

    public static void main(String[] args) {
        final String queryFile = "data/topics.robust.all";
        final String resFile = "data/lmdir.all";
        final String qrelsFile = "data/qrels.robust.all";

        try {
            SettingsLoader loader = new SettingsLoader("init.properties");

            QPPEvaluator qppEvaluator = new QPPEvaluator(
                    loader.getProp(),
                    loader.getCorrelationMetric(), loader.getSearcher(), loader.getNumWanted());
            List<TRECQuery> queries = qppEvaluator.constructQueries(queryFile);

            NQCCalibratedSpecificity qppMethod = new NQCCalibratedSpecificity(loader.getSearcher());
            qppMethod.setParameters(2, 2, 0.5f);
            //NQCSpecificity qppMethod = new NQCSpecificity(loader.getSearcher());
            Similarity sim = new LMDirichletSimilarity(1000);

            final int nwanted = loader.getNumWanted();
            final int qppTopK = loader.getQppTopK();

            Map<String, TopDocs> topDocsMap = new HashMap<>();
            Evaluator evaluator = qppEvaluator.executeQueries(queries, sim, nwanted, qrelsFile, resFile, topDocsMap);
            System.out.println(topDocsMap.keySet());

            int numQueries = queries.size();
            double[] qppEstimates = new double[numQueries];
            double[] evaluatedMetricValues = new double[numQueries];

            int i = 0;
            for (TRECQuery query : queries) {
                RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.id);
                TopDocs topDocs = topDocsMap.get(query.id);

                evaluatedMetricValues[i] = evaluator.compute(query.id, Metric.AP);
                qppEstimates[i] = (float)qppMethod.computeSpecificity(
                        query.getLuceneQueryObj(), rr, topDocs, qppTopK);

                System.out.println(String.format("%s: AP = %.4f, QPP = %.4f", query.id, evaluatedMetricValues[i], qppEstimates[i]));
                i++;
            }
            double corr = new KendalCorrelation().correlation(evaluatedMetricValues, qppEstimates);
            System.out.println(String.format("Kendall's = %.4f", corr));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
