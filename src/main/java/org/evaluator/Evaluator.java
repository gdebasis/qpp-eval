/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.evaluator;

import org.apache.lucene.search.MultiCollectorManager;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.experiments.Settings;
import org.pooling.IRSystem;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author Debasis
 */

class PerQueryRelDocs {
    String qid;
    Map<String, Integer> relMap; // keyed by docid, entry stores the rel value

    public PerQueryRelDocs(String qid) {
        this.qid = qid;
        relMap = new HashMap<>();
    }

    void addTuple(String docId, int rel) {
        if (relMap.get(docId) != null)
            return;
        if (rel > 0) {
            relMap.put(docId, rel);
        }
    }
}

class AllRelRcds {
    String qrelsFile;
    Map<String, PerQueryRelDocs> perQueryRels;
    int totalNumRel;
    List<IRSystem> systems;
    Map<String, Boolean> inducedRel;    // a map from docid to a boolean
                                        // which is true if each system retrieves this doc
                                        // at rank < depth for that query

    public AllRelRcds(String qrelsFile) {
        this.qrelsFile = qrelsFile;
        perQueryRels = new HashMap<>();
        totalNumRel = 0;
        load();
    }

    public AllRelRcds(String qrelsFile, List<IRSystem> systems) {
        this.qrelsFile = qrelsFile;
        this.systems = systems;
        perQueryRels = new HashMap<>();
        totalNumRel = 0;
        load();
    }

    int getTotalNumRel() {
        if (totalNumRel > 0)
            return totalNumRel;
        
        for (Map.Entry<String, PerQueryRelDocs> e : perQueryRels.entrySet()) {
            PerQueryRelDocs perQryRelDocs = e.getValue();
            totalNumRel += perQryRelDocs.relMap.size();
        }
        return totalNumRel;
    }

    void filter(IRSystem system, String qid) {
        TopDocs topDocs = system.getTopDocs(qid);
        int depth = system.getDepth(qid);

        for (int i=0; i < Math.min(depth, topDocs.scoreDocs.length); i++) {
            ScoreDoc sd = topDocs.scoreDocs[i];
            String docId = Settings.getDocIdFromOffset(sd.doc);
            Boolean rel = inducedRel.get(docId);
            if (rel==null)
                continue;
            inducedRel.put(docId, true);
        }
    }

    private void load() {
        try {
            FileReader fr = new FileReader(qrelsFile);
            BufferedReader br = new BufferedReader(fr);
            String line;

            while ((line = br.readLine()) != null) {
                storeRelRcd(line);
            }
            br.close();
            fr.close();

            // TODO: We need to make this a faster operation
            // filter out the relevant documents that appear beyond the depth cut-off
            if (systems != null) {
                // init every rel doc to true
                for (String qid : this.perQueryRels.keySet()) {
                    PerQueryRelDocs perQueryRelDocs = perQueryRels.get(qid);
                    inducedRel = perQueryRelDocs.relMap.keySet().stream().collect(Collectors.toMap(x -> x, x -> false));
                    for (IRSystem system : systems) {
                        filter(system, qid);
                    }

                    // Now leave out the ones whose indicator is false...
                    Set<String> filteredRelDocs =
                            inducedRel.entrySet().stream()
                                    .filter(e -> e.getValue() == true)
                                    .map(e -> e.getKey()).collect(Collectors.toSet());

                    Map<String, Integer> newRelMap = new HashMap<>();
                    for (String docId : filteredRelDocs) {
                        newRelMap.put(docId, perQueryRelDocs.relMap.get(docId));
                    }
                    perQueryRelDocs.relMap = newRelMap;
                }

                System.out.println("#rels: " +
                perQueryRels
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(e->e.getKey(), e->e.getValue().relMap.size()))
                    .toString()
                );
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
    
    void storeRelRcd(String line) {
        String[] tokens = line.split("\\s+");
        String qid = tokens[0];
        PerQueryRelDocs relTuple = perQueryRels.get(qid);
        if (relTuple == null) {
            relTuple = new PerQueryRelDocs(qid);
            perQueryRels.put(qid, relTuple);
        }
        relTuple.addTuple(tokens[2], Integer.parseInt(tokens[3]));
    }
    
    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (Map.Entry<String, PerQueryRelDocs> e : perQueryRels.entrySet()) {
            PerQueryRelDocs perQryRelDocs = e.getValue();
            buff.append(e.getKey()).append("\n");
            for (Map.Entry<String, Integer> rel : perQryRelDocs.relMap.entrySet()) {
                String docName = rel.getKey();
                int relVal = rel.getValue();
                buff.append(docName).append(",").append(relVal).append("\t");
            }
            buff.append("\n");
        }
        return buff.toString();
    }
    
    PerQueryRelDocs getRelInfo(String qid) {
        return perQueryRels.get(qid);
    }
}

public class Evaluator {
    AllRelRcds relRcds;
    AllRetrievedResults retRcds;

