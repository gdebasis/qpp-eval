package org.evaluator;

public class ResultTuple implements Comparable<ResultTuple> {
    String docName; // doc name
    int rank;       // rank of retrieved document
    double score;    // score of retrieved document
    int rel;    // is this relevant? comes from qrel-info

    public ResultTuple(String docName, int rank, double score) {
        this.docName = docName;
        this.rank = rank;
        this.setScore(score);
    }

    @Override
    public int compareTo(ResultTuple t) {
        return rank < t.rank? -1 : rank == t.rank? 0 : 1;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getDocName() {
        return docName;
    }
}

