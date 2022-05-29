package org.pooling;

import org.apache.lucene.search.TopDocs;

import java.util.HashMap;
import java.util.Map;

public class IRSystem implements Comparable<IRSystem> {
    String name;
    Map<String, TopDocs> topDocsMap; // a list of topDocs for the query set
    Map<String, Integer> depths;
    double map;

    IRSystem(String name, Map<String, TopDocs> topDocsMap) {
        this.name = name;
        this.topDocsMap = new HashMap<>(topDocsMap);
    }

    @Override
    public int compareTo(IRSystem that) {
        return Double.compare(this.map, that.map);
    }

    public String toString() {
        return String.format("%s: %.4f", name, map);
    }

    public TopDocs getTopDocs(String qid) { return topDocsMap.get(qid); }
    public int getDepth(String qid) { return depths.get(qid); }
}


