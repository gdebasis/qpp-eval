package org.pooling;

import org.apache.commons.math3.stat.correlation.KendallsCorrelation;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.*;
import org.evaluator.AllRetrievedResults;
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

    public double[] computeCorrelations(List<TRECQuery> queries, IRSystem system, QPPMethod qppMethod) {
        final int qppTopK = Settings.getQppTopK();
        int numQueries = queries.size();
        double[] qppEstimates = new double[numQueries]; // stores qpp estimates for the list of input queries
        int i = 0;

        for (TRECQuery query : queries) {
            RetrievedResults rr = new RetrievedResults(query.id, system.getTopDocs(query.id));
            qppEstimates[i] = (float)qppMethod.computeSpecificity(
                    query.getLuceneQueryObj(), rr, null, qppTopK);
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
        System.out.println("Number of systems: " + pool.size());
        return pool;
    }

    List<IRSystem> evaluateRuns(int maxDepth) throws Exception {
        List<IRSystem> pool = new ArrayList<>();
        List<String> queryIds = queries.stream().map(x->x.id).collect(Collectors.toList());

        for (String field: QueryFields) {
            for (Similarity sim : Sims) { // <(t/td/tdn), sim> defines a run

                for (TRECQuery query : this.queries) {
                    Query luceneQuery = TRECQueryParser.constructLuceneQueryObj(query,
                            field, FieldConstants.FIELD_ANALYZED_CONTENT);
                    query.setLuceneQueryObj(luceneQuery);
                }

                topDocsMap.clear(); // reuse the class variable... avoids creating new objects
                IRSystem irSystem = new IRSystem(field, sim.toString(), queryIds, maxDepth);

                // batch execute queries
                qppEvaluator.executeQueries(queries, sim,
                        Settings.getNumWanted(), Settings.getQrelsFile(),
                        Settings.RES_FILE + "_" + irSystem.name + ".txt", topDocsMap);
                irSystem.setTopDocs(topDocsMap);

                pool.add(irSystem);
            }
        }

        evaluator = new Evaluator(Settings.getQrelsFile(), pool);
        for (IRSystem system: pool) {
            float map = 0;  // map of this run
            for (TRECQuery query : this.queries) {
                map += evaluator.compute(query.id, system, Metric.AP); // AP of this query
            }
            system.map = map/(float)this.queries.size();
        }
        System.out.println("Number of systems: " + pool.size());
        return pool;
    }

    public void computeDepths(IRSystem system) {
        double[] qppEstimates = computeCorrelations(this.queries, system, this.qppMethod);
        qppEstimates = Arrays.stream(qppEstimates).map(x->Math.log(1+x)).toArray();
        double maxQPPEstimate = Arrays.stream(qppEstimates).max().getAsDouble();
        qppEstimates = Arrays.stream(qppEstimates).map(x->x/maxQPPEstimate).toArray();

        // Calculate depths
        system.depths = new HashMap<>();
        int i = 0;
        // depth of the pool a function of QPP scores
        for (TRECQuery query: queries) {
            int depth = minDepth + (int)(qppEstimates[i]*depthRange);
            //System.out.println(String.format("%s: QPP-score = %.4f, depth = %d", query.id, qppEstimates[i], depth));
            system.depths.put(query.id, depth);
            i++;
        }
    }

    public double systemRankCorrelation(
            List<IRSystem> refSystems /* ref order with maxdepth*/,
            List<IRSystem> thisSystems) throws Exception {
        /*
        // evaluator flow that's aware of the depths (present inside the system objects)
        Evaluator evaluator = new Evaluator(Settings.getQrelsFile(), thisSystems);
        for (IRSystem system: thisSystems) {
            // evaluate each system with this depth-aware evaluator
            system.map = evaluateRun(evaluator, queries, system);
        }
        */
        double corr = (new KendallsCorrelation()) // no sorting!
        .correlation(
            refSystems.stream().mapToDouble(x->x.map).toArray(),
            thisSystems.stream().mapToDouble(x->x.map).toArray()
        );
        return corr;
    }

    List<IRSystem> evaluateRunAndPrintStats(final List<IRSystem> systems, int constantDepth) throws Exception {
        List<IRSystem> reevaluated_systems = new ArrayList<>();
        for (IRSystem system: systems)
            reevaluated_systems.add(new IRSystem(system));

        if (constantDepth==0) {
            for (IRSystem system : reevaluated_systems) {
                computeDepths(system); // QPP-based depth computation and set the depths to be used later
            }
        }
        else {
            List<String> qids = queries.stream().map(x->x.id).collect(Collectors.toList());
            for (IRSystem system: reevaluated_systems) {
                system.depths.clear();
                for (String qid: qids)
                    system.depths.put(qid, constantDepth);
            }
        }

        Evaluator evaluator = new Evaluator(Settings.getQrelsFile(), reevaluated_systems);
        for (IRSystem system: reevaluated_systems) {
            // evaluate each system with this depth-aware evaluator
            system.map = evaluateRun(evaluator, queries, system);
        }

        List<Integer> depths = reevaluated_systems.stream()
                .flatMap(
                    x->x.depths.values()
                            .stream()
                            .map(y->y.intValue())
                )
                .sorted()
                .collect(Collectors.toList()
        );
        System.out.println(String.format("Avg. depth: %.4f",
                depths.stream().mapToInt(x->x.intValue()).sum()/(double)depths.size()));
        System.out.println(String.format("Median depth: %d",
                depths.stream().collect(Collectors.toList()).get(depths.size()/2)));

        return reevaluated_systems;
    }

    public static void main(String[] args) {
        if (args.length==0) {
            args = new String[1];
            args[0] = "deptheval.properties";
        }
        Settings.init(args[0]);

        try {
            DepthPoolingWorkflow depthPoolingWorkflow =
                    new DepthPoolingWorkflow(Settings.minDepth, Settings.maxDepth);

            List<IRSystem> systems_maxDepth = depthPoolingWorkflow.evaluateRuns(Settings.maxDepth); // initial eval with max depth
            System.out.println("System MAPs with depth = " + Settings.maxDepth);
            System.out.println(systems_maxDepth.stream().collect(Collectors.toMap(x->x.name, x->x.map)));

            List<IRSystem> systems_varDepth = depthPoolingWorkflow.evaluateRunAndPrintStats(systems_maxDepth, 0);
            System.out.println("System MAPs with variable depth");
            System.out.println(systems_varDepth.stream().collect(Collectors.toMap(x->x.name, x->x.map)));

            System.out.println(String.format("Kendall's = %.4f",
                (new KendallsCorrelation()) // no sorting!
                    .correlation(
                        systems_maxDepth.stream().mapToDouble(x->x.map).toArray(),
                        systems_varDepth.stream().mapToDouble(x->x.map).toArray()
                    )
                )
            );

            List<IRSystem> systems_minDepth = depthPoolingWorkflow.evaluateRunAndPrintStats(systems_maxDepth, Settings.minDepth);
            System.out.println(String.format("Kendall's = %.4f",
                (new KendallsCorrelation()) // no sorting!
                    .correlation(
                            systems_maxDepth.stream().mapToDouble(x->x.map).toArray(),
                            systems_minDepth.stream().mapToDouble(x->x.map).toArray()
                    )
                )
            );

            System.out.println("System MAPs with depth = " + Settings.minDepth);
            System.out.println(systems_minDepth.stream().collect(Collectors.toMap(x->x.name, x->x.map)));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