    public Evaluator(String qrelsFile, String resFile) {
        relRcds = new AllRelRcds(qrelsFile);
        retRcds = new AllRetrievedResults(resFile);
        fillRelInfo();
    }

    public Evaluator(String qrelsFile, List<IRSystem> systems) {
        relRcds = new AllRelRcds(qrelsFile, systems);
    }

    public Evaluator(Properties prop) {
        this(prop.getProperty("qrels.file"),
            prop.getProperty("res.file")
        );
    }

    public RetrievedResults getRetrievedResultsForQueryId(String qid) {
        return retRcds.getRetrievedResultsForQueryId(qid);
    }

    private void fillRelInfo() {
        retRcds.fillRelInfo(relRcds);
    }
    
    public String computeAll() {
        return retRcds.computeAll();
    }

    public double compute(String qid, Metric m) {
        return retRcds.compute(qid, m);
    }

    public double compute(String qid, IRSystem system, Metric m) {
        // load retrieved tuples from memory instead of loading from file
        retRcds = new AllRetrievedResults(qid, system.getTopDocs(qid));
        retRcds.fillRelInfo(this.relRcds);
        return retRcds.compute(qid, m);
    }

    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff.append(relRcds.toString()).append("\n");
        buff.append(retRcds.toString());
        return buff.toString();
    }

    public float avgRecallAtVariableDepths() {
        double[] thisNumRels = relRcds.perQueryRels
                .entrySet()
                .stream()
                .map(x->(double)x.getValue().relMap.size())
                .mapToDouble(x->x.doubleValue())
                .toArray()
        ;
        double[] depths = this.relRcds.systems.get(0).getDepths().stream().mapToDouble(x->x.doubleValue()).toArray();
        double sum = 0, z = 0;
        for (int i=0; i<thisNumRels.length; i++) {
            sum += 1/depths[i] * thisNumRels[i];
            z += 1/depths[i];
        }
        return (float)(sum/z); // weighted average
    }

    public float precisionAtDepths(Evaluator ref) {
        int thisNumRels = relRcds.perQueryRels
                .entrySet()
                .stream()
                .map(x->x.getValue().relMap.size())
                .mapToInt(x-> x.intValue())
                .sum();

        int refNumRels = ref.relRcds.perQueryRels
                .entrySet()
                .stream()
                .map(x->x.getValue().relMap.size())
                .mapToInt(x-> x.intValue())
                .sum();
        return thisNumRels/(float)refNumRels;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }
        try {
            Properties prop = new Properties();
            prop.load(new FileReader(args[0]));
            
            String qrelsFile = prop.getProperty("qrels.file");
            String resFile = prop.getProperty("res.file");
            
            Evaluator evaluator = new Evaluator(qrelsFile, resFile);
            System.out.println(evaluator.computeAll());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
    
}
