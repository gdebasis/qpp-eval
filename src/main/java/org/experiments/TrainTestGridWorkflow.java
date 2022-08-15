package org.experiments;

import org.qpp.*;
import org.trec.TRECQuery;

import java.util.List;

public class TrainTestGridWorkflow extends NQCCalibrationWorkflow {
    QPPMethod qppMethod;

    TrainTestGridWorkflow(QPPMethod qppMethod, String queryFile, String resFile) throws Exception {
        super(queryFile, resFile);
        this.qppMethod = qppMethod;
    }

    public int calibrateTopK(List<TRECQuery> trainQueries) {
        final int[] qppTopKChoices = {10, 20, 30, 40, 50};
        int best_qppTopK = 0;
        double max_corr = -1;

        for (int qppTopK: qppTopKChoices) {
            System.out.println(String.format("Executing QPP Method %s (%d)", qppMethod.name(), qppTopK));
            double corr = computeCorrelation(trainQueries, qppMethod, qppTopK);
            if (corr > max_corr) {
                max_corr = corr;
                best_qppTopK = qppTopK;
            }
        }
        return best_qppTopK;
    }

    public double epoch() {
        final float TRAIN_RATIO = 0.5f;
        int tuned_topk = 0;

        TrainTestInfo trainTestInfo = new TrainTestInfo(queries, TRAIN_RATIO);
        if (!qppMethod.name().equals("avgidf")) { // nothing to tune for pre-ret methods!
            tuned_topk = calibrateTopK(trainTestInfo.getTrain());
            System.out.println("Optimal top-k = " + tuned_topk);
        }
        double corr = computeCorrelation(trainTestInfo.getTest(), qppMethod, tuned_topk);
        System.out.println("Test set correlation = " + corr);
        return corr;
    }

    public static void main(String[] args) {
        final String queryFile = "data/trecdl1920.queries";
        //final String resFile = "msmarco_runs/colbert.reranked.res.trec";
        final String resFile = "msmarco_runs/trecdl.monot5.rr.pos-scores.res";
        Settings.init("msmarco.properties");

        try {
            QPPMethod[] qppMethods = {
                    new AvgIDFSpecificity(Settings.getSearcher()),
                    //new NQCSpecificity(Settings.getSearcher()),
                    //new WIGSpecificity(Settings.getSearcher()),
                    //new OddsRatioSpecificity(Settings.getSearcher(), 0.2f), // 20% as top and bottom
            };

            for (QPPMethod qppMethod: qppMethods) {
                System.out.println("Getting results for QPP method " + qppMethod.name());
                TrainTestGridWorkflow trainTestGridWorkflow = new TrainTestGridWorkflow(qppMethod, queryFile, resFile);
                trainTestGridWorkflow.averageAcrossEpochs();
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
