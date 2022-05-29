package org.qpp;

import org.apache.lucene.search.*;
import org.apache.lucene.index.Term;
import org.correlation.OverlapStats;
import org.evaluator.RetrievedResults;
import org.experiments.QPPEvaluator;
import org.experiments.Settings;
import org.feedback.RelevanceModelConditional;
import org.feedback.RelevanceModelIId;
import org.feedback.RetrievedDocTermInfo;
import org.trec.TRECQuery;
import org.evaluator.Evaluator;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.math3.stat.inference.TTest;

class QueryAndTopDocs {
    Query q;
    TopDocs topDocs;
    boolean pos;
    double specificity;

    QueryAndTopDocs(Query q, TopDocs topDocs) {
        this.q = q;
        this.topDocs = topDocs;
    }

    void setPolarity(boolean pos) { this.pos = pos; }
    void setSpecificity(double specificity) { this.specificity = specificity; }
}

class RefListInfo {
    List<QueryAndTopDocs> posList;  // {q: NQC(q) > NQC(q_0) and scores are significantly different}
    List<QueryAndTopDocs> negList;  // // {q: NQC(q) <= NQC(q_0) and scores are significantly different}

    RefListInfo() {
        posList = new ArrayList<>();
        negList = new ArrayList<>();
    }
}

public class RLSSpecificity implements QPPMethod {
    NQCSpecificity nqcSpecificity;
    IndexSearcher searcher;
    int NUM_FDBK_TOP_DOCS;
    int NUM_SAMPLES;  // Number of reference lists, i.e. number of queries to form
    int TOP_REFS; // number of reference lists for both pos and neg (corresponding to the lowest p values)
    static float ALPHA = 0.05f; // threshold for rejection of null hypothesis

    static Random rnd = new Random(Settings.SEED);

    public RLSSpecificity(IndexSearcher searcher, int num_fdbk, int num_sample, int top_refs) {
        this.searcher = searcher;
        this.NUM_FDBK_TOP_DOCS = num_fdbk;
        this.NUM_SAMPLES = num_sample;
        this.TOP_REFS = top_refs;
        nqcSpecificity = new NQCSpecificity(searcher);
    }

