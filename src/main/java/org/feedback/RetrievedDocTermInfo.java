/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.feedback;

/**
 *
 * @author Debasis
 */
public class RetrievedDocTermInfo implements Comparable<RetrievedDocTermInfo> {
    private String term;
    private int tf;
    private int df;
    private float wt;   // weight of this term, e.g. the P(w|R) value

    public RetrievedDocTermInfo(String term) {
        this.term = term;
    }
    
    public RetrievedDocTermInfo(String term, int tf) {
        this.term = term;
        this.tf = tf;
    }

    @Override
    public int compareTo(RetrievedDocTermInfo that) { // descending order
        return this.wt < that.wt? 1 : this.wt == that.wt? 0 : -1;
    }
    
    public float getWeight() { return wt; }
    public void setWeight(float wt) { this.wt = wt; }
    
    public String getTerm() { return term; }
    public void incrementDF() { df++; }
    public void setDf(int df) { this.df = df; }
    public void incrementTf(int tf) { this.tf += tf; }

    public int getDf() { return df; }
    public int getTf() { return tf; }
    public void setTf(int tf) { this.tf = tf; }

    @Override
    public String toString() {
        return term + ", " + getWeight();
    }
}

