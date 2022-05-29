package org.correlation;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class OverlapStats {

    static double overlap(int[] docIdsA, int[] docIdsB, int depth) {
        Set<Integer> docSetA = Arrays.stream(docIdsA).boxed().collect(Collectors.toSet());
        Set<Integer> docSetB = Arrays.stream(docIdsB).boxed().collect(Collectors.toSet());
        Set<Integer> overlap = new HashSet<>(docSetA);
        overlap.retainAll(docSetB);
        return overlap.size()/(double)depth;
    }

    static public double computeRBO(int[] docIdsA, int[] docIdsB, int k, float p) {
        double ao_d;
        double aggr_overlaps = 0;
        for (int d=1; d <= k; d++) {  // overlap at each cutoff
            ao_d = overlap(docIdsA, docIdsB, d) * Math.pow(p, d-1);
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

    public static void main(String[] args) {
        int[] a = {1, 3, 4, 5, 8, 9};
        int[] b = {2, 3, 5, 6, 8, 10};
        System.out.println(OverlapStats.computeRBO(a, b, a.length, 0.9f));

        int[] c = {1, 3, 4, 5, 8, 9};
        int[] d = {3, 4, 5, 6, 8, 10};
        System.out.println(OverlapStats.computeRBO(c, d, c.length, 0.9f));

        int[] e = {1, 3, 4, 5, 8, 9};
        int[] f = {3, 8, 12, 14, 18};
        System.out.println(OverlapStats.computeRBO(e, f, e.length, 0.9f));
    }
}
