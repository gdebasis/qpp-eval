package org.pooling;

import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.stat.correlation.KendallsCorrelation;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.similarities.*;
import org.correlation.PearsonCorrelation;
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class DepthPoolingWorkflow extends NQCCalibrationWorkflow  {
    int depthRange;
    Evaluator refEvaluator;
    Evaluator depthBasedEvaluator;

    static Similarity[] Sims = {
            new BM25Similarity(.5f, .25f),
            new LMDirichletSimilarity(1000),
    };
    static String[] QueryFields = { "t", "td", "tdn"};

    DepthPoolingWorkflow(int minDepth, int maxDepth) throws Exception {
        super();
        qppMethod = new NQCSpecificity(Settings.getSearcher());
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

    List<IRSystem> evaluateRuns(int maxDepth) throws Exception {
        List<IRSystem> pool = new ArrayList<>();
        List<String> queryIds = queries.stream().map(x->x.id).collect(Collectors.toList());

        for (String field: QueryFields) {
            if (!Settings.tsvMode) {
                for (TRECQuery query : this.queries) {
                    Query luceneQuery = TRECQueryParser.constructLuceneQueryObj(query,
                            field, FieldConstants.FIELD_ANALYZED_CONTENT);
                    query.setLuceneQueryObj(luceneQuery);
                }
            }

            for (Similarity sim : Sims) { // <(t/td/tdn), sim> defines a run

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

        refEvaluator = new Evaluator(Settings.getQrelsFile(), pool);
        for (IRSystem system: pool) {
            float map = 0;  // map of this run
            for (TRECQuery query : this.queries) {
                map += refEvaluator.compute(query.id, system, Metric.AP); // AP of this query
            }
            system.map = map/(float)this.queries.size();
        }
        System.out.println("Number of systems: " + pool.size());
        return pool;
    }

    List<IRSystem> evaluateRuns(String resFileDir, int maxDepth) throws Exception {
        List<IRSystem> systems = new ArrayList<>();
        List<String> queryIds = queries.stream().map(x->x.id).collect(Collectors.toList());
        File[] resFiles = new File(resFileDir).listFiles();

        for (int i=0; i < resFiles.length; i++) {
            IRSystem irSystem = new IRSystem(resFiles[i], queryIds, maxDepth);
            systems.add(irSystem);
        }

        refEvaluator = new Evaluator(Settings.getQrelsFile(), systems);
        for (IRSystem system: systems) {
            float map = 0;  // map of this run
            for (TRECQuery query : this.queries) {
                map += refEvaluator.compute(query.id, system, Metric.AP); // AP of this query
            }
            system.map = map/(float)this.queries.size();
        }
        System.out.println("Number of systems: " + systems.size());
        return systems;
    }

    // Calculate depths
    public void computeDepths(IRSystem system) throws Exception {
        if (Settings.randomDepths) { // random depths
            system.depths =
                queries
                .stream()
                .map(x->x.id)
                .collect(
                    Collectors.toMap(
                        x->x, x->Settings.minDepth + (int)(Math.random()*depthRange)
                    )
                );
            return;
        }

        double[] qppEstimates;
        if (Settings.getQppScoresFile().length()>0) {
            // there's a QPP scores file we should read from... so, what're u waiting for?
            // the order in which the QPP scores are stored MUST be identical to the order
            // in which the queries appear in the queries file!
            List<String> qppScores = FileUtils.readLines(
                    new File(Settings.getQppScoresFile()), StandardCharsets.UTF_8);
            qppEstimates = qppScores.stream()
                            .map(x->Double.parseDouble(x))
                            .mapToDouble(x->x.doubleValue())
                            .toArray();
        }
        else {
            qppEstimates = computeCorrelations(this.queries, system, this.qppMethod);
        }

        if (Settings.applyLogTransform())
            qppEstimates = Arrays.stream(qppEstimates).map(x->Math.log(1+x)).toArray();

        double min = Arrays.stream(qppEstimates).min().getAsDouble();
        double max = Arrays.stream(qppEstimates).max().getAsDouble();
        qppEstimates = Arrays.stream(qppEstimates).map(x -> 1 - ((x-min)/(max-min))).toArray();

        int i = 0;
        system.depths = new HashMap<>();
        boolean proportional = Boolean.parseBoolean(Settings.getProp().getProperty("qpp.direct", "false"));
        // depth of the pool a function of QPP scores
        for (TRECQuery query: queries) {
            int depth = proportional?
                    Settings.minDepth + (int)((qppEstimates[i])*depthRange):
                    Settings.minDepth + (int)((1-qppEstimates[i])*depthRange);
            system.depths.put(query.id, depth);
            i++;
        }
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

        depthBasedEvaluator = new Evaluator(Settings.getQrelsFile(), reevaluated_systems);
        for (IRSystem system: reevaluated_systems) {
            // evaluate each system with this depth-aware evaluator
            system.map = evaluateRun(depthBasedEvaluator, queries, system);
        }

        List<Integer> depths = reevaluated_systems.get(0).depths.values().stream().collect(Collectors.toList());
        System.out.println("Depths per query: " + depths);
        System.out.println(String.format("Avg. depth: %.4f",
                depths.stream().mapToInt(x->x.intValue()).sum()/(double)depths.size()));
        System.out.println(String.format("Median depth: %d",
                depths.stream().sorted().collect(Collectors.toList()).get(depths.size()/2)));

        return reevaluated_systems;
    }

    double rmse(double[] a, double[] b) {
        double s = 0;
        for (int i=0; i<a.length; i++) {
            double d = a[i] - b[i];
            s += d*d;
        }
        return Math.pow(s/(double)a.length, 0.5);
    }

    void printPoolStats(List<IRSystem> systems_maxDepth, List<IRSystem> systems_varDepth) {
        double[] refMaps = systems_maxDepth.stream().mapToDouble(x->x.map).toArray();
        double[] approxMaps = systems_varDepth.stream().mapToDouble(x->x.map).toArray();
        System.out.println(String.format("Pearson's = %.4f", (new PearsonCorrelation()).correlation(refMaps, approxMaps)));
        System.out.println(String.format("Kendall's = %.4f", (new KendallsCorrelation()).correlation(refMaps, approxMaps)));
        System.out.println(String.format("RMSE = %.4f", rmse(refMaps, approxMaps)));
        double recall = depthBasedEvaluator.avgRecallAtVariableDepths();
        System.out.println(String.format("Avg.Recall@Depths = %.4f", recall));
    }

    public static void main(String[] args) {
        if (args.length==0) {
            args = new String[1];
            args[0] = "deptheval.msmarco.properties";
        }
        Settings.init(args[0]);
        List<IRSystem> systems_maxDepth = null;
        if (Settings.tsvMode) {
            QueryFields = new String[1];
            QueryFields[0] = "t";
        }

        try {
            DepthPoolingWorkflow depthPoolingWorkflow =
                    new DepthPoolingWorkflow(Settings.minDepth, Settings.maxDepth);

            String resFileDir = Settings.getProp().getProperty("resfiledir");
            if (resFileDir!=null) {
                systems_maxDepth = depthPoolingWorkflow.evaluateRuns(resFileDir, Settings.maxDepth); // initial eval with max depth
            }
            else {
                systems_maxDepth = depthPoolingWorkflow.evaluateRuns(Settings.maxDepth); // initial eval with max depth
            }

            System.out.println("System MAPs with depth = " + Settings.maxDepth);
            System.out.println(systems_maxDepth.stream().collect(Collectors.toMap(x->x.name, x->x.map)));

            List<IRSystem> systems_varDepth = depthPoolingWorkflow.evaluateRunAndPrintStats(systems_maxDepth, 0);
            System.out.println("System MAPs with variable depth");
            System.out.println(systems_varDepth.stream().collect(Collectors.toMap(x->x.name, x->x.map)));

            depthPoolingWorkflow.printPoolStats(systems_maxDepth, systems_varDepth);

            /*
            int avgDepth = (int)(systems_varDepth.get(0).depths.values()
                    .stream()
                    .mapToInt(x->x.intValue())
                    .sum()/(double)systems_varDepth.get(0).depths.size()
            );
             */
            int avgDepth = Settings.minDepth + (int)(0.5f*(Settings.maxDepth - Settings.minDepth));
            List<IRSystem> systems_minDepth = depthPoolingWorkflow.evaluateRunAndPrintStats(systems_maxDepth, avgDepth);
            depthPoolingWorkflow.printPoolStats(systems_maxDepth, systems_minDepth);

            System.out.println("System MAPs with depth = " + avgDepth);
            System.out.println(systems_minDepth.stream().collect(Collectors.toMap(x->x.name, x->x.map)));

            systems_minDepth = depthPoolingWorkflow.evaluateRunAndPrintStats(systems_maxDepth, Settings.minDepth);
            depthPoolingWorkflow.printPoolStats(systems_maxDepth, systems_minDepth);

            System.out.println("System MAPs with depth = " + Settings.minDepth);
            System.out.println(systems_minDepth.stream().collect(Collectors.toMap(x->x.name, x->x.map)));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
