/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trec;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;

/**
 *
 * @author Debasis
 */
public class TRECQuery {
    public String       id;
    public String       title;
    public String       desc;
    public String       narr;
    public Query        luceneQuery;
    
    @Override
    public String toString() {
        return luceneQuery.toString();
    }

    public TRECQuery() {}

    public TRECQuery(Query luceneQuery) {
        this.luceneQuery = luceneQuery;
    }

    public TRECQuery(TRECQuery that) { // copy constructor
        this.id = that.id;
        this.title = that.title;
        this.desc = that.desc;
        this.narr = that.narr;
    }
    
    public TRECQuery(String id, Query luceneQuery) {
        this.id = id;
        this.title = "";
        this.desc = ""; this.narr = "";
        this.luceneQuery = luceneQuery;
    }

    public Query getLuceneQueryObj() { return luceneQuery; }

    public Set<Term> getQueryTerms(IndexSearcher searcher) throws IOException {
        Set<Term> terms = new HashSet<>();
        luceneQuery.createWeight(searcher, ScoreMode.COMPLETE, 1).extractTerms(terms);
        return terms;
    }
}
