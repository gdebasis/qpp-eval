package org.qpp;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.evaluator.RetrievedResults;
import org.experiments.Settings;

import java.util.Arrays;
import java.util.Random;

public class ScoreThresholdSpecificity extends NQCSpecificity {
    double[] rsvs;
    float s_min;
    float mu;
    float sigma;
    float lambda;
    float G_t;
    float s_mu;
    float s_std;

    static int SEED = 314156;
    static Random rnd = new Random(SEED);
    static float MIN = -10;
    static float MAX = 10;

    public ScoreThresholdSpecificity(IndexSearcher searcher) {
        super(searcher);
    }

    private float p_s_rel(float mu, float sigma, float s) { // P(s|1)
        return 1/sigma * phi((s-mu)/sigma);
    }

    private float phi(float s) {
        return (float)(Math.exp(-s*s/2)/ Math.sqrt(2*Math.PI));
    }

    private float psi(float lambda, float s) {
        return (float)(lambda * Math.exp(-lambda*s));
    }

    private float p_s_nonrel(float lambda, float s) {
        return psi(lambda, s-s_min);
    }

    private float Gt_new(int t) {
        float num = 0, denom = 0, p_rel;
        for (int i=0; i < t; i++) {
            p_rel = posterior_rel(t, lambda, mu, sigma, G_t, (float)rsvs[i]);
            num += p_rel;
        }
        return num/(float)t;
    }

    private float mu_new(int t) {
        float num = 0, denom = 0, p_rel;
        for (int i=0; i < t; i++) {
            p_rel = posterior_rel(t, lambda, mu, sigma, G_t, (float)rsvs[i]);
            num += p_rel * (float)rsvs[i];
            denom += p_rel;
        }
        return num/denom;
    }

    private float lambda_new(int t) {
        float num = 0, denom = 0, p_nrel, s_i;
        for (int i=0; i < t; i++) {
            s_i = (float)rsvs[i];
            p_nrel = posterior_nrel(t, lambda, mu, sigma, G_t, s_i);
            num += p_nrel;
            denom += p_nrel * s_i;
        }
        return num/denom;
    }

    private float sigma_new(int t, float mu) {
        float num = 0, denom = 0, p_1_s, s_i;
        for (int i=0; i < t; i++) {
            s_i = (float)rsvs[i];
            p_1_s = posterior_rel(t, lambda, mu, sigma, G_t, (float)rsvs[i]);
            num += p_1_s * (s_i - mu)*(s_i - mu); // to be computed with new mu
            denom += p_1_s;
        }
        return num/denom;
    }

    private float posterior_rel(int t, float lambda, float mu, float sigma, float G_t, float s) {
        float prior = p_s_rel(mu, sigma, s);
        float p_rel = G_t;
        float p_s = p_s(t, lambda, mu, sigma, s);
        return prior*p_rel/p_s;
    }

    private float posterior_nrel(int t, float lambda, float mu, float sigma, float G_t, float s) {
        float prior = p_s_nonrel(lambda, s);
        float p_nrel = 1 - G_t;
        float p_s = p_s(t, lambda, mu, sigma, s);
        return prior*p_nrel/p_s;
    }

    float p_s(int t, float lambda, float mu, float sigma, float s) {
        return (1-G_t) * p_s_nonrel(lambda, s) + G_t * p_s_rel(mu, sigma, s);
    }

    void runEM(int t) {
        final int MAX_ITERS = 10;
        initParams(t);

        for (int i=0; i < MAX_ITERS; i++) {
            G_t = Gt_new(t);
            mu = mu_new(t);
            sigma = sigma_new(t, mu); // use the new mu
            lambda = lambda_new(t);
        }
    }

    void initParams(int t) {
        float EPSILON = 0.01f;
        G_t = rnd.nextFloat(); // [0, 1]
        mu = s_min + ((float)rsvs[0] - s_min) * rnd.nextFloat();
        lambda = 1.0f/(float)(EPSILON + (s_mu - rsvs[t]) * rnd.nextDouble());
        sigma = (1+rnd.nextFloat()) * (float)Math.sqrt(Math.max(EPSILON*EPSILON, s_std*s_std - 1/(lambda*lambda)));
    }

    @Override
    public double computeSpecificity(Query q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        rsvs = getRSVs(topDocs);
        s_min = (float)rsvs[rsvs.length-1];
        s_mu = (float)(Arrays.stream(rsvs).average().getAsDouble());
        s_std = 0;
        for (int i=0; i < rsvs.length; i++) {
            s_std += (rsvs[i] - s_mu)*(rsvs[i] - s_mu);
        }
        s_std = (float)Math.sqrt(s_std/(double)rsvs.length);

        // Grid search for optimal t
        for (int t = Settings.minDepth; t <= Settings.maxDepth; t++) {
            runEM(t);
        }

        return 0;
    }
}