    // Returns q + t as a new query
    Query createAugmented(TRECQuery q, String newTerm) throws IOException {
        BooleanQuery.Builder qb = new BooleanQuery.Builder();
        Set<Term> terms = q.getQueryTerms(searcher);
        String fieldName = null;
        TermQuery tq;

        for (Term term: terms) {
            fieldName = term.field();
            tq = new TermQuery(term);
            qb.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));
        }
        tq = new TermQuery(new Term(fieldName, newTerm));
        qb.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));
        return qb.build();
    }
    
    // Different to UEF, the different ranked lists are not to be sampled as subsets
    // of the initial list but are to be constructed by retrieving on augmented queries
    Set<RetrievedDocTermInfo> topFdbkTerms(TRECQuery q, TopDocs topDocs) throws Exception {
        RelevanceModelIId rlm = new RelevanceModelConditional(searcher, q, topDocs, NUM_FDBK_TOP_DOCS);
        rlm.computeFdbkWeights();
        Set<String> qTerms = q.getQueryTerms(searcher).stream().map(x->x.text()).collect(Collectors.toSet());

        return
            rlm
            .getRetrievedDocsTermStats()
            .getTermStats()
            .values().stream()
            .sorted(RetrievedDocTermInfo::compareTo)
            .filter(x -> !qTerms.contains(x.getTerm()))
            .limit(NUM_SAMPLES)
            .collect(Collectors.toSet());
    }

    List<Query> generateAugmentedQueries(TRECQuery q, TopDocs topDocs) throws Exception {
        Set<RetrievedDocTermInfo> topTermWts = topFdbkTerms(q, topDocs); // NUM_SAMPLES feedback terms
        List<Query> augmented_queries = new ArrayList<>();
        /* for each feedback term */
        for (RetrievedDocTermInfo termInfo: topTermWts) {
            String term = termInfo.getTerm();
            Query augmented_query = createAugmented(q, term);
            augmented_queries.add(augmented_query);
        }
        return augmented_queries;
    }

    /* create a list of reference queries */
    List<QueryAndTopDocs> getReferenceLists(Query q, TopDocs topDocs, int numWanted) throws Exception {
        List<Query> augmented_queries = generateAugmentedQueries(new TRECQuery(q), topDocs);
        augmented_queries.stream().forEach(System.out::println);

        /* list of augmented <query + topdocs> objects */
        List<QueryAndTopDocs> refLists = new ArrayList<>();
        for (Query q_augmented: augmented_queries) {
            QueryAndTopDocs queryAndTopDocs = new QueryAndTopDocs(q_augmented, searcher.search(q_augmented, numWanted));
            refLists.add(queryAndTopDocs);
        }
        return refLists;
    }

    void computeNQCEstimates(List<TRECQuery> queries) throws Exception {
        QPPEvaluator qppEvaluator = new QPPEvaluator(
                Settings.getProp(),
                Settings.getCorrelationMetric(), Settings.getSearcher(), Settings.getNumWanted());
        Evaluator evaluator = qppEvaluator.executeQueries(queries,
                searcher.getSimilarity(), Settings.getNumWanted(), Settings.getQrelsFile(),
                Settings.RES_FILE, null);

    }

    // return q or -q if to be inserted in +ve list
    double compareSpecificities(Query orig_query, TopDocs orig_q_topDocs,
                                 Query augmented_query, TopDocs augmented_q_topDocs) {
        double nqc_orig_query = nqcSpecificity.computeNQC(orig_query, orig_q_topDocs, Settings.getQppTopK());
        double nqc_augmented_query = nqcSpecificity.computeNQC(augmented_query, augmented_q_topDocs, Settings.getQppTopK());
        int sign = nqc_augmented_query > nqc_orig_query? 1 : -1;
        return nqc_augmented_query*sign;
    }

    // retList is the list for the original query; refList for the augmented queries
    List<QueryAndTopDocs> filterAndPolarize(Query orig_q, TopDocs retList,
                                            List<QueryAndTopDocs> refLists) throws IOException {
        List<QueryAndTopDocs> queryAndTopDocsList = new ArrayList<>(refLists.size());
        System.out.println(String.format("Query %s: #ref lists = %d", orig_q.toString(), refLists.size()));

        int numPos = 0;
        double [] original_query_rsv, augmented_query_rsv;
//        double maxidf_o = new BaseIDFSpecificity(searcher).maxIDF(orig_q);

        double max_orig_rsv = Arrays.stream(retList.scoreDocs).map(x->x.score).mapToDouble(x->x).max().getAsDouble();
        original_query_rsv = Arrays.stream(retList.scoreDocs)
                .map(scoreDoc -> scoreDoc.score)
                .mapToDouble(d -> d)
                .map(d->d/max_orig_rsv)
                .toArray();        

        //List<Double> p_values = new ArrayList<>();

        for (QueryAndTopDocs queryAndTopDocs: refLists) {
            Query aug_query = queryAndTopDocs.q;
            //double maxidf_aug = new BaseIDFSpecificity(searcher).maxIDF(aug_query);
            double max_aug_rsv = Arrays.stream(retList.scoreDocs).map(x->x.score).mapToDouble(x->x).max().getAsDouble();
            augmented_query_rsv = Arrays.stream(queryAndTopDocs.topDocs.scoreDocs)
                    //.map(scoreDoc -> scoreDoc.score * maxidf_aug)
                    .map(scoreDoc -> scoreDoc.score) // "to be or not to be" idf
                    .mapToDouble(d -> d)
                    .map(d->d/max_aug_rsv)
                    .toArray();

            boolean rejected = new TTest().tTest(original_query_rsv, augmented_query_rsv, ALPHA);
            //DEBUG: If you wanna check the p values
            //double p = new TTest().pairedTTest(original_query_rsv, augmented_query_rsv);
            //p_values.add(p);

//            System.out.println("p = " + p);
            if (rejected) { // scores come from different means; hence they are different significantly
                double specificity_estimate = compareSpecificities(orig_q, retList, aug_query, queryAndTopDocs.topDocs);
//                System.out.println("Specificity estimate : " + specificity_estimate);
                queryAndTopDocs.setPolarity(specificity_estimate >= 0);
                queryAndTopDocs.setSpecificity(specificity_estimate);
                queryAndTopDocsList.add(queryAndTopDocs);
                if (specificity_estimate >= 0)
                    numPos++;
            }
            else {
//                System.out.println("Discarding ranked list for query " + aug_query); // debug ref lists
            }
        }
//        System.out.println(String.format("#pos = %d, #neg = %d", numPos, queryAndTopDocsList.size() - numPos));
        //System.out.println(String.format("Max p: %.6f", p_values.stream().mapToDouble(x->x).max().getAsDouble()));
        return queryAndTopDocsList;
    }

    @Override
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        final float p = 0.9f;
        List<QueryAndTopDocs> refLists;
        List<QueryAndTopDocs> polarizedRefLists;
        double sim;
        double aggr_specificity = 0;
        double aggr_sim = 0;
        try {
            refLists = getReferenceLists(q, topDocs, topDocs.scoreDocs.length);
            polarizedRefLists = filterAndPolarize(q, topDocs, refLists);
            for (QueryAndTopDocs queryAndTopDocs: polarizedRefLists) {
                sim = OverlapStats.computeRBO(topDocs, queryAndTopDocs.topDocs, k, p);
                if (!queryAndTopDocs.pos)
                    sim = -1*sim + 1;
                aggr_specificity += sim * queryAndTopDocs.specificity;
                aggr_sim += sim;
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return aggr_specificity/aggr_sim;
    }

    @Override
    public String name() {
        return "rls";
    }
}
