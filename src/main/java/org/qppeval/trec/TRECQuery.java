/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.qppeval.trec;

import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Weight;

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
}
