/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.correlation;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

public class PolynomialRegression {
    
    public static void main(String[] args) {
        ArrayList<Integer> keyPoints1 = new ArrayList<Integer>();
        ArrayList<Integer> keyPoints2 = new ArrayList<Integer>();

        keyPoints1.add(1);
        keyPoints1.add(150);
        keyPoints1.add(10000);
        keyPoints1.add(100000);
        keyPoints1.add(1000000);
        
        keyPoints2.add(2);
        keyPoints2.add(250);
        keyPoints2.add(20000);
        keyPoints2.add(200000);
        keyPoints2.add(2000000);

        WeightedObservedPoints obs = new WeightedObservedPoints();
//        if(keyPoints != null && keyPoints.size() != 1) {
//            int size = keyPoints.size();
//            int sectionSize = (int) (1000 / (size - 1));
//            System.out.println("#### : " + sectionSize);
//            for(int i = 0; i < size; i++) {
//                if(i != 0)
//                    obs.add(keyPoints.get(i),  i * sectionSize);
//                else
//                    obs.add(keyPoints.get(0),  1);
//            }
//        } else if(keyPoints.size() == 1 && keyPoints.get(0) >= 1) {
//            obs.add(1,  1);
//            obs.add(keyPoints.get(0),  1000);
//        }
        
        for(int i = 0; i < keyPoints1.size(); i++) {
            obs.add(keyPoints1.get(i), keyPoints2.get(i));
        }

        PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);
//        fitter.withStartPoint(new double[] {keyPoints.get(0), 1});
        double[] coeff = fitter.fit(obs.toList());
//        System.out.println(Arrays.toString(coeff));
        System.out.println(coeff[0]);
    }
}