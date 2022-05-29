package org.pooling;

import org.apache.commons.math3.stat.correlation.KendallsCorrelation;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.*;
import org.evaluator.Metric;
import org.evaluator.RetrievedResults;
import org.experiments.NQCCalibrationWorkflow;
import org.experiments.Settings;
import org.qpp.NQCSpecificity;
import org.qpp.QPPMethod;
import org.trec.FieldConstants;
import org.trec.TRECQuery;
import org.trec.TRECQueryParser;
import org.evaluator.Evaluator;

import java.util.*;
import java.util.stream.Collectors;

public class DepthPoolingWorkflow extends NQCCalibrationWorkflow  {
    int minDepth;
    int maxDepth;
    int depthRange;

    static Similarity[] Sims = {
            new BM25Similarity(.5f, .25f),
            //new BM25Similarity(.5f, .5f),
            //new BM25Similarity(1.0f, .5f),
            //new LMDirichletSimilarity(100),
            new LMDirichletSimilarity(1000),
            new LMJelinekMercerSimilarity(0.6f),
    };
    static String[] QueryFields = { "t", "td", "tdn"};

    DepthPoolingWorkflow(int minDepth, int maxDepth) throws Exception {
        super();
        qppMethod = new NQCSpecificity(Settings.getSearcher());
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
        depthRange = maxDepth-minDepth;
    }

    public double[] computeCorrelations(List<TRECQuery> queries, QPPMethod qppMethod) {
        final int qppTopK = Settings.getQppTopK();
        int numQueries = queries.size();
        double[] qppEstimates = new double[numQueries]; // stores qpp estimates for the list of input queries
        int i = 0;

        for (TRECQuery query : queries) {
            RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.id);
            TopDocs topDocs = topDocsMap.get(query.id);
            qppEstimates[i] = (float)qppMethod.computeSpecificity(
                    query.getLuceneQueryObj(), rr, topDocs, qppTopK);
            i++;
        }

        return qppEstimates;
    }

    float evaluateRun(Evaluator evaluator, List<TRECQuery> queries, IRSystem system) throws Exception {
        float evaluated_metric = 0;
        for (TRECQuery query: queries) {
            evaluated_metric += evaluator.compute(query.id, system, Metric.AP);
        }
        return evaluated_metric/(float)queries.size();
    }

    List<IRSystem> evaluateRuns() throws Exception {
        List<IRSystem> pool = new ArrayList<>();

        for (String field: QueryFields) {
            for (Similarity sim : Sims) { // <(t/td/tdn), sim> defines a run
                String name = field + ":" + sim.toString();
                for (TRECQuery query : this.queries) {
                    Query luceneQuery = TRECQueryParser.constructLuceneQueryObj(query,
                            field, FieldConstants.FIELD_ANALYZED_CONTENT);
                    query.setLuceneQueryObj(luceneQuery);
                    //System.out.println(luceneQuery);
                }
                topDocsMap.clear();
                evaluator = qppEvaluator.executeQueries(queries, sim,
                        Settings.getNumWanted(), Settings.getQrelsFile(),
                        Settings.RES_FILE + "_" + name + ".txt",
                        topDocsMap);

                float map = 0;  // map of this run
                for (TRECQuery query : this.queries) {
                    map += evaluator.compute(query.id, Metric.AP); // AP of this query
                }

                IRSystem irSystem = new IRSystem(name, topDocsMap);
                irSystem.map = map/(float)this.queries.size();
                pool.add(irSystem);
            }
        }
        System.out.println(pool.size());
        return pool;
    }

    List<IRSystem> orderByMAP(List<IRSystem> systems) {
        return systems.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }

    public Map<String, Integer> computeDepths() {
        double[] qppEstimates = computeCorrelations(this.queries, this.qppMethod);
        qppEstimates = Arrays.stream(qppEstimates).map(x->Math.log(1+x)).toArray();
        double maxQPPEstimate = Arrays.stream(qppEstimates).max().getAsDouble();
        qppEstimates = Arrays.stream(qppEstimates).map(x->x/maxQPPEstimate).toArray();

        // Calculate depths
        Map<String, Integer> depths = new HashMap<>();
        int i = 0;
        // depth of the pool a function of QPP scores
        for (TRECQuery query: queries) {
            int depth = minDepth + (int)(qppEstimates[i]*depthRange);
            //System.out.println(String.format("%s: QPP-score = %.4f, depth = %d", query.id, qppEstimates[i], depth));
            depths.put(query.id, depth);
            i++;
        }
        return depths;
    }

    public static void main(String[] args) {
        Settings.init("init.properties");

        try {
            DepthPoolingWorkflow depthPoolingWorkflow = new DepthPoolingWorkflow(20, 200);
            List<IRSystem> systems = depthPoolingWorkflow.evaluateRuns(); // initial eval with max depth

            System.out.println("Rank with maxdepth");
            List<IRSystem> maxDepthEval = depthPoolingWorkflow.orderByMAP(systems);
            maxDepthEval.stream().forEach(System.out::println); // initial ordering

            Map<String, Integer> depths = depthPoolingWorkflow.computeDepths(); // QPP-based depth computation
            for (IRSystem system: systems) {
                system.depths = depths; // set the depths to be used later
            }

            // evaluator flow that's aware of the depths (present inside the system objects)
            Evaluator evaluator = new Evaluator(Settings.getQrelsFile(), systems);
            for (IRSystem system: systems) {
                // evaluate each system with this depth-aware evaluator
                system.map = depthPoolingWorkflow.evaluateRun(evaluator, depthPoolingWorkflow.queries, system);
            }
            // new ordering
            System.out.println("Rank with variable depth");
            List<IRSystem> varDepthEval = depthPoolingWorkflow.orderByMAP(systems);
            varDepthEval.stream().forEach(System.out::println);

            double corr = (new KendallsCorrelation())
                .correlation(
                    maxDepthEval.stream().mapToDouble(x->x.map).toArray(),
                    varDepthEval.stream().mapToDouble(x->x.map).toArray()
            );
            System.out.println(String.format("Kendall's correlation = %.4f", corr));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
