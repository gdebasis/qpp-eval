#!/bin/bash

if [ $# -lt 4 ]
then
    echo "Usage: " $0 " <following arguments in sequence>";
    echo "1. Model (bm25/lmdir/lmjm)";
    echo "2. Method (avgidf/nqc/wig/clarity/uef_nqc,/uef_wig/uef_clarity)";
    echo "3. Retrieval Eval Metric (ap, p_10, recall, ndcg)";
    echo "4. QPP eval Metric (r/rho/tau/qsim/qsim_strict/pairacc/class_acc/rmse)";
    exit 1;
fi

#These are hyper-parameters... usually these values work well
NUMWANTED=100
NUMTOP=100
SPLITS=80

MODEL=$1
METHOD=$2
RETEVAL_METRIC=$3
METRIC=$4
INDEXDIR=/store/index/trec_robust_lucene8/
QRELS=data/qrels.robust.all
QUERYFILE=data/topics.401-450.xml

cat > qpp.properties << EOF1

ret.model=$MODEL
index.dir=$INDEXDIR
query.file=$QUERYFILE
qrels.file=$QRELS
res.file=outputs/lmdir.res
res.train=outputs/train.res
res.test=outputs/test.res
retrieve.num_wanted=$NUMWANTED
reteval.metric=$RETEVAL_METRIC
qpp.numtopdocs=$NUMTOP
qpp.method=$METHOD
qpp.metric=$METRIC
qpp.splits=$SPLITS
transform_scores=true

EOF1

#mvn exec:java@method_metric_pair -Dexec.args="qpp.properties" > res.txt
#mvn exec:java@compute_all -Dexec.args="qpp.properties"
#mvn exec:java@across_metrics -Dexec.args="qpp.properties"
#mvn exec:java@across_models -Dexec.args="qpp.properties"
#mvn exec:java@linear_regressor -Dexec.args="qpp.properties"
#mvn exec:java@poly_regressor -Dexec.args="qpp.properties"
#mvn exec:java@nqc_calibrate -Dexec.args="qpp.properties"
mvn exec:java@rls_predict -Dexec.args="qpp.properties"

