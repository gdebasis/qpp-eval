package org.correlation;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.evaluator.AllRetrievedResults;
import org.evaluator.RetrievedResults;

import java.util.*;
import java.io.*;
import java.util.stream.Collectors;

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

    static void computeOverlapForQueryPairs(String resFile, String idFile, int depth, float p) throws Exception {
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
        int num_pairs = 0;
        for (Set<String> queriesForComparison: equivalenceClass.values()) {
            int num_queries = queriesForComparison.size();
            num_pairs += (num_queries*(num_queries-1))>>1;
            String[] queryIds = queriesForComparison.toArray(new String[0]);
            rbo += rbo_overlap(queryIds, retrievedResults, depth, p);
            jaccard += jacard_overlap(queryIds, retrievedResults);
        }
        System.out.println("RBO = " + rbo/(double)num_pairs);
        System.out.println("Jaccard = " + jaccard/(double)num_pairs);
    }

    public static double jacard_overlap(String[] queryIds, AllRetrievedResults retrievedResults) {
        double avg_jaccard = 0;
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
                avg_jaccard += intersection.size()/(setA.size() + setB.size() - intersection.size());
            }
        }
        return avg_jaccard;
    }

    public static double rbo_overlap(String[] queryIds, AllRetrievedResults retrievedResults, int depth, float p) {
        double avg_rbo = 0;
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

                avg_rbo += computeRBO(setA, setB, depth, p);
            }
        }
        return avg_rbo;
    }

    public static void main(String[] args) {

        int[] a = {1, 3, 4, 5, 8, 9};
        int[] b = {2, 3, 5, 6, 8, 10};
        System.out.println(OverlapStats.computeRBO(a, b, a.length, 0.8f));

        int[] c = {1, 3, 4, 5, 8, 9};
        int[] d = {3, 4, 5, 6, 8, 10};
        System.out.println(OverlapStats.computeRBO(c, d, c.length, 0.8f));

        int[] e = {1, 3, 4, 5, 8, 9};
        int[] f = {3, 8, 12, 14, 18};
        System.out.println(OverlapStats.computeRBO(e, f, e.length, 0.8f));

        try {
            computeOverlapForQueryPairs(
                    "msmarco_runs/bm25/bm25_one",
                    "msmarco_runs/1.txt",
                    10, 0.5f);
            computeOverlapForQueryPairs(
                    "msmarco_runs/bm25/bm25_zero",
                    "msmarco_runs/0.txt",
                    10, 0.5f);
        }
        catch (Exception ex) { ex.printStackTrace(); }

    }
}
