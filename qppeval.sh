#!/bin/bash

if [ $# -lt 2 ]
then
	echo "usage: $0 <num docs to retrieve (e.g. 100)> <num-top docs for qpp-estimation (e.g. 50)>"
	exit
fi

#Change this path to index/ (committed on git) after downloading the Lucene indexed
#TREC disks 4/5 index from https://rsgqglln.tkhcloudstorage.com/item/c59086c6b00d41e79d53c58ad66bc21f
INDEXDIR=../luc4ir/index_trecd45/

QRELS=data/qrels.trec8.adhoc

cat > qpp.properties << EOF1

index.dir=$INDEXDIR
query.file=data/topics.401-450.xml
res.file=res_
qrels.file=$QRELS
retrieve.num_wanted=$1
qpp.numtopdocs=$2
EOF1

mvn exec:java@qppeval -Dexec.args="qpp.properties"

rm qpp.properties
