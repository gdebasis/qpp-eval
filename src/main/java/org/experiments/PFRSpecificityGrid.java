package org.experiments;

import org.qpp.QPPMethod;


public class PFRSpecificityGrid {
    float alpha;
    int num_fdbk;
    int num_expansion;
    PRFSpecificityWorkflow prfs;

    PFRSpecificityGrid(String queryFile, float alpha, int num_fdbk, int num_expansion) throws Exception {
        this.alpha = alpha;
        this.num_fdbk = num_fdbk;
        this.num_expansion = num_expansion;
        prfs = new PRFSpecificityWorkflow(queryFile, alpha, num_fdbk, num_expansion);
    }

    PFRSpecificityGrid(String queryFile, String resFile, float alpha, int num_fdbk, int num_expansion) throws Exception {
        this.alpha = alpha;
        this.num_fdbk = num_fdbk;
        this.num_expansion = num_expansion;
        prfs = new PRFSpecificityWorkflow(queryFile, resFile, alpha, num_fdbk, num_expansion);    
    }

    public static void main(String[] args) {
        final String QUERY_FILE = "/home/suchana/NetBeansProjects/qpp-variation/data/topics.401-450.xml";
        final String RES_FILE = "/store/causalIR/drmm/NQC_trec/DRMM_NQC_noQV/trec8_drmm_noQV_100cut.res";
        final String RES_FILE_INPUT = "rerank"; // rerank when you input a reranked res file externally

        Settings.init("qpp.properties");
        final float[] ALPHA = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f};
        final int[] NUM_FDBK_TOP_DOCS = {10, 15, 20, 25, 30, 35, 40, 45, 50};
        final int[] NUM_EXPANSION = {5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};  // Number of reference lists, i.e. number of queries to form

        switch(RES_FILE_INPUT) {
            case "rerank":
                for (float alpha : ALPHA) {
                    for (int num_fdbk: NUM_FDBK_TOP_DOCS) {
                        for (int num_expansion: NUM_EXPANSION) {
                            System.out.println(String.format("RUN STARTS FOR alpha = %d num_fdbk = %d, num_expansion = %d", 
                                    alpha, num_fdbk, num_expansion));
                            PFRSpecificityGrid pfr = null;
                            try {
                                pfr = new PFRSpecificityGrid(QUERY_FILE, RES_FILE, alpha, num_fdbk, num_expansion);
                                System.out.println(String.format("Evaluating on %d queries", pfr.prfs.queries.size()));
//                                pfr.prfs.computeCorrelationWithExpandedQueries(pfr.prfs.queries);
                                pfr.prfs.computeCorrelationWithoutExpandedQueries(pfr.prfs.queries);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    } 
                }
                break;
                
            case "lm":
                for (float alpha : ALPHA) {
                    for (int num_fdbk: NUM_FDBK_TOP_DOCS) {
                        for (int num_expansion: NUM_EXPANSION) {
                            System.out.println(String.format("RUN STARTS FOR alpha = %d num_fdbk = %d, num_expansion = %d", 
                                    alpha, num_fdbk, num_expansion));
                            PFRSpecificityGrid pfr = null;
                            try {
                                pfr = new PFRSpecificityGrid(QUERY_FILE, alpha, num_fdbk, num_expansion);
                                System.out.println(String.format("Evaluating on %d queries", pfr.prfs.queries.size()));
//                                pfr.prfs.computeCorrelationWithExpandedQueries(pfr.prfs.queries);
                                pfr.prfs.computeCorrelationWithoutExpandedQueries(pfr.prfs.queries);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    } 
                }
                break;
        }                  
    }
}
