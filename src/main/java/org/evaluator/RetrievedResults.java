package org.evaluator;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RetrievedResults implements Comparable<RetrievedResults> {
    String qid;
    List<ResultTuple> rtuples;
    int numRelRet;
    float avgP;
    PerQueryRelDocs relInfo;

    public RetrievedResults(String qid) {
        this.qid = qid;
        this.rtuples = new ArrayList<>(100);
        avgP = -1;
        numRelRet = -1;
    }

    public String getQid() { return qid; }

    public int getNumRet() { return rtuples.size(); }

    public List<ResultTuple> getTuples() { return this.rtuples; }

    public double[] getRSVs(int k) {
        return ArrayUtils
                .toPrimitive(rtuples
                .stream()
                .map(ResultTuple::getScore)
                .collect(Collectors.toList())
                .subList(0, Math.min(k, rtuples.size()))
                .toArray(new Double[0]), 0.0);
    }

    public void addTuple(String docName, int rank, double score) {
        rtuples.add(new ResultTuple(docName, rank, score));
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (ResultTuple rt : rtuples) {
            buff.append(qid).append("\t").
                    append(rt.docName).append("\t").
                    append(rt.rank).append("\t").
                    append(rt.rel).append("\n");
        }
        return buff.toString();
    }

    void fillRelInfo(PerQueryRelDocs relInfo) {
        String qid = relInfo.qid;

        for (ResultTuple rt : rtuples) {
            Integer relIntObj = relInfo.relMap.get(rt.docName);
            rt.rel = relIntObj == null? 0 : relIntObj.intValue();
        }
        this.relInfo = relInfo;
    }

    float computeAP() {
        if (avgP > -1)
            return avgP;

        float prec = 0;
        int numRel = relInfo.relMap.size();
        int numRelSeen = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (tuple.rel < 1)
                continue;
            numRelSeen++;
            prec += numRelSeen/(float)(tuple.rank);
        }
        numRelRet = numRelSeen;
        prec = numRel==0? 0 : prec/(float)numRel;
        this.avgP = prec;

        return prec;
    }

    float precAtTop(int k) {
        int numRelSeen = 0;
        int numSeen = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (k>0 && numSeen >= k)
                break;
            if (tuple.rel >= 1)
                numRelSeen++;
            numSeen++;
        }
        return numRelSeen/(float)k;
    }

    float computeRecall() {
        if (numRelRet > -1)
            return numRelRet;
        int numRelSeen = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (tuple.rel < 1)
                continue;
            numRelSeen++;
        }
        numRelRet = numRelSeen;
        return numRelSeen/(float)relInfo.relMap.size();
    }
    
    float computeNdcg() {
        float dcg = 0;
        float idcg = calculateIdcg(relInfo.relMap.size());
        if (idcg == 0) {
            return 0;
        }
        
        for (int i = 0; i < this.rtuples.size(); i++) {
            ResultTuple predictedItem = this.rtuples.get(i);
            if (!relInfo.relMap.containsKey(predictedItem.docName))
                continue;

            // the relevance in the DCG part is either 1 (the item is contained in real data) 
            // or 0 (item is not contained in the real data)
            int itemRelevance = 1;
            if (!relInfo.relMap.containsKey(predictedItem.docName))
                itemRelevance = 0;

            // compute NDCG
            int rank = i + 1;
            dcg += (Math.pow(2, itemRelevance) - 1.0) * (Math.log(2) / Math.log(rank + 1));
        }

    return dcg / idcg;
    }
    
    float calculateIdcg(int n) {
        float idcg = 0;
        // if can get relevance for every item should replace the relevance score at this point, else
        // every item in the ideal case has relevance of 1
        int itemRelevance = 1;

        for (int i = 0; i < n; i++){
            idcg += (Math.pow(2, itemRelevance) - 1.0) * ( Math.log(2) / Math.log(i + 2) );
        }

        return idcg;
    }

    @Override
    public int compareTo(RetrievedResults that) {
        return this.qid.compareTo(that.qid);
    }
}


