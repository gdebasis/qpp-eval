#!/bin/bash

cat > deptheval.properties << EOF1
index.dir=/Users/debasis/research/common/trecd45/index/
#qrels.file=data/qrels.401-402.adhoc
qrels.file=data/qrels.trec8.adhoc
#query.file=data/topics.401-402.xml
query.file=data/topics.401-450.xml
retrieve.num_wanted=1000
qpp.numtopdocs=1000
resfiledir=trec/trec8
qsim.numintervals=5
qpp.method=avgidf
qpp.metric=tau
pool.mindepth=20
pool.maxdepth=50
random_depth=true
qpp.direct=true
EOF1

mvn exec:java@deptheval -Dexec.args="deptheval.properties"

