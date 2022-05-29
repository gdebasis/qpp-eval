# QPP-Evaluator

A common framework, implemented in Java, to evaluate and compare between Query Performance Prediction (QPP) approaches with different ground-truth (reference) lists.

Simply speaking, a QPP method basically takes as input a set of queries, and predicts the relative order of the difficulty of finding relevant information for these queries, i.e., ranks the queries from easy to hard ones. This has got potential applications in query reformulation, relevance feedback etc.

This easy-to-manage tool could be used by Information Retrieval (IR) researchers as a tool for obtaining baseline results (e.g. standard methods on standard datasets), and could also be used by practitioners as a black-box to suit various applications.

This tool uses [Apache Lucene](https://lucene.apache.org/) for retrieval.
Currently, the following query performance prediction (QPP) models are implemented within the system.

1. Normalized Query Commitment (NQC) (more details in this [paper](https://ie.technion.ac.il/~kurland/qdQueryPerf.pdf))
2. Weighted Information Gain (WIG) (more details in this [paper](http://maroo.cs.umass.edu/getpdf.php?id=792))
3. Average IDF, (more information in this [thesis](https://chauff.github.io/documents/publications/thesis.pdf))

## Installing the tool

Clone the repository by executing

```
https://github.com/gdebasis/qpp-eval
```

For building the project, simply execute
```
mvn compile
```
You need to ensure that [Apache Maven](https://maven.apache.org/) and the standard [Java Development Kit](https://www.oracle.com/uk/java/technologies/javase-downloads.html) are installed on your system.

## Configuring the tool

The tool uses a properties file for configuration. The most important parameter is the path to a Lucene index, as specified the `index.dir` parameter.
This tool uses Lucene version 8.8 and hence you need to have an index obtained with a relatively recent version of Lucene.

A simple way to obtain a Lucene-based index is to make use of the resource  [luc4ir](https://github.com/gdebasis/luc4ir)  (another repository of mine) and follow the instructions to create a Lucene index.

Or you could simply download this pre-saved TREC disks 4/5 [index](https://drive.google.com/drive/folders/13k0AFcIemmtBvBpaBCyJR7ZYUIoRf2Kx?usp=sharing) on your local file system and set `index.dir` to that location.
Note that this only works if you want to run your experiments on the TREC Robust collection. For other collections, you have to execute the indexing step first.

The next step would be to configure the query set, e.g. the TREC 8 topic set (query ids: 401 to 450). For this you have to set the `query.file`. The `qrels.file` points to the relevance judgments, which you would need to evaluate the effectiveness of the query performance prediction (QPP).


## Example Script

An example [script](https://github.com/gdebasis/qpp-eval/blob/main/qppeval.sh) to run experiments on the TREC-8 topic set is provided.
A sample invocation of the script is
```
./qppeval.sh 1000 20 uef_wig rho
```
which means that
 - we retrieve `1000` documents for measuring the average precision of the queries (to define the ground-truth of easiness or hardness for evaluation), the QPP experiments,
 - we use `20` documents for estimating QPP with the `WIG` method (for pre-retrieval methods, such as AvgIDF this parameter is unused), and that we
 - report the QPP effectiveness with the `rho` measure.
 
 On running the script, you'll see a table of results. Each column corresponds to QPP being estimated (in this example with WIG) on the top-retrieved documents of a particular IR model (as samples we provide 4 such models). Each row of the table reports the rank correlation obtained with different ground-truths (namely with AP, P@5, P@10 and Recall).

A sample output is shown below.

|             | BM25   (0.5, 1) | BM25 (1.5,   0.75) | LM-Dir (1000) | LM-JM (0.6) |
|-------------|-----------------|--------------------|---------------|-------------|
| AP_1000     | 0.4487          | 0.5185             | 0.5365        | 0.4701      |
| Prec_5      | 0.3199          | 0.3340             | 0.3310        | 0.3539      |
| Prec_10     | 0.3780          | 0.4287             | 0.4176        | 0.3422      |
| Recall_1000 | 0.4232          | 0.4823             | 0.4676        | 0.4280      |

The value in the second row and third column indicates that the rank correlation ([Spearman's rho](https://en.wikipedia.org/wiki/Spearman%27s_rank_correlation_coefficient)) value by considering a reference order of TREC-8 queries sorted by the P@5 values is `0.3310`.
