package org.experiments;

import org.qpp.NQCSpecificity;
import org.qpp.NQCSpecificityCalibrated;
import org.qpp.QPPMethod;
import org.qpp.WIGSpecificity;
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
        double max_corr = 0;
        for (int qppTopK: qppTopKChoices) {
            System.out.println(String.format("Executing NQC (%d)", qppTopK));
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
        TrainTestInfo trainTestInfo = new TrainTestInfo(queries, TRAIN_RATIO);
        int tuned_topk = calibrateTopK(trainTestInfo.getTrain());
        System.out.println("Optimal top-k = " + tuned_topk);
        QPPMethod qppMethod = new NQCSpecificity(Settings.getSearcher());
        double corr = computeCorrelation(trainTestInfo.getTest(), qppMethod, tuned_topk);
        System.out.println("Test set correlation = " + corr);
        return corr;
    }

    public static void main(String[] args) {
        final String queryFile = "data/topics.robust.all";
        final String resFile = "bm25.robust.res";
        Settings.init("qpp.properties");

        try {
            QPPMethod[] qppMethods = {
                    new NQCSpecificity(Settings.getSearcher()),
                    new WIGSpecificity(Settings.getSearcher())
            };

            for (QPPMethod qppMethod: qppMethods) {
                System.out.println("Getting results for QPP method " + qppMethod);
                TrainTestGridWorkflow trainTestGridWorkflow = new TrainTestGridWorkflow(qppMethod, queryFile, resFile);
                trainTestGridWorkflow.averageAcrossEpochs();
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
