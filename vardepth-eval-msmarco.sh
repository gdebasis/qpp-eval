#!/bin/bash

cat > deptheval.msmarco.properties << EOF1
index.dir=/Users/debasis/research/common/msmarco/passages/index
qrels.file=/Users/debasis/research/common/msmarco/data/pass_2019.qrels
query.file=/Users/debasis/research/common/msmarco/data/pass_2019.queries
retrieve.num_wanted=1000
qpp.numtopdocs=1000
resfiledir=/Users/debasis/research/qpp-eval/msmarco_runs/trecdl19/
qsim.numintervals=5
qpp.method=avgidf
qpp.metric=tau
pool.mindepth=20
pool.maxdepth=50
random_depth=false
qpp.direct=true
query.readmode=tsv
EOF1

mvn exec:java@deptheval -Dexec.args="deptheval.msmarco.properties"

