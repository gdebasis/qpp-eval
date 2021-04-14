package org.experiments;

import org.trec.TRECQuery;
import java.util.List;

public class CorrelationAcrossModels {

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }

        try {
            SettingsLoader loader = new SettingsLoader(args[0]);

            QPPEvaluator qppEvaluator = new QPPEvaluator(
                    loader.getProp(),
                    loader.getCorrelationMetric(), loader.getSearcher(), loader.getNumWanted());
            List<TRECQuery> queries = qppEvaluator.constructQueries();
            qppEvaluator.relativeSystemRanksAcrossSims(queries);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
