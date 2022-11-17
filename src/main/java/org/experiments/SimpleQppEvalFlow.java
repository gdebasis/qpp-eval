package org.experiments;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.correlation.KendalCorrelation;
import org.evaluator.Evaluator;
import org.evaluator.Metric;
import org.evaluator.RetrievedResults;
import org.qpp.*;
import org.trec.TRECQuery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleQppEvalFlow {

    public static void main(String[] args) {
        final String queryFile = "data/topics.351-400.xml";
        final String resFile = "data/lmdir.all";
        final String qrelsFile = "data/qrels.trec7.adhoc";

        try {
            //SettingsLoader loader = new SettingsLoader("init.properties");
            Settings.init("init.properties");

            QPPEvaluator qppEvaluator = new QPPEvaluator(
                    Settings.getProp(),
                    Settings.getCorrelationMetric(), Settings.getSearcher(), Settings.getNumWanted());
            List<TRECQuery> queries = qppEvaluator.constructQueries(queryFile);

            //NQCCalibratedSpecificity qppMethod = new NQCCalibratedSpecificity(loader.getSearcher());
            //qppMethod.setParameters(2, 2, 0.5f);
            //NQCSpecificity qppMethod = new NQCSpecificity(Settings.getSearcher());
            //WIGSpecificity qppMethod = new WIGSpecificity(Settings.getSearcher());
            UEFSpecificity qppMethod = new UEFSpecificity(new WIGSpecificity(Settings.getSearcher()));

            Similarity sim = new LMDirichletSimilarity(1000);

            final int nwanted = Settings.getNumWanted();
            final int qppTopK = Settings.getQppTopK();

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
            double pearsons = new PearsonsCorrelation().correlation(evaluatedMetricValues, qppEstimates);
            double kendals = new KendalCorrelation().correlation(evaluatedMetricValues, qppEstimates);

            boolean ref, pred;
            int correct = 0;
            int numpairs = 0;
            for (int j=0; j < evaluatedMetricValues.length-1; j++) {
                for (int k=j+1; k < evaluatedMetricValues.length; k++) {
                    ref = evaluatedMetricValues[j] < evaluatedMetricValues[k];
                    pred = qppEstimates[j] < qppEstimates[k];
                    correct += ref && pred? 1: 0;
                    numpairs++;
                }
            }
            System.out.println(numpairs);
            System.out.println(String.format("acc = %.4f, r = %.4f, tau = %.4f",
                    correct/(float)(numpairs),
                    pearsons, kendals));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
