package org.experiments;

import org.trec.TRECQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TrainTestInfo {
    List<TRECQuery> train;
    List<TRECQuery> test;
    static final int SEED = 31415;
    static Random r = new Random(SEED);

    TrainTestInfo(List<TRECQuery> parent, float trainRatio) {
        List<TRECQuery> listToShuffle = new ArrayList<>(parent);
        Collections.shuffle(listToShuffle, r); // shuffle the copy!

        int splitPoint = (int)(trainRatio * listToShuffle.size());
        train = listToShuffle.subList(0, splitPoint);
        test = listToShuffle.subList(splitPoint, listToShuffle.size());
    }

    List<TRECQuery> getTrain() { return train; }
    List<TRECQuery> getTest() { return test; }
}

