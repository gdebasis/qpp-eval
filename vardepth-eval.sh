#!/bin/bash

cat > deptheval.properties << EOF1
index.dir=/Users/debasis/research/common/trecd45/index/
qrels.file=data/qrels.trec8.adhoc
query.file=data/topics.401-450.xml
retrieve.num_wanted=1000
qpp.numtopdocs=1000
qsim.numintervals=5
#methods {avgidf, nqc, wig, clarity, uef_nqc, uef_wig, uef_clarity}
qpp.method=avgidf
#metrics = {rho, tau, qsim, qsim_strict, pairacc}
qpp.metric=tau
EOF1

mvn exec:java@deptheval -Dexec.args="deptheval.properties"

