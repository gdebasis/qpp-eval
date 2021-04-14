#!/bin/bash

if [ $# -lt 4 ]
then
	echo "usage: $0 <num docs to retrieve (e.g. 100)> <num-top docs for qpp-estimation (e.g. 50) <method (avgidf/nqc/wig/clarity/uef_nqc,/uef_wig/uef_clarity)> <metric (rho/tau/qsim/qsim_strict/pairacc)>>"
	exit
fi

METHOD=$3
METRIC=$4

#Change this path to index/ (committed on git) after downloading the Lucene indexed
#TREC disks 4/5 index from https://rsgqglln.tkhcloudstorage.com/item/c59086c6b00d41e79d53c58ad66bc21f
INDEXDIR=/Users/debasis/research/common/trecd45/index/

QRELS=data/qrels.trec8.adhoc

cat > qpp.properties << EOF1

index.dir=$INDEXDIR
query.file=data/topics.401-450.xml
res.file=res_
qrels.file=$QRELS
retrieve.num_wanted=$1
qpp.numtopdocs=$2
qpp.method=$METHOD
qpp.metric=$METRIC

EOF1

mvn exec:java@method_metric_pair -Dexec.args="qpp.properties" > res.txt

rm qpp.properties

grep -w $METRIC res.txt | grep "QPP-method: $METHOD" | awk '{print $NF}' | awk '{s=$1" "s; if (NR%4==0) {print s; s=""}}' 
