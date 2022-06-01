package org.correlation;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.evaluator.AllRetrievedResults;
import org.evaluator.RetrievedResults;

import java.util.*;
import java.io.*;
import java.util.stream.Collectors;

class EvalData {
    String name;
    String resFile_1;
    String resFile_0;

    static String queryGroups_1;
    static String queryGroups_0;

    static final int depth = 10;
    static final float p = 0.9f;

    static void init(String queryGroups_1, String queryGroups_0) {
        EvalData.queryGroups_0 = queryGroups_0;
        EvalData.queryGroups_1 = queryGroups_1;
    }

    EvalData(String name, String resFile_1, String resFile_0) {
        this.name = name;
        this.resFile_0 = resFile_0;
        this.resFile_1 = resFile_1;
    }

    void evaluate() throws Exception {
        Pair<Double, Double> diffIN = OverlapStats.computeOverlapForQueryPairs(
                resFile_1, // resfile_1 contains those queries where we expect a change (or sim-info-need=false)
                queryGroups_1,
                depth, p, false);
        Pair<Double, Double> simIN = OverlapStats.computeOverlapForQueryPairs(
                resFile_0,
                queryGroups_0,
                depth, p, true);

        // left is Jaccard, right is RBO
        System.out.println(String.format("%s\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f",
                name,
                simIN.getLeft(), // Jaccard sim
                diffIN.getLeft(), // Jaccard diff
                (diffIN.getLeft() + simIN.getLeft())/2, // avg
                simIN.getRight(), // RBO sim
                diffIN.getRight(), // RBO diff
                (diffIN.getRight() + simIN.getRight())/2
            )
        );
    }
}

public class OverlapStats {

    static double rbo_overlap(List<String> docSetA, List<String> docSetB, int depth) {
        Set<String> overlap = new HashSet<>(docSetA.stream().limit(depth).collect(Collectors.toSet()));
        overlap.retainAll(docSetB.stream().limit(depth).collect(Collectors.toSet()));
        return overlap.size()/(double)depth;
    }

    static public double computeRBO(List<String> docIdsA, List<String> docIdsB, int k, float p) {
        double ao_d;
        double aggr_overlaps = 0;
        for (int d=1; d <= k; d++) {  // overlap at each cutoff
            ao_d = rbo_overlap(docIdsA, docIdsB, d) * Math.pow(p, d-1);
            aggr_overlaps += ao_d;
        }
        return (1-p) * aggr_overlaps;
    }

    static double rbo_overlap(int[] docIdsA, int[] docIdsB, int depth) {
        Set<Integer> docSetA = Arrays.stream(docIdsA).boxed().limit(depth).collect(Collectors.toSet());
        Set<Integer> docSetB = Arrays.stream(docIdsB).boxed().limit(depth).collect(Collectors.toSet());
        Set<Integer> overlap = new HashSet<>(docSetA);
        overlap.retainAll(docSetB);
        return overlap.size()/(double)depth;
    }

    static public double computeRBO(int[] docIdsA, int[] docIdsB, int k, float p) {
        double ao_d;
        double aggr_overlaps = 0;
        for (int d=1; d <= k; d++) {  // overlap at each cutoff
            ao_d = rbo_overlap(docIdsA, docIdsB, d) * Math.pow(p, d-1);
            aggr_overlaps += ao_d;
        }
        return (1-p) * aggr_overlaps;
    }

    static public double computeRBO(TopDocs listA, TopDocs listB) {
        int[] docIdsA = getTopDocNames(listA);
        int[] docIdsB = getTopDocNames(listB);
        return computeRBO(docIdsA, docIdsB, listA.scoreDocs.length, 0.9f);
    }

    static public double computeRBO(TopDocs listA, TopDocs listB, int k, float p) {
        int[] docIdsA = getTopDocNames(listA);
        int[] docIdsB = getTopDocNames(listB);
        return computeRBO(docIdsA, docIdsB, k, p);
    }

    static private int[] getTopDocNames(TopDocs topDocs) {
        int[] docIds = new int[topDocs.scoreDocs.length];
        int i=0;
        for (ScoreDoc sd: topDocs.scoreDocs) {
            docIds[i++] = sd.doc;
        }
        return docIds;
    }

    // instead of using a rank correlation metric (where we need to supply two lists of float),
    // we compute the average shift in the ranks of items.
    // Assumption: |listA \cap listB| = |listA| = |listB|
    static public double computeRankDist(TopDocs listA, TopDocs listB) {
        int[] docIdsA = getTopDocNames(listA);
        int[] docIdsB = getTopDocNames(listB);

        Arrays.sort(docIdsB);
        int posInA = 0, posInB;
        double delRank;
        double avgShift = 0;

        for (int docId: docIdsA) {
            posInB = Arrays.binarySearch(docIdsB, docId);
            if (posInB >= 0) {
                delRank = (posInA - posInB)/(double)docIdsA.length;
                avgShift += delRank * delRank;
            }
            posInA++;
        }

        avgShift = avgShift/(double)docIdsA.length;
        avgShift = Math.sqrt(avgShift);
        return avgShift;
    }

