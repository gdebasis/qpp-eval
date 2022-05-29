package org.experiments;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.Similarity;
import org.evaluator.Evaluator;
import org.evaluator.Metric;
import org.trec.TRECQuery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrossIRModelComparator {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "qpp.properties";
        }

        Settings.init(args[0]);
        Similarity[] sims = QPPEvaluator.modelsToTest();

        final String queryFile = Settings.getQueryFile();
        final String qrelsFile = Settings.getQrelsFile();
        final int nwanted = Settings.getNumWanted();

        QPPEvaluator qppEvaluator = new QPPEvaluator(
                Settings.getProp(),
                Settings.getCorrelationMetric(), Settings.getSearcher(), nwanted);
        List<TRECQuery> queries = qppEvaluator.constructQueries(queryFile);

        Map<String, TopDocs> topDocsMap = new HashMap<>();
        Map<String, Evaluator> evaluatorMap = new HashMap<>();
        for (Similarity sim: sims) {
            String resFile = "outputs/" + sim.toString() + ".res";
            Evaluator evaluator = qppEvaluator.executeQueries(queries, sim, nwanted, qrelsFile, resFile, topDocsMap);
            evaluatorMap.put(sim.toString(), evaluator);
        }

        findCorr(sims, qppEvaluator, evaluatorMap, queries, topDocsMap);
    }

    static void findCorr(Similarity[] sims, QPPEvaluator qppEvaluator,
                                   Map<String, Evaluator> evaluatorMap,
                                   List<TRECQuery> queries,
                                   Map<String, TopDocs> topDocsMap) throws Exception {

        Map<String, double[]> metricValues = new HashMap<>();
        int i, j, m_i, m_j = 0;
        String key;
        Metric[] metrics = { Metric.AP, Metric.Recall, Metric.nDCG} ;

        for (m_i=0; m_i< metrics.length; m_i++) {
            for (Similarity sim : sims) {
                System.out.println("Evaluating with model " + sim.toString());
                Evaluator evaluator = evaluatorMap.get(sim.toString());

                double[] evaluatedMetricValues = new double[queries.size()];
                i = 0;
                for (i=0; i < queries.size(); i++) {
                    TRECQuery query = queries.get(i);
                    evaluatedMetricValues[i++] = evaluator.compute(query.id, metrics[m_i]);
                }
                key = sim.toString() + ":" + metrics[m_i].name();
                metricValues.put(key, evaluatedMetricValues);
            }
        }

        for (m_i=0; m_i< metrics.length-1; m_i++) {
            for (m_j = m_i + 1; m_j < metrics.length; m_j++) {

                for (i = 0; i < sims.length - 1; i++) {
                    key = sims[i].toString() + ":" + metrics[m_i].name();
                    double[] sim_a_eval = metricValues.get(key);

                    for (j = i + 1; j < sims.length; j++) {
                        key = sims[j].toString() + ":" + metrics[m_j].name();
                        double[] sim_b_eval = metricValues.get(key);

                        double r = new PearsonsCorrelation().correlation(sim_a_eval, sim_b_eval);
                        double rho = new SpearmansCorrelation().correlation(sim_a_eval, sim_b_eval);
                        System.out.println(String.format("[%s (%s)][%s (%s)]: r=%.4f, rho=%.4f",
                                sims[i].toString(), metrics[m_i].name(),
                                sims[j].toString(), metrics[m_j].name(),
                                r, rho));
                    }
                }
            }
        }
    }
}
