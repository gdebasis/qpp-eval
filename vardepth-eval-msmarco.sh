#!/bin/bash

cat > deptheval.msmarco.properties << EOF1
index.dir=/Users/debasis/research/common/msmarco/passages/index
qrels.file=/Users/debasis/research/common/msmarco/data/pass_2019.qrels
query.file=/Users/debasis/research/common/msmarco/data/pass_2019.queries
retrieve.num_wanted=1000
qpp.numtopdocs=1000
resfiledir=msmarco_passage_trec_archive/dl19
qsim.numintervals=5
qpp.method=avgidf
qpp.metric=tau
pool.mindepth=5
pool.maxdepth=20
random_depth=false
qpp.direct=false
query.readmode=tsv
qpp.scores.file=qppbert/19.pred
qpp.logtramsform=false
EOF1

mvn exec:java@deptheval -Dexec.args="deptheval.msmarco.properties"

