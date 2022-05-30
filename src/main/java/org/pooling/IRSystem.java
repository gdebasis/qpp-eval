package org.pooling;

import org.apache.lucene.search.TopDocs;
import java.util.*;
import java.util.stream.Collectors;

public class IRSystem implements Comparable<IRSystem> {
    String name;
    String tdn;
    String sim;
    Map<String, TopDocs> topDocsMap; // a list of topDocs for the query set
    Map<String, Integer> depths;
    double map;

    IRSystem(String tdn, String sim, List<String> queryIds, int constantDepth) {
        this.tdn = tdn;
        this.sim = sim;
        name = tdn + ":" + sim.toString();
        topDocsMap = null;
        depths = queryIds.stream().collect(Collectors.toMap(x->x, x->constantDepth));
    }

    IRSystem(String name, Map<String, TopDocs> topDocsMap) {
        this.name = name;
        this.topDocsMap = new HashMap<>(topDocsMap);
    }

    void setTopDocs(Map<String, TopDocs> topDocsMap) {
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

