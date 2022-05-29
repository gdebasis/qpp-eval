package org.experiments;

import org.qpp.RLSSpecificity;

public class RLSWorkflow extends NQCCalibrationWorkflow {
    int num_fdbk;
    int num_sample;
    int top_refs;

    RLSWorkflow(String queryFile, int num_fdbk, int num_sample, int top_refs) throws Exception {
        super(queryFile);
        this.num_fdbk = num_fdbk;
        this.num_sample = num_sample;
        this.top_refs = top_refs;
        qppMethod = new RLSSpecificity(Settings.getSearcher(), num_fdbk, num_sample, top_refs);
    }

    RLSWorkflow(String queryFile, String resFile, int num_fdbk, int num_sample, int top_refs) throws Exception {
        super(queryFile, resFile);
        this.num_fdbk = num_fdbk;
        this.num_sample = num_sample;
        this.top_refs = top_refs;
        qppMethod = new RLSSpecificity(Settings.getSearcher(), num_fdbk, num_sample, top_refs);
    }

    public static void main(String[] args) {
        final String QUERY_FILE = "/home/suchana/NetBeansProjects/qpp-variation/data/topics.401-450.xml";
        final String RES_FILE = "/store/causalIR/drmm/NQC_trec/DRMM_NQC_noQV/trec8_drmm_noQV_100cut.res";
        final String RES_FILE_INPUT = "rerank"; // rerank when you input a reranked res file externally

        Settings.init("qpp.properties");
        final int[] NUM_FDBK_TOP_DOCS = {10, 15, 20, 25, 30, 35, 40, 45, 50};
        final int[] NUM_SAMPLES = {5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};  // Number of reference lists, i.e. number of queries to form
//        final int[] NUM_FDBK_TOP_DOCS = {10};
//        final int[] NUM_SAMPLES = {5};  // Number of reference lists, i.e. number of queries to form
//        final int L = 5; // number of reference lists for both pos and neg (corresponding to the lowest p values)
        
        switch(RES_FILE_INPUT) {
            case "rerank":
                for (int num_fdbk: NUM_FDBK_TOP_DOCS) {
                    for (int num_sample: NUM_SAMPLES) {
                        for (int l = (int)Math.floor(num_sample/2); l>0; l--) {
    //                        int l = L;
                            System.out.println(String.format("RUN STARTS FOR num_fdbk = %d num_sample = %d, top refs = %d", num_fdbk, num_sample, l));
                            RLSWorkflow rlsWorkflow = null;
                            try {
                                rlsWorkflow = new RLSWorkflow(QUERY_FILE, RES_FILE, num_fdbk, num_sample, l);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }

                            System.out.println(String.format("Evaluating on %d queries", rlsWorkflow.queries.size()));
                            rlsWorkflow.computeCorrelation(rlsWorkflow.queries, rlsWorkflow.qppMethod);
                        //}
                        }
                    }
                } 
                break;
                
            case "lm":
                for (int num_fdbk: NUM_FDBK_TOP_DOCS) {
                    for (int num_sample: NUM_SAMPLES) {
                        for (int l = (int)Math.floor(num_sample/2); l>0; l--) {
    //                        int l = L;
                            System.out.println(String.format("RUN STARTS FOR num_fdbk = %d num_sample = %d, top refs = %d", num_fdbk, num_sample, l));
                            RLSWorkflow rlsWorkflow = null;
                            try {
                                rlsWorkflow = new RLSWorkflow(QUERY_FILE, num_fdbk, num_sample, l);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }

                            System.out.println(String.format("Evaluating on %d queries", rlsWorkflow.queries.size()));
                            rlsWorkflow.computeCorrelation(rlsWorkflow.queries, rlsWorkflow.qppMethod);
                            //}
                        }
                    }
                } 
                break;
        }                  
    }
}
