package org.qppeval.evaluator;

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
        this.rtuples = new ArrayList<>(1000);
        avgP = -1;
        numRelRet = -1;
    }

    public double[] getRSVs(int k) {
        return ArrayUtils
                .toPrimitive(rtuples
                .stream()
                .map(ResultTuple::getScore)
                .collect(Collectors.toList())
                .subList(0, Math.min(k, rtuples.size()))
                .toArray(new Double[0]),
    0.0);
    }

    void addTuple(String docName, int rank, double score) {
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
        int numRel = relInfo.numRel;
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
        return numRelSeen;
    }

    @Override
    public int compareTo(RetrievedResults that) {
        return this.qid.compareTo(that.qid);
    }
}


