/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.regressor;

import org.qpp.QPPMethod;

/**
 *
 * @author suchana
 */

public class RegressionLearner {
    public String   qppMethod;
    public String   metric;
    public double   slope;
    public double   yIntercept;
    public double[] coeff;

    public RegressionLearner() {}

    public RegressionLearner(RegressionLearner that) { // copy constructor
        this.qppMethod = that.qppMethod;
        this.metric = that.metric;
        this.slope = that.slope;
        this.yIntercept = that.yIntercept;
        this.coeff = that.coeff;
    }
    
    public String getQppMethod() {
        return qppMethod;
    }

    public void setQppMethod(String qppMethod) {
        this.qppMethod = qppMethod;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public double getSlope() {
        return slope;
    }

    public void setSlope(double slope) {
        this.slope = slope;
    }

    public double getyIntercept() {
        return yIntercept;
    }

    public void setyIntercept(double yIntercept) {
        this.yIntercept = yIntercept;
    }
    
    public double[] getCoeff() {
        return coeff;
    }
    
    public void setCoeff(double[] coeff) {
        this.coeff = coeff;
    }
}