    static Pair<Double, Double> computeOverlapForQueryPairs(
            String resFile, String idFile, int depth, float p, boolean similarInfoNeed) throws Exception {
        Map<String, Set<String>> equivalenceClass = new HashMap<>();

        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(idFile))) {
            while ((line = br.readLine())!=null) {
                String[] tokens = line.split("\t");
                Set<String> queriesToCompareWith = equivalenceClass.get(tokens[0]);
                if (queriesToCompareWith==null) {
                    queriesToCompareWith = new HashSet<>();
                    queriesToCompareWith.add(tokens[0]);
                    equivalenceClass.put(tokens[0], queriesToCompareWith);
                }
                queriesToCompareWith.add(tokens[2]);
            }
        }

        AllRetrievedResults retrievedResults = new AllRetrievedResults(resFile);
        double rbo = 0, jaccard = 0;
        for (Set<String> queriesForComparison: equivalenceClass.values()) {
            String[] queryIds = queriesForComparison.toArray(new String[0]);
            double del_rbo = rbo_overlap(queryIds, retrievedResults, depth, p);;
            double del_jaccard = jacard_overlap(queryIds, retrievedResults);
            rbo += del_rbo;
            jaccard += del_jaccard;
        }
        double z = (double)equivalenceClass.size();
        return Pair.of(similarInfoNeed? jaccard/z: 1-jaccard/z, similarInfoNeed? rbo/z: 1-rbo/z);
    }

    public static double jacard_overlap(String[] queryIds, AllRetrievedResults retrievedResults) {
        double avg_jaccard = 0;
        int npairs = 0;
        for (int i=0; i < queryIds.length-1; i++) {
            RetrievedResults this_query_retRcds_a = retrievedResults.getRetrievedResultsForQueryId(queryIds[i]);

            for (int j=i+1; j < queryIds.length; j++) {
                RetrievedResults this_query_retRcds_b = retrievedResults.getRetrievedResultsForQueryId(queryIds[j]);
                List<String> setA = this_query_retRcds_a.getTuples()
                        .stream()
                        .map(x -> x.getDocName())
                        .collect(Collectors.toList());

                List<String> setB = this_query_retRcds_b.getTuples()
                        .stream()
                        .map(x -> x.getDocName())
                        .collect(Collectors.toList());

                Set<String> intersection = setA.stream().collect(Collectors.toSet());
                intersection.retainAll(setB);
                double del_jaccard = intersection.size()/(double)(setA.size() + setB.size() - intersection.size());
                if (del_jaccard>0) {
                    avg_jaccard += del_jaccard;
                    npairs++;
                }
            }
        }
        return npairs==0? 0: avg_jaccard/(double)npairs;
    }

    public static double rbo_overlap(String[] queryIds, AllRetrievedResults retrievedResults, int depth, float p) {
        double avg_rbo = 0;
        int npairs = 0;
        for (int i=0; i < queryIds.length-1; i++) {
            RetrievedResults this_query_retRcds_a = retrievedResults.getRetrievedResultsForQueryId(queryIds[i]);

            for (int j=i+1; j < queryIds.length; j++) {
                RetrievedResults this_query_retRcds_b = retrievedResults.getRetrievedResultsForQueryId(queryIds[j]);
                List<String> setA = this_query_retRcds_a.getTuples()
                        .stream()
                        .map(x -> x.getDocName())
                        .collect(Collectors.toList());

                List<String> setB = this_query_retRcds_b.getTuples()
                        .stream()
                        .map(x -> x.getDocName())
                        .collect(Collectors.toList());

                double del_rbo = computeRBO(setA, setB, depth, p);
                if (del_rbo > 0) {
                    avg_rbo += del_rbo;
                    npairs++;
                }
            }
        }
        return npairs==0? 0: avg_rbo/(double)npairs;
    }

    public static void main(String[] args) {

        /*
        int[] a = {1, 3, 4, 5, 8, 9};
        int[] b = {2, 3, 5, 6, 8, 10};
        System.out.println(OverlapStats.computeRBO(a, b, a.length, 0.8f));

        int[] c = {1, 3, 4, 5, 8, 9};
        int[] d = {3, 4, 5, 6, 8, 10};
        System.out.println(OverlapStats.computeRBO(c, d, c.length, 0.8f));

        int[] e = {1, 3, 4, 5, 8, 9};
        int[] f = {3, 8, 12, 14, 18};
        System.out.println(OverlapStats.computeRBO(e, f, e.length, 0.8f));
        */

        EvalData.init("msmarco_runs/1.txt", "msmarco_runs/0.txt");
        List<EvalData> evalDataList = new ArrayList<>();
        evalDataList.add(new EvalData("bm25","msmarco_runs/bm25/bm25_one", "msmarco_runs/bm25/bm25_zero"));
        evalDataList.add(new EvalData("rlm","msmarco_runs/rlm-bm25/rlm_bm25_one", "msmarco_runs/rlm-bm25/rlm_bm25_zero"));
        evalDataList.add(new EvalData("kderlm","msmarco_runs/kderlm-bm25/msmarco_kderlm_res_one", "msmarco_runs/kderlm-bm25/msmarco_kderlm_res_zero"));
        evalDataList.add(new EvalData("drmm","msmarco_runs/drmm-bm25/msmarco_BM25_one.result", "msmarco_runs/drmm-bm25/msmarco_BM25_zero.result"));
        evalDataList.add(new EvalData("monot5","msmarco_runs/monot5-bm25/query-variant-one-bm25-monot5-0.8-0.5.txt", "msmarco_runs/monot5-bm25/query-variant-zero-bm25-monot5-0.8-0.5.txt"));
        evalDataList.add(new EvalData("monobert","msmarco_runs/monobert-bm25/query-variant-one-bm25-monobert-0.8-0.5.txt", "msmarco_runs/monobert-bm25/query-variant-zero-bm25-monobert-0.8-0.5.txt"));
        evalDataList.add(new EvalData("colbert","msmarco_runs/colbert-bm25/colbert-on-bm25-query-variant-one", "msmarco_runs/colbert-bm25/colbert-on-bm25-query-variant-zero"));

        try {
            for (EvalData evalData: evalDataList) {
                evalData.evaluate();
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}
