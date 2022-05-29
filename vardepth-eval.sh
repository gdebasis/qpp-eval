#!/bin/bash

cat > deptheval.properties << EOF1

index.dir=/Users/debasis/research/common/trecd45/index/
qrels.file=data/qrels.trec8.adhoc
query.file=data/topics.401-450.xml
retrieve.num_wanted=1000
EOF1

mvn exec:java@deptheval -Dexec.args="deptheval.properties"

