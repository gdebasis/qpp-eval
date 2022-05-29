/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.evaluator;

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

        for (int i=0; i < depth; i++) {
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

class AllRetrievedResults {
    Map<String, RetrievedResults> allRetMap;
    String resFile;
    AllRelRcds allRelInfo;
    
    public AllRetrievedResults(String resFile) {
        String line;
        this.resFile = resFile;

        allRetMap = new TreeMap<>();
        try (FileReader fr = new FileReader(resFile);
             BufferedReader br = new BufferedReader(fr); ) {
            while ((line = br.readLine()) != null) {
                storeRetRcd(line);
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    public AllRetrievedResults(String qid, TopDocs topDocs) {
        allRetMap = new TreeMap<>();
        RetrievedResults rr = new RetrievedResults(qid);
        int rank = 1;
        for (ScoreDoc sd: topDocs.scoreDocs) {
            rr.addTuple(Settings.getDocIdFromOffset(sd.doc), rank++, sd.score);
        }
        allRetMap.put(qid, rr);
    }

    public RetrievedResults getRetrievedResultsForQueryId(String qid) {
        return allRetMap.get(qid);
    }

    void storeRetRcd(String line) {
        String[] tokens = line.split("\\s+");
        String qid = tokens[0];
        RetrievedResults res = allRetMap.get(qid);
        if (res == null) {
            res = new RetrievedResults(qid);
            allRetMap.put(qid, res);
        }
        res.addTuple(tokens[2], Integer.parseInt(tokens[3]), Double.parseDouble(tokens[4]));
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            buff.append(res.toString()).append("\n");
        }
        return buff.toString();        
    }
    
    public void fillRelInfo(AllRelRcds relInfo) {
        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            PerQueryRelDocs thisRelInfo = relInfo.getRelInfo(String.valueOf(res.qid));
            if (thisRelInfo != null)
                res.fillRelInfo(thisRelInfo);
        }
        this.allRelInfo = relInfo;
    }

    public double compute(String qid, Metric m) {
        double res = 0;
        RetrievedResults rr = allRetMap.get(qid);
        switch (m) {
            case AP: res = rr.computeAP(); break;
            case P_10: res = rr.precAtTop(10); break;
            case Recall: res = rr.computeRecall(); break;
            case nDCG: res = rr.computeNdcg();
        }
        return res;
    }

    String computeAll() {
        StringBuffer buff = new StringBuffer();
        float map = 0f;
        float gm_ap = 0f;
        float avgRecall = 0f;
        float numQueries = (float)allRetMap.size();
        float pAt5 = 0f;

        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            float ap = res.computeAP();
            map += ap;
            gm_ap += ap>0? Math.log(ap): 0;
            pAt5 += res.precAtTop(5);
        }
        
        buff.append("recall:\t").append(avgRecall/(float)allRelInfo.getTotalNumRel()).append("\n");
        buff.append("map:\t").append(map/numQueries).append("\n");
        buff.append("gmap:\t").append((float)Math.exp(gm_ap/numQueries)).append("\n");
        buff.append("P@5:\t").append(pAt5/numQueries).append("\n");

        return buff.toString();
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
